package com.lgcns.test.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class NamedQueueManager {

    public enum Result { OK, QUEUE_EXIST, QUEUE_NOT_FOUND, QUEUE_FULL }

    private final Map<String, LinkedBlockingQueue<String>> queueMap = new ConcurrentHashMap<>();

    public Result create(String name, int capacity) {
        if (queueMap.containsKey(name)) return Result.QUEUE_EXIST;
        queueMap.put(name, new LinkedBlockingQueue<>(capacity));
        return Result.OK;
    }

    public Result send(String name, String message) {
        LinkedBlockingQueue<String> queue = queueMap.get(name);
        if (queue == null) return Result.QUEUE_NOT_FOUND;
        if (queue.remainingCapacity() == 0) return Result.QUEUE_FULL;
        queue.add(message);
        return Result.OK;
    }

    /** 큐가 없거나 비어 있으면 null 반환 */
    public String receive(String name) {
        LinkedBlockingQueue<String> queue = queueMap.get(name);
        return queue != null ? queue.poll() : null;
    }

    public int size(String name) {
        LinkedBlockingQueue<String> queue = queueMap.get(name);
        return queue != null ? queue.size() : -1;
    }
}
