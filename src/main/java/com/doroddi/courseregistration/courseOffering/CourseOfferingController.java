package com.doroddi.courseregistration.courseOffering;

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
public class CourseOfferingController {
    private final ListQueryParser parser;
    private final CourseOfferingQueryService service;

    @GetMapping(value = "/course-offerings", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<CourseOfferingListItem> list(@RequestParam MultiValueMap<String, String> parameters) {
        return service.list(parser.parseCourseOfferings(parameters));
    }
}
