package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.Faction;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.client.input.ModKeyMappings;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.util.FactionDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 说明书内容：
 *   · 固定章节（RTS / 逻辑 / 教程）来自语言文件，{key.xxx} 会替换成玩家当前的实际按键；
 *   · 阵营 / 敌对关系 / 首领 / 士兵章节按实时数据生成，数值直接读实体类的 getter，
 *     改实体类里的数值后书里同步变化。
 *   排版：每种单位之间画分隔线，T0~T4 数值用对应等级颜色（取自救刷怪蛋头像框），
 *   特点使用单独的颜色。
 */
public final class GuideBookContent {

    private GuideBookContent() {}

    // ===================== 颜色 =====================

    /** 小节标题（阵营名 / 规则名）。 */
    public static final int COLOR_HEADING = 0xFF8A3B12;
    /** 正文。 */
    public static final int COLOR_BODY = 0xFF2B2218;
    /** 条目。 */
    public static final int COLOR_BULLET = 0xFF35506E;
    /** 单位名字。 */
    public static final int COLOR_NAME = 0xFF7A2E0A;
    /** 特点说明。 */
    public static final int COLOR_TRAIT = 0xFF2F6B3A;
    /** 数值标签（生命 / 护甲 / 移速…）。 */
    public static final int COLOR_LABEL = 0xFF6B4A2A;
    /** 分隔线。 */
    public static final int COLOR_SEPARATOR = 0x556B4A2A;

    /**
     * T0~T4 颜色：取自救刷怪蛋贴图的头像框色（皮革 / 铁 / 白 / 青 / 紫），
     *   按纸张底色适当加深，保证在书页上仍清晰可读。
     */
    public static final int[] TIER_COLORS = {
            0xFFA06540,   // T0 皮革
            0xFF6E6E6E,   // T1 铁
            0xFF7C8794,   // T2 白（纸面加深为钢灰）
            0xFF2E8F82,   // T3 青（纸面加深）
            0xFF7A4FBF    // T4 紫（纸面加深）
    };

    public record Chapter(String id, int color) {
        public Component title() {
            return Component.translatable("guide.monster_conquest.chapter." + id);
        }
    }

    /** 一行内容：separator = 分隔线；blank = 空行。 */
    public record GuideLine(@Nullable MutableComponent text, int indent, int gapBefore,
                            boolean separator, boolean blank) {
        public static GuideLine of(MutableComponent text, int indent) {
            return new GuideLine(text, indent, 0, false, false);
        }
        public static GuideLine of(MutableComponent text, int indent, int gapBefore) {
            return new GuideLine(text, indent, gapBefore, false, false);
        }
        public static GuideLine blankLine() {
            return new GuideLine(null, 0, 6, false, true);
        }
        public static GuideLine separatorLine() {
            return new GuideLine(null, 0, 8, true, false);
        }
    }

    public static final List<Chapter> CHAPTERS = List.of(
            new Chapter("rts", 0xFF8CD0FF),
            new Chapter("logic", 0xFFB9E08A),
            new Chapter("tutorial", 0xFFFFE082),
            new Chapter("factions", 0xFFFFA0A0),
            new Chapter("relations", 0xFFE0A0FF),
            new Chapter("commanders", 0xFFFFB060),
            new Chapter("soldiers", 0xFFA0E0FF),
            new Chapter("world", 0xFFB0E0B0)
    );

    /** 巡逻队长（不在 Faction#getCommanderTypes 里，单独列出）。 */
    private static final List<EntityType<? extends AbstractArmyCommander>> PATROL_CAPTAINS = List.of(
            ModEntities.UNDEAD_PATROL_CAPTAIN.get(),
            ModEntities.VILLAGER_PATROL_CAPTAIN.get(),
            ModEntities.ILLAGER_PATROL_CAPTAIN.get(),
            ModEntities.NETHER_PATROL_CAPTAIN.get(),
            ModEntities.END_PATROL_CAPTAIN.get(),
            ModEntities.MONSTER_PATROL_CAPTAIN.get(),
            ModEntities.NEUTRAL_PATROL_CAPTAIN.get()
    );

