# MemoraAI — Preserving Memories, Strengthening Connections

MemoraAI is an AI-powered personal memory assistant designed to capture, organize, and retrieve life memories through structured records, semantic vector search, multimedia image archiving, and grounded conversational AI (RAG).

---

## 🌟 Feature Status & Implementation Matrix

MemoraAI distinguishes between **implemented core capabilities** (verified in the codebase across Phases 1–8) and **planned future roadmap features** that define the broader project vision.

### ✅ Implemented Core Features (Phases 1–8)

1. **Authentication & User Management:**
   - Stateless JWT authentication using Spring Security 6.
   - Secure password hashing (Argon2 / BCrypt).
   - User registration, login, profile inspection, and strict per-user ownership isolation.

2. **Structured Memory Vault (CRUD):**
   - Full Create, Read, Update, and Delete operations backed by PostgreSQL 15 and Spring Data JPA.
   - Memory fields: `title`, `content`, `category`, `tags`, `createdAt`, `updatedAt`.
   - Comprehensive field-level validation and error handling.

3. **AI Memory Processing:**
   - Dedicated FastAPI microservice (`ai-service`).
   - Automated memory summarization, smart category suggestion, and keyword tag extraction.

4. **Semantic Vector Search:**
   - Qdrant vector database integration with collection `memories_v2`.
   - Dense 384-dimensional neural embeddings using `sentence-transformers/all-MiniLM-L6-v2`.
   - Cosine similarity matching with configurable relevance threshold (`0.35`).
   - On-demand vector re-indexing API (`POST /api/memories/reindex`).

5. **Ask MemoraAI / Grounded RAG:**
   - Retrieval-Augmented Generation answering user questions strictly grounded in their personal memories.
   - Dual provider support: Google Gemini 3.8 Flash (`gemini`) and deterministic mock provider (`mock`).
   - Prompt-injection defenses preventing untrusted memory content from overriding assistant instructions.
   - Error-string redaction ensuring API keys, secret parameters, and memory contents are never leaked in logs or error responses.

6. **Multimedia Memory Vault (Phase 8):**
   - MinIO S3-compatible local object storage with dedicated bucket `memora-media`.
   - Multi-image attachments (up to 5 images per memory, maximum 10 MiB per file; JPEG, PNG, WebP).
   - Authenticated image streaming with `Cache-Control: private, no-store` headers.
   - Transactional rollback compensation: MinIO uploads are cleaned up if the PostgreSQL transaction fails to commit.
   - Cascade cleanup: deleting a memory purges all associated MinIO objects and Qdrant vectors.
   - Image attachments supported during memory creation in both **Save Manually** and **Auto-Organize & Save** flows.

7. **Frontend Dashboard & Navigation:**
   - Built with Next.js 14 (App Router, TypeScript, CSS).
   - Authenticated dashboard with live metrics (Total Memories, Active Categories, Tagged Memories) backed by verified API data.
   - Interactive memory gallery with full-size preview modal.
8. **Knowledge Graph Integration (Phase 10):**
   - Neo4j 5.15.0 graph database integration using the official Neo4j Java Driver and parameterized Cypher queries.
   - Deterministic entity extraction (`POST /extract-entities` on `ai-service`) for conservative recognition of people and places without requiring external LLM dependencies.
   - Transactional after-commit synchronization connecting memories to categories, user-scoped tags, extracted people, and places.
   - Cascade graph cleanup on memory deletion and orphan entity pruning.
   - Secure, tenant-isolated knowledge graph API (`GET /api/memories/graph`) returning strongly typed nodes and relationships.
   - Authenticated historical backfill and reconciliation endpoint (`POST /api/memories/graph/reindex`) with bounded batch pagination.

---

### 🗺️ Planned Future Roadmap

The following capabilities represent the expanded project vision and are actively planned for future development milestones:

- **🎙️ Whisper Speech-to-Text & Audio Ingestion:**
  Automated voice recording upload, speech transcription via OpenAI Whisper, and automatic ingestion into structured memories.
- **🕸️ Interactive Graph Visualization & Family Relationship Mapping:**
  Frontend visual graph explorer and multi-generational family tree mapping connecting relatives across collaborative vaults.
- **📊 Memory Confidence Scoring:**
  An algorithmic software score (0–100%) measuring memory consistency across conversations, detail alignment, and family member confirmations.
