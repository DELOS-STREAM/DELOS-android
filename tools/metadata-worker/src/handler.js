import { createResponseCache } from "./cache.js";
import { HttpError, RETRY_AFTER_SECONDS } from "./errors.js";
import { buildDetailsQuery, detailsObject } from "./details.js";
import { buildIdQuery, buildSearchQuery, createIgdb } from "./igdb.js";
import { createBucket, createGate } from "./limits.js";
import { cargoParams, createPcgw, pageWhere, parseParams, redirectParams, searchParams, steamWhere } from "./pcgw.js";
import { createSteam } from "./steam.js";
import { createSteamMedia, steamAppId } from "./steam-media.js";
import { MAX_IGDB_ID, MAX_STEAM_TAGS_APP_ID, singleParams, validId, validSteamIds, validTerm, validTitle } from "./validate.js";

export const MAX_URL_LENGTH = 2048;
export const MAX_HEADER_BYTES = 8192;
export const CACHE_SECONDS = 6 * 60 * 60;
export const STEAM_CACHE_SECONDS = 24 * 60 * 60;
// A details answer whose Steam media lookup failed transiently is kept only briefly, so Steam media appear soon.
export const STEAM_RETRY_SECONDS = 5 * 60;

const JSON_TYPE = "application/json; charset=utf-8";

// Rate limit per client IP when the Workers Rate Limiting binding is absent: burst of 60, then 200 per minute.
const BUCKET_CAPACITY = 60;
const BUCKET_REFILL_PER_SECOND = 200 / 60;

function jsonResponse(status, body, cacheControl, extraHeaders = {}) {
  return new Response(body, {
    status,
    headers: {
      "Content-Type": JSON_TYPE,
      "Cache-Control": cacheControl,
      "X-Content-Type-Options": "nosniff",
      ...extraHeaders,
    },
  });
}

function errorResponse(error) {
  const known = error instanceof HttpError;
  const status = known ? error.status : 500;
  const headers = known && error.retryAfterSeconds > 0 ? { "Retry-After": String(error.retryAfterSeconds) } : {};
  if (status === 405) headers.Allow = "GET";
  return jsonResponse(status, JSON.stringify({ error: known ? error.code : "internal" }), "no-store", headers);
}

async function shortHash(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest).slice(0, 6)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function headerBytes(headers) {
  let total = 0;
  for (const [name, value] of headers) total += name.length + value.length + 4;
  return total;
}

/**
 * The request handler with every side-effecting dependency injectable (fetch, clock, sleep, Cache API), so the
 * unit tests need no network and no real time. The default export in index.js wires the production values.
 */
