package com.doroddi.courseregistration.teachingAssignment;

import com.doroddi.courseregistration.courseOffering.CourseOffering;
import com.doroddi.courseregistration.professor.Professor;
import jakarta.persistence.*;

@Entity
@Table(name = "teaching_assignment")
public class TeachingAssignment {
    @EmbeddedId
    private TeachingAssignmentId id;

    @MapsId("professorId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    @MapsId("offeringId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering  courseOffering;

    protected TeachingAssignment() {}

    public TeachingAssignment(
        Professor professor,
        CourseOffering courseOffering
    ) {
        this.professor = professor;
        this.courseOffering = courseOffering;

        this.id = new TeachingAssignmentId(
            professor.getId(),
            courseOffering.getId()
        );
    }
}
