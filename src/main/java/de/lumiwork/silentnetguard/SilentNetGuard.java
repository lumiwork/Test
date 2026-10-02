package de.lumiwork.silentnetguard;

import de.lumiwork.silentnetguard.scan.JarScanner;
import de.lumiwork.silentnetguard.scan.ScanResult;
import de.lumiwork.silentnetguard.verify.OriginVerifier;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * SilentNet Guard / InjectedMod Assist.
 * Laeuft als preLaunch-Entrypoint, also bevor die "main"-Entrypoints (ModInitializer) anderer Mods starten:
 * scannt alle Mods, prüft ihre Herkunft, zeigt bei Funden ein Prüf-Fenster und laesst dich entscheiden.
 */
public class SilentNetGuard implements PreLaunchEntrypoint {

    private static final String REPORT = "silentnet-guard-report.txt";
    private static final String LOG = "[SilentNet Guard] ";

    @Override
    public void onPreLaunch() {
        FabricLoader loader = FabricLoader.getInstance();
        Path gameDir = loader.getGameDir();
        GuardConfig config = GuardConfig.load(loader.getConfigDir());
        Path ownJar = ownJar();

        Report report = scanMods(gameDir.resolve("mods"), ownJar, config);
        if (config.onlineCheck) {
            report.onlineChecked = true;
            report.online = new OriginVerifier(config.cacheFile).verify(report.mods);
        }
        List<String> maliciousFiles = report.mods.stream()
                .filter(m -> m.worstLevel() == ScanResult.Level.MALICIOUS && m.file != null)
                .map(m -> Path.of(m.file).toAbsolutePath().toString()).toList();
        report.system.addAll(SystemCheck.run(maliciousFiles));

        boolean show = config.alwaysShow || report.anyMalicious() || report.anySuspicious()
                || (config.showNotOriginal && report.anyNotOriginal());
        if (!show) {
            System.out.println(LOG + report.mods.size() + " Mods geprüft, keine Auffälligkeiten.");
            return;
        }

        String text = report.toText();
        System.out.println(LOG + "\n" + text);
        try {
            Files.writeString(gameDir.resolve(REPORT), text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // nur Komfort
        }

        String decision = askUser(report, gameDir, loader, ownJar, config);
        switch (decision) {
            case "proceed" -> System.out.println(LOG + "Start trotz Funden vom Benutzer erlaubt.");
            case "quarantine" -> throw new IllegalStateException(LOG + "Start abgebrochen - markierte Mods werden nach "
                    + gameDir.resolve("silentnet-quarantine") + " verschoben.\n\n" + text);
            default -> throw new IllegalStateException(LOG + (report.anyMalicious()
                    ? "Schädliche Mod/Infektion erkannt - Start abgebrochen. "
                    : "Start vom Benutzer abgebrochen. ") + "Details in " + gameDir.resolve(REPORT) + "\n\n" + text);
        }
    }

    private static Report scanMods(Path modsDir, Path ownJar, GuardConfig config) {
        Report report = new Report();
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> files = Files.walk(modsDir, 2)) {
            files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(p))
                    .filter(p -> ownJar == null || !sameFile(p, ownJar))
                    .forEach(jars::add);
        } catch (IOException e) {
            report.errors.add(modsDir + ": " + e);
        }
        for (Path jar : jars) {
            try {
                ScanResult r = JarScanner.scan(jar);
                applyAllowlist(r, config);
                report.mods.add(r);
            } catch (IOException | RuntimeException e) {
                report.errors.add(jar + ": " + e);
            }
        }
        return report;
    }

    private static void applyAllowlist(ScanResult r, GuardConfig config) {
        if (config.allowlist.contains(r.sha256)) {
            r.allowlisted = true;
        }
        for (ScanResult c : r.children) {
            if (r.allowlisted) {
                c.allowlisted = true;
            }
            applyAllowlist(c, config);
        }
    }

    /** Zeigt das Prüf-Fenster in einem eigenen Prozess und liefert proceed/abort/quarantine. */
    private static String askUser(Report report, Path gameDir, FabricLoader loader, Path ownJar, GuardConfig config) {
        boolean uiPossible = loader.getEnvironmentType() == EnvType.CLIENT && !GraphicsEnvironment.isHeadless()
                && ownJar != null && Files.isRegularFile(ownJar) && !Boolean.getBoolean("silentnetguard.noui");
        if (uiPossible) {
            try {
                Path reportFile = Files.createTempFile("silentnet-guard", ".report");
                reportFile.toFile().deleteOnExit();
                report.write(reportFile);
                ProcessBuilder pb = new ProcessBuilder(javaBinary(), "-cp", ownJar.toString(),
                        ReviewScreen.class.getName(), reportFile.toString(), String.valueOf(ProcessHandle.current().pid()),
                        gameDir.resolve("silentnet-quarantine").toString(), config.allowlistFile.toString());
                Files.createDirectories(gameDir.resolve("logs"));
                pb.redirectError(gameDir.resolve("logs").resolve("silentnet-guard-ui.log").toFile());
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("DECISION:")) {
                            return line.substring("DECISION:".length()).trim();
                        }
                    }
                }
                System.out.println(LOG + "Prüf-Fenster ohne Entscheidung beendet (Exit-Code " + p.waitFor() + ").");
                return "abort";
            } catch (IOException e) {
                System.out.println(LOG + "Prüf-Fenster konnte nicht gestartet werden: " + e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "abort";
            }
        }
        // Kein Fenster möglich (Server, headless): bei Malware abbrechen, sonst nur warnen.
        return report.anyMalicious() ? "abort" : "proceed";
    }

    private static String javaBinary() {
        String cmd = ProcessHandle.current().info().command().orElse(null);
        if (cmd != null && Files.isExecutable(Path.of(cmd))) {
            return cmd;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString();
    }

    private static Path ownJar() {
        try {
            return Path.of(SilentNetGuard.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }
}
