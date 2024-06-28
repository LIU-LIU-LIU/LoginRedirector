package cc.ahaly.mc.loginredirector;

import cc.ahaly.mc.loginredirector.util.ConfigManager;
import cc.ahaly.mc.loginredirector.util.PlayerInfo;
import cc.ahaly.mc.loginredirector.util.PlayerInfoManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class LoginHandler {

    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final PlayerInfoManager playerInfoManager;
    private final OkHttpClient httpClient = new OkHttpClient();
    private boolean isPremium = false;
    private boolean uuidRequestFailed;
    private boolean nameRequestFailed;
    private String denyReason = null; // 用于存储拒绝的原因
    private  String OfflineServername;

    public LoginHandler(ProxyServer server, Logger logger, ConfigManager configManager, PlayerInfoManager playerInfoManager) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
        this.playerInfoManager = playerInfoManager;

        OfflineServername = configManager.getOfflineServer();
    }
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        logger.info("处理玩家首次建立链接事件, event: " + event.toString());

        String name = event.getUsername();
        UUID uuid = event.getUniqueId();

        boolean isExists = playerInfoManager.isPlayerExists(name, uuid);
        boolean isVerifyLocal = playerInfoManager.verifyPlayer(name, uuid);;

        if (isExists) {
            //如果存在判断该玩家是否是例外玩家例如BE端的exception是否为真
            Optional<PlayerInfo> playerInfoOptional = playerInfoManager.getPlayerByName(name);
            if (playerInfoOptional.isPresent()) {
                PlayerInfo playerInfo = playerInfoOptional.get();
                // 检查玩家的 exception 字段
                if (playerInfo.getException()) {
                    return;
                }
            }
        }

        boolean isVerifiedMojang = false;
        if (uuid != null) {
            isVerifiedMojang = verifyWithMojang(uuid, name);
        }else {
            denyReason = "获取不到UUID，拒绝登录.";
            return;
        }

        //打印调试信息
        logger.info("玩家信息: 名字 id 是否存在 本地验证 在线验证" + name + " " + uuid + " " + isExists + " " + isVerifyLocal + " " + isVerifiedMojang);


        if (uuidRequestFailed && nameRequestFailed){
            denyReason = "请求mojang服务器异常次数过多，拒绝登录.";
            return;
        }

        if (isVerifiedMojang) {
            handleVerifiedMojang(event, isExists, isVerifyLocal, uuid, name);
        } else {
            handleUnverifiedMojang(event, isExists, isVerifyLocal, uuid, name);
        }
    }

    private void handleVerifiedMojang(PreLoginEvent event, boolean isExists, boolean isVerifyLocal, UUID uuid, String name) {
        if (isExists) {
            if (!isVerifyLocal) {
                denyReason = "你的身份未通过本地认证，请将该异常报告给管理员.";
                return;
            }
        }
        playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, true, false, false));
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        isPremium = true;
        // 根据配置决定是否使用离线UUID
        if (configManager.useOfflineUUID()) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

        }
    }

    private void handleUnverifiedMojang(PreLoginEvent event, boolean isExists, boolean isVerifyLocal, UUID uuid, String name) {
        if (isExists) {
            if (!isVerifyLocal) {
                denyReason = "你的身份未通过本地认证，可能和此服务器上的用户冲突了，请尝试更换用户名或将该异常报告给管理员.";
                return;
            }
        }
        playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, false, true, false));
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        isPremium = false;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getUsername();
        UUID uuid = player.getUniqueId();

        if (playerInfoManager.isPlayerExists(name, uuid)) {
            if (!playerInfoManager.verifyPlayer(name, uuid)) {
                denyReason = "2你的身份未通过本地认证，可能和此服务器上的用户冲突了，请尝试更换用户名或将该异常报告给管理员.";
                return;
            }
        }

        //打印调试信息
        logger.info("处理玩家登入事件,event:" + event + "player.getUsername:" + name + "player.getUniqueId:" + uuid);
        if (denyReason != null) {
            player.disconnect(Component.text(denyReason));
            denyReason = null;
            return;
        }

        // 检查玩家是否通过 Geyser 连接
        if (FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
            logger.info("Player " + name + " " + uuid + " joined through Geyser (Bedrock Edition).");
            playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, false, false, true));
            return;
        }

        // 检查玩家是否通过在线模式登录
        if (isPremium) {
            logger.info("Player " + player.getUsername() + uuid + " is online or ExemptIPs mode, redirecting to default server.");
        } else {
            logger.info("Player " + player.getUsername() + uuid + " is offline mode, redirecting to main server.");
            player.createConnectionRequest(server.getServer(OfflineServername).get()).fireAndForget();
        }
    }

    private boolean verifyWithMojang(UUID uuid, String name) {
        String uuidStr = uuid.toString().replace("-", "");
        String uuidUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr;
        String nameUrl = "https://api.mojang.com/users/profiles/minecraft/" + name;
        boolean uuidMatched = false;
        boolean nameMatched = false;
        uuidRequestFailed = false;
        nameRequestFailed = false;

        logger.info("Verifying UUID " + uuidStr + " and name " + name + " with Mojang...");

        // Verify name for UUID
        try (Response response = httpClient.newCall(new Request.Builder().url(uuidUrl).build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.info("UUID Response from Mojang: " + responseBody);

                if (!responseBody.isEmpty()) {
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    String responseName = jsonObject.get("name").getAsString();
                    nameMatched = Objects.equals(responseName, name);
                    if (!nameMatched) {
                        logger.warning("Name mismatch for name: " + name + ", expected: " + responseName);
                    }
                } else {
                    logger.warning("Empty response body from Mojang for name: " + name);
                }
            } else {
                logger.warning("Unsuccessful name response from Mojang for name: " + name);
            }
        } catch (IOException e) {
            logger.severe("Error verifying name for UUID with Mojang: " + e.getMessage());
            uuidRequestFailed = true;
        }

        // Verify UUID for Name
        try (Response response = httpClient.newCall(new Request.Builder().url(nameUrl).build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.info("Name Response from Mojang: " + responseBody);

                if (!responseBody.isEmpty()) {
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    String id = jsonObject.get("id").getAsString();
                    uuidMatched = Objects.equals(id, uuidStr);
                    if (!uuidMatched) {
                        logger.warning("UUID mismatch for UUID: " + uuidStr + ", expected: " + id);
                    }
                } else {
                    logger.warning("Empty response body from Mojang for UUID: " + uuidStr);
                }
            } else {
                logger.warning("Unsuccessful UUID response from Mojang for UUID: " + uuidStr);
            }
        } catch (IOException e) {
            logger.severe("Error verifying UUID for Name with Mojang: " + e.getMessage());
            nameRequestFailed = true;
        }

        if (uuidRequestFailed && nameRequestFailed) {
            logger.severe("Both UUID and Name requests to Mojang failed. Cancelling login.");
            return false;
        }

        return uuidMatched || nameMatched;
    }

    @Subscribe
    public void onPlayerDisconnect(KickedFromServerEvent event) {
        Player player = event.getPlayer();

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        logger.info("Player " + player.getUsername() + " disconnected from server " + currentServer.get().getServerInfo().getName());
        if (currentServer.isPresent()) {
            String serverName = currentServer.get().getServerInfo().getName();

            // 检查玩家是否从 main 服务器断开连接
            if (serverName.equals(OfflineServername)) {
                //断开链接
                player.disconnect(Component.text("在认证服务器失去连接时无法重定向到备用服务器，所以断开链接。"));
            }
        }
    }
}
