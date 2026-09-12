package com.pheeeew.device.fixture;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class FakeGoogleApiServer implements AutoCloseable {

    private static final String TOKEN_PATH = "/token";

    private final HttpServer server;
    private final List<받은_요청> 받은_요청들 = new CopyOnWriteArrayList<>();
    private final Deque<가짜_응답> 토큰_응답들 = new ArrayDeque<>();
    private final Deque<가짜_응답> 복호화_응답들 = new ArrayDeque<>();

    private 가짜_응답 마지막_토큰_응답;
    private 가짜_응답 마지막_복호화_응답;

    private FakeGoogleApiServer(HttpServer server) {
        this.server = server;
    }

    public static FakeGoogleApiServer 시작한다() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeGoogleApiServer fake = new FakeGoogleApiServer(server);
            server.createContext("/", fake::handle);
            server.start();
            return fake;
        } catch (IOException exception) {
            throw new IllegalStateException("가짜 구글 서버를 띄울 수 없습니다.", exception);
        }
    }

    public String 기준_주소() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String 토큰_엔드포인트() {
        return 기준_주소() + TOKEN_PATH;
    }

    public RestClient 이_서버를_향하는_클라이언트() {
        JdkClientHttpRequestFactory delegate = new JdkClientHttpRequestFactory();
        String baseUrl = 기준_주소();
        ClientHttpRequestFactory rewriting = (uri, method) -> delegate.createRequest(
                URI.create(baseUrl + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())),
                method
        );

        return RestClient.builder()
                .requestFactory(rewriting)
                .build();
    }

    public void 토큰_응답을_넣는다(int status, String body) {
        토큰_응답들.add(new 가짜_응답(status, body));
    }

    public void 복호화_응답을_넣는다(int status, String body) {
        복호화_응답들.add(new 가짜_응답(status, body));
    }

    public List<받은_요청> 받은_요청들() {
        return List.copyOf(받은_요청들);
    }

    public long 토큰_요청_수() {
        return 받은_요청들.stream().filter(request -> request.path().equals(TOKEN_PATH)).count();
    }

    public long 복호화_요청_수() {
        return 받은_요청들.stream().filter(request -> !request.path().equals(TOKEN_PATH)).count();
    }

    public 받은_요청 마지막_복호화_요청() {
        return 받은_요청들.stream()
                .filter(request -> !request.path().equals(TOKEN_PATH))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String body = readBody(exchange);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        받은_요청들.add(new 받은_요청(exchange.getRequestMethod(), path, authorization, body));

        가짜_응답 응답 = path.equals(TOKEN_PATH) ? 다음_토큰_응답() : 다음_복호화_응답();
        byte[] payload = 응답.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(응답.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private synchronized 가짜_응답 다음_토큰_응답() {
        if (!토큰_응답들.isEmpty()) {
            마지막_토큰_응답 = 토큰_응답들.poll();
        }
        if (마지막_토큰_응답 == null) {
            return new 가짜_응답(500, "{\"error\":\"no stub\"}");
        }
        return 마지막_토큰_응답;
    }

    private synchronized 가짜_응답 다음_복호화_응답() {
        if (!복호화_응답들.isEmpty()) {
            마지막_복호화_응답 = 복호화_응답들.poll();
        }
        if (마지막_복호화_응답 == null) {
            return new 가짜_응답(500, "{\"error\":\"no stub\"}");
        }
        return 마지막_복호화_응답;
    }

    public record 받은_요청(String method, String path, String authorization, String body) {
    }

    private record 가짜_응답(int status, String body) {
    }
}
