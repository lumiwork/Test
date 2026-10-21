"""Erzeugt die leichten Soundeffekte für das Video (keine Downloads, keine Credits).

Aufruf: python3 sounds.py <zielordner>
"""
import sys
import wave
from pathlib import Path

import numpy as np

SR = 48000
rng = np.random.default_rng(47)


def t(sec):
    return np.arange(int(sec * SR)) / SR


def band(x, lo=None, hi=None):
    """Bandpass über FFT (für kurze, statische Sounds völlig ausreichend)."""
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X = np.fft.rfft(x)
    m = np.ones_like(f)
    if lo:
        m *= 1 / (1 + (lo / np.maximum(f, 1)) ** 4)
    if hi:
        m *= 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * m, len(x))


def env(n, a, d):
    """Anschlag a Sekunden, dann exponentieller Abfall mit Zeitkonstante d."""
    tt = np.arange(n) / SR
    return np.minimum(1, tt / max(a, 1e-4)) * np.exp(-np.maximum(0, tt - a) / d)


def loop_fade(x, sec=0.5):
    """Übergang Ende→Anfang weich machen, damit die Schleife nicht knackt."""
    n = int(sec * SR)
    head, tail = x[:n].copy(), x[-n:].copy()
    w = np.linspace(0, 1, n)
    x = x[:-n].copy()
    x[:n] = head * w + tail * (1 - w)
    return x


def norm(x, peak_db=-6):
    return x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)


def save(path, x):
    y = np.clip(x, -1, 1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((y * 32767).astype(np.int16).tobytes())


def rain():
    n = len(t(12.5))
    x = band(rng.standard_normal(n), 400, 5000) * 0.6
    x += band(rng.standard_normal(n), 80, 400) * 0.25
    for _ in range(900):  # einzelne Tropfen
        p = rng.integers(0, n - 2000)
        d = band(rng.standard_normal(1200), 1500, 7000) * env(1200, 0.0005, 0.004) * rng.uniform(0.2, 1)
        x[p:p + 1200] += d
    return norm(loop_fade(x), -8)


def hum():
    tt = t(6.5)
    x = sum(a * np.sin(2 * np.pi * f * tt) for f, a in [(100, 1), (200, .5), (300, .3), (400, .12), (1200, .03)])
    x *= 1 + 0.08 * np.sin(2 * np.pi * 0.7 * tt)
    x += band(rng.standard_normal(len(tt)), 2000, 6000) * 0.04
    return norm(loop_fade(x), -10)


def tick():
    n = int(0.08 * SR)
    x = band(rng.standard_normal(n), 2500, 7000) * env(n, 0.0005, 0.006)
    x += np.sin(2 * np.pi * 1300 * t(0.08)) * env(n, 0.0005, 0.012) * 0.5
    return norm(x, -4)


def knock():
    n = int(0.35 * SR)
    tt = t(0.35)
    x = np.sin(2 * np.pi * 95 * tt) * env(n, 0.001, 0.05)
    x += band(rng.standard_normal(n), 300, 1100) * env(n, 0.0008, 0.03) * 0.9
    x += band(rng.standard_normal(n), 3000, 9000) * env(n, 0.001, 0.08) * 0.15  # Scheibe klirrt leicht
    return norm(x, -3)


def flicker():
    n = int(1.1 * SR)
    tt = t(1.1)
    gate = np.zeros(n)
    for _ in range(14):
        p = rng.integers(0, n - 4000)
        L = rng.integers(800, 4000)
        gate[p:p + L] = rng.uniform(0.4, 1)
    x = (np.sign(np.sin(2 * np.pi * 100 * tt)) * 0.3 + band(rng.standard_normal(n), 1500, 9000)) * gate
    return norm(band(x, 60, 10000), -6)


def static():
    n = int(0.6 * SR)
    x = band(rng.standard_normal(n), 300, 9000) * np.minimum(1, np.linspace(0, 12, n)) * np.linspace(1, 0.2, n)
    x *= 1 + 0.5 * np.sign(np.sin(2 * np.pi * 60 * t(0.6)))
    return norm(x, -6)


def hit():
    n = int(3.0 * SR)
    tt = t(3.0)
    f = 75 * np.exp(-tt * 0.8) + 32
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.005, 0.9)
    x += band(rng.standard_normal(n), 30, 900) * env(n, 0.002, 0.35) * 0.6
    x += band(rng.standard_normal(n), 2000, 8000) * env(n, 0.001, 0.05) * 0.2
    return norm(x, -2)


