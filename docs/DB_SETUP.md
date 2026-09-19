# DB 스키마와 JPA 검증

## 구현 범위

Flyway V1~V9로 학과·교수·학생·과목·개설 강좌·수업 시간·강의 담당·수강신청의 8개 테이블을 관리한다. 각 테이블의 엔티티와 Repository를 구현했다. V8은 수업 종료 시각을 24:00 미만으로 제한하는 추가 마이그레이션이다.

Spring Data JPA, Flyway, PostgreSQL JDBC 드라이버, Lombok, Testcontainers를 사용하며 라이브러리 버전은 Spring Boot 4.1.1 BOM으로 관리한다. Flyway가 DDL을 적용하고 JPA는 ddl-auto=validate로 매핑을 확인한다. schema.sql/data.sql 자동 실행은 사용하지 않는다.

생성 ID는 BIGINT IDENTITY이며 학번·과목 코드는 직접 부여한다. 강의 담당은 EmbeddedId와 MapsId로 교수·개설 강좌 복합 키를 매핑한다. 관계는 단방향 ManyToOne(LAZY)이며 부모 삭제는 FK로 제한한다. 수강취소의 저장 동작은 신청 행 삭제이며 재신청 시 새로운 ID가 생성된다.

## Repository 테스트 실행

JDK 25와 실행 중인 Docker 엔진이 필요하다. 최초 실행에는 Gradle 의존성과 postgres:18.6 등 테스트 이미지 다운로드가 필요할 수 있다.

```powershell
.\gradlew.bat test --tests '*RepositoryTest'
```

macOS/Linux에서는 ./gradlew를 사용한다. Repository 테스트는 ServiceConnection으로 별도 PostgreSQL 컨테이너에 연결하므로 개발 DB의 실행이나 DB_PASSWORD 설정이 필요하지 않다. Flyway V1~V9가 빈 테스트 DB에 적용되고 JPA 매핑 검증 후 테스트가 실행된다. 테스트별 트랜잭션은 롤백하며 시퀀스 번호의 빈 구간은 허용한다.

전체 test 실행에 포함되는 ApplicationStartupTest는 아직 개발 DB와 환경 변수를 사용한다. 따라서 Repository 테스트 130개 성공을 전체 테스트 성공으로 표현하지 않는다.

## 개발 서버와 전체 테스트 실행

Docker Desktop과 JDK 25를 준비하고 JAVA_HOME을 설정한다. .env.example을 .env로 복사해 개발용 POSTGRES_PASSWORD를 설정한다. .env는 Git에 포함하지 않는다.

```powershell
docker compose up -d --wait postgres
$localDbSettings = ConvertFrom-StringData (Get-Content .env -Raw)
$env:DB_PASSWORD = $localDbSettings.POSTGRES_PASSWORD
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$($localDbSettings.POSTGRES_PORT)/course_registration"
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

이 .env 읽기 예제는 프로젝트의 단순 KEY=VALUE 형식을 대상으로 한다. Spring Boot는 .env를 자동으로 읽지 않으므로 DB_PASSWORD를 별도로 전달한다. IntelliJ 실행 설정에도 같은 환경 변수가 필요하다. 기본 DB_USERNAME은 course_app이다.

개발 서버 및 ApplicationStartupTest 기동 시 지정된 DB에 마이그레이션이 적용된다. 이미 적용한 SQL은 수정하지 않고 새로운 버전의 마이그레이션으로 변경한다. Repository 테스트는 새 DB 생성 경로를 검증하며, 기존 데이터가 있는 개발 DB의 업그레이드 성공까지 보장하지 않는다.

/health와 업무 API는 아직 미구현이다. PostgreSQL 컨테이너 healthy와 데이터 생성·API 준비 완료는 서로 다른 상태이다.

## 실제 검증 결과

2026-09-19, Java 25.0.4.1·Gradle 9.7.1·PostgreSQL 18.6에서 다음 명령을 실행했다.

```powershell
.\gradlew.bat test --tests '*RepositoryTest' --no-daemon
```

| 테스트 클래스 | 실행 수 | 주요 검증 |
|---|---:|---|
| DepartmentRepositoryTest | 7 | 학과 저장·조회, 이름 유일성·필수값·길이 |
| ProfessorRepositoryTest | 6 | 소속 학과, 동명이인, FK·삭제 제한 |
| StudentRepositoryTest | 26 | 학번·학년 경계값, 이름·해시 필수값·길이, 학과 관계 |
| SubjectRepositoryTest | 12 | 숫자 코드, 선행 0, 길이·PK 제약 |
| CourseOfferingRepositoryTest | 34 | 학기·연도·학점·정원, 코드 재사용·중복, 부모 관계 |
| CourseScheduleRepositoryTest | 32 | 수업 시간·요일·24:00 거절, 복합 키, 공동 강의, 삭제 제한 |
| EnrollmentRepositoryTest | 13 | 중복 신청, 삭제·재신청, 새 ID, 부모 보존, IDENTITY·CACHE 1·인덱스 |
| 합계 | 130 | 실패·오류·건너뜀 모두 0 |

보고서는 build/reports/tests/test/index.html, XML 결과는 build/test-results/test에 생성된다. 이 경로의 결과는 다음 테스트 실행 때 갱신된다. 파라미터화 테스트의 각 입력 사례를 실행 수에 포함한다.

## 검증의 한계와 다음 단계

- 인증, 업무 API, 초기 데이터 생성, 정원·18학점·동일 과목·시간 충돌 검사와 동시성은 아직 구현·검증하지 않았다.
- 강좌의 수업 시간·담당 교수 최소 1개는 FK만으로 보장하지 않는다. 데이터 생성 및 강좌 구성 검증에서 확인한다.
- 직접 부여한 ID의 Repository.save는 merge가 될 수 있다. PK 중복 INSERT 거절은 JDBC로 별도 검증했으며 학생 생성 요청의 중복 처리 정책을 구현한 것은 아니다.
- 테스트의 비밀번호 해시 문자열은 저장 검증용 값이다. 실제 비밀번호 해시 생성·검증과 응답 DTO의 해시 제외는 인증/API 단계에서 검증한다.
- 지연 로딩은 현재 Hibernate 환경에서 확인했다. 목록 조회의 SQL 개수·성능이나 잠금 SQL은 측정하지 않았다.
- 인덱스 존재·대상 컬럼을 확인했으며 실행 계획과 성능 효과는 미검증이다.
- 현재 130개 실행에는 ApplicationStartupTest가 포함되지 않는다. 전체 테스트와 독립 JAR 서버 기동 결과는 별도로 확인한다.

## 공식 근거

- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html): 컨테이너 생명주기와 ServiceConnection.
- [Spring Data JPA 저장 동작](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html): persist·merge 및 신규 엔티티 판단.
- [PostgreSQL 제약조건](https://www.postgresql.org/docs/18/ddl-constraints.html): PK·UNIQUE·CHECK·FK.
- [PostgreSQL IDENTITY](https://www.postgresql.org/docs/18/ddl-identity-columns.html): 자동 ID와 명시 ID 입력 제한.

[스키마 AI 리뷰](reviews/schema-jpa-ai-review.md)와 [PR 본문 초안](reviews/schema-jpa-pr.md)에 검토 결과와 변경 범위를 정리한다.

## 패키징 검증

2026-09-19: gradlew.bat bootJar --no-daemon 성공. 실행 JAR 생성까지 확인했으며 java -jar 서버 기동은 별도 미검증이다.
