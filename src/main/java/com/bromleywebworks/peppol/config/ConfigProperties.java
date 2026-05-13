package com.bromleywebworks.peppol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "peppol")
public class ConfigProperties {

    private String configPath = "config.json";
}
