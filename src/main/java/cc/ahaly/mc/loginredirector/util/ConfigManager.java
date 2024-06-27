package cc.ahaly.mc.loginredirector.util;

import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private Configuration config;

    public ConfigManager(@DataDirectory Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;

        try {
            this.config = loadConfig();
        } catch (IOException e) {
            logger.severe("Failed to initialize ConfigManager: " + e.getMessage());
        }
    }

    private Configuration loadConfig() throws IOException {
        File dataFolder = dataDirectory.toFile();

        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                logger.severe("Failed to create data folder: " + dataFolder.getAbsolutePath());
                throw new IOException("Failed to create data folder");
            }
        }

        File configFile = new File(dataFolder, "config.yml");

        if (!configFile.exists()) {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yml");
                 FileOutputStream outputStream = new FileOutputStream(configFile)) {
                if (inputStream == null) {
                    logger.severe("Default config file not found in JAR: config.yml");
                    throw new IOException("Default config file not found in JAR");
                }

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                logger.info("Default config file copied to: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.severe("Failed to copy default config file: " + e.getMessage());
                throw e;
            }
        }

        try {
            return YamlConfiguration.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            logger.severe("Failed to load config file: " + e.getMessage());
            throw e;
        }
    }

    public String getOfflineServer() {
        return config.getString("offlineServer", "main");
    }
}
