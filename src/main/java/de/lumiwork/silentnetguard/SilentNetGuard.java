package de.lumiwork.silentnetguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Laeuft als preLaunch-Entrypoint, also bevor die "main"-Entrypoints (ModInitializer) anderer Mods starten.
 * Findet es eine schaedliche Mod, wird ein Bericht geschrieben und Minecraft absichtlich gecrasht.
 */
public class SilentNetGuard implements PreLaunchEntrypoint {

    private static final String REPORT = "silentnet-guard-report.txt";
    private static final String ALLOWLIST = "silentnet-guard-allow.txt";

    @Override
    public void onPreLaunch() {
        FabricLoader loader = FabricLoader.getInstance();
        Path gameDir = loader.getGameDir();
        Path modsDir = gameDir.resolve("mods");
        Set<String> allowed = readAllowlist(loader.getConfigDir().resolve(ALLOWLIST));

        List<ModScanner.Result> hits = new ArrayList<>();
        List<String> scanErrors = new ArrayList<>();
        try (Stream<Path> files = Files.walk(modsDir)) {
            for (Path jar : files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                try {
                    ModScanner.Result r = ModScanner.scan(jar);
                    if (r.malicious() && !allowed.contains(r.sha256())) {
                        hits.add(r);
                    }
                } catch (IOException | RuntimeException e) {
                    scanErrors.add(jar + ": " + e);
                }
            }
        } catch (IOException e) {
            scanErrors.add(modsDir + ": " + e);
        }

        List<String> system = SystemCheck.run(hits);

        if (hits.isEmpty() && system.isEmpty()) {
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("SilentNet Guard - ").append(LocalDateTime.now()).append("\n\n");
        for (ModScanner.Result r : hits) {
            report.append("SCHAEDLICHE MOD: ").append(r.file()).append('\n');
            report.append("  SHA-256: ").append(r.sha256()).append('\n');
            report.append("  Punkte:  ").append(r.score()).append('\n');
            r.findings().forEach(f -> report.append("  - ").append(f).append('\n'));
            report.append('\n');
        }
        if (!system.isEmpty()) {
            report.append("SYSTEM:\n");
            system.forEach(s -> report.append("  - ").append(s).append('\n'));
            report.append('\n');
        }
        if (!scanErrors.isEmpty()) {
            report.append("Nicht lesbar:\n");
            scanErrors.forEach(s -> report.append("  - ").append(s).append('\n'));
            report.append('\n');
        }
        report.append("""
                Was jetzt?
                  1. Die oben genannten JAR-Dateien loeschen.
                  2. Microsoft-Passwort aendern und unter account.microsoft.com ueberall abmelden
                     (macht einen gestohlenen Session-Token ungueltig).
                  3. Falls "NtProfileIndex" oben steht: Ordner %LOCALAPPDATA%\\Microsoft\\Windows\\NtProfileIndex
                     loeschen und einen vollstaendigen Virenscan machen.
                Fehlalarm? SHA-256 in config/""" + ALLOWLIST + " eintragen (eine Zeile pro Hash).\n");

        try {
            Files.writeString(gameDir.resolve(REPORT), report, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Der Crash-Report enthaelt den Text ebenfalls.
        }
        System.err.println(report);

        throw new IllegalStateException("[SilentNet Guard] Schaedliche Mod/Infektion erkannt - Start abgebrochen. "
                + "Details in " + gameDir.resolve(REPORT) + "\n\n" + report);
    }

    private static Set<String> readAllowlist(Path file) {
        Set<String> out = new HashSet<>();
        try {
            for (String line : Files.readAllLines(file)) {
                String l = line.trim().toLowerCase(Locale.ROOT);
                if (!l.isEmpty() && !l.startsWith("#")) {
                    out.add(l);
                }
            }
        } catch (IOException ignored) {
            // keine Allowlist vorhanden
        }
        return out;
    }
}
