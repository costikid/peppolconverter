package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ConvertRequest;
import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.exception.MissingIdentifierException;
import com.helger.commons.state.ETriState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MappingService {

    private static final String CUSTOMIZATION_ID = "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";
    private static final String PROFILE_ID = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
    private static final String INVOICE_TYPE_CODE = "380";
    private static final String TAX_SCHEME_ID = "VAT";

    private final ConfigService configService;

    public InvoiceType map(ExtractedInvoice extracted, ConvertRequest request) {
        InvoiceType invoice = new InvoiceType();

        // Mandatory Peppol header
        invoice.setCustomizationID(new CustomizationIDType(CUSTOMIZATION_ID));
        invoice.setProfileID(new ProfileIDType(PROFILE_ID));
        invoice.setID(new IDType(extracted.getInvoiceNumber()));
        // Fallback for issue date: use current date if not extracted
        LocalDate issueDate = extracted.getIssueDate() != null ? extracted.getIssueDate() : LocalDate.now();
        invoice.setIssueDate(new IssueDateType(issueDate));
        invoice.setDueDate(new DueDateType(extracted.getDueDate()));
        invoice.setInvoiceTypeCode(new InvoiceTypeCodeType(INVOICE_TYPE_CODE));
        invoice.setDocumentCurrencyCode(new DocumentCurrencyCodeType(extracted.getCurrency()));

        // BuyerReference or OrderReference: at least one must be provided (PEPPOL-EN16931-R003)
        String buyerRef = extracted.getBuyer() != null ? extracted.getBuyer().getName() : "";
        if (buyerRef != null && !buyerRef.isEmpty()) {
            invoice.setBuyerReference(new BuyerReferenceType(buyerRef));
        } else {
            // Fallback to OrderReference with invoice number if BuyerReference is not available
            OrderReferenceType orderRef = new OrderReferenceType();
            orderRef.setID(new IDType(extracted.getInvoiceNumber()));
            invoice.setOrderReference(orderRef);
        }

        // Parties
        invoice.setAccountingSupplierParty(buildSupplierParty(extracted));
        invoice.setAccountingCustomerParty(buildCustomerParty(extracted, request));

        // Payment Means
        invoice.getPaymentMeans().add(buildPaymentMeans(extracted));
        
        // PaymentTerms: only add if it has content (PEPPOL-EN16931-R008)
        PaymentTermsType paymentTerms = buildPaymentTerms(extracted);
        if (paymentTerms.getNote() != null && !paymentTerms.getNote().isEmpty()) {
            invoice.getPaymentTerms().add(paymentTerms);
        }

        // Tax & Monetary Totals
        String vatCategory = resolveVatCategory(extracted);
        BigDecimal effectiveTaxPercent = resolveEffectiveTaxPercent(extracted, vatCategory);
        invoice.getTaxTotal().add(buildTaxTotal(extracted, vatCategory, effectiveTaxPercent));
        invoice.setLegalMonetaryTotal(buildLegalMonetaryTotal(extracted));

        // Invoice Lines
        for (ExtractedInvoice.LineItem line : extracted.getLineItems()) {
            invoice.getInvoiceLine().add(buildInvoiceLine(line, extracted.getCurrency(), vatCategory, effectiveTaxPercent));
        }

        return invoice;
    }

    private SupplierPartyType buildSupplierParty(ExtractedInvoice extracted) {
        SupplierPartyType party = new SupplierPartyType();
        PartyType p = new PartyType();

        String endpointID = configService.getSellerString("endpointID");
        String schemeID = configService.getSellerString("schemeID");
        EndpointIDType eid = new EndpointIDType(endpointID);
        eid.setSchemeID(schemeID);
        p.setEndpointID(eid);

        // PartyName
        String sellerName = configService.getSellerString("name");
        PartyNameType partyName = new PartyNameType();
        partyName.setName(new NameType(sellerName));
        p.addPartyName(partyName);

        // PostalAddress from config (PDF may not have full structured address)
        AddressType addr = new AddressType();
        addr.setStreetName(new StreetNameType(configService.getSellerAddressField("street")));
        addr.setCityName(new CityNameType(configService.getSellerAddressField("city")));
        addr.setPostalZone(new PostalZoneType(configService.getSellerAddressField("postcode")));
        CountryType country = new CountryType();
        country.setIdentificationCode(new IdentificationCodeType(configService.getSellerAddressField("countryCode")));
        addr.setCountry(country);
        p.setPostalAddress(addr);

        // Legal Entity (BR-09: must have CompanyID when no VAT)
        PartyLegalEntityType legal = new PartyLegalEntityType();
        legal.setRegistrationName(new RegistrationNameType(sellerName));
        String companyNumber = configService.getSellerString("companyNumber");
        if (companyNumber != null && !companyNumber.isEmpty()) {
            CompanyIDType companyID = new CompanyIDType(companyNumber);
            companyID.setSchemeID("0002"); // UK Companies House
            legal.setCompanyID(companyID);
        }
        p.addPartyLegalEntity(legal);

        // VAT: use config vatNumber if registered, or extracted seller vatNumber as fallback
        String vatNumber = configService.isSellerVatRegistered()
                ? configService.getSellerString("vatNumber")
                : (extracted.getSeller() != null ? extracted.getSeller().getVatNumber() : null);
        if (vatNumber != null && !vatNumber.isEmpty()) {
            // BR-CO-09: must have ISO 3166-1 alpha-2 country prefix
            if (!vatNumber.matches("^[A-Z]{2}.*")) {
                vatNumber = "GB" + vatNumber;
            }
            PartyTaxSchemeType taxScheme = new PartyTaxSchemeType();
            taxScheme.setCompanyID(new CompanyIDType(vatNumber));
            TaxSchemeType ts = new TaxSchemeType();
            ts.setID(new IDType(TAX_SCHEME_ID));
            taxScheme.setTaxScheme(ts);
            p.addPartyTaxScheme(taxScheme);
        }

        party.setParty(p);
        return party;
    }

    private CustomerPartyType buildCustomerParty(ExtractedInvoice extracted, ConvertRequest request) {
        CustomerPartyType party = new CustomerPartyType();
        PartyType p = new PartyType();

        ExtractedInvoice.Party buyer = extracted.getBuyer();
        if (buyer == null) {
            throw new MissingIdentifierException("Buyer could not be extracted from PDF");
        }

        // ID resolution: 1) request override, 2) config lookup, 3) exception
        String buyerName = buyer.getCompanyName() != null ? buyer.getCompanyName() : buyer.getName();
        
        // Fallback: if buyer name is still null or empty, require endpoint in request
        if (buyerName == null || buyerName.isEmpty()) {
            if (request == null || request.getBuyerEndpoint() == null || request.getBuyerEndpoint().isEmpty()) {
                throw new MissingIdentifierException(
                        "Buyer name not found in PDF and no buyer endpoint provided in request. " +
                        "Provide buyerEndpoint in request metadata or ensure buyer name is extractable.");
            }
            buyerName = "Unknown Buyer";
            log.warn("Buyer name not found, using fallback: {}", buyerName);
        }
        
        String endpointID = resolveBuyerEndpointID(buyerName, request);
        String schemeID = resolveBuyerSchemeID(buyerName, request);

        EndpointIDType eid = new EndpointIDType(endpointID);
        eid.setSchemeID(schemeID);
        p.setEndpointID(eid);

        // PartyName: always set for BR-07 compliance
        PartyNameType partyName = new PartyNameType();
        partyName.setName(new NameType(buyerName));
        p.addPartyName(partyName);

        // PostalAddress from PDF (only set non-empty elements per PEPPOL-EN16931-R008)
        AddressType addr = new AddressType();
        if (buyer.getStreet() != null && !buyer.getStreet().isEmpty()) {
            addr.setStreetName(new StreetNameType(buyer.getStreet()));
        }
        if (buyer.getAdditionalStreet() != null && !buyer.getAdditionalStreet().isEmpty()) {
            addr.setAdditionalStreetName(new AdditionalStreetNameType(buyer.getAdditionalStreet()));
        }
        if (buyer.getCity() != null && !buyer.getCity().isEmpty()) {
            addr.setCityName(new CityNameType(buyer.getCity()));
        }
        if (buyer.getPostcode() != null && !buyer.getPostcode().isEmpty()) {
            addr.setPostalZone(new PostalZoneType(buyer.getPostcode()));
        }
        if (buyer.getCountryCode() != null && !buyer.getCountryCode().isEmpty()) {
            CountryType country = new CountryType();
            country.setIdentificationCode(new IdentificationCodeType(buyer.getCountryCode()));
            addr.setCountry(country);
        }
        p.setPostalAddress(addr);

        // Legal Entity: always set for BR-07 compliance
        PartyLegalEntityType legal = new PartyLegalEntityType();
        legal.setRegistrationName(new RegistrationNameType(buyerName));
        p.addPartyLegalEntity(legal);

        party.setParty(p);
        return party;
    }

    private String resolveBuyerEndpointID(String buyerName, ConvertRequest request) {
        if (request != null && request.getBuyerEndpoint() != null && !request.getBuyerEndpoint().isEmpty()) {
            return request.getBuyerEndpoint();
        }
        var lookup = configService.getBuyerLookup(buyerName);
        if (lookup != null && lookup.has("endpointID")) {
            return lookup.get("endpointID").asText();
        }
        throw new MissingIdentifierException(
                "Missing buyer EndpointID for: " + buyerName +
                ". Provide it in the request metadata or add to config.json buyerLookup.");
    }

    private static final Set<String> VALID_EAS_SCHEMES = Set.of(
            "0002", "0007", "0009", "0037", "0060", "0088", "0096", "0097", "0106", "0130", "0135",
            "0142", "0147", "0151", "0154", "0158", "0170", "0177", "0183", "0184", "0188", "0190",
            "0191", "0192", "0193", "0194", "0195", "0196", "0198", "0199", "0200", "0201", "0202",
            "0203", "0204", "0205", "0208", "0209", "0210", "0211", "0212", "0213", "0215", "0216",
            "0217", "0218", "0219", "0220", "0221", "0225", "0230", "0235", "0240", "9910", "9913",
            "9914", "9915", "9918", "9919", "9920", "9922", "9923", "9924", "9925", "9926", "9927",
            "9928", "9929", "9930", "9931", "9932", "9933", "9934", "9935", "9936", "9937", "9938",
            "9939", "9940", "9941", "9942", "9943", "9944", "9945", "9946", "9947", "9948", "9949",
            "9950", "9951", "9952", "9953", "9957", "9959", "AN", "AQ", "AS", "AU", "EM"
    );

    private String resolveBuyerSchemeID(String buyerName, ConvertRequest request) {
        String schemeID = null;
        if (request != null && request.getBuyerScheme() != null && !request.getBuyerScheme().isEmpty()) {
            schemeID = request.getBuyerScheme();
        } else {
            var lookup = configService.getBuyerLookup(buyerName);
            if (lookup != null && lookup.has("schemeID")) {
                schemeID = lookup.get("schemeID").asText();
            }
        }
        
        if (schemeID == null || schemeID.isEmpty()) {
            throw new MissingIdentifierException(
                    "Missing buyer schemeID for: " + buyerName +
                    ". Provide it in the request metadata or add to config.json buyerLookup.");
        }
        
        if (!VALID_EAS_SCHEMES.contains(schemeID)) {
            throw new MissingIdentifierException(
                    "Invalid buyer schemeID '" + schemeID + "' for: " + buyerName +
                    ". Must be a valid CEF EAS code (e.g., 0002, 0007, 0088, 0130, etc.).");
        }
        
        return schemeID;
    }

    private PaymentMeansType buildPaymentMeans(ExtractedInvoice extracted) {
        PaymentMeansType pm = new PaymentMeansType();
        
        // Map payment method to PEPPOL PaymentMeansCode
        String paymentMethod = extracted.getPaymentMethod();
        String paymentCode = "30"; // Default: Credit transfer
        String paymentName = "Credit transfer";
        
        if ("PAYPAL".equals(paymentMethod)) {
            paymentCode = "58";
            paymentName = "PayPal";
        } else if ("STRIPE".equals(paymentMethod)) {
            paymentCode = "59";
            paymentName = "Stripe";
        } else if ("GOCARDLESS".equals(paymentMethod)) {
            paymentCode = "97";
            paymentName = "Direct debit";
        }
        
        ExtractedInvoice.PaymentDetails pd = extracted.getPaymentDetails();
        
        // Check if we have valid account details for credit transfer (BR-61)
        if (("30".equals(paymentCode) || "58".equals(paymentCode)) && 
            (pd == null || (pd.getAccountNumber() == null || pd.getAccountNumber().isEmpty()))) {
            // No valid account details for credit transfer, use generic payment method
            paymentCode = "31"; // Other payment means
            paymentName = "Other payment means";
            log.warn("No valid payment account details for credit transfer, using payment code 31 (Other)");
        }
        
        pm.setPaymentMeansCode(new PaymentMeansCodeType(paymentCode));
        PaymentMeansCodeType pmc = pm.getPaymentMeansCode();
        pmc.setName(paymentName);
        pm.getPaymentID().add(new PaymentIDType(extracted.getInvoiceNumber()));

        if (pd != null) {
            // Only create account if we have valid account details (BR-50, PEPPOL-EN16931-R008)
            String accountId = pd.getSortCode() != null && pd.getAccountNumber() != null
                    ? pd.getSortCode() + pd.getAccountNumber()
                    : pd.getAccountNumber();
            
            if (accountId != null && !accountId.isEmpty()) {
                FinancialAccountType account = new FinancialAccountType();
                account.setID(new IDType(accountId));
                account.setName(new NameType(configService.getSellerString("name")));

                // Only set branch if we have sort code (PEPPOL-EN16931-R008)
                if (pd.getSortCode() != null && !pd.getSortCode().isEmpty()) {
                    BranchType branch = new BranchType();
                    branch.setID(new IDType(pd.getSortCode()));
                    account.setFinancialInstitutionBranch(branch);
                }
                pm.setPayeeFinancialAccount(account);
            }
        }
        return pm;
    }

    private PaymentTermsType buildPaymentTerms(ExtractedInvoice extracted) {
        PaymentTermsType pt = new PaymentTermsType();
        if (extracted.getDueDate() != null && extracted.getIssueDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(extracted.getIssueDate(), extracted.getDueDate());
            String note = "Payment within " + days + " days";
            if (note != null && !note.isEmpty()) {
                pt.addNote(new NoteType(note));
            }
        }
        return pt;
    }

    private BigDecimal resolveEffectiveTaxPercent(ExtractedInvoice extracted, String vatCategory) {
        BigDecimal taxPercent = extracted.getLineItems().stream()
                .map(ExtractedInvoice.LineItem::getVatRate)
                .filter(r -> r != null && r.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (taxPercent.compareTo(BigDecimal.ZERO) == 0
                && "S".equals(vatCategory)
                && extracted.getVatAmount() != null && extracted.getVatAmount().compareTo(BigDecimal.ZERO) > 0
                && extracted.getTotalAmount() != null && extracted.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            taxPercent = extracted.getVatAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(extracted.getTotalAmount(), 0, RoundingMode.HALF_UP);
            log.info("Computed taxPercent from totals ratio: {}", taxPercent);
        }
        return taxPercent;
    }

    private TaxTotalType buildTaxTotal(ExtractedInvoice extracted, String vatCategory, BigDecimal taxPercent) {
        TaxTotalType taxTotal = new TaxTotalType();
        BigDecimal taxAmount = extracted.getVatAmount() != null ? extracted.getVatAmount() : BigDecimal.ZERO;

        taxTotal.setTaxAmount(new TaxAmountType(taxAmount));
        taxTotal.getTaxAmount().setCurrencyID(extracted.getCurrency());

        TaxSubtotalType subtotal = new TaxSubtotalType();
        BigDecimal taxableAmount = extracted.getTotalAmount() != null ? extracted.getTotalAmount() : BigDecimal.ZERO;
        subtotal.setTaxableAmount(new TaxableAmountType(taxableAmount));
        subtotal.getTaxableAmount().setCurrencyID(extracted.getCurrency());
        subtotal.setTaxAmount(new TaxAmountType(taxAmount));
        subtotal.getTaxAmount().setCurrencyID(extracted.getCurrency());

        TaxCategoryType taxCat = new TaxCategoryType();
        taxCat.setID(new IDType(vatCategory));
        if (configService.isSellerVatRegistered() || taxPercent.compareTo(BigDecimal.ZERO) > 0) {
            taxCat.setPercent(new PercentType(taxPercent));
        }
        if (!"S".equals(vatCategory)) {
            String reason;
            if ("O".equals(vatCategory)) {
                reason = "Not VAT registered";
            } else if ("E".equals(vatCategory)) {
                reason = "Exempt";
            } else if ("K".equals(vatCategory)) {
                reason = "Reverse charge";
            } else if ("Z".equals(vatCategory)) {
                reason = "Zero rated";
            } else {
                reason = "Zero rated";
            }
            taxCat.getTaxExemptionReason().add(new TaxExemptionReasonType(reason));
        }
        // Add TaxExemptionReasonCode for category E (intra-EU supplies)
        if ("E".equals(vatCategory)) {
            taxCat.setTaxExemptionReasonCode(new TaxExemptionReasonCodeType("VATEX-EU-F"));
        }
        TaxSchemeType scheme = new TaxSchemeType();
        scheme.setID(new IDType(TAX_SCHEME_ID));
        taxCat.setTaxScheme(scheme);
        subtotal.setTaxCategory(taxCat);

        taxTotal.getTaxSubtotal().add(subtotal);
        return taxTotal;
    }

    private MonetaryTotalType buildLegalMonetaryTotal(ExtractedInvoice extracted) {
        MonetaryTotalType total = new MonetaryTotalType();
        String currency = extracted.getCurrency();
        BigDecimal lineExtension = extracted.getTotalAmount() != null ? extracted.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal vatAmount = extracted.getVatAmount() != null ? extracted.getVatAmount() : BigDecimal.ZERO;
        BigDecimal taxInclusive = lineExtension.add(vatAmount);

        total.setLineExtensionAmount(new LineExtensionAmountType(lineExtension));
        total.getLineExtensionAmount().setCurrencyID(currency);
        total.setTaxExclusiveAmount(new TaxExclusiveAmountType(lineExtension));
        total.getTaxExclusiveAmount().setCurrencyID(currency);
        total.setTaxInclusiveAmount(new TaxInclusiveAmountType(taxInclusive));
        total.getTaxInclusiveAmount().setCurrencyID(currency);

        // If there's a paid amount, set PrepaidAmount
        BigDecimal paidAmount = extracted.getPaidAmount();
        if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            total.setPrepaidAmount(new PrepaidAmountType(paidAmount));
            total.getPrepaidAmount().setCurrencyID(currency);
        }

        BigDecimal payable = extracted.getDueAmount() != null ? extracted.getDueAmount() : taxInclusive;
        total.setPayableAmount(new PayableAmountType(payable));
        total.getPayableAmount().setCurrencyID(currency);
        return total;
    }

    private InvoiceLineType buildInvoiceLine(ExtractedInvoice.LineItem line, String currency, String vatCategory, BigDecimal effectiveTaxPercent) {
        InvoiceLineType il = new InvoiceLineType();
        il.setID(new IDType(String.valueOf(line.getLineNumber())));
        InvoicedQuantityType qty = new InvoicedQuantityType(line.getQuantity());
        qty.setUnitCode(line.getUnitCode());
        il.setInvoicedQuantity(qty);
        il.setLineExtensionAmount(new LineExtensionAmountType(line.getLineTotal()));
        il.getLineExtensionAmount().setCurrencyID(currency);

        ItemType item = new ItemType();
        item.setName(new NameType(line.getDescription()));
        item.getDescription().add(new DescriptionType(line.getDescription()));

        // VAT category on item level — use effectiveTaxPercent so it matches TaxSubtotal (BR-S-05, BR-S-08)
        BigDecimal linePercent = line.getVatRate() != null && line.getVatRate().compareTo(BigDecimal.ZERO) > 0
                ? line.getVatRate() : effectiveTaxPercent;
        TaxCategoryType taxCat = new TaxCategoryType();
        taxCat.setID(new IDType(vatCategory));
        if (linePercent.compareTo(BigDecimal.ZERO) > 0) {
            taxCat.setPercent(new PercentType(linePercent));
        }
        TaxSchemeType scheme = new TaxSchemeType();
        scheme.setID(new IDType(TAX_SCHEME_ID));
        taxCat.setTaxScheme(scheme);
        item.getClassifiedTaxCategory().add(taxCat);

        il.setItem(item);

        PriceType price = new PriceType();
        price.setPriceAmount(new PriceAmountType(line.getUnitPrice()));
        price.getPriceAmount().setCurrencyID(currency);
        il.setPrice(price);

        return il;
    }

    private String resolveVatCategory(ExtractedInvoice extracted) {
        // EC Status takes priority for VAT category determination
        if (extracted.getEcStatus() != null) {
            String ecStatus = extracted.getEcStatus().toLowerCase();
            if (ecStatus.contains("ec goods")) {
                return "E"; // Intra-EU supply of goods
            } else if (ecStatus.contains("ec services")) {
                return "E"; // Intra-EU supply of services
            } else if (ecStatus.contains("reverse charge")) {
                return "K"; // Reverse charge
            }
        }

        // Explicit VAT rate from line items takes priority over config (e.g. QuickBooks 20%)
        for (ExtractedInvoice.LineItem line : extracted.getLineItems()) {
            if (line.getVatRate() != null && line.getVatRate().compareTo(BigDecimal.ZERO) > 0) {
                return vatRateToPeppolCategory(line.getVatRate());
            }
        }

        // Invoice-level VAT amount also overrides config
        if (extracted.getVatAmount() != null && extracted.getVatAmount().compareTo(BigDecimal.ZERO) > 0) {
            return "S";
        }

        // No explicit VAT in the invoice — respect config registration flag
        if (!configService.isSellerVatRegistered()) {
            // If the PDF contains a seller VAT number, seller is VAT registered → zero-rated, not "O"
            boolean extractedVatRegistered = extracted.getSeller() != null
                    && extracted.getSeller().getVatNumber() != null
                    && !extracted.getSeller().getVatNumber().isEmpty();
            return extractedVatRegistered ? "Z" : "O";
        }

        // Fallback to config vatMappings (description-based)
        Map<String, String> vatMappings = configService.getVatMappings();
        for (ExtractedInvoice.LineItem line : extracted.getLineItems()) {
            String desc = line.getDescription().toLowerCase();
            for (Map.Entry<String, String> entry : vatMappings.entrySet()) {
                if (desc.contains(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
        }

        // Default: if no VAT amount, assume Z; otherwise S
        BigDecimal totalVat = BigDecimal.ZERO;
        return totalVat.compareTo(BigDecimal.ZERO) == 0 ? "Z" : "S";
    }

    private String vatRateToPeppolCategory(BigDecimal vatRate) {
        int rate = vatRate.intValue();
        if (rate == 20) return "S";
        if (rate == 5)  return "R";
        return "Z";
    }
}
