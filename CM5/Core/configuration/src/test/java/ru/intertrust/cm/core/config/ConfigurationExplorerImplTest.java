package ru.intertrust.cm.core.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import junit.framework.Assert;
import ru.intertrust.cm.core.config.base.CollectionConfig;
import ru.intertrust.cm.core.config.base.Configuration;
import ru.intertrust.cm.core.config.converter.ConfigurationClassesCache;
import ru.intertrust.cm.core.config.module.ModuleConfiguration;
import ru.intertrust.cm.core.config.module.ModuleService;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static ru.intertrust.cm.core.config.Constants.COLLECTIONS_CONFIG_PATH;
import static ru.intertrust.cm.core.config.Constants.CONFIGURATION_SCHEMA_PATH;
import static ru.intertrust.cm.core.config.Constants.DOMAIN_OBJECTS_CONFIG_PATH;
import static ru.intertrust.cm.core.config.Constants.MODULES_CUSTOM_CONFIG;
import static ru.intertrust.cm.core.config.Constants.MODULES_CUSTOM_SCHEMA;
import static ru.intertrust.cm.core.config.Constants.MODULES_DOMAIN_OBJECTS;

/**
 * @author vmatsukevich
 *         Date: 6/24/13
 *         Time: 3:43 PM
 */
public class ConfigurationExplorerImplTest {

    private static final String PERSON_CONFIG_NAME = "Person";
    private static final String EMPLOYEES_CONFIG_NAME = "Employees";
    private static final String E_MAIL_CONFIG_NAME = "EMail";
    private static final String GLOBAL_XML_PATH = "config/global-test.xml";

    private Configuration config;

    private ConfigurationExplorerImpl configExplorer;

    @Before
    public void setUp() throws Exception {
        ConfigurationSerializer configurationSerializer = createConfigurationSerializer(DOMAIN_OBJECTS_CONFIG_PATH);

        config = configurationSerializer.deserializeConfiguration();
        configExplorer = new ConfigurationExplorerImpl(config);
    }

    @Test
    public void testGetConfiguration() throws Exception {
        Configuration testConfiguration = configExplorer.getConfiguration();
        assertTrue(config != testConfiguration);
        Assert.assertEquals(config, testConfiguration);
    }

    @Test
    public void testInit() throws Exception {
        DomainObjectTypeConfig domainObjectTypeConfig =
                configExplorer.getConfig(DomainObjectTypeConfig.class, PERSON_CONFIG_NAME);
        assertNotNull(domainObjectTypeConfig);

        FieldConfig fieldConfig = configExplorer.getFieldConfig(PERSON_CONFIG_NAME, E_MAIL_CONFIG_NAME);
        assertNotNull(fieldConfig);

        CollectionConfig collectionConfig =
                configExplorer.getConfig(CollectionConfig.class, EMPLOYEES_CONFIG_NAME);
        assertNotNull(collectionConfig);
    }

    @Test
    public void testGetDomainObjectConfigs() throws Exception {
        Collection<DomainObjectTypeConfig> domainObjectTypeConfigs =
                configExplorer.getConfigs(DomainObjectTypeConfig.class);

        assertNotNull(domainObjectTypeConfigs);
        assertEquals(13, domainObjectTypeConfigs.size());

        List<String> domainObjectNames = new ArrayList<>();
        domainObjectNames.addAll(Arrays.asList("Outgoing_Document", PERSON_CONFIG_NAME, "Employee", "Department",
                "Assignment", "Incoming_Document", "Incoming_Document2", "Attachment", "Person_Attachment",
                "Authentication_Info", "User_Group", "Group_Member", "Group_Admin", "Delegation", "Negotiation_Card", "Organization",
                "Internal_Document", "Delegation", "Status"));

        for (DomainObjectTypeConfig domainObjectTypeConfig : domainObjectTypeConfigs) {
            String name = domainObjectTypeConfig.getName();
            assertTrue(domainObjectNames.contains(name));
            domainObjectNames.remove(name);
        }
    }

    @Test
    public void testGetCollectionConfigs() throws Exception {
        Collection<CollectionConfig> collectionConfigs = configExplorer.getConfigs(CollectionConfig.class);

        assertNotNull(collectionConfigs);
        assertEquals(collectionConfigs.size(), 2);

        List<String> collectionNames = new ArrayList<>();
        collectionNames.addAll(Arrays.asList(EMPLOYEES_CONFIG_NAME, "Employees_2"));

        for(CollectionConfig collectionConfig : collectionConfigs) {
            String name = collectionConfig.getName();
            assertTrue(collectionNames.contains(name));
            collectionNames.remove(name);
        }
    }

