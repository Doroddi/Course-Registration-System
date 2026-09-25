# 강좌 목록 API

상태: 강좌 목록 API 구현·검증 / 실제 결과는 아래 구현·검증 절 참조

[요구사항 D16~D31, D40, D90~D91](REQUIREMENTS.md)을 바탕으로 확정된 계약을 정리한다. 공통 코드의 역할·검증 범위는 [목록 공통 입력·페이지 응답](LIST_QUERY_COMMON.md)을 따른다. 조회 일관성과 내부 배열 정렬은 D93을 따른다. 학생 조회는 [학생 목록 API](STUDENTS_API.md)에 정리한다. 교수 조회는 [교수 목록 API](PROFESSORS_API.md)에 정리한다. 신청은 [수강신청 API](ENROLLMENTS_API.md)에 정리한다. [수강취소 API](CANCELLATIONS_API.md)와 [내 시간표 API](TIMETABLE_API.md)는 D70~D72의 확정 계약과 미정 구현 사항을 구분한다.

강좌 목록은 로그인 후에만 조회할 수 있으며 유효한 Bearer JWT가 필요하다(D50). 토큰 누락·위조·만료는 모두 401이며 강좌 데이터를 반환하지 않는다(D51). 아래 성공·입력 오류 사례는 인증이 유효한 경우를 전제로 한다.

## 요청

```http
GET /course-offerings?departmentId=10&page=0&size=20
Authorization: Bearer <accessToken>
```

| 쿼리 파라미터 | 필수 | 생략 시 동작 | 제약 |
|---|---|---|---|
| departmentId | 아니오 | 대상 학기 전체 학과 조회 | 1~9223372036854775807(Long.MAX_VALUE), 존재하는 학과 ID |
| page | 아니오 | 0 | 0~2147483647(Integer.MAX_VALUE), page × size도 이 상한 이하 |
| size | 아니오 | 20 | 1~100의 정수 |

- 신청 대상 연도·학기는 서버 설정을 사용한다. 클라이언트가 다른 학기를 선택하는 기능은 제공하지 않는다.
- 학과 필터는 개설 강좌의 소속 학과에 적용한다.
- 강좌 코드(offeringCode) 오름차순으로 정렬한다. 숫자 크기순으로 비교하고 같은 숫자값은 개설 강좌 ID 오름차순으로 구분한다. 선행 0은 원문에 보존한다.
- 유효한 페이지 번호에 해당하는 결과가 없으면 200과 빈 목록을 반환한다.
- 숫자 값은 ASCII 0~9만 허용하며 선행 0을 허용한다. 앞뒤 공백·부호·빈 값·소수·지수 표기·그 밖의 문자는 400 INVALID_PARAMETER로 거절한다. 예를 들어 size 생략 시 20을 적용하지만 size=는 빈 값 오류로 처리한다.
- departmentId·page·size 외의 파라미터는 400 INVALID_PARAMETER로 거절한다. 예: szie=20.
- 같은 파라미터가 여러 번 나타나면 값이 같아도 400 INVALID_PARAMETER로 거절한다. 예: size=20&size=50, size=20&size=20. 오류 메시지는 해당 항목을 설명하며 DB 조회는 수행하지 않는다.
- 모든 개별 항목이 유효한 뒤 (long) page * size를 계산하여 2147483647을 초과하면 400 INVALID_PARAMETER로 거절한다.

## 성공 응답

HTTP 200, JSON 본문.

| 필드 | 의미 |
|---|---|
| content | 해당 페이지의 개설 강좌 배열 |
| page | 적용된 페이지 번호 |
| size | 적용된 페이지 크기. 마지막 페이지에서도 실제 반환 개수로 바꾸지 않음 |
| totalElements | 대상 학기·학과 조건에 맞는 전체 개설 강좌 수 |
| totalPages | totalElements / size를 올림한 값. 전체 0건이면 0 |

### 강좌 항목

| 필드 | 의미 |
|---|---|
| id | 개설 강좌 식별자. 과목 코드와 구분 |
| name | 강좌명 |
| credits | 강좌 학점 |
| capacity | 정원 |
| enrolled | 현재 신청 인원 |
| schedules | 수업 시간 객체 배열 |
| schedules[].dayOfWeek | MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY |
| schedules[].startTime / endTime | KST 기준 24시간제 HH:mm 문자열. 시·분 각각 두 자리 |
| departmentName | 개설 강좌의 소속 학과명 문자열 |
| professors | 담당 교수의 id·name 객체 배열 |

ID는 BIGINT·Java Long(D76), 학점은 정수 1~6(D77)으로 확정했다. 목록 쿼리의 departmentId 입력 상한은 D90을 따른다. 수업 시간 배열은 월~일 → 시작 시각 → 종료 시각, 교수 배열은 교수 ID 오름차순으로 반환한다(D93).

### 전체 조회 결과가 1건인 예시

대상 학기의 컴퓨터공학부 강좌가 아래 한 건뿐이라고 가정한다. 값은 명세 설명용이며 실제 서버 응답이 아니다.

