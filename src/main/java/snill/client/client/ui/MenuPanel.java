package snill.client.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import snill.client.Snill;
import snill.client.api.QClient;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.client.ClientSoundPlayer;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.Module;
import snill.client.client.ui.clickgui.ClickGuiInputHandler;
import snill.client.client.ui.clickgui.ClickGuiLayout;
import snill.client.client.ui.clickgui.ClickGuiRenderer;
import snill.client.client.ui.clickgui.ClickGuiSettingRenderer;
import snill.client.client.ui.clickgui.ClickGuiState;
import snill.client.client.ui.clickgui.ClickGuiThemeSelector;

import java.util.EnumMap;
import java.util.Map;

public class MenuPanel extends Screen implements QClient {

    private static final float SLIDE_SPEED        = 8f;
    private static final float ALPHA_SPEED        = 5f;
    private static final float OPEN_SPEED         = 7.5f;
    private static final float CLOSE_SPEED        = 7.5f;
    private static final float SLIDE_OFFSET       = 220f;
    private static final float PANEL_OFFSET_Y_MUL = 22.0f;

    private static final ClickGuiState SHARED_STATE = new ClickGuiState();
    private final int categoryCount = Module.ModuleCategory.values().length;
    private final ClickGuiState state = SHARED_STATE;
    private final ClickGuiThemeSelector themeSelector = new ClickGuiThemeSelector();
    private final ClickGuiRenderer renderer = new ClickGuiRenderer(state, new ClickGuiSettingRenderer(), themeSelector, this);
    private final ClickGuiInputHandler inputHandler = new ClickGuiInputHandler(state, themeSelector);
    private final AnimationUtils openAnimation = new AnimationUtils(0f, OPEN_SPEED, Easings.CUBIC_OUT);

    private final Map<Module.ModuleCategory, AnimationUtils> panelSlideX = new EnumMap<>(Module.ModuleCategory.class);
    private final Map<Module.ModuleCategory, AnimationUtils> panelSlideY = new EnumMap<>(Module.ModuleCategory.class);
    private final Map<Module.ModuleCategory, AnimationUtils> panelAlpha  = new EnumMap<>(Module.ModuleCategory.class);

    private boolean closing;
    private boolean closeSoundPlayed;

    public MenuPanel() {
        super(Text.of("SNILL"));
        state.refreshModules();
        initPanelAnimations();
    }

    private void initPanelAnimations() {
        for (Module.ModuleCategory category : Module.ModuleCategory.values()) {
            panelSlideX.put(category, new AnimationUtils(getStartOffX(category), SLIDE_SPEED, Easings.QUINT_OUT));
            panelSlideY.put(category, new AnimationUtils(getStartOffY(category), SLIDE_SPEED, Easings.QUINT_OUT));
            panelAlpha.put(category, new AnimationUtils(0f, ALPHA_SPEED, Easings.BOUNCE_OUT));
        }
    }

    private void resetPanelSlides() {
        for (Module.ModuleCategory category : Module.ModuleCategory.values()) {
            AnimationUtils sx    = panelSlideX.get(category);
            AnimationUtils sy    = panelSlideY.get(category);
            AnimationUtils alpha = panelAlpha.get(category);
            if (sx    != null) sx.setValue(getStartOffX(category));
            if (sy    != null) sy.setValue(getStartOffY(category));
            if (alpha != null) alpha.setValue(0f);
        }
    }

    private float getStartOffX(Module.ModuleCategory category) {
        return switch (category) {
            case COMBAT -> -SLIDE_OFFSET;
            case MISC -> SLIDE_OFFSET;
            default -> 0f;
        };
    }

    private float getStartOffY(Module.ModuleCategory category) {
        return switch (category) {
            case MOVEMENT, PLAYER -> -SLIDE_OFFSET;
            case RENDER           ->  SLIDE_OFFSET;
            default               ->  0f;
        };
    }

    public float getPanelSlideOffsetX(Module.ModuleCategory category) {
        AnimationUtils sx = panelSlideX.get(category);
        if (sx == null) return 0f;
        if (!closing) sx.update(0f);
        return sx.getValue();
    }

    public float getPanelSlideOffsetY(Module.ModuleCategory category) {
        AnimationUtils sy = panelSlideY.get(category);
        if (sy == null) return 0f;
        if (!closing) sy.update(0f);
        return sy.getValue();
    }

    public float getPanelAlpha(Module.ModuleCategory category) {
        AnimationUtils alpha = panelAlpha.get(category);
        if (alpha == null) return 1f;
        if (!closing) alpha.update(1f);
        return MathHelper.clamp((float) alpha.getValue(), 0f, 1f);
    }

