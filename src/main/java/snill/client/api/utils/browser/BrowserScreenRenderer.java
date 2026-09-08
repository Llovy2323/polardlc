package snill.client.api.utils.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import snill.client.api.QClient;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;

public class BrowserScreenRenderer implements QClient {

    public static final float GUI_SCALE = 0.0035f;

    public static void renderScreen(Event3DRender event, WorldScreen screen, WorldScreen.RayHit hit, int laserColor) {
        if (screen == null || screen.getPosition() == null || mc.player == null) return;

        BrowserEngine engine = BrowserEngine.getInstance();
        engine.updateTexture();

        MatrixStack matrices = event.getMatrices();
        Camera camera = event.getCamera();
        Vec3d camPos = camera.getPos();

        float w = screen.getWidth();
        float h = screen.getHeight();
        float hw = w * 0.5f;
        float hh = h * 0.5f;
        boolean fs = engine.isFullscreen();
        float barH = fs ? 0f : h * 0.12f;

        // 1. Отрисовка экрана в локальных координатах
        matrices.push();
        matrices.translate(screen.getPosition().x - camPos.x,
                screen.getPosition().y - camPos.y,
                screen.getPosition().z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-screen.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(screen.getPitch()));

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 1.1. Задняя крышка монитора и окантовка (Bezel)
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder quadBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Тонкий неоновый контур вокруг монитора
        float borderPad = 0.025f;
        float r = ((laserColor >> 16) & 0xFF) / 255f;
        float g = ((laserColor >> 8) & 0xFF) / 255f;
        float b = (laserColor & 0xFF) / 255f;
        quadBuffer.vertex(posMatrix, -hw - borderPad, -hh - borderPad, -0.003f).color(r, g, b, 0.75f);
        quadBuffer.vertex(posMatrix, hw + borderPad, -hh - borderPad, -0.003f).color(r, g, b, 0.75f);
        quadBuffer.vertex(posMatrix, hw + borderPad, hh + borderPad, -0.003f).color(r, g, b, 0.75f);
        quadBuffer.vertex(posMatrix, -hw - borderPad, hh + borderPad, -0.003f).color(r, g, b, 0.75f);

        // Задний темный корпус (глубина -0.008)
        quadBuffer.vertex(posMatrix, -hw, -hh, -0.006f).color(0.08f, 0.08f, 0.10f, 0.98f);
        quadBuffer.vertex(posMatrix, hw, -hh, -0.006f).color(0.08f, 0.08f, 0.10f, 0.98f);
        quadBuffer.vertex(posMatrix, hw, hh, -0.006f).color(0.08f, 0.08f, 0.10f, 0.98f);
        quadBuffer.vertex(posMatrix, -hw, hh, -0.006f).color(0.08f, 0.08f, 0.10f, 0.98f);

        // Верхняя плашка Chrome Header (фон шапки) — только если не в полноэкранном режиме
        if (!fs) {
            float barMinY = hh - barH;
            quadBuffer.vertex(posMatrix, -hw, barMinY, 0.001f).color(0.12f, 0.12f, 0.15f, 0.98f);
            quadBuffer.vertex(posMatrix, hw, barMinY, 0.001f).color(0.12f, 0.12f, 0.15f, 0.98f);
            quadBuffer.vertex(posMatrix, hw, hh, 0.001f).color(0.12f, 0.12f, 0.15f, 0.98f);
            quadBuffer.vertex(posMatrix, -hw, hh, 0.001f).color(0.12f, 0.12f, 0.15f, 0.98f);
        }

        BufferRenderer.drawWithGlobalProgram(quadBuffer.end());

        // 1.2. Отрисовка полотна веб-страницы (в полноэкранном режиме занимает 100% монитора)
        float contentMinY = -hh;
        float contentMaxY = fs ? hh : hh - barH;

        if (engine.hasTexture()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, BrowserEngine.TEXTURE_ID);

            BufferBuilder texBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            texBuffer.vertex(posMatrix, -hw, contentMaxY, 0.001f).texture(0f, 0f).color(255, 255, 255, 255);
            texBuffer.vertex(posMatrix, hw, contentMaxY, 0.001f).texture(1f, 0f).color(255, 255, 255, 255);
            texBuffer.vertex(posMatrix, hw, contentMinY, 0.001f).texture(1f, 1f).color(255, 255, 255, 255);
            texBuffer.vertex(posMatrix, -hw, contentMinY, 0.001f).texture(0f, 1f).color(255, 255, 255, 255);
            BufferRenderer.drawWithGlobalProgram(texBuffer.end());
        } else {
            // Экран загрузки / ожидания кадра от Chromium
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder loadBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            loadBuf.vertex(posMatrix, -hw, contentMaxY, 0.001f).color(0.06f, 0.06f, 0.08f, 1.0f);
            loadBuf.vertex(posMatrix, hw, contentMaxY, 0.001f).color(0.06f, 0.06f, 0.08f, 1.0f);
            loadBuf.vertex(posMatrix, hw, contentMinY, 0.001f).color(0.06f, 0.06f, 0.08f, 1.0f);
            loadBuf.vertex(posMatrix, -hw, contentMinY, 0.001f).color(0.06f, 0.06f, 0.08f, 1.0f);
            BufferRenderer.drawWithGlobalProgram(loadBuf.end());
        }

