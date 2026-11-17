# QuizGate data plane

Always-on Intake + batch SI + Relay SoT for anonymized per-UUID progress.

- Public API: **https://api.quizgate.app**
- Tip repo: `quizgate-users` (branch `users`)
- Docs: [`../docs/DATA_PLANE.md`](../docs/DATA_PLANE.md)

```bash
npm test          # node --test
npm run intake    # :8791
npm run batch     # drain queue → tip commits as quizgate-batch
```
