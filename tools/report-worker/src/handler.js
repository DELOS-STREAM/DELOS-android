import { createBucket } from "./limits.js";
import { rescrub } from "./scrub.js";

// The second scrub of a report takes about 5 ms of CPU per 128 KiB (README, "Evidence"); the Workers Free plan allows 10 ms of CPU per
// request. The app cuts its report to the same size (ReportUploader.MAX_BYTES); ScrubContractTest and scrub.test.js check that they agree.
export const MAX_BYTES = 128 * 1024;
export const TTL_SECONDS = 30 * 24 * 60 * 60;
export const WORK_MS = 5000;
// The Workers Free plan allows 1000 KV writes per day and an upload makes two (the report and the counter): 400 uploads
// use 800 and leave room for the owner's own tests.
export const DAILY_LIMIT = 400;
export const ID_LENGTH = 22;

const ID_PATTERN = /^[A-Za-z0-9_-]{22}$/;
const COUNTER_TTL_SECONDS = 2 * 24 * 60 * 60;
const JSON_TYPE = "application/json; charset=utf-8";

// In-isolate buckets next to the binding: 5 uploads per 10 minutes and 60 reads per minute per client.
const UPLOAD_CAPACITY = 5;
const UPLOAD_REFILL_PER_SECOND = 5 / 600;
const READ_CAPACITY = 60;
const READ_REFILL_PER_SECOND = 1;

/** The only error type that reaches a client: `code` is a fixed identifier of this code base, never text of the request. */
class HttpError extends Error {
  constructor(status, code, { retryAfter = 0, allow = null } = {}) {
    super(code);
    this.name = "HttpError";
    this.status = status;
    this.code = code;
    this.retryAfter = retryAfter;
    this.allow = allow;
  }
}

const invalid = () => new HttpError(400, "invalid_request");
const tooLarge = () => new HttpError(413, "too_large");

function response(status, body, type, extraHeaders = {}) {
  return new Response(body, {
    status,
    headers: { "Content-Type": type, "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff", ...extraHeaders },
  });
}

const json = (status, value, extraHeaders = {}) => response(status, JSON.stringify(value), JSON_TYPE, extraHeaders);

function errorResponse(error) {
  const known = error instanceof HttpError;
  const headers = {};
  if (known && error.retryAfter > 0) headers["Retry-After"] = String(error.retryAfter);
  if (known && error.allow) headers.Allow = error.allow;
  return json(known ? error.status : 500, { error: known ? error.code : "internal" }, headers);
}

function base64Url(bytes) {
  let text = "";
  for (const byte of bytes) text += String.fromCharCode(byte);
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** The media type must be text/plain, and a charset, when given, utf-8. */
function checkContentType(header) {
  if (header === null) throw new HttpError(415, "unsupported_media_type");
  const [media, ...parameters] = header.split(";").map((part) => part.trim());
  if (media.toLowerCase() !== "text/plain") throw new HttpError(415, "unsupported_media_type");
  for (const parameter of parameters) {
    const [name, value = ""] = parameter.split("=");
    if (name.trim().toLowerCase() === "charset" && value.trim().replace(/^"|"$/g, "").toLowerCase() !== "utf-8") {
      throw new HttpError(415, "unsupported_media_type");
    }
  }
}

function contentEncoding(header) {
  const value = (header ?? "").trim().toLowerCase();
  if (value === "" || value === "identity") return "identity";
  if (value === "gzip" || value === "x-gzip") return "gzip";
  throw new HttpError(415, "unsupported_encoding");
}

/** All bytes of [stream], or 413 as soon as more than [limit] arrive; nothing past the limit is kept. */
async function readLimited(stream, limit) {
  const reader = stream.getReader();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > limit) {
      await reader.cancel().catch(() => {});
      throw tooLarge();
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

async function readText(request, encoding) {
  if (request.body === null) throw invalid();
  let bytes;
  try {
    bytes = await readLimited(request.body, MAX_BYTES);
  } catch (error) {
    throw error instanceof HttpError ? error : invalid();
  }
  if (encoding === "gzip") {
    try {
      bytes = await readLimited(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip")), MAX_BYTES);
    } catch (error) {
      // 413 for a gzip bomb (more than the limit after inflating); anything else is not a gzip stream.
      throw error instanceof HttpError ? error : invalid();
    }
  }
  let text;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw invalid();
  }
  if (text.trim() === "" || text.includes("\u0000")) throw invalid();
  return text;
}

/** [promise], or 504 when it does not settle within [ms]. */
function withDeadline(promise, ms) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new HttpError(504, "timeout")), ms);
  });
  promise.catch(() => {});
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * The request handler with the clock and the random source injectable, so the unit tests need no real time. The default
 * export in index.js wires the production values. The handler never logs a request body, a link or an address: on a failure
 * (status >= 500) it logs one line with the status and the fixed error code.
 */
