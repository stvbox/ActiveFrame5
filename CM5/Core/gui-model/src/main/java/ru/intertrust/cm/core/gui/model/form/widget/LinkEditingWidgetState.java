package ru.intertrust.cm.core.gui.model.form.widget;

import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.business.api.dto.ReferenceValue;
import ru.intertrust.cm.core.business.api.dto.Value;

import java.util.ArrayList;

/**
 * If widget is capable of setting links between objects, similarly it can set a single reference field, in case the
 * widget itself is limit by a single choice
 *
 * @author Denis Mitavskiy
 *         Date: 26.10.13
 *         Time: 22:13
 */
public abstract class LinkEditingWidgetState extends ValueEditingWidgetState {
    public abstract ArrayList<Id> getIds();

    @Override
    public Value getValue() {
        ArrayList<Id> ids = getIds();
        return ids == null || ids.isEmpty() ? null : new ReferenceValue(ids.get(0));
    }
}
