#!/usr/bin/env python3
"""A · Csound: the chosen Bubbles set re-voiced with Csound 7's physical models.

Each drop is an impulse ringing through modal resonators (`mode`) whose frequency glides like a shrinking bubble, a
second inharmonic mode for the cavity, and Perry Cook's PhISEM water model (`dripwater`) as a wet texture; pops are
resonators falling in pitch with a burst of air; a light `reverbsc` room. Rendered offline by the csound binary,
then loudness-matched by make_ui_sounds.finish(). Run through make_ui_sounds.py, which installs the result.
"""
import subprocess, tempfile, wave
from pathlib import Path
import numpy as np
import make_ui_sounds as make

OUT = make.OUT / "bubbles_csound"

ORC = r"""
sr = 48000
ksmps = 16
nchnls = 2
0dbfs = 1
seed 7
gaRevL init 0
gaRevR init 0

; a bubble: an impulse through two modal resonators whose pitch rises from p4 to p5 (Hz) over p3, Q p6, level p7
instr 1
  kf expseg p4, p3 * 0.6, p5, p3 * 0.4, p5 * 1.02
  ain mpulse 1, 0
  a1 mode ain, kf, p6
  a2 mode ain, kf * 2.31, p6 * 0.45
  awet dripwater 0.02 * p7, 0.01, 1, 0.9, 0, p4 * 1.5, p4 * 2.2, p4 * 3.1
  aenv linseg 1, p3 * 0.85, 1, p3 * 0.15, 0
  aout = (a1 * 0.02 + a2 * 0.004 + awet * 0.3) * p7 * aenv
  outs aout, aout
  gaRevL += aout * 0.12
  gaRevR += aout * 0.12
endin

; a cork pop: resonators falling from p4 to p5, a burst of air
instr 2
  kf expseg p4, p3, p5
  ain mpulse 1, 0
  a1 mode ain, kf, 18
  a2 mode ain, kf * 1.71, 10
  anz noise 0.25, 0
  anz butterbp anz, 2000, 1800
  anz *= expseg(1, 0.012, 0.001, p3, 0.001)
  aenv linseg 1, p3 * 0.8, 1, p3 * 0.2, 0
  aout = (a1 * 0.03 + a2 * 0.01 + anz * 0.5) * p6 * aenv
  outs aout, aout
  gaRevL += aout * 0.1
  gaRevR += aout * 0.1
endin

; a soft click for the context menu: the cork's resonators with less ring and only a trace of air (closer to A+)
instr 5
  kf expseg p4, p3, p5
  ain mpulse 1, 0
  a1 mode ain, kf, 9
  a2 mode ain, kf * 1.71, 5
  anz noise 0.25, 0
  anz butterbp anz, 1600, 1200
  anz *= expseg(1, 0.006, 0.001, p3, 0.001)
  aenv linseg 1, p3 * 0.7, 1, p3 * 0.3, 0
  aout = (a1 * 0.03 + a2 * 0.008 + anz * 0.12) * p6 * aenv
  outs aout, aout
  gaRevL += aout * 0.06
  gaRevR += aout * 0.06
endin

; a breath of water: filtered noise swelling (p4 = 1) or fading (p4 = 0), band p5..p6 Hz, level p7
instr 3
  anz noise 1, 0
  anz butterbp anz, (p5 + p6) / 2, (p6 - p5)
  kenv = p4 == 1 ? expseg(0.001, p3 * 0.75, 1, p3 * 0.25, 0.001) : expseg(1, p3, 0.001)
  aout = anz * kenv * p7 * 0.15
  outs aout, aout
  gaRevL += aout * 0.15
  gaRevR += aout * 0.15
endin

; a low, unsure tone for errors: a triangle gliding down, p4 -> p5, level p6
instr 4
  kf expseg p4, p3, p5
  a1 vco2 0.3, kf, 12
  a1 butterlp a1, 1400
  aenv expseg 0.001, 0.004, 1, p3 - 0.004, 0.001
  aout = a1 * aenv * p6
  outs aout, aout
  gaRevL += aout * 0.08
  gaRevR += aout * 0.08
endin

instr 99
  aL, aR reverbsc gaRevL, gaRevR, 0.45, 7000
  outs aL * 0.3, aR * 0.3
  clear gaRevL, gaRevR
endin
"""

# (start, instr, duration, params...) per action: the Bubbles set's timing and pitches
def drop(t, f0, f1, d=0.1, q=40, lvl=1.0): return f"i1 {t} {d} {f0} {f1} {q} {lvl}"
def pop(t, f0, f1, d=0.07, lvl=1.0): return f"i2 {t} {d} {f0} {f1} {lvl}"
def breath(t, d, up, lo, hi, lvl=1.0): return f"i3 {t} {d} {1 if up else 0} {lo} {hi} {lvl}"
def click(t, f0, f1, d=0.04, lvl=1.0): return f"i5 {t} {d} {f0} {f1} {lvl}"

