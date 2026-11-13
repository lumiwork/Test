package de.lumiwork.silentac.feature;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.lumiwork.silentac.SilentAC;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bans with configurable templates: each template has a reason, duration, kick screen
 * and a global chat announcement (MiniMessage).
 */
public final class BanManager implements Listener {

    private static final Pattern DURATION = Pattern.compile("(\\d+)([smhdwy])");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final SilentAC plugin;

    public BanManager(SilentAC plugin) {
        this.plugin = plugin;
    }

    public Set<String> templates() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("bans.templates");
        return section != null ? section.getKeys(false) : Set.of();
    }

    /** Parses 30m, 12h, 7d, 2w, 1y (combinable: 1d12h). perm / permanent / empty means forever (null). */
    public static Duration parseDuration(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("perm") || text.equalsIgnoreCase("permanent")) {
            return null;
        }
        Matcher matcher = DURATION.matcher(text.toLowerCase(Locale.ROOT));
        Duration total = Duration.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException(text);
            }
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                case "w" -> Duration.ofDays(amount * 7);
                default -> Duration.ofDays(amount * 365);
            });
            end = matcher.end();
        }
        if (end != text.length() || total.isZero()) {
            throw new IllegalArgumentException(text);
        }
        return total;
    }

    public static boolean isDuration(String text) {
        try {
            parseDuration(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String format(Duration duration) {
        if (duration == null) {
            return "permanent";
        }
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        return parts.isEmpty() ? duration.toSeconds() + "s" : String.join(" ", parts);
    }

    /** Ban using a template from config. */
    public boolean banTemplate(CommandSender staff, OfflinePlayer target, String templateId) {
        ConfigurationSection template = plugin.getConfig().getConfigurationSection("bans.templates." + templateId);
        if (template == null) {
            return false;
        }
        ban(staff, target, template.getString("reason", templateId), parseDuration(template.getString("duration", "perm")),
                template.getString("broadcast", plugin.getConfig().getString("bans.default-broadcast", "")));
        return true;
    }

    /** Ban with a custom reason and duration, announced with bans.default-broadcast. */
    public void banCustom(CommandSender staff, OfflinePlayer target, Duration duration, String reason) {
        ban(staff, target, reason, duration, plugin.getConfig().getString("bans.default-broadcast", ""));
    }

    private void ban(CommandSender staff, OfflinePlayer target, String reason, Duration duration, String broadcast) {
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        String staffName = staff instanceof Player player ? player.getName() : "Console";
        target.ban(reason, duration, staffName);

        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("player", targetName),
                Placeholder.unparsed("reason", reason),
                Placeholder.unparsed("duration", format(duration)),
                Placeholder.unparsed("staff", staffName));
        Player online = target.getPlayer();
        if (online != null) {
            online.kick(screen(reason, duration == null ? null : new Date(System.currentTimeMillis() + duration.toMillis()), staffName));
        }
        if (broadcast != null && !broadcast.isEmpty()) {
            Bukkit.broadcast(plugin.mini(broadcast, resolver));
        }
        plugin.alerts().sendWebhook(String.format("**%s** banned **%s** (%s): %s", staffName, targetName, format(duration), reason));
        plugin.getLogger().info(staffName + " banned " + targetName + " (" + format(duration) + "): " + reason);
    }

    public boolean unban(OfflinePlayer target) {
        if (!target.isBanned()) {
            return false;
        }
        ProfileBanList list = Bukkit.getBanList(BanListType.PROFILE);
        list.pardon(target.getPlayerProfile());
        return true;
    }

    private Component screen(String reason, Date expires, String staff) {
        List<String> lines = plugin.getConfig().getStringList("bans.screen");
        String text = lines.isEmpty() ? "<red>You are banned.</red>\n<gray><reason>" : String.join("\n", lines);
        return plugin.mini(text,
                Placeholder.unparsed("reason", reason == null ? "" : reason),
                Placeholder.unparsed("expires", expires == null ? "never" : DATE.format(expires.toInstant())),
                Placeholder.unparsed("staff", staff == null ? "?" : staff));
    }

    // Runs before the vanilla ban check, so banned players see the custom screen.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        PlayerProfile profile = event.getPlayerProfile();
        ProfileBanList list = Bukkit.getBanList(BanListType.PROFILE);
        BanEntry<PlayerProfile> entry = list.getBanEntry(profile);
        if (entry == null || (entry.getExpiration() != null && entry.getExpiration().before(new Date()))) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                screen(entry.getReason(), entry.getExpiration(), entry.getSource()));
    }
}
