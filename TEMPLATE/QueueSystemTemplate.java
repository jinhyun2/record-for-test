package com.lgcns.test;

// =============================================================================
// Queue System 코딩테스트 패턴 템플릿
//
// SUB1~4에서 반복되는 핵심 패턴 모음.
// 필요한 클래스를 복사해서 바로 사용하거나 조합할 수 있도록 구성됨.
//
// [패턴 목록]
//  Pattern1_ConsoleInput       : Scanner 안전 입력 루프
//  Pattern2_SimpleQueue        : 단일 FIFO 큐 (SUB1)
//  Pattern3_NamedQueueManager  : 이름 기반 다중 큐 + 용량 제어 (SUB2)
//  Pattern4_JettyServer        : Jetty 임베디드 HTTP 서버 설정
//  Pattern5_JsonHelper         : GSON JSON 파싱 / 응답 빌더
//  Pattern6_HttpServletBase    : Jetty 서블릿 라우팅 기본 틀
//  Pattern7_AckFailQueue       : ACK/FAIL 기반 신뢰성 큐 (SUB3)
//  Pattern8_LongPollQueue      : 롱폴링 + ProcessTimeout + DLQ (SUB4)
// =============================================================================

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


// =============================================================================
// PATTERN 1: Scanner 안전 입력 루프
// 사용: 콘솔 입력을 받아 명령을 처리하는 모든 SUB (SUB1, SUB2)
//
// ❌ 흔한 실수: while((line = sc.nextLine()) != null)
//    → nextLine()은 EOF 시 null 반환이 아닌 NoSuchElementException을 던짐
// ✅ 올바른 방법: sc.hasNextLine() + try-with-resources
// =============================================================================
class Pattern1_ConsoleInput {
    public static void main(String[] args) {
        try (java.util.Scanner sc = new java.util.Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();

                if (line.startsWith("COMMAND1 ")) {
                    String arg = line.substring(9); // "COMMAND1 " 길이만큼 잘라냄
                    // TODO: 처리 로직

                } else if (line.startsWith("COMMAND2 ")) {
                    // split(" ", N) : N개까지만 분리. 메시지에 공백이 있어도 안전
                    // ❌ split(" ")    → "SEND Q Hello World"에서 "Hello"만 추출
                    // ✅ split(" ", 3) → ["SEND", "Q", "Hello World"]
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3) continue; // 인자 부족 방어
                    // TODO: parts[1], parts[2] 사용

                } else if (line.equals("COMMAND3")) {
                    // TODO: 처리 로직
                }
            }
        }
    }
}


// =============================================================================
// PATTERN 2: 단일 FIFO 큐
// 사용: SUB1 — 하나의 무제한 큐로 SEND/RECEIVE 처리
// =============================================================================
class Pattern2_SimpleQueue {

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public void send(String message) {
        queue.add(message);
    }

    /** 비어 있으면 null 반환 */
    public String receive() {
        return queue.poll();
    }

    public int size() { return queue.size(); }
}


// =============================================================================
// PATTERN 3: 이름 기반 다중 큐 + 용량 제어
// 사용: SUB2 — CREATE/SEND/RECEIVE 명령으로 여러 큐를 독립 관리
//
// ✅ Result enum으로 결과 타입화 → 호출부에서 분기 명확
// ✅ ConcurrentHashMap으로 스레드 안전한 큐 맵 관리
// =============================================================================
class Pattern3_NamedQueueManager {

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

    public String receive(String name) {
        LinkedBlockingQueue<String> queue = queueMap.get(name);
        return queue != null ? queue.poll() : null;
    }
}


// =============================================================================
// PATTERN 4: Jetty 임베디드 HTTP 서버 설정
// 사용: SUB3, SUB4 — Jetty를 코드 안에서 직접 띄울 때
//
// ✅ addServletWithMapping(..., "/*") : 모든 하위 경로 매핑
//    ❌ "/"만 쓰면 /CREATE/foo 같은 경로가 라우팅되지 않을 수 있음
// ✅ ScheduledExecutorService: Timer 대신 권장
//    Timer는 태스크 예외 시 타이머 스레드 종료, ScheduledExecutorService는 다음 주기 계속 실행
// =============================================================================
class Pattern4_JettyServer {

