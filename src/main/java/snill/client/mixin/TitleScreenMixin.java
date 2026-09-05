package snill.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.client.ClientSoundPlayer;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.ui.altmanager.AltManagerScreen;
import snill.client.client.ui.space.SpaceBackgroundRenderer;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static SpaceBackgroundRenderer snill$spaceBackground;

    @Unique
    private final AnimationUtils[] snill$btnAnimations = new AnimationUtils[]{
            new AnimationUtils(0f, 10f, Easings.CUBIC_OUT),
            new AnimationUtils(0f, 10f, Easings.CUBIC_OUT),
            new AnimationUtils(0f, 10f, Easings.CUBIC_OUT),
            new AnimationUtils(0f, 10f, Easings.CUBIC_OUT),
            new AnimationUtils(0f, 10f, Easings.CUBIC_OUT)
    };

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (snill$spaceBackground == null) {
            snill$spaceBackground = new SpaceBackgroundRenderer();
        }
        this.clearChildren();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (snill$spaceBackground == null) {
            snill$spaceBackground = new SpaceBackgroundRenderer();
        }

        MatrixStack matrices = context.getMatrices();
        int width = this.width;
        int height = this.height;

        // 1. Clean live Space Background
        snill$spaceBackground.render(context, mouseX, mouseY, delta);

        Font logoFont = Fonts.getFont("suisse", 38);
        Font subLogoFont = Fonts.getFont("suisse", 10);
        Font bugFont = Fonts.getFont("suisse", 9);
        Font btnFont = Fonts.getFont("suisse", 12);
        Font badgeFont = Fonts.getFont("suisse", 11);

        float centerX = width / 2f;
        float logoY = height * 0.16f;

        // 2. Big clean logo "SNILL" with subtle drop shadow
        logoFont.drawCenteredString(matrices, "SNILL", centerX + 1f, logoY + 1f, 0x60000000);
        logoFont.drawCenteredString(matrices, "SNILL", centerX, logoY, 0xFFFFFFFF);

        // Subtitle badge "Бета версия"
        float subW = 95f;
        float subH = 15f;
        float subY = logoY + 34f;
        RenderUtils.drawGradientRect(matrices, centerX - subW / 2f, subY, subW, subH, 4f,
                0x5018142A, 0x50120F20, 0x50120F20, 0x5018142A);
        RenderUtils.drawRoundedRectOutline(matrices, centerX - subW / 2f, subY, subW, subH, 4f, 0.8f, 0x408B5CF6);
        subLogoFont.drawCenteredString(matrices, "Бета версия", centerX, subY + 4f, 0xFFC4B5FD);

        // Subtitle note "Возможны баги"
        float bugWarningY = subY + subH + 3f;
        bugFont.drawCenteredString(matrices, "Возможны баги", centerX, bugWarningY, 0x95FCA5A5);

        // 3. Compact Main Menu Glassmorphism Action Buttons
        String[] btnTitles = new String[]{
                "⚔   Одиночная игра",
                "🌐   Сетевая игра",
                "👤   Alt Manager",
                "⚙   Настройки",
                "🚪   Выход из игры"
        };

        float btnW = 175f;
        float btnH = 27f;
        float btnSpacing = 6f;
        float totalButtonsH = btnTitles.length * btnH + (btnTitles.length - 1) * btnSpacing;
        float startBtnY = bugWarningY + 16f;

        // Container Panel
        float containerPad = 9f;
        float contX = centerX - btnW / 2f - containerPad;
        float contY = startBtnY - containerPad;
        float contW = btnW + containerPad * 2f;
        float contH = totalButtonsH + containerPad * 2f;
        RenderUtils.drawGradientRect(matrices, contX, contY, contW, contH, 10f,
                0x55141026, 0x550C0918, 0x550C0918, 0x55141026);
        RenderUtils.drawRoundedRectOutline(matrices, contX, contY, contW, contH, 10f, 1.0f, 0x258B5CF6);

        float currentBtnY = startBtnY;
        for (int i = 0; i < btnTitles.length; i++) {
            float btnX = centerX - btnW / 2f;
            boolean hovered = HoveringUtils.isHovered(mouseX, mouseY, btnX, currentBtnY, btnW, btnH);

            AnimationUtils anim = snill$btnAnimations[i];
            anim.update(hovered ? 1f : 0f);
            float hProgress = anim.getValue();

            int bgBase = i == 2 ? 0x504A154B : 0x35161226;
            int bgHover = i == 2 ? 0xCC7C3AED : (i == 4 ? 0xBBE11D48 : 0x906D28D9);
            int curBg = ColorUtils.interpolateColor(bgBase, bgHover, hProgress);

            int borderCol = ColorUtils.interpolateColor(0x258B5CF6, 0xFFA855F7, hProgress);

            RenderUtils.drawRoundedRect(matrices, btnX, currentBtnY, btnW, btnH, 6f, curBg);
            RenderUtils.drawRoundedRectOutline(matrices, btnX, currentBtnY, btnW, btnH, 6f, 0.8f + hProgress * 0.3f, borderCol);

            float textOffset = hProgress * 1.5f;
            int textCol = ColorUtils.interpolateColor(0xFFE2E8F0, 0xFFFFFFFF, hProgress);
            btnFont.drawCenteredString(matrices, btnTitles[i], centerX + textOffset, currentBtnY + 9f, textCol);

            currentBtnY += btnH + btnSpacing;
        }

        // 4. Compact Profile Badge (Top Right)
        String username = client != null && client.getSession() != null ? client.getSession().getUsername() : "Player";
        float badgeW = 140f;
        float badgeH = 30f;
        float badgeX = width - badgeW - 12f;
        float badgeY = 12f;
        boolean badgeHover = HoveringUtils.isHovered(mouseX, mouseY, badgeX, badgeY, badgeW, badgeH);

        int badgeBg = badgeHover ? 0x80241C42 : 0x45141026;
        RenderUtils.drawRoundedRect(matrices, badgeX, badgeY, badgeW, badgeH, 6f, badgeBg);
        RenderUtils.drawRoundedRectOutline(matrices, badgeX, badgeY, badgeW, badgeH, 6f, 0.8f, badgeHover ? 0xFFA855F7 : 0x258B5CF6);

        float headSize = 20f;
        float headX = badgeX + 5f;
        float headY = badgeY + (badgeH - headSize) / 2f;
        RenderUtils.drawPlayerHead(matrices, username, headX, headY, headSize, 3f);
        RenderUtils.drawRoundedRectOutline(matrices, headX, headY, headSize, headSize, 3f, 0.8f, 0x50A855F7);

        badgeFont.drawString(matrices, username, headX + headSize + 6f, badgeY + 6f, 0xFFFFFFFF);
        RenderUtils.drawRoundCircle(matrices, headX + headSize + 7f, badgeY + 20f, 4f, 0xFF22C55E);
        Fonts.getFont("suisse", 9).drawString(matrices, "Сменить аккаунт", headX + headSize + 13f, badgeY + 18f, 0xFF94A3B8);

        // 5. Footer info
        Fonts.getFont("suisse", 10).drawString(matrices, "SNILL Client • Fabric 1.21.4", 10f, height - 14f, 0x7094A3B8);
        Fonts.getFont("suisse", 10).drawRight(matrices, "Made by Llovy12XC", width - 10f, height - 14f, 0x8094A3B8);

        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        int width = this.width;
        int height = this.height;
        float centerX = width / 2f;
        float logoY = height * 0.16f;
        float subY = logoY + 34f;
        float subH = 15f;
        float bugWarningY = subY + subH + 3f;
        float startBtnY = bugWarningY + 16f;

        float btnW = 175f;
        float btnH = 27f;
        float btnSpacing = 6f;

        // Button 0: Singleplayer
        if (HoveringUtils.isHovered(mouseX, mouseY, centerX - btnW / 2f, startBtnY, btnW, btnH)) {
            ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
            if (this.client != null) {
                this.client.setScreen(new SelectWorldScreen((TitleScreen) (Object) this));
            }
            cir.setReturnValue(true);
            return;
        }

        // Button 1: Multiplayer
        float btn1Y = startBtnY + (btnH + btnSpacing);
        if (HoveringUtils.isHovered(mouseX, mouseY, centerX - btnW / 2f, btn1Y, btnW, btnH)) {
            ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
            if (this.client != null) {
                this.client.setScreen(new MultiplayerScreen((TitleScreen) (Object) this));
            }
            cir.setReturnValue(true);
            return;
        }

        // Button 2: Alt Manager
        float btn2Y = startBtnY + (btnH + btnSpacing) * 2f;
        if (HoveringUtils.isHovered(mouseX, mouseY, centerX - btnW / 2f, btn2Y, btnW, btnH)) {
            ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
            if (this.client != null) {
                this.client.setScreen(new AltManagerScreen((TitleScreen) (Object) this));
            }
            cir.setReturnValue(true);
            return;
        }

        // Button 3: Options
        float btn3Y = startBtnY + (btnH + btnSpacing) * 3f;
        if (HoveringUtils.isHovered(mouseX, mouseY, centerX - btnW / 2f, btn3Y, btnW, btnH)) {
            ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
            if (this.client != null) {
                this.client.setScreen(new OptionsScreen((TitleScreen) (Object) this, this.client.options));
            }
            cir.setReturnValue(true);
            return;
        }

        // Button 4: Quit
        float btn4Y = startBtnY + (btnH + btnSpacing) * 4f;
        if (HoveringUtils.isHovered(mouseX, mouseY, centerX - btnW / 2f, btn4Y, btnW, btnH)) {
            ClientSoundPlayer.playSound("closegui.wav", 0.7, 1.0f);
            if (this.client != null) {
                this.client.scheduleStop();
            }
            cir.setReturnValue(true);
            return;
        }

        // Profile Badge Click -> Open Alt Manager
        float badgeW = 140f;
        float badgeH = 30f;
        float badgeX = width - badgeW - 12f;
        float badgeY = 12f;
        if (HoveringUtils.isHovered(mouseX, mouseY, badgeX, badgeY, badgeW, badgeH)) {
            ClientSoundPlayer.playSound("clickguiopen.wav", 0.7, 1.0f);
            if (this.client != null) {
                this.client.setScreen(new AltManagerScreen((TitleScreen) (Object) this));
            }
            cir.setReturnValue(true);
        }
    }
}
