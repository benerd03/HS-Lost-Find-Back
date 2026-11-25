package kr.ac.hanseo.lostfound.founditem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.common.config.AppProperties;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;
import kr.ac.hanseo.lostfound.user.StudentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FoundItemApiTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categoryRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FoundItemRepository foundItemRepository;
    @Autowired ClaimRequestRepository claimRequestRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TransactionTemplate tx;
    @Autowired AppProperties appProperties;

    MockHttpSession studentA;
    MockHttpSession studentB;
    long electronics;
    long wallet;
    long inmun;
    long library;

    @BeforeEach
    void setUp() throws Exception {
        studentA = login("STUDENT", "20210001", SeedDataRunner.TEST_PASSWORD);
        studentB = login("STUDENT", "20210002", SeedDataRunner.TEST_PASSWORD);
        electronics = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        wallet = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(1).getId();
        inmun = buildingRepository.findByCode("H11").orElseThrow().getId();
        library = buildingRepository.findByCode("H07").orElseThrow().getId();
    }

    // ---------- 등록 ----------

    @Test
    void 학생이_사진과_함께_등록하면_201과_ID() throws Exception {
        mvc.perform(createRequest("에어팟 프로", electronics, inmun, "2026-11-21T14:30:00", png())
                        .param("locationDetail", "2층").param("description", "흰색 케이스")
                        .session(studentA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.foundItemId").isNumber());
    }

    @Test
    void 사진이_없으면_IMAGE_REQUIRED() throws Exception {
        mvc.perform(createRequest("지갑", wallet, inmun, "2026-11-21T14:30:00", null).session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_REQUIRED"));
    }

    @Test
    void 이미지가_아닌_파일은_IMAGE_INVALID() throws Exception {
        MockMultipartFile text = new MockMultipartFile("image", "a.png", "image/png", "hello".getBytes());
        mvc.perform(createRequest("지갑", wallet, inmun, "2026-11-21T14:30:00", text).session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
    }

    @Test
    void 미래_날짜는_FUTURE_DATE() throws Exception {
        String future = LocalDateTime.now().plusDays(1).withNano(0).toString();
        mvc.perform(createRequest("지갑", wallet, inmun, future, png()).session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
    }

    @Test
    void 필수값이_빠지면_필드명과_함께_VALIDATION_FAILED() throws Exception {
        mvc.perform(multipart("/api/found-items").file(png()).param("categoryId", String.valueOf(wallet))
                        .session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("itemName")))
                .andExpect(jsonPath("$.message", containsString("foundAt")))
                .andExpect(jsonPath("$.message", containsString("buildingId")));
    }

    @Test
    void 날짜_형식이_틀리면_VALIDATION_FAILED() throws Exception {
        mvc.perform(createRequest("지갑", wallet, inmun, "어제", png()).session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("foundAt")));
    }

    @Test
    void 없는_건물이면_VALIDATION_FAILED() throws Exception {
        mvc.perform(createRequest("지갑", wallet, 99999, "2026-11-21T14:30:00", png()).session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("buildingId")));
    }

    @Test
    void UTC_시간으로_보내도_한국시간으로_저장된다() throws Exception {
        long id = create(studentA, "UTC테스트", electronics, inmun, "2026-11-21T05:30:00Z");
        mvc.perform(get("/api/found-items/{id}", id).session(studentA))
                .andExpect(jsonPath("$.data.foundAt").value("2026-11-21T14:30:00+09:00"));
    }

    @Test
    void 관리자는_습득물을_등록할_수_없다() throws Exception {
        MockHttpSession admin = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
        mvc.perform(createRequest("지갑", wallet, inmun, "2026-11-21T14:30:00", png()).session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 로그인하지_않으면_목록도_못_본다() throws Exception {
        mvc.perform(get("/api/found-items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    // ---------- 검색 ----------

    /** 최종 시연 성공 조건: 검색 조건을 1·2·3개로 바꾸면 결과가 달라진다 */
    @Test
    void 조건을_1_2_3개로_늘리면_결과가_좁혀진다() throws Exception {
        String tag = uniqueTag();
        create(studentA, tag + " 에어팟", electronics, inmun, "2026-11-20T10:00:00");
        create(studentA, tag + " 갤럭시버즈", electronics, library, "2026-11-20T11:00:00");
        create(studentA, tag + " 카드지갑", wallet, inmun, "2026-11-20T12:00:00");

        assertThat(searchCount("keyword", tag)).isEqualTo(3);
        assertThat(searchCount("keyword", tag, "categoryId", electronics)).isEqualTo(2);
        assertThat(searchCount("keyword", tag, "categoryId", electronics, "buildingId", inmun)).isEqualTo(1);
    }

    @Test
    void 키워드는_물품명과_설명에서_대소문자_무시하고_찾는다() throws Exception {
        String tag = uniqueTag();
        mvc.perform(createRequest("검정 우산", wallet, inmun, "2026-11-20T10:00:00", png())
                        .param("description", tag + " AirPods 케이스").session(studentA))
                .andExpect(status().isCreated());

        assertThat(searchCount("keyword", tag + " airpods")).isEqualTo(1);
    }

    @Test
    void 키워드의_퍼센트_기호는_글자_그대로_찾는다() throws Exception {
        String tag = uniqueTag();
        create(studentA, tag + " 100%충전 보조배터리", electronics, inmun, "2026-11-20T10:00:00");
        create(studentA, tag + " 100원 동전지갑", wallet, inmun, "2026-11-20T10:00:00");

        assertThat(searchCount("keyword", tag + " 100%")).isEqualTo(1);
    }

    @Test
    void 빈_문자열_조건은_없는_것과_같다() throws Exception {
        String tag = uniqueTag();
        create(studentA, tag + " 텀블러", wallet, inmun, "2026-11-20T10:00:00");

        mvc.perform(get("/api/found-items").param("keyword", tag).param("buildingId", "").param("categoryId", "")
                        .session(studentB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void 수령완료와_삭제된_습득물은_검색에서_빠진다() throws Exception {
        String tag = uniqueTag();
        long claimed = create(studentA, tag + " 완료된물건", wallet, inmun, "2026-11-20T10:00:00");
        long deleted = create(studentA, tag + " 지운물건", wallet, inmun, "2026-11-20T10:00:00");
        long awaiting = create(studentA, tag + " 수령대기물건", wallet, inmun, "2026-11-20T10:00:00");
        setStatus(claimed, FoundItemStatus.CLAIMED);
        setStatus(awaiting, FoundItemStatus.AWAITING_PICKUP);
        mvc.perform(delete("/api/found-items/{id}", deleted).session(studentA)).andExpect(status().isOk());

        mvc.perform(get("/api/found-items").param("keyword", tag).session(studentB))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].foundItemId").value(awaiting))
                .andExpect(jsonPath("$.data.items[0].status").value("AWAITING_PICKUP"));
    }

    @Test
    void 최신순으로_페이지를_나눠_보낸다() throws Exception {
        String tag = uniqueTag();
        long first = create(studentA, tag + " 1", wallet, inmun, "2026-11-20T10:00:00");
        long second = create(studentA, tag + " 2", wallet, inmun, "2026-11-20T10:00:00");
        long third = create(studentA, tag + " 3", wallet, inmun, "2026-11-20T10:00:00");

        mvc.perform(get("/api/found-items").param("keyword", tag).param("size", "2").session(studentB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].foundItemId").value(third))
                .andExpect(jsonPath("$.data.items[1].foundItemId").value(second))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("지갑·카드"))
                .andExpect(jsonPath("$.data.items[0].buildingName").value("인문사회관"))
                .andExpect(jsonPath("$.data.items[0].imageUrl", containsString("/uploads/")))
                .andExpect(jsonPath("$.data.items[0].foundAt").value("2026-11-20T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].locationDetail").isEmpty());

        mvc.perform(get("/api/found-items").param("keyword", tag).param("size", "2").param("page", "1")
                        .session(studentB))
                .andExpect(jsonPath("$.data.items[0].foundItemId").value(first));
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_VALIDATION_FAILED() throws Exception {
        mvc.perform(get("/api/found-items").param("size", "101").session(studentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---------- 상세 ----------

    @Test
    void 상세는_내_물건이면_own_item() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");

        mvc.perform(get("/api/found-items/{id}", id).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.foundItemId").value(id))
                .andExpect(jsonPath("$.data.categoryId").value(electronics))
                .andExpect(jsonPath("$.data.buildingId").value(inmun))
                .andExpect(jsonPath("$.data.createdAt", containsString("+09:00")))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.myClaimStatus").isEmpty())
                .andExpect(jsonPath("$.data.claimEligibility").value("own-item"))
                .andExpect(jsonPath("$.data.finder").doesNotExist());
    }

    @Test
    void 상세는_남의_물건이고_신청이_없으면_allowed() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");

        mvc.perform(get("/api/found-items/{id}", id).session(studentB))
                .andExpect(jsonPath("$.data.isMine").value(false))
                .andExpect(jsonPath("$.data.myClaimStatus").isEmpty())
                .andExpect(jsonPath("$.data.claimEligibility").value("allowed"));
    }

    @Test
    void 상세는_내_신청_상태를_먼저_보여준다() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        addClaim(id, "20210002");
        setStatus(id, FoundItemStatus.CLAIM_REQUESTED);

        mvc.perform(get("/api/found-items/{id}", id).session(studentB))
                .andExpect(jsonPath("$.data.myClaimStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.claimEligibility").value("already-requested"));
    }

    @Test
    void 상세는_신청_없이_수령대기면_closed() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        setStatus(id, FoundItemStatus.AWAITING_PICKUP);

        mvc.perform(get("/api/found-items/{id}", id).session(studentB))
                .andExpect(jsonPath("$.data.claimEligibility").value("closed"));
    }

    @Test
    void 없는_습득물은_404() throws Exception {
        mvc.perform(get("/api/found-items/{id}", 999999).session(studentA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------- 수정 ----------

    @Test
    void 본인은_내용을_고칠_수_있고_사진을_안_보내면_그대로다() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        String imageBefore = detail(id, studentA).get("imageUrl").asText();

        mvc.perform(updateRequest(id, "맥북", electronics, library, "2026-11-20T09:00:00", null)
                        .param("locationDetail", "3층 열람실").session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        JsonNode after = detail(id, studentA);
        assertThat(after.get("itemName").asText()).isEqualTo("맥북");
        assertThat(after.get("buildingName").asText()).isEqualTo("연암도서관");
        assertThat(after.get("locationDetail").asText()).isEqualTo("3층 열람실");
        assertThat(after.get("imageUrl").asText()).isEqualTo(imageBefore);
    }

    @Test
    void 사진을_바꾸면_새_파일로_바뀌고_옛_파일은_지워진다() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        String oldUrl = detail(id, studentA).get("imageUrl").asText();
        assertThat(Files.exists(uploadPath(oldUrl))).isTrue();

        MockMultipartFile jpg = new MockMultipartFile("image", "new.jpg", "image/jpeg", JPG);
        mvc.perform(updateRequest(id, "노트북", electronics, inmun, "2026-11-20T10:00:00", jpg).session(studentA))
                .andExpect(status().isOk());

        String newUrl = detail(id, studentA).get("imageUrl").asText();
        assertThat(newUrl).isNotEqualTo(oldUrl).endsWith(".jpg");
        assertThat(Files.exists(uploadPath(newUrl))).isTrue();
        assertThat(Files.exists(uploadPath(oldUrl))).isFalse();
    }

    @Test
    void 남의_습득물은_수정_삭제할_수_없다() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");

        mvc.perform(updateRequest(id, "가로채기", electronics, inmun, "2026-11-20T10:00:00", null).session(studentB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/found-items/{id}", id).session(studentB))
                .andExpect(status().isForbidden());
    }

    @Test
    void 수령_신청이_들어온_습득물은_ITEM_LOCKED() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        addClaim(id, "20210002");
        setStatus(id, FoundItemStatus.CLAIM_REQUESTED);

        mvc.perform(updateRequest(id, "노트북", electronics, inmun, "2026-11-20T10:00:00", null).session(studentA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_LOCKED"));
        mvc.perform(delete("/api/found-items/{id}", id).session(studentA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_LOCKED"));
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_404가_되고_사진도_지워지지만_행은_남는다() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        String imageUrl = detail(id, studentA).get("imageUrl").asText();

        mvc.perform(delete("/api/found-items/{id}", id).session(studentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(get("/api/found-items/{id}", id).session(studentA)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/found-items/{id}", id).session(studentA)).andExpect(status().isNotFound());
        assertThat(Files.exists(uploadPath(imageUrl))).isFalse();
        assertThat(foundItemRepository.findById(id)).get()
                .extracting(FoundItem::getDeletedAt).isNotNull();
    }

    @Test
    void 거절된_신청이_남아있어도_삭제된다() throws Exception {
        long id = create(studentA, "노트북", electronics, inmun, "2026-11-20T10:00:00");
        long claimId = addClaim(id, "20210002");
        tx.executeWithoutResult(s -> claimRequestRepository.findById(claimId).orElseThrow()
                .reject(null, "증빙 불일치", LocalDateTime.now()));

        mvc.perform(delete("/api/found-items/{id}", id).session(studentA)).andExpect(status().isOk());
        mvc.perform(get("/api/found-items").param("keyword", "노트북").session(studentB))
                .andExpect(jsonPath("$.data.items[*].foundItemId", not(hasItem((int) id))));
    }

    // ---------- 도우미 ----------

    private MockMultipartHttpServletRequestBuilder createRequest(String name, long categoryId, long buildingId,
            String foundAt, MockMultipartFile image) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/found-items");
        if (image != null) {
            builder.file(image);
        }
        return (MockMultipartHttpServletRequestBuilder) builder.param("itemName", name)
                .param("categoryId", String.valueOf(categoryId))
                .param("buildingId", String.valueOf(buildingId))
                .param("foundAt", foundAt);
    }

    private MockMultipartHttpServletRequestBuilder updateRequest(long id, String name, long categoryId,
            long buildingId, String foundAt, MockMultipartFile image) {
        MockMultipartHttpServletRequestBuilder builder = multipart(HttpMethod.PUT, "/api/found-items/{id}", id);
        if (image != null) {
            builder.file(image);
        }
        return (MockMultipartHttpServletRequestBuilder) builder.param("itemName", name)
                .param("categoryId", String.valueOf(categoryId))
                .param("buildingId", String.valueOf(buildingId))
                .param("foundAt", foundAt);
    }

    private long create(MockHttpSession session, String name, long categoryId, long buildingId, String foundAt)
            throws Exception {
        String body = mvc.perform(createRequest(name, categoryId, buildingId, foundAt, png()).session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/foundItemId").asLong();
    }

    private JsonNode detail(long id, MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/found-items/{id}", id).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private long searchCount(Object... params) throws Exception {
        var request = get("/api/found-items").session(studentB);
        for (int i = 0; i < params.length; i += 2) {
            request.param((String) params[i], String.valueOf(params[i + 1]));
        }
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/totalCount").asLong();
    }

    private void setStatus(long id, FoundItemStatus status) {
        tx.executeWithoutResult(s -> foundItemRepository.findById(id).orElseThrow().changeStatus(status));
    }

    private long addClaim(long foundItemId, String studentId) {
        return tx.execute(s -> claimRequestRepository.save(new ClaimRequest(
                foundItemRepository.findById(foundItemId).orElseThrow(),
                studentRepository.findById(studentId).orElseThrow(), null, "케이스 안쪽 이니셜")).getId());
    }

    private Path uploadPath(String imageUrl) {
        return Path.of(appProperties.uploadDir()).resolve(imageUrl.substring("/uploads/".length()));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("image", "photo.png", "image/png", PNG);
    }

    private static String uniqueTag() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MockHttpSession login(String role, String loginId, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"%s\",\"loginId\":\"%s\",\"password\":\"%s\"}"
                                .formatted(role, loginId, password)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
