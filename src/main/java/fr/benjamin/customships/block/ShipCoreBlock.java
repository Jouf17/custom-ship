package fr.benjamin.customships.block;

import fr.benjamin.customships.CustomShipsMod;
import fr.benjamin.customships.assembly.ShipAssemblerHelper;
import fr.benjamin.customships.assembly.ShipControllerAttachment;
import fr.benjamin.customships.assembly.ShipPartStatsUpdater;
import fr.benjamin.customships.assembly.ShipPartStatsUpdater.ShipPartType;
import fr.benjamin.customships.assembly.ShipStatsScanner;
import fr.benjamin.customships.assembly.ShipStatsScanner.ShipStats;
import fr.benjamin.customships.config.ModConfig;
import fr.benjamin.customships.menu.ShipCoreMenu;
import fr.benjamin.customships.registry.ModBlockEntities;
import fr.benjamin.customships.scanner.ConnectedBlocksScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
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
        BlockEntity rawBE = level.getBlockEntity(pos);
        if (!(rawBE instanceof ShipCoreBlockEntity)) {
            CustomShipsMod.LOGGER.warn("Ship Core at {} has no BlockEntity!", pos);
            return InteractionResult.FAIL;
        }

        AssemblyInfo info = getAssemblyInfo(serverLevel, pos);
        LoadedServerShip managingShip = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, pos);
        boolean assembled = managingShip != null;

        NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Ship Core");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new ShipCoreMenu(containerId, inventory, pos, info.blockCount, info.capacity, assembled,
                        info.maxSpeed, info.stabilizerCount, info.stabilizerCapacity);
            }
        }, buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(info.blockCount);
            buf.writeVarInt(info.capacity);
            buf.writeBoolean(assembled);
            buf.writeDouble(info.maxSpeed);
            buf.writeVarInt(info.stabilizerCount);
            buf.writeVarInt(info.stabilizerCapacity);
        });

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            ShipPartStatsUpdater.placed(level, pos, ShipPartType.CORE);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(state.getBlock())) {
            ShipPartStatsUpdater.removed(level, pos, ShipPartType.CORE);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static InteractionResult assembleFromCore(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockEntity rawBE = level.getBlockEntity(pos);
        if (!(rawBE instanceof ShipCoreBlockEntity blockEntity)) {
            CustomShipsMod.LOGGER.warn("Ship Core at {} has no BlockEntity!", pos);
            return InteractionResult.FAIL;
        }

        if (VSGameUtilsKt.getLoadedShipManagingPos(level, pos) != null) {
            player.sendSystemMessage(Component.literal("§e[Ship Core] Vaisseau deja assemble."));
            return InteractionResult.SUCCESS;
        }

        AssemblyInfo info = getAssemblyInfo(level, pos);
        Set<BlockPos> connectedBlocks = info.blocks;
        if (connectedBlocks.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Ship Core] Aucun bloc connecte detecte."));
            return InteractionResult.FAIL;
        }

        if (info.blockCount > info.capacity) {
            player.sendSystemMessage(Component.literal(
                    "§c[Ship Core] Pas assez de capacite : " + info.blockCount + "/" + info.capacity
                            + " blocs. Ajoutez des coeurs ou augmentez leur capacite avec /ship coeur <nombre>."));
            return InteractionResult.FAIL;
        }

        CustomShipsMod.LOGGER.info("Assembling ship with {} blocks at {} for player {}",
                connectedBlocks.size(), pos, player.getName().getString());
        ShipStats stats = ShipStatsScanner.count(level, connectedBlocks);

        blockEntity.setOriginalWorldPos(pos);

        Long vsShipId = ShipAssemblerHelper.assembleShip(level, connectedBlocks, pos);
        if (vsShipId == null) {
            player.sendSystemMessage(Component.literal(
                    "§c[Ship Core] Echec de l'assemblage. Consulte les logs du serveur."));
            return InteractionResult.FAIL;
        }

        blockEntity.setShipId(vsShipId);
        applyShipStats(level, vsShipId, stats);
        var registeredShip = CustomShipsMod.ships().register(vsShipId, level, pos, stats);

        player.sendSystemMessage(Component.literal(
                "§a[Ship Core] Vaisseau #" + registeredShip.shipId + " assemble ! " + connectedBlocks.size()
                        + " blocs. Capacite : " + info.capacity + "."));
        CustomShipsMod.LOGGER.info("Ship assembled successfully with public ID {} / VS ID {} for player {}",
                registeredShip.shipId, vsShipId, player.getName().getString());

        return InteractionResult.SUCCESS;
    }

    public static InteractionResult disassembleFromCore(ServerLevel level, ServerPlayer player, BlockPos pos) {
        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship == null) {
            player.sendSystemMessage(Component.literal("§e[Ship Core] Ce coeur n'est pas dans un vaisseau assemble."));
            return InteractionResult.SUCCESS;
        }

        player.sendSystemMessage(Component.literal(
                "§c[Ship Core] Desassemblage non disponible pour l'instant : VS2 ne fournit pas ici d'inverse sur de l'assemblage."));
        return InteractionResult.FAIL;
    }

    private static AssemblyInfo getAssemblyInfo(ServerLevel level, BlockPos pos) {
        Set<BlockPos> blocks = ConnectedBlocksScanner.scan(level, pos);
        ShipStats stats = ShipStatsScanner.count(level, blocks);
        int capacity = 0;
        for (BlockPos blockPos : blocks) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof ShipCoreBlockEntity core) {
                capacity += core.getMaxBlocks();
            }
        }
        if (capacity <= 0) {
            capacity = 50;
        }
        double maxSpeed = ShipControllerAttachment.calculateMaxSpeed(
                ModConfig.SHIP_SPEED.get(),
                stats.blockCount(),
                stats.reactorCount()
        );
        int stabilizerCapacity = stats.stabilizerCount() * ShipControllerAttachment.BLOCKS_PER_STABILIZER;
        return new AssemblyInfo(blocks, blocks.size(), capacity, maxSpeed, stats.stabilizerCount(), stabilizerCapacity);
    }

    private static void applyShipStats(ServerLevel level, long shipId, ShipStats stats) {
        LoadedServerShip ship = ShipAssemblerHelper.getLoadedShipById(level, shipId);
        if (ship == null) {
            CustomShipsMod.LOGGER.warn("[CustomShips] Ship {} was not loaded immediately after assembly; stats will use defaults", shipId);
            return;
        }

        ShipControllerAttachment controller = ship.getOrPutAttachment(
                ShipControllerAttachment.class,
                ShipControllerAttachment::new
        );
        controller.setShipStats(stats.blockCount(), stats.coreCount(), stats.reactorCount(), stats.stabilizerCount());
        CustomShipsMod.LOGGER.info("[CustomShips] Ship {} stats: blocks={}, cores={}, reactors={}, stabilizers={}",
                shipId, stats.blockCount(), stats.coreCount(), stats.reactorCount(), stats.stabilizerCount());
    }

    private record AssemblyInfo(Set<BlockPos> blocks, int blockCount, int capacity,
                                double maxSpeed, int stabilizerCount, int stabilizerCapacity) {}
}
