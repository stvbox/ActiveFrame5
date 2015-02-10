package ru.intertrust.cm.core.dao.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import ru.intertrust.cm.core.business.api.dto.ColumnInfo;
import ru.intertrust.cm.core.business.api.dto.ForeignKeyInfo;
import ru.intertrust.cm.core.business.api.dto.UniqueKeyInfo;
import ru.intertrust.cm.core.config.*;
import ru.intertrust.cm.core.dao.api.DataStructureDao;
import ru.intertrust.cm.core.dao.api.DomainObjectDao;
import ru.intertrust.cm.core.dao.api.DomainObjectTypeIdDao;
import ru.intertrust.cm.core.dao.api.MD5Service;
import ru.intertrust.cm.core.dao.impl.utils.ForeignKeysRowMapper;
import ru.intertrust.cm.core.dao.impl.utils.SchemaTablesRowMapper;
import ru.intertrust.cm.core.dao.impl.utils.UniqueKeysRowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getName;
import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getSqlName;

/**
 * Реализация {@link ru.intertrust.cm.core.dao.api.DataStructureDao} для PostgreSQL
 * @author vmatsukevich Date: 5/15/13 Time: 4:27 PM
 */
public abstract class BasicDataStructureDaoImpl implements DataStructureDao {
    @Autowired
    private DomainObjectTypeIdDao domainObjectTypeIdDao;

    @Autowired
    private JdbcOperations jdbcTemplate;

    @Autowired
    private MD5Service md5Service;

    
    private BasicQueryHelper queryHelper;

    protected abstract BasicQueryHelper createQueryHelper(DomainObjectTypeIdDao domainObjectTypeIdDao, MD5Service md5Service);

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#createSequence(ru.intertrust.cm.core.config.DomainObjectTypeConfig)}
     */
    @Override
    public void createSequence(DomainObjectTypeConfig config) {
        if (config.getExtendsAttribute() != null) {
            return; // Для таблиц дочерхних доменных объектов индекс не создается - используется индекс родителя
        }

        String createSequenceQuery = getQueryHelper().generateSequenceQuery(config);
        
        jdbcTemplate.update(createSequenceQuery);
    }

