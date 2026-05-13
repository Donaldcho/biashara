# Git branches — navigation and policy

This document is the **single map** for which branch to use and how it relates to `main`.  
Keep it updated when you add long-lived integration lines or change the default PR target.

---

## Quick reference (copy–paste)

| I want to… | Command |
|------------|---------|
| See current branch | `git branch --show-current` |
| List remote branches | `git branch -r` |
| Fetch everything | `git fetch origin` |
| Work on **Phase 4 prep** (chat, DB v15, HANDOFF A0) | `git fetch origin && git checkout feat/phase4-lineage-v1` |
| Work on **POS feature line** (historical) | `git fetch origin && git checkout feat/pos-module` |
| Start a **new** task from latest integration | `git fetch origin && git checkout feat/phase4-lineage-v1 && git pull && git checkout -b feat/<short-topic>` |

---

## Branch lineage (no implicit merges)

```text
main
 └── feat/pos-module          (POS / earlier integration — pushed earlier)
       └── feat/phase4-lineage-v1   (current: HANDOFF A0, chat sessions, v15, …)
```

- **`main`** — release-aligned default; treat as **protected** (PR + review + CI before merge).
- **`origin/feat/pos-module`** — prior integration branch; **not replaced** by the new line.
- **`origin/feat/phase4-lineage-v1`** — **dedicated integration branch** for work queued before **Prompt A1** (agent DB migrations).  
  - **Do not merge locally into `main` without a PR** — push this branch and open a PR so history and review stay traceable.

---

## Naming convention (DevOps-friendly)

| Prefix | Use for |
|--------|---------|
| `feat/` | New behaviour users or agents rely on |
| `fix/` | Bug fixes |
| `docs/` | Documentation only (e.g. HANDOFF, this file) |
| `chore/` | Tooling, CI, Gradle housekeeping |
| `refactor/` | Internal structure with no intended behaviour change |

Use **kebab-case** and a **short topic**: `feat/agent-approval-ui`, not `branch2`.

---

## Workflow principles

1. **One PR per coherent slice** when possible; this `feat/phase4-lineage-v1` branch may bundle several prompts — split follow-ups into smaller PRs from here.
2. **Push early, push often** to the remote feature branch; avoids single-machine loss of work.
3. **No `--force` on shared branches** (`main`, widely used `feat/*`) unless the team explicitly agrees (e.g. after secret leak).
4. **Linear preference**: merge PRs with “squash” or “merge commit” per team policy; document the choice in your org’s CONTRIBUTING if you add one.
5. **CI**: run `.\scripts\preflight-build.ps1` and `./gradlew.bat assembleDebug` (or CI) before requesting review.

---

## Where prompt / phase state lives

- **`HANDOFF.md`** — authoritative **prompt tracker** (last completed, next prompt, phase, DB version).
- **This file** — authoritative **branch map** only; do not duplicate long feature lists here (link to HANDOFF).

---

## Remote setup (first time)

```powershell
cd "<repo-root>"
git remote -v
# If origin is missing:
# git remote add origin <your-git-url>
```

Push a new branch and set upstream:

```powershell
git push -u origin feat/phase4-lineage-v1
```

---

## Changelog (branch doc)

| Date | Change |
|------|--------|
| 2026-05-12 | Added `feat/phase4-lineage-v1` as Phase 4 prep integration line; documented lineage from `feat/pos-module`. |
