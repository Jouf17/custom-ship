package fr.benjamin.customships.block;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.assembly.ShipControllerAttachment;
import fr.benjamin.customships.assembly.ShipPartStatsUpdater;
import fr.benjamin.customships.assembly.ShipPartStatsUpdater.ShipPartType;
import fr.benjamin.customships.control.ShipControlManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShipControllerBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

    public ShipControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            ShipPartStatsUpdater.placed(level, pos, ShipPartType.CONTROLLER);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(state.getBlock())) {
            ShipPartStatsUpdater.removed(level, pos, ShipPartType.CONTROLLER);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (serverPlayer.isShiftKeyDown() && !CustomShipsMod.getShipControlManager().isPiloting(serverPlayer.getUUID())) {
            Direction next = state.getValue(FACING).getClockWise();
            level.setBlock(pos, state.setValue(FACING, next), 3);
            LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
            ShipControllerAttachment controller = ship == null ? null : ship.getAttachment(ShipControllerAttachment.class);
            if (controller != null) {
                controller.setForwardDirection(next);
            }
            serverPlayer.sendSystemMessage(Component.literal("§a[Ship Controller] Sens: " + directionName(next) + "."));
            return InteractionResult.SUCCESS;
        }

        LoadedServerShip managingShip = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);

        if (managingShip == null) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§c[Ship Controller] Aucun vaisseau assemblé connecté. Assemblez d'abord le Ship Core."));
            return InteractionResult.FAIL;
        }

        ShipControllerAttachment controller = managingShip.getAttachment(ShipControllerAttachment.class);
        if (controller == null || !controller.hasCore()) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§c[Ship Controller] Pilotage impossible : aucun Ship Core actif sur ce vaisseau."));
            return InteractionResult.FAIL;
        }
        controller.setForwardDirection(state.getValue(FACING));

        ShipControlManager controlManager = CustomShipsMod.getShipControlManager();
        if (controlManager.isPiloting(serverPlayer.getUUID())) {
            if (serverPlayer.isShiftKeyDown()) {
                controlManager.stopPiloting(serverPlayer);
                serverPlayer.sendSystemMessage(Component.literal(
                        "§e[Ship Controller] Vous avez quitté les commandes."));
            } else {
                serverPlayer.sendSystemMessage(Component.literal(
                        "§e[Ship Controller] Vous êtes déjà aux commandes. V ou /ship leave pour quitter."));
            }
            return InteractionResult.SUCCESS;
        }

        boolean started = controlManager.startPiloting(serverPlayer, managingShip, serverLevel, pos);
        if (started) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§a[Ship Controller] Pilotage ! Z/S=avancer/reculer, Q/D=tourner, Espace/Shift=monter/descendre, V=quitter."));
        } else {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§c[Ship Controller] Impossible de prendre les commandes."));
        }

        return InteractionResult.SUCCESS;
    }

    private static String directionName(Direction direction) {
        return switch (direction) {
            case NORTH -> "nord";
            case SOUTH -> "sud";
            case EAST -> "est";
            case WEST -> "ouest";
            default -> direction.getName();
        };
    }
}
