package de.lumiwork.silentnetguard;

import de.lumiwork.silentnetguard.scan.Finding;
import de.lumiwork.silentnetguard.scan.JarScanner;
import de.lumiwork.silentnetguard.scan.ScanResult;
import de.lumiwork.silentnetguard.verify.OriginVerifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Kommandozeile ohne Minecraft:
 * java -cp silentnet-guard.jar de.lumiwork.silentnetguard.Cli [--online] datei.jar ...
 */
public final class Cli {

    private Cli() {
    }

    public static void main(String[] args) throws Exception {
        boolean online = false;
        List<ScanResult> results = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--online")) {
                online = true;
                continue;
            }
            try {
                results.add(JarScanner.scan(Path.of(arg)));
            } catch (Exception e) {
                System.out.println("[FEHLER] " + arg + ": " + e);
            }
        }
        if (online) {
            new OriginVerifier(null).verify(results);
        }
        for (ScanResult r : results) {
            print(r, "");
        }
    }

    private static void print(ScanResult r, String indent) {
        String origin = r.origin.status() == de.lumiwork.silentnetguard.scan.Origin.Status.NOT_CHECKED
                ? "" : "  [" + r.origin.status().label + (r.origin.detail().isEmpty() ? "" : ": " + r.origin.detail()) + "]";
        System.out.println(indent + "[" + (r.nested ? r.level() : r.worstLevel()).label + "] " + r.display + "  score=" + r.score() + origin);
        for (Finding f : r.findings) {
            System.out.println(indent + "    - (" + f.category().label + ", +" + f.points() + ") " + f.text());
        }
        for (ScanResult c : r.children) {
            if (c.score() > 0 || c.origin.status() != de.lumiwork.silentnetguard.scan.Origin.Status.NOT_CHECKED) {
                print(c, indent + "    ");
            }
        }
    }
}
