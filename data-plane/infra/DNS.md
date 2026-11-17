# quizgate.app DNS / TLS (data plane)

All public hosts are **`*.quizgate.app`**. Do not use clevertree.* for QuizGate APIs.

## Current (marketing)

| Name | Type | Target |
|------|------|--------|
| `@` / apex | Vercel | project `quizgate` |
| `www` | 308 → apex | Vercel |

Nameservers (Namecheap Custom DNS): `ns1.vercel-dns.com`, `ns2.vercel-dns.com`.

## Required for Intake

| Name | Type | Target |
|------|------|--------|
| `api` | **A** or **CNAME** | Intake host (iad1 park / load balancer IP) |

TLS: terminate on Intake (Caddy/park) for `api.quizgate.app`, or Vercel proxy if using serverless edge in front (not default — Intake is always-on Node).

### Current DNS (Vercel DNS zone)

| Name | Type | Value | Notes |
|------|------|-------|-------|
| `api` | A | `162.209.124.53` | iad1 — Caddy on-demand TLS → Intake `:8791` |

Live as of 2026-11-17: Let's Encrypt cert for `api.quizgate.app`; `/health` returns Intake JSON. See [`INTAKE_HOST.md`](INTAKE_HOST.md).

TLS ask allowlist: keep `api.quizgate.app` in `/etc/caddy/on-demand-extra.txt` (survives park regenerations of `parked-domains.txt`). Do **not** add it to Relay catalog `domains[]` (that parks Host → gateway `:8080`).

## Admin UI

| Name | Type | Target |
|------|------|--------|
| `admin` | CNAME → `cname.vercel-dns.com` | Vercel project `quizgate`; host redirect `/` → `/admin/` |

Verified live: `https://admin.quizgate.app` → remote admin login.

## Optional later

| Name | Role |
|------|------|
| `status.quizgate.app` | Ops status page only if needed |

## Verify

```bash
dig api.quizgate.app +short   # expect 162.209.124.53
curl -sS https://api.quizgate.app/health
```
