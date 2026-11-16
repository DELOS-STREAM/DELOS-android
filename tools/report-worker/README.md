# delos-reports: the report service

## Purpose

A small Cloudflare Worker (Workers Free plan, Workers KV, no payment method) that keeps the cleaned diagnostic log of a
problem report for 30 days and answers with a short link. The Delos app posts the log that it already cleaned with its
scrubber, puts the link into the prefilled GitHub issue form, and the maintainer opens the link. This document specifies
the service: its API, its limits, what it stores, how to deploy it and how to test it. It does not hold the app side; that
is in [docs/DIAGNOSTICS.md](../../docs/DIAGNOSTICS.md) ("The report service").

The Worker is separate from the metadata Worker in [`tools/metadata-worker/`](../metadata-worker/README.md), which stays
`GET` only and holds the credentials of IGDB and PCGamingWiki. This Worker holds **no credentials**, calls **no other
service** and has no secrets.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The endpoints, limits, storage, second scrub and deploy steps | How the app builds, cleans and uploads the report, and the privacy text: [docs/DIAGNOSTICS.md](../../docs/DIAGNOSTICS.md) |
| The tests of the Worker | The security model as a whole: [SECURITY.md](../../SECURITY.md) |

## Behavior

### API

All bodies are UTF-8. Anything not listed is `404` (any method); a listed path with another method is `405` with an
`Allow` header. There is no listing endpoint, no delete endpoint and no CORS (the app is the only client, so there is no
preflight and no `Access-Control-*` header on any response).

