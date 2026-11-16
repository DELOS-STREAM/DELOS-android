import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { rescrub } from "../src/scrub.js";

const scrub = (text) => rescrub(text).text;

// The cases of app/src/test/java/com/limelight/diag/LogScrubberTest.kt that need no knowledge of this install (the PC names,
// host addresses, profile names, client id and API key are not known to the Worker), with the same inputs. Where a case
// of the app depends on a known name, the expectation is what the patterns alone make of it.
describe("the app's pattern rules", () => {
  it("removes the address of the parallel poll line and keeps the port", () => {
    expect(scrub("Starting parallel poll for Meiton-PC (192.168.0.200:47989, null, null, null)")).toBe(
      "Starting parallel poll for Meiton-PC (<ip-1>:47989, null, null, null)",
    );
  });

  it("keeps the endpoint of an https request but not the query values", () => {
    expect(scrub("https://192.168.0.200:47984/applist?devicename=roth&uniqueid=0123456789ABCDEF&uuid=11111111-2222-3333-4444-555555555555")).toBe(
      "https://<ip-1>:47984/applist?devicename=<redacted>&uniqueid=<redacted>&uuid=<redacted>",
    );
  });

  it("keeps small query values readable", () => {
    expect(scrub("http://10.0.0.5:47989/launch?appid=1234&mode=1920x1080x60&rikey=00112233445566778899aabbccddeeff")).toBe(
      "http://<ip-1>:47989/launch?appid=1234&mode=1920x1080x60&rikey=<redacted>",
    );
  });

  it("removes the credentials, the host and the token of a URL", () => {
    const out = scrub("GET https://user:hunter2@meiton.example.org/api/v1/status?token=abc123 failed");
    expect(out).toBe("GET https://<host>/api/v1/status?token=<redacted> failed");
  });

  it("keeps the host of a public service", () => {
    expect(scrub("GET https://www.steamgriddb.com/api/v2/grids/game/5254?types=static")).toBe(
      "GET https://www.steamgriddb.com/api/v2/grids/game/5254?types=<redacted>",
    );
  });

  it("replaces an unknown URL host", () => {
    expect(scrub("https://meiton-pc:47984/serverinfo")).toBe("https://<host>:47984/serverinfo");
  });

  it("removes the unique id by its shape", () => {
    expect(scrub("UID is now: fedcba9876543210")).toBe("UID is now: <hex>");
  });

  it("numbers IPv4 addresses and keeps the number of a repeated one", () => {
    expect(scrub("192.168.0.200 -> 8.8.8.8 -> 192.168.0.200")).toBe("<ip-1> -> <ip-2> -> <ip-1>");
  });

  it("does not take an octet out of range for an address", () => {
    expect(scrub("version 999.1.2.3 built")).toBe("version 999.1.2.3 built");
  });

  it("removes IPv6 addresses with a zone, brackets and a mapped tail", () => {
    expect(scrub("addr fe80::1c2d:3eff:fe4f:5a6b%wlan0 used")).toBe("addr <ip-1> used");
    expect(scrub("connect [2001:db8::1]:47989")).toBe("connect [<ip-1>]:47989");
    expect(scrub("mapped 2001:db8:0:0:0:0:0:1")).toBe("mapped <ip-1>");
    expect(scrub("mapped ::ffff:192.168.1.7")).toBe("mapped <ip-1>");
    expect(scrub("seen fe80::1.")).toBe("seen <ip-1>.");
  });

  it("does not take times and ports for addresses", () => {
    const line = "2026-11-10 12:34:56.789 I/Lime [main] ratio 16:9 at 12:34:56 on port 47989 (Foo::bar)";
    expect(scrub(line)).toBe(line);
  });

  it("numbers MAC addresses", () => {
    expect(scrub("wake 00:1A:2B:3C:4D:5E and aa-bb-cc-dd-ee-ff and 00:1a:2b:3c:4d:5e")).toBe("wake <mac-1> and <mac-2> and <mac-1>");
  });

  it("removes API keys by key and by shape", () => {
    expect(scrub("api_key=abcDEF123")).toBe("api_key=<redacted>");
    expect(scrub("Authorization: Bearer abc.def-ghi")).toBe("Authorization: <redacted>");
    expect(scrub("sent 9fB3kQ7xL2mZ8vC1nR5tY0uW4pH6jD3s done")).toBe("sent <token> done");
    expect(scrub("steamgriddb 0123456789abcdef0123456789abcdef")).toBe("steamgriddb <hex>");
    expect(scrub("id 11111111-2222-3333-4444-555555555555")).toBe("id <uuid>");
  });

  it("removes user names in paths and external volume ids but keeps the app's own storage path", () => {
    expect(scrub("/Users/meiton/Library/Caches/x")).toBe("/Users/<user>/Library/Caches/x");
    expect(scrub("C:\\Users\\Meiton\\AppData\\x")).toBe("C:\\Users\\<user>\\AppData\\x");
    expect(scrub("/home/bob/.config")).toBe("/home/<user>/.config");
    expect(scrub("/storage/1A2B-3C4D/Download/log.txt")).toBe("/storage/<volume>/Download/log.txt");
    expect(scrub("/storage/emulated/0/Download/log.txt")).toBe("/storage/emulated/0/Download/log.txt");
  });

  it("removes Wi-Fi names and serial numbers", () => {
    expect(scrub('connected to SSID: "Home Wifi 5G"')).toBe("connected to SSID: <redacted>");
    expect(scrub("BSSID=aa:bb:cc:dd:ee:ff")).toBe("BSSID=<redacted>");
    expect(scrub("serial: R5CT1234ABC")).toBe("serial: <redacted>");
  });

  it("removes e-mail addresses and local host names", () => {
    expect(scrub("from bob@example.com")).toBe("from <email>");
    expect(scrub("cannot reach desktop-4f2k.local")).toBe("cannot reach <host>");
    expect(scrub("java.net.UnknownHostException: gamingrig")).toBe("java.net.UnknownHostException: <host>");
    expect(scrub('Unable to resolve host "gamingrig": No address')).toBe('Unable to resolve host "<host>": No address');
  });

  it("leaves ordinary lines alone", () => {
    for (const line of [
      "2026-11-10 12:34:56.789 I/Stream [main] Negotiated HEVC Main10 4:2:0 HDR at 1920x1080@120, bitrate 80000 kbps",
      "Process started: Delos 0.1.0 (debug), pid 12345",
    ]) {
      expect(rescrub(line)).toEqual({ text: line, count: 0 });
    }
  });

  it("changes nothing more when it runs twice", () => {
    const text =
      "poll Meiton-PC (192.168.0.200:47989) https://192.168.0.200:47984/applist?uniqueid=0123456789abcdef mac 00:1A:2B:3C:4D:5E";
    const once = scrub(text);
    expect(scrub(once)).toBe(once);
  });

  it("leaves nothing of a mixed log", () => {
    const secrets = ["192.168.0.200", "10.0.0.5", "fe80::1c2d:3eff:fe4f:5a6b", "00:1A:2B:3C:4D:5E", "0123456789abcdef", "hunter2", "Home Wifi", "meiton@example.com"];
    const log = [
      "Starting parallel poll for Meiton-PC (192.168.0.200:47989, 10.0.0.5:47989, null, fe80::1c2d:3eff:fe4f:5a6b)",
      "https://192.168.0.200:47984/applist?devicename=x&uniqueid=0123456789abcdef&uuid=1",
      "Sending WoL to 00:1A:2B:3C:4D:5E for Living Room via meiton.example.org",
      "UID is now: 0123456789abcdef",
      "artwork key sgdbSecretKey99 password=hunter2",
      'SSID: "Home Wifi" owner meiton@example.com',
    ].join("\n");
    const out = scrub(log);
    for (const secret of secrets) expect(out.toLowerCase(), secret).not.toContain(secret.toLowerCase());
  });
});

