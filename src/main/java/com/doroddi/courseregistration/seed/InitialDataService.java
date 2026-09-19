package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.config.EnrollmentTermProperties;

import com.doroddi.courseregistration.classMeeting.ClassMeetingRepository;
import com.doroddi.courseregistration.courseOffering.CourseOfferingRepository;
import com.doroddi.courseregistration.department.DepartmentRepository;
import com.doroddi.courseregistration.professor.ProfessorRepository;
import com.doroddi.courseregistration.teachingAssignment.TeachingAssignmentRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitialDataService {

    private final DepartmentRepository departmentRepository;
    private final ProfessorRepository professorRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ClassMeetingRepository classMeetingRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SeedPassword seedPassword;
    private final InitialDataValidator initialDataValidator;
    private final EnrollmentTermProperties enrollmentTerm;

    private final EntityManager entityManager;

    private final AcademicSeedFactory academicFactory = new AcademicSeedFactory();
    private final CourseSeedFactory courseFactory = new CourseSeedFactory();

    @Transactional
    public void createInitialData() {
        Session session = entityManager.unwrap(Session.class);
        Integer previousBatchSize = session.getJdbcBatchSize();
        // 최초 생성에서만 묶어 전송하고, 호출자가 공유한 세션의 설정은 복원한다.
        session.setJdbcBatchSize(50);
        try {
            persistInitialData();
        } finally {
            session.setJdbcBatchSize(previousBatchSize);
        }
    }

    private void persistInitialData() {
        StopWatch timings = new StopWatch();
        timings.start("password-hash");
        String passwordHash = seedPassword.createHash();

        timings.stop();
        timings.start("departments");
        // 학과 생성
        var savedDepartments = departmentRepository.saveAll(
            academicFactory.createDepartments());

        timings.stop();
        timings.start("professors");
        // 교수 생성
        var savedProfessors = professorRepository.saveAll(
            academicFactory.createProfessors(savedDepartments, 100));

        timings.stop();
        timings.start("students-persist");
        // 학생 생성
        var students = academicFactory.createStudents(savedDepartments, 10000, passwordHash);

        for(var student : students) {
            entityManager.persist(student);
        }

        timings.stop();
        timings.start("subjects-persist");
        // 과목 생성
        var subjects = courseFactory.createSubjects(savedDepartments);

        for(var subject : subjects) {
            entityManager.persist(subject);
        }

        timings.stop();
        timings.start("offerings");
        // 강좌 생성
        var savedOfferings = courseOfferingRepository.saveAll(
            courseFactory.createOfferings(savedDepartments, subjects, enrollmentTerm.academicYear(), enrollmentTerm.term())
        );

        timings.stop();
        timings.start("meetings");
        // 시간표 저장
        classMeetingRepository.saveAll(
            courseFactory.createMeetings(savedOfferings)
        );

        timings.stop();
        timings.start("assignments-persist");
        // 교수-강좌 저장
        var assignments = courseFactory.createAssignments(savedOfferings, savedProfessors);

        for(var assignment : assignments) {
            entityManager.persist(assignment);
        }

        timings.stop();
        timings.start("flush");
        // 검증 실패도 생성 트랜잭션 안에서 발생시켜 부분 데이터를 남기지 않는다.
        teachingAssignmentRepository.flush();
        timings.stop();
        timings.start("validation");
        initialDataValidator.validateExistingData(enrollmentTerm.academicYear(), enrollmentTerm.term());
        timings.stop();
        // 추가 flush 없이 실제 실행 구간을 측정한다. persist 구간과 SQL 실행 구간은 다를 수 있다.
        for (StopWatch.TaskInfo task : timings.getTaskInfo()) {
            log.info("Initial data stage: name={}, elapsedMs={}", task.getTaskName(), task.getTimeMillis());
        }
    }
}
