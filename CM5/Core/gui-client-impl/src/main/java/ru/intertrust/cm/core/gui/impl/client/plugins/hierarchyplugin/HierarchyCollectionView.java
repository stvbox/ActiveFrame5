package ru.intertrust.cm.core.gui.impl.client.plugins.hierarchyplugin;


import com.google.gwt.user.client.ui.*;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.gui.collection.view.CollectionViewConfig;
import ru.intertrust.cm.core.config.gui.navigation.hierarchyplugin.HierarchyCollectionConfig;
import ru.intertrust.cm.core.gui.impl.client.event.hierarchyplugin.ExpandHierarchyEvent;
import ru.intertrust.cm.core.gui.impl.client.event.hierarchyplugin.HierarchyActionEvent;
import ru.intertrust.cm.core.gui.impl.client.event.hierarchyplugin.HierarchyActionEventHandler;
import ru.intertrust.cm.core.gui.model.plugin.collection.CollectionRowItem;

/**
 * Created by IntelliJ IDEA.
 * Developer: Ravil Abdulkhairov
 * Date: 29.07.2016
 * Time: 12:11
 * To change this template use File | Settings | File and Code Templates.
 */
public class HierarchyCollectionView extends HierarchyNode implements HierarchyActionEventHandler {

    private Boolean expandable = false;
    private CollectionRowItem rowItem;
    private CollectionViewConfig collectionViewConfig;


    public HierarchyCollectionView(HierarchyCollectionConfig aCollectionConfig, CollectionRowItem aRow, CollectionViewConfig aCollectionViewConfig) {
        super();
        collectionViewConfig = aCollectionViewConfig;
        rowItem = aRow;
        collectionConfig = aCollectionConfig;
        guiElementsFactory = new HierarchyGuiElementsFactory();
        guiFactory = new HierarchyGuiFactory();
        rootPanel = new AbsolutePanel();
        rootPanel.addStyleName(HierarchyPluginStaticData.STYLE_HEADER_CELL);
        headerPanel = new HorizontalPanel();
        headerPanel.addStyleName(HierarchyPluginStaticData.STYLE_WRAP_CELL);
        childPanel = new VerticalPanel();
        childPanel.addStyleName(HierarchyPluginStaticData.STYLE_CHILD_CELL);

        if(collectionConfig.getHierarchyGroupConfigs().size()>0  ||
                collectionConfig.getHierarchyCollectionConfigs().size()>0){
            expandable = true;
        }

        addRepresentationCells(headerPanel);
        rootPanel.add(headerPanel);
        rootPanel.add(childPanel);
        childPanel.setVisible(expanded);
        initWidget(rootPanel);

        localBus.addHandler(ExpandHierarchyEvent.TYPE, this);
        localBus.addHandler(HierarchyActionEvent.TYPE, this);
    }

    @Override
    protected void addRepresentationCells(Panel container) {

        FlexTable grid = new FlexTable();
        grid.addStyleName(HierarchyPluginStaticData.STYLE_REPRESENTATION_CELL);
        if (expandable) {
            grid.setWidget(0, 0, guiElementsFactory.buildExpandCell(localBus,rowItem.getId()));
        }

        int columnIndex = 1;
        for(String key : rowItem.getRow().keySet()){
            InlineHTML fieldName = new InlineHTML("<b>" + key + "</b>");
            fieldName.addStyleName(HierarchyPluginStaticData.STYLE_FIELD_NAME);
            grid.setWidget(0, columnIndex, fieldName);
            InlineHTML fieldValue = new InlineHTML(rowItem.getRow().get(key).toString());
            fieldValue.addStyleName(HierarchyPluginStaticData.STYLE_FIELD_VALUE);
            grid.setWidget(0, columnIndex+1, fieldValue);
            columnIndex+=2;
        }

        container.add(grid);
    }


    @Override
    public void onHierarchyActionEvent(HierarchyActionEvent event) {

    }
}
