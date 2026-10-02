package de.lumiwork.silentnetguard.scan;

/** Art eines Fundes. */
public enum Category {
    KNOWN_MALWARE("Bekannte Malware"),
    TOKEN("Minecraft-Token-Diebstahl"),
    STEALER("Datendiebstahl"),
    EXFIL("Datenversand / C2"),
    RAT("Fernsteuerung (RAT)"),
    PERSISTENCE("Autostart / Einnistung"),
    INJECTION("Eingeschleuster Code"),
    OBFUSCATION("Verschleierung"),
    ORIGIN("Herkunft");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
