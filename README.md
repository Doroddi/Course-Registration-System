# Course Registration System

동시 요청 상황에서도 정원·학점·시간표 제약을 유지하는 대학교 수강신청 API 프로젝트입니다.

[무신사 공개 과제](https://github.com/musinsatech/2026-musinsa-rookie/blob/main/PROBLEM.md)를 바탕으로 요구사항과 설계를 구체화합니다.

## 현재 상태

Spring Boot 기본 서버와 기동 테스트, PostgreSQL 개발 환경을 구성했습니다. 업무 API·JPA·초기 데이터 및 애플리케이션 DB 연결은 아직 구현 전입니다. /health는 준비 완료를 의미하므로 현재 제공하지 않습니다.

## 설계 검토 경로

1. [요구사항과 정책](docs/REQUIREMENTS.md): 확정 사항, 미정 사항, 검증 시나리오
2. [데이터 모델과 ERD](docs/DATA_MODEL.md): 관계, 키, 제약조건 초안
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

설계 검토 → 실행 환경 및 헬스체크 → 데이터 생성·조회 → 신청·취소 → 동시성 검증 → 실행 및 API 문서 정리 순으로 진행합니다. 변경은 기능 단위로 나누고 설계 근거와 검증 결과를 기록합니다.

[변경 단위와 검증 계획](docs/DELIVERY_PLAN.md)에 인증·스키마·초기 데이터까지 포함한 PR 순서와 완료 기준을 정리합니다. 다음 문서 변경의 범위는 [API 및 상세 설계 PR 초안](docs/reviews/api-design-pr.md)에서 확인할 수 있습니다.

## 빌드 및 실행

JDK 25를 설치하고 JAVA_HOME을 해당 JDK로 지정합니다. 최초 빌드에는 Gradle 및 Maven Central 접속이 필요합니다. Gradle을 별도 설치할 필요는 없습니다.

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

기본 HTTP 포트는 8080이며 SERVER_PORT 환경 변수로 변경할 수 있습니다. 현재 업무 엔드포인트와 /health는 미구현으로 404입니다. 기동 테스트는 임의 포트의 실제 HTTP 서버를 실행하여 응답을 확인하며, 업무 API나 DB 동시성 검증을 대체하지 않습니다.

실행 JAR 경로는 build/libs/Course-Registration-System-0.1.0-SNAPSHOT.jar입니다.

## 로컬 PostgreSQL 준비

Docker와 Compose가 실행 가능한 환경에서 아래 순서로 진행합니다.

1. .env.example을 .env로 복사하고 POSTGRES_PASSWORD를 개발용 값으로 변경합니다.
2. docker compose up -d postgres
3. docker compose ps 로 상태를 확인합니다.
4. 사용 후 docker compose stop postgres 로 중지합니다.

호스트 접속은 127.0.0.1:5432, DB 이름은 course_registration, 계정은 course_app입니다. 포트는 .env의 POSTGRES_PORT로 변경할 수 있습니다. 이 계정과 Compose 구성은 로컬 개발용이며 서비스 배포 설정이 아닙니다.

DB 파일은 명명된 볼륨으로 보존합니다. 현재 애플리케이션의 DB 연결·마이그레이션은 아직 적용하지 않았습니다. PostgreSQL 컨테이너의 healthy 상태는 DB 접속 준비만 의미하며, 과제의 데이터 생성 및 API 준비 완료와 구분합니다.

[실행 기반 구현·검증 기록](docs/BOOTSTRAP.md)에 변경 의도와 검증 범위를 기록합니다.

## 검토 방식

PR에는 변경 이유, 검증 결과, 미해결 사항을 기록합니다. AI는 반례 검토와 문서 작성·코드 리뷰를 지원하며, AI 검토와 리뷰어의 검토 결과를 구분합니다.

기술 선택과 남은 물리 설계는 [기술 스택·물리 데이터 모델 검토](docs/TECH_STACK_PROPOSAL.md)에 정리합니다. 스택 확정과 실제 환경 구성·실행 검증은 구분합니다.
