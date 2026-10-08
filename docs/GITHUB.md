# Publish this project

Suggested repository name: **autonomous-crm-simulator**

Suggested description: **Java campaign evaluation lab with durable leased workflows, MCP tools, pgvector retrieval, and an inspectable decision dashboard.**

Suggested topics: `java`, `spring-boot`, `postgresql`, `pgvector`, `langchain4j`, `mcp`, `workflow`, `simulation`.

## Upload

Create an empty repository in your GitHub account, then run these commands from this project's directory:

```sh
git init -b main
git add .
git commit -m "Implement campaign simulator with durable workflows and evidence retrieval"
git remote add origin https://github.com/SriMaddireddy/autonomous-crm-simulator.git
git push -u origin main
```

Use your actual current commit date. Ensure the initial commit excludes `data/`, `target/`, `.env`, and any real API keys. If a Git repository already exists, skip initialization and inspect `git status` before committing.

GitHub Actions will run both Java tests and the Docker/PostgreSQL checks after the push. Inspect the results and fix any failing check before calling the PostgreSQL stack verified.

Pin the repository on your profile. A short profile README entry can link it:

> **Campaign Lab** — Java/Spring Boot campaign simulator with five policy skills, durable workflow recovery, inspectable evidence, MCP tools, and optional LangChain4j explanations.

Keep the local benchmark's storage-mode caveat beside any reported performance number. Update your project dates to reflect actual development. Add future commits for real changes, such as a new policy or a documented benchmark.
