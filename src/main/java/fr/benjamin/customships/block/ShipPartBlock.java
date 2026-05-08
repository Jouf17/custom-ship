package fr.benjamin.customships.block;

import fr.benjamin.customships.assembly.ShipPartStatsUpdater;
import fr.benjamin.customships.assembly.ShipPartStatsUpdater.ShipPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ShipPartBlock extends Block {

    private final ShipPartType type;

    public ShipPartBlock(Properties properties, ShipPartType type) {
        super(properties);
        this.type = type;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            ShipPartStatsUpdater.placed(level, pos, type);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(state.getBlock())) {
            ShipPartStatsUpdater.removed(level, pos, type);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
