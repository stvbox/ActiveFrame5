package ru.intertrust.cm.core.gui.impl.client.action.system;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.DOM;
import ru.intertrust.cm.core.gui.api.client.Application;
import ru.intertrust.cm.core.gui.api.client.CompactModeState;
import ru.intertrust.cm.core.gui.api.client.Component;
import ru.intertrust.cm.core.gui.impl.client.ComponentHelper;
import ru.intertrust.cm.core.gui.impl.client.action.ToggleAction;
import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.action.ToggleActionContext;

/**
 * @author Sergey.Okolot
 */
@ComponentName("size.toggle.action")
public class SizeToggleAction extends ToggleAction {

    @Override
    protected void execute() {
        final CompactModeState compactModeState = Application.getInstance().getCompactModeState();
        final ToggleActionContext actionContext = getInitialContext();
        if (!compactModeState.isExpanded()) {
            final Element header = DOM.getElementById(ComponentHelper.HEADER_ID);
            final Element left = DOM.getElementById(ComponentHelper.LEFT_ID);
            compactModeState.setTopOffset(header.getOffsetHeight());
            compactModeState.setLeftOffset(left.getOffsetWidth());
            compactModeState.setLeft(getPlugin().getView().asWidget().getParent().getParent()
                    .getElement().getOffsetLeft());
        }
        compactModeState.setExpanded(!compactModeState.isExpanded());
        actionContext.setPushed(compactModeState.isExpanded());
        updateSize(compactModeState);
    }

    private void updateSize(final CompactModeState state) {
        final Element left = DOM.getElementById(ComponentHelper.LEFT_ID);
        final Element header = DOM.getElementById(ComponentHelper.HEADER_ID);
        final Element center = DOM.getElementById(ComponentHelper.DOMAIN_ID);
        if (state.isExpanded()) {
            header.getStyle().setDisplay(Style.Display.NONE);
            left.getStyle().setDisplay(Style.Display.NONE);
            center.replaceClassName("central-div-panel-test", "central-div-panel-test-full");

        } else {
            header.getStyle().setDisplay(Style.Display.BLOCK);
            left.getStyle().setDisplay(Style.Display.BLOCK);
            center.replaceClassName("central-div-panel-test-full", "central-div-panel-test");
        }
    }

    @Override
    public Component createNew() {
        return new SizeToggleAction();
    }
}
