package snill.client.client.modules.impl.render.base;

import lombok.RequiredArgsConstructor;
import snill.client.api.QClient;
import snill.client.api.events.implement.EventRender;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.draggable.Draggable;

@RequiredArgsConstructor
public class InterfaceProcessing implements QClient {

    public final Draggable draggable;
    private boolean unusualRectType = true;

    public boolean isUnusualRectType() {
        return unusualRectType;
    }

    public void setUnusualRectType(boolean unusualRectType) {
        this.unusualRectType = unusualRectType;
    }

    public void onUpdate(EventUpdate eventUpdate) {
    }

    public void onRender(EventRender.Default eventRender) {

    }
}
