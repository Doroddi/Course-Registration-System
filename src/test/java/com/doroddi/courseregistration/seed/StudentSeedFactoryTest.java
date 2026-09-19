package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.student.Student;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StudentSeedFactoryTest {
    private final AcademicSeedFactory factory = new AcademicSeedFactory();
    // 해시 알고리즘 검증이 아닌 생성 데이터 전달 검증용 값이다.
    private static final String HASH = "test-hash-placeholder";

    @Test
    void generatesTenThousandUniqueStudentsWithValidReferencesAndNumbers() {
        var departments = factory.createDepartments();
        var students = factory.createStudents(departments, 10_000, HASH);
        assertEquals(10_000, students.size());
        assertEquals(10_000, students.stream().map(Student::getStudentNumber).distinct().count());
        for (Student student : students) {
            int number = student.getStudentNumber();
            assertTrue(number / 100_000 >= 2020 && number / 100_000 <= 2026);
            assertEquals(student.getDepartment().getCode().intValue(), number / 1_000 % 100);
            assertTrue(number % 1_000 >= 100 && number % 1_000 <= 499);
            assertTrue(departments.contains(student.getDepartment()));
            assertTrue(student.getName().matches("[가-힣]{3}"));
            assertEquals(HASH, student.getPasswordHash());
        }
        var groups = students.stream().collect(Collectors.groupingBy(
                s -> s.getStudentNumber() / 1_000, Collectors.counting()));
        assertEquals(70, groups.size());
        assertTrue(groups.values().stream().allMatch(n -> n == 142 || n == 143));
        var grades = students.stream().collect(Collectors.groupingBy(Student::getGrade, Collectors.counting()));
        assertEquals(4, grades.size());
        assertTrue(grades.values().stream().allMatch(n -> n == 2500));
    }

    @Test
    void supportsCapacityBoundaryWithoutReusingNumbers() {
        var departments = List.of(new Department("수학과", (short) 99));
        var students = factory.createStudents(departments, 2800, HASH);
        assertEquals(2800, students.stream().map(Student::getStudentNumber).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> factory.createStudents(departments, 2801, HASH));
        assertThrows(IllegalArgumentException.class, () -> factory.createStudents(departments, -1, HASH));
        assertTrue(factory.createStudents(departments, 0, HASH).isEmpty());
    }

    @Test
    void rejectsMissingInvalidAndDuplicateDepartmentCodes() {
        assertThrows(IllegalArgumentException.class, () -> factory.createStudents(List.of(), 1, HASH));
        for (Short code : new Short[]{null, 9, 100}) {
            assertThrows(IllegalArgumentException.class, () -> factory.createStudents(
                    List.of(new Department("수학과", code)), 1, HASH));
        }
        assertThrows(IllegalArgumentException.class, () -> factory.createStudents(List.of(
                new Department("수학과", (short) 10), new Department("물리학과", (short) 10)), 1, HASH));
    }

    @Test
    void rejectsMissingAndOversizedHash() {
        for (String hash : new String[]{null, "", "  ", "x".repeat(256)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createStudents(factory.createDepartments(), 1, hash));
        }
    }
}
