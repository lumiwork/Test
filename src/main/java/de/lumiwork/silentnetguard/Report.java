package de.lumiwork.silentnetguard;

import de.lumiwork.silentnetguard.scan.Finding;
import de.lumiwork.silentnetguard.scan.Origin;
import de.lumiwork.silentnetguard.scan.ScanResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Alles, was das Prüf-Fenster anzeigt. Wird zwischen Minecraft-Prozess und Fenster-Prozess übergeben. */
public final class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    final List<ScanResult> mods = new ArrayList<>();
    final List<SystemCheck.Issue> system = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    boolean onlineChecked;
    boolean online;

    boolean anyMalicious() {
        return mods.stream().anyMatch(m -> m.worstLevel() == ScanResult.Level.MALICIOUS)
                || system.stream().anyMatch(SystemCheck.Issue::critical);
    }

    boolean anySuspicious() {
        return mods.stream().anyMatch(m -> m.worstLevel() == ScanResult.Level.SUSPICIOUS) || !system.isEmpty();
    }

    boolean anyNotOriginal() {
        return mods.stream().anyMatch(m -> !m.allowlisted && m.origin.status() == Origin.Status.MODIFIED);
    }

    int libraryCount() {
        int[] n = {0};
        mods.forEach(m -> count(m, n));
        return n[0];
    }

    private static void count(ScanResult r, int[] n) {
        for (ScanResult c : r.children) {
            n[0]++;
            count(c, n);
        }
    }

    void write(Path file) throws IOException {
        try (OutputStream o = Files.newOutputStream(file); ObjectOutputStream out = new ObjectOutputStream(o)) {
            out.writeObject(this);
        }
    }

    static Report read(Path file) throws IOException, ClassNotFoundException {
        try (InputStream i = Files.newInputStream(file); ObjectInputStream in = new ObjectInputStream(i)) {
            // Nur die eigenen Klassen und Standard-Collections zulassen
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "de.lumiwork.silentnetguard.**;java.util.*;java.lang.*;java.lang.Enum;!*"));
            return (Report) in.readObject();
        }
    }

    /** Textfassung für silentnet-guard-report.txt und das Log. */
    String toText() {
        StringBuilder b = new StringBuilder("SilentNet Guard / InjectedMod Assist - Prüfbericht\n\n");
        for (ScanResult m : mods) {
            append(b, m, "");
        }
        if (!system.isEmpty()) {
            b.append("SYSTEM:\n");
            system.forEach(s -> b.append(s.critical() ? "  [!] " : "  [?] ").append(s.text()).append('\n'));
            b.append('\n');
        }
        if (!errors.isEmpty()) {
            b.append("Nicht lesbar:\n");
            errors.forEach(e -> b.append("  - ").append(e).append('\n'));
        }
        return b.toString();
    }

    private static void append(StringBuilder b, ScanResult r, String indent) {
        boolean interesting = r.score() > 0 || r.origin.status() == Origin.Status.MODIFIED || !r.nested;
        if (interesting) {
            b.append(indent).append('[').append(r.level().label).append("] ").append(r.title()).append("  (")
                    .append(r.display).append(")  Punkte: ").append(r.score()).append('\n');
            b.append(indent).append("    Herkunft: ").append(r.origin.status().label)
                    .append(r.origin.detail().isEmpty() ? "" : " - " + r.origin.detail()).append('\n');
            b.append(indent).append("    SHA-256: ").append(r.sha256).append('\n');
            for (Finding f : r.findings) {
                b.append(indent).append("    - [").append(f.category().label).append(", +").append(f.points()).append("] ")
                        .append(f.text()).append('\n');
            }
        }
        for (ScanResult c : r.children) {
            if (c.worstLevel() != ScanResult.Level.CLEAN || c.origin.status() == Origin.Status.MODIFIED) {
                append(b, c, indent + "    ");
            }
        }
        if (interesting && indent.isEmpty()) {
            b.append('\n');
        }
    }
}