def buzz():
    tt = t(1.15)
    x = np.sign(np.sin(2 * np.pi * 165 * tt)) * 0.6 + np.sin(2 * np.pi * 330 * tt) * 0.3
    on = ((tt % 0.62) < 0.42).astype(float)
    on = np.convolve(on, np.ones(240) / 240, "same")
    x = band(x * on * (1 + 0.3 * rng.standard_normal(len(tt))), 100, 2500)
    return norm(x, -9)


def heart():
    n = int(1.0 * SR)
    x = np.zeros(n)
    for start, amp in [(0.0, 1.0), (0.24, 0.7)]:
        p = int(start * SR)
        L = int(0.25 * SR)
        tt = t(0.25)
        x[p:p + L] += np.sin(2 * np.pi * (58 - 18 * tt / 0.25) * tt) * env(L, 0.006, 0.06) * amp
    return norm(band(x, 25, 300), -3)


def whoosh():
    n = int(0.9 * SR)
    noise = rng.standard_normal(n)
    parts = []
    for i, c in enumerate(np.linspace(300, 2500, 9)):
        seg = band(noise, c * 0.7, c * 1.4)
        w = np.exp(-((np.arange(n) / n - i / 8) ** 2) / 0.02)
        parts.append(seg * w)
    x = sum(parts) * np.sin(np.pi * np.arange(n) / n) ** 1.5
    return norm(x, -10)


def drone():
    tt = t(12.5)
    x = np.sin(2 * np.pi * 55 * tt) + np.sin(2 * np.pi * 55.6 * tt) * 0.8 + np.sin(2 * np.pi * 82.5 * tt) * 0.25
    x += band(rng.standard_normal(len(tt)), 60, 400) * 0.3
    x *= 1 + 0.2 * np.sin(2 * np.pi * 0.15 * tt)
    return norm(loop_fade(x), -10)


def ding():
    n = int(2.2 * SR)
    tt = t(2.2)
    x = np.zeros(n)
    for start, f in [(0, 1318.5), (0.35, 1046.5)]:
        p = int(start * SR)
        seg = (np.sin(2 * np.pi * f * tt[:n - p]) + 0.3 * np.sin(2 * np.pi * 2 * f * tt[:n - p])) * env(n - p, 0.003, 0.6)
        x[p:] += seg
    return norm(x, -6)


def door():
    n = int(1.4 * SR)
    tt = t(1.4)
    x = band(rng.standard_normal(n), 120, 1500) * np.sin(np.pi * tt / 1.4) ** 1.2
    x += np.sin(2 * np.pi * 60 * tt) * 0.2 * np.sin(np.pi * tt / 1.4)
    x[-int(0.12 * SR):] += np.sin(2 * np.pi * 80 * tt[:int(0.12 * SR)]) * env(int(0.12 * SR), 0.002, 0.03) * 2
    return norm(x, -6)


def wind():
    tt = t(12.5)
    noise = rng.standard_normal(len(tt))
    x = band(noise, 150, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.13 * tt) * np.sin(2 * np.pi * 0.07 * tt + 1))
    x += band(noise, 900, 2500) * 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.21 * tt))
    return norm(loop_fade(x), -8)


def creak():
    n = int(1.3 * SR)
    tt = t(1.3)
    f = 180 + 70 * np.sin(2 * np.pi * 0.8 * tt) + 25 * rng.standard_normal(n).cumsum() / np.sqrt(n)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.4 * np.sin(3.1 * ph)) * (np.sin(np.pi * tt / 1.3) ** 2)
    x *= 0.6 + 0.4 * (rng.random(n) > 0.4)
    return norm(band(x, 150, 3000), -6)


