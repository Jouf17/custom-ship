package fr.benjamin.customships.network;

import fr.benjamin.customships.CustomShipsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client to server every tick while the player is piloting.
 * Encodes which movement keys are pressed and the player's yaw.
 *
 * Bit mask layout for the directions byte:
 *   bit 0 = forward
 *   bit 1 = back
 *   bit 2 = left
 *   bit 3 = right
 *   bit 4 = up
 *   bit 5 = down
 */
public class ShipControlPacket {

    private static final byte FWD   = 1;
    private static final byte BACK  = 1 << 1;
    private static final byte LEFT  = 1 << 2;
    private static final byte RIGHT = 1 << 3;
    private static final byte UP    = 1 << 4;
    private static final byte DOWN  = 1 << 5;
    private static final byte EXIT  = 1 << 6;

    private final byte directions;
    private final float yaw;

    public ShipControlPacket(boolean forward, boolean back, boolean left, boolean right,
                             boolean up, boolean down, float yaw) {
        this(forward, back, left, right, up, down, false, yaw);
    }

    public ShipControlPacket(boolean forward, boolean back, boolean left, boolean right,
                             boolean up, boolean down, boolean exit, float yaw) {
        byte d = 0;
        if (forward) d |= FWD;
        if (back)    d |= BACK;
        if (left)    d |= LEFT;
        if (right)   d |= RIGHT;
        if (up)      d |= UP;
        if (down)    d |= DOWN;
        if (exit)    d |= EXIT;
        this.directions = d;
        this.yaw = yaw;
    }

    private ShipControlPacket(byte directions, float yaw) {
        this.directions = directions;
        this.yaw = yaw;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(directions);
        buf.writeFloat(yaw);
    }

    public static ShipControlPacket decode(FriendlyByteBuf buf) {
        byte directions = buf.readByte();
        float yaw = buf.readFloat();
        return new ShipControlPacket(directions, yaw);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            if ((directions & EXIT) != 0) {
                CustomShipsMod.getShipControlManager().stopPiloting(player);
                return;
            }

            CustomShipsMod.getShipControlManager().handleControlInput(
                    player,
                    (directions & FWD)   != 0,
                    (directions & BACK)  != 0,
                    (directions & LEFT)  != 0,
                    (directions & RIGHT) != 0,
                    (directions & UP)    != 0,
                    (directions & DOWN)  != 0,
                    yaw,
                    serverLevel
            );
        });
        ctx.setPacketHandled(true);
    }
}
