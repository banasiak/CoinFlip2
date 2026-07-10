# Compose Migration — Main + Settings

Documentation for migrating the last two legacy XML screens (Main coin-flip and
Settings) to Jetpack Compose. Committed so the work can be resumed from another
machine.

| Doc | Purpose |
|---|---|
| [STATUS.md](./STATUS.md) | Current progress + the remaining on-device smoke-test checklist |
| [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) | Approved technical plan |
| [WALKTHROUGH.md](./WALKTHROUGH.md) | Summary of changes + verification results |

**State:** COMPLETE. Implementation, static verification (`ktlintCheck`,
44 unit tests, `assembleDebug`, previews), and the on-device smoke test
(Pixel 9a, 2026-07-09) all pass. Two small issues found and fixed during the
smoke test — see [STATUS.md](./STATUS.md).
