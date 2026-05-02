package fr.benjamin.customships.network;

import fr.benjamin.customships.client.ClientInputHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from server to client to start or stop piloting mode.
 * On receipt, the client handler enables/disables sending control packets.
 */
public class PilotStatePacket {

    private final boolean piloting;

    public PilotStatePacket(boolean piloting) {
        this.piloting = piloting;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(piloting);
    }

    public static PilotStatePacket decode(FriendlyByteBuf buf) {
        return new PilotStatePacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(piloting))
        );
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(boolean isPiloting) {
        ClientInputHandler.setPiloting(isPiloting);
    }
}
