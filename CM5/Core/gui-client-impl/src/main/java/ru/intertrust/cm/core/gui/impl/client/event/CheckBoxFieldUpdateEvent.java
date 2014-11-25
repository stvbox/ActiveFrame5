package ru.intertrust.cm.core.gui.impl.client.event;

import com.google.gwt.event.shared.GwtEvent;
import ru.intertrust.cm.core.business.api.dto.Id;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 09.12.13
 *         Time: 11:15
 */
public class CheckBoxFieldUpdateEvent extends GwtEvent<CheckBoxFieldUpdateEventHandler> {

    public static Type<CheckBoxFieldUpdateEventHandler> TYPE = new Type<CheckBoxFieldUpdateEventHandler>();
    private Id id;
    private boolean deselected;
    private boolean mainContent;

    public CheckBoxFieldUpdateEvent(Id id, boolean deselected, boolean mainContent) {
        this.id = id;
        this.deselected = deselected;
        this.mainContent = mainContent;
    }

    @Override
    public Type<CheckBoxFieldUpdateEventHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(CheckBoxFieldUpdateEventHandler handler) {
        handler.onCheckBoxFieldUpdate(this);
    }

    public Id getId() {
        return id;
    }

    public boolean isDeselected() {
        return deselected;
    }

    public boolean isMainContent() {
        return mainContent;
    }
}
