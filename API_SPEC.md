# 분실·습득물 시스템 API 명세서 v1.8

Nov 22, 2026 · @하은

이 명세서는 11/18 기획 확정안(FR-01\~07)과 프론트 홈 화면 시안을 기준으로, 프론트가 백엔드 완성 전에도 같은 모양의 가짜 데이터로 화면을 만들 수 있게 쓴 약속이다. 바꾸고 싶은 부분은 해당 줄에 댓글로 남긴다.

**변경 이력**

| 버전 | 날짜 | 내용 |
| --- | --- | --- |
| v1.8 | 2026-11-25 | 보행 그래프 구현: 그래프 데이터가 있으면 `method: WALK_GRAPH`와 도보 시간, 없으면 `STRAIGHT_LINE`. `unreachableNodes`는 출입구가 있는 건물 중 하나라도 닿지 못하는 노드. CSV 오류 메시지는 `파일명 N행: 이유` |
| v1.7 | 2026-11-25 | 알림 구현하며 빈칸 채움: 읽지 않은 수 `{ unreadCount }`, 읽음 처리 응답은 목록 항목 1개, 모두 읽음 `{ updatedCount }`, 이동할 곳이 없으면 `link: null`, 알림은 학생 전용 |
| v1.6 | 2026-11-25 | 전달 구현하며 빈칸 채움: 보행 그래프 전까지는 직선거리(`method: STRAIGHT_LINE`, 도보 시간 null), 받았어요 판정 순서(습득자 `FORBIDDEN` 먼저), A4에 이동 가능 시각 표시(보고서 ④-4 A로 확정), `GET /api/handovers/{id}`도 학생 전용 |
| v1.5 | 2026-11-25 | 관리자 구현하며 빈칸 채움: A2 `claimCount`는 모든 상태의 신청 수, A3 묶음의 `claims`는 그 습득물의 모든 신청(오래된 순), 확정 2단계 판정 기준, 자동 거절 사유, 거절 본문 생략 가능 |
| v1.4 | 2026-11-25 | 구현하며 빈칸 채움: 신청 취소 응답, `/api/me/summary` 건수 기준(진행 중만), 내 습득 신고 `claimCount`(PENDING 수), `/api/me/**`는 학생 전용(관리자 403) |
| v1.3 | 2026-11-23 | FR-07 장소 추천을 캠퍼스 보행 그래프(도보 시간) 기반으로 변경. 전달 응답 `building`에 `myWalkSeconds`·`otherWalkSeconds`·`method` 추가, `demoSelectable`·시연 모드 기준을 출입구 노드로 변경, "장소 추천 계산" 절과 관리자 보행 그래프 API 3개 추가. ERD는 `docs/ERD.md`, 데이터 양식은 `docs/DATA_GUIDE.md` |
| v1.2 | 2026-11-23 | 백엔드 검토 반영. ready 재요청 규칙 확정(연장 없음), 습득물 소프트 삭제, `LOST_ITEM_HAS_CLAIM` 코드와 종료된 신고의 close 처리, 신청 생성·습득물 수정·삭제의 행 잠금 규칙, `activeHandoverId` 선택 규칙 |
| v1.1 | 2026-11-22 | 프론트 보완 요청 반영. 날짜에 `+09:00` 표기, ID·null 규칙, 인증·세션·CSRF 규칙, `SESSION_EXPIRED`·`HANDOVER_ALREADY_READY` 코드, `/api/auth/me`에 department, 건물 `demoSelectable`, 습득물 `claimEligibility`, 매칭 `reasons`와 최종 정렬, 전달 조회 응답 재정의(`serverNow` 등), 전달 예외 상황표, 관리자 A1\~A4 응답·신청 묶음 조회·전달 상세·동시성 규칙, vite proxy 안내 수정. 보고서: [API 명세서 v1.1 검토 보고서](https://claude.ai/artifact/QuasLA876tjzcnRVmDnVbE) |
| v1 | 2026-11-22 | 최초 작성 |

이 문서는 API 계약이며, 백엔드는 아직 구현되지 않았다. "(협의 필요)"가 붙은 항목은 보고서 ④의 기본안이다.

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 기본 주소 | 로컬 `http://localhost:8080/api`. 관리자 기능은 `/api/admin/**` |
| 로그인 방식 | 세션 쿠키(JSESSIONID). Vite proxy를 쓰면 쿠키가 자동으로 오가지만, 혹시 따로 호출할 때를 위해 `withCredentials: true` |
| 요청 본문 | JSON. 이미지가 있는 습득물 등록·수정만 `multipart/form-data` |
| 날짜·시간 | ISO 8601, 한국 시간, 응답은 항상 시간대 표기를 붙임. 요청은 +09:00과 Z(UTC) 모두 받음. 예(편의상 이 문서의 다른 예시는 +09:00을 생략함): `2026-11-21T14:30:00`+09:00. "어제" 같은 상대 시간은 프론트가 계산 |
| 이미지 | 업로드 필드명 `image`, jpg·png, 최대 5MB. 응답의 `imageUrl`은 `/uploads/파일명` |
| 목록 페이지 | `?page=0&size=20` (기본값). 응답은 `items`, `page`, `size`, `totalCount` |
| 장소 표시 | `buildingName` + `locationDetail`를 프론트가 이어 붙임. 예: 인문사회관 2층 |
| 정렬 | 목록은 따로 적지 않으면 등록 시각(createdAt) 최신순 |
| 페이지 적용 범위 | 습득물 검색과 관리자 습득물 목록만 페이지를 나눈다. /api/me/\*\* 와 나머지 목록은 전체를 배열로 보낸다 |
| 빈 값 | 검색 파라미터가 빈 문자열이면 보내지 않은 것과 같다. 값이 없는 응답 필드는 null |
| 프론트 연결 | Vite proxy로 /api 와 /uploads 두 경로를 백엔드(8080)로 넘긴다. /uploads 를 빠뜨리면 사진이 안 보인다 |
| 관리자 계정 | 회원가입 없음. 샘플 데이터로 미리 넣어 둔다 |
| ID | 모든 xxxId는 JSON 숫자(정수). 학번(loginId, studentId)과 관리자 ID만 문자열 |
| null | 명세에 있는 필드는 응답에 항상 포함하고, 값이 없으면 null. 필드를 빼지 않는다 |
| 세션 | Spring Security를 쓰지 않고 HttpSession + 인터셉터로 권한 검사. 마지막 요청부터 30분 뒤 만료(협의 필요). 쿠키는 HttpOnly, SameSite=Lax |
| CSRF | 별도 토큰 없음. SameSite=Lax 쿠키, 변경 요청은 JSON 또는 multipart만 허용, GET으로 상태를 바꾸는 API 없음. 학습·시연용 수준의 방어다 |
| 업로드 검증 | 로그인한 학생만 가능. 확장자와 Content-Type을 함께 검사(jpg·png), 5MB 초과는 IMAGE\_INVALID. 저장 파일명은 서버가 UUID로 새로 지음 |

**응답 형식**

```json
{ "success": true, "data": { } }
```

```json
{ "success": false, "code": "CLAIM_DUPLICATE", "message": "이미 신청한 물품입니다." }
```

**HTTP 상태 코드**

| 코드 | 뜻 |
| --- | --- |
| 200 / 201 | 성공 / 새로 만들어짐 |
| 400 | 입력값 오류 (필수값 누락, 미래 날짜 등) |
| 401 | 로그인 필요 |
| 403 | 권한 없음 (남의 글, 학생이 관리자 API 호출) |
| 404 | 대상 없음 |
| 409 | 상태 충돌 (중복 신청, 이미 처리된 건 등) |

**에러 코드 (`code` 값)**

| code | HTTP | 상황 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 필수값 누락, 형식 오류. `message`에 필드명 포함 |
| `IMAGE_REQUIRED` | 400 | 습득물 등록 시 이미지 없음 |
| `IMAGE_INVALID` | 400 | 형식이 jpg·png가 아니거나 5MB 초과 |
| `FUTURE_DATE` | 400 | 분실·습득 일시가 미래 |
| `LOGIN_FAILED` | 401 | ID 또는 비밀번호 불일치 (어느 쪽인지 알려주지 않음) |
| `LOGIN_REQUIRED` | 401 | 로그인 안 함 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 대상 없음 |
| `STUDENT_ID_DUPLICATE` | 409 | 이미 가입된 학번 |
| `ITEM_LOCKED` | 409 | 수령 신청이 걸린 습득물 수정·삭제 |
| `CLAIM_OWN_ITEM` | 409 | 본인이 등록한 습득물에 신청 |
| `CLAIM_DUPLICATE` | 409 | 같은 물건에 이미 신청함 (거절된 경우 포함, 재신청 불가) |
| `CLAIM_CLOSED` | 409 | 이미 수령대기·완료된 물건 |
| `CLAIM_NOT_PENDING` | 409 | 대기 상태가 아닌 신청의 취소·확정·거절 |
| `OWNER_ALREADY_CONFIRMED` | 409 | 같은 습득물에 이미 주인이 확정됨 |
| `HANDOVER_NOT_PARTY` | 403 | 전달 당사자(습득자·분실자)가 아님 |
| `HANDOVER_DONE` | 409 | 이미 수령완료된 전달 |
| `LOCATION_REQUIRED` | 400 | 이동 가능 요청에 위치도 건물도 없음 |
| LOST\_ITEM\_INVALID | 400 | 수령 신청에 넣은 lostItemId가 내 것이 아니거나 OPEN이 아님 |
| LOST\_ITEM\_CLOSED | 409 | 종료(CLOSED)된 분실 신고의 수정·삭제 |
| HANDOVER\_PLACE\_SET | 409 | 장소가 이미 정해진 뒤 다시 이동 가능을 누름 (다시 정하기부터) |
| SESSION\_EXPIRED | 401 | 요청에 세션 쿠키는 있는데 서버 세션이 만료됨. 쿠키 자체가 없으면 LOGIN\_REQUIRED |
| HANDOVER\_ALREADY\_READY | 409 | 이동 가능이 아직 유효한데 다시 누름. 연장은 없고 만료된 뒤에만 다시 누를 수 있음 (보고서 ④-1 B로 확정) |
| LOST\_ITEM\_HAS\_CLAIM | 409 | 연결된 진행 중(PENDING·APPROVED) 수령 신청이 있는 분실 신고의 삭제 |

## 상태값과 코드

아래 영문 값이 그대로 JSON에 들어가고, 화면에는 한글 표시를 쓴다.

| 대상 | 값 (한글 표시) |
| --- | --- |
| 습득물 `status` | `STORED` 보관중, `CLAIM_REQUESTED` 수령신청, `AWAITING_PICKUP` 수령대기, `CLAIMED` 수령완료 |
| 수령 신청 `status` | `PENDING` 대기, `APPROVED` 승인, `REJECTED` 거절, `CANCELLED` 취소, `COMPLETED` 수령완료 |
| 분실 신고 `status` | `OPEN` 찾는 중, `CLOSED` 종료 |
| 전달 진행 `status` | `WAITING` 대기, `PLACE_SET` 장소 확정, `DONE` 완료 |
| 사용자 `role` | `STUDENT`, `ADMIN` |

카테고리와 건물은 고정 값이 아니라 DB 목록이다. 프론트는 `GET /api/categories`, `GET /api/buildings`로 받아서 쓴다. 건물 목록에는 "기타(건물 외)"가 포함된다.

## 화면별 사용 API

프론트 시안의 메뉴(홈, 습득물 찾기, 신고·등록, 내 신고·신청, 이용 안내)와 기획안 화면 번호를 함께 적었다.

| 화면 | 화면 요소 | API |
| --- | --- | --- |
| 홈 | 최근 등록된 습득물 3건 | `GET /api/found-items?size=3` |
| 홈 | 검색창, 카테고리 아이콘 | 검색 화면으로 이동하며 `keyword` 또는 `categoryId` 전달, 아이콘 목록은 `GET /api/categories` |
| 홈 | 내 진행 현황 (로그인 시) | `GET /api/me/summary` |
| 홈 | 전달 장소 추천 카드 | 진행 중인 전달이 있으면 S10 패널로, 없으면 이용 안내로 이동 (`GET /api/me/handovers`) |
| 로그인·회원가입 (S1) | 학생·관리자 탭 | `POST /api/auth/signup`, `POST /api/auth/login` |
| 습득물 찾기 (S6) | 상세검색: 건물·카테고리·키워드 | `GET /api/found-items?buildingId=&categoryId=&keyword=` |
| 습득물 상세 (S7) | 사진, 정보, 수령 신청 버튼 | `GET /api/found-items/{id}` |
| 수령 신청 (S8) | 소유 증빙, 연결할 분실 신고 | `GET /api/me/lost-items?status=OPEN`, `POST /api/found-items/{id}/claims` |
| 분실물 신고 (S3) | 입력 폼 | `GET /api/buildings`, `POST /api/lost-items` |
| 매칭 결과 (S4) | Top 3 카드, "없음" 문구 | 등록 응답의 `matches`, 다시 보기는 `GET /api/lost-items/{id}/matches` |
| 습득물 등록 (S5) | 입력 폼, 사진 | `POST /api/found-items` (multipart) |
| 내 신고·신청 (S9) | 탭 3개 | `GET /api/me/lost-items`, `GET /api/me/found-items`, `GET /api/me/claims` |
| 전달 진행 패널 (S10) | 두 사람 상태, 이동 가능, 추천 건물, 받았어요 | `GET /api/handovers/{id}` (5초마다), `POST .../ready`, `.../reset`, `.../receive` |
| 관리자 (A1\~A4) | 대시보드, 습득물, 신청 처리, 전달 현황 | `/api/admin/**` (관리자 API 절) |
| 이용 안내 | 이용 절차 6단계, 전달 방법 안내 | 없음 (고정 텍스트) |
| 공통 상단 | 알림(종) 아이콘, 빨간 점, 알림 목록 | GET /api/me/notifications/unread-count (30초마다), GET /api/me/notifications, PATCH .../read |

습득물 목록·검색·상세는 로그인해야 볼 수 있다. 로그인 전 홈의 "최근 등록된 습득물" 자리에는 로그인 안내를 띄운다. 카테고리·건물 목록은 로그인 없이 볼 수 있다.

## 인증·공통 데이터

| 메서드 | 경로 | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | /api/auth/signup | 학생 회원가입 | 누구나 |
| POST | /api/auth/login | 로그인 (학생·관리자 공통) | 누구나 |
| POST | /api/auth/logout | 로그아웃 | 로그인 |
| GET | /api/auth/me | 내 정보와 역할. 새로고침 후 로그인 유지 확인용 | 로그인 |
| GET | /api/categories | 카테고리 목록 | 누구나 |
| GET | /api/buildings | 서산캠퍼스 건물 목록 (기타 포함, 좌표는 보내지 않음) | 누구나 |

**회원가입 요청**

```json
{ "studentId": "20211234", "password": "pass1234!", "name": "김하은",
  "department": "무인항공기학과", "phone": "010-1234-5678" }
```

응답 201: `{ "success": true, "data": { "studentId": "20211234" } }`. 예외는 `STUDENT_ID_DUPLICATE`, `VALIDATION_FAILED`.

입력 규칙: 학번은 숫자 8자리, 비밀번호는 8자 이상이며 영문과 숫자를 모두 포함, 전화번호는 `010-0000-0000` 형식. 모든 항목 필수. 가입 후 자동 로그인은 하지 않는다.

**로그인 요청·응답**

```json
{ "role": "STUDENT", "loginId": "20211234", "password": "pass1234!" }
```

```json
{ "success": true, "data": { "loginId": "20211234", "name": "김하은", "role": "STUDENT",
  "department": "무인항공기학과" } }
```

관리자는 `role: "ADMIN"`과 관리자 ID를 보낸다. 로그인 화면은 기본이 학생이고 "관리자" 버튼으로 전환한다. `/api/auth/me` 응답도 같은 모양이며, 관리자는 `department`가 소속 부서다. 로그인하지 않았거나 세션이 만료되면 401(`LOGIN_REQUIRED` / `SESSION_EXPIRED`). 로그아웃은 세션을 무효화하고 200을 돌려준다.

**카테고리·건물 응답**

```json
{ "success": true, "data": [
  { "categoryId": 1, "name": "전자기기" }, { "categoryId": 2, "name": "지갑·카드" },
  { "categoryId": 3, "name": "가방" }, { "categoryId": 4, "name": "의류·잡화" },
  { "categoryId": 5, "name": "기타 물품" } ] }
```

```json
{ "success": true, "data": [
  { "buildingId": 11, "name": "인문사회관", "demoSelectable": true },
  { "buildingId": 17, "name": "기타", "demoSelectable": false } ] }
```

`demoSelectable`은 장소 추천 후보가 될 수 있는 건물이다. 보행 그래프 모드에서는 출입구(ENTRANCE) 노드가 1개 이상 있는 건물, 직선거리 대체 모드에서는 좌표가 등록된 건물이다. 시연 모드의 현재 위치 선택과 전달 장소 추천에는 이 건물만 쓴다. 좌표 값은 보내지 않는다. 건물 이름은 프론트 목록(이학관\~자악관 16개 + 기타)을 샘플 데이터에 그대로 쓴다.

## 습득물

| 메서드 | 경로 | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | /api/found-items | 등록 (multipart, 이미지 필수) | 학생 |
| GET | /api/found-items | 목록·동적 검색 (최신순) | 로그인 |
| GET | /api/found-items/{id} | 상세 | 로그인 |
| PUT | /api/found-items/{id} | 수정 (multipart, 이미지는 바꿀 때만) | 등록자 본인, STORED일 때만 |
| DELETE | /api/found-items/{id} | 삭제 | 등록자 본인, STORED일 때만 |

**등록 요청 (multipart/form-data 필드)**

| 필드 | 필수 | 예시 |
| --- | --- | --- |
| itemName | 예 | 에어팟 프로 |
| categoryId | 예 | 1 |
| foundAt | 예 | 2026-11-21T14:30:00 |
| buildingId | 예 | 3 |
| locationDetail | 아니오 | 2층 |
| description | 아니오 | 흰색 케이스, 스티커 있음 |
| image | 예 | 파일 |

응답 201: `{ "success": true, "data": { "foundItemId": 12 } }`. 예외는 `IMAGE_REQUIRED`, `IMAGE_INVALID`, `FUTURE_DATE`, `VALIDATION_FAILED`.

**동적 검색:** `GET /api/found-items?buildingId=3&categoryId=1&keyword=에어팟&page=0&size=20`. 모든 조건은 선택이고 넣은 것만 AND로 건다. `keyword`는 물품명·설명 부분 일치. 기본으로 수령완료(CLAIMED)는 빼고 보여준다.

```json
{ "success": true, "data": {
  "items": [
    { "foundItemId": 12, "itemName": "에어팟 프로", "categoryName": "전자기기",
      "buildingName": "인문사회관", "locationDetail": "2층",
      "foundAt": "2026-11-21T14:30:00", "imageUrl": "/uploads/12.jpg", "status": "STORED" }
  ],
  "page": 0, "size": 20, "totalCount": 1 } }
```

**상세 응답:** 목록 항목에 `categoryId`, `buildingId`, `description`, `createdAt`, `isMine`(내가 등록했는지), `myClaimStatus`(내 신청 상태, 없으면 null)를 더한다. 프론트는 두 값으로 수령 신청 버튼을 숨기거나 끄며, 습득자 이름·연락처는 보내지 않는다.

상세 응답에는 서버가 계산한 `claimEligibility`도 넣는다. 프론트는 이 값 하나로 버튼을 그린다.

| claimEligibility | 조건 |
| --- | --- |
| `allowed` | 내 물건이 아니고, 신청 이력이 없고, 상태가 STORED 또는 CLAIM\_REQUESTED |
| `own-item` | 내가 등록한 습득물 |
| `already-requested` | 내 신청이 PENDING, APPROVED, COMPLETED |
| `rejected` | 내 신청이 REJECTED |
| `cancelled` | 내 신청이 CANCELLED |
| `closed` | 신청 이력은 없지만 습득물이 AWAITING\_PICKUP 또는 CLAIMED |

판정 순서: own-item → (내 신청이 있으면) already-requested / rejected / cancelled → closed → allowed. 신청 이력은 상태와 관계없이 먼저 보여준다.

**수령 신청 버튼 규칙 (프론트):** 로그인 상태이고, `isMine`이 false이고, `myClaimStatus`가 null이고, `status`가 STORED 또는 CLAIM\_REQUESTED일 때만 누를 수 있다. 나머지는 버튼을 끄고 상태 문구를 보여준다. 검색 결과에는 수령대기(AWAITING\_PICKUP) 물건도 나오지만 신청은 못 한다.

**수정·삭제:** 수정 필드는 등록과 같고 `image`만 선택이다. STORED가 아니면 `ITEM_LOCKED`. 삭제는 소프트 삭제다: 행은 남기고 `deleted_at`에 시각을 기록하며(거절·취소된 신청이 FK로 남아 있을 수 있음), 이미지 파일만 지운다. 삭제된 습득물은 모든 목록·검색·매칭·관리자 화면에서 빠지고, 상세 조회는 404다.

## 분실 신고와 자동 매칭

분실 신고는 신고자 본인만 볼 수 있고 공개 목록은 없다. 남의 신고를 조회하면 403이다.

| 메서드 | 경로 | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | /api/lost-items | 등록. 응답에 매칭 Top 3 포함 | 학생 |
| GET | /api/lost-items/{id} | 상세 | 본인 |
| PUT | /api/lost-items/{id} | 수정 | 본인, OPEN일 때만 |
| DELETE | /api/lost-items/{id} | 삭제 | 본인, OPEN이고 연결된 진행 중 신청이 없을 때 (있으면 `LOST_ITEM_HAS_CLAIM`) |
| GET | /api/lost-items/{id}/matches | 후보 다시 조회 (신고 뒤 새로 올라온 습득물 포함) | 본인 |
| PATCH | /api/lost-items/{id}/close | "찾았음" (서비스 밖에서 찾음) → CLOSED | 본인 |

**등록 요청 (JSON)**

```json
{ "itemName": "에어팟", "categoryId": 1, "lostAt": "2026-11-20T13:00:00",
  "buildingId": 3, "locationDetail": "2층 강의실", "description": "흰색, 케이스에 고양이 스티커" }
```

**등록 응답 201 (매칭 포함)**

```json
{ "success": true, "data": {
  "lostItemId": 7,
  "matches": [
    { "foundItemId": 12, "itemName": "에어팟 프로", "categoryId": 1, "categoryName": "전자기기",
      "buildingName": "인문사회관", "locationDetail": "2층",
      "foundAt": "2026-11-21T14:30:00+09:00", "imageUrl": "/uploads/12.jpg",
      "status": "STORED", "claimEligibility": "allowed",
      "score": 90,
      "reasons": [
        { "code": "CATEGORY", "label": "카테고리 일치", "points": 30 },
        { "code": "BUILDING", "label": "건물 일치", "points": 30 },
        { "code": "NAME", "label": "‘에어팟’ 부분 일치", "points": 30 } ] }
  ] } }
```

후보가 없으면 `"matches": []`로 보내고, 화면은 "추천할 습득물이 없습니다"를 띄운다. `GET .../matches`도 `matches` 배열을 같은 모양으로 돌려준다.

**매칭 근거(`reasons`)**

| code | label | points |
| --- | --- | --- |
| `CATEGORY` | 카테고리 일치 | 30 |
| `BUILDING` | 건물 일치 ("기타"끼리는 없음) | 30 |
| `NAME` | ‘분실 물품명’ 부분 일치 | 30 |
| `DESCRIPTION` | 설명에 ‘분실 물품명’ 포함 | 10 |

- 서버는 점수를 계산한 같은 쿼리 결과로 `reasons`를 만든다. 항상 `score`는 `reasons`의 points 합과 같다.
- 매칭 항목의 `claimEligibility`는 습득물 상세와 같은 규칙이다. 매칭 후보는 STORED·CLAIM\_REQUESTED만 나오므로 `closed`는 없다. `rejected`·`cancelled`·`already-requested` 후보도 목록에 남기고, 화면은 신청 버튼 대신 상태 문구를 보여준다.
- 후보가 없으면 `"matches": []`이고 HTTP 200이다. 화면은 "추천할 습득물이 없습니다"를 띄운다.

**상세 응답 (`GET /api/lost-items/{id}`, 내 분실 신고 목록 항목)**

```json
{ "lostItemId": 7, "itemName": "에어팟", "categoryId": 1, "categoryName": "전자기기",
  "lostAt": "2026-11-20T13:00:00", "buildingId": 3, "buildingName": "인문사회관",
  "locationDetail": "2층 강의실", "description": "흰색, 케이스에 고양이 스티커",
  "status": "OPEN", "createdAt": "2026-11-20T18:00:00" }
```

"찾았음"으로 종료해도 이 신고에 연결된 수령 신청은 자동으로 취소하지 않는다(협의 필요). 필요하면 사용자가 직접 취소한다. 종료된 신고의 수정·삭제와 다시 "찾았음"을 누르는 요청은 `LOST_ITEM_CLOSED`. 진행 중(PENDING·APPROVED) 신청이 연결된 신고의 삭제는 `LOST_ITEM_HAS_CLAIM`이다. 삭제할 수 있는 경우 거절·취소된 신청에 남은 연결(lost\_item\_id)은 null로 바꾼다.

**매칭 규칙**

1. 먼저 걸러내기: 습득 일시가 분실 일시 ±3일 안이고, 상태가 STORED 또는 CLAIM\_REQUESTED이며, 내가 등록한 습득물이 아닌 것만 후보로 둔다.
2. 점수: 카테고리 같음 +30, 건물 같음 +30 ("기타"끼리는 0), 물품명 부분 일치 +30 (양방향), 분실 물품명이 습득물 설명에 들어 있으면 +10. 세부 위치는 점수에 쓰지 않는다.
3. 정렬: 점수 내림차순, 같으면 분실 일시와의 시간 차이가 작은 순, 그래도 같으면 foundItemId가 큰 순(더 최근 등록). 점수 0은 빼고 상위 3건.

가중치는 샘플 데이터 테스트 뒤 조정할 수 있고, 응답 모양은 바뀌지 않는다.

## 수령 신청과 마이페이지

| 메서드 | 경로 | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | /api/found-items/{id}/claims | 수령 신청 | 학생 |
| PATCH | /api/claims/{id}/cancel | 신청 취소 | 신청자 본인, PENDING일 때만 |
| GET | /api/me/summary | 홈 "내 진행 현황" 건수 | 로그인 |
| GET | /api/me/lost-items?status= | 내 분실 신고 (status 생략 시 전체) | 로그인 |
| GET | /api/me/found-items | 내 습득 신고 | 로그인 |
| GET | /api/me/claims | 내 수령 신청과 상태 | 로그인 |

**수령 신청 요청**

```json
{ "ownershipEvidence": "케이스 안쪽에 이니셜 HE가 적혀 있어요", "lostItemId": 7 }
```

`lostItemId`는 선택이다. Top 3 화면에서 신청하면 프론트가 자동으로 넣고, 검색 화면에서는 내 OPEN 분실 신고 중 하나를 고르거나 비워 둔다. 응답 201: `{ "claimId": 31, "status": "PENDING" }`. 예외는 `CLAIM_OWN_ITEM`, `CLAIM_DUPLICATE`(거절 후 재신청 포함), `CLAIM_CLOSED`.

연결하는 분실 신고는 내 것이고 OPEN이어야 한다. 아니면 `LOST_ITEM_INVALID`.

**상태 변화 (서버가 같이 처리)**

- 습득물의 첫 신청이면 습득물이 STORED → CLAIM\_REQUESTED.
- 취소나 거절로 그 습득물의 PENDING 신청이 하나도 없으면 습득물은 STORED로 돌아간다.
- 취소한 신청도 UNIQUE 제약 때문에 같은 물건 재신청이 막힌다 (아래 확인할 것 참고).

**신청 취소 응답:** `{ "claimId": 31, "status": "CANCELLED" }`. 남의 신청은 `FORBIDDEN`, PENDING이 아니면 `CLAIM_NOT_PENDING`, 없으면 404.

`/api/me/**`는 학생 전용이다. 관리자가 호출하면 403 `FORBIDDEN`.

**내 진행 현황 응답**

```json
{ "success": true, "data": { "lostItemCount": 1, "foundItemCount": 2,
  "claimCount": 1, "activeHandoverId": 5 } }
```

건수는 모두 진행 중인 것만 센다. `lostItemCount`는 OPEN 분실 신고, `foundItemCount`는 수령완료·삭제가 아닌 습득 신고, `claimCount`는 PENDING·APPROVED 신청. `activeHandoverId`는 내가 당사자(습득자 또는 분실자)인 진행 중(DONE 제외) 전달 중 가장 최근에 만들어진 1건이다. 여러 건이면 전체는 `GET /api/me/handovers`로 받는다. `activeHandoverId`가 있으면 홈의 "전달 장소 추천" 카드와 "전달 방법 안내"가 전달 진행 패널로 연결된다. 없으면 null.

**내 수령 신청 목록 항목**

```json
{ "claimId": 31, "status": "APPROVED", "requestedAt": "2026-11-21T15:00:00",
  "foundItem": { "foundItemId": 12, "itemName": "에어팟 프로", "imageUrl": "/uploads/12.jpg" },
  "handoverId": 5 }
```

`handoverId`는 APPROVED 이후에만 값이 있다. 내 분실 신고 목록은 분실 신고 상세와 같은 모양이다. 내 습득 신고 목록은 습득물 목록 항목에 `claimCount`(대기 중인 PENDING 신청 수. 0보다 크면 수정·삭제가 막힘)와 `handoverId`(주인 확정 후에만 값, 습득자가 전달 패널로 가는 입구)를 더한다.

## 전달 진행 (FR-07)

관리자가 주인을 확정하면 전달 건이 자동으로 만들어지고, 이후는 습득자와 분실자 두 사람만 이 API를 쓴다.

| 메서드 | 경로 | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | /api/me/handovers | 내가 당사자인 진행 중(DONE 제외) 전달 목록. S10 패널을 띄울지 판단 | 로그인 |
| GET | /api/handovers/{id} | 현재 상태. 패널이 5초마다 호출 | 두 당사자 |
| POST | /api/handovers/{id}/ready | "지금 이동 가능" (30분 유지) | 두 당사자 |
| POST | /api/handovers/{id}/reset | 다시 정하기. 두 사람 상태와 추천 건물을 초기화 | 두 당사자 |
| POST | /api/handovers/{id}/receive | 받았어요 → 수령완료 | 분실자만 |

**상태 조회 응답**

```json
{ "success": true, "data": {
  "handoverId": 5,
  "status": "WAITING",
  "myRole": "OWNER",
  "serverNow": "2026-11-22T20:45:03+09:00",
  "claimId": 31,
  "claimStatus": "APPROVED",
  "item": { "foundItemId": 12, "itemName": "에어팟 프로", "categoryId": 1, "categoryName": "전자기기",
            "buildingName": "인문사회관", "imageUrl": "/uploads/12.jpg", "status": "AWAITING_PICKUP" },
  "finderReadyUntil": null,
  "ownerReadyUntil": "2026-11-22T21:10:00+09:00",
  "finderReady": false,
  "ownerReady": true,
  "building": null,
  "placeSetAt": null,
  "completedAt": null } }
```

- `myRole`은 `FINDER`(습득자) 또는 `OWNER`(분실자). "받았어요" 버튼은 OWNER에게만 보인다.
- `finderReady`, `ownerReady`는 서버가 `readyUntil > serverNow`로 계산한 값이다. 남은 시간은 프론트가 `readyUntil`과 `serverNow`의 차이로 계산해 휴대폰 시계 오차를 피한다.
- `readyUntil`은 만료된 뒤에도 값이 남을 수 있다 (상태는 ready 플래그로 판단).
- `PLACE_SET`이면 `"building": { "buildingId": 8, "name": "인곡관(제1학사관)", "myWalkSeconds": 240, "otherWalkSeconds": 310, "method": "WALK_GRAPH" }`, `placeSetAt`에 시각. 도보 시간과 `method`는 아래 "장소 추천 계산" 절 참고. `DONE`이면 `completedAt`, `claimStatus: "COMPLETED"`, `item.status: "CLAIMED"`.
- 좌표와 상대의 이름·연락처는 보내지 않는다. 추천 건물에서 서로를 찾는 것은 두 사람이 알아서 한다 (11/22 결정).
- ready, reset, receive의 성공 응답도 이 조회 응답과 같은 모양이다. 프론트는 응답으로 화면을 바로 갱신한다.
- `GET /api/me/handovers` 응답은 같은 모양의 배열이다 (DONE 제외).

**이동 가능 요청**

```json
{ "lat": 36.6905, "lng": 126.5841 }
```

시연 모드에서는 GPS 대신 현재 건물을 고른다: `{ "buildingId": 7 }`. 서버는 그 건물의 출입구 노드(여러 개면 node\_id가 가장 작은 것)를 현재 위치로 쓴다. 직선거리 대체 모드에서는 건물 좌표를 쓴다. 둘 다 없으면 `LOCATION_REQUIRED`. 예시 좌표는 형식 설명용이다.

응답은 상태 조회와 같은 모양이다. 두 번째 사람이 누른 경우 서버가 건물을 계산해 `status: "PLACE_SET"`과 `building`을 바로 돌려주고, 임시 좌표는 지운다.

**받았어요:** 응답은 조회 응답과 같은 모양이며 `status: "DONE"`이다. 같은 트랜잭션에서 수령 신청 COMPLETED, 습득물 CLAIMED로 바꾸고 습득자에게 HANDOVER\_DONE 알림을 만든다. 예외 판정 순서: 없음 404 → 당사자 아님 `HANDOVER_NOT_PARTY` → 습득자가 호출 `FORBIDDEN` → 이미 완료 `HANDOVER_DONE`.

**장소 추천 계산 (FR-07, 11/23 C안)**

기획안 6장의 원칙(두 사람 중 더 오래 가는 쪽이 가장 덜 가는 건물)을 그대로 쓰되, 직선거리 대신 캠퍼스 길을 따라 걷는 시간(언덕·계단 반영, 오르막과 내리막을 따로 계산)으로 비교한다. 테이블과 계산식은 `docs/ERD.md`의 "보행 그래프와 장소 추천" 절.

1. 두 번째 ready가 들어오면 HANDOVER 행을 잠근 상태에서 두 사람의 위치를 각각 가장 가까운 보행 노드에 맞춘다. 맞추는 데 걸리는 시간은 직선거리 ÷ 1.25m/s로 더한다. 시연 모드는 고른 건물의 출입구 노드를 쓰고 더하는 시간은 0이다.
2. `WALK_TIME`(미리 계산한 노드→건물 최단 도보 시간)을 두 노드로 한 번 조회한다.
3. 건물마다 A시간·B시간을 구해 `max(A, B)`가 가장 작은 건물을 고른다. 같으면 `A + B`가 작은 건물, 그래도 같으면 buildingId가 작은 건물.
4. 각자의 시간을 HANDOVER의 finder·owner\_walk\_seconds에 저장하고, 임시 좌표를 지운다.

| 응답 필드 (`building` 안) | 뜻 |
| --- | --- |
| `myWalkSeconds` | 요청한 사람의 예상 도보 시간(초). 화면은 분으로 올림해 "도보 약 4분" |
| `otherWalkSeconds` | 상대의 예상 도보 시간(초). 공평하게 골랐음을 보여주는 용도 |
| `method` | `WALK_GRAPH` 보행 그래프로 계산, `STRAIGHT_LINE` 직선거리 대체 계산. `STRAIGHT_LINE`이면 두 도보 시간은 null |

- 대체 계산: WALK\_TIME이 비어 있거나(그래프 데이터 미입력) 두 사람 모두 닿을 수 있는 건물이 없으면 기획안의 `ST_Distance_Sphere` SQL로 계산한다. 시연이 멈추지 않게 하기 위한 안전장치다.
- 관리자 A4 응답의 `building`은 `{ buildingId, name }`만 보낸다.

**상태별 허용 동작**

| 전달 상태 | ready | reset | receive |
| --- | --- | --- | --- |
| WAITING | 가능. 내 이동 가능이 아직 유효하면 `HANDOVER_ALREADY_READY` (연장 없음) | 가능 (내 이동 가능 해제) | 가능 (추천 없이 만나도 됨) |
| PLACE\_SET | `HANDOVER_PLACE_SET` | 가능 → WAITING | 가능 |
| DONE | `HANDOVER_DONE` | `HANDOVER_DONE` | `HANDOVER_DONE` |

- 30분이 지나 이동 가능이 풀리면 그 사람의 임시 좌표도 다음 조회 때 지운다.
- 시연 모드의 `buildingId`는 설정값(`app.demo-mode: true`)일 때만 받는다. `demoSelectable`이 false인 건물("기타" 등)을 보내면 `LOCATION_REQUIRED`. 시연 모드가 꺼져 있는데 `buildingId`만 보내도 `LOCATION_REQUIRED`. 좌표와 `buildingId`를 함께 보내면 좌표를 쓴다.
- 두 사람이 거의 동시에 ready를 눌러도 건물 계산은 한 번만 한다 (전달 행 잠금).

**예외 상황별 처리**

| 상황 | 서버 상태 변경 | 응답 | 프론트가 쓸 정보 | 알림 |
| --- | --- | --- | --- | --- |
| 한 사람만 이동 가능인 채 30분 만료 | 상태 그대로 WAITING. 다음 조회 때 그 사람의 임시 좌표 삭제 | 조회 200, `xxxReady: false` | 동그라미 끄고 "다시 누르기" 표시 | 없음 |
| 장소 확정 후 30분 경과 | 변화 없음. PLACE\_SET과 추천 건물 유지 (협의 필요) | 조회 200 | 건물 이름 계속 표시, ready 플래그는 무시 | 없음 |
| 장소 확정 후 다시 정하기 | WAITING, 두 사람 readyUntil·building·placeSetAt를 null로 | 200 + 조회 응답 | 상대는 다음 5초 조회에서 변경을 봄 | 없음 (협의 필요, 상대 알림 추가안) |
| 두 사람이 거의 동시에 이동 가능 | HANDOVER 행을 `SELECT … FOR UPDATE`로 잠가 순서대로 처리. 두 번째 요청에서 건물을 한 번만 계산하고 PLACE\_SET | 먼저 처리된 쪽은 WAITING, 나중 쪽은 PLACE\_SET 응답 | 먼저 누른 쪽도 5초 안에 PLACE\_SET을 받음 | HANDOVER\_READY(상대에게), HANDOVER\_PLACE\_SET(둘 다) |
| 추천 건물 없이 직접 전달 | WAITING에서도 receive 가능 → DONE, building은 null로 남음 | 200 | 완료 화면 | HANDOVER\_DONE(습득자) |
| 수령완료 후 중복 요청 | 변화 없음 | 409 `HANDOVER_DONE` | 다시 조회해서 DONE 화면 표시 | 없음 |
| 폴링 중 세션 만료 | 없음 | 401 `SESSION_EXPIRED` | 폴링 중단, 로그인 화면 | 없음 |
| 폴링 중 네트워크 오류 | 없음 | 응답 없음 | 마지막 상태 유지, 경고 표시, 다음 주기에 재시도 | 없음 |
| 폴링 중 상대가 수령완료 | DONE | 조회 200, `status: "DONE"` | 폴링 중단, 완료 표시 | HANDOVER\_DONE(습득자) |
| 당사자가 아니거나 없는 전달 | 없음 | 403 `HANDOVER_NOT_PARTY` / 404 | 패널 숨김 | 없음 |

브라우저 위치 권한은 HTTPS에서만 동작한다. 지금 쓰는 ngrok 주소는 HTTPS라 휴대폰 시연에서도 쓸 수 있다.

**패널 표시 방식 (프론트)**

- 두 사람 상태는 작은 동그라미 두 개로 보여준다. `me.ready`, `other.ready`가 true면 불이 켜지고, 옆에 남은 시간(`readyUntil` 기준)을 표시한다.
- 패널은 특정 페이지가 아니라 레이아웃 공통 영역에 둔다. 그래야 어느 화면에 있어도 상대의 불이 켜지는 걸 볼 수 있다.
- 5초 폴링은 진행 중인 전달이 있을 때만 돌리고, DONE이 되거나 로그아웃하면 멈춘다. 로그인 직후 `GET /api/me/handovers`로 진행 중인 전달을 확인해 패널을 띄운다.
- `status`가 PLACE\_SET으로 바뀌는 순간 추천 건물 이름을 크게 띄운다 (예: "인곡관에서 만나세요").
- 위치 권한을 거부하면 패널에 "위치 권한을 허용해야 이동 가능을 알릴 수 있어요"를 띄우고 요청을 보내지 않는다.

## 알림 (종 아이콘)

상태가 바뀔 때 서버가 알림을 저장하고, 종 아이콘은 읽지 않은 알림 수를 빨간 점으로, 누르면 목록을 보여준다. 알림은 상태 변경과 같은 트랜잭션 안에서 만든다.

| 종류 (`type`) | 받는 사람 | 언제 | 문구 예시 |
| --- | --- | --- | --- |
| `CLAIM_RECEIVED` | 습득자 | 내 습득물에 수령 신청이 들어옴 | 에어팟 프로에 수령 신청이 들어왔어요 |
| `CLAIM_APPROVED` | 신청자 | 관리자가 주인으로 확정 | 에어팟 프로의 주인으로 확정됐어요 |
| `CLAIM_REJECTED` | 신청자 | 관리자 거절, 또는 다른 사람 확정으로 자동 거절 | 에어팟 프로 수령 신청이 거절됐어요 |
| `HANDOVER_STARTED` | 습득자 | 주인이 확정되어 전달이 시작됨 | 에어팟 프로 주인이 확정됐어요. 전달을 진행해 주세요 |
| `HANDOVER_READY` | 상대 당사자 | 상대가 이동 가능을 누름 | 상대가 21:10에 이동 가능을 눌렀어요 |
| `HANDOVER_PLACE_SET` | 두 당사자 | 추천 건물이 정해짐 | 인곡관에서 만나세요 |
| `HANDOVER_DONE` | 습득자 | 분실자가 받았어요를 누름 | 에어팟 프로 전달이 완료됐어요 |

| 메서드 | 경로 | 설명 | 권한 |
| --- | --- | --- | --- |
| GET | /api/me/notifications | 내 알림 목록 (최신순, 최근 30건) | 로그인 |
| GET | /api/me/notifications/unread-count | 읽지 않은 알림 수 | 로그인 |
| PATCH | /api/me/notifications/{id}/read | 읽음 처리 | 본인 |
| PATCH | /api/me/notifications/read-all | 모두 읽음 | 로그인 |

**알림 목록 항목**

```json
{ "notificationId": 41, "type": "HANDOVER_READY", "message": "상대가 21:10에 이동 가능을 눌렀어요",
  "isRead": false, "createdAt": "2026-11-22T21:10:00",
  "link": { "kind": "HANDOVER", "id": 5 } }
```

- 읽지 않은 수 응답: `{ "unreadCount": 3 }`. 읽음 처리 응답: 목록 항목 1개(이미 읽은 알림도 200). 남의 알림은 `FORBIDDEN`. 모두 읽음 응답: `{ "updatedCount": 8 }`.
- 이동할 화면이 없는 알림은 `link`가 null이다.
- 알림은 학생에게만 간다. 관리자가 `/api/me/notifications/**`를 호출하면 403.
- `message`는 서버가 만들어 보내므로 프론트는 그대로 띄운다.
- `link.kind`는 `HANDOVER`(전달 패널), `CLAIM`(내 수령 신청), `FOUND_ITEM`(내 습득물). 알림을 누르면 읽음 처리하고 해당 화면으로 이동한다.
- 읽지 않은 수는 30초마다 조회한다. 전달 패널이 떠 있는 동안에는 패널의 5초 조회로 불이 먼저 바뀌므로, 종은 기록을 남기는 용도다.
- DB에는 NOTIFICATION 테이블(notification\_id, student\_id FK, type, message, link\_kind, link\_id, is\_read, created\_at)이 추가된다. 기획 확정안의 ERD도 9개 테이블로 고쳐야 한다.

## 관리자

모두 관리자 로그인이 필요하고, 학생이 호출하면 403이다. 관리자는 주인 확정까지만 하고 수령완료는 처리하지 않는다.

| 메서드 | 경로 | 설명 | 화면 |
| --- | --- | --- | --- |
| GET | /api/admin/summary | 상태별 건수와 총합 | A1 |
| GET | /api/admin/found-items?status=&page=&size= | 습득물 목록 (페이지) | A2 |
| GET | /api/admin/found-items/{id} | 습득물 상세 (설명 포함) | A2 |
| GET | /api/admin/claims?claimStatus=&foundItemId= | 습득물별 신청 묶음 | A3 |
| GET | /api/admin/found-items/{id}/claims | 습득물 1건의 신청자 목록 | A3 |
| POST | /api/admin/claims/{id}/approve | 주인 확정 (트랜잭션) | A3 |
| POST | /api/admin/claims/{id}/reject | 거절 | A3 |
| GET | /api/admin/handovers?status=&from=&to= | 전달 목록과 집계 (보기 전용) | A4 |
| GET | /api/admin/handovers/{id} | 전달 상세 | A4 |
| GET | /api/admin/path-graph | 보행 그래프 상태: 노드·간선·건물 수, 계산 시각, 닿을 수 없는 노드·건물 목록 | 운영용 |
| POST | /api/admin/path-graph/rebuild | `data/` 폴더의 CSV 3개를 다시 읽어 그래프를 교체하고 WALK\_TIME을 다시 계산 | 운영용 |
| GET | /api/admin/path-graph/route?fromNode=&toBuilding= | 노드→건물의 계산된 시간과 지나는 노드 목록. 실측 보정용 | 운영용 |

**보행 그래프 관리 (운영용, 화면 없음)**

서버가 시작할 때 PATH\_NODE가 비어 있으면 `data/buildings.csv`, `data/path_nodes.csv`, `data/path_edges.csv`를 읽어 넣고 WALK\_TIME을 계산한다. 이미 있으면 WALK\_TIME만 다시 계산한다. CSV를 고친 뒤에는 `rebuild`를 호출한다.

`GET /api/admin/path-graph` 응답:

```json
{ "success": true, "data": {
  "nodeCount": 52, "edgeCount": 74, "candidateBuildingCount": 16,
  "computedAt": "2026-11-28T10:00:00+09:00",
  "unreachableNodes": ["J-31"], "buildingsWithoutEntrance": ["기타"] } }
```

`unreachableNodes`는 출입구가 있는 건물 중 하나라도 닿지 못하는 노드(길이 끊긴 곳 찾기용), `candidateBuildingCount`는 한 노드에서라도 닿는 건물 수다. `rebuild`는 같은 모양으로 응답한다. CSV에 오류가 있으면(없는 node\_code 참조, 중복 코드, 좌표 형식 오류) 400 `VALIDATION_FAILED`이고 `message`에 파일명과 행 번호를 넣으며, 기존 그래프는 그대로 둔다. 진행 중인 전달의 추천 결과는 바뀌지 않는다.

`GET /api/admin/path-graph/route?fromNode=E-DORM1-1&toBuilding=INMUN` 응답: `{ "seconds": 312, "nodes": ["E-DORM1-1", "J-04", "J-07", "E-INMUN-1"] }`. 코드가 없으면 404.

**A1 상태별 건수**

```json
{ "success": true, "data": {
  "counts": { "STORED": 18, "CLAIM_REQUESTED": 4, "AWAITING_PICKUP": 2, "CLAIMED": 9 },
  "total": 33 } }
```

`total`은 네 상태의 합(삭제된 습득물 제외)이다. 건수가 0인 상태도 빼지 않고 0으로 보낸다.

**A2 습득물 목록·상세:** `status`는 생략 시 전체. 목록 항목은 학생용 습득물 목록 항목과 같고 `createdAt`, `claimCount`(모든 상태의 신청 수. 학생용 내 습득 신고의 claimCount와 다름)를 더한다. 상세는 여기에 `description`과 습득자 `finder: { studentId, name }`를 더한다. 응답은 공통 페이지 형식(`items`, `page`, `size`, `totalCount`).

**A3 신청 묶음 (`GET /api/admin/claims`)**

기본값은 PENDING 신청이 있는 습득물만, 신청이 가장 오래된 습득물부터 보낸다. `claimStatus`를 주면 그 상태의 신청이 있는 묶음을 보낸다. 묶음 안의 `claims`에는 상태와 관계없이 그 습득물의 모든 신청을 오래된 순으로 넣는다(거절·취소 이력도 보이도록). 페이지 없이 배열로 보낸다.

```json
{ "success": true, "data": [
  { "item": { "foundItemId": 12, "itemName": "에어팟 프로", "categoryName": "전자기기",
              "buildingName": "인문사회관", "locationDetail": "2층",
              "foundAt": "2026-11-21T14:30:00+09:00", "imageUrl": "/uploads/12.jpg", "status": "CLAIM_REQUESTED" },
    "handoverId": null,
    "claims": [
      { "claimId": 31, "status": "PENDING", "requestedAt": "2026-11-21T15:00:00+09:00",
        "student": { "studentId": "20211234", "name": "김하은", "phone": "010-1234-5678" },
        "ownershipEvidence": "케이스 안쪽에 이니셜 HE",
        "lostItem": { "lostItemId": 7, "itemName": "에어팟", "lostAt": "2026-11-20T13:00:00+09:00",
                      "buildingName": "인문사회관" },
        "processedAt": null } ] }
] }
```

`lostItem`은 연결된 분실 신고가 없으면 null이다. `GET /api/admin/found-items/{id}/claims`는 이 중 한 묶음을 보낸다. 신청자 연락처는 관리자가 증빙을 확인할 때만 쓴다.

**주인 확정 응답**

```json
{ "success": true, "data": {
  "claimId": 31, "claimStatus": "APPROVED",
  "itemStatus": "AWAITING_PICKUP",
  "rejectedClaimIds": [32, 35],
  "handover": { "handoverId": 5, "status": "WAITING" },
  "closedLostItemId": 7 } }
```

하나의 트랜잭션에서 순서대로 처리하고, 하나라도 실패하면 전부 롤백한다.

1. 습득물 행을 `SELECT … FOR UPDATE`로 잠근다.
2. 습득물이 AWAITING\_PICKUP·CLAIMED이거나 이미 APPROVED 신청이 있으면 409 `OWNER_ALREADY_CONFIRMED`. (취소로 STORED가 된 경우는 3에서 `CLAIM_NOT_PENDING`)
3. 선택한 신청을 다시 읽어 PENDING이 아니면 409 `CLAIM_NOT_PENDING`.
4. 선택한 신청 APPROVED, 나머지 PENDING 신청 REJECTED (processedAt·처리 관리자 기록, 자동 거절 사유는 "다른 신청자가 주인으로 확정됨").
5. 습득물 AWAITING\_PICKUP.
6. 전달 건 WAITING 생성.
7. 연결된 분실 신고가 OPEN이면 CLOSED (없으면 `closedLostItemId: null`).
8. 알림 생성: 확정된 사람 CLAIM\_APPROVED, 거절된 사람들 CLAIM\_REJECTED, 습득자 HANDOVER\_STARTED.

**거절:** 요청 `{ "reason": "증빙이 물품 특징과 다름" }`(선택, 255자 이내, 본문 자체를 생략해도 됨). 사유는 DB에 남지만 학생 응답에는 보내지 않는다. 응답 `{ "claimId": 32, "claimStatus": "REJECTED", "itemStatus": "CLAIM_REQUESTED" }`. 마지막 PENDING 신청을 거절하면 `itemStatus`는 STORED. 같은 잠금 순서를 따르고 CLAIM\_REJECTED 알림을 만든다.

**동시성:** 습득물 상태를 바꾸거나 상태를 보고 판단하는 요청은 모두 같은 습득물 행을 `SELECT … FOR UPDATE`로 먼저 잠근 뒤 처리한다. 대상은 수령 신청 생성(STORED→CLAIM\_REQUESTED 전환 포함), 신청 취소, 주인 확정, 거절, 습득물 수정·삭제다. 잠금을 얻은 뒤 상태를 다시 읽어 판단하므로, 확정 직후 들어온 신청은 `CLAIM_CLOSED`, 신청 직후 들어온 수정·삭제는 `ITEM_LOCKED`를 받는다. 두 관리자가 동시에 확정하거나, 취소와 확정이 겹치면 먼저 잠금을 얻은 쪽이 처리되고 다른 쪽은 409(`OWNER_ALREADY_CONFIRMED` 또는 `CLAIM_NOT_PENDING`)를 받는다. 화면은 409를 받으면 목록을 다시 조회한다.

**A4 전달 목록 (`GET /api/admin/handovers`)**

`status`는 `WAITING`, `PLACE_SET`, `DONE`, `ONGOING`(WAITING+PLACE\_SET) 중 하나이고 생략하면 전체. `from`, `to`는 주인 확정일 기준 날짜(`2026-11-01`)로 양쪽 포함. 최신 확정순, 배열.

```json
{ "success": true, "data": {
  "summary": { "ongoing": 2, "placeSet": 1, "done": 9 },
  "items": [
    { "handoverId": 5, "status": "PLACE_SET",
      "item": { "foundItemId": 12, "itemName": "에어팟 프로", "imageUrl": "/uploads/12.jpg", "status": "AWAITING_PICKUP" },
      "claim": { "claimId": 31, "status": "APPROVED", "approvedAt": "2026-11-22T10:00:00+09:00" },
      "finderName": "정승원", "ownerName": "김하은",
      "building": { "buildingId": 8, "name": "인곡관(제1학사관)" },
      "finderReadyUntil": null, "ownerReadyUntil": null,
      "placeSetAt": "2026-11-22T20:46:00+09:00", "completedAt": null } ] } }
```

`summary.ongoing`은 WAITING+PLACE\_SET, `placeSet`은 그중 PLACE\_SET, `done`은 DONE 건수다(날짜 필터 적용). 상세 API는 목록 항목과 같은 모양 하나를 보낸다. 관리자에게 좌표는 보내지 않고, 이동 가능 시각(readyUntil)은 보낸다 (보고서 ④-4 기본안 A).

## 기획 확정안과 화면 시안 비교 (협의용)

비교 대상은 11/18 기획 확정안과 현재 받은 홈 화면 시안(PC, 모바일) 2장이다. 다른 화면은 아직 못 봤으므로 아래 두 번째 표는 "확인해야 할 목록"이다.

**시안에서 달라진 점**

| 항목 | 기획 확정안 | 화면 시안 | 제안 |
| --- | --- | --- | --- |
| 로그인 전 습득물 보기 | 로그인해야 조회 (11/22 결정) | 로그인 전 홈에 "최근 등록된 습득물" 3건이 보임 | 로그인 전에는 그 자리에 로그인 안내를 띄움 |
| "전달 장소 추천" 카드 (PC 홈) | 독립 기능이 아니라 주인 확정 후 두 당사자에게만 뜨는 패널 | 분실 신고·습득 등록과 나란히 누구나 누르는 메뉴처럼 보임 | 진행 중인 전달이 있으면 패널로, 없으면 "주인 확정 후 이용할 수 있어요" 안내 후 이용 안내로 이동 |
| 카테고리 아이콘 (모바일) | 카테고리 5개: 전자기기, 지갑·카드, 가방, 의류·잡화, 기타 물품 | "가방·지갑류"가 있고 "더보기"가 있음 | "가방"으로 고침. 5개가 다 들어가니 "더보기" 대신 "기타 물품" 아이콘. 목록은 `GET /api/categories`로 받기 |
| 모바일 하단 탭 "MY" | 마이페이지(S9) = 내 분실 신고 / 내 습득 신고 / 내 수령 신청 | "내 신청"과 "MY"가 따로 있음 | MY에 무엇이 들어갈지 정해야 함. 추천: MY = 내 정보 + 로그아웃. 정보 수정 API는 없으므로 보기만 |
| 메뉴 이름 | S9 마이페이지 | PC "내 신고·신청", 모바일 "내 신청" | 같은 화면이면 이름을 하나로 통일 |
| 모바일 로그인 입구 | — | PC는 오른쪽 위 로그인 버튼, 모바일 헤더에는 로그인 버튼이 안 보임 | 모바일은 MY 탭에서 로그인하는지 확인 |
| 내 진행 현황 박스 (PC 홈) | 기획에 없음 | 내 신고한 건, 내 수령 신청, 전달 방법 안내 | 유지. `GET /api/me/summary`로 건수를 채움 (이미 명세에 반영) |
| 이용 안내 페이지, 이용 절차 6단계 | 기획에 없음 | 메뉴와 홈에 있음 | 유지. API 필요 없는 고정 텍스트. 6단계 내용은 확정안과 일치함 |
| 상세검색 | 건물·카테고리·키워드 중 고른 것만 조합 | "상세검색" 드롭다운 (내용 미확인) | 드롭다운 안에 건물·카테고리 선택이 있는지 확인. 자유 입력 장소 칸이면 안 됨 |
| 습득물 카드의 장소 | 건물(선택) + 세부 위치(자유 입력) | "인문사회관 2층" | 일치. `buildingName` + `locationDetail`를 이어 붙이면 됨 |
| 알림(종) | 기획에 없었음 | 아이콘 있음 | 11/22 추가 결정. 명세의 알림 절 참고 |
|  |  |  |  |

**시안에서 아직 못 본 화면 (나머지 화면을 받아 확인)**

- [ ] S3 분실 신고 폼: 건물 드롭다운(필수) + 세부 위치(선택) 두 칸으로 나뉘어 있는지
- [ ] S4 매칭 결과 Top 3: 등록 직후 이동, 후보 0건일 때 "추천할 습득물이 없습니다"
- [ ] S5 습득물 등록 폼: 사진 필수, 건물 + 세부 위치
- [ ] S7 습득물 상세: 수령 신청 버튼을 꺼야 하는 경우(내 물건, 이미 신청, 수령대기)
- [ ] S8 수령 신청: 소유 증빙 입력 + "연결할 분실 신고" 선택(선택 사항)
- [ ] S9 내 신고·신청: 분실 신고의 "찾았음", "후보 다시 조회", 신청 취소
- [ ] S10 전달 진행 패널: 레이아웃 공통 영역, 두 사람 동그라미, 남은 시간, 추천 건물, 다시 정하기, 받았어요(분실자만), 시연 모드 건물 선택
- [ ] A1\~A4 관리자 화면: 상태별 건수, 습득물별 신청자 펼치기와 주인 확정·거절, 전달 현황(보기 전용). 수령 완료 버튼은 관리자 화면에 있으면 안 됨
- [ ] 로그인: 기본 학생, "관리자" 버튼으로 전환

## 프론트와 결정한 것

**결정됨 (11/22)**

- 카테고리: 전자기기, 지갑·카드, 가방, 의류·잡화, 기타 물품. 시안의 "가방·지갑류"는 "가방"으로 고친다.
- 전달 단계 연락: 서비스가 연락처를 주지 않고, 추천 건물에서 두 사람이 알아서 만난다.
- 취소한 신청도 같은 물건에 재신청 불가 (UNIQUE 제약 그대로).
- 습득물 목록·검색·상세는 로그인 필요.
- 상대의 이동 가능 상태는 전달 패널의 동그라미에 불이 켜지는 방식으로 보여준다.
- 알림(종): 상대의 이동 가능(누른 시각), 수령 신청 확정·거절 등을 알림으로 받는다 (알림 절).
- 로그인: 화면 하나에 기본은 학생, "관리자" 버튼을 누르면 관리자 로그인으로 바뀐다. 요청의 `role`로 구분한다.

**프론트·백엔드 연결 방식**

- 프론트·백엔드 연결: Vite proxy 방식. 프론트는 `/api/...`처럼 상대 경로로 호출하고, 통합 테스트와 시연 때는 프론트 개발 서버와 백엔드를 같은 노트북에서 띄운다 (11/22 결정).

```ts
// vite.config.ts — 기존 allowedHosts 목록은 그대로 두고 proxy만 추가
export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: [
      'implement-tableware-staunch.ngrok-free.dev',
    ],
    proxy: {
      '/api': 'http://localhost:8080',
      '/uploads': 'http://localhost:8080',
    },
  },
})
```
