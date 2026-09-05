package snill.client.api.utils.rpc;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import snill.client.api.QClient;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public final class DiscordProfileCache implements QClient {

    public static final Identifier AVATAR_TEXTURE_ID = Identifier.of("snill", "discord_avatar");

    private static volatile String username = "";
    private static volatile boolean avatarReady;
    private static volatile String lastAvatarUrl = "";

    private DiscordProfileCache() {
    }

    public static void onReady(String userId, String discordUsername, String avatarHash) {
        if (discordUsername != null && !discordUsername.isEmpty()) {
            username = discordUsername;
        }

        String avatarUrl = buildAvatarUrl(userId, avatarHash);
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return;
        }
        if (avatarUrl.equals(lastAvatarUrl) && avatarReady) {
            return;
        }

        lastAvatarUrl = avatarUrl;
        avatarReady = false;
        new Thread(() -> loadAvatar(avatarUrl), "Discord-Avatar-Loader").start();
    }

    public static String getUsername() {
        return username;
    }

    public static String getDisplayUsername() {
        if (username != null && !username.isEmpty()) {
            return username;
        }
        if (mc != null && mc.getSession() != null) {
            String sessionName = mc.getSession().getUsername();
            if (sessionName != null && !sessionName.isEmpty()) {
                return sessionName;
            }
        }
        return "Player";
    }

    public static boolean hasAvatar() {
        return avatarReady;
    }

    public static Identifier getAvatarTexture() {
        return AVATAR_TEXTURE_ID;
    }

    private static String buildAvatarUrl(String userId, String avatarHash) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }

        if (avatarHash != null && !avatarHash.isEmpty()) {
            return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png?size=64";
        }

        int index = 0;
        try {
            index = (int) (Long.parseLong(userId) % 5L);
        } catch (NumberFormatException ignored) {
        }
        return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
    }

    private static void loadAvatar(String avatarUrl) {
        try {
            URL url = URI.create(avatarUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "PolarClient/1.0");

            try (InputStream input = connection.getInputStream()) {
                NativeImage image = NativeImage.read(input);
                if (image == null) {
                    return;
                }

                if (mc == null) {
                    image.close();
                    return;
                }

                mc.execute(() -> registerAvatarTexture(image));
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private static void registerAvatarTexture(NativeImage image) {
        if (mc == null) {
            image.close();
            return;
        }

        try {
            if (mc.getTextureManager().getTexture(AVATAR_TEXTURE_ID) != null) {
                mc.getTextureManager().destroyTexture(AVATAR_TEXTURE_ID);
            }
            mc.getTextureManager().registerTexture(AVATAR_TEXTURE_ID, new NativeImageBackedTexture(image));
            avatarReady = true;
        } catch (Exception ignored) {
            image.close();
            avatarReady = false;
        }
    }
}
