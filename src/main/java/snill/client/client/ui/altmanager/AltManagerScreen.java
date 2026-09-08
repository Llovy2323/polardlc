package snill.client.client.ui.altmanager;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import snill.client.Snill;
import snill.client.api.QClient;
import snill.client.api.storages.implement.alt.Alt;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.client.ClientSoundPlayer;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.scissor.ScissorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AltManagerScreen extends Screen implements QClient {

    private static final Identifier MENU_BG = Identifier.of("snill", "textures/mainmenu/menu.png");
    private static final int PARTICLE_COUNT = 60;

    private final Screen parent;
    private final List<MenuParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    private float smoothMouseX = 0f;
    private float smoothMouseY = 0f;

    // Hover animations
    private final AnimationUtils backAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
    private final AnimationUtils addAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
    private final AnimationUtils randAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
    private final AnimationUtils clipAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);

    private String inputName = "";
    private boolean typing = false;
    private float scrollOffset = 0f;
    private float targetScroll = 0f;
    private String statusMessage = "";
    private long statusMessageTime = 0L;
    private boolean statusSuccess = true;

    // Window dimensions matching ocean theme
    private static final float WIN_WIDTH = 440f;
    private static final float WIN_HEIGHT = 240f;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
        initParticles();
    }

    private void initParticles() {
        particles.clear();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new MenuParticle(
                    random.nextFloat(),
                    random.nextFloat(),
                    0.5f + random.nextFloat() * 1.8f,
                    0.00025f + random.nextFloat() * 0.0006f,
                    0.15f + random.nextFloat() * 0.5f,
                    random.nextFloat() * ((float) Math.PI * 2f)
            ));
        }
    }

    @Override
    protected void init() {
        scrollOffset = 0f;
        targetScroll = 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        int width = this.width;
        int height = this.height;
        long time = System.currentTimeMillis();

        smoothMouseX = MathHelper.lerp(0.06f, smoothMouseX, (float) mouseX);
        smoothMouseY = MathHelper.lerp(0.06f, smoothMouseY, (float) mouseY);

        float parallaxX = (smoothMouseX - width * 0.5f) * 0.012f;
        float parallaxY = (smoothMouseY - height * 0.5f) * 0.012f;

        // 1. Cinematic volumetric ocean & light rays background (identical to main menu)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.drawImage(matrices, MENU_BG, -15f + parallaxX, -15f + parallaxY, width + 30f, height + 30f, 0xFFFFFFFF);

        // 2. Ambient rising particles (underwater motes in light rays)
        for (MenuParticle p : particles) {
            p.y -= p.speed;
            if (p.y < -0.05f) {
                p.y = 1.05f;
                p.x = random.nextFloat();
            }
            float px = p.x * width + (float) Math.sin(time * 0.001f + p.phase) * 10f;
            float py = p.y * height;
            float pAlpha = p.alpha * (0.6f + 0.4f * (float) Math.sin(time * 0.002f + p.phase));
            int pColor = ColorUtils.rgba(160, 200, 255, (int) (pAlpha * 255));
            RenderUtils.drawRoundCircle(matrices, px, py, p.size, pColor);
        }

        scrollOffset = MathHelper.lerp(0.14f, scrollOffset, targetScroll);

        Font titleFont = Fonts.getFont("suisse", 14);
        if (titleFont == null) titleFont = Fonts.getFont("moe3", 14);
        Font btnFont = Fonts.getFont("suisse", 10);
        if (btnFont == null) btnFont = Fonts.getFont("moe3", 10);
        Font subFont = Fonts.getFont("suisse", 9);
        Font microFont = Fonts.getFont("suisse", 8);

        String currentUsername = mc.getSession() != null ? mc.getSession().getUsername() : "Player";

        // 3. Central Ocean Glass Window
        float winX = (width - WIN_WIDTH) / 2f;
        float winY = (height - WIN_HEIGHT) / 2f;

        // Glass shadow & panel background
        RenderUtils.drawShadow(matrices, winX, winY, WIN_WIDTH, WIN_HEIGHT, 8f, 16f,
                0x301D4ED8, 0x252563EB, 0x203B82F6, 0x301D4ED8);
        RenderUtils.drawBlur(matrices, winX, winY, WIN_WIDTH, WIN_HEIGHT, 8f, 6f, ColorUtils.rgba(5, 8, 18, 130));
        RenderUtils.drawGradientRect(matrices, winX, winY, WIN_WIDTH, WIN_HEIGHT, 8f,
                0x500A1022, 0x3D060A16, 0x3D060A16, 0x500A1022);
        RenderUtils.drawRoundedRectOutline(matrices, winX, winY, WIN_WIDTH, WIN_HEIGHT, 8f, 0.8f, 0x24406596);

        // 4. Header
        // Back Button
        float backX = winX + 12f;
        float backY = winY + 8f;
        float backW = 60f;
        float backH = 20f;
        boolean backHover = HoveringUtils.isHovered(mouseX, mouseY, backX, backY, backW, backH);
        backAnim.update(backHover ? 1f : 0f);
        float backP = backAnim.getValue();

        int backBg = ColorUtils.interpolateColor(ColorUtils.rgba(10, 16, 32, 45), ColorUtils.rgba(26, 46, 92, 95), backP);
        int backBorder = ColorUtils.interpolateColor(ColorUtils.rgba(60, 95, 155, 30), ColorUtils.rgba(96, 165, 250, 140), backP);
        RenderUtils.drawRoundedRect(matrices, backX, backY, backW, backH, 4f, backBg);
        RenderUtils.drawRoundedRectOutline(matrices, backX, backY, backW, backH, 4f, 0.6f + backP * 0.3f, backBorder);
        if (backP > 0.05f) {
            RenderUtils.drawShadow(matrices, backX, backY, backW, backH, 4f, 5f * backP, 0x253B82F6, 0x253B82F6, 0x253B82F6, 0x253B82F6);
        }
        if (subFont != null) {
            int backTextCol = ColorUtils.interpolateColor(0xFFCBD5E1, 0xFFFFFFFF, backP);
            subFont.drawCenteredString(matrices, "‹ Назад", backX + backW / 2f, backY + 9.5f, backTextCol);
        }

        // Title: ALT MANAGER
        if (titleFont != null) {
            float titleX = width / 2f;
            titleFont.drawCenteredString(matrices, "ALT MANAGER", titleX + 0.5f, winY + 12.5f, 0x60000000);
            titleFont.drawCenteredString(matrices, "ALT MANAGER", titleX, winY + 12f, 0xFFFFFFFF);
        }

        // Active User Widget (Top Right)
        float profW = 115f;
        float profH = 20f;
        float profX = winX + WIN_WIDTH - profW - 12f;
        float profY = winY + 8f;
        RenderUtils.drawRoundedRect(matrices, profX, profY, profW, profH, 4f, ColorUtils.rgba(10, 16, 32, 50));
        RenderUtils.drawRoundedRectOutline(matrices, profX, profY, profW, profH, 4f, 0.6f, 0x30406596);

        float headSize = 14f;
        float headX = profX + 3f;
        float headY = profY + (profH - headSize) / 2f;
        RenderUtils.drawPlayerHead(matrices, currentUsername, headX, headY, headSize, 2f);
        RenderUtils.drawRoundedRectOutline(matrices, headX, headY, headSize, headSize, 2f, 0.5f, 0x4060A5FA);

        float textX = headX + headSize + 4f;
        if (microFont != null) {
            microFont.drawString(matrices, currentUsername, textX, profY + 5.5f, 0xFFFFFFFF);
        }
        RenderUtils.drawRoundCircle(matrices, textX + 1.5f, profY + 15.5f, 1.8f, 0xFF22C55E);
        if (microFont != null) {
            microFont.drawString(matrices, "В сети", textX + 6f, profY + 14f, 0xFF94A3B8);
        }

        // Header Separator Line
        float sepY = winY + 32f;
        RenderUtils.drawGradientRect(matrices, winX + 12f, sepY, WIN_WIDTH - 24f, 0.8f, 0.4f,
                0x0A3B82F6, 0x403B82F6, 0x403B82F6, 0x0A3B82F6);

        // 5. Left Column: Saved Accounts
        float leftX = winX + 12f;
        float leftY = sepY + 8f;
        float leftW = 245f;
        float leftH = WIN_HEIGHT - 48f;

        List<Alt> alts = Snill.INSTANCE.altStorage != null ? Snill.INSTANCE.altStorage.getAlts() : new ArrayList<>();
        if (subFont != null) {
            subFont.drawString(matrices, "СОХРАНЕННЫЕ АККАУНТЫ", leftX, leftY + 1f, 0xFFBAC7D5);
            if (microFont != null) {
                microFont.drawString(matrices, "[" + alts.size() + "]", leftX + 116f, leftY + 2f, 0xFF60A5FA);
            }
        }

        float listY = leftY + 14f;
        float listH = leftH - 14f;
        RenderUtils.drawRoundedRect(matrices, leftX, listY, leftW, listH, 5f, ColorUtils.rgba(6, 10, 20, 50));
        RenderUtils.drawRoundedRectOutline(matrices, leftX, listY, leftW, listH, 5f, 0.6f, 0x1A406596);

        float itemH = 28f;
        float itemSpacing = 3.5f;
        float totalContentH = alts.size() * (itemH + itemSpacing);
        float maxScroll = Math.max(0f, totalContentH - listH + 4f);
        targetScroll = MathHelper.clamp(targetScroll, -maxScroll, 0f);

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates((double) leftX, (double) (listY + 2f), (double) leftW, (double) (listH - 4f));

        for (int i = 0; i < alts.size(); i++) {
            Alt alt = alts.get(i);
            float cardY = listY + 3f + i * (itemH + itemSpacing) + scrollOffset;

            if (cardY + itemH < listY || cardY > listY + listH) continue;

            boolean cardHover = HoveringUtils.isHovered(mouseX, mouseY, leftX + 3f, cardY, leftW - 6f, itemH);
            boolean isActive = alt.getUsername().equalsIgnoreCase(currentUsername);

            int cardBg = isActive ? ColorUtils.rgba(14, 30, 60, 80) :
                    (cardHover ? ColorUtils.rgba(16, 28, 54, 70) : ColorUtils.rgba(10, 16, 32, 35));
            int cardBorder = isActive ? 0x6060A5FA :
                    (cardHover ? 0x4060A5FA : 0x15406596);

            RenderUtils.drawRoundedRect(matrices, leftX + 3f, cardY, leftW - 6f, itemH, 4f, cardBg);
            RenderUtils.drawRoundedRectOutline(matrices, leftX + 3f, cardY, leftW - 6f, itemH, 4f, 0.6f, cardBorder);

            if (isActive) {
                RenderUtils.drawRoundedRect(matrices, leftX + 3f, cardY + 2f, 1.5f, itemH - 4f, 0.75f, 0xFF60A5FA);
            }

            // Head Avatar
            float cHeadSize = 18f;
            float cHeadX = leftX + 8f;
            float cHeadY = cardY + (itemH - cHeadSize) / 2f;
            RenderUtils.drawPlayerHead(matrices, alt.getUsername(), cHeadX, cHeadY, cHeadSize, 2.5f);
            RenderUtils.drawRoundedRectOutline(matrices, cHeadX, cHeadY, cHeadSize, cHeadSize, 2.5f, 0.5f,
                    isActive ? 0x8060A5FA : 0x25406596);

            // Alt Name & Status (properly vertically centered)
            float cTextX = cHeadX + cHeadSize + 6f;
            if (btnFont != null) {
                int nameCol = isActive ? 0xFFFFFFFF : (cardHover ? 0xFFF1F5F9 : 0xFFCBD5E1);
                btnFont.drawString(matrices, alt.getUsername(), cTextX, cardY + 7.5f, nameCol);
            }

            if (microFont != null) {
                if (isActive) {
                    RenderUtils.drawRoundCircle(matrices, cTextX + 1.5f, cardY + 19.5f, 1.8f, 0xFF22C55E);
                    microFont.drawString(matrices, "Активен", cTextX + 6f, cardY + 18f, 0xFF22C55E);
                } else {
                    microFont.drawString(matrices, "Нажмите для входа", cTextX, cardY + 18f, 0x6094A3B8);
                }
            }

            // Delete button on the right (Ruby red hover)
            float delSize = 15f;
            float delX = leftX + leftW - delSize - 8f;
            float delY = cardY + (itemH - delSize) / 2f;
            boolean delHover = HoveringUtils.isHovered(mouseX, mouseY, delX, delY, delSize, delSize);

            int delBg = delHover ? ColorUtils.rgba(160, 24, 52, 90) : ColorUtils.rgba(30, 10, 16, 25);
            int delBorder = delHover ? ColorUtils.rgba(251, 113, 133, 140) : ColorUtils.rgba(244, 63, 94, 25);
            RenderUtils.drawRoundedRect(matrices, delX, delY, delSize, delSize, 3f, delBg);
            RenderUtils.drawRoundedRectOutline(matrices, delX, delY, delSize, delSize, 3f, 0.5f, delBorder);

            if (microFont != null) {
                int delTextCol = delHover ? 0xFFFFFFFF : 0x80F43F5E;
                microFont.drawCenteredString(matrices, "×", delX + delSize / 2f, delY + 7.0f, delTextCol);
            }
        }

        ScissorUtils.pop();
        ScissorUtils.unset();

        // Scrollbar if needed
        if (maxScroll > 0f) {
            float barW = 2f;
            float barX = leftX + leftW - barW - 2f;
            float barTrackH = listH - 6f;
            float barThumbH = Math.max(16f, barTrackH * (listH / totalContentH));
            float barProgress = -scrollOffset / maxScroll;
            float barThumbY = listY + 3f + (barTrackH - barThumbH) * barProgress;

            RenderUtils.drawRoundedRect(matrices, barX, barThumbY, barW, barThumbH, 1f, 0x4060A5FA);
        }

        // 6. Right Column: Add Account & Quick Actions
        float rightX = leftX + leftW + 12f;
        float rightY = sepY + 8f;
        float rightW = WIN_WIDTH - leftW - 36f;

        if (subFont != null) {
            subFont.drawString(matrices, "ДОБАВИТЬ АККАУНТ", rightX, rightY + 1f, 0xFFBAC7D5);
        }

        // Input Box
        float inY = rightY + 14f;
        float inH = 22f;
        boolean inHover = HoveringUtils.isHovered(mouseX, mouseY, rightX, inY, rightW, inH);

        int inBg = ColorUtils.rgba(10, 16, 32, 50);
        int inBorder = typing ? 0x9060A5FA : (inHover ? 0x5060A5FA : 0x24406596);

        RenderUtils.drawRoundedRect(matrices, rightX, inY, rightW, inH, 4f, inBg);
        RenderUtils.drawRoundedRectOutline(matrices, rightX, inY, rightW, inH, 4f, typing ? 0.8f : 0.6f, inBorder);

        if (subFont != null) {
            if (inputName.isEmpty() && !typing) {
                subFont.drawString(matrices, "Введите никнейм...", rightX + 8f, inY + 10.5f, 0x6094A3B8);
            } else {
                subFont.drawString(matrices, inputName, rightX + 8f, inY + 10.5f, 0xFFFFFFFF);
                if (typing && (System.currentTimeMillis() % 1000L < 500L)) {
                    float cursorX = rightX + 8f + subFont.getStringWidth(inputName);
                    RenderUtils.drawRoundedRect(matrices, cursorX + 1f, inY + 5.5f, 1f, inH - 11f, 0.5f, 0xFF60A5FA);
                }
            }
        }

        // "Войти и сохранить" Button
        float addY = inY + inH + 6f;
        float addH = 20f;
        boolean addHover = HoveringUtils.isHovered(mouseX, mouseY, rightX, addY, rightW, addH);
        addAnim.update(addHover ? 1f : 0f);
        float addP = addAnim.getValue();

        int addBg = ColorUtils.interpolateColor(ColorUtils.rgba(29, 78, 216, 85), ColorUtils.rgba(37, 99, 235, 110), addP);
        int addBorder = ColorUtils.interpolateColor(ColorUtils.rgba(96, 165, 250, 100), ColorUtils.rgba(147, 197, 253, 160), addP);

        if (addP > 0.05f) {
            RenderUtils.drawShadow(matrices, rightX, addY, rightW, addH, 5f, 6f * addP, 0x302563EB, 0x302563EB, 0x302563EB, 0x302563EB);
        }
        RenderUtils.drawRoundedRect(matrices, rightX, addY, rightW, addH, 4f, addBg);
        RenderUtils.drawRoundedRectOutline(matrices, rightX, addY, rightW, addH, 4f, 0.6f + addP * 0.3f, addBorder);

        if (btnFont != null) {
            int addTextCol = ColorUtils.interpolateColor(0xFFE2E8F0, 0xFFFFFFFF, addP);
            btnFont.drawCenteredString(matrices, "Войти и сохранить", rightX + rightW / 2f, addY + 9.5f, addTextCol);
        }

        // Separator: БЫСТРЫЕ ДЕЙСТВИЯ
        float sep2Y = addY + addH + 12f;
        RenderUtils.drawGradientRect(matrices, rightX, sep2Y, rightW, 0.8f, 0.4f,
                0x0A3B82F6, 0x303B82F6, 0x303B82F6, 0x0A3B82F6);

        if (microFont != null) {
            microFont.drawString(matrices, "БЫСТРЫЕ ДЕЙСТВИЯ", rightX, sep2Y + 8f, 0x8594A3B8);
        }

        // "Случайный ник" Button
        float randY = sep2Y + 20f;
        float randH = 20f;
        boolean randHover = HoveringUtils.isHovered(mouseX, mouseY, rightX, randY, rightW, randH);
        randAnim.update(randHover ? 1f : 0f);
        float randP = randAnim.getValue();

        int randBg = ColorUtils.interpolateColor(ColorUtils.rgba(10, 16, 32, 45), ColorUtils.rgba(26, 46, 92, 95), randP);
        int randBorder = ColorUtils.interpolateColor(ColorUtils.rgba(60, 95, 155, 30), ColorUtils.rgba(96, 165, 250, 140), randP);

        if (randP > 0.05f) {
            RenderUtils.drawShadow(matrices, rightX, randY, rightW, randH, 4f, 5f * randP, 0x253B82F6, 0x253B82F6, 0x253B82F6, 0x253B82F6);
        }
        RenderUtils.drawRoundedRect(matrices, rightX, randY, rightW, randH, 4f, randBg);
        RenderUtils.drawRoundedRectOutline(matrices, rightX, randY, rightW, randH, 4f, 0.6f + randP * 0.3f, randBorder);

        if (btnFont != null) {
            int randTextCol = ColorUtils.interpolateColor(0xFFCBD5E1, 0xFFFFFFFF, randP);
            btnFont.drawCenteredString(matrices, "Случайный ник", rightX + rightW / 2f, randY + 9.5f, randTextCol);
        }

        // "Из буфера обмена" Button
        float clipY = randY + randH + 6f;
        float clipH = 20f;
        boolean clipHover = HoveringUtils.isHovered(mouseX, mouseY, rightX, clipY, rightW, clipH);
        clipAnim.update(clipHover ? 1f : 0f);
        float clipP = clipAnim.getValue();

        int clipBg = ColorUtils.interpolateColor(ColorUtils.rgba(10, 16, 32, 45), ColorUtils.rgba(26, 46, 92, 95), clipP);
        int clipBorder = ColorUtils.interpolateColor(ColorUtils.rgba(60, 95, 155, 30), ColorUtils.rgba(96, 165, 250, 140), clipP);

        if (clipP > 0.05f) {
            RenderUtils.drawShadow(matrices, rightX, clipY, rightW, clipH, 4f, 5f * clipP, 0x253B82F6, 0x253B82F6, 0x253B82F6, 0x253B82F6);
        }
        RenderUtils.drawRoundedRect(matrices, rightX, clipY, rightW, clipH, 4f, clipBg);
        RenderUtils.drawRoundedRectOutline(matrices, rightX, clipY, rightW, clipH, 4f, 0.6f + clipP * 0.3f, clipBorder);

        if (btnFont != null) {
            int clipTextCol = ColorUtils.interpolateColor(0xFFCBD5E1, 0xFFFFFFFF, clipP);
            btnFont.drawCenteredString(matrices, "Из буфера обмена", rightX + rightW / 2f, clipY + 9.5f, clipTextCol);
        }

        // 7. Toast Notification (Bottom Center)
        long elapsed = time - statusMessageTime;
        if (elapsed < 3000L && !statusMessage.isEmpty()) {
            float toastAlpha = elapsed < 300L ? (elapsed / 300f) :
                    (elapsed > 2600L ? (1f - (elapsed - 2600f) / 400f) : 1f);
            toastAlpha = MathHelper.clamp(toastAlpha, 0f, 1f);

            float toastW = subFont != null ? subFont.getStringWidth(statusMessage) + 24f : 140f;
            float toastH = 20f;
            float toastX = (width - toastW) / 2f;
            float toastY = height - 28f;

            int tBg = ColorUtils.rgba(8, 14, 28, (int) (toastAlpha * 180));
            int tBorder = statusSuccess ?
                    ColorUtils.rgba(34, 197, 94, (int) (toastAlpha * 140)) :
                    ColorUtils.rgba(239, 68, 68, (int) (toastAlpha * 140));

            RenderUtils.drawBlur(matrices, toastX, toastY, toastW, toastH, 4f, 4f, ColorUtils.rgba(5, 8, 18, (int) (toastAlpha * 120)));
            RenderUtils.drawRoundedRect(matrices, toastX, toastY, toastW, toastH, 4f, tBg);
            RenderUtils.drawRoundedRectOutline(matrices, toastX, toastY, toastW, toastH, 4f, 0.6f, tBorder);

            int dotCol = statusSuccess ?
                    ColorUtils.rgba(34, 197, 94, (int) (toastAlpha * 255)) :
                    ColorUtils.rgba(239, 68, 68, (int) (toastAlpha * 255));
            RenderUtils.drawRoundCircle(matrices, toastX + 8f, toastY + 10f, 2f, dotCol);

            if (subFont != null) {
                int textCol = ColorUtils.rgba(241, 245, 249, (int) (toastAlpha * 255));
                subFont.drawString(matrices, statusMessage, toastX + 15f, toastY + 9.5f, textCol);
            }
        }

        // 8. Footer
        if (subFont != null) {
            subFont.drawString(matrices, "SNILL Client • Fabric 1.21.4", 14f, height - 12f, 0x6594A3B8);
            subFont.drawRight(matrices, "Нажмите Esc для возврата", width - 14f, height - 12f, 0x7594A3B8);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        float winX = (this.width - WIN_WIDTH) / 2f;
        float winY = (this.height - WIN_HEIGHT) / 2f;

        // Back button
        float backX = winX + 12f;
        float backY = winY + 8f;
        float backW = 60f;
        float backH = 20f;
        if (HoveringUtils.isHovered(mouseX, mouseY, backX, backY, backW, backH)) {
            ClientSoundPlayer.playSound("closegui.wav", 0.7, 1.0f);
            if (mc != null) {
                mc.setScreen(parent);
            }
            return true;
        }

        // Check account cards click & delete
        float sepY = winY + 32f;
        float leftX = winX + 12f;
        float leftW = 245f;
        float listY = sepY + 8f + 14f;
        float listH = WIN_HEIGHT - 48f - 14f;

        List<Alt> alts = Snill.INSTANCE.altStorage != null ? Snill.INSTANCE.altStorage.getAlts() : new ArrayList<>();
        float itemH = 28f;
        float itemSpacing = 3.5f;

        if (HoveringUtils.isHovered(mouseX, mouseY, leftX, listY, leftW, listH)) {
            for (int i = 0; i < alts.size(); i++) {
                Alt alt = alts.get(i);
                float cardY = listY + 3f + i * (itemH + itemSpacing) + scrollOffset;

                if (cardY + itemH < listY || cardY > listY + listH) continue;

                // Delete button check
                float delSize = 15f;
                float delX = leftX + leftW - delSize - 8f;
                float delY = cardY + (itemH - delSize) / 2f;
                if (HoveringUtils.isHovered(mouseX, mouseY, delX, delY, delSize, delSize)) {
                    if (Snill.INSTANCE.altStorage != null) {
                        Snill.INSTANCE.altStorage.removeAlt(alt);
                    }
                    ClientSoundPlayer.playSound("closegui.wav", 0.7, 1.0f);
                    showStatus("Аккаунт " + alt.getUsername() + " удален", false);
                    return true;
                }

                // Card click -> login
                if (HoveringUtils.isHovered(mouseX, mouseY, leftX + 3f, cardY, leftW - 6f, itemH)) {
                    if (Snill.INSTANCE.altStorage != null) {
                        Snill.INSTANCE.altStorage.login(alt);
                    }
                    ClientSoundPlayer.playSound("opengui.wav", 0.7, 1.0f);
                    showStatus("Аккаунт " + alt.getUsername() + " активирован!", true);
                    return true;
                }
            }
        }

        // Right Column
        float rightX = leftX + leftW + 12f;
        float rightY = sepY + 8f;
        float rightW = WIN_WIDTH - leftW - 36f;

        // Input box click
        float inY = rightY + 14f;
        float inH = 22f;
        if (HoveringUtils.isHovered(mouseX, mouseY, rightX, inY, rightW, inH)) {
            typing = true;
            return true;
        } else {
            typing = false;
        }

        // "Войти и сохранить"
        float addY = inY + inH + 6f;
        float addH = 20f;
        if (HoveringUtils.isHovered(mouseX, mouseY, rightX, addY, rightW, addH)) {
            submitInput();
            return true;
        }

        // "Случайный ник"
        float sep2Y = addY + addH + 12f;
        float randY = sep2Y + 20f;
        float randH = 20f;
        if (HoveringUtils.isHovered(mouseX, mouseY, rightX, randY, rightW, randH)) {
            generateRandomNick();
            return true;
        }

        // "Из буфера обмена"
        float clipY = randY + randH + 6f;
        float clipH = 20f;
        if (HoveringUtils.isHovered(mouseX, mouseY, rightX, clipY, rightW, clipH)) {
            pasteFromClipboard();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float winX = (this.width - WIN_WIDTH) / 2f;
        float winY = (this.height - WIN_HEIGHT) / 2f;
        float sepY = winY + 32f;
        float leftX = winX + 12f;
        float leftW = 245f;
        float listY = sepY + 8f + 14f;
        float listH = WIN_HEIGHT - 48f - 14f;

        if (HoveringUtils.isHovered(mouseX, mouseY, leftX, listY, leftW, listH)) {
            targetScroll += (float) (verticalAmount * 22f);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ClientSoundPlayer.playSound("closegui.wav", 0.7, 1.0f);
            if (mc != null) {
                mc.setScreen(parent);
            }
            return true;
        }

        if (typing) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitInput();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!inputName.isEmpty()) {
                    inputName = inputName.substring(0, inputName.length() - 1);
                }
                return true;
            }

            // Ctrl + V
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (mc != null && mc.keyboard != null) {
                    String clip = mc.keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        clip = clip.trim();
                        for (char c : clip.toCharArray()) {
                            if (isValidNickChar(c) && inputName.length() < 16) {
                                inputName += c;
                            }
                        }
                    }
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (typing && isValidNickChar(chr) && inputName.length() < 16) {
            inputName += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private boolean isValidNickChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private void submitInput() {
        if (inputName == null || inputName.trim().isEmpty()) {
            showStatus("Ошибка: введите никнейм!", false);
            return;
        }
        String name = inputName.trim();
        if (Snill.INSTANCE.altStorage != null) {
            Snill.INSTANCE.altStorage.login(name);
        }
        ClientSoundPlayer.playSound("opengui.wav", 0.7, 1.0f);
        showStatus("Аккаунт " + name + " активирован!", true);
        inputName = "";
        typing = false;
    }

    private void generateRandomNick() {
        String randName = Snill.INSTANCE.altStorage != null ?
                Snill.INSTANCE.altStorage.generateRandomNick() :
                "Snill_" + (1000 + random.nextInt(9000));
        if (Snill.INSTANCE.altStorage != null) {
            Snill.INSTANCE.altStorage.login(randName);
        }
        ClientSoundPlayer.playSound("opengui.wav", 0.7, 1.0f);
        showStatus("Случайный ник " + randName + " создан!", true);
    }

    private void pasteFromClipboard() {
        if (mc != null && mc.keyboard != null) {
            String clip = mc.keyboard.getClipboard();
            if (clip != null && !clip.trim().isEmpty()) {
                clip = clip.trim();
                if (clip.length() > 16) clip = clip.substring(0, 16);
                inputName = clip;
                typing = true;
                showStatus("Никнейм вставлен из буфера", true);
                return;
            }
        }
        showStatus("Буфер обмена пуст", false);
    }

    private void showStatus(String msg, boolean success) {
        statusMessage = msg;
        statusSuccess = success;
        statusMessageTime = System.currentTimeMillis();
    }

    private static class MenuParticle {
        float x, y;
        float size;
        float speed;
        float alpha;
        float phase;

        MenuParticle(float x, float y, float size, float speed, float alpha, float phase) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speed = speed;
            this.alpha = alpha;
            this.phase = phase;
        }
    }
}
