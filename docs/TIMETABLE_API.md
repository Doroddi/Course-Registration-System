# 내 시간표 API

상태: GET /me/timetable 구현 / 검증 결과는 아래 기록 참조

기존 확정 정책은 요구사항 D06, D24, D40, D49~D51, D61, D63을 따른다. 강좌 목록의 신청 순서 정렬은 확정 정책이다. 경로·응답 구성·배열 정렬과 목록·총학점의 일관성은 D71~D72로 확정했다.

## 기존 확정 정책

- 로그인한 학생을 검증된 JWT의 sub로 식별한다. 다른 학생의 학번을 요청하지 않는다.
- 서버 설정의 신청 대상 연도·학기 시간표를 조회한다. 현재 날짜에서 학기를 추론하지 않는다.
- 수업 요일·시각은 KST 기준이며 시각은 HH:mm을 사용한다.
- 토큰 누락·위조·만료는 401이다. 토큰 발급 후 학생 삭제는 범위에서 제외한다.

## 요청·응답

```http
GET /me/timetable
Authorization: Bearer <accessToken>
```

학번·학기·페이지 파라미터 없이 대상 학기의 본인 신청 강좌를 한 번에 반환한다. 전체 시간표를 함께 확인할 수 있도록 페이지를 나누지 않는 구성이다.

성공은 HTTP 200이며 content에 강좌 목록, totalCredits에 해당 강좌 학점 합을 반환한다. 각 항목은 기존 강좌 응답의 id·name·credits·schedules·departmentName·professors 구조를 재사용한다. 정원·현재 인원은 시간표 항목에서 제외한다.

```json
{
  "content": [
    {
      "id": 11,
      "name": "자료구조",
      "credits": 3,
      "schedules": [
        { "dayOfWeek": "MONDAY", "startTime": "13:00", "endTime": "15:00" },
        { "dayOfWeek": "WEDNESDAY", "startTime": "13:00", "endTime": "15:00" }
      ],
      "departmentName": "컴퓨터공학부",
      "professors": [ { "id": 31, "name": "박구조" } ]
    }
  ],
  "totalCredits": 3
}
```

예시는 응답 구조 설명용이며 실제 서버 실행 결과가 아니다. content[].id는 개설 강좌 ID이다. 복수 수업 시간·공동 강의 교수로 조회 행이 늘어나더라도 강좌 학점은 강좌별 한 번만 합산한다.

신청한 강좌가 없으면 200과 다음 본문을 반환한다.

```json
{
  "content": [],
  "totalCredits": 0
}
```

강좌 목록은 먼저 수강신청한 순서로 반환한다(D63). 취소 후 재신청한 강좌는 새 신청 순서에 배치한다. 전체 강좌 조회의 강좌 코드 정렬과 구분한다. 대상 학생·학기의 신청을 증가 enrollment_id 오름차순으로 반환한다(D66). 같은 학생의 동시 요청은 학생 잠금 안에서 성공적으로 저장·커밋한 순서이며 HTTP 도착 순서와는 구분한다.

배열 내부 정렬: 수업 시간은 월~일 → 시작 시각 → 종료 시각 순, 교수 배열은 교수 ID 오름차순으로 제공한다.

## 오류와 조회 일관성

기존 오류 구조를 재사용하여 지원하지 않는 쿼리 파라미터는 400 INVALID_PARAMETER와 "잘못된 요청입니다."로 거절한다. 토큰 오류는 확정된 401을 따른다. 인증을 먼저 확인한다.

동시 신청·취소 중에도 content와 totalCredits는 동일한 조회 결과에서 구성한다(D72). 응답에 포함한 강좌별 학점을 한 번씩 합산하며 총학점만 별도로 다시 조회하지 않는다. 읽기 전용 READ COMMITTED에서 최초 조회한 신청 강좌를 기준으로 content와 totalCredits를 구성한다. 구체적인 SQL 구성은 아래 구현 항목을 따른다.

## 검증 계획

