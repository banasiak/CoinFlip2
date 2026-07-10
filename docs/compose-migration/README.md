# Compose Migration — Main + Settings

Documentation for migrating the last two legacy XML screens (Main coin-flip and
Settings) to Jetpack Compose. Committed so the work can be resumed from another
machine.

| Doc | Purpose |
|---|---|
| [STATUS.md](./STATUS.md) | Current progress + the remaining on-device smoke-test checklist |
| [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) | Approved technical plan |
| [WALKTHROUGH.md](./WALKTHROUGH.md) | Summary of changes + verification results |

**State at hand-off:** implementation and static verification complete
(`ktlintCheck` passes, 44 unit tests pass, `assembleDebug` succeeds, previews
render). Only the manual on-device smoke test remains.
