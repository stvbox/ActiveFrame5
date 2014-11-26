package ru.intertrust.cm.core.gui.impl.client.plugins.collection;

import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HeaderPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.gui.navigation.CollectionViewerConfig;
import ru.intertrust.cm.core.gui.api.client.Application;
import ru.intertrust.cm.core.gui.api.client.ConfirmCallback;
import ru.intertrust.cm.core.gui.impl.client.event.CollectionRowSelectedEvent;
import ru.intertrust.cm.core.gui.impl.client.event.collection.OpenDomainObjectFormEvent;
import ru.intertrust.cm.core.gui.impl.client.plugins.objectsurfer.DomainObjectSurferPlugin;
import ru.intertrust.cm.core.gui.model.plugin.collection.CollectionRowItem;

/**
 * @author Yaroslav Bondacrhuk
 *         Date: 05/03/15
 *         Time: 12:05 PM
 */
public class CollectionDataGrid extends DataGrid<CollectionRowItem>{
    private HeaderPanel panel;
    private EventBus eventBus;
    private CollectionPlugin plugin;

    public CollectionDataGrid(CollectionPlugin plugin, int pageNumber, Resources resources, EventBus eventBus) {
        super(pageNumber, resources);
        this.plugin = plugin;
        this.eventBus = eventBus;
        panel = (HeaderPanel) getWidget();
        panel.getHeaderWidget().getElement().getFirstChildElement().setClassName("dataGridHeaderRow");
        setAutoHeaderRefreshDisabled(false);
        setHeaderBuilder(new HeaderBuilder<>(this, false));
        addStyleName("collection-plugin-view collection-plugin-view-container");
        addCellPreviewHandler(new CollectionCellPreviewHandler());
        sinkEvents(Event.ONDBLCLICK | Event.ONCLICK);
        setEmptyTableMessage();
    }

    public ScrollPanel getScrollPanel() {
        return (ScrollPanel) panel.getContentWidget();
    }

    @Override
    protected boolean resetFocusOnCell() {
        return  true;
    }

    private void setEmptyTableMessage() {
        String emptyTableText = "Результаты отсутствуют";
        HTML emptyTableWidget = new HTML("<br/><div align='center'> <h1> " + emptyTableText + " </h1> </div>");
        this.setEmptyTableWidget(emptyTableWidget);

    }

    private class CollectionCellPreviewHandler implements CellPreviewEvent.Handler<CollectionRowItem> {
        private int countClick = 0;
        private Id earlierClickedId;

        @Override
        public void onCellPreview(final CellPreviewEvent<CollectionRowItem> event) {
            final Id id = event.getValue().getId();
            if (Event.getTypeInt(event.getNativeEvent().getType()) == Event.ONCLICK) {
                if (checkDirtiness()) {
                    Application.getInstance().getActionManager().checkChangesBeforeExecution(new ConfirmCallback() {
                        @Override
                        public void onAffirmative() {
                            handleClick(id);
                        }

                        @Override
                        public void onCancel() {
                            countClick = 0;
                        }
                    });
                } else {
                    handleClick(id);
                }
            }
        }

        private void handleClick(final Id id) {
            countClick++;
            if (countClick == 1) {
                performOnClickAction(id);
                Timer timer = new Timer() {
                    @Override
                    public void run() {
                        countClick = 0;
                    }
                };
                timer.schedule(500);
            } else if (countClick == 2) {
                performOnDoubleClickAction(id);
            }
        }

        public void performOnClickAction(Id id) {
            final CollectionViewerConfig collectionViewerConfig = (CollectionViewerConfig) plugin.getConfig();
            if (collectionViewerConfig.getTableBrowserParams() == null) {
                Application.getInstance().getHistoryManager().setSelectedIds(id);
            }
            eventBus.fireEvent(new CollectionRowSelectedEvent(id));
        }

        public void performOnDoubleClickAction(Id id) {
            final CollectionViewerConfig collectionViewerConfig = (CollectionViewerConfig) plugin.getConfig();
            if (collectionViewerConfig.getTableBrowserParams() == null) {
                eventBus.fireEvent(new OpenDomainObjectFormEvent(id));
            }
        }

        private boolean checkDirtiness() {
            final DomainObjectSurferPlugin parentSurfer = plugin.getContainingDomainObjectSurferPlugin();
            return parentSurfer != null && !parentSurfer.getPluginState().isToggleEdit();
        }
    }
}