    private Window getWindow() {
        return mc == null ? null : mc.getWindow();
    }

    public float getGuiScale() {
        Window window = getWindow();
        if (window == null) return 0.80f;

        float totalWidth = ClickGuiLayout.getTotalCategoriesWidth(categoryCount) + 30f;
        float totalHeight = ClickGuiLayout.HEIGHT + ClickGuiLayout.SEARCH_GAP + ClickGuiLayout.SEARCH_HEIGHT + ClickGuiLayout.THEME_PANEL_H + 45f;

        float scaleX = (float) window.getScaledWidth() / totalWidth;
        float scaleY = (float) window.getScaledHeight() / totalHeight;
        float fitScale = Math.min(scaleX, scaleY) * 0.90f;

        return Math.min(0.80f, fitScale);
    }

    private void syncLayout() {
        Window window = getWindow();
        if (window != null) {
            float guiScale = getGuiScale();
            state.updatePosition(window, categoryCount, guiScale);
        }
    }

    @Override
    protected void init() {
        super.init();
        resetPanelSlides();
        openAnimation.setValue(0f);
        openAnimation.setEasing(Easings.CUBIC_OUT);
        closing = false;
        closeSoundPlayed = false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Window window = getWindow();
        if (window == null) return;

        updateAnimation();
        float progress = getAnimationProgress();

        if (closing && progress <= 0.001f) {
            if (mc != null) mc.setScreen(null);
            return;
        }

        float guiScale = getGuiScale();
        state.updatePosition(window, categoryCount, guiScale);
        state.setRenderOffsetY(getPanelOffsetY(progress));

        float invScale = 1.0f / guiScale;
        int scaledMouseX = (int) (mouseX * invScale);
        int scaledMouseY = (int) (mouseY * invScale);

        ScissorUtils.setCustomScale(guiScale);

        context.getMatrices().push();
        context.getMatrices().scale(guiScale, guiScale, 1.0f);

        renderer.render(context, scaledMouseX, scaledMouseY, window, progress);

        context.getMatrices().pop();

        ScissorUtils.setCustomScale(1.0);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        syncLayout();
        state.setRenderOffsetY(getPanelOffsetY(getAnimationProgress()));
        float guiScale = getGuiScale();
        double scaledMouseX = mouseX / guiScale;
        double scaledMouseY = mouseY / guiScale;
        return inputHandler.mouseClicked(scaledMouseX, scaledMouseY, button, getWindow())
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (closing) return true;
        syncLayout();
        return inputHandler.mouseReleased(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (closing) return true;
        syncLayout();
        state.setRenderOffsetY(getPanelOffsetY(getAnimationProgress()));
        float guiScale = getGuiScale();
        double scaledMouseX = mouseX / guiScale;
        double scaledMouseY = mouseY / guiScale;
        return inputHandler.mouseDragged(scaledMouseX, scaledMouseY, button)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing) return true;
        syncLayout();
        state.setRenderOffsetY(getPanelOffsetY(getAnimationProgress()));
        float guiScale = getGuiScale();
        double scaledMouseX = mouseX / guiScale;
        double scaledMouseY = mouseY / guiScale;
        return inputHandler.mouseScrolled(scaledMouseX, scaledMouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (inputHandler.keyPressed(keyCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startClosing();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (closing) return true;
        return inputHandler.charTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        startClosing();
    }

    @Override
    public void removed() {
        ScissorUtils.setCustomScale(1.0);
        if (!closeSoundPlayed) {
            closeSoundPlayed = true;
            ClientSoundPlayer.playSound("closegui.wav", 0.6, 1.0f);
        }
        if (Snill.INSTANCE != null && Snill.INSTANCE.configStorage != null) {
            new Thread(() -> {
                try {
                    Snill.INSTANCE.configStorage.saveAll();
                } catch (Exception ignored) {
                }
            }, "Snill-ConfigAutoSaver").start();
        }
        super.removed();
    }

    private void startClosing() {
        if (closing) return;
        closing = true;
        openAnimation.setEasing(Easings.CUBIC_IN);
        if (!closeSoundPlayed) {
            closeSoundPlayed = true;
            ClientSoundPlayer.playSound("closegui.wav", 0.6, 1.0f);
        }
    }

    private void updateAnimation() {
        if (closing) {
            openAnimation.update(0.0f);
        } else {
            openAnimation.setEasing(Easings.CUBIC_OUT);
            openAnimation.update(1.0f);
        }
    }

    private float getAnimationProgress() {
        return MathHelper.clamp(openAnimation.getValue(), 0.0f, 1.0f);
    }

    private float getPanelOffsetY(float progress) {
        return (1.0f - progress) * PANEL_OFFSET_Y_MUL;
    }
}
