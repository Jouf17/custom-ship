package fr.benjamin.customships.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.assembly.ShipAssemblerHelper;
import fr.benjamin.customships.assembly.ShipControllerAttachment;
import fr.benjamin.customships.block.ShipCoreBlockEntity;
import fr.benjamin.customships.data.ShipRegistry.ShipData;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.bodies.properties.BodyTransform;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.core.internal.ShipTeleportData;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CustomShipsCommands {
    private static final int PAGE_SIZE = 10;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ship")
                        .then(Commands.literal("leave")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    if (CustomShipsMod.getShipControlManager().isPiloting(player.getUUID())) {
                                        CustomShipsMod.getShipControlManager().stopPiloting(player);
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("[CustomShips] Vous avez quitte les commandes."),
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
                        .then(Commands.literal("coeur")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 10000))
                                        .executes(context -> setTargetCoreCapacity(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "blocks")
                                        ))
                                )
                        )
                        .then(Commands.literal("core")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 10000))
                                        .executes(context -> setTargetCoreCapacity(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "blocks")
                                        ))
                                )
                        )
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> listShips(context.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> listShips(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "page")
                                        ))
                                )
                        )
                        .then(Commands.literal("info")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("shipId", LongArgumentType.longArg(1))
                                        .executes(context -> showShipInfo(
                                                context.getSource().getPlayerOrException(),
                                                LongArgumentType.getLong(context, "shipId")
                                        ))
                                )
                        )
                        .then(Commands.literal("tp")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("shipId", LongArgumentType.longArg(1))
                                        .executes(context -> teleportToShip(
                                                context.getSource().getPlayerOrException(),
                                                LongArgumentType.getLong(context, "shipId")
                                        ))
                                )
                        )
                        .then(Commands.literal("tphere")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("shipId", LongArgumentType.longArg(1))
                                        .executes(context -> teleportShipHere(
                                                context.getSource().getPlayerOrException(),
                                                LongArgumentType.getLong(context, "shipId")
                                        ))
                                )
                        )
                        .then(Commands.literal("delete")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("shipId", LongArgumentType.longArg(1))
                                        .executes(context -> showDeleteConfirmation(
                                                context.getSource().getPlayerOrException(),
                                                LongArgumentType.getLong(context, "shipId")
                                        ))
                                        .then(Commands.literal("--confirm")
                                                .executes(context -> deleteShip(
                                                        context.getSource().getPlayerOrException(),
                                                        LongArgumentType.getLong(context, "shipId")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("register")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> registerTargetShip(context.getSource().getPlayerOrException()))
                        )
        );
    }

    private int setTargetCoreCapacity(ServerPlayer player, int blocks) {
        HitResult hit = player.pick(8.0F, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            player.sendSystemMessage(Component.literal("[CustomShips] Regardez un Ship Core puis refaites la commande."));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof ShipCoreBlockEntity core)) {
            player.sendSystemMessage(Component.literal("[CustomShips] Le bloc vise n'est pas un Ship Core."));
            return 0;
        }

        core.setMaxBlocks(blocks);
        player.sendSystemMessage(Component.literal("[CustomShips] Capacite du Ship Core reglee a " + blocks + " blocs."));
        return blocks;
    }

    private int listShips(ServerPlayer player, int page) {
        List<ShipData> ships = new ArrayList<>(CustomShipsMod.ships().all());
        ships.sort(Comparator.comparingLong(ship -> ship.shipId));
        if (ships.isEmpty()) {
            player.sendSystemMessage(Component.literal("Â§e[CustomShips] Aucun vaisseau enregistre."));
            return 1;
        }

        int totalPages = Math.max(1, (int) Math.ceil(ships.size() / (double) PAGE_SIZE));
        int currentPage = Math.min(page, totalPages);
        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, ships.size());
        player.sendSystemMessage(Component.literal("Â§6=== Vaisseaux (" + ships.size() + ") - page " + currentPage + "/" + totalPages + " ==="));
        for (ShipData ship : ships.subList(from, to)) {
            ServerLevel level = CustomShipsMod.ships().findLevel(player.server, ship);
            boolean loaded = level != null && ShipAssemblerHelper.getLoadedShipById(level, ship.vsShipId) != null;
            player.sendSystemMessage(Component.literal(String.format(
                    "Â§f#%d Â§7%s Â§f%,d blocs Â§7reacteurs:%d stab:%d Â§8%s Â§8(vs:%d)",
                    ship.shipId,
                    ship.worldName,
                    ship.blockCount,
                    ship.reactorCount,
                    ship.stabilizerCount,
                    loaded ? "charge" : "hors-ligne",
                    ship.vsShipId
            )));
        }
        return 1;
    }

    private int showShipInfo(ServerPlayer player, long shipId) {
        ShipData ship = CustomShipsMod.ships().get(shipId);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("Â§c[CustomShips] Vaisseau #" + shipId + " introuvable."));
            return 0;
        }

        ServerLevel level = CustomShipsMod.ships().findLevel(player.server, ship);
        LoadedServerShip loaded = level == null ? null : ShipAssemblerHelper.getLoadedShipById(level, ship.vsShipId);
        Vector3dc current = currentPosition(loaded);
        if (current != null) {
            CustomShipsMod.ships().updateLastPosition(ship.shipId, current.x(), current.y(), current.z());
        }

        double x = current == null ? ship.lastX : current.x();
        double y = current == null ? ship.lastY : current.y();
        double z = current == null ? ship.lastZ : current.z();
        player.sendSystemMessage(Component.literal("Â§6=== Vaisseau #" + ship.shipId + " ==="));
        player.sendSystemMessage(Component.literal("Â§7Monde: Â§f" + ship.worldName + " Â§7| etat: Â§f" + (loaded == null ? "hors-ligne" : "charge")));
        player.sendSystemMessage(Component.literal("Â§7ID VS2: Â§f" + ship.vsShipId));
        player.sendSystemMessage(Component.literal("Â§7Taille: Â§f" + ship.blockCount + " blocs Â§7| cores: Â§f" + ship.coreCount
                + " Â§7| reacteurs: Â§f" + ship.reactorCount + " Â§7| stabilisateurs: Â§f" + ship.stabilizerCount));
        player.sendSystemMessage(Component.literal(String.format("Â§7Core origine: Â§f%d %d %d", ship.coreX, ship.coreY, ship.coreZ)));
        player.sendSystemMessage(Component.literal(String.format("Â§7Position: Â§f%.1f %.1f %.1f", x, y, z)));
        return 1;
    }

    private int teleportToShip(ServerPlayer player, long shipId) {
        ShipData ship = CustomShipsMod.ships().get(shipId);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("Â§c[CustomShips] Vaisseau #" + shipId + " introuvable."));
            return 0;
        }

        ServerLevel level = CustomShipsMod.ships().findLevel(player.server, ship);
        if (level == null) {
            player.sendSystemMessage(Component.literal("Â§c[CustomShips] Monde introuvable: " + ship.worldName));
            return 0;
        }

        LoadedServerShip loaded = ShipAssemblerHelper.getLoadedShipById(level, ship.vsShipId);
        Vector3dc position = currentPosition(loaded);
        double x = position == null ? ship.lastX : position.x();
        double y = position == null ? ship.lastY : position.y();
        double z = position == null ? ship.lastZ : position.z();
        if (position != null) {
            CustomShipsMod.ships().updateLastPosition(ship.shipId, x, y, z);
        }

        player.teleportTo(level, x, y + 2.0D, z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Â§a[CustomShips] TP au vaisseau #" + ship.shipId + "."));
        return 1;
    }

    private int teleportShipHere(ServerPlayer player, long shipId) {
        ShipData data = CustomShipsMod.ships().get(shipId);
        if (data == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau #" + shipId + " introuvable."));
            return 0;
        }

        ServerLevel currentLevel = CustomShipsMod.ships().findLevel(player.server, data);
        if (currentLevel == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Monde du vaisseau introuvable: " + data.worldName));
            return 0;
        }

        ServerShip ship = ShipAssemblerHelper.getShipById(currentLevel, data.vsShipId);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau VS2 #" + data.vsShipId + " introuvable."));
            return 0;
        }

        try {
            ServerLevel targetLevel = player.serverLevel();
            Vector3d target = new Vector3d(player.getX(), player.getY(), player.getZ());
            BodyTransform transform = ship.getTransform();
            ShipTeleportData teleport = ValkyrienSkiesMod.getVsCore().newShipTeleportData(
                    target,
                    transform.getRotation(),
                    ship.getVelocity(),
                    ship.getAngularVelocity(),
                    VSGameUtilsKt.getDimensionId(targetLevel),
                    transform.getScaling().x(),
                    transform.getPositionInModel()
            );
            ValkyrienSkiesMod.getVsCore().teleportShip(
                    (ServerShipWorld) VSGameUtilsKt.getShipObjectWorld(currentLevel),
                    ship,
                    teleport
            );

            data.worldName = targetLevel.dimension().location().toString();
            CustomShipsMod.ships().updateLastPosition(data.shipId, target.x(), target.y(), target.z());
            player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau #" + data.shipId + " teleporte a votre position."));
            return 1;
        } catch (RuntimeException e) {
            CustomShipsMod.LOGGER.error("[CustomShips] Unable to teleport ship {} to player {}", data.shipId, player.getName().getString(), e);
            player.sendSystemMessage(Component.literal("[CustomShips] TP impossible: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
            return 0;
        }
    }

    private int showDeleteConfirmation(ServerPlayer player, long shipId) {
        ShipData ship = CustomShipsMod.ships().get(shipId);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau #" + shipId + " introuvable."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("[CustomShips] Suppression du vaisseau #" + ship.shipId
                + " (" + ship.blockCount + " blocs)."));
        player.sendSystemMessage(Component.literal("[CustomShips] Confirmez avec: /ship delete " + ship.shipId + " --confirm"));
        return 1;
    }

    private int deleteShip(ServerPlayer player, long shipId) {
        ShipData ship = CustomShipsMod.ships().get(shipId);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau #" + shipId + " introuvable."));
            return 0;
        }

        ServerLevel level = CustomShipsMod.ships().findLevel(player.server, ship);
        if (level == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Monde introuvable: " + ship.worldName));
            return 0;
        }

        LoadedServerShip loaded = ShipAssemblerHelper.getLoadedShipById(level, ship.vsShipId);
        if (loaded == null) {
            player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau #" + ship.shipId
                    + " non charge. Chargez la zone du vaisseau puis relancez la commande."));
            return 0;
        }

        int deleted = ShipAssembler.INSTANCE.deleteShip(level, loaded, true, false);
        if (deleted <= 0) {
            player.sendSystemMessage(Component.literal("[CustomShips] VS2 n'a pas pu supprimer les blocs du vaisseau #"
                    + ship.shipId + ". Registre conserve."));
            return 0;
        }

        CustomShipsMod.ships().remove(ship.shipId);
        player.sendSystemMessage(Component.literal("[CustomShips] Vaisseau #" + ship.shipId
                + " supprime: blocs retires et registre nettoye."));
        return 1;
    }

    private int registerTargetShip(ServerPlayer player) {
        HitResult hit = player.pick(32.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            player.sendSystemMessage(Component.literal("Â§c[CustomShips] Regarde un bloc du vaisseau a enregistrer."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = blockHit.getBlockPos();
        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("Â§c[CustomShips] Aucun vaisseau VS2 charge sur ce bloc."));
            return 0;
        }

        ShipControllerAttachment controller = ship.getAttachment(ShipControllerAttachment.class);
        int blocks = controller == null ? 0 : controller.getBlockCount();
        int cores = controller == null ? 0 : controller.getCoreCount();
        int reactors = controller == null ? 0 : controller.getReactorCount();
        int stabilizers = controller == null ? 0 : controller.getStabilizerCount();
        Vector3dc position = currentPosition(ship);
        ShipData data = CustomShipsMod.ships().register(ship.getId(), level, pos, blocks, cores, reactors, stabilizers);
        if (position != null) {
            CustomShipsMod.ships().updateLastPosition(data.shipId, position.x(), position.y(), position.z());
        }
        player.sendSystemMessage(Component.literal("Â§a[CustomShips] Vaisseau #" + data.shipId + " enregistre. Â§8(vs:" + data.vsShipId + ")"));
        return 1;
    }

    private Vector3dc currentPosition(LoadedServerShip ship) {
        return ship == null ? null : ship.getTransform().getPositionInWorld();
    }
}
