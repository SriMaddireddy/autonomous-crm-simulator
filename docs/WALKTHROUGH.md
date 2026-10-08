# Demo and interview walkthrough

## Five-minute demo

1. Start the app and open the dashboard. Identify the synthetic labels and current database mode.
2. Click Evaluate 500 campaigns. Observe the worker capacity, running/queued counts, and completed decisions.
3. Select a PAUSE recommendation. Show why budget or risk wins over a SCALE recommendation.
4. Expand Historical evidence. Explain numeric feature similarity and where evidence IDs are saved.
5. Run the smoke script. Show how the same idempotency key returns the same workflow.
6. Run `mvn verify` and inspect stale-worker/rollback tests. If Docker is available, run the recovery script.

## Questions to be able to answer

**Why persist workflows instead of an in-memory executor only?** The executor disappears on a crash. The database keeps accepted work and lets a new worker reclaim an expired lease.

**Why use an owner and attempt number?** They fence old computations. A worker from attempt 1 must not write after attempt 2 claims the workflow.

**Does this guarantee exactly-once execution?** No. Computation and LLM calls can repeat. Database completion is fenced and atomic, with one result/decision set per workflow.

**What makes this retrieval-grounded?** The explanation receives the nearest historical profiles and their IDs. The policy decision itself uses current campaign metrics. The vector representation is hand-engineered, not a text embedding model.

**How much faster is it?** Report only the measured JSON run. No manual baseline was collected, so there is no supported reduction percentage.

**Are the five skills autonomous AI agents?** The default skills are explicit Java policy functions. LangChain4j optionally generates the explanation. An external MCP client can invoke each skill or queue the entire workflow.

## Exercises to make the project your own

- Change the budget boundary from 90% to 85%; update and run the boundary test.
- Add a minimum sample-size rule for the audience skill and explain its tradeoff.
- Implement a real manual evaluation baseline before reporting a speedup percentage.
- Add tests for a database outage and explain which writes remain durable.
- Add a sixth skill and update discovery, conflict precedence, and decision-count assertions intentionally.
