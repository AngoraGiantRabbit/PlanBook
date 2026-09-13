# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

**This repo is single-context**: one `CONTEXT.md` at the repo root, one `docs/adr/` with sequential ADRs.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — the glossary of this project's domain language (计划本, 灵活/临时/每日/长期任务, 活跃区间, 复盘, 自动待办, 完成态双模型…).
- **`docs/adr/`** — read ADRs that touch the area you're about to work in. Currently: 0001 完成态双模型, 0002 任务日期语义, 0003 长期任务过期自动完成.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/grill-with-docs`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0003 (长期任务过期自动完成) — but worth reopening because…_
