package ru.intertrust.cm.core.gui.impl.server.action;

import org.springframework.beans.factory.annotation.Autowired;
import ru.intertrust.cm.core.business.api.ProcessService;
import ru.intertrust.cm.core.business.api.ProfileService;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.gui.action.ActionConfig;
import ru.intertrust.cm.core.config.localization.LocalizationKeys;
import ru.intertrust.cm.core.config.localization.MessageResourceProvider;
import ru.intertrust.cm.core.gui.api.server.action.ActionHandler;
import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.GuiException;
import ru.intertrust.cm.core.gui.model.action.ActionData;
import ru.intertrust.cm.core.gui.model.action.CompleteTaskActionContext;

/**
 * @author Denis Mitavskiy
 *         Date: 23.10.13
 *         Time: 15:18
 */
@ComponentName("complete.task.action")
public class CompleteTaskActionHandler extends ActionHandler<CompleteTaskActionContext, ActionData> {

    @Autowired
    private ProcessService processservice;

    @Autowired
    private ProfileService profileService;

    @Override
    public ActionData executeAction(CompleteTaskActionContext completeTaskActionContext) {
        Id domainObjectId = completeTaskActionContext.getRootObjectId();
        if (domainObjectId == null) {
            throw new GuiException(MessageResourceProvider.getMessage(LocalizationKeys.GUI_EXCEPTION_OBJECT_NOT_SAVED,
                    profileService.getPersonLocale()));
        }

        // todo: do some action with this domain object or with new domain
        // object
        processservice.completeTask(completeTaskActionContext.getTaskId(), null, completeTaskActionContext.getTaskAction());

        return null;
    }

    @Override
    public CompleteTaskActionContext getActionContext(final ActionConfig actionConfig) {
        return new CompleteTaskActionContext(actionConfig);
    }
}
