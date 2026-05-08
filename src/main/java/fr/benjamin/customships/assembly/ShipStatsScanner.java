package fr.benjamin.customships.assembly;

import fr.benjamin.customships.registry.ModBlocks;
import fr.benjamin.customships.scanner.ConnectedBlocksScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class ShipStatsScanner {

    private ShipStatsScanner() {}

    public static ShipStats scan(ServerLevel level, BlockPos start) {
        Set<BlockPos> blocks = ConnectedBlocksScanner.scan(level, start);
        return count(level, blocks);
    }

    public static ShipStats count(ServerLevel level, Set<BlockPos> blocks) {
        int cores = 0;
        int reactors = 0;
        int stabilizers = 0;
        for (BlockPos blockPos : blocks) {
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.is(ModBlocks.SHIP_CORE.get())) {
                cores++;
            } else if (blockState.is(ModBlocks.SHIP_REACTOR.get())) {
                reactors++;
            } else if (blockState.is(ModBlocks.SHIP_STABILIZER.get())) {
                stabilizers++;
            }
        }
        return new ShipStats(blocks.size(), cores, reactors, stabilizers);
    }

    public record ShipStats(int blockCount, int coreCount, int reactorCount, int stabilizerCount) {}
}
