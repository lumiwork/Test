package de.lumiwork.silentnetguard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Prueft, ob die Malware bereits gelaufen ist (Ordner, laufende Prozesse). */
final class SystemCheck {

    private SystemCheck() {
    }

    static List<String> run(List<ModScanner.Result> hits) {
        List<String> out = new ArrayList<>();

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Path dropDir = Path.of(localAppData, "Microsoft", "Windows", "NtProfileIndex");
            if (Files.exists(dropDir)) {
                out.add("Malware-Ordner gefunden: " + dropDir + " -> der Stealer ist sehr wahrscheinlich schon gelaufen!");
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
            boolean suspicious = cmd.contains("NtProfileIndex");
            for (ModScanner.Result r : hits) {
                if (cmd.contains(r.file().toAbsolutePath().toString())) {
                    suspicious = true;
                }
            }
            if (suspicious) {
                boolean killed = p.destroyForcibly();
                out.add("Malware-Prozess PID " + p.pid() + (killed ? " beendet" : " (konnte nicht beendet werden)") + ": " + cmd);
            }
        });
        return out;
    }
}
