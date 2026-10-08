# Verification

This file distinguishes implemented behavior from runtime evidence. See `docs/benchmark-local.json` if a measured local run has been saved.

## Checks

- `mvn verify`: unit and H2-backed integration tests.
- `scripts/smoke.py`: real HTTP submission, duplicate-key behavior, successful five-skill result, traceable retrieval, MCP discovery/polling, and lifecycle events.
- `scripts/benchmark.py`: real batch submission, completion polling, observed latency distribution, and worker counters. Generates a JSON report with caveats.
- `scripts/recovery.py`: Docker/PostgreSQL restart with an expired synthetic claim; verifies attempt 2 succeeds with exactly five decision rows. It does not test database disk destruction or random interruption of every instruction.
- `.github/workflows/ci.yml`: definitions for hosted Java and Docker/PostgreSQL checks; hosted results only exist after publishing/running them.

## Measured standalone run

A 500-campaign batch completed successfully with zero failures in 2.784 seconds. Retrieval p50 was 0.186 ms, p95 0.401 ms, and maximum 1.257 ms. The configured pool had 50 slots; observed peak active workflows was 48. See [raw report](benchmark-local.json).

These numbers describe a single local H2/exact-cosine run on macOS/Apple Silicon. They are not PostgreSQL/HNSW measurements and do not establish a percentage reduction against manual evaluation. LLM calls were disabled.

## Local verification status

- Java unit/integration checks: **18 tests passed**, zero failures/errors/skips.
- Live HTTP smoke and 500-workflow benchmark: passed.
- Isolated H2 process-restart check: passed; committed result survived SIGKILL, expired claim recovered on attempt 2, exactly ten decisions for two completed workflows.
- Desktop (1280px) and mobile (390px): verified; no horizontal page overflow.
- PostgreSQL/pgvector Docker runtime: not exercised on this machine because Docker is unavailable. CI and reproducible scripts are provided; their presence does not imply a hosted run passed.
- Live paid LLM call: not exercised. The LangChain4j request and fallback path were tested against a local mock server.

The 10,000 logs and 500 campaigns are synthetic seeded datasets. Numeric profile retrieval is not semantic text RAG. Five skills are deterministic policies, with optional AI narrative generation. Recovery is scoped to persisted database state; no guarantee covers database/volume destruction.