    public static List<GuideLine> body(Chapter chapter) {
        return switch (chapter.id()) {
            case "factions" -> factionsBody();
            case "relations" -> relationsBody();
            case "commanders" -> commandersBody();
            case "soldiers" -> soldiersBody();
            default -> staticBody(chapter.id());
        };
    }

    // ===================== 固定章节 =====================

    private static List<GuideLine> staticBody(String id) {
        String raw = Component.translatable("guide.monster_conquest.chapter." + id + ".body").getString();
        List<GuideLine> out = new ArrayList<>();
        for (String line : raw.split("\n", -1)) {
            String text = line.strip();
            if (text.isEmpty()) {
                out.add(GuideLine.blankLine());
                continue;
            }
            boolean heading = text.startsWith("#");
            boolean bullet = !heading && text.startsWith("-");
            String content = heading || bullet ? text.substring(1).strip() : text;
            int color = heading ? COLOR_HEADING : (bullet ? COLOR_BULLET : COLOR_BODY);
            int indent = heading ? 0 : (bullet ? 10 : 6);
            out.add(GuideLine.of(Component.literal(resolveKeys(content)).withStyle(s -> s.withColor(color)),
                    indent, heading ? 6 : 0));
        }
        return out;
    }

    /** 把 {key.xxx} 替换成玩家当前实际按键（改键位后书里同步变化）。 */
    private static String resolveKeys(String text) {
        if (!text.contains("{key.")) return text;
        return text
                .replace("{key.faction}", keyName(ModKeyMappings.OPEN_FACTION_GUI))
                .replace("{key.wheel}", keyName(ModKeyMappings.COMMAND_WHEEL))
                .replace("{key.equipment}", keyName(ModKeyMappings.OPEN_EQUIPMENT))
                .replace("{key.rts}", keyName(ModKeyMappings.RTS_TOGGLE))
                .replace("{key.cavalry}", keyName(ModKeyMappings.RTS_RIDE_CAVALRY))
                .replace("{key.infantry}", keyName(ModKeyMappings.RTS_RIDE_INFANTRY))
                .replace("{key.formation1}", keyName(ModKeyMappings.RTS_FORMATION_1))
                .replace("{key.formation2}", keyName(ModKeyMappings.RTS_FORMATION_2))
                .replace("{key.formation3}", keyName(ModKeyMappings.RTS_FORMATION_3))
                .replace("{key.formation4}", keyName(ModKeyMappings.RTS_FORMATION_4))
                .replace("{key.formation5}", keyName(ModKeyMappings.RTS_FORMATION_5));
    }

    private static String keyName(net.minecraft.client.KeyMapping mapping) {
        try {
            return mapping.getTranslatedKeyMessage().getString();
        } catch (Throwable t) {
            return "?";
        }
    }

    // ===================== 阵营 / 敌对关系 =====================

    private static List<GuideLine> factionsBody() {
        List<GuideLine> out = new ArrayList<>();
        for (Faction faction : FactionRegistry.all()) {
            if (faction.isPlayerFaction()) continue;
            out.add(GuideLine.of(heading(factionName(faction.getId())), 0, 8));
            List<String> soldiers = new ArrayList<>();
            for (EntityType<? extends AbstractArmySoldier> type : faction.getSoldierTypes()) {
                soldiers.add(name(type));
            }
            out.add(GuideLine.of(labelled("guide.monster_conquest.stat.summon_natural",
                    soldiers.isEmpty() ? tr("guide.monster_conquest.stat.none")
                            : String.join("、", soldiers)), 8));
            out.add(GuideLine.blankLine());
        }
        out.add(GuideLine.of(heading(tr("guide.monster_conquest.section.player")), 0, 8));
        out.add(GuideLine.of(body(tr("guide.monster_conquest.player.note")), 8));
        return out;
    }

