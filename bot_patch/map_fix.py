# Drop-in replacement for bot.py:
#   1) replace the whole _try_send_image(...) function with the one below
#   2) replace the whole _generate_map_image(...) function with the one below
# (needs: import io, os  — already imported in bot.py)

# rough continent outlines in 0..1 map coordinates (x, y)
_CONTINENTS = [
    [(.05,.14),(.16,.08),(.30,.08),(.34,.16),(.30,.24),(.24,.38),(.22,.46),(.28,.52),(.24,.40),(.12,.34),(.06,.22)],  # N. America
    [(.24,.52),(.34,.52),(.38,.62),(.34,.80),(.28,.90),(.25,.78),(.24,.62)],                                          # S. America
    [(.42,.18),(.50,.10),(.60,.12),(.62,.22),(.58,.34),(.52,.38),(.44,.38),(.42,.28)],                                # Europe
    [(.42,.42),(.54,.40),(.62,.46),(.62,.58),(.58,.70),(.56,.84),(.50,.82),(.47,.68),(.43,.54)],                      # Africa
    [(.58,.12),(.72,.08),(.90,.14),(.94,.30),(.88,.44),(.80,.56),(.72,.54),(.66,.46),(.60,.40),(.58,.26)],            # Asia
    [(.78,.66),(.90,.66),(.92,.78),(.84,.82),(.78,.76)],                                                              # Australia
]

def _generate_map_image() -> bytes:
    if not _PIL_OK:
        print("[!] Pillow not installed — cannot render map", flush=True)
        return None
    try:
        W, H = 1400, 700
        MW = 1000                                   # map width, legend on the right
        img = Image.new("RGB", (W, H), (10, 20, 40))
        d = ImageDraw.Draw(img)
        for x in range(0, MW, 50):
            d.line([(x, 0), (x, H)], fill=(18, 32, 58))
        for y in range(0, H, 50):
            d.line([(0, y), (MW, y)], fill=(18, 32, 58))
        for poly in _CONTINENTS:
            pts = [(int(x * MW), int(y * H)) for x, y in poly]
            d.polygon(pts, fill=(34, 52, 74), outline=(70, 95, 125))

        try:
            f_big = ImageFont.truetype("DejaVuSans-Bold.ttf", 22)
            f = ImageFont.truetype("DejaVuSans.ttf", 14)
            f_s = ImageFont.truetype("DejaVuSans.ttf", 11)
        except Exception:
            f_big = f = f_s = ImageFont.load_default()

        for k, (name, flag, fx, fy) in LANDS.items():
            x, y = int(fx * MW), int(fy * H)
            c = war_data["countries"].get(k)
            if c and c.get("owner"):
                color = _owner_color(c["owner"]) or (255, 200, 60)
                r, outline = 11, (255, 255, 255)
            else:
                color, r, outline = (90, 120, 150), 5, (120, 150, 180)
            d.ellipse([x - r, y - r, x + r, y + r], fill=color, outline=outline, width=2)
            if c and c.get("owner"):
                d.text((x + r + 3, y - 7), name, fill=(235, 240, 250), font=f_s)

        d.text((12, 10), "WORLD CONQUEST MAP", fill=(220, 230, 255), font=f_big)
        d.text((12, 40), "colored = claimed   |   grey-blue = free", fill=(140, 170, 200), font=f)

        d.rectangle([MW + 10, 10, W - 10, H - 10], fill=(16, 26, 46), outline=(60, 80, 110))
        d.text((MW + 24, 20), "claimed countries", fill=(220, 230, 255), font=f_big)
        yy = 60
        claimed = [(k, c) for k, c in war_data["countries"].items() if c.get("owner")]
        for k, c in claimed[:18]:
            name = LANDS[k][0]
            on = user_names.get(c["owner"]) or _phone_of_jid(c["owner"])
            col = _owner_color(c["owner"]) or (255, 220, 120)
            d.ellipse([MW + 24, yy + 3, MW + 36, yy + 15], fill=col)
            d.text((MW + 44, yy), name, fill=col, font=f)
            d.text((MW + 44, yy + 17), "-> " + str(on)[:28], fill=(180, 200, 220), font=f_s)
            yy += 36
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue()
    except Exception as e:
        print("[!] map render failed:", e, flush=True)
        return None


def _try_send_image(chat_jid, image_bytes, caption="") -> bool:
    if not image_bytes:
        return False
    path = os.path.abspath("last_map.png")
    try:
        with open(path, "wb") as fh:
            fh.write(image_bytes)
    except Exception:
        pass
    # neonize wants raw bytes / path / url — NOT BytesIO
    for label, fn in (
        ("send_image(bytes)",    lambda: client.send_image(chat_jid, image_bytes, caption=caption)),
        ("send_image(path)",     lambda: client.send_image(chat_jid, path, caption=caption)),
        ("send_document(bytes)", lambda: client.send_document(chat_jid, image_bytes, caption=caption,
                                                              filename="world_map.png")),
        ("send_document(path)",  lambda: client.send_document(chat_jid, path, caption=caption)),
    ):
        try:
            fn()
            print("[+]", label, "ok", flush=True)
            return True
        except Exception as e:
            print("[!]", label, "failed:", e, flush=True)
    return False
