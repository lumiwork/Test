package de.lumiwork.silentnetguard;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Statischer Scanner fuer Mod-JARs. Liest die Dateien nur, laedt oder fuehrt nichts davon aus.
 * Erkennt die "SilentNet"-Familie (sltnnt.ru / Polygon-Contract-Loader) und aehnliche Session-Stealer.
 */
public final class ModScanner {

    /** Ab dieser Punktzahl gilt eine Datei als schaedlich. */
    public static final int THRESHOLD = 60;

    /** SHA-256 bekannter Samples. */
    private static final Set<String> KNOWN_HASHES = Set.of(
            "f47fecd6b5107111e518c2ceec31d07cfca170f69fcd991dc175c78b65a384c0" // auto-schematic-builder-1.21.11.jar
    );

    private static final long PAYLOAD_MIN_SIZE = 256 * 1024;
    private static final double PAYLOAD_MIN_ENTROPY = 7.9;

    /** Klasse direkt in com/github/ ohne Unterpaket, z. B. com/github/dDhfz.class oder com/github/dDhfz$xy.class. */
    private static final Pattern INJECTED_CLASS = Pattern.compile("com/github/[^/]+\\.class");
    /** Entrypoint in fabric.mod.json, der auf so eine Klasse zeigt. */
    private static final Pattern INJECTED_ENTRYPOINT = Pattern.compile("\"(com\\.github\\.[A-Za-z0-9_$]+)\"");
    /** Ab so vielen verschluesselten Literalen gilt eine Klasse als SilentNet-verschluesselt. */
    private static final int ENCRYPTED_LITERALS_MIN = 5;

    /** Constant-Pool-Inhalt einer Klasse: alle UTF8-Eintraege und die String-Literale (ldc). */
    record ClassInfo(Set<String> utf8, List<String> literals) {
    }

    public record Result(Path file, String sha256, int score, List<String> findings) {
        public boolean malicious() {
            return score >= THRESHOLD;
        }
    }

    private ModScanner() {
    }

