package snill.client.api.events.implement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import snill.client.api.events.Event;

@Getter
@Setter
public class EventThorns extends Event {
    private final LivingEntity user;
    private final Entity attacker;
    private final int level;
    private float damage;

    public EventThorns(LivingEntity user, Entity attacker, int level, float damage) {
        this.user = user;
        this.attacker = attacker;
        this.level = level;
        this.damage = damage;
    }
}