- **👨‍👩‍👧 Family & Caregiver Sharing & Timelines:**
  Collaborative multi-user vaults, life timeline visualization, and observational activity trend presentations for caregivers.

---

## 🚀 Architecture Overview

MemoraAI uses a resilient multi-service architecture orchestrated via Docker Compose:

- **Frontend:** Next.js 14 (App Router, TypeScript, Vanilla CSS)
- **Backend API Gateway:** Spring Boot 3 (Java 17, Spring Security 6, Spring Data JPA, Flyway)
- **AI Microservice:** FastAPI (Python 3.11, FastEmbed / SentenceTransformers, Gemini REST API)
- **Relational Database:** PostgreSQL 15
- **Vector Database:** Qdrant (384-dimensional dense embeddings, Cosine distance)
- **Object Storage:** MinIO (Local S3-compatible storage with `mc` readiness checks)
- **Knowledge Graph Database:** Neo4j 5 (Neo4j Community 5.15.0, official Java Driver, parameterized Cypher memory graph)

---

## 🛠️ Prerequisites

- **Docker Desktop** (with Docker Compose v2+)
- **Git**
- *(Optional for bare-metal development without Docker)*: Java 17+, Node.js 18+, Python 3.11+

---

## ⚙️ Environment Setup

1. Copy the example environment configuration:
   ```bash
   cp .env.example .env
   ```
2. Verify development environment settings. By default, MemoraAI runs in **DEMO MODE** (`DEMO_MODE=true`, `LLM_PROVIDER=mock`), which does not require external API keys. To use live Google Gemini models, set `LLM_PROVIDER=gemini` and provide a valid `GEMINI_API_KEY`.

---

## 🐳 Starting the Application with Docker

To build and launch all services with health-check-ordered startup:

```bash
docker compose up --build -d
```

### Startup Resilience
The Compose setup configures health checks so that Spring Boot waits for all required backing services before starting:
- **PostgreSQL:** Checked via `pg_isready`
- **MinIO:** Checked via native `mc ready local`
- **Qdrant:** Checked via bash HTTP probe to `http://127.0.0.1:6333/readyz` verifying HTTP 200
- **AI Service:** Checked via Python HTTP health check on `/health`

To inspect container health and status:
```bash
docker compose ps
```

To view logs across all containers:
```bash
docker compose logs -f
```

---

## 🌐 Service Ports & Access Table

| Service | Host URL / Port | Purpose | Status |
| :--- | :--- | :--- | :--- |
| **Frontend** | `http://localhost:3000` | Web Dashboard & Vault UI | Active |
| **Backend API** | `http://localhost:8080` | REST API Gateway | Active |
| **AI Microservice** | `http://localhost:8000` | Embeddings, NLP & RAG | Active |
| **PostgreSQL** | `localhost:5432` | Relational Storage | Active |
| **Qdrant DB** | `http://localhost:6333` | Vector Database REST & Dashboard | Active |
| **MinIO Console** | `http://localhost:9001` | Media Storage Web Console | Active |
| **Neo4j Browser** | `http://localhost:7474` | Knowledge Graph Browser | Provisioned *(Roadmap)* |

---

## 🔍 Service Health Check Endpoints

- **Backend Health Check:**
  ```bash
  curl http://localhost:8080/api/health
  ```
  *Expected Output:* `{"service":"MemoraAI Backend","status":"UP"}`

- **AI Service Health Check:**
  ```bash
  curl http://localhost:8000/health
  ```
  *Expected Output:* `{"service":"MemoraAI AI Service","status":"UP"}`

---

## 🧪 Running Automated Tests

- **Backend Tests (Spring Boot + JUnit 5 + Mockito):**
  ```bash
  cd backend && ./mvnw test
  ```
- **AI Microservice Tests (pytest):**
  ```bash
  cd ai-service && pytest
  ```
- **Frontend Media & Helper Tests (Node.js test runner):**
  ```bash
  cd frontend && node --test --experimental-strip-types src/test/media.test.mjs
  ```
- **Frontend Production Build:**
  ```bash
  cd frontend && npm run build
  ```

---

## 🛑 Stopping the Application

To stop all running containers:
```bash
docker compose down
```

To stop containers and remove persistent volumes (fresh restart):
```bash
docker compose down -v
```
