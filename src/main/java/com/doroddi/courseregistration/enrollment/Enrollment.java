package com.doroddi.courseregistration.enrollment;

import com.doroddi.courseregistration.courseOffering.CourseOffering;
import com.doroddi.courseregistration.student.Student;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "enrollment")
@Getter
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long enrollmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_number", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering courseOffering;

    protected Enrollment() {
    }

    public Enrollment(
        Student student,
        CourseOffering courseOffering
    ) {
        this.student = student;
        this.courseOffering = courseOffering;
    }
}