    @Test
    public void testGetDomainObjectConfig() throws Exception {
        DomainObjectTypeConfig domainObjectTypeConfig =
                configExplorer.getConfig(DomainObjectTypeConfig.class, PERSON_CONFIG_NAME);
        assertNotNull(domainObjectTypeConfig);
        assertEquals(domainObjectTypeConfig.getName(), PERSON_CONFIG_NAME);
    }

    @Test
    public void testGetCollectionConfig() throws Exception {
        CollectionConfig collectionConfig =
                configExplorer.getConfig(CollectionConfig.class, EMPLOYEES_CONFIG_NAME);
        assertNotNull(collectionConfig);
        assertEquals(collectionConfig.getName(), EMPLOYEES_CONFIG_NAME);
    }

    @Test
    public void testGetFieldConfig() throws Exception {
        FieldConfig fieldConfig = configExplorer.getFieldConfig(PERSON_CONFIG_NAME, E_MAIL_CONFIG_NAME);
        assertNotNull(fieldConfig);
        assertEquals(fieldConfig.getName(), E_MAIL_CONFIG_NAME);
    }

    @Test
    public void testGetSystemFieldConfig() throws Exception {
        FieldConfig fieldConfig = configExplorer.getFieldConfig(PERSON_CONFIG_NAME, SystemField.id.name());
        assertNotNull(fieldConfig);
        Assert.assertEquals(fieldConfig.getName(), SystemField.id.name());

        fieldConfig = configExplorer.getFieldConfig(PERSON_CONFIG_NAME, SystemField.created_date.name());
        assertNotNull(fieldConfig);
        Assert.assertEquals(fieldConfig.getName(), SystemField.created_date.name());

        fieldConfig = configExplorer.getFieldConfig(PERSON_CONFIG_NAME, SystemField.updated_date.name());
        assertNotNull(fieldConfig);
        Assert.assertEquals(fieldConfig.getName(), SystemField.updated_date.name());
    }

    @Test
    public void testFindChildDomainObjectTypes() {
        Collection<DomainObjectTypeConfig> types = configExplorer.findChildDomainObjectTypes("Person", true);
        assertTrue(types.contains(configExplorer.getConfig(DomainObjectTypeConfig.class, "Employee")));
        assertTrue(types.size() == 1);
    }


    private ConfigurationSerializer createConfigurationSerializer(String configPath) throws Exception {
        ConfigurationClassesCache.getInstance().build(); // Инициализируем кэш конфигурации тэг-класс

        ConfigurationSerializer configurationSerializer = new ConfigurationSerializer();
        configurationSerializer.setModuleService(createModuleService(configPath));

        return configurationSerializer;
    }

    private ModuleService createModuleService(String configPath) throws MalformedURLException {
        URL moduleUrl = getClass().getClassLoader().getResource(".");

        ModuleService result = new ModuleService();
        ModuleConfiguration confCore = new ModuleConfiguration();
        confCore.setName("core");
        result.getModuleList().add(confCore);
        confCore.setConfigurationPaths(new ArrayList<String>());
        confCore.getConfigurationPaths().add(configPath);
        confCore.getConfigurationPaths().add(COLLECTIONS_CONFIG_PATH);
        confCore.getConfigurationPaths().add(GLOBAL_XML_PATH);
        confCore.setConfigurationSchemaPath(CONFIGURATION_SCHEMA_PATH);
        confCore.setModuleUrl(moduleUrl);

        ModuleConfiguration confCustom = new ModuleConfiguration();
        confCustom.setName("custom");
        result.getModuleList().add(confCustom);
        confCustom.setConfigurationPaths(new ArrayList<String>());
        confCustom.getConfigurationPaths().add(MODULES_CUSTOM_CONFIG);
        confCustom.getConfigurationPaths().add(MODULES_DOMAIN_OBJECTS);
        confCustom.setConfigurationSchemaPath(MODULES_CUSTOM_SCHEMA);
        confCustom.setDepends(new ArrayList<String>());
        confCustom.getDepends().add(confCore.getName());
        confCustom.setModuleUrl(moduleUrl);

        return result;
    }
}
