package com.lgcns.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * POST /monitor  - agent가 예측·실제 데이터를 전송
 *   요청: {"agentId":"agent01","reqId":"req01","time":"20250303100000","dataType":"P","dataValue":0}
 *   응답: {"result":"OK"}
 *
 * POST /perf  - 모델 성능 지표 조회
 *   요청: {"modelName":"qwen-2.5","time":"2025030310"}
 *   응답: {"correct":3,"total":5}
 */
public class MyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json");

        String uri = req.getRequestURI();
        JsonObject body = JsonParser.parseReader(req.getReader()).getAsJsonObject();
        JsonObject result = new JsonObject();

        if ("/monitor".equals(uri)) {
            handleMonitor(body, result);
        } else if ("/perf".equals(uri)) {
            handlePerf(body, result);
        } else {
            result.addProperty("result", "Not Found");
            resp.setStatus(404);
        }

        resp.getWriter().write(result.toString());
    }

    // -------------------------------------------------------------------------

    private void handleMonitor(JsonObject body, JsonObject result) {
        String agentId   = body.get("agentId").getAsString();
        String reqId     = body.get("reqId").getAsString();
        String time      = body.get("time").getAsString();
        String dataType  = body.get("dataType").getAsString();
        String dataValue = body.get("dataValue").getAsString();

        DataStore.INSTANCE.add(
            new DataStore.AgentRecord(agentId, reqId, time, dataType, dataValue)
        );
        result.addProperty("result", "OK");
    }

    private void handlePerf(JsonObject body, JsonObject result) {
        String modelName  = body.get("modelName").getAsString();
        String timePrefix = body.get("time").getAsString();

        // 1. 모델에 속한 agentId 목록 조회
        List<String> agentIds = ModelStore.getAgents(modelName);

        // 2. 해당 agent 레코드 중 timePrefix 에 해당하는 것만 필터링
        //    timePrefix "2025030310" → time.startsWith("2025030310") 이면 해당 범위
        Map<String, String> predictMap = new LinkedHashMap<>();
        Map<String, String> actualMap  = new LinkedHashMap<>();

        for (DataStore.AgentRecord r : DataStore.INSTANCE.getByAgentIds(agentIds)) {
            if (!r.time.startsWith(timePrefix)) continue;

            if ("P".equals(r.dataType)) {
                predictMap.put(r.reqId, r.dataValue);
            } else if ("A".equals(r.dataType)) {
                actualMap.put(r.reqId, r.dataValue);
            }
        }

        // 3. P·A 둘 다 존재하는 reqId에 대해 일치 여부 집계
        int total   = 0;
        int correct = 0;

        for (Map.Entry<String, String> e : predictMap.entrySet()) {
            String actualVal = actualMap.get(e.getKey());
            if (actualVal == null) continue;
            total++;
            if (e.getValue().equals(actualVal)) correct++;
        }

        result.addProperty("correct", correct);
        result.addProperty("total", total);
    }
}
