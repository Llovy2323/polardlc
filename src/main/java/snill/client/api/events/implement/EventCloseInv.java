package snill.client.api.events.implement;

import lombok.AllArgsConstructor;
import snill.client.api.events.Event;

@AllArgsConstructor
public class EventCloseInv extends Event {
    public int windowId;
}

