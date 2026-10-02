package de.lumiwork.silentnetguard.scan;

import de.lumiwork.silentnetguard.util.MiniJson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennungsregeln. Jede Regel zählt pro JAR hoechstens einmal. Ab {@link ScanResult#SUSPICIOUS} Punkten ist eine
 * Datei verdächtig, ab {@link ScanResult#MALICIOUS} schädlich. Die Regeln sind so gewichtet, dass eine einzelne
 * harmlose Fähigkeit (z. B. Netzwerk oder Prozessstart) nie allein ausloest.
 */
final class Rules {

    /** SHA-256 bekannter Samples. */
    private static final Set<String> KNOWN_HASHES = Set.of(
            "f47fecd6b5107111e518c2ceec31d07cfca170f69fcd991dc175c78b65a384c0" // SilentNet: auto-schematic-builder-1.21.11.jar
    );

    private static final Pattern INJECTED_CLASS = Pattern.compile("com/github/[^/]+");
    private static final Pattern RAW_IP_URL = Pattern.compile("^(?:https?|tcp|ws)s?://(\\d{1,3}(?:\\.\\d{1,3}){3})");

    private Rules() {
    }

    static void evaluate(ScanResult r, JarFacts f) {
        knownMalware(r, f);
        tokenTheft(r, f);
        stealer(r, f);
        exfil(r, f);
        rat(r, f);
        persistence(r, f);
        injection(r, f);
        obfuscation(r, f);
    }

    // ---------------------------------------------------------------- bekannte Familien

    private static void knownMalware(ScanResult r, JarFacts f) {
        if (KNOWN_HASHES.contains(r.sha256)) {
            r.add(Category.KNOWN_MALWARE, 100, "Bekanntes SilentNet-Sample (SHA-256 " + r.sha256 + ")");
        }
        String fabric = f.textResources.get("fabric.mod.json");
        if (fabric != null) {
            String compact = fabric.replaceAll("\\s", "");
            if (compact.contains("\"id\":\"package\"") && compact.contains("Corelibrarymodule")) {
                r.add(Category.KNOWN_MALWARE, 40, "SilentNet-Tarnung: id \"package\" / \"Core library module\"");
            }
        }
        List<String> fracture = f.matches("files-8ie.pages.dev", "85.217.144.130", "107.189.3.101",
                "libwebgl64", "systemd-utility");
        if (!fracture.isEmpty()) {
            r.add(Category.KNOWN_MALWARE, 100, "Fractureiser-Indikatoren: " + fracture);
        }
        // Fractureiser Stage 0: URLClassLoader lädt Klasse "Utility" und ruft run() auf (meist im static-Block)
        if (f.has("java/net/URLClassLoader") && f.has("Utility") && f.has("run") && f.has("forName")) {
            r.add(Category.KNOWN_MALWARE, 60, "Fractureiser-Stage-0-Muster (URLClassLoader -> Class.forName(\"Utility\").run)");
        }
    }

    // ---------------------------------------------------------------- Minecraft-Token

    private static void tokenTheft(ScanResult r, JarFacts f) {
        boolean token = false;
        if (f.tokenClass != null) {
            token = true;
            r.add(Category.TOKEN, 30, "Liest den Session-Token (Session.getAccessToken) in " + f.tokenClass);
        }
        if (f.matches("field_1726").size() > 0) {
            token = true;
            r.add(Category.TOKEN, 25, "Greift per Reflection auf das Session-Feld zu (field_1726)");
        }
        if (!f.matches("--accesstoken", "accesstoken\"").isEmpty()
                || (f.has("sun.java.command") && f.has("accessToken"))) {
            token = true;
            r.add(Category.TOKEN, 30, "Liest den Token aus den Startparametern (--accessToken)");
        } else if (f.has("sun.java.command") || f.has("getInputArguments")) {
            r.add(Category.TOKEN, 10, "Liest die Startparameter der JVM (darin steht der Token)");
        }
        List<String> launcherFiles = f.matches("launcher_accounts", "microsoft_accounts.json", "tlauncherprofiles",
                ".lunarclient", ".feather", "essential/microsoft", "prismlauncher", "polymc", "multimc",
                "badlion client", "launcher_msa_credentials", "atlauncher", "gdlauncher", "curseforge/minecraft");
        if (!launcherFiles.isEmpty()) {
            token = true;
            r.add(Category.TOKEN, 40, "Liest Account-Dateien von Launchern: " + launcherFiles);
        }
        boolean network = f.has("java/net/HttpURLConnection") || f.has("java/net/http/HttpClient")
                || f.has("java/net/Socket") || f.has("java/net/URL") || f.hasClassSuffix("/OkHttpClient");
        boolean mojangOnly = !f.matches("minecraftservices.com", "sessionserver.mojang.com", "login.live.com",
                "login.microsoftonline.com", "xboxlive.com").isEmpty();
        if (token && network && !mojangOnly) {
            r.add(Category.TOKEN, 20, "Token-Zugriff plus Netzwerkzugriff zu Nicht-Mojang/Microsoft-Servern");
        }
    }

    // ---------------------------------------------------------------- Datendiebstahl

    private static void stealer(ScanResult r, JarFacts f) {
        List<String> discord = f.matches("discord/local storage", "discord\\local storage", "discordcanary",
                "discordptb", "dqw4w9wgxcq", "discord\\\\local storage");
        if (!discord.isEmpty()) {
            r.add(Category.STEALER, 50, "Sucht Discord-Tokens: " + discord);
        }
        List<String> browser = f.matches("login data", "local state", "encrypted_key", "cookies.sqlite", "logins.json",
                "key4.db", "google/chrome/user data", "google\\chrome\\user data", "bravesoftware", "opera software",
                "yandexbrowser", "microsoft/edge/user data", "microsoft\\edge\\user data", "web data");
        if (browser.size() >= 2) {
            r.add(Category.STEALER, 40, "Liest Browser-Passwörter/Cookies: " + browser);
        } else if (browser.size() == 1) {
            r.add(Category.STEALER, 10, "Browser-Datei erwähnt: " + browser);
        }
        List<String> wallets = f.matches("exodus", "electrum", "metamask", "nkbihfbeogaeaoehlefnkodbefgpgknn",
                "atomic/local storage", "atomic\\local storage", "wallet.dat", "bfnaelmomeimhlpmgjnjophhpkkoljpa",
                "ejbalbakoplchlghecdalmeeeajnimhm", "jaxx", "coinomi", "guarda", "armory");
        if (wallets.size() >= 2) {
            r.add(Category.STEALER, 40, "Sucht Krypto-Wallets: " + wallets);
        }
        if (!f.matches("cryptunprotectdata").isEmpty()) {
            r.add(Category.STEALER, 40, "Entschlüsselt Windows-geschützte Daten (CryptUnprotectData/DPAPI)");
        }
        List<String> sysinfo = f.matches("wmic", "systeminfo", "ipconfig", "api.ipify.org", "ipinfo.io", "ip-api.com",
                "checkip.amazonaws.com", "icanhazip");
        if (sysinfo.size() >= 2) {
            r.add(Category.STEALER, 15, "Sammelt System-/IP-Informationen: " + sysinfo);
        }
    }

    // ---------------------------------------------------------------- Datenversand / C2

    private static void exfil(ScanResult r, JarFacts f) {
        if (!f.matches("discord.com/api/webhooks", "discordapp.com/api/webhooks").isEmpty()) {
            r.add(Category.EXFIL, 50, "Discord-Webhook: " + f.firstContaining("/api/webhooks"));
        }
        if (!f.matches("api.telegram.org/bot").isEmpty()) {
            r.add(Category.EXFIL, 50, "Telegram-Bot-API: " + f.firstContaining("api.telegram.org"));
        }
        List<String> hosts = f.matches("pastebin.com/raw", "rentry.co", "hastebin", "ngrok", "trycloudflare.com",
                "workers.dev", "pages.dev", "transfer.sh", "gofile.io", "file.io", "anonfiles", "catbox.moe",
                "0x0.st", "glitch.me", "repl.co", "herokuapp.com", "paste.ee", "pastes.dev");
        if (!hosts.isEmpty()) {
            r.add(Category.EXFIL, 25, "Paste-/Tunnel-/Filehoster (oft für Nachladen oder Upload): " + hosts);
        }
        Set<String> ips = new TreeSet<>();
        for (String s : f.lowerStrings) {
            Matcher m = RAW_IP_URL.matcher(s);
            if (m.find()) {
                String ip = m.group(1);
                if (isPublic(ip)) {
                    ips.add(ip);
                }
            }
        }
        if (!ips.isEmpty()) {
            r.add(Category.EXFIL, 25, "Verbindet zu nackten IP-Adressen: " + ips);
        }
        List<String> chain = f.matches("eth_call", "drpc.org", "publicnode.com", "1rpc.io", "quiknode.pro",
                "bsc-dataseed", "polygon-rpc.com", "infura.io", "alchemy.com", "api.zan.top", "binance.org");
        if (!chain.isEmpty()) {
            r.add(Category.EXFIL, 40, "Holt Server-Adresse aus der Blockchain (EtherHiding): " + chain);
        }
        if (!f.matches("/dns-query").isEmpty()) {
            r.add(Category.EXFIL, 15, "Eigene DNS-Auflösung über DNS-over-HTTPS (umgeht DNS-Filter)");
        }
    }

    // ---------------------------------------------------------------- Fernsteuerung

    private static void rat(ScanResult r, JarFacts f) {
        boolean network = f.has("java/net/HttpURLConnection") || f.has("java/net/http/HttpClient")
                || f.has("java/net/Socket") || f.has("java/net/URLConnection") || f.has("openConnection")
                || f.has("openStream");

        // Allgemeine Fähigkeiten, die auch normale Libraries haben: wenig Punkte, nur als Zusatzinfo.
        if (!f.selfSpawn.isEmpty()) {
            r.add(Category.RAT, 10, "Startet sich selbst als eigenen Prozess (ProcessBuilder + eigener JAR-Pfad) in "
                    + first(f.selfSpawn.keySet()));
        }
        if (!f.remoteLoad.isEmpty()) {
            r.add(Category.RAT, 10, "Lädt Klassen und hat Netzwerkzugriff in derselben Klasse: " + first(f.remoteLoad.keySet()));
        }
        if (!f.shellExec.isEmpty()) {
            r.add(Category.RAT, 10, "Führt Shell-Befehle aus: " + new TreeSet<>(f.shellExec.values()));
        }
        // Eindeutigere Muster
        if (!f.reverseShell.isEmpty()) {
            r.add(Category.RAT, 25, "Reverse-Shell-Muster (Socket + Prozessstart in derselben Klasse): "
                    + first(f.reverseShell.keySet()));
        }
        if (!f.jarRewrite.isEmpty()) {
            r.add(Category.RAT, 35, "Kann Mods im mods-Ordner verändern (Selbstverbreitung wie Fractureiser): "
                    + first(f.jarRewrite.keySet()));
        }
        if (f.has("java/awt/Robot") && f.has("createScreenCapture")) {
            r.add(Category.RAT, 25, "Erstellt Screenshots (java.awt.Robot)");
        }
        List<String> keylog = f.matches("globalscreen", "nativekeylistener", "getasynckeystate", "setwindowshookex");
        if (!keylog.isEmpty()) {
            r.add(Category.RAT, 40, "Keylogger: " + keylog);
        }
        if (!f.matches("avicap32", "capcreatecapturewindow").isEmpty() || f.has("com/github/sarxos/webcam/Webcam")) {
            r.add(Category.RAT, 30, "Webcam-Zugriff");
        }
        if (f.has("getSystemClipboard") && network && !f.matches("discord.com/api/webhooks", "api.telegram.org").isEmpty()) {
            r.add(Category.RAT, 20, "Liest die Zwischenablage und sendet Daten");
        }
        List<String> av = f.matches("add-mppreference", "set-mppreference", "exclusionpath", "disablerealtimemonitoring");
        if (!av.isEmpty()) {
            r.add(Category.RAT, 50, "Schaltet Windows Defender ab / setzt Ausnahmen: " + av);
        }
    }

    /** Nur oeffentliche Adressen zaehlen (keine lokalen, privaten, link-lokalen oder Cloud-Metadaten-IPs). */
    private static boolean isPublic(String ip) {
        String[] p = ip.split("\\.");
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168) || (a == 100 && b >= 64 && b <= 127));
    }

    private static String first(Set<String> s) {
        return s.size() == 1 ? s.iterator().next() : s.iterator().next() + " (+" + (s.size() - 1) + " weitere)";
    }

    // ---------------------------------------------------------------- Autostart

    private static void persistence(ScanResult r, JarFacts f) {
        List<String> p = f.matches("currentversion\\run", "currentversion/run", "currentversion\\\\run", "schtasks",
                "start menu\\programs\\startup", "start menu/programs/startup", "start menu\\\\programs\\\\startup",
                "/systemd/user", ".config/autostart", "launchagents", "crontab", "ntprofileindex");
        if (!p.isEmpty()) {
            r.add(Category.PERSISTENCE, 40, "Nistet sich ins System ein (Autostart): " + p);
        }
        List<String> hide = f.matches("attrib +h", "attrib +s", "\\appdata\\roaming\\microsoft\\", "\\microsoft\\windows\\");
        if (!hide.isEmpty()) {
            r.add(Category.PERSISTENCE, 15, "Versteckt Dateien in System-Ordnern: " + hide);
        }
    }

    // ---------------------------------------------------------------- eingeschleuster Code

    private static void injection(ScanResult r, JarFacts f) {
        Set<String> injected = new TreeSet<>();
        Set<String> encryptedInjected = new TreeSet<>();
        for (String c : f.classNames) {
            if (INJECTED_CLASS.matcher(c).matches()) {
                injected.add(c);
                if (f.encryptedLiterals.getOrDefault(c, 0) >= 5) {
                    encryptedInjected.add(c);
                }
            }
        }
        if (!injected.isEmpty()) {
            r.add(Category.INJECTION, 20, "Klassen direkt in com/github/ (typisch für eingeschleusten Code): " + injected);
        }
        if (!encryptedInjected.isEmpty()) {
            r.add(Category.INJECTION, 40, "SilentNet-String-Verschlüsselung in: " + encryptedInjected);
        }

        String fabric = f.textResources.get("fabric.mod.json");
        if (fabric == null) {
            return;
        }
        List<String> entrypoints = new ArrayList<>();
        Set<String> mixinPackages = new HashSet<>();
        try {
            Map<String, Object> json = MiniJson.obj(MiniJson.parse(fabric));
            for (Object list : MiniJson.obj(json.get("entrypoints")).values()) {
                for (Object e : MiniJson.arr(list)) {
                    String v = e instanceof String s ? s : MiniJson.str(MiniJson.obj(e).get("value"));
                    if (v != null) {
                        entrypoints.add(v.contains("::") ? v.substring(0, v.indexOf("::")) : v);
                    }
                }
            }
            for (Object m : MiniJson.arr(json.get("mixins"))) {
                String cfg = m instanceof String s ? s : MiniJson.str(MiniJson.obj(m).get("config"));
                String content = cfg == null ? null : f.textResources.get(cfg);
                if (content != null) {
                    String pkg = MiniJson.str(MiniJson.obj(MiniJson.parse(content)).get("package"));
                    if (pkg != null) {
                        mixinPackages.add(pkg);
                    }
                }
            }
        } catch (RuntimeException e) {
            return;
        }

        for (String ep : entrypoints) {
            if (ep.matches("com\\.github\\.[A-Za-z0-9_$]+")) {
                r.add(Category.INJECTION, 30, "Entrypoint zeigt auf eingeschleuste Klasse: " + ep);
                break;
            }
        }
        // Entrypoint in einem Paket, das sonst nichts mit der Mod zu tun hat
        if (entrypoints.size() >= 2) {
            for (String ep : entrypoints) {
                String root = root(ep);
                boolean shared = mixinPackages.stream().anyMatch(p -> root(p).equals(root))
                        || entrypoints.stream().anyMatch(o -> o != ep && root(o).equals(root));
                if (!shared) {
                    String pkg = ep.contains(".") ? ep.substring(0, ep.lastIndexOf('.')).replace('.', '/') + "/" : "";
                    long siblings = f.classNames.stream().filter(c -> c.startsWith(pkg)).count();
                    if (siblings <= 10) {
                        r.add(Category.INJECTION, 30, "Fremder Entrypoint außerhalb der Mod-Pakete: " + ep
                                + " (" + siblings + " Klassen in diesem Paket)");
                        break;
                    }
                }
            }
        }
    }

    private static String root(String fqcn) {
        String[] parts = fqcn.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : fqcn;
    }

    // ---------------------------------------------------------------- Verschleierung

    private static void obfuscation(ScanResult r, JarFacts f) {
        Set<String> heavy = new TreeSet<>();
        for (Map.Entry<String, Integer> e : f.encryptedLiterals.entrySet()) {
            if (e.getValue() >= 10) {
                heavy.add(e.getKey());
            }
        }
        if (!heavy.isEmpty()) {
            r.add(Category.OBFUSCATION, 15, "Stark verschlüsselte Strings in " + heavy.size() + " Klasse(n): "
                    + (heavy.size() > 5 ? new ArrayList<>(heavy).subList(0, 5) + " ..." : heavy));
        }
    }
}
