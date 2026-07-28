package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.entity.ConvertedInvoice;
import com.bromleywebworks.peppol.repository.ConvertedInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceStorageServiceTest {

    @Mock
    private ConvertedInvoiceRepository repository;

    @InjectMocks
    private InvoiceStorageService service;

    private ExtractedInvoice extracted;

    @BeforeEach
    void setUp() {
        extracted = new ExtractedInvoice();
        extracted.setInvoiceNumber("INV-001");
        extracted.setIssueDate(LocalDate.of(2024, 1, 15));
        extracted.setDueDate(LocalDate.of(2024, 1, 30));
        extracted.setCurrency("GBP");
        extracted.setTotalAmount(new BigDecimal("100.00"));
        extracted.setVatAmount(new BigDecimal("20.00"));
        extracted.setDueAmount(new BigDecimal("120.00"));

        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        seller.setName("Seller Co");
        extracted.setSeller(seller);

        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        buyer.setName("Buyer Inc");
        extracted.setBuyer(buyer);
    }

    @Test
    void save_shouldMapAllFieldsCorrectly() {
        String xml = "<xml>test</xml>";
        String userId = "12345";

        ConvertedInvoice savedEntity = new ConvertedInvoice();
        savedEntity.setId(1L);
        when(repository.save(any(ConvertedInvoice.class))).thenReturn(savedEntity);

        ConvertedInvoice result = service.save(extracted, xml, userId);

        ArgumentCaptor<ConvertedInvoice> captor = ArgumentCaptor.forClass(ConvertedInvoice.class);
        verify(repository).save(captor.capture());
        ConvertedInvoice captured = captor.getValue();

        assertEquals(userId, captured.getUserId());
        assertEquals("INV-001", captured.getInvoiceNumber());
        assertEquals(LocalDate.of(2024, 1, 15), captured.getIssueDate());
        assertEquals(LocalDate.of(2024, 1, 30), captured.getDueDate());
        assertEquals("GBP", captured.getCurrency());
        assertEquals(new BigDecimal("100.00"), captured.getTotalAmount());
        assertEquals(new BigDecimal("20.00"), captured.getVatAmount());
        assertEquals(new BigDecimal("120.00"), captured.getDueAmount());
        assertEquals("Seller Co", captured.getSellerName());
        assertEquals("Buyer Inc", captured.getBuyerName());
        assertEquals("oauth", captured.getSource());
        assertEquals(xml, captured.getPeppolXml());
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void save_shouldHandleNullParties() {
        extracted.setSeller(null);
        extracted.setBuyer(null);

        when(repository.save(any(ConvertedInvoice.class))).thenReturn(new ConvertedInvoice());

        service.save(extracted, "<xml/>", "user1");

        ArgumentCaptor<ConvertedInvoice> captor = ArgumentCaptor.forClass(ConvertedInvoice.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getSellerName());
        assertNull(captor.getValue().getBuyerName());
    }

    @Test
    void listForUser_shouldDelegateToRepository() {
        String userId = "12345";
        ConvertedInvoice inv = new ConvertedInvoice();
        inv.setUserId(userId);
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(inv));

        List<ConvertedInvoice> result = service.listForUser(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
    }

    @Test
    void findByIdForUser_shouldReturnInvoiceWhenFound() {
        String userId = "12345";
        Long id = 1L;
        ConvertedInvoice inv = new ConvertedInvoice();
        inv.setId(id);
        inv.setUserId(userId);
        when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(inv));

        Optional<ConvertedInvoice> result = service.findByIdForUser(id, userId);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    void findByIdForUser_shouldReturnEmptyWhenNotFound() {
        when(repository.findByIdAndUserId(999L, "12345")).thenReturn(Optional.empty());

        Optional<ConvertedInvoice> result = service.findByIdForUser(999L, "12345");

        assertTrue(result.isEmpty());
    }
}
