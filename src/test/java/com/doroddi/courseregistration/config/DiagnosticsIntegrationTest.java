package com.doroddi.courseregistration.config;

import com.doroddi.courseregistration.student.auth.JwtTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.initial-data.enabled=false", "management.server.port=0"})
@ActiveProfiles("diagnostics")
@Testcontainers
class DiagnosticsIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18.6");
    @LocalServerPort int port;
    @Value("${local.management.port}") int managementPort;
    @Autowired JwtTokenService tokens;
    private final HttpClient client=HttpClient.newHttpClient();

    @Test
    void metricsRequireJwtAndAreAvailableOnlyOnManagementPort() throws Exception {
        String token=tokens.issue(202010100);
        assertThat(get(managementPort,"/actuator/prometheus",null).statusCode()).isEqualTo(401);
        var metrics=get(managementPort,"/actuator/prometheus",token);
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("hikaricp_connections_active", "process_cpu_usage");
        assertThat(get(port,"/actuator/prometheus",token).statusCode()).isEqualTo(404);
        assertThat(get(managementPort,"/actuator/env",token).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int targetPort,String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+targetPort+path));
        if(token!=null) request.header("Authorization","Bearer "+token);
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
}
