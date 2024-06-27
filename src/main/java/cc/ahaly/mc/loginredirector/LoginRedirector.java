package cc.ahaly.mc.loginredirector;

import cc.ahaly.mc.loginredirector.util.PlayerInfoManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

@Plugin(id = "loginredirector", name = "LoginRedirector", version = "1.0-SNAPSHOT", authors = {"AHALY.CC"})
public class LoginRedirector {

    private final ProxyServer server;
    private final Logger logger;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final PlayerInfoManager playerInfoManager;

    @Inject
    public LoginRedirector(ProxyServer server, Logger logger, PlayerInfoManager playerInfoManager) {
        this.server = server;
        this.logger = logger;
        this.playerInfoManager = new PlayerInfoManager();

    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        logger.info("处理玩家首次建立链接事件,event.toString:" + event.toString()  );
        logger.info("event.getUniqueId:"+ event.getUniqueId());
        logger.info("event.getConnection().toString():"+ event.getConnection().toString());

        String name = event.getUsername();
        UUID uuid = event.getUniqueId();

        if (isOnlineMode(uuid, name)) {
//            通过api匹配
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());

        }else {
            //未通过匹配
        }

    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        logger.info("处理玩家登入事件,event.toString:" + event.toString());
        logger.info(" event.getPlayer().toString():" + event.getPlayer().toString());

        // 检查玩家是否通过在线模式登录
        if (isOnlineMode(player)) {
            logger.info("Player " + player.getUsername() + " is online mode, redirecting to login server.");
            player.createConnectionRequest(server.getServer("login").get()).fireAndForget();
        } else {
            logger.info("Player " + player.getUsername() + " is offline mode, redirecting to main server.");
            player.createConnectionRequest(server.getServer("main").get()).fireAndForget();
        }
    }

    private boolean isOnlineMode(UUID uuid, String name) {
        boolean uuidResult = verifyUUIDWithMojang(uuid);
        boolean nameResult = verifyNameWithMojang(name);
        // 如果UUID或名称验证成功，则返回true
        return uuidResult || nameResult;
    }


    private boolean verifyUUIDWithMojang(UUID uuid) {
        String uuidr = uuid.toString().replace("-", "");
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidr;
        Request request = new Request.Builder().url(url).build();
        logger.info("Verifying UUID " + uuidr + " with Mojang...");
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.info("Response from Mojang: " + responseBody);

                JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                String id = jsonObject.get("id").getAsString();
                String name = jsonObject.get("name").getAsString();

                logger.info("UUID from response: " + id);
                logger.info("Name from response: " + name);

                return Objects.equals(id, uuidr);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean verifyNameWithMojang(String name) {
        String url = "https://api.mojang.com/users/profiles/minecraft/" + name;
        Request request = new Request.Builder().url(url).build();
        logger.info("Verifying name " + name + " with Mojang...");
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                logger.info("Response from Mojang: " + responseBody);

                JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                String id = jsonObject.get("id").getAsString();
                String name_mojang = jsonObject.get("name").getAsString();

                logger.info("UUID from response: " + id);
                logger.info("Name from response: " + name_mojang);

                return Objects.equals(name_mojang, name);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

}
