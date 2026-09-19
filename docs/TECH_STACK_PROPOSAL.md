# 기술 스택과 물리 데이터 모델 검토

상태: 기술 스택·물리 스키마 확정 / Flyway V1~V9·JPA 매핑 및 Repository 테스트 130개 통과 / 업무 API·동시성 구현 전

확정 정책은 [요구사항](REQUIREMENTS.md)을 따른다. 데이터 접근 방식은 D73, 나머지 기술 스택은 D74로 확정했다. 잠금 적용 방침은 D75를 따르며 주요 키·데이터 범위·JPA 관계·삭제·스키마 관리 방침은 D76~D78로 확정했다. 물리 타입·길이·생성 구문·인증 정보 위치·초기 인덱스는 D79~D83으로 확정했다. 서버 기반 검증은 [실행 기반 기록](BOOTSTRAP.md), 스키마·매핑 검증은 [DB 검증 기록](DB_SETUP.md)을 따른다.

## 확정 기술 구성

| 영역 | 선택 | 프로젝트상 근거 |
|---|---|---|
| 언어 | Java 25 LTS, 프리뷰 기능 미사용 | 신규 구현의 기준 버전을 LTS로 고정 |
| 서버 | Spring Boot 4.1.1, Spring MVC | HTTP 요청과 JPA 트랜잭션을 동기 흐름으로 구성 |
| 빌드 | Gradle Kotlin DSL, Wrapper 9.7.1 | 기존 Wrapper 8.10을 갱신. Java 25 지원 범위를 확인하고 배포 체크섬 고정 |
| DB | PostgreSQL 18.6 | 실제 행 잠금·READ COMMITTED·COUNT 동작을 개발과 테스트에서 동일 DB로 검증 |
| 데이터 접근(확정) | Spring Data JPA | 엔티티 매핑과 영속성 관리를 사용하며 생성 SQL·트랜잭션 동작을 검증 |
| 트랜잭션 | 서비스 계층의 Spring 트랜잭션 | 신청·취소 전체를 하나의 커밋/롤백 단위로 구성 |
| 인증 | Spring Security | 인증·접근 제어를 공통 계층에서 처리. JWT 키·알고리즘·재발급 정책은 별도 설계 |
| 스키마 관리 | Flyway | 스키마 변경을 버전별 기록. 실제 의존성·DB 지원 조합은 도입 시 검증 |
| 실행·테스트 | 로컬 DB는 Docker Compose, 통합 테스트는 Testcontainers PostgreSQL | 재현 가능한 DB 환경과 실제 DB 동시성 검증. Docker 실행 환경 필요 |

Spring Boot의 의존성 관리 범위를 우선 사용하며 개별 라이브러리 버전을 임의로 혼합하지 않는다. 정확한 JDK 배포판·패치, Gradle 및 라이브러리 버전은 환경 구성 시 검증·고정한다.

Spring Data JPA를 사용하되 엔티티 관계·변경 감지뿐 아니라 영속성 컨텍스트, flush 시점, 생성 SQL과 잠금 모드를 함께 검증한다. JPA 선택이 기존의 학생 → 강좌 잠금 순서, READ COMMITTED, 잠금 후 별도 조회 정책을 대체하지 않는다. JDBC와의 성능 우위를 측정 없이 주장하지 않는다.

## JPA 구현 검토 순서

| 단계 | 검토 내용 | 확인 방법 |
|---|---|---|
| 엔티티와 키 | Entity, Id, 생성 ID, FK 매핑 | 생성·조회 SQL과 DB 제약 확인 |
| 관계 매핑 | ManyToOne, 관계 주인, 지연 로딩 | 신청·강의 담당 연결을 별도 엔티티로 표현하는 방안 검토 |
| 영속성 컨텍스트 | 동일 엔티티 관리, 변경 감지, flush와 commit의 차이 | 변경 SQL 발생 시점과 롤백 후 DB 상태 관찰 |
| 목록·시간표 조회 | JPQL, DTO 조회, N+1, 페이지네이션 | 실제 SQL 수·조인 중복·정렬·목록과 학점 합 일치 검증 |
| 신청·취소 | 서비스 트랜잭션, 비관적 잠금, 잠금 이후 새 조회 | SQL 순서 및 실제 DB 동시 요청 테스트 |
| 초기 데이터 | ID 생성 전략과 일괄 입력 | 데이터 준비 1분 요구사항 측정. 필요 시 JDBC 보조 사용을 별도 검토 |

