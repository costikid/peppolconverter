package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.entity.ConvertedInvoice;
import com.bromleywebworks.peppol.repository.ConvertedInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceStorageService {

    private final ConvertedInvoiceRepository repository;

    public ConvertedInvoice save(ExtractedInvoice extracted, String xml, String userId) {
        ConvertedInvoice entity = new ConvertedInvoice();
        entity.setUserId(userId);
        entity.setInvoiceNumber(extracted.getInvoiceNumber());
        entity.setIssueDate(extracted.getIssueDate());
        entity.setDueDate(extracted.getDueDate());
        entity.setCurrency(extracted.getCurrency());
        entity.setTotalAmount(extracted.getTotalAmount());
        entity.setVatAmount(extracted.getVatAmount());
        entity.setDueAmount(extracted.getDueAmount());
        entity.setSellerName(extracted.getSeller() != null ? extracted.getSeller().getName() : null);
        entity.setBuyerName(extracted.getBuyer() != null ? extracted.getBuyer().getName() : null);
        entity.setSource("oauth");
        entity.setPeppolXml(xml);

        ConvertedInvoice saved = repository.save(entity);
        log.info("Saved converted invoice id={} for user={}, invoiceNumber={}",
                saved.getId(), userId, extracted.getInvoiceNumber());
        return saved;
    }

    public List<ConvertedInvoice> listForUser(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<ConvertedInvoice> findByIdForUser(Long id, String userId) {
        return repository.findByIdAndUserId(id, userId);
    }
}
