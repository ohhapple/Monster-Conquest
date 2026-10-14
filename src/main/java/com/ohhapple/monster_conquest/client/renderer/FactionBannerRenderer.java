package com.ohhapple.monster_conquest.client.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.army.FactionBanner;
import com.ohhapple.monster_conquest.army.FactionColors;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 阵营长杆旗帜渲染器：读取 {@code models/banner/faction_banner.json} 的形状，
 * 杆 / 旗顶 / 装饰按原样画，旗面按阵营配色并叠加波浪飘动与阵营十字。
 *
 * 飘动：旗面沿离开旗杆的方向切成竖条，每条偏移 = 振幅（越远越大）
 * × sin(时间相位 - 位置相位)，波形随时间向自由边推进。
 */
public class FactionBannerRenderer extends BlockEntityWithoutLevelRenderer {

    /** 形状文件（只被本渲染器读取，不参与模型烘焙）。 */
    private static final ResourceLocation SHAPE_FILE =
            ResourceLocation.fromNamespaceAndPath("monster_conquest", "models/banner/faction_banner.json");
    /** 旗面那一块用来标记自己的贴图引用名：#cloth。 */
    private static final String CLOTH_REF = "cloth";

    // ===================== 飘动参数（写死，不做成配置） =====================

    /** 一次完整摆动的时间（毫秒）。 */
    private static final long WAVE_PERIOD_MS = 2400L;
    /** 自由边最大摆幅，相对旗面宽度。 */
    private static final float WAVE_AMPLITUDE_RATIO = 0.28f;
    private static final float WAVE_AMPLITUDE_MIN = 0.02f;
    private static final float WAVE_AMPLITUDE_MAX = 0.22f;
    /** 旗面切成的竖条数量范围。 */
    private static final int SEGMENTS_MIN = 10;
    private static final int SEGMENTS_MAX = 26;
    /** 十字大小占旗面短边的比例。 */
    private static final float MARK_SIZE_RATIO = 0.65f;
    /** 十字竖直方向再拉长一点（"长"）。 */
    private static final float MARK_HEIGHT_BONUS = 1.15f;
    /** 十字线条粗细占十字宽度的比例（至少 1px，也就是"宽"）。 */
    private static final float MARK_BAR_RATIO = 0.26f;

    /** 形状文件里没写 display 时使用的默认姿势。 */
    private static final Map<ItemDisplayContext, ItemTransform> DEFAULT_DISPLAY =
            new EnumMap<>(ItemDisplayContext.class);

    static {
        putDisplay(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                new float[]{0f, -90f, 55f}, new float[]{0f, 4f, 0.5f}, new float[]{0.85f, 0.85f, 0.85f});
        putDisplay(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                new float[]{0f, 90f, -55f}, new float[]{0f, 4f, 0.5f}, new float[]{0.85f, 0.85f, 0.85f});
        putDisplay(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                new float[]{0f, -90f, 25f}, new float[]{1.13f, 3.2f, 1.13f}, new float[]{0.68f, 0.68f, 0.68f});
        putDisplay(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                new float[]{0f, 90f, -25f}, new float[]{1.13f, 3.2f, 1.13f}, new float[]{0.68f, 0.68f, 0.68f});
        putDisplay(ItemDisplayContext.GUI,
                new float[]{30f, 20f, 0f}, new float[]{-3f, -1f, 0f}, new float[]{0.75f, 0.75f, 0.75f});
        putDisplay(ItemDisplayContext.GROUND,
                new float[]{0f, 0f, 0f}, new float[]{-4f, 2f, 0f}, new float[]{0.5f, 0.5f, 0.5f});
        putDisplay(ItemDisplayContext.FIXED,
                new float[]{0f, 180f, 0f}, new float[]{-4f, 0f, 0f}, new float[]{0.5f, 0.5f, 0.5f});
        putDisplay(ItemDisplayContext.HEAD,
                new float[]{0f, 180f, 0f}, new float[]{0f, 13f, 7f}, new float[]{1f, 1f, 1f});
    }

    private static void putDisplay(ItemDisplayContext context, float[] rotation, float[] translation, float[] scale) {
        DEFAULT_DISPLAY.put(context, new ItemTransform(new Vector3f(rotation),
                new Vector3f(translation).mul(1f / 16f), new Vector3f(scale)));
    }

