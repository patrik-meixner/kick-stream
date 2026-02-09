# Repository Guidelines

## Project Structure & Module Organization

This repository is in an initial state and currently contains only documentation and meta files. Expected additions as the project evolves:

- `src/` for application source code (likely Java/Kotlin).
- `test/` or `src/test/` for unit/integration tests.
- `resources/` for configuration or static assets.
- Build files such as `build.gradle.kts` or `build.gradle` once Gradle is set up.

## Build, Test, and Development Commands

A build system is not configured yet. When Gradle is added, common commands will be listed here (examples):

- `./gradlew build` builds the project.
- `./gradlew test` runs all tests.
- `./gradlew test --tests "com.example.MyTest"` runs a single test.

## Coding Style & Naming Conventions

No formal style rules are defined yet. Until conventions are documented:

- Follow standard Java/Kotlin conventions.
- Use 4-space indentation in Kotlin/Java files.
- Prefer descriptive class and method names, e.g., `StreamSessionManager`.

If a formatter or linter is introduced (e.g., `ktlint`, `spotless`), document it here with the exact command.

## Testing Guidelines

No testing framework is configured yet. Once added, this section should specify:

- Test framework (e.g., JUnit 5).
- Coverage expectations, if any.
- Naming conventions like `*Test` suffixes.

## Commit & Pull Request Guidelines

There is only an initial commit, so no established convention exists yet. For now:

- Use concise, imperative commit messages (e.g., `Add Gradle build`).
- Keep commits scoped to a single change when possible.

For pull requests:

- Include a clear description of the change and rationale.
- Link any related issues if applicable.
- Add screenshots or logs if the change affects UX or runtime behavior.

## Agent-Specific Instructions

`CLAUDE.md` points to this file as the single source of truth for contributor guidance. Update `AGENTS.md` when workflows or conventions change.
