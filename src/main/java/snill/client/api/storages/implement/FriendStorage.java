package snill.client.api.storages.implement;

import lombok.Getter;
import snill.client.Snill;

import java.util.ArrayList;
import java.util.List;

public class FriendStorage {

    @Getter private final List<String> friends = new ArrayList<>();

    public void add(String friend) {
        if (!friend.isEmpty()) {
            friends.add(friend);
            save();
        }
    }

    public void remove(String friend) {
        friends.remove(friend);
        save();
    }

    public void clear() {
        friends.clear();
        save();
    }

    public boolean isFriend(String friend) {
        return friends.contains(friend);
    }

    public boolean isEmpty() {
        return friends.isEmpty();
    }

    private void save() {
        try {
            Snill.INSTANCE.configStorage.saveGlobals();
        } catch (Exception ignored) {
        }
    }
}