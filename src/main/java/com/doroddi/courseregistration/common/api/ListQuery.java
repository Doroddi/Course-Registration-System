package com.doroddi.courseregistration.common.api;

public record ListQuery(Long departmentId, Short grade, int page, int size) {
}
