import { describe, expect, it } from "vitest";
import { createClient, createWorld } from "./harness.js";
import { buildDetailsQuery, DETAIL_FIELDS, normalizeDetails, ratingMark } from "../src/details.js";

const details = (query) => `/v1/igdb/game-details?${query}`;

const RAW_GAME = {
  id: 214337,
  name: " Hollow Meridian ",
  slug: "hollow-meridian",
  summary: "A salvage diver wakes aboard the Meridian.",
  storyline: "",
  first_release_date: 1758153600,
  rating: 82.3712, rating_count: 1284, aggregated_rating: 86.1, aggregated_rating_count: 42, total_rating: 84.24, total_rating_count: 1326,
  genres: [{ id: 5, name: "Shooter" }, { id: 31, name: "Adventure" }, { id: 5, name: "Shooter" }],
  themes: [{ id: 19, name: "Horror" }],
  game_modes: [{ id: 1, name: "Single player" }],
  player_perspectives: [{ id: 2, name: "Third person" }],
  platforms: [{ id: 6, abbreviation: "PC", name: "PC (Microsoft Windows)" }, { id: 167, name: "PlayStation 5" }],
  involved_companies: [
    { company: { id: 1, name: "Driftlight Studios" }, developer: true, publisher: false, porting: false, supporting: false },
    { company: { id: 2, name: "Northvale Interactive" }, developer: false, publisher: true, porting: false, supporting: false },
    { company: { id: 1, name: "Driftlight Studios" }, developer: false, publisher: true },
    { company: {}, developer: true },
  ],
  age_ratings: [
    { organization: { id: 2, name: "PEGI" }, rating_category: { rating: "Eighteen" }, rating_content_descriptions: [{ description: "Violence" }] },
    { organization: { id: 1, name: "ESRB" }, rating_category: { rating: "M" } },
    { organization: { id: 3, name: "USK" } },
  ],
  screenshots: [{ id: 1, image_id: "sc6x1a", width: 1920, height: 1080 }, { id: 2, image_id: "bad/id" }, { id: 3, image_id: "sc6x1b" }],
  artworks: [{ id: 4, image_id: "ar9q", width: 3840, height: 2160 }],
  videos: [{ name: "Launch Trailer", video_id: "dQw4w9WgXcQ" }, { name: "Broken", video_id: "<script>" }],
  websites: [{ type: { id: 1, type: "Official Website" }, url: "https://hollowmeridian.example" }, { url: "javascript:alert(1)" }, { url: "not a url" }],
  similar_games: [{ id: 11, name: "Ashfall Protocol", cover: { image_id: "co1abc" } }, { id: 12, name: "The Quiet Orbit" }, { id: 0, name: "Zero" }],
  franchises: [{ name: "Meridian" }], collections: [{ name: "Meridian" }], game_engines: [{ name: "Unreal Engine 5" }],
  keywords: [{ name: "space station" }],
  language_supports: [{ language: { name: "English" } }, { language: { name: "English" } }, { language: { name: "German" } }],
  multiplayer_modes: [{ platform: 6, onlinemax: 4, onlinecoop: true }, { platform: 167, onlinemax: 8, splitscreen: true }],
  release_dates: [{ date: 1760000000, human: "Oct 09, 2025", platform: { abbreviation: "PS5" } }, { date: 1758153600, human: "Sep 18, 2025", platform: { abbreviation: "PC" } }],
  external_games: [{ uid: "2741160", external_game_source: 1 }, { uid: "x" }],
};

const NORMALIZED = {
  id: 214337,
  name: "Hollow Meridian",
  slug: "hollow-meridian",
  summary: "A salvage diver wakes aboard the Meridian.",
  storyline: null,
  firstReleaseDate: 1758153600,
  releaseDates: [
    { platform: "PC", date: 1758153600, human: "Sep 18, 2025" },
    { platform: "PS5", date: 1760000000, human: "Oct 09, 2025" },
  ],
  rating: 82.37, ratingCount: 1284, criticRating: 86.1, criticRatingCount: 42, totalRating: 84.24, totalRatingCount: 1326,
  genres: ["Shooter", "Adventure"],
  themes: ["Horror"],
  modes: ["Single player"],
  perspectives: ["Third person"],
  platforms: [{ name: "PC (Microsoft Windows)", abbreviation: "PC" }, { name: "PlayStation 5", abbreviation: null }],
  companies: [
    { name: "Driftlight Studios", developer: true, publisher: true, porting: false, supporting: false },
    { name: "Northvale Interactive", developer: false, publisher: true, porting: false, supporting: false },
  ],
  ageRatings: [
    { organization: "PEGI", rating: "18", descriptors: ["Violence"] },
    { organization: "ESRB", rating: "M", descriptors: [] },
  ],
  screenshots: [{ imageId: "sc6x1a", width: 1920, height: 1080 }, { imageId: "sc6x1b", width: null, height: null }],
  artworks: [{ imageId: "ar9q", width: 3840, height: 2160 }],
  videos: [{ name: "Launch Trailer", videoId: "dQw4w9WgXcQ" }],
  websites: [{ url: "https://hollowmeridian.example/", type: "Official Website" }],
  similarGames: [{ id: 11, name: "Ashfall Protocol", coverImageId: "co1abc" }, { id: 12, name: "The Quiet Orbit", coverImageId: null }],
  franchises: ["Meridian"], collections: ["Meridian"], engines: ["Unreal Engine 5"], keywords: ["space station"],
  languages: ["English", "German"],
  multiplayer: {
    onlineMax: 8, offlineMax: 0, onlineCoopMax: 0, offlineCoopMax: 0,
    onlineCoop: true, offlineCoop: false, lanCoop: false, campaignCoop: false, dropIn: false, splitscreen: true,
  },
  externalGames: [{ uid: "2741160", source: 1 }],
};

