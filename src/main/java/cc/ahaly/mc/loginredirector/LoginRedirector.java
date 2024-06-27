package cc.ahaly.mc.loginredirector;

import cc.ahaly.mc.loginredirector.util.ConfigManager;
import cc.ahaly.mc.loginredirector.util.PlayerInfo;
import cc.ahaly.mc.loginredirector.util.PlayerInfoManager;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;



@Plugin(id = "loginredirector", name = "LoginRedirector", version = "1.0-SNAPSHOT", authors = {"AHALY.CC"})
public class LoginRedirector {

    private final ProxyServer server;
    private final Logger logger;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final PlayerInfoManager playerInfoManager;
    //prelogin事件里用于标记玩家是否正版的全局变量给login事件
    private boolean isPremium = false;
    //用于标记请求mojang异常次数，如果有俩次则暂时拒绝登录
    private int requestExceptionCount = 0;

    @Inject
    public LoginRedirector(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;

        // Initialize ConfigManager
        ConfigManager configManager = new ConfigManager(dataDirectory, logger);

        // Initialize PlayerInfoManager
        this.playerInfoManager = new PlayerInfoManager(dataDirectory);
        logger.info("Plugin enabled and PlayerInfoManager initialized.");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        logger.info("处理玩家首次建立链接事件, event: " + event.toString());
        logger.info("event.getUniqueId: " + event.getUniqueId());
        logger.info("event.getConnection(): " + event.getConnection().toString());

        String name = event.getUsername();
        UUID uuid = event.getUniqueId();

        boolean isExists = playerInfoManager.isPlayerExists(name, uuid.toString());
        boolean isVerifyLocal = playerInfoManager.verifyPlayer(name, uuid.toString());
        boolean isVerifiedMojang = verifyWithMojang(uuid, name);

        if (requestExceptionCount >= 2){
            denyAccess(event, "请求mojang服务器异常次数过多，拒绝登录.");
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
                denyAccess(event, "你的身份未通过本地认证，请将该异常报告给管理员.");
            }
        } else {
            playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, true, false, false));
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
            isPremium = true;
        }
    }

    private void handleUnverifiedMojang(PreLoginEvent event, boolean isExists, boolean isVerifyLocal, UUID uuid, String name) {
        if (isExists) {
            if (isVerifyLocal) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                isPremium = false;
            } else {
                denyAccess(event, "你的身份未通过本地认证，可能和此服务器上的在线用户冲突了，请尝试更换用户名或将该异常报告给管理员.");
            }
        } else {
            playerInfoManager.addPlayer(new PlayerInfo(new Date(), uuid, name, false, true, false));
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            isPremium = false;
        }
    }

    private void denyAccess(PreLoginEvent event, String reason) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(reason)));
    }


    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        logger.info("处理玩家登入事件,event.toString:" + event.toString());
        logger.info(" event.getPlayer().toString():" + event.getPlayer().toString());

        // 检查玩家是否通过在线模式登录
        if (isPremium) {
            logger.info("Player " + player.getUsername() + " is online mode, redirecting to login server.");
//            player.createConnectionRequest(server.getServer("login").get()).fireAndForget();
        } else {
            logger.info("Player " + player.getUsername() + " is offline mode, redirecting to main server.");
            player.createConnectionRequest(server.getServer("main").get()).fireAndForget();
        }
    }

    private boolean verifyWithMojang(UUID uuid, String name) {
        String uuidStr = uuid.toString().replace("-", "");
        String uuidUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr;
        String nameUrl = "https://api.mojang.com/users/profiles/minecraft/" + name;
        boolean uuidMatched = false;
        boolean nameMatched = false;
        boolean uuidRequestFailed = false;
        boolean nameRequestFailed = false;

        logger.info("Verifying UUID " + uuidStr + " and name " + name + " with Mojang...");

        // Verify UUID
        try (Response response = httpClient.newCall(new Request.Builder().url(uuidUrl).build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.info("UUID Response from Mojang: " + responseBody);

                if (!responseBody.isEmpty()) {
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    String id = jsonObject.get("id").getAsString();
                    uuidMatched = Objects.equals(id, uuidStr);
                } else {
                    logger.warning("Empty response body from Mojang for UUID: " + uuidStr);
                }
            } else {
                logger.warning("Unsuccessful UUID response from Mojang for UUID: " + uuidStr);
            }
        } catch (IOException e) {
            logger.severe("Error verifying UUID with Mojang: " + e.getMessage());
            uuidRequestFailed = true;
            // requestExceptionCount+1
            requestExceptionCount = requestExceptionCount + 1;
        }

        // Verify Name
        try (Response response = httpClient.newCall(new Request.Builder().url(nameUrl).build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.info("Name Response from Mojang: " + responseBody);

                if (!responseBody.isEmpty()) {
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    String responseName = jsonObject.get("name").getAsString();
                    nameMatched = Objects.equals(responseName, name);
                } else {
                    logger.warning("Empty response body from Mojang for name: " + name);
                }
            } else {
                logger.warning("Unsuccessful name response from Mojang for name: " + name);
            }
        } catch (IOException e) {
            logger.severe("Error verifying name with Mojang: " + e.getMessage());
            nameRequestFailed = true;
            // requestExceptionCount+1
            requestExceptionCount = requestExceptionCount + 1;
        }

        // 判断请求结果
        if (uuidRequestFailed && nameRequestFailed) {
            logger.severe("Both UUID and Name requests to Mojang failed. Cancelling login.");
            // Handle login cancellation in the calling method
            return false;
        }

        return uuidMatched || nameMatched;
    }


}