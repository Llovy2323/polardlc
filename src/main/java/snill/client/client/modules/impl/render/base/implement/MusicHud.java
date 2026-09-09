package snill.client.client.modules.impl.render.base.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.api.utils.media.MediaTrack;
import snill.client.api.utils.media.MediaTracker;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

public class MusicHud extends InterfaceProcessing {

    private static final float CARD_WIDTH = 140.0f;
    private static final float CARD_HEIGHT = 42.0f;
    private static final float COVER_SIZE = 30.0f;
    private static final float COVER_PAD = 6.0f;
    private static final int BAR_COUNT = 10;

    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 8.5f, Easings.QUAD_OUT);
    private final AnimationUtils progressAnimation = new AnimationUtils(0.0f, 5.0f, Easings.QUAD_OUT);
    private final AnimationUtils playHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils prevHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils nextHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils coverHoverAnim = new AnimationUtils(0.0f, 10.0f, Easings.QUAD_OUT);
    private final AnimationUtils playingAnim = new AnimationUtils(0.0f, 7.0f, Easings.QUAD_OUT);

    private float discRotation;
    private float marqueeOffset;
    private long lastMarqueeNs = System.nanoTime();
    private String lastMarqueeKey = "";

    public MusicHud(Draggable draggable) {
        super(draggable);
        MediaTracker.getInstance().start();
    }

    private Font font(int size) {
        Font f = Fonts.getFont("suisse", size);
        if (f == null) f = Fonts.getFont("sf_regular", size);
        return f;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        MatrixStack matrices = eventRender.getContext().getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();

        draggable.setWidth(CARD_WIDTH);
        draggable.setHeight(CARD_HEIGHT);

        MediaTracker tracker = MediaTracker.getInstance();
        tracker.updateVisualizer();
        MediaTrack track = tracker.getCurrentTrack();

        boolean inChat = mc.currentScreen instanceof ChatScreen;
        boolean hasTrack = track != null && (!track.getRawTitle().isEmpty() || track.isPlaying());
        boolean visible = hasTrack || inChat;

        alphaAnimation.update(visible ? 1.0f : 0.0f);
        float alpha = alphaAnimation.getValue();
        if (alpha <= 0.01f) return;

        boolean isPlaying = track != null && track.isPlaying();
        playingAnim.update(isPlaying ? 1.0f : 0.0f);
        float playing = playingAnim.getValue();

        if (isPlaying) {
            discRotation = (discRotation + 1.65f + playing * 0.55f) % 360.0f;
        }

        String displayTitle = "Нет трека";
        String artist = "Включите в браузере";
        boolean hasCover = false;

        if (track != null && !track.getRawTitle().isEmpty()) {
            displayTitle = track.getRawTitle();
            artist = track.getArtist();
            hasCover = track.hasCover();
        } else if (inChat) {
            displayTitle = "Music Player";
            artist = "Ожидание трека...";
        }

        String marqueeKey = displayTitle + "|" + artist;
        if (!marqueeKey.equals(lastMarqueeKey)) {
            lastMarqueeKey = marqueeKey;
            marqueeOffset = 0.0f;
        }

        int themeColor = ColorUtils.getThemeColor();
        int themeSoft = ColorUtils.darken(themeColor, 0.35f);
        int textColor = ColorUtils.applyAlpha(ColorUtils.rgba(245, 246, 252, 255), alpha);
        int subTextColor = ColorUtils.applyAlpha(ColorUtils.rgba(158, 162, 178, 255), alpha);

        float pulse = isPlaying
                ? 0.55f + 0.45f * (float) ((Math.sin(System.currentTimeMillis() * 0.0064) + 1.0) * 0.5)
                : 0.25f;

        float coverX = x + COVER_PAD;
        float coverY = y + (CARD_HEIGHT - COVER_SIZE) * 0.5f;
        float coverCx = coverX + COVER_SIZE * 0.5f;
        float coverCy = coverY + COVER_SIZE * 0.5f;
        float textX = coverX + COVER_SIZE + 6.5f;

        // Полноразмерная ширина для названия трека сверху
        float maxTextW = (x + CARD_WIDTH - 8.0f) - textX;

        // Нижняя панель контролов (справа внизу)
        float ctrlCenterY = y + 29.5f;
        float playCx = x + CARD_WIDTH - 17.5f;
        float btnDist = 12.0f;
        float prevCx = playCx - btnDist;
        float nextCx = playCx + btnDist;

        double mx = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double my = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        boolean inScreen = mc.currentScreen != null;

        boolean coverHovered = inScreen && HoveringUtils.isHovered(mx, my, coverX, coverY, COVER_SIZE, COVER_SIZE);
        boolean prevHovered = inScreen && HoveringUtils.isHovered(mx, my, prevCx - 5.0f, ctrlCenterY - 5.0f, 10.0f, 10.0f);
        boolean playHovered = inScreen && HoveringUtils.isHovered(mx, my, playCx - 7.0f, ctrlCenterY - 7.0f, 14.0f, 14.0f);
        boolean nextHovered = inScreen && HoveringUtils.isHovered(mx, my, nextCx - 5.0f, ctrlCenterY - 5.0f, 10.0f, 10.0f);

        coverHoverAnim.update(coverHovered ? 1.0f : 0.0f);
        prevHoverAnim.update(prevHovered ? 1.0f : 0.0f);
        playHoverAnim.update(playHovered ? 1.0f : 0.0f);
        nextHoverAnim.update(nextHovered ? 1.0f : 0.0f);

        // 1. Подложка карточки
        drawCard(matrices, x, y, themeColor, themeSoft, alpha, pulse);

        // 2. Обложка и кольцо прогресса
        drawCover(matrices, track, coverX, coverY, coverCx, coverCy, hasCover, isPlaying, themeColor, alpha, pulse);
        drawProgressRing(matrices, track, coverCx, coverCy, themeColor, alpha);

        // 3. Текст: Название трека сверху (бегущая строка без артефактов)
        Font titleFont = font(11);
        Font artistFont = font(9);
        drawMarquee(titleFont, matrices, displayTitle, textX, y + 5.0f, maxTextW, textColor);

        // 4. Текст: Артист
        if (artistFont != null) {
            artistFont.draw(matrices, ellipsize(artistFont, artist, maxTextW), textX, y + 15.5f, subTextColor);
        }

        // 5. Визуализатор звука (слева внизу, под артистом)
        drawVisualizer(matrices, tracker, textX, y, themeColor, alpha, isPlaying);

        // 6. Кнопки управления (справа внизу)
        drawControls(matrices, playCx, prevCx, nextCx, ctrlCenterY, isPlaying, themeColor, subTextColor, alpha);

        super.onRender(eventRender);
    }

    private void drawCard(MatrixStack matrices, float x, float y, int themeColor, int themeSoft, float alpha, float pulse) {
        int glow = ColorUtils.applyAlpha(themeColor, alpha * 0.16f * pulse);
        RenderUtils.drawShadow(matrices, x - 1.5f, y - 1.0f, CARD_WIDTH + 3.0f, CARD_HEIGHT + 2.5f, 6.0f, 10.0f, glow);

        // Темное стекло
        RenderUtils.drawRoundedRect(matrices, x, y, CARD_WIDTH, CARD_HEIGHT, 6.0f,
                ColorUtils.rgba(12, 13, 18, (int) (230 * alpha)));
        RenderUtils.drawRoundedRectOutline(matrices, x, y, CARD_WIDTH, CARD_HEIGHT, 6.0f, 0.75f,
                ColorUtils.rgba(255, 255, 255, (int) (14 * alpha)));

        // Тонкий неоновый акцент по верхней грани
        RenderUtils.drawRoundedRect(matrices, x + 8.0f, y, CARD_WIDTH - 16.0f, 1.0f, 0.5f,
                ColorUtils.applyAlpha(themeColor, alpha * 0.85f));
    }

    private void drawCover(
            MatrixStack matrices,
            MediaTrack track,
            float coverX,
            float coverY,
            float coverCx,
            float coverCy,
            boolean hasCover,
            boolean isPlaying,
            int themeColor,
            float alpha,
            float pulse
    ) {
        RenderUtils.drawShadow(matrices, coverX - 1.0f, coverY - 1.0f, COVER_SIZE + 2.0f, COVER_SIZE + 2.0f, 6.0f, 8.0f,
                ColorUtils.applyAlpha(themeColor, alpha * 0.28f * pulse));

        if (hasCover && track != null) {
            RenderUtils.drawRoundedImage(matrices, track.getCoverTexture(), coverX, coverY, COVER_SIZE, 5.0f, alpha);
            RenderUtils.drawRoundedRect(matrices, coverX, coverY, COVER_SIZE, COVER_SIZE, 5.0f,
                    ColorUtils.rgba(255, 255, 255, (int) (14 * alpha)));

            if (isPlaying) {
                RenderUtils.drawRoundCircle(matrices, coverCx, coverCy, 3.0f,
                        ColorUtils.applyAlpha(ColorUtils.rgba(8, 8, 12, 255), alpha * 0.75f));
                RenderUtils.drawRoundCircle(matrices, coverCx, coverCy, 1.2f,
                        ColorUtils.applyAlpha(themeColor, alpha));
            }
        } else {
            drawVinyl(matrices, coverCx, coverCy, COVER_SIZE * 0.5f, themeColor, alpha, isPlaying);
        }

        float hover = coverHoverAnim.getValue();
        if (hover > 0.02f) {
            RenderUtils.drawRoundCircle(matrices, coverCx, coverCy, COVER_SIZE * 0.5f,
                    ColorUtils.rgba(6, 7, 12, (int) (150 * alpha * hover)));
            int overlay = ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * hover);
            if (isPlaying) {
                RenderUtils.drawRoundedRect(matrices, coverCx - 2.1f, coverCy - 3.0f, 1.4f, 6.0f, 0.45f, overlay);
                RenderUtils.drawRoundedRect(matrices, coverCx + 0.7f, coverCy - 3.0f, 1.4f, 6.0f, 0.45f, overlay);
            } else {
                drawPlayIcon(matrices, coverCx + 0.2f, coverCy, overlay);
            }
        }
    }

    private void drawVinyl(MatrixStack matrices, float cx, float cy, float radius, int themeColor, float alpha, boolean playing) {
        matrices.push();
        matrices.translate(cx, cy, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(playing ? discRotation : discRotation * 0.12f));
        matrices.translate(-cx, -cy, 0.0f);

        RenderUtils.drawRoundCircle(matrices, cx, cy, radius,
                ColorUtils.applyAlpha(ColorUtils.rgba(10, 10, 14, 255), alpha));
        RenderUtils.drawRoundCircle(matrices, cx, cy, radius - 1.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(24, 24, 32, 255), alpha));

        for (int i = 0; i < 4; i++) {
            float groove = radius - 3.0f - i * 2.2f;
            if (groove < 5.0f) break;
            RenderUtils.drawRoundCircle(matrices, cx, cy, groove,
                    ColorUtils.applyAlpha(ColorUtils.rgba(36 + i * 4, 36, 46, 255), alpha));
            RenderUtils.drawRoundCircle(matrices, cx, cy, groove - 0.75f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(16, 16, 22, 255), alpha));
        }

        RenderUtils.drawRoundCircle(matrices, cx, cy, 4.5f, ColorUtils.applyAlpha(themeColor, alpha));
        RenderUtils.drawRoundCircle(matrices, cx, cy, 1.8f,
                ColorUtils.applyAlpha(ColorUtils.rgba(8, 8, 12, 255), alpha));

        RenderUtils.drawRingArc(matrices, cx - radius, cy - radius, radius * 2.0f, 1.2f, -48.0f, 28.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.28f));
        matrices.pop();
    }

    private void drawProgressRing(MatrixStack matrices, MediaTrack track, float coverCx, float coverCy, int themeColor, float alpha) {
        float ringSize = COVER_SIZE + 3.0f;
        float ringX = coverCx - ringSize * 0.5f;
        float ringY = coverCy - ringSize * 0.5f;
        float thickness = 1.4f;

        RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, thickness, 0.0f, 360.0f,
                ColorUtils.rgba(255, 255, 255, (int) (16 * alpha)));

        float targetProg = track != null ? track.getProgress() : 0.0f;
        progressAnimation.update(targetProg);
        float animProg = MathHelper.clamp(progressAnimation.getValue(), 0.0f, 1.0f);

        if (track != null && track.getDurationMs() > 0) {
            float arc = Math.max(8.0f, animProg * 360.0f);
            RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, thickness, -90.0f, -90.0f + arc,
                    ColorUtils.applyAlpha(themeColor, alpha * 0.95f));
        } else if (track != null && track.isPlaying()) {
            float spin = (System.currentTimeMillis() * 0.16f) % 360.0f;
            RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, thickness, spin, spin + 90.0f,
                    ColorUtils.applyAlpha(themeColor, alpha * 0.90f));
        }
    }

    private void drawVisualizer(MatrixStack matrices, MediaTracker tracker, float textX, float y, int themeColor, float alpha, boolean isPlaying) {
        float[] bars = tracker.getVisualizerBars();
        float[] peaks = tracker.getPeakBars();
        if (bars == null || bars.length == 0) return;

        float barsStartX = textX;
        float barsBaseY = y + CARD_HEIGHT - 4.5f;
        float barWidth = 1.6f;
        float barGap = 1.2f;
        float maxBarH = 7.5f;
        int count = Math.min(BAR_COUNT, bars.length);

        float totalW = count * (barWidth + barGap) - barGap;
        RenderUtils.drawRoundedRect(matrices, barsStartX, barsBaseY + 0.5f, totalW, 0.6f, 0.3f,
                ColorUtils.applyAlpha(themeColor, alpha * 0.20f));

        long time = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            float wave = isPlaying ? 0.08f * (float) Math.sin(time * 0.012 + i * 0.55) : 0.0f;
            float value = MathHelper.clamp(bars[i] + wave, 0.12f, 1.0f);
            float barH = Math.max(1.6f, value * maxBarH);
            float bx = barsStartX + i * (barWidth + barGap);
            float by = barsBaseY - barH;

            int topColor = ColorUtils.interpolateColor(themeColor, ColorUtils.rgba(255, 255, 255, 255), 0.55f);
            int bottomColor = ColorUtils.darken(themeColor, 0.18f);

            RenderUtils.drawGradientRect(matrices, bx, by, barWidth, barH, 0.6f,
                    ColorUtils.applyAlpha(topColor, alpha * 0.96f),
                    ColorUtils.applyAlpha(bottomColor, alpha * 0.82f));

            if (peaks != null && i < peaks.length) {
                float peakY = barsBaseY - Math.max(barH + 0.7f, peaks[i] * maxBarH);
                RenderUtils.drawRoundedRect(matrices, bx, peakY, barWidth, 0.7f, 0.35f,
                        ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.88f));
            }
        }
    }

    private void drawControls(
            MatrixStack matrices,
            float playCx,
            float prevCx,
            float nextCx,
            float ctrlCenterY,
            boolean isPlaying,
            int themeColor,
            int subTextColor,
            float alpha
    ) {
        int prevColor = ColorUtils.interpolateColor(subTextColor, themeColor, prevHoverAnim.getValue());
        int nextColor = ColorUtils.interpolateColor(subTextColor, themeColor, nextHoverAnim.getValue());
        drawPrevIcon(matrices, prevCx, ctrlCenterY, ColorUtils.applyAlpha(prevColor, alpha));
        drawNextIcon(matrices, nextCx, ctrlCenterY, ColorUtils.applyAlpha(nextColor, alpha));

        float hover = playHoverAnim.getValue();
        float ringSize = 13.5f;
        float ringX = playCx - ringSize * 0.5f;
        float ringY = ctrlCenterY - ringSize * 0.5f;

        RenderUtils.drawRoundCircle(matrices, playCx, ctrlCenterY, 6.5f,
                ColorUtils.applyAlpha(themeColor, alpha * (0.10f + 0.18f * hover)));
        RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, 1.15f, 0.0f, 360.0f,
                ColorUtils.applyAlpha(themeColor, alpha * (0.35f + 0.45f * hover)));

        int core = ColorUtils.interpolateColor(
                ColorUtils.rgba(18, 19, 28, (int) (240 * alpha)),
                ColorUtils.rgba(36, 38, 54, (int) (245 * alpha)),
                hover
        );
        RenderUtils.drawRoundCircle(matrices, playCx, ctrlCenterY, 4.4f, core);

        int iconColor = ColorUtils.interpolateColor(themeColor, ColorUtils.rgba(255, 255, 255, 255), hover);
        iconColor = ColorUtils.applyAlpha(iconColor, alpha);
        if (isPlaying) {
            RenderUtils.drawRoundedRect(matrices, playCx - 1.6f, ctrlCenterY - 2.3f, 1.0f, 4.6f, 0.35f, iconColor);
            RenderUtils.drawRoundedRect(matrices, playCx + 0.6f, ctrlCenterY - 2.3f, 1.0f, 4.6f, 0.35f, iconColor);
        } else {
            drawPlayIcon(matrices, playCx + 0.15f, ctrlCenterY, iconColor);
        }
    }

    private void drawMarquee(Font font, MatrixStack matrices, String text, float x, float y, float maxW, int color) {
        if (font == null || text == null || text.isEmpty() || maxW <= 1.0f) return;

        float width = font.getWidth(text);
        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastMarqueeNs) / 1_000_000_000.0f, 0.0f, 0.05f);
        lastMarqueeNs = now;

        if (width <= maxW) {
            marqueeOffset = 0.0f;
            font.draw(matrices, text, x, y, color);
            return;
        }

        marqueeOffset += dt * 18.0f;
        float gap = 20.0f;
        float loop = width + gap;
        if (marqueeOffset >= loop) marqueeOffset -= loop;

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x, y - 1.0f, maxW, 11.0f);
        try {
            font.draw(matrices, text, x - marqueeOffset, y, color);
            font.draw(matrices, text, x - marqueeOffset + loop, y, color);
        } finally {
            ScissorUtils.unset();
            ScissorUtils.pop();
        }
    }

    private String ellipsize(Font font, String text, float maxW) {
        if (font == null || text == null) return "";
        if (font.getWidth(text) <= maxW) return text;

        String value = text;
        while (font.getWidth(value + "...") > maxW && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private void drawPlayIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float[] rgba = unpack(color);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, cx - 1.3f, cy - 2.4f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx - 1.3f, cy + 2.4f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx + 2.2f, cy, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private void drawNextIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float[] rgba = unpack(color);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, cx - 2.5f, cy - 2.2f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx - 2.5f, cy + 2.2f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx + 0.4f, cy, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();

        RenderUtils.drawRoundedRect(matrices, cx + 1.4f, cy - 2.2f, 1.0f, 4.4f, 0.35f, color);
    }

    private void drawPrevIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderUtils.drawRoundedRect(matrices, cx - 2.4f, cy - 2.2f, 1.0f, 4.4f, 0.35f, color);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float[] rgba = unpack(color);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, cx + 2.4f, cy - 2.2f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx + 2.4f, cy + 2.2f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx - 0.4f, cy, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private float[] unpack(int color) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                a / 255.0f
        };
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float x = draggable.getX();
        float y = draggable.getY();
        float coverX = x + COVER_PAD;
        float coverY = y + (CARD_HEIGHT - COVER_SIZE) * 0.5f;
        float ctrlCenterY = y + 29.5f;
        float playCx = x + CARD_WIDTH - 17.5f;
        float btnDist = 12.0f;
        float prevCx = playCx - btnDist;
        float nextCx = playCx + btnDist;

        if (HoveringUtils.isHovered(mouseX, mouseY, prevCx - 5.0f, ctrlCenterY - 5.0f, 10.0f, 10.0f)) {
            MediaTracker.getInstance().prevTrack();
            return true;
        }
        if (HoveringUtils.isHovered(mouseX, mouseY, playCx - 7.0f, ctrlCenterY - 7.0f, 14.0f, 14.0f)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }
        if (HoveringUtils.isHovered(mouseX, mouseY, nextCx - 5.0f, ctrlCenterY - 5.0f, 10.0f, 10.0f)) {
            MediaTracker.getInstance().nextTrack();
            return true;
        }
        if (HoveringUtils.isHovered(mouseX, mouseY, coverX, coverY, COVER_SIZE, COVER_SIZE)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }
        return false;
    }
}
