package com.doroddi.courseregistration.common.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListQueryParserTest {
    private final ListQueryParser parser = new ListQueryParser();

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void defaultsApplyOnlyWhenParametersAreAbsent(Endpoint endpoint) {
        assertThat(parse(endpoint, params())).isEqualTo(new ListQuery(null, null, 0, 20));
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void acceptsOptionalDepartmentFilterForEveryList(Endpoint endpoint) {
        assertThat(parse(endpoint, params("departmentId", "17")))
                .isEqualTo(new ListQuery(17L, null, 0, 20));
    }

    @Test
    void studentGradeFilterCanBeUsedWithoutDepartmentFilter() {
        assertThat(parser.parseStudents(params("grade", "3")))
                .isEqualTo(new ListQuery(null, (short) 3, 0, 20));
    }

    @ParameterizedTest
    @EnumSource(value = Endpoint.class, names = {"PROFESSORS", "COURSE_OFFERINGS"})
    void gradeIsUnknownForProfessorAndCourseOfferingLists(Endpoint endpoint) {
        assertThatThrownBy(() -> parse(endpoint, params("grade", "2")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("지원하지 않는 파라미터입니다: grade.");
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void acceptsLeadingZerosWithoutChangingNumericMeaning(Endpoint endpoint) {
        var query = params("departmentId", "00000000000000000000000000017",
                "page", "0002", "size", "0010");
        if (endpoint == Endpoint.STUDENTS) {
            query.add("grade", "0004");
        }

        assertThat(parse(endpoint, query)).isEqualTo(
                new ListQuery(17L, endpoint == Endpoint.STUDENTS ? (short) 4 : null, 2, 10));
    }

    @Test
    void acceptsMinimumFilterAndPagingValues() {
        assertThat(parser.parseStudents(params("departmentId", "1", "grade", "1", "page", "0", "size", "1")))
                .isEqualTo(new ListQuery(1L, (short) 1, 0, 1));
    }

    @Test
    void acceptsLongDepartmentMaximumAndIntegerPageMaximumWithSizeOne() {
        assertThat(parser.parseStudents(params("departmentId", "9223372036854775807", "grade", "4",
                "page", "2147483647", "size", "1")))
                .isEqualTo(new ListQuery(Long.MAX_VALUE, (short) 4, Integer.MAX_VALUE, 1));
    }

    @ParameterizedTest
    @MethodSource("malformedValues")
    void rejectsNonAsciiIntegerSyntax(String name, String value) {
        assertThatThrownBy(() -> parser.parseStudents(params(name, value)))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage(formatMessage(name));
    }

    static Stream<Arguments> malformedValues() {
        return Stream.of(
                Arguments.of("departmentId", ""),
                Arguments.of("grade", (String) null),
                Arguments.of("page", " "),
                Arguments.of("size", "\t"),
                Arguments.of("departmentId", " 1"),
                Arguments.of("grade", "1 "),
                Arguments.of("page", "+1"),
                Arguments.of("size", "-1"),
                Arguments.of("departmentId", "1.0"),
                Arguments.of("grade", "1e0"),
                Arguments.of("page", "first"),
                Arguments.of("size", "1_0"),
                Arguments.of("departmentId", "１"),
                Arguments.of("grade", "١"),
                Arguments.of("page", "1\n"));
    }

    @ParameterizedTest
    @MethodSource("outOfRangeValues")
    void rejectsRangeAndNumericOverflowWithTheFieldRange(String name, String value, String message) {
        assertThatThrownBy(() -> parser.parseStudents(params(name, value)))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage(message);
    }

    static Stream<Arguments> outOfRangeValues() {
        return Stream.of(
                Arguments.of("departmentId", "0", rangeMessage("departmentId", 1, Long.MAX_VALUE)),
                Arguments.of("departmentId", "9223372036854775808", rangeMessage("departmentId", 1, Long.MAX_VALUE)),
                Arguments.of("departmentId", "9999999999999999999999999999999999999999",
                        rangeMessage("departmentId", 1, Long.MAX_VALUE)),
                Arguments.of("grade", "0", rangeMessage("grade", 1, 4)),
                Arguments.of("grade", "5", rangeMessage("grade", 1, 4)),
                Arguments.of("grade", "32768", rangeMessage("grade", 1, 4)),
                Arguments.of("page", "2147483648", rangeMessage("page", 0, Integer.MAX_VALUE)),
                Arguments.of("size", "0", rangeMessage("size", 1, 100)),
                Arguments.of("size", "101", rangeMessage("size", 1, 100)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departmentId", "grade", "page", "size"})
    void duplicateValuesAreRejectedEvenWhenEqual(String name) {
        assertThatThrownBy(() -> parser.parseStudents(params(name, "1", name, "1")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage(duplicateMessage(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departmentId", "grade", "page", "size"})
    void duplicateDetectionPrecedesSyntaxAndRangeWithinTheSameField(String name) {
        assertThatThrownBy(() -> parser.parseStudents(params(name, "bad", name, "999999999999999999999999")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage(duplicateMessage(name));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownNamesPrecedeKnownFieldErrorsAndUseNaturalNameOrder(boolean reverseOrder) {
        var query = params("page", "bad", "size", "1", "size", "1",
                "alpha", "1", "Zeta", "1", "Zeta", "2");

        assertThatThrownBy(() -> parser.parseStudents(reverseOrder ? reverseKeys(query) : query))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("지원하지 않는 파라미터입니다: Zeta.");
    }

    @Test
    void parameterNamesAreCaseSensitive() {
        assertThatThrownBy(() -> parser.parseStudents(params("Page", "1")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("지원하지 않는 파라미터입니다: Page.");
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void fixedFieldOrderPrecedesDuplicatesInLaterFieldsForBothQueryOrders(Endpoint endpoint) {
        var firstDepartment = params("departmentId", "bad", "page", "1", "page", "1", "size", "0");
        var firstPage = params("page", "bad", "size", "1", "size", "1");

        assertFailureInBothOrders(endpoint, firstDepartment, formatMessage("departmentId"));
        assertFailureInBothOrders(endpoint, firstPage, formatMessage("page"));
    }

    @Test
    void studentGradeIsCheckedBetweenDepartmentAndPageForBothQueryOrders() {
        assertFailureInBothOrders(Endpoint.STUDENTS,
                params("departmentId", "bad", "grade", "1", "grade", "1"), formatMessage("departmentId"));
        assertFailureInBothOrders(Endpoint.STUDENTS,
                params("grade", "5", "page", "1", "page", "1"), rangeMessage("grade", 1, 4));
    }

    @Test
    void acceptsLargestPageAtMaximumSizeWithoutOffsetOverflow() {
        assertThat(parser.parseStudents(params("page", "21474836", "size", "100")))
                .isEqualTo(new ListQuery(null, null, 21474836, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"21474837", "2147483647"})
    void rejectsOffsetsBeyondIntegerMaximumWithoutIntegerMultiplicationOverflow(String page) {
        assertThatThrownBy(() -> parser.parseStudents(params("page", page, "size", "100")))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("page와 size의 곱은 2147483647 이하여야 합니다.");
    }

    @Test
    void validatesEveryFieldBeforeCheckingCombinedOffset() {
        assertFailureInBothOrders(Endpoint.STUDENTS,
                params("page", "2147483647", "size", "101"), rangeMessage("size", 1, 100));
        assertFailureInBothOrders(Endpoint.STUDENTS,
                params("grade", "5", "page", "21474837", "size", "100"), rangeMessage("grade", 1, 4));
    }

    private void assertFailureInBothOrders(Endpoint endpoint, MultiValueMap<String, String> query, String message) {
        for (var orderedQuery : java.util.List.of(query, reverseKeys(query))) {
            assertThatThrownBy(() -> parse(endpoint, orderedQuery))
                    .isInstanceOf(InvalidParameterException.class)
                    .hasMessage(message);
        }
    }

    private ListQuery parse(Endpoint endpoint, MultiValueMap<String, String> query) {
        return switch (endpoint) {
            case STUDENTS -> parser.parseStudents(query);
            case PROFESSORS -> parser.parseProfessors(query);
            case COURSE_OFFERINGS -> parser.parseCourseOfferings(query);
        };
    }

    private static MultiValueMap<String, String> params(String... pairs) {
        var result = new LinkedMultiValueMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.add(pairs[index], pairs[index + 1]);
        }
        return result;
    }

    private static MultiValueMap<String, String> reverseKeys(MultiValueMap<String, String> query) {
        var entries = new ArrayList<>(query.entrySet());
        Collections.reverse(entries);
        var reversed = new LinkedMultiValueMap<String, String>();
        entries.forEach(entry -> reversed.addAll(entry.getKey(), entry.getValue()));
        return reversed;
    }

    private static String formatMessage(String name) {
        return name + "는 공백이나 부호 없이 숫자(0~9)로 입력해야 합니다.";
    }

    private static String rangeMessage(String name, long min, long max) {
        return name + "는 " + min + " 이상 " + max + " 이하여야 합니다.";
    }

    private static String duplicateMessage(String name) {
        return name + "는 한 번만 전달해야 합니다.";
    }

    private enum Endpoint {
        STUDENTS, PROFESSORS, COURSE_OFFERINGS
    }
}
