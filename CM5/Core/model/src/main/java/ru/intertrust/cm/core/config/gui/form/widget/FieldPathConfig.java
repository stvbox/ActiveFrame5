package ru.intertrust.cm.core.config.gui.form.widget;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.convert.Convert;
import ru.intertrust.cm.core.business.api.dto.Dto;
import ru.intertrust.cm.core.config.converter.FieldPathOnDeleteActionConverter;

/**
 * @author Yaroslav Bondacrhuk
 *         Date: 13/9/13
 *         Time: 12:05 PM
 */
@Root(name = "field-path")
@Convert(FieldPathOnDeleteActionConverter.class)
public class FieldPathConfig implements Dto {
    public static final String CASCADE_STRING = "cascade";
    public static final String UNLINK_STRING = "unlink";

    public static enum OnDeleteAction {
        CASCADE(CASCADE_STRING),
        UNLINK(UNLINK_STRING);

        private final String string;

        private OnDeleteAction() {
            string = null;
        }

        private OnDeleteAction(String str) {
            this.string = str;
        }

        public String getString() {
            return string;
        }

        public static OnDeleteAction getEnum(String name) {
            switch (name) {
                case CASCADE_STRING:
                    return CASCADE;
                case UNLINK_STRING:
                    return UNLINK;
            }
            return null;
        }
    }

    @Attribute(name = "value", required = false)
    private String value;

    @Attribute(name = "domain-object-linker", required = false)
    private String domainObjectLinker;

    @Attribute(name="on-root-delete", required = false)
    private OnDeleteAction onRootDelete;

    @Element(name = "on-link", required = false)
    private OnLinkConfig onLinkConfig;

    @Element(name = "on-unlink", required = false)
    private OnUnlinkConfig onUnlinkConfig;


    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public OnDeleteAction getOnRootDelete() {
        return onRootDelete;
    }

    public void setOnRootDelete(OnDeleteAction onRootDelete) {
        this.onRootDelete = onRootDelete;
    }

    public String getDomainObjectLinker() {
        return domainObjectLinker;
    }

    public void setDomainObjectLinker(String domainObjectLinker) {
        this.domainObjectLinker = domainObjectLinker;
    }

    public OnUnlinkConfig getOnUnlinkConfig() {
        return onUnlinkConfig;
    }

    public void setOnUnlinkConfig(OnUnlinkConfig onUnlinkConfig) {
        this.onUnlinkConfig = onUnlinkConfig;
    }

    public OnLinkConfig getOnLinkConfig() {
        return onLinkConfig;
    }

    public void setOnLinkConfig(OnLinkConfig onLinkConfig) {
        this.onLinkConfig = onLinkConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FieldPathConfig that = (FieldPathConfig) o;

        if (domainObjectLinker != null ? !domainObjectLinker.equals(that.domainObjectLinker) : that.domainObjectLinker != null) {
            return false;
        }
        if (onLinkConfig != null ? !onLinkConfig.equals(that.onLinkConfig) : that.onLinkConfig != null) {
            return false;
        }
        if (onRootDelete != that.onRootDelete) {
            return false;
        }
        if (onUnlinkConfig != null ? !onUnlinkConfig.equals(that.onUnlinkConfig) : that.onUnlinkConfig != null) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
