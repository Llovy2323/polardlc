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

/**
 * MusicHud "Aurora" — переработанный компактный музыкальный виджет.
 *
 * Фичи:
 *  - стеклянная тёмная пилюля 142x42 с неоновым свечением цвета темы;
 *  - периодический световой sweep (диагональный блик) по карточке;
 *  - обложка с двойным кольцом прогресса и светящейся точкой на конце дуги;
 *  - винил с орбитальным маркером, если обложки нет (вращается при воспроизведении);
 *  - ping-pong бегущая строка названия (без дублирования текста, с паузами по краям);
 *  - капсульный эквалайзер (6 столбиков с glow-точками);
 *  - нижняя линия прогресса с светящейся головкой;
 *  - кнопки prev/next (chevron) плавно появляются при наведении на карточку;
 *  - play/pause кнопка с вращающимся пунктирным кольцом во время игры.
 */
public class MusicHud extends InterfaceProcessing {

    private static final float CARD_W = 142.0f;
    private static final float CARD_H = 42.0f;

    private static final float COVER = 30.0f;
    private static final float COVER_X = 6.0f;
    private static final float COVER_Y = (CARD_H - COVER) * 0.5f;

    private static final float TEXT_X = COVER_X + COVER + 6.0f; // 42
    private static final float CTRL_Y = CARD_H - 10.5f;
    private static final float PLAY_CX = CARD_W - 22.0f;
    private static final float PREV_CX = PLAY_CX - 13.0f;
    private static final float NEXT_CX = PLAY_CX + 13.0f;

    private static final int EQ_BARS = 6;

