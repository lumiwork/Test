package de.lumiwork.silentac;

import de.lumiwork.silentac.PlayerData.CheckType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public final class AlertManager {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SilentAC plugin;
    private final Path logFile;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

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
        sendWebhook(String.format("**%s** failed **%s** (VL %.1f) `%s`", player.getName(), type, vl, details));
    }

    private void sendWebhook(String content) {
        if (!plugin.getConfig().getBoolean("webhook.enabled", false)) {
            return;
        }
        String url = plugin.getConfig().getString("webhook.url", "");
        if (url.isBlank()) {
            return;
        }
        String body = "{\"username\":" + json(plugin.getConfig().getString("webhook.username", "SilentAC"))
                + ",\"content\":" + json(content) + "}";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid webhook.url in config.yml");
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Webhook failed: " + error.getMessage());
            } else if (response.statusCode() >= 300) {
                plugin.getLogger().warning("Webhook returned HTTP " + response.statusCode());
            }
        });
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
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
