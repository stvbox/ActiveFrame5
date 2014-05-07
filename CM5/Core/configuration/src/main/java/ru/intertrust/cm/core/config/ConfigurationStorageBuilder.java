package ru.intertrust.cm.core.config;

import ru.intertrust.cm.core.business.api.dto.CaseInsensitiveMap;
import ru.intertrust.cm.core.business.api.dto.GenericDomainObject;
import ru.intertrust.cm.core.config.base.Configuration;
import ru.intertrust.cm.core.config.base.TopLevelConfig;
import ru.intertrust.cm.core.config.gui.action.ToolBarConfig;
import ru.intertrust.cm.core.config.gui.collection.view.CollectionColumnConfig;
import ru.intertrust.cm.core.config.gui.collection.view.CollectionViewConfig;
import ru.intertrust.cm.core.model.FatalException;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ConfigurationStorageBuilder {

    private static final String ALL_STATUSES_SIGN = "*";
    private final static String GLOBAL_SETTINGS_CLASS_NAME = "ru.intertrust.cm.core.config.GlobalSettingsConfig";

    private ConfigurationExplorer configurationExplorer;
    private ConfigurationStorage configurationStorage;

    ConfigurationStorageBuilder(ConfigurationExplorer configurationExplorer, ConfigurationStorage configurationStorage) {
        this.configurationExplorer = configurationExplorer;
        this.configurationStorage = configurationStorage;
    }

    void buildConfigurationStorage() {
        initConfigurationMaps();
    }

    void fillTopLevelConfigMap(TopLevelConfig config) {
        CaseInsensitiveMap<TopLevelConfig> typeMap = configurationStorage.topLevelConfigMap.get(config.getClass());
        if (typeMap == null) {
            typeMap = new CaseInsensitiveMap<>();
            configurationStorage.topLevelConfigMap.put(config.getClass(), typeMap);
        }
        typeMap.put(config.getName(), config);
    }

    void fillGlobalSettingsCache(TopLevelConfig config) {
        if (GLOBAL_SETTINGS_CLASS_NAME.equalsIgnoreCase(config.getClass().getCanonicalName())) {
            configurationStorage.globalSettings = (GlobalSettingsConfig) config;
            configurationStorage.sqlTrace = configurationStorage.globalSettings.getSqlTrace();
        }
    }

    void fillCollectionColumnConfigMap(CollectionViewConfig collectionViewConfig) {
        if (collectionViewConfig.getCollectionDisplayConfig() != null) {
            for (CollectionColumnConfig columnConfig : collectionViewConfig.getCollectionDisplayConfig().
                    getColumnConfig()) {
                FieldConfigKey fieldConfigKey =
                        new FieldConfigKey(collectionViewConfig.getName(), columnConfig.getField());
                configurationStorage.collectionColumnConfigMap.put(fieldConfigKey, columnConfig);
            }
        }
    }

    void updateCollectionColumnConfigMap(CollectionViewConfig oldConfig, CollectionViewConfig newConfig) {
        if (oldConfig != null && oldConfig.getCollectionDisplayConfig() != null) {
            for (CollectionColumnConfig columnConfig : oldConfig.getCollectionDisplayConfig().getColumnConfig()) {
                FieldConfigKey fieldConfigKey = new FieldConfigKey(oldConfig.getName(), columnConfig.getField());
                configurationStorage.collectionColumnConfigMap.remove(fieldConfigKey);
            }
        }

        fillCollectionColumnConfigMap(newConfig);
    }

    void updateToolbarConfigByPluginMap(ToolBarConfig oldConfig, ToolBarConfig newConfig) {
        if (oldConfig != null) {
            configurationStorage.toolbarConfigByPluginMap.remove(oldConfig.getPlugin());
        }

        fillToolbarConfigByPluginMap(newConfig);
    }

    void fillToolbarConfigByPluginMap(ToolBarConfig toolBarConfig) {
        if (configurationStorage.toolbarConfigByPluginMap.get(toolBarConfig.getPlugin()) == null) {
            configurationStorage.toolbarConfigByPluginMap.put(toolBarConfig.getPlugin(), toolBarConfig);
        }
    }

    private void initConfigurationMaps() {
        if (configurationStorage.configuration == null) {
            throw new FatalException("Failed to initialize ConfigurationExplorerImpl because " +
                    "Configuration is null");
        }

        List<DomainObjectTypeConfig> attachmentOwnerDots = new ArrayList<>();
        for (TopLevelConfig config : configurationStorage.configuration.getConfigurationList()) {
            fillGlobalSettingsCache(config);
            fillTopLevelConfigMap(config);

            if (DomainObjectTypeConfig.class.equals(config.getClass())) {
                DomainObjectTypeConfig domainObjectTypeConfig = (DomainObjectTypeConfig) config;
                fillFieldsConfigMap(domainObjectTypeConfig);
                if (domainObjectTypeConfig.getAttachmentTypesConfig() != null) {
                    attachmentOwnerDots.add(domainObjectTypeConfig);
                }
            } else if (CollectionViewConfig.class.equals(config.getClass())) {
                CollectionViewConfig collectionViewConfig = (CollectionViewConfig) config;
                fillCollectionColumnConfigMap(collectionViewConfig);
            } else if (AccessMatrixConfig.class.equals(config.getClass())) {
                AccessMatrixConfig accessMatrixConfig = (AccessMatrixConfig) config;
                fillReadPermittedToEverybodyMap(accessMatrixConfig);
            } else if (ToolBarConfig.class.equals(config.getClass())) {
                ToolBarConfig toolBarConfig = (ToolBarConfig) config;
                fillToolbarConfigByPluginMap(toolBarConfig);
            }
        }

        initDynamicGroupConfigContextMap();

        initConfigurationMapsOfAttachmentDomainObjectTypes(attachmentOwnerDots);
        initConfigurationMapOfChildDomainObjectTypes();
    }

    private void initDynamicGroupConfigContextMap() {
        Collection<DynamicGroupConfig> dynamicGroupConfigs = configurationExplorer.getConfigs(DynamicGroupConfig.class);
        for (DynamicGroupConfig dynamicGroup : dynamicGroupConfigs) {
            if (dynamicGroup.getContext() != null && dynamicGroup.getContext().getDomainObject() != null) {
                String objectType = dynamicGroup.getContext().getDomainObject().getType();

                if (configurationStorage.dynamicGroupConfigByContextMap.get(objectType) != null) {
                    continue;
                }

                initDynamicGroupConfigContextMap(objectType);
            }
        }
    }

    private void initDynamicGroupConfigContextMap(String domainObjectType) {
        List<DynamicGroupConfig> dynamicGroups = new ArrayList<>();

        Collection<DynamicGroupConfig> dynamicGroupConfigs = configurationExplorer.getConfigs(DynamicGroupConfig.class);
        for (DynamicGroupConfig dynamicGroup : dynamicGroupConfigs) {
            if (dynamicGroup.getContext() != null && dynamicGroup.getContext().getDomainObject() != null) {
                String objectType = dynamicGroup.getContext().getDomainObject().getType();

                if (objectType.equals(domainObjectType)) {
                    dynamicGroups.add(dynamicGroup);
                }
            }
        }

        configurationStorage.dynamicGroupConfigByContextMap.put(domainObjectType, dynamicGroups);
    }

    private void initConfigurationMapOfChildDomainObjectTypes() {
        Collection<DomainObjectTypeConfig> allTypes = configurationExplorer.getConfigs(DomainObjectTypeConfig.class);
        for (DomainObjectTypeConfig type : allTypes) {
            ArrayList<DomainObjectTypeConfig> directChildTypes = new ArrayList<>();
            ArrayList<DomainObjectTypeConfig> indirectChildTypes = new ArrayList<>();
            String typeName = type.getName();

            initConfigurationMapOfChildDomainObjectTypes(typeName, directChildTypes, indirectChildTypes, true);
            configurationStorage.directChildDomainObjectTypesMap.put(typeName, directChildTypes);
            configurationStorage.indirectChildDomainObjectTypesMap.put(typeName, indirectChildTypes);
        }
    }

    private void initConfigurationMapOfChildDomainObjectTypes(String typeName, ArrayList<DomainObjectTypeConfig> directChildTypes,
                                                              ArrayList<DomainObjectTypeConfig> indirectChildTypes, boolean fillDirect) {
        Collection<DomainObjectTypeConfig> allTypes = configurationExplorer.getConfigs(DomainObjectTypeConfig.class);
        for (DomainObjectTypeConfig type : allTypes) {
            if (typeName.equals(type.getExtendsAttribute())) {
                if (indirectChildTypes.contains(type)) {
                    throw new ConfigurationException("Loop in the hierarchy, typeName: " + typeName);
                }

                if (fillDirect) {
                    directChildTypes.add(type);
                }
                indirectChildTypes.add(type);
                initConfigurationMapOfChildDomainObjectTypes(type.getName(), directChildTypes, indirectChildTypes, false);
            }
        }
    }

    private void fillSystemFields(DomainObjectTypeConfig domainObjectTypeConfig) {
        for (FieldConfig fieldConfig : domainObjectTypeConfig.getSystemFieldConfigs()) {
            FieldConfigKey fieldConfigKey =
                    new FieldConfigKey(domainObjectTypeConfig.getName(), fieldConfig.getName());
            if (GenericDomainObject.STATUS_DO.equals(domainObjectTypeConfig.getName())
                    && GenericDomainObject.STATUS_FIELD_NAME.equals(fieldConfig.getName())) {
                continue;
            }
            configurationStorage.fieldConfigMap.put(fieldConfigKey, fieldConfig);
        }
    }

    private void fillFieldsConfigMap(DomainObjectTypeConfig domainObjectTypeConfig) {
        for (FieldConfig fieldConfig : domainObjectTypeConfig.getFieldConfigs()) {
            FieldConfigKey fieldConfigKey =
                    new FieldConfigKey(domainObjectTypeConfig.getName(), fieldConfig.getName());
            configurationStorage.fieldConfigMap.put(fieldConfigKey, fieldConfig);
        }
        fillSystemFields(domainObjectTypeConfig);
    }

    private void initConfigurationMapsOfAttachmentDomainObjectTypes(List<DomainObjectTypeConfig> ownerAttachmentDOTs) {
        if (ownerAttachmentDOTs == null || ownerAttachmentDOTs.isEmpty()) {
            return;
        }

        try {
            AttachmentPrototypeHelper factory = new AttachmentPrototypeHelper();
            for (DomainObjectTypeConfig domainObjectTypeConfig : ownerAttachmentDOTs) {
                for (AttachmentTypeConfig attachmentTypeConfig : domainObjectTypeConfig.getAttachmentTypesConfig()
                        .getAttachmentTypeConfigs()) {
                    DomainObjectTypeConfig attachmentDomainObjectTypeConfig =
                            factory.makeAttachmentConfig(attachmentTypeConfig.getName(),
                                    domainObjectTypeConfig.getName());
                    fillTopLevelConfigMap(attachmentDomainObjectTypeConfig);
                    fillFieldsConfigMap(attachmentDomainObjectTypeConfig);
                    configurationStorage.attachmentDomainObjectTypes.put(attachmentDomainObjectTypeConfig.getName(), attachmentDomainObjectTypeConfig.getName());
                }
            }
        } catch (IOException e) {
            throw new ConfigurationException(e);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(e);
        }
    }

    private void fillReadPermittedToEverybodyMap(AccessMatrixConfig accessMatrixConfig) {
        Boolean readEverybody = accessMatrixConfig.isReadEverybody();
        if (readEverybody != null) {
            configurationStorage.readPermittedToEverybodyMap.put(accessMatrixConfig.getType(), readEverybody);
        } else {
            fillReadPermittedToEverybodyMapFromStatus(accessMatrixConfig);
        }
    }

    @Deprecated
    private void fillReadPermittedToEverybodyMapFromStatus(AccessMatrixConfig accessMatrixConfig) {
        for (AccessMatrixStatusConfig accessMatrixStatus : accessMatrixConfig.getStatus()) {
            if (ALL_STATUSES_SIGN.equals(accessMatrixStatus.getName()))
                for (BaseOperationPermitConfig permission : accessMatrixStatus.getPermissions()) {

                    if (ReadConfig.class.equals(permission.getClass())
                            && (Boolean.TRUE.equals(((ReadConfig) permission).isPermitEverybody()))) {
                        configurationStorage.readPermittedToEverybodyMap.put(accessMatrixConfig.getType(), true);
                        return;
                    }
                }
            configurationStorage.readPermittedToEverybodyMap.put(accessMatrixConfig.getType(), false);
        }
    }

    private class PrototypeHelper {
        private ByteArrayInputStream bis;

        private PrototypeHelper(String templateName) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            TopLevelConfig templateDomainObjectTypeConfig = configurationExplorer.getConfig(DomainObjectTypeConfig.class, templateName);
            oos.writeObject(templateDomainObjectTypeConfig);
            oos.close();
            bis = new ByteArrayInputStream(bos.toByteArray());
        }

        public DomainObjectTypeConfig makeDomainObjectTypeConfig(String name)
                throws IOException, ClassNotFoundException {
            bis.reset();
            DomainObjectTypeConfig cloneDomainObjectTypeConfig =
                    (DomainObjectTypeConfig) new ObjectInputStream(bis).readObject();
            cloneDomainObjectTypeConfig.setTemplate(false);
            cloneDomainObjectTypeConfig.setName(name);

            return cloneDomainObjectTypeConfig;
        }
    }

    private class AttachmentPrototypeHelper {
        private PrototypeHelper prototypeHelper;

        private AttachmentPrototypeHelper() throws IOException {
            prototypeHelper = new PrototypeHelper("Attachment");
        }

        public DomainObjectTypeConfig makeAttachmentConfig(String name, String ownerTypeName)
                throws IOException, ClassNotFoundException {
            DomainObjectTypeConfig cloneDomainObjectTypeConfig = prototypeHelper.makeDomainObjectTypeConfig(name);

            ReferenceFieldConfig ownerReferenceConfig = new ReferenceFieldConfig();
            ownerReferenceConfig.setName(ownerTypeName);
            ownerReferenceConfig.setType(ownerTypeName);
            cloneDomainObjectTypeConfig.getFieldConfigs().add(ownerReferenceConfig);

            return cloneDomainObjectTypeConfig;
        }
    }
}
