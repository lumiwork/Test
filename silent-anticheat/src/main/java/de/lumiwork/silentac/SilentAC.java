package de.lumiwork.silentac;

import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.check.BlockCheck;
import de.lumiwork.silentac.check.ReachCheck;
import de.lumiwork.silentac.check.RotationCheck;
import de.lumiwork.silentac.check.SpeedCheck;
import de.lumiwork.silentac.feature.AdminGui;
import de.lumiwork.silentac.feature.BanManager;
import de.lumiwork.silentac.feature.CombatLog;
import de.lumiwork.silentac.feature.DragonManager;
import de.lumiwork.silentac.xray.AntiXray;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SilentAC extends JavaPlugin implements Listener {

    private static final List<String> SUBCOMMANDS = List.of("admin", "alerts", "vl", "reset", "ban", "unban", "reload");

    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private AlertManager alerts;
    private AntiXray antiXray;
    private Bedrock bedrock;
    private CombatLog combatLog;
    private BanManager bans;
    private AdminGui adminGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        alerts = new AlertManager(this);
        bedrock = new Bedrock(this);
        combatLog = new CombatLog(this);
        bans = new BanManager(this);
        adminGui = new AdminGui(this);
        antiXray = new AntiXray(this);
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new SpeedCheck(this), this);
        pm.registerEvents(new ReachCheck(this), this);
        pm.registerEvents(new BlockCheck(this), this);
        pm.registerEvents(new RotationCheck(this), this);
        pm.registerEvents(antiXray, this);
        pm.registerEvents(new DragonManager(this), this);
        pm.registerEvents(combatLog, this);
        pm.registerEvents(bans, this);
        pm.registerEvents(adminGui, this);
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

    public Bedrock bedrock() {
        return bedrock;
    }

    public CombatLog combatLog() {
        return combatLog;
    }

    public BanManager bans() {
        return bans;
    }

    /** Extra tolerance from bedrock.extra-tolerance.&lt;key&gt; for Bedrock players, 0 for Java players. */
    public double bedrockExtra(Player player, String key) {
        return bedrock.isBedrock(player) ? getConfig().getDouble("bedrock.extra-tolerance." + key, 0) : 0;
    }

    public Component mini(String text, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(text == null ? "" : text, resolvers);
    }

    public void message(Player player, String text, TagResolver... resolvers) {
        if (text != null && !text.isEmpty()) {
            player.sendMessage(mini(text, resolvers));
        }
    }

    public void prefixed(CommandSender sender, String text) {
        sender.sendMessage(prefix().append(Component.text(text, NamedTextColor.GRAY)));
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
            case "admin" -> {
                if (!(sender instanceof Player player)) {
                    prefixed(sender, "Nur für Spieler.");
                } else {
                    adminGui.openOverview(player, 0);
                }
            }
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    prefixed(sender, "Nur für Spieler.");
                    return true;
                }
                PlayerData data = data(player);
                data.alertsEnabled = !data.alertsEnabled;
                prefixed(sender, "Alerts " + (data.alertsEnabled ? "an" : "aus"));
            }
            case "vl", "reset" -> {
                if (args.length < 2) {
                    return false;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    prefixed(sender, "Spieler ist nicht online.");
                    return true;
                }
                PlayerData data = data(target);
                if (args[0].equalsIgnoreCase("reset")) {
                    data.reset();
                    prefixed(sender, "VL von " + target.getName() + " zurückgesetzt.");
                } else {
                    String vls = Arrays.stream(CheckType.values())
                            .map(type -> String.format("%s %.1f", type, data.vl(type)))
                            .collect(Collectors.joining(" | "));
                    prefixed(sender, target.getName() + (bedrock.isBedrock(target) ? " (Bedrock)" : "") + ": " + vls);
                }
            }
            case "ban" -> {
                // /silentac ban <player> <template>  or  /silentac ban <player> <duration|perm> <reason...>
                if (args.length < 3) {
                    prefixed(sender, "/silentac ban <Spieler> <Vorlage> | <Dauer|perm> <Grund...>  Vorlagen: "
                            + String.join(", ", bans.templates()));
                    return true;
                }
                OfflinePlayer target = offline(args[1]);
                if (target == null) {
                    prefixed(sender, "Spieler unbekannt: " + args[1]);
                    return true;
                }
                if (bans.templates().contains(args[2])) {
                    bans.banTemplate(sender, target, args[2]);
                } else if (args.length >= 4 && (BanManager.isDuration(args[2]) || args[2].equalsIgnoreCase("perm"))) {
                    Duration duration = BanManager.parseDuration(args[2]);
                    bans.banCustom(sender, target, duration, String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                } else {
                    prefixed(sender, "Unbekannte Vorlage oder Dauer: " + args[2]);
                }
            }
            case "unban" -> {
                if (args.length < 2) {
                    return false;
                }
                OfflinePlayer target = offline(args[1]);
                prefixed(sender, target != null && bans.unban(target) ? args[1] + " entbannt." : args[1] + " ist nicht gebannt.");
            }
            case "reload" -> {
                reloadConfig();
                antiXray.reload();
                prefixed(sender, "Config neu geladen.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Stream<String> options;
        if (args.length == 1) {
            options = SUBCOMMANDS.stream();
        } else if (args.length == 2 && List.of("vl", "reset", "ban", "unban").contains(args[0].toLowerCase())) {
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ban")) {
            List<String> list = new ArrayList<>(bans.templates());
            list.addAll(List.of("perm", "1h", "1d", "7d", "30d"));
            options = list.stream();
        } else {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase();
        return options.filter(option -> option.toLowerCase().startsWith(last)).toList();
    }

    private static Component prefix() {
        return Component.text("[SilentAC] ", NamedTextColor.DARK_RED);
    }
}