    private static List<GuideLine> relationsBody() {
        List<GuideLine> out = new ArrayList<>();
        for (Faction faction : FactionRegistry.all()) {
            if (faction.isPlayerFaction()) continue;
            out.add(GuideLine.of(heading(factionName(faction.getId())), 0, 8));
            out.add(GuideLine.of(labelled("guide.monster_conquest.stat.hostile",
                    factionList(faction.getDefaultHostileFactions())), 8));
            if (!faction.getDefaultAllyFactions().isEmpty()) {
                out.add(GuideLine.of(labelled("guide.monster_conquest.stat.ally",
                        factionList(faction.getDefaultAllyFactions())), 8));
            }
            out.add(GuideLine.blankLine());
        }
        out.add(GuideLine.of(heading(tr("guide.monster_conquest.section.player")), 0, 8));
        out.add(GuideLine.of(labelled("guide.monster_conquest.stat.hostile",
                factionListNames(FactionConfig.playerFactionHostile)), 8));
        out.add(GuideLine.blankLine());
        out.add(GuideLine.of(heading(tr("guide.monster_conquest.section.relation_rules")), 0, 8));
        for (String line : tr("guide.monster_conquest.relations.rules").split("\n")) {
            String text = line.strip();
            if (text.isEmpty()) continue;
            boolean bullet = text.startsWith("-");
            out.add(GuideLine.of(Component.literal((bullet ? text.substring(1).strip() : text))
                    .withStyle(s -> s.withColor(COLOR_BULLET)), bullet ? 10 : 6));
        }
        return out;
    }

    // ===================== 首领 / 士兵（动态数值） =====================

    private static List<GuideLine> commandersBody() {
        List<GuideLine> out = new ArrayList<>();
        for (Faction faction : FactionRegistry.all()) {
            if (faction.isPlayerFaction()) continue;
            out.add(GuideLine.of(heading(factionName(faction.getId())), 0, 8));
            for (EntityType<? extends AbstractArmyCommander> type : faction.getCommanderTypes()) {
                appendCommander(out, type, faction);
            }
        }
        out.add(GuideLine.of(heading(tr("guide.monster_conquest.section.patrol")), 0, 8));
        for (EntityType<? extends AbstractArmyCommander> type : PATROL_CAPTAINS) {
            appendCommander(out, type, null);
        }
        return out;
    }

    private static void appendCommander(List<GuideLine> out,
                                        EntityType<? extends AbstractArmyCommander> type,
                                        @Nullable Faction faction) {
        AbstractArmyCommander c = commanderSample(type);
        out.add(GuideLine.of(named(name(type)), 6, 6));
        if (c == null) {
            out.add(GuideLine.of(body(tr("guide.monster_conquest.stat.unknown")), 12));
            return;
        }
        out.add(GuideLine.of(flatStats(
                label("guide.monster_conquest.stat.health", num(c.getCommanderMaxHealth())),
                label("guide.monster_conquest.stat.armor", num(c.getCommanderArmor())),
                label("guide.monster_conquest.stat.melee", num(c.getCommanderAttackDamage())),
                label("guide.monster_conquest.stat.ranged", num(c.getCommanderRangedDamage())),
                label("guide.monster_conquest.stat.follow", num(c.getCommanderFollowRange()))
        ), 12));
        out.add(GuideLine.of(flatStats(
                label("guide.monster_conquest.stat.speed", num(c.getCommanderMovementSpeed())),
                label("guide.monster_conquest.stat.horse", num(c.getCommanderHorseSpeed())),
                label("guide.monster_conquest.stat.charge", num(c.getCommanderChargeSpeed())),
                label("guide.monster_conquest.stat.knockback", num(c.getCommanderKnockbackResistance()))
        ), 12));
        if (faction != null && !faction.getSoldierTypes().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (EntityType<? extends AbstractArmySoldier> s : faction.getSoldierTypes()) {
                names.add(name(s));
            }
            out.add(GuideLine.of(labelled("guide.monster_conquest.stat.summon_natural",
                    String.join("、", names)), 12));
        }
        String trait = trait(type);
        if (trait != null) {
            out.add(GuideLine.of(traitLine(trait), 12, 4));
        }
        out.add(GuideLine.separatorLine());
    }

