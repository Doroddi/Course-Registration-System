# 수강신청 API

상태: POST /enrollments 구현 / 검증 결과는 아래 구현·검증 기록 참조

[요구사항 D50~D51, D54~D61](REQUIREMENTS.md)에 따른 요청·성공 응답 계약이다. 신청 제약은 요구사항 I01~I05와 확정 정책을 따른다.

## 요청

```http
POST /enrollments
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "courseOfferingId": 11
}
```

- courseOfferingId는 신청할 개설 강좌의 ID이다. 과목 코드가 아니라 학기·분반이 특정된 개설 강좌를 지정한다.
- 학생은 검증된 JWT의 sub로 식별하며 요청 본문에 학번을 받지 않는다.
- 서버에 설정된 신청 대상 연도·학기의 강좌만 신청할 수 있다.
- courseOfferingId는 필수이며 양의 정수인 JSON 숫자만 허용한다. 누락·null·0·음수·소수·문자열·불리언은 400 INVALID_PARAMETER와 "잘못된 요청입니다." 메시지로 거절한다. 물리 타입은 BIGINT·Java Long(D76)이며 입력 범위는 1~9223372036854775807(Long.MAX_VALUE)이다. 정수의 소수점·지수 표기와 중복 필드는 거절한다.
- 잘못된 JSON과 courseOfferingId 외의 필드도 같은 400 응답으로 거절한다. 요청에 studentNumber를 추가해도 거절한다.

## 성공 응답

신청 내역 생성에 성공하면 HTTP 201 Created를 반환한다.

```json
{
  "courseOfferingId": 11
}
```

응답은 신청한 개설 강좌 ID만 제공한다. 예시는 계약 설명용이며 실제 서버 실행 결과가 아니다.

## 기존 정책과 오류 처리

- 토큰 누락·위조·만료는 401이며 누락 TOKEN_REQUIRED·만료 TOKEN_EXPIRED·그 밖의 실패 INVALID_TOKEN으로 구분한다. [공통 인증 계약](AUTH_API.md)을 따른다.
- 이미 신청한 개설 강좌는 중복 신청 오류로 처리한다.
- 같은 학기의 동일 과목 중복, 정원 초과, 18학점 초과, 시간표 충돌을 허용하지 않는다.
- 한 수업의 종료 시각과 다른 수업의 시작 시각이 같으면 함께 신청할 수 있다. 수업 시간은 KST를 기준으로 해석한다.
- 취소 후 재신청은 가능하며 신청 시점에 모든 조건을 다시 검사한다.

## 전체 처리 순서

1. 인증
2. 입력 검증
3. 개설 강좌 존재 확인
4. 서버 설정의 대상 학기 확인
5. 신청 조건 검사

앞 단계에서 실패하면 뒤 단계로 진행하지 않는다(D60). 토큰 누락·위조·만료와 입력 오류가 함께 있으면 401을 반환한다. 입력 오류이면 개설 강좌 존재 확인을 수행하지 않는다. 로그인하여 토큰이 발급된 학생이 이후 삭제되는 상황은 범위에서 제외하며 별도의 학생 존재 확인 단계와 STUDENT_NOT_FOUND 응답은 제공하지 않는다(D61). 학생 행 잠금 조회는 존재 확인이 아닌 동시성 제어를 위해 수행한다(D64).

## 확정된 신청 조건 검사 순서

동일 개설 강좌 중복 → 동일 학기·과목 중복 → 최대 학점 초과 → 시간표 겹침 → 해당 개설 강좌 잠금 획득 → 정원 검사 순으로 진행한다(D55). 이 순서는 위 전체 처리 흐름의 신청 조건 검사 단계에 적용한다.

