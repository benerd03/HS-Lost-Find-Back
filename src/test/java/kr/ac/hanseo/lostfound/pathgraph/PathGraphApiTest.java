package kr.ac.hanseo.lostfound.pathgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;

/**
 * 보행 그래프 통합 테스트. src/test/resources/graph-fixture의 가상 언덕 캠퍼스를 쓴다.
 * 남북 400m, 북쪽으로 10% 오르막. 아랫관(0m)·낮은관(150m)·가운데관(200m)·윗관(400m).
 * 아래와 위에서 만나면 직선거리는 정가운데(가운데관)지만, 오르막이 느리므로 도보 시간으로는 낮은관이 더 공평하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PathGraphApiTest {

    private static final Path FIXTURE = Path.of("src/test/resources/graph-fixture");
    private static final Path DATA_DIR;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    static {
        try {
            DATA_DIR = Files.createTempDirectory("graph-test");
            copyFixture();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", DATA_DIR::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BuildingRepository buildingRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired StraightLinePlaceRecommender straightLine;

    @AfterEach
    void restoreFixture() throws Exception {
        copyFixture();
        mvc.perform(post("/api/admin/path-graph/rebuild").session(admin())).andExpect(status().isOk());
    }

    @Test
    void 서버가_켜질_때_CSV를_읽어_도보_시간을_계산해_둔다() throws Exception {
        mvc.perform(get("/api/admin/path-graph").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(6))
                .andExpect(jsonPath("$.data.edgeCount").value(4))
                .andExpect(jsonPath("$.data.candidateBuildingCount").value(4))
                .andExpect(jsonPath("$.data.computedAt").isString())
                .andExpect(jsonPath("$.data.unreachableNodes[0]").value("J-ISO"))
                .andExpect(jsonPath("$.data.unreachableNodes.length()").value(1))
                .andExpect(jsonPath("$.data.buildingsWithoutEntrance[0]").value("기타"));

        mvc.perform(get("/api/buildings"))
                .andExpect(jsonPath("$.data[?(@.name == '낮은관')].demoSelectable").value(true))
                .andExpect(jsonPath("$.data[?(@.name == '기타')].demoSelectable").value(false));
    }

    @Test
    void 같은_길도_오르막_경로가_내리막_경로보다_오래_걸린다() throws Exception {
        JsonNode up = route("E-BS-1", "BN");
        JsonNode down = route("E-BN-1", "BS");

        assertThat(up.get("nodes")).extracting(JsonNode::asText)
                .containsExactly("E-BS-1", "E-BL-1", "E-BM-1", "E-BN-1");
        assertThat(up.get("seconds").asInt()).isBetween(400, 412);   // 400m를 10% 오르막으로
        assertThat(down.get("seconds").asInt()).isBetween(280, 290); // 같은 길 내리막
    }

    @Test
    void 비워_둔_고도는_추정해서_오르막으로_계산한다() throws Exception {
        // 낮은관 고도는 비어 있지만 다른 노드로 맞춘 평면에서 약 15m → 150m 오르막 약 152초 (평지면 107초)
        assertThat(route("E-BS-1", "BL").get("seconds").asInt()).isBetween(148, 156);
    }

    /** 차별화 기능 시연: 직선거리 정가운데가 아니라, 언덕을 고려해 더 공평한 건물을 고른다 */
    @Test
    void 아래와_위에서_만나면_직선거리는_가운데관_도보시간은_낮은관() throws Exception {
        MockHttpSession finder = signupAndLogin("81000001");
        MockHttpSession owner = signupAndLogin("81000002");
        long handoverId = openHandover(finder, owner);
        long bottom = buildingRepository.findByCode("BS").orElseThrow().getId();
        long top = buildingRepository.findByCode("BN").orElseThrow().getId();

        ready(handoverId, finder, bottom).andExpect(jsonPath("$.data.status").value("WAITING"));
        String body = ready(handoverId, owner, top)
                .andExpect(jsonPath("$.data.status").value("PLACE_SET"))
                .andExpect(jsonPath("$.data.building.name").value("낮은관"))
                .andExpect(jsonPath("$.data.building.method").value("WALK_GRAPH"))
                .andReturn().getResponse().getContentAsString();

        JsonNode place = objectMapper.readTree(body).at("/data/building");
        int ownerSeconds = place.get("myWalkSeconds").asInt();   // 위에서 250m 내려옴
        int finderSeconds = place.get("otherWalkSeconds").asInt(); // 아래에서 150m 올라감
        assertThat(ownerSeconds).isBetween(174, 184);
        assertThat(finderSeconds).isBetween(148, 156);

        var straight = straightLine.recommend(new GeoPoint(36.690000, 126.580000), new GeoPoint(36.693597, 126.580000));
        assertThat(straight.get().building().getName()).isEqualTo("가운데관");
    }

    @Test
    void GPS_위치는_가장_가까운_노드로_맞추고_그_거리만큼_시간을_더한다() throws Exception {
        MockHttpSession finder = signupAndLogin("81000003");
        MockHttpSession owner = signupAndLogin("81000004");
        long handoverId = openHandover(finder, owner);

        // 둘 다 가운데관 동쪽 노드(J-01) 근처 → 같은 노드에 맞춰진다. 가장 가까운 건물은 가운데관(100m)
        gps(handoverId, finder, 36.691799, 126.581200);
        String body = gps(handoverId, owner, 36.691850, 126.581122)
                .andExpect(jsonPath("$.data.building.name").value("가운데관"))
                .andReturn().getResponse().getContentAsString();
        int mine = objectMapper.readTree(body).at("/data/building/myWalkSeconds").asInt();
        assertThat(mine).isBetween(72, 82); // 노드까지 약 6m(5초) + J-01 → 가운데관 평지 100m(71초)
    }

    @Test
    void 두_사람이_같은_건물에서_누르면_그_건물에서_0초() throws Exception {
        MockHttpSession finder = signupAndLogin("81000005");
        MockHttpSession owner = signupAndLogin("81000006");
        long handoverId = openHandover(finder, owner);
        long middle = buildingRepository.findByCode("BM").orElseThrow().getId();

        ready(handoverId, finder, middle);
        ready(handoverId, owner, middle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.building.name").value("가운데관"))
                .andExpect(jsonPath("$.data.building.myWalkSeconds").value(0))
                .andExpect(jsonPath("$.data.building.otherWalkSeconds").value(0));
    }

    @Test
    void CSV가_틀리면_파일명과_행번호를_알려주고_기존_그래프는_그대로다() throws Exception {
        Files.writeString(DATA_DIR.resolve("path_edges.csv"), """
                from_node_code,to_node_code,edge_type,distance_m,forward_seconds,backward_seconds,memo
                E-BS-1,E-BL-1,PATH,,,,
                E-BL-1,E-XX-9,PATH,,,,
                """);

        mvc.perform(post("/api/admin/path-graph/rebuild").session(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("path_edges.csv 3행")))
                .andExpect(jsonPath("$.message", containsString("E-XX-9")));

        mvc.perform(get("/api/admin/path-graph").session(admin()))
                .andExpect(jsonPath("$.data.edgeCount").value(4));
    }

    @Test
    void 실측_시간을_넣고_다시_읽으면_계산값_대신_실측값을_쓴다() throws Exception {
        Files.writeString(DATA_DIR.resolve("path_edges.csv"), """
                from_node_code,to_node_code,edge_type,distance_m,forward_seconds,backward_seconds,memo
                E-BS-1,E-BL-1,STAIRS,,300,90,계단 실측
                E-BL-1,E-BM-1,PATH,,,,
                E-BM-1,E-BN-1,PATH,,,,
                E-BM-1,J-01,PATH,,,,
                """);

        mvc.perform(post("/api/admin/path-graph/rebuild").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edgeCount").value(4));

        assertThat(route("E-BS-1", "BL").get("seconds").asInt()).isEqualTo(300);
        assertThat(route("E-BL-1", "BS").get("seconds").asInt()).isEqualTo(90);
    }

    @Test
    void 중복된_길이나_잘못된_종류도_막는다() throws Exception {
        Files.writeString(DATA_DIR.resolve("path_edges.csv"), """
                from_node_code,to_node_code,edge_type,distance_m,forward_seconds,backward_seconds,memo
                E-BS-1,E-BL-1,PATH,,,,
                E-BL-1,E-BS-1,PATH,,,,
                """);
        mvc.perform(post("/api/admin/path-graph/rebuild").session(admin()))
                .andExpect(jsonPath("$.message", containsString("path_edges.csv 3행")));

        Files.writeString(DATA_DIR.resolve("path_nodes.csv"), Files.readString(FIXTURE.resolve("path_nodes.csv"))
                .replace("J-01,JUNCTION", "J-01,CROSSROAD"));
        mvc.perform(post("/api/admin/path-graph/rebuild").session(admin()))
                .andExpect(jsonPath("$.message", containsString("path_nodes.csv 6행")))
                .andExpect(jsonPath("$.message", containsString("node_type")));
    }

    @Test
    void 경로_조회는_없는_코드면_404_학생은_403() throws Exception {
        mvc.perform(get("/api/admin/path-graph/route").param("fromNode", "NOPE").param("toBuilding", "BS")
                        .session(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/path-graph/route").param("fromNode", "J-ISO").param("toBuilding", "BS")
                        .session(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/path-graph").session(signupAndLogin("81000009")))
                .andExpect(status().isForbidden());
    }

    // ---------- 도우미 ----------

    private static void copyFixture() throws IOException {
        for (String f : new String[] {"buildings.csv", "path_nodes.csv", "path_edges.csv"}) {
            Files.copy(FIXTURE.resolve(f), DATA_DIR.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonNode route(String from, String toBuilding) throws Exception {
        String body = mvc.perform(get("/api/admin/path-graph/route").param("fromNode", from)
                        .param("toBuilding", toBuilding).session(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private long openHandover(MockHttpSession finder, MockHttpSession owner) throws Exception {
        long category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        long building = buildingRepository.findByCode("BS").orElseThrow().getId();
        String item = mvc.perform(multipart("/api/found-items")
                        .file(new MockMultipartFile("image", "p.png", "image/png", PNG))
                        .param("itemName", "에어팟").param("categoryId", String.valueOf(category))
                        .param("buildingId", String.valueOf(building)).param("foundAt", "2026-11-20T10:00:00")
                        .session(finder))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(item).at("/data/foundItemId").asLong();
        String claim = mvc.perform(post("/api/found-items/{id}/claims", itemId).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownershipEvidence\":\"이니셜\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long claimId = objectMapper.readTree(claim).at("/data/claimId").asLong();
        String approved = mvc.perform(post("/api/admin/claims/{id}/approve", claimId).session(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(approved).at("/data/handover/handoverId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions ready(long handoverId, MockHttpSession session,
            long buildingId) throws Exception {
        return mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"buildingId\":" + buildingId + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions gps(long handoverId, MockHttpSession session,
            double lat, double lng) throws Exception {
        return mvc.perform(post("/api/handovers/{id}/ready", handoverId).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":" + lat + ",\"lng\":" + lng + "}"));
    }

    private MockHttpSession admin() throws Exception {
        return login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);
    }

    private MockHttpSession signupAndLogin(String id) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"studentId":"%s","password":"pass1234","name":"학생%s","department":"학과","phone":"010-2222-3333"}"""
                .formatted(id, id)));
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
