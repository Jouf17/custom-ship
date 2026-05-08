package fr.benjamin.customships.network;

import fr.benjamin.customships.block.ShipCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShipCoreActionPacket {

    public enum Action {
        ASSEMBLE,
        DISASSEMBLE
    }

    private final BlockPos corePos;
    private final Action action;

    public ShipCoreActionPacket(BlockPos corePos, Action action) {
        this.corePos = corePos;
        this.action = action;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeEnum(action);
    }

    public static ShipCoreActionPacket decode(FriendlyByteBuf buf) {
        return new ShipCoreActionPacket(buf.readBlockPos(), buf.readEnum(Action.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) return;
            if (player.distanceToSqr(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5) > 64.0) return;

            if (action == Action.ASSEMBLE) {
                ShipCoreBlock.assembleFromCore(level, player, corePos);
            } else {
                ShipCoreBlock.disassembleFromCore(level, player, corePos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
