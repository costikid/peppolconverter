package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.*;
import org.springframework.stereotype.Service;

@Service
public class PeppolInvoiceFormMapper {

    public ExtractedInvoice mapToExtractedInvoice(PeppolInvoiceForm form) {
        ExtractedInvoice invoice = new ExtractedInvoice();
        
        // Invoice metadata
        invoice.setInvoiceNumber(form.getInvoiceId());
        invoice.setIssueDate(form.getIssueDate());
        invoice.setDueDate(form.getDueDate());
        invoice.setCurrency(form.getCurrency());
        
        // Seller party
        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        seller.setName(form.getSellerName());
        seller.setVatNumber(form.getSellerVatNumber());
        if (form.getSellerAddress() != null) {
            seller.setStreet(form.getSellerAddress().getStreet());
            seller.setAdditionalStreet(form.getSellerAddress().getAdditionalStreet());
            seller.setCity(form.getSellerAddress().getCity());
            seller.setPostcode(form.getSellerAddress().getPostcode());
            seller.setCountryCode(form.getSellerAddress().getCountryCode());
        }
        invoice.setSeller(seller);
        
        // Buyer party
        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        buyer.setName(form.getBuyerName());
        buyer.setCompanyName(form.getBuyerName());
        if (form.getBuyerAddress() != null) {
            buyer.setStreet(form.getBuyerAddress().getStreet());
            buyer.setAdditionalStreet(form.getBuyerAddress().getAdditionalStreet());
            buyer.setCity(form.getBuyerAddress().getCity());
            buyer.setPostcode(form.getBuyerAddress().getPostcode());
            buyer.setCountryCode(form.getBuyerAddress().getCountryCode());
        }
        invoice.setBuyer(buyer);
        
        // Line items
        for (LineItemForm lineItemForm : form.getLineItems()) {
            ExtractedInvoice.LineItem lineItem = new ExtractedInvoice.LineItem();
            lineItem.setLineNumber(lineItemForm.getLineNumber());
            lineItem.setDescription(lineItemForm.getDescription());
            lineItem.setQuantity(lineItemForm.getQuantity());
            lineItem.setUnitCode(lineItemForm.getUnitCode());
            lineItem.setUnitPrice(lineItemForm.getUnitPrice());
            lineItem.setLineTotal(lineItemForm.getLineTotal());
            lineItem.setVatRate(lineItemForm.getVatRate());
            invoice.getLineItems().add(lineItem);
        }
        
        // Payment details
        ExtractedInvoice.PaymentDetails paymentDetails = new ExtractedInvoice.PaymentDetails();
        paymentDetails.setBankName(form.getBankName());
        paymentDetails.setSortCode(form.getSortCode());
        paymentDetails.setAccountNumber(form.getAccountNumber());
        paymentDetails.setPaymentReference(form.getPaymentReference());
        invoice.setPaymentDetails(paymentDetails);
        
        // Totals
        invoice.setTotalAmount(form.getSubtotal());
        invoice.setVatAmount(form.getVatAmount());
        invoice.setDueAmount(form.getTotalAmount());
        
        return invoice;
    }

    public PeppolInvoiceForm mapFromExtractedInvoice(ExtractedInvoice extracted, ConvertRequest request) {
        PeppolInvoiceForm form = new PeppolInvoiceForm();
        
        // Invoice metadata
        form.setInvoiceId(extracted.getInvoiceNumber());
        if (extracted.getIssueDate() != null) {
            form.setIssueDate(extracted.getIssueDate());
        }
        if (extracted.getDueDate() != null) {
            form.setDueDate(extracted.getDueDate());
        }
        form.setCurrency(extracted.getCurrency());
        
        // Seller party
        AddressForm sellerAddress = new AddressForm();
        if (extracted.getSeller() != null) {
            form.setSellerName(extracted.getSeller().getName());
            form.setSellerVatNumber(extracted.getSeller().getVatNumber());
            sellerAddress.setStreet(extracted.getSeller().getStreet());
            sellerAddress.setAdditionalStreet(extracted.getSeller().getAdditionalStreet());
            sellerAddress.setCity(extracted.getSeller().getCity());
            sellerAddress.setPostcode(extracted.getSeller().getPostcode());
            sellerAddress.setCountryCode(extracted.getSeller().getCountryCode());
        }
        form.setSellerAddress(sellerAddress);
        
        // Buyer party
        AddressForm buyerAddress = new AddressForm();
        if (extracted.getBuyer() != null) {
            form.setBuyerName(extracted.getBuyer().getName());
            form.setBuyerEndpointId(request != null ? request.getBuyerEndpoint() : null);
            form.setBuyerSchemeId(request != null ? request.getBuyerScheme() : null);
            buyerAddress.setStreet(extracted.getBuyer().getStreet());
            buyerAddress.setAdditionalStreet(extracted.getBuyer().getAdditionalStreet());
            buyerAddress.setCity(extracted.getBuyer().getCity());
            buyerAddress.setPostcode(extracted.getBuyer().getPostcode());
            buyerAddress.setCountryCode(extracted.getBuyer().getCountryCode());
        }
        form.setBuyerAddress(buyerAddress);
        
        // Line items
        for (ExtractedInvoice.LineItem line : extracted.getLineItems()) {
            LineItemForm lineItemForm = new LineItemForm();
            lineItemForm.setLineNumber(line.getLineNumber());
            lineItemForm.setDescription(line.getDescription());
            lineItemForm.setQuantity(line.getQuantity());
            lineItemForm.setUnitCode(line.getUnitCode());
            lineItemForm.setUnitPrice(line.getUnitPrice());
            lineItemForm.setLineTotal(line.getLineTotal());
            lineItemForm.setVatRate(line.getVatRate());
            form.getLineItems().add(lineItemForm);
        }
        
        // Payment details
        if (extracted.getPaymentDetails() != null) {
            form.setBankName(extracted.getPaymentDetails().getBankName());
            form.setSortCode(extracted.getPaymentDetails().getSortCode());
            form.setAccountNumber(extracted.getPaymentDetails().getAccountNumber());
            form.setPaymentReference(extracted.getPaymentDetails().getPaymentReference());
        }
        
        // Totals
        form.setSubtotal(extracted.getTotalAmount());
        form.setVatAmount(extracted.getVatAmount());
        form.setTotalAmount(extracted.getDueAmount());
        
        return form;
    }
}
