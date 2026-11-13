package de.lumiwork.silentac.check;

import de.lumiwork.silentac.PlayerData;
import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.SilentAC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public final class SpeedCheck implements Listener {

    // Ground movement per tick is roughly 2.16x the movement_speed attribute (walk 0.216, sprint ~0.281).
    private static final double GROUND_FACTOR = 2.16;
    // Sprint-jumping briefly carries more momentum than ground movement.
    private static final double AIR_FACTOR = 1.65;
    private static final double ICE_FACTOR = 2.6;
    private static final double BLOCK_ABOVE_FACTOR = 1.4;

    private final SilentAC plugin;

    public SpeedCheck(SilentAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("speed.enabled", true)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance == 0) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        int tick = Bukkit.getCurrentTick();
        updateEnvironment(player, data, to, tick);
        data.lastMoveDistance = distance;
        data.lastMoveTick = tick;

        // Elytra and riptide are not limited, but their momentum is carried into the next ticks.
        if (player.isGliding() || player.isRiptiding()) {
            data.momentum = distance;
            data.momentumTick = tick;
            data.resetBuffer(CheckType.SPEED);
            return;
        }
        if (player.isHandRaised() && Weapons.isKinetic(player.getActiveItem())) {
            data.lastWeaponTick = tick;
        }

        if (isExempt(player, data, tick)) {
            data.resetBuffer(CheckType.SPEED);
            return;
        }

        double limit = limit(player, data, tick) + plugin.getConfig().getDouble("speed.tolerance", 0.03)
                + plugin.bedrockExtra(player, "speed");
        if (distance > limit) {
            double buffer = data.addBuffer(CheckType.SPEED, 1);
            if (buffer > plugin.getConfig().getInt("speed.buffer", 6)) {
                double over = distance - limit;
                plugin.alerts().flag(player, CheckType.SPEED, Math.min(5, 1 + over * 10),
                        String.format("dist=%.3f max=%.3f", distance, limit));
                data.addBuffer(CheckType.SPEED, -2);
            }
        } else {
            data.addBuffer(CheckType.SPEED, -0.25);
        }
    }

    private double limit(Player player, PlayerData data, int tick) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        double speed = attribute != null ? attribute.getValue() : 0.1;
        if (!player.isSprinting()) {
            // Sprint state can lag a tick behind the client; treat as sprinting.
            speed *= 1.3;
        }
        double limit = speed * GROUND_FACTOR;
        // Air momentum carries over for a few ticks after landing.
        if (tick - data.lastAirTick < 5) {
            limit *= AIR_FACTOR;
        }
        if (tick - data.lastIceTick < 40) {
            limit *= ICE_FACTOR;
        }
        if (tick - data.lastBlockAboveTick < 20) {
            limit *= BLOCK_ABOVE_FACTOR;
        }
        if (tick - data.lastLiquidTick < 20) {
            limit = Math.max(limit, 0.6);
        }
        // Decay per received move (not per server tick) so bundled packets under lag stay in step with the client.
        if (data.momentum > 0 && tick - data.momentumTick < 200) {
            data.momentum *= player.isOnGround() ? 0.6 : 0.91;
            limit = Math.max(limit, data.momentum + 0.05);
            if (data.momentum < 0.05) {
                data.momentum = 0;
            }
        }
        return limit;
    }

    private boolean isExempt(Player player, PlayerData data, int tick) {
        return player.hasPermission("silentac.bypass")
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isFlying()
                || player.getAllowFlight()
                || player.isInsideVehicle()
                || tick - data.lastWeaponTick < plugin.getConfig().getInt("speed.weapon-exempt-ticks", 20)
                || player.isDead()
                || tick < data.exemptUntilTick
                || tick - data.lastBounceTick < 40;
    }

    private void updateEnvironment(Player player, PlayerData data, Location to, int tick) {
        Block feet = to.getBlock();
        Block below = feet.getRelative(0, -1, 0);
        if (!player.isOnGround()) {
            data.lastAirTick = tick;
        }
        if (Tag.ICE.isTagged(below.getType()) || Tag.ICE.isTagged(feet.getRelative(0, -2, 0).getType())) {
            data.lastIceTick = tick;
        }
        Material belowType = below.getType();
        if (belowType == Material.SLIME_BLOCK || Tag.BEDS.isTagged(belowType)) {
            data.lastBounceTick = tick;
        }
        if (!feet.getRelative(0, 2, 0).isPassable()) {
            data.lastBlockAboveTick = tick;
        }
        if (feet.isLiquid() || player.isSwimming() || player.isInWater()) {
            data.lastLiquidTick = tick;
        }
    }

    private void exempt(Player player, int ticks) {
        PlayerData data = plugin.data(player);
        data.exemptUntilTick = Math.max(data.exemptUntilTick, Bukkit.getCurrentTick() + ticks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        exempt(event.getPlayer(), 10);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        double horizontal = Math.hypot(event.getVelocity().getX(), event.getVelocity().getZ());
        exempt(event.getPlayer(), 20 + (int) (horizontal * 40));
    }

    // Spear lunge / jab moves the player forward.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (Weapons.holdsSpecial(event.getPlayer())) {
            plugin.data(event.getPlayer()).lastWeaponTick = Bukkit.getCurrentTick();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        exempt(event.getPlayer(), 60);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        exempt(event.getPlayer(), 40);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        exempt(event.getPlayer(), 40);
    }
}
