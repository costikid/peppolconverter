package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.freeagent.FreeAgentCompany;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentContact;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoiceSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FreeAgentApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FreeAgentApiService(
            @Value("${freeagent.api.base-url:https://api.freeagent.com}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        log.info("FreeAgentApiService initialised with base URL: {}", this.baseUrl);
    }

    public FreeAgentCompany getCompany(OAuth2AccessToken token) {
        String url = baseUrl + "/v2/company";
        JsonNode root = apiGet(url, token);
        JsonNode companyNode = root.get("company");
        if (companyNode == null) {
            throw new RuntimeException("FreeAgent API returned no company data");
        }
        return MAPPER.convertValue(companyNode, FreeAgentCompany.class);
    }

    public List<FreeAgentInvoiceSummary> listInvoices(OAuth2AccessToken token, int page) {
        String url = baseUrl + "/v2/invoices?view=all&page=" + page + "&per_page=25";
        JsonNode root = apiGet(url, token);
        JsonNode invoicesNode = root.get("invoices");
        if (invoicesNode == null) {
            return new ArrayList<>();
        }
        List<FreeAgentInvoiceSummary> invoices = new ArrayList<>();
        for (JsonNode node : invoicesNode) {
            invoices.add(MAPPER.convertValue(node, FreeAgentInvoiceSummary.class));
        }
        return invoices;
    }

    public FreeAgentInvoice getInvoice(OAuth2AccessToken token, String invoiceId) {
        String url = baseUrl + "/v2/invoices/" + invoiceId + "?nested_invoice_items=true";
        JsonNode root = apiGet(url, token);
        JsonNode invoiceNode = root.get("invoice");
        if (invoiceNode == null) {
            throw new RuntimeException("FreeAgent API returned no invoice data for id: " + invoiceId);
        }
        return MAPPER.convertValue(invoiceNode, FreeAgentInvoice.class);
    }

    public FreeAgentContact getContact(OAuth2AccessToken token, String contactUrl) {
        JsonNode root = apiGet(contactUrl, token);
        JsonNode contactNode = root.get("contact");
        if (contactNode == null) {
            throw new RuntimeException("FreeAgent API returned no contact data for url: " + contactUrl);
        }
        return MAPPER.convertValue(contactNode, FreeAgentContact.class);
    }

    private JsonNode apiGet(String url, OAuth2AccessToken token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.getTokenValue());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.debug("FreeAgent API GET: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("FreeAgent API call failed: " + response.getStatusCode() + " for URL: " + url);
        }

        try {
            return MAPPER.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FreeAgent API response from: " + url, e);
        }
    }
}
