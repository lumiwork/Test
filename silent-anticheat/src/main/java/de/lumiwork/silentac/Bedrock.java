package de.lumiwork.silentac;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players joining through Geyser/Floodgate without a compile-time dependency.
 */
public final class Bedrock {

    private final SilentAC plugin;
    private Object floodgate;
    private Method floodgateCheck;
    private Object geyser;
    private Method geyserCheck;

    public Bedrock(SilentAC plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgate = api.getMethod("getInstance").invoke(null);
                floodgateCheck = api.getMethod("isFloodgatePlayer", UUID.class);
                plugin.getLogger().info("Floodgate found: Bedrock players get the bedrock profile.");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Floodgate found but its API could not be loaded: " + e);
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyser = api.getMethod("api").invoke(null);
                geyserCheck = api.getMethod("isBedrockPlayer", UUID.class);
                plugin.getLogger().info("Geyser found: Bedrock players get the bedrock profile.");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Geyser found but its API could not be loaded: " + e);
        }
    }

    public boolean isBedrock(Player player) {
        if (!plugin.getConfig().getBoolean("bedrock.enabled", true)) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (call(floodgate, floodgateCheck, id) || call(geyser, geyserCheck, id)) {
            return true;
        }
        // Floodgate gives Bedrock players UUIDs that start with zeros, and usually a name prefix.
        String prefix = plugin.getConfig().getString("bedrock.name-prefix", ".");
        return id.getMostSignificantBits() == 0
                || (prefix != null && !prefix.isEmpty() && player.getName().startsWith(prefix));
    }

    private static boolean call(Object api, Method method, UUID id) {
        if (api == null) {
            return false;
        }
        try {
            return (boolean) method.invoke(api, id);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
