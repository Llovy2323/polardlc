package snill.client.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.Vec3d;
import snill.client.api.events.Event;

@Getter
@Setter
@AllArgsConstructor
public class EventOnTravelPost extends Event {
    private Vec3d oldVelocity;
}
