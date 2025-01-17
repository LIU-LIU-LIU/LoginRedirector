package cc.ahaly.mc.loginredirector.velocity;

import cc.ahaly.mc.loginredirector.util.ConfigManager;
import cc.ahaly.mc.loginredirector.util.PlayerInfo;
import cc.ahaly.mc.loginredirector.util.PlayerInfoManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class LoginHandler {

    private final ProxyServer server;
    private final Logger logger;
    private final PlayerInfoManager playerInfoManager;
    private final OkHttpClient httpClient = new OkHttpClient();
    private boolean isPremium = false;
    private boolean uuidRequestFailed;
    private boolean nameRequestFailed;
    private String denyReason = null; // 用于存储拒绝的原因
    private final String OfflineServername;

    public LoginHandler(ProxyServer server, Logger logger, ConfigManager configManager, PlayerInfoManager playerInfoManager) {
        this.server = server;
        this.logger = logger;
        this.playerInfoManager = playerInfoManager;

        OfflineServername = configManager.getOfflineServer();
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        logger.info("处理玩家首次建立链接事件, event: " + event.toString());

        String name = event.getUsername();
        UUID uuid = event.getUniqueId();

        boolean isExists = playerInfoManager.isPlayerExists(name, uuid);
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

           //通过本地pre字段设置玩家状态
           if (playerInfoOptional.isPresent()) {
               PlayerInfo playerInfo = playerInfoOptional.get();
               // 检查玩家的 getPremium 字段
               if (playerInfo.getPremium()) {
                   event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                   isPremium = true;
               }
               if (playerInfo.getbBdrock()) {
                   event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                   isPremium = false;
               }
           }
        }else{
            boolean isVerifiedMojang = false;
            if (uuid == null) {
                //获取不到uuid默认为离线玩家去处理,一般只有在线玩家会在预处理阶段传uuid，离线玩家可能不会传，需要服务器生成。
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                isPremium = false;
                return;
            }
            isVerifiedMojang = verifyWithMojang(uuid, name);
            //打印调试信息
            logger.info("玩家不存在，信息: 名字 id 在线验证" + name + " " + uuid + " "  + isVerifiedMojang);
            if (uuidRequestFailed && nameRequestFailed){
                denyReason = "请求mojang服务器异常次数过多.";
                return;
            }
            if (isVerifiedMojang) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                isPremium = true;
            } else {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                isPremium = false;
            }
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getUsername();
        UUID uuid = player.getUniqueId();

        //打印调试信息
        logger.info("处理玩家登入事件,event:" + event + "player.getUsername:" + name + "player.getUniqueId:" + uuid);

        if (uuid == null) {
            denyReason = "获取不到UUID，拒绝登录.";
            return;
        }

        boolean isExists = playerInfoManager.isPlayerExists(name, uuid);
        if (isExists) {
            //只有JE在线玩家才会在pre事件传达正确的UUID(只有在设置在线登录的时候在login事件中JE在线玩家才会是正确的UUID)，BE和JE离线玩家pre的UUID都不准确，不要使用。
            //初步链接事件的ID是客户端生成的，登入服务器后的UUID是服务器生成的，所以只有在线玩家才是正确的。
            if (!playerInfoManager.verifyPlayer(name, uuid)) {
                player.disconnect(Component.text("你的身份未通过本地认证，可能和此服务器上的用户冲突了，请尝试更换账号或将该异常报告给管理员."));
                return;
            }
        }

        // 检查玩家是否通过 Geyser 连接
        if (FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
            logger.info("Player " + name + " " + uuid + " joined through Geyser (Bedrock Edition).");
            playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, false, false, true));
            return;
        }else {
            if (denyReason != null) {
                player.disconnect(Component.text(denyReason));
                denyReason = null;
                return;
            }
        }

        // 检查玩家是否通过在线模式登录
        if (isPremium) {
            logger.info("Player " + player.getUsername() + uuid + " 是在线玩家，前往默认服务器.");
            playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, true, false, false));
        } else {
            logger.info("Player " + player.getUsername() + uuid + " 是离线玩家，重定向到认证服务器。");
            //重定向到认证服务器
            Optional<RegisteredServer> optionalServer = server.getServer(OfflineServername);
            if (optionalServer.isPresent()) {
                RegisteredServer targetServer = optionalServer.get();
                logger.info("Attempting to redirect player " + player.getUsername() + " to server " + targetServer.getServerInfo().getName());
                player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(result -> {
                    if (!result) {
                        logger.warning("Player " + player.getUsername() + " failed to connect to " + targetServer.getServerInfo().getName());
                        player.disconnect(Component.text("无法连接到认证服务器:"));
                    }
                });
            } else {
                logger.warning("无法找到服务器：" + OfflineServername);
                player.disconnect(Component.text("服务器配置错误，请联系管理员。"));
            }
            playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, false, true, false));
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
        if (currentServer.isPresent()) {
            logger.info("Player " + player.getUsername() + " disconnected from server " + currentServer.get().getServerInfo().getName());
            String serverName = currentServer.get().getServerInfo().getName();

            // 检查玩家是否从 认证 服务器断开连接
            if (serverName.equals(OfflineServername)) {
                player.disconnect(Component.text("在认证服务器失去连接时无法重定向到备用服务器，所以断开链接。断开原因: " + PlainTextComponentSerializer.plainText().serialize(event.getServerKickReason().orElse(Component.text("未知原因")))));
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        Optional<ServerConnection> currentServer = player.getCurrentServer();
        if (currentServer.isPresent()) {
            String serverName = currentServer.get().getServerInfo().getName();
            logger.info("Player " + player.getUsername() + " disconnected from server " + serverName);

            // 检查玩家是否从 认证 服务器断开连接
            if (serverName.equals(OfflineServername)) {
                player.disconnect(Component.text("在认证服务器失去连接时无法重定向到备用服务器，所以断开链接。" ));
            }
        }
    }
}
