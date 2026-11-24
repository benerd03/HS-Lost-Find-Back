# HS-Lost-Find-Back

한서대학교 분실·습득물 시스템 백엔드 (Spring Boot)

| 문서 | 내용 |
| --- | --- |
| [API_SPEC.md](API_SPEC.md) | API 명세 (프론트와의 약속) |
| [docs/ERD.md](docs/ERD.md) | ERD, 테이블 설명, 장소 추천 계산 방식 |
| [db/schema.sql](db/schema.sql) | MySQL DDL (12개 테이블) |
| [docs/DATA_GUIDE.md](docs/DATA_GUIDE.md) | 건물·보행 그래프 데이터 수집 방법 |
| 기획 확정안 PDF | 요구사항(FR-01~07), 최종 시연 성공 조건 |

## 기술 스택

Java 17 이상 · Spring Boot 3.5 · Spring Data JPA · MySQL 8.0.16 이상 · Gradle · Lombok
Spring Security는 쓰지 않는다 (HttpSession + 인터셉터, 비밀번호는 BCrypt만 사용).

## 처음 실행하기

1. **MySQL에 DB와 테이블 만들기**

   ```sql
   CREATE DATABASE lostfound DEFAULT CHARACTER SET utf8mb4;
   ```

   그다음 `db/schema.sql`을 실행한다 (MySQL Workbench에서 파일 열고 실행, 또는 `mysql -u root -p lostfound < db/schema.sql`).

2. **DB 접속 정보 넣기**
   `src/main/resources/application-local.yml.example`을 같은 폴더에 `application-local.yml`로 복사하고 비밀번호를 고친다. 이 파일은 깃에 올라가지 않는다.

3. **서버 실행**

   ```bash
   ./gradlew bootRun
   ```

   윈도우는 `gradlew.bat bootRun`. IntelliJ에서는 `LostFoundApplication`을 실행한다 (작업 폴더는 프로젝트 루트).
   `http://localhost:8080/api/categories`가 열리면 성공이다.

4. **처음 켜면 자동으로 들어가는 데이터** (DB가 비어 있을 때만)

   | 종류 | 값 |
   | --- | --- |
   | 카테고리 | 전자기기, 지갑·카드, 가방, 의류·잡화, 기타 물품 |
   | 관리자 | `admin` / `admin1234` (시연 전에 바꿀 것) |
   | 테스트 학생 | `20210001`, `20210002` / `test1234` |
   | 건물 | `data/buildings.csv`. 없으면 `data/templates/buildings.csv` 예시 2개 |

   진짜 건물 목록을 넣으려면 `data/buildings.csv`를 만들고, building 테이블을 비운 뒤 다시 켠다.

## 테스트

```bash
./gradlew test
```

MySQL 없이 H2 메모리 DB로 돈다. 현재 23개: 회원가입·로그인·로그아웃, 세션 만료 구분, 관리자 API 403, 카테고리·건물 조회, 날짜 형식, 이미지 검증.

## 폴더 구조

```
src/main/java/kr/ac/hanseo/lostfound/
├── common/            공통 (모든 기능이 쓰는 것)
│   ├── api/           ApiResponse, ErrorResponse, PageResponse, PageParams
│   ├── error/         ErrorCode(명세의 에러 코드 표), BusinessException, GlobalExceptionHandler
│   ├── auth/          SessionUser, AuthInterceptor, @LoginUser, @StudentOnly
│   ├── time/          KstTime(날짜 변환), TimeProvider(현재 시각)
│   ├── file/          ImageStorage (이미지 검증·저장·삭제)
│   ├── config/        WebConfig, TimeConfig, PasswordConfig, AppProperties
│   └── seed/          SeedDataRunner (기본 데이터)
├── user/              Student, Admin
├── auth/              회원가입·로그인·로그아웃·내 정보        ✅ 구현
├── catalog/           카테고리·건물 목록                      ✅ 구현
├── admin/             관리자 API                              ✅ A1만 구현
├── founditem/         습득물                                  엔티티·저장소만
├── lostitem/          분실 신고, 매칭                         엔티티·저장소만
├── claim/             수령 신청                               엔티티·저장소만
├── handover/          전달 진행 (FR-07)                       엔티티·저장소만
├── notification/      알림                                    엔티티·저장소만
└── pathgraph/         보행 그래프, 장소 추천                  엔티티·저장소만
```

