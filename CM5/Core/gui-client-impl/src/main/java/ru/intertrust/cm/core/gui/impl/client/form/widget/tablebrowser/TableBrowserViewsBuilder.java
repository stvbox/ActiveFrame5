package ru.intertrust.cm.core.gui.impl.client.form.widget.tablebrowser;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.web.bindery.event.shared.EventBus;
import ru.intertrust.cm.core.config.gui.form.widget.*;
import ru.intertrust.cm.core.gui.impl.client.PluginPanel;
import ru.intertrust.cm.core.gui.impl.client.form.widget.buttons.ClearAllConfiguredButton;
import ru.intertrust.cm.core.gui.impl.client.form.widget.buttons.ConfiguredButton;
import ru.intertrust.cm.core.gui.impl.client.form.widget.buttons.OpenCollectionConfiguredButton;
import ru.intertrust.cm.core.gui.impl.client.form.widget.hyperlink.HyperlinkNoneEditablePanel;
import ru.intertrust.cm.core.gui.model.form.widget.TableBrowserState;
import ru.intertrust.cm.core.gui.model.util.WidgetUtil;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 19.11.2014
 *         Time: 6:12
 */
public class TableBrowserViewsBuilder {
    public static final int DEFAULT_DIALOG_WIDTH = 800;
    public static final int DEFAULT_DIALOG_HEIGHT = 300;
    public static final int DEFAULT_TABLE_HEIGHT = 1000;
    public static final int DEFAULT_TABLE_WIDTH = 300;
    public static final int DEFAULT_HEIGHT_OFFSET = 100;
    private int dialogWidth;
    private int dialogHeight;
    private int tableHeight;
    private int tableWidth;
    private TableBrowserState state;
    private HasLinkedFormMappings hasLinkedFormMappings;
    private EventBus eventBus;
    private boolean editable;
    private ClickHandler openCollectionButtonHandler;
    private ConfiguredButton createLinkedItemButton;
    private WidgetDisplayConfig displayConfig;

    public TableBrowserViewsBuilder withState(TableBrowserState state) {
        this.state = state;
        return this;
    }

    public TableBrowserViewsBuilder withHasLinkedFormMappings(HasLinkedFormMappings hasLinkedFormMappings) {
        this.hasLinkedFormMappings = hasLinkedFormMappings;
        return this;
    }

