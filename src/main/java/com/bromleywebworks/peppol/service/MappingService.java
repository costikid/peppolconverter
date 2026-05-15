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
        invoice.setIssueDate(new IssueDateType(extracted.getIssueDate()));
        invoice.setDueDate(new DueDateType(extracted.getDueDate()));
        invoice.setInvoiceTypeCode(new InvoiceTypeCodeType(INVOICE_TYPE_CODE));
        invoice.setDocumentCurrencyCode(new DocumentCurrencyCodeType(extracted.getCurrency()));

        String buyerRef = extracted.getBuyer() != null ? extracted.getBuyer().getName() : "";
        invoice.setBuyerReference(new BuyerReferenceType(buyerRef));

        // Parties
        invoice.setAccountingSupplierParty(buildSupplierParty(extracted));
        invoice.setAccountingCustomerParty(buildCustomerParty(extracted, request));

        // Payment Means
        invoice.getPaymentMeans().add(buildPaymentMeans(extracted));
        invoice.getPaymentTerms().add(buildPaymentTerms(extracted));

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
        String endpointID = resolveBuyerEndpointID(buyerName, request);
        String schemeID = resolveBuyerSchemeID(buyerName, request);

        EndpointIDType eid = new EndpointIDType(endpointID);
        eid.setSchemeID(schemeID);
        p.setEndpointID(eid);

        // PartyName
        PartyNameType partyName = new PartyNameType();
        partyName.setName(new NameType(buyerName));
        p.addPartyName(partyName);

        // PostalAddress from PDF
        AddressType addr = new AddressType();
        addr.setStreetName(new StreetNameType(buyer.getStreet()));
        if (buyer.getAdditionalStreet() != null) {
            addr.setAdditionalStreetName(new AdditionalStreetNameType(buyer.getAdditionalStreet()));
        }
        addr.setCityName(new CityNameType(buyer.getCity()));
        addr.setPostalZone(new PostalZoneType(buyer.getPostcode()));
        CountryType country = new CountryType();
        country.setIdentificationCode(new IdentificationCodeType(buyer.getCountryCode()));
        addr.setCountry(country);

        // Override with request values if provided
        if (request != null && request.getBuyerStreet() != null && !request.getBuyerStreet().isEmpty()) {
            addr.setStreetName(new StreetNameType(request.getBuyerStreet()));
        }
        if (request != null && request.getBuyerCity() != null && !request.getBuyerCity().isEmpty()) {
            addr.setCityName(new CityNameType(request.getBuyerCity()));
        }
        if (request != null && request.getBuyerPostcode() != null && !request.getBuyerPostcode().isEmpty()) {
            addr.setPostalZone(new PostalZoneType(request.getBuyerPostcode()));
        }
        if (request != null && request.getBuyerCountryCode() != null && !request.getBuyerCountryCode().isEmpty()) {
            country.setIdentificationCode(new IdentificationCodeType(request.getBuyerCountryCode()));
        }

        p.setPostalAddress(addr);

        // Legal Entity
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

    private String resolveBuyerSchemeID(String buyerName, ConvertRequest request) {
        if (request != null && request.getBuyerScheme() != null && !request.getBuyerScheme().isEmpty()) {
            return request.getBuyerScheme();
        }
        var lookup = configService.getBuyerLookup(buyerName);
        if (lookup != null && lookup.has("schemeID")) {
            return lookup.get("schemeID").asText();
        }
        log.warn("No buyer schemeID found for: {}. Using default scheme '0192'", buyerName);
        return "0192";
    }

    private PaymentMeansType buildPaymentMeans(ExtractedInvoice extracted) {
        PaymentMeansType pm = new PaymentMeansType();
        pm.setPaymentMeansCode(new PaymentMeansCodeType("30"));
        PaymentMeansCodeType pmc = pm.getPaymentMeansCode();
        pmc.setName("Credit transfer");
        pm.getPaymentID().add(new PaymentIDType(extracted.getInvoiceNumber()));

        ExtractedInvoice.PaymentDetails pd = extracted.getPaymentDetails();
        if (pd != null) {
            FinancialAccountType account = new FinancialAccountType();
            // UK sort code + account number as pseudo-IBAN or raw account number
            String accountId = pd.getSortCode() != null && pd.getAccountNumber() != null
                    ? pd.getSortCode() + pd.getAccountNumber()
                    : pd.getAccountNumber();
            account.setID(new IDType(accountId));
            account.setName(new NameType(configService.getSellerString("name")));

            BranchType branch = new BranchType();
            if (pd.getSortCode() != null) {
                branch.setID(new IDType(pd.getSortCode()));
            }
            account.setFinancialInstitutionBranch(branch);
            pm.setPayeeFinancialAccount(account);
        }
        return pm;
    }

    private PaymentTermsType buildPaymentTerms(ExtractedInvoice extracted) {
        PaymentTermsType pt = new PaymentTermsType();
        if (extracted.getDueDate() != null && extracted.getIssueDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(extracted.getIssueDate(), extracted.getDueDate());
            pt.addNote(new NoteType("Payment within " + days + " days"));
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
            String reason = "O".equals(vatCategory) ? "Not VAT registered" : "Zero rated";
            taxCat.getTaxExemptionReason().add(new TaxExemptionReasonType(reason));
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

        BigDecimal payable = extracted.getDueAmount() != null ? extracted.getDueAmount() : lineExtension;
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
