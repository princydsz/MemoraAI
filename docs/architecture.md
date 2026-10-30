# MemoraAI — System Architecture & Design Documentation

## 1. Overview & Vision

**MemoraAI** is a full-stack, AI-powered personal memory companion application. It assists users in preserving personal life memories, searching past experiences semantically, archiving multimedia photo attachments, and interacting with their memories through grounded conversational AI (RAG).

The project vision extends to multi-generational family knowledge graphs, life timelines, and observational trend insights for caregivers.

---

## 2. System Architecture Diagram

```
                               ┌─────────────────────────┐
                               │     Next.js Frontend    │
                               │  (App Router, TS, CSS)  │
                               └────────────┬────────────┘
                                            │ REST / JWT
                                            ▼
                               ┌─────────────────────────┐
                               │   Spring Boot Backend   │
                               │ (Java 17, JPA, Gateway) │
                               └─┬──────────┬──────────┬─┘
                                 │          │          │
         ┌───────────────────────┘          │          └───────────────────────────┐
         │ (JDBC / Flyway)                  │ (MinIO SDK)                          │ (Bolt / HTTP)
         ▼                                  ▼                                      ▼
┌──────────────────┐               ┌──────────────────┐                  ┌──────────────────┐
│    PostgreSQL    │               │      MinIO       │                  │      Neo4j       │
│  Relational DB   │               │   Object Store   │                  │     Graph DB     │
│  (Users/Memories)│               │  (Media Vault)   │                  │  [ROADMAP SPEC]  │
└──────────────────┘               └──────────────────┘                  └──────────────────┘
         │                                  │
         │ REST (NLP, Embeddings, RAG)      │
         ▼                                  │
┌─────────────────────────┐                 │
│   FastAPI AI Service    │                 │
│  (Python AI Microsvc)   │                 │
└────────────┬────────────┘                 │
             │ REST                         │
             ▼                              │
┌─────────────────────────┐                 │
│         Qdrant          │                 │
│ (Vector DB - 384d Cos)  │                 │
│ collection: memories_v2 │                 │
└─────────────────────────┘                 │
```

---

## 3. Implementation Status Matrix

To maintain architectural transparency, system capabilities are classified as either **Implemented (Phases 1–8)** or **Planned Roadmap**:

### Implemented Architecture (Active in Codebase)

| Component | Technology | Responsibility | Current Status |
| :--- | :--- | :--- | :--- |
| **Frontend UI** | Next.js 14, TypeScript | Authenticated dashboard, memory CRUD UI, search, media gallery, RAG portal | **Production Active** |
| **API Gateway** | Spring Boot 3, Java 17 | Authentication (JWT), memory business logic, media service, access control | **Production Active** |
| **AI Microservice** | FastAPI, Python 3.11 | Deterministic memory summarization, category & tag extraction, RAG orchestration | **Production Active** |
| **Relational DB** | PostgreSQL 15, Flyway | Persistent storage for users, roles, memories, and media metadata | **Production Active** |
| **Vector Engine** | Qdrant v1.7.4 | Stores 384-d normalized embeddings; provides fast cosine similarity search | **Production Active** |
| **Media Vault** | MinIO S3-compatible | Object storage for image files (JPEG, PNG, WebP) with strict rollback cleanup | **Production Active** |

### Planned Roadmap Architecture (Design Specifications)

| Feature / Service | Planned Technology | Intended Role | Current Status |
| :--- | :--- | :--- | :--- |
| **Voice Ingestion** | OpenAI Whisper / FastAPI | Transcribes audio recordings into text memories | **Planned Roadmap** |
| **Knowledge Graph** | Neo4j 5 Java Driver, Cypher | Entity and relationship memory knowledge graph | **Implemented (Phase 10)** |
| **Confidence Scoring**| Algorithmic Pipeline | Automated software consistency and family confirmation score | **Planned Roadmap** |
| **Family Timelines** | Next.js, WebSockets | Multi-user collaborative vault and caregiver timeline trends | **Planned Roadmap** |

> **Architecture Note on Neo4j (Phase 10 Implementation):**
> Spring Boot integrates Neo4j 5.15.0 via the official Neo4j Java Driver with parameterized Cypher queries. The graph model anchors user ownership through `(:User)-[:OWNS]->(:Memory)` and connects memories to categories (`IN_CATEGORY`), user-scoped tags (`HAS_TAG`), and extracted people (`MENTIONS`) and places (`LOCATED_AT`). Graph synchronization executes after PostgreSQL transaction commit. Historical backfill and reconciliation are provided via `POST /api/memories/graph/reindex`, and graph retrieval is exposed via `GET /api/memories/graph`. Future phases will expand this model to include cross-user family relationship traversals and frontend visualization.

