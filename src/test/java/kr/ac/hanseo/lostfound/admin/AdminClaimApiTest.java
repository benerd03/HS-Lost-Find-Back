package kr.ac.hanseo.lostfound.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.auth.Role;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.lostitem.LostItemRepository;
import kr.ac.hanseo.lostfound.lostitem.LostItemStatus;
import kr.ac.hanseo.lostfound.notification.Notification;
import kr.ac.hanseo.lostfound.notification.NotificationRepository;
import kr.ac.hanseo.lostfound.notification.NotificationType;

/** 관리자 A2·A3. 테스트마다 새 학생(습득자 F, 신청자 A·B)을 만든다 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminClaimApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FoundItemRepository foundItemRepository;
    @Autowired LostItemRepository lostItemRepository;
    @Autowired ClaimRequestRepository claimRequestRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AdminClaimService adminClaimService;
    @Autowired TransactionTemplate tx;

    MockHttpSession admin;
    MockHttpSession finder;
    MockHttpSession studentA;
    MockHttpSession studentB;
    String finderId;
    String aId;
    String bId;
    long category;
    long building;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        finderId = newId();
        aId = newId();
        bId = newId();
        finder = signupAndLogin(finderId);
        studentA = signupAndLogin(aId);
        studentB = signupAndLogin(bId);
        category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        building = buildingRepository.findByCode("H11").orElseThrow().getId();
    }

    // ---------- 주인 확정 ----------

    /** 최종 시연 성공 조건: 관리자가 A를 주인으로 확정하면 B는 자동 거절되고, 습득자와 A의 화면에 전달 진행 패널이 뜬다 */
    @Test
    void A를_확정하면_B는_자동거절_전달생성_분실신고종료_알림3종() throws Exception {
        long item = registerFound("에어팟 프로");
        long lostA = createLost(studentA, "에어팟");
        long claimA = claim(item, studentA, "이니셜 HE", lostA);
        long claimB = claim(item, studentB, "스크래치", null);

        String body = mvc.perform(post("/api/admin/claims/{id}/approve", claimA).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimId").value(claimA))
                .andExpect(jsonPath("$.data.claimStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.itemStatus").value("AWAITING_PICKUP"))
                .andExpect(jsonPath("$.data.rejectedClaimIds", contains((int) claimB)))
                .andExpect(jsonPath("$.data.handover.handoverId").isNumber())
                .andExpect(jsonPath("$.data.handover.status").value("WAITING"))
                .andExpect(jsonPath("$.data.closedLostItemId").value(lostA))
                .andReturn().getResponse().getContentAsString();
        long handoverId = objectMapper.readTree(body).at("/data/handover/handoverId").asLong();

        assertThat(claimStatus(claimB)).isEqualTo(ClaimStatus.REJECTED);
        assertThat(foundItemRepository.findById(item).orElseThrow().getStatus())
                .isEqualTo(FoundItemStatus.AWAITING_PICKUP);
        assertThat(lostItemRepository.findById(lostA).orElseThrow().getStatus()).isEqualTo(LostItemStatus.CLOSED);

        // 습득자와 A의 홈에서 진행 중 전달이 보인다 → 프론트가 전달 패널을 띄운다
        mvc.perform(get("/api/me/summary").session(studentA))
                .andExpect(jsonPath("$.data.activeHandoverId").value(handoverId));
        mvc.perform(get("/api/me/summary").session(finder))
                .andExpect(jsonPath("$.data.activeHandoverId").value(handoverId));
        mvc.perform(get("/api/me/summary").session(studentB))
                .andExpect(jsonPath("$.data.activeHandoverId").isEmpty());
        mvc.perform(get("/api/me/claims").session(studentA))
                .andExpect(jsonPath("$.data[0].handoverId").value(handoverId));
        mvc.perform(get("/api/found-items/{id}", item).session(studentB))
                .andExpect(jsonPath("$.data.claimEligibility").value("rejected"));

        assertThat(latestNotification(aId).getType()).isEqualTo(NotificationType.CLAIM_APPROVED);
        assertThat(latestNotification(aId).getMessage()).isEqualTo("에어팟 프로의 주인으로 확정됐어요");
        assertThat(latestNotification(bId).getType()).isEqualTo(NotificationType.CLAIM_REJECTED);
        assertThat(latestNotification(finderId).getType()).isEqualTo(NotificationType.HANDOVER_STARTED);
        assertThat(latestNotification(finderId).getLinkId()).isEqualTo(handoverId);
    }

    @Test
    void 이미_주인이_정해졌으면_OWNER_ALREADY_CONFIRMED() throws Exception {
        long item = registerFound("지갑");
        long claimA = claim(item, studentA, "a", null);
        long claimB = claim(item, studentB, "b", null);
        mvc.perform(post("/api/admin/claims/{id}/approve", claimA).session(admin)).andExpect(status().isOk());

        mvc.perform(post("/api/admin/claims/{id}/approve", claimA).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_ALREADY_CONFIRMED"));
        mvc.perform(post("/api/admin/claims/{id}/approve", claimB).session(admin))
                .andExpect(jsonPath("$.code").value("OWNER_ALREADY_CONFIRMED"));
        mvc.perform(post("/api/admin/claims/{id}/reject", claimB).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
    }

    @Test
    void 취소된_신청을_확정하려_하면_CLAIM_NOT_PENDING() throws Exception {
        long item = registerFound("지갑");
        long claimA = claim(item, studentA, "a", null);
        claim(item, studentB, "b", null);
        mvc.perform(patch("/api/claims/{id}/cancel", claimA).session(studentA)).andExpect(status().isOk());

        mvc.perform(post("/api/admin/claims/{id}/approve", claimA).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
    }

    @Test
    void 확정된_뒤에는_습득자가_물건을_지울_수_없고_신청자가_취소할_수도_없다() throws Exception {
        long item = registerFound("지갑");
        long claimA = claim(item, studentA, "a", null);
        mvc.perform(post("/api/admin/claims/{id}/approve", claimA).session(admin)).andExpect(status().isOk());

        mvc.perform(delete("/api/found-items/{id}", item).session(finder))
                .andExpect(jsonPath("$.code").value("ITEM_LOCKED"));
        mvc.perform(patch("/api/claims/{id}/cancel", claimA).session(studentA))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
    }

    /** 동시성: 두 관리자가 같은 습득물의 서로 다른 신청을 동시에 확정하면 한쪽만 성공 */
    @Test
    void 두_관리자가_동시에_확정하면_하나만_성공한다() throws Exception {
        long item = registerFound("지갑");
        long claimA = claim(item, studentA, "a", null);
        long claimB = claim(item, studentB, "b", null);
        SessionUser adminUser = new SessionUser(SeedDataRunner.ADMIN_ID, "관리자", Role.ADMIN, "학생지원팀");

        List<Object> results = runTogether(
                () -> adminClaimService.approve(claimA, adminUser),
                () -> adminClaimService.approve(claimB, adminUser));

        long successes = results.stream().filter(r -> r instanceof AdminResponses.ApproveResult).count();
        assertThat(successes).isEqualTo(1);
        assertThat(results).anySatisfy(r -> assertThat(r).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isIn(ErrorCode.OWNER_ALREADY_CONFIRMED, ErrorCode.CLAIM_NOT_PENDING)));
        long approved = tx.execute(s -> List.of(claimStatus(claimA), claimStatus(claimB)).stream()
                .filter(st -> st == ClaimStatus.APPROVED).count());
        assertThat(approved).isEqualTo(1);
    }

    // ---------- 거절 ----------

    @Test
    void 거절하면_사유가_남고_마지막_대기_신청이면_보관중으로_돌아간다() throws Exception {
        long item = registerFound("지갑");
        long claimA = claim(item, studentA, "a", null);
        long claimB = claim(item, studentB, "b", null);

        mvc.perform(post("/api/admin/claims/{id}/reject", claimA).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"증빙이 물품 특징과 다름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimId").value(claimA))
                .andExpect(jsonPath("$.data.claimStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.itemStatus").value("CLAIM_REQUESTED"));

        // 사유 없이, 본문 없이 보내도 된다
        mvc.perform(post("/api/admin/claims/{id}/reject", claimB).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemStatus").value("STORED"));

        String reason = tx.execute(s -> claimRequestRepository.findById(claimA).orElseThrow().getRejectReason());
        assertThat(reason).isEqualTo("증빙이 물품 특징과 다름");
        assertThat(latestNotification(aId).getType()).isEqualTo(NotificationType.CLAIM_REJECTED);
        assertThat(latestNotification(aId).getMessage()).isEqualTo("지갑 수령 신청이 거절됐어요");
        // 거절된 학생은 같은 물건에 다시 신청할 수 없다
        mvc.perform(post("/api/found-items/{id}/claims", item).session(studentA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownershipEvidence\":\"다시\"}"))
                .andExpect(jsonPath("$.code").value("CLAIM_DUPLICATE"));
    }

    @Test
    void 없는_신청은_404_학생은_403() throws Exception {
        mvc.perform(post("/api/admin/claims/{id}/approve", 999999).session(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/claims/{id}/reject", 999999).session(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/claims/{id}/approve", 1).session(studentA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/claims").session(studentA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/found-items").session(studentA)).andExpect(status().isForbidden());
    }

    // ---------- A3 신청 묶음 ----------

    @Test
    void 신청_묶음은_대기_신청이_오래된_습득물부터_신청자_연락처와_연결된_분실신고를_보여준다() throws Exception {
        long older = registerFound("에어팟");
        long newer = registerFound("지갑");
        long lostA = createLost(studentA, "에어팟");
        long claimOlder = claim(older, studentA, "이니셜 HE", lostA);
        long claimNewer = claim(newer, studentB, "카드", null);
        claim(older, studentB, "스크래치", null);

        String body = mvc.perform(get("/api/admin/claims").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var bundles = objectMapper.readTree(body).get("data");
        List<Long> order = new ArrayList<>();
        bundles.forEach(b -> order.add(b.at("/item/foundItemId").asLong()));
        assertThat(order).containsSubsequence(older, newer);

        var olderBundle = findBundle(bundles, older);
        assertThat(olderBundle.at("/item/itemName").asText()).isEqualTo("에어팟");
        assertThat(olderBundle.at("/item/status").asText()).isEqualTo("CLAIM_REQUESTED");
        assertThat(olderBundle.get("handoverId").isNull()).isTrue();
        assertThat(olderBundle.get("claims")).hasSize(2);
        var first = olderBundle.at("/claims/0");
        assertThat(first.get("claimId").asLong()).isEqualTo(claimOlder);
        assertThat(first.get("status").asText()).isEqualTo("PENDING");
        assertThat(first.at("/student/studentId").asText()).isEqualTo(aId);
        assertThat(first.at("/student/phone").asText()).isEqualTo("010-2222-3333");
        assertThat(first.get("ownershipEvidence").asText()).isEqualTo("이니셜 HE");
        assertThat(first.at("/lostItem/lostItemId").asLong()).isEqualTo(lostA);
        assertThat(first.at("/lostItem/buildingName").asText()).isEqualTo("인문사회관");
        assertThat(first.get("processedAt").isNull()).isTrue();
        assertThat(olderBundle.at("/claims/1/lostItem").isNull()).isTrue();

        // 확정하면 PENDING 묶음에서 빠지고 APPROVED 묶음에 handoverId와 함께 나온다
        mvc.perform(post("/api/admin/claims/{id}/approve", claimNewer).session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/claims").param("foundItemId", String.valueOf(newer)).session(admin))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/admin/claims").param("claimStatus", "APPROVED")
                        .param("foundItemId", String.valueOf(newer)).session(admin))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].handoverId").isNumber())
                .andExpect(jsonPath("$.data[0].claims[0].processedAt").isString());

        mvc.perform(get("/api/admin/found-items/{id}/claims", older).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.foundItemId").value(older))
                .andExpect(jsonPath("$.data.claims", hasSize(2)));
    }

    @Test
    void 신청이_없는_습득물의_묶음은_빈_claims_없는_습득물은_404() throws Exception {
        long item = registerFound("우산");
        mvc.perform(get("/api/admin/found-items/{id}/claims", item).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claims", hasSize(0)))
                .andExpect(jsonPath("$.data.handoverId").isEmpty());
        mvc.perform(get("/api/admin/found-items/{id}/claims", 999999).session(admin))
                .andExpect(status().isNotFound());
    }

    // ---------- A2 습득물 ----------

    @Test
    void 관리자_습득물_목록은_상태로_거르고_신청_수와_등록시각을_붙인다() throws Exception {
        long requested = registerFound("에어팟");
        long stored = registerFound("우산");
        claim(requested, studentA, "a", null);
        long cancelled = claim(requested, studentB, "b", null);
        mvc.perform(patch("/api/claims/{id}/cancel", cancelled).session(studentB));

        String body = mvc.perform(get("/api/admin/found-items").param("status", "CLAIM_REQUESTED")
                        .param("size", "100").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(body).at("/data/items");
        var row = findBy(items, "foundItemId", requested);
        assertThat(row.get("claimCount").asLong()).isEqualTo(2);
        assertThat(row.get("createdAt").asText()).endsWith("+09:00");
        assertThat(row.get("status").asText()).isEqualTo("CLAIM_REQUESTED");
        items.forEach(i -> assertThat(i.get("status").asText()).isEqualTo("CLAIM_REQUESTED"));
        assertThat(stored).isPositive();

        mvc.perform(get("/api/admin/found-items/{id}", requested).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finder.studentId").value(finderId))
                .andExpect(jsonPath("$.data.finder.name").value("학생" + finderId))
                .andExpect(jsonPath("$.data.description").value("흰색"))
                .andExpect(jsonPath("$.data.claimCount").value(2));
    }

    // ---------- 도우미 ----------

    private Notification latestNotification(String studentId) {
        return notificationRepository.findLatest(studentId, PageRequest.of(0, 1)).get(0);
    }

    private ClaimStatus claimStatus(long claimId) {
        return claimRequestRepository.findById(claimId).orElseThrow().getStatus();
    }

    private com.fasterxml.jackson.databind.JsonNode findBundle(com.fasterxml.jackson.databind.JsonNode bundles,
            long foundItemId) {
        for (var b : bundles) {
            if (b.at("/item/foundItemId").asLong() == foundItemId) {
                return b;
            }
        }
        throw new AssertionError("묶음 없음: " + foundItemId);
    }

    private com.fasterxml.jackson.databind.JsonNode findBy(com.fasterxml.jackson.databind.JsonNode array,
            String field, long value) {
        for (var n : array) {
            if (n.get(field).asLong() == value) {
                return n;
            }
        }
        throw new AssertionError("항목 없음: " + value);
    }

    @SafeVarargs
    private List<Object> runTogether(Callable<Object>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return task.call();
                } catch (BusinessException e) {
                    return e;
                }
            }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private long registerFound(String name) throws Exception {
        String body = mvc.perform(multipart("/api/found-items")
                        .file(new MockMultipartFile("image", "p.png", "image/png", PNG))
                        .param("itemName", name).param("categoryId", String.valueOf(category))
                        .param("buildingId", String.valueOf(building)).param("foundAt", "2026-11-20T10:00:00")
                        .param("description", "흰색")
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

    private long claim(long itemId, MockHttpSession session, String evidence, Long lostItemId) throws Exception {
        var node = objectMapper.createObjectNode().put("ownershipEvidence", evidence);
        if (lostItemId != null) {
            node.put("lostItemId", lostItemId);
        }
        ResultActions result = mvc.perform(post("/api/found-items/{id}/claims", itemId).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(node.toString()));
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/claimId").asLong();
    }

    private static String newId() {
        return String.valueOf(50000000 + SEQ.incrementAndGet());
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