    private static List<GuideLine> soldiersBody() {
        List<GuideLine> out = new ArrayList<>();
        for (Faction faction : FactionRegistry.all()) {
            if (faction.isPlayerFaction()) continue;
            List<EntityType<? extends AbstractArmySoldier>> types = faction.getSoldierTypes();
            if (types.isEmpty()) continue;
            out.add(GuideLine.of(heading(factionName(faction.getId())), 0, 8));
            for (EntityType<? extends AbstractArmySoldier> type : types) {
                appendSoldier(out, type);
            }
        }
        return out;
    }

    private static void appendSoldier(List<GuideLine> out, EntityType<? extends AbstractArmySoldier> type) {
        AbstractArmySoldier s = soldierSample(type);
        out.add(GuideLine.of(named(name(type)), 6, 6));
        if (s == null) {
            out.add(GuideLine.of(body(tr("guide.monster_conquest.stat.unknown")), 12));
            return;
        }
        SoldierRole role = s.getRole();
        SoldierType form = s.getDefaultSoldierType();

        out.add(GuideLine.of(flatStats(
                label("guide.monster_conquest.stat.role",
                        tr("tooltip.monster_conquest.role." + role.name().toLowerCase())),
                label("guide.monster_conquest.stat.form",
                        tr("tooltip.monster_conquest.form."
                                + (form.isCavalry() ? "cavalry" : "infantry")))
        ), 12));

        out.add(GuideLine.of(tierStats("guide.monster_conquest.stat.health",
                t -> s.getMaxHealthForTier(t)), 12));
        out.add(GuideLine.of(tierStats("guide.monster_conquest.stat.armor",
                s::getArmorForTier), 12));
        if (role.isMelee() || role.isDual()) {
            out.add(GuideLine.of(tierStats("guide.monster_conquest.stat.melee",
                    s::getMeleeDamageForTier), 12));
        }
        if (role.isArcher() || role.isDual() || role.isGunner()) {
            out.add(GuideLine.of(tierStats("guide.monster_conquest.stat.ranged",
                    t -> (double) s.getRangedDamageForTier(t)), 12));
        }

        List<MutableComponent> misc = new ArrayList<>();
        if (role.isMelee() || role.isDual()) {
            misc.add(label("guide.monster_conquest.stat.attack_interval",
                    seconds(s.getMeleeAttackIntervalTicks())));
        }
        if (role.isArcher() || role.isDual() || role.isGunner()) {
            misc.add(label("guide.monster_conquest.stat.ranged_interval",
                    seconds(s.getRangedAttackCooldownTicks())));
        }
        misc.add(label("guide.monster_conquest.stat.speed", num(s.getMovementSpeed())));
        misc.add(label("guide.monster_conquest.stat.horse", num(s.getCavalryHorseSpeed())));
        if (s.usesLanceInCavalry()) {
            misc.add(label("guide.monster_conquest.stat.charge", num(s.getCavalryChargeSpeed())));
        }
        out.add(GuideLine.of(flatStats(misc.toArray(new MutableComponent[0])), 12));

        String trait = trait(type);
        if (trait != null) {
            out.add(GuideLine.of(traitLine(trait), 12, 4));
        }
        out.add(GuideLine.separatorLine());
    }

    // ===================== 组件拼装 =====================

    private interface TierFn { double apply(EquipmentTier tier); }

    /** 每种等级一段：T0 24 ｜ T1 34 ｜ …，等级用对应颜色。 */
    private static MutableComponent tierStats(String labelKey, TierFn fn) {
        MutableComponent out = Component.empty();
        out.append(labelComponent(labelKey));
        EquipmentTier[] tiers = EquipmentTier.values();
        for (int i = 0; i < tiers.length; i++) {
            if (i > 0) out.append(sep());
            int color = TIER_COLORS[Math.min(i, TIER_COLORS.length - 1)];
            out.append(Component.literal("T" + i + " ").withStyle(s -> s.withColor(color)));
            out.append(Component.literal(num(fn.apply(tiers[i])))
                    .withStyle(s -> s.withColor(color)));
        }
        return out;
    }

