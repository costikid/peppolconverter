package com.bromleywebworks.peppol.service;

import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ValidationService {

    public List<String> validate(InvoiceType invoice) {
        List<String> errors = new ArrayList<>();

        // Basic mandatory field checks (BR-01, BR-02, etc.)
        if (invoice.getID() == null || invoice.getID().getValue() == null || invoice.getID().getValue().isEmpty()) {
            errors.add("BR-01: Invoice ID is missing");
        }
        if (invoice.getIssueDate() == null) {
            errors.add("BR-02: Invoice issue date is missing");
        }
        if (invoice.getInvoiceTypeCode() == null) {
            errors.add("BR-03: Invoice type code is missing");
        }
        if (invoice.getDocumentCurrencyCode() == null) {
            errors.add("BR-04: Document currency code is missing");
        }
        if (invoice.getCustomizationID() == null) {
            errors.add("BR-05: Customization ID is missing");
        }
        if (invoice.getProfileID() == null) {
            errors.add("BR-06: Profile ID is missing");
        }

        // Seller party checks (BR-09: at least one ID must be present)
        if (invoice.getAccountingSupplierParty() == null ||
            invoice.getAccountingSupplierParty().getParty() == null) {
            errors.add("BR-09: Seller party is missing");
        } else {
            var party = invoice.getAccountingSupplierParty().getParty();
            boolean hasID = (party.getEndpointID() != null && party.getEndpointID().getValue() != null) ||
                          (party.getPartyIdentification() != null && !party.getPartyIdentification().isEmpty()) ||
                          (party.getPartyTaxScheme() != null && !party.getPartyTaxScheme().isEmpty()) ||
                          (party.getPartyLegalEntity() != null && !party.getPartyLegalEntity().isEmpty());
            if (!hasID) {
                errors.add("BR-09: Seller must have at least one of EndpointID, PartyIdentification, PartyTaxScheme, or PartyLegalEntity");
            }
        }

        // Buyer party checks (BR-10)
        if (invoice.getAccountingCustomerParty() == null ||
            invoice.getAccountingCustomerParty().getParty() == null) {
            errors.add("BR-10: Buyer party is missing");
        }

        // Tax total checks
        if (invoice.getTaxTotal() == null || invoice.getTaxTotal().isEmpty()) {
            errors.add("BR-16: Tax total is missing");
        }

        // Legal monetary total checks
        if (invoice.getLegalMonetaryTotal() == null) {
            errors.add("BR-13: Legal monetary total is missing");
        }

        // Invoice line checks
        if (invoice.getInvoiceLine() == null || invoice.getInvoiceLine().isEmpty()) {
            errors.add("BR-21: At least one invoice line is required");
        }

        // VAT Category O/E/Z must have TaxExemptionReason
        if (invoice.getTaxTotal() != null) {
            invoice.getTaxTotal().forEach(taxTotal -> {
                if (taxTotal.getTaxSubtotal() != null) {
                    taxTotal.getTaxSubtotal().forEach(subtotal -> {
                        if (subtotal.getTaxCategory() != null) {
                            String catID = subtotal.getTaxCategory().getID() != null ?
                                    subtotal.getTaxCategory().getID().getValue() : "";
                            if (catID.matches("[EOZ]")) {
                                boolean hasReason = subtotal.getTaxCategory().getTaxExemptionReason() != null &&
                                        !subtotal.getTaxCategory().getTaxExemptionReason().isEmpty();
                                boolean hasReasonCode = subtotal.getTaxCategory().getTaxExemptionReasonCode() != null &&
                                        subtotal.getTaxCategory().getTaxExemptionReasonCode().getValue() != null &&
                                        !subtotal.getTaxCategory().getTaxExemptionReasonCode().getValue().isEmpty();
                                if (!hasReason && !hasReasonCode) {
                                    errors.add("BR-CL-01: Tax Category " + catID + " requires TaxExemptionReason or TaxExemptionReasonCode");
                                }
                            }
                        }
                    });
                }
            });
        }

        if (!errors.isEmpty()) {
            log.warn("Validation failed with {} errors", errors.size());
        }
        return errors;
    }
}
