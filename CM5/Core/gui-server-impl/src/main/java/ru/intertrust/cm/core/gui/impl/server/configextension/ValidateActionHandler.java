package ru.intertrust.cm.core.gui.impl.server.configextension;

import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.config.ConfigurationException;
import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.GuiException;
import ru.intertrust.cm.core.gui.model.action.SimpleActionContext;
import ru.intertrust.cm.core.gui.model.action.SimpleActionData;

import java.util.Collection;
import java.util.List;

/**
 * Created by Ravil on 16.05.2017.
 */
@ComponentName("validate.action.handler")
public class ValidateActionHandler extends ConfigExtensionsGuiActionBase {



    @Override
    public SimpleActionData executeAction(SimpleActionContext context) {
        SimpleActionData aData = new SimpleActionData();
        if(context.getRootObjectId()!=null){
            List<DomainObject> toolingDos = getToolingDos(context.getRootObjectId());
            if(toolingDos.size()>0) {
                final Collection<ConfigurationException> configurationExceptions = configurationControlService.validateDrafts(toolingDos);
                if (configurationExceptions.isEmpty()) {
                    aData.setOnSuccessMessage("Объект успешно проверен.");
                } else {
                    StringBuilder resultMessage = new StringBuilder();
                    for (ConfigurationException configurationException : configurationExceptions) {
                        resultMessage.append(configurationException.getMessage()).append("\n");
                    }
                    throw new GuiException(resultMessage.toString());
                }
            } else {
                aData.setOnFailureMessage("Для данного обьекта нет шаблона");
            }
        } else {
            aData.setOnFailureMessage("Обьект еще не сохранен");
        }
        return aData;
    }


}
