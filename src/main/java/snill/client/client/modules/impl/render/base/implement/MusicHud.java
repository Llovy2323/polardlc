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
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

public class MusicHud extends InterfaceProcessing {

    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 8.5f, Easings.QUAD_OUT);
    private final AnimationUtils progressAnimation = new AnimationUtils(0.0f, 5.0f, Easings.QUAD_OUT);
    private float discRotation = 0.0f;

    private static final float CARD_WIDTH = 136.0f;
    private static final float CARD_HEIGHT = 40.0f;
    private static final float COVER_SIZE = 32.0f;

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

        // Плавное вращение винила при воспроизведении
        if (track != null && track.isPlaying()) {
            discRotation = (discRotation + 1.8f) % 360f;
        }

        // Данные трека (лимит в 24 символа)
        String displayTitle = "Нет трека";
        String artist = "Включите в браузере";
        boolean isPlaying = false;
        boolean hasCover = false;

        Font titleFont = font(12);
        Font artistFont = font(10);

        float coverX = x + 4.5f;
        float coverY = y + 4.0f;
        float textX = coverX + COVER_SIZE + 5.5f;
        float maxTextW = (x + CARD_WIDTH - 6.0f) - textX;

        if (track != null && !track.getRawTitle().isEmpty()) {
            displayTitle = track.getDisplayTitle();
            if (displayTitle.length() > 27) {
                displayTitle = displayTitle.substring(0, 24) + "...";
            }
            if (titleFont != null) {
                while (titleFont.getWidth(displayTitle) > maxTextW && displayTitle.length() > 4) {
                    String base = displayTitle.endsWith("...") ? displayTitle.substring(0, displayTitle.length() - 3) : displayTitle;
                    if (base.length() <= 1) break;
                    displayTitle = base.substring(0, base.length() - 1) + "...";
                }
            }
            artist = track.getArtist();
            if (artist.length() > 24) {
                artist = artist.substring(0, 24) + "...";
            }
            if (artistFont != null) {
                while (artistFont.getWidth(artist) > maxTextW && artist.length() > 4) {
                    String base = artist.endsWith("...") ? artist.substring(0, artist.length() - 3) : artist;
                    if (base.length() <= 1) break;
                    artist = base.substring(0, base.length() - 1) + "...";
                }
            }
            isPlaying = track.isPlaying();
            hasCover = track.hasCover();
        } else if (inChat) {
            displayTitle = "Music Player";
            artist = "Ожидание трека...";
        }

        int themeColor = ColorUtils.getThemeColor();
        int textColor = ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha);
        int subTextColor = ColorUtils.applyAlpha(ColorUtils.rgba(165, 165, 180, 255), alpha);

        // 1. Мягкая внешняя тень карточки
        RenderUtils.drawRoundedRect(matrices, x - 1.0f, y - 1.0f, CARD_WIDTH + 2.0f, CARD_HEIGHT + 2.0f, 6.5f,
                ColorUtils.rgba(0, 0, 0, (int) (45 * alpha)));

        // 2. Основная компактная темная стеклянная карточка (Glassmorphic Dark)
        RenderUtils.drawRoundedRect(matrices, x, y, CARD_WIDTH, CARD_HEIGHT, 5.5f,
                ColorUtils.rgba(18, 18, 24, (int) (225 * alpha)));

        // 3. Тонкий акцентный неоновый блик по верхней грани
        RenderUtils.drawGradientRect(matrices, x + 6.0f, y, CARD_WIDTH - 12.0f, 1.0f, 1.0f,
                ColorUtils.applyAlpha(themeColor, alpha * 0.85f),
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 40), alpha * 0.35f));

        // 4. Обложка трека (аккуратный квадрат 32x32 со скруглением)
        if (hasCover) {
            RenderUtils.drawRoundedImage(matrices, track.getCoverTexture(), coverX, coverY, COVER_SIZE, 4.5f, alpha);
            // Тонкая полупрозрачная рамка вокруг обложки
            RenderUtils.drawRoundedRect(matrices, coverX, coverY, COVER_SIZE, COVER_SIZE, 4.5f,
                    ColorUtils.rgba(255, 255, 255, (int) (18 * alpha)));
        } else {
            // Премиальная виниловая пластинка с гранями и вращением
            float cx = coverX + COVER_SIZE * 0.5f;
            float cy = coverY + COVER_SIZE * 0.5f;
            float radius = COVER_SIZE * 0.5f;

            // Внешний обод
            RenderUtils.drawRoundCircle(matrices, cx, cy, radius,
                    ColorUtils.applyAlpha(ColorUtils.rgba(32, 32, 40, 255), alpha));
            // Тело винила
            RenderUtils.drawRoundCircle(matrices, cx, cy, radius - 1.0f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(18, 18, 22, 255), alpha));
            // Глянцевые звуковые дорожки
            RenderUtils.drawRoundCircle(matrices, cx, cy, radius - 3.5f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(30, 30, 38, 255), alpha));
            RenderUtils.drawRoundCircle(matrices, cx, cy, radius - 6.0f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(40, 40, 50, 255), alpha));
            RenderUtils.drawRoundCircle(matrices, cx, cy, radius - 8.5f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(24, 24, 30, 255), alpha));
            // Центральная этикетка цвета темы
            RenderUtils.drawRoundCircle(matrices, cx, cy, 4.0f,
                    ColorUtils.applyAlpha(themeColor, alpha));
            // Центральное отверстие
            RenderUtils.drawRoundCircle(matrices, cx, cy, 1.5f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(12, 12, 16, 255), alpha));
        }

        // 5. Текстовая информация (до 24 букв)
        if (titleFont != null) {
            titleFont.draw(matrices, displayTitle, textX, y + 5.5f, textColor);
        }
        if (artistFont != null) {
            artistFont.draw(matrices, artist, textX, y + 16.0f, subTextColor);
        }

        // 6. Идеально симметричная панель управления: [|<]  [ ( ▶ / || ) ]  [>|]
        double mx = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double my = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        boolean inScreen = mc.currentScreen != null;

        float ctrlCenterY = y + 26.0f;
        float playCx = x + CARD_WIDTH - 25.5f;
        float btnDist = 15.0f;
        float prevCx = playCx - btnDist;
        float nextCx = playCx + btnDist;

        float ringSize = 15.0f;
        float ringThickness = 1.6f;
        float ringX = playCx - (ringSize / 2f);
        float ringY = ctrlCenterY - (ringSize / 2f);

        boolean prevHovered = inScreen && HoveringUtils.isHovered(mx, my, prevCx - 6.0f, ctrlCenterY - 6.0f, 12f, 12f);
        boolean playHovered = inScreen && HoveringUtils.isHovered(mx, my, playCx - 8.0f, ctrlCenterY - 8.0f, 16f, 16f);
        boolean nextHovered = inScreen && HoveringUtils.isHovered(mx, my, nextCx - 6.0f, ctrlCenterY - 6.0f, 12f, 12f);

        // 6.1. Кнопка предыдущего трека (|<)
        int prevColor = ColorUtils.applyAlpha(prevHovered ? themeColor : subTextColor, alpha);
        drawPrevIcon(matrices, prevCx, ctrlCenterY, prevColor);

        // 6.2. Фоновое тонкое кольцо прогресса
        RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, ringThickness, 0f, 360f,
                ColorUtils.rgba(255, 255, 255, (int) (22 * alpha)));

        // Динамическая дуга прогресса трека
        float targetProg = track != null ? track.getProgress() : 0.0f;
        progressAnimation.update(targetProg);
        float animProg = Math.max(0.0f, Math.min(1.0f, progressAnimation.getValue()));

        if (track != null && track.getDurationMs() > 0) {
            float arcAngle = Math.max(3.0f, animProg * 360f);
            RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, ringThickness, -90f, -90f + arcAngle,
                    ColorUtils.applyAlpha(themeColor, alpha * 0.95f));
        } else if (isPlaying) {
            float spin = (System.currentTimeMillis() * 0.12f) % 360f;
            RenderUtils.drawRingArc(matrices, ringX, ringY, ringSize, ringThickness, spin, spin + 100f,
                    ColorUtils.applyAlpha(themeColor, alpha * 0.90f));
        }

        // Внутренняя стеклянная круглая кнопка
        int coreColor = playHovered
                ? ColorUtils.rgba(45, 45, 60, (int) (245 * alpha))
                : ColorUtils.rgba(24, 24, 32, (int) (235 * alpha));
        RenderUtils.drawRoundCircle(matrices, playCx, ctrlCenterY, 5.0f, coreColor);

        // Иконка Play / Pause внутри кольца (строго по центру)
        if (isPlaying) {
            int pauseColor = playHovered ? ColorUtils.rgba(255, 255, 255, 255) : themeColor;
            RenderUtils.drawRoundedRect(matrices, playCx - 1.8f, ctrlCenterY - 2.8f, 1.2f, 5.6f, 0.4f,
                    ColorUtils.applyAlpha(pauseColor, alpha));
            RenderUtils.drawRoundedRect(matrices, playCx + 0.6f, ctrlCenterY - 2.8f, 1.2f, 5.6f, 0.4f,
                    ColorUtils.applyAlpha(pauseColor, alpha));
        } else {
            int playColor = playHovered ? ColorUtils.rgba(255, 255, 255, 255) : themeColor;
            drawPlayIcon(matrices, playCx, ctrlCenterY, ColorUtils.applyAlpha(playColor, alpha));
        }

        // 6.3. Кнопка следующего трека (>|)
        int nextColor = ColorUtils.applyAlpha(nextHovered ? themeColor : subTextColor, alpha);
        drawNextIcon(matrices, nextCx, ctrlCenterY, nextColor);

        // 7. Красивая звуковая волна (13 полос, гармонично заполняющих левую часть)
        float[] bars = tracker.getVisualizerBars();
        float[] peaks = tracker.getPeakBars();
        float barsStartX = textX;
        float barsBaseY = y + CARD_HEIGHT - 4.5f;
        float barWidth = 1.8f;
        float barGap = 1.2f;
        float maxBarH = 9.5f;
        int barCount = 13;

        float visualizerTotalW = barCount * (barWidth + barGap) - barGap;
        RenderUtils.drawRoundedRect(matrices, barsStartX, barsBaseY + 0.6f, visualizerTotalW, 0.6f, 0.3f,
                ColorUtils.applyAlpha(themeColor, alpha * 0.25f));

        for (int i = 0; i < Math.min(bars.length, barCount); i++) {
            float barH = Math.max(1.8f, bars[i] * maxBarH);
            float bx = barsStartX + (i * (barWidth + barGap));
            float by = barsBaseY - barH;

            int bottomColor = ColorUtils.darken(themeColor, 0.12f);
            int topColor = ColorUtils.interpolateColor(themeColor, ColorUtils.rgba(255, 255, 255, 255), 0.65f);

            RenderUtils.drawGradientRect(matrices, bx, by, barWidth, barH, 0.8f,
                    ColorUtils.applyAlpha(topColor, alpha * 0.95f),
                    ColorUtils.applyAlpha(bottomColor, alpha * 0.85f));

            float peakY = barsBaseY - Math.max(barH + 1.0f, peaks[i] * maxBarH);
            RenderUtils.drawRoundedRect(matrices, bx, peakY, barWidth, 0.8f, 0.4f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.92f));
        }

        super.onRender(eventRender);
    }

    private void drawPlayIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float af = a / 255f;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        // Геометрически центрированный треугольник Play ▶
        buffer.vertex(matrix, cx - 1.5f, cy - 2.8f, 0).color(r, g, b, af);
        buffer.vertex(matrix, cx - 1.5f, cy + 2.8f, 0).color(r, g, b, af);
        buffer.vertex(matrix, cx + 2.5f, cy, 0).color(r, g, b, af);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    private void drawNextIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float af = a / 255f;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        // Треугольник >
        buffer.vertex(matrix, cx - 3.0f, cy - 2.8f, 0).color(r, g, b, af);
        buffer.vertex(matrix, cx - 3.0f, cy + 2.8f, 0).color(r, g, b, af);
        buffer.vertex(matrix, cx + 0.6f, cy, 0).color(r, g, b, af);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();

        // Вертикальная черточка |
        RenderUtils.drawRoundedRect(matrices, cx + 1.8f, cy - 2.8f, 1.2f, 5.6f, 0.4f, color);
    }

    private void drawPrevIcon(MatrixStack matrices, float cx, float cy, int color) {
        // Вертикальная черточка |
        RenderUtils.drawRoundedRect(matrices, cx - 3.0f, cy - 2.8f, 1.2f, 5.6f, 0.4f, color);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float af = a / 255f;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        // Треугольник <
        buffer.vertex(matrix, cx + 3.0f, cy - 2.8f, 0).color(r, g, b, af);
        buffer.vertex(matrix, cx + 3.0f, cy + 2.8f, 0).color(r, g, b, af);
        buffer.vertex(matrix, cx - 0.6f, cy, 0).color(r, g, b, af);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float x = draggable.getX();
        float y = draggable.getY();
        float ctrlCenterY = y + 26.0f;
        float playCx = x + CARD_WIDTH - 25.5f;
        float btnDist = 15.0f;
        float prevCx = playCx - btnDist;
        float nextCx = playCx + btnDist;

        // Клик по предыдущему треку (|<)
        if (HoveringUtils.isHovered(mouseX, mouseY, prevCx - 6.0f, ctrlCenterY - 6.0f, 12f, 12f)) {
            MediaTracker.getInstance().prevTrack();
            return true;
        }

        // Клик по кольцу Play/Pause
        if (HoveringUtils.isHovered(mouseX, mouseY, playCx - 8.0f, ctrlCenterY - 8.0f, 16f, 16f)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }

        // Клик по следующему треку (>|)
        if (HoveringUtils.isHovered(mouseX, mouseY, nextCx - 6.0f, ctrlCenterY - 6.0f, 12f, 12f)) {
            MediaTracker.getInstance().nextTrack();
            return true;
        }

        // Клик по обложке трека (Play/Pause)
        if (HoveringUtils.isHovered(mouseX, mouseY, x + 4.5f, y + 4.0f, COVER_SIZE, COVER_SIZE)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }

        return false;
    }
}
