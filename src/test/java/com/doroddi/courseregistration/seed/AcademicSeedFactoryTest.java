package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.department.Department;
import com.doroddi.courseregistration.professor.Professor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AcademicSeedFactoryTest {
    private final AcademicSeedFactory factory = new AcademicSeedFactory();

    @Test
    void createsUniqueDepartmentNamesWithoutAssigningDatabaseIds() {
        var departments = factory.createDepartments();
        assertEquals(10, departments.size());
        assertEquals(10, departments.stream().map(Department::getCode).distinct().count());
        assertTrue(departments.stream().allMatch(d -> d.getCode() >= 10 && d.getCode() <= 99));
        assertEquals(10, departments.stream().map(Department::getName).distinct().count());
        assertTrue(departments.stream().allMatch(d -> d.getId() == null
                && !d.getName().isBlank() && d.getName().length() <= 100));
    }

    @Test
    void createsProfessorsWithValidReferencesAndBalancedDistribution() {
        var departments = factory.createDepartments();
        var professors = factory.createProfessors(departments, 100);
        assertEquals(100, professors.size());
        assertTrue(professors.stream().allMatch(p -> p.getId() == null
                && p.getName().matches("[가-힣]{3}")
                && departments.contains(p.getDepartment())));
        var counts = professors.stream().collect(Collectors.groupingBy(
                Professor::getDepartment, Collectors.counting()));
        assertEquals(10, counts.size());
        assertTrue(counts.values().stream().allMatch(count -> count == 10L));
    }

    @Test
    void repeatsValuesButCreatesIndependentEntities() {
        var first = factory.createDepartments();
        var second = factory.createDepartments();
        assertNotSame(first.getFirst(), second.getFirst());
        var firstProfessors = factory.createProfessors(first, 100);
        var secondProfessors = factory.createProfessors(second, 100);
        assertEquals(firstProfessors.stream().map(Professor::getName).toList(),
                secondProfessors.stream().map(Professor::getName).toList());
        assertSame(first.getFirst(), firstProfessors.getFirst().getDepartment());
        assertSame(second.getFirst(), secondProfessors.getFirst().getDepartment());
    }

    @Test
    void rejectsMissingDepartmentsAndNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> factory.createProfessors(List.of(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> factory.createProfessors(factory.createDepartments(), -1));
        assertTrue(factory.createProfessors(factory.createDepartments(), 0).isEmpty());
    }
}