    public static Result scan(Path jar) throws IOException {
        List<String> findings = new ArrayList<>();
        int score = 0;

        String sha = sha256(jar);
        if (KNOWN_HASHES.contains(sha)) {
            score += 100;
            findings.add("Bekanntes SilentNet-Sample (SHA-256 " + sha + ")");
        }

        boolean tokenAccess = false;
        boolean processBuilder = false;
        boolean ownJarPath = false;
        boolean payload = false;
        String tokenClass = null;
        Set<String> injectedClasses = new TreeSet<>();
        Set<String> encryptedClasses = new TreeSet<>();

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    ClassInfo info;
                    try (InputStream in = zip.getInputStream(entry)) {
                        info = readClass(in.readAllBytes());
                    } catch (IOException | RuntimeException e) {
                        continue;
                    }
                    Set<String> pool = info.utf8();
                    boolean injected = INJECTED_CLASS.matcher(name).matches();
                    if (injected) {
                        injectedClasses.add(name);
                    }
                    if (injected && countEncryptedLiterals(info.literals()) >= ENCRYPTED_LITERALS_MIN) {
                        encryptedClasses.add(name);
                    }
                    // class_320 = Session, method_1674 = getAccessToken (intermediary)
                    if (pool.contains("net/minecraft/class_320") && pool.contains("method_1674")) {
                        tokenAccess = true;
                        tokenClass = name;
                    }
                    if (pool.contains("java/lang/ProcessBuilder")) {
                        processBuilder = true;
                    }
                    if (pool.contains("getProtectionDomain") && pool.contains("getCodeSource")) {
                        ownJarPath = true;
                    }
                } else if (name.equals("fabric.mod.json")) {
                    String json;
                    try (InputStream in = zip.getInputStream(entry)) {
                        json = new String(in.readAllBytes()).replaceAll("\\s", "");
                    }
                    if (json.contains("\"id\":\"package\"") && json.contains("Corelibrarymodule")) {
                        score += 40;
                        findings.add("Tarn-Metadaten: id \"package\" / \"Core library module\"");
                    }
                    Matcher m = INJECTED_ENTRYPOINT.matcher(json);
                    if (m.find()) {
                        score += 30;
                        findings.add("Entrypoint zeigt auf eingeschleuste Klasse: " + m.group(1));
                    }
                } else if (!isHarmlessResource(name) && entry.getSize() >= PAYLOAD_MIN_SIZE) {
                    double entropy;
                    try (InputStream in = zip.getInputStream(entry)) {
                        entropy = entropy(in.readAllBytes());
                    }
                    if (entropy >= PAYLOAD_MIN_ENTROPY) {
                        payload = true;
                        findings.add(String.format(Locale.ROOT,
                                "Verschluesselte Payload: %s (%d Bytes, Entropie %.3f)", name, entry.getSize(), entropy));
                    }
                }
            }
        }

        if (!injectedClasses.isEmpty()) {
            score += 20;
            findings.add("Klassen direkt in com/github/ (typisch fuer eingeschleusten Code): " + injectedClasses);
        }
        if (!encryptedClasses.isEmpty()) {
            score += 40;
            findings.add("SilentNet-String-Verschluesselung in: " + encryptedClasses);
        }
        if (payload) {
            score += 30;
        }
        if (tokenAccess) {
            score += 30;
            findings.add("Liest den Minecraft-Session-Token (Session.getAccessToken) in " + tokenClass);
        }
        if (processBuilder && ownJarPath) {
            score += 30;
            findings.add("Startet einen eigenen Prozess mit dem eigenen JAR (ProcessBuilder + getCodeSource)");
        }

        return new Result(jar, sha, score, findings);
    }

    /** Dateien, die normalerweise komprimiert/zufaellig aussehen und keine Payload sind. */
    private static boolean isHarmlessResource(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".ogg") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".jar") || n.endsWith(".zip") || n.endsWith(".gz") || n.endsWith(".ttf")
                || n.endsWith(".otf") || n.endsWith(".nbt") || n.endsWith(".mcmeta");
    }

    /** Liest UTF8-Eintraege und String-Literale aus dem Constant Pool einer .class-Datei. */
    static ClassInfo readClass(byte[] bytes) throws IOException {
        Set<String> utf8 = new HashSet<>();
        List<String> literals = new ArrayList<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) {
            return new ClassInfo(utf8, literals);
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utfByIndex = new String[count];
        List<Integer> stringRefs = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    utfByIndex[i] = in.readUTF();
                    utf8.add(utfByIndex[i]);
                }
                case 3, 4 -> in.skipNBytes(4);
                case 5, 6 -> {
                    in.skipNBytes(8);
                    i++;
                }
                case 8 -> stringRefs.add(in.readUnsignedShort());
                case 7, 16, 19, 20 -> in.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 15 -> in.skipNBytes(3);
                default -> throw new IOException("Unbekannter Constant-Pool-Tag " + tag);
            }
        }
        for (int ref : stringRefs) {
            if (ref > 0 && ref < count && utfByIndex[ref] != null) {
                literals.add(utfByIndex[ref]);
            }
        }
        return new ClassInfo(utf8, literals);
    }

    /**
     * Zaehlt Literale in der Form der SilentNet-Verschluesselung: das erste oder letzte Zeichen ist eine kleine
     * Schluessellaenge (1-31), der Rest besteht ueberwiegend aus Zeichen jenseits von Latin-1.
     */
    static int countEncryptedLiterals(List<String> literals) {
        int n = 0;
        for (String s : literals) {
            if (s.length() < 4) {
                continue;
            }
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            boolean keyMarker = (first >= 1 && first < 32 && first < s.length() - 1)
                    || (last >= 1 && last < 32 && last < s.length() - 1);
            if (!keyMarker) {
                continue;
            }
            int high = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) >= 0x100) {
                    high++;
                }
            }
            if (high * 2 >= s.length() - 1) {
                n++;
            }
        }
        return n;
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

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Kommandozeile zum Testen: java ModScanner <jar...> */
    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            Result r = scan(Path.of(arg));
            System.out.println((r.malicious() ? "[SCHAEDLICH] " : "[ok] ") + r.file() + "  score=" + r.score());
            r.findings().forEach(f -> System.out.println("    - " + f));
        }
    }
}
