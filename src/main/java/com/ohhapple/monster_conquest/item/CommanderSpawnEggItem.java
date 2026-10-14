package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class CommanderSpawnEggItem extends Item {

    private final ResourceLocation defaultFaction;
    private final Supplier<EntityType<? extends AbstractArmyCommander>> typeSupplier;

    public CommanderSpawnEggItem(ResourceLocation defaultFaction,
                                 Supplier<EntityType<? extends AbstractArmyCommander>> typeSupplier,
                                 Properties props) {
        super(props);
        this.defaultFaction = defaultFaction;
        this.typeSupplier = typeSupplier;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        // 与原生刷怪蛋一致：点到的方块没有碰撞箱（雪片 / 草丛 / 树苗等）时就放在这一格本身，
        //   否则放在点击面外侧一格 —— 不再要求点击面是完整实心面，避免点草丛放不出单位
        BlockState clickedState = sl.getBlockState(clicked);
        BlockPos pos = clickedState.getCollisionShape(sl, clicked).isEmpty()
                ? clicked
                : clicked.relative(face);

        EntityType<? extends AbstractArmyCommander> type = typeSupplier.get();
        AbstractArmyCommander commander = type.create(sl);
        if (commander == null) return InteractionResult.PASS;

        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        commander.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String override = tag.getString(RetrievalToolItem.KEY_FACTION);
        ResourceLocation faction = override.isEmpty()
                ? defaultFaction
                : ResourceLocation.tryParse(override);
        if (faction != null) commander.setFactionId(faction);
        // 收纳工具做出来的蛋带着单位界面里配置的手动装备 / 战术装备
        commander.readGearTag(tag);

        sl.addFreshEntity(commander);

        boolean cavalry = tag.getBoolean(RetrievalToolItem.KEY_CAVALRY);
        if (cavalry) {
            commander.spawnAndMountHorse();
        }

        sl.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL,
                1.0f, (sl.random.nextFloat() - sl.random.nextFloat()) * 0.2f + 1.0f);

        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String override = tag.getString(RetrievalToolItem.KEY_FACTION);
        ResourceLocation shown = override.isEmpty()
                ? defaultFaction
                : ResourceLocation.tryParse(override);

        // 阵营（翻译键）
        if (shown != null) {
            if (FactionIds.isPlayer(shown)) {
                tooltip.add(Component.translatable("tooltip.monster_conquest.player_faction",
                                playerName(shown))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltip.add(Component.translatable("faction.monster_conquest." + shown.getPath())
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        // 形态（翻译键）
        boolean cavalry = tag.getBoolean(RetrievalToolItem.KEY_CAVALRY);
        tooltip.add(Component.translatable("tooltip.monster_conquest.form",
                        Component.translatable("tooltip.monster_conquest.form."
                                + (cavalry ? "cavalry" : "infantry")))
                .withStyle(ChatFormatting.YELLOW));

        // 复用 SoldierSpawnEggItem 的提示逻辑（首领蛋不支持中键切换阵营）
        SoldierSpawnEggItem.appendActionHints(tooltip, tag, shown, false);

        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    private static String playerName(ResourceLocation factionId) {
        UUID owner = FactionIds.parsePlayer(factionId);
        if (owner == null) return "?";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(owner);
                if (info != null && info.getProfile() != null) {
                    return info.getProfile().getName();
                }
            }
        } catch (Throwable ignored) { }
        String s = owner.toString();
        return s.substring(0, 8);
    }
}
