package com.bromleywebworks.peppol.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class OAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFreeAgentLandingPage_Accessible() throws Exception {
        mockMvc.perform(get("/freeagent-to-peppol"))
            .andExpect(status().isOk());
    }

    @Test
    public void testFreeAgentLoginPage_Accessible() throws Exception {
        mockMvc.perform(get("/freeagent-login"))
            .andExpect(status().isOk());
    }

    @Test
    public void testFreeAgentInvoices_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/freeagent/invoices"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    public void testFreeAgentConvert_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/freeagent/convert/123"))
            .andExpect(status().is3xxRedirection());
    }

}
