package com.doroddi.courseregistration.seed;

import com.doroddi.courseregistration.classMeeting.ClassMeetingRepository;
import com.doroddi.courseregistration.courseOffering.CourseOfferingRepository;
import com.doroddi.courseregistration.department.DepartmentRepository;
import com.doroddi.courseregistration.enrollment.EnrollmentRepository;
import com.doroddi.courseregistration.professor.ProfessorRepository;
import com.doroddi.courseregistration.student.StudentRepository;
import com.doroddi.courseregistration.subject.SubjectRepository;
import com.doroddi.courseregistration.teachingAssignment.TeachingAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.repository.CrudRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialDataValidatorTest {
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final ClassMeetingRepository meetings = mock(ClassMeetingRepository.class);
    private final TeachingAssignmentRepository assignments = mock(TeachingAssignmentRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final InitialDataValidator validator = new InitialDataValidator(
            departments, professors, students, subjects, offerings, meetings, assignments, enrollments);

    @Test
    void allEmptyTablesAllowInitialization() {
        assertTrue(validator.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void anyNonemptyTablePreventsInitialization(int index) {
        CrudRepository<?, ?>[] repositories = {
                departments, professors, students, subjects, offerings, meetings, assignments, enrollments};
        when(repositories[index].count()).thenReturn(1L);
        assertFalse(validator.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void acceptsMinimumCountsAndAdditionalData(int extra) {
        counts(10 + extra, 100 + extra, 10_000 + extra, 500 + extra);
        assertDoesNotThrow(() -> validator.validateExistingData(2026, (short) 2));
        // 기존 신청을 비우거나 재생성하지 않는다.
        verify(enrollments).countOverCapacityOfferings(2026, (short) 2);
    }

    @ParameterizedTest
    @CsvSource({"9,100,10000,500,학과", "10,99,10000,500,교수",
            "10,100,9999,500,학생", "10,100,10000,499,신청 대상 학기의 개설 강좌"})
    void rejectsInsufficientDataWithCause(long departmentCount, long professorCount,
            long studentCount, long offeringCount, String target) {
        counts(departmentCount, professorCount, studentCount, offeringCount);
        var error = assertThrows(IllegalStateException.class,
                () -> validator.validateExistingData(2026, (short) 2));
        assertTrue(error.getMessage().contains(target));
    }

    @ParameterizedTest
    @CsvSource({",2", "999,2", "2026,", "2026,3"})
    void rejectsInvalidTargetBeforeAccessingDatabase(Integer year, Short term) {
        assertThrows(IllegalArgumentException.class, () -> validator.validateExistingData(year, term));
        verifyNoInteractions(departments, professors, students, subjects, offerings, meetings, assignments, enrollments);
    }

    private void counts(long departmentCount, long professorCount, long studentCount, long offeringCount) {
        when(departments.count()).thenReturn(departmentCount);
        when(professors.count()).thenReturn(professorCount);
        when(students.count()).thenReturn(studentCount);
        when(offerings.countByAcademicYearAndTerm(2026, (short) 2)).thenReturn(offeringCount);
    }
}
