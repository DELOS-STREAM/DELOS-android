#!/usr/bin/env python3
"""Generates the DELOS interface sounds and the comparison gallery they were chosen in.

Owner's choice, 2026-11-27: "A · Csound" (csound_family.py), every cue in 4 takes that the app plays in turn. This
script renders the numpy families (A, A+, A++) for comparison, runs csound_family.py (needs the csound binary,
`brew install csound`), writes build/sound-gallery/index.html to listen to all of them, and installs the chosen set
as app/src/main/res/raw/ui_<action>.wav and ui_<action>_v2..v4.wav (48 kHz, 24-bit, stereo). Pure synthesis: no
recordings, no third-party audio. Loudness: loudest 400 ms at -30 dBFS RMS, peaks at or below -3 dBFS.

    python3 tools/sounds/make_ui_sounds.py
"""
import json, math, shutil, wave
from pathlib import Path
import numpy as np

SR = 48_000
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "sound-gallery"
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
CHOSEN = "bubbles_csound"
rng = np.random.default_rng(7)

# ---------- building blocks ----------
def t_of(dur): return np.arange(int(dur * SR)) / SR

def env(dur, attack=0.004, decay=0.12, hold=0.0, curve=1.0):
    """Exponential-ish attack/decay envelope; [decay] is the time to about -40 dB after the hold."""
    t = t_of(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1) ** 0.6
    d = np.where(t < attack + hold, 1.0, np.exp(-(t - attack - hold) / max(decay, 1e-4) * 4.6) ** curve)
    return a * d

def osc(freq, dur, kind="sine", phase=0.0):
    """[freq]: a number or an array (Hz per sample)."""
    n = int(dur * SR)
    f = np.full(n, freq, dtype=float) if np.isscalar(freq) else np.asarray(freq, dtype=float)[:n]
    ph = 2 * np.pi * np.cumsum(f) / SR + phase
    if kind == "sine": return np.sin(ph)
    if kind == "tri": return 2 / np.pi * np.arcsin(np.sin(ph))
    if kind == "saw":  # band-limited enough for these pitches: a few harmonics
        return sum(np.sin(k * ph) / k for k in range(1, 9)) * 0.6
    if kind == "square": return sum(np.sin(k * ph) / k for k in range(1, 12, 2)) * 0.9
    raise ValueError(kind)

def glide(f0, f1, dur, shape=3.0):
    """Exponential pitch glide from f0 to f1 (Hz); [shape] > 1 is fast at first."""
    t = t_of(dur) / dur
    k = 1 - np.exp(-shape * t)
    k /= (1 - math.exp(-shape))
    return f0 * (f1 / f0) ** k

def noise(dur): return rng.standard_normal(int(dur * SR))

def spectral(x, lo=None, hi=None, peak=None, q=4.0):
    """Static filter in the frequency domain: high-pass [lo], low-pass [hi], resonant peak at [peak] Hz."""
    n = len(x); X = np.fft.rfft(x); f = np.fft.rfftfreq(n, 1 / SR)
    H = np.ones_like(f)
    if lo: H *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1)) ** 4)
    if hi: H *= 1 / np.sqrt(1 + (f / hi) ** 4)
    if peak: H = H * 0.15 + H / (1 + ((f - peak) / (peak / q)) ** 2)
    return np.fft.irfft(X * H, n)

def bell(freq, dur, ratios=(1, 2.76, 5.40, 8.93), gains=(1, .45, .22, .1), decay=0.5):
    """Inharmonic partials (struck glass / small bell); higher partials die faster."""
    out = np.zeros(int(dur * SR))
    for r, g in zip(ratios, gains):
        out += g * osc(freq * r, dur) * env(dur, 0.001, decay / (1 + 0.6 * (r - 1)))
    return out

def place(total, *parts):
    """Mix (offset seconds, signal) pairs into a buffer [total] seconds long."""
    out = np.zeros(int(total * SR))
    for off, sig in parts:
        i = int(off * SR); j = min(len(out), i + len(sig))
        out[i:j] += sig[: j - i]
    return out

ROOM_SCALE = 1.0  # the family's room amount (set per gallery column)

