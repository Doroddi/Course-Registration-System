package com.doroddi.courseregistration.student.auth;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

// 로그인에만 엄격한 타입 규칙을 적용해 숫자 문자열·소수의 자동 변환을 막는다.
public class LoginRequestDeserializer extends StdDeserializer<LoginRequest> {
    public LoginRequestDeserializer() {
        super(LoginRequest.class);
    }

    @Override
    public LoginRequest deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return invalid(context);
        }
        Integer studentNumber = null;
        String password = null;
        Set<String> fields = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                return invalid(context);
            }
            String field = parser.currentName();
            if (!fields.add(field)) {
                return invalid(context);
            }
            JsonToken token = parser.nextToken();
            switch (field) {
                case "studentNumber" -> {
                    if (token == JsonToken.VALUE_NULL) {
                        studentNumber = null;
                    } else if (token == JsonToken.VALUE_NUMBER_INT) {
                        studentNumber = parser.getIntValue();
                    } else {
                        return invalid(context);
                    }
                }
                case "password" -> {
                    if (token == JsonToken.VALUE_NULL) {
                        password = null;
                    } else if (token == JsonToken.VALUE_STRING) {
                        password = parser.getString();
                    } else {
                        return invalid(context);
                    }
                }
                default -> { return invalid(context); }
            }
        }
        return new LoginRequest(studentNumber, password);
    }

    private LoginRequest invalid(DeserializationContext context) {
        // 예외에 입력값을 넣지 않는다. 범위·필수 값은 이후 Validator에서 검사한다.
        return context.reportInputMismatch(LoginRequest.class, "잘못된 요청입니다.");
    }
}
