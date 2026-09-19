package com.doroddi.courseregistration.professor;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.department.DepartmentRepository;
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

@SpringBootTest(properties = "app.initial-data.enabled=false")
@Import(ProfessorRepositoryTest.DatabaseConfig.class)
@Transactional
class ProfessorRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18.6");
        }
    }

    @Autowired
    ProfessorRepository professorRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void savesAndFindsProfessorWithDepartment() {
        Department department = departmentRepository.saveAndFlush(new Department("컴퓨터공학부", (short) 10));
        Professor saved = professorRepository.saveAndFlush(new Professor("김교수", department));
        assertNotNull(saved.getId());

        // DB에서 다시 읽어 FK 매핑과 학과의 지연 로딩을 확인한다.
        entityManager.clear();
        Professor found = professorRepository.findById(saved.getId()).orElseThrow();
        assertEquals("김교수", found.getName());
        assertFalse(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(found, "department"));
        assertEquals(department.getId(), found.getDepartment().getId());
        assertEquals("컴퓨터공학부", found.getDepartment().getName());
    }

    @Test
    void allowsProfessorsWithSameNameInSameDepartment() {
        Department department = departmentRepository.saveAndFlush(new Department("컴퓨터공학부", (short) 10));
        Professor first = professorRepository.saveAndFlush(new Professor("김교수", department));
        Professor second = professorRepository.saveAndFlush(new Professor("김교수", department));
        assertNotEquals(first.getId(), second.getId());

        entityManager.clear();
        Professor foundFirst = professorRepository.findById(first.getId()).orElseThrow();
        Professor foundSecond = professorRepository.findById(second.getId()).orElseThrow();
        assertEquals("김교수", foundFirst.getName());
        assertEquals("김교수", foundSecond.getName());
        assertEquals(department.getId(), foundFirst.getDepartment().getId());
        assertEquals(department.getId(), foundSecond.getDepartment().getId());
    }

    @Test
    void rejectsProfessorWithoutDepartment() {
        assertThrows(DataIntegrityViolationException.class, () ->
                professorRepository.saveAndFlush(new Professor("김교수", null)));
    }

    @Test
    void rejectsProfessorReferencingMissingDepartment() {
        // 존재하지 않는 학과를 조회하지 않고 FK 값으로 전달하여 DB 제약을 확인한다.
        Department missing = departmentRepository.getReferenceById(-1L);
        assertThrows(DataIntegrityViolationException.class, () ->
                professorRepository.saveAndFlush(new Professor("김교수", missing)));
    }

    @Test
    void rejectsDeletingDepartmentReferencedByProfessor() {
        Department department = departmentRepository.saveAndFlush(new Department("컴퓨터공학부", (short) 10));
        professorRepository.saveAndFlush(new Professor("김교수", department));
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.deleteById(department.getId());
            departmentRepository.flush();
        });
    }

    @Test
    void rejectsWhitespaceOnlyProfessorName() {
        Department department = departmentRepository.saveAndFlush(new Department("컴퓨터공학부", (short) 10));
        assertThrows(DataIntegrityViolationException.class, () ->
                professorRepository.saveAndFlush(new Professor("   ", department)));
    }
}
