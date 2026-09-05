package snill.client.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import snill.client.Snill;
import snill.client.client.modules.settings.Setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Getter
@Setter
public class ModeSetting extends Setting {

    private List<String> mods;

    private String current;

    private int index;

    public ModeSetting(String name, String current, String... modes) {
        super(name);
        this.mods = Arrays.asList(modes);
        this.index = this.mods.indexOf(current);
        if (this.index < 0) {
            this.index = 0;
        }
        this.current = this.mods.get(this.index);
    }

    public void set(String selected) {
        int newIndex = this.mods.indexOf(selected);
        if (newIndex < 0) {
            return;
        }
        this.current = selected;
        this.index = newIndex;
    }

    public void addMode(String mode) {
        if (mods.contains(mode)) return;
        mods = new java.util.ArrayList<>(mods);
        mods.add(mode);
    }

    public boolean is(String mode) {
        return current.equals(mode);
    }

    public String displayMode(String mode) {
        return Snill.INSTANCE.localizationStorage == null ? mode : Snill.INSTANCE.localizationStorage.translate(mode);
    }

    public String displayCurrent() {
        return displayMode(current);
    }

    public ModeSetting visible(Supplier<Boolean> state) {
        this.visible = state;
        return this;
    }
}