def room(x, size=0.35, mix=0.18, bright=6000):
    """A small stereo room: decorrelated decaying noise impulse responses, convolved by FFT."""
    mix *= ROOM_SCALE
    size = max(0.05, size * (0.4 + 0.6 * ROOM_SCALE))
    n_ir = int(size * SR)
    t = np.arange(n_ir) / SR
    out = []
    for seed in (1, 2):
        r = np.random.default_rng(seed).standard_normal(n_ir) * np.exp(-t / (size / 5))
        r = spectral(r, lo=200, hi=bright)
        r /= np.sqrt(np.sum(r ** 2)) + 1e-9
        n = len(x) + n_ir
        wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(r, n), n)
        dry = np.concatenate([x, np.zeros(n_ir)])
        out.append(dry * (1 - mix) + wet * mix * 3)
    st = np.stack(out, axis=1)
    # trim the tail once it is under -70 dB
    a = np.abs(st).max(axis=1); thr = a.max() * 10 ** (-70 / 20)
    last = np.nonzero(a > thr)[0]
    st = st[: (last[-1] + 1) if len(last) else len(st)]
    # 5 ms fade-out so nothing clicks at the end
    f = min(len(st), int(0.005 * SR)); st[-f:] *= np.linspace(1, 0, f)[:, None]
    return st

def pan(x, p):
    """Mono to stereo, p in -1..1."""
    return np.stack([x * math.sqrt((1 - p) / 2), x * math.sqrt((1 + p) / 2)], axis=1)

# ---------- loudness ----------
# 6 dB louder than the first target (owner, 2026-11-27: some too quiet at full volume); peaks still keep 3 dB headroom
TARGET, PEAK = -30.0, -3.0
def finish(st):
    m = st.mean(axis=1)
    pad = np.concatenate([m, np.zeros(int(0.4 * SR))])
    w = int(0.4 * SR)
    loud = max(np.sqrt(np.mean(pad[i:i + w] ** 2)) for i in range(0, max(1, len(m)), 480))
    g = min(TARGET - 20 * math.log10(loud + 1e-12), PEAK - 20 * math.log10(np.abs(st).max() + 1e-12))
    return st * 10 ** (g / 20)

def write(path, st):
    path.parent.mkdir(parents=True, exist_ok=True)
    v = np.clip(np.round(st * (1 << 23)), -(1 << 23), (1 << 23) - 1).astype(np.int32)
    v = np.where(v < 0, v + (1 << 24), v).reshape(-1)
    b = np.stack([v & 255, (v >> 8) & 255, (v >> 16) & 255], axis=1).astype(np.uint8).tobytes()
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2); w.setsampwidth(3); w.setframerate(SR); w.writeframes(b)

# ---------- family A: Bubbles (organic pops, drops and bubbles) ----------
PHYS = False      # A+: physically modelled bubbles and pops instead of the plain blips
JITTER = 0.0      # A+ variations: random spread of pitch and length per call
vrng = np.random.default_rng(11)  # its own generator, so the plain set renders exactly as before
PUNCH = 0.0      # A++: a sharper strike on each bubble (0 = A+, 1 = like Csound's impulse-driven drops)
RS = 1.0         # A++: spacing of the bubbles in rolls (open, complete, undo, stream start/end); below 1 is tighter

def jit(x, amount=1.0):
    return x * (1 + (vrng.random() * 2 - 1) * JITTER * amount) if JITTER else x

def minnaert(f0, f1, dur, body):
    """A bubble after van den Doel (2005): a sine at the Minnaert frequency of the bubble, damped as a real bubble is
    (d = 0.13 f + 0.0072 f^1.5, slowed here so it rings a little longer), its pitch rising as it shrinks towards the
    surface; a second, weaker cavity mode and a soft wet transient make it organic."""
    t = t_of(dur)
    d = (0.13 * f0 + 0.0072 * f0 ** 1.5) * min(1.0, 0.02 / max(body, 1e-3))
    sigma = math.log(f1 / f0) / dur
    f = f0 * np.exp(sigma * t * (1 - np.exp(-t / (dur * 0.35))))
    a = np.exp(-d * (1 + 0.6 * PUNCH) * t) * np.clip(t / (0.0015 - 0.0012 * PUNCH), 0, 1)
    main = np.sin(2 * np.pi * np.cumsum(f) / SR) * a
    cav = np.sin(2 * np.pi * np.cumsum(f * 2.31) / SR) * np.exp(-d * 2.2 * t) * 0.12
    wet = spectral(noise(min(dur, 0.012)), lo=1200 + 1200 * PUNCH, hi=6000 + 3000 * PUNCH) * env(min(dur, 0.012), 0.0006, 0.005) * 0.18 * (1 + 1.2 * PUNCH)
    # the strike: a short ring above the bubble, like an impulse hitting a resonator
    knock = np.sin(2 * np.pi * f0 * 1.52 * t) * np.exp(-t / 0.004) * 0.35 * PUNCH
    return place(dur, (0, main + cav + knock), (0, wet))

