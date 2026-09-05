package snill.client.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.network.packet.Packet;
import snill.client.api.events.Event;

@AllArgsConstructor @Getter
public class EventPacket extends Event {
    private final Packet<?> packet;
    private final Type type;

    public enum Type {
        SEND,
        RECEIVE
    }
}