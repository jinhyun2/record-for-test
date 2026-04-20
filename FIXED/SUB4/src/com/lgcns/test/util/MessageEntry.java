package com.lgcns.test.util;

/**
 * 큐에 저장되는 메시지 엔티티.
 * sendTime=0  → 아직 전송되지 않음 (전송 가능)
 * sendTime>0  → 전송됨 (초 단위 정렬 epoch ms), ProcessTimeout 기준으로 타임아웃 판단
 * failCount   → FAIL/타임아웃 누적 횟수, MaxFailCount 초과 시 DLQ 이동
 *
 * 모든 필드는 반드시 QueueHolder.lock 보유 중에 접근해야 한다.
 */
public class MessageEntry {

    public final String messageId;
    public final String message;
    public int failCount;
    public long sendTime;

    public MessageEntry(String messageId, String message) {
        this.messageId = messageId;
        this.message = message;
        this.failCount = 0;
        this.sendTime = 0L;
    }
}
