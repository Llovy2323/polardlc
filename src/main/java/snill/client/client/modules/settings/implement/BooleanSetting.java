package snill.client.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import snill.client.client.modules.settings.Setting;

import java.util.function.Supplier;

@Getter
@Setter
public class BooleanSetting extends Setting {

    private boolean state;

    public BooleanSetting(String name, boolean state) {
        super(name);
        this.state = state;
    }

    public static BooleanSetting of(String name, boolean state) {
        return new BooleanSetting(name, state);
    }

    public BooleanSetting visible(Supplier<Boolean> state) {
        this.visible = state;
        return this;
    }
}
