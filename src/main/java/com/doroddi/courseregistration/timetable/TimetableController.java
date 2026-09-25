package com.doroddi.courseregistration.timetable;

import com.doroddi.courseregistration.common.api.InvalidParameterException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TimetableController {
    private final TimetableService service;

    @GetMapping("/me/timetable")
    public TimetableResponse get(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) throw new InvalidParameterException("잘못된 요청입니다.");
        return service.get(Integer.valueOf(jwt.getSubject()));
    }
}
