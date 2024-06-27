package cc.ahaly.mc.loginredirector.util;

import java.util.UUID;

public class PlayerInfo {
    private String id;
    private UUID uuid;
    private String name;
    private boolean premium;
    private boolean bedrock;
    private boolean exception;

    public PlayerInfo(String id, UUID uuid, String name, boolean premium, boolean bedrock, boolean exception) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.premium = premium;
        this.bedrock = bedrock;
        this.exception = exception;
    }

    // Getters and Setters
}
