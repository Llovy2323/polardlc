package snill.client.client.ui.altmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import snill.client.Snill;
import snill.client.api.QClient;
import snill.client.api.storages.implement.alt.Alt;
import snill.client.api.storages.implement.alt.AltStorage;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.client.ClientSoundPlayer;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import snill.client.api.utils.render.ShaderUtils;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.ui.space.SpaceBackgroundRenderer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AltManagerScreen extends Screen implements QClient {

    private final Screen parent;
    private final SpaceBackgroundRenderer spaceBackground = new SpaceBackgroundRenderer();
    private final AnimationUtils openAnimation = new AnimationUtils(0f, 9.0f, Easings.CUBIC_OUT);

    private String inputName = "";
    private boolean typing = false;
    private float scrollOffset = 0f;
    private float targetScroll = 0f;
    private String statusMessage = "";
    private long statusMessageTime = 0L;
    private boolean statusSuccess = true;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openAnimation.setValue(0f);
        openAnimation.update(1f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        // 1. Live Animated Space Background
        spaceBackground.render(context, mouseX, mouseY, delta);

        scrollOffset = MathHelper.lerp(0.12f, scrollOffset, targetScroll);

        Font titleFont = Fonts.getFont("suisse", 17);
        Font subFont = Fonts.getFont("suisse", 11);
        Font textFont = Fonts.getFont("suisse", 11);
        Font smallFont = Fonts.getFont("suisse", 9);

        String currentUsername = mc.getSession() != null ? mc.getSession().getUsername() : "Player";

        // Compact Header Glass Panel
        float headerWidth = Math.min(width - 32, 540);
        float headerX = (width - headerWidth) / 2f;
        float headerY = 12f;
        float headerH = 38f;

        RenderUtils.drawGradientRect(matrices, headerX, headerY, headerWidth, headerH, 8f,
                0x7018142A, 0x70120F20, 0x70120F20, 0x7018142A);
        RenderUtils.drawRoundedRectOutline(matrices, headerX, headerY, headerWidth, headerH, 8f, 0.9f, 0x358B5CF6);

        // Back Button
        float backX = headerX + 8f;
        float backY = headerY + 8f;
        float backW = 58f;
        float backH = 22f;
        boolean backHover = HoveringUtils.isHovered(mouseX, mouseY, backX, backY, backW, backH);
        int backBg = backHover ? 0xCC7C3AED : 0x50251B40;
        RenderUtils.drawRoundedRect(matrices, backX, backY, backW, backH, 5f, backBg);
        RenderUtils.drawRoundedRectOutline(matrices, backX, backY, backW, backH, 5f, 0.8f, backHover ? 0xFFC084FC : 0x358B5CF6);
        subFont.drawCenteredString(matrices, "Назад", backX + backW / 2f, backY + 6f, 0xFFFFFFFF);

        // Title text
        float titleX = width / 2f;
        titleFont.drawCenteredString(matrices, "ALT MANAGER", titleX, headerY + 7f, 0xFFFFFFFF);
        int statusDotColor = 0xFF22C55E;
        RenderUtils.drawRoundCircle(matrices, titleX - 65f, headerY + 26f, 5f, statusDotColor);
        subFont.drawString(matrices, "Текущий ник: " + currentUsername, titleX - 56f, headerY + 22f, 0xFFC4B5FD);

        // Compact Main Layout: Left Accounts List & Right Actions Panel
        float contentY = headerY + headerH + 10f;
        float contentH = height - contentY - 16f;
        float listW = headerWidth * 0.58f;
        float actionW = headerWidth - listW - 10f;
        float listX = headerX;
        float actionX = listX + listW + 10f;

        // --- LEFT SIDE: Accounts List ---
        RenderUtils.drawGradientRect(matrices, listX, contentY, listW, contentH, 8f,
                0x60141024, 0x600E0B1A, 0x600E0B1A, 0x60141024);
        RenderUtils.drawRoundedRectOutline(matrices, listX, contentY, listW, contentH, 8f, 0.8f, 0x258B5CF6);

        subFont.drawString(matrices, "Сохраненные аккаунты", listX + 12f, contentY + 9f, 0xFFE2E8F0);

        AltStorage storage = Snill.INSTANCE.altStorage;
        List<Alt> alts = storage != null ? storage.getAlts() : List.of();

        // Scrollable region
        float cardStartY = contentY + 26f;
        float cardAreaH = contentH - 34f;
        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(listX, cardStartY, listW, cardAreaH);

        float currentCardY = cardStartY + scrollOffset;
        float cardH = 34f;
        float cardW = listW - 16f;
        float cardX = listX + 8f;

        for (int i = 0; i < alts.size(); i++) {
            Alt alt = alts.get(i);
            boolean isCurrent = alt.getUsername().equalsIgnoreCase(currentUsername);
            boolean cardHover = HoveringUtils.isHovered(mouseX, mouseY, cardX, currentCardY, cardW, cardH)
                    && mouseY >= cardStartY && mouseY <= cardStartY + cardAreaH;

            int cardBg = isCurrent ? 0x852E1065 : (cardHover ? 0x65201A38 : 0x40130F24);
            int borderCol = isCurrent ? 0xFFA855F7 : (cardHover ? 0x60A855F7 : 0x208B5CF6);

            RenderUtils.drawRoundedRect(matrices, cardX, currentCardY, cardW, cardH, 5f, cardBg);
            RenderUtils.drawRoundedRectOutline(matrices, cardX, currentCardY, cardW, cardH, 5f, 0.8f, borderCol);

            // Head avatar
            float headX = cardX + 6f;
            float headY = currentCardY + 5f;
            float headSize = 24f;
            RenderUtils.drawPlayerHead(matrices, alt.getUsername(), headX, headY, headSize, 3f);
            RenderUtils.drawRoundedRectOutline(matrices, headX, headY, headSize, headSize, 3f, 0.8f, 0x40A855F7);

            // Username
            subFont.drawString(matrices, alt.getUsername(), headX + headSize + 6f, currentCardY + 6f, isCurrent ? 0xFFF0ABFC : 0xFFFFFFFF);

            // Date / Status
            String dateStr = DATE_FORMAT.format(new Date(alt.getAddedDate()));
            smallFont.drawString(matrices, isCurrent ? "Активен" : dateStr, headX + headSize + 6f, currentCardY + 19f, isCurrent ? 0xFF4ADE80 : 0xFF94A3B8);

            // Delete button
            float btnDelW = 18f;
            float btnDelH = 18f;
            float btnDelX = cardX + cardW - btnDelW - 6f;
            float btnDelY = currentCardY + (cardH - btnDelH) / 2f;
            boolean delHover = HoveringUtils.isHovered(mouseX, mouseY, btnDelX, btnDelY, btnDelW, btnDelH)
                    && mouseY >= cardStartY && mouseY <= cardStartY + cardAreaH;

            RenderUtils.drawRoundedRect(matrices, btnDelX, btnDelY, btnDelW, btnDelH, 4f,
                    delHover ? 0xFFE11D48 : 0x85BE123C);
            RenderUtils.drawRoundedRectOutline(matrices, btnDelX, btnDelY, btnDelW, btnDelH, 4f, 0.8f,
                    delHover ? 0xFFFDA4AF : 0x40FB7185);

            float crossPad = 5.0f;
            float cx1 = btnDelX + crossPad;
            float cy1 = btnDelY + crossPad;
            float cx2 = btnDelX + btnDelW - crossPad;
            float cy2 = btnDelY + btnDelH - crossPad;

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderUtils.sonar);
            BufferBuilder crossBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            Matrix4f crossMatrix = matrices.peek().getPositionMatrix();
            int crossColor = delHover ? 0xFFFFFFFF : 0xFFFEE2E2;

            crossBuf.vertex(crossMatrix, cx1, cy1, 0f).color(crossColor);
            crossBuf.vertex(crossMatrix, cx2, cy2, 0f).color(crossColor);
            crossBuf.vertex(crossMatrix, cx2, cy1, 0f).color(crossColor);
            crossBuf.vertex(crossMatrix, cx1, cy2, 0f).color(crossColor);

            BufferRenderer.drawWithGlobalProgram(crossBuf.end());
            RenderSystem.defaultBlendFunc();

            if (!isCurrent) {
                float btnLoginW = 44f;
                float btnLoginH = 18f;
                float btnLoginX = btnDelX - btnLoginW - 4f;
                float btnLoginY = currentCardY + (cardH - btnLoginH) / 2f;
                boolean loginHover = HoveringUtils.isHovered(mouseX, mouseY, btnLoginX, btnLoginY, btnLoginW, btnLoginH)
                        && mouseY >= cardStartY && mouseY <= cardStartY + cardAreaH;

                int loginBg = loginHover ? 0xCC7C3AED : 0x457C3AED;
                RenderUtils.drawRoundedRect(matrices, btnLoginX, btnLoginY, btnLoginW, btnLoginH, 4f, loginBg);
                RenderUtils.drawRoundedRectOutline(matrices, btnLoginX, btnLoginY, btnLoginW, btnLoginH, 4f, 0.8f, loginHover ? 0xFFC084FC : 0x358B5CF6);
                smallFont.drawCenteredString(matrices, "Войти", btnLoginX + btnLoginW / 2f, btnLoginY + 5f, 0xFFFFFFFF);
            }

            currentCardY += cardH + 5f;
        }

        ScissorUtils.unset();
        ScissorUtils.pop();

        // Max scroll
        float totalCardsH = alts.size() * (cardH + 5f);
        float minScroll = Math.min(0f, cardAreaH - totalCardsH);
        if (targetScroll < minScroll) targetScroll = minScroll;
        if (targetScroll > 0f) targetScroll = 0f;

        // --- RIGHT SIDE: Action Controls Panel ---
        RenderUtils.drawGradientRect(matrices, actionX, contentY, actionW, contentH, 8f,
                0x60141024, 0x600E0B1A, 0x600E0B1A, 0x60141024);
        RenderUtils.drawRoundedRectOutline(matrices, actionX, contentY, actionW, contentH, 8f, 0.8f, 0x258B5CF6);

        float actPadX = actionX + 10f;
        float actPadW = actionW - 20f;
        float actCurY = contentY + 10f;

        subFont.drawString(matrices, "Добавить аккаунт", actPadX, actCurY, 0xFFE2E8F0);
        actCurY += 18f;

        // Input field
        float inputH = 26f;
        boolean inputHover = HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, inputH);
        int inputBg = typing ? 0x75241C42 : 0x40181330;
        int inputBorder = typing ? 0xFFA855F7 : (inputHover ? 0x608B5CF6 : 0x258B5CF6);

        RenderUtils.drawRoundedRect(matrices, actPadX, actCurY, actPadW, inputH, 5f, inputBg);
        RenderUtils.drawRoundedRectOutline(matrices, actPadX, actCurY, actPadW, inputH, 5f, 0.9f, inputBorder);

        String renderText = inputName.isEmpty() ? (typing ? "" : "Введите никнейм...") : inputName;
        int textColor = inputName.isEmpty() && !typing ? 0xFF64748B : 0xFFFFFFFF;
        textFont.drawString(matrices, renderText, actPadX + 7f, actCurY + 8f, textColor);

        if (typing && (System.currentTimeMillis() / 450) % 2 == 0) {
            float cursorX = actPadX + 7f + textFont.getStringWidth(inputName);
            RenderUtils.drawRoundedRect(matrices, cursorX + 1f, actCurY + 6f, 1.2f, 14f, 0f, 0xFFA855F7);
        }

        actCurY += inputH + 8f;

        // Button: Add & Login
        float btnH = 24f;
        boolean addHover = HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, btnH);
        int addBg = addHover ? 0xEE7C3AED : 0x806D28D9;
        RenderUtils.drawRoundedRect(matrices, actPadX, actCurY, actPadW, btnH, 5f, addBg);
        RenderUtils.drawRoundedRectOutline(matrices, actPadX, actCurY, actPadW, btnH, 5f, 0.9f, addHover ? 0xFFC084FC : 0x408B5CF6);
        subFont.drawCenteredString(matrices, "Войти и сохранить", actPadX + actPadW / 2f, actCurY + 7f, 0xFFFFFFFF);

        actCurY += btnH + 16f;
        smallFont.drawString(matrices, "БЫСТРЫЕ ДЕЙСТВИЯ", actPadX, actCurY, 0xFF94A3B8);
        actCurY += 12f;

        // Button: Random Nick
        boolean randHover = HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, btnH);
        int randBg = randHover ? 0x804338CA : 0x45312E81;
        RenderUtils.drawRoundedRect(matrices, actPadX, actCurY, actPadW, btnH, 5f, randBg);
        RenderUtils.drawRoundedRectOutline(matrices, actPadX, actCurY, actPadW, btnH, 5f, 0.8f, randHover ? 0xFF818CF8 : 0x304338CA);
        subFont.drawCenteredString(matrices, "🎲 Случайный ник", actPadX + actPadW / 2f, actCurY + 7f, 0xFFFFFFFF);

        actCurY += btnH + 6f;

        // Button: Clipboard
        boolean clipHover = HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, btnH);
        int clipBg = clipHover ? 0x800E7490 : 0x45155E75;
        RenderUtils.drawRoundedRect(matrices, actPadX, actCurY, actPadW, btnH, 5f, clipBg);
        RenderUtils.drawRoundedRectOutline(matrices, actPadX, actCurY, actPadW, btnH, 5f, 0.8f, clipHover ? 0xFF22D3EE : 0x300E7490);
        subFont.drawCenteredString(matrices, "📋 Из буфера обмена", actPadX + actPadW / 2f, actCurY + 7f, 0xFFFFFFFF);

        // Toast Notification
        if (System.currentTimeMillis() - statusMessageTime < 3000L && !statusMessage.isEmpty()) {
            float toastW = headerWidth * 0.7f;
            float toastH = 22f;
            float toastX = (width - toastW) / 2f;
            float toastY = height - toastH - 8f;
            int toastBg = statusSuccess ? 0xD014532D : 0xD07F1D1D;
            int toastBorder = statusSuccess ? 0xFF4ADE80 : 0xFFF87171;
            RenderUtils.drawRoundedRect(matrices, toastX, toastY, toastW, toastH, 5f, toastBg);
            RenderUtils.drawRoundedRectOutline(matrices, toastX, toastY, toastW, toastH, 5f, 0.8f, toastBorder);
            subFont.drawCenteredString(matrices, statusMessage, toastX + toastW / 2f, toastY + 5f, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        float headerWidth = Math.min(width - 32, 540);
        float headerX = (width - headerWidth) / 2f;
        float headerY = 12f;

        // Back Button
        float backX = headerX + 8f;
        float backY = headerY + 8f;
        float backW = 58f;
        float backH = 22f;
        if (HoveringUtils.isHovered(mouseX, mouseY, backX, backY, backW, backH)) {
            ClientSoundPlayer.playSound("closegui.wav", 0.7, 1.0f);
            mc.setScreen(parent);
            return true;
        }

        float contentY = headerY + 38f + 10f;
        float contentH = height - contentY - 16f;
        float listW = headerWidth * 0.58f;
        float actionW = headerWidth - listW - 10f;
        float listX = headerX;
        float actionX = listX + listW + 10f;

        // Accounts list clicks
        float cardStartY = contentY + 26f;
        float cardAreaH = contentH - 34f;
        float cardW = listW - 16f;
        float cardX = listX + 8f;
        float cardH = 34f;

        AltStorage storage = Snill.INSTANCE.altStorage;
        if (storage != null && HoveringUtils.isHovered(mouseX, mouseY, listX, cardStartY, listW, cardAreaH)) {
            List<Alt> alts = storage.getAlts();
            float currentCardY = cardStartY + scrollOffset;

            for (int i = 0; i < alts.size(); i++) {
                Alt alt = alts.get(i);
                float btnDelW = 18f;
                float btnDelH = 18f;
                float btnDelX = cardX + cardW - btnDelW - 6f;
                float btnDelY = currentCardY + (cardH - btnDelH) / 2f;

                if (HoveringUtils.isHovered(mouseX, mouseY, btnDelX, btnDelY, btnDelW, btnDelH)) {
                    storage.removeAlt(alt);
                    ClientSoundPlayer.playSound("closegui.wav", 0.6, 1.0f);
                    setStatus("Удален: " + alt.getUsername(), true);
                    return true;
                }

                if (HoveringUtils.isHovered(mouseX, mouseY, cardX, currentCardY, cardW, cardH)) {
                    if (storage.login(alt)) {
                        ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
                        setStatus("Успешный вход: " + alt.getUsername(), true);
                    }
                    return true;
                }

                currentCardY += cardH + 5f;
            }
        }

        // Right side: Input field
        float actPadX = actionX + 10f;
        float actPadW = actionW - 20f;
        float actCurY = contentY + 10f + 18f;
        float inputH = 26f;

        if (HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, inputH)) {
            typing = true;
            return true;
        } else {
            typing = false;
        }

        actCurY += inputH + 8f;
        float btnH = 24f;

        // Button: Add & Login
        if (HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, btnH)) {
            if (!inputName.trim().isEmpty()) {
                if (storage != null && storage.login(inputName.trim())) {
                    ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
                    setStatus("Успешный вход: " + inputName.trim(), true);
                    inputName = "";
                } else {
                    setStatus("Ошибка входа", false);
                }
            } else {
                setStatus("Введите никнейм!", false);
            }
            return true;
        }

        actCurY += btnH + 16f + 12f;

        // Button: Random Nick
        if (HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, btnH)) {
            if (storage != null) {
                String randomNick = storage.generateRandomNick();
                if (storage.login(randomNick)) {
                    ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
                    setStatus("Активирован: " + randomNick, true);
                }
            }
            return true;
        }

        actCurY += btnH + 6f;

        // Button: Clipboard
        if (HoveringUtils.isHovered(mouseX, mouseY, actPadX, actCurY, actPadW, btnH)) {
            String clip = mc.keyboard.getClipboard();
            if (clip != null && !clip.trim().isEmpty()) {
                String clean = clip.trim().replaceAll("[^a-zA-Z0-9_]", "");
                if (!clean.isEmpty()) {
                    if (storage != null && storage.login(clean)) {
                        ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
                        setStatus("Вход из буфера: " + clean, true);
                    }
                } else {
                    setStatus("Некорректный ник", false);
                }
            } else {
                setStatus("Буфер обмена пуст", false);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetScroll += (float) verticalAmount * 22f;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!inputName.isEmpty()) {
                    inputName = inputName.substring(0, inputName.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!inputName.trim().isEmpty() && Snill.INSTANCE.altStorage != null) {
                    Snill.INSTANCE.altStorage.login(inputName.trim());
                    ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
                    setStatus("Успешный вход: " + inputName.trim(), true);
                    inputName = "";
                    typing = false;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                typing = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            mc.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (typing) {
            if (Character.isLetterOrDigit(chr) || chr == '_') {
                if (inputName.length() < 16) {
                    inputName += chr;
                }
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void setStatus(String message, boolean success) {
        this.statusMessage = message;
        this.statusSuccess = success;
        this.statusMessageTime = System.currentTimeMillis();
    }
}
