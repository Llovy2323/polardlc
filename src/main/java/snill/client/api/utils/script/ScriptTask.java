package snill.client.api.utils.script;

import snill.client.api.events.implement.EventUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ScriptTask {

    private final List<Function<EventUpdate, Boolean>> steps = new ArrayList<>();
    private int currentStep;

    public ScriptTask schedule(Function<EventUpdate, Boolean> step) {
        steps.add(step);
        return this;
    }

    public boolean tick(EventUpdate event) {
        if (currentStep >= steps.size()) {
            return true;
        }
        if (Boolean.TRUE.equals(steps.get(currentStep).apply(event))) {
            currentStep++;
        }
        return currentStep >= steps.size();
    }
}
