package com.doroddi.courseregistration.teachingAssignment;

import com.doroddi.courseregistration.classMeeting.*;
import com.doroddi.courseregistration.courseOffering.*;
import com.doroddi.courseregistration.department.*;
import com.doroddi.courseregistration.professor.*;
import com.doroddi.courseregistration.subject.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.initial-data.enabled=false")
@Import(CourseScheduleRepositoryTest.DatabaseConfig.class)
@Transactional
class CourseScheduleRepositoryTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18.6"); }
    }
    @Autowired ClassMeetingRepository meetings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ProfessorRepository professors;
    @Autowired DepartmentRepository departments;
    @Autowired SubjectRepository subjects;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    private CourseOffering offering;
    private Professor professor;
    private Subject subject;
    private Department department;

    @BeforeEach
    void prepareParents() {
        department = departments.saveAndFlush(new Department("컴퓨터공학부", (short) 10));
        Department other = departments.saveAndFlush(new Department("수학과", (short) 11));
        professor = professors.saveAndFlush(new Professor("김교수", other));
        subject = subjects.saveAndFlush(new Subject("001"));
        offering = offerings.saveAndFlush(new CourseOffering(subject, 2026, (short) 1,
                "001", "자료구조", (short) 3, 30, department));
    }

    @ParameterizedTest
    @CsvSource({"1,00:00,00:01", "7,23:58,23:59", "3,13:00,15:00"})
    void savesAndFindsMeeting(short day, String start, String end) {
        ClassMeeting saved = meetings.saveAndFlush(new ClassMeeting(offering, day,
                LocalTime.parse(start), LocalTime.parse(end)));
        assertNotNull(saved.getId());
        entityManager.clear();
        ClassMeeting found = meetings.findById(saved.getId()).orElseThrow();
        assertEquals(Short.valueOf(day), found.getDayOfWeek());
        assertEquals(LocalTime.parse(start), found.getStartsAt());
        assertEquals(LocalTime.parse(end), found.getEndsAt());
        assertFalse(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(found, "courseOffering"));
        assertEquals(offering.getId(), found.getCourseOffering().getId());
        assertEquals("자료구조", found.getCourseOffering().getName());
    }

    @ParameterizedTest
    @ValueSource(shorts = {0, 8})
    void rejectsInvalidWeekday(short day) {
        assertThrows(DataIntegrityViolationException.class, () -> meetings.saveAndFlush(
                new ClassMeeting(offering, day, LocalTime.of(13, 0), LocalTime.of(15, 0))));
    }

    @ParameterizedTest
    @CsvSource({"13:00,13:00", "15:00,13:00", "23:00,01:00",
            "13:00:01,15:00", "13:00,15:00:01", "13:00:00.5,15:00", "13:00,15:00:00.5"})
    void rejectsInvalidTimeRangeOrPrecision(String start, String end) {
        assertThrows(DataIntegrityViolationException.class, () -> meetings.saveAndFlush(
                new ClassMeeting(offering, (short) 1, LocalTime.parse(start), LocalTime.parse(end))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"offering", "day", "start", "end"})
    void rejectsMissingMeetingValue(String field) {
        ClassMeeting missing = new ClassMeeting(field.equals("offering") ? null : offering,
                field.equals("day") ? null : (short) 1,
                field.equals("start") ? null : LocalTime.of(13, 0),
                field.equals("end") ? null : LocalTime.of(15, 0));
        assertThrows(DataIntegrityViolationException.class, () -> meetings.saveAndFlush(missing));
    }

    @Test
    void rejectsMeetingReferencingMissingOffering() {
        CourseOffering missing = offerings.getReferenceById(-1L);
        assertThrows(DataIntegrityViolationException.class, () -> meetings.saveAndFlush(
                new ClassMeeting(missing, (short) 1, LocalTime.of(13, 0), LocalTime.of(15, 0))));
    }

    @Test
    void rejectsDeletingOfferingWithMeetings() {
        meetings.saveAndFlush(new ClassMeeting(offering, (short) 1, LocalTime.of(13, 0), LocalTime.of(15, 0)));
        entityManager.clear();
        assertThrows(DataIntegrityViolationException.class, () -> {
            offerings.deleteById(offering.getId());
            offerings.flush();
        });
    }

    @Test
    void allowsMultipleMeetingsAndAdjacentTimes() {
        var first = meetings.saveAndFlush(new ClassMeeting(offering, (short) 1, LocalTime.of(13, 0), LocalTime.of(14, 0)));
        var next = meetings.saveAndFlush(new ClassMeeting(offering, (short) 1, LocalTime.of(14, 0), LocalTime.of(15, 0)));
        var anotherDay = meetings.saveAndFlush(new ClassMeeting(offering, (short) 3, LocalTime.of(13, 0), LocalTime.of(14, 0)));
        entityManager.clear();
        assertEquals(meetings.findById(first.getId()).orElseThrow().getEndsAt(),
                meetings.findById(next.getId()).orElseThrow().getStartsAt());
        assertEquals(Short.valueOf((short) 3), meetings.findById(anotherDay.getId()).orElseThrow().getDayOfWeek());
        assertEquals(3L, meetings.count());
    }

    @Test
    void createsOfferingIndexForMeetings() {
        String definition = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'class_meeting'
                AND indexname = 'idx_class_meeting_offering_id'
                """, String.class);
        assertNotNull(definition);
        assertTrue(definition.contains("(offering_id)"));
    }

    @Test
    void savesAssignmentAcrossDepartmentsAndFindsByCompositeId() {
        var saved = assignments.saveAndFlush(new TeachingAssignment(professor, offering));
        var id = new TeachingAssignmentId(professor.getId(), offering.getId());
        assertEquals(id, entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(saved));
        entityManager.clear();
        var found = assignments.findById(id).orElseThrow();
        var unit = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertFalse(unit.isLoaded(found, "professor"));
        assertFalse(unit.isLoaded(found, "courseOffering"));
        // JPA 연관 경로를 통해 교수·강좌와 각각의 학과 매핑을 검증한다.
        Object[] row = entityManager.createQuery("""
                select a.professor.name, a.professor.department.name,
                       a.courseOffering.name, a.courseOffering.department.name
                from TeachingAssignment a where a.id = :id
                """, Object[].class).setParameter("id", id).getSingleResult();
        assertArrayEquals(new Object[]{"김교수", "수학과", "자료구조", "컴퓨터공학부"}, row);
    }

    @Test
    void allowsTeamTeachingAndProfessorTeachingMultipleOfferings() {
        Professor second = professors.saveAndFlush(new Professor("박교수", department));
        CourseOffering another = offerings.saveAndFlush(new CourseOffering(subject, 2026, (short) 1,
                "002", "자료구조", (short) 3, 30, department));
        assignments.saveAndFlush(new TeachingAssignment(professor, offering));
        assignments.saveAndFlush(new TeachingAssignment(second, offering));
        assignments.saveAndFlush(new TeachingAssignment(professor, another));
        entityManager.clear();
        assertTrue(assignments.findById(new TeachingAssignmentId(professor.getId(), offering.getId())).isPresent());
        assertTrue(assignments.findById(new TeachingAssignmentId(second.getId(), offering.getId())).isPresent());
        assertTrue(assignments.findById(new TeachingAssignmentId(professor.getId(), another.getId())).isPresent());
        assertEquals(3L, assignments.count());
    }

    @Test
    void databaseRejectsDuplicateAssignment() {
        assignments.saveAndFlush(new TeachingAssignment(professor, offering));
        // 같은 복합 키의 save는 merge가 될 수 있으므로 중복 INSERT는 JDBC로 검증한다.
        assertThrows(DataIntegrityViolationException.class, () -> insertAssignment(professor.getId(), offering.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"professor", "offering"})
    void databaseRejectsMissingAssignmentParent(String parent) {
        assertThrows(DataIntegrityViolationException.class, () -> insertAssignment(
                parent.equals("professor") ? -1L : professor.getId(),
                parent.equals("offering") ? -1L : offering.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"professor", "offering"})
    void databaseRejectsNullAssignmentParent(String parent) {
        assertThrows(DataIntegrityViolationException.class, () -> insertAssignment(
                parent.equals("professor") ? null : professor.getId(),
                parent.equals("offering") ? null : offering.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"professor", "offering"})
    void rejectsDeletingAssignedParent(String parent) {
        assignments.saveAndFlush(new TeachingAssignment(professor, offering));
        entityManager.clear();
        assertThrows(DataIntegrityViolationException.class, () -> {
            if (parent.equals("professor")) {
                professors.deleteById(professor.getId());
                professors.flush();
            } else {
                offerings.deleteById(offering.getId());
                offerings.flush();
            }
        });
    }

    @Test
    void deletingAssignmentPreservesParents() {
        assignments.saveAndFlush(new TeachingAssignment(professor, offering));
        var id = new TeachingAssignmentId(professor.getId(), offering.getId());
        entityManager.clear();
        assignments.deleteById(id);
        assignments.flush();
        entityManager.clear();
        assertTrue(assignments.findById(id).isEmpty());
        assertTrue(professors.findById(professor.getId()).isPresent());
        assertTrue(offerings.findById(offering.getId()).isPresent());
    }

    @Test
    void compositeIdUsesBothValuesForEquality() {
        var id = new TeachingAssignmentId(1L, 2L);
        var same = new TeachingAssignmentId(1L, 2L);
        assertEquals(id, same);
        assertEquals(id.hashCode(), same.hashCode());
        assertNotEquals(id, new TeachingAssignmentId(2L, 2L));
        assertNotEquals(id, new TeachingAssignmentId(1L, 3L));
        assertNotEquals(id, null);
    }

    @Test
    void databaseRejectsEndAtTwentyFourHours() {
        // LocalTimeでは24:00を作れないため、SQLで直接入力してDB制約を検証する。
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO class_meeting (offering_id, day_of_week, starts_at, ends_at)
                VALUES (?, 1, TIME '23:00:00', TIME '24:00:00')
                """, offering.getId()));
    }
    private void insertAssignment(Long professorId, Long offeringId) {
        jdbc.update("INSERT INTO teaching_assignment (professor_id, offering_id) VALUES (?, ?)",
                professorId, offeringId);
    }
}
