package fr.benjamin.customships.client;

import fr.benjamin.customships.CustomShipsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup. Registered via @Mod.EventBusSubscriber so it never
 * runs on dedicated servers (Dist.CLIENT).
 */
@Mod.EventBusSubscriber(modid = CustomShipsMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Register the client tick handler on the Forge event bus
        MinecraftForge.EVENT_BUS.register(new ClientInputHandler());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientInputHandler.LEAVE_SHIP_KEY);
    }
}
