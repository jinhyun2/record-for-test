package com.lgcns.test;

import java.io.*;
import java.util.*;

/**
 * 파일 형식: <요청ID>#<타임스탬프>#<데이터타입>#<데이터값>
 *   - 데이터타입 P: 예측값, A: 실제값
 *   - 타임스탬프: YYYYMMDDHHmmss (14자리)
 *
 * 콘솔 입력: 타임스탬프 앞자리 prefix (예: 2025030210)
 *   → 20250302100000 ~ 20250302105959 범위 내 레코드만 대상으로 정확도 계산
 *   → prefix 길이가 달라도 동일하게 동작 (startsWith 방식)
 *
 * 실행: java -cp out com.lgcns.test.Main <파일경로>
 */
public class Main {

    // 파일에서 읽은 레코드 (requestId, timestamp, dataType, dataValue)
    static class Record {
        final String requestId;
        final String timestamp;
        final String dataType;
        final String dataValue;

        Record(String requestId, String timestamp, String dataType, String dataValue) {
            this.requestId = requestId;
            this.timestamp = timestamp;
            this.dataType  = dataType;
            this.dataValue = dataValue;
        }
    }

    public static void main(String[] args) throws IOException {
        String filePath = args.length > 0 ? args[0] : "input.txt";

        List<Record> records = loadRecords(filePath);

        try (Scanner sc = new Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String prefix = sc.nextLine().trim();
                if (prefix.isEmpty()) continue;

                calcAccuracy(records, prefix);
            }
        }
    }

    private static List<Record> loadRecords(String filePath) throws IOException {
        List<Record> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("#", 4);
                if (parts.length < 4) continue;

                records.add(new Record(parts[0], parts[1], parts[2], parts[3]));
            }
        }
        return records;
    }

    private static void calcAccuracy(List<Record> records, String prefix) {
        // prefix로 시작하는 타임스탬프 레코드만 필터링
        // 예: prefix "2025030210" → 타임스탬프가 "2025030210"으로 시작하는 것만
        Map<String, String> predictMap = new LinkedHashMap<>();
        Map<String, String> actualMap  = new LinkedHashMap<>();

        for (Record r : records) {
            if (!r.timestamp.startsWith(prefix)) continue;

            if ("P".equals(r.dataType)) {
                predictMap.put(r.requestId, r.dataValue);
            } else if ("A".equals(r.dataType)) {
                actualMap.put(r.requestId, r.dataValue);
            }
        }

        int total   = 0;
        int matched = 0;

        for (Map.Entry<String, String> entry : predictMap.entrySet()) {
            String actualVal = actualMap.get(entry.getKey());
            if (actualVal == null) continue;

            total++;
            if (entry.getValue().equals(actualVal)) {
                matched++;
            }
        }

        // 범위 문자열 생성 (표시용)
        String rangeStart = padTo14(prefix, '0');
        String rangeEnd   = padTo14(prefix, '9');

        System.out.printf("[%s ~ %s]%n", rangeStart, rangeEnd);
        if (total == 0) {
            System.out.println("대상 요청이 없습니다.");
            return;
        }

        double ratio = (double) matched / total * 100.0;
        System.out.printf("전체 요청 수 (P·A 모두 존재): %d%n", total);
        System.out.printf("일치한 요청 수: %d%n", matched);
        System.out.printf("정확도: %.2f%%%n", ratio);
    }

    // prefix를 14자리로 패딩 (표시용)
    private static String padTo14(String prefix, char padChar) {
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < 14) sb.append(padChar);
        return sb.toString();
    }
}
