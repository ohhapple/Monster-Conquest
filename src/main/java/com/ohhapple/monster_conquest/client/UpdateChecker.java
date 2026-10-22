package com.ohhapple.monster_conquest.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.util.ModVersion;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端通过仓库内固定的更新清单检查新版本。
 *   清单直接声明模组版本和下载页，不依赖 Release 标签、标题或附件名称。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class UpdateChecker {

    private static final String RELEASE_URL =
            "https://github.com/ohhapple/Monster-Conquest/releases";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean();

    private UpdateChecker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !GeneralConfig.updateCheckEnabled
                || !CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        String currentVersion = currentVersion();
        BuildMetadata build;
        try {
            build = buildMetadata();
        } catch (IOException exception) {
            Monster_conquest.LOGGER.debug(
                    "Monster Conquest update check disabled: build metadata is unavailable",
                    exception);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                BranchInfo info = loadBranchInfo(build.branch(), currentVersion);
                minecraft.execute(() -> finishCheck(
                        minecraft, currentVersion, build, info));
            } catch (Exception exception) {
                Monster_conquest.LOGGER.debug(
                        "Monster Conquest update check failed", exception);
            }
        });
    }

    private static void finishCheck(Minecraft minecraft, String currentVersion,
                                    BuildMetadata build, BranchInfo info) {
        ModVersion current = ModVersion.parse(currentVersion);
        if (current == null) {
            Monster_conquest.LOGGER.warn(
                    "Cannot check for updates: installed mod version '{}' is invalid",
                    currentVersion);
            return;
        }

        if (!build.loader().equals(info.loader())
                || !SharedConstants.getCurrentVersion().getName()
                .equals(info.minecraftVersion())) {
            return;
        }
        if (!info.version().isNewerThan(current) || minecraft.player == null) return;

        String downloadUrl = RELEASE_URL;
        Component download = Component.literal(downloadUrl).withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.OPEN_URL, downloadUrl)));
        minecraft.player.displayClientMessage(
                Component.translatable(
                                "msg.monster_conquest.update_available",
                                info.version().display(), current.display())
                        .withStyle(ChatFormatting.YELLOW)
                        .append(download),
                false);
    }

    private static BranchInfo loadBranchInfo(String branch, String currentVersion)
            throws Exception {
        String encodedBranch = URLEncoder.encode(branch, StandardCharsets.UTF_8);
        URI manifestApi = URI.create(
                "https://api.github.com/repos/ohhapple/Monster-Conquest/"
                        + "contents/gradle.properties?ref=" + encodedBranch);
        URI manifestRaw = URI.create(
                "https://raw.githubusercontent.com/ohhapple/"
                        + "Monster-Conquest/" + encodedBranch + "/gradle.properties");
        try {
            JsonObject response = JsonParser.parseString(
                    fetch(manifestApi, "application/vnd.github+json",
                            currentVersion)).getAsJsonObject();
            if (!response.has("content")) {
                throw new IOException("Branch metadata API response has no content");
            }
            String content = response.get("content").getAsString()
                    .replaceAll("\\s+", "");
            String manifest = new String(
                    Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
            return parseBranchInfo(manifest);
        } catch (Exception apiFailure) {
            try {
                return parseBranchInfo(
                        fetch(manifestRaw, "application/json", currentVersion));
            } catch (Exception rawFailure) {
                rawFailure.addSuppressed(apiFailure);
                throw rawFailure;
            }
        }
    }

    private static String fetch(URI uri, String accept, String currentVersion)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", accept)
                .header("User-Agent", Monster_conquest.MODID + "/" + currentVersion)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Branch metadata returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static BranchInfo parseBranchInfo(String raw) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(raw));
        ModVersion version = ModVersion.parse(
                properties.getProperty("mod_version", "").trim());
        if (version == null) {
            throw new IOException("Branch mod_version is invalid");
        }
        String minecraftVersion = properties.getProperty(
                "minecraft_version", "").trim();
        String loader = properties.getProperty("mod_loader", "").trim();
        if (minecraftVersion.isBlank() || loader.isBlank()) {
            throw new IOException(
                    "Branch metadata must declare minecraft_version and mod_loader");
        }
        return new BranchInfo(version, minecraftVersion, loader);
    }

    private static String currentVersion() {
        return ModList.get().getModContainerById(Monster_conquest.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("0.0.0");
    }

    private static BuildMetadata buildMetadata() throws IOException {
        try (InputStream stream = UpdateChecker.class.getResourceAsStream(
                "/monster_conquest_build.properties")) {
            if (stream == null) {
                throw new IOException("Missing monster_conquest_build.properties");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String branch = properties.getProperty("gitBranch", "").trim();
            String loader = properties.getProperty("modLoader", "").trim();
            if (branch.isEmpty() || "HEAD".equals(branch) || "unknown".equals(branch)
                    || loader.isEmpty()) {
                throw new IOException("Build branch or loader metadata is missing or invalid");
            }
            return new BuildMetadata(branch, loader);
        }
    }

    static boolean isNewer(String candidate, String current) {
        ModVersion candidateVersion = ModVersion.parse(candidate);
        ModVersion currentVersion = ModVersion.parse(current);
        return candidateVersion != null && currentVersion != null
                && candidateVersion.isNewerThan(currentVersion);
    }

    private record BranchInfo(ModVersion version, String minecraftVersion,
                              String loader) {}

    private record BuildMetadata(String branch, String loader) {}
}