위 내용은 구현·검증 계획이며 실행 결과가 아니다. 관계 매핑·fetch 전략은 개별 조회 요구에 맞춰 확정한다. D78에 따라 Entity를 API 응답으로 직접 노출하지 않고 기존 응답 계약에 맞춘 DTO를 사용한다.

- 잠금은 D75에 따라 @Lock(PESSIMISTIC_WRITE)를 우선 적용한다. 생성 SQL·DB 동작을 확인하여 FOR NO KEY UPDATE를 얻지 못하면 native query로 전환한다. annotation과 특정 SQL을 자동으로 동일시하지 않는다.
- 잠금 전 로드한 신청 컬렉션을 조건 검사에 재사용하지 않는다. 잠금 이후 실제 SQL이 수행되는지, 영속성 컨텍스트의 기존 객체로 최신 조회가 대체되지 않는지 확인한다.
- 서비스 트랜잭션 안에서 검사와 저장을 묶는다. save 호출·flush·commit을 구분하며, flush는 커밋을 의미하지 않는다.
- 잠금 대기 3초는 트랜잭션 전체 timeout과 구분하여 적용한다. JPA 힌트만으로 DB에서 원하는 한도가 보장된다고 가정하지 않는다.
- 증가 신청 ID의 생성 전략은 동일 학생의 순서를 보존해야 한다. 시퀀스의 사전 할당·풀링을 채택한다면 다중 서버에서도 D66을 만족하는지 검증한다. IDENTITY의 대량 입력 성능 역시 측정 대상이다.

## 물리 스키마의 주요 선택

| 대상 | 확정 설계 |
|---|---|
| 학번 | 확정된 INTEGER PK 유지, 9자리 범위 CHECK. 연도·뒤 5자리 생성 규칙은 별도 결정 |
| 학과·교수·개설 강좌·수업 시간 ID | BIGINT GENERATED ALWAYS AS IDENTITY PK, Java Long |
| 신청 ID | BIGINT GENERATED ALWAYS AS IDENTITY PK로 전환, UNIQUE(student_number, offering_id) 유지 |
| 과목 | subject_code VARCHAR(30) PK, 숫자만 허용. 이름·학점은 개설 강좌에 유지 |
| 강의 담당 연결 | PRIMARY KEY(professor_id, offering_id), 별도 증가 ID 없음 |
| 수업 시간 | 요일 SMALLINT(월=1~일=7), 시작·종료 TIME WITHOUT TIME ZONE. API enum·HH:mm과 변환하며 KST 의미 유지 |
| 학년 | SMALLINT, CHECK 1~4 |
| 필수 관계 | NOT NULL과 FK 적용, 부모 삭제는 RESTRICT. 신청 취소는 신청 행만 명시적으로 DELETE |

학기·학점·정원·수업 범위와 최소 수업 시간·담당 교수 수는 D77로 확정했다. 문자열 길이·숫자 타입은 D79~D80을 따른다. 비밀번호 해시는 STUDENT.password_hash VARCHAR(255) NOT NULL에 저장하고 계정 테이블 분리는 추후 검토한다(D82).

## 초기 인덱스와 후속 후보

- 초기 적용: ENROLLMENT(offering_id), CLASS_MEETING(offering_id)(D83). 강좌별 COUNT와 수업 시간 조회 지원.
- 아래 나머지 인덱스는 후속 후보이며 초기 적용으로 확정하지 않는다.
- ENROLLMENT(student_number, enrollment_id): 학생별 신청 순서 조회 지원. 대상 학기는 개설 강좌와 조인하여 필터.
- COURSE_OFFERING(academic_year, term, offering_code): 확정된 학기별 강좌 코드 유일성.
- COURSE_OFFERING(academic_year, term, name, offering_id) 및 학과 필터용 department_id 포함 인덱스: 목록 필터·정렬 후보.
- CLASS_MEETING(offering_id), TEACHING_ASSIGNMENT(offering_id, professor_id): 강좌별 수업 시간·교수 조회 지원.

PK·UNIQUE가 만드는 인덱스를 중복 생성하지 않는다. 나머지 인덱스는 실제 SQL·실행 계획과 생성 데이터로 유효성을 확인한다. 현재 성능 측정 결과는 없다.

## 확정된 잠금 적용 방침

