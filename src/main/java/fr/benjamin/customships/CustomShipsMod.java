package fr.benjamin.customships;

import com.mojang.logging.LogUtils;
import fr.benjamin.customships.assembly.ShipControllerAttachment;
import fr.benjamin.customships.command.CustomShipsCommands;
import fr.benjamin.customships.config.ModConfig;
import fr.benjamin.customships.control.ShipControlManager;
import fr.benjamin.customships.data.ShipRegistry;
import fr.benjamin.customships.network.ModPackets;
import fr.benjamin.customships.registry.ModBlockEntities;
import fr.benjamin.customships.registry.ModBlocks;
import fr.benjamin.customships.registry.ModItems;
import fr.benjamin.customships.registry.ModMenus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mod(CustomShipsMod.MODID)
public class CustomShipsMod {

    public static final String MODID = "customships";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final ShipControlManager SHIP_CONTROL_MANAGER = new ShipControlManager();
    private static ShipRegistry shipRegistry;

    public CustomShipsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModConfig.register(modEventBus);
        shipRegistry = new ShipRegistry(FMLPaths.CONFIGDIR.get().resolve("customships").resolve("ships.json"));
        shipRegistry.load();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(SHIP_CONTROL_MANAGER);
        MinecraftForge.EVENT_BUS.register(new CustomShipsCommands());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModPackets.register();
            registerVS2Attachments();
        });
    }

    /**
     * Registers our attachment type with VS2's attachment system.
     *
     * VS2 uses this registration to:
     *  - know the Class type for getAttachment() / setAttachment() lookups
     *  - call physTick() on it every physics tick (because it implements ShipPhysicsListener)
     *  - skip serialization (useTransientSerializer) since desired-velocity is runtime-only
     *
     * Must run after VS2 is initialised, which is guaranteed by:
     *  - VS2 declared as mandatory dependency with ordering=AFTER in mods.toml
     *  - enqueueWork() defers execution to after all mod constructors have run
     */
    private static void registerVS2Attachments() {
        var vsCore = VSGameUtilsKt.getVsCore();
        vsCore.registerAttachment(
                vsCore.newAttachmentRegistrationBuilder(ShipControllerAttachment.class)
                      .useTransientSerializer()
                      .build()
        );
        LOGGER.info("[CustomShips] Registered ShipControllerAttachment with VS2");
    }

    public static ShipControlManager getShipControlManager() {
        return SHIP_CONTROL_MANAGER;
    }

    public static ShipRegistry ships() {
        return shipRegistry;
    }
}
