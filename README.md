# Course Registration System

동시 요청 상황에서도 정원·학점·시간표 제약을 유지하는 대학교 수강신청 API 프로젝트입니다.

[무신사 공개 과제](https://github.com/musinsatech/2026-musinsa-rookie/blob/main/PROBLEM.md)를 바탕으로 요구사항과 설계를 구체화합니다.

## 현재 상태

현재 요구사항과 논리 데이터 모델을 검토하고 있습니다. Java·Gradle 기본 골격이 준비되어 있으며, API 구현과 동시성 검증은 다음 단계입니다.

Java·Spring Boot를 사용할 계획입니다. DB 후보는 PostgreSQL이며, DB 선정과 기술 버전은 실행 환경 구성 단계에서 확정합니다.

## 설계 검토 경로

1. [요구사항과 정책](docs/REQUIREMENTS.md): 확정 사항, 미정 사항, 검증 시나리오
2. [데이터 모델과 ERD](docs/DATA_MODEL.md): 관계, 키, 제약조건 초안
3. [첫 설계 PR 초안](docs/reviews/initial-design-pr.md): 검토 범위와 주요 질문
4. [설계 관련 프롬프트 발췌](prompts/0001-design-conversation.md): 요구사항 해석과 대안 검토 과정

## 개발 및 검증 계획

설계 검토 → 실행 환경 및 헬스체크 → 데이터 생성·조회 → 신청·취소 → 동시성 검증 → 실행 및 API 문서 정리 순으로 진행합니다. 변경은 기능 단위로 나누고 설계 근거와 검증 결과를 기록합니다.

빌드·서버 실행 명령, 요구 JDK·DB 버전, 접속 포트는 실행 환경 구성 후 검증 결과와 함께 추가합니다.

## 검토 방식

PR에는 변경 이유, 검증 결과, 미해결 사항을 기록합니다. AI는 반례 검토와 문서 작성·코드 리뷰를 지원하며, AI 검토와 리뷰어의 검토 결과를 구분합니다.