    private static MutableComponent labelled(String labelKey, String value) {
        return Component.empty()
                .append(labelComponent(labelKey))
                .append(Component.literal("：" + value).withStyle(s -> s.withColor(COLOR_BODY)));
    }

    private static MutableComponent flatStats(MutableComponent... parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(sep());
            out.append(parts[i]);
        }
        return out;
    }

    private static MutableComponent label(String key, String value) {
        return Component.empty()
                .append(labelComponent(key))
                .append(Component.literal(" " + value).withStyle(s -> s.withColor(COLOR_BODY)));
    }

    private static MutableComponent labelComponent(String key) {
        return Component.translatable(key).withStyle(s -> s.withColor(COLOR_LABEL));
    }

    private static MutableComponent sep() {
        return Component.literal(" ｜ ").withStyle(s -> s.withColor(0xFFB49A78));
    }

    private static MutableComponent heading(String text) {
        return Component.literal(text).withStyle(s -> s.withColor(COLOR_HEADING));
    }

    private static MutableComponent named(String text) {
        return Component.literal(text).withStyle(s -> s.withColor(COLOR_NAME));
    }

    private static MutableComponent body(String text) {
        return Component.literal(text).withStyle(s -> s.withColor(COLOR_BODY));
    }

    private static MutableComponent traitLine(String text) {
        return Component.empty()
                .append(labelComponent("guide.monster_conquest.stat.trait"))
                .append(Component.literal("：" + text).withStyle(s -> s.withColor(COLOR_TRAIT)));
    }

    private static String factionList(java.util.Set<ResourceLocation> ids) {
        if (ids.isEmpty()) return tr("guide.monster_conquest.stat.none");
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : ids) names.add(factionName(id));
        return String.join("、", names);
    }

    private static String factionListNames(java.util.Collection<String> ids) {
        if (ids.isEmpty()) return tr("guide.monster_conquest.stat.none");
        List<String> names = new ArrayList<>();
        for (String raw : ids) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            names.add(id != null ? factionName(id) : raw);
        }
        return String.join("、", names);
    }

    // ===================== 取样 / 格式化 =====================

    private static final Map<EntityType<?>, Object> SAMPLES = new HashMap<>();

    @Nullable
    private static AbstractArmySoldier soldierSample(EntityType<? extends AbstractArmySoldier> type) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            Object cached = SAMPLES.get(type);
            if (cached instanceof AbstractArmySoldier s) return s;
            AbstractArmySoldier created = type.create(level);
            if (created != null) SAMPLES.put(type, created);
            return created;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static AbstractArmyCommander commanderSample(EntityType<? extends AbstractArmyCommander> type) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            Object cached = SAMPLES.get(type);
            if (cached instanceof AbstractArmyCommander c) return c;
            AbstractArmyCommander created = type.create(level);
            if (created != null) SAMPLES.put(type, created);
            return created;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 去掉多余小数：12.0 → 12，12.5 → 12.5 */
    private static String num(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0e-6) return String.valueOf((long) Math.rint(value));
        return String.valueOf(Math.rint(value * 100.0) / 100.0);
    }

    private static String seconds(int ticks) {
        return num(ticks / 20.0) + tr("guide.monster_conquest.stat.second_suffix");
    }

    private static String name(EntityType<?> type) {
        return type.getDescription().getString();
    }

    private static String factionName(ResourceLocation id) {
        try {
            return FactionDisplay.displayName(id).getString();
        } catch (Throwable t) {
            return id.toString();
        }
    }

    @Nullable
    private static String trait(EntityType<?> type) {
        String key = "guide.monster_conquest.trait." + EntityType.getKey(type).getPath();
        Component c = Component.translatable(key);
        String raw = c.getString();
        return raw.equals(key) ? null : raw;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
