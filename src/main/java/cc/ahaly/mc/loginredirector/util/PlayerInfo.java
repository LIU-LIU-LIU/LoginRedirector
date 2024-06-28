package cc.ahaly.mc.loginredirector.util;

import java.util.Date;
import java.util.UUID;

public class PlayerInfo {
    //时间
    private Date time;
    private UUID uuid;
    private String name;
    private boolean premium;
    private boolean bedrock;
    private boolean exception;

    public PlayerInfo(Date time, UUID uuid, String name, boolean premium, boolean bedrock, boolean exception) {
        this.time = time;
        this.uuid = uuid;
        this.name = name;
        this.premium = premium;
        this.bedrock = bedrock;
        this.exception = exception;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean getPremium() {
        return premium;
    }

    public boolean getbBdrock() {
        return bedrock;
    }
    // Getters and Setters
}
