package fr.benjamin.customships.registry;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.menu.ShipCoreMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CustomShipsMod.MODID);

    public static final RegistryObject<MenuType<ShipCoreMenu>> SHIP_CORE =
            MENUS.register("ship_core", () -> IForgeMenuType.create(ShipCoreMenu::new));
}
