package fr.benjamin.customships.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.config.ModConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CustomShipsCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("customships")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("maxShipBlocks")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 10000))
                                        .executes(context -> {
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            ModConfig.setMaxShipBlocks(value);
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("[CustomShips] maxShipBlocks = " + value),
                                                    true
                                            );
                                            return value;
                                        })
                                )
                        )
        );

        event.getDispatcher().register(
                Commands.literal("ship")
                        .then(Commands.literal("leave")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    if (CustomShipsMod.getShipControlManager().isPiloting(player.getUUID())) {
                                        CustomShipsMod.getShipControlManager().stopPiloting(player);
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("[CustomShips] Vous avez quitté les commandes."),
                                                false
                                        );
                                    } else {
                                        context.getSource().sendFailure(
                                                Component.literal("[CustomShips] Vous ne pilotez aucun vaisseau.")
                                        );
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