SCORES = {
    "move": [drop(0, 900, 1250, 0.07, 30)],
    "select": [drop(0, 700, 1100), drop(0.055, 1050, 1500, 0.09)],
    # the mirror of Confirm: two drops falling instead of rising (the cork pops sounded like an old browser click)
    "back": [drop(0, 1500, 1050, 0.09, 40), drop(0.055, 1100, 700, 0.1, 40)],
    "open": [drop(i * 0.045, 600 + i * 160, 900 + i * 220, 0.09, 40, 0.7 + 0.1 * i) for i in range(4)],
    "error": ["i4 0 0.13 320 260 1", "i4 0.13 0.15 300 230 1"],
    "toggle": [pop(0, 1400, 900, 0.05), drop(0.04, 800, 1300, 0.08, 35)],
    "step": [drop(0, 1500, 1700, 0.045, 22, 0.8)],
    "tab": [drop(0, 500, 820, 0.1, 40), drop(0.05, 700, 1050, 0.09, 40, 0.7)],
    "complete": [drop(i * 0.07, f, f * 1.35, 0.14, 55) for i, f in enumerate((700, 880, 1050, 1400))],
    "undo": [drop(i * 0.05, f * 1.3, f, 0.1, 45) for i, f in enumerate((1300, 1050, 880))],
    "screenshot": [pop(0, 2200, 1400, 0.03, 0.8), pop(0.035, 1800, 1200, 0.03, 0.6), drop(0.07, 1100, 1600, 0.11, 45)],
    "toast_info": [drop(0, 1000, 1300, 0.11, 45, 0.8)],
    "toast_warning": [drop(0, 600, 760, 0.11, 40), drop(0.11, 600, 760, 0.11, 40)],
    "toast_success": [drop(0, 800, 1100, 0.11, 45), drop(0.08, 1200, 1650, 0.15, 55)],
    "pad_connect": [drop(0, 500, 700, 0.11, 45), drop(0.09, 750, 1050, 0.15, 55)],
    "pad_disconnect": [drop(0, 1050, 750, 0.11, 45), drop(0.09, 700, 480, 0.15, 55)],
    "stream_start": [drop(i * 0.06, 400 + i * 120, 700 + i * 160, 0.1, 40, 0.45 + 0.05 * i) for i in range(7)],
    "stream_end": [drop(i * 0.06, 1100 - i * 110, 800 - i * 90, 0.1, 40, 0.75 - 0.05 * i) for i in range(7)],
    "menu_open": [click(0, 950, 760, 0.04, 0.8), click(0.045, 1350, 1080, 0.04)],
    "menu_close": [click(0, 1350, 1080, 0.04), click(0.045, 950, 760, 0.04, 0.8)],
}

TAKES = 4

def vary(events, rng, spread=0.03):
    """One take: each bubble, pop or tone a little higher or lower (±3 %) and longer or shorter (±6 %)."""
    out = []
    for e in events:
        p = e.split()
        if p[0] in ("i1", "i2", "i4", "i5"):
            k = 1 + (rng.random() * 2 - 1) * spread
            p[2] = f"{float(p[2]) * (1 + (rng.random() * 2 - 1) * spread * 2):.4f}"
            p[3] = f"{float(p[3]) * k:.2f}"; p[4] = f"{float(p[4]) * k:.2f}"
        out.append(" ".join(p))
    return out

def render(action, events, take=0):
    end = max(float(e.split()[1]) + float(e.split()[2]) for e in events) + 0.6
    orc = ORC.replace("seed 7", f"seed {7 + take}")
    csd = "<CsoundSynthesizer>\n<CsOptions>\n-d -m0 --nodisplays\n</CsOptions>\n<CsInstruments>\n" + orc \
          + "\n</CsInstruments>\n<CsScore>\n" + "\n".join(events) + f"\ni99 0 {end}\ne\n</CsScore>\n</CsoundSynthesizer>\n"
    with tempfile.TemporaryDirectory() as d:
        src = Path(d) / "s.csd"; wav = Path(d) / "s.wav"
        src.write_text(csd)
        subprocess.run(["csound", "-W", "-3", "-o", str(wav), str(src)], check=True, capture_output=True)
        with wave.open(str(wav)) as w:
            raw = w.readframes(w.getnframes())
        b = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        st = (np.where(v >= 1 << 23, v - (1 << 24), v) / float(1 << 23)).reshape(-1, 2)
    # trim the silent tail and fade the last 5 ms
    a = np.abs(st).max(axis=1); thr = a.max() * 10 ** (-70 / 20); last = np.nonzero(a > thr)[0]
    st = st[: last[-1] + 1]; f = min(len(st), 240); st[-f:] *= np.linspace(1, 0, f)[:, None]
    return st

def main():
    rng = np.random.default_rng(23)
    for action, events in SCORES.items():
        make.write(OUT / f"{action}.wav", make.finish(render(action, events)))
        for take in range(1, TAKES):
            make.write(OUT / f"{action}_v{take + 1}.wav", make.finish(render(action, vary(events, rng), take)))
    print("ok")

if __name__ == "__main__":
    main()
