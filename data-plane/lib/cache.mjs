/** Simple in-memory + disk request cache for Intake. */
import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync, readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CACHE_DIR = process.env.QUIZGATE_CACHE_DIR || join(__dirname, "../.data/cache");
const mem = new Map();

function keyOf(parts) {
  return createHash("sha256").update(parts.join("|")).digest("hex").slice(0, 32);
}

export function cacheGet(namespace, key, maxAgeMs = 60_000) {
  const k = keyOf([namespace, key]);
  const hit = mem.get(k);
  if (hit && Date.now() - hit.at < maxAgeMs) return hit.value;
  const fp = join(CACHE_DIR, namespace, `${k}.json`);
  if (!existsSync(fp)) return null;
  try {
    const parsed = JSON.parse(readFileSync(fp, "utf8"));
    if (Date.now() - parsed.at < maxAgeMs) {
      mem.set(k, parsed);
      return parsed.value;
    }
  } catch {
    return null;
  }
  return null;
}

export function cacheSet(namespace, key, value) {
  const k = keyOf([namespace, key]);
  const rec = { at: Date.now(), value };
  mem.set(k, rec);
  const dir = join(CACHE_DIR, namespace);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, `${k}.json`), JSON.stringify(rec));
  return value;
}

export function cacheDedupePost(namespace, bodyHash, maxAgeMs = 30_000) {
  const existing = cacheGet(namespace, bodyHash, maxAgeMs);
  if (existing) return { duplicate: true, value: existing };
  return { duplicate: false };
}
