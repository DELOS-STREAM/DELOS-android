import { HttpError } from "./errors.js";

// Expanded fields of the game overview (issue #107). IGDB v4 names only: organization/rating_category and
// rating_content_descriptions for age ratings, websites.type, external_games.external_game_source.
export const DETAIL_FIELDS =
  "fields name,slug,summary,storyline,first_release_date," +
  "rating,rating_count,aggregated_rating,aggregated_rating_count,total_rating,total_rating_count," +
  "genres.name,themes.name,game_modes.name,player_perspectives.name,platforms.abbreviation,platforms.name," +
  "involved_companies.company.name,involved_companies.developer,involved_companies.publisher," +
  "involved_companies.porting,involved_companies.supporting," +
  "age_ratings.organization.name,age_ratings.rating_category.rating,age_ratings.rating_content_descriptions.description," +
  "screenshots.image_id,screenshots.width,screenshots.height,artworks.image_id,artworks.width,artworks.height," +
  "videos.name,videos.video_id,websites.url,websites.type.type,similar_games.name,similar_games.cover.image_id," +
  "franchises.name,collections.name,game_engines.name,keywords.name,language_supports.language.name," +
  "multiplayer_modes.*,release_dates.date,release_dates.human,release_dates.platform.abbreviation," +
  "external_games.uid,external_games.external_game_source;";

export function buildDetailsQuery(id) {
  return `${DETAIL_FIELDS} where id = ${id}; limit 1;`;
}

// Caps keep the answer small for the app's cache; IGDB rarely exceeds them.
export const LIMITS = {
  text: 6000,
  name: 200,
  list: 40,
  screenshots: 30,
  artworks: 10,
  videos: 10,
  websites: 20,
  similarGames: 12,
  keywords: 30,
  languages: 60,
  companies: 20,
};

const IMAGE_ID = /^[A-Za-z0-9_-]{1,64}$/;
const VIDEO_ID = /^[A-Za-z0-9_-]{6,20}$/;
const NUMBER_WORDS = { Three: "3", Seven: "7", Twelve: "12", Sixteen: "16", Eighteen: "18" };

const isObject = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const array = (value) => (Array.isArray(value) ? value : []);

function text(value, max) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (trimmed === "") return null;
  return trimmed.length > max ? trimmed.slice(0, max) : trimmed;
}

function count(value) {
  return Number.isInteger(value) && value > 0 ? value : 0;
}

function score(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 && value <= 100 ? Math.round(value * 100) / 100 : null;
}

function seconds(value) {
  return Number.isInteger(value) ? value : null;
}

function dimension(value) {
  return Number.isInteger(value) && value > 0 ? value : null;
}

/** Distinct, trimmed names of `[{name}]`, in IGDB's order. */
function names(list, max = LIMITS.list, pick = (item) => item && item.name) {
  const seen = new Set();
  const out = [];
  for (const item of array(list)) {
    const name = text(pick(item), LIMITS.name);
    if (name === null || seen.has(name)) continue;
    seen.add(name);
    out.push(name);
    if (out.length >= max) break;
  }
  return out;
}

function images(list, max) {
  const seen = new Set();
  const out = [];
  for (const item of array(list)) {
    const imageId = isObject(item) && typeof item.image_id === "string" && IMAGE_ID.test(item.image_id) ? item.image_id : null;
    if (imageId === null || seen.has(imageId)) continue;
    seen.add(imageId);
    out.push({ imageId, width: dimension(item.width), height: dimension(item.height) });
    if (out.length >= max) break;
  }
  return out;
}

/** "Eighteen" -> "18", "PEGI 18" -> "18", "E10" -> "E10+": the mark as printed on the badge. */
export function ratingMark(organization, rating) {
  let mark = text(rating, 40);
  if (mark === null) return null;
  if (organization && mark.toUpperCase().startsWith(`${organization.toUpperCase()} `)) mark = mark.slice(organization.length + 1).trim();
  if (NUMBER_WORDS[mark]) return NUMBER_WORDS[mark];
  if (mark === "E10") return "E10+";
  return mark;
}

function ageRatings(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item)) continue;
    const organization = text(isObject(item.organization) ? item.organization.name : null, 40);
    const rating = ratingMark(organization, isObject(item.rating_category) ? item.rating_category.rating : null);
    if (organization === null || rating === null) continue;
    if (out.some((existing) => existing.organization === organization)) continue;
    out.push({ organization, rating, descriptors: names(item.rating_content_descriptions, LIMITS.list, (d) => d && d.description) });
  }
  return out;
}

function companies(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item)) continue;
    const name = text(isObject(item.company) ? item.company.name : null, LIMITS.name);
    if (name === null) continue;
    const roles = {
      developer: item.developer === true,
      publisher: item.publisher === true,
      porting: item.porting === true,
      supporting: item.supporting === true,
    };
    const existing = out.find((company) => company.name === name);
    if (existing) {
      for (const role of Object.keys(roles)) existing[role] ||= roles[role];
      continue;
    }
    out.push({ name, ...roles });
    if (out.length >= LIMITS.companies) break;
  }
  return out;
}

function platforms(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item)) continue;
    const name = text(item.name, LIMITS.name);
    if (name === null || out.some((platform) => platform.name === name)) continue;
    out.push({ name, abbreviation: text(item.abbreviation, 40) });
    if (out.length >= LIMITS.list) break;
  }
  return out;
}

