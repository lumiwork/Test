# language: Python, file: bot.py, runtime: 3.11, dep: neonize, Pillow
# Gambling bot for WhatsApp. Currency: Dabloons.
# !gamble <game> [args]  — 20+ games, all real functions.
# Balance stored in balance.json next to bot.py.
# Uses edit_message + emoji reactions to reduce chat spam.
#
# Features:
#   - Admin commands ONLY via "!gamble <admin_cmd> ..."
#   - !war  → world-conquest game with a REAL world map (country shapes are
#             embedded in this file, no extra downloads), land claims,
#             resources, combat, attack notifications, economy,
#             guided interactive flows for send/upgrade.
#   - !gamble clover → 3x5 slot machine with paylines, 666 = bust.

# ---- libmagic shim ----
import sys, types
if "magic" not in sys.modules:
    _m = types.ModuleType("magic")
    _m.from_buffer = lambda b, mime=False: "application/octet-stream"
    _m.from_file = lambda p, mime=False: "application/octet-stream"
    class _M:
        def __init__(self, *a, **k): pass
        def from_buffer(self, b, mime=False): return "application/octet-stream"
        def from_file(self, p, mime=False): return "application/octet-stream"
    _m.Magic = _M
    sys.modules["magic"] = _m
# ---- end shim ----

import os, json, time, random, math, threading, io, zlib, base64
from neonize.client import NewClient
from neonize.events import MessageEv, ConnectedEv

try:
    from PIL import Image, ImageDraw, ImageFont
    _PIL_OK = True
except Exception:
    _PIL_OK = False

SESSION_DB    = "wa_session.db"
BALANCE_FILE  = "balance.json"
PAYMENTS_FILE = "payments.json"
NAMES_FILE    = "names.json"
WAR_FILE      = "war.json"
PREFIX        = "!"
CURRENCY      = "🪙 Dabloons"
START_AMOUNT  = 20

# ---- admin config ----
ADMIN_NUMBERS = set()
_env_admin = os.environ.get("BOT_ADMIN", "").strip()
if _env_admin:
    for _x in _env_admin.split(","):
        _x = "".join(c for c in _x if c.isdigit())
        if _x:
            ADMIN_NUMBERS.add(_x)

client = NewClient(SESSION_DB)

# ---------- persistence ----------

def _load_json(path, default):
    if not os.path.exists(path):
        return default
    try:
        with open(path, "r") as f:
            return json.load(f)
    except Exception:
        return default

