package com.bromleywebworks.peppol.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "converted_invoices")
@Data
public class ConvertedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currency;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private BigDecimal dueAmount;
    private String sellerName;
    private String buyerName;
    private String source;

    @Column(name = "peppol_xml", columnDefinition = "TEXT", nullable = false)
    private String peppolXml;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
