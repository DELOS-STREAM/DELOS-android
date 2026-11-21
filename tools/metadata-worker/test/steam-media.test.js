import { describe, expect, it } from "vitest";
import { createClient, createWorld } from "./harness.js";
import { parseAppDetails, steamAppId } from "../src/steam-media.js";

const details = (id) => `/v1/igdb/game-details?id=${id}`;
const CDN = "https://video.akamai.steamstatic.com/store_trailers/1145360/312686/abc/1750627549";
const IMG = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360";

const GAME = { id: 113112, name: "Hades", external_games: [{ uid: "Hades", external_game_source: 1 }, { uid: "1145360", external_game_source: 1 }] };

// appdetails in both shapes: adaptive streams (current) and progressive files (older answers).
const APPDETAILS = {
  1145360: {
    success: true,
    data: {
      name: "Hades",
      movies: [
        { id: 300, name: "Gameplay", thumbnail: `${IMG}/300/movie.293x165.jpg`, mp4: { 480: `${IMG}/300/movie480.mp4`, max: `${IMG}/300/movie_max.mp4` }, webm: { 480: `${IMG}/300/movie480.webm` }, highlight: false },
        { id: 256, name: " Launch Trailer ", thumbnail: `${IMG}/256/movie.293x165.jpg`, hls_h264: `${CDN}/hls_264_master.m3u8?t=1`, dash_h264: `${CDN}/dash_h264.mpd`, dash_av1: `${CDN}/dash_av1.mpd`, highlight: true },
        { id: 256, name: "Duplicate", hls_h264: `${CDN}/dup.m3u8` },
        { id: 400, name: "No streams", thumbnail: `${IMG}/400/movie.jpg` },
        { id: 401, name: "Foreign host", mp4: { max: "https://evil.example/movie.mp4" } },
        { id: 402, hls_h264: "http://video.akamai.steamstatic.com/insecure.m3u8" },
        { id: 500, dash_av1: `${CDN}/only_av1.mpd` },
      ],
      screenshots: [
        { id: 0, path_thumbnail: `${IMG}/ss_a.600x338.jpg`, path_full: `${IMG}/ss_a.1920x1080.jpg` },
        { id: 1, path_thumbnail: "javascript:alert(1)", path_full: `${IMG}/ss_b.1920x1080.jpg` },
        { id: 2, path_thumbnail: `${IMG}/ss_c.600x338.jpg` },
      ],
    },
  },
};

const STEAM = {
  appId: 1145360,
  movies: [
    { id: 256, name: "Launch Trailer", thumbnail: `${IMG}/256/movie.293x165.jpg`, hls: `${CDN}/hls_264_master.m3u8?t=1`, dash: `${CDN}/dash_h264.mpd`, highlight: true },
    { id: 300, name: "Gameplay", thumbnail: `${IMG}/300/movie.293x165.jpg`, mp4: `${IMG}/300/movie_max.mp4`, webm: `${IMG}/300/movie480.webm`, highlight: false },
    { id: 500, dash: `${CDN}/only_av1.mpd` },
  ],
  screenshots: [
    { id: 0, thumbnail: `${IMG}/ss_a.600x338.jpg`, full: `${IMG}/ss_a.1920x1080.jpg` },
    { id: 1, full: `${IMG}/ss_b.1920x1080.jpg` },
  ],
};

const steamCalls = (world) => world.calls.filter((call) => call.url.host === "store.steampowered.com");

function world(steamResponse = () => new Response(JSON.stringify(APPDETAILS)), game = GAME) {
  return createWorld({ igdbResponse: () => new Response(JSON.stringify([game])), steamResponse });
}

