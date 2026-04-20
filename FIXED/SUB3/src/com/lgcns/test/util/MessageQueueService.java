package com.lgcns.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ACK/FAIL 기반 신뢰성 메시지 큐 서비스.
 * - RECEIVE 시 메시지를 dequeue(in-flight)로 이동하여 처리 추적
 * - ACK: dequeue에서 제거 (처리 완료)
 * - FAIL: dequeue에서 꺼내 MessageID 순서에 맞게 queue 재삽입
 * - 모든 queue/dequeue 접근은 QueueHolder.lock으로 직렬화
 */
public class MessageQueueService {

    public enum Status { OK, QUEUE_EXIST, QUEUE_NOT_FOUND, QUEUE_FULL, NO_MESSAGE, NOT_FOUND }

    public static class MessageEntry {
        public final String messageId;
        public final String message;

        public MessageEntry(String messageId, String message) {
            this.messageId = messageId;
            this.message = message;
        }
    }

    public static class QueueResult {
        public final Status status;
        public final MessageEntry entry;

        public QueueResult(Status status) { this.status = status; this.entry = null; }
        public QueueResult(Status status, MessageEntry entry) { this.status = status; this.entry = entry; }
    }

    private static class QueueHolder {
        final LinkedBlockingQueue<MessageEntry> queue;
        final LinkedBlockingQueue<MessageEntry> dequeue;
        final ReentrantLock lock = new ReentrantLock();

        QueueHolder(int capacity) {
            this.queue   = new LinkedBlockingQueue<>(capacity);
            this.dequeue = new LinkedBlockingQueue<>(capacity);
        }
    }

    private final Map<String, QueueHolder> holderMap = new ConcurrentHashMap<>();

    /** @return OK | QUEUE_EXIST */
    public QueueResult create(String name, int capacity) {
        if (holderMap.containsKey(name)) return new QueueResult(Status.QUEUE_EXIST);
        holderMap.put(name, new QueueHolder(capacity));
        return new QueueResult(Status.OK);
    }

    /** @return OK | QUEUE_NOT_FOUND | QUEUE_FULL */
    public QueueResult send(String name, String message) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            if (holder.queue.remainingCapacity() == 0) return new QueueResult(Status.QUEUE_FULL);
            holder.queue.add(new MessageEntry(String.valueOf(System.currentTimeMillis()), message));
            return new QueueResult(Status.OK);
        } finally {
            holder.lock.unlock();
        }
    }

    /**
     * 메시지를 queue에서 꺼내 dequeue(in-flight)에 등록.
     * dequeue가 가득 찬 경우 queue에 되돌려 메시지 유실 방지.
     *
     * @return OK(entry 포함) | QUEUE_NOT_FOUND | NO_MESSAGE
     */
    public QueueResult receive(String name) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            MessageEntry entry = holder.queue.poll();
            if (entry == null) return new QueueResult(Status.NO_MESSAGE);
            if (!holder.dequeue.offer(entry)) {
                holder.queue.add(entry);
                return new QueueResult(Status.NO_MESSAGE);
            }
            return new QueueResult(Status.OK, entry);
        } finally {
            holder.lock.unlock();
        }
    }

    /** @return OK | QUEUE_NOT_FOUND | NOT_FOUND */
    public QueueResult ack(String name, String messageId) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            MessageEntry target = findIn(holder.dequeue, messageId);
            if (target == null) return new QueueResult(Status.NOT_FOUND);
            holder.dequeue.remove(target);
            return new QueueResult(Status.OK);
        } finally {
            holder.lock.unlock();
        }
    }

    /**
     * dequeue에서 제거 후 MessageID 순서에 맞게 queue 재삽입.
     *
     * @return OK | QUEUE_NOT_FOUND | NOT_FOUND
     */
    public QueueResult fail(String name, String messageId) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            MessageEntry target = findIn(holder.dequeue, messageId);
            if (target == null) return new QueueResult(Status.NOT_FOUND);

            holder.dequeue.remove(target);
            reinsertOrdered(holder.queue, target);
            return new QueueResult(Status.OK);
        } finally {
            holder.lock.unlock();
        }
    }

    public int queueSize(String name) {
        QueueHolder holder = holderMap.get(name);
        return holder != null ? holder.queue.size() : -1;
    }

    private MessageEntry findIn(LinkedBlockingQueue<MessageEntry> q, String messageId) {
        for (MessageEntry e : q) {
            if (e.messageId.equals(messageId)) return e;
        }
        return null;
    }

    /** MessageID(epoch ms) 오름차순 위치에 entry 삽입 */
    private void reinsertOrdered(LinkedBlockingQueue<MessageEntry> queue, MessageEntry entry) {
        long targetId = Long.parseLong(entry.messageId);
        List<MessageEntry> snapshot = new ArrayList<>(queue);
        queue.clear();
        boolean inserted = false;
        for (MessageEntry e : snapshot) {
            if (!inserted && targetId < Long.parseLong(e.messageId)) {
                queue.add(entry);
                inserted = true;
            }
            queue.add(e);
        }
        if (!inserted) queue.add(entry);
    }
}
