package fr.benjamin.customships.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * BFS flood-fill to find all solid blocks connected to a starting block.
 */
public class ConnectedBlocksScanner {

    private ConnectedBlocksScanner() {}

    /**
     * Scans all solid, non-fluid blocks connected (6-directional) to {@code start}.
     *
     * @param level the server world
     * @param start the origin block (Ship Core position)
     * @return the set of connected block positions
     */
    public static Set<BlockPos> scan(ServerLevel level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);

                if (visited.contains(neighbor)) {
                    continue;
                }

                if (!isIncludedBlock(level, neighbor)) {
                    continue;
                }

                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return visited;
    }

    /**
     * A block is included in the ship if it is:
     * - not air
     * - not a fluid (water, lava)
     * - not outside the world height limits
     */
    private static boolean isIncludedBlock(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        // Skip fluid source blocks
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return false;
        }

        // Skip blocks that are just air-like (barrier, structure void, etc.)
        if (state.is(Blocks.BARRIER) || state.is(Blocks.STRUCTURE_VOID)) {
            return false;
        }

        return true;
    }
}
