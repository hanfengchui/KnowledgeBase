# Repository Guidelines

## Project Structure & Module Organization

This repository contains an enterprise knowledge assistant demo. The Spring Boot backend lives in `backend/`, with Java source under `backend/src/main/java/com/example/knowledgeassistant`, resources and SQL schema under `backend/src/main/resources`, and JUnit tests under `backend/src/test/java`. The Vue 3 frontend lives in `frontend/`, with app code in `frontend/src` and Vite config in `frontend/vite.config.js`. Product notes are in `docs/`; sample documents are in `samples/`.

## Build, Test, and Development Commands

- `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local`: run the API on the local profile.
- `cd backend && mvn test`: run backend unit tests.
- `cd frontend && npm install`: install dependencies from `package-lock.json`.
- `cd frontend && npm run dev`: start Vite on port `5173` with `/api` proxied to `http://localhost:8080`.
- `cd frontend && npm run build`: create the production frontend bundle in `frontend/dist`.
- `cd frontend && npm run preview`: preview the built frontend locally.

Local backend setup requires Java 17, PostgreSQL with `pgvector`, and a Bailian-compatible OpenAI API key in `BAILIAN_API_KEY`. Copy `backend/src/main/resources/application-local.yml.example` to `application-local.yml`.

## Coding Style & Naming Conventions

Use Java 17 and Spring Boot conventions in the backend: 4-space indentation, package names under `com.example.knowledgeassistant`, PascalCase classes, camelCase methods and fields, and suffixes such as `Controller`, `Service`, `Request`, `Response`, and `Dto`. Keep controllers thin; place authorization, tenant isolation, and business logic in services.

Frontend code uses Vue single-file components and ES modules. Follow the existing style: 2-space indentation, single quotes, no semicolons, PascalCase component files, and camelCase functions/state. Keep API calls in `frontend/src/api.js`.

## Testing Guidelines

Backend tests use JUnit 5 via `spring-boot-starter-test`. Name classes `*Test` and place them beside the matching package under `backend/src/test/java`. Add focused tests for service logic, document parsing/chunking, permissions, and tool behavior. The frontend has no test runner; run `npm run build` before submitting UI changes.

## Commit & Pull Request Guidelines

Git history currently contains only `Initial local RAG knowledge base demo`, so no strict convention is established. Use short, imperative subjects such as `Add tenant audit filtering` or `Fix document upload validation`. Pull requests should include a concise description, linked issue if applicable, test results (`mvn test`, `npm run build`), screenshots for UI changes, and notes for schema or config changes.

## Security & Configuration Tips

Do not commit `backend/src/main/resources/application-local.yml`, `.env`, database dumps, tokens, or generated folders such as `backend/target`, `frontend/node_modules`, and `frontend/dist`. Keep tenant and knowledge-base access checks server-side, even when the UI hides unauthorized actions.