        // 1.3. UI элементы шапки браузера (в масштабе GUI)
        matrices.push();
        matrices.translate(0, 0, 0.003f);
        matrices.scale(GUI_SCALE, -GUI_SCALE, GUI_SCALE);

        float pixelW = w / GUI_SCALE;
        float pixelH = h / GUI_SCALE;
        float pixelBarH = barH / GUI_SCALE;

        float topX = -pixelW * 0.5f;
        float topY = -pixelH * 0.5f;

        if (!fs) {
            renderChromeUI(matrices, engine, topX, topY, pixelW, pixelH, pixelBarH, hit, laserColor);
        } else if (hit != null && hit.v <= 0.08f) {
            // В полноэкранном режиме при наведении на верхний край показываем подсказку выхода
            float pillW = 280.0f;
            float pillH = 24.0f;
            float pillX = -pillW * 0.5f;
            float pillY = topY + 8.0f;
            RenderUtils.drawRoundedRect(matrices, pillX, pillY, pillW, pillH, 6.0f, ColorUtils.rgba(20, 20, 30, 230));
            RenderUtils.drawRoundedRect(matrices, pillX, pillY, pillW, pillH, 6.0f, ColorUtils.rgba(255, 60, 60, 180));
            Font fontSmall = Fonts.getFont("suisse", 12);
            if (fontSmall == null) fontSmall = Fonts.getFont("moe3", 12);
            if (fontSmall != null) {
                String hint = "✕ Выйти из полноэкранного режима (ESC)";
                float hwHint = fontSmall.getWidth(hint) * 0.5f;
                fontSmall.draw(matrices, hint, -hwHint, pillY + pillH * 0.5f - 5.5f, -1);
            }
        }

        // Если текстуры ещё нет, пишем статус в центре
        if (!engine.hasTexture()) {
            Font centerFont = Fonts.getFont("suisse", 22);
            if (centerFont == null) centerFont = Fonts.getFont("sf_regular", 22);
            if (centerFont != null) {
                String status = engine.isConnected() ? "Загрузка страницы..." : "Запуск Brave / Chromium...";
                float sw = centerFont.getWidth(status);
                centerFont.draw(matrices, status, -sw * 0.5f, 0f, ColorUtils.rgba(200, 200, 230, 255));
            }
        }

        matrices.pop();
        matrices.pop();

