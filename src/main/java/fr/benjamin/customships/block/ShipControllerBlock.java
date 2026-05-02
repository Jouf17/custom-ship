package fr.benjamin.customships.block;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.control.ShipControlManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShipControllerBlock extends Block {

    private static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

    public ShipControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        LoadedServerShip managingShip = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);

        if (managingShip == null) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§c[Ship Controller] Aucun vaisseau assemblé connecté. Assemblez d'abord le Ship Core."));
            return InteractionResult.FAIL;
        }

        ShipControlManager controlManager = CustomShipsMod.getShipControlManager();
        if (controlManager.isPiloting(serverPlayer.getUUID())) {
            if (serverPlayer.isShiftKeyDown()) {
                controlManager.stopPiloting(serverPlayer);
                serverPlayer.sendSystemMessage(Component.literal(
                        "§e[Ship Controller] Vous avez quitté les commandes."));
            } else {
                serverPlayer.sendSystemMessage(Component.literal(
                        "§e[Ship Controller] Vous êtes déjà aux commandes. Sneak + clic-droit pour quitter."));
            }
            return InteractionResult.SUCCESS;
        }

        boolean started = controlManager.startPiloting(serverPlayer, managingShip, serverLevel, pos);
        if (started) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§a[Ship Controller] Pilotage ! Z/S=avancer/reculer, Q/D=tourner, Espace/Shift=monter/descendre."));
        } else {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§c[Ship Controller] Impossible de prendre les commandes."));
        }

        return InteractionResult.SUCCESS;
    }
}