describe("steam media parsing", () => {
  it("maps both appdetails shapes, highlight movies first, absent fields absent", () => {
    expect(parseAppDetails(1145360, JSON.stringify(APPDETAILS))).toEqual(STEAM);
  });

  it("is null for an unknown app or an app without media", () => {
    expect(parseAppDetails(5, '{"5":{"success":false}}')).toBeNull();
    expect(parseAppDetails(5, '{"5":{"success":true,"data":{"name":"X"}}}')).toBeNull();
    expect(parseAppDetails(5, "[]")).toBeNull();
  });

  it("caps movies and screenshots", () => {
    const movies = Array.from({ length: 40 }, (_, i) => ({ id: i, mp4: { max: `${IMG}/${i}.mp4` } }));
    const screenshots = Array.from({ length: 50 }, (_, i) => ({ id: i, path_full: `${IMG}/${i}.jpg` }));
    const result = parseAppDetails(1, JSON.stringify({ 1: { success: true, data: { movies, screenshots } } }));
    expect(result.movies).toHaveLength(10);
    expect(result.screenshots).toHaveLength(30);
  });

  it("takes the first numeric Steam uid", () => {
    expect(steamAppId([{ uid: "abc", source: 1 }, { uid: "70", source: 5 }, { uid: "620", source: 1 }, { uid: "400", source: 1 }])).toBe(620);
    expect(steamAppId([{ uid: "0620", source: 1 }, { uid: "99999999999", source: 1 }])).toBeNull();
    expect(steamAppId([])).toBeNull();
  });
});

describe("game details with steam media", () => {
  it("adds the steam object and asks appdetails once per app", async () => {
    const w = world();
    const client = createClient(w);
    const response = await client.request(details(113112));
    expect(response.status).toBe(200);
    expect(response.json().steam).toEqual(STEAM);
    expect(response.headers.get("cache-control")).toBe("public, max-age=21600");
    const [call] = steamCalls(w);
    expect(call.url.pathname).toBe("/api/appdetails");
    expect(call.url.searchParams.get("appids")).toBe("1145360");
    expect(call.url.searchParams.get("l")).toBe("english");
    expect(call.redirect).toBe("manual");

    // Another IGDB id for the same Steam app reuses the cached media.
    w.igdbResponse = () => new Response(JSON.stringify([{ ...GAME, id: 9 }]));
    expect((await client.request(details(9))).json().steam).toEqual(STEAM);
    expect(steamCalls(w)).toHaveLength(1);
  });

  it("omits steam without a Steam external game, and makes no Steam call", async () => {
    const w = world(undefined, { id: 7, name: "Paper Lanterns", external_games: [{ uid: "123", external_game_source: 5 }] });
    const response = await createClient(w).request(details(7));
    expect(response.json()).not.toHaveProperty("steam");
    expect(steamCalls(w)).toHaveLength(0);
  });

  it("omits steam when Steam does not know the app, cached like a normal answer", async () => {
    const w = world(() => new Response('{"1145360":{"success":false}}'));
    const response = await createClient(w).request(details(113112));
    expect(response.json()).not.toHaveProperty("steam");
    expect(response.headers.get("cache-control")).toBe("public, max-age=21600");
  });

  for (const [label, steamResponse] of [
    ["busy", () => new Response("UPSTREAM-BODY", { status: 429 })],
    ["broken", () => new Response("<html>UPSTREAM-BODY</html>")],
    ["redirecting", () => new Response(null, { status: 302, headers: { location: "https://store.steampowered.com/" } })],
    ["offline", () => { throw new TypeError("network"); }],
  ]) {
    it(`still answers when Steam is ${label}, cached briefly and retried later`, async () => {
      const w = world(steamResponse);
      const client = createClient(w);
      const response = await client.request(details(113112));
      expect(response.status).toBe(200);
      expect(response.json()).not.toHaveProperty("steam");
      expect(response.json().name).toBe("Hades");
      expect(response.headers.get("cache-control")).toBe("public, max-age=300");
      w.clock += 301_000;
      w.steamResponse = () => new Response(JSON.stringify(APPDETAILS));
      expect((await client.request(details(113112))).json().steam).toEqual(STEAM);
    });
  }
});
