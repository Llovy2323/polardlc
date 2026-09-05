package snill.client.api.utils.script;

import snill.client.api.events.implement.EventUpdate;

public class DelayScript {

    private final ScriptManager scriptManager = new ScriptManager();
    private ScriptTask currentTask;

    public void update(EventUpdate event) {
        scriptManager.tick(event);
    }

    public DelayScript cleanup() {
        scriptManager.clear();
        currentTask = null;
        return this;
    }

    public DelayScript addTickStep(int delayTicks, Runnable action) {
        if (currentTask == null) {
            currentTask = new ScriptTask();
            scriptManager.addTask(currentTask);
        }

        final int[] remaining = {Math.max(0, delayTicks)};
        currentTask.schedule(event -> {
            if (remaining[0] > 0) {
                remaining[0]--;
                return false;
            }
            action.run();
            return true;
        });
        return this;
    }
}
