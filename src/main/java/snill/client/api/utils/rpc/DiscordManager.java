package snill.client.api.utils.rpc;

import lombok.Getter;
import net.minecraft.client.network.ServerInfo;
import snill.client.api.QClient;
import snill.client.api.utils.rpc.utils.DiscordEventHandlers;
import snill.client.api.utils.rpc.utils.DiscordRPC;
import snill.client.api.utils.rpc.utils.DiscordRichPresence;
import ru.virtuoz.convert.Convert;

@Getter
public class DiscordManager implements QClient {

    private DiscordDaemonThread discordDaemonThread;
    private long APPLICATION_ID;

    private boolean running;

    private String image;
    private String site;
    private String discord;

    public static DiscordRichPresence discordRichPresence = new DiscordRichPresence();
    public static DiscordRPC discordRPC = DiscordRPC.INSTANCE;
    private void cppInit() {
        discordDaemonThread = new DiscordDaemonThread();
        APPLICATION_ID = 1518324607998885908L;
        running = true;
        image = "https://files.catbox.moe/bfi6g2.gif";
        site = "https://polardlc.ru";
        discord = "https://discord.gg/zqgU8kcKmh";
    }
    @Convert(Convert.ConvertType.ULTRA)
    public void init() {
        cppInit();
        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder()
                .ready(user -> {
                    if (user == null) {
                        return;
                    }
                    String userId = user.userId;
                    String name = user.username;
                    String avatar = user.avatar;
                    DiscordProfileCache.onReady(userId, name, avatar);
                })
                .build();

        DiscordRPC.INSTANCE.Discord_Initialize(String.valueOf(APPLICATION_ID), handlers, true, "");
        discordRichPresence.startTimestamp = System.currentTimeMillis() / 1000L;
        discordRPC.Discord_UpdatePresence(discordRichPresence);

        new Thread(() -> {
            while (running) {
                try {
                    String playerName = DiscordProfileCache.getDisplayUsername();
                    discordRichPresence.details = "SNILL Client | " + playerName;
                    discordRichPresence.state = getServerDisplayName();
                    discordRichPresence.largeImageKey = image;
                    discordRichPresence.button_label_1 = "SNILL";
                    discordRichPresence.button_url_1 = site;
                    discordRichPresence.button_label_2 = "Discord";
                    discordRichPresence.button_url_2 = discord;
                    DiscordRPC.INSTANCE.Discord_UpdatePresence(discordRichPresence);
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Discord-RPC-Updater").start();

        discordDaemonThread.start();
    }

    public DiscordManager start() {
        init();
        return this;
    }

    private String getServerDisplayName() {
        if (mc == null) return "Idle";
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null || info.address == null || info.address.isEmpty()) return "Singleplayer";
        String host = info.address;
        int portIndex = host.indexOf(':');
        if (portIndex > 0) host = host.substring(0, portIndex);
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            return String.join(".", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return host;
    }

    public void stopRPC() {
        running = false;
        DiscordRPC.INSTANCE.Discord_Shutdown();
        if (discordDaemonThread != null) {
            discordDaemonThread.interrupt();
        }
    }

    private class DiscordDaemonThread extends Thread {
        @Override
        public void run() {
            this.setName("Discord-RPC");

            try {
                while (running) {
                    DiscordRPC.INSTANCE.Discord_RunCallbacks();
                    Thread.sleep(2000L);
                }
            } catch (Exception exception) {
                stopRPC();
            }

            super.run();
        }
    }
}
