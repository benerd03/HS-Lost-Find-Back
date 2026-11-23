-- 분실·습득물 시스템 스키마 (MySQL 8.0.16 이상, utf8mb4)
-- 기준: 기획 확정안(11/17) ERD 8개 + NOTIFICATION + 보행 그래프 3개(FR-07 C안) = 12개 테이블
-- 시간 컬럼은 모두 한국 시간(Asia/Seoul) DATETIME으로 저장한다.
-- 변경 내역과 설명은 docs/ERD.md 참고.

SET NAMES utf8mb4;

DROP TABLE IF EXISTS walk_time;
DROP TABLE IF EXISTS path_edge;
DROP TABLE IF EXISTS path_node;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS handover;
DROP TABLE IF EXISTS claim_request;
DROP TABLE IF EXISTS found_item;
DROP TABLE IF EXISTS lost_item;
DROP TABLE IF EXISTS building;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS admin;
DROP TABLE IF EXISTS student;

-- ---------------------------------------------------------------
-- 사용자
-- ---------------------------------------------------------------
CREATE TABLE student (
    student_id     VARCHAR(8)   NOT NULL,
    password_hash  VARCHAR(60)  NOT NULL,                -- BCrypt
    name           VARCHAR(30)  NOT NULL,
    department     VARCHAR(50)  NOT NULL,
    phone          VARCHAR(13)  NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id),
    CONSTRAINT chk_student_id    CHECK (student_id REGEXP '^[0-9]{8}$'),
    CONSTRAINT chk_student_phone CHECK (phone REGEXP '^010-[0-9]{4}-[0-9]{4}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin (
    admin_id       VARCHAR(20)  NOT NULL,
    password_hash  VARCHAR(60)  NOT NULL,
    name           VARCHAR(30)  NOT NULL,
    department     VARCHAR(50)  NOT NULL,                -- 소속 부서
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 공통 코드
-- ---------------------------------------------------------------
CREATE TABLE category (
    category_id    BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(30)  NOT NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- lat·lng: 화면 표시와 직선거리 대체 계산용 대표 좌표. "기타"는 NULL.
-- 추천 후보(demoSelectable)는 ENTRANCE 노드가 1개 이상 있는 건물이다.
CREATE TABLE building (
    building_id    BIGINT        NOT NULL AUTO_INCREMENT,
    building_code  VARCHAR(20)   NOT NULL,               -- 데이터 입력용 영문 코드 (예: INMUN, ETC)
    name           VARCHAR(50)   NOT NULL,
    lat            DECIMAL(9,6)  NULL,
    lng            DECIMAL(9,6)  NULL,
    sort_order     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (building_id),
    UNIQUE KEY uk_building_code (building_code),
    UNIQUE KEY uk_building_name (name),
    CONSTRAINT chk_building_coord CHECK ((lat IS NULL AND lng IS NULL) OR (lat IS NOT NULL AND lng IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 분실 신고 / 습득물
-- ---------------------------------------------------------------
CREATE TABLE lost_item (
    lost_item_id      BIGINT         NOT NULL AUTO_INCREMENT,
    student_id        VARCHAR(8)     NOT NULL,
    category_id       BIGINT         NOT NULL,
    item_name         VARCHAR(100)   NOT NULL,
    lost_at           DATETIME       NOT NULL,
    lost_building_id  BIGINT         NOT NULL,
    location_detail   VARCHAR(100)   NULL,
    description       VARCHAR(1000)  NULL,
    status            VARCHAR(10)    NOT NULL DEFAULT 'OPEN',
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (lost_item_id),
    KEY idx_lost_student (student_id, status),
    CONSTRAINT fk_lost_student  FOREIGN KEY (student_id)       REFERENCES student (student_id),
    CONSTRAINT fk_lost_category FOREIGN KEY (category_id)      REFERENCES category (category_id),
    CONSTRAINT fk_lost_building FOREIGN KEY (lost_building_id) REFERENCES building (building_id),
    CONSTRAINT chk_lost_status  CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- student_id = 습득자. 물건은 수령완료 전까지 습득자가 직접 보관한다.
-- deleted_at: 소프트 삭제 (API 명세 v1.2). 삭제된 행은 모든 조회에서 제외.
CREATE TABLE found_item (
    found_item_id      BIGINT         NOT NULL AUTO_INCREMENT,
    student_id         VARCHAR(8)     NOT NULL,
    category_id        BIGINT         NOT NULL,
    item_name          VARCHAR(100)   NOT NULL,
    found_at           DATETIME       NOT NULL,
    found_building_id  BIGINT         NOT NULL,
    location_detail    VARCHAR(100)   NULL,
    description        VARCHAR(1000)  NULL,
    image_path         VARCHAR(255)   NOT NULL,          -- 이미지는 경로만 저장 (예: /uploads/uuid.jpg)
    status             VARCHAR(20)    NOT NULL DEFAULT 'STORED',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at         DATETIME       NULL,
    PRIMARY KEY (found_item_id),
    KEY idx_found_category (category_id),
    KEY idx_found_status (status),
    KEY idx_found_found_at (found_at),
    KEY idx_found_building (found_building_id),
    KEY idx_found_student (student_id),
    CONSTRAINT fk_found_student  FOREIGN KEY (student_id)        REFERENCES student (student_id),
    CONSTRAINT fk_found_category FOREIGN KEY (category_id)       REFERENCES category (category_id),
    CONSTRAINT fk_found_building FOREIGN KEY (found_building_id) REFERENCES building (building_id),
    CONSTRAINT chk_found_status  CHECK (status IN ('STORED', 'CLAIM_REQUESTED', 'AWAITING_PICKUP', 'CLAIMED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 수령 신청
-- ---------------------------------------------------------------
-- UNIQUE(student_id, found_item_id): 중복 신청, 거절·취소 후 재신청 방지 (11/18, 11/22 확정)
CREATE TABLE claim_request (
    claim_id            BIGINT         NOT NULL AUTO_INCREMENT,
    found_item_id       BIGINT         NOT NULL,
    student_id          VARCHAR(8)     NOT NULL,          -- 신청자
    lost_item_id        BIGINT         NULL,              -- 연결한 분실 신고 (선택)
    ownership_evidence  VARCHAR(1000)  NOT NULL,
    approval_status     VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    requested_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        DATETIME       NULL,              -- 관리자 확정·거절 시각 (자동 거절 포함)
    processed_admin_id  VARCHAR(20)    NULL,
    reject_reason       VARCHAR(255)   NULL,
    cancelled_at        DATETIME       NULL,
    completed_at        DATETIME       NULL,
    PRIMARY KEY (claim_id),
    UNIQUE KEY uk_claim_student_item (student_id, found_item_id),
    KEY idx_claim_item_status (found_item_id, approval_status),
    KEY idx_claim_lost_item (lost_item_id),
    CONSTRAINT fk_claim_found_item FOREIGN KEY (found_item_id)      REFERENCES found_item (found_item_id),
    CONSTRAINT fk_claim_student    FOREIGN KEY (student_id)         REFERENCES student (student_id),
    CONSTRAINT fk_claim_lost_item  FOREIGN KEY (lost_item_id)       REFERENCES lost_item (lost_item_id) ON DELETE SET NULL,
    CONSTRAINT fk_claim_admin      FOREIGN KEY (processed_admin_id) REFERENCES admin (admin_id),
    CONSTRAINT chk_claim_status    CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 전달 진행 (FR-07)
-- ---------------------------------------------------------------
-- 주인 확정 때 1행 생성. 습득자 = found_item.student_id, 분실자 = claim_request.student_id
-- *_lat·*_lng: "이동 가능" 때 받은 임시 좌표. 건물 계산 후 또는 ready 만료 후 NULL.
-- *_node_id: 임시 좌표를 가장 가까운 보행 노드에 맞춘 결과 (좌표와 같이 지움)
-- *_walk_seconds: 장소 확정 시 계산한 각자의 예상 도보 시간 (다시 정하기 때 NULL)
CREATE TABLE handover (
    handover_id          BIGINT        NOT NULL AUTO_INCREMENT,
    claim_id             BIGINT        NOT NULL,
    status               VARCHAR(10)   NOT NULL DEFAULT 'WAITING',
    finder_ready_until   DATETIME      NULL,
    owner_ready_until    DATETIME      NULL,
    finder_lat           DECIMAL(9,6)  NULL,
    finder_lng           DECIMAL(9,6)  NULL,
    owner_lat            DECIMAL(9,6)  NULL,
    owner_lng            DECIMAL(9,6)  NULL,
    building_id          BIGINT        NULL,              -- 추천 건물 (PLACE_SET 이후)
    finder_walk_seconds  INT           NULL,
    owner_walk_seconds   INT           NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- = 주인 확정 시각
    place_set_at         DATETIME      NULL,
    completed_at         DATETIME      NULL,
    PRIMARY KEY (handover_id),
    UNIQUE KEY uk_handover_claim (claim_id),
    KEY idx_handover_status (status, created_at),
    CONSTRAINT fk_handover_claim    FOREIGN KEY (claim_id)    REFERENCES claim_request (claim_id),
    CONSTRAINT fk_handover_building FOREIGN KEY (building_id) REFERENCES building (building_id),
    CONSTRAINT chk_handover_status  CHECK (status IN ('WAITING', 'PLACE_SET', 'DONE')),
    CONSTRAINT chk_handover_place   CHECK (status <> 'PLACE_SET' OR building_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 알림 (11/22 추가)
-- ---------------------------------------------------------------
CREATE TABLE notification (
    notification_id  BIGINT        NOT NULL AUTO_INCREMENT,
    student_id       VARCHAR(8)    NOT NULL,
    type             VARCHAR(30)   NOT NULL,
    message          VARCHAR(200)  NOT NULL,
    link_kind        VARCHAR(20)   NULL,
    link_id          BIGINT        NULL,
    is_read          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    KEY idx_notification_student (student_id, is_read, created_at),
    CONSTRAINT fk_notification_student FOREIGN KEY (student_id) REFERENCES student (student_id),
    CONSTRAINT chk_notification_type CHECK (type IN ('CLAIM_RECEIVED', 'CLAIM_APPROVED', 'CLAIM_REJECTED',
        'HANDOVER_STARTED', 'HANDOVER_READY', 'HANDOVER_PLACE_SET', 'HANDOVER_DONE')),
    CONSTRAINT chk_notification_link CHECK (link_kind IS NULL OR link_kind IN ('HANDOVER', 'CLAIM', 'FOUND_ITEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 캠퍼스 보행 그래프 (FR-07 C안, 11/23)
-- ---------------------------------------------------------------
-- 노드: 건물 출입구(ENTRANCE) 또는 길 교차점·꺾이는 곳(JUNCTION)
CREATE TABLE path_node (
    node_id       BIGINT        NOT NULL AUTO_INCREMENT,
    node_code     VARCHAR(30)   NOT NULL,                -- 데이터 입력용 사람이 읽는 ID (예: E-INMUN-1, J-07)
    node_type     VARCHAR(10)   NOT NULL,
    building_id   BIGINT        NULL,                    -- ENTRANCE면 필수
    lat           DECIMAL(9,6)  NOT NULL,
    lng           DECIMAL(9,6)  NOT NULL,
    elevation_m   DECIMAL(6,2)  NOT NULL,                -- 해발 고도(m)
    memo          VARCHAR(100)  NULL,
    PRIMARY KEY (node_id),
    UNIQUE KEY uk_path_node_code (node_code),
    KEY idx_path_node_building (building_id),
    CONSTRAINT fk_path_node_building FOREIGN KEY (building_id) REFERENCES building (building_id),
    CONSTRAINT chk_path_node_type CHECK (node_type IN ('ENTRANCE', 'JUNCTION')),
    CONSTRAINT chk_path_node_entrance CHECK ((node_type = 'ENTRANCE' AND building_id IS NOT NULL)
                                          OR (node_type = 'JUNCTION' AND building_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 간선: 두 노드를 잇는 길 1개 = 1행 (양방향). 방향별 시간은 서버가 고도 차로 따로 계산한다.
-- distance_m가 NULL이면 서버가 두 좌표로 계산한다.
-- forward_seconds·backward_seconds를 넣으면 계산값 대신 실측값을 쓴다 (from→to, to→from).
CREATE TABLE path_edge (
    edge_id           BIGINT        NOT NULL AUTO_INCREMENT,
    from_node_id      BIGINT        NOT NULL,
    to_node_id        BIGINT        NOT NULL,
    edge_type         VARCHAR(10)   NOT NULL DEFAULT 'PATH',
    distance_m        DECIMAL(7,2)  NULL,
    forward_seconds   INT           NULL,
    backward_seconds  INT           NULL,
    memo              VARCHAR(100)  NULL,
    PRIMARY KEY (edge_id),
    UNIQUE KEY uk_path_edge_pair (from_node_id, to_node_id),
    KEY idx_path_edge_to (to_node_id),
    CONSTRAINT fk_path_edge_from FOREIGN KEY (from_node_id) REFERENCES path_node (node_id),
    CONSTRAINT fk_path_edge_to   FOREIGN KEY (to_node_id)   REFERENCES path_node (node_id),
    CONSTRAINT chk_path_edge_self CHECK (from_node_id <> to_node_id),
    CONSTRAINT chk_path_edge_type CHECK (edge_type IN ('PATH', 'ROAD', 'STAIRS', 'INDOOR')),
    CONSTRAINT chk_path_edge_seconds CHECK ((forward_seconds IS NULL OR forward_seconds > 0)
                                        AND (backward_seconds IS NULL OR backward_seconds > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 미리 계산한 최단 도보 시간: 노드 → 건물 (건물의 여러 출입구 중 가장 빠른 곳 기준)
-- 서버 시작 시와 관리자 재계산 API 호출 시 Dijkstra(Java)로 전체를 다시 채운다.
CREATE TABLE walk_time (
    from_node_id    BIGINT    NOT NULL,
    to_building_id  BIGINT    NOT NULL,
    seconds         INT       NOT NULL,
    computed_at     DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_node_id, to_building_id),
    KEY idx_walk_time_building (to_building_id),
    CONSTRAINT fk_walk_time_node     FOREIGN KEY (from_node_id)   REFERENCES path_node (node_id) ON DELETE CASCADE,
    CONSTRAINT fk_walk_time_building FOREIGN KEY (to_building_id) REFERENCES building (building_id) ON DELETE CASCADE,
    CONSTRAINT chk_walk_time_seconds CHECK (seconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
