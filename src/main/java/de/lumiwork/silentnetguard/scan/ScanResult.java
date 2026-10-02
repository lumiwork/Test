package de.lumiwork.silentnetguard.scan;

import java.io.Serializable;
import java.util.ArrayList;

/** Ergebnis für eine JAR-Datei (Mod oder eingebettete Library). */
public final class ScanResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MALICIOUS = 60;
    public static final int SUSPICIOUS = 25;

    public enum Level {
        MALICIOUS("SCHÄDLICH"),
        SUSPICIOUS("Verdächtig"),
        CLEAN("Unauffällig");

        public final String label;

        Level(String label) {
            this.label = label;
        }
    }

    /** Pfad auf der Platte (nur bei Dateien direkt im mods-Ordner gesetzt). */
    public final String file;
    /** Anzeigename, bei eingebetteten Libraries "parent.jar!/META-INF/jars/lib.jar". */
    public final String display;
    public final boolean nested;
    public final String sha1;
    public final String sha256;
    public final String sha512;
    public String modId;
    public String modName;
    public String modVersion;
    public final ArrayList<Finding> findings = new ArrayList<>();
    public final ArrayList<ScanResult> children = new ArrayList<>();
    public Origin origin = Origin.notChecked();
    public boolean allowlisted;

    public ScanResult(String file, String display, boolean nested, String sha1, String sha256, String sha512) {
        this.file = file;
        this.display = display;
        this.nested = nested;
        this.sha1 = sha1;
        this.sha256 = sha256;
        this.sha512 = sha512;
    }

    public int score() {
        return findings.stream().mapToInt(Finding::points).sum();
    }

    public Level level() {
        if (allowlisted) {
            return Level.CLEAN;
        }
        int s = score();
        return s >= MALICIOUS ? Level.MALICIOUS : s >= SUSPICIOUS ? Level.SUSPICIOUS : Level.CLEAN;
    }

    /** Schlechteste Stufe dieser Datei inklusive aller eingebetteten Libraries. */
    public Level worstLevel() {
        Level worst = level();
        for (ScanResult c : children) {
            Level l = c.worstLevel();
            if (l.ordinal() < worst.ordinal()) {
                worst = l;
            }
        }
        return worst;
    }

    public String title() {
        String name = modName != null ? modName : display.substring(display.lastIndexOf('/') + 1);
        return modVersion != null ? name + " " + modVersion : name;
    }

    public void add(Category category, int points, String text) {
        findings.add(new Finding(category, points, text));
    }
}
