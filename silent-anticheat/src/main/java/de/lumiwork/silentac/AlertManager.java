package de.lumiwork.silentac;

import de.lumiwork.silentac.PlayerData.CheckType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public final class AlertManager {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SilentAC plugin;
    private final Path logFile;

    public AlertManager(SilentAC plugin) {
        this.plugin = plugin;
        this.logFile = plugin.getDataFolder().toPath().resolve("violations.log");
    }

    public void flag(Player player, CheckType type, double vlAdd, String details) {
        PlayerData data = plugin.data(player);
        double vl = data.addVl(type, vlAdd);
        String line = String.format("%s %s failed %s (VL %.1f) %s",
                LocalDateTime.now().format(TIME), player.getName(), type, vl, details);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeLog(line));

        if (vl < plugin.getConfig().getDouble("alerts.min-vl", 3)
                || !data.alertReady(type, System.currentTimeMillis(), plugin.getConfig().getLong("alerts.cooldown-ms", 2000))) {
            return;
        }
        Component message = Component.text("[SilentAC] ", NamedTextColor.DARK_RED)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" failed ", NamedTextColor.GRAY))
                .append(Component.text(type.name(), NamedTextColor.RED))
                .append(Component.text(String.format(" VL %.1f ", vl), NamedTextColor.GOLD))
                .append(Component.text(details, NamedTextColor.DARK_GRAY));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("silentac.alerts") && plugin.data(staff).alertsEnabled) {
                staff.sendMessage(message);
            }
        }
        plugin.getLogger().info(line);
    }

    private synchronized void writeLog(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write violations.log", e);
        }
    }
}
