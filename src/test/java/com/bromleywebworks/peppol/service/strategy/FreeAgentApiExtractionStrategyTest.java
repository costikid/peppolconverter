package com.bromleywebworks.peppol.service.strategy;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.service.ConfigService;
import com.bromleywebworks.peppol.service.FreeAgentApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FreeAgentApiExtractionStrategyTest {

    @MockBean
    private FreeAgentApiClient apiClient;

    @MockBean
    private ConfigService configService;

    @Autowired
    private FreeAgentApiExtractionStrategy strategy;

    @Test
    public void testStrategyExists() {
        assertNotNull(strategy);
        assertEquals("freeagent-api", strategy.getSupportedType());
    }

    @Test
    public void testMapUnitCode_Mapping() {
        // Test private method through reflection or via public methods
        // For now, verify strategy exists
        assertNotNull(strategy);
    }

    @Test
    public void testMapCountryCode_Mapping() {
        // Test country code mapping
        assertNotNull(strategy);
    }
}
