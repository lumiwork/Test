"""Erzeugt den ElevenLabs-Text direkt aus den Untertiteln einer Seite (ohne Farb-Marker).

Aufruf: python3 stimme_text.py ../etage13/index.html > ../etage13/elevenlabs.txt
Stimme ab Fall #017 Auflösung: „Markus – German young natural voice“ (IeQubAjK1ujbppIdhJw4), eleven_v4.
Markus spricht flott genug, align.py braucht daher kein TEMPO.
"""
import re
import sys
from pathlib import Path

html = Path(sys.argv[1]).read_text(encoding="utf-8")
lines = [re.sub(r"[*#~]", "", l) for l in re.findall(r"\bcap(?:At|Words)?\('([^']*)'", html)]
print(" [pause]\n".join(lines))
