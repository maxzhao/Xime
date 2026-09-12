# Xime Project Knowledge

Purpose: route Agents to the smallest project-local knowledge area needed for the current work.

## Entrypoints

- [`wiki/index.md`](wiki/index.md): curated, reusable project knowledge and synthesis.
- [`specs/index.md`](specs/index.md): behavior specifications and spec-driven change artifacts.
- [`inbox/index.md`](inbox/index.md): unreviewed captures, feedback, and curation queue.
- [`attachments/index.md`](attachments/index.md): durable shared files without a more specific semantic owner.
- [`canvases/index.md`](canvases/index.md): Obsidian Canvas artifacts and their routing notes.
- `tasks/`: TaskAdmin-owned task files; load `task-admin` before task operations.
- `drafts/`: temporary, Git-ignored drafts; do not treat as durable knowledge.
- `translate-cache/`: generated translation/cache content; do not treat as source of truth.

## Routing Rules

1. Start with the category matching the current information need.
2. Follow direct child indexes rather than scanning the whole Vault.
3. Put uncategorized captures in `inbox/` and record curation actions in `inbox/log.md`.
4. Create optional root categories only when actual project content needs them; each new maintained root category requires `index.md` and `log.md`.
5. Do not create retired `.supermax/knowledge/` or root `raw/` paths.
