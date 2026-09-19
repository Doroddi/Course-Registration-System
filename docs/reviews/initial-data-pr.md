## 해결할 문제

빈 DB에서 요구 규모의 데이터를 동적으로 생성하고, 기존 DB에서는 데이터를 보존하며 정합성을 확인합니다. 초기화·검증 실패를 무시하거나 준비 전에 정상 상태를 반환하지 않도록 /health를 연결했습니다.

선행 스키마 PR #4를 기준으로 한 변경입니다. #2 → #3 → #4 → 이 PR 순서로 검토하며 선행 PR 병합 후 base와 diff를 다시 확인합니다.

## 변경 및 근거

- V10 학과 코드와 학생 번호 생성 규칙, 학과 10·교수 100·학생 10,000·과목 250·강좌 500개의 동적 생성.
- 빈 DB 최초 생성만 수행하고 재시작 시 최소 규모·연결·정원·학점·동일 과목·시간 충돌을 검증. 불완전 데이터는 자동 삭제·보충 없이 기동 실패.
- 직접 부여한 ID는 persist로 저장하고 초기 생성 트랜잭션에만 Hibernate 배치 50 적용. pgJDBC 배치 재작성과 학점 집계 쿼리 개선.
- 대상 학기를 ConfigurationProperties로 통합. 기본 2026년 2학기, 환경변수로 변경하며 잘못된 값은 기동 실패.
- 데이터 검증·커밋과 Spring Boot 기동 작업이 모두 끝난 뒤 GET /health 200. 미준비·초기화 비활성화는 503.
- 개발 DB와 테스트를 분리하고 실행 문서·설계 결정·성능 측정 근거 갱신.

## 검증

2026-09-20, JDK 25 / Spring Boot 4.1.1 / PostgreSQL 18.6:

- `gradlew.bat test bootJar --no-daemon`: 전체 204개 통과, 실패·오류·건너뜀 0, 실행 JAR 빌드 성공.
- `scripts/verify-initial-data.ps1 -AcademicYear 2030 -Term 1`: 실제 JAR와 임시 PostgreSQL에서 기본값과 다른 학기의 500개 강좌 생성 확인.
- /health 503 → 200, 최초 준비 41.670초·재시작 45.421초. 단일 표본으로 다른 환경에 일반화하지 않음.
- 유효 신청 추가 후 비밀번호 없는 재시작의 전체 행 보존, 수업 시간 누락 시 종료 코드 1과 기존 데이터 보존 확인.
- 앞선 배치 적용 계측에서는 INSERT 문장 수가 11,860 → 1,755회로 감소했고 저장 행 수는 같았음. SQL 호출 수와 네트워크 왕복 횟수는 구분함.

[구현 및 실행](https://github.com/Doroddi/Course-Registration-System/blob/codex/initial-data/docs/INITIAL_DATA.md), [측정 조건과 결과](https://github.com/Doroddi/Course-Registration-System/blob/codex/initial-data/docs/INITIAL_DATA_PERFORMANCE.md)

## 한계 및 리뷰 요청

V10은 기존 학과 행이 없는 DB를 전제로 합니다. 초기화는 단일 앱 인스턴스를 가정하며 /health는 기동 후 DB 장애를 계속 검사하지 않습니다. 인증·업무 API·동시 신청의 잠금 처리는 후속 작업입니다. 따라서 현재 /health 성공을 전체 과제 완료로 표시하지 않습니다.

대상 학기 변경 시 기존 데이터 처리, 준비 상태 전환 시점, 트랜잭션·배치 롤백, 초기 데이터 분포를 중심으로 검토해 주세요.

## AI 활용 및 리뷰

작성자가 정한 정책과 구현을 바탕으로 AI가 초기 데이터 검증·테스트·성능 점검, 설정·헬스체크 구현과 문서 정리를 지원했습니다. AI 자체 검토와 사람 리뷰를 구분하며 사람 리뷰는 아직 받지 않았습니다.

[AI 자체 리뷰](https://github.com/Doroddi/Course-Registration-System/blob/codex/initial-data/docs/reviews/initial-data-ai-review.md), [초기 데이터 결정](https://github.com/Doroddi/Course-Registration-System/blob/codex/initial-data/prompts/0005-initial-data.md)
