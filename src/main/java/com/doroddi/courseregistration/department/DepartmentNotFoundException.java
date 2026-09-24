package com.doroddi.courseregistration.department;

public class DepartmentNotFoundException extends RuntimeException {
    public DepartmentNotFoundException() {
        super("해당 학과를 찾을 수 없습니다.");
    }
}
