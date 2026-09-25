package com.doroddi.courseregistration.courseOffering;

import com.doroddi.courseregistration.common.api.ListQuery;
import com.doroddi.courseregistration.common.api.PageResponse;
import com.doroddi.courseregistration.config.EnrollmentTermProperties;
import com.doroddi.courseregistration.department.DepartmentNotFoundException;
import com.doroddi.courseregistration.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class CourseOfferingQueryService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final CourseOfferingListRepository courses;
    private final DepartmentRepository departments;
    private final EnrollmentTermProperties term;

    public PageResponse<CourseOfferingListItem> list(ListQuery query) {
        if (query.departmentId() != null && !departments.existsById(query.departmentId())) {
            throw new DepartmentNotFoundException();
        }
        var page = courses.findPage(term.academicYear(), term.term(), query.departmentId(),
                PageRequest.of(query.page(), query.size()));
        if (page.isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }

        // 이 페이지의 ID만 묶어 조회한다. 항목별 조회 및 다건 조인의 곱집합을 피한다.
        var ids = page.getContent().stream().map(CourseOfferingListRepository.BaseRow::getId).toList();
        Map<Long, List<CourseOfferingListItem.Schedule>> schedules = new HashMap<>();
        for (var row : courses.findMeetings(ids)) {
            schedules.computeIfAbsent(row.getOfferingId(), ignored -> new ArrayList<>())
                    .add(new CourseOfferingListItem.Schedule(DayOfWeek.of(row.getDayOfWeek()).name(),
                            TIME.format(row.getStartsAt()), TIME.format(row.getEndsAt())));
        }
        Map<Long, List<CourseOfferingListItem.ProfessorItem>> professors = new HashMap<>();
        for (var row : courses.findProfessors(ids)) {
            professors.computeIfAbsent(row.getOfferingId(), ignored -> new ArrayList<>())
                    .add(new CourseOfferingListItem.ProfessorItem(row.getId(), row.getName()));
        }
        Map<Long, Long> enrolled = new HashMap<>();
        for (var row : courses.countEnrollments(ids)) {
            enrolled.put(row.getOfferingId(), row.getEnrolled());
        }
        return PageResponse.from(page.map(row -> new CourseOfferingListItem(row.getId(), row.getName(),
                row.getCredits(), row.getCapacity(), enrolled.getOrDefault(row.getId(), 0L),
                schedules.getOrDefault(row.getId(), List.of()), row.getDepartmentName(),
                professors.getOrDefault(row.getId(), List.of()))));
    }
}
