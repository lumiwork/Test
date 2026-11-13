package de.lumiwork.silentac.feature;

import de.lumiwork.silentac.PlayerData;
import de.lumiwork.silentac.PlayerData.CheckType;
import de.lumiwork.silentac.SilentAC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * /silentac admin: player overview sorted by total VL, and per-player actions
 * (teleport, spectate, reset VL, kick, ban templates).
 */
public final class AdminGui implements Listener {

    private static final int PAGE_SIZE = 45;

    private final SilentAC plugin;
    private final NamespacedKey actionKey;

    private static final class Menu implements InventoryHolder {
        final UUID target;
        final int page;
        Inventory inventory;

        Menu(UUID target, int page) {
            this.target = target;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public AdminGui(SilentAC plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "admin_action");
    }

    public void openOverview(Player viewer, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparingDouble((Player p) -> -totalVl(plugin.data(p))).thenComparing(Player::getName));
        int pages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        Menu menu = new Menu(null, page);
        Inventory inventory = Bukkit.createInventory(menu, 54,
                Component.text("SilentAC Admin (" + (page + 1) + "/" + pages + ")", NamedTextColor.DARK_RED));
        menu.inventory = inventory;

        for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < players.size(); i++) {
            inventory.setItem(i, head(players.get(page * PAGE_SIZE + i)));
        }
        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "Vorherige Seite", "page:" + (page - 1)));
        }
        inventory.setItem(49, button(Material.BELL, "Alerts an/aus (" + (plugin.data(viewer).alertsEnabled ? "an" : "aus") + ")", "alerts"));
        if (page < pages - 1) {
            inventory.setItem(53, button(Material.ARROW, "Nächste Seite", "page:" + (page + 1)));
        }
        viewer.openInventory(inventory);
    }

    private void openPlayer(Player viewer, Player target) {
        Menu menu = new Menu(target.getUniqueId(), 0);
        Inventory inventory = Bukkit.createInventory(menu, 54, Component.text("SilentAC: " + target.getName(), NamedTextColor.DARK_RED));
        menu.inventory = inventory;

        inventory.setItem(4, head(target));
        inventory.setItem(19, button(Material.ENDER_PEARL, "Teleportieren", "tp"));
        inventory.setItem(20, button(Material.ENDER_EYE, "Zuschauen (Spectator)", "spectate"));
        inventory.setItem(21, button(Material.MILK_BUCKET, "VL zurücksetzen", "reset"));
        inventory.setItem(22, button(Material.CHEST, "Inventar ansehen", "invsee"));
        inventory.setItem(23, button(Material.IRON_DOOR, "Kicken", "kick"));

        ConfigurationSection templates = plugin.getConfig().getConfigurationSection("bans.templates");
        int slot = 37;
        if (templates != null) {
            for (String id : templates.getKeys(false)) {
                if (slot > 43) {
                    break;
                }
                ItemStack item = button(Material.BARRIER, "Bannen: " + id, "ban:" + id);
                ItemMeta meta = item.getItemMeta();
                meta.lore(List.of(
                        line("Grund: " + templates.getString(id + ".reason", id)),
                        line("Dauer: " + templates.getString(id + ".duration", "perm"))));
                item.setItemMeta(meta);
                inventory.setItem(slot++, item);
            }
        }
        inventory.setItem(49, button(Material.ARROW, "Zurück", "back"));
        viewer.openInventory(inventory);
    }

    private ItemStack head(Player player) {
        PlayerData data = plugin.data(player);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        double total = totalVl(data);
        meta.displayName(Component.text(player.getName(), total >= 10 ? NamedTextColor.RED : total > 0 ? NamedTextColor.GOLD : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (CheckType type : CheckType.values()) {
            if (data.vl(type) > 0) {
                lore.add(line(String.format("%s: %.1f", type, data.vl(type))));
            }
        }
        if (lore.isEmpty()) {
            lore.add(line("Keine Verstöße"));
        }
        lore.add(line("Ping: " + player.getPing() + " ms"));
        if (plugin.bedrock().isBedrock(player)) {
            lore.add(Component.text("Bedrock", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        if (plugin.combatLog().isTagged(player)) {
            lore.add(Component.text("Im Kampf (" + plugin.combatLog().secondsLeft(player) + "s)", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "player:" + player.getUniqueId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static double totalVl(PlayerData data) {
        double total = 0;
        for (CheckType type : CheckType.values()) {
            total += data.vl(type);
        }
        return total;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || !viewer.hasPermission("silentac.admin")) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        if (action.startsWith("page:")) {
            openOverview(viewer, Integer.parseInt(action.substring(5)));
            return;
        }
        if (action.equals("alerts")) {
            plugin.data(viewer).alertsEnabled = !plugin.data(viewer).alertsEnabled;
            openOverview(viewer, menu.page);
            return;
        }
        if (action.equals("back")) {
            openOverview(viewer, 0);
            return;
        }
        if (action.startsWith("player:")) {
            Player target = Bukkit.getPlayer(UUID.fromString(action.substring(7)));
            if (target != null) {
                openPlayer(viewer, target);
            }
            return;
        }

        Player target = menu.target == null ? null : Bukkit.getPlayer(menu.target);
        if (target == null) {
            plugin.prefixed(viewer, "Spieler ist nicht mehr online.");
            viewer.closeInventory();
            return;
        }
        switch (action) {
            case "tp" -> {
                viewer.closeInventory();
                viewer.teleport(target);
            }
            case "spectate" -> {
                viewer.closeInventory();
                viewer.setGameMode(GameMode.SPECTATOR);
                viewer.teleport(target);
            }
            case "reset" -> {
                plugin.data(target).reset();
                plugin.prefixed(viewer, "VL von " + target.getName() + " zurückgesetzt.");
                openPlayer(viewer, target);
            }
            case "invsee" -> viewer.openInventory(target.getInventory());
            case "kick" -> {
                viewer.closeInventory();
                target.kick(plugin.mini(plugin.getConfig().getString("bans.kick-message", "<red>Kicked")));
            }
            default -> {
                if (action.startsWith("ban:")) {
                    viewer.closeInventory();
                    plugin.bans().banTemplate(viewer, target, action.substring(4));
                }
            }
        }
    }
}
