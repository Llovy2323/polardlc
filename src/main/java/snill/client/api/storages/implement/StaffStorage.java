package snill.client.api.storages.implement;

import lombok.Getter;
import snill.client.Snill;

import java.util.ArrayList;
import java.util.List;

public class StaffStorage {

    @Getter private final List<String> staffs = new ArrayList<>();

    public void add(String staff) {
        if (!staff.isEmpty()) {
            staffs.add(staff);
            save();
        }
    }

    public void remove(String staff) {
        staffs.remove(staff);
        save();
    }

    public void clear() {
        staffs.clear();
        save();
    }

    public boolean isStaff(String staff) {
        return staffs.contains(staff);
    }

    public boolean isEmpty() {
        return staffs.isEmpty();
    }

    private void save() {
        try {
            Snill.INSTANCE.configStorage.saveGlobals();
        } catch (Exception ignored) {
        }
    }
}