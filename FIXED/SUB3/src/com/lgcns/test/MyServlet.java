package com.lgcns.test;

import com.google.gson.JsonParser;
import com.lgcns.test.util.JsonResponseBuilder;
import com.lgcns.test.util.MessageQueueService;
import com.lgcns.test.util.MessageQueueService.QueueResult;
import com.lgcns.test.util.MessageQueueService.Status;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MyServlet extends HttpServlet {

    private static final MessageQueueService queueService = new MessageQueueService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Content-Type", "application/json");

        String uri = req.getRequestURI();
        String response;

        if (uri.startsWith("/RECEIVE/")) {
            String queueName = lastSegment(uri);
            QueueResult result = queueService.receive(queueName);
            if (result.status == Status.OK) {
                System.out.println("RECEIVE:" + queueName + ":" + queueService.queueSize(queueName));
                response = JsonResponseBuilder.ok(result.entry);
            } else {
                response = JsonResponseBuilder.error("No Message");
            }
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
            int queueSize = JsonParser.parseReader(req.getReader())
                    .getAsJsonObject().get("QueueSize").getAsInt();
            QueueResult result = queueService.create(lastSegment(uri), queueSize);
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok()
                    : JsonResponseBuilder.error("Queue Exist");

        } else if (uri.startsWith("/SEND/")) {
            String queueName = lastSegment(uri);
            String message = JsonParser.parseReader(req.getReader())
                    .getAsJsonObject().get("Message").getAsString();
            QueueResult result = queueService.send(queueName, message);
            if (result.status == Status.OK) {
                System.out.println("SEND:" + queueName + ":" + queueService.queueSize(queueName));
                response = JsonResponseBuilder.ok();
            } else {
                response = JsonResponseBuilder.error("Queue Full");
            }

        } else if (uri.startsWith("/ACK/")) {
            QueueResult result = queueService.ack(secondLastSegment(uri), lastSegment(uri));
            response = result.status == Status.OK
                    ? JsonResponseBuilder.ok()
                    : JsonResponseBuilder.error("Not Found");

        } else if (uri.startsWith("/FAIL/")) {
            String queueName = secondLastSegment(uri);
            QueueResult result = queueService.fail(queueName, lastSegment(uri));
            if (result.status == Status.OK) {
                System.out.println("FAIL:" + queueName + ":" + queueService.queueSize(queueName));
                response = JsonResponseBuilder.ok();
            } else {
                response = JsonResponseBuilder.error("Not Found");
            }

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
