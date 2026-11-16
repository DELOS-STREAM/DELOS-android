import { createHandler } from "../src/handler.js";

export const BASE = "https://delos-reports.example.workers.dev";

/** Workers KV stand-in: remembers every write with its options and honors the expiry against the manual clock. */
export function createKv(clock) {
  const store = new Map(); // key -> { value, expiresAt }
  const kv = {
    store,
    writes: [],
    failReads: false,
    failWrites: false,
    async get(key) {
      if (kv.failReads) throw new Error("KV read failed");
      const entry = store.get(key);
      if (!entry) return null;
      if (entry.expiresAt !== null && clock.now >= entry.expiresAt) {
        store.delete(key);
        return null;
      }
      return entry.value;
    },
    async put(key, value, options = {}) {
      if (kv.failWrites) throw new Error("KV write failed");
      kv.writes.push({ key, value, options });
      store.set(key, { value, expiresAt: options.expirationTtl ? clock.now + options.expirationTtl * 1000 : null });
    },
    reports() {
      return [...store.keys()].filter((key) => key.startsWith("r:"));
    },
  };
  return kv;
}

/** The Worker with a fake KV, a manual clock and a counting random source; every response is checked for the fixed headers. */
export function createClient({ env = {}, handlerOptions = {} } = {}) {
  const clock = { now: Date.parse("2026-11-20T10:00:00Z") };
  const kv = createKv(clock);
  let counter = 0;
  const handler = createHandler({
    now: () => clock.now,
    randomBytes: (length) => Uint8Array.from({ length }, (_, i) => (++counter * 31 + i * 7) % 256),
    ...handlerOptions,
  });
  const fullEnv = { REPORTS: kv, ...env };
  const client = {
    clock,
    kv,
    env: fullEnv,
    async request(path, init = {}) {
      const request = new Request(BASE + path, { method: "GET", ...init });
      const response = await handler(request, fullEnv, { waitUntil() {} });
      const text = await response.text();
      expectNoCors(response);
      return { status: response.status, headers: response.headers, text, json: () => JSON.parse(text) };
    },
    upload(body, headers = {}, ip = "203.0.113.7") {
      return client.request("/v1/reports", {
        method: "POST",
        body,
        headers: { "content-type": "text/plain; charset=utf-8", "cf-connecting-ip": ip, ...headers },
      });
    },
  };
  return client;
}

function expectNoCors(response) {
  const cors = [...response.headers.keys()].filter((name) => name.startsWith("access-control-"));
  if (cors.length > 0) throw new Error(`CORS header present: ${cors.join(", ")}`);
}
