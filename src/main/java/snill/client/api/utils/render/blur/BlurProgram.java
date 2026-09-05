package snill.client.api.utils.render.blur;

import snill.client.api.utils.render.RenderUtils;

public class BlurProgram {

    private static final BlurProgram INSTANCE = new BlurProgram();

    public static BlurProgram getInstance() {
        return INSTANCE;
    }

    public void beginFrame() {
        RenderUtils.beginLiquidBlurFrame();
    }

    public void request() {
        RenderUtils.requestLiquidBlur();
    }

    public static int getTexture() {
        return RenderUtils.getLiquidBlurTexture();
    }

    public static void cleanup() {
        RenderUtils.cleanupLiquidBlur();
    }

    public void setBlurOffset(float blurOffset) {
        RenderUtils.setLiquidBlurOffset(blurOffset);
    }
}