def bubble(f0, f1, dur=0.09, body=0.06, kind="sine"):
    """A water-drop blip: a sine whose pitch rises quickly, with a soft click on top."""
    if PHYS:
        k = jit(1.0); return minnaert(f0 * k, f1 * k, jit(dur, 2), jit(body, 2))
    s = osc(glide(f0, f1, dur, 6), dur, kind) * env(dur, 0.002, body)
    click = spectral(noise(0.01), lo=2000, hi=9000) * env(0.01, 0.0005, 0.006) * 0.25
    return place(dur, (0, s), (0, click))

def pop(f0, f1, dur=0.07):
    """A cork-like pop: falling pitch, very short."""
    if PHYS:
        # a cork: two modes falling together, a burst of air at the start
        k = jit(1.0); dur = jit(dur, 2); t = t_of(dur)
        f = glide(f0 * k, f1 * k, dur, 8)
        body = (np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * np.sin(2 * np.pi * np.cumsum(f * 1.71) / SR)) * env(dur, 0.001, 0.035)
        air = spectral(noise(min(dur, 0.015)), lo=800, hi=4000) * env(min(dur, 0.015), 0.0005, 0.008) * 0.3
        return place(dur, (0, body), (0, air))
    return osc(glide(f0, f1, dur, 8), dur) * env(dur, 0.0015, 0.04)

A = {
    "move":        lambda: room(bubble(900, 1250, 0.07, 0.035), 0.2, 0.10),
    "select":      lambda: room(place(0.2, (0, bubble(700, 1100)), (0.055, bubble(1050, 1500, 0.08, 0.05))), 0.25, 0.14),
    "back":        lambda: room(place(0.22, (0, pop(1200, 700)), (0.06, pop(900, 520, 0.09))), 0.25, 0.14),
    "open":        lambda: room(place(0.4, *[(i * 0.045 * RS, bubble(600 + i * 160, 900 + i * 220, 0.08, 0.05) * (0.7 + 0.1 * i)) for i in range(4)]), 0.35, 0.2),
    "error":       lambda: room(place(0.3, (0, osc(glide(320, 260, 0.12), 0.12, "tri") * env(0.12, 0.003, 0.09)), (0.13, osc(glide(300, 230, 0.14), 0.14, "tri") * env(0.14, 0.003, 0.11))), 0.25, 0.12),
    "toggle":      lambda: room(place(0.16, (0, pop(1400, 900, 0.05)), (0.04, bubble(800, 1300, 0.07, 0.04))), 0.2, 0.12),
    "step":        lambda: room(bubble(1500, 1700, 0.04, 0.018) * 0.8, 0.15, 0.08),
    "tab":         lambda: room(place(0.2, (0, bubble(500, 820, 0.09, 0.05)), (0.05, bubble(700, 1050, 0.08, 0.045) * 0.7)), 0.3, 0.16),
    "complete":    lambda: room(place(0.6, *[(i * 0.07 * RS, bubble(f, f * 1.35, 0.12, 0.09)) for i, f in enumerate((700, 880, 1050, 1400))]), 0.45, 0.22),
    "undo":        lambda: room(place(0.35, *[(i * 0.05 * RS, bubble(f * 1.3, f, 0.09, 0.06)) for i, f in enumerate((1300, 1050, 880))]), 0.35, 0.18),
    "screenshot":  lambda: room(place(0.3, (0, pop(2200, 1400, 0.03) * 0.8), (0.035, pop(1800, 1200, 0.03) * 0.6), (0.07, bubble(1100, 1600, 0.1, 0.07))), 0.3, 0.16),
    "toast_info":  lambda: room(bubble(1000, 1300, 0.1, 0.06) * 0.8, 0.3, 0.16),
    "toast_warning": lambda: room(place(0.28, (0, bubble(600, 760, 0.1, 0.06)), (0.11, bubble(600, 760, 0.1, 0.06))), 0.3, 0.14),
    "toast_success": lambda: room(place(0.35, (0, bubble(800, 1100, 0.1, 0.07)), (0.08, bubble(1200, 1650, 0.14, 0.1))), 0.35, 0.18),
    "pad_connect": lambda: room(place(0.35, (0, bubble(500, 700, 0.1, 0.07)), (0.09, bubble(750, 1050, 0.14, 0.1))), 0.35, 0.18),
    "pad_disconnect": lambda: room(place(0.35, (0, bubble(1050, 750, 0.1, 0.07)), (0.09, bubble(700, 480, 0.14, 0.1))), 0.35, 0.18),
    "stream_start": lambda: room(place(0.8, *[(i * 0.06 * RS, bubble(400 + i * 120, 700 + i * 160, 0.09, 0.06) * (0.45 + 0.05 * i)) for i in range(7)]), 0.5, 0.22),
    "stream_end":  lambda: room(place(0.8, *[(i * 0.06 * RS, bubble(1100 - i * 110, 800 - i * 90, 0.09, 0.06) * (0.75 - 0.05 * i)) for i in range(7)]), 0.5, 0.22),
    # Context menu: two quick clicks, rising when it opens and the same two falling when it closes
    "menu_open":   lambda: room(place(0.12, (0, pop(950, 760, 0.04) * 0.8), (0.045, pop(1350, 1080, 0.04))), 0.2, 0.1),
    "menu_close":  lambda: room(place(0.12, (0, pop(1350, 1080, 0.04)), (0.045, pop(950, 760, 0.04) * 0.8)), 0.2, 0.1),
}

