package fr.benjamin.customships.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

public class ModConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue SHIP_SPEED = BUILDER
            .comment("Base movement speed of ships in blocks/second (default: 5.0)")
            .defineInRange("shipSpeed", 5.0, 0.1, 50.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(Type.COMMON, SPEC);
    }
}