- 다른 학생·다른 학기의 신청 강좌가 섞이지 않음.
- 취소한 강좌는 제외하고, 재신청 성공 후에는 다시 포함.
- 신청 강좌가 없으면 content=[], totalCredits=0.
- 수업 시간·교수가 여러 명이어도 강좌 중복 및 학점 중복 합산 없음.
- 강좌명·ID 순서와 신청 순서가 다른 경우에도 신청 순서대로 반환(T96).
- A → B → C 신청 후 A를 취소·재신청하면 B → C → A 반환(T97).
- KST 수업 시간과 확정한 배열 내부 정렬 순서 확인.
- 토큰 오류 시 401, 시간표 데이터 미반환.
- 동시 신청·취소 중에도 totalCredits가 content의 강좌별 학점 합과 일치(T107).
- 지원하지 않는 쿼리 파라미터는 400이며 인증 오류가 있으면 401을 우선 반환.

위 목록은 검증 계획이며 실제 실행 결과는 아래에 구분한다.

## 구현 의도와 조회 흐름

TimetableController는 검증된 JWT sub만 서비스에 전달하며 모든 쿼리 파라미터를 거절한다. TimetableRepository는 서버 설정 연도·학기의 본인 신청을 enrollment_id 오름차순으로 조회한다. 정원·신청 인원·비밀번호 등 응답에 불필요한 필드는 읽지 않는다.

TimetableService는 기존 강좌 목록의 수업 시간·교수 일괄 조회를 재사용한다. 다건 관계를 한 SQL에서 조인하지 않아 복수 시간·공동 교수에 의한 강좌 중복과 학점 중복 합산을 피한다. 빈 신청 결과에서는 관계 조회를 생략한다. 총학점은 반환하는 content에서 계산하고 별도 집계 SQL을 실행하지 않는다.

읽기 전용 READ COMMITTED를 적용하고 명시적 행 잠금은 획득하지 않는다. 최초 신청 목록 조회 뒤 다른 트랜잭션의 신청·취소가 커밋되더라도 이번 응답은 최초 목록과 같은 학점 합을 유지한다. 다음 요청에서는 변경된 신청 내역을 읽는다. 여러 SQL 전체에 동일 스냅샷을 보장하는 방식은 아니며, 신청 중 강좌 정보 변경을 지원하지 않는 기존 범위(D69)를 따른다.

공식 근거: [Spring Data JPA projection](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html), [PostgreSQL 18 READ COMMITTED](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED). 2026-09-25 확인. 기존 Spring Data JPA 4.1.1과 PostgreSQL 18.6 구성을 유지한다.
## 실제 검증 결과

2026-09-25: 시간표 HTTP·PostgreSQL 통합 테스트 18개와 기존 신청·취소 80개, 총 98개가 통과했다. 실패·오류·건너뜀 0, bootJar 성공이며 실행 JAR에 테스트 코드가 포함되지 않음을 확인했다.

~~~text
./gradlew test --tests '*TimetableIntegrationTest' --tests '*EnrollmentIntegrationTest' --tests '*EnrollmentExceptionHandlerTest' bootJar --no-daemon
~~~

Testcontainers PostgreSQL 18.6에서 다음을 확인했다.

- JWT 본인·서버 설정 학기만 조회하고 이름·강좌 ID와 다른 신청 순서를 유지한다. 테스트의 대상 학기는 현재 날짜와 다른 2025년 1학기로 지정했다.
- 빈 시간표는 SQL 1회, 신청이 있는 시간표는 강좌·수업 시간·교수 SQL 3회이다. 18개 강좌에도 조회 횟수가 늘지 않는다.
- 복수 수업 시간·공동 교수에서 강좌 및 학점 합산의 중복이 없고 KST HH:mm·요일·교수 ID 정렬을 유지한다.
- 정원·신청 인원 필드는 제외하고 개설 강좌의 학과를 반환한다.
- 파라미터를 모두 거절하며 인증 오류를 우선한다. 오류 요청은 DB에 접근하지 않는다.
- 취소 후 제외, 재신청 후 마지막 순서 반영, 학생·강좌 행 잠금 중 일반 시간표 조회 성공을 확인했다.
- 최초 신청 목록 조회와 관계 조회 사이에 신청 또는 취소 커밋을 끼워 넣어도 해당 응답의 목록·총학점이 일치한다. 후속 요청에는 변경이 반영된다.

이번 실행은 위 관련 테스트 범위이며 전체 프로젝트 회귀·대규모 부하 검증 결과가 아니다.
