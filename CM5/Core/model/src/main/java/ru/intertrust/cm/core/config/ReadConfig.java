package ru.intertrust.cm.core.config;

import org.simpleframework.xml.Attribute;

/**
 * Конфигурация разрешений на чтение объекта.
 * @author atsvetkov
 *
 */
public class ReadConfig extends BaseOperationPermitConfig {

    /**
     * Разрешает операцию чтения для всех персон.
     */
    @Attribute(name= "permit-everybody", required = false)
    private boolean permitEverybody;

    public boolean isPermitEverybody() {
        return permitEverybody;
    }

    public void setPermitEverybody(boolean permitEverybody) {
        this.permitEverybody = permitEverybody;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ReadConfig that = (ReadConfig) o;

        if (permitEverybody != that.permitEverybody ) {
            return false;
        }

        if (getPermitConfigs() != null ? !getPermitConfigs().equals(that.getPermitConfigs()) : that.getPermitConfigs() != null) {
            return false;
        }

        return true;
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
