package com.github.deripas.receipt.parse.qr.rs.suf;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Verification service on localhost answering with the responses of the real one, saved in {@code suf/}
 * for a receipt of 2026-01-06: the link asked for JSON, its page and the specification.
 */
public class FakeSufServer implements AutoCloseable {

    public static final String LINK_PATH = "/v/?vl=A1g4OUZYM0ZE";

    private final HttpServer server;
    /**
     * Bodies of the specification requests.
     */
    public final List<String> specificationRequests = new ArrayList<>();
    public volatile boolean specificationAvailable = true;

    @SneakyThrows
    public FakeSufServer() {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v/", this::verification);
        server.createContext("/specifications", this::specification);
        server.start();
    }

    public URI baseUri() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
    }

    public URI link() {
        return baseUri().resolve(LINK_PATH);
    }

    public SufClient client() {
        return new SufClient(baseUri(), SufClient.DEFAULT_TIMEOUT);
    }

    private void verification(HttpExchange exchange) throws IOException {
        final boolean json = Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("Accept"), "")
                .contains("application/json");
        respond(exchange, json ? "invoice.json" : "page.html");
    }

    private void specification(HttpExchange exchange) throws IOException {
        specificationRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (specificationAvailable) {
            respond(exchange, "specifications.json");
        } else {
            final byte[] body = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, String fixture) throws IOException {
        try (InputStream input = FakeSufServer.class.getResourceAsStream("/suf/" + fixture)) {
            final byte[] body = Objects.requireNonNull(input, fixture).readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
