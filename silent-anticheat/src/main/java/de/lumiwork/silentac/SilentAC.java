package de.lumiwork.silentac;

import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.check.BlockCheck;
import de.lumiwork.silentac.check.ReachCheck;
import de.lumiwork.silentac.check.RotationCheck;
import de.lumiwork.silentac.check.SpeedCheck;
import de.lumiwork.silentac.xray.AntiXray;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SilentAC extends JavaPlugin implements Listener {

    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private AlertManager alerts;
    private AntiXray antiXray;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        alerts = new AlertManager(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new SpeedCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new ReachCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new RotationCheck(this), this);
        antiXray = new AntiXray(this);
        Bukkit.getPluginManager().registerEvents(antiXray, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double decay = getConfig().getDouble("vl-decay-per-minute", 2.0);
            players.values().forEach(data -> data.decay(decay));
        }, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (antiXray != null) {
            antiXray.shutdown();
        }
    }

    public PlayerData data(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), id -> new PlayerData());
    }

    public AlertManager alerts() {
        return alerts;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase()) {
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can toggle alerts.");
                    return true;
                }
                PlayerData data = data(player);
                data.alertsEnabled = !data.alertsEnabled;
                sender.sendMessage(prefix().append(Component.text("Alerts " + (data.alertsEnabled ? "on" : "off"), NamedTextColor.GRAY)));
            }
            case "vl", "reset" -> {
                if (args.length < 2) {
                    return false;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix().append(Component.text("Player not online.", NamedTextColor.GRAY)));
                    return true;
                }
                PlayerData data = data(target);
                if (args[0].equalsIgnoreCase("reset")) {
                    data.reset();
                    sender.sendMessage(prefix().append(Component.text("VL of " + target.getName() + " reset.", NamedTextColor.GRAY)));
                } else {
                    String vls = Arrays.stream(CheckType.values())
                            .map(type -> String.format("%s %.1f", type, data.vl(type)))
                            .collect(Collectors.joining(" | "));
                    sender.sendMessage(prefix().append(Component.text(target.getName() + ": " + vls, NamedTextColor.GRAY)));
                }
            }
            case "reload" -> {
                reloadConfig();
                antiXray.reload();
                sender.sendMessage(prefix().append(Component.text("Config reloaded.", NamedTextColor.GRAY)));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("alerts", "vl", "reset", "reload").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase())).toList();
        }
        return null;
    }

    private static Component prefix() {
        return Component.text("[SilentAC] ", NamedTextColor.DARK_RED);
    }
}
