package com.doroddi.courseregistration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.initial-data.enabled=false")
@org.testcontainers.junit.jupiter.Testcontainers
class ApplicationStartupTest {
    @org.testcontainers.junit.jupiter.Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final org.testcontainers.postgresql.PostgreSQLContainer postgres =
            new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18.6");
    @LocalServerPort
    private int port;

    @Test
    void startsHttpServerWithoutClaimingDataReadiness() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());

            // 초기 데이터 처리를 비활성화했으므로 준비 완료를 선언하지 않는다.
            assertEquals(503, response.statusCode());
        }
    }
}
