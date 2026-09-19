package com.doroddi.courseregistration.subject;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.initial-data.enabled=false")
@Import(SubjectRepositoryTest.DatabaseConfig.class)
@Transactional
class SubjectRepositoryTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18.6"); }
    }
    @Autowired SubjectRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"0", "00123", "123456789012345678901234567890"})
    void preservesValidSubjectCode(String code) {
        repository.saveAndFlush(new Subject(code));
        entityManager.clear();
        assertEquals(code, repository.findById(code).orElseThrow().getSubjectCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "12A", "-1", "1.0", "１２３", "1234567890123456789012345678901"})
    void rejectsInvalidSubjectCode(String code) {
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(new Subject(code)));
    }

    @Test
    void databaseRejectsNullCode() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO subject (subject_code) VALUES (?)", (Object) null));
    }

    @Test
    void databaseRejectsDuplicateCode() {
        repository.saveAndFlush(new Subject("00123"));
        // 직접 부여한 ID의 save는 merge가 될 수 있어 중복 INSERT를 별도로 검증한다.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO subject (subject_code) VALUES (?)", "00123"));
    }
}
