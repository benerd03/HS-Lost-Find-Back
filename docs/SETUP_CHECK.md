# 실행 환경 점검 (윈도우, 터미널)

IntelliJ에서 ▶가 안 눌리거나 Gradle 동기화가 실패할 때, 터미널에서 한 단계씩 확인한다. IntelliJ 왼쪽 아래 터미널 아이콘(>\_)을 누르면 프로젝트 폴더에서 PowerShell이 열린다. 아래 명령은 PowerShell 기준이다.

각 단계의 "정상"과 결과가 다르면 거기서 멈추고 그 화면을 공유한다.

## 1. JDK

JDK가 설치된 폴더를 찾는다. IntelliJ로 받은 JDK는 첫 번째, 설치 파일로 깐 Temurin은 두 번째 위치에 있다.

```powershell
dir $env:USERPROFILE\.jdks
```

```powershell
dir "C:\Program Files\Eclipse Adoptium"
```

찾은 25 폴더 경로를 넣어 이 터미널에서 쓸 JDK를 정한다 (터미널을 닫으면 풀린다).

```powershell
$env:JAVA_HOME="C:\Users\user\.jdks\temurin-25.0.5"
```

```powershell
& "$env:JAVA_HOME\bin\java" -version
```

정상: `openjdk version "25.0.5"` 같은 줄이 나온다. 21도 된다. 26 이상이면 다른 폴더를 고른다.

## 2. Gradle (빌드 도구)

```powershell
.\gradlew.bat --version
```

정상: `Gradle 9.8.2`, `Launcher JVM: 25...`가 나온다.

- `Downloading https://services.gradle.org/...`가 먼저 나오면 Gradle을 아직 안 받은 것이다. 끝날 때까지 기다린다
- `Gradle 8.14.3`이 나오면 예전 코드를 열고 있는 것이다
- `JAVA_HOME is not set` 또는 `invalid directory`면 1단계 경로가 틀렸다

받아 둔 Gradle은 여기에 있다. `gradle-9.8.2-bin` 폴더가 보이면 다운로드 완료다.

```powershell
dir $env:USERPROFILE\.gradle\wrapper\dists
```

## 3. 라이브러리 (Spring Boot 등)

```powershell
.\gradlew.bat dependencies --configuration runtimeClasspath
```

정상: 마지막에 `BUILD SUCCESSFUL`, 위쪽에 아래 버전들이 나온다.

| 라이브러리 | 버전 |
| --- | --- |
| spring-boot-starter-web, data-jpa, validation | 3.5.16 |
| spring-core, spring-webmvc | 6.2.19 |
| hibernate-core | 6.6.53.Final |
| tomcat-embed-core | 10.1.55 |
| jackson-databind | 2.21.4 |
| spring-security-crypto | 6.5.11 |
| mysql-connector-j | 9.7.0 |
| commons-csv | 1.14.1 |

- 버전 옆에 `FAILED`가 붙은 줄이 있으면 그 라이브러리를 못 받은 것이다
- `Read timed out`, `Could not GET 'https://...'`이 나오면 네트워크 문제다. 오류에 나온 주소를 브라우저로 열어 본다. 학교 와이파이에서 안 열리면 핫스팟이나 다른 네트워크로 바꾼다

받다가 끊긴 파일이 남아 있을 수 있으니, 네트워크를 바꾼 뒤에는 이렇게 다시 받는다.

```powershell
.\gradlew.bat dependencies --configuration runtimeClasspath --refresh-dependencies
```

받아 둔 라이브러리는 여기에 쌓인다. `org.springframework.boot`, `org.hibernate.orm`, `com.mysql` 폴더가 보이면 받아진 것이다.

```powershell
dir $env:USERPROFILE\.gradle\caches\modules-2\files-2.1
```

## 4. 컴파일과 테스트 (DB 없이)

```powershell
.\gradlew.bat test
```

정상: `BUILD SUCCESSFUL`. 테스트는 MySQL 없이 메모리 DB로 돈다. 결과 화면은 `build\reports\tests\test\index.html`을 브라우저로 열면 나온다 (23개 통과).

## 5. 서버 실행 (MySQL 필요)

MySQL이 켜져 있고, `db/schema.sql`을 실행했고, `src/main/resources/application-local.yml`에 비밀번호를 넣었는지 먼저 확인한다.

```powershell
.\gradlew.bat bootRun
```

정상: 로그 끝에 `Started LostFoundApplication`과 `[seed] 카테고리 5개`가 나오고, 진행 표시가 `80% EXECUTING`에서 멈춰 있다 (서버가 켜져 있는 상태). 브라우저에서 `http://localhost:8080/api/categories`를 열어 JSON이 보이면 끝. 끌 때는 터미널에서 Ctrl+C.

자주 나오는 오류:

| 로그에 보이는 말 | 원인 | 해결 |
| --- | --- | --- |
| `Access denied for user 'root'` | DB 비밀번호가 틀림 | `application-local.yml`의 password 확인 |
| `Communications link failure` | MySQL이 꺼져 있음 | Workbench로 접속되는지 확인, 윈도우 서비스에서 MySQL80 시작 |
| `Unknown database 'lostfound'` | 테이블을 안 만듦 | Workbench에서 `db/schema.sql` 실행 |
| `Schema-validation: missing table` / `wrong column type` | 테이블이 코드와 다름 | 최신 `db/schema.sql`을 다시 실행 (데이터는 지워짐) |
| `Port 8080 was already in use` | 서버가 이미 켜져 있음 | 다른 터미널이나 IntelliJ 실행 창의 서버를 끈다 |

## 터미널이 되면 IntelliJ도 맞추기

터미널에서 3단계까지 성공했다면 라이브러리는 이미 받아져 있다. IntelliJ에서 코끼리 아이콘 → ↻를 누르면 이번엔 바로 끝나고 ▶가 생긴다. 그래도 안 되면 설정의 Gradle JVM이 1단계에서 쓴 JDK와 같은지 본다.
