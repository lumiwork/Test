package de.lumiwork.silentac.xray;

import de.lumiwork.silentac.SilentAC;
import de.lumiwork.silentac.check.Geometry;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hides real ores from each client until the player is within reveal-radius, and fills solid rock
 * with fake ore BlockDisplays that only become visible to x-ray clients (they are fully enclosed in stone).
 */
public final class AntiXray implements Listener {

    private final SilentAC plugin;
    private final NamespacedKey fakeKey;
    private final Map<UUID, Map<Long, CompletableFuture<Set<Long>>>> ores = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, List<BlockDisplay>>> fakes = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();

    private Set<Material> hidden = EnumSet.noneOf(Material.class);
    private List<Material> fakeOverworld = List.of();
    private List<Material> fakeNether = List.of();
    private Set<String> worlds = Set.of();
    private double radius;

    private static final class View {
        final Set<Long> revealed = new HashSet<>();
        final Set<UUID> hiddenFakes = new HashSet<>();
        final Set<Long> sentChunks = new HashSet<>();
        UUID world;

        void reset(UUID newWorld) {
            revealed.clear();
            hiddenFakes.clear();
            sentChunks.clear();
            world = newWorld;
        }
    }

    public AntiXray(SilentAC plugin) {
        this.plugin = plugin;
        this.fakeKey = new NamespacedKey(plugin, "fake_ore");
        reload();
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateViews, 2L, 2L);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                spawnFakes(chunk);
            }
        }
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        hidden = materials(config.getStringList("anti-xray.hidden-blocks"));
        fakeOverworld = new ArrayList<>(materials(config.getStringList("anti-xray.fake-ores.overworld")));
        fakeNether = new ArrayList<>(materials(config.getStringList("anti-xray.fake-ores.nether")));
        worlds = new HashSet<>(config.getStringList("anti-xray.worlds"));
        radius = config.getDouble("anti-xray.reveal-radius", 5);
        ores.clear();
    }

    private boolean enabled(World world) {
        return plugin.getConfig().getBoolean("anti-xray.enabled", true)
                && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    // ---- hiding real ores ----

    @EventHandler
    public void onPlayerChunk(PlayerChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (!enabled(chunk.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        long chunkKey = chunk.getChunkKey();
        View current = view(player);
        if (!chunk.getWorld().getUID().equals(current.world)) {
            current.reset(chunk.getWorld().getUID());
        }
        current.sentChunks.add(chunkKey);
        oresIn(chunk).thenAccept(positions -> Bukkit.getScheduler().runTask(plugin, () -> {
            View view = views.get(player.getUniqueId());
            if (view == null || !player.getWorld().equals(chunk.getWorld()) || !view.sentChunks.contains(chunkKey)) {
                return;
            }
            Location eye = player.getEyeLocation();
            Map<BlockPosition, BlockData> changes = new HashMap<>();
            for (long pos : positions) {
                if (!view.revealed.contains(pos) && !inRadius(eye, pos)) {
                    changes.put(position(pos), disguise(chunk.getWorld(), pos));
                }
            }
            if (!changes.isEmpty()) {
                player.sendMultiBlockChange(changes);
            }
        })).exceptionally(error -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Anti-xray chunk scan failed", error);
            return null;
        });
    }

    private CompletableFuture<Set<Long>> oresIn(Chunk chunk) {
        Map<Long, CompletableFuture<Set<Long>>> worldOres = ores.computeIfAbsent(chunk.getWorld().getUID(), id -> new ConcurrentHashMap<>());
        return worldOres.computeIfAbsent(chunk.getChunkKey(), key -> {
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            int minY = chunk.getWorld().getMinHeight();
            int maxY = chunk.getWorld().getMaxHeight();
            Set<Material> types = hidden;
            return CompletableFuture.supplyAsync(() -> {
                Set<Long> found = ConcurrentHashMap.newKeySet();
                int baseX = snapshot.getX() << 4;
                int baseZ = snapshot.getZ() << 4;
                for (int y = minY; y < maxY; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (types.contains(snapshot.getBlockType(x, y, z))) {
                                found.add(Geometry.pack(baseX + x, y, baseZ + z));
                            }
                        }
                    }
                }
                return found;
            }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
        });
    }

    private void updateViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            View view = view(player);
            if (!world.getUID().equals(view.world)) {
                view.reset(world.getUID());
            }
            if (!enabled(world)) {
                continue;
            }
            Location eye = player.getEyeLocation();
            updateOres(player, view, world, eye);
            updateFakes(player, view, world, eye);
        }
    }

    private void updateOres(Player player, View view, World world, Location eye) {
        Map<Long, CompletableFuture<Set<Long>>> worldOres = ores.get(world.getUID());
        if (worldOres == null) {
            return;
        }
        Set<Long> inRange = new HashSet<>();
        int r = (int) Math.ceil(radius);
        int cx1 = (eye.getBlockX() - r) >> 4, cx2 = (eye.getBlockX() + r) >> 4;
        int cz1 = (eye.getBlockZ() - r) >> 4, cz2 = (eye.getBlockZ() + r) >> 4;
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                CompletableFuture<Set<Long>> future = worldOres.get(Chunk.getChunkKey(cx, cz));
                if (future == null || !future.isDone()) {
                    continue;
                }
                for (long pos : future.join()) {
                    if (inRadius(eye, pos)) {
                        inRange.add(pos);
                    }
                }
            }
        }
        Map<BlockPosition, BlockData> changes = new HashMap<>();
        for (long pos : inRange) {
            if (view.revealed.add(pos)) {
                changes.put(position(pos), world.getBlockData(Geometry.unpackX(pos), Geometry.unpackY(pos), Geometry.unpackZ(pos)));
            }
        }
        view.revealed.removeIf(pos -> {
            if (inRange.contains(pos)) {
                return false;
            }
            int x = Geometry.unpackX(pos), y = Geometry.unpackY(pos), z = Geometry.unpackZ(pos);
            if (view.sentChunks.contains(Chunk.getChunkKey(x >> 4, z >> 4)) && hidden.contains(world.getType(x, y, z))) {
                changes.put(position(pos), disguise(world, pos));
            }
            return true;
        });
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private boolean inRadius(Location eye, long pos) {
        double dx = Geometry.unpackX(pos) + 0.5 - eye.getX();
        double dy = Geometry.unpackY(pos) + 0.5 - eye.getY();
        double dz = Geometry.unpackZ(pos) + 0.5 - eye.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private BlockData disguise(World world, long pos) {
        Material type = world.getType(Geometry.unpackX(pos), Geometry.unpackY(pos), Geometry.unpackZ(pos));
        return replacementFor(type).createBlockData();
    }

    private static Material replacementFor(Material ore) {
        String name = ore.name();
        if (name.startsWith("DEEPSLATE")) {
            return Material.DEEPSLATE;
        }
        if (name.startsWith("NETHER") || ore == Material.ANCIENT_DEBRIS) {
            return Material.NETHERRACK;
        }
        return Material.STONE;
    }

    private static BlockPosition position(long pos) {
        return Position.block(Geometry.unpackX(pos), Geometry.unpackY(pos), Geometry.unpackZ(pos));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        forget(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::forget);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::forget);
    }

    private void forget(Block block) {
        Map<Long, CompletableFuture<Set<Long>>> worldOres = ores.get(block.getWorld().getUID());
        if (worldOres == null) {
            return;
        }
        CompletableFuture<Set<Long>> future = worldOres.get(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
        if (future != null) {
            long pos = Geometry.pack(block);
            future.thenAccept(set -> set.remove(pos));
        }
    }

    // ---- fake ore displays ----

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                spawnFakes(chunk);
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Map<Long, CompletableFuture<Set<Long>>> worldOres = ores.get(chunk.getWorld().getUID());
        if (worldOres != null) {
            worldOres.remove(chunk.getChunkKey());
        }
        Map<Long, List<BlockDisplay>> worldFakes = fakes.get(chunk.getWorld().getUID());
        if (worldFakes != null) {
            List<BlockDisplay> list = worldFakes.remove(chunk.getChunkKey());
            if (list != null) {
                list.forEach(Entity::remove);
            }
        }
    }

    private void spawnFakes(Chunk chunk) {
        World world = chunk.getWorld();
        FileConfiguration config = plugin.getConfig();
        if (!enabled(world) || !config.getBoolean("anti-xray.fake-ores.enabled", true)) {
            return;
        }
        List<Material> pool = world.getEnvironment() == World.Environment.NETHER ? fakeNether
                : world.getEnvironment() == World.Environment.NORMAL ? fakeOverworld : List.of();
        if (pool.isEmpty()) {
            return;
        }
        Map<Long, List<BlockDisplay>> worldFakes = fakes.computeIfAbsent(world.getUID(), id -> new HashMap<>());
        if (worldFakes.containsKey(chunk.getChunkKey())) {
            return;
        }
        int size = Math.max(1, config.getInt("anti-xray.fake-ores.size", 2));
        int perChunk = config.getInt("anti-xray.fake-ores.per-chunk", 3);
        int minY = world.getMinHeight() + 2;
        int maxY = Math.min(world.getMaxHeight() - size - 2, config.getInt("anti-xray.fake-ores.max-y", 32));
        if (maxY <= minY) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<BlockDisplay> spawned = new ArrayList<>();
        for (int i = 0; i < perChunk; i++) {
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = (chunk.getX() << 4) + 1 + random.nextInt(14 - size);
                int z = (chunk.getZ() << 4) + 1 + random.nextInt(14 - size);
                int y = minY + random.nextInt(maxY - minY);
                if (!enclosed(world, x, y, z, size)) {
                    continue;
                }
                Material ore = pool.get(random.nextInt(pool.size()));
                if (world.getType(x, y, z) == Material.DEEPSLATE) {
                    Material deep = Material.matchMaterial("DEEPSLATE_" + ore.name());
                    if (deep != null) {
                        ore = deep;
                    }
                }
                spawned.add(spawnDisplay(world, x, y, z, size, ore));
                break;
            }
        }
        worldFakes.put(chunk.getChunkKey(), spawned);
    }

    // The whole fake plus a one-block shell must be solid, so legit players can never see it.
    private static boolean enclosed(World world, int x, int y, int z, int size) {
        for (int dx = -1; dx <= size; dx++) {
            for (int dy = -1; dy <= size; dy++) {
                for (int dz = -1; dz <= size; dz++) {
                    if (!world.getType(x + dx, y + dy, z + dz).isOccluding()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private BlockDisplay spawnDisplay(World world, int x, int y, int z, int size, Material ore) {
        return world.spawn(new Location(world, x, y, z), BlockDisplay.class, display -> {
            display.setBlock(ore.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(size, size, size), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
            display.getPersistentDataContainer().set(fakeKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void updateFakes(Player player, View view, World world, Location eye) {
        Map<Long, List<BlockDisplay>> worldFakes = fakes.get(world.getUID());
        if (worldFakes == null) {
            return;
        }
        int size = Math.max(1, plugin.getConfig().getInt("anti-xray.fake-ores.size", 2));
        double hideDistance = radius + size;
        int pcx = eye.getBlockX() >> 4, pcz = eye.getBlockZ() >> 4;
        Set<UUID> near = new HashSet<>();
        for (int cx = pcx - 1; cx <= pcx + 1; cx++) {
            for (int cz = pcz - 1; cz <= pcz + 1; cz++) {
                List<BlockDisplay> list = worldFakes.get(Chunk.getChunkKey(cx, cz));
                if (list == null) {
                    continue;
                }
                for (BlockDisplay display : list) {
                    Location center = display.getLocation().add(size / 2.0, size / 2.0, size / 2.0);
                    if (center.distanceSquared(eye) <= hideDistance * hideDistance) {
                        near.add(display.getUniqueId());
                        if (view.hiddenFakes.add(display.getUniqueId())) {
                            player.hideEntity(plugin, display);
                        }
                    }
                }
            }
        }
        view.hiddenFakes.removeIf(id -> {
            if (near.contains(id)) {
                return false;
            }
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                player.showEntity(plugin, entity);
            }
            return true;
        });
    }

    @EventHandler
    public void onPlayerChunkUnload(PlayerChunkUnloadEvent event) {
        View view = views.get(event.getPlayer().getUniqueId());
        if (view != null) {
            view.sentChunks.remove(event.getChunk().getChunkKey());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
    }

    private View view(Player player) {
        return views.computeIfAbsent(player.getUniqueId(), id -> new View());
    }

    public void shutdown() {
        fakes.values().forEach(worldFakes -> worldFakes.values().forEach(list -> list.forEach(Entity::remove)));
        fakes.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
                if (entity.getPersistentDataContainer().has(fakeKey)) {
                    entity.remove();
                }
            }
        }
    }

    private Set<Material> materials(List<String> names) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Unknown block in anti-xray config: " + name);
            } else {
                result.add(material);
            }
        }
        return result;
    }
}