    public void start() throws Exception {
        // ── (선택) 타이머 설정 ──────────────────────────────────────────────
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timer-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> { /* 주기적 처리 로직 */ },
                0, 100, TimeUnit.MILLISECONDS
        );

        // ── HTTP 서버 설정 ──────────────────────────────────────────────────
        Server server = new Server();
        ServerConnector http = new ServerConnector(server);
        http.setHost("127.0.0.1");
        http.setPort(8080);
        server.addConnector(http);

        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(Pattern6_HttpServletBase.class, "/*");
        server.setHandler(handler);

        server.start();
        server.join();

        scheduler.shutdown();
    }
}


// =============================================================================
// PATTERN 5: GSON JSON 파싱 / 응답 빌더
// 사용: SUB3, SUB4 — HTTP 요청 body 파싱 + 응답 JSON 생성
//
// ❌ 흔한 실수: Integer.valueOf(String.valueOf(body.get("key")))
//    → JsonElement.toString()을 거치므로 따옴표가 붙을 수 있음
// ✅ 올바른 방법: body.get("key").getAsInt() / getAsLong() / getAsString()
// =============================================================================
class Pattern5_JsonHelper {

    // 요청 body 파싱
    static void parseExample(HttpServletRequest req) throws IOException {
        JsonObject body = JsonParser.parseReader(req.getReader()).getAsJsonObject();

        int intVal    = body.get("IntField").getAsInt();
        long longVal  = body.get("LongField").getAsLong();
        String strVal = body.get("StrField").getAsString();
    }

    // 응답 JSON 빌더
    static String ok() {
        JsonObject obj = new JsonObject();
        obj.addProperty("Result", "OK");
        return obj.toString();
    }

    static String okWithMessage(String messageId, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("Result", "OK");
        obj.addProperty("MessageID", messageId);
        obj.addProperty("Message", message);
        return obj.toString();
    }

    static String error(String reason) {
        JsonObject obj = new JsonObject();
        obj.addProperty("Result", reason);
        return obj.toString();
    }
}


// =============================================================================
// PATTERN 6: HTTP 서블릿 라우팅 기본 틀
// 사용: SUB3, SUB4 — uri.startsWith()로 엔드포인트 분기
//
// ✅ lastSegment / secondLastSegment 헬퍼로 uri 파싱 반복 제거
//    ❌ String[] uris = uri.split("/"); uris[uris.length-1] 반복 사용
// ✅ doGet / doPost 모두 Content-Type: application/json 설정
// =============================================================================
class Pattern6_HttpServletBase extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "application/json");
        String uri = req.getRequestURI();
        String response;

        if (uri.startsWith("/RECEIVE/")) {
            String queueName = lastSegment(uri);
            // TODO: 처리 후 response 설정
            response = Pattern5_JsonHelper.ok();
        } else {
            response = Pattern5_JsonHelper.error("Not Found");
        }

        resp.getWriter().write(response);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json");
        String uri = req.getRequestURI();
        String response;

        if (uri.startsWith("/CREATE/")) {
            String name = lastSegment(uri);
            // TODO: 처리 후 response 설정
            response = Pattern5_JsonHelper.ok();
        } else if (uri.startsWith("/ACK/")) {
            String queueName  = secondLastSegment(uri); // /ACK/{queueName}/{messageId}
            String messageId  = lastSegment(uri);
            // TODO: 처리 후 response 설정
            response = Pattern5_JsonHelper.ok();
        } else {
            response = Pattern5_JsonHelper.error("Not Found");
        }

        resp.getWriter().write(response);
    }

    // URI 마지막 세그먼트: /RECEIVE/myQueue → "myQueue"
    private static String lastSegment(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }

    // URI 뒤에서 두 번째 세그먼트: /ACK/myQueue/msgId → "myQueue"
    private static String secondLastSegment(String uri) {
        String trimmed = uri.substring(0, uri.lastIndexOf('/'));
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}


