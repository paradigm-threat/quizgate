/**
 * Exact-path user render only — never list users/ or browse the tree.
 * Serves profile.json / days.jsonl / events.jsonl under users/<uuid>/.
 */
export default async function get(ctx) {
  const path = String(ctx?.path || ctx?.url || "").replace(/^\/+/, "");
  if (!path || path === "users" || path === "users/" || /^users\/?$/.test(path)) {
    return { status: 404, body: "not_found" };
  }
  const m = path.match(
    /^users\/([0-9a-fA-F-]{36})\/(profile\.json|days\.jsonl|events\.jsonl)$/,
  );
  if (!m) {
    const bare = path.match(/^users\/([0-9a-fA-F-]{36})\/?$/);
    if (bare) {
      return {
        status: 302,
        headers: { Location: `/users/${bare[1]}/profile.json` },
      };
    }
    return { status: 404, body: "not_found" };
  }
  const file = m[2];
  return {
    status: 200,
    contentType: file.endsWith(".json") ? "application/json" : "application/x-ndjson",
    repoPath: path,
  };
}
