# RAG PostgreSQL + pgvector

## Server layout

- Container: `ai-agent-scaffold-backup`
- Image: `pgvector/pgvector:0.8.1-pg16`
- Database: `ai_agent_scaffold_lite`
- Docker network: `ai-agent-scaffold-lite-network`
- Data volume: `ai-agent-scaffold-lite-postgres-data`
- Host binding: `127.0.0.1:15432`
- Secret file: `/opt/ai-agent-scaffold-lite/.env.pgvector` with mode `600`

The database port is not exposed publicly. A backend container on the same Docker
network should use `jdbc:postgresql://ai-agent-scaffold-backup:5432/ai_agent_scaffold_lite`.

For local development, create an SSH tunnel first:

```bash
ssh -N -L 15432:127.0.0.1:15432 -p 2222 root@<server-ip>
```

Then configure the application without committing credentials:

```properties
RAG_DB_URL=jdbc:postgresql://127.0.0.1:15432/ai_agent_scaffold_lite
RAG_DB_USERNAME=ai_agent
RAG_DB_PASSWORD=<password-from-server-secret-file>
```

Database objects are managed by Flyway migration
`V5__rag_vector_store.sql`. Do not edit an applied migration; add a new migration
for later schema changes.
