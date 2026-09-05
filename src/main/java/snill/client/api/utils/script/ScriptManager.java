package snill.client.api.utils.script;

import snill.client.api.events.implement.EventUpdate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScriptManager {

    private final List<ScriptTask> tasks = new CopyOnWriteArrayList<>();

    public void addTask(ScriptTask task) {
        tasks.add(task);
    }

    public void tick(EventUpdate event) {
        tasks.removeIf(task -> task.tick(event));
    }

    public void clear() {
        tasks.clear();
    }
}
