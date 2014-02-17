package ru.intertrust.cm.core.gui.impl.client.plugins.collection;

import ru.intertrust.cm.core.gui.impl.client.converter.ValueConverter;
import ru.intertrust.cm.core.gui.impl.client.plugins.collection.view.TextCell;
import ru.intertrust.cm.core.gui.model.plugin.CollectionRowItem;

/**
 * @author Yaroslav Bondacrhuk
 *         Date: 14/02/14
 *         Time: 12:05 PM
 */
public class TextCollectionColumn extends CollectionColumn {

    private ValueConverter converter;

    public TextCollectionColumn(TextCell cell) {
        super(cell);
    }

    @Override
    public String getValue(CollectionRowItem object) {
        return converter.valueToString(object.getRowValue(fieldName));

    }

    public TextCollectionColumn(TextCell cell, String fieldName, Boolean resizable, ValueConverter converter) {
        super(cell, fieldName, resizable);
        this.converter = converter;
    }

}

