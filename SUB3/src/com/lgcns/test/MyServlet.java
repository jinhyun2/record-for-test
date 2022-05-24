package com.lgcns.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class MyServlet extends HttpServlet {
    Map<String, LinkedBlockingQueue<Map>> queueMap = new ConcurrentHashMap<>();
    Map<String, LinkedBlockingQueue<Map>> deQueueMap = new ConcurrentHashMap<>();

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        JsonObject result = new JsonObject();
        if (uri.startsWith("/RECEIVE")) {
            String[] uris = uri.split("/");
            LinkedBlockingQueue<Map> queue = queueMap.get(uris[uris.length - 1]);
            if (queue != null && !queue.isEmpty()) {
                Map<String, String> pollMap = queue.poll();
                Queue<Map> dequeue = deQueueMap.get(uris[uris.length - 1]);
                dequeue.offer(pollMap);
                result.addProperty("Result", "OK");
                result.addProperty("MessageID", (String) pollMap.get("MessageID"));
                result.addProperty("Message", (String) pollMap.get("Message"));
            } else {
                result.addProperty("Result", "No Message");
            }
            System.out.println("RECEIVE" + ":" +uris[uris.length - 1] +":" +queue.size());
        }
        resp.getWriter().write(result.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        JsonObject result = new JsonObject();
        if (uri.startsWith("/CREATE")) {
            String[] uris = uri.split("/");
            Queue<Map> queue = queueMap.get(uris[uris.length - 1]);
            if (queue != null) {
                result.addProperty("Result", "Queue Exist");
            } else {
                JsonObject body = (JsonObject) JsonParser.parseReader(req.getReader());
                queueMap.put(uris[uris.length - 1], new LinkedBlockingQueue<>(Integer.valueOf(String.valueOf(body.get("QueueSize")))));
                deQueueMap.put(uris[uris.length - 1], new LinkedBlockingQueue<>(Integer.valueOf(String.valueOf(body.get("QueueSize")))));
                result.addProperty("Result", "OK");
            }
        } else if (uri.startsWith("/SEND")) {
            String[] uris = uri.split("/");
            LinkedBlockingQueue<Map> queue = queueMap.get(uris[uris.length - 1]);
            if (queue != null && queue.remainingCapacity() > 0) {
                JsonObject body = (JsonObject) JsonParser.parseReader(req.getReader());
                Map<String, String> msgMap = new ConcurrentHashMap<>();
                msgMap.put("MessageID", String.valueOf(System.currentTimeMillis()));
                msgMap.put("Message", String.valueOf(body.get("Message").getAsString()));
                queue.add(msgMap);
                result.addProperty("Result", "OK");
            } else if (queue != null && queue.remainingCapacity() == 0) {
                result.addProperty("Result", "Queue Full");
            }
            System.out.println("SEND" +":"+ uris[uris.length - 1] +":" +queue.size());
        } else if (uri.startsWith("/ACK")) {
            String[] uris = uri.split("/");
            LinkedBlockingQueue<Map> queue = deQueueMap.get(uris[uris.length - 2]);
            Map<String, String> mapForRemove = null;
            for (Map<String, String> map : queue) {
                if (map.get("MessageID").equals(uris[uris.length - 1])) {
                    mapForRemove = map;
                    break;
                }
            }
            queue.remove(mapForRemove);
            result.addProperty("Result", "OK");
        } else if (uri.startsWith("/FAIL")) {
            String[] uris = uri.split("/");
            LinkedBlockingQueue<Map> dequeue = deQueueMap.get(uris[uris.length - 2]);
            LinkedBlockingQueue<Map> queue = queueMap.get(uris[uris.length - 2]);
            List<Map> list = new LinkedList<>();
            Map<String, String> mapForAdd = null;
            for (Map<String, String> map : dequeue) {
                if (map.get("MessageID").equals(uris[uris.length - 1])) {
                    mapForAdd = map;
                    break;
                }
            }
            dequeue.remove(mapForAdd);
            long id = Long.valueOf(mapForAdd.get("MessageID"));
            boolean isInsert = false;
            if (queue.size() == 0) {
                list.add(mapForAdd);
            } else {
                for (Map<String, String> map : queue) {
                    if (!isInsert && id < Long.valueOf(map.get("MessageID"))) {
                        list.add(mapForAdd);
                        isInsert = true;
                    }
                    list.add(map);
                }
                if (!isInsert) {
                    list.add(mapForAdd);
                }
            }
            queue.clear();
            queue.addAll(list);
            System.out.println("FAIL" +":"+uris[uris.length - 2] +":" +queue.size());
            result.addProperty("Result", "OK");
        }
        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json");
        resp.getWriter().write(result.toString());
    }
}
