package snill.client.client.ui.mainmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.ui.altmanager.AltManagerScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SNILL / Nocturne — Minecraft 1.21.4, Fabric, Yarn, Java 21.
 * Clean, modern dark glass dashboard with player profile and action strips.
 * Features buttery smooth Hermite screen fade transitions on button click and exit,
 * smooth 14f/22f hover and press physical spring physics, and anti-aliased glowing borders.
 */
public final class MainMenuRenderer extends Screen {
    private static final int TEXT = 0xFFF0F2F8;
    private static final int MUTED = 0xFF9CA6BA;
    private static final int DIM = 0xFF858FAA;
    private static final int ACCENT = 0xFFA8BEFF;
    private static final int CYAN = 0xFF87D5DB;
    private static final int DANGER = 0xFFF0A0AE;
    private static final float RADIUS = 12f;
    private static final float BLUR_STRENGTH = 5f;
    private static final double TAU = Math.PI * 2.0;
    private static final long FADE_NS = 170_000_000L;
    // Uptime since this menu was first loaded; preserved across new Screen instances.
    private static final long SESSION_STARTED_AT = System.nanoTime();

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<MenuButton> buttons = new ArrayList<>(5);
    private final Particle[] particles = new Particle[32];
    private Font bold;
    private Font regular;
    private Font small;
    private float scale = 1f;
    private float logicalWidth, logicalHeight, originX, originY;
    private float profileX, profileY, profileW, profileH, navX, navY, navW;
    private float parallaxX, parallaxY;
    private float backgroundTime;
    private boolean compact;
    private boolean reducedMotion;
    private boolean initialized;
    private boolean keyboardMode;
    private int focus = -1;
    private double previousMouseX = Double.NaN;
    private double previousMouseY = Double.NaN;
    private long lastFrame;
    private long enteredAt;
    private long actionAt;
    private long lastSessionSecond = -1;
    private String username = "";
    private String displayName = "";
    private String sessionTime = "00:00:00";
    private MenuButton held;
    private Runnable pendingAction;