function releaseDates(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item)) continue;
    const platform = text(isObject(item.platform) ? item.platform.abbreviation : null, 40);
    const date = seconds(item.date);
    const human = text(item.human, 40);
    if (date === null && human === null) continue;
    out.push({ platform, date, human });
    if (out.length >= LIMITS.list) break;
  }
  return out.sort((a, b) => (a.date ?? Number.MAX_SAFE_INTEGER) - (b.date ?? Number.MAX_SAFE_INTEGER));
}

function videos(list) {
  const out = [];
  for (const item of array(list)) {
    const videoId = isObject(item) && typeof item.video_id === "string" && VIDEO_ID.test(item.video_id) ? item.video_id : null;
    if (videoId === null || out.some((video) => video.videoId === videoId)) continue;
    out.push({ name: text(item.name, LIMITS.name), videoId });
    if (out.length >= LIMITS.videos) break;
  }
  return out;
}

function websites(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item) || typeof item.url !== "string") continue;
    let url;
    try {
      url = new URL(item.url.trim());
    } catch {
      continue;
    }
    if (url.protocol !== "https:" && url.protocol !== "http:") continue;
    if (out.some((site) => site.url === url.href)) continue;
    out.push({ url: url.href, type: text(isObject(item.type) ? item.type.type : null, 60) });
    if (out.length >= LIMITS.websites) break;
  }
  return out;
}

function similarGames(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item) || !Number.isInteger(item.id) || item.id <= 0) continue;
    const name = text(item.name, LIMITS.name);
    if (name === null || out.some((game) => game.id === item.id)) continue;
    const cover = isObject(item.cover) && typeof item.cover.image_id === "string" && IMAGE_ID.test(item.cover.image_id) ? item.cover.image_id : null;
    out.push({ id: item.id, name, coverImageId: cover });
    if (out.length >= LIMITS.similarGames) break;
  }
  return out;
}

/** All multiplayer_modes rows (one per platform) folded into one summary; null when IGDB lists none. */
function multiplayer(list) {
  const rows = array(list).filter(isObject);
  if (rows.length === 0) return null;
  const max = (key) => rows.reduce((best, row) => Math.max(best, count(row[key])), 0);
  const any = (key) => rows.some((row) => row[key] === true);
  return {
    onlineMax: max("onlinemax"),
    offlineMax: max("offlinemax"),
    onlineCoopMax: max("onlinecoopmax"),
    offlineCoopMax: max("offlinecoopmax"),
    onlineCoop: any("onlinecoop"),
    offlineCoop: any("offlinecoop"),
    lanCoop: any("lancoop"),
    campaignCoop: any("campaigncoop"),
    dropIn: any("dropin"),
    splitscreen: any("splitscreen") || any("splitscreenonline"),
  };
}

function externalGames(list) {
  const out = [];
  for (const item of array(list)) {
    if (!isObject(item) || !Number.isInteger(item.external_game_source)) continue;
    const uid = text(item.uid, 64);
    if (uid === null) continue;
    out.push({ uid, source: item.external_game_source });
    if (out.length >= LIMITS.list) break;
  }
  return out;
}

/**
 * Turns IGDB's array answer into the overview's flat, documented shape (README "Game details"). Every list is
 * always present (possibly empty), every missing scalar is null, so the apps parse one strict shape. The handler
 * adds the optional `steam` media object before serializing.
 */
export function detailsObject(rawText) {
  let parsed;
  try {
    parsed = JSON.parse(rawText);
  } catch {
    throw new HttpError(502, "upstream_error");
  }
  if (!Array.isArray(parsed)) throw new HttpError(502, "upstream_error");
  const game = parsed[0];
  if (!isObject(game) || !Number.isInteger(game.id)) throw new HttpError(404, "game_not_found");
  const name = text(game.name, LIMITS.name);
  if (name === null) throw new HttpError(404, "game_not_found");
  return {
    id: game.id,
    name,
    slug: text(game.slug, LIMITS.name),
    summary: text(game.summary, LIMITS.text),
    storyline: text(game.storyline, LIMITS.text),
    firstReleaseDate: seconds(game.first_release_date),
    releaseDates: releaseDates(game.release_dates),
    rating: score(game.rating),
    ratingCount: count(game.rating_count),
    criticRating: score(game.aggregated_rating),
    criticRatingCount: count(game.aggregated_rating_count),
    totalRating: score(game.total_rating),
    totalRatingCount: count(game.total_rating_count),
    genres: names(game.genres),
    themes: names(game.themes),
    modes: names(game.game_modes),
    perspectives: names(game.player_perspectives),
    platforms: platforms(game.platforms),
    companies: companies(game.involved_companies),
    ageRatings: ageRatings(game.age_ratings),
    screenshots: images(game.screenshots, LIMITS.screenshots),
    artworks: images(game.artworks, LIMITS.artworks),
    videos: videos(game.videos),
    websites: websites(game.websites),
    similarGames: similarGames(game.similar_games),
    franchises: names(game.franchises),
    collections: names(game.collections),
    engines: names(game.game_engines),
    keywords: names(game.keywords, LIMITS.keywords),
    languages: names(game.language_supports, LIMITS.languages, (support) => support && isObject(support.language) && support.language.name),
    multiplayer: multiplayer(game.multiplayer_modes),
    externalGames: externalGames(game.external_games),
  };
}

export function normalizeDetails(rawText) {
  return JSON.stringify(detailsObject(rawText));
}
