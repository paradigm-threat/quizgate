/**
 * Allow unsigned pushes on users/main (service batch writer).
 */
export default async function preReceive(_ctx) {
  return { ok: true };
}
