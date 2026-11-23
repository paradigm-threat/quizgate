# QuizGate curricula (source of truth)

Signed `.qgpack` zips are built from folders here. The Android APK bundles
`basic-math` **v8** as a fallback (L0 = sums ≤5 MC-only for first-contact success).
**All users** get updates from the shared index:

`https://quizgate.app/curricula/index.json` → download `.qgpack` →
`files/curricula/{id}/` on device (offline).

`bundled: true` in the index means the pack also ships in the APK; devices still
sync when `packUrl` is set. Sync compares the index to the **installed** dir
(not the APK), so the first pull always fills `files/curricula/` for offline use.

## Commands

```bash
python3 curricula/scripts/sync_pack_hashes.py   # sha + qgpack + index version/packUrl
python3 curricula/scripts/validate_packs.py
python3 curricula/scripts/si_batch_review.py
./scripts/deploy_site.sh --prod                 # publish index + packs
```

SI = Super Intelligence (i.e. AI) — batch curation only; humans publish.
