package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.subject.Subject;
import com.doroddi.courseregistration.courseOffering.CourseOffering;
import com.doroddi.courseregistration.classMeeting.ClassMeeting;
import com.doroddi.courseregistration.professor.Professor;
import com.doroddi.courseregistration.teachingAssignment.TeachingAssignment;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 생성만 담당하며 부모 저장과 트랜잭션은 호출 서비스가 담당한다. */
public final class CourseSeedFactory {
    private static final List<String> SUFFIXES = List.of("개론", "이론", "응용", "실습", "세미나");
    private static final Map<String, List<String>> TOPICS = Map.of(
            "컴퓨터공학과", List.of("프로그래밍", "자료구조", "데이터베이스", "네트워크", "인공지능"),
            "전자공학과", List.of("전자회로", "반도체", "신호처리", "통신", "제어공학"),
            "기계공학과", List.of("열역학", "유체역학", "기계설계", "재료역학", "동역학"),
            "산업공학과", List.of("생산관리", "품질관리", "최적화", "인간공학", "물류"),
            "수학과", List.of("선형대수", "해석학", "대수학", "기하학", "확률론"),
            "물리학과", List.of("고전역학", "전자기학", "양자역학", "열물리학", "광학"),
            "경영학과", List.of("회계", "마케팅", "재무관리", "조직행동", "경영전략"),
            "경제학과", List.of("미시경제", "거시경제", "계량경제", "국제경제", "금융경제"),
            "국어국문학과", List.of("국어학", "현대문학", "고전문학", "문예창작", "한국어교육"),
            "영어영문학과", List.of("영어학", "영국문학", "미국문학", "영어교육", "번역")
    );

    public List<Subject> createSubjects(List<Department> departments) {
        validateDepartments(departments);
        var subjects = new ArrayList<Subject>();
        for (Department department : departments) {
            for (int number = 1; number <= 25; number++) {
                subjects.add(new Subject(subjectCode(department, number)));
            }
        }
        return List.copyOf(subjects);
    }

    public List<CourseOffering> createOfferings(List<Department> departments, List<Subject> savedSubjects,
                                                int year, short term) {
        validateDepartments(departments);
        if (year < 1000 || year > 9999 || (term != 1 && term != 2)) {
            throw new IllegalArgumentException("개설 연도 또는 학기가 유효하지 않습니다.");
        }
        var subjectsByCode = savedSubjects.stream().collect(Collectors.toMap(
                Subject::getSubjectCode, Function.identity()));
        var offerings = new ArrayList<CourseOffering>();
        for (Department department : departments) {
            var topics = TOPICS.get(department.getName());
            for (int number = 1; number <= 25; number++) {
                String code = subjectCode(department, number);
                Subject subject = subjectsByCode.get(code);
                if (subject == null) {
                    throw new IllegalArgumentException("필요한 과목이 저장 결과에 없습니다: " + code);
                }
                String name = topics.get((number - 1) / 5) + " " + SUFFIXES.get((number - 1) % 5);
                for (int section = 1; section <= 2; section++) {
                    offerings.add(new CourseOffering(subject, year, term, code + "0" + section, name,
                            (short) ((number - 1) % 3 + 1), 30 + offerings.size() % 4 * 10, department));
                }
            }
        }
        return List.copyOf(offerings);
    }

    public List<ClassMeeting> createMeetings(List<CourseOffering> savedOfferings) {
        var meetings = new ArrayList<ClassMeeting>();
        for (int index = 0; index < savedOfferings.size(); index++) {
            var offering = savedOfferings.get(index);
            if (offering.getCredits() < 1 || offering.getCredits() > 3) {
                throw new IllegalArgumentException("초기 강좌의 학점은 1~3이어야 합니다.");
            }
            // 100강좌 단위로 요일을 바꿔 교수 100명의 순환 배정 시 하루 한 강좌씩 담당하게 한다.
            short day = (short) ((index / 100) % 5 + 1);
            LocalTime start = LocalTime.of(9 + index % 4 * 2, 0);
            meetings.add(new ClassMeeting(offering, day, start, start.plusHours(offering.getCredits())));
        }
        return List.copyOf(meetings);
    }

    public List<TeachingAssignment> createAssignments(List<CourseOffering> savedOfferings,
                                                      List<Professor> savedProfessors) {
        if (savedProfessors.isEmpty() || savedProfessors.stream().anyMatch(p -> p.getId() == null)
                || savedOfferings.stream().anyMatch(o -> o.getId() == null)) {
            throw new IllegalArgumentException("강의 담당 연결 전에 교수와 개설 강좌를 저장해야 합니다.");
        }
        var assignments = new ArrayList<TeachingAssignment>();
        for (int index = 0; index < savedOfferings.size(); index++) {
            assignments.add(new TeachingAssignment(savedProfessors.get(index % savedProfessors.size()),
                    savedOfferings.get(index)));
        }
        return List.copyOf(assignments);
    }

    private String subjectCode(Department department, int number) {
        return String.format(Locale.ROOT, "%02d%03d", department.getCode(), number);
    }

    private void validateDepartments(List<Department> departments) {
        if (departments.isEmpty() || departments.stream().anyMatch(d -> d.getCode() == null
                || d.getCode() < 10 || d.getCode() > 99 || !TOPICS.containsKey(d.getName()))
                || departments.stream().map(Department::getCode).distinct().count() != departments.size()) {
            throw new IllegalArgumentException("초기 학과명과 유일한 10~99 학과 코드가 필요합니다.");
        }
    }
}
