package ru.intertrust.cm.core.gui.api.client;

import com.google.web.bindery.event.shared.EventBus;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.gui.form.widget.linkediting.LinkedFormMappingConfig;

/**
 * Created by IntelliJ IDEA.
 * Developer: Ravil Abdulkhairov
 * Date: 05.07.2016
 * Time: 14:09
 * To change this template use File | Settings | File and Code Templates.
 */
public interface CustomEdit {
    void edit(Id id, LinkedFormMappingConfig config, EventBus eventBus);
}
