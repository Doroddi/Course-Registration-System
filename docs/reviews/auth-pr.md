# 로그인·JWT 인증 PR

## 해결할 문제

초기 학생 계정으로 로그인해도 업무 요청에서 본인을 인증할 경로가 없었다. POST /auth/login에서 학번·비밀번호를 검증해 30분짜리 HS256 JWT를 발급하고, 공개 경로 외에는 유효한 Bearer 토큰을 요구하도록 연결했다.

## 변경 및 근거

- JSON 구조·타입과 필수 값을 DB 조회 전에 검사한다. 비밀번호 공백은 보존하며 UTF-8 72바이트를 넘는 입력은 bcrypt가 접미사를 무시하지 않도록 기존 인증 실패로 거절한다.
- 로그인 성공은 accessToken·Bearer·1800초를 반환하며 캐시를 금지한다. HS256 서명, 필수 클레임, 발급자·대상·학번과 정확한 만료 경계를 검증한다.
- POST /auth/login과 GET /health는 공개한다. 나머지 경로는 세션 없이 인증하며 보안 필터 오류도 공통 code·message를 사용한다. 보호 요청의 학생은 검증된 sub로 식별한다.

[인증 계약·구성](../AUTH_API.md), [관련 정책 D43~D53·D89와 검증 시나리오](../REQUIREMENTS.md)를 따른다.

## 선행 PR

초기 데이터 [PR #5](https://github.com/Doroddi/Course-Registration-System/pull/5)를 기반으로 한다. 비교 기준은 codex/initial-data이며 인증 관련 39개 파일만 변경한다. 검토·병합 순서는 #2 → #3 → #4 → #5 → 이 PR이다. 선행 PR 병합 후 기준 브랜치와 diff를 다시 확인한다.

## 검증

- 비밀번호 경계 보완 전 `./gradlew.bat test bootJar --no-daemon`: 전체 300개 통과, bootJar 성공.
- 보완 후 `./gradlew.bat test --tests 'com.doroddi.courseregistration.student.auth.*' --tests 'com.doroddi.courseregistration.config.JwtConfigurationTest' bootJar --no-daemon`: 인증 102개 통과(실제 HTTP·PostgreSQL 75개), bootJar 성공.
- 두 실행 모두 실패·오류·건너뜀 0. 중복 실행한 테스트 수는 합산하지 않는다. [실행 기록과 범위](../AUTH_API.md#검증)를 따른다.
- PR 브랜치의 소스·빌드 설정은 테스트한 코드와 일치한다. 실행 JAR에 테스트 전용 경로·설정이 포함되지 않았으며 비밀키·로컬 설정은 게시하지 않는다.

## 미정 사항 및 리뷰 요청

재발급·로그아웃, 키 교체·유출 대응, 로그인 시도 제한, 이메일 초기 설정·비밀번호 재설정, HTTPS 배포는 후속 범위이다. 목록·신청·취소·시간표의 업무 로직과 동시성 처리는 다음 기능 단위이다. 정확한 만료 경계, 공개 경로 범위, JSON 검증 순서와 학생 식별 전달을 중심으로 검토한다.

## AI 활용 및 리뷰

확정된 설계에 따라 구현·테스트 작성과 검토에 AI를 활용했다. [설계 결정 기록](../../prompts/0002-api-design.md)과 [AI 검토·반영 결과](auth-ai-review.md)를 남겼다. 자체 검토와 별도 AI 코드 검토를 수행했으며 bcrypt 경계 의견을 반영했다. 사람 리뷰는 아직 받지 않았다.
