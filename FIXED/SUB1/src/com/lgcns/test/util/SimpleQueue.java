package com.lgcns.test.util;

import java.util.concurrent.LinkedBlockingQueue;

public class SimpleQueue {

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public void send(String message) {
        queue.add(message);
    }

    public String receive() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }
}
