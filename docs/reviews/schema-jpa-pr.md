# 스키마·JPA 변경 범위와 검증

## 해결할 문제

수강신청 도메인의 설계를 실제 PostgreSQL 저장 구조로 구현한다. Flyway가 8개 테이블과 제약조건을 생성하고 JPA 엔티티·Repository로 저장·조회한다. 같은 학생의 동일 강좌 중복 저장은 거절하며, 신청 행 삭제 후 재신청하면 새로운 ID를 발급한다.

## 변경 및 근거

- V1~V9: 학과·교수·학생·과목·개설 강좌·수업 시간·강의 담당·수강신청 테이블, FK·UNIQUE·CHECK·인덱스. V8은 종료 시각 24:00을 제한한다.
- 8개 엔티티·Repository와 강의 담당 복합 키. 단방향 ManyToOne(LAZY), 부모 삭제 제한, 신청 IDENTITY·CACHE 1.
- JPA·Flyway·PostgreSQL 드라이버·Lombok·Testcontainers 의존성과 DB 연결 설정. Flyway로 DDL을 관리하고 ddl-auto=validate로 매핑을 확인한다.
- 관련 정책: D01·D11·D33~D34·D39·D76~D84. 관련 시나리오 T109~T116 중 스키마·저장 범위를 검증한다. API·초기 데이터·동시성까지 완료한 것은 아니다.

## 검증

2026-09-19: `gradlew.bat test --tests '*RepositoryTest' --no-daemon` 성공. PostgreSQL 18.6 Testcontainers에서 130개 실행, 실패·오류·건너뜀 0.

학번·학년·코드·시간 범위, 저장·조회, 필수값, 관계·부모 삭제 제한, 복합 키, 학기별 강좌 코드 재사용, 신청 삭제·재신청, IDENTITY·CACHE 1·인덱스를 확인했다. [명령과 클래스별 결과](../DB_SETUP.md).

## DB 제약과 서비스 검증의 경계

DB 제약은 애플리케이션의 저장 경로와 관계없이 잘못된 참조·중복·컬럼 값을 거절하는 방어선이다. 여러 신청 행을 함께 읽어 판단하는 규칙은 별도의 서비스 트랜잭션과 잠금으로 보호한다.

| 보호할 규칙 | 현재 DB에서 보장하는 범위 | 후속 서비스 구현·검증 |
|---|---|---|
| 동일 개설 강좌 중복 | 학생·강좌 UNIQUE로 중복 행 저장 거절 | 사전 검사와 중복 오류 응답, 동일 학생 동시 신청 검증 |
| 동일 학기·과목 중복 | 다른 분반은 offering_id가 달라 UNIQUE만으로 거절하지 않음 | 학생 잠금 후 대상 학기 신청의 과목 코드 검사 |
| 최대 18학점 | 각 강좌의 학점이 1~6인지 CHECK로 제한 | 학생 잠금 후 대상 학기 신청 학점 합계 검사 |
| 시간표 충돌 | 각 수업 구간의 요일·시각·시작/종료 순서 제한 | 학생 잠금 후 기존 강좌와 시간 구간 비교. 종료와 시작이 같으면 허용 |
| 정원 초과 | 정원 컬럼이 양수인지 CHECK로 제한 | 학생 조건 통과 후 강좌 잠금, 별도 COUNT 조회와 정원 검사 |
| 참조 무결성과 취소 | 존재하는 부모만 참조, 참조 중인 부모 삭제 제한. 신청 삭제 후 재저장 가능 | 본인·대상 학기 확인, 학생→강좌 잠금, 반복 취소·재신청 경합 검증 |
| 강좌별 수업 시간·담당 교수 최소 1개 | FK는 자식이 참조하는 부모의 존재만 보장 | 초기 데이터 구성 시 최소 개수와 관계 완전성 검사 |

예를 들어 capacity > 0은 정원 값의 유효성만 검사하며, 신청 인원이 정원을 넘지 않는다는 뜻은 아니다. 현재 130개 Repository 테스트는 위 DB 제약과 저장·조회 동작에 대한 근거이며, 동시 요청에서 업무 규칙이 유지된다는 검증 결과는 아니다.

관계와 키의 근거는 [데이터 모델](../DATA_MODEL.md), 잠금 순서·격리 수준·경합 검증 계획은 [동시성 설계](../CONCURRENCY_DESIGN.md)를 따른다.

## 미구현·미검증 범위 및 리뷰 요청

- 정원·18학점·동일 과목·시간 충돌과 동시성은 후속 서비스 구현 범위이다.
- 강좌의 수업 시간·담당 교수 최소 1개는 초기 데이터 구성 검증에서 처리한다.
- 생성된 빈 DB의 마이그레이션 경로를 검증했다. 기존 데이터 DB의 업그레이드 및 독립 JAR 서버 기동은 이번 결과에 포함하지 않는다.
- ApplicationStartupTest는 개발 DB를 사용하므로 130개 Repository 테스트와 실행 조건이 다르다.
- 리뷰 요청: DB 제약과 JPA 매핑의 대응, 관계 삭제 범위, 복합 키, 경계값 테스트와 미검증 범위가 적절한지 확인한다.

## AI 활용 및 리뷰

AI는 매핑 검토·SQL 제약 보완, 테스트 작성과 실행, 문서 정리를 지원했다. [AI 자체 리뷰](schema-jpa-ai-review.md)와 사람 리뷰는 구분하며 사람 리뷰는 아직 받지 않았다. 설계 결정은 [물리 스키마 기록](../../prompts/0004-physical-schema.md)을 참고한다.

## 변경 분리 기준

[PR #4](https://github.com/Doroddi/Course-Registration-System/pull/4)는 선행 환경 PR #3의 codex/bootstrap-environment를 기준으로 아래 범위를 포함한다. 선행 PR 병합 후 기준 브랜치와 비교 범위를 다시 확인한다.

- build.gradle.kts의 DB·Lombok·테스트 의존성 추가분과 application.yaml의 DB·JPA·Flyway 설정
- src/main/resources/db/migration/V1~V9
- src/main/java/com/doroddi/courseregistration 아래 8개 도메인 패키지
- src/test/java/com/doroddi/courseregistration 아래 Repository 테스트 7개 클래스
- README, DB_SETUP, DATA_MODEL, REQUIREMENTS, TECH_STACK_PROPOSAL, CONCURRENCY_DESIGN, DELIVERY_PLAN의 이번 구현 관련 변경
- 물리 스키마 결정 기록 및 이 PR 초안·AI 리뷰

Gradle Wrapper·Compose·서버 기본 골격은 선행 실행 환경 변경이다. .env, .tools, IDE 설정과 빌드 산출물은 포함하지 않는다.

## 패키징 검증

2026-09-19: gradlew.bat bootJar --no-daemon 성공. 실행 JAR 생성까지 확인했으며 java -jar 서버 기동은 별도 미검증이다.
