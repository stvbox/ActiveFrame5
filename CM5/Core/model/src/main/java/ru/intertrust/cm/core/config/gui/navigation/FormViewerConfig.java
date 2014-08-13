package ru.intertrust.cm.core.config.gui.navigation;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import ru.intertrust.cm.core.business.api.dto.Dto;
import ru.intertrust.cm.core.config.gui.form.FormViewerMappingConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lesia Puhova
 *         Date: 11.08.14
 *         Time: 13:54
 */
@Root(name = "form-viewer")
public class FormViewerConfig implements Dto {

    @Attribute(name="form-mapping-component", required = false)
    private String formMappingComponent;

    @ElementList(inline = true, required = false)
    private List<FormViewerMappingConfig> formMappingConfigList = new ArrayList<>();

    public String getFormMappingComponent() {
        return formMappingComponent;
    }

    public void setFormMappingComponent(String formMappingComponent) {
        this.formMappingComponent = formMappingComponent;
    }

    public List<FormViewerMappingConfig> getFormMappingConfigList() {
        return formMappingConfigList;
    }

    public void setFormMappingConfigList(List<FormViewerMappingConfig> formMappingConfigList) {
        this.formMappingConfigList = formMappingConfigList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FormViewerConfig that = (FormViewerConfig) o;

        if (formMappingComponent != null ? !formMappingComponent.equals(that.formMappingComponent) : that
                .formMappingComponent != null) {
            return false;
        }
        if (formMappingConfigList != null ? !formMappingConfigList.equals(that.formMappingConfigList) : that
                .formMappingConfigList != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = formMappingComponent != null ? formMappingComponent.hashCode() : 0;
        result = 31 * result + (formMappingConfigList != null ? formMappingConfigList.hashCode() : 0);
        return result;
    }
}