    @Override
    public void createAuditSequence(DomainObjectTypeConfig config) {
        if (config.getExtendsAttribute() != null) {
            return; // Для таблиц дочерхних доменных объектов индекс не создается - используется индекс родителя
        }

        String createAuditSequenceQuery = getQueryHelper().generateAuditSequenceQuery(config);
        jdbcTemplate.update(createAuditSequenceQuery);
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#createTable(ru.intertrust.cm.core.config.DomainObjectTypeConfig)} Dot шаблон (с
     * isTemplate = true) не отображается в базе данных
     */
    @Override
    public void createTable(DomainObjectTypeConfig config, boolean isParentType) {
        if (config.isTemplate()) {
            return;
        }

        Integer id = domainObjectTypeIdDao.insert(config);
        config.setId(id);       
        
        jdbcTemplate.update(getQueryHelper().generateCreateTableQuery(config, isParentType));

        createAutoIndices(config, isParentType);
        createExplicitIndexes(config, config.getIndicesConfig().getIndices());
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#createAclTables(ru.intertrust.cm.core.config.DomainObjectTypeConfig)} Dot шаблон (с isTemplate = true) не отображается в
     * базе данных
     */
    public void createAclTables(DomainObjectTypeConfig config) {
        if (!config.isTemplate()) {
            jdbcTemplate.update(getQueryHelper().generateCreateAclTableQuery(config));
            jdbcTemplate.update(getQueryHelper().generateCreateAclReadTableQuery(config));
            createAclIndexes(config);    
        }
        
    }

    /**
     * Создание индексов для _ACL, _READ таблиц по полям object_id, group_id
     * @param config
     * @return
     */
    private void createAclIndexes(DomainObjectTypeConfig config) {
        String aclTableName = getSqlName(config) + BasicQueryHelper.ACL_TABLE_SUFFIX;
        String readTableName = getSqlName(config) + BasicQueryHelper.READ_TABLE_SUFFIX;

        jdbcTemplate.update(getQueryHelper().generateCreateAclIndexQuery(config, aclTableName, BasicQueryHelper.OBJECT_ID_FIELD, 1));
        jdbcTemplate.update(getQueryHelper().generateCreateAclIndexQuery(config, aclTableName, BasicQueryHelper.GROUP_ID_FIELD, 2));
        jdbcTemplate.update(getQueryHelper().generateCreateAclIndexQuery(config, readTableName, BasicQueryHelper.OBJECT_ID_FIELD, 1));
        jdbcTemplate.update(getQueryHelper().generateCreateAclIndexQuery(config, readTableName, BasicQueryHelper.GROUP_ID_FIELD, 2));
    }

    @Override
    public void updateTableStructure(DomainObjectTypeConfig config, List<FieldConfig> fieldConfigList, boolean isParent) {
        if (config == null || ((fieldConfigList == null || fieldConfigList.isEmpty()))) {
            throw new IllegalArgumentException("Invalid (null or empty) arguments");
        }

        String query = getQueryHelper().generateAddColumnsQuery(getName(config.getName(), false), fieldConfigList);
        jdbcTemplate.update(query);

        createAutoIndices(config, fieldConfigList, false, true, isParent);
    }

    @Override
    public void createIndices(DomainObjectTypeConfig domainObjectTypeConfig, List<IndexConfig> indexConfigsToCreate) {        
        if (domainObjectTypeConfig.getName() == null || ((indexConfigsToCreate == null || indexConfigsToCreate.isEmpty()))) {
            throw new IllegalArgumentException("Invalid (null or empty) arguments");
        }

        getQueryHelper().skipAutoIndices(domainObjectTypeConfig, indexConfigsToCreate);

        createExplicitIndexes(domainObjectTypeConfig, indexConfigsToCreate);
    }

    @Override    
    public void deleteIndices(DomainObjectTypeConfig domainObjectTypeConfig, List<IndexConfig> indexConfigsToDelete){        
        if (domainObjectTypeConfig.getName() == null || ((indexConfigsToDelete == null || indexConfigsToDelete.isEmpty()))) {
            throw new IllegalArgumentException("Invalid (null or empty) arguments");
        }
        getQueryHelper().skipAutoIndices(domainObjectTypeConfig, indexConfigsToDelete);

        List<String> indexNamesToDelete = new ArrayList<>(indexConfigsToDelete.size());
        
        for (IndexConfig indexConfigToDelete : indexConfigsToDelete) {
            List<String> indexFields = new ArrayList<>();
            List<String> indexExpressions = new ArrayList<>();

            for (BaseIndexExpressionConfig indexExpression : indexConfigToDelete.getIndexFieldConfigs()) {
                if (indexExpression instanceof IndexFieldConfig) {
                    indexFields.add(getSqlName(((IndexFieldConfig) indexExpression).getName()));
                } else if (indexExpression instanceof IndexExpressionConfig) {
                    indexExpressions.add(getSqlName(((IndexExpressionConfig) indexExpression).getValue()));
                }
            }
            
            String indexName = getQueryHelper().createExplicitIndexName(domainObjectTypeConfig, indexFields, indexExpressions);
            indexNamesToDelete.add(indexName);
        }
        
        String deleteIndexesQuery = getQueryHelper().generateDeleteExplicitIndexesQuery(indexNamesToDelete);
        if (deleteIndexesQuery != null) {
            jdbcTemplate.update(deleteIndexesQuery);
        }
    }

    private String createIndexFieldsExpression(List<String> indexFields, List<String> indexExpressions) {
        StringBuilder result = new StringBuilder();
        result.append("(").append(getQueryHelper().createIndexFieldsPart(indexFields, indexExpressions)).append(")");
        return result.toString();
    }

    @Override
    public void createForeignKeyAndUniqueConstraints(DomainObjectTypeConfig config,
                                                     List<ReferenceFieldConfig> fieldConfigList,
                                                     List<UniqueKeyConfig> uniqueKeyConfigList) {
        if (config == null || fieldConfigList == null || uniqueKeyConfigList == null) {
            throw new IllegalArgumentException("Invalid (null or empty) arguments");
        }

        int index = countForeignKeys(config.getName());
        for (ReferenceFieldConfig fieldConfig : fieldConfigList) {
            String query = getQueryHelper().generateCreateForeignKeyConstraintQuery(config, fieldConfig, index);
            if (query != null) {
                index ++;
                jdbcTemplate.update(query);
            }
        }

        index = countUniqueKeys(config.getName());
        for (UniqueKeyConfig uniqueKeyConfig : uniqueKeyConfigList) {
            String query = getQueryHelper().generateCreateUniqueConstraintQuery(config, uniqueKeyConfig, index);
            if (query != null){
                index ++;
                jdbcTemplate.update(query);
            }
        }
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#createServiceTables()}
     */
    @Override
    public void createServiceTables() {
        jdbcTemplate.update(getQueryHelper().generateCreateDomainObjectTypeIdSequenceQuery());
        jdbcTemplate.update(getQueryHelper().generateCreateDomainObjectTypeIdTableQuery());

        jdbcTemplate.update(getQueryHelper().generateCreateConfigurationSequenceQuery());
        jdbcTemplate.update(getQueryHelper().generateCreateConfigurationTableQuery());
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#isTableExist(String)}
     */
    @Override
    public boolean isTableExist(String tableName) {
        int total = jdbcTemplate.queryForObject(getQueryHelper().generateIsTableExistQuery(), Integer.class, tableName);
        return total > 0;
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#getSchemaTables()}
     */
    @Override
     public Map<String, Map<String, ColumnInfo>> getSchemaTables() {
        return jdbcTemplate.query(getQueryHelper().generateGetSchemaTablesQuery(), new SchemaTablesRowMapper());
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#getForeignKeys()}
     */
    @Override
    public Map<String, Map<String, ForeignKeyInfo>> getForeignKeys() {
        return jdbcTemplate.query(getQueryHelper().generateGetForeignKeysQuery(), new ForeignKeysRowMapper());
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#getUniqueKeys()}
     */
    @Override
    public Map<String, Map<String, UniqueKeyInfo>> getUniqueKeys() {
        return jdbcTemplate.query(getQueryHelper().generateGetUniqueKeysQuery(), new UniqueKeysRowMapper());
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#setColumnNullable(ru.intertrust.cm.core.config.DomainObjectTypeConfig, ru.intertrust.cm.core.config.FieldConfig)}
     */
    @Override
    public void setColumnNullable(DomainObjectTypeConfig config, FieldConfig fieldConfig) {
        jdbcTemplate.update(getQueryHelper().generateSetColumnNullableQuery(config, fieldConfig));
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#dropConstraint(ru.intertrust.cm.core.config.DomainObjectTypeConfig, String)}
     */
    @Override
    public void dropConstraint(DomainObjectTypeConfig config, String constraintName) {
        jdbcTemplate.update(getQueryHelper().generateDropConstraintQuery(config, constraintName));
    }

    /**
     * Смотри {@link ru.intertrust.cm.core.dao.api.DataStructureDao#updateColumnType(ru.intertrust.cm.core.config.DomainObjectTypeConfig, ru.intertrust.cm.core.config.FieldConfig)}
     */
    @Override
    public void updateColumnType(DomainObjectTypeConfig config, FieldConfig fieldConfig) {
        jdbcTemplate.update(getQueryHelper().generateUpdateColumnTypeQuery(config, fieldConfig));
    }

    protected BasicQueryHelper getQueryHelper() {
        if (queryHelper == null) {
            queryHelper = createQueryHelper(domainObjectTypeIdDao, md5Service);
        }

        return queryHelper;
    }

    private int countIndexes(DomainObjectTypeConfig domainObjectTypeConfig, boolean isAl) {
        return jdbcTemplate.queryForObject(generateCountTableIndexes(),
                new Object[]{getSqlName(domainObjectTypeConfig.getName(), isAl)}, Integer.class);
    }

    private int countForeignKeys(String domainObjectConfigName) {
        return jdbcTemplate.queryForObject(generateCountTableForeignKeys(),
                new Object[] {getSqlName(domainObjectConfigName)}, Integer.class);
    }

    private int countUniqueKeys(String domainObjectConfigName) {
        return jdbcTemplate.queryForObject(generateCountTableUniqueKeys(),
                new Object[] {getSqlName(domainObjectConfigName)}, Integer.class);
    }

    private void createExplicitIndexes(DomainObjectTypeConfig config, List<IndexConfig> indexConfigs) {
        if (indexConfigs == null || indexConfigs.isEmpty()) {
            return;
        }

        getQueryHelper().skipAutoIndices(config, indexConfigs);

        for (IndexConfig indexConfig : indexConfigs) {
            jdbcTemplate.update(getQueryHelper().generateComplexIndexQuery(config, indexConfig));
        }
    }

    private void createAutoIndices(DomainObjectTypeConfig config, boolean isParentType) {
        createAutoIndices(config, config.getFieldConfigs(), false, false, isParentType);
    }

    private void createAutoIndices(DomainObjectTypeConfig config, List<FieldConfig> fieldConfigs, boolean isAl, boolean update, boolean isParentType) {

        if (fieldConfigs == null || fieldConfigs.isEmpty()) {
            return;
        }

        int index = update ? countIndexes(config, isAl) : 0;

        for (FieldConfig fieldConfig : fieldConfigs) {
            if (!(fieldConfig instanceof ReferenceFieldConfig)) {
                continue;
            }
            jdbcTemplate.update(getQueryHelper().generateCreateAutoIndexQuery(config, (ReferenceFieldConfig)fieldConfig, index, isAl));
            index++;
        }

        // Создание индексов для системных полей.
        if (isParentType) {
            for (FieldConfig fieldConfig : config.getSystemFieldConfigs()) {
                if (fieldConfig instanceof ReferenceFieldConfig) {
                    if (SystemField.id.name().equals(fieldConfig.getName())) {
                        continue;
                    }
                    jdbcTemplate.update(getQueryHelper().generateCreateAutoIndexQuery(config, (ReferenceFieldConfig) fieldConfig, index, isAl));
                    index++;
                }
            }

            createIndexForAccessObjectId(config, index, isAl);

        }
    }

    private void createIndexForAccessObjectId(DomainObjectTypeConfig config, int index, boolean isAl) {
        ReferenceFieldConfig accessObjectIdConfig = new ReferenceFieldConfig();
        accessObjectIdConfig.setName(DomainObjectDao.ACCESS_OBJECT_ID);            
        //тип ссылочного поля не должен быть типа *, т.к. нет поля access_object_id_type и оно не будет указвано в DDL для индекса.
        jdbcTemplate.update(getQueryHelper().generateCreateAutoIndexQuery(config, accessObjectIdConfig, index, isAl));
    }

    protected abstract String generateSelectTableIndexes();

    protected abstract String generateCountTableIndexes();

    protected abstract String generateCountTableUniqueKeys();

    protected abstract String generateCountTableForeignKeys();
}
