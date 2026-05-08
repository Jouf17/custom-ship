package fr.benjamin.customships.registry;

import fr.benjamin.customships.CustomShipsMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CustomShipsMod.MODID);

    public static final RegistryObject<Item> SHIP_CORE = ITEMS.register("ship_core",
            () -> new BlockItem(ModBlocks.SHIP_CORE.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> SHIP_CONTROLLER = ITEMS.register("ship_controller",
            () -> new BlockItem(ModBlocks.SHIP_CONTROLLER.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> SHIP_REACTOR = ITEMS.register("ship_reactor",
            () -> new BlockItem(ModBlocks.SHIP_REACTOR.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> SHIP_STABILIZER = ITEMS.register("ship_stabilizer",
            () -> new BlockItem(ModBlocks.SHIP_STABILIZER.get(), new Item.Properties())
    );
}
