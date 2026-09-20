package com.doroddi.courseregistration.student.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final LoginRequestValidator requestValidator;
    private final AuthService authService;
    private final JwtTokenService tokenService;

    @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        requestValidator.validate(request);
        Integer studentNumber = authService.authenticate(request.studentNumber(), request.password());
        String token = tokenService.issue(studentNumber);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new LoginResponse(token, "Bearer", JwtTokenService.TOKEN_LIFETIME.toSeconds()));
    }
}
