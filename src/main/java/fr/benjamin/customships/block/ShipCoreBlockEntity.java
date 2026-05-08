package fr.benjamin.customships.block;

import fr.benjamin.customships.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ShipCoreBlockEntity extends BlockEntity {

    private static final String TAG_SHIP_ID  = "shipId";
    private static final String TAG_ORIG_X   = "origX";
    private static final String TAG_ORIG_Y   = "origY";
    private static final String TAG_ORIG_Z   = "origZ";
    private static final String TAG_MAX_BLOCKS = "maxBlocks";

    private long shipId = -1L;
    private int maxBlocks = 50;
    // Original world-space position before VS2 assembly (= ship local-space position in VS2).
    // Stored so we can compute the block's current world position via ship.getShipToWorld().
    private BlockPos originalWorldPos = BlockPos.ZERO;

    public ShipCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_CORE.get(), pos, state);
    }

    public long getShipId() { return shipId; }
    public BlockPos getOriginalWorldPos() { return originalWorldPos; }
    public int getMaxBlocks() { return maxBlocks; }

    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = Math.max(1, maxBlocks);
        setChanged();
    }

    public void setOriginalWorldPos(BlockPos pos) {
        this.originalWorldPos = pos.immutable();
        setChanged();
    }

    public void setShipId(long id) {
        this.shipId = id;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(TAG_SHIP_ID, shipId);
        tag.putInt(TAG_ORIG_X, originalWorldPos.getX());
        tag.putInt(TAG_ORIG_Y, originalWorldPos.getY());
        tag.putInt(TAG_ORIG_Z, originalWorldPos.getZ());
        tag.putInt(TAG_MAX_BLOCKS, maxBlocks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shipId = tag.getLong(TAG_SHIP_ID);
        originalWorldPos = new BlockPos(tag.getInt(TAG_ORIG_X), tag.getInt(TAG_ORIG_Y), tag.getInt(TAG_ORIG_Z));
        maxBlocks = tag.contains(TAG_MAX_BLOCKS) ? tag.getInt(TAG_MAX_BLOCKS) : 50;
    }
}
