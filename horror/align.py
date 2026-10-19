"""Ordnet die Wort-Zeitstempel einer Transkription den Untertiteln aus index.html zu.

Aufruf: [SEITE=pfad/index.html] [TEMPO=1.1] python3 align.py transkript.json|aufnahme.mp3 voice.json [anzahl_saetze]
transkript.json: Liste von Wörtern mit text/start/end (Sekunden) – z. B. aus ElevenLabs Scribe.
Ergebnis voice.json: pro Untertitel-Zeile Start (s), Ende (e) und die Startzeit jedes Wortes relativ zu s (w).
"""
import difflib, json, os, re, sys
from pathlib import Path

ZAHLEN = {"vierzig": "40", "drei": "3", "vier": "4", "zwölf": "12", "dreizehn": "13", "vierzehn": "14", "siebzehn": "17",
          "zweiundzwanzig": "22", "dreiundzwanzig": "23", "fünfundzwanzig": "25", "dreißig": "30", "zweitausendneunzehn": "2019"}


def norm(w):
    w = re.sub(r"[^\wäöüß]", "", w.lower())
    return ZAHLEN.get(w, w)


def captions(n):
    html = Path(os.environ.get("SEITE", Path(__file__).parent / "index.html")).read_text(encoding="utf-8")
    lines = re.findall(r"\bcap(?:At|Words)?\('([^']*)'", html)[:n]
    return [[re.sub(r"[*#~]", "", w) for w in l.split()] for l in lines]


def load_words(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(data, dict):
        data = data.get("words") or data["transcripts"][0]["words"]
    out = []
    for w in data:
        if w.get("type", "word") != "word" or (w.get("text") or "").startswith("["):
            continue  # Regie-Tags wie [pause] gehören nicht zum gesprochenen Text
        text = w.get("text") or w.get("word") or ""
        if norm(text):
            out.append((norm(text), float(w["start"]), float(w["end"])))
    return out


def from_pauses(audio, lines):
    """Ohne Transkript: Satzgrenzen = die längsten Pausen, Wörter nach Buchstaben verteilt."""
    import subprocess
    import numpy as np
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", audio, "-ac", "1", "-ar", "8000", "-af", "highpass=f=120",
                          "-f", "s16le", "-"], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.int16).astype(float) / 32768
    hop = 400  # 50 ms
    db = np.array([20 * np.log10(np.sqrt(np.mean(x[i:i + hop] ** 2)) + 1e-9) for i in range(0, len(x) - hop, hop)])
    speech = db > np.percentile(db, 95) - 24
    runs, i = [], 0
    while i < len(speech):
        if speech[i]:
            j = i
            while j < len(speech) and speech[j]:
                j += 1
            if runs and (i - runs[-1][1]) * 0.05 < 0.12:
                runs[-1][1] = j
            else:
                runs.append([i, j])
            i = j
        else:
            i += 1
    runs = [(a * 0.05, b * 0.05) for a, b in runs if (b - a) * 0.05 >= 0.1]
    # Satzgrenzen per DP: Sprechdauer passt zur Buchstabenzahl, Grenzen liegen bevorzugt in langen Pausen
    chars = [len(re.sub(r"[^\wäöüß]", "", "".join(l))) for l in lines]
    rate = sum(b - a for a, b in runs) / sum(chars)
    m, n = len(runs), len(lines)
    gap = [runs[k + 1][0] - runs[k][1] for k in range(m - 1)] + [0]
    INF = float("inf")
    best = [[INF] * (m + 1) for _ in range(n + 1)]
    back = [[0] * (m + 1) for _ in range(n + 1)]
    best[0][0] = 0
    for i in range(1, n + 1):
        exp = chars[i - 1] * rate
        for j in range(i, m + 1):
            for k in range(i - 1, j):
                if best[i - 1][k] == INF:
                    continue
                dur = sum(b - a for a, b in runs[k:j])
                cost = best[i - 1][k] + ((dur - exp) / exp) ** 2 - (1.5 * gap[j - 1] if j < m else 0)
                if cost < best[i][j]:
                    best[i][j], back[i][j] = cost, k
    groups, j = [], m
    for i in range(n, 0, -1):
        k = back[i][j]
        groups.insert(0, runs[k:j])
        j = k
    out = []
    for l, g in zip(lines, groups):
        total = sum(b - a for a, b in g)
        weights = [len(re.sub(r"[^\wäöüß]", "", w)) + 1 for w in l]
        pos, w_starts = 0.0, []
        for wt in weights:  # Anteil an der reinen Sprechzeit -> echte Zeit (Pausen überspringen)
            t, acc = g[0][0], 0.0
            for a, b in g:
                if acc + (b - a) >= pos * total:
                    t = a + (pos * total - acc)
                    break
                acc += b - a
            w_starts.append(t)
            pos += wt / sum(weights)
        s = g[0][0]
        out.append({"text": " ".join(l), "s": round(s, 3), "e": round(g[-1][1], 3), "w": [round(t - s, 3) for t in w_starts]})
    return out


def main():
    lines = captions(int(sys.argv[3]) if len(sys.argv) > 3 else 16)
    if sys.argv[1].endswith((".mp3", ".wav", ".m4a")):
        out = from_pauses(sys.argv[1], lines)
        Path(sys.argv[2]).write_text(json.dumps({"lines": out}, ensure_ascii=False, indent=1), encoding="utf-8")
        for l in out:
            print(f"{l['s']:7.2f} – {l['e']:7.2f}  {l['text']}")
        return
    words = load_words(sys.argv[1])
    flat = [norm(w) for l in lines for w in l]
    # Zahlen-Untertitel wie "40" auch dann treffen, wenn das Transkript "vierzig" schreibt
    sm = difflib.SequenceMatcher(None, flat, [w[0] for w in words], autojunk=False)
    start, end = [None] * len(flat), [None] * len(flat)
    for a, b, size in sm.get_matching_blocks():
        for k in range(size):
            start[a + k], end[a + k] = words[b + k][1], words[b + k][2]
    hits = sum(x is not None for x in start)
    # Lücken linear auffüllen
    known = [i for i, x in enumerate(start) if x is not None]
    for i in range(len(flat)):
        if start[i] is None:
            lo = max([k for k in known if k < i], default=None)
            hi = min([k for k in known if k > i], default=None)
            if lo is None:
                start[i] = end[i] = start[hi]
            elif hi is None:
                start[i] = end[i] = end[lo]
            else:
                f = (i - lo) / (hi - lo)
                start[i] = end[lo] + f * (start[hi] - end[lo])
                end[i] = start[i] + 0.25
    out, i = [], 0
    for l in lines:
        st, en = start[i:i + len(l)], end[i:i + len(l)]
        s = st[0]
        out.append({"text": " ".join(l), "s": round(s, 3), "e": round(en[-1], 3), "w": [round(x - s, 3) for x in st]})
        i += len(l)
    tempo = float(os.environ.get("TEMPO", "1"))  # Stimme mit atempo beschleunigt → Zeiten mitskalieren
    for l in out:
        l["s"], l["e"], l["w"] = round(l["s"] / tempo, 3), round(l["e"] / tempo, 3), [round(x / tempo, 3) for x in l["w"]]
    Path(sys.argv[2]).write_text(json.dumps({"lines": out}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{hits}/{len(flat)} Wörter direkt zugeordnet")
    for l in out:
        print(f"{l['s']:7.2f} – {l['e']:7.2f}  {l['text']}")


if __name__ == "__main__":
    main()
