package snill.client.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import snill.client.client.modules.settings.Setting;

import java.util.function.Supplier;

@Getter
@Setter
public class BindSetting extends Setting {

    private int key;

    public BindSetting(String name, int keyDefault) {
        super(name);
        this.key = keyDefault;
    }

    public BindSetting visible(Supplier<Boolean> state) {
        this.visible = state;
        return this;
    }}