describe("game details query", () => {
  it("asks for the expanded v4 fields of one game", () => {
    expect(buildDetailsQuery(214337)).toBe(`${DETAIL_FIELDS} where id = 214337; limit 1;`);
    for (const field of ["storyline", "aggregated_rating_count", "age_ratings.rating_category.rating", "age_ratings.rating_content_descriptions.description", "websites.type.type", "similar_games.cover.image_id", "external_games.external_game_source"]) {
      expect(DETAIL_FIELDS).toContain(field);
    }
    for (const deprecated of ["age_ratings.category", "age_ratings.content_descriptions", "websites.category", "external_games.category"]) {
      expect(DETAIL_FIELDS).not.toContain(deprecated);
    }
  });

  it("sends exactly that query and returns the normalized shape with the usual cache headers", async () => {
    const world = createWorld({
      igdbResponse: () => new Response(JSON.stringify([RAW_GAME])),
      steamResponse: () => new Response('{"2741160":{"success":false}}'),
    });
    const client = createClient(world);
    const response = await client.request(details("id=214337"));
    expect(response.status).toBe(200);
    expect(world.igdbBodies).toEqual([buildDetailsQuery(214337)]);
    expect(response.json()).toEqual(NORMALIZED);
    expect(response.headers.get("cache-control")).toBe("public, max-age=21600");
    expect(response.headers.get("x-delos-cache")).toBe("MISS");
    const again = await client.request(details("id=214337"));
    expect(again.headers.get("x-delos-cache")).toBe("HIT");
    expect(again.json()).toEqual(NORMALIZED);
    expect(world.igdbBodies).toHaveLength(1);
  });
});

describe("game details normalization", () => {
  it("fills every list and scalar for a sparse game", () => {
    const sparse = JSON.parse(normalizeDetails('[{"id":7,"name":"Paper Lanterns","rating":71.2,"rating_count":23}]'));
    expect(sparse).toEqual({
      id: 7, name: "Paper Lanterns", slug: null, summary: null, storyline: null, firstReleaseDate: null, releaseDates: [],
      rating: 71.2, ratingCount: 23, criticRating: null, criticRatingCount: 0, totalRating: null, totalRatingCount: 0,
      genres: [], themes: [], modes: [], perspectives: [], platforms: [], companies: [], ageRatings: [],
      screenshots: [], artworks: [], videos: [], websites: [], similarGames: [],
      franchises: [], collections: [], engines: [], keywords: [], languages: [], multiplayer: null, externalGames: [],
    });
  });

  it("maps rating words and prefixes to the badge mark", () => {
    expect(ratingMark("PEGI", "Sixteen")).toBe("16");
    expect(ratingMark("PEGI", "PEGI 12")).toBe("12");
    expect(ratingMark("ESRB", "E10")).toBe("E10+");
    expect(ratingMark("ESRB", "T")).toBe("T");
    expect(ratingMark("ESRB", " ")).toBeNull();
  });

  it("caps long texts and lists", () => {
    const many = Array.from({ length: 80 }, (_, i) => ({ id: i + 1, image_id: `img${i}`, name: `Name ${i}` }));
    const result = JSON.parse(normalizeDetails(JSON.stringify([{ id: 1, name: "X", summary: "a".repeat(9000), screenshots: many, similar_games: many, keywords: many }])));
    expect(result.summary).toHaveLength(6000);
    expect(result.screenshots).toHaveLength(30);
    expect(result.similarGames).toHaveLength(12);
    expect(result.keywords).toHaveLength(30);
  });

  it("drops out-of-range ratings", () => {
    const result = JSON.parse(normalizeDetails('[{"id":1,"name":"X","rating":140,"aggregated_rating":-1,"rating_count":-3}]'));
    expect(result.rating).toBeNull();
    expect(result.criticRating).toBeNull();
    expect(result.ratingCount).toBe(0);
  });
});

describe("game details errors", () => {
  it("answers 404 game_not_found for an unknown id and does not cache it", async () => {
    const world = createWorld({ igdbResponse: () => new Response("[]") });
    const client = createClient(world);
    const response = await client.request(details("id=5"));
    expect(response.status).toBe(404);
    expect(response.json()).toEqual({ error: "game_not_found" });
    expect(response.headers.get("cache-control")).toBe("no-store");
    await client.request(details("id=5"));
    expect(world.igdbBodies).toHaveLength(2);
  });

  it("maps a non-array IGDB answer to 502", async () => {
    const world = createWorld({ igdbResponse: () => new Response('{"message":"UPSTREAM-BODY"}') });
    const response = await createClient(world).request(details("id=5"));
    expect(response.status).toBe(502);
    expect(response.text).not.toContain("UPSTREAM-BODY");
  });

  const bad = ["", "id=", "id=0", "id=-1", "id=abc", "id=01", "id=99999999999", "id=1&id=2", "id=1&fields=all", "search=x"];
  for (const query of bad) {
    it(`rejects ?${query}`, async () => {
      const world = createWorld();
      const response = await createClient(world).request(details(query));
      expect(response.status).toBe(400);
      expect(response.json()).toEqual({ error: "invalid_request" });
      expect(world.calls).toHaveLength(0);
    });
  }
});
