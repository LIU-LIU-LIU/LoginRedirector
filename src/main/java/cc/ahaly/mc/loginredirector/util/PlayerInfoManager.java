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

public class PlayerInfoManager {
    private final Path filePath;
    private List<PlayerInfo> players;
    private final Gson gson;

    public PlayerInfoManager(Path dataDirectory) {
        this.gson = new Gson();
        this.filePath = dataDirectory.resolve("players.json");
        this.players = loadPlayers();
    }

    private List<PlayerInfo> loadPlayers() {
        try (JsonReader reader = new JsonReader(new FileReader(filePath.toFile()))) {
            Type playerListType = new TypeToken<ArrayList<PlayerInfo>>(){}.getType();
            return gson.fromJson(reader, playerListType);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
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
        this.players.add(player);
        savePlayers();
    }

    public List<PlayerInfo> getPlayers() {
        return this.players;
    }

    //查询json文件中是否存在该玩家
    public boolean isPlayerExists(String name, String uuid) {
        return players.stream()
                .anyMatch(player -> player.getName().equals(name) && player.getUuid().equals(uuid));
    }

    public boolean verifyPlayer(String name, String uuid) {
        boolean uuidMatches = players.stream()
                .anyMatch(player -> player.getUuid().equals(uuid) && player.getName().equals(name));

        boolean nameMatches = players.stream()
                .anyMatch(player -> player.getName().equals(name) && player.getUuid().equals(uuid));

        if (uuidMatches && nameMatches) {
            System.out.println("Player " + name + " with UUID " + uuid + " is verified.");
            return true;
        } else {
            System.out.println("Player verification failed for name: " + name + " and UUID: " + uuid);
            return false;
        }
    }

}
