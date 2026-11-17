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
| `api` | A | `162.209.124.53` | iad1 — terminate TLS + proxy to Intake `:8791` |

See [`INTAKE_HOST.md`](INTAKE_HOST.md) for the process that must listen once the host is wired.

## Optional later

| Name | Role |
|------|------|
| `status.quizgate.app` | Ops status page only if needed |

## Verify

```bash
dig api.quizgate.app +short
curl -sS https://api.quizgate.app/health
```
