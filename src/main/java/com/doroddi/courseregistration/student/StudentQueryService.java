package com.doroddi.courseregistration.student;

import com.doroddi.courseregistration.common.api.ListQuery;
import com.doroddi.courseregistration.common.api.PageResponse;
import com.doroddi.courseregistration.department.DepartmentNotFoundException;
import com.doroddi.courseregistration.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class StudentQueryService {
    private final StudentRepository students;
    private final DepartmentRepository departments;

    public PageResponse<StudentListItem> list(ListQuery query) {
        if (query.departmentId() != null && !departments.existsById(query.departmentId())) {
            throw new DepartmentNotFoundException();
        }
        return PageResponse.from(students.findStudentPage(query.departmentId(), query.grade(),
                PageRequest.of(query.page(), query.size())));
    }
}
