package com.doroddi.courseregistration.courseOffering;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.subject.Subject;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "course_offering")
@Getter
public class CourseOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offering_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_code", nullable = false)
    private Subject subject;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "term", nullable = false)
    private Short term;

    @Column(name = "offering_code", length = 30, nullable = false)
    private String offeringCode;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "credits", nullable = false)
    private Short credits;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    protected CourseOffering() {}

    public CourseOffering(
        Subject subject,
        Integer academicYear,
        Short term,
        String offeringCode,
        String name,
        Short credits,
        Integer capacity,
        Department department
    ) {
        this.subject = subject;
        this.academicYear = academicYear;
        this.term = term;
        this.offeringCode = offeringCode;
        this.name = name;
        this.credits = credits;
        this.capacity = capacity;
        this.department = department;
    }
}
