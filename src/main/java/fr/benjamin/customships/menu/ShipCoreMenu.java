package fr.benjamin.customships.menu;

import fr.benjamin.customships.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class ShipCoreMenu extends AbstractContainerMenu {

    private final BlockPos corePos;
    private final int blockCount;
    private final int capacity;
    private final boolean assembled;
    private final double maxSpeed;
    private final int stabilizerCount;
    private final int stabilizerCapacity;

    public ShipCoreMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readDouble(), buf.readVarInt(), buf.readVarInt());
    }

    public ShipCoreMenu(int containerId, Inventory inventory, BlockPos corePos, int blockCount, int capacity, boolean assembled,
                        double maxSpeed, int stabilizerCount, int stabilizerCapacity) {
        super(ModMenus.SHIP_CORE.get(), containerId);
        this.corePos = corePos;
        this.blockCount = blockCount;
        this.capacity = capacity;
        this.assembled = assembled;
        this.maxSpeed = maxSpeed;
        this.stabilizerCount = stabilizerCount;
        this.stabilizerCapacity = stabilizerCapacity;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRemainingBlocks() {
        return Math.max(0, capacity - blockCount);
    }

    public boolean isAssembled() {
        return assembled;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public int getStabilizerCount() {
        return stabilizerCount;
    }

    public int getStabilizerCapacity() {
        return stabilizerCapacity;
    }

    public int getMissingStabilizedBlocks() {
        return Math.max(0, blockCount - stabilizerCapacity);
    }
}
