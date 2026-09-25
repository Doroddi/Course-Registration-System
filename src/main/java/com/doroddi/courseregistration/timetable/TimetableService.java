package com.doroddi.courseregistration.timetable;

import com.doroddi.courseregistration.config.EnrollmentTermProperties;
import com.doroddi.courseregistration.courseOffering.CourseOfferingListItem.ProfessorItem;
import com.doroddi.courseregistration.courseOffering.CourseOfferingListItem.Schedule;
import com.doroddi.courseregistration.courseOffering.CourseOfferingListRepository;
import lombok.RequiredArgsConstructor;
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
public class TimetableService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final TimetableRepository timetable;
    private final CourseOfferingListRepository courses;
    private final EnrollmentTermProperties term;

    public TimetableResponse get(Integer studentNumber) {
        var rows = timetable.findTimetable(studentNumber, term.academicYear(), term.term());
        if (rows.isEmpty()) return new TimetableResponse(List.of(), 0);

        // 최초 조회한 신청 강좌 ID를 고정한다. 이후 취소 커밋이 있어도 목록·학점의 기준은 같다.
        var ids = rows.stream().map(TimetableRepository.CourseRow::getId).toList();
        Map<Long, List<Schedule>> schedules = new HashMap<>();
        for (var row : courses.findMeetings(ids)) {
            schedules.computeIfAbsent(row.getOfferingId(), ignored -> new ArrayList<>())
                    .add(new Schedule(DayOfWeek.of(row.getDayOfWeek()).name(),
                            TIME.format(row.getStartsAt()), TIME.format(row.getEndsAt())));
        }
        Map<Long, List<ProfessorItem>> professors = new HashMap<>();
        for (var row : courses.findProfessors(ids)) {
            professors.computeIfAbsent(row.getOfferingId(), ignored -> new ArrayList<>())
                    .add(new ProfessorItem(row.getId(), row.getName()));
        }
        var content = rows.stream().map(row -> new TimetableResponse.Item(
                row.getId(), row.getName(), row.getCredits(),
                schedules.getOrDefault(row.getId(), List.of()), row.getDepartmentName(),
                professors.getOrDefault(row.getId(), List.of()))).toList();
        // 총학점을 별도 SQL로 다시 읽으면 동시 신청·취소 시 목록과 합계의 시점이 달라진다.
        int totalCredits = content.stream().mapToInt(item -> item.credits().intValue()).sum();
        return new TimetableResponse(content, totalCredits);
    }
}
