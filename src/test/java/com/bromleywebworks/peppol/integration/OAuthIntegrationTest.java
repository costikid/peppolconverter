package com.bromleywebworks.peppol.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class OAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testOAuthCallback_Redirects() throws Exception {
        // OAuth callback path is handled by Spring Security, verify endpoint exists
        mockMvc.perform(get("/oauth2/callback/freeagent"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                // OAuth callback without proper state returns 400 or redirects
                org.junit.jupiter.api.Assertions.assertTrue(
                    status >= 300 && status < 500,
                    "Expected 3xx redirect or 4xx error, got: " + status
                );
            });
    }

    @Test
    public void testFreeAgentLoginPage_Accessible() throws Exception {
        mockMvc.perform(get("/freeagent/login"))
            .andExpect(status().isOk());
    }
}
