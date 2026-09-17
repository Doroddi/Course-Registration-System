# 실행 기반 구성

상태: 기본 서버 구현 / 업무 API·DB 연동 미구현

## 변경 의도

- 단순 Java 진입점을 SpringBootApplication으로 교체하여 MVC 서버를 기동한다. 패키지는 com.doroddi.courseregistration 아래로 모아 컴포넌트 탐색 범위를 명확히 한다.
- Java toolchain 25와 Gradle Wrapper 9.7.1을 사용한다. Wrapper 배포 파일 체크섬을 고정하고 Spring Boot 4.1.1 BOM으로 의존성 버전을 관리한다.
- 현재 단계에는 MVC·Validation·기동 테스트 의존성만 추가한다. JPA·Security·Flyway·Testcontainers는 확정 스택이며 엔티티·인증·DB 구현 단계에서 적용한다.
- 초기 데이터와 업무 API가 준비되기 전 /health가 준비 완료를 주장하지 않도록 아직 엔드포인트를 제공하지 않는다. ApplicationStartupTest는 실제 HTTP 서버 기동과 /health의 404를 확인하는 임시 단계의 검증이다. 데이터 초기화가 구현되면 준비 전/후 검증으로 교체한다.
- Compose는 PostgreSQL 18.6의 개발 환경을 정의한다. 호스트 접속은 loopback으로 제한하고 비밀번호는 Git에서 제외하는 .env로 받는다. DB 볼륨은 PostgreSQL 18 이미지의 /var/lib/postgresql에 연결한다.
- DB healthy와 애플리케이션의 데이터·API 준비 완료는 구분한다. 현재 기본 서버는 DB에 연결하지 않는다.

## 학습 순서

1. main에서 SpringApplication.run이 애플리케이션 컨텍스트와 내장 웹 서버를 시작하는 흐름을 확인한다.
2. build.gradle.kts의 plugin, BOM, toolchain, dependencies의 역할을 구분한다.
3. 기동 테스트에서 RANDOM_PORT로 실제 서버를 실행하고 HTTP 응답을 검증하는 방식을 확인한다.
4. 다음 단계에서 Flyway 스키마와 JPA 엔티티를 연결하고 DB 통합 테스트를 추가한다.

## 검증

- 검증 JDK: Eclipse Temurin 25.0.4.1+1 LTS.
- Gradle: 9.7.1.
- JDK 압축 파일 및 Gradle 배포 체크섬 확인.
- 2026-09-17: Gradle test bootJar 성공. ApplicationStartupTest 1개 실행, 실패·오류·건너뜀 0. 임의 포트의 실제 HTTP 서버 기동 및 /health 404 확인.
- 실행 JAR: build/libs/Course-Registration-System-0.1.0-SNAPSHOT.jar 생성 확인. 독립 java -jar 기동 후 127.0.0.1:18080/health에서 404 응답 확인. 검증 프로세스는 종료했다.
- Wrapper 9.7.1 갱신 및 공식 Wrapper JAR SHA-256 일치 확인. git diff --check 통과.
- Windows 11 / WSL 2.7.14에서 Docker Desktop 4.91.0 설치 완료. Docker CLI 29.8.0, Compose v5.5.1 확인.
- docker compose config --quiet 통과. .env가 Git 제외 대상임을 확인.
- Docker 엔진 기동·PostgreSQL 컨테이너 실행 및 SQL 연결: 미검증.
- 업무 API·초기 데이터·동시성·성능: 미구현 또는 미검증.

## 공식 근거

2026-09-17 확인.

- [Spring Boot Gradle 플러그인](https://docs.spring.io/spring-boot/gradle-plugin/getting-started.html): 플러그인·BOM 적용.
- [Gradle Java 호환성](https://docs.gradle.org/current/userguide/compatibility.html): Java 25는 Gradle 9.1.0 이상에서 실행 지원.
- [Spring Boot 관리 의존성](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html): MVC·Validation·테스트 starter 선택.
- [PostgreSQL 공식 이미지](https://github.com/docker-library/docs/blob/master/postgres/README.md): 18.6 이미지, 초기 환경 변수 및 데이터 볼륨 경로.

- [Docker Desktop Windows 설치](https://docs.docker.com/desktop/setup/install/windows-install/): WSL 2 기반 사용자 단위 설치 및 실행 요구사항.
