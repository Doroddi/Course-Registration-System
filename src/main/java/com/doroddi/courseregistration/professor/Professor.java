package com.doroddi.courseregistration.professor;

import com.doroddi.courseregistration.department.Department;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "professor")
@Getter
public class Professor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "professor_id")
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    protected Professor() {}

    public Professor(String name, Department department) {
        this.name = name;
        this.department = department;
    }
}
