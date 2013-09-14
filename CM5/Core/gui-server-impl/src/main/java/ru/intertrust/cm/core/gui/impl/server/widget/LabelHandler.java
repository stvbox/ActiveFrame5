package ru.intertrust.cm.core.gui.impl.server.widget;

import ru.intertrust.cm.core.gui.api.server.widget.WidgetHandler;
import ru.intertrust.cm.core.gui.model.form.FormData;
import ru.intertrust.cm.core.gui.model.form.widget.WidgetContext;
import ru.intertrust.cm.core.gui.model.form.widget.WidgetData;

/**
 * @author Denis Mitavskiy
 *         Date: 14.09.13
 *         Time: 17:04
 */
public class LabelHandler extends WidgetHandler {
    @Override
    public <T extends WidgetData> T getInitialDisplayData(WidgetContext context, FormData formData) {
        return null;
    }
}
