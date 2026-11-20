import { HttpError, discard, readText, rejectStatus, upstreamFetch } from "./errors.js";
import { MAX_STEAM_TAGS_APP_ID } from "./validate.js";

// Steam's public store API (no key). It is undocumented and rate limited per IP (roughly 200 calls per 5 minutes),
// so every answer, including "no such app", is cached for a day and calls share the Steam store gate.
const APPDETAILS_URL = "https://store.steampowered.com/api/appdetails";
export const STEAM_MEDIA_TIMEOUT_MS = 5000;
export const STEAM_SOURCE = 1; // IGDB external_game_source: Steam
export const MEDIA_LIMITS = { movies: 10, screenshots: 30, name: 200 };
const MEDIA_HOSTS = [".steamstatic.com", ".steampowered.com"];

const isObject = (value) => value !== null && typeof value === "object" && !Array.isArray(value);

/** The Steam app id of a normalized details object: the first IGDB external game with source 1 and a numeric uid. */
export function steamAppId(externalGames) {
  for (const game of externalGames) {
    if (game.source !== STEAM_SOURCE || !/^[1-9][0-9]{0,9}$/.test(game.uid)) continue;
    const id = Number(game.uid);
    if (id <= MAX_STEAM_TAGS_APP_ID) return id;
  }
  return null;
}

/** An https URL on Steam's CDN or store, or null. */
function mediaUrl(value) {
  if (typeof value !== "string") return null;
  let url;
  try {
    url = new URL(value.trim());
  } catch {
    return null;
  }
  if (url.protocol !== "https:" || !MEDIA_HOSTS.some((suffix) => url.hostname.endsWith(suffix))) return null;
  return url.href;
}

/** Sets out[key] only when value is present, so fields Steam does not provide stay absent. */
function put(out, key, value) {
  if (value !== null && value !== undefined) out[key] = value;
}

function movie(item) {
  if (!isObject(item) || !Number.isInteger(item.id) || item.id < 0) return null;
  const out = { id: item.id };
  if (typeof item.name === "string" && item.name.trim() !== "") out.name = item.name.trim().slice(0, MEDIA_LIMITS.name);
  put(out, "thumbnail", mediaUrl(item.thumbnail));
  // Newer answers carry adaptive streams, older ones progressive files in two sizes; the largest wins.
  put(out, "hls", mediaUrl(item.hls_h264));
  put(out, "dash", mediaUrl(item.dash_h264) ?? mediaUrl(item.dash_av1));
  const progressive = (format) => (isObject(format) ? mediaUrl(format.max) ?? mediaUrl(format["480"]) : null);
  put(out, "mp4", progressive(item.mp4));
  put(out, "webm", progressive(item.webm));
  if (!("hls" in out || "dash" in out || "mp4" in out || "webm" in out)) return null;
  if (typeof item.highlight === "boolean") out.highlight = item.highlight;
  return out;
}

function screenshot(item) {
  if (!isObject(item) || !Number.isInteger(item.id) || item.id < 0) return null;
  const full = mediaUrl(item.path_full);
  if (full === null) return null;
  const out = { id: item.id };
  put(out, "thumbnail", mediaUrl(item.path_thumbnail));
  out.full = full;
  return out;
}

/**
 * The `steam` object of the details answer from one appdetails body, or null when Steam does not know the app or
 * lists no media. Highlight movies come first (stable), duplicates by id are dropped, lists are capped.
 */
export function parseAppDetails(appId, rawText) {
  let parsed;
  try {
    parsed = JSON.parse(rawText);
  } catch {
    throw new HttpError(502, "upstream_error");
  }
  // Steam answers `null` or `[]` for ids it cannot look up; that is "no media", not a failure.
  const entry = isObject(parsed) ? parsed[String(appId)] : null;
  if (!isObject(entry) || entry.success !== true || !isObject(entry.data)) return null;
  const distinct = (list, map, max) => {
    const out = [];
    for (const item of Array.isArray(list) ? list : []) {
      const value = map(item);
      if (value === null || out.some((existing) => existing.id === value.id)) continue;
      out.push(value);
      if (out.length >= max) break;
    }
    return out;
  };
  const movies = distinct(entry.data.movies, movie, MEDIA_LIMITS.movies * 3)
    .sort((a, b) => Number(b.highlight === true) - Number(a.highlight === true))
    .slice(0, MEDIA_LIMITS.movies);
  const screenshots = distinct(entry.data.screenshots, screenshot, MEDIA_LIMITS.screenshots);
  if (movies.length === 0 && screenshots.length === 0) return null;
  return { appId, movies, screenshots };
}

/** Steam trailers and screenshots of one app through the public appdetails API. */
export function createSteamMedia({ fetch: fetchFn, gate }) {
  /** The `steam` object, or null when Steam has nothing; throws HttpError on a transient failure. */
  async function media(appId) {
    await gate();
    const url = `${APPDETAILS_URL}?appids=${appId}&l=english&filters=basic,movies,screenshots`;
    const response = await upstreamFetch(fetchFn, url, { method: "GET", headers: { Accept: "application/json" } }, STEAM_MEDIA_TIMEOUT_MS);
    if (response.status >= 300 && response.status < 400) {
      await discard(response);
      throw new HttpError(502, "upstream_error");
    }
    if (!(response.status >= 200 && response.status < 300)) await rejectStatus(response);
    return parseAppDetails(appId, await readText(response));
  }

  return { media };
}
