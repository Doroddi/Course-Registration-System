package com.doroddi.courseregistration.teachingAssignment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TeachingAssignmentId implements Serializable {
    @Column(name = "professor_id")
    private Long professorId;

    @Column(name = "offering_id")
    private Long offeringId;

    protected TeachingAssignmentId() {}

    public TeachingAssignmentId(Long professorId, Long offeringId) {
        this.professorId = professorId;
        this.offeringId = offeringId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeachingAssignmentId that)) return false;

        return Objects.equals(professorId, that.professorId)
            && Objects.equals(offeringId, that.offeringId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(professorId, offeringId);
    }
}
