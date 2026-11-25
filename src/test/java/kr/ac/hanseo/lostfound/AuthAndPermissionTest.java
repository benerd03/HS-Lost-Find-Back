package kr.ac.hanseo.lostfound;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import kr.ac.hanseo.lostfound.common.seed.SeedDataRunner;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAndPermissionTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 회원가입_후_로그인하면_내정보를_볼수있다() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                { "studentId": "20229999", "password": "pass1234!", "name": "김하은",
                  "department": "무인항공기학과", "phone": "010-1234-5678" }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentId").value("20229999"));

        MockHttpSession session = login("STUDENT", "20229999", "pass1234!");

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("20229999"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.department").value("무인항공기학과"));
    }

    @Test
    void 같은_학번으로_다시_가입하면_409() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                { "studentId": "20210001", "password": "pass1234", "name": "중복",
                  "department": "학과", "phone": "010-0000-0000" }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ID_DUPLICATE"));
    }

    @Test
    void 입력값이_틀리면_필드명과_함께_400() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                { "studentId": "2021", "password": "onlyletters", "name": "a",
                  "department": "b", "phone": "01012345678" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("studentId")))
                .andExpect(jsonPath("$.message", containsString("password")))
                .andExpect(jsonPath("$.message", containsString("phone")));
    }

    @Test
    void 비밀번호가_틀리면_LOGIN_FAILED() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                { "role": "STUDENT", "loginId": "20210001", "password": "wrong1234" }"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 학생_탭으로_관리자_ID를_넣으면_로그인_실패() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                { "role": "STUDENT", "loginId": "admin", "password": "admin1234" }"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 쿠키없이_호출하면_LOGIN_REQUIRED() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 만료된_세션쿠키로_호출하면_SESSION_EXPIRED() throws Exception {
        mvc.perform(get("/api/auth/me").with(request -> {
                    request.setRequestedSessionId("expired-session-id");
                    request.setRequestedSessionIdValid(false);
                    return request;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void 로그아웃하면_세션이_끊기고_쿠키를_지운다() throws Exception {
        MockHttpSession session = login("STUDENT", "20210001", SeedDataRunner.TEST_PASSWORD);

        mvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    /** 최종 시연 성공 조건: 학생 계정으로 관리자 API를 호출하면 403 */
    @Test
    void 학생이_관리자_API를_호출하면_403() throws Exception {
        MockHttpSession session = login("STUDENT", "20210001", SeedDataRunner.TEST_PASSWORD);

        mvc.perform(get("/api/admin/summary").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자는_상태별_건수를_0까지_포함해_본다() throws Exception {
        MockHttpSession session = login("ADMIN", SeedDataRunner.ADMIN_ID, SeedDataRunner.ADMIN_PASSWORD);

        mvc.perform(get("/api/admin/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.STORED").value(0))
                .andExpect(jsonPath("$.data.counts.CLAIM_REQUESTED").value(0))
                .andExpect(jsonPath("$.data.counts.AWAITING_PICKUP").value(0))
                .andExpect(jsonPath("$.data.counts.CLAIMED").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void 카테고리와_건물은_로그인없이_조회된다() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].categoryId").isNumber())
                .andExpect(jsonPath("$.data[0].name").value("전자기기"));

        mvc.perform(get("/api/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].buildingId").isNumber())
                .andExpect(jsonPath("$.data[0].demoSelectable").isBoolean())
                .andExpect(jsonPath("$.data[?(@.name == '기타')].demoSelectable").value(false))
                .andExpect(jsonPath("$.data[0].lat").doesNotExist());
    }

    @Test
    void 없는_경로는_404_NOT_FOUND() throws Exception {
        MockHttpSession session = login("STUDENT", "20210001", SeedDataRunner.TEST_PASSWORD);
        mvc.perform(get("/api/no-such-api").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private MockHttpSession login(String role, String loginId, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"%s\",\"loginId\":\"%s\",\"password\":\"%s\"}"
                                .formatted(role, loginId, password)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
