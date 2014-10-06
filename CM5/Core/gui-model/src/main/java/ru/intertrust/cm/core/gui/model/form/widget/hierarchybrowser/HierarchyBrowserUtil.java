package ru.intertrust.cm.core.gui.model.form.widget.hierarchybrowser;

import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.gui.form.widget.DisplayValuesAsLinksConfig;
import ru.intertrust.cm.core.config.gui.form.widget.NodeCollectionDefConfig;
import ru.intertrust.cm.core.config.gui.form.widget.filter.SelectionFiltersConfig;
import ru.intertrust.cm.core.gui.model.form.widget.HierarchyBrowserItem;
import ru.intertrust.cm.core.gui.model.form.widget.HierarchyBrowserWidgetState;
import ru.intertrust.cm.core.gui.model.util.WidgetUtil;

import java.util.*;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 17.09.2014
 *         Time: 0:15
 */
public class HierarchyBrowserUtil {
    public static NodeCollectionDefConfig getRequiredNodeConfig(String collectionName, String domainObjectType,
                                                                Map<String, NodeCollectionDefConfig> collectionNameNodeMap) {
        NodeCollectionDefConfig rootNodeCollectionDefConfig = collectionNameNodeMap.get(collectionName);
        List<NodeCollectionDefConfig> nodeCollectionDefConfigs = rootNodeCollectionDefConfig.getNodeCollectionDefConfigs();
        for (NodeCollectionDefConfig nodeCollectionDefConfig : nodeCollectionDefConfigs) {
            if (domainObjectType.equalsIgnoreCase(nodeCollectionDefConfig.getDomainObjectType())) {
                return nodeCollectionDefConfig;
            }
        }
        return rootNodeCollectionDefConfig;
    }

    public static int getSizeFromString(String size, int defaultSize) {
        if (size == null) {
            return defaultSize;
        }
        String temp = size.replaceAll("\\D", "");
        return Integer.parseInt(temp);
    }

    public static boolean isDisplayingHyperlinks(HierarchyBrowserWidgetState state) {
        DisplayValuesAsLinksConfig displayValuesAsLinksConfig = state.getHierarchyBrowserConfig()
                .getDisplayValuesAsLinksConfig();
        return displayValuesAsLinksConfig != null && displayValuesAsLinksConfig.isValue();
    }

    public static ArrayList<HierarchyBrowserItem> getCopyOfChosenItems(ArrayList<HierarchyBrowserItem> itemsToCopy) {
        ArrayList<HierarchyBrowserItem> copyOfItems = new ArrayList<HierarchyBrowserItem>(itemsToCopy.size());
        for (HierarchyBrowserItem item : itemsToCopy) {
            copyOfItems.add(item.getCopy());
        }
        return copyOfItems;
    }

    public static boolean shouldInitializeTooltip(HierarchyBrowserWidgetState state, int delta){
        if(state.getTooltipChosenItems() != null){
            return false;
        }
        return state.isTooltipAvailable(delta);

    }

    public static void preHandleAddingItemToTempState(HierarchyBrowserItem item, HierarchyBrowserWidgetState state){
       if(!state.isTooltipAvailable(1)){
           return;
       }
        List<HierarchyBrowserItem> temporaryTooltipItems = state.getTooltipChosenItems();
        List<HierarchyBrowserItem> temporaryChosenItems = state.getTemporaryChosenItems();
        String collectionName = item.getNodeCollectionName();
        SelectionFiltersConfig selectionFiltersConfig = state.getCollectionNameNodeMap()
                .get(collectionName).getSelectionFiltersConfig();
        Integer expected = WidgetUtil.getLimit(selectionFiltersConfig);
        List<HierarchyBrowserItem > listOfParticularCollection = getListOfParticularCollection(temporaryChosenItems,
                collectionName);
        int actual = listOfParticularCollection.size();
        if(expected == null || expected < actual){
            return;
        }
        HierarchyBrowserItem itemForTooltip = temporaryChosenItems.remove(temporaryChosenItems.size() - 1);
        if(itemForTooltip != null){
            temporaryTooltipItems.add(itemForTooltip);

        }

    }

    public static void preHandleRemovingItem(HierarchyBrowserItem item, HierarchyBrowserWidgetState state){
        if(!state.isTooltipAvailable()){
            return;
        }
        List<HierarchyBrowserItem> content = state.getCurrentItems();
       if(content.contains(item)){
        List<HierarchyBrowserItem> tooltipItems = state.getTooltipChosenItems();
        HierarchyBrowserItem itemToContent = tooltipItems.remove(0);

        content.add(itemToContent);
       }

    }

    
    private static List<HierarchyBrowserItem> getListOfParticularCollection(List<HierarchyBrowserItem> items, String collectionName) {
        List<HierarchyBrowserItem> result = new ArrayList<HierarchyBrowserItem>();
        for (HierarchyBrowserItem item : items) {
            if (collectionName.equalsIgnoreCase(item.getNodeCollectionName())) {
                result.add(item);
            }
        }
        return result;
    }

    public static void handleUpdateChosenItem(HierarchyBrowserItem updatedItem, List<HierarchyBrowserItem> chosenItems) {
        HierarchyBrowserItem itemToReplace = findHierarchyBrowserItem(updatedItem.getId(), chosenItems);
        if (itemToReplace == null) {
            return;
        }
        String collectionName = itemToReplace.getNodeCollectionName();
        updatedItem.setNodeCollectionName(collectionName);
        updatedItem.setChosen(itemToReplace.isChosen());
        int index = chosenItems.indexOf(itemToReplace);
        chosenItems.set(index, updatedItem);

    }

    private static HierarchyBrowserItem findHierarchyBrowserItem(Id id, List<HierarchyBrowserItem> chosenItems) {
        for (HierarchyBrowserItem item : chosenItems) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    public static Map<String, Integer> createTemporaryCountOfType(Map<String, NodeCollectionDefConfig> defConfigMap){
        Map<String, Integer> result = new HashMap<String, Integer>();
        Set<String> collectionNames = defConfigMap.keySet();
        for (String collectionName : collectionNames) {
            NodeCollectionDefConfig config = defConfigMap.get(collectionName);
            result.put(collectionName, config.getElementsCount());
        }
        return result;
    }

    public static void updateCountOfType(Map<String, Integer> countMap, Map<String, NodeCollectionDefConfig> defConfigMap){
        Set<String> collectionNames = defConfigMap.keySet();
        for (String collectionName : collectionNames) {
            NodeCollectionDefConfig config = defConfigMap.get(collectionName);
            Integer count = countMap.get(collectionName);
            config.setElementsCount(count);
        }
    }

}
