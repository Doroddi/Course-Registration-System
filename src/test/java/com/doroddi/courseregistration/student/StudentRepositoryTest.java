package com.doroddi.courseregistration.student;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.department.DepartmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
@Import(StudentRepositoryTest.DatabaseConfig.class)
@Transactional
class StudentRepositoryTest {
    // 인증 검증용 해시가 아닌, DB 저장·조회만 확인하는 테스트 값이다.
    private static final String HASH = "test-only-hash";

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18.6");
        }
    }

    @Autowired StudentRepository studentRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    private Department saveDepartment() {
        return departmentRepository.saveAndFlush(new Department("컴퓨터공학부", (short) 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {100000000, 202600001, 999999999})
    void savesAndFindsStudentWithAssignedNumber(int number) {
        Department department = saveDepartment();
        Student saved = studentRepository.saveAndFlush(
                new Student(number, "김학생", (short) 1, department, HASH));
        assertEquals(number, saved.getStudentNumber());
        entityManager.clear();

        Student found = studentRepository.findById(number).orElseThrow();
        assertEquals("김학생", found.getName());
        assertEquals(Short.valueOf((short) 1), found.getGrade());
        assertEquals(HASH, found.getPasswordHash());
        assertFalse(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(found, "department"));
        assertEquals(department.getId(), found.getDepartment().getId());
        assertEquals("컴퓨터공학부", found.getDepartment().getName());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 99999999, 1000000000})
    void rejectsStudentNumberOutsideNineDigits(int number) {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(number, "김학생", (short) 1, department, HASH)));
    }

    @ParameterizedTest
    @ValueSource(shorts = {1, 4})
    void acceptsGradeBoundaries(short grade) {
        Department department = saveDepartment();
        studentRepository.saveAndFlush(new Student(202600001, "김학생", grade, department, HASH));
        entityManager.clear();
        assertEquals(Short.valueOf(grade), studentRepository.findById(202600001).orElseThrow().getGrade());
    }

    @ParameterizedTest
    @ValueSource(shorts = {0, 5})
    void rejectsGradeOutsideRange(short grade) {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, "김학생", grade, department, HASH)));
    }

    @Test
    void rejectsNullGrade() {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, "김학생", null, department, HASH)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrBlankName(String name) {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, name, (short) 1, department, HASH)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrBlankPasswordHash(String hash) {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, "김학생", (short) 1, department, hash)));
    }

    @Test
    void acceptsMaximumNameAndHashLengths() {
        Department department = saveDepartment();
        String name = "가".repeat(100);
        String hash = "x".repeat(255);
        studentRepository.saveAndFlush(new Student(202600001, name, (short) 1, department, hash));
        entityManager.clear();
        Student found = studentRepository.findById(202600001).orElseThrow();
        assertEquals(name, found.getName());
        assertEquals(hash, found.getPasswordHash());
    }

    @Test
    void rejectsNameExceedingMaximumLength() {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, "가".repeat(101), (short) 1, department, HASH)));
    }

    @Test
    void rejectsHashExceedingMaximumLength() {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, "김학생", (short) 1, department, "x".repeat(256))));
    }

    @Test
    void rejectsMissingDepartment() {
        assertThrows(DataIntegrityViolationException.class, () -> studentRepository.saveAndFlush(
                new Student(202600001, "김학생", (short) 1, null, HASH)));
    }

    @Test
    void databaseRejectsMissingDepartmentReference() {
        assertThrows(DataIntegrityViolationException.class, () -> insertStudent(202600001, -1L));
    }

    @Test
    void rejectsDeletingDepartmentReferencedByStudent() {
        Department department = saveDepartment();
        studentRepository.saveAndFlush(new Student(202600001, "김학생", (short) 1, department, HASH));
        entityManager.clear();
        assertThrows(DataIntegrityViolationException.class, () -> {
            departmentRepository.deleteById(department.getId());
            departmentRepository.flush();
        });
    }

    @Test
    void databaseRejectsDuplicateStudentNumber() {
        Department department = saveDepartment();
        studentRepository.saveAndFlush(new Student(202600001, "김학생", (short) 1, department, HASH));
        // 직접 부여한 ID의 save는 merge가 될 수 있으므로 PK 중복 INSERT는 JDBC로 검증한다.
        assertThrows(DataIntegrityViolationException.class, () -> insertStudent(202600001, department.getId()));
    }

    @Test
    void databaseRejectsNullStudentNumber() {
        Department department = saveDepartment();
        assertThrows(DataIntegrityViolationException.class, () -> insertStudent(null, department.getId()));
    }

    private void insertStudent(Integer number, Long departmentId) {
        jdbcTemplate.update("""
                INSERT INTO student (student_number, name, grade, department_id, password_hash)
                VALUES (?, ?, ?, ?, ?)
                """, number, "김학생", 1, departmentId, HASH);
    }
}
