# MemoraAI AI Improvements Progress

This file is the source of truth for continuing the AI-improvement work.

## Checkpoint 1 — Audit

- **Status:** COMPLETE
- **Branch:** `feature/ai-improvements`
- **Pre-existing changes preserved:** `.env.example` changes `LLM_PROVIDER` from `mock` to `gemini`; `docker-compose.yml` changes the MinIO image to `cgr.dev/chainguard/minio:latest`. `.env` is ignored and untracked. Its values and credentials were not recorded here.
- **Files inspected:** `ai-service/app/main.py`, `ai-service/app/test_main.py`, `ai-service/requirements.txt`, `backend/src/main/java/com/memoraai/service/AiService.java`, `VectorStoreService.java`, `MemoryService.java`, `MemoryController.java`, `GlobalExceptionHandler.java`, relevant DTOs and integration tests, `frontend/src/app/ask/page.tsx`, `frontend/src/lib/memory.ts`, `frontend/src/types/rag.ts`, `docker-compose.yml`, `backend/src/main/resources/application.yml`, `.env.example`, `README.md`.
- **Confirmed findings:**
  - Ask path is frontend → authenticated Spring endpoint → Qdrant search → PostgreSQL ownership-filtered fetch → FastAPI `/rag/answer` → Gemini REST API if `LLM_PROVIDER=gemini`.
  - Compose defaults `LLM_PROVIDER` to `mock`; the mock branch emits a memory-echo template, not an LLM answer. `DEMO_MODE` is passed to the container but is not read by the Python service.
  - Ask exits before calling the generator when search yields zero results, so general questions cannot get Gemini answers without a matching memory.
  - Embedding model errors are swallowed and produce a 384-dimensional zero vector; the endpoint responds as if embedding generation succeeded. Java embedding transport errors similarly become an empty vector.
  - Vector search errors and no-match results both become an empty list, preventing Ask from distinguishing retrieval failure from no relevant memories.
  - `/process-memory` summarization/category/tag suggestions and `/extract-entities` are deterministic heuristics.
  - Python uses raw Gemini REST, not the declared `google-genai` SDK. The configured default `gemini-3.8-flash` does not appear as a text-generation model in Google's current [Gemini model list](https://ai.google.dev/gemini-api/docs/models); `gemini-2.5-flash` is listed as a generative text model. No live API request was made.
  - No conversation-history storage or request field exists; follow-up context is not implemented.
- **Tests inspected:** Python tests mock Gemini requests; Java Ask integration tests mock AI and vector services; no live Gemini or real Qdrant pipeline is exercised.
- **Changes implemented:** None yet.
- **Tests performed:** None. `pytest` is not installed in the available Python environment; no dependencies were installed.
- **Remaining work:** Correct model/configuration and provider handling; improve Gemini prompt and empty-memory behavior; surface embedding/retrieval failures; add focused tests; update README and create handoff document; run available tests and review final diff.
- **Exact next action:** Implement the Python embedding/Gemini behavior and backend retrieval/error distinction, then add focused tests before documentation changes.

## Checkpoint 2 — Gemini generation and error handling

- **Status:** BLOCKED
- **Files modified:** `ai-service/app/main.py`, `ai-service/app/test_main.py`, `docker-compose.yml`, `.env.example`.
- **Findings/changes:** Default provider now selects Gemini; only explicit `mock` selects mock mode, and unsupported provider values fail. Default model changed to `gemini-2.5-flash` per Google's model list. Gemini uses the API-key header, structured system/user content, general knowledge alongside strict personal-memory grounding, and classifies missing key, auth, quota, unavailable model, timeout, network, and malformed-response failures. Embedding failures/zero vectors now fail explicitly rather than returning zeros.
- **Tests:** Added or updated focused tests for general questions without memories, key absence, unsupported provider, prompt contents, key header, authentication/model errors, network/timeouts, malformed response, quota, and embedding failure. Python `unittest` loaded the module but skipped all 30 tests because FastAPI/httpx are absent; Python source/test AST syntax check passed. No packages installed.
- **Remaining work:** Run the Python tests in an environment with declared `ai-service/requirements.txt` dependencies; review any failures.
- **Exact next action:** Restore the AI service's declared dependencies in a suitable development environment, then run `python -m unittest app.test_main` from `ai-service`.

## Checkpoint 3 — Retrieval and prompt behavior

