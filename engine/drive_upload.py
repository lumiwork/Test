"""Lädt fertige Videos in einen Google-Drive-Ordner hoch (Berechtigung drive.file: nur selbst angelegte Dateien).

Zugangsdaten: Umgebungsvariablen GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET, GDRIVE_REFRESH_TOKEN
oder GDRIVE_DIR=<ordner> mit client.json + token.json (niemals ins Repository legen).

Aufruf: python3 drive_upload.py [--public] [--ordner "TikTok Uploads"] video1.mp4 video2.mp4 ...
Ausgabe pro Datei: Name, Drive-ID und Link (mit --public: „jeder mit dem Link darf ansehen“).
"""
import json, os, sys, urllib.parse, urllib.request
from pathlib import Path

API = "https://www.googleapis.com"


def creds():
    if os.environ.get("GDRIVE_REFRESH_TOKEN"):
        return os.environ["GDRIVE_CLIENT_ID"], os.environ["GDRIVE_CLIENT_SECRET"], os.environ["GDRIVE_REFRESH_TOKEN"]
    d = Path(os.environ["GDRIVE_DIR"])
    c, t = json.loads((d / "client.json").read_text()), json.loads((d / "token.json").read_text())
    return c["client_id"], c["client_secret"], t["refresh_token"]


def access_token():
    cid, secret, refresh = creds()
    data = urllib.parse.urlencode({"client_id": cid, "client_secret": secret, "refresh_token": refresh, "grant_type": "refresh_token"}).encode()
    return json.load(urllib.request.urlopen(urllib.request.Request("https://oauth2.googleapis.com/token", data=data), timeout=30))["access_token"]


def call(tok, method, url, body=None, headers=None):
    h = {"Authorization": f"Bearer {tok}", **(headers or {})}
    if isinstance(body, (dict, list)):
        body, h["Content-Type"] = json.dumps(body).encode(), "application/json; charset=UTF-8"
    return urllib.request.urlopen(urllib.request.Request(url, data=body, headers=h, method=method), timeout=600)


def folder(tok, name):
    q = urllib.parse.quote(f"name = '{name}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
    found = json.load(call(tok, "GET", f"{API}/drive/v3/files?q={q}&fields=files(id)"))["files"]
    if found:
        return found[0]["id"]
    return json.load(call(tok, "POST", f"{API}/drive/v3/files?fields=id", {"name": name, "mimeType": "application/vnd.google-apps.folder"}))["id"]


def upload(tok, path, parent, public):
    size = path.stat().st_size
    # Fortsetzbarer Upload: erst Sitzung anlegen, dann die Datei in 16-MB-Stücken senden
    r = call(tok, "POST", f"{API}/upload/drive/v3/files?uploadType=resumable&fields=id,name,webViewLink",
             {"name": path.name, "parents": [parent]}, {"X-Upload-Content-Type": "video/mp4", "X-Upload-Content-Length": str(size)})
    session, chunk, pos = r.headers["Location"], 16 * 1024 * 1024, 0
    with path.open("rb") as f:
        while pos < size:
            part = f.read(chunk)
            end = pos + len(part) - 1
            req = urllib.request.Request(session, data=part, method="PUT", headers={"Content-Range": f"bytes {pos}-{end}/{size}"})
            try:
                res = json.load(urllib.request.urlopen(req, timeout=600))
            except urllib.error.HTTPError as e:
                if e.code != 308:  # 308 = Stück angekommen, weiter
                    raise
                res = None
            pos = end + 1
    if public:
        call(tok, "POST", f"{API}/drive/v3/files/{res['id']}/permissions", {"role": "reader", "type": "anyone"})
    return res


def main():
    args, public, name = sys.argv[1:], False, "TikTok Uploads"
    if "--public" in args:
        args.remove("--public"); public = True
    if "--ordner" in args:
        i = args.index("--ordner"); name = args[i + 1]; del args[i:i + 2]
    tok = access_token()
    parent = folder(tok, name)
    for a in args:
        res = upload(tok, Path(a), parent, public)
        print(f"{res['name']}\t{res['id']}\t{res['webViewLink']}", flush=True)


if __name__ == "__main__":
    main()
