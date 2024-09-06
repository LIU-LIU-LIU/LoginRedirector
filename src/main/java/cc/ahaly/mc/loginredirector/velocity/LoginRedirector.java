package cc.ahaly.mc.loginredirector.velocity;

import cc.ahaly.mc.loginredirector.util.ConfigManager;
import cc.ahaly.mc.loginredirector.util.PlayerInfoManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.logging.Logger;

import javax.inject.Inject;
import java.nio.file.Path;

@Plugin(id = "loginredirector", name = "LoginRedirector", version = "1.0-SNAPSHOT", authors = {"AHALY.CC"})
public class LoginRedirector {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public LoginRedirector(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // 初始化 ConfigManager 和 PlayerInfoManager
        ConfigManager configManager = new ConfigManager(dataDirectory, logger);
        PlayerInfoManager playerInfoManager = new PlayerInfoManager(dataDirectory);

        // 注册事件监听器
        EventManager eventManager = server.getEventManager();
        eventManager.register(this, new LoginHandler(server, logger, configManager, playerInfoManager));
        eventManager.register(this, new CommandManager(logger, configManager));

        logger.info("LoginRedirector plugin has been initialized.");
    }
}
