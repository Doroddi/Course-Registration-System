package com.doroddi.courseregistration.department;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "department")
@Getter
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long id;

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    protected Department() {}

    public Department(String name) {
        this.name = name;
    }
}
