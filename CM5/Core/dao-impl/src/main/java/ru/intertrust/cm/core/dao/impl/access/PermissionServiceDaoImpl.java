package ru.intertrust.cm.core.dao.impl.access;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import ru.intertrust.cm.core.business.api.dto.*;
import ru.intertrust.cm.core.business.api.dto.DomainObjectPermission.Permission;
import ru.intertrust.cm.core.business.api.dto.impl.RdbmsId;
import ru.intertrust.cm.core.config.*;
import ru.intertrust.cm.core.config.base.Configuration;
import ru.intertrust.cm.core.config.base.TopLevelConfig;
import ru.intertrust.cm.core.dao.access.*;
import ru.intertrust.cm.core.dao.api.ActionListener;
import ru.intertrust.cm.core.dao.api.ExtensionService;
import ru.intertrust.cm.core.dao.api.GlobalCacheClient;
import ru.intertrust.cm.core.dao.api.UserTransactionService;
import ru.intertrust.cm.core.dao.api.extension.ExtensionPoint;
import ru.intertrust.cm.core.dao.api.extension.OnLoadConfigurationExtensionHandler;
import ru.intertrust.cm.core.dao.exception.DaoException;
import ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper;
import ru.intertrust.cm.core.dao.impl.ResultSetExtractionLogger;
import ru.intertrust.cm.core.dao.impl.utils.ConfigurationExplorerUtils;
import ru.intertrust.cm.core.dao.impl.utils.DaoUtils;
import ru.intertrust.cm.core.model.PermissionException;

import javax.annotation.Resource;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionSynchronizationRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static ru.intertrust.cm.core.dao.impl.utils.DaoUtils.wrap;

/**
 * Реализация сервиса обновления списков доступа.
 * @author atsvetkov
 */
