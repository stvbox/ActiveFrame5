package ru.intertrust.cm.core.dao.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.config.*;
import ru.intertrust.cm.core.config.base.Configuration;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.access.UserGroupGlobalCache;
import ru.intertrust.cm.core.dao.access.UserSubject;
import ru.intertrust.cm.core.dao.api.CurrentUserAccessor;
import ru.intertrust.cm.core.dao.api.SecurityStamp;


import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит тест для DomainObjectQueryHelper
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainObjectQueryHelperTest {

    private static final String ACCESS_RIGHTS_PART = " and exists (select 1 from \"person_read\" r " +
            "where r.\"group_id\" in (select \"parent_group_id\" from cur_user_groups) and " +
            "r.\"object_id\" = person.\"access_object_id\")";

    private static final String WITH_PART = "with cur_user_groups as (select distinct gg.\"parent_group_id\" " +
            "from \"group_member\" gm inner join \"group_group\" gg on gg.\"child_group_id\" = gm.\"usergroup\" " +
            "where gm.\"person_id\" = :user_id) ";

    private final DomainObjectQueryHelper domainObjectQueryHelper = new DomainObjectQueryHelper();

    private ConfigurationExplorerImpl configurationExplorer;

    private DomainObjectTypeConfig domainObjectTypeConfig;

    @Mock
    private CurrentUserAccessor currentUserAccessor;

    @Mock
    private UserGroupGlobalCache userGroupCache;

    @Mock
    protected SecurityStamp securityStamp;

    @Before
    public void setUp() throws Exception {
        initConfigs();
    }

    @Test
    public void testGenerateFindQuery() {
        AccessToken accessToken = createMockAccessToken();
        String expectedQuery = WITH_PART + "select person.\"id\", person.\"id_type\", person.\"created_date\", person.\"updated_date\", person.\"created_by\", person.\"created_by_type\", person.\"updated_by\", person.\"updated_by_type\", person.\"status\", person.\"status_type\", person.\"security_stamp\", person.\"security_stamp_type\", person.\"access_object_id\", person.\"email\", person.\"login\", person.\"password\", person.\"boss\", person.\"boss_type\" from \"person\" person where person.\"id\"=:id" + ACCESS_RIGHTS_PART;
        Assert.assertEquals(expectedQuery, domainObjectQueryHelper.generateFindQuery("Person", accessToken, false));
    }

    @Test
    public void testGenerateFindQueryWithLock() {
        AccessToken accessToken = createMockAccessToken();
        String expectedQuery = WITH_PART + "select person.\"id\", person.\"id_type\", person.\"created_date\", person.\"updated_date\", person.\"created_by\", person.\"created_by_type\", person.\"updated_by\", person.\"updated_by_type\", person.\"status\", person.\"status_type\", person.\"security_stamp\", person.\"security_stamp_type\", person.\"access_object_id\", person.\"email\", person.\"login\", person.\"password\", person.\"boss\", person.\"boss_type\" from \"person\" person where person.\"id\"=:id"
                + ACCESS_RIGHTS_PART + " for update";
        Assert.assertEquals(expectedQuery, domainObjectQueryHelper.generateFindQuery("Person", accessToken, true));
    }

    @Test
    public void testGenerateFindByUniqueKeyQuery() {
        AccessToken accessToken = createMockAccessToken();
        String expectedQuery = WITH_PART + "select person.\"id\", person.\"id_type\", person.\"created_date\", person.\"updated_date\", person.\"created_by\", person.\"created_by_type\", person.\"updated_by\", person.\"updated_by_type\", person.\"status\", person.\"status_type\", person.\"security_stamp\", person.\"security_stamp_type\", person.\"access_object_id\", person.\"email\", person.\"login\", person.\"password\", person.\"boss\", person.\"boss_type\" from \"person\" person where person.\"email\" = :email and person.\"login\" = :login" +
                ACCESS_RIGHTS_PART;
        Assert.assertEquals(expectedQuery, domainObjectQueryHelper.generateFindQuery("Person",
                domainObjectTypeConfig.getUniqueKeyConfigs().get(0), accessToken, false));
    }

    @Test
    public void testGenerateFindByUniqueKeyQueryWithLock() {
        AccessToken accessToken = createMockAccessToken();
        String expectedQuery = WITH_PART + "select person.\"id\", person.\"id_type\", person.\"created_date\", person.\"updated_date\", person.\"created_by\", person.\"created_by_type\", person.\"updated_by\", person.\"updated_by_type\", person.\"status\", person.\"status_type\", person.\"security_stamp\", person.\"security_stamp_type\", person.\"access_object_id\", person.\"email\", person.\"login\", person.\"password\", person.\"boss\", person.\"boss_type\" from \"person\" person where person.\"email\" = :email and person.\"login\" = :login" +
                ACCESS_RIGHTS_PART + " for update";
        Assert.assertEquals(expectedQuery, domainObjectQueryHelper.generateFindQuery("Person",
                domainObjectTypeConfig.getUniqueKeyConfigs().get(0), accessToken, true));
    }

    private void initConfigs() {

        /*
         * Создаем конфигурацию следующего ввида <domain-object name="Person">
         * <fields> <string name="EMail" length="128" /> <string name="Login"
         * length="64" not-null="true" /> <password name="Password" length="128"
         * /> </fields> <uniqueKey> <!-- This key means automatic key + index
         * creation--> <field name="EMail"/> </uniqueKey> </domain-object>
         */
        GlobalSettingsConfig globalSettings = new GlobalSettingsConfig();
        domainObjectTypeConfig = new DomainObjectTypeConfig();
        domainObjectTypeConfig.setName("Person");
        StringFieldConfig email = new StringFieldConfig();
        email.setName("EMail");
        email.setLength(128);
        domainObjectTypeConfig.getFieldConfigs().add(email);

        StringFieldConfig login = new StringFieldConfig();
        login.setName("Login");
        login.setLength(64);
        login.setNotNull(true);
        domainObjectTypeConfig.getFieldConfigs().add(login);

        StringFieldConfig password = new StringFieldConfig();
        password.setName("Password");
        password.setLength(128);
        domainObjectTypeConfig.getFieldConfigs().add(password);

        ReferenceFieldConfig boss = new ReferenceFieldConfig();
        boss.setName("Boss");
        boss.setType("Internal_Employee");
        domainObjectTypeConfig.getFieldConfigs().add(boss);


        UniqueKeyConfig uniqueKeyConfig = new UniqueKeyConfig();
        domainObjectTypeConfig.getUniqueKeyConfigs().add(uniqueKeyConfig);

        UniqueKeyFieldConfig uniqueKeyFieldConfig1 = new UniqueKeyFieldConfig();
        uniqueKeyFieldConfig1.setName("EMail");
        uniqueKeyConfig.getUniqueKeyFieldConfigs().add(uniqueKeyFieldConfig1);

        UniqueKeyFieldConfig uniqueKeyFieldConfig2 = new UniqueKeyFieldConfig();
        uniqueKeyFieldConfig2.setName("Login");
        uniqueKeyConfig.getUniqueKeyFieldConfigs().add(uniqueKeyFieldConfig2);

        DomainObjectTypeConfig internalEmployee = new DomainObjectTypeConfig();
        internalEmployee.setName("Internal_Employee");
        internalEmployee.setExtendsAttribute("Person");

        DomainObjectTypeConfig externalEmployee = new DomainObjectTypeConfig();
        externalEmployee.setName("External_Employee");
        internalEmployee.setExtendsAttribute("Person");

        DomainObjectTypeConfig assignment = new DomainObjectTypeConfig();
        assignment.setName("assignment");

        ReferenceFieldConfig author = new ReferenceFieldConfig();
        author.setName("author");
        author.setType("Person");

        assignment.getFieldConfigs().add(author);

        Configuration configuration = new Configuration();
        configuration.getConfigurationList().add(domainObjectTypeConfig);
        configuration.getConfigurationList().add(internalEmployee);
        configuration.getConfigurationList().add(externalEmployee);
        configuration.getConfigurationList().add(assignment);
        configuration.getConfigurationList().add(globalSettings);

        configurationExplorer = new ConfigurationExplorerImpl(configuration);
        configurationExplorer.init();
        domainObjectQueryHelper.setConfigurationExplorer(configurationExplorer);
        when(userGroupCache.isAdministrator(any(Id.class))).thenReturn(false);
        domainObjectQueryHelper.setCurrentUserAccessor(currentUserAccessor);
        domainObjectQueryHelper.setUserGroupCache(userGroupCache);
        ReflectionTestUtils.setField(domainObjectQueryHelper, "securityStamp", securityStamp);
    }

    private AccessToken createMockAccessToken() {
        AccessToken accessToken = mock(AccessToken.class);
        when(accessToken.isDeferred()).thenReturn(true);

        UserSubject subject = mock(UserSubject.class);
        when(subject.getUserId()).thenReturn(1);
        when(accessToken.getSubject()).thenReturn(subject);
        return accessToken;
    }

}