- **Status:** BLOCKED
- **Files modified:** `backend/src/main/java/com/memoraai/exception/AiServiceException.java`, `MemoryRetrievalException.java`, `GlobalExceptionHandler.java`, `service/AiService.java`, `service/VectorStoreService.java`, `service/MemoryService.java`, `dto/AskMemoryResponse.java`, `frontend/src/types/rag.ts`, `frontend/src/app/ask/page.tsx`, `backend/src/test/java/com/memoraai/MemoryIntegrationTest.java`.
- **Findings:** No-memory Ask skipped generation; search failures and empty results were indistinguishable; successful responses did not identify provider/model.
- **Changes:** Ask now sends up to five ownership-validated results (or an empty context) to the generator, allowing general Gemini answers without retrieved personal facts. Retrieval and generation outages return HTTP 503 rather than successful-looking empty/notice text. Provider/model are passed to the UI. Prompt context is structured JSON and capped to five memories/16,000 content characters; memory content is explicitly treated as untrusted.
- **Tests:** Java `MemoryIntegrationTest` passed (28 tests). Python tests were skipped as recorded in Checkpoint 2. Frontend helper tests passed (24 tests); frontend production build was attempted but blocked because the Next.js executable is missing.
- **Remaining work:** Complete Python runtime tests and frontend build in environments with dependencies. Live Gemini was not called and remains unverified.
- **Exact next action:** Run the focused Python tests and frontend production build once their declared dependencies are available.

## Checkpoint 4 — Focused verification

- **Status:** BLOCKED
- **Files modified:** `ai-service/app/test_main.py`, `backend/src/test/java/com/memoraai/MemoryIntegrationTest.java`.
- **Findings:** Existing tests mock external AI/vector dependencies and do not verify live Gemini.
- **Changes:** Added focused provider, prompt, embedding failure, retrieval failure, no-memory general answer, provider metadata, and user isolation coverage.
- **Tests:** Java `MemoryIntegrationTest`: 28 passed. Frontend media/helper tests: 24 passed. Python test module: 30 skipped because required FastAPI/httpx modules are unavailable. Python source/test syntax: passed. Frontend production build failed to start because `next` is unavailable. No live Gemini call.
- **Remaining work:** Runtime-run AI tests and compile/build frontend when dependencies are available; report live Gemini as unverified.
- **Exact next action:** Run `python -m unittest app.test_main` from `ai-service` and `npm run build --prefix frontend` in dependency-restored environments.

## Checkpoint 5 — Documentation and handoff

- **Status:** COMPLETE
- **Files modified:** `README.md`, `docs/AI_AGENT_HANDOFF.md`.
- **Findings:** README defaults conflict with `.env.example` and overstates grounded/strictly-memory-only generation.
- **Changes:** Documented actual provider/mode semantics, request flow, general-answer behavior, memory grounding, errors, model and key configuration, missing conversation history, verification status, branch changes, and handoff instructions.
- **Tests:** Documentation manually checked against the source/configuration and actual test outcomes.
- **Remaining work:** Recheck final diff/status.
- **Exact next action:** Perform final diff hygiene and confirm protected local edits and ignored `.env` remain untouched.

## Checkpoint 6 — Final review

- **Status:** COMPLETE
- **Files modified:** Code, tests, docs, and config files listed in Checkpoints 2–5.
- **Findings:** The original `.env.example` provider selection and Docker Compose MinIO image change remain intact. `.env` remains ignored/untracked and was not modified or displayed. Branch remains `feature/ai-improvements`.
- **Changes:** AI provider default and error behavior, retrieval handling, provider metadata, tests, and documentation updated. `git diff --check` passed; final status contains the expected source/docs changes and the preserved local edits.
- **Tests:** See Checkpoint 4. No live Gemini request was made.
- **Remaining work:** Python runtime tests and frontend production build are blocked on missing declared dependencies; live Gemini validity/access remains unverified.
- **Exact next action:** In dependency-restored environments, run `python -m unittest app.test_main` from `ai-service` and `npm run build --prefix frontend`; do not send the local key to Google without explicit authorization.

## Checkpoint 7 — Saved-memory retrieval investigation

