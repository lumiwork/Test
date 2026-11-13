package de.lumiwork.silentac.check;

import de.lumiwork.silentac.PlayerData;
import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.SilentAC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

/**
 * Block reach, fast break (Meteor SpeedMine) and nuker detection.
 */
public final class BlockCheck implements Listener {

    private final SilentAC plugin;

    public BlockCheck(SilentAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        Block block = event.getBlock();
        PlayerData data = plugin.data(player);
        int tick = Bukkit.getCurrentTick();
        checkReach(player, block, "dig");

        if (data.digStartsTick != tick) {
            data.digStartsTick = tick;
            data.digStartsThisTick = 0;
        }
        data.digStartsThisTick++;
        // A vanilla client starts digging at most one block per tick.
        if (config().getBoolean("nuker.enabled", true) && data.digStartsThisTick > 1) {
            flagBuffered(player, data, CheckType.NUKER, 1, config().getInt("nuker.buffer", 2),
                    "multi-dig starts=" + data.digStartsThisTick + "/tick");
        }
        if (!event.getInstaBreak()) {
            data.digPos = Geometry.pack(block);
            data.digStartTick = tick;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        Block block = event.getBlock();
        PlayerData data = plugin.data(player);
        int tick = Bukkit.getCurrentTick();
        checkReach(player, block, "break");

        float breakSpeed = block.getBreakSpeed(player);
        boolean instant = breakSpeed >= 1f;
        long pos = Geometry.pack(block);

        if (!instant) {
            if (data.breaksTick != tick) {
                data.breaksTick = tick;
                data.breaksThisTick = 0;
            }
            data.breaksThisTick++;
            if (config().getBoolean("nuker.enabled", true) && data.breaksThisTick > 1) {
                flagBuffered(player, data, CheckType.NUKER, 2, 0, "multi-break " + data.breaksThisTick + "/tick");
            }
            checkFastBreak(player, data, block, breakSpeed, pos, tick);
        }
        checkLookingAt(player, data, block);

        data.lastBreakPos = pos;
        data.lastBreakTick = tick;
        data.digPos = Long.MIN_VALUE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!exempt(player)) {
            checkReach(player, event.getBlockAgainst(), "place");
        }
    }

    // Meteor SpeedMine "grim bypass" aborts digging the block above right after finishing a break.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        Player player = event.getPlayer();
        if (exempt(player) || !config().getBoolean("fast-break.enabled", true)) {
            return;
        }
        PlayerData data = plugin.data(player);
        Block block = event.getBlock();
        if (Bukkit.getCurrentTick() - data.lastBreakTick <= 1
                && data.lastBreakPos == Geometry.pack(block.getX(), block.getY() - 1, block.getZ())
                && data.digPos != Geometry.pack(block)) {
            plugin.alerts().flag(player, CheckType.FAST_BREAK, 3, "abort-above signature (Meteor SpeedMine)");
        }
    }

    private void checkFastBreak(Player player, PlayerData data, Block block, float breakSpeed, long pos, int tick) {
        if (!config().getBoolean("fast-break.enabled", true)) {
            return;
        }
        if (data.digPos != pos) {
            // Breaking a non-instant block that was never started (packet mine / nuker).
            flagBuffered(player, data, CheckType.NUKER, 1.5, config().getInt("nuker.buffer", 2),
                    "break without dig start " + block.getType());
            return;
        }
        int expected = (int) Math.ceil(1.0 / breakSpeed);
        if (expected < config().getInt("fast-break.min-ticks", 4)) {
            return;
        }
        int elapsed = tick - data.digStartTick;
        double ratio = elapsed / (double) expected;
        if (ratio < config().getDouble("fast-break.min-ratio", 0.85)) {
            flagBuffered(player, data, CheckType.FAST_BREAK, 1 + (1 - ratio) * 5, config().getInt("fast-break.buffer", 2),
                    String.format("%s %d/%d ticks (%.0f%%)", block.getType(), elapsed, expected, ratio * 100));
        } else {
            data.addBuffer(CheckType.FAST_BREAK, -0.5);
        }
    }

    private void checkLookingAt(Player player, PlayerData data, Block block) {
        if (!config().getBoolean("nuker.enabled", true)) {
            return;
        }
        Vector eye = player.getEyeLocation().toVector();
        Vector toBlock = block.getBoundingBox().getCenter().subtract(eye);
        if (toBlock.lengthSquared() < 2.25) {
            return;
        }
        double angle = Math.toDegrees(player.getEyeLocation().getDirection().angle(toBlock));
        if (angle > config().getDouble("nuker.max-angle", 60)) {
            flagBuffered(player, data, CheckType.NUKER, 1, config().getInt("nuker.buffer", 2),
                    String.format("break not looking angle=%.0f %s", angle, block.getType()));
        } else {
            data.addBuffer(CheckType.NUKER, -0.1);
        }
    }

    private void checkReach(Player player, Block block, String action) {
        if (!config().getBoolean("block-reach.enabled", true)) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double max = (attribute != null ? attribute.getValue() : 4.5) + config().getDouble("block-reach.tolerance", 0.5)
                + plugin.bedrockExtra(player, "block-reach");
        double distance = Geometry.distanceToBox(player.getEyeLocation().toVector(), Geometry.fullBox(block));
        if (distance > max) {
            PlayerData data = plugin.data(player);
            flagBuffered(player, data, CheckType.BLOCK_REACH, 1 + (distance - max) * 2, config().getInt("block-reach.buffer", 1),
                    String.format("%s reach=%.2f max=%.2f %s", action, distance, max, block.getType()));
        }
    }

    private void flagBuffered(Player player, PlayerData data, CheckType type, double vl, int bufferLimit, String details) {
        if (data.addBuffer(type, 1) > bufferLimit) {
            plugin.alerts().flag(player, type, vl, details);
        }
    }

    private boolean exempt(Player player) {
        return player.hasPermission("silentac.bypass") || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}