// =============================================================================
// PATTERN 7: ACK/FAIL 기반 신뢰성 큐
// 사용: SUB3 — RECEIVE 후 ACK/FAIL로 처리 완료/실패를 확인하는 패턴
//
// 구조:
//   queue   : 대기 중인 메시지
//   dequeue : RECEIVE 후 처리 중인 메시지 (in-flight 추적)
//   ACK     : dequeue에서 제거 (처리 완료)
//   FAIL    : dequeue에서 꺼내 queue에 MessageID 순서로 재삽입
//
// ✅ ReentrantLock으로 receive(poll+offer) / fail(재삽입) 원자적 처리
// ✅ dequeue.offer() 반환값 확인 → 실패 시 queue에 복원하여 메시지 유실 방지
// =============================================================================
class Pattern7_AckFailQueue {

    public enum Status { OK, QUEUE_EXIST, QUEUE_NOT_FOUND, QUEUE_FULL, NO_MESSAGE, NOT_FOUND }

    public static class MessageEntry {
        public final String messageId;
        public final String message;
        MessageEntry(String messageId, String message) {
            this.messageId = messageId;
            this.message = message;
        }
    }

    public static class QueueResult {
        public final Status status;
        public final MessageEntry entry;
        QueueResult(Status s) { this.status = s; this.entry = null; }
        QueueResult(Status s, MessageEntry e) { this.status = s; this.entry = e; }
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

    public QueueResult create(String name, int capacity) {
        if (holderMap.containsKey(name)) return new QueueResult(Status.QUEUE_EXIST);
        holderMap.put(name, new QueueHolder(capacity));
        return new QueueResult(Status.OK);
    }

    public QueueResult send(String name, String message) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            if (h.queue.remainingCapacity() == 0) return new QueueResult(Status.QUEUE_FULL);
            h.queue.add(new MessageEntry(String.valueOf(System.currentTimeMillis()), message));
            return new QueueResult(Status.OK);
        } finally { h.lock.unlock(); }
    }

    public QueueResult receive(String name) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            MessageEntry entry = h.queue.poll();
            if (entry == null) return new QueueResult(Status.NO_MESSAGE);
            // offer 실패(dequeue 가득 참) 시 queue에 복원 → 메시지 유실 방지
            if (!h.dequeue.offer(entry)) { h.queue.add(entry); return new QueueResult(Status.NO_MESSAGE); }
            return new QueueResult(Status.OK, entry);
        } finally { h.lock.unlock(); }
    }

    public QueueResult ack(String name, String messageId) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            MessageEntry target = findIn(h.dequeue, messageId);
            if (target == null) return new QueueResult(Status.NOT_FOUND);
            h.dequeue.remove(target);
            return new QueueResult(Status.OK);
        } finally { h.lock.unlock(); }
    }

    public QueueResult fail(String name, String messageId) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            MessageEntry target = findIn(h.dequeue, messageId);
            if (target == null) return new QueueResult(Status.NOT_FOUND);
            h.dequeue.remove(target);
            reinsertOrdered(h.queue, target); // MessageID 오름차순 위치에 삽입
            return new QueueResult(Status.OK);
        } finally { h.lock.unlock(); }
    }

    private MessageEntry findIn(LinkedBlockingQueue<MessageEntry> q, String messageId) {
        for (MessageEntry e : q) { if (e.messageId.equals(messageId)) return e; }
        return null;
    }

    private void reinsertOrdered(LinkedBlockingQueue<MessageEntry> queue, MessageEntry entry) {
        long targetId = Long.parseLong(entry.messageId);
        List<MessageEntry> snapshot = new ArrayList<>(queue);
        queue.clear();
        boolean inserted = false;
        for (MessageEntry e : snapshot) {
            if (!inserted && targetId < Long.parseLong(e.messageId)) { queue.add(entry); inserted = true; }
            queue.add(e);
        }
        if (!inserted) queue.add(entry);
    }
}


