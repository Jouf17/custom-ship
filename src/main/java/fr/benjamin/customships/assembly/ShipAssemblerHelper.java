package fr.benjamin.customships.assembly;

import fr.benjamin.customships.CustomShipsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.Set;

/**
 * Thin wrapper around the Valkyrien Skies 2 (v2.4.11) assembly API.
 *
 * Assembly method used:
 *   ShipAssembler.assembleToShip(ServerLevel, Set<BlockPos>, double scale)
 *   → ServerShip   (static Kotlin companion method exposed as Java static)
 *
 * Ship ID type: long  (Ship.getId())
 */
public class ShipAssemblerHelper {

    private ShipAssemblerHelper() {}

    /**
     * Moves the given blocks from world space into a new VS2 ship.
     *
     * @param level  the server level containing the blocks
     * @param blocks block positions to include (must all be in {@code level})
     * @param center the Ship Core position — used only for logging here;
     *               VS2 places the ship near its bounding-box center
     * @return the VS2 ship ID (long) of the newly created ship, or null on failure
     */
    @Nullable
    public static Long assembleShip(ServerLevel level, Set<BlockPos> blocks, BlockPos center) {
        try {
            // VS2 2.4.11 static method:
            //   ShipAssembler.assembleToShip(ServerLevel, Set<? extends BlockPos>, double scale)
            // scale = 1.0  →  normal ship size (no scaling applied)
            ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0);

            if (ship == null) {
                CustomShipsMod.LOGGER.error("[CustomShips] assembleToShip returned null for {} blocks at {}", blocks.size(), center);
                return null;
            }

            CustomShipsMod.LOGGER.info("[CustomShips] VS2 ship created: id={} for {} blocks", ship.getId(), blocks.size());
            return ship.getId();

        } catch (Exception e) {
            CustomShipsMod.LOGGER.error("[CustomShips] Exception during ship assembly at {}: {}", center, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Returns the loaded VS2 ship for a given ship ID, or null if not loaded.
     *
     * Uses {@code VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id)}.
     * A ship can be unloaded if its chunks are out of range; in that case this
     * returns null and the caller should handle the absence gracefully.
     */
    @Nullable
    public static LoadedServerShip getLoadedShipById(ServerLevel level, long shipId) {
        try {
            return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(shipId);
        } catch (Exception e) {
            CustomShipsMod.LOGGER.error("[CustomShips] Error fetching loaded ship {}: {}", shipId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Returns the VS2 ship for a given ID regardless of load state.
     * Useful for checking whether the ship still exists after a restart.
     */
    @Nullable
    public static ServerShip getShipById(ServerLevel level, long shipId) {
        try {
            return VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(shipId);
        } catch (Exception e) {
            CustomShipsMod.LOGGER.error("[CustomShips] Error fetching ship {}: {}", shipId, e.getMessage(), e);
            return null;
        }
    }
}
