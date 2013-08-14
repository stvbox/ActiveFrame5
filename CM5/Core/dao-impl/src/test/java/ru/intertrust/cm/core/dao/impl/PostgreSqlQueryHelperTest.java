package ru.intertrust.cm.core.dao.impl;

import org.junit.Before;
import org.junit.Test;
import ru.intertrust.cm.core.config.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static ru.intertrust.cm.core.dao.api.ConfigurationDao.CONFIGURATION_TABLE;
import static ru.intertrust.cm.core.dao.api.DataStructureDao.AUTHENTICATION_INFO_TABLE;
import static ru.intertrust.cm.core.dao.api.DomainObjectTypeIdDao.DOMAIN_OBJECT_TYPE_ID_TABLE;
import static ru.intertrust.cm.core.dao.api.DomainObjectDao.PARENT_COLUMN;
import static ru.intertrust.cm.core.dao.api.DomainObjectDao.MASTER_COLUMN;
import static ru.intertrust.cm.core.dao.api.DomainObjectDao.TYPE_COLUMN;

/**
 * @author vmatsukevich
 *         Date: 5/29/13
 *         Time: 12:39 PM
 */
public class PostgreSqlQueryHelperTest {

    private DomainObjectTypeConfig domainObjectTypeConfig;

    @Before
    public void setUp() throws Exception {
        initDomainObjectConfig();
    }

    @Test
    public void testGenerateCountTablesQuery() {
        String query = "select count(table_name) FROM information_schema.tables WHERE table_schema = 'public'";
        String testQuery = PostgreSqlQueryHelper.generateCountTablesQuery();
        assertEquals(testQuery, query);
    }

    @Test
    public void testGenerateCreateDomainObjectTableQuery() {
        String query = "create table " + DOMAIN_OBJECT_TYPE_ID_TABLE + "(ID bigserial not null, NAME varchar(256) not null, " +
                "constraint PK_" + DOMAIN_OBJECT_TYPE_ID_TABLE + " primary key (ID), constraint U_" + DOMAIN_OBJECT_TYPE_ID_TABLE + " unique (NAME))";
        String testQuery = PostgreSqlQueryHelper.generateCreateDomainObjectTableQuery();
        assertEquals(testQuery, query);
    }

    @Test
    public void testGenerateCreateConfigurationTableQuery() {
        String query = "create table " + CONFIGURATION_TABLE + "(ID bigserial not null, CONTENT text not null, " +
                "LOADED_DATE timestamp not null, constraint PK_" + CONFIGURATION_TABLE + " primary key (ID))";
        String testQuery = PostgreSqlQueryHelper.generateCreateConfigurationTableQuery();
        assertEquals(testQuery, query);
    }

    @Test
    public void testGenerateCreateAuthenticationInfoTableQuery() {
        String query = "CREATE TABLE " + AUTHENTICATION_INFO_TABLE + " (ID bigint not null, " +
                "user_uid character varying(64) NOT NULL, password character varying(128), constraint PK_" +
                AUTHENTICATION_INFO_TABLE + "_ID primary key (ID), constraint U_" + AUTHENTICATION_INFO_TABLE +
                "_USER_UID unique(user_uid))";
        String testQuery = PostgreSqlQueryHelper.generateCreateAuthenticationInfoTableQuery();
        assertEquals(testQuery, query);
    }

    @Test
    public void testGenerateSequenceQuery() {
        String query = "create sequence OUTGOING_DOCUMENT_SEQ";
        String testQuery = PostgreSqlQueryHelper.generateSequenceQuery(domainObjectTypeConfig);
        assertEquals(testQuery, query);
    }

    @Test
     public void testGenerateCreateTableQuery() throws Exception {
        String query = PostgreSqlQueryHelper.generateCreateTableQuery(domainObjectTypeConfig);
        String checkQuery = "create table OUTGOING_DOCUMENT ( ID bigint not null, " +
                PARENT_COLUMN + " bigint not null, MASTER bigint, REGISTRATION_NUMBER varchar(128), " +
                "REGISTRATION_DATE timestamp, AUTHOR bigint, " +
                "LONG_FIELD bigint, DECIMAL_FIELD_1 decimal(10, 2), DECIMAL_FIELD_2 decimal(10), " +
                "constraint PK_OUTGOING_DOCUMENT_ID primary key (ID), " +
                "constraint U_OUTGOING_DOCUMENT_REGISTRATION_NUMBER_REGISTRATION_DATE unique (REGISTRATION_NUMBER, REGISTRATION_DATE), " +
                "constraint FK_OUTGOING_DOCUMENT_" + MASTER_COLUMN + " foreign key (" + MASTER_COLUMN +
                    ") references PARENT_DOCUMENT(ID), " +
                "constraint FK_OUTGOING_DOCUMENT_AUTHOR foreign key (AUTHOR) references EMPLOYEE(ID)" +
                ", constraint FK_OUTGOING_DOCUMENT_" + PARENT_COLUMN + " foreign key (" + PARENT_COLUMN +
                    ") references DOCUMENT(ID))";
        assertEquals(checkQuery, query);
    }

