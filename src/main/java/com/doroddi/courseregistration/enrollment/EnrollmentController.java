package com.doroddi.courseregistration.enrollment;

import com.doroddi.courseregistration.common.api.InvalidParameterException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {
    private final EnrollmentService service;

    @PostMapping(value = "/enrollments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrollmentResponse> enroll(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestBody EnrollmentRequest request) {
        if (request == null || request.courseOfferingId() == null || request.courseOfferingId() <= 0) {
            throw new InvalidParameterException("잘못된 요청입니다.");
        }
        // 인증된 sub는 9자리 학번이다. 요청 본문에서 학생 ID를 받지 않는다.
        service.enroll(Integer.valueOf(jwt.getSubject()), request.courseOfferingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new EnrollmentResponse(request.courseOfferingId()));
    }
}
