package snill.client.client.modules.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.events.implement.EventRender;
import snill.client.api.events.implement.EventTickPre;
import snill.client.api.utils.browser.BrowserEngine;
import snill.client.api.utils.browser.BrowserScreenRenderer;
import snill.client.api.utils.browser.WorldScreen;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class Browser extends Module {

    public static Browser INSTANCE = new Browser();

    private final FloatSetting width = new FloatSetting("Ширина экрана", 5.2f, 2.0f, 12.0f, 0.2f);
    private final FloatSetting distance = new FloatSetting("Дистанция", 4.0f, 1.5f, 10.0f, 0.5f);
    private final ModeSetting browserType = new ModeSetting("Браузер", "Edge", "Edge", "Brave");
    private final ModeSetting laserColorMode = new ModeSetting("Цвет лазера", "Тема",
            "Тема", "Бирюзовый", "Фиолетовый", "Белый", "Красный", "Золотой");
    private final ModeSetting defaultSite = new ModeSetting("Стартовая", "YouTube",
            "YouTube", "VK Music", "Google", "Twitch");
    private final BooleanSetting resetPosition = new BooleanSetting("Сбросить позицию", false);

    private WorldScreen worldScreen;
    private boolean prevLeftMouse = false;
    private boolean prevRightMouse = false;
    private boolean urlTypingMode = false;
    private boolean webTypingMode = false;
    private String urlTextBuffer = "";

    public boolean isTypingMode() {
        return isEnable() && (urlTypingMode || webTypingMode);
    }

    public boolean isUrlTypingMode() {
        return isEnable() && urlTypingMode;
    }

    public boolean isWebTypingMode() {
        return isEnable() && webTypingMode;
    }

    public String getUrlTextBuffer() {
        return urlTextBuffer;
    }

    public void setWebTypingMode(boolean webTypingMode) {
        this.webTypingMode = webTypingMode;
        if (webTypingMode) {
            this.urlTypingMode = false;
        }
    }

    public void startUrlTyping() {
        this.urlTypingMode = true;
        this.webTypingMode = false;
        this.urlTextBuffer = "";
        BrowserEngine.getInstance().blurActiveElement();
    }

    public void stopAllTyping() {
        this.urlTypingMode = false;
        this.webTypingMode = false;
        this.urlTextBuffer = "";
        BrowserEngine.getInstance().blurActiveElement();
    }

    public Browser() {
        super("Browser", "Физический браузер в 3D мире с VR-указкой", ModuleCategory.RENDER);
        addSettings(width, distance, browserType, laserColorMode, defaultSite, resetPosition);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        stopAllTyping();
        String initialUrl = switch (defaultSite.getCurrent()) {
            case "VK Music" -> "https://vk.com/audio";
            case "Google" -> "https://www.google.com";
            case "Twitch" -> "https://www.twitch.tv";
            default -> "https://www.youtube.com";
        };

        if (width.get() < 4.5f) {
            width.setValue(5.5f);
        }

        BrowserEngine.getInstance().start(initialUrl, browserType.getCurrent());

        if (mc.player != null) {
            Camera camera = mc.gameRenderer.getCamera();
            Vec3d eyePos = camera != null ? camera.getPos() : mc.player.getEyePos();
            float yaw = camera != null ? camera.getYaw() : mc.player.getYaw();
            float pitch = camera != null ? camera.getPitch() : mc.player.getPitch();

            worldScreen = new WorldScreen(eyePos, yaw, pitch, width.get());
            worldScreen.spawnInFrontOf(eyePos, yaw, pitch, distance.get());
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        stopAllTyping();
        BrowserEngine.getInstance().stop();
        if (worldScreen != null) {
            worldScreen.setDragging(false);
        }
    }

    @EventLink
    public void onTick(EventTickPre event) {
        if (!isEnable() || worldScreen == null || mc.player == null) return;

        worldScreen.setDimensions(width.get());

        if (resetPosition.isState()) {
            resetPosition.setState(false);
            Camera camera = mc.gameRenderer.getCamera();
            Vec3d eyePos = camera != null ? camera.getPos() : mc.player.getEyePos();
            float yaw = camera != null ? camera.getYaw() : mc.player.getYaw();
            float pitch = camera != null ? camera.getPitch() : mc.player.getPitch();
            worldScreen.spawnInFrontOf(eyePos, yaw, pitch, distance.get());
        }
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (!isEnable() || mc.player == null || mc.world == null) return;

        Camera camera = event.getCamera();
        Vec3d camPos = camera.getPos();

        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float yawRad = (float) Math.toRadians(camera.getYaw());

        float lx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float ly = -MathHelper.sin(pitchRad);
        float lz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d lookDir = new Vec3d(lx, ly, lz).normalize();

        if (worldScreen == null) {
            worldScreen = new WorldScreen(camPos, camera.getYaw(), camera.getPitch(), width.get());
            worldScreen.spawnInFrontOf(camPos, camera.getYaw(), camera.getPitch(), distance.get());
        }

        // 1. Вычисляем пересечение луча взгляда с плоскостью экрана
        WorldScreen.RayHit hit = worldScreen.intersect(camPos, lookDir);

        // 2. Управление перетаскиванием экрана в реальном времени
        long window = mc.getWindow().getHandle();
        boolean rightMouse = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (worldScreen.isDragging()) {
            if (rightMouse) {
                worldScreen.updateDrag(camPos, lookDir, camera.getYaw(), camera.getPitch());
            } else {
                worldScreen.setDragging(false);
            }
        }

        // 2.1. Подавление атаки в Minecraft и отслеживание курсора при наведении на экран
        if (hit != null && !worldScreen.isDragging()) {
            if (mc.options.attackKey.isPressed()) {
                mc.options.attackKey.setPressed(false);
                while (mc.options.attackKey.wasPressed()) {}
                if (mc.interactionManager != null) {
                    mc.interactionManager.cancelBlockBreaking();
                }
            }

            boolean fs = BrowserEngine.getInstance().isFullscreen();
            float pixelH = worldScreen.getHeight() / BrowserScreenRenderer.GUI_SCALE;
            float pixelBarH = fs ? 0f : pixelH * 0.12f;
            float py = hit.v * pixelH;

            if (fs || py > pixelBarH) {
                float webV = fs ? MathHelper.clamp(hit.v, 0f, 1f) : MathHelper.clamp((py - pixelBarH) / (pixelH - pixelBarH), 0f, 1f);
                BrowserEngine engine = BrowserEngine.getInstance();
                float vw = engine.getViewportWidth();
                float vh = engine.getViewportHeight();
                int bx = (int) MathHelper.clamp(hit.u * vw, 0f, vw);
                int by = (int) MathHelper.clamp(webV * vh, 0f, vh);
                engine.mouseMove(bx, by);
            }
        }

        // 3. Отрисовка экрана и VR-указки
        int color = getLaserColor();
        BrowserScreenRenderer.renderScreen(event, worldScreen, hit, color);
    }

    public static boolean handleMouseButton(int button, int action) {
        Browser browser = Browser.INSTANCE;
        if (browser == null || !browser.isEnable() || browser.worldScreen == null || mc.player == null) return false;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return false;

        Vec3d camPos = camera.getPos();
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float yawRad = (float) Math.toRadians(camera.getYaw());

        float lx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float ly = -MathHelper.sin(pitchRad);
        float lz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d lookDir = new Vec3d(lx, ly, lz).normalize();

        WorldScreen.RayHit hit = browser.worldScreen.intersect(camPos, lookDir);

        // 1. Управление перетаскиванием экрана (ПКМ)
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW.GLFW_PRESS && hit != null) {
                browser.worldScreen.setDragging(true);
                browser.worldScreen.setDragDistance(Math.max(1.5, Math.min(10.0, hit.distance)));
                return true;
            } else if (action == GLFW.GLFW_RELEASE && browser.worldScreen.isDragging()) {
                browser.worldScreen.setDragging(false);
                return true;
            } else if (browser.worldScreen.isDragging()) {
                return true;
            }
        }

        // Если не целимся в экран и не перетаскиваем его
        if (hit == null) {
            if (action == GLFW.GLFW_PRESS && browser.isTypingMode()) {
                browser.stopAllTyping();
            }
            return false;
        }

        // 2. Обработка клика ЛКМ
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Подавляем атаку в игре, чтобы персонаж не бил рукой и не ломал блоки
            mc.options.attackKey.setPressed(false);
            while (mc.options.attackKey.wasPressed()) {}
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }

            if (action == GLFW.GLFW_PRESS) {
                browser.handleClick(hit.u, hit.v);
            }
            return true;
        }

        return false;
    }

    public static boolean handleMouseScroll(double vertical) {
        Browser browser = Browser.INSTANCE;
        if (browser == null || !browser.isEnable() || browser.worldScreen == null || mc.player == null) return false;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return false;

        Vec3d camPos = camera.getPos();
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float yawRad = (float) Math.toRadians(camera.getYaw());

        float lx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float ly = -MathHelper.sin(pitchRad);
        float lz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d lookDir = new Vec3d(lx, ly, lz).normalize();

        WorldScreen.RayHit hit = browser.worldScreen.intersect(camPos, lookDir);
        if (hit == null) return false;

        boolean fs = BrowserEngine.getInstance().isFullscreen();
        float pixelH = browser.worldScreen.getHeight() / BrowserScreenRenderer.GUI_SCALE;
        float pixelBarH = fs ? 0f : pixelH * 0.12f;
        float py = hit.v * pixelH;

        if (fs || py > pixelBarH) {
            float webV = fs ? MathHelper.clamp(hit.v, 0f, 1f) : MathHelper.clamp((py - pixelBarH) / (pixelH - pixelBarH), 0f, 1f);
            BrowserEngine engine = BrowserEngine.getInstance();
            float vw = engine.getViewportWidth();
            float vh = engine.getViewportHeight();
            int bx = (int) MathHelper.clamp(hit.u * vw, 0f, vw);
            int by = (int) MathHelper.clamp(webV * vh, 0f, vh);
            engine.mouseWheel(bx, by, -(int) (vertical * 120.0));
        }

        return true;
    }

    private void handleClick(float u, float v) {
        if (worldScreen == null) return;

        BrowserEngine engine = BrowserEngine.getInstance();
        boolean fs = engine.isFullscreen();
        float pixelW = worldScreen.getWidth() / BrowserScreenRenderer.GUI_SCALE;
        float pixelH = worldScreen.getHeight() / BrowserScreenRenderer.GUI_SCALE;
        float pixelBarH = fs ? 0f : pixelH * 0.12f;

        float px = u * pixelW;
        float py = v * pixelH;

        BrowserEngine.log("[Click] u=" + String.format("%.3f", u) + " v=" + String.format("%.3f", v) + " px=" + (int) px + " py=" + (int) py + " fs=" + fs);

        if (!fs && py <= pixelBarH) {
            handleHeaderClick(px, py, pixelW, pixelH, pixelBarH);
        } else {
            // В полноэкранном режиме клик по верхнему краю (по подсказке) выходит из полноэкранного режима
            if (fs && py <= pixelH * 0.08f && px >= pixelW * 0.30f && px <= pixelW * 0.70f) {
                engine.exitFullscreen();
                BrowserEngine.log("[Fullscreen] Exited via top button click");
                return;
            }

            if (urlTypingMode) {
                urlTypingMode = false;
                urlTextBuffer = "";
            }

            float webV = fs ? MathHelper.clamp(v, 0f, 1f) : MathHelper.clamp((py - pixelBarH) / (pixelH - pixelBarH), 0f, 1f);
            float vw = engine.getViewportWidth();
            float vh = engine.getViewportHeight();
            int bx = (int) MathHelper.clamp(u * vw, 0f, vw);
            int by = (int) MathHelper.clamp(webV * vh, 0f, vh);

            BrowserEngine.log("[WebClick] Sending click to CDP: (" + bx + ", " + by + ") [viewport: " + (int) vw + "x" + (int) vh + "]");
            engine.click(bx, by);
            engine.checkInputFocus();
        }
    }

    private void handleHeaderClick(float px, float py, float width, float height, float barH) {
        BrowserEngine engine = BrowserEngine.getInstance();

        float tabW = 220.0f;
        float tabH = barH * 0.45f;
        float tabX = 18.0f;
        float tabY = 5.0f;

        // 1. Кнопка закрыть [✕] (вверху справа)
        if (px >= width - 48.0f && py <= tabH + 8.0f) {
            BrowserEngine.log("[Header] Close [✕] clicked -> toggle browser");
            stopAllTyping();
            toggle();
            return;
        }

        // 2. Кнопка свернуть [—] (левее закрытия)
        if (px >= width - 80.0f && px < width - 48.0f && py <= tabH + 8.0f) {
            BrowserEngine.log("[Header] Minimize [—] clicked -> toggle browser");
            stopAllTyping();
            toggle();
            return;
        }

        // 3. Кнопка новой вкладки [+] (правее активной вкладки)
        float plusBtnX = tabX + tabW + 18.0f;
        float plusBtnY = tabY + tabH * 0.5f;
        if (Math.abs(px - plusBtnX) <= 18.0f && Math.abs(py - plusBtnY) <= 18.0f) {
            BrowserEngine.log("[Header] New tab [+] clicked -> Google");
            stopAllTyping();
            engine.navigate("https://www.google.com");
            return;
        }

        // 4. Закрытие активной вкладки [x]
        if (px >= tabX + tabW - 25.0f && px <= tabX + tabW && py >= tabY && py <= tabY + tabH) {
            BrowserEngine.log("[Header] Active tab [x] clicked -> Google");
            stopAllTyping();
            engine.navigate("https://www.google.com");
            return;
        }

        float navY = tabH + 8.0f;
        float navH = barH - tabH - 14.0f;
        float btnSize = navH;

        // 5. Кнопка [◀] Назад
        float backX = 18.0f;
        if (px >= 10.0f && px <= backX + btnSize + 5.0f && py >= navY - 4.0f && py <= navY + navH + 4.0f) {
            BrowserEngine.log("[Header] Back clicked");
            stopAllTyping();
            engine.goBack();
            return;
        }

        // 6. Кнопка [▶] Вперед
        float fwdX = backX + btnSize + 10.0f;
        if (px >= fwdX - 4.0f && px <= fwdX + btnSize + 5.0f && py >= navY - 4.0f && py <= navY + navH + 4.0f) {
            BrowserEngine.log("[Header] Forward clicked");
            stopAllTyping();
            engine.goForward();
            return;
        }

        // 7. Кнопка [⟳] Обновить
        float reloadX = fwdX + btnSize + 10.0f;
        if (px >= reloadX - 4.0f && px <= reloadX + btnSize + 5.0f && py >= navY - 4.0f && py <= navY + navH + 4.0f) {
            BrowserEngine.log("[Header] Reload clicked");
            stopAllTyping();
            engine.reload();
            return;
        }

        // 8. Кнопка [🔊 Звук]
        float soundW = 76.0f;
        float bmW = 88.0f;
        float bookmarkX = width - bmW - 18.0f;
        float soundX = bookmarkX - soundW - 10.0f;

        if (px >= soundX - 5.0f && px <= soundX + soundW + 5.0f && py >= navY - 4.0f && py <= navY + navH + 4.0f) {
            BrowserEngine.log("[Header] Sound unmute clicked");
            engine.unmuteAudio();
            return;
        }

        // 9. Кнопка закладки: ТОЛЬКО [YouTube]
        if (px >= bookmarkX - 5.0f && px <= bookmarkX + bmW + 5.0f && py >= navY - 4.0f && py <= navY + navH + 4.0f) {
            BrowserEngine.log("[Header] YouTube bookmark clicked");
            stopAllTyping();
            engine.navigate("https://www.youtube.com");
            return;
        }

        // 10. Адресная строка / строка поиска в шапке
        float urlBarX = reloadX + btnSize + 16.0f;
        float urlBarW = soundX - urlBarX - 12.0f;
        if (px >= urlBarX && px <= urlBarX + urlBarW && py >= navY - 4.0f && py <= navY + navH + 4.0f) {
            startUrlTyping();
            BrowserEngine.log("[TypingMode] Activated: clicked header URL bar");
        }
    }

    public static boolean handleChar(int codePoint) {
        Browser browser = Browser.INSTANCE;
        if (browser == null || !browser.isEnable() || !browser.isTypingMode()) return false;

        if (Character.isValidCodePoint(codePoint) && !Character.isISOControl(codePoint)) {
            String str = new String(Character.toChars(codePoint));
            if (browser.urlTypingMode) {
                if (browser.urlTextBuffer.length() < 256) {
                    browser.urlTextBuffer += str;
                }
                return true;
            } else if (browser.webTypingMode) {
                BrowserEngine.getInstance().insertText(str);
                return true;
            }
        }
        return false;
    }

    public static boolean handleKey(int key, int action) {
        Browser browser = Browser.INSTANCE;
        if (browser == null || !browser.isEnable() || browser.worldScreen == null || mc.player == null) return false;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return false;

        Vec3d camPos = camera.getPos();
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float yawRad = (float) Math.toRadians(camera.getYaw());

        float lx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float ly = -MathHelper.sin(pitchRad);
        float lz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        Vec3d lookDir = new Vec3d(lx, ly, lz).normalize();

        WorldScreen.RayHit hit = browser.worldScreen.intersect(camPos, lookDir);

        // 1. Если включен ввод в адресную строку в шапке
        if (browser.urlTypingMode) {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                browser.urlTypingMode = false;
                browser.urlTextBuffer = "";
                BrowserEngine.log("[UrlTyping] Cancelled via ESC");
                return true;
            }

            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!browser.urlTextBuffer.isEmpty()) {
                        browser.urlTextBuffer = browser.urlTextBuffer.substring(0, browser.urlTextBuffer.length() - 1);
                    }
                    return true;
                } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    String query = browser.urlTextBuffer.trim();
                    browser.urlTypingMode = false;
                    browser.urlTextBuffer = "";
                    if (!query.isEmpty()) {
                        if (query.startsWith("http://") || query.startsWith("https://")) {
                            BrowserEngine.getInstance().navigate(query);
                        } else if (!query.contains(" ") && query.contains(".")) {
                            BrowserEngine.getInstance().navigate("https://" + query);
                        } else {
                            try {
                                String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                                BrowserEngine.getInstance().navigate("https://www.google.com/search?q=" + encoded);
                            } catch (Exception e) {
                                BrowserEngine.getInstance().navigate("https://www.google.com/search?q=" + query);
                            }
                        }
                    }
                    return true;
                } else if (key == GLFW.GLFW_KEY_SPACE) {
                    if (browser.urlTextBuffer.length() < 256) {
                        browser.urlTextBuffer += " ";
                    }
                    return true;
                }
            }
            return true;
        }

        // 2. Если фокус находится в поле ввода на веб-странице (YouTube, Google и др.)
        if (browser.webTypingMode) {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                browser.webTypingMode = false;
                BrowserEngine.getInstance().blurActiveElement();
                BrowserEngine.log("[WebTyping] Disabled via ESC");
                return true;
            }

            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                BrowserEngine engine = BrowserEngine.getInstance();
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    engine.pressBackspace();
                    return true;
                } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    engine.pressEnter();
                    return true;
                } else if (key == GLFW.GLFW_KEY_SPACE) {
                    engine.insertText(" ");
                    return true;
                } else if (key == GLFW.GLFW_KEY_DELETE) {
                    engine.pressDelete();
                    return true;
                } else if (key == GLFW.GLFW_KEY_TAB) {
                    engine.pressTab();
                    return true;
                } else if (key == GLFW.GLFW_KEY_LEFT) {
                    engine.pressArrow("ArrowLeft", "ArrowLeft", 37);
                    return true;
                } else if (key == GLFW.GLFW_KEY_RIGHT) {
                    engine.pressArrow("ArrowRight", "ArrowRight", 39);
                    return true;
                } else if (key == GLFW.GLFW_KEY_UP) {
                    engine.pressArrow("ArrowUp", "ArrowUp", 38);
                    return true;
                } else if (key == GLFW.GLFW_KEY_DOWN) {
                    engine.pressArrow("ArrowDown", "ArrowDown", 40);
                    return true;
                }
            }
            return true;
        }

        // 3. Если ни один режим ввода не активен — обычный режим игры + горячие клавиши при наведении на экран
        if (hit == null) return false;

        if (action == GLFW.GLFW_PRESS) {
            BrowserEngine engine = BrowserEngine.getInstance();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (engine.isFullscreen()) {
                    BrowserEngine.log("[Hotkey ESC] -> Exit Fullscreen");
                    engine.exitFullscreen();
                    return true;
                }
            }

            long window = mc.getWindow().getHandle();
            boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

            if (ctrl) {
                if (key == GLFW.GLFW_KEY_R) {
                    BrowserEngine.log("[Hotkey Ctrl+R] -> Reload");
                    engine.reload();
                    return true;
                } else if (key == GLFW.GLFW_KEY_T) {
                    BrowserEngine.log("[Hotkey Ctrl+T] -> Start URL typing");
                    browser.startUrlTyping();
                    return true;
                } else if (key == GLFW.GLFW_KEY_W) {
                    BrowserEngine.log("[Hotkey Ctrl+W] -> Close");
                    browser.toggle();
                    return true;
                }
            }
        }

        return false;
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (!isEnable() || mc.player == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        DrawContext context = event.getContext();
        MatrixStack matrices = context.getMatrices();

        Font font = Fonts.getFont("suisse", 12);
        if (font == null) font = Fonts.getFont("moe3", 12);

        if (urlTypingMode) {
            float barW = 490f;
            float barH = 24f;
            float barX = (screenW - barW) * 0.5f;
            float barY = 8f;

            RenderUtils.drawRoundedRect(matrices, barX, barY, barW, barH, 6.0f, ColorUtils.rgba(15, 30, 45, 240));
            RenderUtils.drawRoundedRect(matrices, barX, barY, barW, barH, 6.0f, ColorUtils.rgba(0, 230, 255, 160));

            if (font != null) {
                String text = "🌐 [АДРЕСНАЯ СТРОКА] Введите сайт или запрос | ENTER - Перейти | ESC - Отмена";
                float tw = font.getWidth(text);
                font.draw(matrices, text, barX + (barW - tw) * 0.5f, barY + 6.5f, ColorUtils.rgba(180, 245, 255, 255));
            }
        } else if (webTypingMode) {
            float barW = 480f;
            float barH = 24f;
            float barX = (screenW - barW) * 0.5f;
            float barY = 8f;

            RenderUtils.drawRoundedRect(matrices, barX, barY, barW, barH, 6.0f, ColorUtils.rgba(15, 30, 45, 240));
            RenderUtils.drawRoundedRect(matrices, barX, barY, barW, barH, 6.0f, ColorUtils.rgba(0, 230, 255, 160));

            if (font != null) {
                String text = "⌨ [ПОИСК НА СТРАНИЦЕ] Набирайте текст на клавиатуре | ENTER - Найти | ESC - Выход";
                float tw = font.getWidth(text);
                font.draw(matrices, text, barX + (barW - tw) * 0.5f, barY + 6.5f, ColorUtils.rgba(180, 245, 255, 255));
            }
        } else {
            float barW = 480f;
            float barH = 22f;
            float barX = (screenW - barW) * 0.5f;
            float barY = 6f;

            RenderUtils.drawRoundedRect(matrices, barX, barY, barW, barH, 5.0f, ColorUtils.rgba(20, 20, 28, 220));
            RenderUtils.drawRoundedRect(matrices, barX, barY, barW, barH, 5.0f, ColorUtils.rgba(255, 255, 255, 20));

            if (font != null) {
                String text = "Браузер: [ЛКМ] Клик / Поиск | [ПКМ] Перетаскивание | [✕] в шапке — Закрыть";
                float tw = font.getWidth(text);
                font.draw(matrices, text, barX + (barW - tw) * 0.5f, barY + 5.5f, ColorUtils.rgba(230, 235, 255, 255));
            }
        }
    }

    private int getLaserColor() {
        return switch (laserColorMode.getCurrent()) {
            case "Бирюзовый" -> ColorUtils.rgba(0, 240, 255, 255);
            case "Фиолетовый" -> ColorUtils.rgba(180, 70, 255, 255);
            case "Белый" -> ColorUtils.rgba(255, 255, 255, 255);
            case "Красный" -> ColorUtils.rgba(255, 60, 60, 255);
            case "Золотой" -> ColorUtils.rgba(255, 200, 40, 255);
            default -> ColorUtils.getThemeColor();
        };
    }
}
