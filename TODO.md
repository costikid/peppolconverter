# FreeAgent-to-Peppol Converter – Execution Plan

## Phase 1: Environment (Maven + Docker skeleton)
- [x] `pom.xml` with Spring Boot 2.7.18, PDFBox, ph-ubl21, Lombok
- [x] `src/main/java/.../PeppolConverterApplication.java`
- [x] `src/main/resources/application.properties`
- [x] `config.json` (ground-truth seller + buyer lookup + keyword mappings)
- [x] `Dockerfile` + `docker-compose.yml`

## Phase 2: ExtractionService
- [x] `ExtractedInvoice` DTO (invoice metadata, seller, buyer, line items, payment details)
- [x] `ExtractionService` using PDFBox coordinate-based text extraction for FreeAgent layout
- [ ] Unit test: feed `***REMOVED***` → assert 100% field accuracy

## Phase 3: MappingService
- [x] `MappingService` that maps `ExtractedInvoice` + config + optional metadata → `InvoiceType`
- [x] ID resolution hierarchy: 1) request override, 2) config lookup, 3) `MissingIdentifierException`
- [x] VAT logic: non-registered → Category O + `TaxExemptionReason`; registered → keyword mapping → S/E/Z
- [x] `PayeeFinancialAccount` from PDF payment details

## Phase 4: ValidationService
- [x] `ValidationService` with basic Peppol rule checks (BR-01, BR-02, BR-09, BR-10, BR-13, BR-16, BR-21, BR-CL-01)
- [x] Return `400 Bad Request` with specific Peppol rule ID on validation failure
- **Note**: PHIVE artifact `phive-rules-peppol` not available in Maven Central. Implemented manual validation covering key Peppol BIS Billing 3.0 rules.

## Phase 5: API & Docker
- [x] `POST /convert` multipart endpoint (`file` + optional `metadata` JSON)
- [x] Global exception handler (`MissingIdentifierException` → 400)
- [x] Docker multi-stage build + `docker-compose.yml`
- [ ] Run application and test with actual PDF