def _save_json(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(obj, f)
    os.replace(tmp, path)

balances = _load_json(BALANCE_FILE, {})
payments = _load_json(PAYMENTS_FILE, {})
user_names = _load_json(NAMES_FILE, {})
war_data = _load_json(WAR_FILE, {"countries": {}, "players": {}})
war_data.setdefault("countries", {})
war_data.setdefault("players", {})

def _save_balances(): _save_json(BALANCE_FILE, balances)
def _save_payments(): _save_json(PAYMENTS_FILE, payments)
def _save_names():    _save_json(NAMES_FILE, user_names)
def _save_war():      _save_json(WAR_FILE, war_data)

def get_balance(uid: str) -> int:
    if uid not in balances:
        balances[uid] = START_AMOUNT
        _save_balances()
    return balances[uid]

def set_balance(uid: str, amount: int):
    balances[uid] = int(amount)
    _save_balances()

def add_balance(uid: str, amount: int):
    balances[uid] = balances.get(uid, START_AMOUNT) + int(amount)
    _save_balances()

# ---------- phone / jid helpers ----------

def _digits(s: str) -> str:
    if not s:
        return ""
    if "@" in s:
        s = s.split("@", 1)[0]
    return "".join(c for c in s if c.isdigit())

def _phone_of_jid(jid: str) -> str:
    return _digits(jid)

def _find_user_by_phone(phone: str):
    p = _digits(phone)
    if not p:
        return None, "invalid phone number"
    for jid in balances:
        if _digits(jid) == p:
            return jid, None
    for jid in balances:
        if _digits(jid).endswith(p) or p.endswith(_digits(jid)):
            return jid, None
    return None, f"no user `{phone}` has used the bot yet"

def _best_effort_jid(phone: str) -> str:
    p = _digits(phone)
    return f"{p}@s.whatsapp.net"

# ---------- identity ----------

def _sender_id(info) -> str:
    src = info.MessageSource
    if not src.SenderAlt.IsEmpty:
        return f"{src.SenderAlt.User}@{src.SenderAlt.Server}"
    return f"{src.Sender.User}@{src.Sender.Server}"

def _reply_jid(info):
    src = info.MessageSource
    if src.IsGroup:
        return src.Chat
    if src.IsFromMe:
        if not src.Chat.IsEmpty:
            return src.Chat
        for name in ("RecipientAlt", "SenderAlt"):
            j = getattr(src, name, None)
            if j is not None and not j.IsEmpty:
                return j
    else:
        if not src.Chat.IsEmpty:
            return src.Chat
        return src.Sender
    return src.Chat

def _push_name(info) -> str:
    for attr in ("PushName", "Pushname", "push_name"):
        v = getattr(info, attr, None)
        if isinstance(v, str) and v.strip():
            return v.strip()
    src = getattr(info, "MessageSource", None)
    if src is not None:
        for attr in ("PushName", "Pushname", "push_name"):
            v = getattr(src, attr, None)
            if isinstance(v, str) and v.strip():
                return v.strip()
    return ""

def _is_admin(info) -> bool:
    try:
        if info.MessageSource.IsFromMe:
            return True
    except Exception:
        pass
    uid = _sender_id(info)
    return _digits(uid) in ADMIN_NUMBERS

def _get_text(msg):
    if msg.HasField("conversation"):
        return msg.conversation
    if msg.HasField("extendedTextMessage"):
        return msg.extendedTextMessage.text
    return None

# ---------- game helper ----------

def parse_amount(s: str, uid: str, all_in: bool = False):
    if all_in or s.lower() in ("all", "allin", "max"):
        amt = get_balance(uid)
        if amt <= 0:
            return 0, "you're broke, no bet to place 💸"
        return amt, None
    if not s.isdigit():
        return 0, "amount must be a number (or `all`)"
    amt = int(s)
    if amt <= 0:
        return 0, "bet must be positive"
    if amt > get_balance(uid):
        return amt, f"you only have {get_balance(uid)} {CURRENCY}"
    return amt, None

def fmt_bet(uid: str, amt: int, result: str, delta: int, emoji: str) -> str:
    return (f"{emoji} {result}\n"
            f"bet: {amt} {CURRENCY}\n"
            f"{'+' if delta >= 0 else ''}{delta} {CURRENCY}\n"
            f"balance: {get_balance(uid)} {CURRENCY}")

# ---------- message editing + reactions ----------

def _extract_msg_id(sent):
    if sent is None:
        return None
    candidates = []
    for attr in ("ID", "id"):
        v = getattr(sent, attr, None)
        if isinstance(v, str) and v:
            candidates.append(v)
    key = getattr(sent, "key", None)
    if key is not None:
        for attr in ("id", "ID"):
            v = getattr(key, attr, None)
            if isinstance(v, str) and v:
                candidates.append(v)
    info = getattr(sent, "Info", None)
    if info is not None:
        v = getattr(info, "ID", None)
        if isinstance(v, str) and v:
            candidates.append(v)
    msg = getattr(sent, "message", None)
    if msg is not None:
        k2 = getattr(msg, "key", None)
        if k2 is not None:
            v = getattr(k2, "id", None)
            if isinstance(v, str) and v:
                candidates.append(v)
    return candidates[0] if candidates else None


def _try_edit(chat_jid, msg_id, text) -> bool:
    if not msg_id:
        return False
    try:
        client.edit_message(chat_jid, msg_id, text)
        return True
    except Exception:
        pass
    try:
        from neonize.proto.waE2E.WAWebProtobufsE2E_pb2 import Message as WAMessage
        client.edit_message(chat_jid, msg_id, WAMessage(conversation=text))
        return True
    except Exception:
        pass
    try:
        client.edit_message(chat_jid=chat_jid, message_id=msg_id, message=text)
        return True
    except Exception:
        pass
    return False


def _try_send_image(chat_jid, image_bytes, caption="") -> bool:
    """Send a PNG. neonize wants raw bytes / a file path / a URL — NOT a
    BytesIO object (that was the old 'bytes-like object is required' error).
    Falls back to path, then to sending as a document."""
    if not image_bytes:
        print("[!] _try_send_image: no bytes", flush=True)
        return False

    path = os.path.abspath("last_map.png")
    try:
        with open(path, "wb") as _f:
            _f.write(image_bytes)
    except Exception:
        pass

    attempts = (
        ("send_image(bytes)",
         lambda: client.send_image(chat_jid, image_bytes, caption=caption)),
        ("send_image(path)",
         lambda: client.send_image(chat_jid, path, caption=caption)),
        ("send_document(bytes)",
         lambda: client.send_document(chat_jid, image_bytes, caption=caption,
                                      filename="world_map.png")),
        ("send_document(path)",
         lambda: client.send_document(chat_jid, path, caption=caption)),
    )
    for label, fn in attempts:
        try:
            fn()
            print(f"[+] {label} ok", flush=True)
            return True
        except Exception as e:
            print(f"[!] {label} failed: {e}", flush=True)
    return False


def _react(info, reply_jid, emoji) -> bool:
    try:
        msg_id = getattr(info, "ID", None)
    except Exception:
        msg_id = None
    if not msg_id:
        return False
    sender = None
    try:
        src = info.MessageSource
        sender = src.SenderAlt if not src.SenderAlt.IsEmpty else src.Sender
    except Exception:
        sender = None
    for args in (
        (reply_jid, sender or reply_jid, msg_id, emoji),
        (reply_jid, msg_id, emoji),
    ):
        try:
            client.send_reaction(*args)
            return True
        except Exception:
            continue
    try:
        from neonize.proto.waE2E.WAWebProtobufsE2E_pb2 import (
            Message as WAMessage, ReactionMessage, MessageKey,
        )
        def jid_str(j):
            try:
                return f"{j.User}@{j.Server}"
            except Exception:
                return str(j)
        key_kwargs = {"id": msg_id, "remoteJid": jid_str(reply_jid), "fromMe": False}
        if sender is not None:
            key_kwargs["participant"] = jid_str(sender)
        key = MessageKey(**key_kwargs)
        reaction = ReactionMessage(key=key, text=emoji)
        msg = WAMessage(reactionMessage=reaction)
        client.send_message(reply_jid, msg)
        return True
    except Exception:
        pass
    return False


def _run_frames(reply_jid, frames, delay=0.9):
    if frames is None:
        return
    if isinstance(frames, str):
        frames = [frames]
    frames = [str(f) for f in frames if f]
    if not frames:
        return
    try:
        sent = client.send_message(reply_jid, frames[0])
    except Exception as e:
        print("[!] send failed:", e, flush=True)
        return
    if len(frames) == 1:
        return
    msg_id = _extract_msg_id(sent)
    for f in frames[1:]:
        time.sleep(delay)
        ok = _try_edit(reply_jid, msg_id, f) if msg_id else False
        if not ok:
            try:
                client.send_message(reply_jid, f)
            except Exception as e:
                print("[!] send failed:", e, flush=True)


def _dispatch(reply_jid, resp):
    threading.Thread(target=_run_frames, args=(reply_jid, resp), daemon=True).start()


# ========== CASINO GAMES ==========

def game_coinflip(uid, args):
    if len(args) < 1:
        return "usage: !gamble coinflip <bet> [h|t]", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    pick = args[1].lower() if len(args) > 1 else "h"
    if pick not in ("h","t","heads","tails"):
        pick = "h"
    pick = "h" if pick.startswith("h") else "t"
    flip = random.choice("ht")
    win = flip == pick
    delta = bet if win else -bet
    add_balance(uid, delta)
    emoji = "🪙" if win else "💀"
    head = "heads 🗣" if flip == "h" else "tails 🪙"
    return (f"🪙 flipped: {head} (you picked {'heads' if pick=='h' else 'tails'})\n"
            + fmt_bet(uid, bet, "WIN!" if win else "LOSE.", delta, emoji)), None

def game_dice(uid, args):
    if len(args) < 1:
        return "usage: !gamble dice <bet> [target 1-6]", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    target = 6
    if len(args) > 1 and args[1].isdigit():
        target = max(1, min(6, int(args[1])))
    roll = random.randint(1, 6)
    win = roll == target
    delta = bet * 5 if win else -bet
    add_balance(uid, delta)
    return (f"🎲 rolled {roll} (target {target})\n"
            + fmt_bet(uid, bet, "JACKPOT x5!" if win else "no dice.", delta,
                      "🎯" if win else "💀")), None

def game_slots(uid, args):
    if len(args) < 1:
        return "usage: !gamble slots <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    syms = ["🍒","🍋","🔔","⭐","💎","7️⃣"]
    r = [random.choice(syms) for _ in range(3)]
    counts = {s: r.count(s) for s in set(r)}
    mult = 0
    if counts.get("7️⃣") == 3: mult = 50
    elif counts.get("💎") == 3: mult = 25
    elif any(c == 3 for c in counts.values()): mult = 10
    elif any(c == 2 for c in counts.values()): mult = 2
    delta = bet * (mult - 1) if mult > 0 else -bet
    add_balance(uid, delta)
    frames = [f"🎰 spinning…  bet {bet} {CURRENCY}"]
    for _ in range(2):
        rr = [random.choice(syms) for _ in range(3)]
        frames.append(f"🎰 | {' | '.join(rr)} |")
    frames.append(
        f"🎰 | {' | '.join(r)} |\n"
        + fmt_bet(uid, bet,
                  f"BIG WIN x{mult}!" if mult >= 10 else
                  (f"match x{mult}" if mult > 0 else "no match."),
                  delta, "🎉" if mult > 0 else "💀")
    )
    return frames, None

# ----- CLOVER: 3x5 slot machine -----

def _clover_spin():
    syms = ["🍒","🍋","🔔","⭐","💎","🍀","6️⃣"]
    weights = [18, 18, 16, 14, 10, 4, 20]
    return [random.choices(syms, weights=weights)[0] for _ in range(15)]

def _clover_show(g):
    lines = []
    for r in range(3):
        row = g[r*5:(r+1)*5]
        lines.append(" | ".join(row))
    return "\n".join(lines)

_CLOVER_PAYLINES = [
    ("middle 5 straight", [(1,0),(1,1),(1,2),(1,3),(1,4)], 10),
    ("top 5 straight",    [(0,0),(0,1),(0,2),(0,3),(0,4)], 12),
    ("bottom 5 straight", [(2,0),(2,1),(2,2),(2,3),(2,4)], 12),
    ("V shape",           [(0,0),(1,1),(2,2),(1,3),(0,4)], 20),
    ("inverted V",        [(2,0),(1,1),(0,2),(1,3),(2,4)], 20),
    ("zig-zag",           [(0,0),(1,1),(2,2),(2,3),(1,4)], 15),
    ("middle 3",          [(1,1),(1,2),(1,3)], 5),
    ("top 3",             [(0,1),(0,2),(0,3)], 5),
    ("bottom 3",          [(2,1),(2,2),(2,3)], 5),
    ("left col 3",        [(0,0),(1,0),(2,0)], 6),
    ("mid col 3",         [(0,2),(1,2),(2,2)], 6),
    ("right col 3",       [(0,4),(1,4),(2,4)], 6),
    ("full middle row",   [(1,0),(1,1),(1,2),(1,3),(1,4)], 8),
]

def _clover_line_match(g, cells):
    vals = [g[r*5+c] for (r,c) in cells]
    if all(v == vals[0] for v in vals):
        return vals[0]
    if all(v in (vals[0], "🍀") for v in vals) and "🍀" in vals:
        return vals[0] if vals[0] != "🍀" else "🍀"
    return None

def game_clover(uid, args):
    if len(args) < 1:
        return "usage: !gamble clover <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None

    frames = [f"🍀 *CLOVER* — spinning…  bet {bet} {CURRENCY}"]
    for _ in range(2):
        gg = _clover_spin()
        frames.append("🍀 spinning…\n" + _clover_show(gg))

    grid = _clover_spin()

    center_three = [grid[1*5+1], grid[1*5+2], grid[1*5+3]]
    if all(s == "6️⃣" for s in center_three):
        bal = get_balance(uid)
        set_balance(uid, 0)
        frames.append(
            "🍀 final spin:\n" + _clover_show(grid) +
            f"\n\n☠️ *CURSED* — 6️⃣6️⃣6️⃣ in the middle!\n"
            f"you lost EVERYTHING: -{bal} {CURRENCY}\n"
            f"balance: 0 {CURRENCY}"
        )
        return frames, None

    hits = []
    total_mult = 0
    for name, cells, mult in _CLOVER_PAYLINES:
        if mult <= 0:
            continue
        match = _clover_line_match(grid, cells)
        if match is None:
            continue
        sym_mult = {
            "🍒": 1.0, "🍋": 1.0, "🔔": 1.2, "⭐": 1.5,
            "💎": 2.5, "🍀": 3.0, "6️⃣": 0.5,
        }.get(match, 1.0)
        line_mult = round(mult * sym_mult, 2)
        hits.append((name, match, line_mult))
        total_mult += line_mult

    if total_mult > 0:
        total_mult = min(total_mult, 200.0)
        delta = int(bet * (total_mult - 1))
    else:
        delta = -bet

    add_balance(uid, delta)

    hit_txt = ""
    if hits:
        hit_txt = "\n" + "\n".join(
            f"  ✓ {name}: {sym} x{m}" for name, sym, m in hits
        )

    frames.append(
        "🍀 final spin:\n" + _clover_show(grid) + "\n" +
        (f"wins:{hit_txt}\n" if hits else "no lines matched\n") +
        fmt_bet(uid, bet,
                f"BIG WIN x{round(total_mult,2)}!" if total_mult >= 5 else
                (f"win x{round(total_mult,2)}" if total_mult > 0 else "no luck."),
                delta, "🎉" if total_mult > 0 else "💀")
    )
    return frames, None

_ROULETTE_REDS = {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}

def _roulette_color(n: int) -> str:
    if n == 0:
        return "green"
    return "red" if n in _ROULETTE_REDS else "black"

def _roulette_emoji(n: int) -> str:
    c = _roulette_color(n)
    return "🟢" if c == "green" else ("🔴" if c == "red" else "⚫")

def game_roulette(uid, args):
    if len(args) < 2:
        return ("usage: !gamble roulette <bet> <pick>\n"
                "picks: red, black, green, odd, even, low, high,\n"
                "       d1/d2/d3 (dozens), c1/c2/c3 (columns), or 0-36"), None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    pick = args[1].lower().strip()
    n = random.randint(0, 36)
    color = _roulette_color(n)
    win = False; mult = 0; label = pick
    if pick in ("red", "r"):
        win = color == "red";              mult = 2;  label = "RED"
    elif pick in ("black", "b"):
        win = color == "black";            mult = 2;  label = "BLACK"
    elif pick in ("green", "g", "0"):
        win = n == 0;                      mult = 36; label = "GREEN (0)"
    elif pick in ("odd", "o"):
        win = n != 0 and n % 2 == 1;       mult = 2;  label = "ODD"
    elif pick in ("even", "e"):
        win = n != 0 and n % 2 == 0;       mult = 2;  label = "EVEN"
    elif pick in ("low", "l", "1-18", "1to18"):
        win = 1 <= n <= 18;                mult = 2;  label = "LOW (1-18)"
    elif pick in ("high", "h", "19-36", "19to36"):
        win = 19 <= n <= 36;               mult = 2;  label = "HIGH (19-36)"
    elif pick in ("d1", "1st12", "1-12", "dozen1"):
        win = 1 <= n <= 12;                mult = 3;  label = "1st DOZEN (1-12)"
    elif pick in ("d2", "2nd12", "13-24", "dozen2"):
        win = 13 <= n <= 24;               mult = 3;  label = "2nd DOZEN (13-24)"
    elif pick in ("d3", "3rd12", "25-36", "dozen3"):
        win = 25 <= n <= 36;               mult = 3;  label = "3rd DOZEN (25-36)"
    elif pick in ("c1", "col1", "column1"):
        win = n != 0 and n % 3 == 1;       mult = 3;  label = "COLUMN 1"
    elif pick in ("c2", "col2", "column2"):
        win = n != 0 and n % 3 == 2;       mult = 3;  label = "COLUMN 2"
    elif pick in ("c3", "col3", "column3"):
        win = n != 0 and n % 3 == 0;       mult = 3;  label = "COLUMN 3"
    elif pick.isdigit() and 0 <= int(pick) <= 36:
        win = n == int(pick);              mult = 36; label = f"NUMBER {pick}"
    else:
        return ("invalid pick — try red, black, green, odd, even, low, high,\n"
                "d1/d2/d3, c1/c2/c3, or a number 0-36"), None
    delta = bet * (mult - 1) if win else -bet
    add_balance(uid, delta)
    frames = [f"🎡 *ROULETTE*\nbet {bet} {CURRENCY} on *{label}*\n_spinning…_"]
    for _ in range(3):
        nn = random.randint(0, 36)
        frames.append(f"🎡 spinning…\n> {nn} {_roulette_emoji(nn)}")
    frames.append(
        f"🎡 *ROULETTE* — ball on *{n}* {_roulette_emoji(n)} ({color})\n"
        f"your bet: *{label}*\n"
        + fmt_bet(uid, bet, f"WIN x{mult}!" if win else "LOSE.",
                  delta, "🎉" if win else "💀")
    )
    return frames, None

def game_blackjack(uid, args):
    if len(args) < 1:
        return "usage: !gamble blackjack <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    def draw(): return random.randint(1, 11)
    p = draw() + draw(); d = draw() + draw()
    if p == 21: delta = int(bet * 1.5)
    elif d == 21: delta = -bet
    elif p > 21: delta = -bet
    elif d > 21: delta = bet
    elif p > d: delta = bet
    elif p < d: delta = -bet
    else: delta = 0
    add_balance(uid, delta)
    res = "WIN!" if delta > 0 else ("push." if delta == 0 else "LOSE.")
    return (f"🃏 you {p} — dealer {d}\n"
            + fmt_bet(uid, bet, res, delta,
                      "🎉" if delta > 0 else ("🤝" if delta == 0 else "💀"))), None

def game_highlow(uid, args):
    if len(args) < 2:
        return "usage: !gamble highlow <bet> <h|l>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    pick = args[1].lower()
    if pick not in ("h","l","high","low"):
        return "pick h or l", None
    pick_h = pick.startswith("h")
    a = random.randint(1, 100); b = random.randint(1, 100)
    while b == a: b = random.randint(1, 100)
    win = (b > a) == pick_h
    delta = bet if win else -bet
    add_balance(uid, delta)
    return (f"📈 {a} → {b}\n"
            + fmt_bet(uid, bet, "called it!" if win else "wrong.", delta,
                      "🎯" if win else "💀")), None

def game_crash(uid, args):
    if len(args) < 2:
        return "usage: !gamble crash <bet> <cashout_multiplier 1.05-4>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    try: target = float(args[1])
    except Exception: return "multiplier must be a number like 1.5", None
    if not (1.05 <= target <= 4.0):
        return "multiplier must be 1.05 – 4.0", None
    r = random.random()
    if r < 0.60: crash = round(random.uniform(1.00, 1.30), 2)
    elif r < 0.85: crash = round(random.uniform(1.30, 2.00), 2)
    elif r < 0.97: crash = round(random.uniform(2.00, 3.00), 2)
    else: crash = round(random.uniform(3.00, 4.00), 2)
    win = target < crash
    delta = int(bet * (target - 1)) if win else -bet
    add_balance(uid, delta)
    return (f"🚀 crashed at x{crash}\n"
            + fmt_bet(uid, bet,
                      f"cashed out at x{target}!" if win else "crashed.",
                      delta, "🎉" if win else "💥")), None

def game_mine(uid, args):
    if len(args) < 1:
        return "usage: !gamble mine <bet> [mines 1-24]", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    mines = 1
    if len(args) > 1 and args[1].isdigit():
        mines = max(1, min(24, int(args[1])))
    safe_cells = 25 - mines
    mult = round((25 / max(1, safe_cells)) * 0.98, 2)
    mine_hit = random.random() < mines / 25
    win = not mine_hit
    delta = int(bet * (mult - 1)) if win else -bet
    add_balance(uid, delta)
    return (f"💣 {'BANG' if mine_hit else 'safe'} — {mines} mines, x{mult}\n"
            + fmt_bet(uid, bet, "survived!" if win else "boom.", delta,
                      "🎉" if win else "💥")), None

def game_plinko(uid, args):
    if len(args) < 1:
        return "usage: !gamble plinko <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    slots = [0.2, 0.5, 1.0, 1.5, 2.0, 5.0, 2.0, 1.5, 1.0, 0.5, 0.2]
    idx = random.choices(range(len(slots)), weights=[1,2,3,4,5,4,3,4,5,2,1])[0]
    mult = slots[idx]
    delta = int(bet * (mult - 1))
    add_balance(uid, delta)
    return (f"🎯 plinko lands on x{mult}\n"
            + fmt_bet(uid, bet, f"x{mult}", delta,
                      "🎉" if mult >= 1 else "💀")), None

def game_limbo(uid, args):
    if len(args) < 2:
        return "usage: !gamble limbo <bet> <target_mult 1.1-100>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    try: t = float(args[1])
    except Exception: return "target must be a number", None
    if not (1.1 <= t <= 100): return "target must be 1.1 – 100", None
    roll = round(100.0 / random.uniform(1, 101), 2)
    win = roll >= t
    delta = int(bet * (t - 1)) if win else -bet
    add_balance(uid, delta)
    return (f"🎯 limbo rolled x{roll} (target x{t})\n"
            + fmt_bet(uid, bet, "hit!" if win else "miss.", delta,
                      "🎉" if win else "💀")), None

def game_tower(uid, args):
    if len(args) < 2:
        return "usage: !gamble tower <bet> <floors 1-8>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    try: floors = max(1, min(8, int(args[1])))
    except Exception: return "floors must be 1-8", None
    mult = round(1.6 ** floors, 2)
    fail_chance = 1 - (0.75 ** floors)
    win = random.random() > fail_chance
    delta = int(bet * (mult - 1)) if win else -bet
    add_balance(uid, delta)
    return (f"🗼 climbed {floors} floors (fail chance {fail_chance:.0%})\n"
            + fmt_bet(uid, bet, f"x{mult}!" if win else "fell.", delta,
                      "🎉" if win else "🪂")), None

def game_keno(uid, args):
    if len(args) < 2:
        return "usage: !gamble keno <bet> <pick1,pick2,... 1-80>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    try:
        picks = sorted({int(x) for x in args[1].split(",") if x.strip()})
    except Exception:
        return "picks must be comma-separated numbers 1-80", None
    if not (1 <= len(picks) <= 10) or any(not (1 <= p <= 80) for p in picks):
        return "pick 1–10 numbers between 1 and 80", None
    draws = set(random.sample(range(1, 81), 20))
    hits = len(draws.intersection(picks))
    table = {
        1: {1: 3.5},
        2: {1: 1.5, 2: 5},
        3: {1: 1, 2: 3, 3: 25},
        4: {1: 0.5, 2: 2, 3: 10, 4: 100},
        5: {1: 0, 2: 1, 3: 5, 4: 25, 5: 200},
        6: {3: 3, 4: 15, 5: 50, 6: 500},
        7: {4: 5, 5: 20, 6: 100, 7: 1000},
        8: {4: 3, 5: 10, 6: 50, 7: 500, 8: 2000},
        9: {4: 2, 5: 5, 6: 25, 7: 200, 8: 1000, 9: 5000},
        10:{5: 2, 6: 10, 7: 50, 8: 500, 9: 2000, 10: 10000},
    }
    mult = table.get(len(picks), {}).get(hits, 0)
    delta = int(bet * (mult - 1)) if mult > 1 else (bet if mult == 1 else -bet)
    add_balance(uid, delta)
    return (f"🎱 hits {hits}/{len(picks)}\n"
            f"your picks: {picks}\n"
            + fmt_bet(uid, bet, f"x{mult}!" if mult > 1 else
                      ("break even" if mult == 1 else "no hit."),
                      delta, "🎉" if mult > 1 else ("🤝" if mult == 1 else "💀"))), None

def game_guess(uid, args):
    if len(args) < 1:
        return "usage: !gamble guess <bet> [your_number 1-100]", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    guess = 50
    if len(args) > 1 and args[1].isdigit():
        guess = max(1, min(100, int(args[1])))
    num = random.randint(1, 100)
    diff = abs(num - guess)
    if diff == 0: mult = 30
    elif diff <= 3: mult = 5
    elif diff <= 10: mult = 2
    else: mult = 0
    delta = int(bet * (mult - 1)) if mult > 0 else -bet
    add_balance(uid, delta)
    return (f"🎯 number was {num}, you guessed {guess} (diff {diff})\n"
            + fmt_bet(uid, bet, f"x{mult}!" if mult > 0 else "off.",
                      delta, "🎉" if mult > 0 else "💀")), None

def game_warcards(uid, args):
    if len(args) < 1:
        return "usage: !gamble warcards <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    p = random.randint(1, 13); d = random.randint(1, 13)
    if p > d: delta = bet
    elif p < d: delta = -bet
    else: delta = 0
    add_balance(uid, delta)
    return (f"⚔️ you {p} — dealer {d}\n"
            + fmt_bet(uid, bet, "win!" if delta > 0 else ("tie." if delta == 0 else "lose."),
                      delta, "🎉" if delta > 0 else ("🤝" if delta == 0 else "💀"))), None

def game_penalty(uid, args):
    if len(args) < 1:
        return "usage: !gamble penalty <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    keeper = random.choice(["left","center","right"])
    shot = random.choice(["left","center","right"])
    if shot == keeper:
        delta = -bet; emoji="🧤"; res="saved!"
    else:
        delta = bet; emoji="⚽"; res="GOAL!"
    add_balance(uid, delta)
    return (f"🥅 keeper dives {keeper}, you shoot {shot}\n"
            + fmt_bet(uid, bet, res, delta, emoji)), None

def game_rps(uid, args):
    if len(args) < 2:
        return "usage: !gamble rps <bet> <r|p|s>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    pick = args[1].lower()[:1]
    if pick not in ("r","p","s"): return "pick r, p or s", None
    opp = random.choice("rps")
    beats = {"r":"s","p":"r","s":"p"}
    if pick == opp: delta = 0; res = "tie."
    elif beats[pick] == opp: delta = bet; res = "win!"
    else: delta = -bet; res = "lose."
    add_balance(uid, delta)
    icons = {"r":"🪨","p":"📄","s":"✂️"}
    return (f"{icons[pick]} vs {icons[opp]}\n"
            + fmt_bet(uid, bet, res, delta,
                      "🎉" if delta > 0 else ("🤝" if delta == 0 else "💀"))), None

def game_bj21(uid, args): return game_blackjack(uid, args)

def game_lucky(uid, args):
    if len(args) < 1:
        return "usage: !gamble lucky <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    r = random.random()
    if r < 0.001: mult = 100
    elif r < 0.01: mult = 20
    elif r < 0.05: mult = 5
    elif r < 0.15: mult = 2
    elif r < 0.35: mult = 1.5
    else: mult = 0
    delta = int(bet * (mult - 1)) if mult > 0 else -bet
    add_balance(uid, delta)
    return ("🍀 " + (f"JACKPOT x{mult}!" if mult >= 20 else
                     (f"lucky x{mult}!" if mult > 0 else "not your day.")) + "\n"
            + fmt_bet(uid, bet, "", delta, "🎉" if mult > 0 else "💀")), None

def game_rocket(uid, args):
    if len(args) < 1:
        return "usage: !gamble rocket <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    peak = random.uniform(1.0, 12.0)
    auto = random.uniform(1.1, max(1.2, peak))
    cashed = auto < peak
    delta = int(bet * (auto - 1)) if cashed else -bet
    add_balance(uid, delta)
    return (f"🚀 peak x{peak:.2f}, cashed at x{auto:.2f}\n"
            + fmt_bet(uid, bet,
                      f"cash x{auto:.2f}!" if cashed else "exploded.",
                      delta, "🎉" if cashed else "💥")), None

def game_ladder(uid, args):
    if len(args) < 2:
        return "usage: !gamble ladder <bet> <rungs 1-10>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    try: rungs = max(1, min(10, int(args[1])))
    except Exception: return "rungs must be 1-10", None
    mult = round(1.5 ** rungs, 2)
    fail = 1 - (0.7 ** rungs)
    win = random.random() > fail
    delta = int(bet * (mult - 1)) if win else -bet
    add_balance(uid, delta)
    return (f"🪜 climbed {rungs} rungs (fail {fail:.0%}), payout x{mult}\n"
            + fmt_bet(uid, bet, "up!" if win else "slip.", delta,
                      "🎉" if win else "🪂")), None

def game_bomb(uid, args):
    if len(args) < 1:
        return "usage: !gamble bomb <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    wires = ["🔴","🔵","🟢","🟡","🟣"]
    pick = random.choice(wires)
    safe = random.choice([w for w in wires if w != "🔴"])
    win = pick == safe
    mult = 3
    delta = bet * (mult - 1) if win else -bet
    add_balance(uid, delta)
    return (f"💣 you cut {pick}\n"
            + fmt_bet(uid, bet, "defused!" if win else "BOOM.",
                      delta, "🛠" if win else "💥")), None

def game_streaker(uid, args):
    if len(args) < 1:
        return "usage: !gamble streaker <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    streak = 0
    while random.random() < 0.5 and streak < 12:
        streak += 1
    mult = 1 + streak * 0.75
    delta = int(bet * (mult - 1))
    add_balance(uid, delta)
    return (f"📈 streak of {streak} (x{mult:.2f})\n"
            + fmt_bet(uid, bet, f"payout x{mult:.2f}",
                      delta, "🎉" if mult > 1 else "🤏")), None

def game_flip2(uid, args):
    if len(args) < 1:
        return "usage: !gamble flip2 <bet>", None
    bet, err = parse_amount(args[0], uid)
    if err: return err, None
    need = args[1].lower() if len(args) > 1 else "h"
    need = "h" if need.startswith("h") else "t"
    flips = [random.choice("ht") for _ in range(2)]
    win = all(f == need for f in flips)
    delta = bet * 3 if win else -bet
    add_balance(uid, delta)
    return (f"🪙🪙 flips: {flips[0]} {flips[1]} (needed {need}{need})\n"
            + fmt_bet(uid, bet, "double!" if win else "nope.",
                      delta, "🎉" if win else "💀")), None


# ========== WAR GAME (WORLD CONQUEST) ==========

LANDS = {
    "russia":       ("Russia",         "🇷🇺", 0.62, 0.18),
    "canada":       ("Canada",         "🇨🇦", 0.18, 0.18),
    "usa":          ("United States",  "🇺🇸", 0.20, 0.32),
    "mexico":       ("Mexico",         "🇲🇽", 0.18, 0.46),
    "brazil":       ("Brazil",         "🇧🇷", 0.32, 0.62),
    "argentina":    ("Argentina",      "🇦🇷", 0.30, 0.78),
    "chile":        ("Chile",          "🇨🇱", 0.27, 0.82),
    "peru":         ("Peru",           "🇵🇪", 0.27, 0.66),
    "colombia":     ("Colombia",       "🇨🇴", 0.27, 0.55),
    "venezuela":    ("Venezuela",      "🇻🇪", 0.30, 0.52),
    "uk":           ("United Kingdom", "🇬🇧", 0.46, 0.24),
    "france":       ("France",         "🇫🇷", 0.47, 0.30),
    "spain":        ("Spain",          "🇪🇸", 0.44, 0.36),
    "portugal":     ("Portugal",       "🇵🇹", 0.42, 0.36),
    "germany":      ("Germany",        "🇩🇪", 0.50, 0.26),
    "italy":        ("Italy",          "🇮🇹", 0.51, 0.34),
    "poland":       ("Poland",         "🇵🇱", 0.53, 0.26),
    "ukraine":      ("Ukraine",        "🇺🇦", 0.56, 0.28),
    "sweden":       ("Sweden",         "🇸🇪", 0.52, 0.16),
    "norway":       ("Norway",         "🇳🇴", 0.50, 0.14),
    "finland":      ("Finland",        "🇫🇮", 0.56, 0.15),
    "turkey":       ("Turkey",         "🇹🇷", 0.56, 0.36),
    "greece":       ("Greece",         "🇬🇷", 0.54, 0.38),
    "egypt":        ("Egypt",          "🇪🇬", 0.56, 0.46),
    "libya":        ("Libya",          "🇱🇾", 0.53, 0.46),
    "algeria":      ("Algeria",        "🇩🇿", 0.48, 0.46),
    "morocco":      ("Morocco",        "🇲🇦", 0.44, 0.44),
    "nigeria":      ("Nigeria",        "🇳🇬", 0.50, 0.56),
    "ghana":        ("Ghana",          "🇬🇭", 0.47, 0.57),
    "ethiopia":     ("Ethiopia",       "🇪🇹", 0.58, 0.58),
    "kenya":        ("Kenya",          "🇰🇪", 0.58, 0.64),
    "southafrica":  ("South Africa",   "🇿🇦", 0.54, 0.78),
    "saudi":        ("Saudi Arabia",   "🇸🇦", 0.60, 0.46),
    "iran":         ("Iran",           "🇮🇷", 0.63, 0.42),
    "iraq":         ("Iraq",           "🇮🇶", 0.61, 0.42),
    "israel":       ("Israel",         "🇮🇱", 0.58, 0.42),
    "india":        ("India",          "🇮🇳", 0.70, 0.50),
    "pakistan":     ("Pakistan",       "🇵🇰", 0.68, 0.46),
    "china":        ("China",          "🇨🇳", 0.76, 0.40),
    "mongolia":     ("Mongolia",       "🇲🇳", 0.78, 0.34),
    "kazakhstan":   ("Kazakhstan",     "🇰🇿", 0.68, 0.32),
    "japan":        ("Japan",          "🇯🇵", 0.88, 0.38),
    "skorea":       ("South Korea",    "🇰🇷", 0.86, 0.40),
    "nkorea":       ("North Korea",    "🇰🇵", 0.85, 0.37),
    "vietnam":      ("Vietnam",        "🇻🇳", 0.80, 0.50),
    "thailand":     ("Thailand",       "🇹🇭", 0.78, 0.52),
    "indonesia":    ("Indonesia",      "🇮🇩", 0.82, 0.62),
    "philippines":  ("Philippines",    "🇵🇭", 0.86, 0.50),
    "australia":    ("Australia",      "🇦🇺", 0.84, 0.74),
    "newzealand":   ("New Zealand",    "🇳🇿", 0.94, 0.82),
    "greenland":    ("Greenland",      "🇬🇱", 0.30, 0.10),
    "iceland":      ("Iceland",        "🇮🇸", 0.40, 0.12),
    "ireland":      ("Ireland",        "🇮🇪", 0.44, 0.24),
    "netherlands":  ("Netherlands",    "🇳🇱", 0.48, 0.28),
    "belgium":      ("Belgium",        "🇧🇪", 0.48, 0.30),
    "switzerland":  ("Switzerland",    "🇨🇭", 0.50, 0.31),
    "austria":      ("Austria",        "🇦🇹", 0.52, 0.30),
    "hungary":      ("Hungary",        "🇭🇺", 0.54, 0.30),
    "romania":      ("Romania",        "🇷🇴", 0.55, 0.32),
    "bulgaria":     ("Bulgaria",       "🇧🇬", 0.55, 0.34),
    "serbia":       ("Serbia",         "🇷🇸", 0.53, 0.32),
    "croatia":      ("Croatia",        "🇭🇷", 0.52, 0.33),
    "bosnia":       ("Bosnia",         "🇧🇦", 0.53, 0.33),
    "albania":      ("Albania",        "🇦🇱", 0.54, 0.35),
    "denmark":      ("Denmark",        "🇩🇰", 0.50, 0.22),
    "czech":        ("Czechia",        "🇨🇿", 0.52, 0.28),
    "slovakia":     ("Slovakia",       "🇸🇰", 0.54, 0.28),
    "lithuania":    ("Lithuania",      "🇱🇹", 0.55, 0.23),
    "latvia":       ("Latvia",         "🇱🇻", 0.56, 0.22),
    "estonia":      ("Estonia",        "🇪🇪", 0.57, 0.20),
    "belarus":      ("Belarus",        "🇧🇾", 0.56, 0.25),
    "moldova":      ("Moldova",        "🇲🇩", 0.56, 0.31),
    "georgia":      ("Georgia",        "🇬🇪", 0.60, 0.36),
    "armenia":      ("Armenia",        "🇦🇲", 0.61, 0.37),
    "azerbaijan":   ("Azerbaijan",     "🇦🇿", 0.62, 0.37),
    "syria":        ("Syria",          "🇸🇾", 0.60, 0.40),
    "jordan":       ("Jordan",         "🇯🇴", 0.58, 0.42),
    "lebanon":      ("Lebanon",        "🇱🇧", 0.59, 0.40),
    "kuwait":       ("Kuwait",         "🇰🇼", 0.62, 0.44),
    "uae":          ("UAE",            "🇦🇪", 0.63, 0.46),
    "oman":         ("Oman",           "🇴🇲", 0.65, 0.48),
    "yemen":        ("Yemen",          "🇾🇪", 0.61, 0.50),
    "afghanistan":  ("Afghanistan",    "🇦🇫", 0.67, 0.44),
    "turkmenistan": ("Turkmenistan",   "🇹🇲", 0.65, 0.38),
    "uzbekistan":   ("Uzbekistan",     "🇺🇿", 0.66, 0.38),
    "tajikistan":   ("Tajikistan",     "🇹🇯", 0.68, 0.40),
    "kyrgyzstan":   ("Kyrgyzstan",     "🇰🇬", 0.69, 0.38),
    "nepal":        ("Nepal",          "🇳🇵", 0.71, 0.46),
    "bangladesh":   ("Bangladesh",     "🇧🇩", 0.72, 0.50),
    "myanmar":      ("Myanmar",        "🇲🇲", 0.76, 0.50),
    "laos":         ("Laos",           "🇱🇦", 0.79, 0.50),
    "cambodia":     ("Cambodia",       "🇰🇭", 0.79, 0.54),
    "malaysia":     ("Malaysia",       "🇲🇾", 0.80, 0.58),
    "singapore":    ("Singapore",      "🇸🇬", 0.82, 0.60),
    "brunei":       ("Brunei",         "🇧🇳", 0.82, 0.58),
    "png":          ("Papua New Guinea","🇵🇬",0.92, 0.66),
    "fiji":         ("Fiji",           "🇫🇯", 0.98, 0.70),
}

_EXPLICIT_NEIGHBORS = {
    "russia":     {"china","mongolia","kazakhstan","ukraine","belarus","finland",
                    "norway","estonia","latvia","lithuania","poland","georgia",
                    "azerbaijan","japan","usa","nkorea","skorea"},
    "china":      {"russia","mongolia","kazakhstan","india","nepal","pakistan",
                    "afghanistan","kyrgyzstan","tajikistan","vietnam","laos",
                    "myanmar","nkorea"},
    "usa":        {"canada","mexico","russia"},
    "canada":     {"usa","greenland"},
    "greenland":  {"canada","iceland"},
    "iceland":    {"greenland","uk","norway"},
    "uk":         {"ireland","france","netherlands","belgium","norway","iceland"},
    "ireland":    {"uk"},
    "japan":      {"russia","skorea","nkorea"},
    "skorea":     {"nkorea","japan","china"},
    "nkorea":     {"skorea","china","russia","japan"},
    "india":      {"pakistan","china","nepal","bangladesh","myanmar"},
    "australia":  {"indonesia","png","newzealand"},
    "newzealand": {"australia"},
    "indonesia":  {"malaysia","singapore","png","australia","brunei"},
    "malaysia":   {"indonesia","singapore","thailand","brunei"},
    "singapore":  {"malaysia","indonesia"},
    "brunei":     {"malaysia","indonesia"},
    "png":        {"indonesia","australia"},
    "brazil":     {"argentina","peru","colombia","venezuela","chile"},
    "argentina":  {"chile","brazil"},
    "chile":      {"argentina","peru"},
    "peru":       {"chile","brazil","colombia"},
    "colombia":   {"peru","brazil","venezuela"},
    "venezuela":  {"colombia","brazil"},
    "mexico":     {"usa"},
    "southafrica":{"kenya","ethiopia"},
    "kenya":      {"ethiopia","southafrica"},
    "ethiopia":   {"kenya","egypt","southafrica"},
    "egypt":      {"libya","israel","saudi","ethiopia"},
    "libya":      {"egypt","algeria"},
    "algeria":    {"libya","morocco"},
    "morocco":    {"algeria","spain","portugal"},
    "spain":      {"portugal","france","morocco"},
    "portugal":   {"spain","morocco"},
    "france":     {"spain","italy","germany","belgium","netherlands","switzerland","uk"},
    "germany":    {"france","poland","czech","austria","switzerland","netherlands",
                    "belgium","denmark"},
    "italy":      {"france","switzerland","austria","greece","albania"},
    "poland":     {"germany","czech","slovakia","ukraine","belarus","lithuania","russia"},
    "ukraine":    {"poland","slovakia","hungary","romania","moldova","belarus","russia"},
    "belarus":    {"poland","ukraine","russia","lithuania","latvia"},
    "sweden":     {"norway","finland","denmark"},
    "norway":     {"sweden","finland","russia","denmark","iceland","uk"},
    "finland":    {"sweden","norway","russia","estonia"},
    "denmark":    {"germany","sweden","norway"},
    "turkey":     {"greece","bulgaria","georgia","armenia","iran","syria","iraq"},
    "greece":     {"turkey","bulgaria","albania","italy"},
    "bulgaria":   {"greece","turkey","romania","serbia"},
    "romania":    {"bulgaria","serbia","hungary","ukraine","moldova"},
    "serbia":     {"romania","bulgaria","hungary","croatia","bosnia","albania"},
    "hungary":    {"serbia","romania","croatia","slovakia","austria","ukraine"},
    "croatia":    {"serbia","hungary","bosnia"},
    "bosnia":     {"serbia","croatia","albania"},
    "albania":    {"greece","serbia","bosnia","italy"},
    "austria":    {"germany","italy","switzerland","hungary","czech","slovakia"},
    "switzerland":{"france","germany","italy","austria"},
    "czech":      {"germany","poland","slovakia","austria","hungary"},
    "slovakia":   {"czech","poland","hungary","austria","ukraine"},
    "netherlands":{"germany","belgium","france","uk"},
    "belgium":    {"netherlands","germany","france"},
    "moldova":    {"romania","ukraine"},
    "lithuania":  {"poland","belarus","latvia","russia"},
    "latvia":     {"lithuania","estonia","belarus","russia"},
    "estonia":    {"latvia","russia","finland"},
    "georgia":    {"turkey","armenia","azerbaijan","russia"},
    "armenia":    {"georgia","azerbaijan","turkey","iran"},
    "azerbaijan": {"georgia","armenia","iran","russia"},
    "iran":       {"iraq","turkey","armenia","azerbaijan","turkmenistan",
                    "afghanistan","pakistan"},
    "iraq":       {"iran","turkey","syria","jordan","kuwait","saudi"},
    "syria":      {"turkey","iraq","jordan","israel","lebanon"},
    "lebanon":    {"syria","israel"},
    "israel":     {"lebanon","syria","jordan","egypt"},
    "jordan":     {"israel","syria","iraq","saudi"},
    "kuwait":     {"iraq","saudi"},
    "saudi":      {"iraq","kuwait","jordan","yemen","oman","uae","egypt"},
    "uae":        {"saudi","oman"},
    "oman":       {"saudi","uae","yemen"},
    "yemen":      {"saudi","oman"},
    "afghanistan":{"iran","pakistan","tajikistan","uzbekistan","turkmenistan","china"},
    "pakistan":   {"iran","afghanistan","china","india"},
    "turkmenistan":{"iran","afghanistan","uzbekistan","kazakhstan"},
    "uzbekistan": {"turkmenistan","kazakhstan","tajikistan","kyrgyzstan","afghanistan"},
    "tajikistan": {"uzbekistan","kyrgyzstan","china","afghanistan"},
    "kyrgyzstan": {"uzbekistan","tajikistan","china","kazakhstan"},
    "kazakhstan": {"russia","china","uzbekistan","kyrgyzstan","turkmenistan"},
    "mongolia":   {"russia","china"},
    "nepal":      {"india","china"},
    "bangladesh": {"india","myanmar"},
    "myanmar":    {"india","bangladesh","china","laos","thailand"},
    "thailand":   {"myanmar","laos","cambodia","malaysia"},
    "laos":       {"myanmar","china","vietnam","thailand","cambodia"},
    "vietnam":    {"china","laos","cambodia"},
    "cambodia":   {"thailand","laos","vietnam"},
}

_NEIGHBOR_CACHE = None

def _land_key(s: str):
    s = s.strip().lower()
    aliases = {
        "uk": "uk", "britain": "uk", "england": "uk", "united kingdom": "uk",
        "usa": "usa", "us": "usa", "america": "usa", "united states": "usa",
        "south korea": "skorea", "korea": "skorea",
        "north korea": "nkorea",
        "south africa": "southafrica",
        "new zealand": "newzealand",
        "saudi arabia": "saudi",
        "czechia": "czech", "czech republic": "czech",
        "uae": "uae", "emirates": "uae",
        "papua new guinea": "png",
    }
    if s in aliases:
        return aliases[s]
    if s in LANDS:
        return s
    for k, (name, *_rest) in LANDS.items():
        if name.lower() == s:
            return k
    return None

def _compute_neighbors():
    global _NEIGHBOR_CACHE
    if _NEIGHBOR_CACHE is not None:
        return _NEIGHBOR_CACHE
    neighbors = {k: set() for k in LANDS}
    for a, bs in _EXPLICIT_NEIGHBORS.items():
        for b in bs:
            if b in LANDS:
                neighbors[a].add(b)
                neighbors[b].add(a)
    keys = list(LANDS.keys())
    for a in keys:
        ax, ay = LANDS[a][2], LANDS[a][3]
        for b in keys:
            if a == b:
                continue
            bx, by = LANDS[b][2], LANDS[b][3]
            if math.hypot(ax - bx, ay - by) <= 0.16:
                neighbors[a].add(b)
    sorted_n = {}
    for a, bs in neighbors.items():
        ax, ay = LANDS[a][2], LANDS[a][3]
        sorted_n[a] = sorted(bs, key=lambda b: math.hypot(ax - LANDS[b][2], ay - LANDS[b][3]))
    _NEIGHBOR_CACHE = sorted_n
    return sorted_n

# ---- War state helpers ----

def _war_player(uid):
    p = war_data["players"].get(uid)
    if p is None:
        p = {"country": None, "tutorial": True, "resources_ts": time.time()}
        war_data["players"][uid] = p
        _save_war()
    return p

def _war_country(key):
    return war_data["countries"].get(key)

def _new_country(uid, key):
    neighbors = _compute_neighbors()
    c = war_data["countries"].get(key) or {}
    c.update({
        "owner": uid,
        "members": c.get("members", []) + [uid],
        "soldiers": c.get("soldiers", 10000),
        "bombers":  c.get("bombers", 5),
        "medics":   c.get("medics", 1000),
        "houses":   c.get("houses", 5000),
        "gold":     c.get("gold", 500),
        "food":     c.get("food", 500),
        "neighbors": neighbors.get(key, []),
        "last_tick": time.time(),
    })
    war_data["countries"][key] = c
    return c

def _claim_country(key, uid):
    _new_country(uid, key)
    p = _war_player(uid)
    if not p.get("country"):
        p["country"] = key
    p["tutorial"] = False
    _save_war()

def _countries_owned_by(uid):
    return [k for k, c in war_data["countries"].items() if c.get("owner") == uid]

# ---- Economy tick ----

def _tick_economy(uid):
    now = time.time()
    touched = False
    for key in _countries_owned_by(uid):
        c = war_data["countries"].get(key)
        if not c: continue
        last = c.get("last_tick", now)
        dt = now - last
        if dt < 60:
            continue
        hours = min(dt / 3600.0, 24.0)
        houses = c.get("houses", 0)
        neighbors = len(c.get("neighbors", []))
        c["food"] = c.get("food", 0) + int(houses * 2 * hours)
        c["gold"] = c.get("gold", 0) + int((50 + neighbors * 5) * hours)
        max_soldiers = houses * 10
        cur_sold = c.get("soldiers", 0)
        recruit = int(houses * 0.5 * hours)
        if cur_sold < max_soldiers and c.get("food", 0) > recruit:
            add = min(recruit, max_soldiers - cur_sold)
            c["soldiers"] = cur_sold + add
            c["food"] = max(0, c.get("food", 0) - add)
        c["last_tick"] = now
        touched = True
    if touched:
        _save_war()


# ---- PNG map (real country shapes, Natural Earth 110m, embedded) ----
# Data = zlib+base64 JSON: {"lands": {key: {"c": [x,y], "p": [[[x,y],...],...]}},
#                           "other": [[[x,y],...],...]}   x 0..3600, y 0..1500
# (equirectangular, lon -180..180, lat 85..-65)

_WORLD_B64 = (
    "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"
)

_WORLD_CACHE = None

def _world():
    global _WORLD_CACHE
    if _WORLD_CACHE is None:
        raw = zlib.decompress(base64.b64decode(_WORLD_B64))
        _WORLD_CACHE = json.loads(raw)
    return _WORLD_CACHE

_OWNER_COLORS = [
    (255, 200, 60), (100, 220, 130), (255, 120, 120), (140, 170, 255),
    (255, 160, 220), (200, 255, 160), (255, 220, 150), (180, 140, 255),
    (120, 230, 230), (250, 160, 90), (200, 200, 200), (255, 100, 180),
]

def _owner_color(owner_jid):
    if not owner_jid:
        return None
    h = 0
    for ch in owner_jid:
        h = (h * 131 + ord(ch)) & 0xffffffff
    return _OWNER_COLORS[h % len(_OWNER_COLORS)]


def _load_font(size, bold=False):
    names = (["DejaVuSans-Bold.ttf", "arialbd.ttf", "Arial Bold.ttf"] if bold
             else ["DejaVuSans.ttf", "arial.ttf", "Arial.ttf"])
    for n in names:
        try:
            return ImageFont.truetype(n, size)
        except Exception:
            continue
    try:
        return ImageFont.load_default()
    except Exception:
        return None


def _generate_map_image() -> bytes:
    if not _PIL_OK:
        print("[!] Pillow not installed — cannot render map", flush=True)
        return None
    try:
        world = _world()
        MW, MH = 1800, 750                 # map area (data units * 0.5)
        S = 0.5
        claimed = [(k, c) for k, c in war_data["countries"].items()
                   if c.get("owner") and k in LANDS]
        cols = 3
        rows = max(1, math.ceil(len(claimed) / cols)) if claimed else 0
        LEG_H = 56 + rows * 40 if claimed else 0
        W, H = MW, MH + LEG_H
        img = Image.new("RGB", (W, H), (9, 18, 36))
        d = ImageDraw.Draw(img)

        # ocean grid
        for x in range(0, MW, 100):
            d.line([(x, 0), (x, MH)], fill=(16, 30, 56))
        for y in range(0, MH, 100):
            d.line([(0, y), (MW, y)], fill=(16, 30, 56))

        def pts(poly):
            return [(int(x * S), int(y * S)) for x, y in poly]

        # background countries (not playable)
        for poly in world.get("other", []):
            d.polygon(pts(poly), fill=(40, 52, 68), outline=(62, 76, 96))

        # playable countries
        for k, info in world["lands"].items():
            if k not in LANDS:
                continue
            c = war_data["countries"].get(k)
            owner = c.get("owner") if c else None
            if owner:
                fill = _owner_color(owner) or (255, 200, 60)
                outline = (255, 255, 255)
            else:
                fill = (66, 98, 128)
                outline = (110, 140, 170)
            for poly in info["p"]:
                if len(poly) >= 3:
                    d.polygon(pts(poly), fill=fill, outline=outline)
            if not info["p"]:      # tiny countries without a shape -> dot
                cx, cy = info["c"]
                r = 7 if owner else 4
                d.ellipse([cx*S - r, cy*S - r, cx*S + r, cy*S + r],
                          fill=fill, outline=outline, width=2)

        f_big = _load_font(26, True)
        f_med = _load_font(16)
        f_sm = _load_font(12)

        # labels for claimed countries
        for k, c in claimed:
            info = world["lands"].get(k)
            if not info:
                continue
            cx, cy = info["c"]
            name = LANDS[k][0]
            try:
                d.text((cx * S, cy * S), name, fill=(255, 255, 255), font=f_sm,
                       anchor="mm", stroke_width=2, stroke_fill=(0, 0, 0))
            except Exception:
                d.text((cx * S - 20, cy * S - 6), name, fill=(255, 255, 255), font=f_sm)

        d.text((14, 10), "WORLD CONQUEST", fill=(230, 238, 255), font=f_big,
               stroke_width=2, stroke_fill=(0, 0, 0))
        d.text((16, 44), f"{len(claimed)} lands claimed  |  blue = free, colored = owned",
               fill=(170, 195, 225), font=f_med, stroke_width=1, stroke_fill=(0, 0, 0))

        # legend
        if claimed:
            d.rectangle([0, MH, W, H], fill=(14, 24, 44))
            d.line([(0, MH), (W, MH)], fill=(60, 80, 110), width=2)
            d.text((16, MH + 14), "claimed countries", fill=(230, 238, 255), font=f_med)
            colw = W // cols
            for i, (k, c) in enumerate(claimed):
                col, row = i % cols, i // cols
                x = 16 + col * colw
                y = MH + 46 + row * 40
                owner = c.get("owner", "")
                oname = user_names.get(owner) or _phone_of_jid(owner)
                color = _owner_color(owner) or (255, 220, 120)
                d.rectangle([x, y + 4, x + 18, y + 22], fill=color, outline=(255, 255, 255))
                d.text((x + 28, y), LANDS[k][0], fill=color, font=f_med)
                d.text((x + 28, y + 19), "-> " + str(oname)[:34], fill=(180, 200, 220), font=f_sm)

        buf = io.BytesIO()
        img.save(buf, format="PNG", optimize=True)
        return buf.getvalue()
    except Exception as e:
        print("[!] map render failed:", e, flush=True)
        return None


# ---- War text ----

def _war_status_text(uid):
    _tick_economy(uid)
    owned = _countries_owned_by(uid)
    if not owned:
        return ("🌍 *WORLD CONQUEST*\n"
                "you don't own any land yet.\n"
                "claim one: `!war claim <land>`\n"
                "example: `!war claim Russia`\n"
                "you'll get 10k🪖 5✈️ 1k⛑ 5k🏠 500💰 500🌾")
    lines = [f"🏛 *YOUR EMPIRE* — {len(owned)} countr{'y' if len(owned)==1 else 'ies'}"]
    tot_sold = tot_bomb = tot_med = tot_house = tot_gold = tot_food = 0
    for key in owned:
        c = war_data["countries"][key]
        name, flag, *_ = LANDS[key]
        members = c.get("members", [])
        lines.append(f"\n{flag} *{name}*")
        lines.append(f"  👥 members: {len(members)}")
        for m in members[:6]:
            mn = user_names.get(m) or _phone_of_jid(m)
            lines.append(f"    • {mn}")
        lines.append(f"  🪖 {c.get('soldiers',0)}  ✈️ {c.get('bombers',0)}  "
                     f"⛑ {c.get('medics',0)}  🏠 {c.get('houses',0)}")
        lines.append(f"  💰 {c.get('gold',0)}  🌾 {c.get('food',0)}")
        tot_sold += c.get('soldiers',0)
        tot_bomb += c.get('bombers',0)
        tot_med  += c.get('medics',0)
        tot_house+= c.get('houses',0)
        tot_gold += c.get('gold',0)
        tot_food += c.get('food',0)
    lines.append("")
    lines.append(f"*totals*: 🪖 {tot_sold}  ✈️ {tot_bomb}  ⛑ {tot_med}  "
                 f"🏠 {tot_house}  💰 {tot_gold}  🌾 {tot_food}")
    lines.append("")
    lines.append("`!war claim <land>` — expand / join as member")
    lines.append("`!war attack <land>` — attack a neighbor")
    lines.append("`!war leave <land>` — release a land back to public")
    lines.append("`!war eco` — show economy details")
    lines.append("`!war map` — show map")
    return "\n".join(lines)


_sender_uid_current = [""]

def _format_map_caption():
    uid = _sender_uid_current[0]
    if uid:
        _tick_economy(uid)
    claimed = [(k, c) for k, c in war_data["countries"].items() if c.get("owner")]
    if not claimed:
        return "🌍 *WORLD CONQUEST* — no lands claimed yet"
    by_owner = {}
    for k, c in claimed:
        by_owner.setdefault(c["owner"], []).append(k)
    lines = [f"🌍 *WORLD CONQUEST* — {len(claimed)} lands claimed"]
    for owner, keys in by_owner.items():
        on = user_names.get(owner) or _phone_of_jid(owner)
        flags = " ".join(LANDS[k][1] for k in keys[:8])
        lines.append(f"• {on}: {flags} ({len(keys)})")
    return "\n".join(lines)


def _war_menu(uid):
    _sender_uid_current[0] = uid
    _tick_economy(uid)
    p = _war_player(uid)
    owned = _countries_owned_by(uid)

    lines = ["⚔️ *WORLD CONQUEST* ⚔️", ""]

    if owned:
        lines.append(f"🏛 you own *{len(owned)}* countr{'y' if len(owned)==1 else 'ies'}:")
        for key in owned:
            c = war_data["countries"][key]
            name, flag, *_ = LANDS[key]
            lines.append(f"  {flag} {name}  🪖{c.get('soldiers',0)} "
                         f"💰{c.get('gold',0)} 🌾{c.get('food',0)}")
    else:
        lines.append("you don't own a land yet.")
        lines.append("start: `!war claim <land>`  (e.g. `!war claim Russia`)")
        lines.append("starting army: 10k🪖 5✈️ 1k⛑ 5k🏠 500💰 500🌾")

    lines.append("")
    lines.append("*commands*")
    lines.append("`!war` — this menu + map")
    lines.append("`!war claim <land>` — claim a free land / join as clan member")
    lines.append("`!war attack <land>` — attack an enemy neighbor")
    lines.append("`!war leave <land>` — release one of your lands")
    lines.append("`!war status` — your empire + resources")
    lines.append("`!war eco` — economy details (income rates)")
    lines.append("`!war upgrade` — guided build (asks which land, what, how many)")
    lines.append("`!war send` — guided move of soldiers between your lands")
    lines.append("`!war map` — re-send the world map")

    text = "\n".join(lines)
    img = _generate_map_image()
    if img:
        return {"image": img, "caption": text}
    return text


def _war_eco(uid):
    _tick_economy(uid)
    owned = _countries_owned_by(uid)
    if not owned:
        return "you don't own any land yet — `!war claim <land>`"
    lines = ["💹 *ECONOMY* — per-hour income"]
    for key in owned:
        c = war_data["countries"][key]
        name, flag, *_ = LANDS[key]
        houses = c.get("houses", 0)
        nbrs = len(c.get("neighbors", []))
        food_inc = houses * 2
        gold_inc = 50 + nbrs * 5
        pop_cap = houses * 10
        recruit = int(houses * 0.5)
        lines.append(f"{flag} *{name}*")
        lines.append(f"  💰 +{gold_inc}/h   🌾 +{food_inc}/h")
        lines.append(f"  🪖 recruit +{recruit}/h (cap {pop_cap})")
    lines.append("")
    lines.append("buy more houses to raise pop cap and food.")
    lines.append("`!war upgrade` — guided build")
    return "\n".join(lines)

# ---- Upgrades ----

_UPGRADE_COST = {
    "houses":   {"gold": 20, "food": 0},
    "soldiers": {"gold": 1,  "food": 1},
    "bombers":  {"gold": 200,"food": 0},
    "medics":   {"gold": 5,  "food": 2},
}

# ---- Combat ----

def _combat_round(attacker, defender):
    a_sold = attacker.get("soldiers", 0)
    a_bomb = attacker.get("bombers", 0)
    d_sold = defender.get("soldiers", 0)
    d_bomb = defender.get("bombers", 0)
    a_power = a_sold * (1 + 0.08 * a_bomb) * random.uniform(0.85, 1.15)
    d_power = d_sold * (1 + 0.08 * d_bomb) * random.uniform(0.85, 1.15)
    if a_power > d_power:
        ratio = d_power / max(1, a_power)
        a_losses = int(a_sold * ratio * 0.6)
        d_losses = int(d_sold * (1 - ratio * 0.5) * 0.9)
        winner = "attacker"
    else:
        ratio = a_power / max(1, d_power)
        a_losses = int(a_sold * (1 - ratio * 0.5) * 0.9)
        d_losses = int(d_sold * ratio * 0.6)
        winner = "defender"
    a_losses = max(1, a_losses)
    d_losses = max(1, d_losses)
    return a_losses, d_losses, winner


def _resolve_attack(attacker_uid, defender_key):
    owned = _countries_owned_by(attacker_uid)
    if not owned:
        return "you don't own a land yet — claim one first"
    staging = max(owned, key=lambda k: war_data["countries"][k].get("soldiers", 0))
    atk = war_data["countries"].get(staging)
    dfd = war_data["countries"].get(defender_key)
    if not atk or not dfd:
        return "invalid attack target"
    if not dfd.get("owner"):
        return f"{LANDS[defender_key][1]} {LANDS[defender_key][0]} is unclaimed — use `claim` instead"
    if dfd["owner"] == attacker_uid:
        return "you can't attack your own land"
    nbrs = _compute_neighbors()
    adjacent = any(defender_key in nbrs.get(k, []) for k in owned)
    if not adjacent:
        return (f"*{LANDS[defender_key][1]} {LANDS[defender_key][0]}* is not adjacent "
                f"to any of your lands")

    defender_uid = dfd["owner"]
    atk_name = LANDS[staging][0]
    dfd_name = LANDS[defender_key][0]

    frames = [f"⚔️ *WAR*: {LANDS[staging][1]} {atk_name} → "
              f"{LANDS[defender_key][1]} {dfd_name}\npreparing armies…"]

    atk_sold = atk.get("soldiers", 0)
    dfd_sold = dfd.get("soldiers", 0)
    atk_bomb = atk.get("bombers", 0)
    dfd_bomb = dfd.get("bombers", 0)
    atk_med = atk.get("medics", 0)
    dfd_med = dfd.get("medics", 0)

    total_a_loss = 0
    total_d_loss = 0
    winner = None

    for r in range(1, 6):
        if atk_sold <= 0 or dfd_sold <= 0:
            break
        a_l, d_l, w = _combat_round(
            {"soldiers": atk_sold, "bombers": atk_bomb},
            {"soldiers": dfd_sold, "bombers": dfd_bomb},
        )
        a_l = max(0, a_l - int(atk_med * 0.3))
        d_l = max(0, d_l - int(dfd_med * 0.3))
        atk_sold -= a_l
        dfd_sold -= d_l
        total_a_loss += a_l
        total_d_loss += d_l
        frames.append(
            f"⚔️ round {r}\n"
            f"{LANDS[staging][1]} {atk_name}: -{a_l}🪖 ({atk_sold} left)\n"
            f"{LANDS[defender_key][1]} {dfd_name}: -{d_l}🪖 ({dfd_sold} left)"
        )
        if atk_sold <= 0:
            winner = "defender"; break
        if dfd_sold <= 0:
            winner = "attacker"; break

    if winner is None:
        winner = "attacker" if atk_sold > dfd_sold else ("defender" if dfd_sold > atk_sold else "draw")

    atk["soldiers"] = max(0, atk_sold)
    dfd["soldiers"] = max(0, dfd_sold)

    if winner == "attacker":
        spoils_sold  = max(0, int(dfd.get("soldiers", 0) * 0.5))
        spoils_bomb  = max(0, int(dfd.get("bombers", 0) * 0.5))
        spoils_med   = max(0, int(dfd.get("medics", 0) * 0.5))
        spoils_house = max(0, int(dfd.get("houses", 0) * 0.5))
        spoils_gold  = max(0, int(dfd.get("gold", 0) * 0.5))
        spoils_food  = max(0, int(dfd.get("food", 0) * 0.5))
        atk["soldiers"] += spoils_sold
        atk["bombers"]  += spoils_bomb
        atk["medics"]   += spoils_med
        atk["houses"]   += spoils_house
        atk["gold"]     = atk.get("gold", 0) + spoils_gold
        atk["food"]     = atk.get("food", 0) + spoils_food
        dfd["owner"] = attacker_uid
        dfd["members"] = list(set(dfd.get("members", []) + [attacker_uid]))
        dfd["soldiers"] = 0
        dfd["bombers"]  = 0
        dfd["medics"]   = 0
        dfd["houses"]   = 0
        dfd["gold"]     = 0
        dfd["food"]     = 0
        dfd["neighbors"] = _compute_neighbors().get(defender_key, [])
        frames.append(
            f"🏆 *{LANDS[staging][1]} {atk_name}* conquered "
            f"*{LANDS[defender_key][1]} {dfd_name}*!\n"
            f"spoils: +{spoils_sold}🪖 +{spoils_bomb}✈️ +{spoils_med}⛑ "
            f"+{spoils_house}🏠 +{spoils_gold}💰 +{spoils_food}🌾"
        )
        try:
            _notify_user(defender_uid,
                         f"⚠️ *WAR ALERT*\n"
                         f"{LANDS[staging][1]} {atk_name} "
                         f"({user_names.get(attacker_uid) or _phone_of_jid(attacker_uid)}) "
                         f"has conquered your land *{LANDS[defender_key][1]} {dfd_name}*!")
        except Exception:
            pass
    elif winner == "defender":
        frames.append(
            f"🛡 *{LANDS[defender_key][1]} {dfd_name}* held the line!\n"
            f"attacker losses: {total_a_loss}🪖  defender losses: {total_d_loss}🪖"
        )
        try:
            _notify_user(defender_uid,
                         f"⚠️ *WAR ALERT*\n"
                         f"{LANDS[staging][1]} {atk_name} "
                         f"({user_names.get(attacker_uid) or _phone_of_jid(attacker_uid)}) "
                         f"attacked *{LANDS[defender_key][1]} {dfd_name}* — you repelled them!")
        except Exception:
            pass
    else:
        frames.append(
            f"🤝 *draw* — both armies exhausted\n"
            f"attacker losses: {total_a_loss}🪖  defender losses: {total_d_loss}🪖"
        )
        try:
            _notify_user(defender_uid,
                         f"⚠️ *WAR ALERT*\n"
                         f"{LANDS[staging][1]} {atk_name} "
                         f"({user_names.get(attacker_uid) or _phone_of_jid(attacker_uid)}) "
                         f"attacked *{LANDS[defender_key][1]} {dfd_name}* — it was a draw.")
        except Exception:
            pass

    war_data["countries"][staging] = atk
    war_data["countries"][defender_key] = dfd
    _save_war()
    return frames


def _war_leave(uid, args):
    if len(args) < 2:
        return "usage: `!war leave <land>`"
    key = _land_key(" ".join(args[1:]))
    if not key:
        return "unknown land"
    c = war_data["countries"].get(key)
    if not c or c.get("owner") != uid:
        return f"you don't own *{LANDS[key][1]} {LANDS[key][0]}*"
    remaining = [m for m in c.get("members", []) if m != uid]
    if remaining:
        new_owner = remaining[0]
        c["owner"] = new_owner
        c["members"] = remaining
        try:
            _notify_user(new_owner,
                         f"👑 you are now the owner of *{LANDS[key][1]} {LANDS[key][0]}* "
                         f"(previous owner left)")
        except Exception:
            pass
        _save_war()
        return (f"🚪 you left *{LANDS[key][1]} {LANDS[key][0]}*.\n"
                f"ownership passed to {user_names.get(new_owner) or _phone_of_jid(new_owner)}")
    war_data["countries"].pop(key, None)
    p = _war_player(uid)
    if p.get("country") == key:
        p["country"] = None
    _save_war()
    return f"🕊 you released *{LANDS[key][1]} {LANDS[key][0]}* back to the public"


def _notify_user(uid, text):
    try:
        chat_jid = uid if uid.endswith("@s.whatsapp.net") else f"{_digits(uid)}@s.whatsapp.net"
        client.send_message(chat_jid, text)
    except Exception as e:
        print("[!] notify failed:", e, flush=True)


# ---------- Interactive war prompts ----------

_PENDING = {}

def _pending_start(uid, kind, step, data=None):
    _PENDING[uid] = {"kind": kind, "step": step, "data": data or {}}

def _pending_clear(uid):
    _PENDING.pop(uid, None)

def _pending_get(uid):
    return _PENDING.get(uid)

def _start_send_flow(uid, args):
    owned = _countries_owned_by(uid)
    if not owned:
        return "you don't own any land yet — `!war claim <land>`", None

    # legacy one-shot:  !war send <from> <to> <amount>
    if len(args) >= 4:
        amt_s = args[-1]
        if amt_s.isdigit() and int(amt_s) > 0:
            amt = int(amt_s)
            mid = args[1:-1]
            for split in range(1, len(mid)):
                a = _land_key(" ".join(mid[:split]))
                b = _land_key(" ".join(mid[split:]))
                if a and b and a != b:
                    ca = war_data["countries"].get(a)
                    cb = war_data["countries"].get(b)
                    if not ca or ca.get("owner") != uid:
                        return f"you don't own *{LANDS[a][1]} {LANDS[a][0]}*", None
                    if not cb or cb.get("owner") != uid:
                        return f"you don't own *{LANDS[b][1]} {LANDS[b][0]}*", None
                    if ca.get("soldiers", 0) < amt:
                        return f"not enough soldiers in {LANDS[a][1]} {LANDS[a][0]}", None
                    ca["soldiers"] = ca.get("soldiers", 0) - amt
                    cb["soldiers"] = cb.get("soldiers", 0) + amt
                    _save_war()
                    return (f"🚚 moved {amt}🪖 from {LANDS[a][1]} {LANDS[a][0]} "
                            f"to {LANDS[b][1]} {LANDS[b][0]}"), None

    _pending_start(uid, "send", "from", {"owned": owned})
    lines = ["🚚 *MOVE SOLDIERS*", "", "where do you want to send *FROM*?", ""]
    for k in owned:
        c = war_data["countries"][k]
        name, flag, *_ = LANDS[k]
        lines.append(f"{flag} {name} — 🪖 {c.get('soldiers',0)}")
    lines.append("")
    lines.append("_reply with the country name, or `cancel`_")
    return "\n".join(lines), None


def _start_upgrade_flow(uid, args):
    owned = _countries_owned_by(uid)
    if not owned:
        return "you don't own any land yet — `!war claim <land>`", None
    _pending_start(uid, "upgrade", "which", {"owned": owned})
    lines = ["🔨 *UPGRADE*", "", "which land do you want to upgrade?", ""]
    for k in owned:
        c = war_data["countries"][k]
        name, flag, *_ = LANDS[k]
        lines.append(f"{flag} {name}  💰 {c.get('gold',0)}  🌾 {c.get('food',0)}")
    lines.append("")
    lines.append("_reply with the country name, or `cancel`_")
    return "\n".join(lines), None


def _handle_pending(uid, text):
    """If the user has a pending prompt, consume this message as input.
    Returns a reply string/tuple, or None if no pending."""
    p = _PENDING.get(uid)
    if not p:
        return None
    t = text.strip()
    low = t.lower()
    if low in ("cancel", "stop", "abort", "exit", "x"):
        _pending_clear(uid)
        return "❌ cancelled"

    kind = p["kind"]; step = p["step"]; data = p["data"]

    if kind == "send":
        if step == "from":
            key = _land_key(t)
            if not key or key not in data["owned"]:
                return ("that's not one of your countries — try again, "
                        "or reply `cancel`")
            data["from"] = key
            p["step"] = "to"
            targets = [k for k in data["owned"] if k != key]
            if not targets:
                _pending_clear(uid)
                return "you only own one country — nothing to move to"
            data["targets"] = targets
            lines = [f"✅ from: {LANDS[key][1]} {LANDS[key][0]}", "",
                     "which land do you want to send *TO*?", ""]
            for k in targets:
                lines.append(f"{LANDS[k][1]} {LANDS[k][0]}")
            lines.append("_reply with the country name, or `cancel`_")
            return "\n".join(lines)
        if step == "to":
            key = _land_key(t)
            if not key or key not in data.get("targets", []):
                return ("that's not one of your other countries — try again, "
                        "or reply `cancel`")
            data["to"] = key
            p["step"] = "amount"
            c = war_data["countries"][data["from"]]
            return (f"✅ to: {LANDS[key][1]} {LANDS[key][0]}\n\n"
                    f"how many soldiers? (you have {c.get('soldiers',0)}🪖 in "
                    f"{LANDS[data['from']][1]} {LANDS[data['from']][0]})")
        if step == "amount":
            if not t.isdigit() or int(t) <= 0:
                return "amount must be a positive integer — try again, or `cancel`"
            amt = int(t)
            c_from = war_data["countries"][data["from"]]
            if amt > c_from.get("soldiers", 0):
                return (f"you only have {c_from.get('soldiers',0)}🪖 in "
                        f"{LANDS[data['from']][1]} {LANDS[data['from']][0]} — try again")
            data["amount"] = amt
            p["step"] = "confirm"
            return (f"📋 *confirm move*\n\n"
                    f"from: {LANDS[data['from']][1]} {LANDS[data['from']][0]}\n"
                    f"to:   {LANDS[data['to']][1]} {LANDS[data['to']][0]}\n"
                    f"amt:  {amt}🪖\n\n"
                    f"reply *yes* to confirm or *no* / `cancel` to abort")
        if step == "confirm":
            if low in ("yes", "y", "ok", "confirm", "yep"):
                c_from = war_data["countries"][data["from"]]
                c_to = war_data["countries"][data["to"]]
                amt = data["amount"]
                c_from["soldiers"] = c_from.get("soldiers", 0) - amt
                c_to["soldiers"] = c_to.get("soldiers", 0) + amt
                _save_war()
                _pending_clear(uid)
                return (f"🚚 moved {amt}🪖 from "
                        f"{LANDS[data['from']][1]} {LANDS[data['from']][0]} → "
                        f"{LANDS[data['to']][1]} {LANDS[data['to']][0]}")
            if low in ("no", "n", "nope", "cancel"):
                _pending_clear(uid)
                return "❌ cancelled"
            return "reply *yes* to confirm or *no* to cancel"

    if kind == "upgrade":
        if step == "which":
            key = _land_key(t)
            if not key or key not in data["owned"]:
                return ("that's not one of your countries — try again, "
                        "or reply `cancel`")
            data["land"] = key
            p["step"] = "what"
            c = war_data["countries"][key]
            return (f"✅ land: {LANDS[key][1]} {LANDS[key][0]}\n"
                    f"💰 {c.get('gold',0)}   🌾 {c.get('food',0)}\n\n"
                    f"what do you want to build?\n"
                    f"• *houses* (20💰 each, +2🌾/h, +10 pop)\n"
                    f"• *soldiers* (1💰 +1🌾 each)\n"
                    f"• *bombers* (200💰 each)\n"
                    f"• *medics* (5💰 +2🌾 each)\n\n"
                    f"_reply with one of those, or `cancel`_")
        if step == "what":
            if low not in _UPGRADE_COST:
                return "choose: houses, soldiers, bombers, medics — or `cancel`"
            data["what"] = low
            p["step"] = "amount"
            cost = _UPGRADE_COST[low]
            return (f"✅ building: *{low}*\n"
                    f"cost per unit: {cost['gold']}💰 + {cost['food']}🌾\n\n"
                    f"how many? (reply a number or `cancel`)")
        if step == "amount":
            if not t.isdigit() or int(t) <= 0:
                return "amount must be a positive integer — try again, or `cancel`"
            amt = int(t)
            data["amount"] = amt
            p["step"] = "confirm"
            cost = {k: v * amt for k, v in _UPGRADE_COST[data["what"]].items()}
            c = war_data["countries"][data["land"]]
            return (f"📋 *confirm build*\n\n"
                    f"land: {LANDS[data['land']][1]} {LANDS[data['land']][0]}\n"
                    f"build: +{amt} {data['what']}\n"
                    f"cost: -{cost['gold']}💰 -{cost['food']}🌾\n\n"
                    f"you have: 💰 {c.get('gold',0)}  🌾 {c.get('food',0)}\n\n"
                    f"reply *yes* to confirm or *no* / `cancel` to abort")
        if step == "confirm":
            if low in ("yes", "y", "ok", "confirm", "yep"):
                c = war_data["countries"][data["land"]]
                amt = data["amount"]
                what = data["what"]
                cost = {k: v * amt for k, v in _UPGRADE_COST[what].items()}
                if c.get("gold", 0) < cost["gold"]:
                    _pending_clear(uid)
                    return f"not enough gold (need {cost['gold']}💰 have {c.get('gold',0)}💰)"
                if c.get("food", 0) < cost["food"]:
                    _pending_clear(uid)
                    return f"not enough food (need {cost['food']}🌾 have {c.get('food',0)}🌾)"
                c["gold"] -= cost["gold"]
                c["food"] -= cost["food"]
                c[what] = c.get(what, 0) + amt
                _save_war()
                _pending_clear(uid)
                return (f"✅ *{LANDS[data['land']][1]} {LANDS[data['land']][0]}* "
                        f"upgraded: +{amt} {what}\n"
                        f"cost: -{cost['gold']}💰 -{cost['food']}🌾")
            if low in ("no", "n", "nope", "cancel"):
                _pending_clear(uid)
                return "❌ cancelled"
            return "reply *yes* to confirm or *no* to cancel"
    return None


def cmd_war(uid, args, reply_jid):
    if not args:
        return _war_menu(uid), None
    sub = args[0].lower()

    if sub in ("help", "?", "menu"):
        return _war_menu(uid), None

    if sub == "map":
        _sender_uid_current[0] = uid
        img = _generate_map_image()
        cap = _format_map_caption()
        if img:
            return {"image": img, "caption": cap}, None
        return cap, None

    if sub in ("status", "me"):
        return _war_status_text(uid), None

    if sub == "eco":
        return _war_eco(uid), None

    if sub == "claim":
        if len(args) < 2:
            return "usage: `!war claim <land>`\nexample: `!war claim Russia`", None
        key = _land_key(" ".join(args[1:]))
        if not key:
            return f"unknown land `{' '.join(args[1:])}`", None
        p = _war_player(uid)
        existing = war_data["countries"].get(key)
        if existing and existing.get("owner"):
            if existing["owner"] != uid:
                existing.setdefault("members", [])
                if uid not in existing["members"]:
                    existing["members"].append(uid)
                war_data["countries"][key] = existing
                if not p.get("country"):
                    p["country"] = key
                p["tutorial"] = False
                _save_war()
                owner = existing["owner"]
                owner_name = user_names.get(owner) or _phone_of_jid(owner)
                return (f"🤝 *{LANDS[key][1]} {LANDS[key][0]}* is owned by {owner_name}.\n"
                        f"you joined as a clan member!"), None
            return f"you already own *{LANDS[key][1]} {LANDS[key][0]}*", None

        owned = _countries_owned_by(uid)
        if owned:
            nbrs = _compute_neighbors()
            if not any(key in nbrs.get(k, []) for k in owned):
                sample = owned[0]
                sample_nbrs = nbrs.get(sample, [])[:6]
                return (f"*{LANDS[key][1]} {LANDS[key][0]}* isn't adjacent to any of your lands.\n"
                        f"try: {', '.join(LANDS[n][1]+' '+LANDS[n][0] for n in sample_nbrs)}"), None

        _claim_country(key, uid)
        img = _generate_map_image()
        text = (f"🎉 *{LANDS[key][1]} {LANDS[key][0]}* is now yours!\n"
                f"🪖 10,000 soldiers\n"
                f"✈️ 5 bombers\n"
                f"⛑ 1,000 medics\n"
                f"🏠 5,000 houses\n"
                f"💰 500 gold   🌾 500 food\n"
                f"\nexpand: `!war claim <neighbor>`\n"
                f"attack: `!war attack <enemy>`")
        if img:
            return {"image": img, "caption": text}, None
        return text, None

    if sub == "attack":
        if len(args) < 2:
            return "usage: `!war attack <land>`", None
        key = _land_key(" ".join(args[1:]))
        if not key:
            return f"unknown land `{' '.join(args[1:])}`", None
        frames = _resolve_attack(uid, key)
        if isinstance(frames, str):
            return frames, None
        return frames, None

    if sub == "leave":
        return _war_leave(uid, args), None

    if sub == "send":
        return _start_send_flow(uid, args)

    if sub == "upgrade":
        return _start_upgrade_flow(uid, args)

    return f"unknown war subcommand `{sub}` — try `!war` for the menu", None


# ========== GAMES registry ==========

GAMES = {
    "coinflip":  (game_coinflip,  "!gamble coinflip <bet> [h|t]"),
    "dice":      (game_dice,      "!gamble dice <bet> [target 1-6]"),
    "slots":     (game_slots,     "!gamble slots <bet>"),
    "clover":    (game_clover,    "!gamble clover <bet>"),
    "roulette":  (game_roulette,  "!gamble roulette <bet> <red|black|green|odd|even|low|high|d1|d2|d3|c1|c2|c3|0-36>"),
    "blackjack": (game_blackjack, "!gamble blackjack <bet>"),
    "bj21":      (game_bj21,      "!gamble bj21 <bet>"),
    "highlow":   (game_highlow,   "!gamble highlow <bet> <h|l>"),
    "crash":     (game_crash,     "!gamble crash <bet> <target_mult>"),
    "mine":      (game_mine,      "!gamble mine <bet> [mines]"),
    "plinko":    (game_plinko,    "!gamble plinko <bet>"),
    "limbo":     (game_limbo,     "!gamble limbo <bet> <target_mult>"),
    "tower":     (game_tower,     "!gamble tower <bet> <floors>"),
    "keno":      (game_keno,      "!gamble keno <bet> <picks>"),
    "guess":     (game_guess,     "!gamble guess <bet> [1-100]"),
    "warcards":  (game_warcards,  "!gamble warcards <bet>"),
    "penalty":   (game_penalty,   "!gamble penalty <bet>"),
    "rps":       (game_rps,       "!gamble rps <bet> <r|p|s>"),
    "lucky":     (game_lucky,     "!gamble lucky <bet>"),
    "rocket":    (game_rocket,    "!gamble rocket <bet>"),
    "ladder":    (game_ladder,    "!gamble ladder <bet> <rungs>"),
    "bomb":      (game_bomb,      "!gamble bomb <bet>"),
    "streaker":  (game_streaker,  "!gamble streaker <bet>"),
    "flip2":     (game_flip2,     "!gamble flip2 <bet>"),
}

# ========== PAYMENTS ==========

def record_payment(to_uid, from_uid, from_name, amount):
    payments.setdefault(to_uid, []).append({
        "from": from_uid, "from_name": from_name or "",
        "amount": int(amount), "ts": time.time(),
    })
    _save_payments()

def format_payments(uid):
    entries = payments.pop(uid, None)
    if not entries:
        return []
    _save_payments()
    lines = []
    for p in entries[-5:]:
        sender = p.get("from", "?")
        name = p.get("from_name") or user_names.get(sender) or ""
        amount = p.get("amount", 0)
        phone = _phone_of_jid(sender)
        who = f"{name} ({phone})" if name else phone
        lines.append(f"💸 you got paid {amount} {CURRENCY} by {who}")
    return lines

# ========== COMMANDS ==========

def cmd_balance(uid):
    lines = [f"💰 {get_balance(uid)} {CURRENCY}"]
    notifs = format_payments(uid)
    if notifs:
        lines.append("")
        lines.extend(notifs)
    return "\n".join(lines)

def cmd_top():
    if not balances:
        return "no players yet"
    top = sorted(balances.items(), key=lambda x: -x[1])[:10]
    lines = ["🏆 *LEADERBOARD*"]
    for i, (u, b) in enumerate(top, 1):
        medal = ["🥇","🥈","🥉"][i-1] if i <= 3 else f"{i}."
        phone = _phone_of_jid(u)
        name = user_names.get(u) or ""
        who = f"{name} ({phone})" if name else phone
        lines.append(f"{medal} {who}: {b} {CURRENCY}")
    return "\n".join(lines)

_last_daily = {}

def cmd_daily(uid):
    now = time.time()
    last = _last_daily.get(uid, 0)
    if now - last < 86400:
        rem = int(86400 - (now - last))
        h, m = divmod(rem // 60, 60)
        return f"⏳ come back in {h}h {m}m"
    _last_daily[uid] = now
    add_balance(uid, 20)
    return f"🎁 daily +20 {CURRENCY}\nbalance: {get_balance(uid)} {CURRENCY}"

def cmd_setcoins(info, args):
    if not _is_admin(info):
        return "🚫 admin only"
    if len(args) < 2:
        return "usage: !gamble setcoins <phone_number> <amount>"
    phone, amount_s = args[0], args[1]
    try: amount = int(amount_s)
    except ValueError: return "amount must be an integer"
    if amount < 0: return "amount cannot be negative"
    target_jid, err = _find_user_by_phone(phone)
    if err: target_jid = _best_effort_jid(phone)
    set_balance(target_jid, amount)
    name = user_names.get(target_jid) or ""
    disp = f"{name} ({_phone_of_jid(target_jid)})" if name else _phone_of_jid(target_jid)
    return f"✅ set {disp}'s balance to {amount} {CURRENCY}"

def cmd_pay(info, uid, args):
    if len(args) < 2:
        return "usage: !pay <phone_number> <amount>"
    phone, amount_s = args[0], args[1]
    try: amount = int(amount_s)
    except ValueError: return "amount must be a positive integer"
    if amount <= 0: return "amount must be a positive integer"
    target_jid, err = _find_user_by_phone(phone)
    if err:
        return f"❌ {err} — they must message the bot once before you can pay them"
    if target_jid == uid:
        return "you can't pay yourself 🙃"
    if amount > get_balance(uid):
        return f"you only have {get_balance(uid)} {CURRENCY}"
    add_balance(uid, -amount)
    add_balance(target_jid, amount)
    sender_name = user_names.get(uid) or _push_name(info) or ""
    record_payment(target_jid, uid, sender_name, amount)
    target_name = user_names.get(target_jid) or ""
    target_phone = _phone_of_jid(target_jid)
    who = f"{target_name} ({target_phone})" if target_name else target_phone
    return (f"✅ paid {amount} {CURRENCY} to {who}\n"
            f"your balance: {get_balance(uid)} {CURRENCY}")

# ========== HELP ==========

HELP_TEXT = ("🎰 *LIRA CASINO* 🎰\n"
             "currency: " + CURRENCY + "\n"
             "start: " + str(START_AMOUNT) + " " + CURRENCY + "\n\n"
             "!balance — show your balance\n"
             "!top — leaderboard\n"
             "!daily — claim +20 " + CURRENCY + "\n"
             "!pay <phone> <amount> — send coins\n"
             "!gamble <game> <args>\n"
             "!gamble list — list games\n"
             "!war — world conquest (map + commands)\n\n"
             "admin (only via !gamble):\n"
             "!gamble setcoins <phone> <amount>\n\n"
             "games:\n" +
             "\n".join(f"• {k}: {v[1]}" for k, v in GAMES.items()))

# ========== DISPATCH ==========

def handle_command(info, text, reply_jid):
    uid = _sender_id(info)
    parts = text.strip().split()
    if not parts or not parts[0].startswith(PREFIX):
        return None
    cmd = parts[0][len(PREFIX):].lower()
    args = parts[1:]
    get_balance(uid)

    if cmd in ("balance", "bal"): return cmd_balance(uid)
    if cmd == "top": return cmd_top()
    if cmd == "daily": return cmd_daily(uid)
    if cmd == "pay": return cmd_pay(info, uid, args)

    if cmd == "war":
        try:
            result, _err = cmd_war(uid, args, reply_jid)
        except Exception as e:
            return f"⚠️ error in war: {e}"
        return result

    if cmd == "gamble":
        if not args or args[0] in ("list", "help"):
            return HELP_TEXT
        game_name = args[0].lower()
        if game_name == "setcoins":
            return cmd_setcoins(info, args[1:])
        if game_name not in GAMES:
            return f"unknown game `{game_name}`\nuse !gamble list"
        fn, _usage = GAMES[game_name]
        try:
            result, _err = fn(uid, args[1:])
        except Exception as e:
            return f"⚠️ error in {game_name}: {e}"
        return result

    if cmd == "setcoins":
        return "🚫 that command only works via `!gamble setcoins <phone> <amount>`"

    return None

# ========== EVENTS ==========

@client.event(ConnectedEv)
def _on_connected(_c, _e):
    print("[+] casino bot linked", flush=True)

@client.event(MessageEv)
def _on_message(_c, ev):
    info = ev.Info
    msg  = ev.Message
    text = _get_text(msg)
    if not text:
        return

    # remember display name
    try:
        uid = _sender_id(info)
        name = _push_name(info)
        if name and user_names.get(uid) != name:
            user_names[uid] = name
            _save_names()
    except Exception:
        pass

    # --- interactive war prompt interception ---
    try:
        uid_for_pending = _sender_id(info)
    except Exception:
        uid_for_pending = None
    if uid_for_pending:
        pend_reply = _handle_pending(uid_for_pending, text)
        if pend_reply is not None:
            reply_jid = _reply_jid(info)
            threading.Thread(
                target=_react, args=(info, reply_jid, "⏳"), daemon=True
            ).start()
            if isinstance(pend_reply, tuple):
                pend_reply = pend_reply[0]
            if pend_reply:
                _dispatch(reply_jid, pend_reply)
            return

    reply_jid = _reply_jid(info)

    stripped = text.strip()
    if stripped.startswith(PREFIX):
        head = stripped[len(PREFIX):].split(maxsplit=1)
        first = head[0].lower() if head else ""
        if first == "gamble" and len(head) > 1:
            game = head[1].split(maxsplit=1)[0].lower() if head[1] else ""
            react_emoji = {
                "coinflip": "🪙", "dice": "🎲", "slots": "🎰",
                "clover": "🍀",
                "roulette": "🎡", "blackjack": "🃏", "bj21": "🃏",
                "highlow": "📈", "crash": "🚀", "mine": "💣",
                "plinko": "🎯", "limbo": "🎯", "tower": "🗼",
                "keno": "🎱", "guess": "🎯", "warcards": "⚔️",
                "penalty": "⚽", "rps": "✂️", "lucky": "🍀",
                "rocket": "🚀", "ladder": "🪜", "bomb": "💣",
                "streaker": "📈", "flip2": "🪙",
                "setcoins": "🛠",
            }.get(game, "🎲")
        elif first == "war":
            react_emoji = "⚔️"
        elif first in ("balance", "bal"):
            react_emoji = "💰"
        elif first == "top":
            react_emoji = "🏆"
        elif first == "daily":
            react_emoji = "🎁"
        elif first == "pay":
            react_emoji = "💸"
        else:
            react_emoji = "👀"
        threading.Thread(target=_react, args=(info, reply_jid, react_emoji),
                         daemon=True).start()

    try:
        resp = handle_command(info, text, reply_jid)
    except Exception as e:
        print("[!] handler error:", e, flush=True)
        return

    if not resp:
        return

    # war map image response
    if isinstance(resp, dict) and "image" in resp:
        img = resp.get("image")
        cap = resp.get("caption", "")
        def _send_img():
            ok = _try_send_image(reply_jid, img, cap)
            if not ok:
                try:
                    client.send_message(reply_jid, cap)
                except Exception:
                    pass
        threading.Thread(target=_send_img, daemon=True).start()
        return

    try:
        _dispatch(reply_jid, resp)
    except Exception as e:
        print("[!] dispatch failed:", e, flush=True)

if __name__ == "__main__":
    client.connect()
