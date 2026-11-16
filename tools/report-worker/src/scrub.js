// The pattern rules of the app's scrubber (app/src/main/java/com/limelight/diag/LogScrubber.kt) in JavaScript, run over a
// report a second time before it is stored. The rules and their order are the app's. Two kinds of difference are deliberate:
//   * What only the device knows (PC names, host addresses, profile names, the client id, the API key) is not here: the app
//     removed those before the upload and the Worker cannot know them.
//   * Patterns that could take quadratic time on hostile input (2 MiB of one repeated character) are written so that they
//     cannot: anchored with a lookbehind, bounded, or matched as a whole run and then judged in code. The matches are the same.
// The text the app sends is already clean, so every rule here is expected to find nothing; a rule that does find something
// is counted, and the count goes back to the app (`rescrubbed`). Rules are idempotent: a placeholder is never replaced again.

const URL_RE = /\b[a-z][a-z0-9+.-]{1,12}:\/\/[^\s"'<>\]\)]+/gi;
const BEARER = /\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]+/gi;
const SSID = /\b(b?ssid)(\s*[=:]\s*)("[^"]*"|[^\r\n,;]*)/gi;
const KEY_VALUE =
  /(?<![A-Za-z0-9])(unique_?id|uuid|device_?name|client_?id|device_?id|android_?id|serial(?:_?no|_?number)?|ro\.serialno|imei|hostname|host_name|pc_?name|computer_?name|user_?name|password|passwd|pwd|pin|(?:access_|refresh_|id_)?token|api[_-]?key|x-api-key|(?:client_)?secret|authorization|auth|salt|cookie|session_?id|rikey(?:id)?|client_?cert|private_?key)(\s*[=:]\s*)((?:Bearer\s+|Basic\s+)?(?:"[^"]*"|'[^']*'|[^\s,;&)}\]]+))/gi;
const QUOTED_HOST = /(\bhost\s+")([^"]*)(")/gi;
const UNKNOWN_HOST = /(UnknownHostException:\s*)(\S+)/g;
const MAC = /(?<![0-9a-f:-])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f]|[:-][0-9a-f])/gi;
const IPV4 = /(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?!\d|\.\d)/g;
// A run of hex digits and colons (then dots, for an IPv4 tail, and a zone); the code decides whether it is an address.
const IPV6_CANDIDATE = /(?<![0-9A-Za-z_:.])[0-9A-Fa-f:]+[0-9A-Fa-f:.]*(?:%[A-Za-z0-9_.-]+)?/g;
const UUID = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
const LONG_HEX = /\b[0-9a-f]{16,}\b/gi;
// The whole run, then judged for a digit and a letter (the app's pattern does that with lookaheads).
const LONG_TOKEN = /\b[A-Za-z0-9_-]{32,}\b/g;
const USER_PATH = /(\/Users\/|\/home\/|[A-Za-z]:[\\/]+Users[\\/]+)([^/\\\s:"']+)/gi;
const VOLUME_PATH = /(\/storage\/|\/mnt\/media_rw\/)(?!emulated\b|self\b)([^/\s:"']+)/g;
// The local part is anchored at the start of its run (same matches, no quadratic scan of a long run without "@").
const EMAIL = /(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+/g;
// DNS limits (labels of at most 63 characters, at most 9 labels) bound the scan.
const LOCAL_HOST =
  /\b[a-z0-9][a-z0-9_-]{0,62}(?:\.[a-z0-9_-]{1,63}){0,8}\.(?:local|lan|home|internal|localdomain|home\.arpa|ts\.net|duckdns\.org|dyndns\.[a-z]+|no-ip\.[a-z]+|myfritz\.net|ddns\.net)\b/gi;

const SAFE_QUERY_VALUE = /^(?:\d{1,6}|\d{2,5}x\d{2,5}(?:x\d{1,3})?|true|false)$/i;
const PUBLIC_HOSTS = ["github.com", "steamgriddb.com", "igdb.com", "steampowered.com", "howlongtobeat.com", "pcgamingwiki.com"];

const MAX_IPV6_LENGTH = 100;

function trimEndChars(text, chars) {
  let end = text.length;
  while (end > 0 && chars.includes(text[end - 1])) end--;
  return text.slice(0, end);
}

function isIpv4(s) {
  const parts = s.split(".");
  return parts.length === 4 && parts.every((p) => p.length > 0 && p.length <= 3 && /^[0-9]+$/.test(p) && Number(p) <= 255);
}

/** A textual IPv6 address with an optional zone and IPv4 tail: eight groups, or fewer around one `::`. */
function isIpv6(candidate) {
  const percent = candidate.indexOf("%");
  const address = percent < 0 ? candidate : candidate.slice(0, percent);
  if ([...address].filter((c) => c === ":").length < 2 || address.includes(":::")) return false;
  const halves = address.split("::");
  if (halves.length > 2) return false;
  const groups = halves.flatMap((half) => (half === "" ? [] : half.split(":")));
  let count = 0;
  for (let i = 0; i < groups.length; i++) {
    const group = groups[i];
    if (group.length >= 1 && group.length <= 4 && /^[0-9a-fA-F]+$/.test(group)) count++;
    else if (i === groups.length - 1 && isIpv4(group)) count += 2;
    else return false;
  }
  return count > 0 && (halves.length === 2 ? count <= 7 : count === 8);
}

function splitHostPort(authority) {
  if (authority.startsWith("[")) {
    const close = authority.indexOf("]");
    if (close > 0) {
      const port = authority.slice(close + 1);
      return [authority.slice(0, close + 1), port.startsWith(":") ? port.slice(1) : port];
    }
  }
  const colons = [...authority].filter((c) => c === ":").length;
  if (colons > 1) return [authority, ""];
  const last = authority.lastIndexOf(":");
  return last < 0 ? [authority, ""] : [authority.slice(0, last), authority.slice(last + 1)];
}

/** The largest N of the placeholders `<name-N>` already in [text], so that new ones never reuse a number of the app's. */
function highestAlias(text, name) {
  let highest = 0;
  for (const match of text.matchAll(new RegExp(`<${name}-(\\d{1,9})>`, "g"))) highest = Math.max(highest, Number(match[1]));
  return highest;
}

/**
 * Removes what the patterns find from [text]. Returns `{ text, count }`: the cleaned text and how many matches were changed.
 * A match that is replaced by itself (a placeholder, a public address, a harmless value) is not counted.
 */
export function rescrub(text) {
  let count = 0;
  const ips = new Map();
  const macs = new Map();
  let ipBase = -1;
  let macBase = -1;
  const ipAlias = (address) => {
    if (ipBase < 0) ipBase = highestAlias(text, "ip");
    const key = address.toLowerCase();
    if (!ips.has(key)) ips.set(key, ipBase + ips.size + 1);
    return `<ip-${ips.get(key)}>`;
  };
  const macAlias = (address) => {
    if (macBase < 0) macBase = highestAlias(text, "mac");
    const key = address.toLowerCase().replace(/-/g, ":");
    if (!macs.has(key)) macs.set(key, macBase + macs.size + 1);
    return `<mac-${macs.get(key)}>`;
  };
  const replace = (input, pattern, fn) =>
    input.replace(pattern, (...args) => {
      const match = args[0];
      const out = fn(...args);
      if (out !== match) count++;
      return out;
    });

  const urlHost = (host) => {
    const bare = host.replace(/^\[/, "").replace(/\]$/, "");
    if (bare === "") return "";
    if (isIpv4(bare) || isIpv6(bare)) return ipAlias(bare);
    const lower = bare.toLowerCase();
    if (PUBLIC_HOSTS.some((publicHost) => lower === publicHost || lower.endsWith(`.${publicHost}`))) return bare;
    return "<host>";
  };

  const scrubUrl = (raw) => {
    // Trailing sentence punctuation is not part of the address
    const trimmed = trimEndChars(raw, ".,;:!");
    const tail = raw.slice(trimmed.length);
    const schemeEnd = trimmed.indexOf("://");
    const scheme = trimmed.slice(0, schemeEnd);
    const rest = trimmed.slice(schemeEnd + 3);
    const found = rest.search(/[/?#]/);
    const authorityEnd = found < 0 ? rest.length : found;
    const authority = rest.slice(0, authorityEnd).split("@").pop();
    let remainder = rest.slice(authorityEnd);
    const hash = remainder.indexOf("#");
    if (hash >= 0) remainder = remainder.slice(0, hash);
    const question = remainder.indexOf("?");
    const query = question < 0 ? "" : remainder.slice(question + 1);
    const path = question < 0 ? remainder : remainder.slice(0, question);
    const [host, port] = splitHostPort(authority);
    const cleanQuery =
      query === ""
        ? ""
        : "?" +
          query
            .split("&")
            .map((pair) => {
              const equals = pair.indexOf("=");
              if (equals < 0) return pair;
              const key = pair.slice(0, equals);
              const value = pair.slice(equals + 1);
              // An empty value stays empty: the app's placeholder `<redacted>` ends the address match, so the
              // value of an already clean address is empty here, and "?a=" must not become "?a=<redacted>" again.
              return `${key}=${value === "" || SAFE_QUERY_VALUE.test(value) ? value : "<redacted>"}`;
            })
            .join("&");
    return `${scheme}://${urlHost(host)}${port === "" ? "" : `:${port}`}${path}${cleanQuery}${tail}`;
  };

  let out = text;
  out = replace(out, URL_RE, (match) => scrubUrl(match));
  // A value that already starts with the app's placeholder is the app's own output: the pattern would take the rest of the
  // line after it for a name and cut it, and a second pass must change nothing.
  out = replace(out, SSID, (match, key, separator, value) => (value.startsWith("<redacted>") ? match : `${key}${separator}<redacted>`));
  out = replace(out, KEY_VALUE, (_m, key, separator) => `${key}${separator}<redacted>`);
  out = replace(out, BEARER, (_m, scheme) => `${scheme} <redacted>`);
  out = replace(out, QUOTED_HOST, (_m, open, _name, close) => `${open}<host>${close}`);
  out = replace(out, UNKNOWN_HOST, (match, prefix, name) => (name.toLowerCase() === "unable" ? match : `${prefix}<host>`));
  out = replace(out, MAC, (match) => macAlias(match));
  out = replace(out, IPV6_CANDIDATE, (whole) => {
    if (whole.length > MAX_IPV6_LENGTH || [...whole].filter((c) => c === ":").length < 2) return whole;
    // Sentence punctuation after an address ("seen fe80::1.") is not part of it
    const address = [whole, trimEndChars(whole, "."), trimEndChars(whole, ".:")].find(isIpv6);
    return address === undefined ? whole : ipAlias(address) + whole.slice(address.length);
  });
  out = replace(out, IPV4, (match) => (isIpv4(match) ? ipAlias(match) : match));
  out = replace(out, UUID, () => "<uuid>");
  out = replace(out, LONG_HEX, () => "<hex>");
  out = replace(out, LONG_TOKEN, (match) => (/[0-9]/.test(match) && /[A-Za-z]/.test(match) ? "<token>" : match));
  out = replace(out, USER_PATH, (_m, prefix) => `${prefix}<user>`);
  out = replace(out, VOLUME_PATH, (_m, prefix) => `${prefix}<volume>`);
  out = replace(out, EMAIL, () => "<email>");
  out = replace(out, LOCAL_HOST, () => "<host>");
  return { text: out, count };
}
