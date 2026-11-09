package de.lumiwork.silentac.check;

import de.lumiwork.silentac.PlayerData;
import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.SilentAC;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Detects Meteor-style rotations: Rotations.rotate() sends the aim rotation, performs the action and
 * restores the real camera yaw on the next movement packet, which shows up server-side as a large
 * snap followed by a snap straight back. Also checks KillAura hits without aiming or through walls.
 */
public final class RotationCheck implements Listener {

    private final SilentAC plugin;

    public RotationCheck(SilentAC plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        int tick = Bukkit.getCurrentTick();
        boolean snapEnabled = config().getBoolean("rotation.enabled", true);
        float snap = (float) config().getDouble("rotation.snap-degrees", 30);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data(player);
            Location location = player.getLocation();
            data.prevTickYaw = data.tickYaw;
            data.tickYaw = location.getYaw();

            if (snapEnabled && Math.abs(location.getPitch()) > 90.01f) {
                plugin.alerts().flag(player, CheckType.ROTATION, 5, String.format("invalid pitch=%.1f", location.getPitch()));
            }
            if (!snapEnabled || tick - data.snapTick > 4 || tick == data.snapTick) {
                continue;
            }
            float back = Geometry.yawDiff(data.tickYaw, data.snapPreYaw);
            float away = Geometry.yawDiff(data.tickYaw, data.snapActionYaw);
            if (back < 5f && away > snap * 0.8f) {
                data.snapTick = -1000;
                if (data.addBuffer(CheckType.ROTATION, 1) > config().getInt("rotation.buffer", 2)) {
                    plugin.alerts().flag(player, CheckType.ROTATION, 2,
                            String.format("snap-back %s %.0f° and back", data.snapAction,
                                    Geometry.yawDiff(data.snapPreYaw, data.snapActionYaw)));
                }
            }
        }
    }

    private void recordAction(Player player, String action) {
        PlayerData data = plugin.data(player);
        float now = player.getLocation().getYaw();
        // tickYaw was sampled before this tick's packets; it is the yaw before the action rotation.
        float pre = data.tickYaw;
        if (Geometry.yawDiff(now, pre) > config().getDouble("rotation.snap-degrees", 30)) {
            data.snapTick = Bukkit.getCurrentTick();
            data.snapPreYaw = pre;
            data.snapActionYaw = now;
            data.snapAction = action;
        } else {
            data.addBuffer(CheckType.ROTATION, -0.05);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player) || exempt(player)) {
            return;
        }
        recordAction(player, "attack");
        if (!config().getBoolean("killaura.enabled", true)) {
            return;
        }
        Entity target = event.getEntity();
        PlayerData data = plugin.data(player);
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        BoundingBox box = target.getBoundingBox().expand(config().getDouble("killaura.hitbox-expand", 0.5));
        double maxDistance = 7;

        if (!box.contains(origin) && box.rayTrace(origin, eye.getDirection(), maxDistance) == null) {
            Vector toTarget = target.getBoundingBox().getCenter().subtract(origin);
            double angle = Math.toDegrees(eye.getDirection().angle(toTarget));
            if (data.addBuffer(CheckType.KILLAURA, 1) > config().getInt("killaura.buffer", 3)) {
                plugin.alerts().flag(player, CheckType.KILLAURA, 1.5,
                        String.format("hit without aiming angle=%.0f %s", angle, target.getType()));
            }
        } else {
            data.addBuffer(CheckType.KILLAURA, -0.25);
        }

        if (throughWalls(player, target)) {
            if (data.addBuffer(CheckType.KILLAURA, 1) > config().getInt("killaura.buffer", 3)) {
                plugin.alerts().flag(player, CheckType.KILLAURA, 2, "hit through wall " + target.getType());
            }
        }
    }

    private boolean throughWalls(Player player, Entity target) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        BoundingBox box = target.getBoundingBox();
        Vector[] points = {
                box.getCenter(),
                new Vector(box.getCenterX(), box.getMaxY() - 0.1, box.getCenterZ()),
                new Vector(box.getCenterX(), box.getMinY() + 0.1, box.getCenterZ()),
                new Vector(Geometry.clamp(origin.getX(), box.getMinX(), box.getMaxX()),
                        Geometry.clamp(origin.getY(), box.getMinY(), box.getMaxY()),
                        Geometry.clamp(origin.getZ(), box.getMinZ(), box.getMaxZ()))
        };
        for (Vector point : points) {
            Vector direction = point.clone().subtract(origin);
            double distance = direction.length();
            if (distance < 0.01) {
                return false;
            }
            RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction.normalize(), distance,
                    FluidCollisionMode.NEVER, true);
            if (hit == null) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!exempt(event.getPlayer())) {
            recordAction(event.getPlayer(), "break");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!exempt(event.getPlayer())) {
            recordAction(event.getPlayer(), "place");
        }
    }

    private boolean exempt(Player player) {
        return player.hasPermission("silentac.bypass") || player.getGameMode() == GameMode.SPECTATOR;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}
