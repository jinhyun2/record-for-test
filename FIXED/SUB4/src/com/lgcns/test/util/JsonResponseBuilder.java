package com.lgcns.test.util;

import com.google.gson.JsonObject;

/**
 * HTTP 응답용 JSON 문자열 생성 유틸.
 */
public class JsonResponseBuilder {

    public static String ok() {
        JsonObject obj = new JsonObject();
        obj.addProperty("Result", "OK");
        return obj.toString();
    }

    public static String ok(MessageEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("Result", "OK");
        obj.addProperty("MessageID", entry.messageId);
        obj.addProperty("Message", entry.message);
        return obj.toString();
    }

    public static String error(String resultMessage) {
        JsonObject obj = new JsonObject();
        obj.addProperty("Result", resultMessage);
        return obj.toString();
    }
}
