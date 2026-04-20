package com.lgcns.test;

import com.google.gson.JsonParser;
import com.lgcns.test.util.JsonResponseBuilder;
import com.lgcns.test.util.MessageQueueService;
import com.lgcns.test.util.MessageQueueService.QueueResult;
import com.lgcns.test.util.MessageQueueService.Status;
import com.lgcns.test.util.QueueConfig;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MyServlet extends HttpServlet {

    private static final MessageQueueService svc = MessageQueueService.INSTANCE;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Content-Type", "application/json");

        String uri = req.getRequestURI();
        String response;

        if (uri.startsWith("/RECEIVE/")) {
            String queueName = lastSegment(uri);
            QueueResult result = svc.receive(queueName);
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok(result.entry)
                    : JsonResponseBuilder.error("No Message");

        } else if (uri.startsWith("/DLQ/")) {
            QueueResult result = svc.dlq(lastSegment(uri));
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok(result.entry)
                    : JsonResponseBuilder.error("No Message");

        } else {
            response = JsonResponseBuilder.error("Not Found");
        }

        resp.getWriter().write(response);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json");

        String uri = req.getRequestURI();
        String response;

        if (uri.startsWith("/CREATE/")) {
            var body = JsonParser.parseReader(req.getReader()).getAsJsonObject();
            QueueConfig config = new QueueConfig(
                    body.get("QueueSize").getAsInt(),
                    body.get("ProcessTimeout").getAsLong(),
                    body.get("MaxFailCount").getAsInt(),
                    body.get("WaitTime").getAsLong()
            );
            QueueResult result = svc.create(lastSegment(uri), config);
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok()
                    : JsonResponseBuilder.error("Queue Exist");

        } else if (uri.startsWith("/SEND/")) {
            String message = JsonParser.parseReader(req.getReader())
                    .getAsJsonObject().get("Message").getAsString();
            QueueResult result = svc.send(lastSegment(uri), message);
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok()
                    : JsonResponseBuilder.error("Queue Full");

        } else if (uri.startsWith("/ACK/")) {
            svc.ack(secondLastSegment(uri), lastSegment(uri));
            response = JsonResponseBuilder.ok();

        } else if (uri.startsWith("/FAIL/")) {
            QueueResult result = svc.fail(secondLastSegment(uri), lastSegment(uri));
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok()
                    : JsonResponseBuilder.error("Not Found");

        } else {
            response = JsonResponseBuilder.error("Not Found");
        }

        resp.getWriter().write(response);
    }

    private static String lastSegment(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }

    private static String secondLastSegment(String uri) {
        String trimmed = uri.substring(0, uri.lastIndexOf('/'));
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}
