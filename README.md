# Course Registration System

동시 요청 상황에서도 정원·학점·시간표 제약을 유지하는 대학교 수강신청 API 프로젝트입니다.

[무신사 공개 과제](https://github.com/musinsatech/2026-musinsa-rookie/blob/main/PROBLEM.md)를 바탕으로 요구사항과 설계를 구체화합니다.

## 현재 상태

Spring Boot 서버 기반, Flyway V1~V10, 8개 테이블의 JPA 엔티티·Repository, 초기 데이터 생성·검증과 /health를 구현했습니다. 빈 DB에서는 동적으로 데이터를 생성하고 정상 재시작에서는 기존 데이터를 보존합니다. 대상 학기는 설정으로 관리하며 인증·업무 API·신청 동시성 처리는 후속 구현 대상입니다.

확정 스택은 Java 25, Spring Boot 4.1.1, Spring Data JPA, PostgreSQL 18.6입니다. Gradle Wrapper는 9.7.1을 사용합니다. 현재 빌드에는 MVC·Validation·JPA·Flyway·PostgreSQL 드라이버·Lombok·Testcontainers를 적용했습니다. Security는 인증 단계에서 추가합니다.

## 설계 검토 경로

1. [요구사항과 정책](docs/REQUIREMENTS.md): 확정 사항, 미정 사항, 검증 시나리오
2. [데이터 모델과 ERD](docs/DATA_MODEL.md): 관계, 키, 제약조건과 구현 범위
3. [첫 설계 PR 초안](docs/reviews/initial-design-pr.md): 검토 범위와 주요 질문
4. [설계 결정 정리](prompts/0001-design-conversation.md): 요구사항 해석과 대안 검토 과정

5. [강좌 목록 API](docs/API.md): 요청·응답·오류 계약 초안
6. [학생 목록 API](docs/STUDENTS_API.md): 요청·응답·오류 계약 초안
7. [교수 목록 API](docs/PROFESSORS_API.md): 요청·응답·오류 계약 초안
8. [로그인 API](docs/AUTH_API.md): 학번·비밀번호 인증과 JWT·Bearer 계약 초안
9. [수강신청 API](docs/ENROLLMENTS_API.md): 요청·응답·오류 계약 초안
10. [수강취소 API](docs/CANCELLATIONS_API.md): 대상 학기 제한과 요청·응답 초안
11. [내 시간표 API](docs/TIMETABLE_API.md): 신청 순서 정렬과 응답 구성 초안
12. [신청·취소 동시성 설계](docs/CONCURRENCY_DESIGN.md): 잠금·인원 계산·신청 순서 정책과 구현 제안

## 개발 및 검증 계획

설계 검토 → 실행 환경 → 스키마·JPA → 초기 데이터·준비 상태 → 인증 → 목록 조회 → 신청·취소·시간표 → 동시성·최종 검증 순으로 진행합니다. 변경은 기능 단위로 나누고 설계 근거와 검증 결과를 기록합니다.

[변경 단위와 검증 계획](docs/DELIVERY_PLAN.md)에 인증·스키마·초기 데이터까지 포함한 PR 순서와 완료 기준을 정리합니다. 현재 스키마 변경 범위와 검증 한계는 [스키마·JPA PR 초안](docs/reviews/schema-jpa-pr.md)과 [AI 리뷰](docs/reviews/schema-jpa-ai-review.md)에서 확인할 수 있습니다.

## 빌드 및 실행

JDK 25를 설치하고 JAVA_HOME을 해당 JDK로 지정합니다. 최초 빌드에는 Gradle 및 Maven Central 접속이 필요합니다. Gradle을 별도 설치할 필요는 없습니다.

DB 연결 환경 변수 설정과 실행 순서는 [DB 스키마·JPA 검증](docs/DB_SETUP.md)를 먼저 확인합니다. 전체 DB 통합 테스트는 개발 DB와 분리된 Testcontainers PostgreSQL을 사용합니다. Repository 테스트만 실행하려면 Docker 엔진을 켜고 `./gradlew test --tests '*RepositoryTest'`를 사용합니다. 테스트용 DB는 별도로 생성됩니다.

Windows PowerShell:

~~~powershell
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
~~~

macOS/Linux:

~~~sh
./gradlew test bootJar
./gradlew bootRun
~~~

기본 HTTP 포트는 8080이며 SERVER_PORT 환경 변수로 변경할 수 있습니다. /health는 초기 데이터 생성·검증과 애플리케이션 기동이 완료되면 200을 반환합니다. 초기화가 비활성화됐거나 미완료이면 503입니다. 업무 API는 아직 구현 전이며, 헬스체크 성공을 전체 과제 완료로 해석하지 않습니다.

실행 JAR 경로는 build/libs/Course-Registration-System-0.1.0-SNAPSHOT.jar입니다.

## 로컬 PostgreSQL 준비

Docker와 Compose가 실행 가능한 환경에서 아래 순서로 진행합니다.

1. .env.example을 .env로 복사하고 POSTGRES_PASSWORD를 개발용 값으로 변경합니다.
2. docker compose up -d postgres
3. docker compose ps 로 상태를 확인합니다.
4. 사용 후 docker compose stop postgres 로 중지합니다.

호스트 접속은 127.0.0.1:5432, DB 이름은 course_registration, 계정은 course_app입니다. 포트는 .env의 POSTGRES_PORT로 변경할 수 있습니다. 이 계정과 Compose 구성은 로컬 개발용이며 서비스 배포 설정이 아닙니다.

DB 파일은 명명된 볼륨으로 보존합니다. 서버 기동 시 Flyway 마이그레이션을 적용하고 JPA가 엔티티 매핑을 검증합니다. PostgreSQL 컨테이너의 healthy 상태는 DB 접속 준비만 의미하며, 과제의 데이터 생성 및 API 준비 완료와 구분합니다.

[실행 기반 구현·검증 기록](docs/BOOTSTRAP.md)에 변경 의도와 검증 범위를 기록합니다.

## 검토 방식

PR에는 변경 이유, 검증 결과, 미해결 사항을 기록합니다. AI는 반례 검토와 문서 작성·코드 리뷰를 지원하며, AI 검토와 리뷰어의 검토 결과를 구분합니다.

기술 선택과 남은 물리 설계는 [기술 스택·물리 데이터 모델 검토](docs/TECH_STACK_PROPOSAL.md)에 정리합니다. 스택 확정과 실제 환경 구성·실행 검증은 구분합니다.

초기 데이터의 구성·실행 설정·준비 상태는 [초기 데이터 문서](docs/INITIAL_DATA.md), 실제 측정 근거는 [성능 검증 기록](docs/INITIAL_DATA_PERFORMANCE.md)을 참고합니다.
