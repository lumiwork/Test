package de.lumiwork.silentnetguard;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Einstellungen aus config/silentnet-guard.properties (wird beim ersten Start angelegt). */
final class GuardConfig {

    private static final String DEFAULTS = """
            # SilentNet Guard / InjectedMod Assist
            # Prüfbericht vor jedem Start anzeigen, auch wenn nichts gefunden wurde
            always_show_screen=false
            # Herkunft der Mods online prüfen (Modrinth, Maven Central). Es werden nur Datei-Hashes gesendet.
            online_check=true
            # Fenster auch zeigen, wenn eine Mod nicht original ist, aber sonst unauffällig
            show_screen_for_not_original=true
            """;

    final boolean alwaysShow;
    final boolean onlineCheck;
    final boolean showNotOriginal;
    final Path allowlistFile;
    final Path cacheFile;
    final Set<String> allowlist;

    private GuardConfig(Properties p, Path configDir) {
        alwaysShow = Boolean.parseBoolean(p.getProperty("always_show_screen", "false").trim());
        onlineCheck = Boolean.parseBoolean(p.getProperty("online_check", "true").trim());
        showNotOriginal = Boolean.parseBoolean(p.getProperty("show_screen_for_not_original", "true").trim());
        allowlistFile = configDir.resolve("silentnet-guard-allow.txt");
        cacheFile = configDir.resolve("silentnet-guard-cache.properties");
        allowlist = readAllowlist(allowlistFile);
    }

    static GuardConfig load(Path configDir) {
        Path file = configDir.resolve("silentnet-guard.properties");
        Properties p = new Properties();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                Files.writeString(file, DEFAULTS);
            }
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            }
        } catch (IOException ignored) {
            // Standardwerte
        }
        return new GuardConfig(p, configDir);
    }

    private static Set<String> readAllowlist(Path file) {
        Set<String> out = new HashSet<>();
        try {
            for (String line : Files.readAllLines(file)) {
                String l = line.trim().toLowerCase(Locale.ROOT);
                if (!l.isEmpty() && !l.startsWith("#")) {
                    out.add(l.split("\\s+")[0]);
                }
            }
        } catch (IOException ignored) {
            // keine Allowlist vorhanden
        }
        return out;
    }
}
