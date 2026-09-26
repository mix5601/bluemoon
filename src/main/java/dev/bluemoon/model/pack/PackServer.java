package dev.bluemoon.model.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Minimal HTTP server that hosts the generated resource pack so players can download it. */
public final class PackServer {

    private final Logger logger;
    private HttpServer server;
    private ExecutorService executor;
    private String bind;
    private int port;
    private volatile byte[] data = new byte[0];

    public PackServer(Logger logger) {
        this.logger = logger;
    }

    public boolean isRunning() {
        return server != null;
    }

    public void update(byte[] zip) {
        this.data = zip;
    }

    public void start(String bind, int port) {
        if (server != null && bind.equals(this.bind) && port == this.port) {
            return;
        }
        stop();
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress(bind, port), 0);
            s.createContext("/", exchange -> {
                byte[] body = data;
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "BlueMoon-PackServer");
                t.setDaemon(true);
                return t;
            });
            s.setExecutor(executor);
            s.start();
            server = s;
            this.bind = bind;
            this.port = port;
            logger.info("리소스팩 서버 시작: " + bind + ":" + port);
        } catch (IOException e) {
            logger.warning("리소스팩 서버를 시작하지 못했습니다 (" + bind + ":" + port + "): " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
