package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.professor.Professor;
import com.doroddi.courseregistration.student.Student;

import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 데이터를 메모리에서 구성한다. 저장·트랜잭션·재시작 판단은 호출자가 담당한다.
 */
public final class AcademicSeedFactory {
    private static final List<String> DEPARTMENT_NAMES = List.of(
            "컴퓨터공학과", "전자공학과", "기계공학과", "산업공학과", "수학과",
            "물리학과", "경영학과", "경제학과", "국어국문학과", "영어영문학과"
    );
    private static final List<String> FAMILY_NAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍"
    );
    private static final List<String> GIVEN_NAMES = List.of(
            "민준", "서연", "지훈", "수빈", "도윤", "예진", "현우", "유진", "준서", "지민",
            "서준", "수현", "정우", "은서", "건우", "민서", "시우", "하은", "승현", "소윤"
    );

    public List<Department> createDepartments() {
        var departments = new ArrayList<Department>(DEPARTMENT_NAMES.size());
        for (int index = 0; index < DEPARTMENT_NAMES.size(); index++) {
            departments.add(new Department(DEPARTMENT_NAMES.get(index), (short) (10 + index)));
        }
        return List.copyOf(departments);
    }

    /** 저장된 학과 목록을 전달하면 생성된 교수가 해당 학과 엔티티를 참조한다. */
    public List<Professor> createProfessors(List<Department> departments, int count) {
        var parents = List.copyOf(departments);
        if (parents.isEmpty()) {
            throw new IllegalArgumentException("교수 생성에는 학과가 필요합니다.");
        }
        if (count < 0) {
            throw new IllegalArgumentException("교수 수는 0 이상이어야 합니다.");
        }
        var professors = new ArrayList<Professor>(count);
        for (int index = 0; index < count; index++) {
            professors.add(new Professor(personName(index), parents.get(index % parents.size())));
        }
        return List.copyOf(professors);
    }

    /** 비밀번호 원문 대신 호출자가 준비한 해시를 받는다. 저장과 계정 해시 정책은 호출자가 담당한다. */
    public List<Student> createStudents(List<Department> departments, int count, String passwordHash) {
        var parents = List.copyOf(departments);
        if (parents.isEmpty() || parents.stream().anyMatch(d -> d.getCode() == null
                || d.getCode() < 10 || d.getCode() > 99)
                || parents.stream().map(Department::getCode).distinct().count() != parents.size()) {
            throw new IllegalArgumentException("학생 생성에는 서로 다른 10~99 학과 코드가 필요합니다.");
        }
        int groupCount = 7 * parents.size();
        if (count < 0 || count > groupCount * 400) {
            throw new IllegalArgumentException("생성 인원이 연도·학과별 학번 범위를 초과합니다.");
        }
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 255) {
            throw new IllegalArgumentException("비밀번호 해시가 필요하며 길이는 255 이하여야 합니다.");
        }
        var numbers = new StudentNumberGenerator();
        var students = new ArrayList<Student>(count);
        int group = 0;
        for (int year = 2020; year <= 2026; year++) {
            for (Department department : parents) {
                // 나머지를 앞 그룹에 한 명씩 배분하여 그룹 간 인원 차이를 최대 1명으로 제한한다.
                int groupSize = count / groupCount + (group < count % groupCount ? 1 : 0);
                for (int number : numbers.generate(year, department.getCode(), groupSize)) {
                    int index = students.size();
                    students.add(new Student(number, personName(index), (short) (index % 4 + 1),
                            department, passwordHash));
                }
                group++;
            }
        }
        return List.copyOf(students);
    }
    private String personName(int index) {
        // 같은 입력 순서로 같은 데이터를 만들어 실패 사례를 재현한다. 동명이인은 허용한다.
        return FAMILY_NAMES.get(index % FAMILY_NAMES.size())
                + GIVEN_NAMES.get((index / FAMILY_NAMES.size()) % GIVEN_NAMES.size());
    }
}
