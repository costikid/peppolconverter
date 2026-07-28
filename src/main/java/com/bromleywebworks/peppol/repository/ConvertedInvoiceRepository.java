package com.bromleywebworks.peppol.repository;

import com.bromleywebworks.peppol.entity.ConvertedInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConvertedInvoiceRepository extends JpaRepository<ConvertedInvoice, Long> {

    List<ConvertedInvoice> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<ConvertedInvoice> findByIdAndUserId(Long id, String userId);
}
