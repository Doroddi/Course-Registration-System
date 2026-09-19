package com.doroddi.courseregistration.department;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.initial-data.enabled=false")
@Import(DepartmentRepositoryTest.DatabaseConfig.class)
@Transactional
class DepartmentRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18.6");
        }
    }

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void savesAndFindsDepartment() {
        Department computerScience = new Department("컴퓨터공학부", (short) 10);

        Department saved =  departmentRepository.save(computerScience);

        assertNotNull(saved.getId());

        entityManager.flush();
        entityManager.clear();

        Department found = departmentRepository.findById(saved.getId()).orElseThrow();

        assertEquals("컴퓨터공학부", found.getName());
        assertEquals((short) 10, found.getCode());
    }

    @Test
    void rejectsDuplicateDepartmentName() {
        Department computerScience = new Department("컴퓨터공학부", (short) 10);

        departmentRepository.saveAndFlush(computerScience);

        Department computerScience2 = new Department("컴퓨터공학부", (short) 11);

        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(computerScience2);
        });
    }

    @Test
    void rejectsNullDepartmentName() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department(null, (short) 10));
        });
    }

    @Test
    void rejectsEmptyDepartmentName() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department("", (short) 10));
        });
    }

    @Test
    void rejectsWhitespaceOnlyDepartmentName() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department("   ", (short) 10));
        });
    }

    @Test
    void savesAndFindsDepartmentWithMaximumLengthName() {
        String name = "가".repeat(100);
        Department saved = departmentRepository.saveAndFlush(new Department(name, (short) 10));

        assertNotNull(saved.getId());

        // 영속성 컨텍스트의 객체 대신 DB에서 읽은 이름을 검증한다.
        entityManager.clear();
        Department found = departmentRepository.findById(saved.getId()).orElseThrow();

        assertEquals(name, found.getName());
    }

    @Test
    void rejectsDepartmentNameExceedingMaximumLength() {
        String name = "가".repeat(101);

        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department(name, (short) 10));
        });
    }

    @ParameterizedTest
    @ValueSource(shorts = {10, 99})
    void acceptsDepartmentCodeBoundaries(short code) {
        var saved = departmentRepository.saveAndFlush(new Department("경계 학과", code));
        entityManager.clear();
        assertEquals(code, departmentRepository.findById(saved.getId()).orElseThrow().getCode());
    }

    @ParameterizedTest
    @ValueSource(shorts = {9, 100})
    void rejectsDepartmentCodeOutsideRange(short code) {
        assertThrows(DataIntegrityViolationException.class,
                () -> departmentRepository.saveAndFlush(new Department("범위 학과", code)));
    }

    @Test
    void rejectsDuplicateCodeWithDifferentNames() {
        departmentRepository.saveAndFlush(new Department("컴퓨터공학과", (short) 10));
        assertThrows(DataIntegrityViolationException.class,
                () -> departmentRepository.saveAndFlush(new Department("수학과", (short) 10)));
    }

    @Test
    void rejectsNullDepartmentCode() {
        assertThrows(DataIntegrityViolationException.class,
                () -> departmentRepository.saveAndFlush(new Department("코드 없는 학과", null)));
    }
}
