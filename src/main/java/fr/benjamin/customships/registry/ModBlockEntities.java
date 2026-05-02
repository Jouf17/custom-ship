package fr.benjamin.customships.registry;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.block.ShipCoreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CustomShipsMod.MODID);

    public static final RegistryObject<BlockEntityType<ShipCoreBlockEntity>> SHIP_CORE =
            BLOCK_ENTITIES.register("ship_core",
                    () -> BlockEntityType.Builder
                            .of(ShipCoreBlockEntity::new, ModBlocks.SHIP_CORE.get())
                            .build(null)
            );
}