    @Test
    public void testGenerateCreateTableQueryWithoutExtendsAttribute() throws Exception {
        domainObjectTypeConfig.setExtendsAttribute(null);
        String query = PostgreSqlQueryHelper.generateCreateTableQuery(domainObjectTypeConfig);
        String checkQuery = "create table OUTGOING_DOCUMENT ( ID bigint not null, " +
                "CREATED_DATE timestamp not null, " + "UPDATED_DATE timestamp not null, " + TYPE_COLUMN +" integer, " +
                MASTER_COLUMN + " bigint, REGISTRATION_NUMBER varchar(128), " +
                "REGISTRATION_DATE timestamp, AUTHOR bigint, " +
                "LONG_FIELD bigint, DECIMAL_FIELD_1 decimal(10, 2), DECIMAL_FIELD_2 decimal(10), " +
                "constraint PK_OUTGOING_DOCUMENT_ID primary key (ID), " +
                "constraint U_OUTGOING_DOCUMENT_REGISTRATION_NUMBER_REGISTRATION_DATE unique (REGISTRATION_NUMBER, REGISTRATION_DATE), " +
                "constraint FK_OUTGOING_DOCUMENT_" + MASTER_COLUMN + " foreign key (" + MASTER_COLUMN +
                    ") references PARENT_DOCUMENT(ID), " +
                "constraint FK_OUTGOING_DOCUMENT_AUTHOR foreign key (AUTHOR) references EMPLOYEE(ID), " +
                "constraint FK_OUTGOING_DOCUMENT_" + TYPE_COLUMN + " foreign key (" + TYPE_COLUMN + ") references " +
                    "DOMAIN_OBJECT_TYPE_ID(ID))";
        assertEquals(checkQuery, query);
    }

    @Test
    public void testGenerateCreateAclTableQuery() throws Exception {
        String query = PostgreSqlQueryHelper.generateCreateAclTableQuery(domainObjectTypeConfig);

        String checkQuery = "create table OUTGOING_DOCUMENT_ACL (object_id bigint not null, " +
                "group_id bigint not null, operation varchar(256) not null, " +
                "constraint PK_OUTGOING_DOCUMENT_ACL primary key (object_id, group_id, operation), " +
                "CONSTRAINT FK_OUTGOING_DOCUMENT_ACL_OUTGOING_DOCUMENT FOREIGN KEY (object_id) " +
                "REFERENCES OUTGOING_DOCUMENT (id), " +
                "CONSTRAINT FK_OUTGOING_DOCUMENT_USER_GROUP FOREIGN KEY (group_id) REFERENCES User_Group (id))";
        assertEquals(checkQuery, query);
    }

    @Test
    public void testGenerateCreateAclReadTableQuery() throws Exception {
        String query = PostgreSqlQueryHelper.generateCreateAclReadTableQuery(domainObjectTypeConfig);

        String checkQuery = "create table OUTGOING_DOCUMENT_READ (object_id bigint not null, " +
                "group_id bigint not null, constraint PK_OUTGOING_DOCUMENT_READ primary key (object_id, group_id), " +
                "CONSTRAINT FK_OUTGOING_DOCUMENT_READ_OUTGOING_DOCUMENT FOREIGN KEY (object_id) " +
                "REFERENCES OUTGOING_DOCUMENT (id), " +
                "CONSTRAINT FK_OUTGOING_DOCUMENT_USER_GROUP FOREIGN KEY (group_id) REFERENCES User_Group (id))";
        assertEquals(checkQuery, query);
    }

