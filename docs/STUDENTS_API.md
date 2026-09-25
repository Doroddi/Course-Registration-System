# 학생 목록 API

상태: 입력 검증·페이지 응답과 실제 목록 API 구현·검증

[요구사항 D32~D37, D39~D40, D42, D90~D92](REQUIREMENTS.md)에 따라 확정된 요청과 응답 구조를 정리한다. 공통 코드의 역할·검증 범위는 [목록 공통 입력·페이지 응답](LIST_QUERY_COMMON.md)을 따른다.

로그인 후에만 조회할 수 있다. Authorization: Bearer <accessToken>으로 JWT를 전달하며, 토큰 누락·위조·만료는 401이다(D50~D51). 인증을 먼저 확인하며 401 오류는 누락 TOKEN_REQUIRED·만료 TOKEN_EXPIRED·그 밖의 실패 INVALID_TOKEN으로 구분한다. [인증 명세](AUTH_API.md)를 따른다.

## 요청

```http
GET /students?departmentId=10&grade=2&page=0&size=20
```

| 파라미터 | 필수 | 기본값 | 확정된 조건 |
|---|---|---|---|
| departmentId | 아니오 | 필터 미적용 | 학생 소속 학과 ID, 1~9223372036854775807(Long.MAX_VALUE), 존재하는 학과 |
| grade | 아니오 | 필터 미적용 | 학생 학년, 1~4의 정수 |
| page | 아니오 | 0 | 0~2147483647(Integer.MAX_VALUE), page × size도 이 상한 이하 |
| size | 아니오 | 20 | 1~100의 정수 |

학과·학년 조건에 맞는 학생을 학번 오름차순으로 조회한다. 두 조건을 함께 지정하면 모두 만족해야 한다. 조건을 생략하면 해당 필터를 적용하지 않으며, 둘 다 생략하면 전체 학생을 조회한다. 대상 학기 필터는 제공하지 않는다.

## 응답 구조

정상 응답은 HTTP 200과 JSON 본문을 반환한다.

| 필드 | 의미 |
|---|---|
| content | 해당 페이지 학생 목록 |
| page | 적용된 페이지 번호 |
| size | 적용된 페이지 크기 |
| totalElements | 적용한 학과·학년 조건에 맞는 전체 학생 수 |
| totalPages | 조건에 맞는 전체 학생 수를 페이지 크기로 나눈 값을 올림 |
| content[].studentNumber | 연도 4자리 + 발급 당시 학과 코드 2자리 + 학생 번호 3자리인 9자리 JSON 숫자 |
| content[].name | 학생 이름 |
| content[].grade | 학년, 1~4의 정수 |
| content[].departmentName | 학생의 소속 학과명 문자열 |

학번은 DB INTEGER, Java Integer로 사용한다. D33에 따라 연도 4자리 + 발급 당시 학과 코드 2자리 + 학생 번호 3자리이며 초기 데이터는 연도 2020~2026, 학생 번호 100~499로 생성한다. 전과하더라도 학번은 변경하지 않고 현재 소속 학과를 기준으로 조회한다. 연도의 의미와 현재 학년의 관계는 후속 확정한다. 학과 ID는 BIGINT·Java Long(D76)이며 목록 쿼리의 departmentId 입력 상한은 D90을 따른다.

### 조회 조건에 맞는 학생이 1명인 예시

실제 서버 실행 결과가 아닌 응답 구조 예시이다. 학생 10,000명 이상이라는 초기 데이터 요구는 별도로 유지한다.

```json
{
  "content": [
    {
      "studentNumber": 202610100,
      "name": "김민준",
      "grade": 2,
      "departmentName": "컴퓨터공학부"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 빈 목록과 오류 처리

- 유효한 조건에 맞는 학생이 없으면 HTTP 200, content=[], totalElements=0, totalPages=0을 반환한다.
- 결과 범위 밖의 유효한 페이지도 HTTP 200과 빈 목록을 반환하며 요청 페이지·크기 및 전체 건수·전체 페이지 수를 유지한다.
- page와 size를 생략한 경우에만 각각 0과 20을 적용한다. departmentId·grade도 생략한 조건만 필터를 적용하지 않는다.
- 모든 숫자 값은 ASCII 0~9만 허용하며 선행 0을 허용한다. 앞뒤 공백·부호·빈 값·소수·지수 표기·그 밖의 문자는 400 INVALID_PARAMETER로 거절한다.
- departmentId는 1~Long.MAX_VALUE, page는 0~Integer.MAX_VALUE, size는 1~100 범위를 벗어나면 400 오류이다.
- grade가 1~4 범위 밖이면 400 INVALID_PARAMETER로 거절한다. grade 생략 시 학년 필터를 적용하지 않는다.
- 같은 파라미터가 중복되면 값이 같아도 거절한다. departmentId·grade·page·size 외의 파라미터도 거절한다.
- 지원하지 않는 파라미터 이름을 먼저 검사하며 여러 개면 이름 오름차순의 첫 오류 하나만 반환한다. 이후 departmentId → grade → page → size 순서로 각 항목의 중복 → 형식·범위를 검사한다. URL 파라미터 나열 순서와 무관하다.
- 모든 개별 항목이 유효한 뒤 (long) page * size를 계산하여 Integer.MAX_VALUE를 초과하면 400 INVALID_PARAMETER로 거절한다.
- 입력 오류 시 학과 존재·학생 목록·전체 건수를 조회하지 않는다. 모든 입력이 유효한 경우에만 지정한 학과의 존재를 확인하며, 없으면 404 DEPARTMENT_NOT_FOUND와 해당 학과를 찾을 수 없습니다. 메시지를 반환한다.

오류 본문은 code와 message이다. 입력 오류는 code=INVALID_PARAMETER와 잘못된 항목·허용 조건을 설명하는 message를 반환한다. 아래는 size=101 요청의 HTTP 400 응답 예시이다.

```json
{
  "code": "INVALID_PARAMETER",
  "message": "size는 1 이상 100 이하여야 합니다."
}
```

## 조회 구현

Controller는 공통 파서 검증을 완료한 뒤 QueryService를 호출한다. 학과 조건이 있으면 존재를 확인하고, Repository의 JPQL DTO 조회로 목록과 동일 조건의 전체 건수를 구한다. 응답에 필요한 필드만 선택하고 소속 학과를 조인하여 항목별 추가 조회를 피한다.

D92에 따라 서비스에 읽기 전용 READ COMMITTED를 적용하고 명시적 행 잠금을 사용하지 않는다. 각 SQL은 실행 시점의 커밋된 데이터를 읽으며, 동시 변경이 있으면 목록과 전체 건수의 조회 시점이 다를 수 있다. 현재 학생·교수 정보 수정 API는 제공하지 않는다.

## 후속 결정

- 세부 인가 정책

검증 시나리오: 요구사항 T36~T45, T48~T54와 공통 코드의 T122~T125. 공통 코드의 실행 결과는 [공통 구현](LIST_QUERY_COMMON.md)을 따른다. 실제 목록 HTTP·PostgreSQL 검증과 T126~T128의 범위는 [학생·교수 목록 연결](LIST_QUERY_COMMON.md#학생교수-목록-연결)을 따른다.