// =============================================================================
// PATTERN 8: 롱폴링 + ProcessTimeout(자동 재처리) + DLQ
// 사용: SUB4 — WaitTime 롱폴링, 타임아웃 시 자동 FAIL, MaxFailCount 초과 시 DLQ 이동
//
// 핵심 구조:
//   sendTime=0  → 전송 가능
//   sendTime>0  → in-flight, ProcessTimeout 초과 시 자동 재처리
//
// 롱폴링 핵심:
// ❌ synchronized 메서드 안에서 Thread.sleep → SEND/Timer 전부 블로킹
// ✅ ReentrantLock + Condition.await() → 락 해제하고 대기
//    SEND/runTimeout에서 signalAll()로 대기 중인 receiver를 즉시 깨움
//
// 타이머:
// ❌ Timer/TimerTask → 예외 발생 시 타이머 스레드 종료
// ✅ ScheduledExecutorService → 예외가 나도 다음 주기에 계속 실행
// =============================================================================
class Pattern8_LongPollQueue {

    // QueueConfig: CREATE 시 파라미터 묶음
    static class QueueConfig {
        final int queueSize;
        final long processTimeoutSeconds; // 0 = 타임아웃 없음
        final int maxFailCount;
        final long waitTimeSeconds;       // 0 = 롱폴링 없음 (즉시 반환)

        QueueConfig(int queueSize, long processTimeoutSeconds, int maxFailCount, long waitTimeSeconds) {
            this.queueSize = queueSize;
            this.processTimeoutSeconds = processTimeoutSeconds;
            this.maxFailCount = maxFailCount;
            this.waitTimeSeconds = waitTimeSeconds;
        }
    }

    // MessageEntry: queue에 저장되는 메시지 (sendTime, failCount 포함)
    static class MessageEntry {
        final String messageId;
        final String message;
        int failCount = 0;
        long sendTime = 0L; // 0=미전송, >0=전송됨(초 단위 정렬 epoch ms)

        MessageEntry(String messageId, String message) {
            this.messageId = messageId;
            this.message = message;
        }
    }

    public enum Status { OK, QUEUE_EXIST, QUEUE_NOT_FOUND, QUEUE_FULL, NO_MESSAGE, NOT_FOUND }

    static class QueueResult {
        final Status status;
        final MessageEntry entry;
        QueueResult(Status s) { this.status = s; this.entry = null; }
        QueueResult(Status s, MessageEntry e) { this.status = s; this.entry = e; }
    }

    // QueueHolder: 큐 데이터 + 동기화 도구
    private static class QueueHolder {
        final LinkedBlockingQueue<MessageEntry> queue;
        final LinkedBlockingQueue<MessageEntry> deadQueue = new LinkedBlockingQueue<>();
        final QueueConfig config;
        final ReentrantLock lock = new ReentrantLock();
        final Condition hasMessage = lock.newCondition(); // 롱폴링 깨우기용

        QueueHolder(QueueConfig config) {
            this.queue  = new LinkedBlockingQueue<>(config.queueSize);
            this.config = config;
        }
    }

    // INSTANCE 싱글턴: 서블릿과 타이머가 동일 인스턴스를 공유
    public static final Pattern8_LongPollQueue INSTANCE = new Pattern8_LongPollQueue();
    private Pattern8_LongPollQueue() {}

    private final Map<String, QueueHolder> holderMap = new ConcurrentHashMap<>();

    public QueueResult create(String name, QueueConfig config) {
        if (holderMap.containsKey(name)) return new QueueResult(Status.QUEUE_EXIST);
        holderMap.put(name, new QueueHolder(config));
        return new QueueResult(Status.OK);
    }

