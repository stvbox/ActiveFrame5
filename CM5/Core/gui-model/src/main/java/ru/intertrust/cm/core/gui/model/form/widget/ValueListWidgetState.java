package ru.intertrust.cm.core.gui.model.form.widget;

import ru.intertrust.cm.core.business.api.dto.Id;

import java.util.LinkedHashMap;

/**
 * Состояние виджета, которорый предоставляет список возможных значений.
 *
 * @author Lesia Puhova
 *         Date: 24.01.2014
 *         Time: 11:52:44
 */

public abstract class ValueListWidgetState extends LinkEditingWidgetState {
    private LinkedHashMap<Id, String> listValues; //declared as LinkedHashMap (rather than Map) intentionally, to emphasize that items order is important

    public LinkedHashMap<Id, String> getListValues() {
        return listValues;
    }

    public void setListValues(LinkedHashMap<Id, String> listValues) {
        this.listValues = listValues;
    }
}