중복 신청은 기존의 동일 개설 강좌 중복 금지와 동일 학기·과목 중복 금지 정책을 유지한다. 동일 개설 강좌부터 검사하며 이미 신청한 강좌이면 해당 사실을 우선 안내한다. 이를 통과하더라도 같은 학기에 동일 과목의 다른 분반을 신청했다면 중복 과목임을 안내한다(D56). 학생 조건 위반으로 실패할 요청이 강좌 잠금을 점유하지 않게 하여 강좌 잠금 보유 시간을 줄이고자 한다. 실제 잠금 대기·성능 개선 효과는 아직 측정하지 않았다. 이 순서만으로 같은 학생의 동시 신청에 대한 정합성이 보장되지는 않는다. 학생 조건은 학생 행 잠금으로 보호한다(D64). READ COMMITTED에서 학생 잠금 후 신청 내역을, 강좌 잠금 후 현재 인원을 별도 SQL로 조회한다. 현재 인원은 ENROLLMENT의 COUNT로 계산한다(D65). 성공한 신청은 증가 enrollment_id로 순서를 보존한다(D66).

## 신청 조건 위반 응답

모두 HTTP 409 Conflict와 code·message 본문으로 응답한다. 확정된 검사 순서에서 첫 오류 하나만 반환하고 신청 내역을 생성하지 않는다(D57).

| 검사 순서 | code | message |
|---|---|---|
| 동일 개설 강좌 | ALREADY_ENROLLED | 이미 수강 신청한 강좌입니다. |
| 동일 학기·과목 | SUBJECT_ALREADY_ENROLLED | 해당 학기에 이미 수강 신청한 과목입니다. |
| 최대 학점 | CREDIT_LIMIT_EXCEEDED | 최대 신청 학점인 18학점을 초과합니다. |
| 시간표 충돌 | SCHEDULE_CONFLICT | 이미 신청한 강좌와 수업 시간이 겹칩니다. |
| 정원 | COURSE_FULL | 수강 신청 정원이 찼습니다. |

```json
{
  "code": "ALREADY_ENROLLED",
  "message": "이미 수강 신청한 강좌입니다."
}
```

이 표는 신청 조건 위반을 다룬다. 인증 오류는 기존 401 정책을 따르며 입력·대상 존재·대상 학기 오류는 아래 상태 코드를 따른다.

## 요청값과 신청 대상 오류

| 상황 | HTTP 상태 | code | message |
|---|---|---|---|
| 잘못된 요청값 | 400 Bad Request | INVALID_PARAMETER | 잘못된 요청입니다. |
| 존재하지 않는 개설 강좌 | 404 Not Found | COURSE_OFFERING_NOT_FOUND | 존재하지 않는 강좌입니다. |
| 존재하지만 신청 대상 연도·학기가 아닌 개설 강좌 | 409 Conflict | INVALID_ENROLLMENT_TERM | 신청 대상 학기가 아닙니다. |

신청 대상 연도·학기는 서버 설정을 기준으로 한다. 위 오류에서는 신청 내역을 생성하지 않는다. 오류 본문은 표의 code·message를 사용한다. 입력·강좌·대상 학기 오류는 D58~D59를 따른다.

## 후속 결정

- 부하 측정에 따른 잠금 대기 한도 조정
- 자동 재시도는 현재 미적용이며 후속 검토

검증 계획은 요구사항의 신청 관련 시나리오와 T75~T94을 따른다. 구현 및 실제 실행 결과는 아래에 구분한다.

## 확정된 잠금 실패 응답

D68에 따라 잠금 획득 시도별 3초 대기 한도로 시작한다. 시간 초과 또는 교착 오류 시 전체 롤백 후 503 ENROLLMENT_TEMPORARILY_UNAVAILABLE과 메시지 일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.를 반환한다. 서버 자동 재시도는 현재 적용하지 않는다. 3초는 초기 설정으로 부하 테스트 후 조정한다. 검증 계획은 T102~T103이며 실행 결과는 아래에 구분한다.

신청 처리 중 강좌의 학기·과목 코드·학점·수업 시간·정원 변경은 지원하지 않는다(D69).

## 구현·검증 기록

2026-09-25: POST /enrollments를 구현했다. EnrollmentController는 인증된 JWT sub와 요청 ID를 전달하고, EnrollmentService는 하나의 READ COMMITTED 트랜잭션에서 조건 검사와 저장을 처리한다. 학생·강좌 Repository의 PESSIMISTIC_WRITE가 각각 FOR NO KEY UPDATE를 생성하는 것을 실제 PostgreSQL 18.6에서 확인했다. 잠금 자체에 native query 대체는 필요하지 않았다.

