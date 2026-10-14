package com.ohhapple.monster_conquest.block;

import com.mojang.serialization.MapCodec;
import com.ohhapple.monster_conquest.network.clientbound.trade.OpenTradeGuiPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 交易站方块：右键打开交易 GUI（出售子弹 / 枪械 / 各种战术工具，可批量购买）。
 */
public class TradeStationBlock extends Block {

    public static final MapCodec<TradeStationBlock> CODEC = simpleCodec(TradeStationBlock::new);

    public TradeStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** 手上拿着东西右键 → 打开 GUI（潜行右键保留给"贴墙放方块"）。 */
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
            PacketDistributor.sendToPlayer(sp, new OpenTradeGuiPayload(pos));
        }
    }
}
