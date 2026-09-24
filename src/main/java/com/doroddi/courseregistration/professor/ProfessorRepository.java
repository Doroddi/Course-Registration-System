package com.doroddi.courseregistration.professor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    @Query(value = """
            select new com.doroddi.courseregistration.professor.ProfessorListItem(
                p.id, p.name, d.name)
            from Professor p join p.department d
            where (:departmentId is null or d.id = :departmentId)
            order by p.id asc
            """, countQuery = """
            select count(p)
            from Professor p join p.department d
            where (:departmentId is null or d.id = :departmentId)
            """)
    Page<ProfessorListItem> findProfessorPage(@Param("departmentId") Long departmentId,
                                            Pageable pageable);
}
