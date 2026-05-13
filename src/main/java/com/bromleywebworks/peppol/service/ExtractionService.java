package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.service.strategy.ExtractionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExtractionService {

    private final Map<String, ExtractionStrategy> strategies;

    public ExtractionService(List<ExtractionStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ExtractionStrategy::getSupportedType, Function.identity()));
        log.info("Loaded extraction strategies: {}", this.strategies.keySet());
    }

    public ExtractedInvoice extract(MultipartFile file) throws IOException {
        return extract(file, "freeagent");
    }

    public ExtractedInvoice extract(MultipartFile file, String converterType) throws IOException {
        String type = converterType != null ? converterType.toLowerCase() : "freeagent";
        ExtractionStrategy strategy = strategies.get(type);
        if (strategy == null) {
            log.warn("Unknown converter type '{}', defaulting to freeagent", converterType);
            strategy = strategies.get("freeagent");
        }
        log.info("Using extraction strategy: {}", type);
        return strategy.extract(file);
    }
}
