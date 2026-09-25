package com.doroddi.courseregistration.timetable;

import com.doroddi.courseregistration.enrollment.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TimetableRepository extends Repository<Enrollment, Long> {
    // 다건 수업 시간·교수를 조인하지 않아 신청 한 건당 정확히 한 행을 반환한다.
    @Query("""
            select o.id as id, o.name as name, o.credits as credits, d.name as departmentName
            from Enrollment e join e.courseOffering o join o.department d
            where e.student.studentNumber = :studentNumber
              and o.academicYear = :year and o.term = :term
            order by e.enrollmentId
            """)
    List<CourseRow> findTimetable(@Param("studentNumber") Integer studentNumber,
                                  @Param("year") int year, @Param("term") short term);

    interface CourseRow {
        Long getId();
        String getName();
        Short getCredits();
        String getDepartmentName();
    }
}
