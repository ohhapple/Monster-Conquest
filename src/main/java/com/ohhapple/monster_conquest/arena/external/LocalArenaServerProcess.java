package com.ohhapple.monster_conquest.arena.external;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 主服管理的唯一本地虚空竞技子服进程。 */
public final class LocalArenaServerProcess {

    private static final String SERVER_DIRECTORY = "monster_conquest/arena_server";

    @Nullable private static Process process;
    @Nullable private static Path serverDirectory;
    @Nullable private static Path bridgeDirectory;
    private static boolean stopRequested;

    private LocalArenaServerProcess() {
    }

    public static synchronized boolean ensureStarted(MinecraftServer mainServer) {
        if (!ArenaConfig.arenaServerEnabled) {
            requestStop();
            return false;
        }
        if (process != null && !process.isAlive()) stopRequested = false;
        if (stopRequested) return false;
        if (process != null && process.isAlive()) return true;
        stopRequested = false;

        Path mainDirectory = mainServer.getServerDirectory().toAbsolutePath().normalize();
        serverDirectory = mainDirectory.resolve(SERVER_DIRECTORY);
        bridgeDirectory = mainDirectory.resolve("monster_conquest/arena_bridge");
        try {
            prepareRuntime(mainServer, mainDirectory, serverDirectory, bridgeDirectory);
            List<String> command = buildLaunchCommand(mainDirectory, serverDirectory, bridgeDirectory);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(serverDirectory.toFile());
            builder.redirectErrorStream(true);
            Files.createDirectories(serverDirectory.resolve("logs"));
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(
                    serverDirectory.resolve("logs/arena-subprocess.log").toFile()));
            process = builder.start();
            Monster_conquest.LOGGER.info(
                    "[ARENA-SERVER] Started local child pid={} port={}",
                    process.pid(), ArenaConfig.arenaServerPort);
            return true;
        } catch (Exception exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to start local child server", exception);
            process = null;
            return false;
        }
    }

    public static synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public static synchronized void stop() {
        Process current = process;
        process = null;
        stopRequested = false;
        if (current == null || !current.isAlive()) return;
        try {
            current.getOutputStream().write("stop\n".getBytes(StandardCharsets.US_ASCII));
            current.getOutputStream().flush();
        } catch (IOException ignored) {
            current.destroy();
        }
        try {
            if (!current.waitFor(15, TimeUnit.SECONDS)) current.destroyForcibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }
    }

    public static synchronized void requestStop() {
        Process current = process;
        if (current == null || !current.isAlive() || stopRequested) return;
        stopRequested = true;
        try {
            current.getOutputStream().write(
                    "stop\n".getBytes(StandardCharsets.US_ASCII));
            current.getOutputStream().flush();
        } catch (IOException exception) {
            current.destroy();
        }
        Thread waiter = new Thread(() -> {
            try {
                if (!current.waitFor(12, TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                current.destroyForcibly();
            }
        }, "monster-conquest-arena-stop");
        waiter.setDaemon(true);
        waiter.start();
    }

    @Nullable
    public static Path serverDirectory() {
        return serverDirectory;
    }

    @Nullable
    public static Path bridgeDirectory() {
        return bridgeDirectory;
    }

    private static void prepareRuntime(MinecraftServer mainServer,
                                       Path mainDirectory,
                                       Path childDirectory,
                                       Path bridgeDirectory) throws IOException {
        Files.createDirectories(childDirectory.resolve("mods"));
        Files.createDirectories(childDirectory.resolve("config"));
        Files.createDirectories(childDirectory.resolve(
                "config/monster_conquest"));
        Files.createDirectories(childDirectory.resolve("logs"));
        Files.createDirectories(bridgeDirectory.resolve("requests"));
        Files.writeString(childDirectory.resolve("eula.txt"), "eula=true\n",
                StandardCharsets.US_ASCII);
        Files.writeString(childDirectory.resolve("whitelist.json"), "[]\n",
                StandardCharsets.US_ASCII);
        Path mainArenaConfig = mainDirectory.resolve(
                "config/monster_conquest/arena-common.toml");
        if (Files.isRegularFile(mainArenaConfig)) {
            Files.copy(mainArenaConfig,
                    childDirectory.resolve(
                            "config/monster_conquest/arena-common.toml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        boolean onlineMode = mainServer.isDedicatedServer()
                && mainServer.usesAuthentication();
        boolean secureProfile = mainServer.enforceSecureProfile();
        String properties = String.join("\n",
                "accepts-transfers=true",
                "allow-flight=false",
                "allow-nether=false",
                "difficulty=normal",
                "enable-command-block=false",
                "enable-query=false",
                "enable-rcon=false",
                "enable-status=false",
                "enforce-secure-profile=" + secureProfile,
                "gamemode=spectator",
                "generate-structures=false",
                "generator-settings={\"layers\":[],\"biome\":\"minecraft:the_void\",\"features\":false,\"lakes\":false,\"structure_overrides\":[]}",
                "level-name=arena_world",
                "level-type=minecraft:flat",
                "max-players=" + ArenaConfig.arenaMaxPlayers,
                "motd=Monster Conquest Arena",
                "online-mode=" + onlineMode,
                "pvp=false",
                "server-port=" + ArenaConfig.arenaServerPort,
                "simulation-distance=4",
                "spawn-animals=false",
                "spawn-monsters=false",
                "spawn-npcs=false",
                "view-distance=6",
                "");
        Files.writeString(childDirectory.resolve("server.properties"),
                properties, StandardCharsets.UTF_8);

        Path currentModJar = null;
        IModFileInfo modFile = ModList.get().getModFileById(Monster_conquest.MODID);
        if (modFile != null) {
            Path jar = modFile.getFile().getFilePath();
            if (jar != null && Files.isRegularFile(jar)
                    && jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                currentModJar = jar.toAbsolutePath().normalize();
            }
        }
        List<Path> removedMods = ArenaModDirectorySynchronizer.synchronize(
                mainDirectory, childDirectory, currentModJar);
        for (Path removedMod : removedMods) {
            Monster_conquest.LOGGER.info(
                    "[ARENA-SERVER] Removed stale child mod {}",
                    removedMod);
        }

        // The source tree never writes into the user's main world; the shared
        // bridge is the only main-to-child channel.
        Files.createDirectories(bridgeDirectory);
    }

    private static List<String> buildLaunchCommand(Path mainDirectory,
                                                   Path childDirectory,
                                                   Path bridgeDirectory) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);

        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-agentlib:jdwp")
                    || argument.startsWith("-javaagent:")) {
                continue;
            }
            command.add(argument);
        }

        String classPath = absoluteClassPath(mainDirectory);
        if (!classPath.isBlank()) {
            command.add("-cp");
            command.add(classPath);
        }
        command.add("-D" + ArenaRuntime.ARENA_SERVER_PROPERTY + "=true");
        command.add("-D" + ArenaRuntime.BRIDGE_DIR_PROPERTY + "=" + bridgeDirectory);
        command.add("-D" + ArenaRuntime.PARENT_PID_PROPERTY + "="
                + ProcessHandle.current().pid());

        List<String> mainCommand = combineArgumentFiles(
                splitCommandLine(System.getProperty("sun.java.command", "")),
                mainDirectory);
        if (mainCommand.isEmpty()) {
            throw new IOException("Cannot determine current NeoForge launch command");
        }
        String mainClass = mainCommand.get(0);
        List<String> gameArgs;
        if (mainClass.startsWith("@")) {
            // Production launchers commonly pass an args file containing the
            // module options and BootstrapLauncher main class.
            gameArgs = new ArrayList<>(mainCommand);
        } else if ("-jar".equals(mainClass) && mainCommand.size() > 1) {
            Path jar = Path.of(mainCommand.get(1));
            if (!jar.isAbsolute()) jar = mainDirectory.resolve(jar);
            command.add("-jar");
            command.add(jar.normalize().toString());
            gameArgs = new ArrayList<>(mainCommand.subList(2, mainCommand.size()));
        } else if (mainClass.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            Path jar = Path.of(mainClass);
            if (!jar.isAbsolute()) jar = mainDirectory.resolve(jar);
            command.add("-jar");
            command.add(jar.normalize().toString());
            gameArgs = new ArrayList<>(mainCommand.subList(1, mainCommand.size()));
        } else {
            command.add(mainClass);
            gameArgs = new ArrayList<>(mainCommand.subList(1, mainCommand.size()));
        }

        for (int i = 0; i < gameArgs.size(); i++) {
            String arg = gameArgs.get(i);
            if ("--launchTarget".equals(arg) && i + 1 < gameArgs.size()) {
                gameArgs.set(i + 1, "forgeserverdev");
            }
            if ("--gameDir".equals(arg) && i + 1 < gameArgs.size()) {
                gameArgs.set(i + 1, childDirectory.toString());
            }
            if ("--assetsDir".equals(arg) || "--assetIndex".equals(arg)) {
                gameArgs.set(i, "");
                if (i + 1 < gameArgs.size()) gameArgs.set(i + 1, "");
            }
            if ("--quickPlaySingleplayer".equals(arg)
                    || "--quickPlayMultiplayer".equals(arg)
                    || "--quickPlayRealms".equals(arg)
                    || "--server".equals(arg)
                    || "--port".equals(arg)) {
                gameArgs.set(i, "");
                if (i + 1 < gameArgs.size()) gameArgs.set(i + 1, "");
            }
            if (arg.startsWith("@")) {
                Path file = Path.of(arg.substring(1));
                if (!file.isAbsolute()) file = mainDirectory.resolve(file);
                gameArgs.set(i, "@" + prepareServerArgumentFile(
                        file.normalize(), childDirectory));
            }
        }
        gameArgs.removeIf(String::isBlank);
        if (!gameArgs.contains("nogui")) gameArgs.add("nogui");
        command.addAll(gameArgs);
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Launching child from {} with main class {}",
                childDirectory, mainClass);
        return command;
    }

    private static Path prepareServerArgumentFile(Path source,
                                                  Path childDirectory)
            throws IOException {
        List<String> lines = new ArrayList<>(
                Files.readAllLines(source, StandardCharsets.UTF_8));
        boolean hasGameDir = false;
        boolean hasNoGui = false;
        for (int i = 0; i < lines.size(); i++) {
            String value = lines.get(i).trim();
            if ("--launchTarget".equals(value)
                    && i + 1 < lines.size()) {
                lines.set(i + 1, "forgeserverdev");
            } else if ("--gameDir".equals(value)) {
                hasGameDir = true;
                if (i + 1 < lines.size()) lines.set(i + 1, ".");
            } else if (("--assetsDir".equals(value)
                    || "--assetIndex".equals(value)
                    || "--version".equals(value)
                    || "--quickPlaySingleplayer".equals(value)
                    || "--quickPlayMultiplayer".equals(value)
                    || "--quickPlayRealms".equals(value))
                    && i + 1 < lines.size()) {
                lines.set(i, "");
                lines.set(i + 1, "");
            } else if ("--nogui".equals(value) || "nogui".equals(value)) {
                hasNoGui = true;
            }
        }
        lines.removeIf(String::isBlank);
        if (!hasGameDir) {
            lines.add("--gameDir");
            lines.add(".");
        }
        if (!hasNoGui) lines.add("--nogui");

        Files.createDirectories(childDirectory);
        Path target = childDirectory.resolve("arena-server-program-args.txt");
        Files.write(target, lines, StandardCharsets.UTF_8);
        return target.toAbsolutePath().normalize();
    }

    private static String absoluteClassPath(Path mainDirectory) {
        String separator = java.io.File.pathSeparator;
        String current = System.getProperty("java.class.path", "");
        if (current.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        for (String entry : current.split(java.util.regex.Pattern.quote(separator))) {
            if (entry.isBlank()) continue;
            Path path = Path.of(entry);
            if (!path.isAbsolute()) path = mainDirectory.resolve(path);
            if (!result.isEmpty()) result.append(separator);
            result.append(path.normalize());
        }
        return result.toString();
    }

    private static List<String> splitCommandLine(String commandLine) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    /**
     * {@code sun.java.command} concatenates arguments without retaining quotes.
     * Rejoin an argument-file path split at spaces by checking the real file.
     */
    private static List<String> combineArgumentFiles(List<String> arguments,
                                                     Path mainDirectory) {
        List<String> result = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!argument.startsWith("@")) {
                result.add(argument);
                continue;
            }

            String value = argument.substring(1);
            int end = i;
            while (!argumentFileExists(value, mainDirectory)
                    && end + 1 < arguments.size()) {
                value += " " + arguments.get(++end);
            }
            result.add("@" + value);
            i = end;
        }
        return result;
    }

    private static boolean argumentFileExists(String value, Path mainDirectory) {
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute()) path = mainDirectory.resolve(path);
            return Files.isRegularFile(path);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
