package ru.intertrust.cm.core.gui.model.form.widget;

import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.gui.model.plugin.FormPluginConfig;

import java.util.ArrayList;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 14.01.14
 *         Time: 10:25
 */
public class LinkedDomainObjectHyperlinkState extends LinkEditingWidgetState {
    private String stringRepresentation;
    private Id id;
    private FormPluginConfig config;
    private String domainObjectType;
    public String getStringRepresentation() {
        return stringRepresentation;
    }

    public void setStringRepresentation(String stringRepresentation) {
        this.stringRepresentation = stringRepresentation;
    }

    public Id getId() {
        return id;
    }

    public void setId(Id id) {
        this.id = id;
    }

    public FormPluginConfig getConfig() {
        return config;
    }

    public void setConfig(FormPluginConfig config) {
        this.config = config;
    }

    public String getDomainObjectType() {
        return domainObjectType;
    }

    public void setDomainObjectType(String domainObjectType) {
        this.domainObjectType = domainObjectType;
    }

    @Override
    public ArrayList<Id> getIds() {
        return new ArrayList<Id>(0);
    }
}
