# AI Improvement Handoff

See the [README](../README.md) for setup and the [AI improvement progress log](./AI_IMPROVEMENTS_PROGRESS.md) for phase-by-phase evidence and the next action.

## Architecture and request flow

Authenticated users can ask from the `/ask` page or from the global floating panel mounted in the root layout. Both call Spring Boot `/api/memories/ask`. The backend searches the user's full indexed vault with a Qdrant owner filter, then re-reads matching IDs from PostgreSQL scoped to that same user. Search ranks the full user-scoped collection and returns the top five relevant matches; those validated memories and the question are passed to FastAPI `/rag/answer`. The complete vault is searchable, but the entire vault is not dumped into the LLM prompt. FastAPI calls Gemini's REST `generateContent` endpoint when `LLM_PROVIDER=gemini`. The UI displays provider/model and the retrieved source summaries.

No-match searches still reach the generator with an empty personal context. Gemini may answer general questions, but the prompt disallows unsupported personal claims. Search and generation failures return service errors. Conversation history is not persisted or sent. Memory summaries, categories, tags, and entity extraction remain deterministic heuristics.

## Latest retrieval finding

The live saved-memory retrieval issue was verified, not inferred: PostgreSQL held one memory, and the `memories_v2` Qdrant collection held one corresponding point with the same memory ID and owner ID. The collection is 384-dimensional cosine; the live embedding service returned normalized 384D vectors from `sentence-transformers/all-MiniLM-L6-v2`; the authenticated user filter matched the saved point.

The root cause was the relevance threshold, not saving, indexing, embeddings, user ownership, PostgreSQL validation, or the source-count UI. At the configured `0.35` cutoff, “What do I remember about travel?” scored `0.30580837` and was discarded. The direct query “travel” scored `0.3608004` and passed. Related paraphrases reached as low as `0.21117169`, while the sampled unrelated prompts scored at most `0.07352879`. The default is now `0.20`, still configurable through `QDRANT_SCORE_THRESHOLD`.

After rebuilding the backend, an authenticated request through Spring `/api/memories/search` returned the real saved memory for the natural-language travel query at score `0.30580837`; a distinctive phrase derived privately from that record also retrieved the point at score `0.6235861`. Java tests verify the lower cutoff, rejection below the cutoff, the user filter, Ask context handoff, and source count/list consistency. No saved personal content was sent to Gemini: AI-service tests use a mocked Gemini request to verify context serialization, so live Gemini generation with a retrieved personal memory remains unverified. The live threshold check used one saved memory and is not a broad relevance-quality benchmark.

## Configuration

- `.env.example`: safe local template; `LLM_PROVIDER=gemini`, empty Gemini key, default `GEMINI_MODEL=gemini-2.5-flash`.
- `docker-compose.yml`: passes `LLM_PROVIDER`, `GEMINI_API_KEY`, `GEMINI_MODEL`, and `DEMO_MODE` to `ai-service`.
- Backend and Compose default `QDRANT_SCORE_THRESHOLD` to `0.20`; set it explicitly to tune retrieval relevance.
- `DEMO_MODE` is currently not consumed by source code; provider selection is controlled by `LLM_PROVIDER`.
- The Gemini key must remain in an ignored local `.env`; never paste it into logs, docs, commits, or test fixtures.

## Current branch changes

Branch: `feature/ai-improvements`.

Implementation changes make Gemini the default Compose provider, use the listed `gemini-2.5-flash` text model by default, prevent unknown providers silently selecting mock mode, support general questions without matched memories, limit prompt context, make embedding/retrieval/provider errors explicit, show provider/model in the Ask UI, lower the default Qdrant relevance cutoff to `0.20` based on the verified retrieval failure, and add focused test coverage. The latest frontend change adds the authenticated global vault assistant while preserving the full `/ask` page.

The two pre-existing local changes must be preserved: `.env.example` had already selected Gemini, and `docker-compose.yml` had already switched MinIO to `cgr.dev/chainguard/minio:latest`. The ignored `.env` is private local configuration and must not be inspected for display or overwritten.

## Verification status

- Backend `MemoryIntegrationTest`: 28 passed via the configured test runner.
- Backend `VectorStoreServiceTest`: 2 passed; together with `MemoryIntegrationTest`, 30 Java tests passed after the retrieval threshold change.
- Live authenticated backend semantic search retrieved the actual saved memory from PostgreSQL/Qdrant (one result, score `0.30580837`) after the backend was rebuilt with threshold `0.20`.
- Live embedding check returned 384 dimensions with a normalized vector. No Gemini request was made with the saved personal memory.
- Frontend media helper tests: 24 passed with Node's test runner.
- Python AI tests: the module loaded, but all 30 tests were skipped because the available Python environment lacks FastAPI/httpx (declared AI-service requirements). Python syntax validation passed; dependencies were not installed.
- Frontend Docker production build passed after adding the global assistant; `/` and `/ask` returned HTTP 200 from the deployed frontend container.
- No live Gemini request was made. The key's validity, quota, network access, and live model response are therefore unverified.

## Known limitations and next actions

- Run the Python AI tests in an environment restored from `ai-service/requirements.txt`, then record actual results in the progress log. The existing direct embedding test may require downloading/initializing the embedding model.
- Run the frontend production build after restoring its declared npm dependencies if available; do not install packages unless needed for validation.
- Validate the `0.20` cutoff on a broader set of representative memories and queries; the live calibration had only one saved memory.
- Keep the global assistant scoped to the signed-in user's vault and keep retrieval bounded to relevant context rather than sending every saved record to Gemini.
- Consider adding a live, opt-in Gemini smoke test only when the user explicitly authorizes sending the local credential to Google. Never print the credential.
- Do not claim live Gemini generation with personal context was verified. Do not run `docker compose down -v`, remove volumes, reset databases, switch branches, stage, commit, or push without an explicit request.

## Next coding-agent instructions

1. Read the [README](../README.md) and [progress log](./AI_IMPROVEMENTS_PROGRESS.md) first.
2. Inspect `git branch --show-current` and `git status --short`; preserve all existing work.
3. Never display or overwrite `.env`, and never expose API keys.
4. Inspect current source rather than relying on older descriptions.
5. Continue from the exact next action recorded in the progress log; avoid redoing completed phases.
6. Run relevant tests and update documentation with actual results.
7. Update the progress log before stopping.
8. Avoid destructive Docker/database operations and do not claim unverified live functionality works.
