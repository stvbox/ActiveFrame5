package ru.intertrust.cm.core.gui.impl.client.plugins.collection.view;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import ru.intertrust.cm.core.gui.model.plugin.collection.CollectionRowItem;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 15.06.2015
 *         Time: 9:36
 */
public class ControlExpandableCell extends AbstractTextCell {

    public ControlExpandableCell(String style, String field) {
        super(style, field);
    }

    @Override
    public void render(com.google.gwt.cell.client.Cell.Context context, String text, SafeHtmlBuilder sb) {

        CollectionRowItem item = (CollectionRowItem) context.getKey();
        boolean expanded = item.isExpanded();
        CollectionRowItem.RowType rowType = item.getRowType();
        switch (rowType){
            case DATA:
                sb.append(SafeHtmlUtils.fromTrustedString("<div class=\"collectionCellWrapper\" >"));
                if (expanded) {
                    sb.append(SafeHtmlUtils.fromTrustedString("<div "));
                    addClassName(item, sb);
                    sb.append(SafeHtmlUtils.fromTrustedString(style));
                    sb.append(SafeHtmlUtils.fromTrustedString("/><span ><span class=\"collapseSign"));
                    sb.append(SafeHtmlUtils.fromTrustedString("\">-</span>"));
                    sb.append(SafeHtmlUtils.fromString(text));
                    sb.append(SafeHtmlUtils.fromTrustedString("<span></div>"));
                } else {
                    sb.append(SafeHtmlUtils.fromTrustedString("<div "));
                    addClassName(item, sb);
                    sb.append(SafeHtmlUtils.fromTrustedString(style));
                    sb.append(SafeHtmlUtils.fromTrustedString("/><span ><span class=\"expandSign"));
                    sb.append(SafeHtmlUtils.fromTrustedString("\">+</span>"));
                    sb.append(SafeHtmlUtils.fromString(text));
                    sb.append(SafeHtmlUtils.fromTrustedString("<span></div>"));
                }
                break;
            case FILTER:
                sb.append(SafeHtmlUtils.fromTrustedString("<div class=\"collectionFilterCellWrapper\" >"));
                sb.append(SafeHtmlUtils.fromTrustedString("<div class=\"hierarchicalFilter\">"));
                sb.append(SafeHtmlUtils.fromTrustedString("<input class=\"hierarchicalFilterInput\"\" value = \""));
                String filterText = item.getFilterValue(field);
                sb.append(SafeHtmlUtils.fromTrustedString(filterText));
                sb.append(SafeHtmlUtils.fromTrustedString("\" "));
                sb.append(SafeHtmlUtils.fromTrustedString("type=\"text\" tabindex=\"-1\"></input><div></div></div>"));
                break;
            case BUTTON:
                sb.append(SafeHtmlUtils.fromTrustedString("<div class=\"collectionButtonCellWrapper\" >"));
                sb.append(SafeHtmlUtils.fromTrustedString("<button class=\"moreItems"));
                sb.append(SafeHtmlUtils.fromTrustedString("\">More</button>"));
                break;
        }


        sb.append(SafeHtmlUtils.fromTrustedString("</div>"));
    }


    @Override
    public Set<String> getConsumedEvents() {
        HashSet<String> events = new HashSet<String>();
        events.add(BrowserEvents.CLICK);
        events.add(BrowserEvents.KEYDOWN);
        return events;
    }


}