## 남은 작업 (권장 순서)

API 명세 ③ "추천 연결 순서"와 같다. 하나 끝날 때마다 프론트에 알려 준다.

- [x] 인증: signup, login, logout, me
- [x] 공통 목록: categories, buildings
- [ ] 습득물: 등록(multipart), 검색(동적 조건 + 페이지), 상세(`claimEligibility`), 수정, 삭제
- [ ] 분실 신고와 매칭: 등록 + Top 3, 상세, 수정, 삭제, 후보 다시 조회, 찾았음
- [ ] 수령 신청과 마이페이지: 신청, 취소, `/api/me/*`
- [ ] 관리자 A2~A3: 습득물 목록·상세, 신청 묶음, 주인 확정(트랜잭션), 거절
- [ ] 전달 진행: 조회, ready(장소 계산), reset, receive, `/api/me/handovers`, 관리자 A4
- [ ] 보행 그래프: CSV 읽기, Dijkstra로 WALK\_TIME 계산, 관리자 path-graph API 3개
- [ ] 알림 4개
- [ ] 인덱스 적용 전후 EXPLAIN 비교 (발표 자료)

## 개발 규칙

AI로 코드를 만들 때도 이 규칙을 같이 알려 준다.

1. **응답은 항상 `ApiResponse.ok(data)`**. 실패는 `throw new BusinessException(ErrorCode.XXX)`만 던지면 `{ success:false, code, message }`로 바뀐다. 새 에러 코드는 명세서에 먼저 추가하고 `ErrorCode`에 넣는다.
2. **로그인 사용자는 `@LoginUser SessionUser user`** 로 받는다. 로그인 검사는 인터셉터가 이미 했다.
   - `/api/admin/**`는 관리자만 자동으로 통과한다.
   - 학생만 쓰는 API(등록, 신청, `/api/me/**`, 전달)는 컨트롤러에 `@StudentOnly`를 붙인다.
   - 남의 글인지 같은 "본인" 확인은 서비스에서 직접 한다 (`FORBIDDEN`).
3. **현재 시각은 `TimeProvider.now()`**. `LocalDateTime.now()`를 직접 쓰지 않는다 (테스트에서 30분 뒤를 만들기 위함).
4. **날짜는 `LocalDateTime`(한국 시간)**. 응답에 `+09:00`이 자동으로 붙고, 요청은 `+09:00`·`Z`·표기 없음 모두 받는다.
5. **상태를 보고 바꾸는 작업은 먼저 잠근다.** 습득물은 `FoundItemRepository.findByIdForUpdate`, 전달은 `HandoverRepository.findByIdForUpdate`. 잠근 뒤 다시 읽은 상태로 판단한다 (명세 "동시성").
6. **습득물은 소프트 삭제.** 조회할 때 항상 `deletedAt is null` 조건을 넣는다 (`findActiveById` 참고).
7. **알림은 상태 변경과 같은 `@Transactional` 안에서 저장한다.**
8. **테이블을 바꾸면 세 곳을 같이 고친다:** `db/schema.sql`, 엔티티, `docs/ERD.md`. 서버는 `ddl-auto: validate`라 엔티티와 테이블이 다르면 뜨지 않는다.
9. 명세에 있는 응답 필드는 값이 없어도 `null`로 보낸다 (필드를 빼지 않는다). DTO는 `record`로 만들면 된다.

## 프론트 연결

프론트의 `vite.config.ts`에 proxy만 추가한다 (명세 "프론트·백엔드 연결 방식").

```ts
proxy: {
  '/api': 'http://localhost:8080',
  '/uploads': 'http://localhost:8080',
},
```

## 설정값 (`application.yml`의 `app.*`)

| 키 | 기본값 | 뜻 |
| --- | --- | --- |
| `app.upload-dir` | `./uploads` | 이미지 저장 폴더 (깃에 올라가지 않음) |
| `app.demo-mode` | `true` | 시연 모드: 이동 가능 요청에 GPS 대신 건물 선택 허용 |
| `app.data-dir` | `./data` | 건물·보행 그래프 CSV 폴더 |
| `app.seed-enabled` | `true` | 빈 DB에 기본 데이터 넣기 |
