package com.doroddi.courseregistration.student;

import com.doroddi.courseregistration.common.api.ListQueryParser;
import com.doroddi.courseregistration.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StudentController {
    private final ListQueryParser queryParser;
    private final StudentQueryService queryService;

    @GetMapping(value = "/students", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<StudentListItem> list(@RequestParam MultiValueMap<String, String> parameters) {
        return queryService.list(queryParser.parseStudents(parameters));
    }
}
