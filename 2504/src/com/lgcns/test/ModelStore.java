package com.lgcns.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * models.json 을 읽어 모델명 → agentId 목록 매핑을 관리한다.
 * 파일 형식: {"qwen-2.5": ["agent01","agent02"], "gpt-4": ["agent03"]}
 */
public class ModelStore {

    // modelName -> List<agentId>
    private static final Map<String, List<String>> modelMap = new ConcurrentHashMap<>();

    public static void load(String filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                List<String> agents = new ArrayList<>();
                for (JsonElement e : entry.getValue().getAsJsonArray()) {
                    agents.add(e.getAsString());
                }
                modelMap.put(entry.getKey(), agents);
            }
        }
        System.out.println("ModelStore loaded: " + modelMap.keySet());
    }

    /** modelName에 해당하는 agentId 목록 반환. 없으면 빈 리스트. */
    public static List<String> getAgents(String modelName) {
        return modelMap.getOrDefault(modelName, Collections.emptyList());
    }
}