---

## 4. Qdrant Vector Strategy & Embeddings

- **Collection Name:** `memories_v2`
- **Embedding Model:** `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions)
- **Distance Metric:** `Cosine`
- **Relevance Threshold:** Default `0.20` (configurable via `QDRANT_SCORE_THRESHOLD`)
- **Indexing Pipeline:**
  1. Memory creation or update triggers asynchronous or synchronous embedding generation via `POST /embeddings` on `ai-service`.
  2. The resulting vector is stored in Qdrant with payload metadata (`userId`, `title`, `category`, `tags`).
  3. Search queries embed the prompt and execute a cosine similarity search filtered by `userId`.
  4. Vector re-indexing is available through `POST /api/memories/reindex`.

---

## 5. Multimedia Storage Architecture (Phase 8)

The Multimedia Memory Vault stores binary images in an isolated, authenticated object store:

- **Storage Provider:** MinIO (S3-compatible) running in local Docker network.
- **Bucket:** `memora-media` (automatically verified and created on startup by Spring Boot).
- **Security & Access Control:**
  - MinIO URLs, bucket names, and internal storage paths are never exposed to clients.
  - Image streaming requires valid JWT authentication and verifies memory ownership (`403 Forbidden` on cross-user access).
  - Responses include `Cache-Control: private, no-store` to prevent caching of sensitive photos on shared caches.
- **Validation & Constraints:**
  - Allowed MIME types: `image/jpeg`, `image/png`, `image/webp`.
  - Maximum 5 images per memory.
  - Maximum 10 MiB (10,485,760 bytes) per image file.
- **Failure Resilience & Cleanup:**
  - **Upload Rollback Compensation:** If PostgreSQL transaction commit fails or is rolled back, uploaded MinIO objects are immediately removed via Spring `TransactionSynchronization`.
  - **Cascade Deletion:** Deleting a memory purges all associated MinIO objects, PostgreSQL metadata records, and Qdrant vectors.

---

## 6. Docker Startup Resilience & Health-Check Ordering

To eliminate startup race conditions during cold starts, `docker-compose.yml` defines health checks:

- **PostgreSQL:** `pg_isready -U memora_user -d memora_db`
- **MinIO:** `mc ready local` (built-in MinIO client readiness check, independent of `curl`/`wget`)
- **Qdrant:** `bash -c "exec 3<>/dev/tcp/127.0.0.1/6333 && echo -e 'GET /readyz HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' >&3 && head -n 1 <&3 | grep -q 'HTTP/1.1 200'"` (native bash HTTP GET probe to `/readyz` asserting status 200)
- **AI Service:** `python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health')"`

### Startup Dependency Graph
```
postgres (healthy) ──┐
minio (healthy)    ──┼──► backend (Spring Boot) ──► frontend (Next.js)
qdrant (healthy)   ──┤         ▲
ai-service (healthy) ┘         │
      ▲                        │
      └── qdrant (healthy) ────┘
```

Spring Boot will not boot until PostgreSQL, MinIO, Qdrant, and the AI Service have all transitioned to the `healthy` state.

---

## 7. Security & Environment Configuration

- **Zero-Secret Codebase:** Credentials, database passwords, MinIO root keys, and JWT signing keys are stored exclusively in `.env` and injected via Docker Compose or system environment variables.
- **Key Redaction:** The AI service sanitizes all outgoing error logs and HTTP exception details to redact `GEMINI_API_KEY` values and confidential memory snippets.
- **Role Isolation:** Standard accounts (`ROLE_USER`) have zero access to administrative functions or other users' memories.

---

## 8. Planned Specifications (Roadmap)

### Memory Confidence Score Specification
The **Memory Confidence Score** is an automated software metric ranging from 0% to 100%, planned to evaluate:
1. **Mention Frequency:** Occurrence across multiple distinct conversational sessions.
2. **Detail Consistency:** Coherence of dates, locations, and names across memories.
3. **Family Verification:** Direct confirmation or annotations provided by registered family members.

> **Non-medical Notice:** The Memory Confidence Score is strictly a software consistency and family confirmation score. It is not an objective medical diagnosis, clinical evaluation, or historical truth verification system.

### Neo4j Family Knowledge Graph Specification
The planned knowledge graph will link:
- `(:User)-[:HAS_RELATIVE {relation}]->(:User)`
- `(:Memory)-[:MENTIONS]->(:Person|:Location|:Event)`
- `(:Memory)-[:OCCURRED_ON]->(:Date)`
