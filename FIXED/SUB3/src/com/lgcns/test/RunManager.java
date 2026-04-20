package com.lgcns.test;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

public class RunManager {

    public static void main(String[] args) throws Exception {
        new RunManager().start();
    }

    public void start() throws Exception {
        Server server = new Server();

        ServerConnector http = new ServerConnector(server);
        http.setHost("127.0.0.1");
        http.setPort(8080);
        server.addConnector(http);

        ServletHandler servletHandler = new ServletHandler();
        // [FIX] "/*" 패턴으로 /CREATE/, /SEND/ 등 모든 하위 경로를 명시적으로 매핑
        servletHandler.addServletWithMapping(MyServlet.class, "/*");
        server.setHandler(servletHandler);

        server.start();
        server.join();
    }
}
