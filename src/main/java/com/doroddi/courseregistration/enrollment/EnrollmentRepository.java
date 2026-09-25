package com.doroddi.courseregistration.enrollment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    @Query("""
            select o.id as offeringId, o.subject.subjectCode as subjectCode, o.credits as credits
            from Enrollment e join e.courseOffering o
            where e.student.studentNumber = :studentNumber
              and o.academicYear = :year and o.term = :term
            """)
    List<RegisteredCourse> findRegisteredCourses(@Param("studentNumber") Integer studentNumber,
                                                          @Param("year") int year, @Param("term") short term);

    @Query("""
            select count(m) from Enrollment e, ClassMeeting m, ClassMeeting requested
            where e.student.studentNumber = :studentNumber
              and e.courseOffering.academicYear = :year and e.courseOffering.term = :term
              and m.courseOffering = e.courseOffering and requested.courseOffering.id = :offeringId
              and m.dayOfWeek = requested.dayOfWeek
              and m.startsAt < requested.endsAt and requested.startsAt < m.endsAt
            """)
    long countScheduleConflicts(@Param("studentNumber") Integer studentNumber,
                               @Param("year") int year, @Param("term") short term,
                               @Param("offeringId") Long offeringId);

    long countByCourseOffering_Id(Long offeringId);

    // 부모 행 잠금 이후 실행한다. 엔티티를 읽지 않고 본인·대상 강좌의 관계만 삭제한다.
    @Modifying
    @Query("""
            delete from Enrollment e
            where e.student.studentNumber = :studentNumber and e.courseOffering.id = :offeringId
            """)
    int deleteRegistration(@Param("studentNumber") Integer studentNumber,
                           @Param("offeringId") Long offeringId);

    interface RegisteredCourse {
        Long getOfferingId();
        String getSubjectCode();
        Short getCredits();
    }

    @Query("""
            select count(o) from CourseOffering o
            where o.academicYear = :year and o.term = :term
              and (select count(e) from Enrollment e where e.courseOffering = o) > o.capacity
            """)
    long countOverCapacityOfferings(@Param("year") Integer year, @Param("term") Short term);

    // 대상 학기의 신청을 학생별로 한 번 집계한다.
    @Query("""
            select count(s) from Student s
            where s.studentNumber in (
                select e.student.studentNumber from Enrollment e join e.courseOffering o
                where o.academicYear = :year and o.term = :term
                group by e.student.studentNumber
                having sum(o.credits) > 18
            )
            """)
    long countOverCreditStudents(@Param("year") Integer year, @Param("term") Short term);

    @Query("""
            select count(a) from Enrollment a, Enrollment b
            where a.enrollmentId < b.enrollmentId and a.student = b.student
              and a.courseOffering.subject = b.courseOffering.subject
              and a.courseOffering.academicYear = :year and a.courseOffering.term = :term
              and b.courseOffering.academicYear = :year and b.courseOffering.term = :term
            """)
    long countDuplicateSubjectPairs(@Param("year") Integer year, @Param("term") Short term);

    // 종료 시각과 다음 시작 시각이 같은 인접 수업은 충돌에 포함하지 않는다.
    @Query("""
            select count(a) from Enrollment a, Enrollment b, ClassMeeting m, ClassMeeting n
            where a.enrollmentId < b.enrollmentId and a.student = b.student
              and a.courseOffering.academicYear = :year and a.courseOffering.term = :term
              and b.courseOffering.academicYear = :year and b.courseOffering.term = :term
              and m.courseOffering = a.courseOffering and n.courseOffering = b.courseOffering
              and m.dayOfWeek = n.dayOfWeek
              and m.startsAt < n.endsAt and n.startsAt < m.endsAt
            """)
    long countTimeConflictPairs(@Param("year") Integer year, @Param("term") Short term);
}
