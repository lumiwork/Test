package de.lumiwork.silentnetguard.scan;

import java.io.Serializable;

/** Ergebnis der Herkunftsprüfung (InjectedMod Assist). */
public record Origin(Status status, String detail) implements Serializable {

    public enum Status {
        ORIGINAL("Original"),
        KNOWN_LIBRARY("Bekannte Library"),
        TRUSTED("Vertrauenswuerdig (Allowlist)"),
        MODIFIED("NICHT original"),
        UNKNOWN("Unbekannt"),
        OFFLINE("Nicht prüfbar (offline)"),
        NOT_CHECKED("Nicht geprüft");

        public final String label;

        Status(String label) {
            this.label = label;
        }
    }

    public static Origin notChecked() {
        return new Origin(Status.NOT_CHECKED, "");
    }
}
