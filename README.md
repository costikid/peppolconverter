

### **Master Project Spec: FreeAgent-to-Peppol-BIS3-API**

#### **1. System Objective**

Develop a production-grade Java 17 Spring Boot REST API that transforms FreeAgent PDF invoices into Peppol BIS Billing 3.0 UBL XML. Accuracy is the primary KPI. The system must pass all Schematron validation rules defined in the Peppol BIS 3.0 standard.

#### **2. Persistence & Memory (Anti-Drift Protocol)**

* **Progress Tracking:** Maintain a `TODO.md` in the root directory. After every successful step, update the file.
* **Strict Typing:** Use the `ph-ubl21` library objects for all internal data handling to prevent structural "hallucinations" in the XML.
* **Documentation-Driven:** Reference the provided `base-example.xml` and Peppol documentation links for every mapping decision.

#### **3. Expanded Technical Stack**

* **Framework:** Spring Boot 3.x (Web, DevTools).
* **PDF Engine:** Apache PDFBox (Coordinates-based extraction for FreeAgent layouts).
* **UBL Engine:** `com.helger.ubl:ph-ubl21` (UBL 2.1 implementation).
* **Validation:** `com.helger.phive:phive-rules-peppol` (Official Peppol validation).
* **Container:** Docker (Multi-stage build) + Docker Compose.

#### **4. Modular Service Architecture**

1. **`ConfigService`**: Loads `config.json`. Provides fallback `schemeID`, `EndpointID`, and `VATStatus`.
2. **`ExtractionService`**:
* Target: FreeAgent Standard Template.
* Logic: Extract `Invoice ID`, `Issue Date`, `Due Date`, `Currency`.
* Line Items: Parse table rows for `Quantity`, `Description`, `Unit Price`.


3. **`MappingService`**:
* Maps extracted data into a `oasis.names.specification.ubl.schema.xsd.invoice_2.InvoiceType` object.
* **VAT Rule:** If `config.seller.isVatRegistered` is `false`, apply `TaxCategory: O` and `TaxExemptionReason: 'Not VAT registered'`.


4. **`ValidationService`**:
* Runs the generated XML through the **PHIVE** engine.
* If any "FATAL" or "ERROR" level rules are triggered, the API must return a `400 Bad Request` with the specific Peppol rule violation.



#### **5. The "Ground Truth" Files**

**File: `pom.xml` (Optimized)**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>2.0.27</version>
    </dependency>
    <dependency>
        <groupId>com.helger.ubl</groupId>
        <artifactId>ph-ubl21</artifactId>
        <version>6.7.0</version>
    </dependency>
    <dependency>
        <groupId>com.helger.phive</groupId>
        <artifactId>phive-rules-peppol</artifactId>
        <version>3.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>

```

**File: `config.json` (Required Context)**

```json
{
  "seller": {
    "name": "***REMOVED***",
    "endpointID": "7300010000001",
    "schemeID": "0088",
    "isVatRegistered": false,
    "address": {
      "street": "***REMOVED***",
      "city": "***REMOVED***",
      "postcode": "***REMOVED***",
      "countryCode": "GB"
    }
  }
}

```

#### **6. Phase-by-Phase Execution Plan for Windsurf**

* **Phase 1: Environment.** Setup Maven, Dockerfile, and `TODO.md`.
* **Phase 2: Extraction.** Implement `ExtractionService`. Test it against the provided PDF. Output a JSON representation of the PDF data for verification.
* **Phase 3: Core Mapping.** Implement `MappingService`. Ensure it handles the `VAT Category O` logic correctly for non-registered businesses.
* **Phase 4: Validation.** Integrate the PHIVE engine. Ensure the output XML matches the structure of `base-example.xml`.
* **Phase 5: API & Docker.** Create the `POST /convert` endpoint and finalize the Docker container.

---

### **Instruction for the Agent to Minimize Drifting:**

> "Before writing any code, analyze the attached `***REMOVED***` text content. Map the text locations to the UBL 2.1 elements found in `base-example.xml`. Update `TODO.md` after each service is completed. Do not proceed to the Mapping phase until the Extraction phase passes a unit test with 100% data accuracy."