D75에 따라 JPA @Lock(PESSIMISTIC_WRITE)를 먼저 적용하고 생성 SQL·DB 동작을 확인한다. 목표는 FOR NO KEY UPDATE이며 JPA 방식으로 얻지 못하면 잠금 쿼리만 native query로 전환한다. READ COMMITTED, 잠금 후 별도 조회, 학생 → 강좌 순서는 유지한다. 대기 제한의 구체적 설정 방식은 후속 검토한다.

## 공식 자료

2026-09-17 확인. 문서상 지원 범위 확인이며 실제 프로젝트 빌드 성공을 의미하지 않는다.

- [원 과제](https://raw.githubusercontent.com/musinsatech/2026-musinsa-rookie/main/PROBLEM.md): 언어·프레임워크·DB 선택 범위와 동시성·동적 데이터 생성 요구.
- [Java 지원 로드맵](https://www.oracle.com/java/technologies/java-se-support-roadmap.html): Java 25 LTS 구분. 배포판별 지원·라이선스는 별도 확인.
- [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html): 확인 시 4.1.1, Java 17~26, Gradle 8.14 이상 8.x 또는 9.x.
- [Gradle 호환성](https://docs.gradle.org/current/userguide/compatibility.html): 선택 JDK의 빌드 실행·toolchain 지원 확인 기준.
- [PostgreSQL 지원 버전](https://www.postgresql.org/support/versioning/): 확인 시 18.6 지원.
- [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html): JdbcClient·JdbcTemplate 사용 방식.
- [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/): 실제 PostgreSQL 테스트 환경.

- [Spring Data JPA 잠금](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html): repository의 잠금 메타데이터 지정.
- [Spring Data JPA 트랜잭션](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html): 여러 repository 호출을 포함하는 작업 단위의 트랜잭션 경계.

## 물리 스키마·JPA 매핑·잠금 검토안

상태: 증가 BIGINT PK·신청 UNIQUE·강의 담당 복합 PK·데이터 범위·ManyToOne(LAZY)·DTO·부모 삭제 제한·Flyway 및 ddl-auto=validate는 D76~D78로 확정했다. IDENTITY 구문·문자열 길이·숫자 표현은 D79~D81로 확정했다. EmbeddedId/MapsId는 강의 담당 복합 키에 구현했다. OSIV·대기 제한 설정 방식은 구현 제안이다. 잠금의 JPA 우선 적용·SQL 검증·필요 시 native query 전환은 D75로 확정했다.

### 확정된 제약 범위와 세부 표현 제안

- 학기는 1·2학기만 지원하며 SMALLINT와 CHECK로 제한한다. 계절학기는 범위에서 제외한다(D77). SMALLINT 저장 표현은 D80으로 확정했다.
- 강좌 학점은 SMALLINT 정수 1~6, 정원은 양의 INTEGER를 사용한다. 1~6은 확정된 프로젝트 범위이며 실제 대학 공통 규칙을 의미하지 않는다(D77). 학점·정원 분포는 초기 데이터 생성 단계에서 따로 정한다.
- 매주 반복하는 수업을 대상으로 요일은 1~7, 시각은 분 단위 00:00~23:59로 제한하며 starts_at < ends_at을 요구한다. 자정에 끝나거나 자정을 넘기는 수업은 이 제안의 범위에서 제외한다. TIME(0)만으로는 분 단위가 강제되지 않으므로 초=0 검증도 둔다.
- 신청 가능한 개설 강좌는 수업 시간과 담당 교수가 각각 최소 1개 있어야 한다. 이는 단순 FK·행 CHECK만으로 보장되지 않으므로 데이터 생성 완료 검증과 향후 강좌 등록 검증에서 보장한다.
- 이름은 학생·교수·학과 VARCHAR(100), 강좌 VARCHAR(200), 과목·강좌 코드는 VARCHAR(30)을 사용하며 두 코드는 숫자(0~9)만 허용한다(D79). 필수 값에는 NOT NULL 및 공백만 있는 값 거절을 적용한다.
- 연도는 INTEGER의 1000~9999 범위를 사용한다(D80). 학번의 앞 4자리 의미·생성 규칙은 별도 정책이다.

### JPA 매핑 방침과 세부 구현 제안

- 신청은 Enrollment 엔티티로 두고 Student와 CourseOffering에 각각 단방향 ManyToOne(fetch=LAZY)을 둔다. 증가 신청 ID를 단일 PK로 사용하고 학생·강좌 유일 제약을 별도로 유지한다.
- 강의 담당은 TeachingAssignment 엔티티, 교수·강좌 복합 키는 EmbeddedId와 MapsId로 매핑한다. 직접 ManyToMany로 숨기지 않고 연결 행을 명시적으로 관리한다.
- 학생·교수·강좌에서 학과, 강좌에서 과목, 수업 시간에서 강좌도 필요한 ManyToOne 관계를 둔다. 부모의 역방향 컬렉션은 필요한 경우에만 추가한다.
- 생성 ID는 GenerationType.IDENTITY를 사용한다(D81). 학번·과목 코드는 외부에서 부여하는 Id이며 자동 생성하지 않는다.
- 부모 삭제는 DB RESTRICT, 관계에는 CascadeType.REMOVE 및 광범위한 CascadeType.ALL을 적용하지 않는다. 취소는 Enrollment만 명시적으로 삭제한다.
- Entity를 응답으로 직접 직렬화하지 않고 DTO를 사용한다. spring.jpa.open-in-view=false를 제안하며 필요한 데이터는 서비스 트랜잭션 안에서 조회·구성한다.
- Flyway가 DDL을 관리하고 Hibernate ddl-auto=validate로 매핑을 검증한다(D78). 전체 CHECK·인덱스 검증을 Hibernate validate만으로 대체하지 않는다.

### 잠금 적용 방침과 구현 검토

학생·강좌 잠금은 JPA @Lock(PESSIMISTIC_WRITE)로 시작한다(D75). 실제 생성 SQL과 DB 동작에서 FOR NO KEY UPDATE를 확인한다. 목표 잠금 모드를 얻지 못할 때만 해당 쿼리를 native query로 작성한다. 조건 검사 데이터는 잠금 이후 별도 SQL로 조회한다.

JPA annotation만으로 FOR NO KEY UPDATE 구문을 보장한다고 문서화하지 않는다. 사용한 Hibernate 버전·dialect·생성 SQL을 검증 기록에 남기고, native query 전환 시에는 목표 잠금과의 불일치 근거를 기록한다.

서비스의 하나의 READ COMMITTED 트랜잭션에 다음 순서를 둔다.

1. 트랜잭션 범위의 SET LOCAL lock_timeout = '3s' 설정.
2. 기존 인증 이후 입력·강좌 존재·대상 학기 검증 순서 유지.
3. 학생 PK 잠금, 별도 조회로 학생 조건 검사.
4. 강좌 PK 잠금, 별도 COUNT로 정원 검사.
5. INSERT 및 커밋. 취소는 같은 잠금 순서 후 본인 신청 DELETE 및 커밋.

lock_timeout은 DB의 각 잠금 획득 대기에 적용되며 요청 전체 시간 제한이 아니다. SET LOCAL로 커넥션 풀의 다음 요청에 설정이 남지 않도록 한다. 실제 동일 트랜잭션·커넥션 적용과 롤백 후 복원은 통합 테스트한다. 실패 응답은 D68을 따르며 롤백 완료 후 반환한다.

PostgreSQL identity의 기반 시퀀스는 CACHE 1을 유지하고 실제 DDL을 확인한다. 동일 학생의 신청 ID 순서는 학생 잠금과 함께 검증하며, 서로 다른 DB 연결·서버에서 ID 블록 사전 할당으로 순서가 바뀌지 않도록 한다.

### 검증 계획

- Flyway 스키마 적용 및 JPA 매핑 검증, 부모 참조·유일성·범위 제약 위반 확인.
- 신청 생성·취소 SQL과 부모 삭제 부재 확인.
- 잠금 전 신청 컬렉션을 읽지 않고 잠금 후 검증 SQL이 실행되는지 확인.
- 학생 조건 실패 시 강좌 잠금 미획득, 마지막 자리 경쟁 시 정확히 한 성공 확인.
- 잠금 시간 초과 시 전체 롤백·503·재시도 부재·커넥션 설정 복원 확인.
- IDENTITY 초기 데이터 생성 시간, 목록 SQL 개수, 페이지 크기·정렬, 시간표 학점 중복 합산 여부 확인.

공식 근거(2026-09-17 확인):
- [Hibernate ORM 매핑·영속성·잠금 안내](https://docs.hibernate.org/orm/current/userguide/html_single/): 개념 확인용. 실제 적용 버전은 Boot 의존성으로 결정하고 다시 확인한다.
- [PostgreSQL 18 행 잠금](https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS): NO KEY UPDATE의 충돌·호환 관계.
- [PostgreSQL 18 lock_timeout](https://www.postgresql.org/docs/18/runtime-config-client.html#GUC-LOCK-TIMEOUT): 개별 잠금 획득 시도별 대기 제한.
