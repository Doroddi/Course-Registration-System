package com.doroddi.courseregistration.courseOffering;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface CourseOfferingListRepository extends Repository<CourseOffering, Long> {
    // 숫자 코드의 원문은 보존하고 정렬 때만 numeric으로 변환한다. 30자리도 정확히 비교한다.
    // 다건 관계를 여기서 조인하지 않아 페이지와 전체 강좌 수가 부풀지 않는다.
    @Query(value = """
            select o.offering_id as id, o.name as name, o.credits as credits,
                   o.capacity as capacity, d.name as "departmentName"
            from course_offering o join department d on d.department_id = o.department_id
            where o.academic_year = :year and o.term = :term
              and (cast(:departmentId as bigint) is null or o.department_id = :departmentId)
            order by cast(o.offering_code as numeric) asc, o.offering_id asc
            """, countQuery = """
            select count(*) from course_offering o
            where o.academic_year = :year and o.term = :term
              and (cast(:departmentId as bigint) is null or o.department_id = :departmentId)
            """, nativeQuery = true)
    Page<BaseRow> findPage(@Param("year") int year, @Param("term") short term,
                           @Param("departmentId") Long departmentId, Pageable pageable);

    @Query("""
            select m.courseOffering.id as offeringId, m.dayOfWeek as dayOfWeek,
                   m.startsAt as startsAt, m.endsAt as endsAt
            from ClassMeeting m where m.courseOffering.id in :ids
            order by m.courseOffering.id, m.dayOfWeek, m.startsAt, m.endsAt, m.id
            """)
    List<MeetingRow> findMeetings(@Param("ids") List<Long> ids);

    @Query("""
            select t.courseOffering.id as offeringId, p.id as id, p.name as name
            from TeachingAssignment t join t.professor p
            where t.courseOffering.id in :ids
            order by t.courseOffering.id, p.id
            """)
    List<ProfessorRow> findProfessors(@Param("ids") List<Long> ids);

    @Query("""
            select e.courseOffering.id as offeringId, count(e) as enrolled
            from Enrollment e where e.courseOffering.id in :ids
            group by e.courseOffering.id
            """)
    List<EnrollmentRow> countEnrollments(@Param("ids") List<Long> ids);

    interface BaseRow {
        Long getId();
        String getName();
        Short getCredits();
        Integer getCapacity();
        String getDepartmentName();
    }
    interface MeetingRow {
        Long getOfferingId();
        Short getDayOfWeek();
        LocalTime getStartsAt();
        LocalTime getEndsAt();
    }
    interface ProfessorRow {
        Long getOfferingId();
        Long getId();
        String getName();
    }
    interface EnrollmentRow {
        Long getOfferingId();
        Long getEnrolled();
    }
}
