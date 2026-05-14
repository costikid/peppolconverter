package com.bromleywebworks.peppol.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class FreeAgentApiClientTest {

    @MockBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Autowired
    private FreeAgentApiClient apiClient;

    @Test
    public void testApiClient_Wired() {
        assertNotNull(apiClient);
    }
}
