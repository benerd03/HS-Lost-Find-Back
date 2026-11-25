package kr.ac.hanseo.lostfound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.handover.Handover;
import kr.ac.hanseo.lostfound.handover.HandoverRepository;
import kr.ac.hanseo.lostfound.notification.NotificationType;
import kr.ac.hanseo.lostfound.notification.NotificationRepository;

/** 수령 신청·취소와 마이페이지. 테스트마다 새 학생(습득자 F, 신청자 A·B)을 만들어 서로 섞이지 않게 한다 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClaimAndMyPageApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FoundItemRepository foundItemRepository;
    @Autowired ClaimRequestRepository claimRequestRepository;
    @Autowired HandoverRepository handoverRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionTemplate tx;

    MockHttpSession finder;
    MockHttpSession studentA;
    MockHttpSession studentB;
    String finderId;
    long category;
    long building;

    @BeforeEach
    void setUp() throws Exception {
        finderId = newStudentId();
        finder = signupAndLogin(finderId);
        studentA = signupAndLogin(newStudentId());
        studentB = signupAndLogin(newStudentId());
        category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        building = buildingRepository.findByCode("H11").orElseThrow().getId();
    }

    // ---------- 신청 ----------

    /** 최종 시연 성공 조건: 학생 A와 B가 같은 습득물에 각각 수령 신청할 수 있다 */
    @Test
    void 두_학생이_같은_습득물에_각각_신청하면_수령신청_상태가_되고_습득자에게_알림이_간다() throws Exception {
        long item = registerFound("에어팟 프로");

        claim(item, studentA, "케이스 안쪽 이니셜 HE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.claimId").isNumber())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        claim(item, studentB, "뒷면에 스크래치").andExpect(status().isCreated());

        assertThat(itemStatus(item)).isEqualTo(FoundItemStatus.CLAIM_REQUESTED);
        var notes = notificationRepository.findLatest(finderId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notes).hasSize(2).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.CLAIM_RECEIVED);
            assertThat(n.getMessage()).isEqualTo("에어팟 프로에 수령 신청이 들어왔어요");
            assertThat(n.getLinkId()).isEqualTo(item);
        });

        mvc.perform(get("/api/found-items/{id}", item).session(studentA))
                .andExpect(jsonPath("$.data.myClaimStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.claimEligibility").value("already-requested"));
    }

    @Test
    void 신청이_들어온_습득물은_습득자가_수정할_수_없다() throws Exception {
        long item = registerFound("지갑");
        claim(item, studentA, "카드에 이름").andExpect(status().isCreated());

        mvc.perform(multipart(HttpMethod.PUT, "/api/found-items/{id}", item).session(finder)
                        .param("itemName", "지갑").param("categoryId", String.valueOf(category))
                        .param("buildingId", String.valueOf(building)).param("foundAt", "2026-11-20T10:00:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_LOCKED"));
    }

    @Test
    void 내가_등록한_습득물에는_신청할_수_없다() throws Exception {
        long item = registerFound("지갑");
        claim(item, finder, "내꺼").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_OWN_ITEM"));
    }

    @Test
    void 같은_물건에_다시_신청하면_CLAIM_DUPLICATE_취소한_뒤에도_마찬가지() throws Exception {
        long item = registerFound("지갑");
        long claimId = claimId(claim(item, studentA, "카드에 이름"));

        claim(item, studentA, "또").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_DUPLICATE"));
        mvc.perform(patch("/api/claims/{id}/cancel", claimId).session(studentA)).andExpect(status().isOk());
        claim(item, studentA, "다시").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_DUPLICATE"));
    }

    @Test
    void 수령대기나_수령완료_물건은_CLAIM_CLOSED() throws Exception {
        long awaiting = registerFound("지갑");
        long claimed = registerFound("우산");
        setStatus(awaiting, FoundItemStatus.AWAITING_PICKUP);
        setStatus(claimed, FoundItemStatus.CLAIMED);

        claim(awaiting, studentA, "x").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_CLOSED"));
        claim(claimed, studentA, "x").andExpect(jsonPath("$.code").value("CLAIM_CLOSED"));
    }

    @Test
    void 연결할_분실_신고가_남의_것이거나_종료됐으면_LOST_ITEM_INVALID() throws Exception {
        long item = registerFound("지갑");
        long othersLost = createLost(studentB, "지갑");
        long myClosedLost = createLost(studentA, "지갑");
        mvc.perform(patch("/api/lost-items/{id}/close", myClosedLost).session(studentA)).andExpect(status().isOk());

        claim(item, studentA, "x", othersLost).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOST_ITEM_INVALID"));
        claim(item, studentA, "x", myClosedLost).andExpect(jsonPath("$.code").value("LOST_ITEM_INVALID"));
        claim(item, studentA, "x", 999999L).andExpect(jsonPath("$.code").value("LOST_ITEM_INVALID"));
    }

    @Test
    void 내_OPEN_분실_신고를_연결하면_신청에_남는다() throws Exception {
        long item = registerFound("지갑");
        long myLost = createLost(studentA, "지갑");

        long claimId = claimId(claim(item, studentA, "카드에 이름", myLost));

        Long linked = tx.execute(s -> claimRequestRepository.findById(claimId).orElseThrow().getLostItem().getId());
        assertThat(linked).isEqualTo(myLost);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/lost-items/{id}", myLost).session(studentA))
                .andExpect(jsonPath("$.code").value("LOST_ITEM_HAS_CLAIM"));
    }

    @Test
    void 증빙이_비어_있으면_VALIDATION_FAILED_없는_물건은_404_관리자는_403() throws Exception {
        long item = registerFound("지갑");
        claim(item, studentA, " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        claim(999999L, studentA, "x").andExpect(status().isNotFound());
        MockHttpSession admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        claim(item, admin, "x").andExpect(status().isForbidden());
    }

    // ---------- 취소 ----------

    @Test
    void 마지막_대기_신청을_취소하면_습득물이_보관중으로_돌아간다() throws Exception {
        long item = registerFound("지갑");
        long a = claimId(claim(item, studentA, "a"));
        long b = claimId(claim(item, studentB, "b"));

        mvc.perform(patch("/api/claims/{id}/cancel", a).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimId").value(a))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(itemStatus(item)).isEqualTo(FoundItemStatus.CLAIM_REQUESTED);

        mvc.perform(patch("/api/claims/{id}/cancel", b).session(studentB)).andExpect(status().isOk());
        assertThat(itemStatus(item)).isEqualTo(FoundItemStatus.STORED);
    }

    @Test
    void 남의_신청은_취소할_수_없고_대기가_아니면_CLAIM_NOT_PENDING() throws Exception {
        long item = registerFound("지갑");
        long a = claimId(claim(item, studentA, "a"));

        mvc.perform(patch("/api/claims/{id}/cancel", a).session(studentB))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/claims/{id}/cancel", a).session(studentA)).andExpect(status().isOk());
        mvc.perform(patch("/api/claims/{id}/cancel", a).session(studentA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
        mvc.perform(patch("/api/claims/{id}/cancel", 999999).session(studentA))
                .andExpect(status().isNotFound());
    }

    // ---------- 마이페이지 ----------

    @Test
    void 내_수령_신청은_최신순이고_주인_확정_후에만_handoverId가_있다() throws Exception {
        long first = registerFound("지갑");
        long second = registerFound("에어팟");
        long claimFirst = claimId(claim(first, studentA, "a"));
        long claimSecond = claimId(claim(second, studentA, "b"));
        long handoverId = approve(claimFirst);

        mvc.perform(get("/api/me/claims").session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].claimId").value(claimSecond))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].handoverId").isEmpty())
                .andExpect(jsonPath("$.data[0].requestedAt").isString())
                .andExpect(jsonPath("$.data[0].foundItem.foundItemId").value(second))
                .andExpect(jsonPath("$.data[0].foundItem.itemName").value("에어팟"))
                .andExpect(jsonPath("$.data[0].foundItem.imageUrl").isString())
                .andExpect(jsonPath("$.data[1].claimId").value(claimFirst))
                .andExpect(jsonPath("$.data[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.data[1].handoverId").value(handoverId));
    }

    @Test
    void 내_습득_신고에는_대기_신청_수와_확정_후_handoverId가_붙는다() throws Exception {
        long waiting = registerFound("지갑");
        long confirmed = registerFound("에어팟");
        long empty = registerFound("우산");
        claim(waiting, studentA, "a");
        claim(waiting, studentB, "b");
        long handoverId = approve(claimId(claim(confirmed, studentA, "c")));

        mvc.perform(get("/api/me/found-items").session(finder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].foundItemId").value(empty))
                .andExpect(jsonPath("$.data[0].claimCount").value(0))
                .andExpect(jsonPath("$.data[0].handoverId").isEmpty())
                .andExpect(jsonPath("$.data[1].foundItemId").value(confirmed))
                .andExpect(jsonPath("$.data[1].status").value("AWAITING_PICKUP"))
                .andExpect(jsonPath("$.data[1].handoverId").value(handoverId))
                .andExpect(jsonPath("$.data[2].foundItemId").value(waiting))
                .andExpect(jsonPath("$.data[2].claimCount").value(2))
                .andExpect(jsonPath("$.data[2].categoryName").value("전자기기"))
                .andExpect(jsonPath("$.data[2].buildingName").value("인문사회관"));
    }

    @Test
    void 내_진행_현황은_진행_중인_것만_세고_진행_중_전달을_알려준다() throws Exception {
        mvc.perform(get("/api/me/summary").session(studentA))
                .andExpect(jsonPath("$.data.lostItemCount").value(0))
                .andExpect(jsonPath("$.data.foundItemCount").value(0))
                .andExpect(jsonPath("$.data.claimCount").value(0))
                .andExpect(jsonPath("$.data.activeHandoverId").isEmpty());

        long item = registerFound("지갑");
        long other = registerFound("우산");
        createLost(studentA, "지갑");
        long closed = createLost(studentA, "우산");
        mvc.perform(patch("/api/lost-items/{id}/close", closed).session(studentA));
        long handoverId = approve(claimId(claim(item, studentA, "a")));
        long cancelled = claimId(claim(other, studentA, "b"));
        mvc.perform(patch("/api/claims/{id}/cancel", cancelled).session(studentA));

        mvc.perform(get("/api/me/summary").session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lostItemCount").value(1))
                .andExpect(jsonPath("$.data.claimCount").value(1))
                .andExpect(jsonPath("$.data.activeHandoverId").value(handoverId));

        // 습득자 쪽에서도 같은 전달이 보인다
        mvc.perform(get("/api/me/summary").session(finder))
                .andExpect(jsonPath("$.data.foundItemCount").value(2))
                .andExpect(jsonPath("$.data.activeHandoverId").value(handoverId));
    }

    @Test
    void 관리자는_마이페이지_API를_쓸_수_없다() throws Exception {
        MockHttpSession admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        mvc.perform(get("/api/me/summary").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/claims").session(admin)).andExpect(status().isForbidden());
    }

    // ---------- 도우미 ----------

    private long registerFound(String name) throws Exception {
        String body = mvc.perform(multipart("/api/found-items")
                        .file(new MockMultipartFile("image", "p.png", "image/png", PNG))
                        .param("itemName", name).param("categoryId", String.valueOf(category))
                        .param("buildingId", String.valueOf(building)).param("foundAt", "2026-11-20T10:00:00")
                        .session(finder))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/foundItemId").asLong();
    }

    private long createLost(MockHttpSession session, String name) throws Exception {
        String body = mvc.perform(post("/api/lost-items").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"%s","categoryId":%d,"lostAt":"2026-11-19T10:00:00","buildingId":%d}"""
                                .formatted(name, category, building)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/lostItemId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions claim(long itemId, MockHttpSession session,
            String evidence) throws Exception {
        return claim(itemId, session, evidence, null);
    }

    private org.springframework.test.web.servlet.ResultActions claim(long itemId, MockHttpSession session,
            String evidence, Long lostItemId) throws Exception {
        JsonNode body = objectMapper.createObjectNode().put("ownershipEvidence", evidence)
                .set("lostItemId", lostItemId == null ? null : objectMapper.valueToTree(lostItemId));
        return mvc.perform(post("/api/found-items/{id}/claims", itemId).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private long claimId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/claimId").asLong();
    }

    /** 관리자 주인 확정 API가 생기기 전까지 쓰는 최소 흉내: 신청 APPROVED, 습득물 AWAITING_PICKUP, 전달 생성 */
    private long approve(long claimId) {
        return tx.execute(s -> {
            ClaimRequest claim = claimRequestRepository.findById(claimId).orElseThrow();
            claim.approve(null, LocalDateTime.now());
            FoundItem item = claim.getFoundItem();
            item.changeStatus(FoundItemStatus.AWAITING_PICKUP);
            return handoverRepository.save(new Handover(claim)).getId();
        });
    }

    private void setStatus(long id, FoundItemStatus status) {
        tx.executeWithoutResult(s -> foundItemRepository.findById(id).orElseThrow().changeStatus(status));
    }

    private FoundItemStatus itemStatus(long id) {
        return foundItemRepository.findById(id).orElseThrow().getStatus();
    }

    private static String newStudentId() {
        return String.valueOf(40000000 + SEQ.incrementAndGet());
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
