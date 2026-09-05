package snill.client.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import snill.client.api.events.Event;

@Getter
@AllArgsConstructor
public class EventChunkUpdate extends Event {
    private final int chunkX;
    private final int chunkZ;
}
