package ru.intertrust.cm.core.gui.model.form.widget;

import ru.intertrust.cm.core.business.api.dto.StringValue;
import ru.intertrust.cm.core.business.api.dto.Value;

/**
 * @author Denis Mitavskiy
 *         Date: 14.09.13
 *         Time: 11:42
 */
public class TextBoxState extends ValueEditingWidgetState {
    private String text;

    public TextBoxState() {
    }

    public TextBoxState(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public Value getValue() {
        return new StringValue(text);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
