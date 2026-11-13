package de.lumiwork.silentac.feature;

import de.lumiwork.silentac.SilentAC;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tags players in PvP. Logging out while tagged kills the player (items drop) and is announced.
 */
public final class CombatLog implements Listener {

    private final SilentAC plugin;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private final Map<UUID, String> lastOpponent = new HashMap<>();
    private final Set<UUID> kicked = new HashSet<>();

    public CombatLog(SilentAC plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("combat-log");
        return section != null ? section : plugin.getConfig().createSection("combat-log");
    }

    public boolean isTagged(Player player) {
        return taggedUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public long secondsLeft(Player player) {
        return Math.max(0, (taggedUntil.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis() + 999) / 1000);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config().getBoolean("enabled", true) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        tag(victim, attacker);
        tag(attacker, victim);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }

    private void tag(Player player, Player opponent) {
        if (player.hasPermission("silentac.combat.bypass") || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean wasTagged = isTagged(player);
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + config().getLong("duration-seconds", 15) * 1000);
        lastOpponent.put(player.getUniqueId(), opponent.getName());
        if (!wasTagged) {
            plugin.message(player, config().getString("messages.tagged", ""),
                    Placeholder.unparsed("opponent", opponent.getName()));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = taggedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (entry.getValue() <= now) {
                it.remove();
                lastOpponent.remove(entry.getKey());
                plugin.message(player, config().getString("messages.untagged", ""));
            } else {
                String actionbar = config().getString("messages.actionbar", "");
                if (!actionbar.isEmpty()) {
                    player.sendActionBar(plugin.mini(actionbar,
                            Placeholder.unparsed("seconds", String.valueOf(secondsLeft(player)))));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isTagged(player) || player.hasPermission("silentac.combat.bypass")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        List<String> allowed = config().getStringList("allowed-commands");
        if (!allowed.contains(label)) {
            event.setCancelled(true);
            plugin.message(player, config().getString("messages.command-blocked", ""),
                    Placeholder.unparsed("seconds", String.valueOf(secondsLeft(player))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        taggedUntil.remove(event.getEntity().getUniqueId());
        lastOpponent.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        kicked.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean wasKicked = kicked.remove(id);
        if (!isTagged(player)) {
            taggedUntil.remove(id);
            return;
        }
        String opponent = lastOpponent.getOrDefault(id, "?");
        taggedUntil.remove(id);
        lastOpponent.remove(id);
        if (wasKicked || !config().getBoolean("kill-on-logout", true)) {
            return;
        }
        player.setHealth(0);
        String broadcast = config().getString("messages.logout-broadcast", "");
        if (!broadcast.isEmpty()) {
            Bukkit.broadcast(plugin.mini(broadcast, Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("opponent", opponent)));
        }
        plugin.getLogger().info(player.getName() + " combat-logged against " + opponent);
    }
}
