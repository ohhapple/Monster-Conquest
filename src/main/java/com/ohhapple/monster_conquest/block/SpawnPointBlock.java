package com.ohhapple.monster_conquest.block;

import com.ohhapple.monster_conquest.menu.SpawnPointMenu;
import com.ohhapple.monster_conquest.path.PathRoutes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.SimpleMenuProvider;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;


/**
 * 刷怪点方块：
 *   · 有一个"生成面"—— 放置时朝向玩家的那一面（FACING 指向玩家），六个方向都行；
 *   · 刷出的单位紧贴生成面那一侧出现，并朝向生成面外侧；
 *   · 右键打开配置界面：选单位 / 阵营 / 形态 / 等级，调数量与间隔、上限，
 *     也可以放入一颗单位刷怪蛋当模板（按蛋的数据生成，蛋不消耗）。
 */
public class SpawnPointBlock extends DirectionalBlock implements EntityBlock {

    public static final MapCodec<SpawnPointBlock> CODEC = simpleCodec(SpawnPointBlock::new);

    public SpawnPointBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** 放置时：生成面朝向玩家（与募兵站同款规则，支持上下两面）。 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    /** 手上拿着东西右键 → 打开配置界面（潜行右键保留给"贴墙放方块"）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        openGui(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 空手右键 → 打开配置界面。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        openGui(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openGui(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return;
        // 刷怪点是配置型设施：只有创造模式玩家能打开配置界面
        if (!player.isCreative()) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.spawn_point.creative_only"), true);
            return;
        }
        sp.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new SpawnPointMenu(id, inventory, pos),
                        Component.translatable("gui.monster_conquest.spawn_point.title")),
                buf -> {
                    buf.writeVarInt(SpawnPointMenu.MODE_BLOCK);
                    buf.writeBlockPos(pos);
                });
    }

    // ===================== 方块实体 =====================

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnPointBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof SpawnPointBlockEntity spawner) {
                SpawnPointBlockEntity.serverTick(tickLevel, pos, tickState, spawner);
            }
        };
    }

    /**
     * 拆掉方块（挖掘 / 爆炸 / 指令）：模板蛋单独掉出来，别让它跟着方块一起消失；
     *   同时清掉这个刷怪点在世界上设过的巡逻路径 —— 路径只活在当前世界里，
     *   重新放下、复制、变成物品都不带走（重放时默认没有路径）。
     *   注意：方块本身不给掉落物（生存模式挖掉不掉方块，只能创造模式拿 / 中键复制）。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof SpawnPointBlockEntity spawner
                    && !spawner.getEgg().isEmpty()) {
                ItemStack egg = spawner.getEgg();
                spawner.setEgg(ItemStack.EMPTY);
                Block.popResource(level, pos, egg);
            }
            if (level instanceof ServerLevel serverLevel) {
                PathRoutes.get(serverLevel)
                        .clear(PathRoutes.spawnerKey(pos));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 创造模式的中键复制：连模板刷怪蛋一起复制（放下时由 BlockItem 自动写回方块实体）。
     *   生存模式只返回普通方块物品 —— 中键在生存里是"切到背包里那个方块"，带上数据反而会找不到。
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (player != null && player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof SpawnPointBlockEntity spawner) {
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(spawner.saveSettings()));
        }
        return stack;
    }
}
