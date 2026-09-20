# 초기 데이터와 준비 상태

## 구현 범위

빈 DB에서는 학과·교수·학생·과목·개설 강좌·수업 시간·강의 담당을 동적으로 생성해 한 트랜잭션으로 저장한다. 기존 데이터가 있으면 재생성하지 않고 최소 규모와 정합성을 검증한다. 일부만 있거나 규칙을 위반하면 원인을 알리고 기동을 실패시키며 자동 보충·삭제는 하지 않는다.

| 데이터 | 최초 생성 규모와 규칙 |
| --- | --- |
| 학과 | 10개, 코드 10~19, 고유 이름 |
| 교수 | 100명, 학과에 연결 |
| 학생 | 10,000명, 학년 1~4 균등 배분 |
| 과목 | 학과별 25개, 총 250개 |
| 개설 강좌 | 과목당 2분반, 총 500개 |
| 수업 시간·강의 담당 | 각각 500개, 강좌별 최소 1개 |
| 수강신청 | 최초 0건, 재시작 시 기존 신청 보존 |

학번은 연도 2020~2026 + 학과 코드 2자리 + 학생 번호 100~499로 구성한다. 현재 학년은 학번 연도와 독립적이며 현재 소속은 department_id로 판단한다. 강좌 학점은 1~3, 정원은 30·40·50·60명, 수업은 평일 09~18시로 생성한다. 서로 다른 과목의 시간 충돌·인접 수업 사례를 포함하고 강좌당 담당 교수는 1명이다. DB의 학점 1~6 허용 범위와 공동 강의 모델은 유지한다.

## 대상 학기와 실행 설정

`EnrollmentTermProperties`가 `app.enrollment.academic-year`와 `app.enrollment.term`을 바인딩한다. 기본값은 2026년 2학기이며 환경변수 `ENROLLMENT_YEAR`, `ENROLLMENT_TERM`으로 변경한다. 연도 1000~9999, 학기 1~2를 벗어나거나 타입이 맞지 않으면 기동에 실패한다. 초기 강좌 생성과 기존 데이터 검증은 같은 설정을 사용하며 이후 업무 API도 이 설정을 주입받는다. 학생 학번 연도 범위는 별도의 개발 데이터 생성 규칙이다.

기존 DB의 대상 학기 데이터를 자동으로 바꾸거나 새 학기를 채우지 않는다. 다른 학기로 설정했는데 해당 학기의 필수 데이터가 없으면 검증 실패가 정상이다.

`DB_PASSWORD`는 PostgreSQL 접속 비밀번호다. `INITIAL_STUDENT_PASSWORD`는 빈 DB 생성 시 필요한 개발용 공통 학생 비밀번호다. 비밀번호는 DelegatingPasswordEncoder의 bcrypt로 해시해 저장하며, 누락·공백·UTF-8 72바이트 초과 값은 거절한다. 정상 데이터가 있는 재시작에는 초기 비밀번호가 필요하지 않다. `.env`는 Compose에서 읽지만 Java 실행에는 환경변수를 별도로 전달해야 한다. 실제 비밀번호와 IDE 개인 실행 설정은 Git에 포함하지 않는다.

## 저장과 검증

- AcademicSeedFactory와 CourseSeedFactory는 완성된 정적 데이터 파일 없이 엔티티를 생성한다.
- 직접 ID를 부여하는 학생·과목·강의 담당은 EntityManager.persist를 사용해 신규 저장 전 존재 확인 SELECT를 피한다.
- 최초 생성 트랜잭션의 Hibernate 세션 배치 크기는 50이며 종료 시 이전 값을 복원한다. pgJDBC reWriteBatchedInserts를 함께 사용한다. IDENTITY 엔티티는 이 배치 대상이 아니다.
- 마지막 flush와 정합성 검사를 같은 트랜잭션 안에서 실행한다. 제약조건·배치·최종 검증 실패 시 생성한 도메인 데이터 전체를 롤백한다. Flyway 스키마 변경은 별도 트랜잭션이다.
- 기존 데이터는 학과 10·교수 100·학생 10,000·대상 학기 강좌 500 이상인지, 강좌별 시간·교수 연결이 있는지 확인한다.
- 대상 학기의 정원 초과, 학생별 18학점 초과, 동일 과목 중복, 시간표 충돌을 검사한다. 시작과 종료가 맞닿는 수업은 허용한다. 초기화 검증은 향후 신청 API의 잠금·트랜잭션 검증을 대체하지 않는다.

