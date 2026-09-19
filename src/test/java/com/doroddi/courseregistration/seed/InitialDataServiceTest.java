package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.professor.Professor;
import com.doroddi.courseregistration.teachingAssignment.TeachingAssignmentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.doroddi.courseregistration.professor.ProfessorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {"INITIAL_STUDENT_PASSWORD=seed-integration-test-only", "app.initial-data.enabled=false"})
@Import(InitialDataServiceTest.DatabaseConfig.class)
class InitialDataServiceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18.6");
        }
    }

    @Autowired
    InitialDataService service;
    @Autowired
    jakarta.persistence.EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoSpyBean
    ProfessorRepository professors;
    @MockitoSpyBean
    TeachingAssignmentRepository assignments;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearIsolatedTestData() {
        jdbc.update("DELETE FROM enrollment");
        jdbc.update("DELETE FROM teaching_assignment");
        jdbc.update("DELETE FROM class_meeting");
        jdbc.update("DELETE FROM course_offering");
        jdbc.update("DELETE FROM subject");
        jdbc.update("DELETE FROM student");
        jdbc.update("DELETE FROM professor");
        jdbc.update("DELETE FROM department");
    }

    // 테스트 트랜잭션을 사용하지 않아 서비스가 실제로 커밋한 데이터를 조회한다.
    @Test
    void commitsCompleteInitialDatasetWithValidRelationships() {
        service.createInitialData();

        assertEquals(10, count("department"));
        assertEquals(100, count("professor"));
        assertEquals(10_000, count("student"));
        assertEquals(250, count("subject"));
        assertEquals(500, count("course_offering"));
        assertEquals(500, count("class_meeting"));
        assertEquals(500, count("teaching_assignment"));
        assertEquals(0, count("enrollment"));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM course_offering o
                WHERE NOT EXISTS (SELECT 1 FROM class_meeting m WHERE m.offering_id = o.offering_id)
                OR NOT EXISTS (SELECT 1 FROM teaching_assignment t WHERE t.offering_id = o.offering_id)
                """, Integer.class));
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM student ORDER BY student_number LIMIT 1", String.class);
        assertTrue(passwordEncoder.matches("seed-integration-test-only", hash));
        assertEquals(10, jdbc.queryForObject(
                "SELECT count(DISTINCT department_code) FROM department", Integer.class));
        assertEquals(100, jdbc.queryForObject("""
                SELECT count(*) FROM professor p
                JOIN department d ON d.department_id = p.department_id
                WHERE d.department_code BETWEEN 10 AND 99
                """, Integer.class));
    }

    @Test
    void rollsBackDepartmentsAndPartiallySavedProfessorsWhenGenerationFails() {
        doAnswer(invocation -> {
            Iterable<Professor> generated = invocation.getArgument(0);
            professors.saveAndFlush(generated.iterator().next());
            assertEquals(10, count("department"));
            assertEquals(1, count("professor"));
            // 실제 INSERT 후 예외를 주입해 서비스 트랜잭션의 롤백 범위를 확인한다.
            throw new IllegalStateException("테스트용 초기 데이터 저장 실패");
        }).when(professors).saveAll(any());

        assertThrows(IllegalStateException.class, service::createInitialData);
        assertEquals(0, count("department"));
        assertEquals(0, count("professor"));
        assertEquals(0, count("student"));
    }

    @Test
    void rollsBackEntireDatasetWhenLastStageFails() {
        doAnswer(invocation -> {
            entityManager.flush();
            // persist로 등록한 모든 엔티티가 실제 INSERT된 뒤 실패를 주입한다.
            assertEquals(10_000, count("student"));
            assertEquals(500, count("course_offering"));
            assertEquals(500, count("class_meeting"));
            assertEquals(500, count("teaching_assignment"));
            throw new IllegalStateException("테스트용 마지막 저장 단계 실패");
        }).when(assignments).flush();

        assertThrows(IllegalStateException.class, service::createInitialData);
        for (String table : new String[]{"department", "professor", "student", "subject",
                "course_offering", "class_meeting", "teaching_assignment", "enrollment"}) {
            assertEquals(0, count(table), table + "에 실패한 초기 데이터가 남아 있음");
        }
    }
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    InitialDataValidator validator;

    @Test
    void validatesRelationshipsAndExistingEnrollmentsWithoutChangingData() {
        service.createInitialData();
        long offering = jdbc.queryForObject("SELECT min(offering_id) FROM course_offering", Long.class);
        int student = jdbc.queryForObject("SELECT min(student_number) FROM student", Integer.class);

        inRolledBackTransaction(() -> {
            jdbc.update("DELETE FROM class_meeting WHERE offering_id = ?", offering);
            assertInvalid("수업 시간이 없는 강좌");
        });
        inRolledBackTransaction(() -> {
            jdbc.update("DELETE FROM teaching_assignment WHERE offering_id = ?", offering);
            assertInvalid("담당 교수가 없는 강좌");
        });
        inRolledBackTransaction(() -> {
            int capacity = jdbc.queryForObject("SELECT capacity FROM course_offering WHERE offering_id = ?", Integer.class, offering);
            jdbc.update("INSERT INTO enrollment(student_number, offering_id) SELECT student_number, ? FROM student ORDER BY student_number LIMIT ?", offering, capacity);
            assertDoesNotThrow(() -> validator.validateExistingData(2026, (short) 2));
            jdbc.update("INSERT INTO enrollment(student_number, offering_id) SELECT student_number, ? FROM student ORDER BY student_number LIMIT 1 OFFSET ?", offering, capacity);
            assertInvalid("정원을 초과한 강좌");
        });
        inRolledBackTransaction(() -> {
            var ids = jdbc.queryForList("SELECT DISTINCT ON (subject_code) offering_id FROM course_offering WHERE credits = 3 ORDER BY subject_code, offering_id LIMIT 7", Long.class);
            for (int i = 0; i < 6; i++) {
                jdbc.update("UPDATE class_meeting SET day_of_week = ?, starts_at = '09:00', ends_at = '10:00' WHERE offering_id = ?", i + 1, ids.get(i));
                jdbc.update("INSERT INTO enrollment(student_number, offering_id) VALUES (?, ?)", student, ids.get(i));
            }
            assertDoesNotThrow(() -> validator.validateExistingData(2026, (short) 2));
            jdbc.update("INSERT INTO enrollment(student_number, offering_id) VALUES (?, ?)", student, ids.get(6));
            assertInvalid("18학점을 초과한 학생");
        });
        inRolledBackTransaction(() -> {
            jdbc.update("""
                    INSERT INTO enrollment(student_number, offering_id)
                    SELECT ?, offering_id FROM course_offering
                    WHERE subject_code = (SELECT subject_code FROM course_offering WHERE offering_id = ?)
                    """, student, offering);
            assertInvalid("동일 과목 중복 신청");
        });
        inRolledBackTransaction(() -> {
            var ids = jdbc.queryForList("SELECT DISTINCT ON (subject_code) offering_id FROM course_offering ORDER BY subject_code, offering_id LIMIT 2", Long.class);
            jdbc.update("UPDATE class_meeting SET day_of_week = 1, starts_at = '09:00', ends_at = '10:00' WHERE offering_id = ?", ids.get(0));
            jdbc.update("UPDATE class_meeting SET day_of_week = 1, starts_at = '10:00', ends_at = '11:00' WHERE offering_id = ?", ids.get(1));
            for (long id : ids) {
                jdbc.update("INSERT INTO enrollment(student_number, offering_id) VALUES (?, ?)", student, id);
            }
            assertDoesNotThrow(() -> validator.validateExistingData(2026, (short) 2));
            assertEquals(2, count("enrollment"));
            jdbc.update("UPDATE class_meeting SET starts_at = '09:59' WHERE offering_id = ?", ids.get(1));
            assertInvalid("시간표 충돌");
        });
        inRolledBackTransaction(() -> {
            // 다른 학기의 미완성 강좌는 신청 대상 학기의 검증에 섞이지 않는다.
            jdbc.update("""
                    INSERT INTO course_offering(subject_code, academic_year, term, offering_code, name, credits, capacity, department_id)
                    SELECT subject_code, 2025, 2, '9999999', name, credits, capacity, department_id
                    FROM course_offering WHERE offering_id = ?
                    """, offering);
            assertDoesNotThrow(() -> validator.validateExistingData(2026, (short) 2));
        });
        assertEquals(0, count("enrollment"));
        assertEquals(500, count("course_offering"));
        assertDoesNotThrow(() -> validator.validateExistingData(2026, (short) 2));
    }

    @Test
    void rollsBackGeneratedDataWhenFinalValidationFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("테스트용 최종 검증 실패"))
                .when(validator).validateExistingData(2026, (short) 2);
        assertThrows(IllegalStateException.class, service::createInitialData);
        for (String table : new String[]{"department", "professor", "student", "subject",
                "course_offering", "class_meeting", "teaching_assignment", "enrollment"}) {
            assertEquals(0, count(table));
        }
    }

    private void assertInvalid(String cause) {
        var error = assertThrows(IllegalStateException.class,
                () -> validator.validateExistingData(2026, (short) 2));
        assertTrue(error.getMessage().contains(cause), error.getMessage());
    }

    private void inRolledBackTransaction(Runnable scenario) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    try {
                        scenario.run();
                    } finally {
                        status.setRollbackOnly();
                    }
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {202010100, 202610100})
    void rollsBackAllDataWhenStudentBatchHitsDuplicateKey(int duplicateNumber) {
        doAnswer(invocation -> {
            Iterable<Professor> generated = invocation.getArgument(0);
            var saved = new java.util.ArrayList<Professor>();
            for (Professor professor : generated) {
                saved.add(professors.save(professor));
            }
            professors.flush();
            // 같은 트랜잭션에 충돌 행을 넣어 앞쪽/뒤쪽 학생 배치의 실제 PK 위반을 유발한다.
            jdbc.update("""
                    INSERT INTO student(student_number, name, grade, department_id, password_hash)
                    SELECT ?, '충돌 테스트', 1, min(department_id), 'test-only-hash' FROM department
                    """, duplicateNumber);
            return saved;
        }).when(professors).saveAll(any());

        var error = assertThrows(RuntimeException.class, service::createInitialData);
        Throwable cause = error;
        while (cause != null && !(cause instanceof java.sql.SQLException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "DB 제약 위반이 발생해야 함");
        assertEquals("23505", ((java.sql.SQLException) cause).getSQLState());
        for (String table : new String[]{"department", "professor", "student", "subject",
                "course_offering", "class_meeting", "teaching_assignment", "enrollment"}) {
            assertEquals(0, count(table), table);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