    public QueueResult send(String name, String message) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            if (h.queue.remainingCapacity() == 0) return new QueueResult(Status.QUEUE_FULL);
            h.queue.add(new MessageEntry(String.valueOf(System.currentTimeMillis()), message));
            h.hasMessage.signalAll(); // 롱폴링 대기 중인 receiver 깨우기
            return new QueueResult(Status.OK);
        } finally { h.lock.unlock(); }
    }

    public QueueResult receive(String name) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.NO_MESSAGE);
        h.lock.lock();
        try {
            MessageEntry entry = findDeliverable(h.queue);
            if (entry != null) { markDelivered(entry); return new QueueResult(Status.OK, entry); }
            if (h.config.waitTimeSeconds == 0) return new QueueResult(Status.NO_MESSAGE);

            // 롱폴링: await()는 락을 해제하고 대기 → SEND/runTimeout 정상 실행 가능
            long deadline = System.currentTimeMillis() + h.config.waitTimeSeconds * 1000;
            long remaining;
            while ((remaining = deadline - System.currentTimeMillis()) > 0) {
                try { h.hasMessage.await(remaining, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return new QueueResult(Status.NO_MESSAGE); }
                entry = findDeliverable(h.queue);
                if (entry != null) { markDelivered(entry); return new QueueResult(Status.OK, entry); }
            }
            return new QueueResult(Status.NO_MESSAGE);
        } finally { h.lock.unlock(); }
    }

    public QueueResult ack(String name, String messageId) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            MessageEntry target = findById(h.queue, messageId);
            if (target != null) h.queue.remove(target);
            return new QueueResult(Status.OK);
        } finally { h.lock.unlock(); }
    }

    public QueueResult fail(String name, String messageId) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            MessageEntry target = findById(h.queue, messageId);
            if (target == null) return new QueueResult(Status.NOT_FOUND);
            target.failCount++;
            if (target.failCount > h.config.maxFailCount) {
                h.queue.remove(target);
                h.deadQueue.offer(target); // DLQ 이동
            } else {
                target.sendTime = 0L;      // 재전송 가능 상태로 초기화
                h.hasMessage.signalAll();
            }
            return new QueueResult(Status.OK);
        } finally { h.lock.unlock(); }
    }

    public QueueResult dlq(String name) {
        QueueHolder h = holderMap.get(name);
        if (h == null) return new QueueResult(Status.QUEUE_NOT_FOUND);
        h.lock.lock();
        try {
            MessageEntry entry = h.deadQueue.poll();
            return entry != null ? new QueueResult(Status.OK, entry) : new QueueResult(Status.NO_MESSAGE);
        } finally { h.lock.unlock(); }
    }

    /** ScheduledExecutorService에서 100ms마다 호출 */
    public void runTimeout() {
        long now = System.currentTimeMillis();
        long alignedNow = now - (now % 1000);
        for (Map.Entry<String, QueueHolder> e : holderMap.entrySet()) {
            QueueHolder h = e.getValue();
            if (h.config.processTimeoutSeconds == 0) continue;
            h.lock.lock();
            try {
                List<MessageEntry> toRemove = new ArrayList<>();
                for (MessageEntry entry : h.queue) {
                    if (entry.sendTime == 0L) continue;
                    if (alignedNow < entry.sendTime + h.config.processTimeoutSeconds * 1000) continue;
                    entry.failCount++;
                    if (entry.failCount > h.config.maxFailCount) {
                        h.deadQueue.offer(entry); toRemove.add(entry);
                    } else {
                        entry.sendTime = 0L; h.hasMessage.signalAll();
                    }
                }
                toRemove.forEach(h.queue::remove);
            } finally { h.lock.unlock(); }
        }
    }

    private MessageEntry findDeliverable(LinkedBlockingQueue<MessageEntry> q) {
        for (MessageEntry e : q) { if (e.sendTime == 0L) return e; }
        return null;
    }

    private void markDelivered(MessageEntry entry) {
        long now = System.currentTimeMillis();
        entry.sendTime = now - (now % 1000);
    }

    private MessageEntry findById(LinkedBlockingQueue<MessageEntry> q, String id) {
        for (MessageEntry e : q) { if (e.messageId.equals(id)) return e; }
        return null;
    }
}
