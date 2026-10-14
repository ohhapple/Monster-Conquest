package com.ohhapple.monster_conquest.equipment;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/** 玩家数据附件：战术装备栏内容。 */
public final class ModAttachments {

    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Monster_conquest.MODID);

    /** 战术装备栏内容。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<ItemStack>>> TACTICAL_EQUIPMENT_SLOTS =
            ATTACHMENTS.register("tactical_equipment_slots", () -> AttachmentType
                    .<List<ItemStack>>builder(TacticalEquipmentSlotsData::newTacticalSlotsList)
                    .serialize(ItemStack.OPTIONAL_CODEC.listOf())
                    .copyOnDeath()
                    .build());
}