- **Status:** VERIFIED
- **Verified root cause:** The live PostgreSQL database contained one saved memory and `memories_v2` contained one matching Qdrant point. The point ID and `user_id` matched the PostgreSQL row. Collection configuration was 384-dimensional cosine; the running embedding service used `sentence-transformers/all-MiniLM-L6-v2`, returned normalized 384D vectors, and the backend applied the authenticated user's filter. Retrieval was not failing or losing the result in PostgreSQL or the UI: the Qdrant threshold was `0.35`, which excluded the relevant natural-language question “What do I remember about travel?” at score `0.30580837`. A direct “travel” query scored `0.3608004` and passed, consistent with wording-sensitive threshold behavior.
- **Calibration evidence:** Against that saved memory, tested related paraphrases scored `0.21117169` to `0.30580837`; sampled unrelated queries scored `-0.03516012` to `0.07352879`. Changed the default threshold to `0.20`; `QDRANT_SCORE_THRESHOLD` remains configurable. This small local sample is evidence for the observed failure, not a universal precision/recall benchmark.
- **Changes:** Set the default threshold consistently to `0.20` in Java configuration fallback, backend/test YAML, and Compose; updated README/architecture and threshold-related test descriptions. Added `VectorStoreServiceTest` coverage for returning a relevant 0.3058 result, excluding an unrelated 0.0235 result, sending the configured threshold, and preserving the per-user filter. Extended the authenticated Ask integration test to assert the matched memory is passed to the AI service and that source count equals the source list size.
- **Files changed in this checkpoint:** `backend/src/main/java/com/memoraai/service/VectorStoreService.java`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/application.yml`, `docker-compose.yml`, `backend/src/test/java/com/memoraai/MemoryIntegrationTest.java`, `backend/src/test/java/com/memoraai/service/VectorStoreServiceTest.java`, `README.md`, `docs/architecture.md`, `docs/AI_IMPROVEMENTS_PROGRESS.md`, `docs/AI_AGENT_HANDOFF.md`.
- **Tests and live checks:** Focused Java tests passed: `MemoryIntegrationTest` (28) plus `VectorStoreServiceTest` (2), 30 total. Rebuilt/recreated the backend and AI-service containers without removing volumes; the backend received `QDRANT_SCORE_THRESHOLD=0.20`. An authenticated request through Spring `/api/memories/search` retrieved the actual saved PostgreSQL memory from Qdrant for the natural-language travel question (one result, score `0.30580837`). A distinctive phrase derived privately from the saved memory also retrieved its matching user-filtered Qdrant point (score `0.6235861`). The AI service returned a 384D normalized embedding. `git diff --check` passed. Existing PostgreSQL and Qdrant data remained in place; the MinIO image remains `cgr.dev/chainguard/minio:latest`.
- **Gemini/context verification:** No real saved-memory content was sent to Gemini during this investigation. Backend tests verify the retrieved memory is passed to `generateRagAnswer`; the existing AI-service tests mock Gemini and verify the request context. A live Gemini request with personal memory content is therefore not claimed or verified.
- **Remaining limitations:** Only one saved memory was available for live calibration; validate the threshold against a broader set of user memories and queries before treating it as globally optimal. Live Gemini generation with retrieved context remains unverified. Python tests and the frontend production build remain blocked by missing local dependencies as noted above.
- **Exact next action:** In a dependency-restored environment, run the Python AI tests; if broader live-memory tuning is needed, use representative test records and keep their contents out of logs.

## Checkpoint 8 — Vault-wide assistant access

- **Status:** COMPLETE
- **Request:** Make AI available on every website page and ensure it can search the whole vault of the signed-in user, without crossing user boundaries.
- **Implementation:** Added the authenticated global floating Ask AI panel in `frontend/src/components/VaultAssistant.tsx`, mounted in the root layout. It uses the existing authenticated `/api/memories/ask` request path, displays answer/provider/source metadata, links matched source titles to the vault, and surfaces request failures. The existing `/ask` page remains available. Updated its copy and project documentation.
- **Vault scope:** Qdrant searches the full indexed collection with the current user's `user_id` filter; PostgreSQL validates returned IDs against that same user. Search returns the top five relevant matches as bounded LLM context; it does not send every saved memory to Gemini. The full vault is searchable, while cross-user access remains prohibited.
- **Files changed in this checkpoint:** `frontend/src/components/VaultAssistant.tsx`, `frontend/src/app/layout.tsx`, `frontend/src/app/ask/page.tsx`, `README.md`, `docs/AI_AGENT_HANDOFF.md`, `docs/AI_IMPROVEMENTS_PROGRESS.md`.
- **Validation:** `docker compose build frontend` passed, including Next.js compilation and TypeScript checking. Recreated the frontend container; `/` and `/ask` both returned HTTP 200. Backend and AI-service containers remained running; Postgres/Qdrant volumes were not modified. `git diff --check` passed.
- **Remaining limitations:** Global panel behavior was production-built and route-smoke-tested, but not manually browser-tested with an authenticated session. The Python test suite remains blocked/skipped due host environment dependencies; no Gemini call with personal vault content was made.
- **Exact next action:** If needed, browser-test the global panel while signed in and validate relevance quality against a larger, authorized memory sample.