@ExtensionPoint
public class PermissionServiceDaoImpl extends BaseDynamicGroupServiceImpl implements PermissionServiceDao,
        ApplicationContextAware,
        OnLoadConfigurationExtensionHandler {
    private static final Logger logger = LoggerFactory.getLogger(PermissionServiceDaoImpl.class);
    
    @Resource
    private TransactionSynchronizationRegistry txReg;

    @Autowired
    private GlobalCacheClient globalCacheClient;

    private ApplicationContext applicationContext;

    @Autowired
    protected NamedParameterJdbcOperations masterNamedParameterJdbcTemplate; // Use for data modifying operations

    @Autowired
    protected NamedParameterJdbcOperations switchableNamedParameterJdbcTemplate; // User for read operations

    @Autowired
    private ExtensionService extensionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserTransactionService userTransactionService;

    @Autowired
    private PermissionAfterCommit permissionAfterCommit;

    @Autowired
    private AccessControlConverter accessControlConverter;

    public void setMasterNamedParameterJdbcTemplate(NamedParameterJdbcOperations masterNamedParameterJdbcTemplate) {
        this.masterNamedParameterJdbcTemplate = masterNamedParameterJdbcTemplate;
    }

    //Реестр коллекторов по отслеживаемому типу
    private Hashtable<String, List<ContextRoleRegisterItem>> collectors =
            new Hashtable<>();

    //Реестр коллекторов по имени контекстной роли
    private Hashtable<String, List<ContextRoleRegisterItem>> collectorsByContextRoleNames =
            new Hashtable<>();

    @Override
    public void notifyDomainObjectDeleted(DomainObject domainObject) {
        notifyDomainObjectChangedInternal(domainObject, getDeletedModificationList(domainObject), false, true);
        cleanAclFor(domainObject.getId());
    }

    @Override
    public void notifyDomainObjectChanged(DomainObject domainObject, List<FieldModification> modifiedFieldNames) {
        notifyDomainObjectChangedInternal(domainObject, modifiedFieldNames, false, false);
    }

    @Override
    public void notifyDomainObjectCreated(DomainObject domainObject) {
        notifyDomainObjectChangedInternal(domainObject, getNewObjectModificationList(domainObject), true, false);
    }

    private void notifyDomainObjectChangedInternal(DomainObject domainObject,
            List<FieldModification> modifiedFieldNames,
            boolean invalidateCurrent,
            boolean delete) {

        String typeName = Case.toLower(domainObject.getTypeName());

        List<ContextRoleRegisterItem> typeCollectors = collectors.get(typeName);
        // Формируем мапу динамических групп, требующих пересчета и их
        // коллекторов, исключая дублирование
        Set<Id> invalidContexts = new HashSet<Id>();

        //Для нового объекта и если сменился статус всегда добавляем в не валидный контекст сам создаваемый или измененный объект, 
        //чтобы рассчитались права со статичными или без контекстными группами
        if (invalidateCurrent) {
            invalidContexts.add(domainObject.getId());
        }

        if (typeCollectors != null) {
            for (ContextRoleRegisterItem dynamicGroupCollector : typeCollectors) {
                // Поучаем невалидные контексты и добавляем их в итоговый массив без дублирования
                addAllWithoutDuplicate(invalidContexts,
                        dynamicGroupCollector.getCollector().getInvalidContexts(domainObject,
                                modifiedFieldNames));
            }
        }

        //В случае удаления не надо добавлять в невалидные контексты ид самого удаляемого ДО
        if (delete) {
            invalidContexts.remove(domainObject.getId());
            deleteRegisteredInvalidContext(domainObject.getId());
        }

        // Непосредственно формирование состава, должно вызываться в конце транзакции
        if (invalidContexts != null) {
            regRecalcInvalidAcl(invalidContexts);
        }
    }

    @Override
    public void refreshAclIfMarked(Set<Id> invalidContextIds) {
        //не обрабатываем вне транзакции
        if (getTxReg().getTransactionKey() == null) {
            return;
        }
        //Получаем все невалидные контексты и вызываем для них перерасчет ACL
        RecalcAclSynchronization recalcGroupSynchronization = userTransactionService.getListener(RecalcAclSynchronization.class);
        
        if (recalcGroupSynchronization != null) {
            for (Id contextId : recalcGroupSynchronization.getInvalidContexts()) {
                if (invalidContextIds == null || invalidContextIds.contains(contextId)){
                    refreshAclFor(contextId);
                }
            }
        }
    }    
    
    @Override
    public void refreshAclFor(Id invalidContextId) {
        refreshAclFor(invalidContextId, true);
    }

    private void refreshAclFor(Id invalidContextId, boolean notifyCache) {
        RdbmsId rdbmsId = (RdbmsId) invalidContextId;
        String domainObjectType = domainObjectTypeIdCache.getName(rdbmsId.getTypeId());
        //Проверка, есть ли вообще матрицы доступа для данного типа доменного объекта. Необходима, что бы лишний раз не читать статус ДО.
        AccessMatrixConfig accessMatrixByTypeConfig =
                configurationExplorer.getAccessMatrixByObjectTypeUsingExtension(domainObjectType);

        if (accessMatrixByTypeConfig == null || accessMatrixByTypeConfig.getName() == null) {
            return;
        }

        String status = getStatusFor(invalidContextId);
        AccessMatrixStatusConfig accessMatrixConfig =
                configurationExplorer.getAccessMatrixByObjectTypeAndStatus(domainObjectType, status);

        if (accessMatrixConfig == null || accessMatrixConfig.getName() == null
                || accessMatrixConfig.getPermissions() == null) {
            //Для текущего статуса не найдено вхождения в матрице, отбираем у всех права
            accessMatrixConfig = new AccessMatrixStatusConfig();
            accessMatrixConfig.setName(status);
            accessMatrixConfig.setPermissions(new ArrayList<BaseOperationPermitConfig>());
        }

        //Получение необходимого состава acl
        Set<AclInfo> newAclInfos = new HashSet<>();
        List<AclData> aclDataList = new ArrayList<AclData>();
        for (BaseOperationPermitConfig operationPermitConfig : accessMatrixConfig.getPermissions()) {
            AccessType accessType = getAccessType(operationPermitConfig);
            // Проверяем что матрица должна формировать acl для текущего типа, права на read всегда храним в базе
            if (accessType == DomainObjectAccessType.READ || !isWithoutContextPermissionConfig(accessMatrixConfig, accessType)) {
                aclDataList.add(processOperationPermissions(invalidContextId, operationPermitConfig, accessType));
            }
        }

        //Добавляем без дублирования в newAclInfos
        for (AclData aclData : aclDataList) {
            for (ContextRoleAclInfo contextRoleAclInfo : aclData.getContextRoleAclInfo()) {
                addAllWithoutDuplicate(newAclInfos, contextRoleAclInfo.getAclInfos());
            }
        }

        executeExtensionPoint(aclDataList, invalidContextId);

        //Получение текущего состава acl из базы
        Set<AclInfo> oldAclInfos = getCurrentAclInfo(invalidContextId);

        //Вычисление изменения между новыми и старыми элементами
        Set<AclInfo> addAclInfo = new HashSet<>();
        Set<AclInfo> deleteAclInfo = new HashSet<AclInfo>();

        //Проверка на то что есть в новых но нет в старых
        for (AclInfo aclInfo : newAclInfos) {
            if (!oldAclInfos.contains(aclInfo)){
                addAclInfo.add(aclInfo);
            }
        }

        //Проверка на то что есть в старых но нет в новых
        for (AclInfo aclInfo : oldAclInfos) {
            if (!newAclInfos.contains(aclInfo)){
                deleteAclInfo.add(aclInfo);
            }
        }        
        
        //Непосредственно удаление или добавление в базу
        deleteAclRecords(invalidContextId, deleteAclInfo, notifyCache);
        insertAclRecords(invalidContextId, addAclInfo, notifyCache);
    }

    /**
     * Вызов точки расширения
     * @param aclDataList
     */
    private void executeExtensionPoint(List<AclData> aclDataList, Id domainObjectId) {
        //Подготавливаем структуру AclData, делаем ее меньшего размера, исключая дублирование контекстных ролей        
        //Группируем по контекстной роли
        Map<String, List<AclInfo>> contextRoleAcl = new Hashtable<String, List<AclInfo>>();
        for (AclData aclData : aclDataList) {
            for (ContextRoleAclInfo contextRoleAclInfo : aclData.getContextRoleAclInfo()) {
                String contextRoleName = contextRoleAclInfo.getRoleName() == null ? "" : contextRoleAclInfo.getRoleName();
                List<AclInfo> savedContextRoleAclInfo = contextRoleAcl.get(contextRoleName);
                if (savedContextRoleAclInfo == null) {
                    savedContextRoleAclInfo = new ArrayList<AclInfo>();
                    contextRoleAcl.put(contextRoleName, savedContextRoleAclInfo);
                }
                savedContextRoleAclInfo.addAll(contextRoleAclInfo.getAclInfos());
            }
        }

        //Формируем результат
        AclData result = new AclData();
        for (String contextRoleName : contextRoleAcl.keySet()) {
            ContextRoleAclInfo contextRoleAclInfo =
                    new ContextRoleAclInfo(contextRoleName.equals("") ? null : contextRoleName, contextRoleAcl.get(contextRoleName));
            result.getContextRoleAclInfo().add(contextRoleAclInfo);
        }

        //Регистрация на вызов после окончания транзакции
        RecalcAclSynchronization recalcGroupSynchronization = userTransactionService.getListener(RecalcAclSynchronization.class);
        if (recalcGroupSynchronization == null) {
            recalcGroupSynchronization = new RecalcAclSynchronization();
            userTransactionService.addListener(recalcGroupSynchronization);
        }
        recalcGroupSynchronization.setAclData(domainObjectId, result);

    }

    /**
     * Получение состава acl из базы
     * @param invalidContextId
     * @return
     */
    private Set<AclInfo> getCurrentAclInfo(Id invalidContextId) {

        RdbmsId rdbmsObjectId = (RdbmsId) invalidContextId;

        String tableNameRead =
                AccessControlUtility.getAclReadTableName(configurationExplorer, domainObjectTypeIdCache.getName(rdbmsObjectId.getTypeId()));
        String tableNameAcl =
                AccessControlUtility.getAclTableName(domainObjectTypeIdCache.getName(rdbmsObjectId.getTypeId()));

        StringBuilder query = new StringBuilder();
        query.append("select 'R' as operation, r.").append(DaoUtils.wrap("group_id")).append(" from ").
                append(DaoUtils.wrap(tableNameRead)).append(" r ").append("where r.")
                .append(DaoUtils.wrap("object_id")).
                append(" = :object_id ");
        query.append("union ");
        query.append("select a.").append(DaoUtils.wrap("operation")).append(", a.").append(DaoUtils.wrap("group_id"))
                .append(" from ").
                append(DaoUtils.wrap(tableNameAcl)).append(" a where a.").append(DaoUtils.wrap("object_id"))
                .append(" = :object_id ");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("object_id", rdbmsObjectId.getId());

        /*
         * switchableNamedParameterJdbcTemplate -> masterNamedParameterJdbcTemplate
         * так как незакомиченная транзакция на мастере не видна на слейве
         */
        return masterNamedParameterJdbcTemplate.query(query.toString(), parameters, new ResultSetExtractor<Set<AclInfo>>() {

            @Override
            public Set<AclInfo> extractData(ResultSet rs) throws SQLException, DataAccessException {
                Set<AclInfo> result = new HashSet<AclInfo>();
                long rowCount = 0;
                final long start = System.currentTimeMillis();
                while (rs.next()) {
                    ResultSetExtractionLogger.log("PermissionServiceDaoImpl.getCurrentAclInfo", start, ++rowCount);
                    String operation = rs.getString("operation");
                    AccessType accessType = accessControlConverter.codeToAccessType(operation);

                    if (accessType != null) {
                        AclInfo info =
                                new AclInfo(accessType, new RdbmsId(domainObjectTypeIdCache.getId("User_Group"), rs
                                        .getLong("group_id")));
                        result.add(info);
                    }
                }
                return result;
            }
        });
    }

    /**
     * Определяет тип операции {@link AccessType} по конфигурации операции.
     * @param operationPermitConfig
     *            конфигурации операции
     * @return тип операции
     */
    private AccessType getAccessType(BaseOperationPermitConfig operationPermitConfig) {
        return accessControlConverter.configToAccessType(operationPermitConfig);
    }

    /**
     * Обрабатывает все разрешения на выполнение переданной операции.
     * @param invalidContextId
     *            идентификатор доменного объекта, для которого расчитывается
     *            список доступа
     * @param operationPermitConfig
     *            конфигурация разрешений для операции
     * @param accessType
     *            тип операции
     */
    private AclData processOperationPermissions(Id invalidContextId,
            BaseOperationPermitConfig operationPermitConfig,
            AccessType accessType) {
        AclData result = new AclData();
        RdbmsId rdbmsId = (RdbmsId) invalidContextId;
        String domainObjectType = domainObjectTypeIdCache.getName(rdbmsId.getTypeId());
        for (BasePermit permit : operationPermitConfig.getPermitConfigs()) {
            if (permit.getClass().equals(PermitRole.class)) {
                String contextRoleName = permit.getName();
                ContextRoleConfig contextRoleConfig =
                        configurationExplorer.getConfig(ContextRoleConfig.class, contextRoleName);
                if (contextRoleConfig == null) {
                    throw new ConfigurationException("Context role : " + contextRoleName
                            + " not found in configuration");

                }
                validateRoleContextType(domainObjectType, contextRoleConfig);

                List<ContextRoleRegisterItem> collectors =
                        collectorsByContextRoleNames.get(contextRoleConfig.getName());

                if (collectors != null) {
                    for (ContextRoleRegisterItem collectorItem : collectors) {
                        List<AclInfo> collectorAclData = processAclForCollector(invalidContextId, collectorItem.getCollector(),
                                accessType);

                        result.getContextRoleAclInfo().add(new ContextRoleAclInfo(contextRoleConfig.getName(), collectorAclData));
                    }
                }
            } else if (permit.getClass().equals(PermitGroup.class)) {
                String dynamicGroupName = permit.getName();

                DynamicGroupConfig dynamicGroupConfig =
                        configurationExplorer.getConfig(DynamicGroupConfig.class, dynamicGroupName);

                if (dynamicGroupConfig != null && dynamicGroupConfig.getContext() != null
                        && dynamicGroupConfig.getContext().getDomainObject() != null) {

                    // контекстным объектом является текущий объект (для которого
                    // пересчитываются списки доступа)
                    AclInfo aclInfo = processAclForDynamicGroupWithContext(invalidContextId, accessType, dynamicGroupName, invalidContextId);
                    if (aclInfo != null) {
                        result.getContextRoleAclInfo().add(new ContextRoleAclInfo(null, Arrays.asList(aclInfo)));
                    }
                } else {
                    AclInfo groupAclInfo = processAclForDynamicGroupWithoutContext(invalidContextId, accessType, dynamicGroupName);
                    result.getContextRoleAclInfo().add(new ContextRoleAclInfo(null, Arrays.asList(groupAclInfo)));
                }
            }
        }
        return result;
    }

    private AclInfo processAclForDynamicGroupWithContext(Id objectId, AccessType accessType, String dynamicGroupName,
            Id contextObjectId) {
        Id dynamicGroupId = getUserGroupByGroupNameAndObjectId(dynamicGroupName, contextObjectId);
        if (dynamicGroupId != null) {
            return new AclInfo(accessType, dynamicGroupId);
        } else {
            return null;
        }
    }

    private List<AclInfo> processAclForCollector(Id invalidContextId,
            ContextRoleCollector collector, AccessType accessType) {
        List<AclInfo> result = new ArrayList<>();
        for (Id groupId : collector.getMembers(invalidContextId)) {
            if (groupId != null){
                result.add(new AclInfo(accessType, groupId));
            }else{
                logger.warn("ContextRoleCollector " + collector.getClass() + 
                        " for type " + domainObjectTypeIdCache.getName(invalidContextId) + 
                        " return null group id");
            }
        }
        return result;
    }

    private AclInfo
            processAclForDynamicGroupWithoutContext(Id objectId, AccessType accessType, String dynamicGroupName) {
        Id dynamicGroupId = getUserGroupByGroupName(dynamicGroupName);
        if (dynamicGroupId == null) {
            dynamicGroupId = createUserGroup(dynamicGroupName, null);
        }
        //insertAclRecord(accessType, objectId, dynamicGroupId);
        return new AclInfo(accessType, dynamicGroupId);
    }

    private void insertAclRecords(Id objectId, Set<AclInfo> addAclInfo, boolean notifyCache) {
        RdbmsId rdbmsObjectId = (RdbmsId) objectId;

        List<AclInfo> aclInfoRead = new ArrayList<>();
        List<AclInfo> aclInfoNoRead = new ArrayList<>();

        for (AclInfo aclInfo : addAclInfo) {
            if (aclInfo.getAccessType() == DomainObjectAccessType.READ) {
                aclInfoRead.add(aclInfo);
            } else {
                aclInfoNoRead.add(aclInfo);
            }
        }

        insertAclRecordsInBatch(aclInfoRead, new RdbmsId[] { rdbmsObjectId }, true);
        insertAclRecordsInBatch(aclInfoNoRead, new RdbmsId[] { rdbmsObjectId }, false);

        if (notifyCache) {
            globalCacheClient.notifyAclCreated(objectId, aclInfoRead);
        }
    }

    /**
     * Добавлляет ACL записи в пакетном режиме. Идентификаторы объектов должны
     * быть одного типа.
     * @param addAclInfo
     * @param rdbmsObjectIds
     * @param isReadAcl
     */
    private void insertAclRecordsInBatch(List<AclInfo> addAclInfo, RdbmsId[] rdbmsObjectIds, Boolean isReadAcl) {
        if (addAclInfo == null || addAclInfo.isEmpty() || rdbmsObjectIds == null || rdbmsObjectIds.length == 0) {
            return;
        }

        String query = null;
        RdbmsId etalonRdbmsId = rdbmsObjectIds[0];
        if (isReadAcl) {
            query = generateInsertAclReadRecordQuery(etalonRdbmsId);
        } else {
            query = generateInsertAclRecordQuery(etalonRdbmsId);

        }

        Map<String, Object>[] parameters = new Map[addAclInfo.size() * rdbmsObjectIds.length];

        int index = 0;
        for (RdbmsId rdbmsObjectId : rdbmsObjectIds) {
            for (AclInfo aclInfo : addAclInfo) {
                RdbmsId rdbmsDynamicGroupId = (RdbmsId) aclInfo.getGroupId();
                parameters[index] = initializeInsertAclRecordParameters(aclInfo.getAccessType(), rdbmsObjectId, rdbmsDynamicGroupId);
                index++;
            }
        }
        masterNamedParameterJdbcTemplate.batchUpdate(query, parameters);
    }

    /**
     * Добавляет запись в _ACl (_READ) таблицу.
     * @param accessType
     *            тип доступа
     * @param objectId
     *            идентификатор доменного объекта
     * @param dynamicGroupId
     *            идентификатор группы пользователей
     */
    private void insertAclRecord(AccessType accessType, Id objectId, Id dynamicGroupId) {
        RdbmsId rdbmsObjectId = (RdbmsId) objectId;
        RdbmsId rdbmsDynamicGroupId = (RdbmsId) dynamicGroupId;

        String query;
        if (accessType == DomainObjectAccessType.READ) {
            query = generateInsertAclReadRecordQuery(rdbmsObjectId);
        } else {
            query = generateInsertAclRecordQuery(rdbmsObjectId);

        }

        Map<String, Object> parameters =
                initializeInsertAclRecordParameters(accessType, rdbmsObjectId, rdbmsDynamicGroupId);
        masterNamedParameterJdbcTemplate.update(query, parameters);

    }

    private void deleteAclRecords(Id objectId, Set<AclInfo> addAclInfo, boolean notifyCache) {
        RdbmsId rdbmsObjectId = (RdbmsId) objectId;
        List<AclInfo> aclInfoRead = new ArrayList<>();
        List<AclInfo> aclInfoNoRead = new ArrayList<>();

        for (AclInfo aclInfo : addAclInfo) {
            if (aclInfo.getAccessType() == DomainObjectAccessType.READ) {
                aclInfoRead.add(aclInfo);
            } else {
                aclInfoNoRead.add(aclInfo);
            }
        }

        deleteAclRecordsInBatch(aclInfoRead, rdbmsObjectId, true);
        deleteAclRecordsInBatch(aclInfoNoRead, rdbmsObjectId, false);

        if (notifyCache) {
            globalCacheClient.notifyAclDeleted(objectId, aclInfoRead);
        }
    }

    private void deleteAclRecordsInBatch(List<AclInfo> addAclInfo, RdbmsId rdbmsObjectId, boolean isReadAcl) {
        if (addAclInfo == null || addAclInfo.isEmpty()) {
            return;
        }
        String query;
        if (isReadAcl) {
            query = generateDeleteAclReadRecordQuery(rdbmsObjectId);
        } else {
            query = generateDeleteAclRecordQuery(rdbmsObjectId);

        }

        Map<String, Object>[] parameters = new Map[addAclInfo.size()];

        int index = 0;
        for (AclInfo aclInfo : addAclInfo) {
            RdbmsId rdbmsDynamicGroupId = (RdbmsId) aclInfo.getGroupId();
            parameters[index] = initializeDeleteAclRecordParameters(aclInfo.getAccessType(), rdbmsObjectId, rdbmsDynamicGroupId);
            ;
            index++;
        }
        masterNamedParameterJdbcTemplate.batchUpdate(query, parameters);
    }

    private String generateDeleteAclReadRecordQuery(RdbmsId objectId) {
        String tableName;
        tableName = AccessControlUtility.getAclReadTableName(configurationExplorer,
                domainObjectTypeIdCache.getName(objectId.getTypeId()));

        StringBuilder query = new StringBuilder();
        query.append("delete from ");
        query.append(DaoUtils.wrap(tableName)).append(" where ").append(DaoUtils.wrap("object_id"))
                .append(" = :object_id ");
        query.append("and ").append(DaoUtils.wrap("group_id")).append(" = :group_id");

        return query.toString();
    }

    private String generateInsertAclReadRecordQuery(RdbmsId objectId) {
        String tableName;

        tableName = AccessControlUtility.getAclReadTableName(configurationExplorer,
                domainObjectTypeIdCache.getName(objectId.getTypeId()));

        StringBuilder query = new StringBuilder();
        query.append("insert  into ");
        query.append(DaoUtils.wrap(tableName)).append(" (").append(DaoUtils.wrap("object_id")).append(", ")
                .append(DaoUtils.wrap("group_id")).
                append(") values (:object_id, :group_id)");

        return query.toString();
    }

    private String generateInsertAclRecordQuery(RdbmsId objectId) {
        String tableName = AccessControlUtility.getAclTableName(domainObjectTypeIdCache.getName(objectId.getTypeId()));

        StringBuilder query = new StringBuilder();
        query.append("insert  into ");
        query.append(DaoUtils.wrap(tableName)).append(" (").append(DaoUtils.wrap("operation")).append(", ").
                append(DaoUtils.wrap("object_id")).append(", ").append(DaoUtils.wrap("group_id")).append(")").
                append(" values (:operation, :object_id, :group_id)");

        return query.toString();
    }

    private String getTopLevelParentType(RdbmsId objectId) {
        String objectType = domainObjectTypeIdCache.getName(objectId.getTypeId());
        String topLevelParentType = ConfigurationExplorerUtils.getTopLevelParentType(configurationExplorer, objectType);
        return topLevelParentType;
    }

    private String generateDeleteAclRecordQuery(RdbmsId objectId) {
        String tableName;
        tableName = AccessControlUtility.getAclTableName(domainObjectTypeIdCache.getName(objectId.getTypeId()));

        StringBuilder query = new StringBuilder();
        query.append("delete from ");
        query.append(DaoUtils.wrap(tableName)).append(" where ").append(DaoUtils.wrap("operation"))
                .append("=:operation and ").
                append(DaoUtils.wrap("object_id")).append("=:object_id and ").append(DaoUtils.wrap("group_id"))
                .append("=:group_id");

        return query.toString();
    }

    private Map<String, Object> initializeDeleteAclRecordParameters(AccessType accessType, RdbmsId rdbmsObjectId,
            RdbmsId rdbmsDynamicGroupId) {

        Map<String, Object> parameters = new HashMap<>();
        if (!(accessType == DomainObjectAccessType.READ)) {
            String accessTypeCode = accessControlConverter.accessTypeToCode(accessType);
            parameters.put("operation", accessTypeCode);
        }

        parameters.put("object_id", rdbmsObjectId.getId());
        parameters.put("group_id", rdbmsDynamicGroupId.getId());

        return parameters;
    }

    private Map<String, Object> initializeInsertAclRecordParameters(AccessType accessType, RdbmsId rdbmsObjectId,
            RdbmsId rdbmsDynamicGroupId) {

        Map<String, Object> parameters = new HashMap<>();
        if (!(accessType == DomainObjectAccessType.READ)) {
            String accessTypeCode = accessControlConverter.accessTypeToCode(accessType);
            parameters.put("operation", accessTypeCode);
        }

        parameters.put("object_id", rdbmsObjectId.getId());
        parameters.put("group_id", rdbmsDynamicGroupId.getId());

        return parameters;
    }

    private void validateRoleContextType(String domainObjectType, ContextRoleConfig contextRoleConfig) {
        //Переделать проверку чтобы учитывалось наследование типов
        String roleContextType = null;
        if (contextRoleConfig.getContext() != null && contextRoleConfig.getContext().getDomainObject() != null) {
            roleContextType = Case.toLower(contextRoleConfig.getContext().getDomainObject().getType());
        }

        //Получение всех вышестоящих типов
        List<String> parentTypeList = new ArrayList<>();
        String parentType = domainObjectType;
        while (parentType != null) {
            parentTypeList.add(Case.toLower(parentType));
            parentType = configurationExplorer.getDomainObjectParentType(parentType);
        }

        //Проверка наличие типа в списке родительских типов
        if (!parentTypeList.contains(roleContextType)) {
            throw new ConfigurationException("Context type for context role : " + contextRoleConfig.getName()
                    + " does not match the domain object type in access matrix configuration for: "
                    + domainObjectType);
        }

    }

    @Override
    public void cleanAclFor(Id objectId) {
        deleteAclRecords(objectId);
        deleteAclReadRecords(objectId);
    }

    private void deleteAclRecords(Id objectId) {
        deleteAclRecords(objectId, false);
    }

    private void deleteAclReadRecords(Id objectId) {
        deleteAclRecords(objectId, true);
    }

    private void deleteAclRecords(Id objectId, boolean isAclReadTable) {
        if (needDeleteAclRecords(objectId)) {
            RdbmsId rdbmsObjectId = (RdbmsId) objectId;
            String query = generateDeleteAclQuery(rdbmsObjectId, isAclReadTable);
            Map<String, Object> parameters = initializeDeleteAclParameters(rdbmsObjectId);
            masterNamedParameterJdbcTemplate.update(query, parameters);
        }
    }
    
    /**
     * Проверка на то что необходимо удалять записи прав
     * @param objectId
     * @return
     */
    private boolean needDeleteAclRecords(Id objectId){
        String objectType = domainObjectTypeIdCache.getName(objectId);
        AccessMatrixConfig accessMatrix = configurationExplorer.getAccessMatrixByObjectTypeUsingExtension(objectType);
        //Матрица не настроена, записей нет, удалять не надо
        if (accessMatrix == null){
            return false;
        }
        
        //Проверка заимствуют ли права
        if (accessMatrix.getMatrixReference() != null){
            //Если права комбинированные то удалять надо, если нет то записей быть не должно
            boolean combineAccessReference = AccessControlUtility.isCombineMatrixReference(accessMatrix);
            //Также удалять нужно, если у нас вложение с настроенными правами доступа на чтение содержимого
            boolean isAttachmentWithConfiguredPermissions = configurationExplorer.isAttachmentType(objectType)
                    && AccessControlUtility.isMatrixReferenceWithReadAttachPermissionConfig(accessMatrix);
            return combineAccessReference || isAttachmentWithConfiguredPermissions;
        }
        
        //В остальных случаях записи должны быть, мы их удаляем
        return true;
    }

    private String generateDeleteAclQuery(RdbmsId objectId, boolean isAclReadTable) {
        String tableName;
        String typeName = domainObjectTypeIdCache.getName(objectId.getTypeId());
        if (isAclReadTable) {
            tableName = AccessControlUtility.getAclReadTableName(configurationExplorer, typeName);
        } else {
            tableName = AccessControlUtility.getAclTableName(typeName);
        }

        StringBuilder query = new StringBuilder();
        query.append("delete from ");
        query.append(DaoUtils.wrap(tableName)).append(" o ");
        query.append("where o.").append(DaoUtils.wrap("object_id")).append(" = :object_id");

        return query.toString();
    }

    private Map<String, Object> initializeDeleteAclParameters(RdbmsId objectId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("object_id", objectId.getId());

        return parameters;
    }

    @Override
    public void onLoad() {
        try {
            // Поиск конфигураций динамических групп
            Configuration configuration = configurationExplorer.getConfiguration();
            List<TopLevelConfig> configurationList = configuration.getConfigurationList();
            for (TopLevelConfig topConfig : configurationList) {

                if (topConfig instanceof ContextRoleConfig) {
                    ContextRoleConfig contextRoleConfig = (ContextRoleConfig) topConfig;
                    // Если контекстная роль настраивается классом коллектором,
                    // то создаем его экземпляр, и добавляем в реестр

                    if (contextRoleConfig.getGroups() != null && contextRoleConfig.getGroups().getGroups() != null) {

                        for (Object collectorConfig : contextRoleConfig.getGroups().getGroups()) {
                            if (collectorConfig instanceof CollectorConfig) {
                                CollectorConfig classCollectorConfig = (CollectorConfig) collectorConfig;
                                Class<?> collectorClass = Class.forName(classCollectorConfig.getClassName());
                                ContextRoleCollector collector = (ContextRoleCollector) applicationContext
                                        .getAutowireCapableBeanFactory()
                                        .createBean(
                                                collectorClass,
                                                AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE,
                                                false);
                                collector.init(contextRoleConfig, classCollectorConfig.getSettings());
                                registerCollector(collector, contextRoleConfig, configuration);
                            } else if (collectorConfig instanceof TrackDomainObjectsConfig) {
                                // Специфичный коллектор не указан используем коллектор
                                // по умолчанию
                                TrackDomainObjectsConfig trackDomainObjectsConfig =
                                        (TrackDomainObjectsConfig) collectorConfig;
                                ContextRoleCollector collector = (ContextRoleCollector) applicationContext
                                        .getAutowireCapableBeanFactory()
                                        .createBean(
                                                ContextRoleTrackDomainObjectCollector.class,
                                                AutowireCapableBeanFactory.AUTOWIRE_BY_NAME,
                                                false);
                                collector.init(contextRoleConfig, trackDomainObjectsConfig);
                                registerCollector(collector, contextRoleConfig, configuration);
                            } else if (collectorConfig instanceof StaticGroupCollectorConfig) {
                                StaticGroupCollectorConfig staticGroupCollectorConfig =
                                        (StaticGroupCollectorConfig) collectorConfig;
                                ContextRoleCollector collector = (ContextRoleCollector) applicationContext
                                        .getAutowireCapableBeanFactory()
                                        .createBean(
                                                ContextRoleStaticGroupCollector.class,
                                                AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE,
                                                false);
                                collector.init(contextRoleConfig, staticGroupCollectorConfig);
                                registerCollector(collector, contextRoleConfig, configuration);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new PermissionException("Error on init collector classes", ex);
        }
    }

    private void registerCollector(ContextRoleCollector collector, ContextRoleConfig contextRoleConfig,
            Configuration configuration) {
        // Получение типов, которые отслеживает коллектор
        List<String> types = collector.getTrackTypeNames();
        // Регистрируем коллектор в реестре, для обработки
        // только определенных типов
        if (types != null) {
            for (String type : types) {
                registerCollector(type, collector, contextRoleConfig);
                // Ищем всех наследников и так же регистрируем
                // их в
                // реестре с данным коллектором
                List<String> subTypes = AccessControlUtility.getSubTypes(type, configurationExplorer);
                for (String subtype : subTypes) {
                    registerCollector(subtype, collector, contextRoleConfig);
                }
            }
        }

        registerCollectorForContextRole(contextRoleConfig.getName(), collector, contextRoleConfig);
    }

    /**
     * Регистрация коллектора в реестре коллекторов
     * 
     * @param type
     * @param collector
     */
    private void registerCollector(String type, ContextRoleCollector collector, ContextRoleConfig config) {
        List<ContextRoleRegisterItem> typeCollectors = collectors.get(Case.toLower(type));
        if (typeCollectors == null) {
            typeCollectors = new ArrayList<>();
            collectors.put(Case.toLower(type), typeCollectors);
        }
        typeCollectors.add(new ContextRoleRegisterItem(collector));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void registerCollectorForContextRole(String roleName, ContextRoleCollector collector,
            ContextRoleConfig config) {
        List<ContextRoleRegisterItem> groupCollectors = collectorsByContextRoleNames.get(roleName);
        if (groupCollectors == null) {
            groupCollectors = new ArrayList<>();
            collectorsByContextRoleNames.put(roleName, groupCollectors);
        }
        groupCollectors.add(new ContextRoleRegisterItem(collector));
    }

    /**
     * Класс для описания элемента реестра контекстных ролей
     * @author larin
     * 
     */
    private class ContextRoleRegisterItem {
        private ContextRoleCollector collector;

        private ContextRoleRegisterItem(ContextRoleCollector collector) {
            this.collector = collector;
        }

        public ContextRoleCollector getCollector() {
            return collector;
        }
    }

    @Override
    public DomainObjectPermission getObjectPermission(Id domainObjectId, Id userId) {
        List<DomainObjectPermission> result = getObjectPermissions(domainObjectId, userId);
        if (result.size() > 0) {
            return result.get(0);
        }
        return null;
    }

    @Override
    public List<DomainObjectPermission> getObjectPermissions(Id domainObjectId) {
        return getObjectPermissions(domainObjectId, null);
    }

    private List<DomainObjectPermission> getObjectPermissions(Id domainObjectId, Id personId) {
        RdbmsId rdbmsObjectId = (RdbmsId) domainObjectId;

        String objectType = domainObjectTypeIdCache.getName(rdbmsObjectId.getTypeId());
        String permissionType = objectType;
        String matrixRefType = configurationExplorer.getMatrixReferenceTypeName(permissionType);
        MatrixRefInfo matrixRefInfo = null;
        if (matrixRefType != null) {
            matrixRefInfo = getMatrixRefInfo(objectType, matrixRefType, rdbmsObjectId);
            permissionType = matrixRefInfo.type;
        }

        final AccessMatrixConfig accessMatrix = configurationExplorer.getAccessMatrixByObjectTypeUsingExtension(objectType);
        
        //Флаг комбинированного заимствования прав, когда права на чтение заимствуются, а на запись и удаления настраиваются собственные
        boolean combinateAccessReference = AccessControlUtility.isCombineMatrixReference(accessMatrix);
        
        String domainObjectBaseTable =
                DataStructureNamingHelper.getSqlName(ConfigurationExplorerUtils.getTopLevelParentType(configurationExplorer, objectType));
        String tableNameRead =
                AccessControlUtility.getAclReadTableName(configurationExplorer, permissionType);
        String objectTableNameAcl = AccessControlUtility.getAclTableName(objectType);
        String tableNameAcl = null;
        if (combinateAccessReference){
            tableNameAcl = objectTableNameAcl;
        }else{
            tableNameAcl = AccessControlUtility.getAclTableName(permissionType);
        }

        boolean isAttach = configurationExplorer.isAttachmentType(objectType);
        boolean attachReadAccessNotRestricted = isAttach && !checkIfContentReadRestricted(rdbmsObjectId, objectTableNameAcl);

        StringBuilder query = new StringBuilder();
        query.append("select 'R' as operation, gm.").append(DaoUtils.wrap("person_id")).append(", gm.").
                append(DaoUtils.wrap("person_id_type")).append(" from ").append(DaoUtils.wrap(tableNameRead))
                .append(" r ").
                append("inner join ").append(DaoUtils.wrap("group_group")).append(" gg on (r.")
                .append(DaoUtils.wrap("group_id")).
                append(" = gg.").append(DaoUtils.wrap("parent_group_id")).append(") inner join ")
                .append(DaoUtils.wrap("group_member")).
                append(" gm on gg.").append(DaoUtils.wrap("child_group_id")).append(" = gm.")
                .append(DaoUtils.wrap("usergroup")).
                //добавляем в связи с появлением функциональности замещения прав
                append("inner join ").append(DaoUtils.wrap(domainObjectBaseTable)).append(" o on (o.")
                .append(DaoUtils.wrap("access_object_id")).
                append(" = r.").append(DaoUtils.wrap("object_id")).
                append(") where o.").append(DaoUtils.wrap("id")).append(" = :object_id ");
        if (personId != null) {
            query.append("and gm.").append(DaoUtils.wrap("person_id")).append(" = :person_id ");
        }
        query.append("union ");
        // В случае с комбинированными  правами используем свою таблицу acl и join по id
        query.append(createObjectPermissionsSubQuery(tableNameAcl, domainObjectBaseTable, !combinateAccessReference, personId));
        /* _здесь_ допустимо сравнение строк через "не равно" */
        if (isAttach && !attachReadAccessNotRestricted && objectTableNameAcl != tableNameAcl) {
            query.append(" union ");
            query.append(createObjectPermissionsSubQuery(objectTableNameAcl, domainObjectBaseTable, false, personId));
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("object_id", rdbmsObjectId.getId());
        if (personId != null) {
            parameters.put("person_id", ((RdbmsId) personId).getId());
        }

        List<DomainObjectPermission> res = switchableNamedParameterJdbcTemplate.query(query.toString(), parameters, new ResultSetExtractor<List<DomainObjectPermission>>() {

            @Override
            public List<DomainObjectPermission> extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<Id, DomainObjectPermission> personPermissions = new HashMap<>();
                long rowCount = 0;
                final long start = System.currentTimeMillis();
                while (rs.next()) {
                    ResultSetExtractionLogger.log("PermissionServiceDaoImpl.getObjectPermissions", start, ++rowCount);
                    long personIdLong = rs.getLong("person_id");
                    int personType = rs.getInt("person_id_type");
                    Id personId = new RdbmsId(personType, personIdLong);
                    String operation = rs.getString("operation");

                    DomainObjectPermission personPermission = personPermissions.get(personId);
                    if (personPermission == null) {
                        personPermission = new DomainObjectPermission();
                        personPermission.setPersonId(personId);
                        personPermissions.put(personId, personPermission);
                    }

                    if (operation.equals(AccessControlConverter.OP_READ)) {
                        if (accessMatrix.getMatrixReference() != null) {
                            setMappedPermission(accessMatrix, personPermission, Permission.Read);
                        } else {
                            personPermission.getPermission().add(Permission.Read);
                        }
                        if (attachReadAccessNotRestricted) {
                            personPermission.getPermission().add(Permission.ReadAttachment);
                        }
                    } else if (operation.equals(AccessControlConverter.OP_WRITE)) {
                        if (accessMatrix.getMatrixReference() != null) {
                            setMappedPermission(accessMatrix, personPermission, Permission.Write);
                        } else {
                            personPermission.getPermission().add(Permission.Write);
                        }
                    } else if (operation.equals(AccessControlConverter.OP_DELETE)) {
                        if (accessMatrix.getMatrixReference() != null) {
                            setMappedPermission(accessMatrix, personPermission, Permission.Delete);
                        } else {
                            personPermission.getPermission().add(Permission.Delete);
                        }
                    } else if (operation.equals(AccessControlConverter.OP_READ_ATTACH)) {
                        personPermission.getPermission().add(Permission.ReadAttachment);
                    } else if (operation.startsWith(AccessControlConverter.OP_EXEC_ACTION_PREF)) {
                        String action = operation.substring(2);
                        if (accessMatrix.getMatrixReference() != null) {
                            setMappedActions(accessMatrix, personPermission, action);
                        } else {
                            personPermission.getActions().add(action);
                        }
                    } else if (operation.startsWith(AccessControlConverter.OP_CREATE_CHILD_PREF)) {
                        String childType = operation.substring(2);
                        if (accessMatrix.getMatrixReference() != null) {
                            setMappedCreateTypes(accessMatrix, personPermission, childType);
                        } else {
                            personPermission.getCreateChildTypes().add(childType);
                        }
                    }
                }
                List<DomainObjectPermission> result = new ArrayList<>(personPermissions.size());

                result.addAll(personPermissions.values());
                return result;
            }

        });

        if (res == null) {
            res = new ArrayList<>();
        }

        if (isAttach && attachReadAccessNotRestricted && personId != null) {
            /* Добавляем право на чтение вложения, если по каким-то причинам (общедоступно и на чтение, и на чтение содержимого) оно ещё не проставлено */
            DomainObjectPermission personPermission = res.stream().filter(p -> personId.equals(p.getPersonId())).findAny().orElse(null);
            if (personPermission == null) {
                personPermission = new DomainObjectPermission();
                personPermission.setPersonId(personId);
                res.add(personPermission);
            }
            if (!personPermission.getPermission().contains(Permission.ReadAttachment)) {
                personPermission.getPermission().add(Permission.ReadAttachment);
            }
        }


        if (accessMatrix != null) {
            // Получение статуса непосредственно объекта и объекта откуда заимствуем права если права заимствованные
            String origStatus = getStatusFor(domainObjectId);
            String refStatus = matrixRefType != null ? getStatusFor(matrixRefInfo.id) : null;

            AccessMatrixStatusConfig origMatrixStatusConfig =
                    configurationExplorer.getAccessMatrixByObjectTypeAndStatus(objectType, origStatus);
            AccessMatrixStatusConfig refMatrixStatusConfig = matrixRefType != null ?
                    configurationExplorer.getAccessMatrixByObjectTypeAndStatus(permissionType, refStatus) : null;
            AccessMatrixStatusConfig combinedMatrixStatusConfig = getCombinedMatrixConfig(
                    accessMatrix, origMatrixStatusConfig, refMatrixStatusConfig);

            if (combinedMatrixStatusConfig != null) {
                // Если передана персона, то вычисляем для нее, если нет, то вычисляем для ВСЕХ! персон
                if (personId != null) {
                    for (BaseOperationPermitConfig permissions : combinedMatrixStatusConfig.getPermissions()) {
                        appendTypePermission(res, accessMatrix, personId, combinedMatrixStatusConfig, permissions);
                    }
                } else {
                    // Вычисляем для всех персон ( TODO очень тяжелая ветка, надо бы ее запретить если не используется нигде кроме тестов)
                    List<Id> personsId = masterNamedParameterJdbcTemplate.query(
                            "select id, id_type from person", Collections.emptyMap(), new RowMapper<Id>() {
                                @Override
                                public Id mapRow(ResultSet rs, int rowNum) throws SQLException {
                                    return new RdbmsId(rs.getInt("id_type"), rs.getLong("id"));
                                }
                            });
                    for (Id onePersonId : personsId) {
                        for (BaseOperationPermitConfig permissions : combinedMatrixStatusConfig.getPermissions()) {
                            appendTypePermission(res, accessMatrix, onePersonId, combinedMatrixStatusConfig, permissions);
                        }
                    }
                }
            }
        }

        return res;
    }

    /**
     * Получение матрицы для комбининируемых прав
     * @param accessMatrix
     * @param origMatrixStatusConfig
     * @param refMatrixStatusConfig
     * @return
     */
    private AccessMatrixStatusConfig getCombinedMatrixConfig(AccessMatrixConfig accessMatrix,
                                                             AccessMatrixStatusConfig origMatrixStatusConfig,
                                                             AccessMatrixStatusConfig refMatrixStatusConfig) {
        AccessMatrixStatusConfig result = new AccessMatrixStatusConfig();

        if (accessMatrix.getMatrixReference() == null
                || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.none){
            result = origMatrixStatusConfig;
        }else if (accessMatrix.getBorrowPermissisons() == null
                || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.all){
            result = refMatrixStatusConfig;
        }else if (accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.read){
            if (origMatrixStatusConfig != null) {
                for (BaseOperationPermitConfig permissions : origMatrixStatusConfig.getPermissions()) {
                    if (permissions instanceof WriteConfig
                            || permissions instanceof DeleteConfig
                            || permissions instanceof CreateChildConfig
                            || permissions instanceof ExecuteActionConfig
                            || permissions instanceof ReadAttachmentConfig) {
                        result.getPermissions().add(permissions);
                    }
                }
            }
            if (refMatrixStatusConfig != null) {
                for (BaseOperationPermitConfig permissions : refMatrixStatusConfig.getPermissions()) {
                    if (permissions instanceof ReadConfig) {
                        result.getPermissions().add(permissions);
                    }
                }
            }
        }else if (accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.readWriteDelete){
            if (origMatrixStatusConfig != null) {
                for (BaseOperationPermitConfig permissions : origMatrixStatusConfig.getPermissions()) {
                    if (permissions instanceof CreateChildConfig
                            || permissions instanceof ExecuteActionConfig
                            || permissions instanceof ReadAttachmentConfig) {
                        result.getPermissions().add(permissions);
                    }
                }
            }
            if (refMatrixStatusConfig != null) {
                for (BaseOperationPermitConfig permissions : refMatrixStatusConfig.getPermissions()) {
                    if (permissions instanceof ReadConfig
                            || permissions instanceof WriteConfig
                            || permissions instanceof DeleteConfig) {
                        result.getPermissions().add(permissions);
                    }
                }
            }
        }

        return result;
    }

    private void appendTypePermission(List<DomainObjectPermission> result, AccessMatrixConfig accessMatrix, Id personId,
                                      AccessMatrixStatusConfig matrixStatusConfig,
                                      BaseOperationPermitConfig permissionsConfig){

        AccessType accessType = getAccessType(permissionsConfig);

        // в случае безконтекстных прав на тип формируем информацию о правах на объект. Кроме read; read всегда получится из базы
        if (accessType != DomainObjectAccessType.READ && isWithoutContextPermissionConfig(matrixStatusConfig, accessType)){
            if (hasUserTypePermission(personId, accessType, matrixStatusConfig)){

                // Получаем или создаем объект прав
                DomainObjectPermission personPermission = result.stream().filter(p -> personId.equals(p.getPersonId())).
                        findAny().orElse(null);
                if (personPermission == null){
                    personPermission = new DomainObjectPermission();
                    personPermission.setPersonId(personId);
                    result.add(personPermission);
                }

                // Формируем состав обекта доступа, кроме read; read всегда получится из базы
                if (accessType == DomainObjectAccessType.WRITE){
                    if (accessMatrix.getMatrixReference() != null
                            && (accessMatrix.getBorrowPermissisons() == null
                            || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.readWriteDelete
                            || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.all)) {
                        setMappedPermission(accessMatrix, personPermission, Permission.Write);
                    }else {
                        personPermission.getPermission().add(Permission.Write);
                    }
                }else if (accessType == DomainObjectAccessType.DELETE){
                    if (accessMatrix.getMatrixReference() != null
                            && (accessMatrix.getBorrowPermissisons() == null
                            || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.readWriteDelete
                            || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.all)) {
                        setMappedPermission(accessMatrix, personPermission, Permission.Delete);
                    }else {
                        personPermission.getPermission().add(Permission.Delete);
                    }
                }else if (accessType == DomainObjectAccessType.READ_ATTACH){
                    // TODO не реализован маппинг прав чтения вложения
                    personPermission.getPermission().add(Permission.ReadAttachment);
                }else if (accessType instanceof CreateChildAccessType){
                    if (accessMatrix.getMatrixReference() != null
                            && (accessMatrix.getBorrowPermissisons() == null
                            || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.all)) {
                        setMappedCreateTypes(accessMatrix, personPermission,
                                ((CreateChildAccessType) accessType).getChildType());
                    }else {
                        personPermission.getCreateChildTypes().add(((CreateChildAccessType) accessType).getChildType());
                    }
                }else if (accessType instanceof ExecuteActionAccessType){
                    if (accessMatrix.getMatrixReference() != null
                            && (accessMatrix.getBorrowPermissisons() == null
                            || accessMatrix.getBorrowPermissisons() == AccessMatrixConfig.BorrowPermissisonsMode.all)) {
                        setMappedActions(accessMatrix, personPermission,
                                ((ExecuteActionAccessType) accessType).getActionName());
                    }else {
                        personPermission.getActions().add(((ExecuteActionAccessType) accessType).getActionName());
                    }
                }
            }
        }
    }

    /**
     * Проверка имеет ли пользователь права на тип для данного доменного объекта
     * @param personId
     * @param accessType
     * @return
     */
    @Override
    public boolean hasUserTypePermission(Id personId, AccessType accessType, AccessMatrixStatusConfig matrixStatusConfig) {
        // если нет матрицы для статуса то прав нет на тип
        if (matrixStatusConfig == null){
            return false;
        }
        // Собираем группы, которым разрешен доступ
        Set<String> groups = new HashSet<>();
        for (BaseOperationPermitConfig permitConfig : matrixStatusConfig.getPermissions()) {
            // Получаем только раздел конфигурации, соответствующий проверяемому типу доступа
            if (checkAccessType(permitConfig, accessType)) {
                for (BasePermit basePermit : permitConfig.getPermitConfigs()) {
                    // Здесь могут быть только группы, иначе сюда не попадем, поэтому не анализируем имя класса в basePermit
                    groups.add(basePermit.getName());
                }
            }
        }

        return checkUserGroups(personId, groups);
    }

    @Override
    public boolean checkUserGroup(Id userId, String groupName) {
        return checkUserGroups(userId, Collections.singleton(groupName));
    }

    @Override
    public boolean checkUserGroups(Id userId, Set<String> groupNames) {
        if (groupNames.size() == 0){
            return false;
        }

        String query = getQueryForCheckUserGroup();

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("user_id", ((RdbmsId)userId).getId());
        parameters.put("group_names", new ArrayList<>(groupNames));
        Integer result = masterNamedParameterJdbcTemplate.queryForObject(query, parameters, Integer.class);
        return result > 0;
    }

    private String getQueryForCheckUserGroup() {
        StringBuilder query = new StringBuilder();

        query.append("select count(*) from ").append(wrap("user_group")).append(" ug ");
        query.append("inner join ").append(wrap("group_group")).append(" gg on ug.").append(wrap("id"))
                .append(" = gg.").append(wrap("parent_group_id"));
        query.append("inner join ").append(wrap("group_member")).append(" gm on gg.").append(wrap("child_group_id"))
                .append(" = gm.").append(wrap("usergroup"));
        query.append("where gm.").append(wrap("person_id")).append(" = :user_id and ug.").append(wrap("group_name"))
                .append(" in ( :group_names )");
        return query.toString();
    }

    private StringBuilder createObjectPermissionsSubQuery(String aclTableName, String domainObjectBaseTable, boolean useAccessObjectId, Id personId) {
        StringBuilder query = new StringBuilder();
        query.append("select a.").append(DaoUtils.wrap("operation")).append(", gm.").append(DaoUtils.wrap("person_id"))
                .append(", ").
                        append("gm.").append(DaoUtils.wrap("person_id_type")).append(" from ")
                .append(DaoUtils.wrap(aclTableName)).append(" a ").
                        append("inner join ").append(DaoUtils.wrap("group_group")).append(" gg on (a.")
                .append(DaoUtils.wrap("group_id")).
                        append(" = gg.").append(DaoUtils.wrap("parent_group_id")).append(") inner join ")
                .append(DaoUtils.wrap("group_member")).
                        append(" gm on gg.").append(DaoUtils.wrap("child_group_id")).append(" = gm.")
                .append(DaoUtils.wrap("usergroup")).
                //добавляем в связи с появлением функциональности замещения прав
                        append("inner join ").append(DaoUtils.wrap(domainObjectBaseTable)).append(" o on (o.");

        if (!useAccessObjectId) {
            query.append(DaoUtils.wrap("id"));
        } else {
            query.append(DaoUtils.wrap("access_object_id"));
        }
        query.append(" = a.").append(DaoUtils.wrap("object_id")).
                append(") where o.").append(DaoUtils.wrap("id")).append(" = :object_id ");

        if (personId != null) {
            query.append("and gm.").append(DaoUtils.wrap("person_id")).append(" = :person_id");
        }

        return query;
    }

    /**
     * Получение имени типа у которого заимствуются права. При этом учитывается
     * то что в матрице при заимствование может быть указан атрибут ссылающийся
     * на родительский тип того объекта, у которого реально надо взять матрицу
     * прав
     * @param childType
     * @param parentType
     * @param id
     * @return
     */
    private MatrixRefInfo getMatrixRefInfo(String childType, String parentType, RdbmsId id) {
        String rootForChildType = ConfigurationExplorerUtils.getTopLevelParentType(configurationExplorer, childType);
        String rootForParentType = ConfigurationExplorerUtils.getTopLevelParentType(configurationExplorer, parentType);
        String query =
                "select p.id_type, c.access_object_id from " + rootForChildType + " c inner join " + rootForParentType + " p on (c.access_object_id = p.id) where c.id = :id";

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", id.getId());

        MatrixRefInfo matrixRefInfo = switchableNamedParameterJdbcTemplate.query(query, parameters, new ResultSetExtractor<MatrixRefInfo>() {

            @Override
            public MatrixRefInfo extractData(ResultSet rs) throws SQLException,
                    DataAccessException {
                rs.next();
                MatrixRefInfo result = new MatrixRefInfo();
                result.type = domainObjectTypeIdCache.getName(rs.getInt("id_type"));
                result.id = new RdbmsId(rs.getInt("id_type"), rs.getLong("access_object_id"));
                return result;
            }
        });
        return matrixRefInfo;

    }

    private void regRecalcInvalidAcl(Set<Id> invalidContext) {
        //не обрабатываем вне транзакции
        if (getTxReg().getTransactionKey() == null) {
            return;
        }
        RecalcAclSynchronization recalcGroupSynchronization = userTransactionService.getListener(RecalcAclSynchronization.class);
        if (recalcGroupSynchronization == null) {
            recalcGroupSynchronization = new RecalcAclSynchronization();
            userTransactionService.addListener(recalcGroupSynchronization);
        }
        recalcGroupSynchronization.addContext(invalidContext);
    }

    private void deleteRegisteredInvalidContext(Id invalidContext) {
        //не обрабатываем вне транзакции
        if (getTxReg().getTransactionKey() == null) {
            return;
        }
        RecalcAclSynchronization recalcGroupSynchronization = userTransactionService.getListener(RecalcAclSynchronization.class);
        if (recalcGroupSynchronization == null) {
            recalcGroupSynchronization = new RecalcAclSynchronization();
            userTransactionService.addListener(recalcGroupSynchronization);
        }
        recalcGroupSynchronization.deleteContext(invalidContext);
    }    
    
    private TransactionSynchronizationRegistry getTxReg() {
        if (txReg == null) {
            try {
                txReg =
                        (TransactionSynchronizationRegistry) new InitialContext()
                                .lookup("java:comp/TransactionSynchronizationRegistry");
            } catch (NamingException e) {
                throw new DaoException(e);
            }
        }
        return txReg;
    }

    private class RecalcAclSynchronization implements ActionListener {
        private Set<Id> contextIds = new HashSet<>();
        private Map<Id, AclData> aclDatas = new HashMap<Id, AclData>();

        public RecalcAclSynchronization() {
        }

        public void addContext(Set<Id> invalidContexts) {
            addAllWithoutDuplicate(contextIds, invalidContexts);
        }

        public void deleteContext(Id invalidContexts) {
            contextIds.remove(invalidContexts);
        }

        public void setAclData(Id id, AclData aclData) {
            aclDatas.put(id, aclData);
        }

        @Override
        public void onBeforeCommit() {
            //Перекладываем в массив чтобы избежать ConcurrentModificationException
            Id[] ids = contextIds.toArray(new Id[contextIds.size()]);
            //Очищаем contextIds
            contextIds.clear();

            //Цикл по сформированному массиву
            for (Id contextId : ids) {
                refreshAclFor(contextId, true);
            }

            //Если в процессе назначения прав в методе refreshAclFor был изменен список contextIds то вызываем повторно
            if (contextIds.size() > 0) {
                onBeforeCommit();
            }
        }

        @Override
        public void onAfterCommit() {
            //Вызов обработчиков точки расширения изменения прав
            permissionAfterCommit.onAfterCommit(aclDatas);
        }

        @Override
        public void onRollback() {
        }

        public Set<Id> getInvalidContexts() {
            return contextIds;
        }
    }

    @Override
    public List<Id> getPersons(Id contextId, String roleName) {

        List<ContextRoleRegisterItem> collectors = collectorsByContextRoleNames.get(roleName);

        LinkedHashSet<Id> result = new LinkedHashSet<>();
        if (collectors != null) {
            for (ContextRoleRegisterItem collectorItem : collectors) {
                List<Id> groups = collectorItem.getCollector().getMembers(contextId);
                for (Id groupId : groups) {
                    List<DomainObject> persons = personManagementService.getAllPersonsInGroup(groupId);
                    addUniquePerson(result, persons);
                }
            }
        }

        return new ArrayList<>(result);
    }

    @Override
    public List<Id> getGroups(Id contextId, String roleName) {

        //Получаем все коллекторы
        List<ContextRoleRegisterItem> collectors = collectorsByContextRoleNames.get(roleName);

        LinkedHashSet<Id> result = new LinkedHashSet<>();
        //Цикл по коллекторам
        if (collectors != null) {
            for (ContextRoleRegisterItem collectorItem : collectors) {
                //Получаем состав контекстной роли
                List<Id> groups = collectorItem.getCollector().getMembers(contextId);
                for (Id group : groups) {
                    //Формируем результат с уникальными значениями
                    result.add(group);
                }
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * Добавление уникальных записей в результат
     * @param result
     * @param persons
     */
    private void addUniquePerson(LinkedHashSet<Id> result, List<DomainObject> persons) {
        for (DomainObject domainObject : persons) {
            result.add(domainObject.getId());
        }
    }

    @Override
    public void grantNewObjectPermissions(List<Id> domainObjectIds) {
        Id currentPersonId = currentUserAccessor.getCurrentUserId();

        if (currentPersonId != null && !userGroupGlobalCache.isPersonSuperUser(currentPersonId)) {
            //Исключаем из списка доменные объекты с заимствованными правами и с правами на чтение всем
            List<Id> effectiveDomainObjectIdsRead = new ArrayList<Id>(domainObjectIds.size());
            List<Id> effectiveDomainObjectIdsNoRead = new ArrayList<Id>(domainObjectIds.size());
            for (Id id : domainObjectIds) {
                String typeName = domainObjectTypeIdCache.getName(id);
                AccessMatrixConfig matrixConfig = configurationExplorer.getAccessMatrixByObjectTypeUsingExtension(typeName);
                if (matrixConfig != null) {
                    if (matrixConfig.getMatrixReference() == null) {
                        effectiveDomainObjectIdsNoRead.add(id);
                        if (matrixConfig.isReadEverybody() == null || !matrixConfig.isReadEverybody()) {
                            effectiveDomainObjectIdsRead.add(id);
                        }
                    }
                }
            }
            // Получение динамической группы текущего пользователя.
            Id currentPersonGroup = getUserGroupByGroupNameAndObjectId("Person", currentPersonId);

            List<AclInfo> aclInfoRead = new ArrayList<>();
            List<AclInfo> aclInfoNoRead = new ArrayList<>();

            aclInfoRead.add(new AclInfo(DomainObjectAccessType.READ, currentPersonGroup));
            aclInfoNoRead.add(new AclInfo(DomainObjectAccessType.WRITE, currentPersonGroup));
            aclInfoNoRead.add(new AclInfo(DomainObjectAccessType.DELETE, currentPersonGroup));

            RdbmsId[] idsArrayRead = effectiveDomainObjectIdsRead.toArray(new RdbmsId[effectiveDomainObjectIdsRead.size()]);
            RdbmsId[] idsArrayNoRead = effectiveDomainObjectIdsNoRead.toArray(new RdbmsId[effectiveDomainObjectIdsNoRead.size()]);

            insertAclRecordsInBatch(aclInfoRead, idsArrayRead, true);
            insertAclRecordsInBatch(aclInfoNoRead, idsArrayNoRead, false);
        }
    }

    /**
     * Предоставление прав на новые доменные обьекты
     */
    @Override
    public void grantNewObjectPermissions(Id domainObject) {
        //Получение текущего пользователя
        Id currentPersonId = currentUserAccessor.getCurrentUserId();

        //Проверка наличия контекста пользователя и проверка что пользователь не суперпользователь
        if (currentPersonId != null && !userGroupGlobalCache.isPersonSuperUser(currentPersonId)) {
            //Исключаем доменные объекты с заимствованными правами и с правами на чтение всем        
            String typeName = domainObjectTypeIdCache.getName(domainObject);
            AccessMatrixConfig matrixConfig = configurationExplorer.getAccessMatrixByObjectTypeUsingExtension(typeName);
            if (matrixConfig != null && matrixConfig.getMatrixReference() == null) {

                //Получение динамической группы текущего пользователя.
                Id currentPersonGroup = getUserGroupByGroupNameAndObjectId("Person", currentPersonId);

                //Добавляем права группе
                if (matrixConfig.isReadEverybody() == null || !matrixConfig.isReadEverybody()) {
                    insertAclRecord(DomainObjectAccessType.READ, domainObject, currentPersonGroup);
                }

                insertAclRecord(DomainObjectAccessType.WRITE, domainObject, currentPersonGroup);
                insertAclRecord(DomainObjectAccessType.DELETE, domainObject, currentPersonGroup);
            }
        }
    }

    @Override
    public void refreshAcls() {
        refreshAclIfMarked(null);
    }

    @Override
    public void notifyDomainObjectChangeStatus(DomainObject domainObject) {
        notifyDomainObjectChangedInternal(domainObject, null, true, false);
    }

    @Override
    public boolean checkIfContentReadRestricted(Id attachId) {
        RdbmsId rdbmsObjectId = (RdbmsId) attachId;
        String objectType = domainObjectTypeIdCache.getName(rdbmsObjectId.getTypeId());
        if (!configurationExplorer.isAttachmentType(objectType)) {
            throw new IllegalArgumentException();
        }

        String aclTableName = AccessControlUtility.getAclTableName(objectType);
        return checkIfContentReadRestricted(attachId, aclTableName);
    }

    /**
     * Проверка, ограничен ли доступ к вложению на операцию чтения содержимого
     *
     * @param attachId id вложения - по нему будем искать ограничения в таблице с правами
     * @param aclTableName табличка с ACL для вложений, к типу которых относится наше
     * @return true, если доступ ограничен
     */
    private boolean checkIfContentReadRestricted(Id attachId, String aclTableName) {

        RdbmsId rdbmsObjectId = (RdbmsId) attachId;
        StringBuilder query = new StringBuilder();
        query.append("select count(*) from ")
                .append(DaoUtils.wrap(aclTableName))
                .append(" where ").append(DaoUtils.wrap("operation")).append(" = :operation")
                .append(" and ").append(DaoUtils.wrap("object_id")).append(" = :object_id");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("object_id", rdbmsObjectId.getId());
        parameters.put("operation", AccessControlConverter.OP_READ_ATTACH);

        /* если права доступа на чтение содержимого вложения не указаны ни для кого, то оно считается общедоступным */
        return (0 < switchableNamedParameterJdbcTemplate.query(query.toString(), parameters, new ResultSetExtractor<Integer>() {

            @Override
            public Integer extractData(ResultSet rs) throws SQLException, DataAccessException {
                rs.next();
                return rs.getInt(1);
            }
        }));
    }

    @Override
    public Set<Id> checkIfContentReadAllowedForAll(List<Id> objectIds, String objType) {
        Set<Id> restrictedIds = checkIfContentReadRestricted(objectIds, objType);
        Set<Id> res = new HashSet<>(objectIds);
        res.removeAll(restrictedIds);
        return res;
    }

    @Override
    public Set<Id> checkIfContentReadRestricted(List<Id> objectIds, String objType) {

        String aclTableName = AccessControlUtility.getAclTableNameFor(objType);
        Integer objTypeId = domainObjectTypeIdCache.getId(objType);

        StringBuilder query = new StringBuilder();

        query.append("select objectId from ")
                .append(DaoUtils.wrap(aclTableName))
                .append(" where ").append(DaoUtils.wrap("operation")).append(" = :operation")
                .append(" and ").append(DaoUtils.wrap("object_id")).append(" in (:object_ids)");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("object_ids", AccessControlUtility.convertRdbmsIdsToLongIds(objectIds));
        parameters.put("operation", AccessControlConverter.OP_READ_ATTACH);

        RowMapper<Id> extractorFunction = (rs, num) -> {
            long objectId = rs.getLong("object_id");
            return new RdbmsId(objTypeId, objectId);
        };

        List<Id> checkedIds = switchableNamedParameterJdbcTemplate.query(query.toString(), parameters, extractorFunction);
        return new HashSet<>(checkedIds);
    }

    /**
     * Проверка на то что конфигурация прав не зависит от контекста
     * @return
     */
    @Override
    public boolean isWithoutContextPermissionConfig(AccessMatrixStatusConfig matrixStatusConfig, AccessType accessType){
        // если нет настроек матрицы для статуса то прав нет на объект и не важно в acl или в типе эти настройки,
        // более экономично вычислить права отностительно типа, поэтому возвращаем true
        if (matrixStatusConfig == null){
            return true;
        }

        // выполняем поиск до тех пор, пока не найдем любую роль или контектную динамичсескую группу
        for (BaseOperationPermitConfig permitConfig : matrixStatusConfig.getPermissions()) {
            // Анализируем только раздел конфигурации, соответствующий проверяемому типу доступа
            if (checkAccessType(permitConfig, accessType)) {
                for (BasePermit basePermit : permitConfig.getPermitConfigs()) {
                    // безконтекстными могут быть только группы
                    if (basePermit instanceof PermitGroup) {
                        PermitGroup permitGroup = (PermitGroup) basePermit;
                        // Проверяем статичная группа или нет
                        StaticGroupConfig staticGroupConfig =
                                configurationExplorer.getConfig(StaticGroupConfig.class, permitGroup.getName());
                        // Проверяем найдена ли статичная группа
                        if (staticGroupConfig == null) {
                            // Если статичная группа не найдена то ищем динамическую группу с таким же именем
                            DynamicGroupConfig dynamicGroupConfig =
                                    configurationExplorer.getConfig(DynamicGroupConfig.class, permitGroup.getName());
                            // для группы проверяем контекстная она или нет
                            if (dynamicGroupConfig.getContext() != null
                                    && dynamicGroupConfig.getContext().getDomainObject() != null
                                    && dynamicGroupConfig.getContext().getDomainObject().getType() != null) {
                                return false;
                            }
                        }
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Проверка соответствия настройки прав в матрице проверяемому типу доступа
     * @param permitConfig
     * @param accessType
     * @return
     */
    @Override
    public boolean checkAccessType(BaseOperationPermitConfig permitConfig, AccessType accessType) {
        boolean result = false;
        if ((permitConfig instanceof WriteConfig) && accessType.equals(DomainObjectAccessType.WRITE)){
            result = true;
        }else if((permitConfig instanceof DeleteConfig) && accessType.equals(DomainObjectAccessType.DELETE)){
            result = true;
        }else if((permitConfig instanceof CreateChildConfig)
                && accessType.equals(new CreateChildAccessType(((CreateChildConfig)permitConfig).getType()))){
            result = true;
        }else if((permitConfig instanceof ExecuteActionConfig)
                && accessType.equals(new ExecuteActionAccessType(((ExecuteActionConfig)permitConfig).getName()))){
            result = true;
        }else if((permitConfig instanceof ReadAttachmentConfig)
                && accessType.equals(DomainObjectAccessType.READ_ATTACH)){
            result = true;
        }else if((permitConfig instanceof ReadConfig)
                && accessType.equals(DomainObjectAccessType.READ)){
            result = true;
        }
        return result;
    }


    public static class MatrixRefInfo{
        private String type;
        private Id id;
    }

    private void setMappedPermission(AccessMatrixConfig accessMatrix, DomainObjectPermission personPermission, Permission permission) {
        MatrixReferenceMappingConfig matrixReferenceMappingConfig = accessMatrix.getMatrixReferenceMappingConfig();
        if (matrixReferenceMappingConfig != null) {
            List<MatrixReferenceMappingPermissionConfig> permissionConfigs = matrixReferenceMappingConfig.getPermission();
            if (permissionConfigs != null) {
                for (MatrixReferenceMappingPermissionConfig matrixMapping : permissionConfigs) {
                    String mapTo = matrixMapping.getMapTo();
                    String mapFrom = matrixMapping.getMapFrom();
                    if (permission.equals(Permission.Read) && mapFrom.equals(MatrixReferenceMappingPermissionConfig.READ)) {
                        personPermission.getPermission().addAll(getPermissionFromMatrixRef(mapTo));
                        personPermission.getActions().addAll(getActionsFromMatrixRef(mapTo));
                        personPermission.getCreateChildTypes().addAll(getCreateChildFromMatrixRef(mapTo));
                    } else if (permission.equals(Permission.Write) && mapFrom.equals(MatrixReferenceMappingPermissionConfig.WRITE)) {
                        personPermission.getPermission().addAll(getPermissionFromMatrixRef(mapTo));
                        personPermission.getActions().addAll(getActionsFromMatrixRef(mapTo));
                        personPermission.getCreateChildTypes().addAll(getCreateChildFromMatrixRef(mapTo));
                    } else if (permission.equals(Permission.Delete) && mapFrom.equals(MatrixReferenceMappingPermissionConfig.DELETE)) {
                        personPermission.getPermission().addAll(getPermissionFromMatrixRef(mapTo));
                        personPermission.getActions().addAll(getActionsFromMatrixRef(mapTo));
                        personPermission.getCreateChildTypes().addAll(getCreateChildFromMatrixRef(mapTo));
                    }
                }
            }
        } else {
            //Используем дефалтовый мапинг
            personPermission.getPermission().add(permission);
            if (permission.equals(Permission.Write)) {
                personPermission.getPermission().add(Permission.Delete);
            }
        }
    }

    private List<Permission> getPermissionFromMatrixRef(String mapTo) {
        switch (mapTo) {
            case MatrixReferenceMappingPermissionConfig.READ:
                return Collections.singletonList(Permission.Read);
            case MatrixReferenceMappingPermissionConfig.WRITE:
                return Collections.singletonList(Permission.Write);
            case MatrixReferenceMappingPermissionConfig.DELETE:
                return Collections.singletonList(Permission.Delete);
        }
        return Collections.emptyList();
    }

    private List<String> getActionsFromMatrixRef(String mapTo) {
        if (mapTo.startsWith(MatrixReferenceMappingPermissionConfig.EXECUTE)) {
            return Collections.singletonList(mapTo.split(":")[1]);
        }
        return Collections.emptyList();
    }

    private List<String> getCreateChildFromMatrixRef(String mapTo) {
        if (mapTo.startsWith(MatrixReferenceMappingPermissionConfig.CREATE_CHILD)) {
            return Collections.singletonList(mapTo.split(":")[1]);
        }
        return Collections.emptyList();
    }

    private void setMappedActions(AccessMatrixConfig accessMatrix, DomainObjectPermission personPermission, String action) {
        MatrixReferenceMappingConfig matrixReferenceMappingConfig = accessMatrix.getMatrixReferenceMappingConfig();
        if (matrixReferenceMappingConfig != null) {
            List<MatrixReferenceMappingPermissionConfig> permissionConfigs = matrixReferenceMappingConfig.getPermission();
            if (permissionConfigs != null) {
                for (MatrixReferenceMappingPermissionConfig matrixMapping : permissionConfigs) {
                    if (matrixMapping.getMapFrom().equals(MatrixReferenceMappingPermissionConfig.EXECUTE + ":" + action)) {
                        String mapTo = matrixMapping.getMapTo();
                        personPermission.getPermission().addAll(getPermissionFromMatrixRef(mapTo));
                        personPermission.getActions().addAll(getActionsFromMatrixRef(mapTo));
                        personPermission.getCreateChildTypes().addAll(getCreateChildFromMatrixRef(mapTo));
                    }
                }
            }
        }else{
            personPermission.getActions().add(action);
        }
    }

    private void setMappedCreateTypes(AccessMatrixConfig accessMatrix, DomainObjectPermission personPermission, String childType) {
        MatrixReferenceMappingConfig matrixReferenceMappingConfig = accessMatrix.getMatrixReferenceMappingConfig();
        if (matrixReferenceMappingConfig != null) {
            List<MatrixReferenceMappingPermissionConfig> permission = matrixReferenceMappingConfig.getPermission();
            if (permission != null) {
                for (MatrixReferenceMappingPermissionConfig matrixMapping : permission) {
                    if (matrixMapping.getMapFrom().equals(MatrixReferenceMappingPermissionConfig.CREATE_CHILD + ":" + childType)) {
                        String mapTo = matrixMapping.getMapTo();
                        personPermission.getPermission().addAll(getPermissionFromMatrixRef(mapTo));
                        personPermission.getActions().addAll(getActionsFromMatrixRef(mapTo));
                        personPermission.getCreateChildTypes().addAll(getCreateChildFromMatrixRef(mapTo));
                    }
                }
            }
        }
    }

    @Override
    public String getRealMatrixReferenceTypeName(String refTypeName, Id objectId) {
        // Получаем родительский типб чтоб сделать запрос с атрибутом access_object_id
        String typeName = domainObjectTypeIdCache.getName(objectId);
        String rooTypeName = configurationExplorer.getDomainObjectRootType(typeName);

        String query = "select m.name as tName from " + rooTypeName + " t " +
                "join " + refTypeName + " r on r.id = t.access_object_id " +
                "join domain_object_type_id m on m.id = r.id_type " +
                "where t.id = :id";
        final String result = masterNamedParameterJdbcTemplate.query(
                query, Collections.singletonMap("id", ((RdbmsId)objectId).getId()), (ResultSetExtractor<String>) rs -> rs.next() ? rs.getString("tName") : null);
        return result == null ? refTypeName : result;
    }
}
