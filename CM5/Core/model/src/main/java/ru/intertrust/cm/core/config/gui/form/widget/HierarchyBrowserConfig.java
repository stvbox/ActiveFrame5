package ru.intertrust.cm.core.config.gui.form.widget;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 10.12.13
 *         Time: 11:40
 */
@Root(name = "hierarchy-browser")
public class HierarchyBrowserConfig extends WidgetConfig {

    @Element(name = "node-collection-def")
    private NodeCollectionDefConfig nodeCollectionDefConfig;

    @Element(name = "page-size",required = false)
    private Integer pageSize;

    public NodeCollectionDefConfig getNodeCollectionDefConfig() {
        return nodeCollectionDefConfig;
    }

    public void setNodeCollectionDefConfig(NodeCollectionDefConfig nodeCollectionDefConfig) {
        this.nodeCollectionDefConfig = nodeCollectionDefConfig;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        HierarchyBrowserConfig that = (HierarchyBrowserConfig) o;

        if (nodeCollectionDefConfig != null ? !nodeCollectionDefConfig.equals(that.
                nodeCollectionDefConfig) : that.nodeCollectionDefConfig != null) {
            return false;
        }
        if (pageSize != null ? !pageSize.equals(that.pageSize) : that.pageSize != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (nodeCollectionDefConfig != null ? nodeCollectionDefConfig.hashCode() : 0);
        result = 31 * result + (pageSize != null ? pageSize.hashCode() : 0);
        return result;
    }

    @Override
    public String getComponentName() {
        return "hierarchy-browser";
    }
}
