package ru.intertrust.cm.core.gui.impl.client.form.widget.hyperlink;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Label;
import com.google.web.bindery.event.shared.EventBus;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.gui.form.widget.HasLinkedFormMappings;
import ru.intertrust.cm.core.config.gui.form.widget.SelectionStyleConfig;
import ru.intertrust.cm.core.gui.impl.client.form.widget.tooltip.TooltipWidget;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 03.01.14
 *         Time: 13:15
 */
public class HyperlinkNoneEditablePanel extends NoneEditablePanel implements HyperlinkDisplay {
    private boolean tooltipContent;
    private String hyperlinkPopupTitle;
    private HasLinkedFormMappings widget;

    public HyperlinkNoneEditablePanel(SelectionStyleConfig selectionStyleConfig, EventBus eventBus,
                                      boolean tooltipContent, String hyperlinkPopupTitle, HasLinkedFormMappings widget) {
        super(selectionStyleConfig, eventBus);
        this.tooltipContent = tooltipContent;
        this.hyperlinkPopupTitle = hyperlinkPopupTitle;
        this.widget = widget;
    }

    public void displayHyperlink(Id id, String itemRepresentation) {
        AbsolutePanel element = new AbsolutePanel();
        element.addStyleName("facebook-element");
        Label label = new Label(itemRepresentation);
        label.setStyleName("facebook-label");
        label.addStyleName("facebook-clickable-label");
        label.addClickHandler(new HyperlinkClickHandler(id, this, eventBus, tooltipContent, hyperlinkPopupTitle,widget));
        element.getElement().getStyle().setDisplay(displayStyle);
        element.add(label);
        if (displayStyle.equals(Style.Display.INLINE_BLOCK)) {
            element.getElement().getStyle().setFloat(Style.Float.LEFT);
            label.getElement().getStyle().setFloat(Style.Float.LEFT);

        }
        mainBoxPanel.add(element);
    }

    public void displayHyperlinks(LinkedHashMap<Id, String> listValues, boolean drawTooltipButton) {
        mainBoxPanel.clear();
        Set<Map.Entry<Id, String>> entries = listValues.entrySet();
        for (Map.Entry<Id, String> entry : entries) {
            Id id = entry.getKey();
            String representation = entry.getValue();
            displayHyperlink(id, representation);
        }
        if(drawTooltipButton){
            addTooltipButton();
        }
    }


}