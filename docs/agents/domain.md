# Domain documentation

This repository uses a single domain context.

Before changing product behavior or architecture:

1. Read `CONTEXT.md`.
2. Read `docs/domain/glossary.md` and use its vocabulary exactly.
3. Read the relevant product rules, use cases, invariants, and state machines.
4. Read every accepted ADR that affects the change.

ADRs live in `docs/adr/`. There is no `CONTEXT-MAP.md` and no per-module context hierarchy. If the repository later becomes a true multi-context monorepo, update this file and introduce a root context map through an explicit documentation change.
