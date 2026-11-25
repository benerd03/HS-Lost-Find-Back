package kr.ac.hanseo.lostfound.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.notification.NotificationRepository;
import kr.ac.hanseo.lostfound.notification.NotificationType;
import kr.ac.hanseo.lostfound.support.MutableClock;

/**
 * 전달 진행 (FR-07). 습득자 F와 분실자 O, 제3자 X를 매번 새로 만들고, 관리자 확정 API로 전달을 연다.
 * 건물 좌표는 data/buildings.csv의 임시 좌표를 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HandoverApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FoundItemRepository foundItemRepository;
    @Autowired ClaimRequestRepository claimRequestRepository;
    @Autowired HandoverRepository handoverRepository;
    @Autowired NotificationRepository notificationRepository;

    MockHttpSession admin;
    MockHttpSession finder;
    MockHttpSession owner;
    MockHttpSession outsider;
    String finderId;
    String ownerId;
    long itemId;
    long claimId;
    long handoverId;
    long library;   // H07 연암도서관
    long dorm;      // H08 인곡관(제1학사관)
    long science;   // H01 이학관
    long etc;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        finderId = newId();
        ownerId = newId();
        finder = signupAndLogin(finderId);
        owner = signupAndLogin(ownerId);
        outsider = signupAndLogin(newId());
        library = buildingRepository.findByCode("H07").orElseThrow().getId();
        dorm = buildingRepository.findByCode("H08").orElseThrow().getId();
        science = buildingRepository.findByCode("H01").orElseThrow().getId();
        etc = buildingRepository.findByCode("ETC").orElseThrow().getId();

        itemId = registerFound("에어팟 프로");
        claimId = claim(itemId, owner);
        String body = mvc.perform(post("/api/admin/claims/{id}/approve", claimId).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        handoverId = objectMapper.readTree(body).at("/data/handover/handoverId").asLong();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // ---------- 조회 ----------

    @Test
    void 확정_직후_두_당사자_모두_패널을_볼_수_있다() throws Exception {
        mvc.perform(get("/api/handovers/{id}", handoverId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverId").value(handoverId))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.myRole").value("OWNER"))
                .andExpect(jsonPath("$.data.serverNow").isString())
                .andExpect(jsonPath("$.data.claimId").value(claimId))
                .andExpect(jsonPath("$.data.claimStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.item.foundItemId").value(itemId))
                .andExpect(jsonPath("$.data.item.itemName").value("에어팟 프로"))
                .andExpect(jsonPath("$.data.item.categoryName").value("전자기기"))
                .andExpect(jsonPath("$.data.item.buildingName").value("인문사회관"))
                .andExpect(jsonPath("$.data.item.status").value("AWAITING_PICKUP"))
                .andExpect(jsonPath("$.data.finderReadyUntil").isEmpty())
                .andExpect(jsonPath("$.data.ownerReady").value(false))
                .andExpect(jsonPath("$.data.building").isEmpty())
                .andExpect(jsonPath("$.data.placeSetAt").isEmpty())
                .andExpect(jsonPath("$.data.completedAt").isEmpty());

        mvc.perform(get("/api/handovers/{id}", handoverId).session(finder))
                .andExpect(jsonPath("$.data.myRole").value("FINDER"));
        mvc.perform(get("/api/me/handovers").session(finder))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].handoverId").value(handoverId));
    }

    @Test
    void 당사자가_아니면_HANDOVER_NOT_PARTY_없으면_404_관리자는_403() throws Exception {
        mvc.perform(get("/api/handovers/{id}", handoverId).session(outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HANDOVER_NOT_PARTY"));
        ready(outsider, library).andExpect(jsonPath("$.code").value("HANDOVER_NOT_PARTY"));
        mvc.perform(get("/api/handovers/{id}", 999999).session(owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/handovers/{id}", handoverId).session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/handovers").session(outsider)).andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ---------- 이동 가능 ----------

    /** 최종 시연 성공 조건: 한 사람이 "이동 가능"을 누르면 상대 화면에 몇 초 안에 표시된다 */
    @Test
    void 한_사람이_이동_가능을_누르면_상대의_다음_조회에_불이_켜지고_알림이_간다() throws Exception {
        ready(owner, library)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.ownerReady").value(true))
                .andExpect(jsonPath("$.data.ownerReadyUntil").isString());

        JsonNode seenByFinder = view(finder);
        assertThat(seenByFinder.get("ownerReady").asBoolean()).isTrue();
        assertThat(seenByFinder.get("finderReady").asBoolean()).isFalse();
        // 남은 시간 = readyUntil - serverNow ≈ 30분
        long left = Duration.between(
                java.time.OffsetDateTime.parse(seenByFinder.get("serverNow").asText()),
                java.time.OffsetDateTime.parse(seenByFinder.get("ownerReadyUntil").asText())).toMinutes();
        assertThat(left).isBetween(29L, 30L);

        var note = notificationRepository.findLatest(finderId, PageRequest.of(0, 1)).get(0);
        assertThat(note.getType()).isEqualTo(NotificationType.HANDOVER_READY);
        assertThat(note.getMessage()).matches("상대가 \\d{2}:\\d{2}에 이동 가능을 눌렀어요");
        assertThat(note.getLinkId()).isEqualTo(handoverId);
    }

    /** 최종 시연 성공 조건: 둘 다 누르면 양쪽 화면에 같은 서산캠퍼스 건물 이름이 뜬다 */
    @Test
    void 둘_다_누르면_두_번째_응답에서_바로_같은_건물로_장소가_정해지고_좌표는_지워진다() throws Exception {
        ready(owner, library).andExpect(jsonPath("$.data.status").value("WAITING"));

        ready(finder, library)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLACE_SET"))
                .andExpect(jsonPath("$.data.building.buildingId").value(library))
                .andExpect(jsonPath("$.data.building.name").value("연암도서관"))
                .andExpect(jsonPath("$.data.building.method").value("STRAIGHT_LINE"))
                .andExpect(jsonPath("$.data.building.myWalkSeconds").isEmpty())
                .andExpect(jsonPath("$.data.placeSetAt").isString());

        assertThat(view(owner).at("/building/name").asText()).isEqualTo("연암도서관");
        Handover saved = handoverRepository.findById(handoverId).orElseThrow();
        assertThat(saved.getFinderLat()).isNull();
        assertThat(saved.getOwnerLat()).isNull();
        assertThat(notificationRepository.findLatest(ownerId, PageRequest.of(0, 1)).get(0).getMessage())
                .isEqualTo("연암도서관에서 만나세요");
        assertThat(notificationRepository.findLatest(finderId, PageRequest.of(0, 1)).get(0).getType())
                .isEqualTo(NotificationType.HANDOVER_PLACE_SET);
    }

    @Test
    void 서로_다른_건물에서_누르면_두_사람에게_공평한_건물이_양쪽에_같게_보인다() throws Exception {
        ready(owner, dorm);
        JsonNode result = objectMapper.readTree(ready(finder, science).andReturn().getResponse().getContentAsString());

        String building = result.at("/data/building/name").asText();
        assertThat(result.at("/data/status").asText()).isEqualTo("PLACE_SET");
        assertThat(building).isNotBlank().isNotEqualTo("기타");
        assertThat(view(owner).at("/building/name").asText()).isEqualTo(building);
    }

    @Test
    void GPS_좌표로도_누를_수_있다() throws Exception {
        mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":36.6905,\"lng\":126.5841}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerReady").value(true));
        Handover saved = handoverRepository.findById(handoverId).orElseThrow();
        assertThat(saved.getOwnerLat()).isEqualByComparingTo("36.690500");
    }

    @Test
    void 위치가_없거나_좌표_없는_건물이면_LOCATION_REQUIRED() throws Exception {
        mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(owner))
                .andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":36.69}"))
                .andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        ready(owner, etc).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        ready(owner, 999999L).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":200,\"lng\":126.5}"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 이동_가능이_유효한데_다시_누르면_HANDOVER_ALREADY_READY_장소_정해진_뒤에는_HANDOVER_PLACE_SET() throws Exception {
        ready(owner, library).andExpect(status().isOk());
        ready(owner, library).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_ALREADY_READY"));

        ready(finder, library).andExpect(jsonPath("$.data.status").value("PLACE_SET"));
        ready(owner, library).andExpect(jsonPath("$.code").value("HANDOVER_PLACE_SET"));
    }

    @Test
    void 삼십분이_지나면_불이_꺼지고_좌표가_지워지며_다시_누를_수_있다() throws Exception {
        ready(owner, library).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(31));

        JsonNode seen = view(finder);
        assertThat(seen.get("ownerReady").asBoolean()).isFalse();
        assertThat(seen.get("ownerReadyUntil").isNull()).isFalse(); // 값은 남을 수 있다
        assertThat(handoverRepository.findById(handoverId).orElseThrow().getOwnerLat()).isNull();

        // 만료된 사람이 있으니 습득자가 눌러도 장소는 정해지지 않는다
        ready(finder, library).andExpect(jsonPath("$.data.status").value("WAITING"));
        ready(owner, library).andExpect(jsonPath("$.data.status").value("PLACE_SET"));
    }

    @Test
    void 장소가_정해진_뒤_30분이_지나도_건물은_유지된다() throws Exception {
        ready(owner, library);
        ready(finder, library);
        clock.advance(Duration.ofMinutes(45));

        JsonNode seen = view(owner);
        assertThat(seen.get("status").asText()).isEqualTo("PLACE_SET");
        assertThat(seen.at("/building/name").asText()).isEqualTo("연암도서관");
    }

    // ---------- 다시 정하기 ----------

    @Test
    void 장소가_정해진_뒤_다시_정하기는_두_사람과_건물을_모두_초기화한다() throws Exception {
        ready(owner, library);
        ready(finder, library);

        reset(finder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.building").isEmpty())
                .andExpect(jsonPath("$.data.placeSetAt").isEmpty())
                .andExpect(jsonPath("$.data.finderReadyUntil").isEmpty())
                .andExpect(jsonPath("$.data.ownerReadyUntil").isEmpty());

        assertThat(view(owner).get("status").asText()).isEqualTo("WAITING");
        ready(owner, dorm).andExpect(status().isOk());
    }

    @Test
    void 대기_중_다시_정하기는_내_이동_가능만_해제한다() throws Exception {
        ready(owner, library);
        ready(finder, library).andExpect(jsonPath("$.data.status").value("PLACE_SET"));
        reset(owner);
        ready(owner, library);
        ready(finder, dorm);
        reset(finder); // PLACE_SET → 전체 초기화
        ready(owner, library);

        reset(finder).andExpect(jsonPath("$.data.ownerReady").value(true))
                .andExpect(jsonPath("$.data.finderReady").value(false));
        reset(owner).andExpect(jsonPath("$.data.ownerReady").value(false));
    }

    // ---------- 받았어요 ----------

    /** 최종 시연 성공 조건: 분실자가 "받았어요"를 누르면 마이페이지와 관리자 화면에 수령완료가 표시된다 */
    @Test
    void 받았어요를_누르면_수령완료가_되고_마이페이지와_관리자_화면에_반영된다() throws Exception {
        ready(owner, library);
        ready(finder, library);

        receive(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.claimStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.item.status").value("CLAIMED"))
                .andExpect(jsonPath("$.data.completedAt").isString());

        assertThat(claimRequestRepository.findById(claimId).orElseThrow().getStatus()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(foundItemRepository.findById(itemId).orElseThrow().getStatus()).isEqualTo(FoundItemStatus.CLAIMED);
        assertThat(notificationRepository.findLatest(finderId, PageRequest.of(0, 1)).get(0).getMessage())
                .isEqualTo("에어팟 프로 전달이 완료됐어요");

        mvc.perform(get("/api/me/claims").session(owner))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
        mvc.perform(get("/api/me/found-items").session(finder))
                .andExpect(jsonPath("$.data[0].status").value("CLAIMED"));
        mvc.perform(get("/api/me/handovers").session(owner)).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/me/summary").session(owner)).andExpect(jsonPath("$.data.activeHandoverId").isEmpty());
        mvc.perform(get("/api/admin/handovers/{id}", handoverId).session(admin))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.item.status").value("CLAIMED"))
                .andExpect(jsonPath("$.data.claim.status").value("COMPLETED"));
        // 상대는 다음 조회에서 DONE을 본다
        assertThat(view(finder).get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void 추천_없이_대기_중에도_받았어요를_누를_수_있다() throws Exception {
        receive(owner).andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.building").isEmpty());
    }

    @Test
    void 습득자는_받았어요를_누를_수_없고_완료_뒤에는_모두_HANDOVER_DONE() throws Exception {
        receive(finder).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        receive(owner).andExpect(status().isOk());

        receive(owner).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDOVER_DONE"));
        ready(owner, library).andExpect(jsonPath("$.code").value("HANDOVER_DONE"));
        reset(finder).andExpect(jsonPath("$.code").value("HANDOVER_DONE"));
    }

    // ---------- 관리자 A4 ----------

    @Test
    void 관리자_전달_현황은_상태와_날짜로_거르고_집계를_보낸다() throws Exception {
        ready(owner, library);
        ready(finder, library);
        String today = java.time.LocalDate.now(clock).toString();

        String body = mvc.perform(get("/api/admin/handovers").param("status", "PLACE_SET")
                        .param("from", today).param("to", today).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.at("/summary/placeSet").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.at("/summary/ongoing").asLong()).isGreaterThanOrEqualTo(data.at("/summary/placeSet").asLong());
        data.get("items").forEach(i -> assertThat(i.get("status").asText()).isEqualTo("PLACE_SET"));
        JsonNode mine = null;
        for (JsonNode i : data.get("items")) {
            if (i.get("handoverId").asLong() == handoverId) {
                mine = i;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.at("/item/itemName").asText()).isEqualTo("에어팟 프로");
        assertThat(mine.at("/claim/claimId").asLong()).isEqualTo(claimId);
        assertThat(mine.at("/claim/approvedAt").asText()).endsWith("+09:00");
        assertThat(mine.get("finderName").asText()).isEqualTo("학생" + finderId);
        assertThat(mine.get("ownerName").asText()).isEqualTo("학생" + ownerId);
        assertThat(mine.at("/building/name").asText()).isEqualTo("연암도서관");
        assertThat(mine.has("finderLat")).isFalse();

        mvc.perform(get("/api/admin/handovers").param("status", "ONGOING").param("from", "2000-01-01")
                        .param("to", "2000-01-02").session(admin))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.summary.ongoing").value(0));
        mvc.perform(get("/api/admin/handovers").param("status", "WRONG").session(admin))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/admin/handovers/{id}", 999999).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/handovers").session(owner)).andExpect(status().isForbidden());
    }

    // ---------- 도우미 ----------

    private ResultActions ready(MockHttpSession session, long buildingId) throws Exception {
        return mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"buildingId\":" + buildingId + "}"));
    }

    private ResultActions reset(MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/handovers/{id}/reset", handoverId).session(session));
    }

    private ResultActions receive(MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/handovers/{id}/receive", handoverId).session(session));
    }

    private JsonNode view(MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/handovers/{id}", handoverId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private long registerFound(String name) throws Exception {
        long category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        long building = buildingRepository.findByCode("H11").orElseThrow().getId();
        String body = mvc.perform(multipart("/api/found-items")
                        .file(new MockMultipartFile("image", "p.png", "image/png", PNG))
                        .param("itemName", name).param("categoryId", String.valueOf(category))
                        .param("buildingId", String.valueOf(building)).param("foundAt", "2026-11-20T10:00:00")
                        .session(finder))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/foundItemId").asLong();
    }

    private long claim(long item, MockHttpSession session) throws Exception {
        String body = mvc.perform(post("/api/found-items/{id}/claims", item).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownershipEvidence\":\"이니셜 HE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/claimId").asLong();
    }

    private static String newId() {
        return String.valueOf(60000000 + SEQ.incrementAndGet());
    }

    private MockHttpSession signupAndLogin(String id) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"studentId":"%s","password":"pass1234","name":"학생%s","department":"학과","phone":"010-2222-3333"}"""
                .formatted(id, id))).andExpect(status().isCreated());
        return login("STUDENT", id, "pass1234");
    }

    private MockHttpSession login(String role, String loginId, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"%s\",\"loginId\":\"%s\",\"password\":\"%s\"}"
                                .formatted(role, loginId, password)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