V10은 학과 코드 컬럼과 제약을 추가하며 기존 학과 행이 없는 DB를 전제로 한다. V1~V9 DB에 이미 학과 데이터가 있다면 실제 학과 코드의 이행 계획이 먼저 필요하다. 임의 코드로 자동 보정하거나 적용된 마이그레이션을 수정하지 않는다.

## GET /health

인증 없이 호출하는 기동 준비 상태 엔드포인트다.

| 상태 | HTTP | 본문 |
| --- | --- | --- |
| 초기화·검증 미완료 또는 Spring Boot가 트래픽을 거절 중 | 503 | `{"status":"NOT_READY"}` |
| 초기화·검증 성공 및 Spring Boot의 모든 Runner 완료 | 200 | `{"status":"UP"}` |

초기화 전용 완료 상태와 Spring Boot ApplicationAvailability의 ReadinessState를 함께 확인한다. 생성 경로에서는 서비스 트랜잭션 커밋 후 완료 상태로 전환한다. `app.initial-data.enabled=false`이면 자동 초기화·검증이 생략되므로 /health는 503을 유지한다. 초기화 실패 시 서버는 기동 실패로 종료하며 준비 완료를 선언하지 않는다. HTTP 포트가 열리기 전에는 연결 자체가 되지 않을 수 있다.

이 엔드포인트는 현재 구현된 애플리케이션의 기동 준비 상태를 나타낸다. 아직 구현하지 않은 인증·업무 API의 완성 여부나 기동 후 DB 장애를 상시 검사하지 않는다. 전체 과제 완료와 1분 내 업무 API 준비 조건은 최종 검증에서 별도로 확인한다.

## 검증과 재현

`gradlew.bat test bootJar --no-daemon`은 Testcontainers PostgreSQL을 사용하며 개발 DB와 분리한다. 설정 바인딩·오류 거절, 초기화 성공·실패, 준비 상태의 HTTP 응답, DB 제약·관계·롤백을 검사한다.

`scripts/verify-initial-data.ps1`은 별도의 임시 PostgreSQL 컨테이너와 실행 JAR로 최초 생성 → 유효 신청 추가 → 비밀번호 없이 재시작 → 연결 데이터 누락 후 기동 실패를 확인한다. 재시작·실패 전후 전체 행의 해시를 비교하고 /health가 200이 될 때까지 시간을 측정한다. 자신이 만든 프로세스·컨테이너만 정리한다. PowerShell 7, Docker Desktop, `.tools/jdk-25.0.4.1+1`과 빌드된 JAR가 필요하다.

```powershell
.\scripts\verify-initial-data.ps1
.\scripts\verify-initial-data.ps1 -AcademicYear 2030 -Term 1
```

전체 실행 결과와 한계는 [성능 검증 기록](INITIAL_DATA_PERFORMANCE.md), 검토 결과는 [AI 자체 리뷰](reviews/initial-data-ai-review.md)에 기록한다. PR에 기재된 실제 실행 결과와 위 재현 계획을 구분한다.

## 공식 근거

- [Spring Boot 4.1 Application Availability](https://docs.spring.io/spring-boot/reference/features/spring-application.html): 모든 Runner 완료 후 트래픽 수락 상태로 전환하는 기본 생명주기를 사용한다.
- [Spring Boot 설정 바인딩·검증](https://docs.spring.io/spring-boot/reference/features/external-config.html): ConfigurationProperties와 Bean Validation으로 잘못된 학기 설정을 기동 시 거절한다.
- [Hibernate 세션 배치 설정](https://docs.jboss.org/hibernate/orm/current/javadocs/org/hibernate/SharedSessionContract.html), [pgJDBC 배치 재작성](https://jdbc.postgresql.org/documentation/use/): 기존 스택에서 초기 데이터의 다수 INSERT 전송 비용을 줄인다. 버전 변경이나 별도 배치 프레임워크 도입은 하지 않았다.