    // Анимации
    private final AnimationUtils alphaAnim = new AnimationUtils(0.0f, 8.5f, Easings.QUAD_OUT);
    private final AnimationUtils progressAnim = new AnimationUtils(0.0f, 6.0f, Easings.QUAD_OUT);
    private final AnimationUtils playingAnim = new AnimationUtils(0.0f, 7.0f, Easings.QUAD_OUT);
    private final AnimationUtils cardHoverAnim = new AnimationUtils(0.0f, 10.0f, Easings.QUAD_OUT);
    private final AnimationUtils playHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils prevHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils nextHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils coverHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);

    // Состояние
    private float discRotation;
    private float ringSpin;
    private float marqueePhase;
    private long lastFrameNs = System.nanoTime();
    private String lastTrackKey = "";

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

        draggable.setWidth(CARD_W);
        draggable.setHeight(CARD_H);

        MediaTracker tracker = MediaTracker.getInstance();
        tracker.updateVisualizer();
        MediaTrack track = tracker.getCurrentTrack();

        boolean inChat = mc.currentScreen instanceof ChatScreen;
        boolean hasTrack = track != null && (!track.getRawTitle().isEmpty() || track.isPlaying());
        boolean visible = hasTrack || inChat;

        alphaAnim.update(visible ? 1.0f : 0.0f);
        float alpha = alphaAnim.getValue();
        if (alpha <= 0.01f) return;

        // ---- дельта времени для независимых от FPS анимаций ----
        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastFrameNs) / 1_000_000_000.0f, 0.0f, 0.05f);
        lastFrameNs = now;

        boolean isPlaying = track != null && track.isPlaying();
        playingAnim.update(isPlaying ? 1.0f : 0.0f);
        float playing = playingAnim.getValue();

        if (isPlaying) {
            discRotation = (discRotation + dt * 95.0f) % 360.0f;
            ringSpin = (ringSpin + dt * 60.0f) % 360.0f;
        }

        // ---- данные трека ----
        String title = "Нет трека";
        String artist = "Включите музыку";
        boolean hasCover = false;

        if (track != null && !track.getRawTitle().isEmpty()) {
            title = track.getRawTitle();
            artist = track.getArtist();
            hasCover = track.hasCover();
        } else if (inChat) {
            title = "Music Player";
            artist = "Ожидание трека...";
        }

        String trackKey = title + "|" + artist;
        if (!trackKey.equals(lastTrackKey)) {
            lastTrackKey = trackKey;
            marqueePhase = 0.0f;
        }

        // ---- мышь / hover ----
        double mx = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double my = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        boolean inScreen = mc.currentScreen != null;

        boolean cardHovered = inScreen && HoveringUtils.isHovered(mx, my, x, y, CARD_W, CARD_H);
        boolean coverHovered = inScreen && HoveringUtils.isHovered(mx, my, x + COVER_X, y + COVER_Y, COVER, COVER);
        boolean playHovered = inScreen && HoveringUtils.isHovered(mx, my, x + PLAY_CX - 8, y + CTRL_Y - 8, 16, 16);
        boolean prevHovered = inScreen && HoveringUtils.isHovered(mx, my, x + PREV_CX - 6, y + CTRL_Y - 6, 12, 12);
        boolean nextHovered = inScreen && HoveringUtils.isHovered(mx, my, x + NEXT_CX - 6, y + CTRL_Y - 6, 12, 12);

        cardHoverAnim.update(cardHovered ? 1.0f : 0.0f);
        coverHoverAnim.update(coverHovered ? 1.0f : 0.0f);
        playHoverAnim.update(playHovered ? 1.0f : 0.0f);
        prevHoverAnim.update(prevHovered ? 1.0f : 0.0f);
        nextHoverAnim.update(nextHovered ? 1.0f : 0.0f);

        // ---- цвета ----
        int theme = ColorUtils.getThemeColor();
        int themeSoft = ColorUtils.darken(theme, 0.35f);
        int textColor = ColorUtils.applyAlpha(ColorUtils.rgba(246, 247, 252, 255), alpha);
        int subColor = ColorUtils.applyAlpha(ColorUtils.rgba(150, 155, 172, 255), alpha);

        float pulse = 0.45f + 0.55f * (float) ((Math.sin(System.currentTimeMillis() * 0.0058) + 1.0) * 0.5);
        pulse = 0.35f + pulse * 0.65f * playing;

        // ================= КАРТОЧКА =================
        drawCardBase(matrices, x, y, theme, themeSoft, alpha, pulse);

        // ================= ОБЛОЖКА / ВИНИЛ =================
        float coverCx = x + COVER_X + COVER * 0.5f;
        float coverCy = y + COVER_Y + COVER * 0.5f;
        drawCoverOrVinyl(matrices, track, coverCx, coverCy, hasCover, isPlaying, theme, alpha);
        drawCoverProgressRing(matrices, track, coverCx, coverCy, theme, alpha);
        drawCoverHoverOverlay(matrices, coverCx, coverCy, isPlaying, alpha);

        // ================= ТЕКСТ =================
        Font titleFont = font(11);
        Font artistFont = font(9);
        float textRight = x + CARD_W - 9.0f;
        float maxTextW = Math.max(20.0f, textRight - (x + TEXT_X));

        drawMarquee(titleFont, matrices, title, x + TEXT_X, y + 5.0f, maxTextW, textColor, dt, isPlaying);
        drawArtistLine(artistFont, matrices, artist, x + TEXT_X, y + 15.6f, maxTextW, subColor, theme, alpha, isPlaying);

        // ================= ЭКВАЛАЙЗЕР (капсулы) =================
        float eqRight = x + PREV_CX - 12.0f;
        drawEqualizer(matrices, tracker, x + TEXT_X, y + CTRL_Y, eqRight, theme, alpha, isPlaying);

        // ================= КНОПКИ =================
        // prev/next проявляются при наведении на карточку
        float navAlpha = alpha * cardHoverAnim.getValue();
        if (navAlpha > 0.02f) {
            int prevColor = ColorUtils.interpolateColor(subColor, theme, prevHoverAnim.getValue());
            int nextColor = ColorUtils.interpolateColor(subColor, theme, nextHoverAnim.getValue());
            drawChevron(matrices, x + PREV_CX, y + CTRL_Y, false, ColorUtils.applyAlpha(prevColor, navAlpha));
            drawChevron(matrices, x + NEXT_CX, y + CTRL_Y, true, ColorUtils.applyAlpha(nextColor, navAlpha));
        }

        drawPlayButton(matrices, x + PLAY_CX, y + CTRL_Y, isPlaying, theme, alpha);

        // ================= НИЖНЯЯ ЛИНИЯ ПРОГРЕССА =================
        drawBottomProgress(matrices, track, x, y, theme, alpha);

        super.onRender(eventRender);
    }

    // ------------------------------------------------------------------
    // Карточка
    // ------------------------------------------------------------------

    private void drawCardBase(MatrixStack matrices, float x, float y, int theme, int themeSoft, float alpha, float pulse) {
        // неоновое свечение вокруг карточки
        RenderUtils.drawShadow(matrices, x - 1.5f, y - 1.0f, CARD_W + 3.0f, CARD_H + 3.0f, 10.0f, 15.0f,
                ColorUtils.applyAlpha(theme, alpha * 0.16f * pulse));

        // основная подложка: почти чёрная с синеватым оттенком
        RenderUtils.drawRoundedRect(matrices, x, y, CARD_W, CARD_H, 10.0f,
                ColorUtils.rgba(7, 8, 13, (int) (232 * alpha)));

        // лёгкий вертикальный глянец
        RenderUtils.drawGradientRect(matrices, x, y, CARD_W, CARD_H, 10.0f,
                ColorUtils.rgba(26, 28, 40, (int) (54 * alpha)),
                ColorUtils.rgba(8, 9, 14, (int) (14 * alpha)));

        // обводка: усиливается при наведении
        float hover = cardHoverAnim.getValue();
        int border = ColorUtils.interpolateColor(
                ColorUtils.rgba(255, 255, 255, 14),
                theme,
                hover * 0.55f
        );
        RenderUtils.drawRoundedRectOutline(matrices, x, y, CARD_W, CARD_H, 10.0f, 0.9f,
                ColorUtils.applyAlpha(border, alpha));

        // неоновая вставка сверху слева (акцент)
        RenderUtils.drawRoundedRect(matrices, x + 12.0f, y, 44.0f, 1.0f, 0.5f,
                ColorUtils.applyAlpha(theme, alpha * 0.85f));

        // тусклый отблеск снизу справа
        RenderUtils.drawRoundedRect(matrices, x + CARD_W - 56.0f, y + CARD_H - 1.0f, 44.0f, 1.0f, 0.5f,
                ColorUtils.applyAlpha(themeSoft, alpha * 0.45f));
    }

    /** Периодический диагональный блик, пробегающий по карточке. */
    private void drawLightSweep(MatrixStack matrices, float x, float y, int theme, float alpha, float playing) {
        float period = 4600.0f;
        float t = (System.currentTimeMillis() % (long) period) / period;
        if (t > 0.42f) return; // sweep идёт только первые ~42% цикла

        float p = t / 0.42f;
        p = p * p * (3.0f - 2.0f * p); // smoothstep

        float bandW = 24.0f;
        float sx = x - bandW + p * (CARD_W + bandW * 2.0f);
        float sweepAlpha = alpha * (0.10f + 0.08f * playing);

        int c0 = ColorUtils.applyAlpha(theme, 0.0f);
        int c1 = ColorUtils.applyAlpha(ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), 0.5f), sweepAlpha);

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x + 1.0, y + 1.0, CARD_W - 2.0, CARD_H - 2.0);
        try {
            matrices.push();
            matrices.translate(sx, y + CARD_H * 0.5f, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(16.0f));
            // левая половина блика: прозрачный -> свет
            RenderUtils.drawGradientRect(matrices, -bandW * 0.5f, -CARD_H, bandW * 0.5f, CARD_H * 2.0f, 0.0f, c0, c1, true);
            // правая половина: свет -> прозрачный
            RenderUtils.drawGradientRect(matrices, 0.0f, -CARD_H, bandW * 0.5f, CARD_H * 2.0f, 0.0f, c1, c0, true);
            matrices.pop();
        } finally {
            ScissorUtils.unset();
            ScissorUtils.pop();
        }
    }

    // ------------------------------------------------------------------
    // Обложка / винил / кольцо прогресса
    // ------------------------------------------------------------------

    private void drawCoverOrVinyl(MatrixStack matrices, MediaTrack track, float cx, float cy,
                                  boolean hasCover, boolean isPlaying, int theme, float alpha) {
        // мягкое свечение под обложкой
        RenderUtils.drawShadow(matrices, cx - COVER * 0.5f, cy - COVER * 0.5f, COVER, COVER, 8.0f, 9.0f,
                ColorUtils.applyAlpha(theme, alpha * (0.18f + 0.22f * playingAnim.getValue())));

        if (hasCover && track != null) {
            RenderUtils.drawRoundedImage(matrices, track.getCoverTexture(),
                    cx - COVER * 0.5f, cy - COVER * 0.5f, COVER, 8.0f, alpha);
            // лёгкий глянец на обложке
            RenderUtils.drawGradientRect(matrices, cx - COVER * 0.5f, cy - COVER * 0.5f, COVER, COVER * 0.45f, 8.0f,
                    ColorUtils.rgba(255, 255, 255, (int) (26 * alpha)),
                    ColorUtils.rgba(255, 255, 255, 0));
        } else {
            drawVinyl(matrices, cx, cy, COVER * 0.5f, theme, alpha, isPlaying);
        }
    }

    /** Винил: дорожки + этикетка + орбитальный маркер, чтобы вращение было видно. */
    private void drawVinyl(MatrixStack matrices, float cx, float cy, float r, int theme, float alpha, boolean isPlaying) {
        // drawRoundCircle принимает ДИАМЕТР
        RenderUtils.drawRoundCircle(matrices, cx, cy, r * 2.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(9, 9, 13, 255), alpha));
        RenderUtils.drawRoundCircle(matrices, cx, cy, r * 2.0f - 2.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(24, 24, 32, 255), alpha));

        // дорожки
        for (int i = 0; i < 3; i++) {
            float groove = r - 2.4f - i * 2.4f;
            if (groove < 5.0f) break;
            RenderUtils.drawRoundCircle(matrices, cx, cy, groove * 2.0f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(36 + i * 6, 36, 46, 255), alpha));
            RenderUtils.drawRoundCircle(matrices, cx, cy, groove * 2.0f - 1.6f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(14, 14, 20, 255), alpha));
        }

        // этикетка и шпиндель
        RenderUtils.drawRoundCircle(matrices, cx, cy, 10.0f, ColorUtils.applyAlpha(theme, alpha));
        RenderUtils.drawRoundCircle(matrices, cx, cy, 3.6f,
                ColorUtils.applyAlpha(ColorUtils.rgba(8, 8, 12, 255), alpha));

        // орбитальный маркер (видно вращение)
        float orbitR = r - 4.6f;
        double rad = Math.toRadians(discRotation);
        float dotX = cx + (float) Math.cos(rad) * orbitR;
        float dotY = cy + (float) Math.sin(rad) * orbitR;
        RenderUtils.drawRoundCircle(matrices, dotX, dotY, 2.2f,
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * (0.35f + 0.45f * playingAnim.getValue())));

        // глянцевая арка-блик
        RenderUtils.drawRingArc(matrices, cx - r, cy - r, r * 2.0f, 1.2f, -50.0f, 22.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.28f));
    }

    /** Двойное кольцо вокруг обложки: тонкая база + дуга прогресса с точкой на конце. */
    private void drawCoverProgressRing(MatrixStack matrices, MediaTrack track, float cx, float cy, int theme, float alpha) {
        float size = COVER + 3.8f; // диаметр квадрата арки
        float rx = cx - size * 0.5f;
        float ry = cy - size * 0.5f;
        float thickness = 1.6f;
        float radius = size * 0.5f;

        RenderUtils.drawRingArc(matrices, rx, ry, size, 0.9f, 0.0f, 360.0f,
                ColorUtils.rgba(255, 255, 255, (int) (16 * alpha)));

        float target = track != null ? track.getProgress() : 0.0f;
        progressAnim.update(target);
        float prog = MathHelper.clamp(progressAnim.getValue(), 0.0f, 1.0f);

        float arc;
        float startAngle = -90.0f;
        if (track != null && track.getDurationMs() > 0) {
            arc = Math.max(6.0f, prog * 360.0f);
        } else if (track != null && track.isPlaying()) {
            startAngle = ringSpin;
            arc = 90.0f;
        } else {
            return;
        }

        RenderUtils.drawRingArc(matrices, rx, ry, size, thickness, startAngle, startAngle + arc,
                ColorUtils.applyAlpha(theme, alpha * 0.95f));

        // светящаяся точка на конце дуги
        double tipRad = Math.toRadians(startAngle + arc);
        float midR = radius - thickness * 0.5f;
        float tipX = cx + (float) Math.cos(tipRad) * midR;
        float tipY = cy + (float) Math.sin(tipRad) * midR;
        RenderUtils.drawRoundCircle(matrices, tipX, tipY, 4.2f,
                ColorUtils.applyAlpha(theme, alpha * 0.35f));
        RenderUtils.drawRoundCircle(matrices, tipX, tipY, 2.4f,
                ColorUtils.applyAlpha(ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), 0.6f), alpha));
    }

    /** Затемнение + иконка Play/Pause поверх обложки при наведении. */
    private void drawCoverHoverOverlay(MatrixStack matrices, float cx, float cy, boolean isPlaying, float alpha) {
        float hover = coverHoverAnim.getValue();
        if (hover <= 0.02f) return;

        RenderUtils.drawRoundCircle(matrices, cx, cy, COVER,
                ColorUtils.rgba(5, 6, 10, (int) (150 * alpha * hover)));

        int icon = ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * hover);
        if (isPlaying) {
            RenderUtils.drawRoundedRect(matrices, cx - 2.2f, cy - 3.2f, 1.5f, 6.4f, 0.5f, icon);
            RenderUtils.drawRoundedRect(matrices, cx + 0.7f, cy - 3.2f, 1.5f, 6.4f, 0.5f, icon);
        } else {
            drawPlayIcon(matrices, cx + 0.3f, cy, icon);
        }
    }

    // ------------------------------------------------------------------
    // Текст
    // ------------------------------------------------------------------

    /** Ping-pong marquee: строка плавно скроллится туда-обратно с паузами по краям. */
    private void drawMarquee(Font font, MatrixStack matrices, String text, float x, float y,
                             float maxW, int color, float dt, boolean isPlaying) {
        if (font == null || text == null || text.isEmpty()) return;

        float width = font.getWidth(text);
        float overflow = width - maxW;
        if (overflow <= 0.5f) {
            font.draw(matrices, text, x, y, color);
            return;
        }

        marqueePhase += dt * (isPlaying ? 1.1f : 0.55f);
        float offset = overflow * (0.5f - 0.5f * (float) Math.cos(marqueePhase));

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x, y - 2.0, maxW, 11.0);
        try {
            font.draw(matrices, text, x - offset, y, color);
        } finally {
            ScissorUtils.unset();
            ScissorUtils.pop();
        }
    }

    /** Строка исполнителя с пульсирующим live-индикатором. */
    private void drawArtistLine(Font font, MatrixStack matrices, String artist, float x, float y,
                                float maxW, int subColor, int theme, float alpha, boolean isPlaying) {
        float dotPulse = isPlaying
                ? 0.55f + 0.45f * (float) ((Math.sin(System.currentTimeMillis() * 0.008) + 1.0) * 0.5)
                : 0.3f;
        int dotColor = isPlaying
                ? ColorUtils.applyAlpha(theme, alpha * dotPulse)
                : ColorUtils.applyAlpha(ColorUtils.rgba(110, 114, 130, 255), alpha * 0.7f);

        RenderUtils.drawRoundCircle(matrices, x + 2.0f, y + 4.2f, 4.4f,
                ColorUtils.applyAlpha(theme, alpha * dotPulse * 0.30f));
        RenderUtils.drawRoundCircle(matrices, x + 2.0f, y + 4.2f, 2.6f, dotColor);

        if (font != null) {
            font.draw(matrices, ellipsize(font, artist, maxW - 8.0f), x + 7.0f, y, subColor);
        }
    }

    private String ellipsize(Font font, String text, float maxW) {
        if (font == null || text == null) return "";
        if (font.getWidth(text) <= maxW) return text;
        String value = text;
        while (value.length() > 1 && font.getWidth(value + "...") > maxW) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    // ------------------------------------------------------------------
    // Эквалайзер (капсульные столбики с glow-точками)
    // ------------------------------------------------------------------

    private void drawEqualizer(MatrixStack matrices, MediaTracker tracker, float startX, float baseY,
                               float maxRight, int theme, float alpha, boolean isPlaying) {
        float[] bars = tracker.getVisualizerBars();
        if (bars == null || bars.length == 0) return;

        float barW = 2.3f;
        float gap = 2.6f;
        float totalW = EQ_BARS * barW + (EQ_BARS - 1) * gap;
        if (startX + totalW > maxRight) {
            barW = Math.max(1.4f, (maxRight - startX - (EQ_BARS - 1) * gap) / EQ_BARS);
            totalW = EQ_BARS * barW + (EQ_BARS - 1) * gap;
        }

        float maxH = 9.0f;
        long time = System.currentTimeMillis();

        for (int i = 0; i < EQ_BARS; i++) {
            float src = bars[Math.min(i * 2, bars.length - 1)];
            float breathe = isPlaying ? 0.10f * (float) Math.sin(time * 0.011 + i * 0.9) : 0.0f;
            float value = MathHelper.clamp(src + breathe, 0.10f, 1.0f);
            float h = Math.max(1.6f, value * maxH);

            float bx = startX + i * (barW + gap);
            float by = baseY + maxH * 0.5f - h; // выравнивание по нижней зоне
            float bottom = baseY + maxH * 0.5f;

            // glow-подложка
            RenderUtils.drawRoundedRect(matrices, bx - 0.7f, bottom - h - 0.7f, barW + 1.4f, h + 1.4f,
                    (barW + 1.4f) * 0.5f,
                    ColorUtils.applyAlpha(theme, alpha * 0.14f * value));

            // капсула
            int top = ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), 0.55f);
            int bottomC = ColorUtils.darken(theme, 0.22f);
            RenderUtils.drawGradientRect(matrices, bx, bottom - h, barW, h, barW * 0.5f,
                    ColorUtils.applyAlpha(top, alpha * 0.95f),
                    ColorUtils.applyAlpha(bottomC, alpha * 0.8f));

            // светящаяся точка на вершине
            RenderUtils.drawRoundCircle(matrices, bx + barW * 0.5f, bottom - h, barW * 1.1f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.5f * value));
        }
    }

    // ------------------------------------------------------------------
    // Кнопки управления
    // ------------------------------------------------------------------

    /** Chevron ‹ / › из двух скруглённых планок. */
    private void drawChevron(MatrixStack matrices, float cx, float cy, boolean right, int color) {
        float angle = 36.87f;
        float len = 5.0f;
        float thick = 1.3f;
        if (right) {
            drawArm(matrices, cx, cy - 1.4f, angle, len, thick, color);
            drawArm(matrices, cx, cy + 1.4f, -angle, len, thick, color);
        } else {
            drawArm(matrices, cx, cy - 1.4f, -angle, len, thick, color);
            drawArm(matrices, cx, cy + 1.4f, angle, len, thick, color);
        }
    }

    private void drawArm(MatrixStack matrices, float cx, float cy, float angleDeg, float len, float thick, int color) {
        matrices.push();
        matrices.translate(cx, cy, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angleDeg));
        RenderUtils.drawRoundedRect(matrices, -len * 0.5f, -thick * 0.5f, len, thick, thick * 0.5f, color);
        matrices.pop();
    }

    /** Круглая play/pause кнопка с вращающимся пунктирным кольцом во время игры. */
    private void drawPlayButton(MatrixStack matrices, float cx, float cy, boolean isPlaying, int theme, float alpha) {
        float hover = playHoverAnim.getValue();
        float playing = playingAnim.getValue();

        // свечение
        RenderUtils.drawShadow(matrices, cx - 7.0f, cy - 7.0f, 14.0f, 14.0f, 7.0f, 7.0f,
                ColorUtils.applyAlpha(theme, alpha * (0.18f + 0.25f * hover + 0.15f * playing)));

        // пунктирное кольцо, вращается при воспроизведении
        float ringSize = 14.6f;
        float rx = cx - ringSize * 0.5f;
        float ry = cy - ringSize * 0.5f;
        float ringAlpha = alpha * (0.35f + 0.4f * hover + 0.25f * playing);
        int ringColor = ColorUtils.applyAlpha(theme, ringAlpha);
        for (int i = 0; i < 3; i++) {
            float start = ringSpin + i * 120.0f;
            RenderUtils.drawRingArc(matrices, rx, ry, ringSize, 1.1f, start, start + 72.0f, ringColor);
        }

        // ядро кнопки
        int core = ColorUtils.interpolateColor(
                ColorUtils.rgba(16, 17, 26, 255),
                ColorUtils.rgba(38, 40, 58, 255),
                hover
        );
        RenderUtils.drawRoundCircle(matrices, cx, cy, 10.6f, ColorUtils.applyAlpha(core, alpha * 0.96f));

        int icon = ColorUtils.applyAlpha(
                ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), hover), alpha);
        if (isPlaying) {
            RenderUtils.drawRoundedRect(matrices, cx - 1.9f, cy - 2.6f, 1.3f, 5.2f, 0.45f, icon);
            RenderUtils.drawRoundedRect(matrices, cx + 0.6f, cy - 2.6f, 1.3f, 5.2f, 0.45f, icon);
        } else {
            drawPlayIcon(matrices, cx + 0.2f, cy, icon);
        }
    }

    /** Тонкая линия прогресса вдоль нижней кромки с светящейся головкой. */
    private void drawBottomProgress(MatrixStack matrices, MediaTrack track, float x, float y, int theme, float alpha) {
        float lineX = x + 8.0f;
        float lineW = CARD_W - 16.0f;
        float lineY = y + CARD_H - 2.6f;

        RenderUtils.drawRoundedRect(matrices, lineX, lineY, lineW, 1.2f, 0.6f,
                ColorUtils.rgba(255, 255, 255, (int) (14 * alpha)));

        if (track == null || track.getDurationMs() <= 0) return;

        float prog = MathHelper.clamp(progressAnim.getValue(), 0.0f, 1.0f);
        if (prog <= 0.003f) return;

        float fillW = lineW * prog;
        int headColor = ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), 0.5f);
        RenderUtils.drawGradientRect(matrices, lineX, lineY, fillW, 1.2f, 0.6f,
                ColorUtils.applyAlpha(ColorUtils.darken(theme, 0.15f), alpha * 0.9f),
                ColorUtils.applyAlpha(headColor, alpha),
                true);

        // светящаяся головка
        float headX = lineX + fillW;
        RenderUtils.drawRoundCircle(matrices, headX, lineY + 0.6f, 4.6f,
                ColorUtils.applyAlpha(theme, alpha * 0.30f));
        RenderUtils.drawRoundCircle(matrices, headX, lineY + 0.6f, 2.2f,
                ColorUtils.applyAlpha(headColor, alpha));
    }

    // ------------------------------------------------------------------
    // Треугольные иконки
    // ------------------------------------------------------------------

    private void drawPlayIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float[] rgba = unpack(color);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, cx - 1.5f, cy - 2.7f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx - 1.5f, cy + 2.7f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx + 2.5f, cy, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
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

    // ------------------------------------------------------------------
    // Клики
    // ------------------------------------------------------------------

    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float x = draggable.getX();
        float y = draggable.getY();

        if (HoveringUtils.isHovered(mouseX, mouseY, x + PLAY_CX - 8, y + CTRL_Y - 8, 16, 16)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }
        if (cardHoverAnim.getValue() > 0.3f) {
            if (HoveringUtils.isHovered(mouseX, mouseY, x + PREV_CX - 6, y + CTRL_Y - 6, 12, 12)) {
                MediaTracker.getInstance().prevTrack();
                return true;
            }
            if (HoveringUtils.isHovered(mouseX, mouseY, x + NEXT_CX - 6, y + CTRL_Y - 6, 12, 12)) {
                MediaTracker.getInstance().nextTrack();
                return true;
            }
        }
        if (HoveringUtils.isHovered(mouseX, mouseY, x + COVER_X, y + COVER_Y, COVER, COVER)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }
        return false;
    }
}
