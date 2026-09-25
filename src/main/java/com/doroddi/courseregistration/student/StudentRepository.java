package com.doroddi.courseregistration.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.studentNumber = :studentNumber")
    Optional<Student> findForEnrollment(@Param("studentNumber") Integer studentNumber);

    // 목록에는 비밀번호 해시를 읽지 않고 필요한 필드와 현재 소속 학과명만 조회한다.
    @Query(value = """
            select new com.doroddi.courseregistration.student.StudentListItem(
                s.studentNumber, s.name, s.grade, d.name)
            from Student s join s.department d
            where (:departmentId is null or d.id = :departmentId)
              and (:grade is null or s.grade = :grade)
            order by s.studentNumber asc
            """, countQuery = """
            select count(s)
            from Student s join s.department d
            where (:departmentId is null or d.id = :departmentId)
              and (:grade is null or s.grade = :grade)
            """)
    Page<StudentListItem> findStudentPage(@Param("departmentId") Long departmentId,
                                        @Param("grade") Short grade, Pageable pageable);
}