    public TableBrowserViewsBuilder withEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        return this;

    }

    public TableBrowserViewsBuilder withEditable(boolean editable) {
        this.editable = editable;
        return this;

    }

    public TableBrowserViewsBuilder withOpenCollectionButtonHandler(ClickHandler clickHandler) {
        this.openCollectionButtonHandler = clickHandler;
        return this;

    }

    public TableBrowserViewsBuilder withCreateLinkedItemButton(ConfiguredButton button) {
        this.createLinkedItemButton = button;
        return this;

    }
    public TableBrowserViewsBuilder withWidgetDisplayConfig(WidgetDisplayConfig displayConfig) {
        this.displayConfig = displayConfig;
        return this;

    }

    public ViewHolder buildViewHolder() {
        if (editable) {
            return new TableBrowserEditableViewBuilder().buildViewHolder();
        } else {
            return new TableBrowserNoneEditableViewBuilder().buildViewHolder();
        }
    }
    public CollectionDialogBox buildCollectionDialogBox(){
        PluginPanel pluginPanel = createDialogCollectionPluginPanel();
        CollectionDialogBox dialogBox = new CollectionDialogBox()
                .withDialogWidth(dialogWidth)
                .setDialogHeight(dialogHeight)
                .setPluginPanel(pluginPanel);
        dialogBox.init();
        return dialogBox;

    }

    private void initDialogWindowSize() {
        DialogWindowConfig dialogWindowConfig = state.getTableBrowserConfig().getDialogWindowConfig();
        String widthString = dialogWindowConfig != null ? dialogWindowConfig.getWidth() : null;
        String heightString = dialogWindowConfig != null ? dialogWindowConfig.getHeight() : null;
        dialogWidth = widthString == null ? DEFAULT_DIALOG_WIDTH : Integer.parseInt(widthString.replaceAll("\\D+", ""));
        dialogHeight = heightString == null ? DEFAULT_DIALOG_HEIGHT : Integer.parseInt(heightString.replaceAll("\\D+", ""));
    }

    private void initWidgetSize() {
        String widthString = displayConfig != null ? displayConfig.getWidth() : null;
        String heightString = displayConfig!= null ? displayConfig.getHeight() : null;
        tableWidth = widthString == null ? DEFAULT_TABLE_WIDTH : Integer.parseInt(widthString.replaceAll("\\D+", ""));
        tableHeight = heightString == null ? DEFAULT_TABLE_HEIGHT : Integer.parseInt(heightString.replaceAll("\\D+", ""));
    }

    public PluginPanel createDialogCollectionPluginPanel() {
        initDialogWindowSize();
        PluginPanel pluginPanel = new PluginPanel();
        pluginPanel.setVisibleWidth(dialogWidth);
        pluginPanel.setVisibleHeight(dialogHeight - DEFAULT_HEIGHT_OFFSET);//it's height of table body only. TODO: eliminate hardcoded value
        return pluginPanel;

    }
    public PluginPanel createWidgetCollectionPluginPanel() {
        initWidgetSize();
        PluginPanel pluginPanel = new PluginPanel();
        pluginPanel.setVisibleWidth(tableWidth);
        pluginPanel.setVisibleHeight(tableHeight - DEFAULT_HEIGHT_OFFSET);
        return pluginPanel;

    }

    class TableBrowserNoneEditableViewBuilder {
        private ViewHolder buildViewHolder() {
            SelectionStyleConfig styleConfig = state.getTableBrowserConfig().getSelectionStyleConfig();
            if (WidgetUtil.drawAsTable(styleConfig)) {
                TableBrowserCollection collection = createTableBrowserCollection(true, false, false);
                return new TableBrowserCollectionViewHolder(collection);
            } else {
                HyperlinkNoneEditablePanel widget = new HyperlinkNoneEditablePanel(styleConfig, eventBus, false,
                        state.getTypeTitleMap(), hasLinkedFormMappings);
                return new HyperlinkNoneEditablePanelViewHolder(widget);
            }
        }
    }

    class TableBrowserEditableViewBuilder {
        private ViewHolder buildViewHolder() {
            SelectionStyleConfig styleConfig = state.getTableBrowserConfig().getSelectionStyleConfig();
            if (WidgetUtil.drawAsTable(styleConfig)) {
                TableBrowserCollection mainWidget = createTableBrowserCollection(true, true, true);
                TableBrowserCollectionViewHolder childViewHolder = new TableBrowserCollectionViewHolder(mainWidget);
                TableBrowserEditableView editableView = createEditableWidgetView(mainWidget);
                TableBrowserEditableViewHolder tableBrowserEditableViewHolder = new TableBrowserEditableViewHolder(editableView);
                tableBrowserEditableViewHolder.setChildViewHolder(childViewHolder);
                return tableBrowserEditableViewHolder;
            } else {
                TableBrowserItemsView mainWidget = new TableBrowserItemsView(styleConfig, eventBus, state.getTypeTitleMap(),
                        hasLinkedFormMappings);
                TableBrowserItemsViewHolder childViewHolder = new TableBrowserItemsViewHolder(mainWidget);
                TableBrowserEditableView editableView = createEditableWidgetView(mainWidget);
                TableBrowserEditableViewHolder tableBrowserEditableViewHolder = new TableBrowserEditableViewHolder(editableView);
                tableBrowserEditableViewHolder.setChildViewHolder(childViewHolder);
                return tableBrowserEditableViewHolder;
            }
        }
    }


    private TableBrowserEditableView createEditableWidgetView(final TableBrowserEditableComposite mainWidget) {
        final TableBrowserEditableView root = new TableBrowserEditableView();
        ConfiguredButton openDialogButton = createOpenButton();
        openDialogButton.addClickHandler(openCollectionButtonHandler);
        root.addStyleName("tableBrowserRoot");
        root.addMainWidget(mainWidget);
        root.addWidget(openDialogButton);
        if (createLinkedItemButton != null) {
            root.addWidget(createLinkedItemButton);
        }
        ConfiguredButton clearButton = createClearButton();
        if (clearButton != null) {
            clearButton.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    state.clearState();
                    root.clearContent();
                }
            });
            root.addWidget(clearButton);
        }

        return root;
    }

    private TableBrowserCollection createTableBrowserCollection(Boolean displayOnlySelectedIds, Boolean displayCheckBoxes,
                                                                Boolean displayFilter) {
        boolean tooltipLimitation = WidgetUtil.getLimit(state.getWidgetConfig().getSelectionFiltersConfig()) != -1;
        TableBrowserCollection result = new TableBrowserCollection()
                .withEventBus(eventBus)
                .withPluginPanel(createWidgetCollectionPluginPanel())
                .withHeight(tableHeight)
                .withDisplayOnlySelectedIds(displayOnlySelectedIds)
                .withDisplayCheckBoxes(displayCheckBoxes)
                .withDisplayFilter(displayFilter)
                .withTooltipLimitation(tooltipLimitation)
                .createView();

        return result;
    }

    private ConfiguredButton createOpenButton() {
       TableBrowserConfig tableBrowserConfig = state.getTableBrowserConfig();
       return  new OpenCollectionConfiguredButton(tableBrowserConfig.getAddButtonConfig());
    }

    private ConfiguredButton createClearButton() {
        TableBrowserConfig tableBrowserConfig = state.getTableBrowserConfig();
        ClearAllButtonConfig config = tableBrowserConfig.getClearAllButtonConfig();
        if (config != null) {
            return new ClearAllConfiguredButton(config);
        }
        return null;

    }
}