describe("the second pass over a clean report", () => {
  it("counts only what it changed", () => {
    expect(rescrub("a clean line with <ip-1>:47989 and <mac-1> and <pc-2> and <redacted> and <hex>").count).toBe(0);
    const found = rescrub("leftover 192.168.0.200 and 10.0.0.5 and bob@example.com");
    expect(found.text).toBe("leftover <ip-1> and <ip-2> and <email>");
    expect(found.count).toBe(3);
  });

  it("keeps the app's numbers and never reuses one for another address", () => {
    const text = "stream to <ip-1>, poll <ip-2>, wake <mac-1>; leftover 203.0.113.9 and aa:bb:cc:dd:ee:ff and 203.0.113.9";
    expect(rescrub(text).text).toBe("stream to <ip-1>, poll <ip-2>, wake <mac-1>; leftover <ip-3> and <mac-2> and <ip-3>");
  });

  it("does not turn the app's own placeholders in a URL query into more placeholders", () => {
    const clean = "GET https://www.steamgriddb.com/api/v2/grids/game/5254?types=<redacted> and https://<ip-1>:47984/applist?uniqueid=<redacted>";
    expect(rescrub(clean)).toEqual({ text: clean, count: 0 });
  });

  it("does not cut the rest of a line after the app's own Wi-Fi placeholder", () => {
    const clean = "connected to SSID: <redacted> and 5 GHz, then SSID=<redacted> owner <name>";
    expect(rescrub(clean)).toEqual({ text: clean, count: 0 });
    expect(rescrub('SSID: "Home Wifi" owner bob').text).toBe("SSID: <redacted> owner bob");
    expect(rescrub("SSID: Home Wifi 5G").text).toBe("SSID: <redacted>");
  });

  it("counts a changed match once per match", () => {
    expect(rescrub("token=abc and token=<redacted>").count).toBe(1);
  });
});

describe("hostile input", () => {
  const MIB = 1024 * 1024;
  const cases = {
    "one letter": "a".repeat(2 * MIB),
    "hex and colons": "a:".repeat(MIB),
    "colons": ":".repeat(2 * MIB),
    "labels": "a.".repeat(MIB),
    "dashes": "a-".repeat(MIB),
    "dots and digits": "1.".repeat(MIB),
    "address characters": "[email@".repeat(300_000),
    "spaces after a key": `pin${" ".repeat(2 * MIB)}`,
  };
  for (const [name, text] of Object.entries(cases)) {
    it(`takes bounded time on 2 MiB of ${name}`, () => {
      const started = performance.now();
      rescrub(text);
      expect(performance.now() - started).toBeLessThan(2500);
    });
  }
});

// tools/report-worker/test/fixtures/app-scrub-contract.json is written by the app's ScrubContractTest from the app's own
// scrubber, so the two implementations cannot drift apart unnoticed.
describe("the contract with the app's scrubber", () => {
  const fixtures = JSON.parse(readFileSync(new URL("./fixtures/app-scrub-contract.json", import.meta.url), "utf8"));

  it("has fixtures", () => {
    expect(fixtures.length).toBeGreaterThan(30);
  });

  for (const fixture of fixtures) {
    it(`finds nothing in what the app produced from: ${fixture.raw.slice(0, 60)}`, () => {
      expect(rescrub(fixture.scrubbed)).toEqual({ text: fixture.scrubbed, count: 0 });
    });
    if (fixture.patternsOnly) {
      it(`makes what the app makes of: ${fixture.raw.slice(0, 60)}`, () => {
        expect(rescrub(fixture.raw).text).toBe(fixture.scrubbed);
      });
    }
  }
});
