package com.doroddi.courseregistration.professor;

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
public class ProfessorController {
    private final ListQueryParser queryParser;
    private final ProfessorQueryService queryService;

    @GetMapping(value = "/professors", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<ProfessorListItem> list(@RequestParam MultiValueMap<String, String> parameters) {
        return queryService.list(queryParser.parseProfessors(parameters));
    }
}
