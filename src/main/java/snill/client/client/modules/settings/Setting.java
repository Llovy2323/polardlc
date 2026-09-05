package snill.client.client.modules.settings;

import lombok.Getter;
import lombok.experimental.Accessors;
import snill.client.Snill;
import snill.client.api.QClient;

import java.awt.*;
import java.util.function.Supplier;

@Getter
@Accessors(fluent = true)
public abstract class Setting implements QClient {

    private final String name;
    public Supplier<Boolean> visible = () -> true;
    public Color color = Color.WHITE;

    public Setting(String name) {
        this.name = name;
    }

    public Boolean visible() {
        return visible.get();
    }

    public String displayName() {
        return Snill.INSTANCE.localizationStorage == null ? name : Snill.INSTANCE.localizationStorage.translate(name);
    }
}
