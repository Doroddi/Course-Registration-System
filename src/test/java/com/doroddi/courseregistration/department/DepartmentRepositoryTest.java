package com.doroddi.courseregistration.department;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
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
        Department computerScience = new Department("컴퓨터공학부");

        Department saved =  departmentRepository.save(computerScience);

        assertNotNull(saved.getId());

        entityManager.flush();
        entityManager.clear();

        Department found = departmentRepository.findById(saved.getId()).orElseThrow();

        assertEquals("컴퓨터공학부", found.getName());
    }

    @Test
    void rejectsDuplicateDepartmentName() {
        Department computerScience = new Department("컴퓨터공학부");

        departmentRepository.saveAndFlush(computerScience);

        Department computerScience2 = new Department("컴퓨터공학부");

        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(computerScience2);
        });
    }

    @Test
    void rejectsNullDepartmentName() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department(null));
        });
    }

    @Test
    void rejectsEmptyDepartmentName() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department(""));
        });
    }

    @Test
    void rejectsWhitespaceOnlyDepartmentName() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.saveAndFlush(new Department("   "));
        });
    }

    @Test
    void savesAndFindsDepartmentWithMaximumLengthName() {
        String name = "가".repeat(100);
        Department saved = departmentRepository.saveAndFlush(new Department(name));

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
            departmentRepository.saveAndFlush(new Department(name));
        });
    }
}