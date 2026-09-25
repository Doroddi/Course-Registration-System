package com.doroddi.courseregistration.enrollment;

import com.doroddi.courseregistration.config.EnrollmentTermProperties;
import com.doroddi.courseregistration.courseOffering.CourseOfferingRepository;
import com.doroddi.courseregistration.student.StudentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {
    private final StudentRepository students;
    private final CourseOfferingRepository offerings;
    private final EnrollmentRepository enrollments;
    private final EnrollmentTermProperties term;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void enroll(Integer studentNumber, Long offeringId) {
        var offering = offerings.findById(offeringId).orElseThrow(() -> new EnrollmentRejectedException(
                "COURSE_OFFERING_NOT_FOUND", "존재하지 않는 강좌입니다.", HttpStatus.NOT_FOUND));
        if (offering.getAcademicYear() != term.academicYear() || offering.getTerm() != term.term()) {
            throw conflict("INVALID_ENROLLMENT_TERM", "신청 대상 학기가 아닙니다.");
        }
        // 현재 트랜잭션에만 적용해 풀에서 재사용되는 연결에 대기 제한이 남지 않게 한다.
        entityManager.createNativeQuery("select set_config('lock_timeout', '3s', true)").getSingleResult();
        var student = students.findForEnrollment(studentNumber)
                .orElseThrow(() -> new IllegalStateException("인증된 학생의 신청 상태를 처리할 수 없습니다."));

        // 학생 잠금 이후 별도 SQL로 읽어 앞선 요청의 커밋을 반영한다.
        var registered = enrollments.findRegisteredCourses(studentNumber, term.academicYear(), term.term());
        if (registered.stream().anyMatch(row -> row.getOfferingId().equals(offeringId))) {
            throw conflict("ALREADY_ENROLLED", "이미 수강 신청한 강좌입니다.");
        }
        String subjectCode = offering.getSubject().getSubjectCode();
        if (registered.stream().anyMatch(row -> row.getSubjectCode().equals(subjectCode))) {
            throw conflict("SUBJECT_ALREADY_ENROLLED", "해당 학기에 이미 수강 신청한 과목입니다.");
        }
        int credits = registered.stream().mapToInt(row -> row.getCredits().intValue()).sum();
        if (credits + offering.getCredits() > 18) {
            throw conflict("CREDIT_LIMIT_EXCEEDED", "최대 신청 학점인 18학점을 초과합니다.");
        }
        if (enrollments.countScheduleConflicts(studentNumber, term.academicYear(), term.term(), offeringId) > 0) {
            throw conflict("SCHEDULE_CONFLICT", "이미 신청한 강좌와 수업 시간이 겹칩니다.");
        }

        // 학생 조건을 통과한 요청만 강좌 잠금을 점유한다. 강좌 정보는 신청 중 불변이다(D69).
        offerings.findForEnrollment(offeringId)
                .orElseThrow(() -> new IllegalStateException("신청 중 개설 강좌가 사라졌습니다."));
        // 잠금 SQL과 COUNT를 합치지 않는다. 잠금 대기 이후의 새 스냅샷으로 집계한다.
        if (enrollments.countByCourseOffering_Id(offeringId) >= offering.getCapacity()) {
            throw conflict("COURSE_FULL", "수강 신청 정원이 찼습니다.");
        }
        entityManager.persist(new Enrollment(student, offering));
        // IDENTITY INSERT와 커밋이 모두 성공한 뒤에만 Controller가 201을 반환한다.
    }

    private EnrollmentRejectedException conflict(String code, String message) {
        return new EnrollmentRejectedException(code, message, HttpStatus.CONFLICT);
    }
}
