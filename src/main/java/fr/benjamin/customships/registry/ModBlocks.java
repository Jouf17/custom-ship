package fr.benjamin.customships.registry;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.block.ShipControllerBlock;
import fr.benjamin.customships.block.ShipCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CustomShipsMod.MODID);

    public static final RegistryObject<Block> SHIP_CORE = BLOCKS.register("ship_core",
            () -> new ShipCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            )
    );

    public static final RegistryObject<Block> SHIP_CONTROLLER = BLOCKS.register("ship_controller",
            () -> new ShipControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            )
    );
}
