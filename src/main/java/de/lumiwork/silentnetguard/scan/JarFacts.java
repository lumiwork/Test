package de.lumiwork.silentnetguard.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Alles, was die Regeln über eine JAR wissen müssen. Wird von {@link JarScanner} befuellt. */
public final class JarFacts {

    /** Alle UTF8-Constant-Pool-Einträge aller Klassen (Klassen-, Methoden-, Feldnamen, Strings). */
    final Set<String> utf8 = new HashSet<>();
    /** Kleingeschriebene Strings ab 4 Zeichen für Teilstring-Suche. */
    final List<String> lowerStrings = new ArrayList<>();
    /** Klassenname -> Anzahl verschlüsselter Literale (nur Klassen mit mindestens einem). */
    final Map<String, Integer> encryptedLiterals = new HashMap<>();
    /** Alle Klassennamen (Pfad im JAR ohne .class). */
    final Set<String> classNames = new TreeSet<>();
    /** Erste Klasse, die den Session-Token liest. */
    String tokenClass;
    /** Kleine Text-/JSON-Ressourcen (Pfad -> Inhalt) für fabric.mod.json, Mixin-Configs, mods.toml. */
    final Map<String, String> textResources = new HashMap<>();
    /** Klassen, in denen bestimmte Fähigkeiten gemeinsam vorkommen (Klassenname -> Detail). */
    final Map<String, String> selfSpawn = new TreeMap<>();
    final Map<String, String> remoteLoad = new TreeMap<>();
    final Map<String, String> reverseShell = new TreeMap<>();
    final Map<String, String> shellExec = new TreeMap<>();
    final Map<String, String> jarRewrite = new TreeMap<>();
    /** Funde aus der Ressourcen-Prüfung (versteckte Klassen, Payloads ...). */
    final List<Finding> resourceFindings = new ArrayList<>();

    void addClass(String entryName, ClassInfo info) {
        String className = entryName.substring(0, entryName.length() - ".class".length());
        classNames.add(className);
        utf8.addAll(info.utf8());
        for (String s : info.utf8()) {
            if (s.length() >= 4) {
                lowerStrings.add(s.toLowerCase(Locale.ROOT));
            }
        }
        int enc = info.encryptedLiterals();
        if (enc > 0) {
            encryptedLiterals.put(className, enc);
        }
        if (tokenClass == null && info.utf8().contains("net/minecraft/class_320") && info.utf8().contains("method_1674")) {
            tokenClass = className;
        }
        capabilities(className, info);
    }

    private static final String[] SHELLS = {"cmd.exe", "powershell", "/bin/sh", "/bin/bash", "wscript", "cscript",
            "mshta", "regsvr32", "certutil", "bitsadmin"};

    /** Fähigkeiten, die nur zusammen in derselben Klasse aussagekraeftig sind. */
    private void capabilities(String cls, ClassInfo info) {
        Set<String> u = info.utf8();
        boolean exec = u.contains("java/lang/ProcessBuilder") || (u.contains("java/lang/Runtime") && u.contains("exec"));
        boolean net = u.contains("java/net/HttpURLConnection") || u.contains("java/net/http/HttpClient")
                || u.contains("java/net/Socket") || u.contains("openConnection") || u.contains("openStream");
        if (exec && u.contains("getCodeSource")) {
            selfSpawn.put(cls, "");
        }
        if (net && (u.contains("java/net/URLClassLoader") || u.contains("defineClass"))) {
            remoteLoad.put(cls, "");
        }
        if (exec && u.contains("java/net/Socket") && u.contains("getInputStream") && u.contains("getOutputStream")) {
            reverseShell.put(cls, "");
        }
        if (exec) {
            for (String lit : info.literals()) {
                String l = lit.toLowerCase(Locale.ROOT);
                for (String sh : SHELLS) {
                    if (l.contains(sh)) {
                        shellExec.put(cls, sh);
                    }
                }
            }
        }
        boolean writesJar = u.contains("java/util/jar/JarOutputStream") || u.contains("java/util/zip/ZipOutputStream");
        boolean asm = u.stream().anyMatch(x -> x.endsWith("/ClassWriter") || x.endsWith("/ClassReader"));
        if (writesJar && asm && info.literals().stream().anyMatch(l -> l.equals("mods") || l.contains("/mods") || l.contains("\\mods"))) {
            jarRewrite.put(cls, "");
        }
    }

    boolean has(String exact) {
        return utf8.contains(exact);
    }

    /** Liefert alle Muster, die als Teilstring in irgendeinem String vorkommen. */
    List<String> matches(String... needles) {
        List<String> out = new ArrayList<>();
        for (String n : needles) {
            for (String s : lowerStrings) {
                if (s.contains(n)) {
                    out.add(n);
                    break;
                }
            }
        }
        return out;
    }

    /** Erster String, der das Muster enthält (für die Anzeige). */
    String firstContaining(String needle) {
        for (String s : lowerStrings) {
            if (s.contains(needle)) {
                return s.length() > 120 ? s.substring(0, 117) + "..." : s;
            }
        }
        return needle;
    }

    /** Endet irgendein Klassenverweis auf diesen Namen (auch geshadet, z. B. ".../asm/ClassWriter")? */
    boolean hasClassSuffix(String suffix) {
        for (String s : utf8) {
            if (s.endsWith(suffix) && s.indexOf(' ') < 0) {
                return true;
            }
        }
        return false;
    }
}