학생 잠금 이후 신청 내역을 조회하고, 학생 조건을 통과한 뒤 강좌 잠금을 획득한다. 정원 COUNT는 강좌 잠금 이후 별도 SQL로 실행한다. 외래 키 확인용 KEY SHARE와 잠금이 공존하는 것도 검증했다. 대기 제한은 select set_config('lock_timeout', '3s', true)로 현재 트랜잭션에만 적용한다. SQLSTATE 55P03(잠금 대기 실패)·40P01(교착 상태)은 롤백 후 503으로 변환하며 다른 DB 장애는 잠금 실패로 변환하지 않는다.

입력 구현은 BIGINT·Long 범위와 기존 인증 API의 엄격한 JSON 처리에 맞췄다. courseOfferingId 하나만 받고 양의 Long 정수 토큰을 허용한다. 중복 필드·소수점·지수 표기·후행 JSON은 400으로 거절하며 해당 요청은 DB에 접근하지 않는다.

실행 명령:

~~~text
./gradlew test --tests '*EnrollmentIntegrationTest' --tests '*EnrollmentExceptionHandlerTest' bootJar --no-daemon
~~~

신청 테스트 53개(HTTP·PostgreSQL 통합 47개, 오류 분류 단위 6개)가 통과했다. 실패·오류·건너뜀 0이며 bootJar도 성공했다. 테스트는 Testcontainers의 별도 PostgreSQL에서 수행한다.

| 검증 대상 | 실제 확인 |
|---|---|
| 인증·입력·대상 학기·업무 오류 | 인증 우선, 입력 오류 시 DB 접근 없음, 명세의 첫 조건 오류 반환 |
| 서로 다른 학생 10명·마지막 한 자리 | 성공 1건, COURSE_FULL 9건, 최종 신청 1건 |
| 동일 학생의 동시 신청 | 동일 강좌·동일 과목·18학점·시간 충돌 보호, 독립된 두 강좌는 모두 성공 |
| 잠금 대기 후 조회 | 앞선 트랜잭션의 신청 커밋이 학점 검사·정원 COUNT에 반영 |
| 잠금 SQL 및 순서 | 학생 → 신청 내역 조회 → 강좌 → 정원 COUNT → INSERT, READ COMMITTED |
| 실패 후 회복 | 학생·강좌 잠금 대기 초과, 실제 교착 오류, INSERT 실패 시 롤백 및 후속 신청 성공 |
| 시간 경계 | 인접 시간 허용, 다른 요일 허용, 복수 수업 시간 중 충돌 검출 |
| 연결 재사용 | 트랜잭션 종료 후 lock_timeout이 원래 값으로 복구 |

교착 테스트는 테스트용 트랜잭션에서만 잠금 순서를 뒤집어 PostgreSQL 오류 처리를 검증한다. 운영 신청 경로는 학생 → 강좌 순서를 유지한다. 동시성 테스트는 불변 조건과 오류 처리를 검증하며 처리량·지연 시간의 성능 평가를 대신하지 않는다. 요청 도착 순서를 보장하는 대기열은 구현하지 않았다. 취소·시간표 조회와 신청·취소 간 경합은 후속 구현 범위이다.

공식 근거: [Spring Data JPA 잠금](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [PostgreSQL 18 행 잠금](https://www.postgresql.org/docs/18/explicit-locking.html), [트랜잭션 잠금 대기 설정](https://www.postgresql.org/docs/18/runtime-config-client.html). 2026-09-25 확인.

전체 회귀 검증: 2026-09-25에 ./gradlew test bootJar --no-daemon을 실행하여 31개 테스트 클래스의 541개 테스트가 통과했다(실패·오류·건너뜀 0). bootJar 성공, 실행 JAR에서 테스트 코드 제외를 확인했다. 종료 단계에서 Hikari 연결 재시도 경고가 출력됐으며 빌드와 테스트는 정상 종료했다.