export function createHandler(deps = {}) {
  const now = deps.now ?? (() => Date.now());
  const randomBytes = deps.randomBytes ?? ((length) => crypto.getRandomValues(new Uint8Array(length)));
  const workMs = deps.workMs ?? WORK_MS;
  const dailyLimit = deps.dailyLimit ?? DAILY_LIMIT;
  const uploadBucket = createBucket({ capacity: UPLOAD_CAPACITY, refillPerSecond: UPLOAD_REFILL_PER_SECOND, now });
  const readBucket = createBucket({ capacity: READ_CAPACITY, refillPerSecond: READ_REFILL_PER_SECOND, now });

  /** The binding first (when it is configured and answers), then the in-isolate bucket. */
  async function allowed(limiter, bucket, clientKey) {
    if (limiter && typeof limiter.limit === "function") {
      try {
        if (!(await limiter.limit({ key: clientKey })).success) return false;
      } catch {
        // The bucket alone decides rather than serving unlimited or failing every request.
      }
    }
    return bucket(clientKey);
  }

  const today = () => new Date(now()).toISOString().slice(0, 10);

  async function upload(request, env) {
    const clientKey = request.headers.get("cf-connecting-ip") ?? "unknown";
    if (!(await allowed(env.UPLOAD_LIMITER, uploadBucket, clientKey))) throw new HttpError(429, "rate_limited", { retryAfter: 120 });
    checkContentType(request.headers.get("content-type"));
    const encoding = contentEncoding(request.headers.get("content-encoding"));
    const announced = request.headers.get("content-length");
    if (announced !== null && /^\d+$/.test(announced) && Number(announced) > MAX_BYTES) throw tooLarge();
    if (!env.REPORTS || typeof env.REPORTS.put !== "function") throw new HttpError(503, "not_configured");

    const counterKey = `cap:${today()}`;
    let used;
    try {
      used = Number(await env.REPORTS.get(counterKey)) || 0;
    } catch {
      throw new HttpError(503, "storage_unavailable");
    }
    if (used >= dailyLimit) throw new HttpError(503, "daily_limit", { retryAfter: 3600 });

    const { text, count } = await withDeadline(
      readText(request, encoding).then((raw) => rescrub(raw)),
      workMs,
    );

    const id = base64Url(randomBytes(16));
    try {
      await env.REPORTS.put(`r:${id}`, text, { expirationTtl: TTL_SECONDS });
      // Read, add one, write: two uploads at once may count as one. The counter is a cap, not an accounting.
      await env.REPORTS.put(counterKey, String(used + 1), { expirationTtl: COUNTER_TTL_SECONDS });
    } catch {
      throw new HttpError(503, "storage_unavailable");
    }
    const url = `${new URL(request.url).origin}/r/${id}`;
    const expires = new Date(now() + TTL_SECONDS * 1000).toISOString();
    return json(201, { id, url, expires, rescrubbed: count }, { Location: url });
  }

  async function read(request, env, id) {
    const clientKey = request.headers.get("cf-connecting-ip") ?? "unknown";
    if (!(await allowed(env.READ_LIMITER, readBucket, clientKey))) throw new HttpError(429, "rate_limited", { retryAfter: 30 });
    if (!env.REPORTS || typeof env.REPORTS.get !== "function") throw new HttpError(503, "not_configured");
    let text;
    try {
      text = await env.REPORTS.get(`r:${id}`);
    } catch {
      throw new HttpError(503, "storage_unavailable");
    }
    if (text === null || text === undefined) throw new HttpError(404, "not_found");
    return response(200, text, "text/plain; charset=utf-8", {
      "X-Robots-Tag": "noindex, nofollow",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy": "default-src 'none'; sandbox",
    });
  }

  return async function handle(request, env = {}) {
    try {
      const path = new URL(request.url).pathname;
      if (path === "/v1/reports") {
        if (request.method !== "POST") throw new HttpError(405, "method_not_allowed", { allow: "POST" });
        return await upload(request, env);
      }
      const match = /^\/r\/([^/]+)$/.exec(path);
      if (match && ID_PATTERN.test(match[1])) {
        if (request.method !== "GET") throw new HttpError(405, "method_not_allowed", { allow: "GET" });
        return await read(request, env, match[1]);
      }
      throw new HttpError(404, "not_found");
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 500;
      if (status >= 500) console.error(`report-worker: ${status} ${error instanceof HttpError ? error.code : "internal"}`);
      return errorResponse(error);
    }
  };
}
