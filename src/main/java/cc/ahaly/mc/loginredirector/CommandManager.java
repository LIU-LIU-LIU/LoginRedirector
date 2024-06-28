package cc.ahaly.mc.loginredirector;

import cc.ahaly.mc.loginredirector.util.ConfigManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.logging.Logger;

public class CommandManager {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CommandManager.class);
    private final Logger logger;
    private final ConfigManager configManager;

    public CommandManager(Logger logger, ConfigManager configManager) {
        this.logger = logger;
        this.configManager = configManager;
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getCommandSource();
        String command = event.getCommand();
        List<String> disabledCommands = configManager.getDisabledCommands();
        List<String> disabledCommandsServers = configManager.getDisabledCommandsServers();

        String currentServer = player.getCurrentServer().get().getServerInfo().getName();

        // 检查命令是否以禁用的命令开头
        boolean isCommandDisabled = disabledCommands.stream().anyMatch(command::startsWith);

        if (isCommandDisabled && disabledCommandsServers.contains(currentServer)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(Component.text("该命令在当前服务器中已被禁用"));
        }
    }
}
