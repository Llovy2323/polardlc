package snill.client.client.modules.impl.render.base.implement;

import static snill.client.Snill.INSTANCE;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.math.MathUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.render.fonts.ttf.MCFontRenderer;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

public class Information extends InterfaceProcessing {

    private final AnimationUtils pressAnimation = new AnimationUtils(0f, 14f, Easings.CUBIC_OUT);
    private boolean pressing = false;
    private long pressTime = -1L;

    public Information(Draggable draggable) {
        super(draggable);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        DefaultStyle(eventRender);
        super.onRender(eventRender);
    }
    private MCFontRenderer myfont(int size) {
        return snill.client.api.utils.render.fonts.ttf.Fonts.getFont("myfont.ttf", size);
    }
    public void DefaultStyle(EventRender.Default eventRender) {
        float x = draggable.getX(), y = draggable.getY();
        Font font = Fonts.getFont("suisse", 13);
        Font iconFont = Fonts.getFont("icon", 16);
        Font smallIconFont = Fonts.getFont("icon", 15);

        int colorTheme;
        if (!INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        boolean drawSquares = isUnusualRectType();

        int px = (int) Math.floor(mc.player.getX());
        int py = (int) Math.floor(mc.player.getY());
        int pz = (int) Math.floor(mc.player.getZ());

        float height = 16f;
        double bps = MathUtils.calculateBPS();
        String xValue = String.valueOf(px);
        String yValue = String.valueOf(py);
        String zValue = String.valueOf(pz);
        String coordsText = xValue + "x " + yValue + "y " + zValue + "z";
        String bpsValue = formatTwoDecimals(bps);
        String bpsSuffix = " b/s";
        float widthbps = font.getWidth(bpsValue + bpsSuffix);
        float xbps = x + 17 + widthbps;
        float widthCords = font.getWidth(coordsText);
        float totalWidth = 13 + widthCords + widthbps + 2 + 13.8f;

        if (pressing && System.currentTimeMillis() - pressTime > 150L) {
            pressing = false;
        }
        pressAnimation.update(pressing ? 1f : 0f);

        RenderUtils.drawDefaultHudThemedPanel(eventRender.getContext().getMatrices(), x, y, totalWidth, height, 5, 5, colorTheme);
        if (drawSquares) {
            RenderUtils.drawHudSquarePattern(eventRender.getContext().getMatrices(), x, y, totalWidth, height, colorTheme);
        }

        float speedTextX = x + 13.5f;
        float bpsValueWidth = font.getWidth(bpsValue);
        font.draw(eventRender.getContext().getMatrices(), bpsValue, speedTextX, y + 6.6, -1);
        font.draw(eventRender.getContext().getMatrices(), bpsSuffix, speedTextX + bpsValueWidth - 2, y + 6.6, colorTheme);
        float coordsX = xbps + 9f;
        font.draw(eventRender.getContext().getMatrices(), xValue, coordsX, y + 6.6, -1);
        coordsX += font.getWidth(xValue);
        font.draw(eventRender.getContext().getMatrices(), "x", coordsX - 1, y + 6.6, colorTheme);
        coordsX += font.getWidth("x ");
        font.draw(eventRender.getContext().getMatrices(), yValue, coordsX, y + 6.6, -1);
        coordsX += font.getWidth(yValue);
        font.draw(eventRender.getContext().getMatrices(), "y", coordsX - 1, y + 6.6, colorTheme);
        coordsX += font.getWidth("y ");
        font.draw(eventRender.getContext().getMatrices(), zValue, coordsX, y + 6.6, -1);
        coordsX += font.getWidth(zValue);
        font.draw(eventRender.getContext().getMatrices(), "z", coordsX - 1, y + 6.6, colorTheme);
        myfont(16).drawString( "i", x + 3.25f, y + 6.6f, colorTheme);
        myfont(15).drawString( "j", xbps - 1, y + 6.85f, colorTheme);

        draggable.setHeight(height);
        draggable.setWidth(totalWidth);
    }

    public boolean handleClick(double mouseX, double mouseY) {
        return false;
    }

    public void handleCopyClick() {
        int px = (int) Math.floor(mc.player.getX());
        int py = (int) Math.floor(mc.player.getY());
        int pz = (int) Math.floor(mc.player.getZ());
        mc.keyboard.setClipboard(px + " " + py + " " + pz);
        pressing = true;
        pressTime = System.currentTimeMillis();
    }

    private String formatTwoDecimals(double value) {
        int scaled = (int) Math.round(value * 100.0D);
        int fraction = Math.abs(scaled % 100);
        return (scaled / 100) + "." + (fraction < 10 ? "0" : "") + fraction;
    }
}