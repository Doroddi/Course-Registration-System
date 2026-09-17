# 교수 목록 API

상태: 명세 초안 / 서버 미구현

[요구사항 D38, D40~D42](REQUIREMENTS.md)에 따른 계약이다.

로그인 후에만 조회할 수 있다. Authorization: Bearer <accessToken>으로 JWT를 전달하며, 토큰 누락·위조·만료는 401이다(D50~D51). 오류 본문과 인증·입력 검사의 우선순위는 미정이다.

## 요청

```http
GET /professors?departmentId=10&page=0&size=20
```

| 파라미터 | 필수 | 생략 시 | 조건 |
|---|---|---|---|
| departmentId | 아니오 | 학과 필터 미적용 | 양의 정수이며 존재하는 학과 ID |
| page | 아니오 | 0 | 0 이상의 정수 |
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
- 교수·학과 ID는 BIGINT·Java Long(D76)이며 API 표현 상한은 후속 확정한다. 예시는 숫자 ID를 사용한다.

## 오류 처리

1. departmentId → page → size 순서로 형식·범위를 검사하고 첫 오류 하나를 400 INVALID_PARAMETER로 반환한다. URL 파라미터 나열 순서와 무관하다. 기본값과 필터 미적용은 파라미터를 생략한 경우에만 적용한다.
2. 모든 입력이 유효하고 departmentId가 있으면 학과 존재 여부를 확인한다. 존재하지 않으면 404 DEPARTMENT_NOT_FOUND를 반환한다.
3. 유효한 요청은 목록·전체 건수를 조회한다. 존재하는 학과에 교수가 없으면 200과 빈 목록이다.

빈 문자열·공백만 있는 값·소수·숫자가 아닌 값·범위 위반은 400이다. 같은 파라미터의 중복은 값이 같아도 거절하며 departmentId·page·size 외의 이름도 거절한다. 입력 오류 시 학과 존재·목록·전체 건수를 조회하지 않는다.

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

## 후속 결정

- 숫자 앞뒤 공백·부호·선행 0·표현 상한
- 중복·지원하지 않는 이름·형식 오류가 함께 발생할 때의 메시지 선택 순서
- 세부 인가 정책, 인증 오류 본문·입력 오류와의 우선순위, 목록·전체 건수의 조회 일관성

검증 시나리오: 요구사항 T46~T47, T50~T57. 서버 테스트는 아직 실행하지 않았다.
