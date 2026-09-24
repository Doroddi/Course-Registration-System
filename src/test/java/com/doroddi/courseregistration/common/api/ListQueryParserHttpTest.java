package com.doroddi.courseregistration.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListQueryParserHttpTest {
    // HTTP 파라미터의 원래 값과 중복을 파서에 전달하고 공통 오류 응답으로 연결하는지 검증한다.
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new QueryProbeController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void bindsRawQueryParametersAndReturnsTheirParsedValues() throws Exception {
        mvc.perform(get("/probe/students")
                        .queryParam("departmentId", "0008")
                        .queryParam("grade", "04")
                        .queryParam("page", "02")
                        .queryParam("size", "010"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(8))
                .andExpect(jsonPath("$.grade").value(4))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void repeatedEqualParametersRemainDuplicatesThroughHttpBinding() throws Exception {
        mvc.perform(get("/probe/students").queryParam("page", "1", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("page는 한 번만 전달해야 합니다."));
    }

    @Test
    void firstFieldErrorIsIndependentOfQueryStringOrder() throws Exception {
        mvc.perform(get("/probe/students")
                        .queryParam("page", "1", "1")
                        .queryParam("departmentId", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message")
                        .value("departmentId는 공백이나 부호 없이 숫자(0~9)로 입력해야 합니다."));
    }

    @Test
    void emptyQueryValueIsNotReplacedWithTheDefault() throws Exception {
        mvc.perform(get("/probe/students").queryParam("size", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message")
                        .value("size는 공백이나 부호 없이 숫자(0~9)로 입력해야 합니다."));
    }

    @Test
    void professorGradeFilterUsesTheSharedInvalidParameterResponse() throws Exception {
        mvc.perform(get("/probe/professors").queryParam("grade", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 파라미터입니다: grade."));
    }

    @TestComponent
    @RestController
    static class QueryProbeController {
        private final ListQueryParser parser = new ListQueryParser();

        @GetMapping("/probe/students")
        ListQuery students(@RequestParam MultiValueMap<String, String> parameters) {
            return parser.parseStudents(parameters);
        }

        @GetMapping("/probe/professors")
        ListQuery professors(@RequestParam MultiValueMap<String, String> parameters) {
            return parser.parseProfessors(parameters);
        }
    }
}
