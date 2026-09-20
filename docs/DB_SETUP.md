# 개발 DB와 서버 실행

Docker Desktop, JDK 25를 준비한다. `.env.example`을 `.env`로 복사해 DB 비밀번호와 개발용 학생 초기 비밀번호, JWT 환경변수를 지정한다. `.env`는 Git에 포함하지 않는다.

## PowerShell 실행

다음 예시는 프로젝트의 단순 KEY=VALUE 형식 `.env`를 사용한다. Java 애플리케이션에는 별도 환경변수 전달이 필요하다.

```powershell
docker compose up -d --wait postgres
$localDbSettings = ConvertFrom-StringData (Get-Content .env -Raw)
$env:DB_PASSWORD = $localDbSettings.POSTGRES_PASSWORD
$env:INITIAL_STUDENT_PASSWORD = $localDbSettings.INITIAL_STUDENT_PASSWORD
$env:JWT_SECRET_BASE64 = $localDbSettings.JWT_SECRET_BASE64
$env:JWT_ISSUER = $localDbSettings.JWT_ISSUER
$env:JWT_AUDIENCE = $localDbSettings.JWT_AUDIENCE
$localDbPort = if ($localDbSettings.POSTGRES_PORT) { $localDbSettings.POSTGRES_PORT } else { '5432' }
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$localDbPort/course_registration"
# 기본 2026년 2학기를 변경할 경우 Java 프로세스에 직접 전달한다.
# $env:ENROLLMENT_YEAR = '2030'
# $env:ENROLLMENT_TERM = '1'
.\gradlew.bat bootRun
```

IntelliJ에서는 Gradle bootRun 실행 구성의 환경변수에 DB_PASSWORD, INITIAL_STUDENT_PASSWORD, JWT_SECRET_BASE64, JWT_ISSUER, JWT_AUDIENCE를 등록한다. bootRun은 .env를 자동으로 읽지 않는다. JDBC 기본 주소는 localhost:5432/course_registration, 계정은 course_app이다. 데이터가 이미 정상적으로 생성된 이후에는 초기 학생 비밀번호 없이도 재실행할 수 있다.

## JWT 비밀키 준비

최초 한 번 다음 명령으로 32바이트 난수를 생성한다. Base64 문자열이 클립보드에 복사되며, 이를 .env의 JWT_SECRET_BASE64와 애플리케이션 실행 환경변수에 설정한다. 이미 설정한 키는 재시작할 때 그대로 사용한다.

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtKeyBytes)
$jwtRandom.Dispose()
[Convert]::ToBase64String($jwtKeyBytes) | Set-Clipboard
```

개발용 JWT_ISSUER는 course-registration-system, JWT_AUDIENCE는 course-registration-api이다. 키는 저장소·문서·로그에 기록하지 않는다. 필수 설정 누락·공백, 잘못된 Base64 또는 디코딩 후 32바이트 미만인 키는 서버 기동 실패로 처리한다.

## 데이터와 준비 상태 확인

```powershell
docker compose exec postgres psql -U course_app -d course_registration
```

```sql
SELECT 'student' AS entity, count(*) FROM student
UNION ALL SELECT 'professor', count(*) FROM professor
UNION ALL SELECT 'course_offering', count(*) FROM course_offering;
SELECT student_number, name, grade, department_id FROM student ORDER BY student_number LIMIT 10;
```

psql에서 `\dt`는 테이블 목록, `\q`는 종료다. 최초 생성 시 학생 10,000명·교수 100명·강좌 500개를 확인한다. `http://localhost:8080/health`는 준비 완료 시 200과 `{"status":"UP"}`을 반환한다. 준비 전에는 503 또는 HTTP 포트 개방 전 연결 실패가 발생할 수 있다.

애플리케이션 로그의 `mode=created`는 생성·검증·커밋 완료, `mode=validated`는 기존 데이터 검증 완료를 의미한다. DB는 Docker 명명된 볼륨으로 보존되며 서버·컨테이너 재시작만으로 지워지지 않는다. 불완전한 데이터는 자동으로 고치지 않고 서버 기동을 거절한다. 오류 원인을 확인하고 수정한 뒤 재실행한다.

## 테스트와 스키마 책임

```powershell
.\gradlew.bat test bootJar --no-daemon
```

전체 DB 통합 테스트는 Testcontainers의 별도 PostgreSQL 18.6을 사용한다. 개발 DB 환경변수나 개발 DB 초기화가 필요하지 않다. JWT 설정은 src/test/resources/application.properties의 공개 테스트 전용 값을 사용하며 이 파일은 운영 JAR에 포함되지 않는다. Flyway V1~V10이 스키마를 생성하고 Hibernate ddl-auto=validate가 매핑을 검사한다. CHECK·UNIQUE·FK는 실제 PostgreSQL 저장 실패 테스트로 검증한다.

V10은 기존 학과 행이 없는 DB를 전제로 한다. V1~V9에 데이터가 이미 있다면 코드 부여 이행 계획 없이 업그레이드하지 않는다. 적용된 SQL은 수정하지 않고 다음 버전의 마이그레이션을 추가한다. 동시성의 정원·학점·시간 충돌 보호는 후속 신청 API의 트랜잭션·잠금 구현 대상이다.

## 로그인 확인

서버가 준비되면 조회한 실제 학생 학번과 초기 생성에 사용한 비밀번호로 로그인한다. 아래 예시는 앞의 PowerShell 실행 절에서 읽은 `$localDbSettings`를 사용한다. 학번은 DB에 존재하는 값으로 지정하고 초기 생성 후 비밀번호 환경변수만 바꿨다면 기존 해시에 대응하는 원래 비밀번호를 사용한다.

```powershell
$studentNumber = 202010100
$loginBody = @{
    studentNumber = $studentNumber
    password = $localDbSettings.INITIAL_STUDENT_PASSWORD
} | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/auth/login' -Method Post `
    -ContentType 'application/json' -Body $loginBody
$login | Select-Object tokenType, expiresIn
$authHeaders = @{ Authorization = "Bearer $($login.accessToken)" }
```

정상 응답은 Bearer와 1800초이다. 토큰을 로그나 공유 문서에 출력하지 않는다. 목록·신청 업무 API는 후속 구현이므로 인증 성공을 업무 기능 완료로 해석하지 않는다. 요청·오류 및 만료 조건은 [로그인·JWT 인증](AUTH_API.md)을 따른다.
