package com.doroddi.courseregistration.courseOffering;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CourseOffering o where o.id = :id")
    Optional<CourseOffering> findForEnrollment(@Param("id") Long id);

    long countByAcademicYearAndTerm(Integer academicYear, Short term);

    @Query("""
            select count(o) from CourseOffering o
            where o.academicYear = :year and o.term = :term
              and not exists (select m.id from ClassMeeting m where m.courseOffering = o)
            """)
    long countWithoutMeetings(@Param("year") Integer year, @Param("term") Short term);

    @Query("""
            select count(o) from CourseOffering o
            where o.academicYear = :year and o.term = :term
              and not exists (select t.professor.id from TeachingAssignment t where t.courseOffering = o)
            """)
    long countWithoutProfessors(@Param("year") Integer year, @Param("term") Short term);
}