# ---------- family B: Glass (crystal taps, chimes, airy sweeps) ----------
def tap(freq, dur=0.25, decay=0.18, bright=1.0):
    hit = spectral(noise(0.012), lo=3000, hi=12000) * env(0.012, 0.0004, 0.006) * 0.35 * bright
    return place(dur, (0, bell(freq, dur, decay=decay)), (0, hit))

def air(dur, lo, hi, attack, decay, up=True):
    n = spectral(noise(dur), lo=lo, hi=hi)
    return n * env(dur, attack, decay)

B = {
    "move":        lambda: room(tap(2400, 0.12, 0.06, 0.6) * 0.8, 0.25, 0.14, 9000),
    "select":      lambda: room(place(0.4, (0, tap(1568, 0.35, 0.22)), (0.05, tap(2349, 0.35, 0.2) * 0.6)), 0.4, 0.2, 9000),
    "back":        lambda: room(place(0.4, (0, tap(2093, 0.3, 0.18)), (0.06, tap(1397, 0.35, 0.22) * 0.7)), 0.4, 0.2, 9000),
    "open":        lambda: room(place(0.7, (0, air(0.35, 2000, 8000, 0.25, 0.1) * 0.25), *[(0.05 + i * 0.05, tap(f, 0.45, 0.3) * (0.55 + 0.1 * i)) for i, f in enumerate((1047, 1319, 1568, 2093))]), 0.5, 0.24, 9000),
    "error":       lambda: room(place(0.4, (0, tap(740, 0.3, 0.2) * 0.9), (0.12, tap(698, 0.35, 0.24) * 0.9)), 0.35, 0.16, 7000),
    "toggle":      lambda: room(place(0.3, (0, tap(1760, 0.25, 0.14)), (0.03, tap(2637, 0.2, 0.1) * 0.4)), 0.3, 0.16, 9000),
    "step":        lambda: room(tap(3136, 0.08, 0.04, 0.5) * 0.7, 0.2, 0.1, 10000),
    "tab":         lambda: room(place(0.4, (0, air(0.2, 2500, 9000, 0.06, 0.12) * 0.3), (0.05, tap(1976, 0.3, 0.18) * 0.7)), 0.4, 0.2, 9000),
    "complete":    lambda: room(place(1.0, *[(i * 0.08, tap(f, 0.8, 0.55) * 0.8) for i, f in enumerate((1047, 1319, 1568, 2093, 2637))]), 0.7, 0.28, 9000),
    "undo":        lambda: room(place(0.6, *[(i * 0.06, tap(f, 0.5, 0.3) * 0.8) for i, f in enumerate((2093, 1568, 1319))]), 0.5, 0.22, 9000),
    "screenshot":  lambda: room(place(0.6, (0, spectral(noise(0.04), lo=1500, hi=10000) * env(0.04, 0.0005, 0.02) * 0.7), (0.035, spectral(noise(0.05), lo=1200, hi=8000) * env(0.05, 0.0005, 0.03) * 0.5), (0.09, tap(2637, 0.5, 0.35) * 0.7)), 0.45, 0.2, 9000),
    "toast_info":  lambda: room(tap(1760, 0.4, 0.25) * 0.8, 0.45, 0.22, 9000),
    "toast_warning": lambda: room(place(0.5, (0, tap(1175, 0.35, 0.22)), (0.13, tap(1175, 0.35, 0.22) * 0.85)), 0.45, 0.2, 8000),
    "toast_success": lambda: room(place(0.7, (0, tap(1319, 0.5, 0.3)), (0.09, tap(1976, 0.6, 0.4))), 0.55, 0.24, 9000),
    "pad_connect": lambda: room(place(0.7, (0, tap(988, 0.5, 0.3)), (0.1, tap(1480, 0.6, 0.4))), 0.55, 0.24, 9000),
    "pad_disconnect": lambda: room(place(0.7, (0, tap(1480, 0.5, 0.3)), (0.1, tap(988, 0.6, 0.4))), 0.55, 0.24, 9000),
    "stream_start": lambda: room(place(1.1, (0, air(0.8, 800, 9000, 0.55, 0.25) * 0.5), (0.55, tap(1568, 0.5, 0.35) * 0.6), (0.6, tap(2349, 0.5, 0.35) * 0.4)), 0.7, 0.28, 9000),
    "stream_end":  lambda: room(place(1.1, (0, tap(2349, 0.5, 0.35) * 0.5), (0.05, tap(1568, 0.5, 0.35) * 0.6), (0.1, air(0.8, 600, 7000, 0.05, 0.6) * 0.5)), 0.7, 0.28, 9000),
    "menu_close":  lambda: room(tap(1976, 0.15, 0.08, 0.5) * 0.7, 0.25, 0.12, 9000),
}

