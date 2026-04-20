package com.lgcns.test;

import com.lgcns.test.util.MessageQueueService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RunManager {

    public static void main(String[] args) throws Exception {
        new RunManager().start();
    }

    public void start() throws Exception {
        // Timer/TimerTask 대신 ScheduledExecutorService: 태스크 예외 발생 시에도 다음 주기 계속 실행
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "queue-timeout-timer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                MessageQueueService.INSTANCE::runTimeout,
                0, 100, TimeUnit.MILLISECONDS
        );

        Server server = new Server();
        ServerConnector http = new ServerConnector(server);
        http.setHost("127.0.0.1");
        http.setPort(8080);
        server.addConnector(http);

        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(MyServlet.class, "/*");
        server.setHandler(servletHandler);

        server.start();
        server.join();

        scheduler.shutdown();
    }
}
