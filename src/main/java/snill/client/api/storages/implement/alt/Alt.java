package snill.client.api.storages.implement.alt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Alt {
    private String username;
    private UUID uuid;
    private long addedDate;
    private boolean favorite;

    public Alt(String username) {
        this.username = username;
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.addedDate = System.currentTimeMillis();
        this.favorite = false;
    }
}
