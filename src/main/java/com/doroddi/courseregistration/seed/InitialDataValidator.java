package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.classMeeting.ClassMeetingRepository;
import com.doroddi.courseregistration.courseOffering.CourseOfferingRepository;
import com.doroddi.courseregistration.department.DepartmentRepository;
import com.doroddi.courseregistration.enrollment.EnrollmentRepository;
import com.doroddi.courseregistration.professor.ProfessorRepository;
import com.doroddi.courseregistration.student.StudentRepository;
import com.doroddi.courseregistration.subject.SubjectRepository;
import com.doroddi.courseregistration.teachingAssignment.TeachingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialDataValidator {

    private final DepartmentRepository departmentRepository;
    private final ProfessorRepository professorRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ClassMeetingRepository classMeetingRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public boolean isEmpty() {
        return departmentRepository.count() == 0
            && professorRepository.count() == 0
            && studentRepository.count() == 0
            && subjectRepository.count() == 0
            && courseOfferingRepository.count() == 0
            && classMeetingRepository.count() == 0
            && teachingAssignmentRepository.count() == 0
            && enrollmentRepository.count() == 0;
    }

    public void validateExistingData(Integer academicYear, Short term) {
        if (academicYear == null || academicYear < 1000 || academicYear > 9999
                || term == null || (term != 1 && term != 2)) {
            throw new IllegalArgumentException("신청 대상 연도와 학기가 올바르지 않습니다.");
        }
        requiredMinimum("학과", departmentRepository.count(), 10);
        requiredMinimum("교수", professorRepository.count(), 100);
        requiredMinimum("학생", studentRepository.count(), 10_000);

        requiredMinimum(
            "신청 대상 학기의 개설 강좌",
            courseOfferingRepository.countByAcademicYearAndTerm(academicYear, term),
            500
        );
        requireNone("수업 시간이 없는 강좌", courseOfferingRepository.countWithoutMeetings(academicYear, term));
        requireNone("담당 교수가 없는 강좌", courseOfferingRepository.countWithoutProfessors(academicYear, term));
        requireNone("정원을 초과한 강좌", enrollmentRepository.countOverCapacityOfferings(academicYear, term));
        requireNone("18학점을 초과한 학생", enrollmentRepository.countOverCreditStudents(academicYear, term));
        requireNone("동일 과목 중복 신청", enrollmentRepository.countDuplicateSubjectPairs(academicYear, term));
        requireNone("시간표 충돌", enrollmentRepository.countTimeConflictPairs(academicYear, term));
    }

    private void requireNone(String target, long count) {
        if (count > 0) {
            throw new IllegalStateException("초기 데이터가 불완전합니다: " + target + " " + count + "건");
        }
    }

    private void requiredMinimum(String target, long actual, long minimum) {
        if(actual < minimum) {
            throw new IllegalStateException(
                "초기 데이터가 불완전합니다: " + target + " 최소 " + minimum + "개 필요, 현재 " + actual + "개"
            );
        }
    }
}
