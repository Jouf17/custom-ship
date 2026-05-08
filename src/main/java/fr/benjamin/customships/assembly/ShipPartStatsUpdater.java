package fr.benjamin.customships.assembly;

import fr.benjamin.customships.CustomShipsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class ShipPartStatsUpdater {

    private ShipPartStatsUpdater() {}

    public static void placed(Level level, BlockPos pos, ShipPartType type) {
        apply(level, pos, type, 1);
    }

    public static void removed(Level level, BlockPos pos, ShipPartType type) {
        apply(level, pos, type, -1);
    }

    private static void apply(Level level, BlockPos pos, ShipPartType type, int sign) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
        if (ship == null) {
            return;
        }

        ShipControllerAttachment controller = ship.getOrPutAttachment(
                ShipControllerAttachment.class,
                ShipControllerAttachment::new
        );
        controller.adjustShipStats(sign, type.coreDelta() * sign, type.reactorDelta() * sign, type.stabilizerDelta() * sign);
        CustomShipsMod.LOGGER.info("[CustomShips] Ship {} custom part {} at {}: blockDelta={}, coreDelta={}, reactorDelta={}, stabilizerDelta={}",
                ship.getId(), sign > 0 ? "placed" : "removed", pos,
                sign, type.coreDelta() * sign, type.reactorDelta() * sign, type.stabilizerDelta() * sign);
    }

    public enum ShipPartType {
        CORE(1, 0, 0),
        CONTROLLER(0, 0, 0),
        REACTOR(0, 1, 0),
        STABILIZER(0, 0, 1);

        private final int coreDelta;
        private final int reactorDelta;
        private final int stabilizerDelta;

        ShipPartType(int coreDelta, int reactorDelta, int stabilizerDelta) {
            this.coreDelta = coreDelta;
            this.reactorDelta = reactorDelta;
            this.stabilizerDelta = stabilizerDelta;
        }

        public int coreDelta() {
            return coreDelta;
        }

        public int reactorDelta() {
            return reactorDelta;
        }

        public int stabilizerDelta() {
            return stabilizerDelta;
        }
    }
}
