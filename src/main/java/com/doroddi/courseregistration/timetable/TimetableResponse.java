package com.doroddi.courseregistration.timetable;

import com.doroddi.courseregistration.courseOffering.CourseOfferingListItem.ProfessorItem;
import com.doroddi.courseregistration.courseOffering.CourseOfferingListItem.Schedule;
import java.util.List;

public record TimetableResponse(List<Item> content, int totalCredits) {
    public TimetableResponse {
        content = List.copyOf(content);
    }

    public record Item(Long id, String name, Short credits, List<Schedule> schedules,
                       String departmentName, List<ProfessorItem> professors) {
        public Item {
            schedules = List.copyOf(schedules);
            professors = List.copyOf(professors);
        }
    }
}
