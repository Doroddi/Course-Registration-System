package com.doroddi.courseregistration.classMeeting;

import com.doroddi.courseregistration.courseOffering.CourseOffering;
import com.doroddi.courseregistration.professor.Professor;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalTime;

@Entity
@Table(name = "class_meeting")
@Getter
public class ClassMeeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering courseOffering;

    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "starts_at", nullable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalTime endsAt;

    protected ClassMeeting() {}

    public ClassMeeting(
        CourseOffering courseOffering,
        Short dayOfWeek,
        LocalTime startsAt,
        LocalTime endsAt
    ) {
        this.courseOffering = courseOffering;
        this.dayOfWeek = dayOfWeek;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
