package snill.client.api.utils.media;

import net.minecraft.util.Identifier;

public class MediaTrack {
    private final String rawTitle;
    private final String displayTitle;
    private final String artist;
    private final String album;
    private final boolean playing;
    private final long positionMs;
    private final long durationMs;
    private final long timestamp;
    private Identifier coverTexture;
    private boolean hasCover;

    public MediaTrack(String rawTitle, String artist, String album, boolean playing, long positionMs, long durationMs) {
        this.rawTitle = rawTitle != null ? rawTitle.trim() : "";
        this.displayTitle = formatTitle(this.rawTitle);
        this.artist = artist != null && !artist.trim().isEmpty() ? artist.trim() : "Неизвестный автор";
        this.album = album != null ? album.trim() : "";
        this.playing = playing;
        this.positionMs = Math.max(0, positionMs);
        this.durationMs = Math.max(0, durationMs);
        this.timestamp = System.currentTimeMillis();
    }

    public static String formatTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Нет трека";
        }
        String trimmed = title.trim();
        // Строгий лимит в 24 символа + "..." по требованию пользователя
        if (trimmed.length() > 24) {
            return trimmed.substring(0, 24) + "...";
        }
        return trimmed;
    }

    public String getRawTitle() {
        return rawTitle;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public boolean isPlaying() {
        return playing;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getInterpolatedPositionMs() {
        if (!playing || durationMs <= 0) return positionMs;
        long delta = System.currentTimeMillis() - timestamp;
        return Math.min(durationMs, positionMs + delta);
    }

    public float getProgress() {
        if (durationMs <= 0) return 0.0f;
        return Math.min(1.0f, (float) getInterpolatedPositionMs() / (float) durationMs);
    }

    public Identifier getCoverTexture() {
        return coverTexture;
    }

    public void setCoverTexture(Identifier coverTexture) {
        this.coverTexture = coverTexture;
        this.hasCover = coverTexture != null;
    }

    public boolean hasCover() {
        return hasCover && coverTexture != null;
    }
}