def fire():
    tt = t(12.5)
    n = len(tt)
    x = band(rng.standard_normal(n), 100, 1200) * 0.3
    for _ in range(500):
        p = rng.integers(0, n - 800)
        x[p:p + 800] += band(rng.standard_normal(800), 1500, 8000) * env(800, 0.0003, 0.003) * rng.uniform(0.2, 1)
    return norm(loop_fade(x), -10)


def whisper():
    n = int(2.6 * SR)
    tt = t(2.6)
    syl = np.clip(np.sin(2 * np.pi * 3.1 * tt) * np.sin(2 * np.pi * 0.7 * tt + 0.5), 0, 1) ** 1.5
    x = band(rng.standard_normal(n), 1200, 5000) * syl + band(rng.standard_normal(n), 400, 1200) * syl * 0.4
    return norm(x * np.sin(np.pi * tt / 2.6), -8)


def drip():
    n = int(0.5 * SR)
    tt = t(0.5)
    f = 900 + 1400 * np.exp(-tt * 40)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.001, 0.05)
    return norm(x, -8)


def train():
    tt = t(12.5)
    n = len(tt)
    x = band(rng.standard_normal(n), 30, 300) * 0.7 + np.sin(2 * np.pi * 45 * tt) * 0.15
    for k in np.arange(0, 12.5, 0.85):  # Schienenstöße
        for dk in (0, 0.13):
            p = int((k + dk) * SR)
            L = int(0.12 * SR)
            if p + L < n:
                x[p:p + L] += band(rng.standard_normal(L), 80, 900) * env(L, 0.001, 0.025) * 1.4
    return norm(loop_fade(x), -8)


def glass():
    n = int(0.5 * SR)
    x = band(rng.standard_normal(n), 2500, 9000) * env(n, 0.0005, 0.02)
    for f in (2900, 4100, 5600):
        x += np.sin(2 * np.pi * f * t(0.5)) * env(n, 0.0005, 0.12) * 0.25
    return norm(x, -5)


def speaker():
    n = int(0.9 * SR)
    x = band(rng.standard_normal(n), 800, 4000) * (rng.random(n) > 0.3) * np.minimum(1, np.linspace(0, 10, n)) * np.linspace(1, 0, n) ** 0.5
    return norm(x, -9)



def pop():
    """Kurzes, freundliches Plopp (Icons und Sprechblasen erscheinen)."""
    tt = t(0.18)
    f = 900 * np.exp(-tt * 18) + 300
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(tt), 0.002, 0.05)
    return norm(x, -8)


def tap():
    """Leises Tippen auf dem Handy-Display."""
    n = int(0.06 * SR)
    x = band(rng.standard_normal(n), 1500, 7000) * env(n, 0.0005, 0.008)
    return norm(x, -12)


def click():
    """Schloss rastet ein: zwei kurze metallische Klicks."""
    n = int(0.35 * SR)
    x = np.zeros(n)
    for start, g in ((0, 1.0), (int(0.09 * SR), 0.7)):
        m = int(0.05 * SR)
        x[start:start + m] += g * band(rng.standard_normal(m), 2000, 9000) * env(m, 0.0003, 0.006)
        x[start:start + m] += g * 0.5 * np.sin(2 * np.pi * 3200 * t(0.05)) * env(m, 0.0003, 0.01)
    return norm(x, -7)


def scribble():
    """Stift kritzelt übers Papier."""
    n = int(0.5 * SR)
    am = 0.5 + 0.5 * np.sin(2 * np.pi * 11 * t(0.5)) ** 2
    x = band(rng.standard_normal(n), 2500, 8000) * am * env(n, 0.02, 0.3)
    return norm(x, -14)


def bell(tt, partials, d):
    """Metallischer Glockenton aus unharmonischen Teiltönen (f, Lautstärke, Abklingfaktor)."""
    return sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / (d * k)) for f, a, k in partials)


def coin():
    """Münze klimpert: kurzer, heller Doppel-Ton."""
    tt = t(0.25)
    x = bell(tt, [(3950, 1, 1), (6100, .6, .6), (8300, .25, .4)], 0.06)
    x[int(0.045 * SR):] += 0.6 * bell(tt[:len(tt) - int(0.045 * SR)], [(4400, 1, 1), (6800, .5, .6)], 0.05)
    return norm(x * env(len(tt), 0.0005, 1e9), -9)


