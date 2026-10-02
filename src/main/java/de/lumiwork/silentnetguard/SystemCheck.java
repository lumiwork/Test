package de.lumiwork.silentnetguard;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Prüft, ob Malware bereits gelaufen ist: Ablage-Ordner, Autostart, laufende Prozesse. */
final class SystemCheck {

    /** @param critical true = sicherer Infektionshinweis, false = bitte prüfen */
    record Issue(boolean critical, String text) implements Serializable {
    }

    private SystemCheck() {
    }

    static List<Issue> run(List<String> maliciousJars) {
        List<Issue> out = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        String appData = System.getenv("APPDATA");
        Path home = Path.of(System.getProperty("user.home"));

        if (localAppData != null) {
            critical(out, Path.of(localAppData, "Microsoft", "Windows", "NtProfileIndex"),
                    "SilentNet-Ordner gefunden - der Stealer ist sehr wahrscheinlich schon gelaufen");
            critical(out, Path.of(localAppData, "Microsoft Edge", "libWebGL64.jar"),
                    "Fractureiser Stage 2 gefunden");
            Path edge = Path.of(localAppData, "Microsoft Edge");
            if (Files.isDirectory(edge)) {
                try (Stream<Path> s = Files.list(edge)) {
                    s.filter(p -> p.getFileName().toString().endsWith(".ref"))
                            .forEach(p -> out.add(new Issue(true, "Fractureiser-Datei gefunden: " + p)));
                } catch (IOException ignored) {
                    // kein Zugriff
                }
            }
        }
        critical(out, home.resolve(".config/.data/lib.jar"), "Fractureiser Stage 2 (Linux) gefunden");
        critical(out, home.resolve(".config/systemd/user/systemd-utility.service"), "Fractureiser-Autostart (Linux) gefunden");
        critical(out, Path.of("/etc/systemd/system/systemd-utility.service"), "Fractureiser-Autostart (Linux) gefunden");

        if (appData != null) {
            Path startup = Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
            if (Files.isDirectory(startup)) {
                try (Stream<Path> s = Files.list(startup)) {
                    s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jar") || n.endsWith(".vbs") || n.endsWith(".js") || n.endsWith(".bat")
                                || n.endsWith(".cmd") || n.endsWith(".ps1");
                    }).forEach(p -> out.add(new Issue(false, "Skript/JAR im Autostart-Ordner, bitte prüfen: " + p)));
                } catch (IOException ignored) {
                    // kein Zugriff
                }
            }
        }
        Path autostart = home.resolve(".config/autostart");
        if (Files.isDirectory(autostart)) {
            try (Stream<Path> s = Files.list(autostart)) {
                s.filter(p -> {
                    try {
                        return Files.readString(p).toLowerCase(Locale.ROOT).contains("java");
                    } catch (IOException e) {
                        return false;
                    }
                }).forEach(p -> out.add(new Issue(false, "Java im Autostart (Linux), bitte prüfen: " + p)));
            } catch (IOException ignored) {
                // kein Zugriff
            }
        }

        long self = ProcessHandle.current().pid();
        ProcessHandle.allProcesses().forEach(p -> {
            if (p.pid() == self) {
                return;
            }
            String cmd = p.info().commandLine().orElse("");
            if (cmd.isEmpty()) {
                return;
            }
            boolean bad = cmd.contains("NtProfileIndex") || cmd.contains("libWebGL64");
            for (String jar : maliciousJars) {
                bad |= cmd.contains(jar);
            }
            if (bad) {
                boolean killed = p.destroyForcibly();
                out.add(new Issue(true, "Malware-Prozess PID " + p.pid() + (killed ? " beendet" : " (konnte nicht beendet werden)")
                        + ": " + cmd));
            }
        });
        return out;
    }

    private static void critical(List<Issue> out, Path p, String text) {
        if (Files.exists(p)) {
            out.add(new Issue(true, text + ": " + p));
        }
    }
}
