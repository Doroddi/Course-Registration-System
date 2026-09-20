package com.doroddi.courseregistration.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
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
