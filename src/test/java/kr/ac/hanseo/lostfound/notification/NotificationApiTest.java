package kr.ac.hanseo.lostfound.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;
import kr.ac.hanseo.lostfound.user.StudentRepository;

/** 종 아이콘 알림. 실제 흐름(신청 → 확정)으로 알림을 만들고 조회·읽음을 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired NotificationService notificationService;
    @Autowired TransactionTemplate tx;

    MockHttpSession finder;
    MockHttpSession owner;
    String finderId;
    String ownerId;

    @BeforeEach
    void setUp() throws Exception {
        finderId = newId();
        ownerId = newId();
        finder = signupAndLogin(finderId);
        owner = signupAndLogin(ownerId);
    }

    @Test
    void 신청과_확정으로_생긴_알림이_최신순으로_이동할_곳과_함께_온다() throws Exception {
        long item = registerFound("에어팟 프로");
        long claimId = claim(item, owner);
        long handoverId = approve(claimId);

        mvc.perform(get("/api/me/notifications").session(finder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].type").value("HANDOVER_STARTED"))
                .andExpect(jsonPath("$.data[0].message").value("에어팟 프로 주인이 확정됐어요. 전달을 진행해 주세요"))
                .andExpect(jsonPath("$.data[0].isRead").value(false))
                .andExpect(jsonPath("$.data[0].createdAt").isString())
                .andExpect(jsonPath("$.data[0].link.kind").value("HANDOVER"))
                .andExpect(jsonPath("$.data[0].link.id").value(handoverId))
                .andExpect(jsonPath("$.data[1].type").value("CLAIM_RECEIVED"))
                .andExpect(jsonPath("$.data[1].link.kind").value("FOUND_ITEM"))
                .andExpect(jsonPath("$.data[1].link.id").value(item))
                .andExpect(jsonPath("$.data[0].notificationId").isNumber());

        mvc.perform(get("/api/me/notifications").session(owner))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("CLAIM_APPROVED"))
                .andExpect(jsonPath("$.data[0].message").value("에어팟 프로의 주인으로 확정됐어요"));
    }

    @Test
    void 읽지_않은_수는_읽음_처리하면_줄고_모두_읽음이면_0() throws Exception {
        long item = registerFound("지갑");
        approve(claim(item, owner));
        mvc.perform(get("/api/me/notifications/unread-count").session(finder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        long first = latestId(finder);
        mvc.perform(patch("/api/me/notifications/{id}/read", first).session(finder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(first))
                .andExpect(jsonPath("$.data.isRead").value(true));
        // 이미 읽은 알림을 다시 눌러도 200
        mvc.perform(patch("/api/me/notifications/{id}/read", first).session(finder)).andExpect(status().isOk());
        mvc.perform(get("/api/me/notifications/unread-count").session(finder))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mvc.perform(patch("/api/me/notifications/read-all").session(finder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));
        mvc.perform(get("/api/me/notifications/unread-count").session(finder))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(get("/api/me/notifications").session(finder))
                .andExpect(jsonPath("$.data[0].isRead").value(true))
                .andExpect(jsonPath("$.data[1].isRead").value(true));
    }

    @Test
    void 모두_읽음은_내_알림만_바꾼다() throws Exception {
        long item = registerFound("우산");
        approve(claim(item, owner));

        mvc.perform(patch("/api/me/notifications/read-all").session(finder)).andExpect(status().isOk());

        mvc.perform(get("/api/me/notifications/unread-count").session(owner))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void 남의_알림은_읽음_처리할_수_없고_없는_알림은_404() throws Exception {
        long item = registerFound("지갑");
        claim(item, owner);
        long finderNote = latestId(finder);

        mvc.perform(patch("/api/me/notifications/{id}/read", finderNote).session(owner))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/me/notifications/{id}/read", 999999).session(owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void 목록은_최근_30건까지만_보내고_읽지_않은_수는_전체를_센다() throws Exception {
        tx.executeWithoutResult(s -> {
            var student = studentRepository.findById(ownerId).orElseThrow();
            for (int i = 0; i < 35; i++) {
                notificationService.notify(student, NotificationType.CLAIM_REJECTED, "알림 " + i, null, null);
            }
        });

        mvc.perform(get("/api/me/notifications").session(owner))
                .andExpect(jsonPath("$.data", hasSize(30)))
                .andExpect(jsonPath("$.data[0].message").value("알림 34"))
                .andExpect(jsonPath("$.data[0].link").isEmpty());
        mvc.perform(get("/api/me/notifications/unread-count").session(owner))
                .andExpect(jsonPath("$.data.unreadCount").value(35));
    }

    @Test
    void 로그인_안하면_401_관리자는_403() throws Exception {
        mvc.perform(get("/api/me/notifications/unread-count")).andExpect(status().isUnauthorized());
        MockHttpSession admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        mvc.perform(get("/api/me/notifications").session(admin)).andExpect(status().isForbidden());
    }

    // ---------- 도우미 ----------

    private long latestId(MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/me/notifications").session(session))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/0/notificationId").asLong();
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownershipEvidence\":\"이니셜\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/claimId").asLong();
    }

    private long approve(long claimId) throws Exception {
        MockHttpSession admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        String body = mvc.perform(post("/api/admin/claims/{id}/approve", claimId).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/handover/handoverId").asLong();
    }

    private static String newId() {
        return String.valueOf(70000000 + SEQ.incrementAndGet());
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
