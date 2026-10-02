# SilentNet Guard

Fabric-Mod, die die **SilentNet**-Malware (Minecraft-Session-Token-Stealer, C2 über `sltnnt.ru` bzw.
einen Polygon-Smart-Contract) und ähnlich gebaute Mods erkennt und dann **den Start von Minecraft abbricht**.

## Funktionsweise

Die Mod läuft als `preLaunch`-Entrypoint, also **bevor** die `onInitialize`-Methoden anderer Mods
ausgeführt werden. Sie liest alle `.jar`-Dateien im `mods`-Ordner (auch Unterordner) **statisch** –
es wird nichts davon geladen oder ausgeführt – und vergibt Punkte:

| Merkmal | Punkte |
|---|---|
| Bekannter SHA-256 eines Samples | 100 |
| Tarn-Metadaten (`"id": "package"`, „Core library module“) | 40 |
| Liest den Session-Token (`class_320.method_1674` = `Session.getAccessToken`) | 30 |
| Startet sich selbst als eigenen Prozess (`ProcessBuilder` + `getCodeSource`) | 30 |
| Große verschlüsselte Payload-Datei (≥ 256 KB, Entropie ≥ 7,9, kein Bild/Sound/Archiv) | 30 |

Ab **60 Punkten** gilt eine Mod als schädlich. Ein einzelnes Merkmal reicht also nie (außer dem bekannten Hash),
dadurch werden auch abgewandelte Varianten erkannt, ohne dass z. B. Account-Switcher-Mods Fehlalarm auslösen.

Zusätzlich wird das System geprüft:
- Existiert `%LOCALAPPDATA%\Microsoft\Windows\NtProfileIndex`, ist die Malware schon gelaufen.
- Läuft ein Prozess, dessen Kommandozeile `NtProfileIndex` oder den Pfad einer erkannten JAR enthält,
  wird er beendet.

Bei einem Fund wird `silentnet-guard-report.txt` im Spielordner geschrieben und Minecraft gecrasht
(der Bericht steht auch im Crash-Report).

**Fehlalarm?** Den SHA-256 aus dem Bericht in `config/silentnet-guard-allow.txt` eintragen (eine Zeile pro Hash).

## Grenzen

- Die Reihenfolge von `preLaunch`-Entrypoints verschiedener Mods ist nicht garantiert. Diese Malware nutzt nur
  den `main`-Entrypoint, eine künftige Variante könnte aber früher starten. Die Mod ersetzt keinen Virenscanner.
- Erkennung ist heuristisch; stark veränderte Varianten können durchrutschen.

## Bauen

```
./gradlew build
```

Die fertige Mod liegt danach in `build/libs/silentnet-guard-1.0.0.jar` und kommt in den `mods`-Ordner.

Ohne Minecraft einzelne Dateien prüfen:

```
./gradlew scan -Pjars="C:/Pfad/zu/verdacht.jar,C:/Pfad/zu/anderer.jar"
```

## Indikatoren (IOCs) des analysierten Samples

- SHA-256: `f47fecd6b5107111e518c2ceec31d07cfca170f69fcd991dc175c78b65a384c0` (`auto-schematic-builder-1.21.11.jar`)
- Domain: `sltnnt.ru`, Pfad `/cdn/v2/9f4e7a2c1b8d.png`
- Polygon-Contract (C2-Adresse): `0x9c0a507300fd902787bb193d80fca5ce6e1bff9a`, Selector `0xce6d41de`
- Ordner: `%LOCALAPPDATA%\Microsoft\Windows\NtProfileIndex` (`_spawn.log`, `.cache_idx`)
- Payload in der JAR: `assets/naytzuea.cache`
