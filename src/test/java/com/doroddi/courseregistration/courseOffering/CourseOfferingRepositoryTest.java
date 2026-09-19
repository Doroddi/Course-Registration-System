package com.doroddi.courseregistration.courseOffering;

import com.doroddi.courseregistration.subject.*;
import com.doroddi.courseregistration.department.*;
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

@SpringBootTest
@Import(CourseOfferingRepositoryTest.DatabaseConfig.class)
@Transactional
class CourseOfferingRepositoryTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18.6"); }
    }
    @Autowired CourseOfferingRepository repository;
    @Autowired SubjectRepository subjects;
    @Autowired DepartmentRepository departments;
    @Autowired EntityManager entityManager;

    private Subject subject;
    private Department department;

    @org.junit.jupiter.api.BeforeEach
    void prepareParents() {
        subject = subjects.saveAndFlush(new Subject("00123"));
        department = departments.saveAndFlush(new Department("컴퓨터공학부"));
    }

    private CourseOffering offering(Integer year, Short term, String code, String name, Short credits, Integer capacity) {
        return new CourseOffering(subject, year, term, code, name, credits, capacity, department);
    }

    @ParameterizedTest
    @CsvSource({"1000,1,1,1", "9999,2,6,2147483647"})
    void savesAndFindsOfferingAtBoundaries(int year, short term, short credits, int capacity) {
        String code = "0".repeat(30);
        String name = "가".repeat(200);
        CourseOffering saved = repository.saveAndFlush(offering(year, term, code, name, credits, capacity));
        assertNotNull(saved.getId());
        entityManager.clear();
        CourseOffering found = repository.findById(saved.getId()).orElseThrow();
        assertEquals(year, found.getAcademicYear());
        assertEquals(Short.valueOf(term), found.getTerm());
        assertEquals(code, found.getOfferingCode());
        assertEquals(name, found.getName());
        assertEquals(Short.valueOf(credits), found.getCredits());
        assertEquals(capacity, found.getCapacity());
        var unit = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertFalse(unit.isLoaded(found, "subject"));
        assertFalse(unit.isLoaded(found, "department"));
        assertEquals("00123", found.getSubject().getSubjectCode());
        assertEquals(department.getId(), found.getDepartment().getId());
        assertEquals("컴퓨터공학부", found.getDepartment().getName());
    }

    @ParameterizedTest
    @CsvSource({"999,1,3,30", "10000,1,3,30", "2026,0,3,30", "2026,3,3,30",
            "2026,1,0,30", "2026,1,7,30", "2026,1,3,0", "2026,1,3,-1"})
    void rejectsNumericValuesOutsideRanges(int year, short term, short credits, int capacity) {
        assertThrows(DataIntegrityViolationException.class, () ->
                repository.saveAndFlush(offering(year, term, "1", "자료구조", credits, capacity)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "A1", "-1", "1.0", "１２３", "1234567890123456789012345678901"})
    void rejectsInvalidOfferingCode(String code) {
        assertThrows(DataIntegrityViolationException.class, () ->
                repository.saveAndFlush(offering(2026, (short) 1, code, "자료구조", (short) 3, 30)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrBlankName(String name) {
        assertThrows(DataIntegrityViolationException.class, () ->
                repository.saveAndFlush(offering(2026, (short) 1, "1", name, (short) 3, 30)));
    }

    @Test
    void rejectsNameOverMaximumLength() {
        assertThrows(DataIntegrityViolationException.class, () ->
                repository.saveAndFlush(offering(2026, (short) 1, "1", "가".repeat(201), (short) 3, 30)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"year", "term", "credits", "capacity", "subject", "department"})
    void rejectsMissingRequiredValue(String field) {
        CourseOffering missing = new CourseOffering(
                field.equals("subject") ? null : subject,
                field.equals("year") ? null : 2026,
                field.equals("term") ? null : (short) 1,
                "1", "자료구조",
                field.equals("credits") ? null : (short) 3,
                field.equals("capacity") ? null : 30,
                field.equals("department") ? null : department);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(missing));
    }

    @Test
    void rejectsDuplicateCodeInSameYearAndTermEvenForDifferentSubject() {
        repository.saveAndFlush(offering(2026, (short) 1, "001", "자료구조", (short) 3, 30));
        Subject other = subjects.saveAndFlush(new Subject("00234"));
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new CourseOffering(other, 2026, (short) 1, "001", "알고리즘", (short) 3, 30, department)));
    }

    @Test
    void allowsCodeReuseAcrossYearOrTermAndMultipleSections() {
        var first = repository.saveAndFlush(offering(2026, (short) 1, "001", "자료구조", (short) 3, 30));
        var nextTerm = repository.saveAndFlush(offering(2026, (short) 2, "001", "자료구조", (short) 3, 30));
        var nextYear = repository.saveAndFlush(offering(2027, (short) 1, "001", "자료구조", (short) 3, 30));
        var otherSection = repository.saveAndFlush(offering(2026, (short) 1, "002", "자료구조", (short) 3, 30));
        entityManager.clear();
        for (var saved : java.util.List.of(first, nextTerm, nextYear, otherSection)) {
            var found = repository.findById(saved.getId()).orElseThrow();
            assertEquals(saved.getAcademicYear(), found.getAcademicYear());
            assertEquals(saved.getTerm(), found.getTerm());
            assertEquals(saved.getOfferingCode(), found.getOfferingCode());
        }
        assertEquals(4L, repository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"subject", "department"})
    void rejectsMissingParentReference(String parent) {
        Subject refSubject = parent.equals("subject") ? subjects.getReferenceById("999") : subject;
        Department refDepartment = parent.equals("department") ? departments.getReferenceById(-1L) : department;
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new CourseOffering(refSubject, 2026, (short) 1, "1", "자료구조", (short) 3, 30, refDepartment)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"subject", "department"})
    void rejectsDeletingReferencedParent(String parent) {
        repository.saveAndFlush(offering(2026, (short) 1, "1", "자료구조", (short) 3, 30));
        entityManager.clear();
        assertThrows(DataIntegrityViolationException.class, () -> {
            if (parent.equals("subject")) {
                subjects.deleteById(subject.getSubjectCode());
                subjects.flush();
            } else {
                departments.deleteById(department.getId());
                departments.flush();
            }
        });
    }
}
