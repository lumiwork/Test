package de.lumiwork.silentac.check;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Spears (1.21.11+) and other items with attack_range / kinetic / piercing components
 * have longer reach, lunge the player forward and hit along their charge path.
 */
public final class Weapons {

    private Weapons() {
    }

    public static boolean isSpecial(ItemStack item) {
        return item != null && !item.isEmpty()
                && (item.hasData(DataComponentTypes.ATTACK_RANGE)
                || item.hasData(DataComponentTypes.KINETIC_WEAPON)
                || item.hasData(DataComponentTypes.PIERCING_WEAPON));
    }

    public static boolean holdsSpecial(Player player) {
        return isSpecial(player.getInventory().getItemInMainHand()) || isSpecial(player.getInventory().getItemInOffHand());
    }

    public static boolean isKinetic(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasData(DataComponentTypes.KINETIC_WEAPON);
    }

    /** Maximum entity reach for the item in the main hand, including its hitbox margin. */
    public static double entityReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        double base = attribute != null ? attribute.getValue() : 3.0;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty() || !item.hasData(DataComponentTypes.ATTACK_RANGE)) {
            return base;
        }
        AttackRange range = item.getData(DataComponentTypes.ATTACK_RANGE);
        if (range == null) {
            return base;
        }
        double max = player.getGameMode() == GameMode.CREATIVE ? range.maxCreativeReach() : range.maxReach();
        return Math.max(base, max + range.hitboxMargin());
    }
}
