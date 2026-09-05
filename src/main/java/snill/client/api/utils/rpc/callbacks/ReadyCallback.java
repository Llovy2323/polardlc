package snill.client.api.utils.rpc.callbacks;

import com.sun.jna.Callback;
import snill.client.api.utils.rpc.utils.DiscordUser;

public interface ReadyCallback extends Callback {
    void apply(DiscordUser var1);
}
