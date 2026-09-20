# 학생 목록 API

상태: 명세 초안 / 서버 미구현

[요구사항 D32~D37, D39~D40, D42](REQUIREMENTS.md)에 따라 확정된 요청과 응답 구조를 정리한다. 확정된 오류 정책도 함께 정리한다.

로그인 후에만 조회할 수 있다. Authorization: Bearer <accessToken>으로 JWT를 전달하며, 토큰 누락·위조·만료는 401이다(D50~D51). 인증을 먼저 확인하며 401 오류는 UNAUTHORIZED와 "인증이 필요합니다."를 반환한다. [인증 명세](AUTH_API.md)를 따른다.

## 요청

```http
GET /students?departmentId=10&grade=2&page=0&size=20
```

| 파라미터 | 필수 | 기본값 | 확정된 조건 |
|---|---|---|---|
| departmentId | 아니오 | 필터 미적용 | 학생 소속 학과 ID, 양의 정수이며 존재하는 학과 |
| grade | 아니오 | 필터 미적용 | 학생 학년, 1~4의 정수 |
| page | 아니오 | 0 | 0 이상의 정수, 첫 페이지 0 |
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
| content[].studentNumber | 연도 4자리 + 뒤 5자리로 구성된 9자리 JSON 숫자 |
| content[].name | 학생 이름 |
| content[].grade | 학년, 1~4의 정수 |
| content[].departmentName | 학생의 소속 학과명 문자열 |

학번은 DB INTEGER, Java Integer로 사용한다. 연도의 의미·허용 범위와 뒤 5자리의 부여 규칙은 미정이다. 아래 학번은 형식 설명용 예시이며 일련번호 규칙을 확정한 것이 아니다. 학과 ID는 BIGINT·Java Long(D76)이며 API 표현 상한은 후속 확정한다.

### 조회 조건에 맞는 학생이 1명인 예시

실제 서버 실행 결과가 아닌 응답 구조 예시이다. 학생 10,000명 이상이라는 초기 데이터 요구는 별도로 유지한다.

```json
{
  "content": [
    {
      "studentNumber": 202600001,
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
- page와 size를 생략한 경우에만 각각 0과 20을 적용한다. 빈 문자열·공백만 있는 값·소수·숫자가 아닌 값은 400 오류이다.
- departmentId·grade도 빈 문자열·공백만 있는 값·소수·숫자가 아닌 값을 400으로 거절한다. departmentId는 양의 정수여야 한다.
- page가 음수이거나 size가 1~100 밖이면 400 오류이다.
- grade가 1~4 범위 밖이면 400 INVALID_PARAMETER로 거절한다. grade 생략 시 학년 필터를 적용하지 않는다.
- 같은 파라미터가 중복되면 값이 같아도 거절한다. departmentId·grade·page·size 외의 파라미터도 거절한다.
- departmentId → grade → page → size 순서로 형식·범위를 검사하고 첫 오류 하나만 반환한다. URL 파라미터 나열 순서와 무관하며, 생략한 필터는 검사하지 않는다.
- 입력 오류 시 학과 존재·학생 목록·전체 건수를 조회하지 않는다. 모든 입력이 유효한 경우에만 지정한 학과의 존재를 확인하며, 없으면 404 DEPARTMENT_NOT_FOUND와 해당 학과를 찾을 수 없습니다. 메시지를 반환한다.

오류 본문은 code와 message이다. 입력 오류는 code=INVALID_PARAMETER와 잘못된 항목·허용 조건을 설명하는 message를 반환한다. 아래는 size=101 요청의 HTTP 400 응답 예시이다.

```json
{
  "code": "INVALID_PARAMETER",
  "message": "size는 1 이상 100 이하여야 합니다."
}
```

## 후속 결정

- 숫자 앞뒤 공백·부호·선행 0·표현 상한
- 중복·지원하지 않는 이름·형식 오류가 함께 발생할 때의 메시지 선택 순서
- 세부 인가 정책, 목록·전체 건수의 조회 일관성

검증 시나리오: 요구사항 T36~T45, T48~T54. 서버 테스트는 아직 실행하지 않았다.
