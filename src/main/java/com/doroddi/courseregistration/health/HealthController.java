package com.doroddi.courseregistration.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final ApplicationAvailability availability;
    private final InitialDataReadiness initialData;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        // Runner 비활성화 시에도 데이터 검증을 생략한 상태를 준비 완료로 표시하지 않는다.
        boolean ready = initialData.isReady()
                && availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(ready ? "UP" : "NOT_READY"));
    }

    public record HealthResponse(String status) {}
}
