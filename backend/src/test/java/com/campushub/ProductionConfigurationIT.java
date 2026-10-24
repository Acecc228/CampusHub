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
@ActiveProfiles("prod")
class ProductionConfigurationIT extends MySqlIntegrationSupport {
    @Autowired MockMvc mvc;

    @Test void productionHasNoDemoLoginEvenForAuthenticatedRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/demo-login").with(user("test-only")).with(csrf())
                .param("username", "demo_admin").param("password", "wrong"))
                .andExpect(status().isNotFound());
    }

    @Test void productionDocumentsAreAbsentEvenForAuthenticatedRequests() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"}) {
            mvc.perform(get(path).with(user("test-only"))).andExpect(status().isNotFound());
        }
    }

    @Test void unknownAuthenticatedApiGetsUnified404() throws Exception {
        mvc.perform(get("/api/v1/unknown").with(user("test-only")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
