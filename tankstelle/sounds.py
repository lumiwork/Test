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


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "sounds")
    out.mkdir(parents=True, exist_ok=True)
    for name, fn in [("rain", rain), ("hum", hum), ("tick", tick), ("knock", knock), ("flicker", flicker), ("static", static),
                     ("hit", hit), ("buzz", buzz), ("heart", heart), ("whoosh", whoosh), ("drone", drone)]:
        save(out / f"{name}.wav", fn())
        print("✓", name)
