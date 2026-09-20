package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.department.DepartmentRepository;
import com.doroddi.courseregistration.professor.ProfessorRepository;
import com.doroddi.courseregistration.subject.SubjectRepository;
import com.doroddi.courseregistration.courseOffering.CourseOfferingRepository;
import com.doroddi.courseregistration.classMeeting.ClassMeetingRepository;
import com.doroddi.courseregistration.teachingAssignment.TeachingAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.initial-data.enabled=false")
@Import(CourseSeedFactoryTest.DatabaseConfig.class)
@Transactional
class CourseSeedFactoryTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18.6"); }
    }
    @Autowired DepartmentRepository departments;
    @Autowired ProfessorRepository professors;
    @Autowired SubjectRepository subjects;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ClassMeetingRepository meetings;
    @Autowired TeachingAssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;
    private final AcademicSeedFactory academic = new AcademicSeedFactory();
    private final CourseSeedFactory factory = new CourseSeedFactory();

    @Test
    void savesCompleteCourseGraphAndKeepsEnrollmentEmpty() {
        var savedDepartments = departments.saveAll(academic.createDepartments());
        var savedProfessors = professors.saveAll(academic.createProfessors(savedDepartments, 100));
        var savedSubjects = subjects.saveAll(factory.createSubjects(savedDepartments));
        var savedOfferings = offerings.saveAll(factory.createOfferings(savedDepartments, savedSubjects, 2026, (short) 2));
        meetings.saveAllAndFlush(factory.createMeetings(savedOfferings));
        assignments.saveAllAndFlush(factory.createAssignments(savedOfferings, savedProfessors));

        assertEquals(250, subjects.count());
        assertEquals(500, offerings.count());
        assertEquals(500, meetings.count());
        assertEquals(500, assignments.count());
        assertEquals(0, query("SELECT count(*) FROM enrollment"));
        assertEquals(250, query("SELECT count(*) FROM (SELECT subject_code FROM course_offering GROUP BY subject_code HAVING count(*) = 2) s"));
        assertEquals(500, query("SELECT count(*) FROM course_offering WHERE academic_year = 2026 AND term = 2 AND credits BETWEEN 1 AND 3 AND capacity IN (30,40,50,60)"));
        assertEquals(500, query("SELECT count(*) FROM class_meeting WHERE day_of_week BETWEEN 1 AND 5 AND starts_at >= TIME '09:00' AND ends_at <= TIME '18:00' AND starts_at < ends_at"));
        assertEquals(500, query("SELECT count(*) FROM (SELECT offering_id FROM teaching_assignment GROUP BY offering_id HAVING count(*) = 1) t"));
        assertEquals(10, query("SELECT count(*) FROM (SELECT department_id FROM course_offering GROUP BY department_id HAVING count(*) = 50) d"));
        // 서로 다른 과목 사이에 실제 충돌·인접 구간이 모두 있어야 신청 정책을 검증할 수 있다.
        assertTrue(query("""
                SELECT count(*) FROM class_meeting a JOIN class_meeting b
                ON a.meeting_id < b.meeting_id AND a.day_of_week = b.day_of_week
                JOIN course_offering ca ON ca.offering_id = a.offering_id
                JOIN course_offering cb ON cb.offering_id = b.offering_id
                WHERE ca.subject_code <> cb.subject_code
                AND a.starts_at < b.ends_at AND b.starts_at < a.ends_at
                """) > 0);
        assertTrue(query("""
                SELECT count(*) FROM class_meeting a JOIN class_meeting b
                ON a.day_of_week = b.day_of_week AND a.ends_at = b.starts_at
                JOIN course_offering ca ON ca.offering_id = a.offering_id
                JOIN course_offering cb ON cb.offering_id = b.offering_id
                WHERE ca.subject_code <> cb.subject_code
                """) > 0);
    }

    @Test
    void rejectsMissingSubjectsAndInvalidTerm() {
        var generatedDepartments = academic.createDepartments();
        assertThrows(IllegalArgumentException.class,
                () -> factory.createOfferings(generatedDepartments, List.of(), 2026, (short) 2));
        assertThrows(IllegalArgumentException.class,
                () -> factory.createOfferings(generatedDepartments, List.of(), 2026, (short) 3));
    }

    @Test
    void rejectsUnsavedParentsForCompositeKeys() {
        var generatedDepartments = academic.createDepartments();
        var generatedOfferings = factory.createOfferings(generatedDepartments,
                factory.createSubjects(generatedDepartments), 2026, (short) 2);
        assertThrows(IllegalArgumentException.class, () -> factory.createAssignments(
                generatedOfferings, academic.createProfessors(generatedDepartments, 100)));
    }

    private int query(String sql) { return jdbc.queryForObject(sql, Integer.class); }
}
