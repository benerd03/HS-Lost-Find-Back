package kr.ac.hanseo.lostfound.lostitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.user.StudentRepository;

/**
 * 분실 신고·매칭. 매칭은 DB 전체 습득물을 보기 때문에, 테스트마다 서로 겹치지 않는 날짜(10일 간격)를 쓴다.
 * A = 20210001(분실자), B = 20210002(습득자)
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LostItemApiTest {

    private static final AtomicInteger SLOT = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FoundItemRepository foundItemRepository;
    @Autowired LostItemRepository lostItemRepository;
    @Autowired ClaimRequestRepository claimRequestRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TransactionTemplate tx;

    MockHttpSession studentA;
    MockHttpSession studentB;
    long electronics;
    long wallet;
    long inmun;
    long library;
    long etc;
    /** 이 테스트에서 쓰는 분실 일시. 다른 테스트와 ±3일이 겹치지 않는다 */
    LocalDateTime base;

    @BeforeEach
    void setUp() throws Exception {
        studentA = login("STUDENT", "20210001", SeedDataRunner.TEST_PASSWORD);
        studentB = login("STUDENT", "20210002", SeedDataRunner.TEST_PASSWORD);
        var categories = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        electronics = categories.get(0).getId();
        wallet = categories.get(1).getId();
        inmun = buildingRepository.findByCode("H11").orElseThrow().getId();
        library = buildingRepository.findByCode("H07").orElseThrow().getId();
        etc = buildingRepository.findByCode("ETC").orElseThrow().getId();
        base = LocalDateTime.of(2024, 1, 1, 13, 0).plusDays(10L * SLOT.incrementAndGet());
    }

    // ---------- 매칭 ----------

    /** 최종 시연 성공 조건: 분실 신고를 등록하면 미리 넣어 둔 습득물이 Top 3의 1위로 추천된다 */
    @Test
    void 등록하면_미리_넣어둔_습득물이_1위로_추천된다() throws Exception {
        long airpods = found("에어팟 프로", electronics, inmun, base.plusHours(25), "흰색 케이스");
        found("갤럭시 버즈", electronics, library, base.plusHours(2), null);
        found("카드지갑", wallet, inmun, base.plusHours(1), null);

        JsonNode data = createLost("에어팟", electronics, inmun, base, studentA);

        assertThat(data.get("lostItemId").asLong()).isPositive();
        JsonNode first = data.get("matches").get(0);
        assertThat(first.get("foundItemId").asLong()).isEqualTo(airpods);
        assertThat(first.get("score").asInt()).isEqualTo(90);
        assertThat(first.get("reasons")).hasSize(3);
        assertThat(first.at("/reasons/0/code").asText()).isEqualTo("CATEGORY");
        assertThat(first.at("/reasons/1/code").asText()).isEqualTo("BUILDING");
        assertThat(first.at("/reasons/2/code").asText()).isEqualTo("NAME");
        assertThat(first.at("/reasons/2/label").asText()).isEqualTo("‘에어팟’ 부분 일치");
        assertThat(first.get("claimEligibility").asText()).isEqualTo("allowed");
        assertThat(first.get("status").asText()).isEqualTo("STORED");
        assertThat(first.get("categoryName").asText()).isEqualTo("전자기기");
        assertThat(first.get("buildingName").asText()).isEqualTo("인문사회관");
        assertThat(first.get("foundAt").asText()).endsWith("+09:00");
        assertThat(data.get("matches")).hasSize(3);
    }

    @Test
    void 점수는_항상_근거_점수의_합이다() throws Exception {
        found("우산", wallet, library, base, "검정 에어팟 케이스가 달린 우산");
        found("에어팟 케이스", electronics, etc, base, null);

        JsonNode matches = createLost("에어팟", electronics, inmun, base, studentA).get("matches");

        for (JsonNode m : matches) {
            int sum = 0;
            for (JsonNode r : m.get("reasons")) {
                sum += r.get("points").asInt();
            }
            assertThat(m.get("score").asInt()).isEqualTo(sum);
        }
        // 우산: 설명에 '에어팟' +10 / 에어팟 케이스: 카테고리 30 + 이름 30
        assertThat(matches.get(0).get("score").asInt()).isEqualTo(60);
        assertThat(matches.get(1).get("score").asInt()).isEqualTo(10);
        assertThat(matches.get(1).at("/reasons/0/code").asText()).isEqualTo("DESCRIPTION");
        assertThat(matches.get(1).at("/reasons/0/label").asText()).isEqualTo("설명에 ‘에어팟’ 포함");
    }

    @Test
    void 물품명은_양방향으로_부분_일치한다() throws Exception {
        long shortName = found("지갑", wallet, library, base, null);

        JsonNode matches = createLost("갈색 가죽 지갑", electronics, inmun, base, studentA).get("matches");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("foundItemId").asLong()).isEqualTo(shortName);
        assertThat(matches.get(0).at("/reasons/0/code").asText()).isEqualTo("NAME");
    }

    @Test
    void 기타_건물끼리는_건물_점수가_없다() throws Exception {
        found("에어팟", electronics, etc, base, null);

        JsonNode match = createLost("에어팟", electronics, etc, base, studentA).at("/matches/0");

        assertThat(match.get("score").asInt()).isEqualTo(60);
        assertThat(match.toString()).doesNotContain("BUILDING");
    }

    @Test
    void 분실_일시_3일_밖이나_내가_등록한_습득물은_후보가_아니다() throws Exception {
        found("에어팟", electronics, inmun, base.minusDays(3).minusMinutes(1), null);
        found("에어팟", electronics, inmun, base.plusDays(3).plusMinutes(1), null);
        long edge = found("에어팟", electronics, inmun, base.plusDays(3), null);
        foundBy("20210001", "에어팟", electronics, inmun, base, null);

        JsonNode matches = createLost("에어팟", electronics, inmun, base, studentA).get("matches");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("foundItemId").asLong()).isEqualTo(edge);
    }

    @Test
    void 수령대기_수령완료_삭제된_습득물은_후보가_아니고_수령신청은_후보다() throws Exception {
        long awaiting = found("에어팟", electronics, inmun, base, null);
        long claimed = found("에어팟", electronics, inmun, base, null);
        long deleted = found("에어팟", electronics, inmun, base, null);
        long requested = found("에어팟", electronics, inmun, base, null);
        setStatus(awaiting, FoundItemStatus.AWAITING_PICKUP);
        setStatus(claimed, FoundItemStatus.CLAIMED);
        setStatus(requested, FoundItemStatus.CLAIM_REQUESTED);
        tx.executeWithoutResult(s -> foundItemRepository.findById(deleted).orElseThrow().softDelete(base));

        JsonNode matches = createLost("에어팟", electronics, inmun, base, studentA).get("matches");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("foundItemId").asLong()).isEqualTo(requested);
        assertThat(matches.get(0).get("status").asText()).isEqualTo("CLAIM_REQUESTED");
    }

    @Test
    void 점수_0은_빼고_후보가_없으면_빈_배열() throws Exception {
        found("텀블러", wallet, library, base, null);

        mvc.perform(post("/api/lost-items").session(studentA).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson("에어팟", electronics, inmun, base)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.matches", hasSize(0)));
    }

    @Test
    void 같은_점수면_시간차가_작은_순_그다음_최근_등록순_3건까지() throws Exception {
        long far = found("에어팟", electronics, inmun, base.plusHours(30), null);
        long nearOld = found("에어팟", electronics, inmun, base.plusHours(1), null);
        long nearNew = found("에어팟", electronics, inmun, base.minusHours(1), null);
        long mid = found("에어팟", electronics, inmun, base.plusHours(5), null);

        JsonNode matches = createLost("에어팟", electronics, inmun, base, studentA).get("matches");

        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).get("foundItemId").asLong()).isEqualTo(nearNew);
        assertThat(matches.get(1).get("foundItemId").asLong()).isEqualTo(nearOld);
        assertThat(matches.get(2).get("foundItemId").asLong()).isEqualTo(mid);
        assertThat(far).isPositive();
    }

    @Test
    void 이미_신청한_후보는_목록에_남고_already_requested로_보인다() throws Exception {
        long airpods = found("에어팟", electronics, inmun, base, null);
        addClaim(airpods, "20210001", null);

        JsonNode match = createLost("에어팟", electronics, inmun, base, studentA).at("/matches/0");

        assertThat(match.get("foundItemId").asLong()).isEqualTo(airpods);
        assertThat(match.get("claimEligibility").asText()).isEqualTo("already-requested");
    }

    @Test
    void 후보_다시_조회하면_신고_뒤에_올라온_습득물도_나온다() throws Exception {
        long lostId = createLost("에어팟", electronics, inmun, base, studentA).get("lostItemId").asLong();
        long later = found("에어팟", electronics, inmun, base.plusDays(1), null);

        mvc.perform(get("/api/lost-items/{id}/matches", lostId).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches", hasSize(1)))
                .andExpect(jsonPath("$.data.matches[0].foundItemId").value(later));
    }

    // ---------- 등록 검증 ----------

    @Test
    void 미래_날짜는_FUTURE_DATE() throws Exception {
        mvc.perform(post("/api/lost-items").session(studentA).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson("에어팟", electronics, inmun, LocalDateTime.now().plusDays(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
    }

    @Test
    void 필수값이_빠지면_VALIDATION_FAILED() throws Exception {
        mvc.perform(post("/api/lost-items").session(studentA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"\",\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("itemName")))
                .andExpect(jsonPath("$.message", containsString("lostAt")));
    }

    @Test
    void 관리자는_분실_신고를_쓸_수_없다() throws Exception {
        MockHttpSession admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        mvc.perform(post("/api/lost-items").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson("에어팟", electronics, inmun, base)))
                .andExpect(status().isForbidden());
    }

    // ---------- 조회·수정·찾았음·삭제 ----------

    @Test
    void 상세는_본인만_볼_수_있다() throws Exception {
        long id = createLost("에어팟", electronics, inmun, base, studentA).get("lostItemId").asLong();

        mvc.perform(get("/api/lost-items/{id}", id).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lostItemId").value(id))
                .andExpect(jsonPath("$.data.itemName").value("에어팟"))
                .andExpect(jsonPath("$.data.categoryId").value(electronics))
                .andExpect(jsonPath("$.data.categoryName").value("전자기기"))
                .andExpect(jsonPath("$.data.buildingId").value(inmun))
                .andExpect(jsonPath("$.data.buildingName").value("인문사회관"))
                .andExpect(jsonPath("$.data.lostAt", containsString("+09:00")))
                .andExpect(jsonPath("$.data.locationDetail").value("2층 강의실"))
                .andExpect(jsonPath("$.data.description").isEmpty())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.createdAt", containsString("+09:00")));

        mvc.perform(get("/api/lost-items/{id}", id).session(studentB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/lost-items/{id}/matches", id).session(studentB))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lost-items/{id}", 999999).session(studentA))
                .andExpect(status().isNotFound());
    }

    @Test
    void 수정하면_바뀐_상세를_돌려주고_남은_수정할_수_없다() throws Exception {
        long id = createLost("에어팟", electronics, inmun, base, studentA).get("lostItemId").asLong();

        mvc.perform(put("/api/lost-items/{id}", id).session(studentA).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson("에어팟 프로", electronics, library, base.minusHours(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemName").value("에어팟 프로"))
                .andExpect(jsonPath("$.data.buildingName").value("연암도서관"));

        mvc.perform(put("/api/lost-items/{id}", id).session(studentB).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson("가로채기", electronics, library, base)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 찾았음을_누르면_CLOSED가_되고_이후_수정_삭제_찾았음은_LOST_ITEM_CLOSED() throws Exception {
        long id = createLost("에어팟", electronics, inmun, base, studentA).get("lostItemId").asLong();

        mvc.perform(patch("/api/lost-items/{id}/close", id).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mvc.perform(patch("/api/lost-items/{id}/close", id).session(studentA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOST_ITEM_CLOSED"));
        mvc.perform(put("/api/lost-items/{id}", id).session(studentA).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson("에어팟", electronics, inmun, base)))
                .andExpect(jsonPath("$.code").value("LOST_ITEM_CLOSED"));
        mvc.perform(delete("/api/lost-items/{id}", id).session(studentA))
                .andExpect(jsonPath("$.code").value("LOST_ITEM_CLOSED"));
    }

    @Test
    void 진행_중인_신청이_연결되어_있으면_삭제할_수_없다() throws Exception {
        long id = createLost("에어팟", electronics, inmun, base, studentA).get("lostItemId").asLong();
        long foundId = found("에어팟", electronics, inmun, base, null);
        addClaim(foundId, "20210001", id);

        mvc.perform(delete("/api/lost-items/{id}", id).session(studentA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOST_ITEM_HAS_CLAIM"));
    }

    @Test
    void 취소된_신청만_연결되어_있으면_연결을_끊고_삭제한다() throws Exception {
        long id = createLost("에어팟", electronics, inmun, base, studentA).get("lostItemId").asLong();
        long foundId = found("에어팟", electronics, inmun, base, null);
        long claimId = addClaim(foundId, "20210001", id);
        tx.executeWithoutResult(s -> claimRequestRepository.findById(claimId).orElseThrow().cancel(base));

        mvc.perform(delete("/api/lost-items/{id}", id).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        assertThat(lostItemRepository.findById(id)).isEmpty();
        Long linked = tx.execute(s -> {
            var claim = claimRequestRepository.findById(claimId).orElseThrow();
            return claim.getLostItem() == null ? null : claim.getLostItem().getId();
        });
        assertThat(linked).isNull();
        mvc.perform(get("/api/lost-items/{id}", id).session(studentA)).andExpect(status().isNotFound());
    }

    @Test
    void 내_분실_신고는_내것만_최신순으로_상태별로_본다() throws Exception {
        MockHttpSession fresh = signupAndLogin();
        long first = createLost("우산", wallet, inmun, base, fresh).get("lostItemId").asLong();
        long second = createLost("이어폰", electronics, inmun, base, fresh).get("lostItemId").asLong();
        createLost("남의것", electronics, inmun, base, studentB);
        mvc.perform(patch("/api/lost-items/{id}/close", first).session(fresh)).andExpect(status().isOk());

        mvc.perform(get("/api/me/lost-items").session(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].lostItemId").value(second))
                .andExpect(jsonPath("$.data[1].lostItemId").value(first));

        mvc.perform(get("/api/me/lost-items").param("status", "OPEN").session(fresh))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].lostItemId").value(second));

        mvc.perform(get("/api/me/lost-items").param("status", "").session(fresh))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ---------- 도우미 ----------

    private long found(String name, long categoryId, long buildingId, LocalDateTime foundAt, String description) {
        return foundBy("20210002", name, categoryId, buildingId, foundAt, description);
    }

    private long foundBy(String studentId, String name, long categoryId, long buildingId, LocalDateTime foundAt,
            String description) {
        return tx.execute(s -> foundItemRepository.save(new FoundItem(
                studentRepository.findById(studentId).orElseThrow(),
                categoryRepository.findById(categoryId).orElseThrow(), name, foundAt,
                buildingRepository.findById(buildingId).orElseThrow(), null, description,
                "/uploads/test.png")).getId());
    }

    private void setStatus(long id, FoundItemStatus status) {
        tx.executeWithoutResult(s -> foundItemRepository.findById(id).orElseThrow().changeStatus(status));
    }

    private long addClaim(long foundItemId, String studentId, Long lostItemId) {
        return tx.execute(s -> claimRequestRepository.save(new ClaimRequest(
                foundItemRepository.findById(foundItemId).orElseThrow(),
                studentRepository.findById(studentId).orElseThrow(),
                lostItemId == null ? null : lostItemRepository.findById(lostItemId).orElseThrow(),
                "케이스 안쪽 이니셜")).getId());
    }

    private String lostJson(String name, long categoryId, long buildingId, LocalDateTime lostAt) {
        return """
                {"itemName":"%s","categoryId":%d,"lostAt":"%s","buildingId":%d,"locationDetail":"2층 강의실"}"""
                .formatted(name, categoryId, lostAt.withNano(0), buildingId);
    }

    private JsonNode createLost(String name, long categoryId, long buildingId, LocalDateTime lostAt,
            MockHttpSession session) throws Exception {
        String body = mvc.perform(post("/api/lost-items").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(lostJson(name, categoryId, buildingId, lostAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private MockHttpSession signupAndLogin() throws Exception {
        String id = String.valueOf(30000000 + SLOT.get());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"studentId":"%s","password":"pass1234","name":"새학생","department":"학과","phone":"010-2222-3333"}"""
                .formatted(id))).andExpect(status().isCreated());
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