        // 2. VR-указка (Лазерный луч прямо из правой руки игрока)
        renderVRLaser(event, screen, hit, laserColor);
    }

    private static void renderChromeUI(MatrixStack matrices, BrowserEngine engine, float x, float y, float width, float height, float barH, WorldScreen.RayHit hit, int laserColor) {
        Font fontRegular = Fonts.getFont("suisse", 14);
        if (fontRegular == null) fontRegular = Fonts.getFont("moe3", 14);
        Font fontSmall = Fonts.getFont("suisse", 12);
        if (fontSmall == null) fontSmall = Fonts.getFont("moe3", 12);

        // Координаты лазера в пикселях шапки
        float px = (hit != null) ? hit.u * width : -1000f;
        float py = (hit != null) ? hit.v * height : -1000f;

        // Вкладка активная (вверху слева)
        float tabW = 220.0f;
        float tabH = barH * 0.45f;
        float tabX = x + 18.0f;
        float tabY = y + 5.0f;

        RenderUtils.drawRoundedRect(matrices, tabX, tabY, tabW, tabH, 5.0f, ColorUtils.rgba(40, 40, 50, 240));
        RenderUtils.drawRoundedRect(matrices, tabX + 4.0f, tabY + 4.0f, 3.5f, tabH - 8.0f, 1.5f, laserColor);

        String title = engine.getCurrentUrl();
        if (title.contains("youtube.com")) title = "YouTube";
        else if (title.contains("google.com")) title = "Google";
        else if (title.length() > 24) title = title.substring(0, 21) + "...";

        if (fontSmall != null) {
            fontSmall.draw(matrices, title, tabX + 14.0f, tabY + (tabH * 0.5f) - 5.5f, ColorUtils.rgba(240, 240, 255, 255));
            boolean hoverTabClose = px >= (tabX - x) + tabW - 25.0f && px <= (tabX - x) + tabW && py >= (tabY - y) && py <= (tabY - y) + tabH;
            fontSmall.draw(matrices, "x", tabX + tabW - 18.0f, tabY + (tabH * 0.5f) - 5.5f, hoverTabClose ? ColorUtils.rgba(255, 100, 100, 255) : ColorUtils.rgba(160, 160, 180, 255));
        }

        // Кнопка новой вкладки [+]
        float plusBtnX = tabX + tabW + 18.0f;
        float plusBtnY = tabY + tabH * 0.5f;
        boolean hoverPlus = Math.abs(px - (plusBtnX - x)) <= 16.0f && Math.abs(py - (plusBtnY - y)) <= 16.0f;
        int plusBg = hoverPlus ? ColorUtils.rgba(70, 70, 95, 240) : ColorUtils.rgba(45, 45, 60, 220);
        RenderUtils.drawRoundCircle(matrices, plusBtnX, plusBtnY, 10.5f, plusBg);
        if (fontRegular != null) {
            fontRegular.draw(matrices, "+", plusBtnX - 4.0f, plusBtnY - 6.5f, hoverPlus ? ColorUtils.rgba(255, 255, 255, 255) : ColorUtils.rgba(220, 220, 240, 255));
        }

        // Правые кнопки окна: [—] Свернуть и [✕] Закрыть
        float closeBtnX = x + width - 36.0f;
        float minBtnX = closeBtnX - 32.0f;
        float btnY = y + 7.0f;

        // [—] Кнопка свернуть
        boolean hoverMin = px >= width - 80.0f && px < width - 45.0f && py <= tabH + 8.0f;
        int minBg = hoverMin ? ColorUtils.rgba(70, 70, 95, 250) : ColorUtils.rgba(45, 45, 60, 220);
        RenderUtils.drawRoundedRect(matrices, minBtnX, btnY, 26.0f, 18.0f, 4.0f, minBg);
        RenderUtils.drawRoundedRect(matrices, minBtnX + 7.0f, btnY + 9.0f, 12.0f, 2.0f, 1.0f, ColorUtils.rgba(220, 220, 240, 255));

        // [✕] Кнопка закрыть
        boolean hoverClose = px >= width - 45.0f && py <= tabH + 8.0f;
        int closeBg = hoverClose ? ColorUtils.rgba(255, 45, 45, 255) : ColorUtils.rgba(220, 50, 50, 220);
        RenderUtils.drawRoundedRect(matrices, closeBtnX, btnY, 28.0f, 18.0f, 4.0f, closeBg);
        if (fontSmall != null) {
            fontSmall.draw(matrices, "✕", closeBtnX + 9.0f, btnY + 3.5f, ColorUtils.rgba(255, 255, 255, 255));
        }

        // Нижний ряд бара: Кнопки навигации [◀] [▶] [⟳] + Адресная строка + ТОЛЬКО закладка YouTube
        float navY = y + tabH + 8.0f;
        float navH = barH - tabH - 14.0f;

        float btnSize = navH;
        float btnX = x + 18.0f;

        // ◀
        boolean hoverBack = px >= 10.0f && px <= 18.0f + btnSize + 5.0f && py >= (navY - y) - 4.0f && py <= (navY - y) + navH + 4.0f;
        RenderUtils.drawRoundCircle(matrices, btnX + btnSize * 0.5f, navY + btnSize * 0.5f, btnSize * 0.45f, hoverBack ? ColorUtils.rgba(55, 55, 75, 240) : ColorUtils.rgba(32, 32, 42, 220));
        if (fontRegular != null) fontRegular.draw(matrices, "◀", btnX + 6.0f, navY + btnSize * 0.5f - 6.0f, hoverBack ? -1 : ColorUtils.rgba(200, 200, 220, 255));

        // ▶
        btnX += btnSize + 10.0f;
        boolean hoverFwd = px >= (btnX - x) - 4.0f && px <= (btnX - x) + btnSize + 5.0f && py >= (navY - y) - 4.0f && py <= (navY - y) + navH + 4.0f;
        RenderUtils.drawRoundCircle(matrices, btnX + btnSize * 0.5f, navY + btnSize * 0.5f, btnSize * 0.45f, hoverFwd ? ColorUtils.rgba(55, 55, 75, 240) : ColorUtils.rgba(32, 32, 42, 220));
        if (fontRegular != null) fontRegular.draw(matrices, "▶", btnX + 7.0f, navY + btnSize * 0.5f - 6.0f, hoverFwd ? -1 : ColorUtils.rgba(200, 200, 220, 255));

        // ⟳
        btnX += btnSize + 10.0f;
        boolean hoverReload = px >= (btnX - x) - 4.0f && px <= (btnX - x) + btnSize + 5.0f && py >= (navY - y) - 4.0f && py <= (navY - y) + navH + 4.0f;
        RenderUtils.drawRoundCircle(matrices, btnX + btnSize * 0.5f, navY + btnSize * 0.5f, btnSize * 0.45f, hoverReload ? ColorUtils.rgba(55, 55, 75, 240) : ColorUtils.rgba(32, 32, 42, 220));
        if (fontRegular != null) fontRegular.draw(matrices, "⟳", btnX + 7.0f, navY + btnSize * 0.5f - 6.5f, hoverReload ? -1 : ColorUtils.rgba(200, 200, 220, 255));

        // Кнопка [🔊 Звук] и [▶ YouTube]
        float soundW = 76.0f;
        float bmW = 88.0f;
        float bookmarkX = x + width - bmW - 18.0f;
        float soundX = bookmarkX - soundW - 10.0f;

        // Адресная строка (URL pill bar) растягивается до кнопки звука
        float urlBarX = btnX + btnSize + 16.0f;
        float urlBarW = soundX - urlBarX - 12.0f;
        boolean urlTyping = snill.client.client.modules.impl.render.Browser.INSTANCE.isUrlTypingMode();

        RenderUtils.drawRoundedRect(matrices, urlBarX, navY, urlBarW, navH, 6.0f, ColorUtils.rgba(18, 18, 24, 230));
        RenderUtils.drawRoundedRect(matrices, urlBarX, navY, urlBarW, navH, 6.0f, urlTyping ? ColorUtils.rgba(0, 230, 255, 200) : ColorUtils.rgba(255, 255, 255, 20));

        if (fontSmall != null) {
            if (urlTyping) {
                String buf = snill.client.client.modules.impl.render.Browser.INSTANCE.getUrlTextBuffer();
                String cursor = (System.currentTimeMillis() % 1000 < 500) ? "|" : "";
                if (buf.isEmpty()) {
                    fontSmall.draw(matrices, "Поиск в Google или URL..." + cursor, urlBarX + 14.0f, navY + (navH * 0.5f) - 5.5f, ColorUtils.rgba(130, 140, 160, 255));
                } else {
                    if (buf.length() > 55) buf = buf.substring(buf.length() - 55);
                    fontSmall.draw(matrices, buf + cursor, urlBarX + 14.0f, navY + (navH * 0.5f) - 5.5f, ColorUtils.rgba(0, 240, 255, 255));
                }
            } else {
                String displayUrl = engine.getCurrentUrl();
                if (displayUrl.length() > 55) displayUrl = displayUrl.substring(0, 52) + "...";
                fontSmall.draw(matrices, "🔒 " + displayUrl, urlBarX + 14.0f, navY + (navH * 0.5f) - 5.5f, ColorUtils.rgba(220, 225, 240, 255));
            }
        }

        // Кнопка [🔊 Звук]
        boolean hoverSound = px >= (soundX - x) - 4.0f && px <= (soundX - x) + soundW + 4.0f && py >= (navY - y) - 4.0f && py <= (navY - y) + navH + 4.0f;
        int soundBg = hoverSound ? ColorUtils.rgba(0, 220, 160, 255) : ColorUtils.rgba(0, 160, 120, 220);
        RenderUtils.drawRoundedRect(matrices, soundX, navY, soundW, navH, 5.0f, soundBg);
        if (fontSmall != null) {
            fontSmall.draw(matrices, "🔊 Звук", soundX + 10.0f, navY + navH * 0.5f - 5.5f, -1);
        }

        // Кнопка [▶ YouTube] с подсветкой при наведении лазера
        boolean hoverYT = px >= (bookmarkX - x) - 4.0f && px <= (bookmarkX - x) + bmW + 4.0f && py >= (navY - y) - 4.0f && py <= (navY - y) + navH + 4.0f;
        int ytBg = hoverYT ? ColorUtils.rgba(255, 30, 30, 255) : ColorUtils.rgba(220, 25, 25, 220);
        RenderUtils.drawRoundedRect(matrices, bookmarkX, navY, bmW, navH, 5.0f, ytBg);
        if (fontSmall != null) {
            fontSmall.draw(matrices, "▶ YouTube", bookmarkX + 9.0f, navY + navH * 0.5f - 5.5f, -1);
        }
    }

    private static void renderVRLaser(Event3DRender event, WorldScreen screen, WorldScreen.RayHit hit, int laserColor) {
        Camera camera = event.getCamera();
        Vec3d camPos = camera.getPos();

        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float yawRad = (float) Math.toRadians(camera.getYaw());

        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        // Вектор взгляда игрока (Forward)
        Vec3d forward = new Vec3d(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch).normalize();
        // Вектор вправо от взгляда (Right)
        Vec3d right = new Vec3d(-cosYaw, 0.0, -sinYaw).normalize();
        // Вектор вверх (Up = right x forward)
        Vec3d up = right.crossProduct(forward).normalize();

        // Позиция правой руки персонажа в системе координат камеры (origin = camPos)
        // 0.35 блока вправо, 0.25 блока вниз, 0.50 блока вперед (гарантированно перед камерой)
        Vec3d handRel = right.multiply(0.35)
                .add(up.multiply(-0.25))
                .add(forward.multiply(0.50));

        // Конечная точка луча в мировых координатах и относительно камеры
        Vec3d targetWorld = (hit != null) ? hit.point : camPos.add(forward.multiply(12.0));
        Vec3d targetRel = targetWorld.subtract(camPos);

        MatrixStack matrices = event.getMatrices();
        matrices.push();
        // ВНИМАНИЕ: matrices в Event3DRender уже camera-relative! Поэтому смещение относительно камеры передаем напрямую в координаты вершин.
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float r = ((laserColor >> 16) & 0xFF) / 255f;
        float g = ((laserColor >> 8) & 0xFF) / 255f;
        float b = (laserColor & 0xFF) / 255f;

        // 1. Внешнее неоновое сияние луча
        RenderSystem.lineWidth(3.5f);
        BufferBuilder glowBeam = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        glowBeam.vertex(mat, (float) handRel.x, (float) handRel.y, (float) handRel.z).color(r, g, b, 0.45f);
        glowBeam.vertex(mat, (float) targetRel.x, (float) targetRel.y, (float) targetRel.z).color(r, g, b, 0.70f);
        BufferRenderer.drawWithGlobalProgram(glowBeam.end());

        // 2. Яркий белый сердечник луча
        RenderSystem.lineWidth(1.5f);
        BufferBuilder coreBeam = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        coreBeam.vertex(mat, (float) handRel.x, (float) handRel.y, (float) handRel.z).color(1f, 1f, 1f, 0.95f);
        coreBeam.vertex(mat, (float) targetRel.x, (float) targetRel.y, (float) targetRel.z).color(1f, 1f, 1f, 0.95f);
        BufferRenderer.drawWithGlobalProgram(coreBeam.end());

        // 3. Лазерная точка-курсор на поверхности монитора
        if (hit != null) {
            Vec3d hitRel = hit.point.subtract(camPos).add(screen.getNormal().multiply(0.005));
            Vec3d rVec = screen.getRight().multiply(0.035);
            Vec3d uVec = screen.getUp().multiply(0.035);

            BufferBuilder dotBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            // Внешнее пятно цвета лазера
            dotBuf.vertex(mat, (float) (hitRel.x - rVec.x - uVec.x), (float) (hitRel.y - rVec.y - uVec.y), (float) (hitRel.z - rVec.z - uVec.z)).color(r, g, b, 0.75f);
            dotBuf.vertex(mat, (float) (hitRel.x + rVec.x - uVec.x), (float) (hitRel.y + rVec.y - uVec.y), (float) (hitRel.z + rVec.z - uVec.z)).color(r, g, b, 0.75f);
            dotBuf.vertex(mat, (float) (hitRel.x + rVec.x + uVec.x), (float) (hitRel.y + rVec.y + uVec.y), (float) (hitRel.z + rVec.z + uVec.z)).color(r, g, b, 0.75f);
            dotBuf.vertex(mat, (float) (hitRel.x - rVec.x + uVec.x), (float) (hitRel.y - rVec.y + uVec.y), (float) (hitRel.z - rVec.z + uVec.z)).color(r, g, b, 0.75f);

            // Белая центральная точка
            Vec3d rInner = screen.getRight().multiply(0.015);
            Vec3d uInner = screen.getUp().multiply(0.015);
            dotBuf.vertex(mat, (float) (hitRel.x - rInner.x - uInner.x), (float) (hitRel.y - rInner.y - uInner.y), (float) (hitRel.z - rInner.z - uInner.z)).color(1f, 1f, 1f, 0.95f);
            dotBuf.vertex(mat, (float) (hitRel.x + rInner.x - uInner.x), (float) (hitRel.y + rInner.y - uInner.y), (float) (hitRel.z + rInner.z - uInner.z)).color(1f, 1f, 1f, 0.95f);
            dotBuf.vertex(mat, (float) (hitRel.x + rInner.x + uInner.x), (float) (hitRel.y + rInner.y + uInner.y), (float) (hitRel.z + rInner.z + uInner.z)).color(1f, 1f, 1f, 0.95f);
            dotBuf.vertex(mat, (float) (hitRel.x - rInner.x + uInner.x), (float) (hitRel.y - rInner.y + uInner.y), (float) (hitRel.z - rInner.z + uInner.z)).color(1f, 1f, 1f, 0.95f);

            BufferRenderer.drawWithGlobalProgram(dotBuf.end());
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        matrices.pop();
    }
}
