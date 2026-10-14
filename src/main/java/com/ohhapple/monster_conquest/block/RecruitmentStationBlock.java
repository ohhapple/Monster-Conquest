package com.ohhapple.monster_conquest.block;

import com.mojang.serialization.MapCodec;
import com.ohhapple.monster_conquest.network.clientbound.recruit.OpenRecruitGuiPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 募兵站方块：
 *   · 有一个"生成面"—— 放置时朝向玩家的那一面（FACING 指向玩家）；
 *     六个方向都行：放地上、放天花板、贴墙都按"朝向玩家"定面；
 *   · 招募的士兵紧贴生成面那一侧生成；
 *   · 右键打开招募 GUI（消耗材料招募不同等级的玩家阵营士兵）。
 */
public class RecruitmentStationBlock extends DirectionalBlock {

    public static final MapCodec<RecruitmentStationBlock> CODEC =
            simpleCodec(RecruitmentStationBlock::new);

    public RecruitmentStationBlock(Properties properties) {
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

    /** 放置时：生成面朝向玩家（和发射器 / 活塞"朝玩家"同款规则，支持上下两面）。 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    /** 手上拿着东西右键 → 同样打开 GUI（潜行右键保留给"贴墙放方块"）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        openGui(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 空手右键 → 打开 GUI。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        openGui(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openGui(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new OpenRecruitGuiPayload(pos));
        }
    }
}