| Endpoint | Request | Success |
|---|---|---|
| `POST /v1/reports` | Body: the cleaned report as `text/plain; charset=utf-8` (`charset` may be left out). Optional `Content-Encoding: gzip`. At most 128 KiB uncompressed. | `201` with `{"id":"<22 characters>","url":"https://<host>/r/<id>","expires":"<ISO 8601 date>","rescrubbed":<count>}` and `Location: <url>` |
| `GET /r/<id>` | `<id>` is 22 characters of base64url (128 random bits) | `200`, the stored text as `text/plain; charset=utf-8`. Headers: `Cache-Control: no-store`, `X-Robots-Tag: noindex, nofollow`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: default-src 'none'; sandbox` |

Every error is `{"error":"<code>"}` with `Cache-Control: no-store`. The code is a fixed identifier; the text of the request,
the body and exception messages are never copied into a response.

| Status | `error` | Meaning |
|---|---|---|
| 400 | `invalid_request` | empty or blank body, a NUL byte, not valid UTF-8, a body that is not gzip although `Content-Encoding: gzip` says so |
| 404 | `not_found` | unknown path, malformed id, unknown or expired report |
| 405 | `method_not_allowed` | `Allow: POST` on `/v1/reports`, `Allow: GET` on `/r/<id>` |
| 413 | `too_large` | `Content-Length` over 128 KiB, more than 128 KiB received, or more than 128 KiB after inflating gzip |
| 415 | `unsupported_media_type` | not `text/plain`, or a `charset` other than UTF-8 |
| 415 | `unsupported_encoding` | a `Content-Encoding` other than `gzip` or `identity` |
| 429 | `rate_limited` | per-client rate limit; `Retry-After` 120 (upload) or 30 (read) seconds |
| 503 | `daily_limit` | the day's upload cap is reached; `Retry-After` 3600 |
| 503 | `not_configured` | the KV binding `REPORTS` is missing |
| 503 | `storage_unavailable` | KV failed |
| 504 | `timeout` | the body did not arrive and pass the checks within 5 s |
| 500 | `internal` | unexpected Worker error |

### Limits

| Limit | Value | Constant (`src/handler.js`) |
|---|---|---|
| Size of a report, uncompressed | 128 KiB (what the second scrub can check within about 5 ms of CPU, see "Evidence") | `MAX_BYTES`; the app's `ReportUploader.MAX_BYTES` is the same number, checked by `ScrubContractTest` and `scrub.test.js` |
| Time for reading the body and checking it | 5 s | `WORK_MS` |
| How long a report is kept | 30 days (KV `expirationTtl`) | `TTL_SECONDS` |
| Uploads per day, all clients | 400 | `DAILY_LIMIT` |
| Uploads per client | 5 per 60 s (binding `UPLOAD_LIMITER`) and 5 per 10 minutes (in-isolate bucket) | `wrangler.toml`, `UPLOAD_CAPACITY` |
| Reads per client | 60 per 60 s (binding `READ_LIMITER`) and a bucket of 60 refilled once per second | `wrangler.toml`, `READ_CAPACITY` |

- **Daily cap.** The Workers Free plan allows 1000 KV writes per day. An upload makes two (the report and the counter
  `cap:<UTC date>`), so 400 uploads use 800 and leave room. The counter is read, increased and written, so two uploads at
  the same moment may count as one: it is a cap, not an accounting. A refused upload does not write.
- **Rate limits.** The Workers Rate Limiting binding can count windows of 10 or 60 seconds only, so the 10-minute limit is an
  in-isolate token bucket next to it. Both are keyed by `CF-Connecting-IP` and are approximate: separate isolates and
  Cloudflare locations keep separate counters. Without the bindings the bucket alone applies. A request is counted before it
  is validated, so a flood of malformed uploads is limited too.
- **No logging.** The Worker logs nothing per request. On a failure with status 500 or more it logs one line:
  `report-worker: <status> <error code>`. A body, a link, an id or an address is never logged.

### What is stored

| KV key | Value | Expires |
|---|---|---|
| `r:<id>` | The cleaned text, as it is served | 30 days after the upload |
| `cap:<UTC date>` | The number of uploads of that day | 2 days |

Nothing else: no address, no user agent, no time stamp beyond the expiry, no app version. The id is 128 random bits
(`crypto.getRandomValues`), so a link cannot be guessed and nothing lists the reports. Whoever has the link can read the
report until it expires; a link in a public GitHub issue is public. There is no delete endpoint: a report that must go earlier
is deleted by the owner with `npx wrangler kv key delete "r:<id>" --binding REPORTS --remote`.

### The second scrub

The app removes addresses, names, ids and keys before it uploads. Before it stores the text, the Worker runs the pattern
rules of the app's scrubber over it again (`src/scrub.js`, a JavaScript port of `LogScrubber.kt`): IPv4, IPv6, MAC addresses,
URLs (user info, host and every query value), e-mail addresses, UUIDs, long hexadecimal runs, long tokens, `key=value` pairs
of secrets, Wi-Fi names, user names in file paths and external volume ids. A leftover is replaced by the same placeholder
(`<ip-N>`, `<mac-N>`, `<redacted>`, ...) instead of rejecting the report, and the count of changed matches is returned as
`rescrubbed`. New `<ip-N>` and `<mac-N>` numbers start after the highest number already in the text, so one placeholder
never stands for two addresses.

The Worker cannot know the names of the user's PCs, the host names, profile names, client id or API keys: only the app does,
and it has removed them. The patterns are the app's, with two deliberate differences: a pattern that could take quadratic
time on hostile input is anchored or bounded (same matches), and a placeholder is never replaced again (a value that already
reads `<redacted>` and an empty query value stay as they are). `app/src/test/java/com/limelight/diag/ScrubContractTest.kt`
writes lines scrubbed by the app, and a whole report, to `test/fixtures/app-scrub-contract.json`; `test/scrub.test.js`
requires that this scrub finds nothing in them and makes the app's output from the raw lines that need no knowledge of the
install. The app's test fails when its scrubber changes without that file.

## Settings

| Name | Kind | Meaning |
|---|---|---|
| `REPORTS` | KV namespace binding | The reports and the daily counter |
| `UPLOAD_LIMITER` | Rate Limiting binding, optional | 5 uploads per 60 s per client |
| `READ_LIMITER` | Rate Limiting binding, optional | 60 reads per 60 s per client |

There are no secrets and no variables.

## Code map

| Path | Role |
|---|---|
| `src/index.js` | The Worker entry point |
| `src/handler.js` | Routing, validation, limits, KV access |
| `src/scrub.js` | The second scrub |
| `src/limits.js` | The in-isolate token bucket |
| `wrangler.toml` | Name, KV binding, rate-limit bindings |
| `test/` | `handler.test.js`, `scrub.test.js`, `harness.js` (a KV fake and a manual clock), `fixtures/app-scrub-contract.json` |

## Develop and test

Node.js 20 or newer. The tests use fakes for KV, the clock and the random source; there is no network.

```sh
cd tools/report-worker
npm ci
npm test
```

## Deploy

The owner does this once; nothing here is done by the repository's tooling, and the Worker is not deployed yet.

1. `npx wrangler login` (or set `CLOUDFLARE_API_TOKEN`).
2. `npx wrangler kv namespace create REPORTS` prints the namespace id. Put it into `wrangler.toml` as the `id` of
   `[[kv_namespaces]]` (it replaces `REPLACE_WITH_THE_ID_OF_THE_KV_NAMESPACE`; the id is not a secret).
3. If another Worker of the account already uses the `namespace_id` values `5911` or `5912` of the rate-limit bindings,
   change them.
4. `npx wrangler deploy` creates the Worker `delos-reports` and prints `https://delos-reports.<account>.workers.dev`.
5. Put that address into the app build as `DELOS_REPORTS_URL` (environment variable or `local.properties`, see
   [docs/BUILDING.md](../../docs/BUILDING.md)) and rebuild. A build without it has no upload: "Report this problem" saves the
   log to Downloads as before.

