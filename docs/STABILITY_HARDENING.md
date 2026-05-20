# Stability hardening (deployment prep)

## Symptoms addressed

- Periodic app closes / “scratches” during normal use
- Jank after cold start (heavy bootstrap on main path)
- Background WorkManager storms after bulk POS writes

## Fixes (release/pro-beta)

| Area | Change |
|------|--------|
| Startup | Defer model/skills/knowledge ingest by 4s; enterprise device register only for Enterprise Pro |
| WorkManager | Debounce fraud-reactive enqueue (30s); remove redundant immediate enterprise sync on every launch |
| Enterprise sync | Audit/sync APIs wrapped in `runCatching`; worker no longer infinite-retry crash loop |
| Insights UI | Null-safe view binding + `post {}` callbacks after `onDestroyView` |
| Settings events | Snackbars only when `_binding` is alive |
| ViewModels | `launchSafe` on IO with logged coroutine failures |
| Crash diagnosis | `AppCrashGuard` logs uncaught exceptions to logcat tag `BiasharaCrash` |

## Pre-release verification

1. Cold start → home feed → Settings → Insights (change period chips).
2. Rapid POS sales (10+ lines) — app stays responsive.
3. Enterprise Pro: save deployment, sync queue, no crash if endpoint offline.
4. Logcat: filter `BiasharaCrash`, `EnterpriseAudit`, `CashFlowInsightsVM`.

## If crashes persist

Capture `adb logcat -d | findstr BiasharaCrash` after a repro and attach the stack trace.
