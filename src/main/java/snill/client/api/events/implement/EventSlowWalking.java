package snill.client.api.events.implement;

import snill.client.api.events.Event;

public class EventSlowWalking extends Event {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}