    @Test
    public void testGenerateUpdateTableQuery() {
        String expectedQuery = "alter table OUTGOING_DOCUMENT " +
                "add column " + MASTER_COLUMN + " bigint, " +
                "add column DESCRIPTION varchar(256), " +
                "add column EXECUTOR bigint not null, " +
                "add constraint FK_OUTGOING_DOCUMENT_" + MASTER_COLUMN + " foreign key (" + MASTER_COLUMN + ") " +
                "references PARENT_DOCUMENT(ID), " +
                "add constraint FK_OUTGOING_DOCUMENT_EXECUTOR foreign key (EXECUTOR) references EMPLOYEE(ID), " +
                "add constraint U_OUTGOING_DOCUMENT_REGISTRATION_NUMBER unique (REGISTRATION_NUMBER)";

        List<FieldConfig> newColumns = new ArrayList<>();

        StringFieldConfig descriptionFieldConfig = new StringFieldConfig();
        descriptionFieldConfig.setName("Description");
        descriptionFieldConfig.setLength(256);
        descriptionFieldConfig.setNotNull(false);
        newColumns.add(descriptionFieldConfig);

        ReferenceFieldConfig executorFieldConfig = new ReferenceFieldConfig();
        executorFieldConfig.setName("Executor");
        executorFieldConfig.setType("Employee");
        executorFieldConfig.setNotNull(true);
        newColumns.add(executorFieldConfig);

        UniqueKeyConfig uniqueKeyConfig = new UniqueKeyConfig();
        UniqueKeyFieldConfig uniqueKeyFieldConfig = new UniqueKeyFieldConfig();
        uniqueKeyFieldConfig.setName("Registration_Number");
        uniqueKeyConfig.getUniqueKeyFieldConfigs().add(uniqueKeyFieldConfig);
        List<UniqueKeyConfig> newUniqueConfigs = Collections.singletonList(uniqueKeyConfig);

        DomainObjectParentConfig parentconfig = new DomainObjectParentConfig();
        parentconfig.setName("PARENT_DOCUMENT");


        String testQuery = PostgreSqlQueryHelper.generateUpdateTableQuery(domainObjectTypeConfig.getName(), newColumns,
                newUniqueConfigs, parentconfig);

        assertEquals(expectedQuery, testQuery);
    }

    @Test
    public void testGenerateCreateIndexesQuery() throws Exception {
        String query = PostgreSqlQueryHelper.generateCreateIndexesQuery(domainObjectTypeConfig.getName(),
                domainObjectTypeConfig.getFieldConfigs(), domainObjectTypeConfig.getParentConfig());
        String checkQuery = "create index I_OUTGOING_DOCUMENT_" + MASTER_COLUMN + " on OUTGOING_DOCUMENT (" +
                MASTER_COLUMN + ");\n" +
                "create index I_OUTGOING_DOCUMENT_AUTHOR on OUTGOING_DOCUMENT (AUTHOR);\n";
        assertEquals(query, checkQuery);
    }



    private void initDomainObjectConfig() {
        domainObjectTypeConfig = new DomainObjectTypeConfig();
        domainObjectTypeConfig.setName("Outgoing_Document");
        domainObjectTypeConfig.setExtendsAttribute("Document");

        StringFieldConfig registrationNumber = new StringFieldConfig();
        registrationNumber.setName("Registration_Number");
        registrationNumber.setLength(128);
        domainObjectTypeConfig.getFieldConfigs().add(registrationNumber);

        DateTimeFieldConfig registrationDate = new DateTimeFieldConfig();
        registrationDate.setName("Registration_Date");
        domainObjectTypeConfig.getFieldConfigs().add(registrationDate);

        ReferenceFieldConfig referenceFieldConfig = new ReferenceFieldConfig();
        referenceFieldConfig.setName("Author");
        referenceFieldConfig.setType("Employee");
        domainObjectTypeConfig.getFieldConfigs().add(referenceFieldConfig);

        LongFieldConfig longFieldConfig = new LongFieldConfig();
        longFieldConfig.setName("Long_Field");
        domainObjectTypeConfig.getFieldConfigs().add(longFieldConfig);

        DecimalFieldConfig decimalFieldConfig1 = new DecimalFieldConfig();
        decimalFieldConfig1.setName("Decimal_Field_1");
        decimalFieldConfig1.setNotNull(false);
        decimalFieldConfig1.setPrecision(10);
        decimalFieldConfig1.setScale(2);
        domainObjectTypeConfig.getFieldConfigs().add(decimalFieldConfig1);

        DecimalFieldConfig decimalFieldConfig2 = new DecimalFieldConfig();
        decimalFieldConfig2.setName("Decimal_Field_2");
        decimalFieldConfig2.setNotNull(false);
        decimalFieldConfig2.setPrecision(10);
        domainObjectTypeConfig.getFieldConfigs().add(decimalFieldConfig2);

        UniqueKeyConfig uniqueKeyConfig = new UniqueKeyConfig();
        domainObjectTypeConfig.getUniqueKeyConfigs().add(uniqueKeyConfig);

        UniqueKeyFieldConfig uniqueKeyFieldConfig1 = new UniqueKeyFieldConfig();
        uniqueKeyFieldConfig1.setName("Registration_Number");
        uniqueKeyConfig.getUniqueKeyFieldConfigs().add(uniqueKeyFieldConfig1);

        UniqueKeyFieldConfig uniqueKeyFieldConfig2 = new UniqueKeyFieldConfig();
        uniqueKeyFieldConfig2.setName("Registration_Date");
        uniqueKeyConfig.getUniqueKeyFieldConfigs().add(uniqueKeyFieldConfig2);

        DomainObjectParentConfig parentConfig = new DomainObjectParentConfig();
        parentConfig.setName("Parent_Document");
        domainObjectTypeConfig.setParentConfig(parentConfig);
    }
}
