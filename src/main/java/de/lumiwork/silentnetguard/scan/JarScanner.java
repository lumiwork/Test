package de.lumiwork.silentnetguard.scan;

import de.lumiwork.silentnetguard.util.MiniJson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Statischer Scanner für Mod-JARs. Liest die Dateien nur, lädt oder führt nichts davon aus.
 * Eingebettete JARs (z. B. META-INF/jars/*.jar) werden rekursiv als eigene Ergebnisse geprüft.
 */
public final class JarScanner {

    private static final int MAX_DEPTH = 3;
    private static final long MAX_ENTRY = 64L * 1024 * 1024;
    private static final long PAYLOAD_MIN_SIZE = 256 * 1024;
    private static final double PAYLOAD_MIN_ENTROPY = 7.9;
    private static final Pattern TOML_MOD_ID = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_VERSION = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_NAME = Pattern.compile("displayName\\s*=\\s*\"([^\"]+)\"");

    private JarScanner() {
    }

    public static ScanResult scan(Path file) throws IOException {
        return scan(file.toString(), file.getFileName().toString(), false, Files.readAllBytes(file), 0);
    }

    static ScanResult scan(String file, String display, boolean nested, byte[] bytes, int depth) throws IOException {
        ScanResult result = new ScanResult(file, display, nested,
                hash("SHA-1", bytes), hash("SHA-256", bytes), hash("SHA-512", bytes));
        JarFacts facts = new JarFacts();
        boolean payloadFound = false;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] data = zip.readNBytes((int) MAX_ENTRY);
                String lower = name.toLowerCase(Locale.ROOT);

                if (lower.endsWith(".class")) {
                    try {
                        facts.addClass(name, ClassInfo.read(data));
                    } catch (IOException | RuntimeException ignored) {
                        // kaputte oder absichtlich verfälschte Klasse - ignorieren
                    }
                    continue;
                }
                if (isMagic(data, 0xCA, 0xFE, 0xBA, 0xBE)) {
                    facts.resourceFindings.add(new Finding(Category.INJECTION, 15,
                            "Klassendatei unter falschem Namen: " + name));
                    try {
                        facts.addClass(name + ".class", ClassInfo.read(data));
                    } catch (IOException | RuntimeException ignored) {
                        // nicht lesbar
                    }
                    continue;
                }
                if (isMagic(data, 'P', 'K', 3, 4)) {
                    if (depth < MAX_DEPTH) {
                        String childDisplay = display + "!/" + name;
                        try {
                            result.children.add(scan(null, childDisplay, true, data, depth + 1));
                        } catch (IOException | RuntimeException ignored) {
                            // kein gültiges Archiv
                        }
                    }
                    if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) {
                        facts.resourceFindings.add(new Finding(Category.INJECTION, 30,
                                "Verstecktes Archiv unter falschem Namen: " + name));
                    }
                    continue;
                }
                if (isMagic(data, 'M', 'Z')) {
                    if (lower.endsWith(".exe")) {
                        facts.resourceFindings.add(new Finding(Category.RAT, 15, "Windows-Programm eingebettet: " + name));
                    } else if (!isNativeLibrary(lower)) {
                        facts.resourceFindings.add(new Finding(Category.RAT, 40,
                                "Getarnte Windows-Programmdatei: " + name));
                    }
                    continue;
                }
                if (isMagic(data, 0x7F, 'E', 'L', 'F') && !isNativeLibrary(lower)) {
                    facts.resourceFindings.add(new Finding(Category.RAT, 30, "Getarnte Linux-Programmdatei: " + name));
                    continue;
                }
                if ((lower.endsWith(".json") || lower.endsWith(".toml") || lower.endsWith(".mf"))
                        && data.length <= 256 * 1024) {
                    facts.textResources.put(name, new String(data, StandardCharsets.UTF_8));
                    continue;
                }
                if (!payloadFound && !isHarmlessResource(lower) && data.length >= PAYLOAD_MIN_SIZE) {
                    double entropy = entropy(data);
                    if (entropy >= PAYLOAD_MIN_ENTROPY) {
                        payloadFound = true;
                        facts.resourceFindings.add(new Finding(Category.OBFUSCATION, 20, String.format(Locale.ROOT,
                                "Verschlüsselte Payload-Datei: %s (%d Bytes, Entropie %.3f)", name, data.length, entropy)));
                    }
                }
            }
        }

        readModInfo(result, facts.textResources);
        result.findings.addAll(facts.resourceFindings);
        Rules.evaluate(result, facts);
        return result;
    }

    private static void readModInfo(ScanResult result, Map<String, String> text) {
        String fabric = text.get("fabric.mod.json");
        if (fabric != null) {
            try {
                Map<String, Object> json = MiniJson.obj(MiniJson.parse(fabric));
                result.modId = MiniJson.str(json.get("id"));
                result.modName = MiniJson.str(json.get("name"));
                result.modVersion = MiniJson.str(json.get("version"));
                return;
            } catch (RuntimeException ignored) {
                // kaputtes JSON - unten weiter
            }
        }
        String quilt = text.get("quilt.mod.json");
        if (quilt != null) {
            try {
                Map<String, Object> loader = MiniJson.obj(MiniJson.obj(MiniJson.parse(quilt)).get("quilt_loader"));
                result.modId = MiniJson.str(loader.get("id"));
                result.modVersion = MiniJson.str(loader.get("version"));
                result.modName = MiniJson.str(MiniJson.obj(loader.get("metadata")).get("name"));
                return;
            } catch (RuntimeException ignored) {
                // weiter
            }
        }
        String toml = text.getOrDefault("META-INF/neoforge.mods.toml", text.get("META-INF/mods.toml"));
        if (toml != null) {
            Matcher m = TOML_MOD_ID.matcher(toml);
            if (m.find()) {
                result.modId = m.group(1);
                Matcher n = TOML_NAME.matcher(toml);
                result.modName = n.find() ? n.group(1) : null;
                Matcher v = TOML_VERSION.matcher(toml.substring(m.start()));
                result.modVersion = v.find() ? v.group(1) : null;
            }
        }
    }

    /** Dateien, die normalerweise komprimiert/zufaellig aussehen und keine Payload sind. */
    private static boolean isHarmlessResource(String n) {
        return n.endsWith(".png") || n.endsWith(".ogg") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gz") || n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".nbt")
                || n.endsWith(".mcmeta") || n.endsWith(".webp") || n.endsWith(".mp3") || n.endsWith(".wav")
                || n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib") || n.endsWith(".jnilib")
                || n.endsWith(".bin.properties") || n.endsWith(".xz") || n.endsWith(".lzma");
    }

    private static boolean isNativeLibrary(String n) {
        return n.endsWith(".dll") || n.endsWith(".sys") || n.endsWith(".so") || n.contains(".so.")
                || n.endsWith(".dylib") || n.endsWith(".jnilib") || n.endsWith(".node");
    }

    private static boolean isMagic(byte[] d, int... magic) {
        if (d.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((d[i] & 0xff) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    static double entropy(byte[] data) {
        if (data.length == 0) {
            return 0;
        }
        long[] counts = new long[256];
        for (byte b : data) {
            counts[b & 0xff]++;
        }
        double e = 0;
        for (long c : counts) {
            if (c > 0) {
                double p = (double) c / data.length;
                e -= p * (Math.log(p) / Math.log(2));
            }
        }
        return e;
    }

    static String hash(String algorithm, byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
