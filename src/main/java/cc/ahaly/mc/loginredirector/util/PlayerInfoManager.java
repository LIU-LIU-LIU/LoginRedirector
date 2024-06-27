package cc.ahaly.mc.loginredirector.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PlayerInfoManager {
    private static final String FILE_PATH = "players.json";
    private List<PlayerInfo> players;
    private Gson gson;

    public PlayerInfoManager() {
        this.gson = new Gson();
        this.players = loadPlayers();
    }

    private List<PlayerInfo> loadPlayers() {
        try (JsonReader reader = new JsonReader(new FileReader(FILE_PATH))) {
            Type playerListType = new TypeToken<ArrayList<PlayerInfo>>(){}.getType();
            return gson.fromJson(reader, playerListType);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void savePlayers() {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
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
}