def coinroll():
    """Zähler rattert hoch: Münzklicks, immer schneller und etwas höher (0,75 s)."""
    n = int(0.75 * SR)
    x = np.zeros(n)
    for i in range(12):
        p = int(0.7 * (1 - (1 - i / 12) ** 1.6) * SR)
        tt = t(0.08)
        f = 3600 + i * 90
        hit = bell(tt, [(f, 1, 1), (f * 1.55, .5, .6)], 0.025)
        m = min(len(hit), n - p)
        x[p:p + m] += hit[:m] * (0.6 + i * 0.03)
    return norm(x, -12)


def kaching():
    """Registrierkasse: Schublade ratscht auf, dann klingelt die Glocke."""
    n = int(1.4 * SR)
    x = np.zeros(n)
    for i in range(5):  # Ratsche
        p, m = int(i * 0.022 * SR), int(0.02 * SR)
        x[p:p + m] += band(rng.standard_normal(m), 1500, 8000) * env(m, 0.0005, 0.004) * (0.6 + 0.1 * i)
    p = int(0.12 * SR)
    tt = t(1.4)[:n - p]
    x[p:] += bell(tt, [(2093, 1, 1), (2637, .8, .9), (4186, .4, .6), (5274, .25, .4)], 0.35)
    x[p:] += 0.4 * band(rng.standard_normal(len(tt)), 3000, 9000) * env(len(tt), 0.001, 0.03)
    return norm(x, -6)


def beat():
    """Lockerer Beat (128 BPM, 4 Takte) als Schleife unter Videos ohne Stimme."""
    bpm, bars = 128, 4
    st = 60 / bpm / 4  # Sechzehntel
    n = int(round(st * 16 * bars * SR))
    x = np.zeros(n)

    def put(sig, step):
        p = int(round(step * st * SR))
        m = min(len(sig), n - p)
        x[p:p + m] += sig[:m]

    tk = t(0.35)
    kick = np.sin(2 * np.pi * np.cumsum(45 + 110 * np.exp(-tk / 0.04)) / SR) * env(len(tk), 0.002, 0.12)
    m = int(0.18 * SR)
    clap = band(rng.standard_normal(m), 900, 6000) * env(m, 0.001, 0.05)
    m = int(0.05 * SR)
    hat = band(rng.standard_normal(m), 7000, None) * env(m, 0.0005, 0.012)
    bass_notes = [43.65, 43.65, 51.91, 38.89]  # F1, F1, Ab1, Eb1
    for b in range(bars):
        o = b * 16
        for s in (0, 7, 10):
            put(kick, o + s)
        for s in (4, 12):
            put(0.55 * clap, o + s)
        for s in range(0, 16, 2):
            put((0.22 if s % 4 else 0.12) * hat, o + s + 1)
        tb = t(st * 3)
        f = bass_notes[b]
        bass = (np.sin(2 * np.pi * f * tb) + 0.3 * np.sin(4 * np.pi * f * tb)) * env(len(tb), 0.005, 0.25)
        for s in (0, 3, 6, 8, 11, 14):
            put(0.5 * bass, o + s)
    return norm(x, -7)

if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "sounds")
    out.mkdir(parents=True, exist_ok=True)
    for name, fn in [("rain", rain), ("hum", hum), ("tick", tick), ("knock", knock), ("flicker", flicker), ("static", static),
                     ("hit", hit), ("buzz", buzz), ("heart", heart), ("whoosh", whoosh), ("drone", drone), ("ding", ding),
                     ("door", door), ("wind", wind), ("creak", creak), ("fire", fire), ("whisper", whisper), ("drip", drip),
                     ("train", train), ("glass", glass), ("speaker", speaker),
                     ("pop", pop), ("tap", tap), ("click", click), ("scribble", scribble),
                     ("coin", coin), ("kaching", kaching), ("beat", beat), ("coinroll", coinroll)]:
        save(out / f"{name}.wav", fn())
        print("✓", name)
