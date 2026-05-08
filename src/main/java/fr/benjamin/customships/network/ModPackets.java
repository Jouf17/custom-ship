package fr.benjamin.customships.network;

import fr.benjamin.customships.CustomShipsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModPackets {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CustomShipsMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        // Client -> Server: movement inputs
        CHANNEL.registerMessage(id++,
                ShipControlPacket.class,
                ShipControlPacket::encode,
                ShipControlPacket::decode,
                ShipControlPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Server -> Client: pilot state change
        CHANNEL.registerMessage(id++,
                PilotStatePacket.class,
                PilotStatePacket::encode,
                PilotStatePacket::decode,
                PilotStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                ShipCoreActionPacket.class,
                ShipCoreActionPacket::encode,
                ShipCoreActionPacket::decode,
                ShipCoreActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
