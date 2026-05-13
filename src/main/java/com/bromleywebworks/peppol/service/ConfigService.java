package com.bromleywebworks.peppol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Getter
    private ObjectNode root;

    @PostConstruct
    public void load() throws IOException {
        String envJson = System.getenv("PEPPOL_CONFIG_JSON");
        if (envJson != null && !envJson.isBlank()) {
            this.root = (ObjectNode) MAPPER.readTree(envJson);
            log.info("Loaded Peppol config from PEPPOL_CONFIG_JSON environment variable");
            return;
        }
        File configFile = new File("config.json");
        if (!configFile.exists()) {
            throw new IOException("config.json not found at: " + configFile.getAbsolutePath() +
                    " and PEPPOL_CONFIG_JSON environment variable is not set");
        }
        this.root = (ObjectNode) MAPPER.readTree(configFile);
        log.info("Loaded Peppol config from {}", configFile.getAbsolutePath());
    }

    public ObjectNode getSeller() {
        return (ObjectNode) root.get("seller");
    }

    public String getSellerString(String field) {
        return Optional.ofNullable(getSeller().get(field))
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .orElse(null);
    }

    public boolean isSellerVatRegistered() {
        return Optional.ofNullable(getSeller().get("isVatRegistered"))
                .map(com.fasterxml.jackson.databind.JsonNode::asBoolean)
                .orElse(false);
    }

    public ObjectNode getBuyerLookup(String normalizedName) {
        ObjectNode lookup = (ObjectNode) root.get("buyerLookup");
        if (lookup == null) return null;
        return (ObjectNode) lookup.get(normalizedName);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getUnitMappings() {
        ObjectNode mappings = (ObjectNode) root.get("mappings");
        if (mappings == null) return Map.of();
        ObjectNode units = (ObjectNode) mappings.get("units");
        if (units == null) return Map.of();
        return MAPPER.convertValue(units, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getVatMappings() {
        ObjectNode mappings = (ObjectNode) root.get("mappings");
        if (mappings == null) return Map.of();
        ObjectNode vat = (ObjectNode) mappings.get("vat");
        if (vat == null) return Map.of();
        return MAPPER.convertValue(vat, Map.class);
    }

    public String getSellerAddressField(String field) {
        ObjectNode address = (ObjectNode) getSeller().get("address");
        if (address == null) return null;
        return Optional.ofNullable(address.get(field))
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .orElse(null);
    }

    public String getSellerContactField(String field) {
        ObjectNode contact = (ObjectNode) getSeller().get("contact");
        if (contact == null) return null;
        return Optional.ofNullable(contact.get(field))
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .orElse(null);
    }

    public String getSellerBankField(String field) {
        ObjectNode bank = (ObjectNode) getSeller().get("bankDetails");
        if (bank == null) return null;
        return Optional.ofNullable(bank.get(field))
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .orElse(null);
    }
}
