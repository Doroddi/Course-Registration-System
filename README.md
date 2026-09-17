# Course Registration System

동시 요청 상황에서도 정원·학점·시간표 제약을 유지하는 대학교 수강신청 API 프로젝트입니다.

[무신사 공개 과제](https://github.com/musinsatech/2026-musinsa-rookie/blob/main/PROBLEM.md)를 바탕으로 요구사항과 설계를 구체화합니다.

## 현재 상태

요구사항, 7개 API 계약, 데이터 모델 및 신청·취소 동시성 정책을 정리했습니다. 이 브랜치는 설계 문서 변경을 포함하며, 실행 환경과 업무 API 구현은 후속 PR에서 진행합니다.

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

현재 브랜치에는 초기 Java·Gradle 골격이 있습니다. Windows에서는 ./gradlew.bat build, macOS/Linux에서는 ./gradlew build로 빌드합니다. Spring Boot 서버와 DB 실행 안내는 실행 환경 PR에서 추가합니다. 현재 업무 API와 /health는 구현 전입니다.

## 검토 방식

PR에는 변경 이유, 검증 결과, 미해결 사항을 기록합니다. AI는 반례 검토와 문서 작성·코드 리뷰를 지원하며, AI 검토와 리뷰어의 검토 결과를 구분합니다.

기술 선택과 남은 물리 설계는 [기술 스택·물리 데이터 모델 검토](docs/TECH_STACK_PROPOSAL.md)에 정리합니다. 스택 확정과 실제 환경 구성·실행 검증은 구분합니다.
