package com.doroddi.courseregistration.student;

import com.doroddi.courseregistration.department.Department;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "student")
@Getter
public class Student {
    @Id
    @Column(name = "student_number", nullable = false, updatable = false)
    private Integer studentNumber;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "grade", nullable = false)
    private Short grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    protected Student() {}

    public Student(
        Integer studentNumber,
        String name,
        Short grade,
        Department department,
        String passwordHash
    ) {
        this.studentNumber = studentNumber;
        this.name = name;
        this.grade = grade;
        this.department = department;
        this.passwordHash = passwordHash;
    }
}
