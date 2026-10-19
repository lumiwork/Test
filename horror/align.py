"""Ordnet die Wort-Zeitstempel einer Transkription den Untertiteln aus index.html zu.

Aufruf: python3 align.py transkript.json voice.json [anzahl_saetze]
transkript.json: Liste von Wörtern mit text/start/end (Sekunden) – z. B. aus ElevenLabs Scribe.
Ergebnis voice.json: pro Untertitel-Zeile Start (s), Ende (e) und die Startzeit jedes Wortes relativ zu s (w).
"""
import difflib, json, re, sys
from pathlib import Path

ZAHLEN = {"vierzig": "40", "drei": "3"}


def norm(w):
    w = re.sub(r"[^\wäöüß]", "", w.lower())
    return ZAHLEN.get(w, w)


def captions(n):
    html = (Path(__file__).parent / "index.html").read_text(encoding="utf-8")
    lines = re.findall(r"\bcap\('([^']*)'", html)[:n]
    return [[re.sub(r"[*#~]", "", w) for w in l.split()] for l in lines]


def load_words(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(data, dict):
        data = data.get("words") or data["transcripts"][0]["words"]
    out = []
    for w in data:
        if w.get("type", "word") != "word":
            continue
        text = w.get("text") or w.get("word") or ""
        if norm(text):
            out.append((norm(text), float(w["start"]), float(w["end"])))
    return out


def main():
    words = load_words(sys.argv[1])
    lines = captions(int(sys.argv[3]) if len(sys.argv) > 3 else 16)
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
    Path(sys.argv[2]).write_text(json.dumps({"lines": out}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{hits}/{len(flat)} Wörter direkt zugeordnet")
    for l in out:
        print(f"{l['s']:7.2f} – {l['e']:7.2f}  {l['text']}")


if __name__ == "__main__":
    main()