export function createHandler(deps = {}) {
  const fetchFn = deps.fetch ?? ((...args) => fetch(...args));
  const now = deps.now ?? (() => Date.now());
  const sleep = deps.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  const igdbGate = createGate({ intervalMs: deps.igdbIntervalMs ?? 260, maxWaitMs: 2500, now, sleep });
  const pcgwGate = createGate({ intervalMs: deps.pcgwIntervalMs ?? 550, maxWaitMs: 4000, now, sleep });
  const igdb = createIgdb({ fetch: fetchFn, now, gate: igdbGate });
  const pcgw = createPcgw({ fetch: fetchFn, now, gate: pcgwGate });
  const steamGate = createGate({ intervalMs: deps.steamIntervalMs ?? 600, maxWaitMs: 4000, now, sleep });
  const steam = createSteam({ fetch: fetchFn, gate: steamGate });
  const steamMedia = createSteamMedia({ fetch: fetchFn, gate: steamGate });
  const cache = createResponseCache({ cacheApi: deps.cacheApi ?? null, now });
  const bucket = createBucket({ capacity: BUCKET_CAPACITY, refillPerSecond: BUCKET_REFILL_PER_SECOND, now });
  const inflight = new Map();

  /**
   * Steam media of one app, cached for a day under its own key (shared by every IGDB id that maps to it).
   * Returns the `steam` object, null for "Steam has nothing", or undefined after a transient failure.
   */
  async function cachedSteamMedia(appId, { origin, waitUntil }) {
    const key = `${origin}/v1/steam/media?app=${appId}`;
    const cached = await cache.get(key);
    if (cached !== null) return JSON.parse(cached);
    try {
      const media = await steamMedia.media(appId);
      cache.put(key, JSON.stringify(media), STEAM_CACHE_SECONDS, waitUntil);
      return media;
    } catch (error) {
      if (error instanceof HttpError) return undefined;
      throw error;
    }
  }

  /** IGDB details plus Steam trailers/screenshots; a Steam failure only shortens the cache life of the answer. */
  async function gameDetails(env, io, id) {
    const game = detailsObject(await igdb.games(env, buildDetailsQuery(id)));
    const appId = steamAppId(game.externalGames);
    if (appId === null) return JSON.stringify(game);
    const media = await cachedSteamMedia(appId, io);
    if (media) game.steam = media;
    return media === undefined ? { body: JSON.stringify(game), cacheSeconds: STEAM_RETRY_SECONDS } : JSON.stringify(game);
  }

  // Each route turns validated input into a normalized parameter list (the cache key) and one fixed upstream call.
  const routes = {
    "/v1/igdb/games": {
      parse(params) {
        const values = singleParams(params, ["search", "id"]);
        if (!values || ("search" in values) === ("id" in values)) return null;
        if ("search" in values) {
          const term = validTerm(values.search);
          return term === null ? null : { normalized: [["search", term]], upstream: (env) => igdb.games(env, buildSearchQuery(term)) };
        }
        const id = validId(values.id, MAX_IGDB_ID);
        return id === null ? null : { normalized: [["id", String(id)]], upstream: (env) => igdb.games(env, buildIdQuery(id)) };
      },
    },
    "/v1/igdb/game-details": {
      parse(params) {
        const values = singleParams(params, ["id"], { required: ["id"] });
        const id = values && validId(values.id, MAX_IGDB_ID);
        // `v` changes the cache key when the answer's shape grows (2: Steam media), so stale entries are not served.
        return id ? { normalized: [["id", String(id)], ["v", "2"]], upstream: (env, io) => gameDetails(env, io, id) } : null;
      },
    },
    "/v1/pcgw/cargo": {
      parse(params) {
        const values = singleParams(params, ["steam", "page"]);
        if (!values || ("steam" in values) === ("page" in values)) return null;
        if ("steam" in values) {
          const ids = validSteamIds(values.steam);
          return ids === null ? null : { normalized: [["steam", ids.join(",")]], upstream: (env) => pcgw.query(env, cargoParams(steamWhere(ids))) };
        }
        const title = validTitle(values.page);
        return title === null ? null : { normalized: [["page", title]], upstream: (env) => pcgw.query(env, cargoParams(pageWhere(title))) };
      },
    },
    "/v1/pcgw/page": {
      parse(params) {
        const values = singleParams(params, ["title"], { required: ["title"] });
        const title = values && validTitle(values.title);
        return title ? { normalized: [["title", title]], upstream: (env) => pcgw.query(env, parseParams(title)) } : null;
      },
    },
    "/v1/pcgw/redirect": {
      parse(params) {
        const values = singleParams(params, ["title"], { required: ["title"] });
        const title = values && validTitle(values.title);
        return title ? { normalized: [["title", title]], upstream: (env) => pcgw.query(env, redirectParams(title)) } : null;
      },
    },
    "/v1/pcgw/search": {
      parse(params) {
        const values = singleParams(params, ["q"], { required: ["q"] });
        const term = values && validTitle(values.q);
        return term ? { normalized: [["q", term]], upstream: (env) => pcgw.query(env, searchParams(term)) } : null;
      },
    },
    "/v1/steam/tags": {
      parse(params) {
        const values = singleParams(params, ["app"], { required: ["app"] });
        const id = values && validId(values.app, MAX_STEAM_TAGS_APP_ID);
        return id ? { normalized: [["app", String(id)]], cacheSeconds: STEAM_CACHE_SECONDS, upstream: () => steam.tags(id) } : null;
      },
    },
  };

  async function allowed(env, clientKey) {
    const limiter = env && env.RATE_LIMITER;
    if (limiter && typeof limiter.limit === "function") {
      try {
        return (await limiter.limit({ key: clientKey })).success;
      } catch {
        // Fall through to the in-isolate bucket rather than serving unlimited or failing every request.
      }
    }
    return bucket(clientKey);
  }

  /** One upstream load per cache key at a time inside an isolate. */
  function coalesce(key, load) {
    let pending = inflight.get(key);
    if (!pending) {
      pending = load().finally(() => inflight.delete(key));
      inflight.set(key, pending);
    }
    return pending;
  }

  async function handle(request, env, ctx) {
    if (request.method !== "GET") throw new HttpError(405, "method_not_allowed");
    if (request.url.length > MAX_URL_LENGTH) throw new HttpError(414, "uri_too_long");
    if (headerBytes(request.headers) > MAX_HEADER_BYTES) throw new HttpError(431, "headers_too_large");
    const contentLength = request.headers.get("content-length");
    if ((contentLength !== null && contentLength !== "0") || request.headers.has("transfer-encoding")) {
      throw new HttpError(413, "body_not_allowed");
    }
    const url = new URL(request.url);
    const route = Object.hasOwn(routes, url.pathname) ? routes[url.pathname] : null;
    if (!route) throw new HttpError(404, "not_found");

    // Counted before validation so malformed floods are limited as well.
    if (!(await allowed(env, request.headers.get("cf-connecting-ip") || "unknown"))) {
      throw new HttpError(429, "rate_limited", RETRY_AFTER_SECONDS);
    }
    const lookup = route.parse(url.searchParams);
    if (!lookup) throw new HttpError(400, "invalid_request");

    const key = `${url.origin}${url.pathname}?${lookup.normalized.map(([name, value]) => `${name}=${encodeURIComponent(value)}`).join("&")}`;
    const seconds = lookup.cacheSeconds ?? CACHE_SECONDS;
    const cacheControl = `public, max-age=${seconds}`;
    const waitUntil = ctx && ctx.waitUntil ? (promise) => ctx.waitUntil(promise) : null;
    const cached = await cache.get(key);
    if (cached !== null) return jsonResponse(200, cached, cacheControl, { "X-Delos-Cache": "HIT" });
    try {
      const { body, seconds: stored } = await coalesce(key, async () => {
        const result = await lookup.upstream(env, { origin: url.origin, waitUntil });
        const text = typeof result === "string" ? result : result.body;
        const ttl = typeof result === "string" ? seconds : result.cacheSeconds;
        cache.put(key, text, ttl, waitUntil);
        return { body: text, seconds: ttl };
      });
      return jsonResponse(200, body, `public, max-age=${stored}`, { "X-Delos-Cache": "MISS" });
    } catch (error) {
      // The only log line: route, status, code and a hash of the normalized query, never its text.
      if (error instanceof HttpError && error.status >= 500) {
        console.warn(JSON.stringify({ route: url.pathname, status: error.status, code: error.code, q: await shortHash(key) }));
      }
      throw error;
    }
  }

  return async function fetchHandler(request, env, ctx) {
    try {
      return await handle(request, env, ctx);
    } catch (error) {
      if (!(error instanceof HttpError)) console.warn(JSON.stringify({ code: "internal", name: error && error.name }));
      return errorResponse(error);
    }
  };
}