# ---------- family C: Soft synth (warm plucks, rolls and swells) ----------
def pluck(freq, dur=0.25, decay=0.15, kind="saw", cutoff=2500, glide_to=None):
    f = glide(freq, glide_to, dur, 6) if glide_to else freq
    s = osc(f, dur, kind) * env(dur, 0.003, decay)
    return spectral(s, lo=120, hi=cutoff)

def roll(freqs, step, dur_each=0.16, decay=0.1, cutoff=3000):
    return place(step * len(freqs) + dur_each, *[(i * step, pluck(f, dur_each, decay, cutoff=cutoff) * (0.7 + 0.3 * i / len(freqs))) for i, f in enumerate(freqs)])

C = {
    "move":        lambda: room(pluck(880, 0.08, 0.035, "tri", 4000), 0.2, 0.1),
    "select":      lambda: room(place(0.3, (0, pluck(659, 0.2, 0.12)), (0.05, pluck(988, 0.22, 0.14))), 0.3, 0.16),
    "back":        lambda: room(place(0.3, (0, pluck(988, 0.2, 0.12)), (0.05, pluck(659, 0.22, 0.14))), 0.3, 0.16),
    "open":        lambda: room(roll((523, 659, 784, 1047), 0.04, 0.2, 0.13), 0.4, 0.2),
    "error":       lambda: room(place(0.35, (0, pluck(294, 0.16, 0.1, "square", 1400)), (0.12, pluck(277, 0.2, 0.13, "square", 1300))), 0.3, 0.14),
    "toggle":      lambda: room(pluck(784, 0.16, 0.08, "saw", 3000, glide_to=1047), 0.25, 0.14),
    "step":        lambda: room(pluck(1175, 0.05, 0.02, "tri", 5000) * 0.8, 0.15, 0.08),
    "tab":         lambda: room(pluck(587, 0.22, 0.12, "saw", 2400, glide_to=880), 0.35, 0.18),
    "complete":    lambda: room(roll((523, 659, 784, 1047, 1319), 0.07, 0.4, 0.3, 3500), 0.6, 0.26),
    "undo":        lambda: room(roll((1047, 784, 659), 0.05, 0.25, 0.16), 0.4, 0.2),
    "screenshot":  lambda: room(place(0.4, (0, spectral(noise(0.03), lo=1500, hi=7000) * env(0.03, 0.0005, 0.015) * 0.6), (0.03, spectral(noise(0.04), lo=1200, hi=6000) * env(0.04, 0.0005, 0.02) * 0.45), (0.08, pluck(1319, 0.25, 0.16, "tri", 5000))), 0.35, 0.16),
    "toast_info":  lambda: room(pluck(988, 0.3, 0.2, "tri", 4000), 0.4, 0.2),
    "toast_warning": lambda: room(place(0.4, (0, pluck(587, 0.2, 0.12)), (0.13, pluck(587, 0.22, 0.14))), 0.35, 0.16),
    "toast_success": lambda: room(place(0.45, (0, pluck(784, 0.25, 0.16)), (0.08, pluck(1175, 0.32, 0.22))), 0.45, 0.22),
    "pad_connect": lambda: room(place(0.45, (0, pluck(523, 0.25, 0.16)), (0.09, pluck(784, 0.32, 0.22))), 0.45, 0.22),
    "pad_disconnect": lambda: room(place(0.45, (0, pluck(784, 0.25, 0.16)), (0.09, pluck(523, 0.32, 0.22))), 0.45, 0.22),
    "stream_start": lambda: room(place(1.0, (0, spectral(osc(glide(110, 440, 0.7, 2), 0.7, "saw"), lo=150, hi=2500) * env(0.7, 0.5, 0.15) * 0.4), (0.55, roll((784, 1047, 1319), 0.05, 0.3, 0.2))), 0.6, 0.24),
    "stream_end":  lambda: room(place(1.0, (0, roll((1319, 1047, 784), 0.05, 0.3, 0.2)), (0.12, spectral(osc(glide(440, 110, 0.7, 2), 0.7, "saw"), lo=150, hi=2500) * env(0.7, 0.05, 0.5) * 0.4)), 0.6, 0.24),
    "menu_close":  lambda: room(pluck(740, 0.1, 0.05, "tri", 3000, glide_to=587) * 0.8, 0.2, 0.1),
}
# ---------- derivatives of A ----------
def repitch(st, r):
    """Stereo resample by [r] (>1 higher and shorter, <1 deeper and longer), like a tape speed change."""
    n = len(st); m = max(2, int(n / r))
    x = np.linspace(0, n - 1, m)
    return np.stack([np.interp(x, np.arange(n), st[:, c]) for c in range(st.shape[1])], axis=1)

