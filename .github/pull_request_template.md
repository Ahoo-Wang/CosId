<!--
Title: use Conventional Commits, for example `fix(jdbc): retry segment allocation on deadlock`.
The title type labels the pull request and places it in the release notes. See CONTRIBUTING.md.
-->

Fixes #

## What and why

<!-- What does this change do, and why is it needed? -->

## How it was tested

<!-- Tests added or updated, and the modules you ran `./gradlew <module>:check` for. -->

## Checklist

- [ ] Tests cover the change (a bug fix includes a test that fails without it)
- [ ] `./gradlew <module>:check` passes for every touched module (Checkstyle, SpotBugs, tests)
- [ ] Public API, configuration properties and behavior stay compatible, or the title is marked with `!`
- [ ] Documentation (`documentation/`, `README.md`) is updated where behavior or configuration changed
