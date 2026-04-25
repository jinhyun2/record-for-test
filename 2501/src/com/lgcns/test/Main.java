package com.lgcns.test;

import java.io.*;
import java.util.*;

/**
 * 파일 형식: <요청ID>#<타임스탬프>#<데이터타입>#<데이터값>
 *   - 데이터타입 P: 예측값, A: 실제값
 * 목표: 같은 요청ID에서 예측값(P)과 실제값(A)이 일치하는 요청의 비율 출력
 *
 * 실행: java -cp . com.lgcns.test.Main <파일경로>
 */
public class Main {

    public static void main(String[] args) throws IOException {
        String filePath = args.length > 0 ? args[0] : "input.txt";

        // requestId -> {P값, A값}
        Map<String, String> predictMap = new LinkedHashMap<>();
        Map<String, String> actualMap  = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("#", 4);
                if (parts.length < 4) continue;

                String requestId = parts[0];
                String dataType  = parts[2];
                String dataValue = parts[3];

                if ("P".equals(dataType)) {
                    predictMap.put(requestId, dataValue);
                } else if ("A".equals(dataType)) {
                    actualMap.put(requestId, dataValue);
                }
            }
        }

        // 예측값과 실제값이 모두 존재하는 요청만 대상으로 집계
        int total   = 0;
        int matched = 0;

        for (Map.Entry<String, String> entry : predictMap.entrySet()) {
            String requestId    = entry.getKey();
            String predictedVal = entry.getValue();
            String actualVal    = actualMap.get(requestId);

            if (actualVal == null) continue; // 실제값 없으면 제외

            total++;
            if (predictedVal.equals(actualVal)) {
                matched++;
            }
        }

        if (total == 0) {
            System.out.println("대상 요청이 없습니다.");
            return;
        }

        double ratio = (double) matched / total * 100.0;
        System.out.printf("전체 요청 수 (P·A 모두 존재): %d%n", total);
        System.out.printf("일치한 요청 수: %d%n", matched);
        System.out.printf("일치 비율: %.2f%%%n", ratio);
    }
}