def wobble(x, rate=7.0, depth=0.18):
    """Amplitude tremolo (a nervous, uncertain feel for warnings and errors)."""
    t = np.arange(len(x)) / SR
    return x * (1 - depth + depth * np.sin(2 * np.pi * rate * t))

def tick(freq=2600, dur=0.03, body=0.012):
    """A small wooden tick: a band of noise around [freq] with a short sine knock."""
    n = spectral(noise(dur), lo=freq * 0.6, hi=freq * 1.6) * env(dur, 0.0005, body * 0.6)
    k = osc(freq * 0.5, dur) * env(dur, 0.0008, body)
    return n * 0.6 + k * 0.5

def swish(dur, f0, f1, attack, decay):
    """A swish: noise through a band that slides from f0 to f1 (built from short filtered slices)."""
    out = np.zeros(int(dur * SR)); seg = int(0.02 * SR)
    for i in range(0, len(out), seg):
        f = f0 * (f1 / f0) ** (i / len(out))
        out[i:i + seg] = spectral(noise(seg / SR), lo=f * 0.7, hi=f * 1.4)[: len(out[i:i + seg])]
    return out * env(dur, attack, decay)

# A · varied: the same bubbly voice, but each action has a character of its own (ticks, latches, swishes, sparkles)
AV = {
    "move":        lambda: room(place(0.08, (0, tick(2400, 0.04, 0.014)), (0.004, bubble(1300, 1500, 0.04, 0.016) * 0.4)), 0.2, 0.1),
    "select":      lambda: room(place(0.22, (0, bubble(650, 1000, 0.08, 0.05)), (0.05, bubble(1000, 1500, 0.1, 0.07)), (0.05, tick(3200, 0.02, 0.008) * 0.4)), 0.25, 0.14),
    "back":        lambda: room(place(0.25, (0, swish(0.12, 3500, 1500, 0.01, 0.08) * 0.25), (0.03, pop(1100, 600, 0.1))), 0.25, 0.14),
    "open":        lambda: room(place(0.45, (0, swish(0.25, 900, 3500, 0.12, 0.1) * 0.25), *[(0.06 + i * 0.04, bubble(700 + i * 180, 1000 + i * 240, 0.08, 0.05) * (0.6 + 0.12 * i)) for i in range(4)]), 0.35, 0.2),
    "error":       lambda: room(wobble(place(0.34, (0, osc(glide(260, 200, 0.3, 2), 0.3, "tri") * env(0.3, 0.006, 0.22)), (0, spectral(noise(0.3), lo=200, hi=900) * env(0.3, 0.01, 0.15) * 0.15)), 9, 0.3), 0.25, 0.12),
    "toggle":      lambda: room(place(0.16, (0, tick(1800, 0.025, 0.01)), (0.035, tick(2600, 0.025, 0.01) * 0.8), (0.05, bubble(900, 1300, 0.08, 0.04) * 0.7)), 0.2, 0.12),
    "step":        lambda: room(tick(3000, 0.025, 0.009) * 0.9, 0.15, 0.08),
    "tab":         lambda: room(place(0.24, (0, swish(0.16, 1500, 4500, 0.03, 0.1) * 0.35), (0.07, bubble(600, 900, 0.09, 0.05) * 0.8)), 0.3, 0.16),
    "complete":    lambda: room(place(0.75, *[(i * 0.065, bubble(f, f * 1.3, 0.1, 0.08) * 0.8) for i, f in enumerate((660, 880, 1100, 1320))], *[(0.28 + i * 0.05, bell(f, 0.3, decay=0.2) * 0.25) for i, f in enumerate((2640, 3300, 3960))]), 0.45, 0.22),
    "undo":        lambda: room(place(0.45, (0, swish(0.3, 3000, 900, 0.02, 0.22) * 0.2), *[(0.04 + i * 0.045, bubble(f * 1.25, f, 0.08, 0.05)) for i, f in enumerate((1400, 1150, 950, 800))]), 0.35, 0.18),
    "screenshot":  lambda: room(place(0.32, (0, tick(4200, 0.02, 0.006)), (0.03, spectral(noise(0.06), lo=2000, hi=8000) * env(0.06, 0.001, 0.035) * 0.5), (0.065, tick(3000, 0.025, 0.008) * 0.7), (0.1, bubble(1200, 1700, 0.11, 0.08) * 0.8)), 0.3, 0.16),
    "toast_info":  lambda: room(place(0.2, (0, bubble(1050, 1350, 0.1, 0.06) * 0.8), (0.0, tick(5000, 0.015, 0.005) * 0.3)), 0.3, 0.16),
    "toast_warning": lambda: room(wobble(place(0.32, (0, bubble(560, 700, 0.12, 0.07)), (0.13, bubble(520, 640, 0.14, 0.08))), 6, 0.2), 0.3, 0.14),
    "toast_success": lambda: room(place(0.42, (0, bubble(780, 1050, 0.1, 0.07)), (0.08, bubble(1170, 1580, 0.14, 0.1)), (0.17, bell(3120, 0.25, decay=0.18) * 0.18)), 0.35, 0.18),
    "pad_connect": lambda: room(place(0.42, (0, tick(1500, 0.03, 0.012) * 0.7), (0.04, bubble(480, 680, 0.1, 0.07)), (0.13, bubble(720, 1000, 0.14, 0.1))), 0.35, 0.18),
    "pad_disconnect": lambda: room(place(0.45, (0, bubble(1000, 720, 0.1, 0.07)), (0.09, bubble(680, 460, 0.14, 0.1)), (0.2, tick(1200, 0.03, 0.012) * 0.6)), 0.35, 0.18),
    "stream_start": lambda: room(place(0.9, (0, swish(0.65, 500, 4000, 0.45, 0.15) * 0.4), *[(0.15 + i * 0.055, bubble(450 + i * 130, 750 + i * 170, 0.09, 0.06) * 0.55) for i in range(7)]), 0.5, 0.22),
    "stream_end":  lambda: room(place(0.9, (0, swish(0.65, 4000, 400, 0.04, 0.5) * 0.4), *[(i * 0.055, bubble(1150 - i * 110, 850 - i * 90, 0.09, 0.06) * 0.55) for i in range(7)]), 0.5, 0.22),
    "menu_close":  lambda: room(place(0.12, (0, swish(0.07, 3000, 1600, 0.005, 0.05) * 0.2), (0.015, pop(1000, 700, 0.06) * 0.8)), 0.2, 0.1),
}

