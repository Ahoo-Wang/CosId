# Contributing to CosId

Thanks for helping improve CosId. This guide explains how changes move from an idea to a release.

## Reporting bugs and requesting features

- Search [open and closed issues](https://github.com/Ahoo-Wang/CosId/issues?q=is%3Aissue) first.
- Use the **Bug Report** or **Feature Request** template. For bugs, include the CosId version, the backend
  (Redis, JDBC, ZooKeeper, MongoDB, proxy), and a minimal reproduction.
- Questions about usage go to [Discussions](https://github.com/Ahoo-Wang/CosId/discussions).
- Security vulnerabilities must **not** be reported in public issues. See [SECURITY.md](./SECURITY.md).

Larger changes (new backends, API changes, behavior changes) should start with an issue so the design can be
agreed on before code is written. Planned work is tracked in [docs/ROADMAP.md](./docs/ROADMAP.md).

## Development setup

- **JDK 17** (the Gradle toolchain is pinned to 17).
- Always use the Gradle wrapper: `./gradlew`.
- Enable Lombok annotation processing in your IDE.
- Your editor should honor [`.editorconfig`](./.editorconfig) (UTF-8, LF, 4-space indent for Java/Kotlin).

```bash
# Full verification of one module: compile, Checkstyle, SpotBugs, tests, JaCoCo
./gradlew cosid-core:check

# A single test class
./gradlew cosid-core:test --tests "me.ahoo.cosid.segment.DefaultSegmentIdTest"

# Aggregated coverage report (build/reports under code-coverage-report/)
./gradlew codeCoverageReport
```

Backend modules need the matching service for their integration tests: MySQL for `cosid-jdbc` and
`cosid-spring-boot-starter` (initialize it with `cosid-jdbc/src/main/init-script/init-cosid-mysql.sql`),
Redis on `localhost:6379` for `cosid-spring-redis`. ZooKeeper tests start an in-process server, MongoDB tests use
Testcontainers (Docker required).
CI runs all of them, see `.github/workflows/integration-test.yml`.

## Branching model

- `main` is the only long-lived development branch and is always releasable.
- Maintenance lines live on `1.x` (Spring Boot 2 / Java 8) and `2.x` (Spring Boot 3).
- Work on a short-lived topic branch (for example `fix/segment-overflow` or `feat/proxy-auth`) created from
  `main`, or from the maintenance branch you target. Keep it focused on one change.
- Pull requests are squash-merged, so the pull request title becomes the commit message on `main`.

## Commit messages and pull request titles

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<optional scope>)<optional !>: <description>
```

| Type       | Use for                                   | Release notes section |
|------------|-------------------------------------------|-----------------------|
| `feat`     | a new feature                             | New Features          |
| `fix`      | a bug fix                                 | Bug Fixes             |
| `perf`     | a performance improvement                 | Performance           |
| `docs`     | documentation only                        | Documentation         |
| `refactor` | code change without behavior change       | Maintenance           |
| `test`     | adding or fixing tests                    | Maintenance           |
| `build`, `ci`, `chore`, `style`, `revert` | build, CI, tooling | Maintenance           |

- Use the module as the scope where it helps: `fix(jdbc): ...`, `feat(proxy): ...`. Dependency updates use the
  `deps` scope: `fix(deps): ...`.
- Mark breaking changes with `!` (`feat(core)!: ...`) or a `BREAKING CHANGE:` line in the pull request body.

The **Pull Request Title** check enforces this format. The **Pull Request Labeler** then labels the pull request
from its title type (used to group the release notes) and from the files it changes (`module: *`, `area: *`).

## Pull requests and code review

Before you open a pull request:

1. Add or update tests. Bug fixes come with a test that fails without the fix.
2. Run `./gradlew <module>:check` for every module you touched. Checkstyle and SpotBugs failures fail the build.
3. Keep the Apache 2.0 license header at the top of every Java file (Checkstyle enforces it, the header text is
   in `config/checkstyle/java.header`).
4. Update the documentation (`documentation/`, `README.md`) when behavior or configuration changes.

Every pull request needs:

- passing CI (integration tests per module, CodeQL, coverage upload);
- coverage that meets the [Codecov](./codecov.yml) gates: 60% on the patch, 80% for the project;
- an approving review from a maintainer (see [CODEOWNERS](./.github/CODEOWNERS)).

Reviewers check correctness first (concurrency, clock and lease handling, backend failure modes), then
compatibility of public APIs and configuration properties, tests, and documentation.

## Architecture rules

- `cosid-core` has no Spring, database or middleware dependencies.
- Backends depend on core, the Spring Boot starter depends on backends; never the other way round.
- Dependency versions are managed in `cosid-dependencies` and `gradle/libs.versions.toml`, not in single modules.

See [AGENTS.md](./AGENTS.md) for the module layout and known pitfalls.

## Releases and versioning

CosId follows [Semantic Versioning](https://semver.org/):

- **MAJOR** for breaking API, configuration or behavior changes;
- **MINOR** for backwards compatible features;
- **PATCH** for backwards compatible fixes.

A release is cut by a maintainer:

1. Bump `version=` in `gradle.properties` with a `chore(release): bump version to x.y.z` pull request.
2. Create a GitHub release with the tag `vx.y.z` on that commit. The release notes are generated from the
   pull request labels (see `.github/release.yml`).
3. Publishing the release runs `package-deploy.yml`, which first checks that the tag matches
   `gradle.properties`, then signs and publishes the artifacts to Maven Central and GitHub Packages.
   `docker-deploy.yml` publishes the `cosid-proxy` image for the tag.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](./LICENSE).
