import { gzipSync } from "node:zlib";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DAILY_LIMIT, MAX_BYTES, TTL_SECONDS } from "../src/handler.js";
import { BASE, createClient } from "./harness.js";

const REPORT = [
  "Delos diagnostic log",
  "2026-11-10 12:34:56.789 I/Stream [main] Negotiated HEVC Main10 at 1920x1080@120",
  "2026-11-10 12:34:57.001 I/Lime [main] Starting parallel poll for <pc-1> (<ip-1>:47989, null, null, null)",
].join("\n");
const DAY = 24 * 60 * 60 * 1000;

afterEach(() => vi.restoreAllMocks());

describe("upload and read", () => {
  it("stores the text and serves it back through the link", async () => {
    const client = createClient();
    const created = await client.upload(REPORT);
    expect(created.status).toBe(201);
    const body = created.json();
    expect(Object.keys(body).sort()).toEqual(["expires", "id", "rescrubbed", "url"]);
    expect(body.id).toMatch(/^[A-Za-z0-9_-]{22}$/);
    expect(body.url).toBe(`${BASE}/r/${body.id}`);
    expect(body.rescrubbed).toBe(0);
    expect(created.headers.get("content-type")).toBe("application/json; charset=utf-8");
    expect(created.headers.get("cache-control")).toBe("no-store");
    expect(created.headers.get("location")).toBe(body.url);

    const read = await client.request(`/r/${body.id}`);
    expect(read.status).toBe(200);
    expect(read.text).toBe(REPORT);
    expect(read.headers.get("content-type")).toBe("text/plain; charset=utf-8");
    expect(read.headers.get("x-robots-tag")).toContain("noindex");
    expect(read.headers.get("cache-control")).toBe("no-store");
    expect(read.headers.get("x-content-type-options")).toBe("nosniff");
    expect(read.headers.get("referrer-policy")).toBe("no-referrer");
    expect(read.headers.get("content-security-policy")).toContain("default-src 'none'");
  });

  it("makes every id different and 128 bits long", async () => {
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    const ids = new Set();
    for (let i = 0; i < 5; i++) ids.add((await client.upload(REPORT, {}, `203.0.113.${i}`)).json().id);
    expect(ids.size).toBe(5);
    // 16 bytes of base64url without padding are 22 characters
    for (const id of ids) expect(Buffer.from(id, "base64url")).toHaveLength(16);
  });

  it("uses the real random source by default", async () => {
    const { createHandler } = await import("../src/handler.js");
    const handler = createHandler();
    const kv = createClient().kv;
    const call = () => handler(new Request(`${BASE}/v1/reports`, { method: "POST", body: REPORT, headers: { "content-type": "text/plain" } }), { REPORTS: kv });
    const [a, b] = [await (await call()).json(), await (await call()).json()];
    expect(a.id).not.toBe(b.id);
  });

  it("expires after 30 days: the TTL goes to KV, the date is in the answer and the read stops", async () => {
    const client = createClient();
    const created = (await client.upload(REPORT)).json();
    const put = client.kv.writes.find((write) => write.key === `r:${created.id}`);
    expect(put.options).toEqual({ expirationTtl: TTL_SECONDS });
    expect(TTL_SECONDS).toBe(30 * 86_400);
    expect(created.expires).toBe(new Date(client.clock.now + 30 * DAY).toISOString());

    client.clock.now += 30 * DAY - 1000;
    expect((await client.request(`/r/${created.id}`)).status).toBe(200);
    client.clock.now += 1000;
    const gone = await client.request(`/r/${created.id}`);
    expect(gone.status).toBe(404);
    expect(gone.json()).toEqual({ error: "not_found" });
  });

  it("answers 404 for an id that was never issued or is malformed", async () => {
    const client = createClient();
    for (const path of ["/r/AAAAAAAAAAAAAAAAAAAAAA", "/r/short", "/r/AAAAAAAAAAAAAAAAAAAAAAA", "/r/AAAAAAAAAAAAAAAAAAAA+A", "/r/", "/r/A/B", "/r/..%2F..", "/r"]) {
      const response = await client.request(path);
      expect(response.status, path).toBe(404);
      expect(response.json()).toEqual({ error: "not_found" });
    }
  });

  it("keeps the report text and the counter apart in KV", async () => {
    const client = createClient();
    const { id } = (await client.upload(REPORT)).json();
    expect([...client.kv.store.keys()].sort()).toEqual(["cap:2026-11-20", `r:${id}`].sort());
    // A link can never read the counter
    expect((await client.request("/r/cap:2026-11-20")).status).toBe(404);
  });
});