def shifted(fam, r):
    return {a: (lambda f=f: repitch(f(), r)) for a, f in fam.items()}

FAMILIES = {
    "bubbles_mid": ("A · Bubbles (0.27)", "The chosen original.", A, 0.27, dict()),
    "bubbles_plus": ("A+ · physical bubbles", "The same sounds with physically modelled bubbles (Minnaert / van den Doel) and corks, with about 40 % less room than A; each cue in 4 slightly different takes (pitch ±3 %, length ±6 %). '4×' plays the takes one after another.", A, 0.16, dict(PHYS=True, JITTER=0.03, takes=4)),
    "bubbles_punch": ("A++ · punchier rolls", "A+ with a sharper strike on each bubble (a short knock, a crisper wet transient, a tighter decay) and rolls about a quarter tighter, for Csound's punch in A+'s material. 4 takes per cue.", A, 0.16, dict(PHYS=True, JITTER=0.03, takes=4, PUNCH=1.0, RS=0.75)),
}

ACTIONS = [
    ("move", "Focus moves"), ("select", "Confirm"), ("back", "Back"), ("open", "Open menu / picker"), ("error", "Error"),
    ("toggle", "Switch on/off"), ("step", "Slider step"), ("tab", "Tab change (L1/R1)"), ("complete", "Tutorial lesson done"),
    ("undo", "Undo (profile restored)"), ("screenshot", "Stream screenshot"),
    ("toast_info", "Toast: info"), ("toast_warning", "Toast: warning"), ("toast_success", "Toast: success"),
    ("pad_connect", "Controller connected"), ("pad_disconnect", "Controller disconnected"),
    ("stream_start", "Stream starts"), ("stream_end", "Stream ends"), ("menu_open", "Context menu opens"), ("menu_close", "Context menu closes"),
]

EXTERNAL = [
    ("bubbles_csound", "A · Csound", "Csound 7 physical models: impulses ringing through gliding modal resonators (mode), Perry Cook's PhISEM water drops (dripwater), resonant corks, a reverbsc room."),
    ("bubbles_faust", "A · Faust", "Faust physical-model library: modal and waveguide objects re-voicing the same sounds."),
]

