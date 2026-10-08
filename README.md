# MemoraAI — Preserving Memories, Strengthening Connections

MemoraAI is an AI-powered memory companion designed to preserve personal memories through conversational AI, speech-to-text, structured memory storage, semantic vector search, family graph relationships, life timelines, and collaborative family contributions.

---

## 🚀 Architecture Overview

MemoraAI is structured as a modern multi-service architecture:

- **Frontend:** Next.js (App Router, TypeScript, Tailwind CSS)
- **Backend API Gateway:** Spring Boot 3 (Java 17, Spring Security, Spring Data JPA)
- **AI Microservice:** FastAPI (Python 3.11, Whisper, LLM Providers, SentenceTransformers)
- **Structured Database:** PostgreSQL 15
- **Vector Database:** Qdrant (384-dimensional dense embeddings with Cosine similarity)
- **Knowledge Graph Database:** Neo4j 5
- **Object Storage:** MinIO (Local S3-compatible media storage)

---

## 🛠️ Prerequisites

Ensure you have the following installed on your system:

- **Docker Desktop** (with Docker Compose v2+)
- **Git**
- *(Optional for native local dev without Docker)*: Java 17+, Node.js 18+, Python 3.11+

---

## ⚙️ Environment Setup

1. Copy the template environment configuration file to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` and verify development environment settings. Real API keys for OpenAI/Gemini are optional because MemoraAI runs in **DEMO MODE** by default.

---

## 🐳 Starting the Application with Docker

To build and run all 7 services in detached mode, run:

```bash
docker compose up --build -d
```

To view logs across all containers:
```bash
docker compose logs -f
```

---

## 🌐 Service Ports & Access Table

| Service | Host URL / Port | Purpose |
| :--- | :--- | :--- |
| **Frontend** | `http://localhost:3000` | Web UI |
| **Backend API** | `http://localhost:8080` | Central REST Gateway |
| **AI Microservice** | `http://localhost:8000` | STT, NLP & Vector Retrieval |
| **PostgreSQL** | `localhost:5432` | Relational Database |
| **Qdrant DB** | `http://localhost:6333` | Vector Database Dashboard |
| **Neo4j DB** | `http://localhost:7474` | Knowledge Graph Browser |
| **MinIO Console** | `http://localhost:9001` | Media Storage Console |

---

## 🔍 Service Health Check Endpoints

- **Backend Health Check:**
  ```bash
  curl http://localhost:8080/api/health
  ```
  *Expected Output:* `{"status":"UP","service":"MemoraAI Backend"}`

- **AI Service Health Check:**
  ```bash
  curl http://localhost:8000/health
  ```
  *Expected Output:* `{"status":"UP","service":"MemoraAI AI Service"}`

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
