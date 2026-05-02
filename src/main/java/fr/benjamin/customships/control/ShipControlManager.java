package fr.benjamin.customships.control;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.assembly.ShipAssemblerHelper;
import fr.benjamin.customships.assembly.ShipControllerAttachment;
import fr.benjamin.customships.config.ModConfig;
import fr.benjamin.customships.network.ModPackets;
import fr.benjamin.customships.network.PilotStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks active pilots and routes control packets to VS2 ship attachments.
 * Pilots are locked to a ship-local helm position while their inputs update
 * only the ship controller attachment.
 *
 * Threading: all methods (except volatile reads inside ShipControllerAttachment)
 * run on the server game thread.
 */
public class ShipControlManager {

    private static final double TURN_SPEED = 0.8; // rad/s

    private static class PilotData {
        final long shipId;
        final ServerLevel level;
        final ShipMountingEntity mount;
        final boolean originalNoGravity;
        final boolean originalNoPhysics;

        PilotData(long shipId, ServerLevel level, ShipMountingEntity mount, boolean originalNoGravity, boolean originalNoPhysics) {
            this.shipId = shipId;
            this.level = level;
            this.mount = mount;
            this.originalNoGravity = originalNoGravity;
            this.originalNoPhysics = originalNoPhysics;
        }
    }

    private final Map<UUID, PilotData> pilots = new HashMap<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public boolean isPiloting(UUID playerUUID) {
        return pilots.containsKey(playerUUID);
    }

    public boolean startPiloting(ServerPlayer player, LoadedServerShip ship, ServerLevel level, BlockPos controllerPos) {
        if (player.isPassenger()) {
            CustomShipsMod.LOGGER.warn("[CustomShips] Refusing to start piloting for {} because player is riding {}",
                    player.getName().getString(), player.getVehicle());
            return false;
        }

        ship.getOrPutAttachment(ShipControllerAttachment.class, ShipControllerAttachment::new);

        ShipMountingEntity mount = createShipMount(level, controllerPos);
        if (mount == null) {
            return false;
        }

        boolean startedRiding = player.startRiding(mount, true);
        if (!startedRiding) {
            mount.discard();
            return false;
        }

        pilots.put(player.getUUID(), new PilotData(
                ship.getId(),
                level,
                mount,
                player.isNoGravity(),
                player.noPhysics
        ));
        ModPackets.sendToPlayer(new PilotStatePacket(true), player);
        CustomShipsMod.LOGGER.info("[CustomShips] Player {} started piloting ship {}",
                player.getName().getString(), ship.getId());
        return true;
    }

    public void stopPiloting(ServerPlayer player) {
        PilotData data = pilots.remove(player.getUUID());
        ModPackets.sendToPlayer(new PilotStatePacket(false), player);
        if (data != null) {
            zeroShipVelocity(data);
            restorePlayerPhysics(player, data);
            removeMount(data);
            CustomShipsMod.LOGGER.info("[CustomShips] Player {} stopped piloting ship {}",
                    player.getName().getString(), data.shipId);
        }
    }

    /**
     * Called when a ShipControlPacket arrives from the client.
     * W/S = forward/backward in the ship's own facing direction.
     * A/D = yaw rotation.
     * Space/Shift = ascend/descend.
     */
    public void handleControlInput(ServerPlayer player,
                                   boolean forward, boolean back,
                                   boolean left, boolean right,
                                   boolean up, boolean down,
                                   float yaw,
                                   ServerLevel level) {
        PilotData data = pilots.get(player.getUUID());
        if (data == null) return;

        LoadedServerShip ship = ShipAssemblerHelper.getLoadedShipById(level, data.shipId);
        if (ship == null) {
            CustomShipsMod.LOGGER.warn("[CustomShips] Ship {} unloaded while {} was piloting — stopping",
                    data.shipId, player.getName().getString());
            stopPiloting(player);
            return;
        }

        ShipControllerAttachment controller = ship.getAttachment(ShipControllerAttachment.class);
        if (controller == null) {
            controller = new ShipControllerAttachment();
            ship.setAttachment(controller);
        }

        double speed = ModConfig.SHIP_SPEED.get();

        double fwdSpeed = ((forward ? 1 : 0) - (back  ? 1 : 0)) * speed;
        double vy       =  up   ? speed : (down  ? -speed : 0.0);
        double yawRate  = ((left  ? 1 : 0) - (right ? 1 : 0)) * TURN_SPEED;

        controller.setDesiredFwdSpeed(fwdSpeed);
        controller.setDesiredVy(vy);
        controller.setDesiredYawRate(yawRate);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pilots.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var entry : new ArrayList<>(pilots.entrySet())) {
            UUID uuid = entry.getKey();
            PilotData data = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            LoadedServerShip ship = ShipAssemblerHelper.getLoadedShipById(data.level, data.shipId);
            if (ship == null || !data.mount.isAlive() || player.getVehicle() != data.mount) {
                pilots.remove(uuid);
                zeroShipVelocity(data);
                restorePlayerPhysics(player, data);
                removeMount(data);
                ModPackets.sendToPlayer(new PilotStatePacket(false), player);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Server tick — keep each pilot seated at the ship core block
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Player lifecycle events
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PilotData data = pilots.remove(player.getUUID());
            if (data != null) {
                zeroShipVelocity(data);
                CustomShipsMod.LOGGER.info("[CustomShips] Player {} disconnected while piloting ship {} — released",
                        player.getName().getString(), data.shipId);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPiloting(player.getUUID())) {
            stopPiloting(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPiloting(player.getUUID())) {
            stopPiloting(player);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void zeroShipVelocity(PilotData data) {
        LoadedServerShip ship = ShipAssemblerHelper.getLoadedShipById(data.level, data.shipId);
        if (ship == null) return;
        ShipControllerAttachment ctrl = ship.getAttachment(ShipControllerAttachment.class);
        if (ctrl != null) ctrl.stop();
    }

    private void restorePlayerPhysics(ServerPlayer player, PilotData data) {
        if (player.getVehicle() == data.mount) {
            player.stopRiding();
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(data.originalNoGravity);
        player.noPhysics = data.originalNoPhysics;
        player.fallDistance = 0.0F;
    }

    @Nullable
    private ShipMountingEntity createShipMount(ServerLevel level, BlockPos controllerPos) {
        Entity entity = ValkyrienSkiesMod.INSTANCE.getSHIP_MOUNTING_ENTITY_TYPE().create(level);
        if (!(entity instanceof ShipMountingEntity mount)) {
            CustomShipsMod.LOGGER.warn("[CustomShips] Could not create VS ship mounting entity");
            return null;
        }

        mount.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.65, controllerPos.getZ() + 0.5);
        level.addFreshEntity(mount);
        return mount;
    }

    private void removeMount(PilotData data) {
        if (data.mount.isAlive()) {
            data.mount.discard();
        }
    }

    @Nullable
    public Long getShipIdForPilot(UUID playerUUID) {
        PilotData data = pilots.get(playerUUID);
        return data != null ? data.shipId : null;
    }
}
