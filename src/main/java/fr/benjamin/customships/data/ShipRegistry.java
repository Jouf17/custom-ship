package fr.benjamin.customships.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.assembly.ShipAssemblerHelper;
import fr.benjamin.customships.assembly.ShipStatsScanner.ShipStats;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShipRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<Long, ShipData> ships = new LinkedHashMap<>();
    private long nextId = 1L;

    public ShipRegistry(Path file) {
        this.file = file;
    }

    public void load() {
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SaveData data = GSON.fromJson(reader, SaveData.class);
            ships.clear();
            if (data != null && data.ships != null) {
                nextId = Math.max(1L, data.nextId);
                for (ShipData ship : data.ships) {
                    if (ship.vsShipId <= 0L) {
                        ship.vsShipId = ship.shipId;
                    }
                    ships.put(ship.shipId, ship);
                    nextId = Math.max(nextId, ship.shipId + 1L);
                }
            }
            CustomShipsMod.LOGGER.info("[CustomShips] Loaded {} registered ship(s).", ships.size());
        } catch (IOException | RuntimeException e) {
            CustomShipsMod.LOGGER.error("[CustomShips] Unable to load ship registry from {}", file, e);
        }
    }

    public void save() {
        SaveData data = new SaveData();
        data.nextId = nextId;
        data.ships = new ArrayList<>(ships.values());
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            CustomShipsMod.LOGGER.error("[CustomShips] Unable to save ship registry to {}", file, e);
        }
    }

    public ShipData register(long vsShipId, ServerLevel level, BlockPos corePos, ShipStats stats) {
        return register(vsShipId, level, corePos, stats.blockCount(), stats.coreCount(), stats.reactorCount(), stats.stabilizerCount());
    }

    public ShipData register(long vsShipId, ServerLevel level, BlockPos corePos,
                             int blockCount, int coreCount, int reactorCount, int stabilizerCount) {
        ShipData ship = getByVsShipId(vsShipId);
        if (ship == null) {
            ship = new ShipData();
            ship.shipId = nextId++;
            ship.createdAt = System.currentTimeMillis();
        }
        String worldName = level.dimension().location().toString();
        ship.vsShipId = vsShipId;
        ship.worldName = worldName;
        ship.coreX = corePos.getX();
        ship.coreY = corePos.getY();
        ship.coreZ = corePos.getZ();
        ship.lastX = corePos.getX() + 0.5D;
        ship.lastY = corePos.getY() + 1.0D;
        ship.lastZ = corePos.getZ() + 0.5D;
        ship.blockCount = blockCount;
        ship.coreCount = coreCount;
        ship.reactorCount = reactorCount;
        ship.stabilizerCount = stabilizerCount;
        ship.updatedAt = System.currentTimeMillis();
        ships.put(ship.shipId, ship);
        save();
        return ship;
    }

    public void updateLastPosition(long shipId, double x, double y, double z) {
        ShipData ship = ships.get(shipId);
        if (ship == null) return;
        ship.lastX = x;
        ship.lastY = y;
        ship.lastZ = z;
        ship.updatedAt = System.currentTimeMillis();
        save();
    }

    public void updateLastPositionByVsShipId(long vsShipId, double x, double y, double z) {
        ShipData ship = getByVsShipId(vsShipId);
        if (ship == null) return;
        updateLastPosition(ship.shipId, x, y, z);
    }

    public boolean remove(long shipId) {
        if (ships.remove(shipId) == null) {
            return false;
        }
        save();
        return true;
    }

    public void syncLoadedPositions(MinecraftServer server) {
        boolean dirty = false;
        for (ShipData ship : ships.values()) {
            ServerLevel level = findLevel(server, ship);
            if (level == null) continue;
            LoadedServerShip loaded = ShipAssemblerHelper.getLoadedShipById(level, ship.vsShipId);
            if (loaded == null) continue;
            Vector3dc position = loaded.getTransform().getPositionInWorld();
            if (position == null) continue;
            ship.lastX = position.x();
            ship.lastY = position.y();
            ship.lastZ = position.z();
            ship.updatedAt = System.currentTimeMillis();
            dirty = true;
        }
        if (dirty) {
            save();
        }
    }

    @Nullable
    public ShipData get(long shipId) {
        return ships.get(shipId);
    }

    @Nullable
    public ShipData getByVsShipId(long vsShipId) {
        for (ShipData ship : ships.values()) {
            if (ship.vsShipId == vsShipId) {
                return ship;
            }
        }
        return null;
    }

    public Collection<ShipData> all() {
        return Collections.unmodifiableCollection(ships.values());
    }

    @Nullable
    public ServerLevel findLevel(MinecraftServer server, ShipData ship) {
        ResourceLocation location = ResourceLocation.tryParse(ship.worldName);
        if (location == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(location)) {
                return level;
            }
        }
        return null;
    }

    private static final class SaveData {
        long nextId = 1L;
        List<ShipData> ships = List.of();
    }

    public static final class ShipData {
        public long shipId;
        public long vsShipId;
        public String worldName = "";
        public int coreX, coreY, coreZ;
        public double lastX, lastY, lastZ;
        public int blockCount, coreCount, reactorCount, stabilizerCount;
        public long createdAt, updatedAt;
    }
}
