package com.campushub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// Formal authentication defaults on the real shared test DB; prod itself rejects that DB.
@ActiveProfiles("test")
class FormalDefaultsIT extends MySqlIntegrationSupport {
    @Autowired MockMvc mvc;

    @Test void formalDefaultsHaveNoDemoLoginEvenForAuthenticatedRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/demo-login").with(user("test-only")).with(csrf())
                .param("username", "demo_admin").param("password", "wrong"))
                .andExpect(status().isNotFound());
    }

    @Test void formalDefaultsOmitDocumentsEvenForAuthenticatedRequests() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"}) {
            mvc.perform(get(path).with(user("test-only"))).andExpect(status().isNotFound());
        }
    }

    @Test void unknownAuthenticatedApiGetsUnified404() throws Exception {
        mvc.perform(get("/api/v1/unknown").with(user("test-only")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
