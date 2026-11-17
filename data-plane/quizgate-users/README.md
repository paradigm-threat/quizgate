# quizgate-users

Relay SoT for anonymized QuizGate progress. **UUID = access** — no user listing.

| Host | Role |
|------|------|
| `https://api.quizgate.app` | Always-on Intake (cache, enqueue, realtime) |
| Batch SI (Spot) | **Sole permanent tip writer** |
| `git://iad1.relaygateway.net:9418/quizgate-users.git` | Relay publish (branch `users`) |

## Layout

```
users/<uuid>/profile.json
users/<uuid>/days.jsonl
users/<uuid>/events.jsonl
```

Text only. Binaries live on Intake PVC (`cache://…`), never in git.

## Publish

```bash
git push git://iad1.relaygateway.net:9418/quizgate-users.git users
```

Service author for tip commits: `quizgate-batch` / `quizgate-batch@quizgate.app`.