    public MainMenuRenderer() {
        super(Text.literal("SNILL — Main Menu"));
        Random random = new Random(0x534E494C4CL);
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new Particle(random.nextFloat(), random.nextFloat(),
                    0.45f + random.nextFloat() * 0.75f,
                    0.7f + random.nextFloat() * 1.3f,
                    random.nextFloat() * (float) TAU);
        }
    }

    private void loadFonts() {
        if (bold == null) {
            bold = Fonts.getFont("semibold", 24);
            if (bold == null) bold = Fonts.getFont("sf_bold", 24);
        }
        if (regular == null) {
            regular = Fonts.getFont("suisse", 14);
            if (regular == null) regular = Fonts.getFont("sf_regular", 14);
        }
        if (small == null) {
            small = Fonts.getFont("suisse", 11);
            if (small == null) small = Fonts.getFont("sf_regular", 11);
        }
    }

    @Override
    protected void init() {
        RenderUtils.cleanupLiquidBlur();
        loadFonts();
        if (buttons.isEmpty()) {
            buttons.add(new MenuButton("Singleplayer", "Local worlds", 0,
                    () -> mc.setScreen(new SelectWorldScreen(this))));
            buttons.add(new MenuButton("Multiplayer", "Servers & friends", 1,
                    () -> mc.setScreen(new MultiplayerScreen(this))));
            buttons.add(new MenuButton("Alt Manager", "Account management", 2,
                    () -> mc.setScreen(new AltManagerScreen(this))));
            buttons.add(new MenuButton("Options", "", 3,
                    () -> mc.setScreen(new OptionsScreen(this, mc.options))));
            buttons.add(new MenuButton("Quit", "", 4, mc::scheduleStop));
        }
        buttons.get(1).enabled = mc.isMultiplayerEnabled();
        pendingAction = null;
        held = null;
        for (MenuButton button : buttons) {
            button.hover = 0f;
            button.press = 0f;
        }
        focus = -1;
        keyboardMode = false;
        previousMouseX = previousMouseY = Double.NaN;
        lastFrame = System.nanoTime();
        if (!initialized) enteredAt = lastFrame;
        initialized = true;
        layout();
        updateProfile(lastFrame);
    }

    private void layout() {
        compact = width < height * 1.30f;
        float designWidth = compact ? 420f : 900f;
        float designHeight = compact ? 660f : 560f;
        scale = Math.max(0.001f, Math.min(1f,
                Math.min(width / designWidth, height / designHeight)));
        logicalWidth = width / scale;
        logicalHeight = height / scale;
        originX = (logicalWidth - designWidth) * 0.5f;
        originY = (logicalHeight - designHeight) * 0.5f;
        profileX = originX + (compact ? 34f : 138f);
        profileY = originY + (compact ? 116f : 148f);
        profileW = compact ? 352f : 220f;
        profileH = compact ? 128f : 282f;
        navX = originX + (compact ? 34f : 382f);
        navY = originY + (compact ? 282f : 148f);
        navW = compact ? 352f : 380f;
        for (int i = 0; i < buttons.size(); i++) {
            MenuButton b = buttons.get(i);
            float half = (navW - 12f) * 0.5f;
            b.x = navX + (i == 4 ? half + 12f : 0f);
            b.y = navY + (i < 3 ? i * 78f : 234f);
            b.w = i < 3 ? navW : half;
            b.h = i < 3 ? 66f : 48f;
            b.updateBounds();
        }
        if (regular != null) displayName = fit(regular, username, profileW - 98f);
    }

    private void updateProfile(long now) {
        String current = mc.getSession().getUsername();
        if (!current.equals(username)) {
            username = current;
            if (regular != null) displayName = fit(regular, username, profileW - 98f);
        }
        long second = Math.max(0, (now - SESSION_STARTED_AT) / 1_000_000_000L);
        if (second != lastSessionSecond) {
            lastSessionSecond = second;
            sessionTime = twoDigits(second / 3600) + ":"
                    + twoDigits(second / 60 % 60) + ":" + twoDigits(second % 60);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (bold == null || regular == null || small == null) {
            loadFonts();
            if (regular != null && (displayName == null || displayName.isEmpty())) {
                displayName = fit(regular, username, profileW - 98f);
            }
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0f :
                Math.max(0f, Math.min(0.05f, (now - lastFrame) / 1_000_000_000f));
        lastFrame = now;
        updateProfile(now);

        float mx = (float) (mouseX / scale);
        float my = (float) (mouseY / scale);

        if (!Double.isNaN(previousMouseX)
                && (Math.abs(mouseX - previousMouseX) > 0.5
                || Math.abs(mouseY - previousMouseY) > 0.5)) {
            keyboardMode = false;
        }
        previousMouseX = mouseX;
        previousMouseY = mouseY;

        buttons.get(1).enabled = mc.isMultiplayerEnabled();
        if (held != null && !held.enabled) held = null;
        if (!reducedMotion) backgroundTime += dt;

        parallaxX = approach(parallaxX, reducedMotion ? 0f :
                (clamp(mx / Math.max(1f, logicalWidth)) - 0.5f) * 22f, 4.5f, dt);
        parallaxY = approach(parallaxY, reducedMotion ? 0f :
                (clamp(my / Math.max(1f, logicalHeight)) - 0.5f) * 16f, 4.5f, dt);

        for (MenuButton b : buttons) {
            // Hover is strictly physical mouse geometry — keyboard never alters hover or causes button to re-ignite
            boolean isMouseOver = b.enabled && b.containsBase(mx, my);
            b.hover = approach(b.hover, isMouseOver ? 1f : 0f, 14f, dt);
            b.press = approach(b.press, held == b && isMouseOver ? 1f : 0f, 22f, dt);
            b.updateBounds();
        }

        MatrixStack matrices = context.getMatrices();
        context.draw();
        matrices.push();
        try {
            matrices.scale(scale, scale, 1f);
            // Background mesh pass
            context.draw(consumers -> {
                Mesh g = mesh(consumers.getBuffer(RenderLayer.getGui()), matrices);
                background(g);
                panelLighting(g);
            });
            context.draw();

            profileSurface(matrices);
            for (int i = 0; i < buttons.size(); i++) {
                buttonSurface(matrices, buttons.get(i), keyboardMode && focus == i);
            }

            context.draw();
            context.draw(consumers -> {
                Mesh g = mesh(consumers.getBuffer(RenderLayer.getGui()), matrices);
                details(g);
            });
            context.draw();

            RenderUtils.drawPlayerHead(matrices, username,
                    profileX + 18f, profileY + 40f, 44f, 10f);
            labels(matrices);
            for (MenuButton b : buttons) buttonLabel(matrices, b);
            context.draw();
        } finally {
            matrices.pop();
        }

        float enter = reducedMotion ? 1f : clamp((now - enteredAt) / 330_000_000f);
        float leave = pendingAction == null ? 0f : clamp((now - actionAt) / (float) FADE_NS);
        float fade = Math.max((1f - enter) * (1f - enter), smoothstep(leave));
        if (fade > 0.002f) {
            context.fill(0, 0, width, height, alpha(0xFF060810, fade));
            context.draw();
        }

        if (pendingAction != null && (reducedMotion || now - actionAt >= FADE_NS)) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }
    }

    private Mesh mesh(VertexConsumer vertices, MatrixStack matrices) {
        float physicalScale = scale * (float) mc.getWindow().getScaleFactor();
        return new Mesh(vertices, matrices.peek().getPositionMatrix(),
                0.7f / Math.max(0.5f, physicalScale));
    }

    private void background(Mesh g) {
        float t = backgroundTime;
        g.rect(0f, 0f, logicalWidth, logicalHeight, 0xFF07090E, 0xFF0D1019);
        g.glow(logicalWidth * 0.25f + parallaxX, logicalHeight * 0.30f + parallaxY,
                440f, 340f, 0x283B526F);
        g.glow(logicalWidth * 0.80f - parallaxX, logicalHeight * 0.67f - parallaxY,
                430f, 320f, 0x30424B85);

        // Grid lines behind frosted glass
        float gx = parallaxX * 0.65f;
        float gy = parallaxY * 0.65f;
        for (float x = -48f; x < logicalWidth + 48f; x += 48f)
            g.line(x + gx, 0f, x + gx, logicalHeight, 0.6f, 0x075A7194);
        for (float y = -48f; y < logicalHeight + 48f; y += 48f)
            g.line(0f, y + gy, logicalWidth, y + gy, 0.6f, 0x075A7194);

        for (int wave = 0; wave < 5; wave++) {
            float px = -12f, py = waveY(px, wave, t);
            for (float x = 0f; x <= logicalWidth + 12f; x += 12f) {
                float y = waveY(x, wave, t);
                g.line(px, py, x, y, 6f, 0x034F70A0);
                g.line(px, py, x, y, 0.85f, wave == 2 ? 0x344C7195 : 0x184C7195);
                px = x; py = y;
            }
        }
        for (Particle p : particles) {
            float x = fract(p.x + t * 0.0009f) * logicalWidth + parallaxX * p.size;
            float y = fract(p.y - t * p.speed / 1600f) * logicalHeight + parallaxY * 0.4f;
            float opacity = 0.13f + 0.19f * (float) (0.5 + 0.5 * Math.sin(t * 0.6 + p.phase));
            float size = p.size * 1.4f;
            g.round(x, y, size, size, size / 2f, alpha(ACCENT, opacity), alpha(ACCENT, opacity));
        }
    }

    private float waveY(float x, int wave, float t) {
        return logicalHeight * 0.55f + wave * 22f + parallaxY
                + (float) Math.sin(x * 0.0055 + t * 0.15 + wave * 0.18) * 75f
                + (float) Math.cos(x * 0.011 - t * 0.11) * 14f;
    }

    private void panelLighting(Mesh g) {
        for (int i = 4; i >= 1; i--) {
            float s = i * 3f;
            g.round(profileX - s, profileY - s + 6f, profileW + s * 2f, profileH + s * 2f,
                    RADIUS + 2f + s, 0x08000000, 0x08000000);
        }
        for (int i = 0; i < buttons.size(); i++) {
            MenuButton b = buttons.get(i);
            float h = b.enabled ? b.hover : 0f;
            int a = b.icon == 4 ? DANGER : ACCENT;
            if (i == 0 || h > 0.001f) {
                g.glow(b.vx + b.vw * 0.25f, b.vy + b.vh * 0.5f,
                        b.vw * 0.72f, b.vh * 1.15f,
                        alpha(a, (i == 0 ? 0.09f : 0f) + h * 0.22f));
                g.glow(b.vx + b.vw * 0.80f, b.vy + b.vh * 0.5f,
                        b.vw * 0.40f, b.vh * 1.0f,
                        alpha(b.icon == 4 ? DANGER : CYAN, h * 0.16f));
            }
        }
    }

    private static void blur(MatrixStack m, float x, float y, float w, float h, float r) {
        // No-op in Main Menu to prevent stale world framebuffer leak.
    }

    private static void outline(MatrixStack m, float x, float y, float w, float h,
                                float r, float thickness, int color) {
        RenderUtils.drawRoundedRectOutline(m, x, y, w, h, r, thickness,
                color, color, color, color);
    }

    private void profileSurface(MatrixStack m) {
        RenderUtils.drawRoundedRect(m, profileX, profileY, profileW, profileH,
                RADIUS + 2f, ColorUtils.rgba(14, 17, 24, 165));
        outline(m, profileX, profileY, profileW, profileH, RADIUS + 2f, 0.8f, 0x365E6C88);
        RenderUtils.drawRoundedRect(m, profileX + 17f, profileY + 39f, 46f, 46f, 11f, 0xFF131925);
        outline(m, profileX + 17f, profileY + 39f, 46f, 46f, 11f, 1f, 0x6C7185AD);
    }

    private void buttonSurface(MatrixStack m, MenuButton b, boolean focused) {
        int tint = mix(ColorUtils.rgba(14, 18, 26, 150),
                b.icon == 4 ? 0xCA37182B : 0xC1263558, b.hover);
        if (!b.enabled) {
            tint = ColorUtils.rgba(12, 14, 20, 180);
        }
        RenderUtils.drawRoundedRect(m, b.vx, b.vy, b.vw, b.vh, RADIUS, tint);
        // Base idle outline when hover is absent or minimal
        if (b.hover <= 0.001f) {
            outline(m, b.vx, b.vy, b.vw, b.vh, RADIUS, 0.85f, b.enabled ? 0x42535F78 : 0x34455167);
        }
        if (focused && keyboardMode && b.enabled) {
            outline(m, b.vx - 3f, b.vy - 3f, b.vw + 6f, b.vh + 6f,
                    RADIUS + 3f, 1f, alpha(ACCENT, 0.85f));
        }
        if (b.icon < 3) {
            RenderUtils.drawRoundedRect(m, b.vx + 16f, b.vy + (b.vh - 32f) / 2f,
                    32f, 32f, 9f, alpha(ACCENT, b.enabled ? 0.045f + b.hover * 0.04f : 0.02f));
        }
    }

    private void details(Mesh g) {
        float brandX = profileX;
        float brandY = originY + (compact ? 48f : 91f);
        // Small geometric S mark
        g.line(brandX + 18f, brandY + 2f, brandX + 5f, brandY + 2f, 2f, ACCENT);
        g.line(brandX + 5f, brandY + 2f, brandX + 2f, brandY + 11f, 2f, ACCENT);
        g.line(brandX + 2f, brandY + 11f, brandX + 16f, brandY + 11f, 2f, ACCENT);
        g.line(brandX + 16f, brandY + 11f, brandX + 13f, brandY + 20f, 2f, ACCENT);
        g.line(brandX + 13f, brandY + 20f, brandX, brandY + 20f, 2f, ACCENT);
        g.rect(profileX + 18f, profileY + (compact ? 96f : 108f), profileW - 36f, 0.7f,
                0x30596680, 0x30596680);
        if (!compact) {
            g.rect(profileX + 18f, profileY + 226f, profileW - 36f, 0.7f, 0x28596680, 0x28596680);
            g.round(profileX + 20f, profileY + 251f, 4f, 4f, 2f, CYAN, CYAN);
        }
        float footerY = originY + (compact ? 594f : 461f);
        g.rect(profileX, footerY, compact ? 352f : 624f, 0.7f, 0x24586680, 0x24586680);

        for (int i = 0; i < buttons.size(); i++) {
            MenuButton b = buttons.get(i);
            float h = b.enabled ? b.hover : 0f;
            int c = b.icon == 4 ? DANGER : b.icon == 1 ? CYAN : ACCENT;
            if (!b.enabled) c = DIM;
            float cx = b.vx + (b.icon < 3 ? 32f : 24f);
            float cy = b.vy + b.vh / 2f;
            g.pointGlow(cx, cy, b.icon < 3 ? 28f : 20f, alpha(c, 0.16f + h * 0.35f));
            icon(g, b.icon, cx, cy, b.icon < 3 ? 1.25f : 0.95f, mix(c, TEXT, 0.22f));

            if (b.icon == 0) {
                float artX = b.vx + b.vw - 75f;
                icon(g, 0, artX, cy, 2.5f, alpha(ACCENT, 0.14f + h * 0.18f));
                g.arc(artX, cy, 40f, 22f, -24f, 0f, 360f, 0.8f, alpha(CYAN, 0.18f));
            } else if (b.icon < 3 && b.enabled) {
                float arrowX = b.vx + b.vw - 25f + h * 2.5f;
                g.line(arrowX - 4f, cy - 4f, arrowX, cy, 1.25f, alpha(c, 0.75f));
                g.line(arrowX, cy, arrowX - 4f, cy + 4f, 1.25f, alpha(c, 0.75f));
            }

            if (h > 0.001f) {
                g.roundStroke(b.vx - 2f, b.vy - 2f, b.vw + 4f, b.vh + 4f, RADIUS + 2f, 2f,
                        alpha(c, h * 0.12f), alpha(CYAN, h * 0.08f), alpha(c, h * 0.09f), alpha(c, h * 0.13f));
                g.roundStroke(b.vx, b.vy, b.vw, b.vh, RADIUS, 1f,
                        alpha(c, h * 0.90f), alpha(b.icon == 4 ? DANGER : CYAN, h * 0.75f),
                        alpha(c, h * 0.25f), alpha(c, h * 0.60f));
            }
        }
    }

    private void labels(MatrixStack m) {
        if (bold == null || regular == null || small == null) return;
        float brandY = originY + (compact ? 45f : 88f);
        bold.draw(m, "SNILL", profileX + 33f, brandY, TEXT);
        float tagX = profileX + 33f + bold.getWidth("SNILL") + 14f;
        centerY(small, m, "RELEASE", tagX, brandY + bold.getHeight() / 2f, MUTED);
        if (!compact) right(small, m, "MINECRAFT 1.21.4", navX + navW,
                brandY + (bold.getHeight() - small.getHeight()) / 2f, MUTED);
        small.draw(m, "PLAYER", profileX + 18f, profileY + 16f, MUTED);
        regular.draw(m, displayName, profileX + 76f, profileY + 43f, TEXT);
        small.draw(m, "SNILL User", profileX + 76f, profileY + 65f, MUTED);
        if (compact) {
            centerY(small, m, "Session", profileX + 18f, profileY + 113f, MUTED);
            rightCentered(small, m, sessionTime, profileX + profileW - 18f, profileY + 113f, TEXT);
        } else {
            profileRow(m, "Session", sessionTime, 138f);
            profileRow(m, "Channel", "Release", 168f);
            profileRow(m, "Version", "1.21.4", 198f);
            centerY(small, m, "Release Build", profileX + 34f, profileY + 253f, MUTED);
        }
        small.draw(m, "NAVIGATION", navX, navY - 25f, MUTED);
        float footerY = originY + (compact ? 612f : 480f);
        if (compact) {
            small.draw(m, "1.21.4 / FABRIC", profileX, footerY, MUTED);
            right(small, m, "F8 / FX " + (reducedMotion ? "OFF" : "ON"),
                    profileX + profileW, footerY, MUTED);
            small.drawCentered(m, "TAB / ARROWS  ·  ENTER", originX + 210f, footerY + 23f, MUTED);
        } else {
            small.draw(m, "TAB / ARROWS  ·  ENTER", profileX, footerY, MUTED);
            right(small, m, "F8 / MOTION " + (reducedMotion ? "OFF" : "ON"),
                    navX + navW, footerY, MUTED);
        }
    }

    private void profileRow(MatrixStack m, String label, String value, float offset) {
        centerY(small, m, label, profileX + 18f, profileY + offset, MUTED);
        rightCentered(small, m, value, profileX + profileW - 18f, profileY + offset, TEXT);
    }

    private void buttonLabel(MatrixStack m, MenuButton b) {
        if (regular == null || small == null) return;
        int color = b.enabled ? mix(TEXT, b.icon == 4 ? DANGER : TEXT, b.hover) : 0xFF8590A3;
        if (b.icon < 3) {
            float x = b.vx + 62f;
            float gap = 5f;
            float total = regular.getHeight() + gap + small.getHeight();
            float y = b.vy + (b.vh - total) / 2f;
            regular.draw(m, b.title, x, y, color);
            small.draw(m, b.enabled ? b.subtitle : "Unavailable for this account",
                    x, y + regular.getHeight() + gap, MUTED);
        } else {
            centerY(regular, m, b.title, b.vx + 45f, b.vy + b.vh / 2f, color);
        }
    }

    private static void centerY(Font font, MatrixStack m, String value,
                                float x, float center, int color) {
        if (font == null) return;
        font.draw(m, value, x, center - font.getHeight() / 2f, color);
    }

    private static void right(Font font, MatrixStack m, String value, float x, float y, int color) {
        if (font == null) return;
        font.draw(m, value, x - font.getWidth(value), y, color);
    }

    private static void rightCentered(Font font, MatrixStack m, String value,
                                      float x, float y, int color) {
        if (font == null) return;
        centerY(font, m, value, x - font.getWidth(value), y, color);
    }

    private static String fit(Font font, String text, float maxWidth) {
        if (font == null || font.getWidth(text) <= maxWidth) return text;
        String suffix = "...";
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end) + suffix) > maxWidth)
            end = text.offsetByCodePoints(end, -1);
        return end == 0 && font.getWidth(suffix) > maxWidth ? "" : text.substring(0, end) + suffix;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboardMode = false;
        focus = -1;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingAction != null) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        keyboardMode = false;
        focus = -1;
        held = null;
        float mx = (float) (mouseX / scale);
        float my = (float) (mouseY / scale);
        for (int i = buttons.size() - 1; i >= 0; i--) {
            MenuButton b = buttons.get(i);
            if (b.enabled && b.containsBase(mx, my)) {
                held = b;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        float mx = (float) (mouseX / scale);
        float my = (float) (mouseY / scale);
        MenuButton b = held;
        held = null;
        if (b == null) return pendingAction != null;
        if (b.enabled && b.containsBase(mx, my)) activate(b);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pendingAction != null) return true;
        if (keyCode == GLFW.GLFW_KEY_F8) {
            reducedMotion = !reducedMotion;
            return true;
        }
        boolean back = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_LEFT
                || (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
        boolean forward = keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_RIGHT;
        if (back || forward) {
            keyboardMode = true;
            held = null;
            int step = back ? -1 : 1;
            if (focus < 0) focus = back ? 0 : -1;
            for (int i = 0; i < buttons.size(); i++) {
                focus = Math.floorMod(focus + step, buttons.size());
                if (buttons.get(focus).enabled) break;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (keyboardMode && focus >= 0 && focus < buttons.size()) {
                activate(buttons.get(focus));
            }
            return true;
        }
        return keyCode == GLFW.GLFW_KEY_ESCAPE || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void activate(MenuButton b) {
        if (!b.enabled || pendingAction != null) return;
        if (b.icon == 1 && !mc.isMultiplayerEnabled()) return;
        clickSound();
        b.press = 1f;
        transition(b.action);
    }

    private void clickSound() {
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void transition(Runnable action) {
        if (pendingAction == null) {
            pendingAction = action;
            actionAt = System.nanoTime();
            held = null;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        held = null;
        pendingAction = null;
        keyboardMode = false;
        focus = -1;
        for (MenuButton b : buttons) {
            b.hover = 0f;
            b.press = 0f;
        }
        initialized = false;
        lastFrame = 0L;
        super.removed();
    }

    private static String twoDigits(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }

    private static float approach(float value, float target, float speed, float dt) {
        if (dt <= 0f) return value;
        float result = value + (target - value) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(result - target) < 0.0001f ? target : result;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float smoothstep(float value) {
        float t = clamp(value);
        return t * t * (3f - 2f * t);
    }

    private static int alpha(int color, float factor) {
        return (color & 0x00FFFFFF) | (Math.round((color >>> 24) * clamp(factor)) << 24);
    }

    private static int mix(int a, int b, float value) {
        float t = clamp(value);
        int ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        return Math.round(lerp(a >>> 24, b >>> 24, t)) << 24
                | Math.round(lerp(ar, (b >>> 16) & 255, t)) << 16
                | Math.round(lerp(ag, (b >>> 8) & 255, t)) << 8
                | Math.round(lerp(ab, b & 255, t));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private record Particle(float x, float y, float size, float speed, float phase) {}

    private static final class MenuButton {
        final String title, subtitle;
        final int icon;
        final Runnable action;
        float x, y, w, h, vx, vy, vw, vh, hover, press;
        boolean enabled = true;

        MenuButton(String title, String subtitle, int icon, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.action = action;
        }

        void updateBounds() {
            float lift = hover * 1.8f;
            float push = press * 1.2f;
            vx = x;
            vy = y - lift + push;
            vw = w;
            vh = h;
        }

        boolean containsBase(double mx, double my) {
            return hit(mx, my, x, y, w, h);
        }

        boolean contains(double mx, double my) {
            return containsBase(mx, my);
        }

        private static boolean hit(double mx, double my, float x, float y, float w, float h) {
            if (mx < x || my < y || mx >= x + w || my >= y + h) return false;
            double nx = Math.max(x + RADIUS, Math.min(x + w - RADIUS, mx));
            double ny = Math.max(y + RADIUS, Math.min(y + h - RADIUS, my));
            double dx = mx - nx, dy = my - ny;
            return dx * dx + dy * dy <= RADIUS * RADIUS;
        }
    }

    private static void icon(Mesh g, int kind, float x, float y, float s, int c) {
        float w = 1.35f * s;
        switch (kind) {
            case 0 -> {
                float r = 9f * s, h = r * 0.53f;
                g.line(x, y - r, x + r, y - h, w, c);
                g.line(x + r, y - h, x + r, y + h, w, c);
                g.line(x + r, y + h, x, y + r, w, c);
                g.line(x, y + r, x - r, y + h, w, c);
                g.line(x - r, y + h, x - r, y - h, w, c);
                g.line(x - r, y - h, x, y - r, w, c);
                g.line(x - r, y - h, x, y, w, c);
                g.line(x, y, x + r, y - h, w, c);
                g.line(x, y, x, y + r, w, c);
            }
            case 1 -> {
                g.arc(x, y, 11f * s, 6f * s, -28f, 0f, 360f, w, c);
                g.arc(x, y, 6f * s, 11f * s, -28f, 0f, 360f, w * 0.85f, alpha(c, 0.8f));
                g.disc(x, y, 2.6f * s, c);
                g.disc(x + 9f * s, y - 5f * s, 2.1f * s, c);
                g.disc(x - 8f * s, y + 6f * s, 1.9f * s, c);
            }
            case 2 -> {
                g.arc(x, y - 4f * s, 4.2f * s, 4.2f * s, 0f, 0f, 360f, w, c);
                g.arc(x, y + 9f * s, 8f * s, 7.2f * s, 0f, 190f, 350f, w, c);
                g.line(x + 9f * s, y - 3f * s, x + 9f * s, y + 3f * s, w, alpha(c, 0.7f));
                g.line(x + 6f * s, y, x + 12f * s, y, w, alpha(c, 0.7f));
            }
            case 3 -> {
                for (int i = 0; i < 3; i++) {
                    float xx = x - 7f * s + i * 7f * s, knob = y + (i == 1 ? 4f : -3f) * s;
                    g.line(xx, y - 10f * s, xx, y + 10f * s, w, alpha(c, 0.65f));
                    g.disc(xx, knob, 2.8f * s, c);
                    g.disc(xx, knob, 1.1f * s, 0xFF171A2D);
                }
            }
            case 4 -> {
                g.arc(x, y + s, 8f * s, 8f * s, 0f, -54f, 234f, w, c);
                g.line(x, y - 9f * s, x, y + s, w, c);
            }
            default -> throw new IllegalArgumentException("Unknown icon: " + kind);
        }
    }

    /** GUI-layer mesh. Triangles are encoded as degenerate quads on purpose. */
    private static final class Mesh {
        private static final int CIRCLE = 48;
        private static final float[] COS = new float[CIRCLE + 1];
        private static final float[] SIN = new float[CIRCLE + 1];
        static {
            for (int i = 0; i <= CIRCLE; i++) {
                COS[i] = (float) Math.cos(TAU * i / CIRCLE);
                SIN[i] = (float) Math.sin(TAU * i / CIRCLE);
            }
        }

        private final VertexConsumer vertices;
        private final Matrix4f matrix;
        private final float aa;

        Mesh(VertexConsumer vertices, Matrix4f matrix, float aa) {
            this.vertices = vertices;
            this.matrix = matrix;
            this.aa = aa;
        }

        private void v(float x, float y, int color) {
            vertices.vertex(matrix, x, y, 0f).color(color);
        }

        void rect(float x, float y, float w, float h, int top, int bottom) {
            v(x, y, top); v(x, y + h, bottom);
            v(x + w, y + h, bottom); v(x + w, y, top);
        }

        void line(float x1, float y1, float x2, float y2, float thickness, int color) {
            float dx = x2 - x1, dy = y2 - y1;
            float length = (float) Math.hypot(dx, dy);
            if (length < 0.0001f) return;
            float nx = -dy / length * thickness * 0.5f;
            float ny = dx / length * thickness * 0.5f;
            v(x1 + nx, y1 + ny, color); v(x2 + nx, y2 + ny, color);
            v(x2 - nx, y2 - ny, color); v(x1 - nx, y1 - ny, color);
        }

        void arc(float x, float y, float rx, float ry, float start, float end, float thickness, int color) {
            arc(x, y, rx, ry, 0f, start, end, thickness, color);
        }

        void arc(float x, float y, float rx, float ry, float tilt, float start,
                 float end, float thickness, int color) {
            int steps = Math.max(8, Math.min(64, (int) (Math.abs(end - start) / 8f)));
            float ct = (float) Math.cos(Math.toRadians(tilt)), st = (float) Math.sin(Math.toRadians(tilt));
            float px = 0f, py = 0f;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(start + (end - start) * i / steps);
                float ex = (float) Math.cos(a) * rx, ey = (float) Math.sin(a) * ry;
                float xx = x + ex * ct - ey * st, yy = y + ex * st + ey * ct;
                if (i > 0) line(px, py, xx, yy, thickness, color);
                px = xx;
                py = yy;
            }
        }

        void disc(float x, float y, float radius, int color) {
            for (int i = 0; i < CIRCLE; i += 4) {
                float ax = x + COS[i] * radius, ay = y + SIN[i] * radius;
                float bx = x + COS[i + 4] * radius, by = y + SIN[i + 4] * radius;
                v(x, y, color); v(ax, ay, color); v(bx, by, color); v(bx, by, color);
                v(ax, ay, color); v(bx, by, color);
                v(x + COS[i + 4] * (radius + aa), y + SIN[i + 4] * (radius + aa), alpha(color, 0f));
                v(x + COS[i] * (radius + aa), y + SIN[i] * (radius + aa), alpha(color, 0f));
            }
        }

        void pointGlow(float x, float y, float r, int color) {
            for (int i = 0; i < CIRCLE; i += 3) {
                v(x, y, color);
                v(x + COS[i] * r, y + SIN[i] * r, alpha(color, 0f));
                v(x + COS[i + 3] * r, y + SIN[i + 3] * r, alpha(color, 0f));
                v(x + COS[i + 3] * r, y + SIN[i + 3] * r, alpha(color, 0f));
            }
        }

        void round(float x, float y, float w, float h, float radius, int top, int bottom) {
            if (w <= 0f || h <= 0f) return;
            float r = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
            float cx = x + w * 0.5f, cy = y + h * 0.5f;
            float firstX = 0f, firstY = 0f, firstOX = 0f, firstOY = 0f;
            float px = 0f, py = 0f, pox = 0f, poy = 0f;
            int center = mix(top, bottom, 0.5f);
            for (int i = 0; i <= 52; i++) {
                float nx, ny, ox, oy;
                if (i == 52) {
                    nx = firstX; ny = firstY; ox = firstOX; oy = firstOY;
                } else {
                    int corner = i / 13;
                    double angle = Math.toRadians(-90 + corner * 90 + (i % 13) * 7.5);
                    float ccx = corner < 2 ? x + w - r : x + r;
                    float ccy = corner == 0 || corner == 3 ? y + r : y + h - r;
                    float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                    nx = ccx + cos * r; ny = ccy + sin * r;
                    ox = ccx + cos * (r + aa); oy = ccy + sin * (r + aa);
                }
                if (i == 0) {
                    firstX = nx; firstY = ny; firstOX = ox; firstOY = oy;
                } else {
                    int pc = mix(top, bottom, (py - y) / h);
                    int nc = mix(top, bottom, (ny - y) / h);
                    v(cx, cy, center); v(nx, ny, nc); v(px, py, pc); v(px, py, pc);
                    v(px, py, pc); v(nx, ny, nc);
                    v(ox, oy, alpha(nc, 0f)); v(pox, poy, alpha(pc, 0f));
                }
                px = nx; py = ny; pox = ox; poy = oy;
            }
        }

        void glow(float x, float y, float rx, float ry, int color) {
            for (int ring = 0; ring < 7; ring++) {
                float r0 = ring / 7f, r1 = (ring + 1f) / 7f;
                int inner = alpha(color, (1f - r0) * (1f - r0) * (1f - r0));
                int outer = alpha(color, (1f - r1) * (1f - r1) * (1f - r1));
                for (int i = 0; i < CIRCLE; i++) {
                    v(x + COS[i] * rx * r0, y + SIN[i] * ry * r0, inner);
                    v(x + COS[i + 1] * rx * r0, y + SIN[i + 1] * ry * r0, inner);
                    v(x + COS[i + 1] * rx * r1, y + SIN[i + 1] * ry * r1, outer);
                    v(x + COS[i] * rx * r1, y + SIN[i] * ry * r1, outer);
                }
            }
        }

        void roundStroke(float x, float y, float w, float h, float r, float thickness,
                         int tl, int tr, int br, int bl) {
            float half = thickness * 0.5f;
            float pix = 0f, piy = 0f, pox = 0f, poy = 0f;
            float pex = 0f, pey = 0f, pfx = 0f, pfy = 0f;
            int pc = 0;
            for (int i = 0; i <= 36; i++) {
                int n = i % 36, corner = n / 9;
                double a = Math.toRadians(-90f + corner * 90f + n % 9 * 11.25f);
                float ccx = corner < 2 ? x + w - r : x + r;
                float ccy = corner == 0 || corner == 3 ? y + r : y + h - r;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float ix = ccx + ca * (r - half), iy = ccy + sa * (r - half);
                float ox = ccx + ca * (r + half), oy = ccy + sa * (r + half);
                float ex = ccx + ca * (r + half + aa), ey = ccy + sa * (r + half + aa);
                float fx = ccx + ca * (r - half - aa), fy = ccy + sa * (r - half - aa);
                float tx = clamp((ccx + ca * r - x) / w), ty = clamp((ccy + sa * r - y) / h);
                int c = mix(mix(tl, tr, tx), mix(bl, br, tx), ty);
                if (i > 0) {
                    v(pix, piy, pc); v(ix, iy, c); v(ox, oy, c); v(pox, poy, pc);
                    v(pox, poy, pc); v(ox, oy, c);
                    v(ex, ey, alpha(c, 0f)); v(pex, pey, alpha(pc, 0f));
                    v(pix, piy, pc); v(ix, iy, c);
                    v(fx, fy, alpha(c, 0f)); v(pfx, pfy, alpha(pc, 0f));
                }
                pix = ix; piy = iy; pox = ox; poy = oy;
                pex = ex; pey = ey; pfx = fx; pfy = fy; pc = c;
            }
        }
    }
}
