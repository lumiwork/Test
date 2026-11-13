package de.lumiwork.silentac.feature;

import de.lumiwork.silentac.SilentAC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes the ender dragon tougher: more health, harder hits, less damage taken
 * and ghast-style fireballs at players it can see. Enraged below a health threshold.
 */
public final class DragonManager implements Listener {

    private final SilentAC plugin;
    private final NamespacedKey buffedKey;
    private final NamespacedKey fireballKey;
    private final Map<UUID, Integer> lastShot = new HashMap<>();

    public DragonManager(SilentAC plugin) {
        this.plugin = plugin;
        this.buffedKey = new NamespacedKey(plugin, "dragon_buffed");
        this.fireballKey = new NamespacedKey(plugin, "dragon_fireball");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(EnderDragon.class).forEach(this::buff);
        }
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("dragon");
        return section != null ? section : plugin.getConfig().createSection("dragon");
    }

    private boolean enabled() {
        return config().getBoolean("enabled", true);
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            Bukkit.getScheduler().runTask(plugin, () -> buff(dragon));
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof EnderDragon dragon) {
                buff(dragon);
            }
        }
    }

    private void buff(EnderDragon dragon) {
        if (!enabled() || !dragon.isValid() || dragon.getPersistentDataContainer().has(buffedKey)) {
            return;
        }
        AttributeInstance maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double ratio = dragon.getHealth() / maxHealth.getValue();
        maxHealth.setBaseValue(config().getDouble("health", 600));
        dragon.setHealth(Math.min(maxHealth.getValue(), maxHealth.getValue() * ratio));
        dragon.getPersistentDataContainer().set(buffedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean enraged(EnderDragon dragon) {
        AttributeInstance maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null && dragon.getHealth() / maxHealth.getValue() <= config().getDouble("rage.below-health", 0.5);
    }

    private void tick() {
        if (!enabled() || !config().getBoolean("fireballs.enabled", true)) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (World world : Bukkit.getWorlds()) {
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                if (dragon.isDead() || dragon.getPhase() == EnderDragon.Phase.DYING) {
                    continue;
                }
                boolean rage = enraged(dragon);
                int interval = config().getInt("fireballs.interval-ticks", 60);
                if (rage) {
                    interval = (int) Math.max(5, interval * config().getDouble("rage.fireball-interval-multiplier", 0.5));
                }
                if (now - lastShot.getOrDefault(dragon.getUniqueId(), 0) < interval) {
                    continue;
                }
                Player target = findTarget(dragon);
                if (target == null) {
                    continue;
                }
                lastShot.put(dragon.getUniqueId(), now);
                int volley = rage ? config().getInt("rage.fireballs-per-volley", 3) : config().getInt("fireballs.per-volley", 1);
                for (int i = 0; i < volley; i++) {
                    int delay = i * 6;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (dragon.isValid() && target.isValid() && target.getWorld().equals(dragon.getWorld())) {
                            shoot(dragon, target);
                        }
                    }, delay);
                }
            }
        }
        lastShot.keySet().removeIf(id -> Bukkit.getEntity(id) == null);
    }

    private Player findTarget(EnderDragon dragon) {
        double range = config().getDouble("fireballs.range", 64);
        Location center = dragon.getLocation();
        return dragon.getWorld().getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                .filter(p -> !p.isDead() && p.getLocation().distanceSquared(center) <= range * range)
                .filter(dragon::hasLineOfSight)
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(center)))
                .orElse(null);
    }

    private void shoot(EnderDragon dragon, Player target) {
        Location head = dragon.getLocation();
        Vector aim = target.getEyeLocation().toVector().subtract(head.toVector());
        if (aim.lengthSquared() < 1) {
            return;
        }
        Vector direction = aim.normalize();
        double spread = config().getDouble("fireballs.spread", 0.05);
        direction.add(new Vector((Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread)).normalize();
        // Spawn outside the dragon's own hitbox so the fireball does not explode on it.
        Location spawn = head.clone().add(direction.clone().multiply(8));
        double speed = config().getDouble("fireballs.speed", 1.2);
        dragon.getWorld().spawn(spawn, LargeFireball.class, fireball -> {
            fireball.setShooter(dragon);
            fireball.setDirection(direction);
            fireball.setVelocity(direction.clone().multiply(speed));
            fireball.setYield((float) config().getDouble("fireballs.explosion-power", 2.0));
            fireball.setIsIncendiary(config().getBoolean("fireballs.fire", false));
            fireball.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private boolean isDragonFireball(Entity entity) {
        return entity instanceof Projectile && entity.getPersistentDataContainer().has(fireballKey);
    }

    private static boolean isDragon(Entity entity) {
        return entity instanceof EnderDragon
                || (entity instanceof ComplexEntityPart part && part.getParent() instanceof EnderDragon);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireballHit(ProjectileHitEvent event) {
        if (isDragonFireball(event.getEntity()) && event.getHitEntity() != null && isDragon(event.getHitEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireballExplode(EntityExplodeEvent event) {
        if (isDragonFireball(event.getEntity()) && !config().getBoolean("fireballs.break-blocks", false)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled()) {
            return;
        }
        Entity damager = event.getDamager();
        if (isDragon(event.getEntity())) {
            if (isDragonFireball(damager)) {
                event.setCancelled(true);
            }
            return;
        }
        boolean fromDragon = isDragon(damager)
                || isDragonFireball(damager)
                || (damager instanceof Projectile projectile && projectile.getShooter() instanceof EnderDragon);
        if (fromDragon) {
            double multiplier = config().getDouble("damage-multiplier", 1.5);
            if (damager instanceof EnderDragon dragon && enraged(dragon)) {
                multiplier *= config().getDouble("rage.damage-multiplier", 1.25);
            }
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonHurt(EntityDamageEvent event) {
        if (enabled() && event.getEntity() instanceof EnderDragon) {
            event.setDamage(event.getDamage() * config().getDouble("damage-taken-multiplier", 0.75));
        }
    }
}
