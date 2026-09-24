# 교수 목록 API

상태: 입력 검증·페이지 응답과 실제 목록 API 구현·검증

[요구사항 D38, D40~D42, D90~D92](REQUIREMENTS.md)에 따른 계약이다. 공통 코드의 역할·검증 범위는 [목록 공통 입력·페이지 응답](LIST_QUERY_COMMON.md)을 따른다.

로그인 후에만 조회할 수 있다. Authorization: Bearer <accessToken>으로 JWT를 전달하며, 토큰 누락·위조·만료는 401이다(D50~D51). 인증을 먼저 확인하며 401 오류는 누락 TOKEN_REQUIRED·만료 TOKEN_EXPIRED·그 밖의 실패 INVALID_TOKEN으로 구분한다. [인증 명세](AUTH_API.md)를 따른다.

## 요청

```http
GET /professors?departmentId=10&page=0&size=20
```

| 파라미터 | 필수 | 생략 시 | 조건 |
|---|---|---|---|
| departmentId | 아니오 | 학과 필터 미적용 | 1~9223372036854775807(Long.MAX_VALUE), 존재하는 학과 ID |
| page | 아니오 | 0 | 0~2147483647(Integer.MAX_VALUE), page × size도 이 상한 이하 |
| size | 아니오 | 20 | 1~100의 정수 |

교수의 소속 학과로 필터링하고 교수 ID 오름차순으로 조회한다. 담당 강좌의 학과는 필터 기준이 아니다.

## 성공 응답

HTTP 200. 아래는 조건에 맞는 교수가 한 명인 경우의 명세 예시이며 실제 서버 실행 결과가 아니다. 초기 교수 데이터 100명 이상이라는 요구사항은 별도로 유지한다.

```json
{
  "content": [
    {
      "id": 31,
      "name": "박구조",
      "departmentName": "컴퓨터공학부"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- 각 항목은 교수 식별자 id, 이름 name, 소속 학과명 departmentName을 제공한다. 학과 ID와 department 객체는 반환하지 않는다.
- totalElements는 필터에 맞는 전체 교수 수, totalPages는 해당 건수를 size로 나눈 값을 올림한 수이다.
- 조건에 맞는 교수가 없으면 content=[], totalElements=0, totalPages=0이다.
- 결과 범위 밖의 유효한 페이지는 빈 목록과 실제 전체 건수·전체 페이지 수를 반환한다. 요청 page·size는 유지하며 마지막 페이지에서도 size를 실제 항목 수로 바꾸지 않는다.
- 교수·학과 ID는 BIGINT·Java Long(D76)이며 목록 쿼리의 departmentId 입력 상한은 D90을 따른다. 예시는 숫자 ID를 사용한다.

## 오류 처리

1. 지원하지 않는 파라미터 이름을 먼저 검사하며 여러 개면 이름 오름차순의 첫 오류 하나만 400 INVALID_PARAMETER로 반환한다. grade도 지원하지 않는 이름이다.
2. departmentId → page → size 순서로 각 항목의 중복 → 형식·범위를 검사한다. URL 파라미터 나열 순서와 무관하게 첫 오류 하나만 반환한다. 기본값과 필터 미적용은 파라미터를 생략한 경우에만 적용한다.
3. 모든 개별 항목이 유효한 뒤 (long) page * size를 계산하여 Integer.MAX_VALUE를 초과하면 400 INVALID_PARAMETER로 거절한다.
4. 모든 입력이 유효하고 departmentId가 있으면 학과 존재 여부를 확인한다. 존재하지 않으면 404 DEPARTMENT_NOT_FOUND를 반환한다.
5. 유효한 요청은 목록·전체 건수를 조회한다. 존재하는 학과에 교수가 없으면 200과 빈 목록이다.

숫자 값은 ASCII 0~9만 허용하며 선행 0을 허용한다. 앞뒤 공백·부호·빈 값·소수·지수 표기·그 밖의 문자와 범위 위반은 400이다. 같은 파라미터의 중복은 값이 같아도 거절하며 departmentId·page·size 외의 이름도 거절한다. 입력 오류 시 학과 존재·목록·전체 건수를 조회하지 않는다.

오류 본문은 code와 message를 사용한다. 입력 오류의 message는 잘못된 항목과 허용 조건을 설명한다.

```json
{
  "code": "INVALID_PARAMETER",
  "message": "size는 1 이상 100 이하여야 합니다."
}
```

```json
{
  "code": "DEPARTMENT_NOT_FOUND",
  "message": "해당 학과를 찾을 수 없습니다."
}
```

## 조회 구현

Controller는 공통 파서 검증을 완료한 뒤 QueryService를 호출한다. 학과 조건이 있으면 존재를 확인하고, Repository의 JPQL DTO 조회로 목록과 동일 조건의 전체 건수를 구한다. 응답에 필요한 필드만 선택하고 소속 학과를 조인하여 항목별 추가 조회를 피한다.

D92에 따라 서비스에 읽기 전용 READ COMMITTED를 적용하고 명시적 행 잠금을 사용하지 않는다. 각 SQL은 실행 시점의 커밋된 데이터를 읽으며, 동시 변경이 있으면 목록과 전체 건수의 조회 시점이 다를 수 있다. 현재 학생·교수 정보 수정 API는 제공하지 않는다.

## 후속 결정

- 세부 인가 정책

검증 시나리오: 요구사항 T46~T47, T50~T57과 공통 코드의 T122~T125. 공통 코드의 실행 결과는 [공통 구현](LIST_QUERY_COMMON.md)을 따른다. 실제 목록 HTTP·PostgreSQL 검증과 T126~T128의 범위는 [학생·교수 목록 연결](LIST_QUERY_COMMON.md#학생교수-목록-연결)을 따른다.
