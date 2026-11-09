package de.lumiwork.silentac.check;

import de.lumiwork.silentac.PlayerData;
import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.SilentAC;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class ReachCheck implements Listener {

    // Vanilla grows the target hitbox slightly for hit detection.
    private static final double HITBOX_MARGIN = 0.1;

    private final SilentAC plugin;

    public ReachCheck(SilentAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("reach.enabled", true)
                || event.getCause() != DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player player)) {
            return;
        }
        Entity target = event.getEntity();
        if (player.hasPermission("silentac.bypass")
                || player.getGameMode() == GameMode.CREATIVE
                || player.isInsideVehicle()
                || target.isInsideVehicle()
                || !player.getWorld().equals(target.getWorld())) {
            return;
        }
        int ping = player.getPing();
        if (ping > plugin.getConfig().getInt("reach.max-ping", 300)) {
            return;
        }

        double distance = distanceToBox(player.getEyeLocation().toVector(), target.getBoundingBox().expand(HITBOX_MARGIN));
        double limit = baseReach(player)
                + plugin.getConfig().getDouble("reach.tolerance", 0.3)
                + (ping / 50.0) * plugin.getConfig().getDouble("reach.ping-compensation-per-tick", 0.1)
                + horizontalSpeed(target.getVelocity()) * 2;

        PlayerData data = plugin.data(player);
        if (distance > limit) {
            double buffer = data.addBuffer(CheckType.REACH, 1);
            if (buffer > plugin.getConfig().getInt("reach.buffer", 2)) {
                plugin.alerts().flag(player, CheckType.REACH, Math.min(5, 1 + (distance - limit) * 4),
                        String.format("reach=%.2f max=%.2f ping=%d target=%s", distance, limit, ping, target.getType()));
            }
        } else {
            data.addBuffer(CheckType.REACH, -0.25);
        }
    }

    private static double baseReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        return attribute != null ? attribute.getValue() : 3.0;
    }

    private static double horizontalSpeed(Vector velocity) {
        return Math.hypot(velocity.getX(), velocity.getZ());
    }

    private static double distanceToBox(Vector point, BoundingBox box) {
        double x = clamp(point.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(point.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(point.getZ(), box.getMinZ(), box.getMaxZ());
        return point.distance(new Vector(x, y, z));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
