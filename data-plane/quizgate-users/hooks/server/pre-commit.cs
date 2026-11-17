/**
 * Reject binaries under users/; reject enumeration helpers; only users/ paths.
 */
export default async function preCommit(ctx) {
  const files = ctx?.files || [];
  for (const f of files) {
    const p = String(f.path || f || "");
    if (p.startsWith("hooks/") || p.startsWith("schema/") || p === ".relay.yaml" || p === "README.md") {
      continue;
    }
    if (!p.startsWith("users/")) {
      return { ok: false, error: "path_outside_users_forbidden" };
    }
    if (/\.(bin|wav|mp3|m4a|aac|jpg|jpeg|png|gif|webp|mp4|mov|pdf|zip)$/i.test(p)) {
      return { ok: false, error: "binaries_forbidden_under_users" };
    }
    if (/users\/(index\.(html|json|md)|list\.json|\.listing)$/i.test(p)) {
      return { ok: false, error: "user_enumeration_forbidden" };
    }
  }
  return { ok: true };
}
