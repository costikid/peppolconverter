# FreeAgent to Peppol Converter

A Spring Boot web application that converts accounting invoice PDFs (primarily from **FreeAgent**) into **Peppol BIS Billing 3.0** compliant UBL XML. It provides both a browser-based UI and a REST API, with optional OAuth2 integration for pulling invoices directly from FreeAgent.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running Locally](#running-locally)
  - [Running with Docker](#running-with-docker)
  - [Configuration](#configuration)
- [Usage](#usage)
  - [Web UI](#web-ui)
  - [REST API](#rest-api)
- [Peppol Compliance](#peppol-compliance)
- [Rate Limiting & Usage Tracking](#rate-limiting--usage-tracking)
- [Security](#security)
- [Environment Variables](#environment-variables)
- [License](#license)

---

## Overview

[Peppol](https://peppol.eu/) (Pan-European Public Procurement On-Line) is the European standard for electronic invoicing. Many small businesses use cloud accounting platforms like **FreeAgent** or **QuickBooks**, but these platforms do not natively export invoices in the Peppol format required by some buyers or government entities.

This tool bridges that gap by:

1. Accepting a PDF invoice (uploaded via browser or API)
2. Extracting structured data (seller, buyer, line items, totals, VAT) using PDF text parsing
3. Mapping the extracted data to a Peppol BIS Billing 3.0 UBL XML structure
4. Returning the validated XML for download or direct submission

---

## Features

- **PDF to Peppol conversion** — Extracts invoice data from FreeAgent (and QuickBooks) PDFs and generates Peppol BIS Billing 3.0 XML
- **Web UI** — Clean, responsive upload interface built with Thymeleaf and Bootstrap 5
- **REST API** — Programmatic conversion endpoint for integration into other workflows
- **OAuth2 integration** — Connect your FreeAgent account to pull invoices without manual PDF uploads
- **Multi-accounting-system support** — Extensible strategy pattern for adding new PDF parsers (FreeAgent and QuickBooks supported today)
- **VAT handling** — Supports multiple VAT categories (Standard `S`, Zero-rated `Z`, Exempt `E`, Out-of-scope `O`, etc.) with configurable mappings
- **XSD validation** — Validates generated XML against Peppol BIS Billing 3.0 schemas before returning it
- **Rate limiting** — Per-IP request throttling via Bucket4j + Caffeine to prevent abuse
- **Usage tracking** — Lightweight in-memory (or Redis-backed) usage statistics
- **Docker-ready** — Multi-stage Dockerfile for easy deployment; health checks included
- **Railway / PaaS compatible** — `PORT` environment variable support and health check endpoint

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 2.7 (Java 17) |
| Templating | Thymeleaf |
| UI | Bootstrap 5 |
| PDF Parsing | Apache PDFBox 2.0.27 |
| UBL XML Generation | ph-ubl21 (Helger UBL 2.1) |
| JAXB Runtime | Eclipse GlassFish JAXB |
| Markdown Blog | CommonMark |
| Rate Limiting | Bucket4j + Caffeine |
| Caching / Session | Spring Session (Redis in prod) |
| Build | Maven |
| Container | Eclipse Temurin 17 JRE |

---

## Architecture

The codebase follows a typical layered Spring Boot structure:

```
com.bromleywebworks.peppol
├── config          # Configuration properties and Spring configs
├── controller      # Web controllers (Thymeleaf + REST API)
├── dto             # Data transfer objects (forms, requests, extracted data)
├── exception       # Global exception handling
├── filter          # Servlet filters (rate limiting)
├── service         # Business logic
│   ├── strategy    # Extraction strategies (FreeAgent, QuickBooks)
│   └── usage       # Usage tracking & cookie services
└── resources
    ├── templates   # Thymeleaf HTML templates
    └── blog        # Markdown blog posts
```

### Key Components

- **`ExtractionService`** — Delegates PDF parsing to the correct `ExtractionStrategy` based on the converter type
- **`FreeAgentExtractionStrategy`** — Parses FreeAgent PDF text to extract seller, buyer, line items, VAT, and totals. Handles comma-separated amounts and multiple VAT columns
- **`QuickBooksExtractionStrategy`** — Parses QuickBooks PDF layouts
- **`MappingService`** — Maps `ExtractedInvoice` → Peppol `InvoiceType` using ph-ubl21
- **`PeppolInvoiceFormMapper`** — Bridges web form fields into the mapping pipeline
- **`ValidationService`** — Runs XSD validation on the generated UBL XML
- **`ConfigService`** — Loads seller details, buyer lookup table, unit mappings, and VAT category mappings from `config.json`
- **`BlogService`** — Renders Markdown blog posts with YAML front-matter

---

## Getting Started

### Prerequisites

- Java 17
- Maven 3.8+
- (Optional) Docker & Docker Compose
- (Optional) Redis — for production usage tracking and sessions

### Running Locally

```bash
# 1. Clone the repo
git clone <repo-url>
cd peppol-converter

# 2. Copy the example config and edit it
cp config.json.example config.json
# Edit config.json with your seller details, buyer lookup table, etc.

# 3. Run with the local Spring profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The application will start on `http://localhost:8080`.

### Running with Docker

```bash
# Build and run
docker-compose up --build
```

Or manually:

```bash
docker build -t peppol-converter .
docker run -p 8080:8080 -v $(pwd)/config.json:/app/config.json:ro peppol-converter
```

### Configuration

Create a `config.json` in the project root (or mount one in Docker). See `config.json.example` for the structure:

```json
{
  "seller": {
    "name": "Your Company Name",
    "companyNumber": "12345678",
    "endpointID": "7300010000001",
    "schemeID": "0088",
    "isVatRegistered": false,
    "defaultVatCategory": "O",
    "address": {
      "street": "123 Street Name",
      "city": "City Name",
      "postcode": "POST CODE",
      "countryCode": "GB"
    },
    "contact": {
      "name": "Contact Name",
      "telephone": "01234567890",
      "email": "contact@example.com"
    },
    "bankDetails": {
      "bankName": "Bank Name",
      "sortCode": "000000",
      "accountNumber": "00000000"
    }
  },
  "mappings": {
    "units": {
      "hour": "HUR",
      "day": "DAY",
      "each": "EA",
      "unit": "EA",
      "service": "EA"
    },
    "vat": {
      "insurance": "E",
      "education": "E",
      "health": "E",
      "book": "Z",
      "children": "Z",
      "food": "Z"
    }
  },
  "buyerLookup": {
    "Buyer Company Name": {
      "endpointID": "9948:GB123456789",
      "schemeID": "9948"
    }
  }
}
```

| Section | Purpose |
|---------|---------|
| `seller` | Your business details printed on every outgoing Peppol invoice |
| `mappings.units` | Normalises unit strings (e.g. `"hour"` → `"HUR"`) for Peppol |
| `mappings.vat` | Maps line-item keywords to VAT category codes (`S`, `Z`, `E`, `O`) |
| `buyerLookup` | Pre-populates buyer Peppol endpoint IDs by company name (matched from PDF text) |

---

## Usage

### Web UI

1. Open `http://localhost:8080`
2. Navigate to **FreeAgent to Peppol**
3. Upload a FreeAgent PDF invoice
4. Fill in any missing buyer endpoint details
5. Download the generated Peppol XML

### REST API

**Endpoint:** `POST /api/convert`

**Content-Type:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | The PDF invoice |
| `buyerEndpoint` | String | No | Buyer Peppol endpoint ID |
| `buyerScheme` | String | No | Buyer scheme ID (default `9948`) |
| `dueDate` | String | No | Invoice due date (`YYYY-MM-DD`) |
| `currency` | String | No | Currency code (default `GBP`) |
| `vatCategory` | String | No | VAT category override (`S`, `Z`, `E`, `O`) |

**Example response:**

```json
{
  "status": "success",
  "xml": "<?xml version=\"1.0\"...",
  "validationErrors": []
}
```

---

## Peppol Compliance

The generated XML conforms to:

- **UBL 2.1 Invoice** schema
- **Peppol BIS Billing 3.0** specification
- Customization ID: `urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0`
- Profile ID: `urn:fdc:peppol.eu:2017:poacc:billing:01:1.0`

Key fields populated:

- `cbc:ID` — Invoice number
- `cbc:IssueDate` / `cbc:DueDate`
- `cac:AccountingSupplierParty` — Seller name, address, company ID, endpoint
- `cac:AccountingCustomerParty` — Buyer name, address, endpoint
- `cac:LegalMonetaryTotal` — Taxable amount, tax amount, payable amount
- `cac:InvoiceLine` — Quantity, unit code, description, price, VAT rate, line total

---

## Rate Limiting & Usage Tracking

- **Rate limit:** Configured per IP address using a token-bucket algorithm (Bucket4j)
- **Storage:** In-memory (Caffeine) by default; Redis-backed in production
- **Cookie-based tracking:** A lightweight cookie stores anonymous usage metrics for UI analytics

---

## Security

- **File validation:** Uploaded files are checked for correct magic bytes (`%PDF`), MIME type, and extension
- **Header injection protection:** Download filenames are sanitised to prevent HTTP response splitting
- **OAuth2:** FreeAgent client credentials are injected via environment variables (never hardcoded)
- **Cookie secrets:** Session cookie signing keys are injected via environment variables
- **No secrets in source:** No API keys, passwords, or tokens are committed to the repository

---

## Environment Variables

| Variable | Used In | Purpose |
|----------|---------|---------|
| `FREEAGENT_CLIENT_ID` | `application-local.yml`, `application-prod.yml` | FreeAgent OAuth2 client ID |
| `FREEAGENT_CLIENT_SECRET` | `application-local.yml`, `application-prod.yml` | FreeAgent OAuth2 client secret |
| `COOKIE_SECRET` | `application-local.yml`, `application-prod.yml` | Cookie signing key |
| `COOKIE_SECRET_PREVIOUS` | `application-local.yml`, `application-prod.yml` | Previous cookie signing key (for rotation) |
| `REDIS_URL` | `application-prod.yml` | Redis connection string (production) |
| `PEPPOL_CONFIG_JSON` | `ConfigService` | Inline JSON config (overrides `config.json` file) |
| `PORT` | `Dockerfile` | Runtime HTTP port (PaaS compatibility) |

---

## License

This project is open source. See the repository for license details.