```json
{
  "content": [
    {
      "id": 11,
      "name": "자료구조",
      "credits": 3,
      "capacity": 44,
      "enrolled": 13,
      "schedules": [
        { "dayOfWeek": "MONDAY", "startTime": "13:00", "endTime": "15:00" },
        { "dayOfWeek": "WEDNESDAY", "startTime": "13:00", "endTime": "15:00" }
      ],
      "departmentName": "컴퓨터공학부",
      "professors": [ { "id": 31, "name": "박구조" } ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 존재하는 학과에 대상 학기 강좌가 없는 경우

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

전체 53건·크기 20에서 page=3을 요청하면 content는 빈 배열이지만 totalElements=53, totalPages=3을 유지한다. 페이지 경계의 안정성 검증은 조회 사이 강좌 데이터가 바뀌지 않는 조건에서 수행한다.

## 오류 응답

인증이 유효한 요청의 입력 검사·조회 순서는 다음과 같다.

1. 지원하지 않는 파라미터 이름을 먼저 검사한다. 여러 개면 이름 오름차순의 첫 오류 하나만 400 INVALID_PARAMETER로 반환한다.
2. departmentId → page → size 순서로 각 항목의 중복 → 형식·범위를 검사하고 첫 오류 하나만 반환한다. 생략된 departmentId는 필터를 적용하지 않으며 page·size 생략 시에는 0·20을 적용한다.
3. 모든 개별 항목이 유효하면 page × size의 offset 상한을 검사한다. 여기까지 오류가 있으면 400 INVALID_PARAMETER를 반환하며 학과·강좌·전체 건수를 조회하지 않는다.
4. 입력이 모두 유효하고 departmentId가 있으면 학과 존재 여부를 확인한다. 없으면 404 DEPARTMENT_NOT_FOUND를 반환한다.
5. 입력과 학과 조건이 유효하면 강좌 목록과 전체 건수를 조회한다. departmentId 생략 시 학과 존재 검사는 필요하지 않다.

쿼리에 파라미터가 나열된 순서와 무관하게 위 검사 순서를 적용한다. departmentId=abc, page=-1, size=101이면 departmentId 오류만 설명한다.

예를 들어 size=101이고 학과 ID도 존재하지 않으면 학과를 조회하기 전에 400으로 응답한다. 보안 필터에서 인증을 먼저 확인하며 인증 실패 시 입력 검증 전에 401을 반환한다.

| 상황 | HTTP 상태 | code |
|---|---|---|
| 토큰 누락·위조·만료 | 401 Unauthorized | TOKEN_REQUIRED / TOKEN_EXPIRED / INVALID_TOKEN — 인증 명세 참조 |
| 음수 page, size 범위 위반, 파라미터 형식 오류·중복·지원하지 않는 이름 | 400 Bad Request | INVALID_PARAMETER |
| 형식은 유효하지만 존재하지 않는 departmentId | 404 Not Found | DEPARTMENT_NOT_FOUND |

400·404 오류 본문은 code와 message를 제공한다. 401 오류는 누락 TOKEN_REQUIRED·만료 TOKEN_EXPIRED·그 밖의 실패 INVALID_TOKEN으로 구분하며 메시지는 [인증 명세](AUTH_API.md)를 따른다. code는 프로그램이 오류 종류를 구분하는 값이며 message는 잘못된 항목과 허용 범위 또는 형식을 설명한다.

### 잘못된 페이지 크기

size=101을 요청하면 HTTP 400과 아래 본문을 반환한다.

```json
{
  "code": "INVALID_PARAMETER",
  "message": "size는 1 이상 100 이하여야 합니다."
}
```

### 존재하지 않는 학과

```json
{
  "code": "DEPARTMENT_NOT_FOUND",
  "message": "해당 학과를 찾을 수 없습니다."
}
```

## 조회 일관성과 정렬

- 공통 인증·오류 계약은 [로그인·JWT 인증](AUTH_API.md)을 따르며 인증을 먼저 검사한다.
- 강좌 코드는 numeric으로 변환해 숫자 크기순으로 비교한다. 예: 001 → 2 → 10. 001과 1처럼 숫자값이 같으면 개설 강좌 ID순으로 구분한다.
- 조회는 D93에 따라 읽기 전용 READ COMMITTED이며 명시적 행 잠금을 사용하지 않는다. SQL별 조회 시점이 달라질 수 있고 표시된 신청 인원은 실제 신청 가능 여부를 보장하지 않는다.

검증 시나리오는 요구사항 T16~T35, T50, T67, T70과 공통 코드의 T122~T125를 참조한다. T16~T35 및 T50의 강좌 조회 사례는 유효한 인증을 전제로 한다. 공통 코드의 실행 결과는 [공통 구현](LIST_QUERY_COMMON.md), 강좌 HTTP·DB 조회 검증은 아래를 따른다. 정원·학점·시간 충돌 보장 및 성능 검증은 후속 구현에서 수행한다.

## 구현·검증

Controller는 공통 파서를 거쳐 QueryService를 호출한다. 서비스는 서버 대상 학기 설정과 선택한 학과 조건을 적용하고 읽기 전용 READ COMMITTED 트랜잭션에서 조회한다. 단일 응답의 모든 조회가 같은 스냅샷이라고 보장하지 않으며 표시된 인원은 신청 성공을 예약하지 않는다.

CourseOfferingListRepository는 강좌 기본 항목과 전체 건수를 먼저 구한다. 해당 페이지의 ID만 대상으로 수업 시간·담당 교수·신청 인원을 각각 묶어 조회한다. 모든 다건 관계를 함께 조인하면 시간 수 × 교수 수 × 신청 수만큼 행이 늘어 페이지 및 인원 집계가 왜곡될 수 있어 조회를 분리했다. 빈 페이지에서는 세 상세 조회를 생략한다. 신청 인원은 Enrollment를 강좌별 COUNT하고 행이 없는 강좌는 0으로 반환한다.

페이지 쿼리에만 native SQL을 사용해 PostgreSQL의 `cast(offering_code as numeric)`을 명시한다. 가변 길이와 선행 0을 허용하는 최대 30자리 코드에 정수 오버플로·부동소수점 오차 없이 숫자 정렬을 적용하기 위한 선택이다. 다른 상세 조회는 JPQL을 사용한다. 별도 목록 Repository로 초기 데이터 검증용 기존 Repository와 역할을 구분한다. 스키마·코드 원문·응답 필드는 변경하지 않는다.

공식 근거: [Spring Data JPA의 native 페이지 쿼리와 명시적 countQuery](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.query-methods.at-query), [PostgreSQL numeric의 정확한 수치 표현](https://www.postgresql.org/docs/18/datatype-numeric.html), [READ COMMITTED의 SQL별 스냅샷](https://www.postgresql.org/docs/18/transaction-iso.html). 기존 Spring Data JPA 4.1.1·PostgreSQL 18.6을 유지하며 의존성 추가는 없다. 정렬용 인덱스 추가 여부는 성능 측정 후 판단한다.

### 실행 결과와 한계

2026-09-23: CourseOfferingListIntegrationTest 19개가 통과했다. 별도 PostgreSQL 18.6 컨테이너에 대상 학기 강좌 107개와 다른 학기 강좌 3개를 만들고 실제 HTTP 요청으로 확인했다. 서버 대상은 기본값과 다른 2025년 1학기로 설정했다. 기존 개발 DB는 사용하지 않았다.

- 001·1·2·10, Long 범위를 넘는 코드, 마지막 자리만 다른 30자리 코드의 정확한 숫자 정렬과 ID 보조 정렬을 확인했다. 모든 페이지를 합쳐 기대 순서·중복·누락 없음과 선행 0 원문 보존을 확인했다.
- 각 대상 강좌에 수업 시간 4개·동명이인 교수 2명을 연결하고 일부 강좌에 학생 2명을 신청시켰다. 시간·교수 배열 순서, KST HH:mm 표현, 강좌 수와 인원 중복 집계 없음, 미신청 인원 0을 확인했다.
- 기본·최대·마지막·범위 밖·최대 offset 페이지, 서버 대상 학기, 강좌 소속 학과 필터, 다른 학기에만 강좌가 있는 학과의 빈 결과와 없는 학과 404를 확인했다.
- 크기 1·100의 가득 찬 페이지에서 목록·전체 건수·시간·교수·신청 집계 SQL 5회, 학과 필터를 지정한 정상 페이지에서 6회를 관찰했다. 범위 밖 페이지는 2회이며 상세 조회는 생략했다. 마지막 페이지처럼 전체 건수가 추론되는 경우 Spring Data가 count 쿼리를 생략할 수 있다.
- 입력 오류 400과 누락·만료·위조 토큰 401에서 Hibernate SQL이 없었고 인증이 입력 오류보다 우선했다. 정상 조회의 Spring 트랜잭션 readOnly·READ_COMMITTED 설정과 명시적 행 잠금 SQL 없음도 확인했다.

동일 실행에서 공통 테스트 68개, 학생·교수 목록 40개, 인증·JWT 설정 157개를 함께 실행해 **총 284개, 실패·오류·건너뜀 0**을 확인했다. bootJar도 성공했으며 운영 코드 포함·테스트용 코드 제외를 확인했다.

```powershell
./gradlew.bat test --tests 'com.doroddi.courseregistration.common.api.*' --tests 'com.doroddi.courseregistration.student.auth.*' --tests '*JwtConfigurationTest' bootJar --no-daemon
```

빌드 시간은 6분 48초로, 컴파일·컨테이너 준비·테스트를 포함한다. API 응답 성능이나 전체 서버 준비 시간을 측정한 값은 아니다. 전체 프로젝트 테스트 재실행, 부하 테스트, 동시 신청·취소 중 조회 실험은 이번 범위에 포함하지 않았다. READ COMMITTED에서 여러 SQL의 동일 스냅샷 보장은 제공하지 않는다.
