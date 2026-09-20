# 강좌 목록 API

상태: 명세 초안 / 서버 미구현

[요구사항 D16~D31, D40](REQUIREMENTS.md)을 바탕으로 확정된 계약을 정리한다. 마지막 절의 미정 사항은 구현 전에 보완한다. 학생 조회는 [학생 목록 API](STUDENTS_API.md)에 정리한다. 교수 조회는 [교수 목록 API](PROFESSORS_API.md)에 정리한다. 신청은 [수강신청 API](ENROLLMENTS_API.md)에 정리한다. [수강취소 API](CANCELLATIONS_API.md)와 [내 시간표 API](TIMETABLE_API.md)는 D70~D72의 확정 계약과 미정 구현 사항을 구분한다.

강좌 목록은 로그인 후에만 조회할 수 있으며 유효한 Bearer JWT가 필요하다(D50). 토큰 누락·위조·만료는 모두 401이며 강좌 데이터를 반환하지 않는다(D51). 아래 성공·입력 오류 사례는 인증이 유효한 경우를 전제로 한다.

## 요청

```http
GET /course-offerings?departmentId=10&page=0&size=20
Authorization: Bearer <accessToken>
```

| 쿼리 파라미터 | 필수 | 생략 시 동작 | 제약 |
|---|---|---|---|
| departmentId | 아니오 | 대상 학기 전체 학과 조회 | 양의 정수이며 존재하는 학과 ID |
| page | 아니오 | 0 | 0 이상의 정수. 표현 상한은 미정 |
| size | 아니오 | 20 | 1~100의 정수 |

- 신청 대상 연도·학기는 서버 설정을 사용한다. 클라이언트가 다른 학기를 선택하는 기능은 제공하지 않는다.
- 학과 필터는 개설 강좌의 소속 학과에 적용한다.
- 강좌명 오름차순, 개설 강좌 ID 오름차순으로 정렬한다.
- 유효한 페이지 번호에 해당하는 결과가 없으면 200과 빈 목록을 반환한다.
- 소수·공백만 있는 값·빈 문자열은 400 INVALID_PARAMETER로 거절한다. 예를 들어 size 생략 시 20을 적용하지만 size=는 빈 값 오류로 처리한다.
- departmentId·page·size 외의 파라미터는 400 INVALID_PARAMETER로 거절한다. 예: szie=20.
- 같은 파라미터가 여러 번 나타나면 값이 같아도 400 INVALID_PARAMETER로 거절한다. 예: size=20&size=50, size=20&size=20. 오류 메시지는 해당 항목을 설명하며 DB 조회는 수행하지 않는다.

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

ID는 BIGINT·Java Long(D76), 학점은 정수 1~6(D77)으로 확정했다. API 숫자의 표현 상한은 후속 확정한다. 아래는 숫자 ID를 사용한 예시이며, 시간·교수 배열의 정렬 순서를 확정한 것은 아니다.

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

1. 파라미터 형식·범위를 departmentId → page → size 순서로 검사하고 첫 오류 하나만 반환한다. 생략된 departmentId는 검사에서 제외하며, page·size 생략 시에는 확정된 기본값을 적용한다. 오류가 있으면 400 INVALID_PARAMETER를 반환하며 학과·강좌·전체 건수를 조회하지 않는다.
2. 입력이 유효하고 departmentId가 있으면 학과 존재 여부를 확인한다. 없으면 404 DEPARTMENT_NOT_FOUND를 반환한다.
3. 입력과 학과 조건이 유효하면 강좌 목록과 전체 건수를 조회한다. departmentId 생략 시 학과 존재 검사는 필요하지 않다.

쿼리에 파라미터가 나열된 순서와 무관하게 위 검사 순서를 적용한다. departmentId=abc, page=-1, size=101이면 departmentId 오류만 설명한다.

예를 들어 size=101이고 학과 ID도 존재하지 않으면 학과를 조회하기 전에 400으로 응답한다. 보안 필터에서 인증을 먼저 확인하며 인증 실패 시 입력 검증 전에 401을 반환한다.

| 상황 | HTTP 상태 | code |
|---|---|---|
| 토큰 누락·위조·만료 | 401 Unauthorized | UNAUTHORIZED / 인증이 필요합니다. |
| 음수 page, size 범위 위반, 파라미터 형식 오류·중복·지원하지 않는 이름 | 400 Bad Request | INVALID_PARAMETER |
| 형식은 유효하지만 존재하지 않는 departmentId | 404 Not Found | DEPARTMENT_NOT_FOUND |

400·404 오류 본문은 code와 message를 제공한다. 401 오류는 UNAUTHORIZED와 "인증이 필요합니다."를 반환한다. code는 프로그램이 오류 종류를 구분하는 값이며 message는 잘못된 항목과 허용 범위 또는 형식을 설명한다.

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

## 구현 전 남은 결정

- 공통 인증·오류 계약은 [로그인·JWT 인증](AUTH_API.md)을 따르며 인증을 먼저 검사한다.
- 숫자 앞뒤 공백·부호·선행 0·표현 상한
- 지원하지 않는 이름·중복·형식 오류가 함께 있을 때의 메시지 선택 순서(D29는 알려진 파라미터의 형식·범위 검사 순서)
- 강좌명 정렬의 DB 문자열 비교 규칙, 시간·교수 배열의 정렬
- 목록과 전체 건수 조회 사이 데이터가 변경될 때의 일관성 범위

검증 시나리오는 요구사항 T16~T35, T50, T67, T70을 참조한다. T16~T35 및 T50의 강좌 조회 사례는 유효한 인증을 전제로 한다. 정원·학점·시간 충돌 보장 및 성능 검증은 후속 구현에서 수행한다.
