# MemoraAI — System Architecture & Design Documentation

## Overview

**MemoraAI** is a full-stack, AI-powered personal memory companion application designed for a college mini-project. It assists users in preserving personal memories, managing family connections, visualizing life timelines, and presenting observational activity trends to caregivers.

---

## 1. System Architecture

```
                               ┌─────────────────────────┐
                               │     Next.js Frontend    │
                               │  (App Router, TS, CSS)  │
                               └────────────┬────────────┘
                                            │ REST / JWT
                                            ▼
                               ┌─────────────────────────┐
                               │   Spring Boot Backend   │
                               │  (Java API Gateway/JPA) │
                               └─┬──────────┬──────────┬─┘
                                 │          │          │
         ┌───────────────────────┘          │          └───────────────────────┐
         ▼                                  ▼                                  ▼
┌──────────────────┐               ┌──────────────────┐               ┌──────────────────┐
│    PostgreSQL    │               │      MinIO       │               │      Neo4j       │
│  Relational DB   │               │   Object Store   │               │     Graph DB     │
└──────────────────┘               └──────────────────┘               └──────────────────┘
                                            │
                                            │ HTTP REST
                                            ▼
                               ┌─────────────────────────┐
                               │   FastAPI AI Service    │
                               │  (Python AI Microsvc)   │
                               └────────────┬────────────┘
                                            │
                                            ▼
                               ┌─────────────────────────┐
                               │         Qdrant          │
                               │ (Vector DB - 384d Cos)  │
                               └─────────────────────────┘
```

---

## 2. Docker & Environment Security

To adhere to security best practices and keep secrets out of source control:

- All service credentials (PostgreSQL, Neo4j, MinIO, JWT secrets, AI keys) are strictly stored in `.env`.
- `docker-compose.yml` references environment variables loaded from `.env`.
- `.env.example` provides safe defaults for local development.
- `.env` is ignored by Git via `.gitignore`.

---

## 3. Qdrant Vector Strategy & Embeddings

- **Collection Name:** `memora_memories`
- **Embedding Model:** `all-MiniLM-L6-v2` (runs locally via `SentenceTransformers`)
- **Vector Dimension:** `384`
- **Distance Metric:** `Cosine`
- **Abstraction:** The Python microservice uses an `EmbeddingProvider` interface so alternative models can be introduced seamlessly in future iterations.

---

## 4. Memory Confidence Score

The **Memory Confidence Score** is an automated software metric ranging from 0% to 100%, calculated based on:
1. **Mention Frequency:** Occurrence in multiple conversational sessions.
2. **Detail Consistency:** Alignment of dates, locations, and involved people across memories.
3. **Family Verification:** Direct confirmation or corrections made by registered family members/caregivers.

### Disclaimer
> **Non-medical Notice:** The Memory Confidence Score is strictly a software consistency and family confirmation score. It is not an objective medical diagnosis, clinical evaluation, or historical truth verification system.
