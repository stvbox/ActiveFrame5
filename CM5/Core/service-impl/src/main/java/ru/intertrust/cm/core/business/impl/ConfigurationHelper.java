package ru.intertrust.cm.core.business.impl;

import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.BusinessObjectConfig;
import ru.intertrust.cm.core.config.Configuration;

/**
 * Helper для работы с конфигурацией бизнес-объектов
 * @author vmatsukevich
 *         Date: 5/18/13
 *         Time: 4:03 PM
 */
class ConfigurationHelper {

    /**
     * Находит конфигурацию бизнес-объекта по идентификатору
     * @param configuration конфигурация бизнес-объектов
     * @param id идентификатор бизнес-объекта, конфигурацию которого надо найти
     * @return конфигурация бизнес-объекта
     */
    @Deprecated
    public static BusinessObjectConfig findBusinessObjectConfigById(Configuration configuration, Id id) {
        for(BusinessObjectConfig businessObjectConfig : configuration.getBusinessObjectConfigs()) {
            if(businessObjectConfig.getId().equals(id)) {
                return businessObjectConfig;
            }
        }
        throw new RuntimeException("BusinessObjectConfiguration is not found with id '" + id + "'");
    }
}
