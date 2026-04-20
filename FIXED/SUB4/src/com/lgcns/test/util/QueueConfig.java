package com.lgcns.test.util;

/**
 * 큐 생성 시 설정값 묶음.
 * processTimeoutSeconds=0 이면 타임아웃 없음.
 * waitTimeSeconds=0 이면 롱폴링 없음 (즉시 반환).
 */
public class QueueConfig {

    public final int queueSize;
    public final long processTimeoutSeconds;
    public final int maxFailCount;
    public final long waitTimeSeconds;

    public QueueConfig(int queueSize, long processTimeoutSeconds, int maxFailCount, long waitTimeSeconds) {
        this.queueSize = queueSize;
        this.processTimeoutSeconds = processTimeoutSeconds;
        this.maxFailCount = maxFailCount;
        this.waitTimeSeconds = waitTimeSeconds;
    }
}