def main():
    import csound_family
    csound_family.OUT = OUT / "bubbles_csound"
    csound_family.main()
    meta = {}
    global ROOM_SCALE, PHYS, JITTER, PUNCH, RS
    for key, (title, desc, fam, amount, opts) in FAMILIES.items():
        ROOM_SCALE = amount
        PHYS = opts.get("PHYS", False)
        JITTER = opts.get("JITTER", 0.0)
        PUNCH = opts.get("PUNCH", 0.0)
        RS = opts.get("RS", 1.0)
        for action, _ in ACTIONS:
            for take in range(opts.get("takes", 1)):
                st = finish(fam[action]())
                write(OUT / key / (f"{action}.wav" if take == 0 else f"{action}_v{take + 1}.wav"), st)
                if take == 0: meta.setdefault(key, {})[action] = round(len(st) / SR, 3)
    PHYS, JITTER, PUNCH, RS = False, 0.0, 0.0, 1.0
    families = [{"key": k, "title": v[0], "desc": v[1], "takes": v[4].get("takes", 1)} for k, v in FAMILIES.items()]
    # Families rendered by other tools (csound_family.py, faust_family.py), shown when present
    for key, title, desc in EXTERNAL:
        if (OUT / key).is_dir():
            meta[key] = {a: round(wave.open(str(OUT / key / f"{a}.wav")).getnframes() / SR, 3) for a, _ in ACTIONS}
            takes = 1 + len(list((OUT / key).glob(f"{ACTIONS[0][0]}_v*.wav")))
            families.append({"key": key, "title": title, "desc": desc + (f" Each cue in {takes} takes ('4×')." if takes > 1 else ""), "takes": takes})
    (OUT / "index.html").write_text(PAGE.replace("__DATA__", json.dumps({
        "families": families, "actions": ACTIONS, "lengths": meta, "stamp": int(__import__("time").time())})))
    # Install the chosen set: every take of every cue
    for old in RAW.glob("ui_*.wav"):
        old.unlink()
    for src in sorted((OUT / CHOSEN).glob("*.wav")):
        shutil.copyfile(src, RAW / f"ui_{src.name}")
    print(f"ok: {len(list(RAW.glob('ui_*.wav')))} files from {CHOSEN} in {RAW}")

PAGE = r"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DELOS UI sounds · gallery</title><style>
*{box-sizing:border-box}body{margin:0;padding:16px;background:#080d15;color:#eef3fb;font:16px system-ui,sans-serif}
h1{font-size:20px;margin:0 0 12px}
.tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}
button{background:#161c30;color:#eef3fb;border:1px solid #ffffff26;border-radius:14px;padding:12px 14px;font:600 15px system-ui;cursor:pointer}
.tabs button.on{background:hsl(40 95% 62%);color:#121929;border-color:transparent}
.desc{color:#9aa8bd;font-size:14px;margin:0 0 12px;min-height:2.6em}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:8px}
.cell{display:flex;gap:6px}.cell button{flex:1;text-align:left}.cell .x{flex:0 0 56px;text-align:center}
.seq{width:100%;margin-bottom:12px}
</style></head><body>
<h1>DELOS UI sounds</h1>
<div class="tabs" id="tabs"></div>
<p class="desc" id="desc"></p>
<button class="seq" id="seq">▶ Sequence: browse a menu (moves, confirm, tab, open, steps, back)</button>
<div class="grid" id="grid"></div>
<script>
const D=__DATA__;const cache={};let cur=D.families[0];
function play(f,a){const k=f+'/'+a;let el=cache[k];if(!el){el=cache[k]=new Audio(k+'.wav?'+D.stamp)}el.currentTime=0;el.play()}
function show(f){cur=f;document.querySelectorAll('#tabs button').forEach(b=>b.classList.toggle('on',b.dataset.k===f.key));
 document.getElementById('desc').textContent=f.desc;const g=document.getElementById('grid');g.innerHTML='';
 for(const [a,label] of D.actions){const c=document.createElement('div');c.className='cell';const b=document.createElement('button');b.textContent='▶ '+label;b.onclick=()=>play(f.key,a);c.append(b);
  if(f.takes>1){const m=document.createElement('button');m.className='x';m.textContent='4×';m.onclick=()=>{for(let i=0;i<f.takes;i++)setTimeout(()=>play(f.key,i?a+'_v'+(i+1):a),i*260)};c.append(m)}g.append(c)}}
for(const f of D.families){const b=document.createElement('button');b.textContent=f.title;b.dataset.k=f.key;b.onclick=()=>show(f);document.getElementById('tabs').append(b)}
document.getElementById('seq').onclick=()=>{const s=['move','move','move','select','move','tab','move','move','open','step','step','step','back'];s.forEach((a,i)=>setTimeout(()=>play(cur.key,a),i*170))};
show(cur);
</script></body></html>"""

if __name__ == "__main__":
    main()
