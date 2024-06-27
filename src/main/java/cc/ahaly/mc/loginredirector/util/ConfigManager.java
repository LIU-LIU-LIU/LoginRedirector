package cc.ahaly.mc.loginredirector.util;

import com.google.common.io.ByteStreams;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private Map<String, Object> config;

    public ConfigManager(@DataDirectory Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;

        try {
            copyDefaultResources();
            this.config = loadConfig();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize ConfigManager", e);
        }
    }

    private void copyDefaultResources() throws IOException {
        if (Files.notExists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        // Define the resource files to copy
        String[] resourceFiles = {"config.yml", "players.json"};

        for (String resourceFile : resourceFiles) {
            Path outFile = dataDirectory.resolve(resourceFile);
            if (Files.notExists(outFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceFile);
                     OutputStream out = Files.newOutputStream(outFile)) {
                    if (in == null) {
                        logger.warning("Resource not found: " + resourceFile);
                        continue;
                    }
                    ByteStreams.copy(in, out);
                    logger.info("Copied resource: " + resourceFile + " to " + outFile.toString());
                } catch (IOException e) {
                    logger.severe("Failed to copy resource: " + resourceFile);
                    throw e;
                }
            }
        }
    }

    private Map<String, Object> loadConfig() throws IOException {
        Path configFilePath = dataDirectory.resolve("config.yml");

        try (InputStream inputStream = Files.newInputStream(configFilePath)) {
            Yaml yaml = new Yaml(new Constructor(Map.class));
            return yaml.load(inputStream);
        } catch (IOException e) {
            logger.severe("Failed to load config file: " + e.getMessage());
            throw e;
        }
    }

    public String getOfflineServer() {
        return (String) config.getOrDefault("offlineServer", "main");
    }
}
