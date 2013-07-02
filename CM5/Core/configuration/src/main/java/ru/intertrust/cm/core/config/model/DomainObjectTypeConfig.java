package ru.intertrust.cm.core.config.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Mitavskiy
 *         Date: 5/1/13
 *         Time: 8:50 PM
 */
@Root(name = "domain-object-type")
public class DomainObjectTypeConfig implements Serializable {

    private Long id;

    @Attribute(required = true)
    private String name;

    @Attribute(name = "extends", required = false)
    private String parentConfig;

    // we can't use a list here directly, as elements inside are different, that's why such a "trick"
    @Element(name = "fields")
    private DomainObjectFieldsConfig domainObjectFieldsConfig = new DomainObjectFieldsConfig();

    @ElementList(entry="uniqueKey", type=UniqueKeyConfig.class, inline=true, required = false)
    private List<UniqueKeyConfig> uniqueKeyConfigs = new ArrayList<>();

    public DomainObjectTypeConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentConfig() {
        return parentConfig;
    }

    public void setParentConfig(String parentConfig) {
        this.parentConfig = parentConfig;
    }

    public DomainObjectFieldsConfig getDomainObjectFieldsConfig() {
        return domainObjectFieldsConfig;
    }

    public void setDomainObjectFieldsConfig(DomainObjectFieldsConfig domainObjectFieldsConfig) {
        this.domainObjectFieldsConfig = domainObjectFieldsConfig;
    }

    public List<FieldConfig> getFieldConfigs() {
        return domainObjectFieldsConfig.getFieldConfigs();
    }

    public List<UniqueKeyConfig> getUniqueKeyConfigs() {
        return uniqueKeyConfigs;
    }

    public void setUniqueKeyConfigs(List<UniqueKeyConfig> uniqueKeyConfigs) {
        if(uniqueKeyConfigs != null) {
            this.uniqueKeyConfigs = uniqueKeyConfigs;
        } else {
            this.uniqueKeyConfigs.clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DomainObjectTypeConfig that = (DomainObjectTypeConfig) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (parentConfig != null ? !parentConfig.equals(that.parentConfig) : that.parentConfig != null) {
            return false;
        }
        if (domainObjectFieldsConfig != null ? !domainObjectFieldsConfig.equals(that.domainObjectFieldsConfig) : that.domainObjectFieldsConfig != null) {
            return false;
        }
        if (uniqueKeyConfigs != null ? !uniqueKeyConfigs.equals(that.uniqueKeyConfigs) : that.uniqueKeyConfigs != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
