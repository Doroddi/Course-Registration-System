package com.doroddi.courseregistration.enrollment;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class EnrollmentRequestDeserializer extends StdDeserializer<EnrollmentRequest> {
    public EnrollmentRequestDeserializer() { super(EnrollmentRequest.class); }

    @Override
    public EnrollmentRequest deserialize(JsonParser parser, DeserializationContext context) {
        // 숫자 문자열·소수·지수 표기의 자동 변환과 중복 필드를 허용하지 않는다.
        if (parser.currentToken() != JsonToken.START_OBJECT
                || parser.nextToken() != JsonToken.PROPERTY_NAME
                || !"courseOfferingId".equals(parser.currentName())
                || parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
            return invalid(context);
        }
        long id = parser.getLongValue();
        if (id <= 0 || parser.nextToken() != JsonToken.END_OBJECT) return invalid(context);
        return new EnrollmentRequest(id);
    }

    private EnrollmentRequest invalid(DeserializationContext context) {
        return context.reportInputMismatch(EnrollmentRequest.class, "잘못된 요청입니다.");
    }
}