describe("what the Worker accepts", () => {
  it("takes gzip", async () => {
    const client = createClient();
    const created = await client.upload(gzipSync(Buffer.from(REPORT)), { "content-encoding": "gzip" });
    expect(created.status).toBe(201);
    expect((await client.request(`/r/${created.json().id}`)).text).toBe(REPORT);
  });

  it("takes UTF-8 text with multi-byte characters and a charset in any case", async () => {
    const client = createClient();
    const text = "Spuštěno 日本語 🎮";
    const created = await client.upload(Buffer.from(text, "utf8"), { "content-type": 'Text/Plain; Charset="UTF-8"' });
    expect(created.status).toBe(201);
    expect((await client.request(`/r/${created.json().id}`)).text).toBe(text);
  });

  it("rejects a body over 2 MiB, announced or not", async () => {
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    expect(MAX_BYTES).toBe(2 * 1024 * 1024);
    const announced = await client.upload("x", { "content-length": String(MAX_BYTES + 1) });
    expect(announced.status).toBe(413);
    expect(announced.json()).toEqual({ error: "too_large" });
    const chunk = new Uint8Array(1024 * 1024).fill(97);
    const stream = () =>
      new ReadableStream({
        start(controller) {
          controller.enqueue(chunk);
          controller.enqueue(chunk);
          controller.enqueue(new Uint8Array([97]));
          controller.close();
        },
      });
    const streamed = await client.request("/v1/reports", { method: "POST", body: stream(), duplex: "half", headers: { "content-type": "text/plain" } });
    expect(streamed.status).toBe(413);
    expect(client.kv.reports()).toEqual([]);
    const exact = await client.upload("a".repeat(MAX_BYTES), {}, "203.0.113.50");
    expect(exact.status).toBe(201);
  });

  it("rejects a gzip bomb by what it inflates to", async () => {
    const client = createClient();
    const bomb = gzipSync(Buffer.alloc(MAX_BYTES + 10, 97));
    expect(bomb.length).toBeLessThan(10_000);
    const response = await client.upload(bomb, { "content-encoding": "gzip" });
    expect(response.status).toBe(413);
    expect(client.kv.reports()).toEqual([]);
  });

  it("rejects bytes that are not gzip when gzip is announced", async () => {
    const client = createClient();
    const response = await client.upload("plain text", { "content-encoding": "gzip" });
    expect(response.status).toBe(400);
    expect(response.json()).toEqual({ error: "invalid_request" });
  });

  it("rejects an encoding other than gzip", async () => {
    const client = createClient();
    const response = await client.upload(REPORT, { "content-encoding": "br" });
    expect(response.status).toBe(415);
    expect(response.json()).toEqual({ error: "unsupported_encoding" });
  });

  it("rejects what is not valid UTF-8", async () => {
    const client = createClient();
    for (const bytes of [[0xff, 0xfe, 0x41], [0xc3, 0x28], [0x41, 0xed, 0xa0, 0x80]]) {
      const response = await client.upload(Uint8Array.from(bytes));
      expect(response.status).toBe(400);
      expect(response.json()).toEqual({ error: "invalid_request" });
    }
  });

  it("rejects a NUL byte, an empty body and a blank body", async () => {
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    const bodies = ["abc\u0000def", "", "   \n\t "];
    for (const [i, body] of bodies.entries()) {
      const response = await client.upload(body, {}, `203.0.113.${i + 1}`);
      expect(response.status, JSON.stringify(body)).toBe(400);
    }
    expect(client.kv.reports()).toEqual([]);
  });

  it("rejects any content type but text/plain in UTF-8", async () => {
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    const types = ["application/json", "text/html", "application/octet-stream", "multipart/form-data; boundary=x", "text/plain; charset=iso-8859-1", "text/plainx"];
    for (const [i, type] of types.entries()) {
      const response = await client.upload(REPORT, { "content-type": type }, `203.0.113.${i + 1}`);
      expect(response.status, type).toBe(415);
      expect(response.json()).toEqual({ error: "unsupported_media_type" });
    }
    const none = await client.request("/v1/reports", { method: "POST", body: new Uint8Array([97]), headers: { "content-type": "" } });
    expect(none.status).toBe(415);
    expect(client.kv.reports()).toEqual([]);
  });

  it("answers 504 when the body does not arrive within the work limit", async () => {
    const client = createClient({ handlerOptions: { workMs: 40 } });
    const stalled = new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode("start")); } });
    const response = await client.request("/v1/reports", { method: "POST", body: stalled, duplex: "half", headers: { "content-type": "text/plain" } });
    expect(response.status).toBe(504);
    expect(response.json()).toEqual({ error: "timeout" });
    expect(client.kv.reports()).toEqual([]);
  });

  it("rejects a missing body", async () => {
    const client = createClient();
    const response = await client.request("/v1/reports", { method: "POST", headers: { "content-type": "text/plain" } });
    expect(response.status).toBe(400);
  });
});

