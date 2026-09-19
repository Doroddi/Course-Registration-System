package com.doroddi.courseregistration.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "subject")
@Getter
public class Subject {
    @Id
    @Column(name = "subject_code", length = 30, nullable = false)
    private String subjectCode;

    protected Subject() {}

    public Subject(String subjectCode) {
        this.subjectCode = subjectCode;
    }
}
