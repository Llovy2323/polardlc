package snill.client.client.modules.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class ViewModel extends Module {

    public static ViewModel INSTANCE = new ViewModel();

    public final FloatSetting mainHandX = new FloatSetting("Правая рука X", 0f, -2f, 2f, 0.01f);
    public final FloatSetting mainHandY = new FloatSetting("Правая рука Y", 0f, -2f, 2f, 0.01f);
    public final FloatSetting mainHandZ = new FloatSetting("Правая рука Z", 0f, -2f, 2f, 0.01f);
    public final FloatSetting mainHandScale = new FloatSetting("Правая рука Размер", 1.0f, 0.1f, 2.0f, 0.01f);
    public final FloatSetting mainHandRotX = new FloatSetting("Правая рука Поворот X", 0f, -180f, 180f, 1f);
    public final FloatSetting mainHandRotY = new FloatSetting("Правая рука Поворот Y", 0f, -180f, 180f, 1f);
    public final FloatSetting mainHandRotZ = new FloatSetting("Правая рука Поворот Z", 0f, -180f, 180f, 1f);

    public final FloatSetting offHandX = new FloatSetting("Левая рука X", 0f, -2f, 2f, 0.01f);
    public final FloatSetting offHandY = new FloatSetting("Левая рука Y", 0f, -2f, 2f, 0.01f);
    public final FloatSetting offHandZ = new FloatSetting("Левая рука Z", 0f, -2f, 2f, 0.01f);
    public final FloatSetting offHandScale = new FloatSetting("Левая рука Размер", 1.0f, 0.1f, 2.0f, 0.01f);
    public final FloatSetting offHandRotX = new FloatSetting("Левая рука Поворот X", 0f, -180f, 180f, 1f);
    public final FloatSetting offHandRotY = new FloatSetting("Левая рука Поворот Y", 0f, -180f, 180f, 1f);
    public final FloatSetting offHandRotZ = new FloatSetting("Левая рука Поворот Z", 0f, -180f, 180f, 1f);

    public final BooleanSetting onlyAura = new BooleanSetting("Только с аурой", false);

    public ViewModel() {
        super("ViewModel", "Оффсеты рук и предметов от первого лица", ModuleCategory.RENDER);
        addSettings(
                mainHandX, mainHandY, mainHandZ, mainHandScale, mainHandRotX, mainHandRotY, mainHandRotZ,
                offHandX, offHandY, offHandZ, offHandScale, offHandRotX, offHandRotY, offHandRotZ,
                onlyAura
        );
    }

    public void apply(MatrixStack matrices, Arm arm) {
        if (arm == Arm.RIGHT) {
            matrices.translate(mainHandX.get(), mainHandY.get(), mainHandZ.get());
            if (mainHandRotX.get() != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mainHandRotX.get()));
            if (mainHandRotY.get() != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mainHandRotY.get()));
            if (mainHandRotZ.get() != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(mainHandRotZ.get()));
            float scale = mainHandScale.get();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
        } else {
            matrices.translate(offHandX.get(), offHandY.get(), offHandZ.get());
            if (offHandRotX.get() != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(offHandRotX.get()));
            if (offHandRotY.get() != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(offHandRotY.get()));
            if (offHandRotZ.get() != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(offHandRotZ.get()));
            float scale = offHandScale.get();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
        }
    }

    public void applyHandPosition(MatrixStack matrices, Arm arm) {
        apply(matrices, arm);
    }
}
