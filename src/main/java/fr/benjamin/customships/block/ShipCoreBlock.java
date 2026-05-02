package fr.benjamin.customships.block;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.assembly.ShipAssemblerHelper;
import fr.benjamin.customships.registry.ModBlockEntities;
import fr.benjamin.customships.scanner.ConnectedBlocksScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Set;

public class ShipCoreBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

    public ShipCoreBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SHIP_CORE.get().create(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        // VS2 API tells us whether this block is already part of an assembled ship.
        // We don't rely on BlockEntity NBT state because VS2 copies block entities to
        // ship-space before our code can set the assembled flag.
        LoadedServerShip managingShip = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);

        if (managingShip != null) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "§e[Ship Core] Vaisseau déjà assemblé. Utilisez un Ship Controller pour piloter."));
            return InteractionResult.SUCCESS;
        }

        // Not yet assembled — needs a BlockEntity for assembly.
        BlockEntity rawBE = level.getBlockEntity(pos);
        if (!(rawBE instanceof ShipCoreBlockEntity blockEntity)) {
            CustomShipsMod.LOGGER.warn("Ship Core at {} has no BlockEntity!", pos);
            return InteractionResult.FAIL;
        }
        return handleAssembly(serverLevel, serverPlayer, pos, blockEntity);
    }

    private InteractionResult handleAssembly(ServerLevel level, ServerPlayer player,
                                             BlockPos pos, ShipCoreBlockEntity blockEntity) {
        int maxBlocks = fr.benjamin.customships.config.ModConfig.MAX_SHIP_BLOCKS.get();

        Set<BlockPos> connectedBlocks = ConnectedBlocksScanner.scan(level, pos, maxBlocks);

        if (connectedBlocks == null) {
            player.sendSystemMessage(Component.literal(
                    "§c[Ship Core] Structure trop grande ! Limite : " + maxBlocks + " blocs."));
            return InteractionResult.FAIL;
        }
        if (connectedBlocks.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§c[Ship Core] Aucun bloc connecté détecté."));
            return InteractionResult.FAIL;
        }

        CustomShipsMod.LOGGER.info("Assembling ship with {} blocks at {} for player {}",
                connectedBlocks.size(), pos, player.getName().getString());

        // Save original position BEFORE assembly — VS2 will copy this NBT to ship-space entity.
        blockEntity.setOriginalWorldPos(pos);

        Long shipId = ShipAssemblerHelper.assembleShip(level, connectedBlocks, pos);
        if (shipId == null) {
            player.sendSystemMessage(Component.literal(
                    "§c[Ship Core] Échec de l'assemblage. Consulte les logs du serveur."));
            return InteractionResult.FAIL;
        }

        blockEntity.setShipId(shipId);

        player.sendSystemMessage(Component.literal(
                "§a[Ship Core] Vaisseau assemblé ! " + connectedBlocks.size()
                + " blocs. Placez/utilisez un Ship Controller pour piloter."));
        CustomShipsMod.LOGGER.info("Ship assembled successfully with ID {} for player {}", shipId, player.getName().getString());

        return InteractionResult.SUCCESS;
    }
}
