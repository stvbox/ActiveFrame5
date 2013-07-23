package ru.intertrust.cm.core.config;

import org.junit.Before;
import org.junit.Test;
import ru.intertrust.cm.core.config.model.CollectionConfig;
import ru.intertrust.cm.core.config.model.Configuration;
import ru.intertrust.cm.core.config.model.DomainObjectTypeConfig;
import ru.intertrust.cm.core.config.model.FieldConfig;

import java.util.*;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static ru.intertrust.cm.core.config.Constants.*;

/**
 * @author vmatsukevich
 *         Date: 6/24/13
 *         Time: 3:43 PM
 */
public class ConfigurationExplorerImplTest {

    private static final String PERSON_CONFIG_NAME = "Person";
    private static final String EMPLOYEES_CONFIG_NAME = "Employees";
    private static final String E_MAIL_CONFIG_NAME = "EMail";

    private Configuration config;
    private ConfigurationExplorerImpl configExplorer;

    @Before
    public void setUp() throws Exception {
        ConfigurationSerializer configurationSerializer =
                ConfigurationSerializerTest.createConfigurationSerializer(DOMAIN_OBJECTS_CONFIG_PATH);

        config = configurationSerializer.serializeConfiguration();
        configExplorer = new ConfigurationExplorerImpl(config);
        configExplorer.build();
    }

    @Test
    public void testGetConfiguration() throws Exception {
        Configuration testConfiguration = configExplorer.getConfiguration();
        assertTrue(config == testConfiguration);
    }

    @Test
    public void testSetConfiguration() throws Exception {
        configExplorer.setConfiguration(config);
        Configuration testConfiguration = configExplorer.getConfiguration();
        assertTrue(config == testConfiguration);
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
        assertEquals(6, domainObjectTypeConfigs.size());

        List<String> domainObjectNames = new ArrayList<>();
        domainObjectNames.addAll(Arrays.asList("Outgoing_Document", PERSON_CONFIG_NAME, "Employee", "Department",
                "Incoming_Document", "Incoming_Document2"));

        for(DomainObjectTypeConfig domainObjectTypeConfig : domainObjectTypeConfigs) {
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
}
