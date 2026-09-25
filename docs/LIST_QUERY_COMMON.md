# 목록 공통 입력 검증·페이지 응답

상태: 공통 코드 구현·68개 테스트 통과 / 학생·교수 목록 API 연결 / 강좌 목록 API 연결

학생·교수·강좌 목록의 공통 계약은 [요구사항 D90~D91](REQUIREMENTS.md)을 따른다. 학생·교수 목록은 Controller·Service·DB 조회와 학과 존재 확인을 연결했으며, 강좌 목록도 연결했으며 구현·검증은 [강좌 API](API.md#구현검증)에 구분한다. 학생·교수 조회의 격리 수준은 D92를 따른다.

## 역할과 의도

| 구성 | 역할 |
|---|---|
| ListQueryParser | @Component 공통 파서. parseStudents·parseProfessors·parseCourseOfferings가 MultiValueMap<String, String>을 받아 숫자 형식·중복·미지원 이름·범위를 검증한다. DB 접근은 하지 않는다. |
| ListQuery | 검증된 값의 record. Long departmentId, Short grade, int page, int size를 보관한다. 생략한 필터는 null이며 grade는 학생 목록에만 적용한다. |
| PageResponse<T> | List<T> content, int page, int size, long totalElements, int totalPages의 record. 정적 from(Page<T>)으로 Page 메타데이터를 그대로 옮긴다. |

파서는 기존 InvalidParameterException을 발생시키며 기존 공통 예외 처리기가 400 INVALID_PARAMETER와 code·message 본문으로 변환한다. Controller에서 숫자로 자동 변환하기 전에 모든 파라미터와 중복 값을 받아야 이 검증 순서를 유지할 수 있다. Spring MVC는 이름을 지정하지 않은 @RequestParam MultiValueMap<String, String>으로 요청 파라미터를 받을 수 있다([공식 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html)).

## 입력 규칙

숫자 값은 ASCII 0~9만 허용하며 선행 0은 허용한다. 앞뒤 공백·부호·빈 값·소수·지수 표기·ASCII 이외 숫자는 거절한다. 생략과 빈 값은 구분한다.

| 항목 | 허용 범위 | 생략 시 |
|---|---|---|
| departmentId | 1~9223372036854775807(Long.MAX_VALUE) | 학과 필터 미적용 |
| grade | 학생 목록에서만 1~4 | 학년 필터 미적용 |
| page | 0~2147483647(Integer.MAX_VALUE) | 0 |
| size | 1~100 | 20 |

검사는 지원하지 않는 이름 → departmentId → grade(학생만) → page → size → offset 순서이다. 미지원 이름이 여러 개면 이름 오름차순의 첫 오류를 선택한다. 각 항목에서는 중복을 먼저, 형식·범위를 다음으로 검사하며 같은 값의 중복도 거절한다. URL 나열 순서와 무관하게 첫 오류 하나만 반환한다.

개별 항목이 모두 유효한 뒤 (long) page * size가 Integer.MAX_VALUE 이하인지 검사한다. JPA의 [Query.setFirstResult(int)](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/query#setFirstResult(int))가 받는 offset 범위에 맞추기 위한 프로젝트 입력 제한이며, 곱셈부터 long으로 계산해 오버플로를 피한다. 이 검사까지 모두 통과한 요청만 후속 학과 존재 확인·목록·전체 건수 조회로 진행한다.

## 페이지 응답

PageResponse.from은 [Spring Data Page](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Page.html)의 content·number·size·totalElements·totalPages를 보존한다. 마지막 페이지의 size를 항목 수로 바꾸거나, 범위 밖 페이지의 빈 content를 보고 전체 건수를 0으로 재계산하지 않는다. 예를 들어 전체 53건·size=20·page=3이면 content=[]이지만 totalElements=53, totalPages=3을 유지한다.

content는 List.copyOf로 불변 복사하여 원본 목록 변경에 영향을 받지 않게 한다. 항목 자체를 깊은 복사하는 것은 아니므로 목록 항목은 응답 DTO로 구성한다. Spring Data Commons 4.1.1의 기존 Page API를 사용하며 추가 의존성이나 버전 변경은 없다.

## 검증 범위

2026-09-20: [요구사항 T122~T125](REQUIREMENTS.md)에 대응하는 공통 테스트 68개가 통과했다. 파서 단위 57개, standalone MockMvc 바인딩·오류 응답 5개, PageResponse 6개이며 실패·오류·건너뜀은 모두 0이다. 세 파서의 기본값·숫자 형식·경계·중복·오류 순서·offset, 기존 처리기의 HTTP 400 변환, 페이지 메타데이터와 content 불변성을 확인했다.

당시 검증 명령은 `./gradlew.bat test --tests 'com.doroddi.courseregistration.common.api.*' bootJar --no-daemon`이다. 최종 재검증은 49초에 성공했고 bootJar는 UP-TO-DATE였다. 직전 동일 범위 검증에서 실행 JAR를 생성했으며 운영 코드 3개 포함·새 테스트 및 probe 제외를 확인했다. 재실행 건수는 합산하지 않는다.

공통 코드 검증은 실제 목록 API의 인증·DB 조회·학과 부재 404·필터·정렬·집계 검증을 대신하지 않는다. 학생·교수의 추가 검증은 아래에 구분한다. 강좌 목록도 D93에 따라 읽기 전용 READ COMMITTED로 확정했고 시간·교수 배열은 시간표와 같은 정렬을 적용한다. 강좌 코드는 숫자 크기순으로 비교한다(D18). 추가 구현·검증은 [강좌 목록 API](API.md#구현검증)에 구분한다.

## 학생·교수 목록 연결

2026-09-22: GET /students와 GET /professors에 인증 → 공통 입력 검증 → 학과 존재 확인 → 목록·전체 건수 → PageResponse 흐름을 연결했다. 학생은 학과·학년 필터와 학번 오름차순, 교수는 본인 소속 학과 필터와 교수 ID 오름차순을 적용한다. 존재하지 않는 학과는 404 DEPARTMENT_NOT_FOUND이며 존재하는 학과의 빈 결과는 200이다.

Repository는 JPQL 생성자 표현식으로 StudentListItem·ProfessorListItem record를 직접 조회한다. 학생 엔티티 전체를 불러오지 않아 비밀번호 해시를 읽지 않으며, 학과명도 조인으로 한 번에 가져온다. 목록과 명시적 countQuery에 동일한 필터를 사용한다. Spring Data는 마지막 페이지처럼 목록만으로 전체 건수를 알 수 있는 경우 count 쿼리를 생략할 수 있다.

QueryService는 D92의 읽기 전용 READ COMMITTED 경계를 담당한다. 입력 검증은 서비스 호출 전에 완료하여 잘못된 입력이 DB 조회로 이어지지 않게 한다. readOnly는 조회 의도와 최적화 설정이며 모든 쓰기를 막는 접근 제어 수단으로 사용하지 않는다. PostgreSQL READ COMMITTED는 SQL별 시점이므로 응답 전체가 하나의 스냅샷이라고 보장하지 않는다.

기존 Spring Data JPA 4.1.1과 PostgreSQL 18 구성을 유지하고 추가 의존성은 도입하지 않았다. 공식 근거: [DTO projection](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html), [서비스 트랜잭션 경계와 readOnly](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html), [PostgreSQL READ COMMITTED](https://www.postgresql.org/docs/18/transaction-iso.html).

### 실제 검증 결과

2026-09-22: PeopleListIntegrationTest 40개가 통과했다. 별도 PostgreSQL 18.6 컨테이너에 학생 10,000명·교수 100명을 구성하고 실제 로그인에서 발급받은 JWT로 HTTP 요청을 보냈다. 기존 개발 DB는 사용하지 않았다.

- 기본값·최대 크기, 개별·조합 필터, 학년 경계, 학번·교수 ID 정렬, 인접 페이지 중복 없음, 마지막·범위 밖·최대 offset 페이지와 빈 결과를 확인했다.
- 학생의 현재 소속과 교수 본인의 소속으로 필터링하며, 교수 동명이인을 별도 항목으로 반환함을 확인했다.
- 학과 부재 404, 입력 오류 400, 토큰 누락·위조·만료 401과 인증 우선순위를 확인했다. 입력 오류·인증 실패에서 Repository 호출과 Hibernate SQL이 없었다.
- 크기 20·100의 가득 찬 페이지에서 SQL은 목록·집계 2회였다. 학과를 지정한 정상 조회는 존재 확인을 더해 3회, 없는 학과는 존재 확인 1회였다. 학생 비밀번호 해시 선택과 명시적 행 잠금 SQL은 없었고, 실행 시 Spring 트랜잭션의 readOnly=true·READ_COMMITTED 설정을 관찰했다.
- open-in-view=false인 테스트 환경에서도 DTO 직렬화가 완료됐다. JAR에 새 운영 코드가 포함되고 테스트·SQL 관찰용 코드가 제외됨을 확인했다.

동일 실행에서 공통 테스트 68개와 인증·JWT 설정 회귀 테스트 157개를 함께 실행해 **총 265개, 실패·오류·건너뜀 0**을 확인했고 bootJar 생성도 성공했다. 실행 명령은 아래와 같으며 빌드는 3분 25초였다. 이 시간은 컨테이너 준비·컴파일·테스트를 포함한 빌드 시간으로 API 응답 시간이나 서버 준비 시간 측정값이 아니다.

```powershell
./gradlew.bat test --tests 'com.doroddi.courseregistration.common.api.*' --tests 'com.doroddi.courseregistration.student.auth.*' --tests '*JwtConfigurationTest' bootJar --no-daemon
```

이번 검증은 전체 프로젝트 테스트 재실행이나 부하 테스트가 아니다. 동시 정보 변경 중 목록·집계가 같은 시점을 보장하는지 검증하지 않으며, D92에 따라 그러한 보장도 제공하지 않는다. 강좌 목록·신청 동시성·전체 서버 준비 시간은 후속 검증 대상이다.
