package com.doroddi.courseregistration.enrollment;

import com.doroddi.courseregistration.courseOffering.*;
import com.doroddi.courseregistration.department.*;
import com.doroddi.courseregistration.student.*;
import com.doroddi.courseregistration.subject.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EnrollmentRepositoryTest.DatabaseConfig.class)
@Transactional
class EnrollmentRepositoryTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18.6"); }
    }
    @Autowired EnrollmentRepository enrollments;
    @Autowired StudentRepository students;
    @Autowired CourseOfferingRepository offerings;
    @Autowired DepartmentRepository departments;
    @Autowired SubjectRepository subjects;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private Student student;
    private CourseOffering offering;
    private Department department;
    private Subject subject;

    @BeforeEach
    void prepareParents() {
        department = departments.saveAndFlush(new Department("컴퓨터공학부"));
        student = students.saveAndFlush(new Student(202600001, "김학생", (short) 1, department, "test-only-hash"));
        subject = subjects.saveAndFlush(new Subject("001"));
        offering = offerings.saveAndFlush(new CourseOffering(subject, 2026, (short) 1,
                "001", "자료구조", (short) 3, 30, department));
    }

    @Test
    void savesAndFindsEnrollmentWithGeneratedIdAndLazyParents() {
        Enrollment saved = enrollments.saveAndFlush(new Enrollment(student, offering));
        assertNotNull(saved.getEnrollmentId());
        assertTrue(saved.getEnrollmentId() > 0);
        entityManager.clear();
        Enrollment found = enrollments.findById(saved.getEnrollmentId()).orElseThrow();
        var unit = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertFalse(unit.isLoaded(found, "student"));
        assertFalse(unit.isLoaded(found, "courseOffering"));
        assertEquals(student.getStudentNumber(), found.getStudent().getStudentNumber());
        assertEquals("김학생", found.getStudent().getName());
        assertEquals(offering.getId(), found.getCourseOffering().getId());
        assertEquals("자료구조", found.getCourseOffering().getName());
    }

    @Test
    void rejectsDuplicateStudentAndOffering() {
        enrollments.saveAndFlush(new Enrollment(student, offering));
        entityManager.clear();
        Student studentRef = students.getReferenceById(student.getStudentNumber());
        CourseOffering offeringRef = offerings.getReferenceById(offering.getId());
        assertThrows(DataIntegrityViolationException.class, () ->
                enrollments.saveAndFlush(new Enrollment(studentRef, offeringRef)));
    }

    @Test
    void allowsDifferentStudentsInSameOffering() {
        Student second = students.saveAndFlush(new Student(202600002, "박학생", (short) 2, department, "test-only-hash"));
        Enrollment firstEnrollment = enrollments.saveAndFlush(new Enrollment(student, offering));
        Enrollment secondEnrollment = enrollments.saveAndFlush(new Enrollment(second, offering));
        entityManager.clear();
        assertNotEquals(firstEnrollment.getEnrollmentId(), secondEnrollment.getEnrollmentId());
        assertEquals(student.getStudentNumber(), enrollments.findById(firstEnrollment.getEnrollmentId())
                .orElseThrow().getStudent().getStudentNumber());
        assertEquals(second.getStudentNumber(), enrollments.findById(secondEnrollment.getEnrollmentId())
                .orElseThrow().getStudent().getStudentNumber());
        assertEquals(2L, enrollments.count());
    }

    @Test
    void allowsStudentInDifferentOfferings() {
        Subject otherSubject = subjects.saveAndFlush(new Subject("002"));
        CourseOffering second = offerings.saveAndFlush(new CourseOffering(otherSubject, 2026, (short) 1,
                "002", "알고리즘", (short) 3, 30, department));
        Enrollment firstEnrollment = enrollments.saveAndFlush(new Enrollment(student, offering));
        Enrollment secondEnrollment = enrollments.saveAndFlush(new Enrollment(student, second));
        entityManager.clear();
        assertEquals(offering.getId(), enrollments.findById(firstEnrollment.getEnrollmentId())
                .orElseThrow().getCourseOffering().getId());
        assertEquals(second.getId(), enrollments.findById(secondEnrollment.getEnrollmentId())
                .orElseThrow().getCourseOffering().getId());
        assertEquals(2L, enrollments.count());
    }

    @Test
    void cancelThenReenrollGeneratesNewIdAndPreservesParents() {
        Long oldId = enrollments.saveAndFlush(new Enrollment(student, offering)).getEnrollmentId();
        entityManager.clear();
        enrollments.deleteById(oldId);
        enrollments.flush();
        entityManager.clear();
        assertTrue(enrollments.findById(oldId).isEmpty());
        assertEquals(0L, enrollments.count());
        Student existingStudent = students.findById(student.getStudentNumber()).orElseThrow();
        CourseOffering existingOffering = offerings.findById(offering.getId()).orElseThrow();
        Long newId = enrollments.saveAndFlush(new Enrollment(existingStudent, existingOffering)).getEnrollmentId();
        // 시퀀스의 빈 번호는 허용하므로 +1이 아닌 증가 여부만 검증한다.
        assertTrue(newId > oldId);
        entityManager.clear();
        assertTrue(enrollments.findById(oldId).isEmpty());
        assertTrue(enrollments.findById(newId).isPresent());
        assertEquals(1L, enrollments.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"student", "offering"})
    void rejectsMissingRequiredParent(String parent) {
        Enrollment missing = new Enrollment(parent.equals("student") ? null : student,
                parent.equals("offering") ? null : offering);
        assertThrows(DataIntegrityViolationException.class, () -> enrollments.saveAndFlush(missing));
    }

    @ParameterizedTest
    @ValueSource(strings = {"student", "offering"})
    void rejectsReferenceToNonexistentParent(String parent) {
        Student studentRef = parent.equals("student") ? students.getReferenceById(202699999) : student;
        CourseOffering offeringRef = parent.equals("offering") ? offerings.getReferenceById(-1L) : offering;
        assertThrows(DataIntegrityViolationException.class, () ->
                enrollments.saveAndFlush(new Enrollment(studentRef, offeringRef)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"student", "offering"})
    void rejectsDeletingEnrolledParent(String parent) {
        enrollments.saveAndFlush(new Enrollment(student, offering));
        entityManager.clear();
        assertThrows(DataIntegrityViolationException.class, () -> {
            if (parent.equals("student")) {
                students.deleteById(student.getStudentNumber());
                students.flush();
            } else {
                offerings.deleteById(offering.getId());
                offerings.flush();
            }
        });
    }

    @Test
    void databaseRejectsExplicitGeneratedId() {
        // PostgreSQL의 GENERATED ALWAYS 위반 SQLSTATE를 직접 확인한다.
        var failure = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("""
                INSERT INTO enrollment (enrollment_id, student_number, offering_id)
                VALUES (?, ?, ?)
                """, 999999L, student.getStudentNumber(), offering.getId()));
        SQLException exception = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals("428C9", exception.getSQLState());
    }

    @Test
    void usesIdentityAlwaysWithCacheOneAndOfferingIndex() {
        assertEquals("ALWAYS", jdbc.queryForObject("""
                SELECT identity_generation FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'enrollment'
                AND column_name = 'enrollment_id'
                """, String.class));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT seqcache FROM pg_sequence
                WHERE seqrelid = pg_get_serial_sequence('enrollment', 'enrollment_id')::regclass
                """, Long.class));
        String index = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'
                AND tablename = 'enrollment' AND indexname = 'idx_enrollment_offering_id'
                """, String.class);
        assertNotNull(index);
        assertTrue(index.contains("(offering_id)"));
    }
}
