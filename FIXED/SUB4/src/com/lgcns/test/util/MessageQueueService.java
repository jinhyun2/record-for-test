package com.lgcns.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ProcessTimeout / MaxFailCount / WaitTime(롱폴링) / DLQ 지원 메시지 큐 서비스.
 *
 * 메시지 생명주기:
 *   sendTime=0  → 전송 가능
 *   sendTime>0  → in-flight (전송됨), ProcessTimeout 초과 시 자동 재처리
 *   failCount > maxFailCount → DLQ 이동
 *
 * 롱폴링: Condition.await()로 락을 해제하고 대기.
 *   SEND / runTimeout()이 메시지를 추가하면 signalAll()로 즉시 깨움.
 */
public class MessageQueueService {

    public static final MessageQueueService INSTANCE = new MessageQueueService();

    private MessageQueueService() {}

    public enum Status { OK, QUEUE_EXIST, QUEUE_NOT_FOUND, QUEUE_FULL, NO_MESSAGE, NOT_FOUND }

    public static class QueueResult {
        public final Status status;
        public final MessageEntry entry;

        public QueueResult(Status status) { this.status = status; this.entry = null; }
        public QueueResult(Status status, MessageEntry entry) { this.status = status; this.entry = entry; }
    }

    private static class QueueHolder {
        final LinkedBlockingQueue<MessageEntry> queue;
        final LinkedBlockingQueue<MessageEntry> deadQueue;
        final QueueConfig config;
        final ReentrantLock lock = new ReentrantLock();
        final Condition hasMessage = lock.newCondition();

        QueueHolder(QueueConfig config) {
            this.queue     = new LinkedBlockingQueue<>(config.queueSize);
            this.deadQueue = new LinkedBlockingQueue<>();
            this.config    = config;
        }
    }

    private final Map<String, QueueHolder> holderMap = new ConcurrentHashMap<>();

    /** @return OK | QUEUE_EXIST */
    public QueueResult create(String name, QueueConfig config) {
        if (holderMap.containsKey(name)) return new QueueResult(Status.QUEUE_EXIST);
        holderMap.put(name, new QueueHolder(config));
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
            holder.hasMessage.signalAll();
            System.out.println("SEND:" + name + ":" + holder.queue.size());
            return new QueueResult(Status.OK);
        } finally {
            holder.lock.unlock();
        }
    }

    /**
     * sendTime=0인 메시지를 전송 처리.
     * WaitTime>0이면 Condition.await()로 대기 (락 해제 → SEND/runTimeout 정상 동작).
     *
     * @return OK(entry 포함) | NO_MESSAGE
     */
    public QueueResult receive(String name) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.NO_MESSAGE);

        holder.lock.lock();
        try {
            MessageEntry entry = findDeliverable(holder.queue);
            if (entry != null) {
                markDelivered(entry);
                System.out.println("RECEIVE:" + name + ":" + holder.queue.size());
                return new QueueResult(Status.OK, entry);
            }
            if (holder.config.waitTimeSeconds == 0) return new QueueResult(Status.NO_MESSAGE);

            long deadline = System.currentTimeMillis() + holder.config.waitTimeSeconds * 1000;
            long remainingMs;
            while ((remainingMs = deadline - System.currentTimeMillis()) > 0) {
                try {
                    holder.hasMessage.await(remainingMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new QueueResult(Status.NO_MESSAGE);
                }
                entry = findDeliverable(holder.queue);
                if (entry != null) {
                    markDelivered(entry);
                    System.out.println("RECEIVE(wait):" + name + ":" + holder.queue.size());
                    return new QueueResult(Status.OK, entry);
                }
            }
            return new QueueResult(Status.NO_MESSAGE);
        } finally {
            holder.lock.unlock();
        }
    }

    /** @return OK | QUEUE_NOT_FOUND */
    public QueueResult ack(String name, String messageId) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            MessageEntry target = findByMessageId(holder.queue, messageId);
            if (target != null) {
                holder.queue.remove(target);
                System.out.println("ACK:" + name + ":" + holder.queue.size());
            }
            return new QueueResult(Status.OK);
        } finally {
            holder.lock.unlock();
        }
    }

    /** @return OK | QUEUE_NOT_FOUND | NOT_FOUND */
    public QueueResult fail(String name, String messageId) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            MessageEntry target = findByMessageId(holder.queue, messageId);
            if (target == null) return new QueueResult(Status.NOT_FOUND);

            target.failCount++;
            if (target.failCount > holder.config.maxFailCount) {
                holder.queue.remove(target);
                holder.deadQueue.offer(target);
                System.out.println("FAIL→DLQ:" + name + ":" + target.message);
            } else {
                target.sendTime = 0L;
                holder.hasMessage.signalAll();
                System.out.println("FAIL:" + name + ":" + holder.queue.size());
            }
            return new QueueResult(Status.OK);
        } finally {
            holder.lock.unlock();
        }
    }

    /** @return OK(entry 포함) | QUEUE_NOT_FOUND | NO_MESSAGE */
    public QueueResult dlq(String name) {
        QueueHolder holder = holderMap.get(name);
        if (holder == null) return new QueueResult(Status.QUEUE_NOT_FOUND);

        holder.lock.lock();
        try {
            MessageEntry entry = holder.deadQueue.poll();
            return entry != null ? new QueueResult(Status.OK, entry) : new QueueResult(Status.NO_MESSAGE);
        } finally {
            holder.lock.unlock();
        }
    }

    /** ScheduledExecutorService에서 100ms마다 호출. in-flight 메시지 타임아웃 재처리. */
    public void runTimeout() {
        long cuTime = System.currentTimeMillis();
        long alignedTime = cuTime - (cuTime % 1000);

        for (Map.Entry<String, QueueHolder> e : holderMap.entrySet()) {
            QueueHolder holder = e.getValue();
            if (holder.config.processTimeoutSeconds == 0) continue;

            holder.lock.lock();
            try {
                List<MessageEntry> toRemove = new ArrayList<>();
                for (MessageEntry entry : holder.queue) {
                    if (entry.sendTime == 0L) continue;
                    if (alignedTime < entry.sendTime + holder.config.processTimeoutSeconds * 1000) continue;

                    entry.failCount++;
                    System.out.println("Timeout:" + e.getKey() + ":" + entry.message + " failCount=" + entry.failCount);
                    if (entry.failCount > holder.config.maxFailCount) {
                        holder.deadQueue.offer(entry);
                        toRemove.add(entry);
                    } else {
                        entry.sendTime = 0L;
                        holder.hasMessage.signalAll();
                    }
                }
                toRemove.forEach(holder.queue::remove);
            } finally {
                holder.lock.unlock();
            }
        }
    }

    public int queueSize(String name) {
        QueueHolder holder = holderMap.get(name);
        return holder != null ? holder.queue.size() : -1;
    }

    private MessageEntry findDeliverable(LinkedBlockingQueue<MessageEntry> queue) {
        for (MessageEntry e : queue) {
            if (e.sendTime == 0L) return e;
        }
        return null;
    }

    private void markDelivered(MessageEntry entry) {
        long cuTime = System.currentTimeMillis();
        entry.sendTime = cuTime - (cuTime % 1000);
    }

    private MessageEntry findByMessageId(LinkedBlockingQueue<MessageEntry> queue, String messageId) {
        for (MessageEntry e : queue) {
            if (e.messageId.equals(messageId)) return e;
        }
        return null;
    }
}
