package com.doroddi.courseregistration.common.api;

import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.List;

@Component
public class ListQueryParser {
    private static final List<String> STUDENT_PARAMETERS =
            List.of("departmentId", "grade", "page", "size");
    private static final List<String> DEPARTMENT_PAGE_PARAMETERS =
            List.of("departmentId", "page", "size");

    public ListQuery parseStudents(MultiValueMap<String, String> parameters) {
        return parse(parameters, true);
    }

    public ListQuery parseProfessors(MultiValueMap<String, String> parameters) {
        return parse(parameters, false);
    }

    public ListQuery parseCourseOfferings(MultiValueMap<String, String> parameters) {
        return parse(parameters, false);
    }

    private ListQuery parse(MultiValueMap<String, String> parameters, boolean studentQuery) {
        List<String> allowed = studentQuery ? STUDENT_PARAMETERS : DEPARTMENT_PAGE_PARAMETERS;
        // 요청에 적힌 순서가 달라도 같은 오류를 선택한다.
        parameters.keySet().stream()
                .filter(name -> !allowed.contains(name))
                .sorted()
                .findFirst()
                .ifPresent(name -> {
                    throw new InvalidParameterException("지원하지 않는 파라미터입니다: " + name + ".");
                });

        Long departmentId = readNumber(parameters, "departmentId", 1, Long.MAX_VALUE, null);
        Long grade = studentQuery ? readNumber(parameters, "grade", 1, 4, null) : null;
        int page = readNumber(parameters, "page", 0, Integer.MAX_VALUE, 0L).intValue();
        int size = readNumber(parameters, "size", 1, 100, 20L).intValue();

        // JPA setFirstResult는 int를 받는다. 곱셈부터 long으로 계산해 오버플로를 피한다.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidParameterException("page와 size의 곱은 2147483647 이하여야 합니다.");
        }

        return new ListQuery(departmentId, grade == null ? null : grade.shortValue(), page, size);
    }

    private Long readNumber(MultiValueMap<String, String> parameters, String name,
                            long minimum, long maximum, Long defaultValue) {
        // 생략과 빈 값을 구분해야 빈 입력에 기본값이 적용되지 않는다.
        if (!parameters.containsKey(name)) {
            return defaultValue;
        }

        List<String> values = parameters.get(name);
        if (values == null || values.size() != 1) {
            throw new InvalidParameterException(name + "는 한 번만 전달해야 합니다.");
        }

        String value = values.getFirst();
        if (value == null || value.isEmpty()
                || !value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw new InvalidParameterException(name + "는 공백이나 부호 없이 숫자(0~9)로 입력해야 합니다.");
        }

        String rangeMessage = name + "는 " + minimum + " 이상 " + maximum + " 이하여야 합니다.";
        long number;
        try {
            number = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException(rangeMessage);
        }
        if (number < minimum || number > maximum) {
            throw new InvalidParameterException(rangeMessage);
        }
        return number;
    }
}