Smoke test (replace `$URL`):

```sh
curl -s -i -X POST "$URL/v1/reports" -H "Content-Type: text/plain; charset=utf-8" --data-binary "test log"   # 201 with a link
curl -s -i "$URL/r/<id>"                                                    # 200, the text, no-store, noindex
curl -s -i "$URL/r/AAAAAAAAAAAAAAAAAAAAAA"                                  # 404
curl -s -i "$URL/v1/reports"                                                # 405, Allow: POST
curl -s -i -X POST "$URL/v1/reports" -H "Content-Type: application/json" -d "{}"   # 415
```

## Verification

Automated: `npm test` in this directory (`vitest`): the upload and read round trip, the expiry (the `expirationTtl` that
reaches KV, the date in the answer, the read after 30 days), every reject (size announced and streamed, a gzip bomb, invalid
UTF-8, NUL, empty, content type, encoding, a stalled body), gzip, both rate limits and the binding failing, the daily cap and
its reset, the 404 and 405 matrix for every method, no CORS header, KV failing or missing, nothing logged but the status and
the code, every scrub rule with the fixtures of the app's `LogScrubberTest`, and the contract file of the app.

### Not verified

- The Worker was never deployed and never run on Cloudflare: not the KV namespace, the rate-limit bindings, the `expirationTtl`
  of a real KV, nor the link on `workers.dev`.
- CPU time on Cloudflare. The Workers Free plan allows 10 ms of CPU per request. The limit of 128 KiB was chosen from a local
  measurement of the second scrub in Node.js 26 (V8, which `workerd` also uses), see "Evidence"; the rest of a request (gzip
  inflate, UTF-8 decoding, KV) was not measured, and whether Cloudflare stops a request that is a little over was not tested.
  A request that is stopped looks to the app like a failed upload and the report is saved to Downloads. If that happens for
  ordinary reports, lower `MAX_BYTES` (and the app's `ReportUploader.MAX_BYTES`) or use the Workers Paid plan.
- The 400 uploads per day were derived from the documented KV limit of 1000 writes per day, not measured.

## Related

- [docs/DIAGNOSTICS.md](../../docs/DIAGNOSTICS.md), "The report service".
- [SECURITY.md](../../SECURITY.md), "The report service".
- Issue [#83](https://github.com/Meitoncz/Delos/issues/83).

## Evidence (2026-11-16)

Run on macOS with `npx wrangler dev --local` (wrangler 4.162.0, the local `workerd` and a local KV; no Cloudflare account, no network):

| Request | Result |
|---|---|
| `POST /v1/reports`, `text/plain`, a line with an IPv4 address | `201`, `rescrubbed: 1`; `GET /r/<id>` returned the text with the address replaced by `<ip-1>` and the headers of the API table |
| The same with `Content-Encoding: gzip` | `201` |
| Seven uploads from one client address | `201` five times, then `429` |
| `POST` with `application/json` | `415` |
| A 2 MiB log (2,008,889 bytes, gzip-compressed on the wire), when the limit was 2 MiB | `201` in 0.12 s of wall time |

The local runtime does not enforce the Free plan's CPU limit or KV's quotas. `npx wrangler deploy --dry-run` accepted `wrangler.toml` and listed the three bindings.

### Time of the second scrub (2026-11-16)

`rescrub` over a realistic, already scrubbed report in Node.js 26 on an Apple-silicon Mac: 4,000 lines made from the app's own scrubbed
fixtures and ordinary log lines (timestamps, URLs, placeholders, ratios), cut to the size. The first call of a fresh process is the
cold case of a new isolate, the others are warm. Before the regex work was reduced (the IPv6 candidate pattern skips times and
ratios, e-mail and local-host rules are skipped when the text has no `@` or no local suffix), 256 KiB took about 11 ms.

| Size | First call (cold) | Next calls (warm) |
|---|---|---|
| 256 KiB | 8.2 to 8.9 ms | 5.8 to 7.0 ms |
| 128 KiB | 4.6 to 5.0 ms | 3.2 to 3.7 ms |
| 64 KiB | about 2.6 ms | about 1.7 ms |

The largest size at or under about 6 ms cold is 128 KiB, which is the limit. A hostile input of the maximum size (one repeated
character, `a-a-`, ...) is covered by `scrub.test.js` with a loose bound of 500 ms; it may exceed the Free plan's CPU limit and then
only costs that request.