describe("the second scrub", () => {
  it("replaces a leftover with the app's placeholder, stores the clean text and says how many", async () => {
    const client = createClient();
    const dirty = `${REPORT}\nleftover 192.168.7.7 and 00:1A:2B:3C:4D:5E and bob@example.com and token=abc123`;
    const created = (await client.upload(dirty)).json();
    expect(created.rescrubbed).toBe(4);
    const stored = (await client.request(`/r/${created.id}`)).text;
    expect(stored).toBe(`${REPORT}\nleftover <ip-2> and <mac-1> and <email> and token=<redacted>`);
    for (const secret of ["192.168.7.7", "00:1A:2B:3C:4D:5E", "bob@example.com", "abc123"]) expect(stored).not.toContain(secret);
    expect([...client.kv.store.values()].every((entry) => !entry.value.includes("192.168.7.7"))).toBe(true);
  });

  it("scrubs the report before the gzip copy is stored as text", async () => {
    const client = createClient();
    const created = (await client.upload(gzipSync(Buffer.from("host at 10.1.2.3")), { "content-encoding": "gzip" })).json();
    expect(created.rescrubbed).toBe(1);
    expect((await client.request(`/r/${created.id}`)).text).toBe("host at <ip-1>");
  });
});

describe("routing and method policy", () => {
  it("answers a wrong method with 405 and an Allow header", async () => {
    const client = createClient();
    const id = (await client.upload(REPORT)).json().id;
    for (const method of ["GET", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"]) {
      const response = await client.request("/v1/reports", { method });
      expect(response.status, method).toBe(405);
      expect(response.headers.get("allow")).toBe("POST");
      expect(response.json()).toEqual({ error: "method_not_allowed" });
    }
    for (const method of ["POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"]) {
      const init = method === "POST" || method === "PUT" || method === "PATCH" ? { method, body: "x" } : { method };
      const response = await client.request(`/r/${id}`, init);
      expect(response.status, method).toBe(405);
      expect(response.headers.get("allow")).toBe("GET");
    }
    // Nothing deletes or lists
    expect(client.kv.reports()).toHaveLength(1);
  });

  it("answers anything else with 404, for every method", async () => {
    const client = createClient();
    for (const path of ["/", "/v1", "/v1/reports/", "/v1/reports/list", "/v1/report", "/v2/reports", "/reports", "/r/x/y", "/list", "/admin", "/__proto__", "/favicon.ico"]) {
      for (const method of ["GET", "POST", "DELETE"]) {
        const response = await client.request(path, method === "POST" ? { method, body: "x" } : { method });
        expect(response.status, `${method} ${path}`).toBe(404);
        expect(response.json()).toEqual({ error: "not_found" });
      }
    }
  });

  it("sets no CORS headers, not even for a preflight", async () => {
    const client = createClient();
    const preflight = await client.request("/v1/reports", { method: "OPTIONS", headers: { origin: "https://evil.example", "access-control-request-method": "POST" } });
    expect(preflight.status).toBe(405);
    const post = await client.upload(REPORT, { origin: "https://evil.example" });
    expect(post.status).toBe(201);
  });
});

describe("abuse limits", () => {
  it("returns 429 with Retry-After when the Rate Limiting binding refuses, keyed by the client address", async () => {
    const keys = [];
    let allowed = 1;
    const client = createClient({ env: { UPLOAD_LIMITER: { limit: async ({ key }) => { keys.push(key); return { success: allowed-- > 0 }; } } } });
    expect((await client.upload(REPORT, {}, "198.51.100.1")).status).toBe(201);
    const limited = await client.upload(REPORT, {}, "198.51.100.1");
    expect(limited.status).toBe(429);
    expect(limited.json()).toEqual({ error: "rate_limited" });
    expect(limited.headers.get("retry-after")).toBe("120");
    expect(keys).toEqual(["198.51.100.1", "198.51.100.1"]);
    expect(client.kv.reports()).toHaveLength(1);
  });

  it("allows 5 uploads per client in 10 minutes without the binding, then refills one per 2 minutes", async () => {
    const client = createClient();
    const statuses = [];
    for (let i = 0; i < 7; i++) statuses.push((await client.upload(REPORT, {}, "198.51.100.2")).status);
    expect(statuses).toEqual([201, 201, 201, 201, 201, 429, 429]);
    expect((await client.upload(REPORT, {}, "198.51.100.3")).status).toBe(201);
    client.clock.now += 120_000;
    expect((await client.upload(REPORT, {}, "198.51.100.2")).status).toBe(201);
    expect((await client.upload(REPORT, {}, "198.51.100.2")).status).toBe(429);
    client.clock.now += 600_000;
    for (let i = 0; i < 5; i++) expect((await client.upload(REPORT, {}, "198.51.100.2")).status).toBe(201);
  });

  it("falls back to the bucket when the binding throws", async () => {
    const client = createClient({ env: { UPLOAD_LIMITER: { limit: async () => { throw new Error("binding down"); } } } });
    const statuses = [];
    for (let i = 0; i < 6; i++) statuses.push((await client.upload(REPORT, {}, "198.51.100.4")).status);
    expect(statuses).toEqual([201, 201, 201, 201, 201, 429]);
  });

  it("counts a refused upload too (rejected input is limited like any other)", async () => {
    const client = createClient();
    for (let i = 0; i < 5; i++) expect((await client.upload("", {}, "198.51.100.5")).status).toBe(400);
    expect((await client.upload(REPORT, {}, "198.51.100.5")).status).toBe(429);
  });

  it("limits reads per client", async () => {
    const keys = [];
    let allowed = 2;
    const client = createClient({ env: { READ_LIMITER: { limit: async ({ key }) => { keys.push(key); return { success: allowed-- > 0 }; } } } });
    const headers = { "cf-connecting-ip": "198.51.100.6" };
    expect((await client.request("/r/AAAAAAAAAAAAAAAAAAAAAA", { headers })).status).toBe(404);
    expect((await client.request("/r/AAAAAAAAAAAAAAAAAAAAAA", { headers })).status).toBe(404);
    const limited = await client.request("/r/AAAAAAAAAAAAAAAAAAAAAA", { headers });
    expect(limited.status).toBe(429);
    expect(limited.headers.get("retry-after")).toBe("30");
    expect(keys).toEqual(["198.51.100.6", "198.51.100.6", "198.51.100.6"]);
  });

  it("limits reads without the binding: a burst of 60 per client, then 429", async () => {
    const client = createClient();
    const headers = { "cf-connecting-ip": "198.51.100.7" };
    const statuses = [];
    for (let i = 0; i < 62; i++) statuses.push((await client.request("/r/AAAAAAAAAAAAAAAAAAAAAA", { headers })).status);
    expect(statuses.slice(0, 60).every((status) => status === 404)).toBe(true);
    expect(statuses.slice(60)).toEqual([429, 429]);
  });

  it("stops at the daily cap, and starts again the next UTC day", async () => {
    expect(DAILY_LIMIT).toBe(400);
    const client = createClient({ handlerOptions: { dailyLimit: 3 }, env: { UPLOAD_LIMITER: undefined } });
    for (let i = 0; i < 3; i++) expect((await client.upload(REPORT, {}, `203.0.113.${i}`)).status).toBe(201);
    const capped = await client.upload(REPORT, {}, "203.0.113.9");
    expect(capped.status).toBe(503);
    expect(capped.json()).toEqual({ error: "daily_limit" });
    expect(capped.headers.get("retry-after")).toBe("3600");
    expect(client.kv.reports()).toHaveLength(3);
    // The counter's own entry expires on its own
    const counter = client.kv.writes.filter((write) => write.key === "cap:2026-11-20");
    expect(counter.map((write) => write.value)).toEqual(["1", "2", "3"]);
    expect(counter[0].options.expirationTtl).toBe(2 * 86_400);

    client.clock.now = Date.parse("2026-11-21T00:00:01Z");
    expect((await client.upload(REPORT, {}, "203.0.113.10")).status).toBe(201);
  });

  it("does not count a refused upload against the cap", async () => {
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    await client.upload("", {}, "203.0.113.1");
    await client.upload(REPORT, { "content-type": "text/html" }, "203.0.113.2");
    expect(client.kv.writes).toEqual([]);
  });
});

describe("storage failures", () => {
  it("answers 503 when KV is not bound", async () => {
    const client = createClient({ env: { REPORTS: undefined } });
    const response = await client.upload(REPORT);
    expect(response.status).toBe(503);
    expect(response.json()).toEqual({ error: "not_configured" });
    expect((await client.request("/r/AAAAAAAAAAAAAAAAAAAAAA")).status).toBe(503);
  });

  it("answers 503 when KV fails, with no detail", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    client.kv.failWrites = true;
    const write = await client.upload(REPORT, {}, "203.0.113.1");
    expect(write.status).toBe(503);
    expect(write.json()).toEqual({ error: "storage_unavailable" });
    client.kv.failWrites = false;
    client.kv.failReads = true;
    expect((await client.upload(REPORT, {}, "203.0.113.2")).json()).toEqual({ error: "storage_unavailable" });
    expect((await client.request("/r/AAAAAAAAAAAAAAAAAAAAAA")).json()).toEqual({ error: "storage_unavailable" });
  });
});

describe("what the Worker logs", () => {
  it("never logs a body, a link or an address, not even on a failure", async () => {
    const spies = ["log", "info", "warn", "error", "debug"].map((name) => vi.spyOn(console, name).mockImplementation(() => {}));
    const secret = "TOP-SECRET-BODY-TEXT 192.168.250.9 bob@example.com";
    const client = createClient({ env: { UPLOAD_LIMITER: undefined } });
    const created = (await client.upload(secret, {}, "203.0.113.77")).json();
    await client.request(`/r/${created.id}`, { headers: { "cf-connecting-ip": "203.0.113.77" } });
    client.kv.failWrites = true;
    await client.upload(secret, {}, "203.0.113.78");
    await client.upload("\u0000", {}, "203.0.113.79");
    const logged = spies.flatMap((spy) => spy.mock.calls.flat()).join("\n");
    expect(logged).toBe("report-worker: 503 storage_unavailable");
    for (const forbidden of ["TOP-SECRET", "192.168.250.9", "bob@example.com", created.id, "203.0.113.77", "203.0.113.78"]) expect(logged).not.toContain(forbidden);
  });
});