    /** 每个方向面上四个顶点取 min(0)/max(1) 的顺序，和原版 FaceInfo 一致。 */
    private static final int[][][] FACE_CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},   // down
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},   // up
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},   // north
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},   // south
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},   // west
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},   // east
    };

    /** 解析好的形状；资源重载后清空再重新读。 */
    @Nullable
    private static Shape shape;

    public FactionBannerRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    /** 资源重载时清掉形状缓存，下次渲染重新读取（见 ModClientEvents）。 */
    public static ResourceManagerReloadListener reloadListener() {
        return manager -> shape = null;
    }

    /**
     * 应用显示姿势：优先用形状文件里的 display，
     * 没写的场景用代码里的默认值。
     * 摆放顺序：先移到方块中心 → 变换 → 再移回去。
     */
    private static void applyDisplay(Shape shape, ItemDisplayContext context, PoseStack pose) {
        ItemTransform transform = shape.display().get(context);
        if (transform == null) transform = DEFAULT_DISPLAY.get(context);
        if (transform == null) return;
        boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.translate(0.5f, 0.5f, 0.5f);
        transform.apply(leftHand, pose);
        pose.translate(-0.5f, -0.5f, -0.5f);
    }

    // ===================== 渲染 =====================

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        Shape current = shape;
        if (current == null) {
            current = readShape(mc);
            if (current == null) {
                current = fallbackShape();   // 形状文件读不出来时兜底，至少不是隐形物品
            }
            shape = current;
        }
        if (current == null) return;

        applyDisplay(current, context, pose);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        // 1) 除旗面以外的方块
        for (Element element : current.elements()) {
            drawElement(element, pose, vc, light, overlay, mc);
        }

        // 2) 旗面：会飘 + 阵营配色 + 中央十字
        Cloth cloth = current.cloth();
        if (cloth != null) {
            TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(cloth.texture());
            if (sprite != null) {
                ResourceLocation faction = FactionBanner.factionOf(stack);
                if (faction == null || FactionIds.isBigPlayer(faction)) {
                    // 没写阵营的旗帜（创造栏拿的 / 合成出来的）按玩家自己的阵营上色，
                    // 这样和玩家士兵手里那面旗的颜色一致
                    LocalPlayer player = mc.player;
                    if (player != null) {
                        faction = PlayerFactionRegistry.factionIdOf(player);
                    }
                }
                int corner = FactionColors.bannerCornerColor(faction);
                int cross = FactionColors.bannerCrossColor(faction);
                renderCloth(cloth, sprite, pose, vc, light, overlay, corner, cross, System.currentTimeMillis());
            }
        }
    }

    /** 画一个模型方块（六个面）。 */
    private static void drawElement(Element element, PoseStack pose, VertexConsumer vc,
                                    int light, int overlay, Minecraft mc) {
        PoseStack.Pose p = pose.last();
        for (Map.Entry<Direction, Face> entry : element.faces().entrySet()) {
            Direction direction = entry.getKey();
            Face face = entry.getValue();
            TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(face.texture());
            if (sprite == null) continue;

            int[][] corners = FACE_CORNERS[direction.get3DDataValue()];
            float[] uv = face.uv();
            float[] normal = rotateDir(direction.getNormal().getX(), direction.getNormal().getY(),
                    direction.getNormal().getZ(), element.rotOrigin(), element.rotAxis(), element.rotAngle());
            for (int i = 0; i < 4; i++) {
                float[] point = element.corner(corners[i]);
                float u = sprite.getU((i == 0 || i == 3 ? uv[0] : uv[2]) / 16f);
                float v = sprite.getV((i < 2 ? uv[1] : uv[3]) / 16f);
                vc.addVertex(p.pose(), point[0], point[1], point[2])
                        .setColor(1f, 1f, 1f, 1f)
                        .setUv(u, v)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(p, normal[0], normal[1], normal[2]);
            }
        }
    }

    // ===================== 旗面（会飘） =====================

    private static void renderCloth(Cloth c, TextureAtlasSprite sprite, PoseStack pose, VertexConsumer vc,
                                    int light, int overlay, int corner, int cross, long timeMs) {
        float cr = ((corner >> 16) & 0xFF) / 255f;
        float cg = ((corner >> 8) & 0xFF) / 255f;
        float cb = (corner & 0xFF) / 255f;
        float sr = ((cross >> 16) & 0xFF) / 255f;
        float sg = ((cross >> 8) & 0xFF) / 255f;
        float sb = (cross & 0xFF) / 255f;

        PoseStack.Pose p = pose.last();
        float spanLength = c.spanMax() - c.spanMin();
        float halfThick = c.thickSize() / 2f;
        float amplitude = clamp(spanLength * WAVE_AMPLITUDE_RATIO, WAVE_AMPLITUDE_MIN, WAVE_AMPLITUDE_MAX);
        int segments = Math.round(clamp(spanLength * 16f * 1.5f, SEGMENTS_MIN, SEGMENTS_MAX));

        // 阵营十字：大小取旗面短边的比例，竖直方向再拉长一点，线条加粗一点
        float clothHeight = c.heightMax() - c.heightMin();
        float markWidth = Math.max(2f / 16f,
                Math.round(Math.min(spanLength, clothHeight) * MARK_SIZE_RATIO * 16f) / 16f);
        float markHeight = Math.max(markWidth, Math.round(markWidth * MARK_HEIGHT_BONUS * 16f) / 16f);
        float bar = Math.max(1f / 16f, Math.round(markWidth * MARK_BAR_RATIO * 16f) / 16f);
        float spanCenter = (c.spanMin() + c.spanMax()) / 2f;
        float heightCenter = (c.heightMin() + c.heightMax()) / 2f;
        float markSpanMin = spanCenter - markWidth / 2f;
        float markSpanMax = spanCenter + markWidth / 2f;
        float markHeightMin = heightCenter - markHeight / 2f;
        float markHeightMax = heightCenter + markHeight / 2f;
        float barSpanMin = spanCenter - bar / 2f;
        float barSpanMax = spanCenter + bar / 2f;
        float barHeightMin = heightCenter - bar / 2f;
        float barHeightMax = heightCenter + bar / 2f;

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        for (int i = 0; i < segments; i++) {
            float s0 = c.spanMin() + spanLength * i / segments;
            float s1 = c.spanMin() + spanLength * (i + 1) / segments;
            float w0 = wave(c.waveT(s0), timeMs, amplitude);
            float w1 = wave(c.waveT(s1), timeMs, amplitude);

            // 一条布片按"十字的边界"切小块，十字的颜色直接画在布面上（不再叠一层，两面都严丝合缝）
            for (float[] spanPart : split(s0, s1, markSpanMin, markSpanMax, barSpanMin, barSpanMax)) {
                float px0 = spanPart[0];
                float px1 = spanPart[1];
                float pw0 = c.waveAt(px0, timeMs, amplitude);
                float pw1 = c.waveAt(px1, timeMs, amplitude);
                float spanMid = (px0 + px1) / 2f;
                for (float[] heightPart : split(c.heightMin(), c.heightMax(),
                        markHeightMin, markHeightMax, barHeightMin, barHeightMax)) {
                    float heightMid = (heightPart[0] + heightPart[1]) / 2f;
                    boolean inVerticalBar = spanMid >= barSpanMin && spanMid <= barSpanMax
                            && heightMid >= markHeightMin && heightMid <= markHeightMax;
                    boolean inHorizontalBar = heightMid >= barHeightMin && heightMid <= barHeightMax
                            && spanMid >= markSpanMin && spanMid <= markSpanMax;
                    float r = inVerticalBar || inHorizontalBar ? sr : cr;
                    float g = inVerticalBar || inHorizontalBar ? sg : cg;
                    float b = inVerticalBar || inHorizontalBar ? sb : cb;
                    cellQuad(c, p, vc, light, overlay, px0, px1, heightPart[0], heightPart[1],
                            pw0, pw1, -halfThick, -1f, u0, u1, v0, v1, r, g, b);
                    cellQuad(c, p, vc, light, overlay, px0, px1, heightPart[0], heightPart[1],
                            pw0, pw1, halfThick, 1f, u0, u1, v0, v1, r, g, b);
                }
            }
        }
    }

    /**
     * 布面上的一小块（side = -1 正面，+1 背面）。
     * UV 按它在整块布面里的相对位置取，所以切开后贴图不会错位。
     */
    private static void cellQuad(Cloth c, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay,
                                 float s0, float s1, float h0, float h1, float w0, float w1, float half,
                                 float side, float u0, float u1, float v0, float v1,
                                 float r, float g, float b) {
        float spanLength = c.spanMax() - c.spanMin();
        float clothHeight = c.heightMax() - c.heightMin();
        float ta0 = c.thickCenter() + w0 + half * side;
        float ta1 = c.thickCenter() + w1 + half * side;

        float su0 = u0 + (u1 - u0) * ((s0 - c.spanMin()) / spanLength);
        float su1 = u0 + (u1 - u0) * ((s1 - c.spanMin()) / spanLength);
        // 贴图 v 轴向下：高度大的一侧对应 v0
        float sv0 = v0 + (v1 - v0) * ((c.heightMax() - h1) / clothHeight);
        float sv1 = v0 + (v1 - v0) * ((c.heightMax() - h0) / clothHeight);

        float ua = side < 0 ? su0 : su1;
        float ub = side < 0 ? su1 : su0;
        float[] n = c.rotateDir(side);
        vertex(vc, pose, light, overlay, c.point(s0, h1, ta0), ua, sv0, r, g, b, n);
        vertex(vc, pose, light, overlay, c.point(s1, h1, ta1), ub, sv0, r, g, b, n);
        vertex(vc, pose, light, overlay, c.point(s1, h0, ta1), ub, sv1, r, g, b, n);
        vertex(vc, pose, light, overlay, c.point(s0, h0, ta0), ua, sv1, r, g, b, n);
    }

    /**
     * 把区间 [a, b] 按给定的边界切开（边界落在区间外就忽略）。
     * 用来把布片切成"十字内 / 十字外"的小块。
     */
    private static List<float[]> split(float a, float b, float... boundaries) {
        List<Float> cuts = new ArrayList<>();
        cuts.add(a);
        cuts.add(b);
        for (float boundary : boundaries) {
            if (boundary > a && boundary < b) cuts.add(boundary);
        }
        cuts.sort(Float::compare);
        List<float[]> parts = new ArrayList<>(cuts.size() - 1);
        for (int i = 0; i + 1 < cuts.size(); i++) {
            if (cuts.get(i + 1) - cuts.get(i) > 1e-6f) {
                parts.add(new float[]{cuts.get(i), cuts.get(i + 1)});
            }
        }
        return parts;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, int overlay,
                               float[] p, float u, float v, float r, float g, float b, float[] n) {
        vc.addVertex(pose.pose(), p[0], p[1], p[2])
                .setColor(r, g, b, 1f)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, n[0], n[1], n[2]);
    }

    /** 摆动偏移：t = 0 在固定边，1 在自由边；越靠外摆得越大，波形随时间推进。 */
    private static float wave(float t, long timeMs, float amplitude) {
        double ramp = Math.sqrt(Math.max(0f, Math.min(1f, t)));
        double travel = (timeMs % WAVE_PERIOD_MS) / (double) WAVE_PERIOD_MS;
        return (float) (amplitude * ramp * Math.sin(Math.PI * (t - travel * 2.0)));
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    // ===================== 读取形状文件 =====================

    @Nullable
    private static Shape readShape(Minecraft mc) {
        Optional<Resource> resource = mc.getResourceManager().getResource(SHAPE_FILE);
        if (resource.isEmpty()) return null;

        List<Element> elements = new ArrayList<>();
        Map<ItemDisplayContext, ItemTransform> display = new EnumMap<>(ItemDisplayContext.class);
        JsonObject clothElement = null;
        ResourceLocation clothTexture = null;
        float[] clothFrom = null;
        float[] clothTo = null;
        float[] clothRotOrigin = null;
        int clothRotAxis = 1;
        float clothRotAngle = 0f;
        float[] poleSum = new float[3];
        int poleCount = 0;

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : new JsonObject();
            JsonArray jsonElements = root.has("elements") ? root.getAsJsonArray("elements") : null;
            if (jsonElements == null) return null;
            display.putAll(readDisplay(root));

            for (JsonElement jsonElement : jsonElements) {
                JsonObject obj = jsonElement.getAsJsonObject();
                float[] from = readVector(obj.get("from"), 3);
                float[] to = readVector(obj.get("to"), 3);
                if (from == null || to == null) continue;

                float[] rotOrigin = null;
                int rotAxis = 1;
                float rotAngle = 0f;
                if (obj.has("rotation")) {
                    JsonObject rot = obj.getAsJsonObject("rotation");
                    rotOrigin = readVector(rot.get("origin"), 3);
                    rotAxis = switch (rot.has("axis") ? rot.get("axis").getAsString() : "y") {
                        case "x" -> 0;
                        case "z" -> 2;
                        default -> 1;
                    };
                    rotAngle = rot.has("angle") ? rot.get("angle").getAsFloat() : 0f;
                }

                if (usesClothTexture(obj)) {
                    clothElement = obj;
                    clothTexture = resolveTexture(textures, CLOTH_REF);
                    clothFrom = from;
                    clothTo = to;
                    clothRotOrigin = rotOrigin;
                    clothRotAxis = rotAxis;
                    clothRotAngle = rotAngle;
                    continue;
                }

                Map<Direction, Face> faces = readFaces(obj, textures, from, to);
                elements.add(new Element(from, to, rotOrigin, rotAxis, rotAngle, faces));
                for (int axis = 0; axis < 3; axis++) {
                    poleSum[axis] += (from[axis] + to[axis]) / 2f / 16f;
                }
                poleCount++;
            }
        } catch (Exception exception) {
            return null;
        }

        Cloth cloth = null;
        if (clothElement != null && clothTexture != null && clothFrom != null && clothTo != null) {
            float[] poleCenter = poleCount == 0
                    ? new float[]{0.5f, 0.5f, 0.5f}
                    : new float[]{poleSum[0] / poleCount, poleSum[1] / poleCount, poleSum[2] / poleCount};
            cloth = buildCloth(clothFrom, clothTo, clothRotOrigin, clothRotAxis, clothRotAngle,
                    clothTexture, poleCenter);
        }
        return new Shape(elements, cloth, display);
    }

    /** 读形状文件里的 display。 */
    private static Map<ItemDisplayContext, ItemTransform> readDisplay(JsonObject root) {
        Map<ItemDisplayContext, ItemTransform> display = new EnumMap<>(ItemDisplayContext.class);
        if (!root.has("display")) return display;
        JsonObject jsonDisplay = root.getAsJsonObject("display");
        for (String key : jsonDisplay.keySet()) {
            if (!jsonDisplay.get(key).isJsonObject()) continue;
            ItemDisplayContext context = contextByName(key);
            if (context == null) continue;
            JsonObject jsonTransform = jsonDisplay.getAsJsonObject(key);
            Vector3f rotation = readVector3(jsonTransform.get("rotation"), 0f);
            Vector3f translation = readVector3(jsonTransform.get("translation"), 0f).mul(1f / 16f);
            Vector3f scale = readVector3(jsonTransform.get("scale"), 1f);
            display.put(context, new ItemTransform(rotation, translation, scale));
        }
        return display;
    }

    @Nullable
    private static ItemDisplayContext contextByName(String name) {
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (context.getSerializedName().equals(name)) return context;
        }
        return null;
    }

    private static Vector3f readVector3(@Nullable JsonElement element, float fallback) {
        Vector3f vector = new Vector3f(fallback, fallback, fallback);
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() >= 3) {
                vector.set(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
            }
        }
        return vector;
    }

    /** 把旗面方块的盒子换算成"会飘"的那套参数。 */
    private static Cloth buildCloth(float[] from, float[] to, @Nullable float[] rotOrigin, int rotAxis,
                                    float rotAngle, ResourceLocation texture, float[] poleCenter) {
        float[] min = new float[]{from[0] / 16f, from[1] / 16f, from[2] / 16f};
        float[] max = new float[]{to[0] / 16f, to[1] / 16f, to[2] / 16f};
        for (int axis = 0; axis < 3; axis++) {
            if (min[axis] > max[axis]) {
                float tmp = min[axis];
                min[axis] = max[axis];
                max[axis] = tmp;
            }
        }

        // 厚度 = 最薄的一向；剩下两向里离杆更远的那一向是"布面伸出去"的方向
        int thickAxis = argMinExtent(min, max);
        int spanAxis = -1;
        float bestDistance = -1f;
        for (int axis = 0; axis < 3; axis++) {
            if (axis == thickAxis) continue;
            float distance = Math.max(Math.abs(min[axis] - poleCenter[axis]), Math.abs(max[axis] - poleCenter[axis]));
            if (distance > bestDistance) {
                bestDistance = distance;
                spanAxis = axis;
            }
        }
        int heightAxis = 3 - thickAxis - spanAxis;
        boolean fixedAtMin = Math.abs(min[spanAxis] - poleCenter[spanAxis])
                <= Math.abs(max[spanAxis] - poleCenter[spanAxis]);

        return new Cloth(spanAxis, heightAxis, thickAxis,
                min[spanAxis], max[spanAxis], fixedAtMin,
                min[heightAxis], max[heightAxis],
                min[thickAxis], max[thickAxis],
                rotOrigin == null ? new float[]{0f, 0f, 0f} : rotOrigin,
                rotAxis, rotAngle, texture);
    }

    /** 形状文件缺失/写坏时的兜底形状：一根木杆。 */
    private static Shape fallbackShape() {
        float[] from = {7f, 0f, 7.5f};
        float[] to = {9f, 16f, 8.5f};
        ResourceLocation wood = ResourceLocation.withDefaultNamespace("block/stripped_oak_log");
        Map<Direction, Face> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            faces.put(direction, new Face(wood, autoUv(direction, from, to)));
        }
        return new Shape(List.of(new Element(from, to, null, 1, 0f, faces)), null, Map.of());
    }

    /** 读一个方块的六个面（贴图 + UV，没写 uv 就按原版规则自动铺）。 */
    private static Map<Direction, Face> readFaces(JsonObject element, JsonObject textures, float[] from, float[] to) {
        Map<Direction, Face> faces = new EnumMap<>(Direction.class);
        if (!element.has("faces")) return faces;
        JsonObject jsonFaces = element.getAsJsonObject("faces");
        for (Direction direction : Direction.values()) {
            String key = direction.getName();
            if (!jsonFaces.has(key)) continue;
            JsonObject jsonFace = jsonFaces.getAsJsonObject(key);
            ResourceLocation texture = faceTexture(jsonFace, textures);
            if (texture == null) continue;
            float[] uv = jsonFace.has("uv") ? readVector(jsonFace.get("uv"), 4) : autoUv(direction, from, to);
            if (uv == null) continue;
            faces.put(direction, new Face(texture, uv));
        }
        return faces;
    }

    @Nullable
    private static ResourceLocation faceTexture(JsonObject face, JsonObject textures) {
        if (!face.has("texture")) return null;
        String value = face.get("texture").getAsString();
        if (value.startsWith("#")) {
            return resolveTexture(textures, value.substring(1));
        }
        return ResourceLocation.tryParse(value);
    }

    @Nullable
    private static ResourceLocation resolveTexture(JsonObject textures, String ref) {
        String value = textures.has(ref) ? textures.get(ref).getAsString() : null;
        if (value == null) return null;
        if (value.startsWith("#")) {
            String nested = value.substring(1);
            value = textures.has(nested) ? textures.get(nested).getAsString() : null;
            if (value == null || value.startsWith("#")) return null;
        }
        return ResourceLocation.tryParse(value);
    }

    /** 原版的自动 UV 规则（和 BlockElement#uvsByFace 一致，单位 0..16）。 */
    private static float[] autoUv(Direction direction, float[] from, float[] to) {
        float x0 = Math.min(from[0], to[0]);
        float y0 = Math.min(from[1], to[1]);
        float z0 = Math.min(from[2], to[2]);
        float x1 = Math.max(from[0], to[0]);
        float y1 = Math.max(from[1], to[1]);
        float z1 = Math.max(from[2], to[2]);
        return switch (direction) {
            case DOWN -> new float[]{x0, 16f - z1, x1, 16f - z0};
            case UP -> new float[]{x0, z0, x1, z1};
            case NORTH -> new float[]{16f - x1, 16f - y1, 16f - x0, 16f - y0};
            case SOUTH -> new float[]{x0, 16f - y1, x1, 16f - y0};
            case WEST -> new float[]{z0, 16f - y1, z1, 16f - y0};
            case EAST -> new float[]{16f - z1, 16f - y1, 16f - z0, 16f - y0};
        };
    }

    /** 这一块是否整块都贴 #cloth。 */
    private static boolean usesClothTexture(JsonObject element) {
        if (!element.has("faces")) return false;
        JsonObject faces = element.getAsJsonObject("faces");
        if (faces.size() == 0) return false;
        for (String key : faces.keySet()) {
            JsonObject face = faces.getAsJsonObject(key);
            String texture = face.has("texture") ? face.get("texture").getAsString() : "";
            if (!texture.equals("#" + CLOTH_REF)) return false;
        }
        return true;
    }

    private static int argMinExtent(float[] min, float[] max) {
        int axis = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            float extent = max[i] - min[i];
            if (extent < best) {
                best = extent;
                axis = i;
            }
        }
        return axis;
    }

    @Nullable
    private static float[] readVector(@Nullable JsonElement element, int expectedSize) {
        if (element == null || !element.isJsonArray()) return null;
        JsonArray array = element.getAsJsonArray();
        if (array.size() < expectedSize) return null;
        float[] result = new float[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }

    // ===================== 数据 =====================

    private record Shape(List<Element> elements, @Nullable Cloth cloth,
                         Map<ItemDisplayContext, ItemTransform> display) {}

    private record Face(ResourceLocation texture, float[] uv) {}

    /**
     * 一个模型方块：0..16 的 from/to 换算成 0..1，外加可选的元素旋转。
     */
    private record Element(float[] from, float[] to, @Nullable float[] rotOrigin, int rotAxis, float rotAngle,
                           Map<Direction, Face> faces) {

        /** 取某个顶点（corners 里每项三个 0/1，分别代表取该轴的 min/max）。 */
        float[] corner(int[] corner) {
            float[] point = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                float value = corner[axis] == 0 ? from[axis] : to[axis];
                point[axis] = value / 16f;
            }
            return rotate(point, rotOrigin == null ? new float[]{0f, 0f, 0f} : rotOrigin, rotAxis, rotAngle);
        }
    }

    /**
     * 旗面：三个方向 + 范围 + 厚度 + 贴图 + 可选旋转。
     *
     * @param spanAxis   布面向外伸出去的方向（0=x, 1=y, 2=z）
     * @param heightAxis 布面上下方向
     * @param thickAxis  布面厚度方向（摆动发生在这个方向上）
     * @param fixedAtMin 固定边是否在 span 的最小一侧
     */
    private record Cloth(int spanAxis, int heightAxis, int thickAxis,
                         float spanMin, float spanMax, boolean fixedAtMin,
                         float heightMin, float heightMax,
                         float thickMin, float thickMax,
                         float[] rotOrigin, int rotAxis, float rotAngle,
                         ResourceLocation texture) {

        float thickCenter() {
            return (thickMin + thickMax) / 2f;
        }

        float thickSize() {
            return thickMax - thickMin;
        }

        /** 位置参数 t：0 = 固定边，1 = 自由边。 */
        float waveT(float span) {
            float fixed = fixedAtMin ? spanMin : spanMax;
            float free = fixedAtMin ? spanMax : spanMin;
            float length = free - fixed;
            return length == 0f ? 0f : (span - fixed) / length;
        }

        float waveAt(float span, long timeMs, float amplitude) {
            return wave(waveT(span), timeMs, amplitude);
        }

        /** 把 (span, height, thick) 的局部坐标变成模型坐标。 */
        float[] point(float span, float height, float thick) {
            float[] p = new float[3];
            p[spanAxis] = span;
            p[heightAxis] = height;
            p[thickAxis] = thick;
            return rotate(p, rotOrigin, rotAxis, rotAngle);
        }

        /** 厚度方向的法线（+1 = 背面，-1 = 正面），跟着元素旋转一起转。 */
        float[] rotateDir(float thicknessComponent) {
            float[] d = new float[3];
            d[thickAxis] = thicknessComponent;
            return rotate(d, new float[]{0f, 0f, 0f}, rotAxis, rotAngle);
        }
    }

    /** 绕坐标轴旋转一个点（绕 rotation.origin）。 */
    private static float[] rotate(float[] p, float[] origin, int axis, float degrees) {
        if (degrees == 0f) return p;
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float x = p[0] - origin[0];
        float y = p[1] - origin[1];
        float z = p[2] - origin[2];
        float nx;
        float ny;
        float nz;
        switch (axis) {
            case 0 -> {
                nx = x;
                ny = y * cos - z * sin;
                nz = y * sin + z * cos;
            }
            case 2 -> {
                nx = x * cos - y * sin;
                ny = x * sin + y * cos;
                nz = z;
            }
            default -> {
                nx = x * cos + z * sin;
                ny = y;
                nz = -x * sin + z * cos;
            }
        }
        return new float[]{nx + origin[0], ny + origin[1], nz + origin[2]};
    }

    private static float[] rotateDir(float x, float y, float z, float[] origin, int axis, float degrees) {
        float[] rotated = rotate(new float[]{x, y, z}, new float[]{0f, 0f, 0f}, axis, degrees);
        return rotated;
    }
}
