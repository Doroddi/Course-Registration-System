package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.config.EnrollmentTermProperties;
import com.doroddi.courseregistration.health.InitialDataReadiness;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.initial-data.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class InitialDataRunner implements ApplicationRunner {

    private final InitialDataService initialDataService;
    private final InitialDataValidator initialDataValidator;
    private final EnrollmentTermProperties enrollmentTerm;
    private final InitialDataReadiness readiness;

    @Override
    public void run(ApplicationArguments args) {
        long startedAt = System.nanoTime();
        boolean empty = initialDataValidator.isEmpty();
        if(empty) {
            initialDataService.createInitialData();
        } else {
            initialDataValidator.validateExistingData(enrollmentTerm.academicYear(), enrollmentTerm.term());
        }
        // 서비스 트랜잭션 커밋 또는 기존 데이터 검증이 성공한 뒤에만 준비 완료로 전환한다.
        readiness.markReady();
        log.info("Initial data ready: mode={}, elapsedMs={}",
                empty ? "created" : "validated", (System.nanoTime() - startedAt) / 1_000_000);
    }
}
