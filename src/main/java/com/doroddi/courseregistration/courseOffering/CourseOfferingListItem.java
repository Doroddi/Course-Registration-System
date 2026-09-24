package com.doroddi.courseregistration.courseOffering;

import java.util.List;

public record CourseOfferingListItem(Long id, String name, Short credits, Integer capacity,
                                     long enrolled, List<Schedule> schedules,
                                     String departmentName, List<ProfessorItem> professors) {
    public CourseOfferingListItem {
        schedules = List.copyOf(schedules);
        professors = List.copyOf(professors);
    }

    public record Schedule(String dayOfWeek, String startTime, String endTime) {}
    public record ProfessorItem(Long id, String name) {}
}
