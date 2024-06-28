package cc.ahaly.mc.loginredirector.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerInfoManager {
    private final Path filePath;
    private List<PlayerInfo> players;
    private final Gson gson;

    public PlayerInfoManager(Path dataDirectory) {
        this.gson = new Gson();
        this.filePath = dataDirectory.resolve("players.json");
        this.players = new ArrayList<>();
        loadPlayers();
    }

    private void loadPlayers() {
        try (JsonReader reader = new JsonReader(new FileReader(filePath.toFile()))) {
            Type playerListType = new TypeToken<ArrayList<PlayerInfo>>(){}.getType();
            List<PlayerInfo> loadedPlayers = gson.fromJson(reader, playerListType);
            if (loadedPlayers != null) {
                this.players = loadedPlayers;
            } else {
                this.players = new ArrayList<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.players = new ArrayList<>();
        }
    }

    public void savePlayers() {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            gson.toJson(this.players, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addPlayer(PlayerInfo player) {
        if (!isPlayerExists(player.getName(), player.getUuid())) {
            this.players.add(player);
            savePlayers();
        } else {
            System.out.println("Player " + player.getName() + " with UUID " + player.getUuid() + " already exists.");
        }
    }

    public List<PlayerInfo> getPlayers() {
        return this.players;
    }

    // 查询 JSON 文件中是否存在该玩家
    public boolean isPlayerExists(String name, UUID uuid) {
        loadPlayers();
        return players.stream()
                .anyMatch(player -> player.getName().equals(name) || player.getUuid().equals(uuid));
    }

    public boolean verifyPlayer(String name, UUID uuid) {
        loadPlayers();
        // 通过 UUID 找到玩家
        PlayerInfo playerByUuid = players.stream()
                .filter(player -> player.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);

        // 如果找不到该 UUID 对应的玩家，验证失败
        if (playerByUuid == null) {
            System.out.println("Player verification failed: No player with UUID " + uuid + " found.");
            return false;
        }

        // 检查找到的玩家名称是否与传入的名称匹配
        if (!playerByUuid.getName().equals(name)) {
            System.out.println("Player verification failed: UUID " + uuid + " does not match name " + name + ".");
            return false;
        }

        // 通过名称找到玩家
        PlayerInfo playerByName = players.stream()
                .filter(player -> player.getName().equals(name))
                .findFirst()
                .orElse(null);

        // 如果找不到该名称对应的玩家，验证失败
        if (playerByName == null) {
            System.out.println("Player verification failed: No player with name " + name + " found.");
            return false;
        }

        // 检查找到的玩家 UUID 是否与传入的 UUID 匹配
        if (!playerByName.getUuid().equals(uuid)) {
            System.out.println("Player verification failed: Name " + name + " does not match UUID " + uuid + ".");
            return false;
        }

        // 两次匹配都成功，验证通过
        System.out.println("Player " + name + " with UUID " + uuid + " is verified.");
        return true;
    }
    // 添加方法来通过名称检索玩家并检查异常状态
    public Optional<PlayerInfo> getPlayerByName(String name) {
        return players.stream()
                .filter(player -> player.getName().equals(name))
                .findFirst();
    }
}
