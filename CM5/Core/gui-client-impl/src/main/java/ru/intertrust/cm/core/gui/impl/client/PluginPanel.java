package ru.intertrust.cm.core.gui.impl.client;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import ru.intertrust.cm.core.gui.impl.client.event.PluginPanelSizeChangedEventHandler;

import java.util.ArrayList;

/**
 * Панель плагинов. Имеет возможность отображать плагины (как самостоятельные, так и дочерние), поддерживая порядок их
 * открытия.
 *
 * @author Denis Mitavskiy
 *         Date: 20.08.13
 *         Time: 16:53
 */
public class PluginPanel implements IsWidget {

    private int visibleWidth;
    private int visibleHeight;
    private SimplePanel impl = new SimplePanel();
    // LinkedList doesn't emulate GWT fully, thus we're using ArrayList
    private ArrayList<Plugin> plugins = new ArrayList<Plugin>(1);
    private boolean openingChild;

    public PluginPanel() {
    }

    public int getVisibleWidth() {
        return visibleWidth;
    }

    public void setVisibleWidth(int visibleWidth) {
        this.visibleWidth = visibleWidth;
    }

    public int getVisibleHeight() {
        return visibleHeight;
    }

    public void setVisibleHeight(int visibleHeight) {
        this.visibleHeight = visibleHeight;
    }

    public Plugin getCurrentPlugin() {
        return plugins.isEmpty() ? null : plugins.get(plugins.size() - 1);
    }

    /**
     * <p>
     * Открывает плагин, замещая предыдущий, если он был открыт. Операция необратима, восстановить предыдущий плагин или
     * любого его открытия невозможно.
     * </p>
     *
     * @param plugin плагин, который нужно открыть в панели
     */
    public void open(Plugin plugin) {
        this.openingChild = false;
        plugin.setOwner(this);
        plugin.setUp();
    }

    /**
     * <p>
     * Открывает дочерний плагин, замещая предыдущий, если он был открыт. Восстановить предыдущий плагин, вызвав метод
     * {@link #closeCurrentPlugin()}.
     * </p>
     *
     * @param plugin плагин, который нужно открыть в панели
     */
    public void openChild(Plugin plugin) {
        this.openingChild = true;
        plugin.setOwner(this);
        plugin.setUp();
    }

    /**
     * Закрывает текущий плагин. Если у него есть родитель, то он будет показан по закрытию текущего.
     */
    public void closeCurrentPlugin() {
        Plugin currentPluginToClose = removeCurrentPluginLeavingViewDisplayed();
        if (currentPluginToClose == null) {
            return;
        }

        Plugin parentPlugin = getCurrentPlugin(); // current plugin is already a different one
        if (parentPlugin != null) { // there's a parent plugin to display after closing previous
            impl.setWidget(parentPlugin.getView());
        } else {
            impl.remove(currentPluginToClose.getView());
        }
    }

    /**
     * Закрывает все плагины. Операция необратима.
     */
    public void closeAllPlugins() {

    }

    public void beforePluginOpening() {
        // noop
    }

    public void onPluginViewCreated(Plugin plugin) {
        if (!this.openingChild) { // replace current plugin or just create a first one
            removeAllPluginsLeavingViewDisplayed();
        }
        this.plugins.add(plugin);
        beforePluginOpening();
        impl.setWidget(plugin.getView());
    }

    @Override
    public Widget asWidget() {
        return impl;
    }

    public void setSize(String width, String height) {
        impl.setSize(width, height);
    }

    private Plugin removeCurrentPluginLeavingViewDisplayed() {
        Plugin currentPluginToClose = getCurrentPlugin();
        if (currentPluginToClose == null) {
            return null;
        }
        currentPluginToClose.clearHandlers();
        this.plugins.remove(this.plugins.size() - 1);
        return currentPluginToClose;
    }

    private void removeAllPluginsLeavingViewDisplayed() {
        while (this.plugins.size() != 0) {
            removeCurrentPluginLeavingViewDisplayed();
        }
    }
}
