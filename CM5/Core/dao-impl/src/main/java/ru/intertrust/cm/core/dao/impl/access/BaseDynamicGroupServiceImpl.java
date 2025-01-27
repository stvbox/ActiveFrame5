package ru.intertrust.cm.core.dao.impl.access;

import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getSqlName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.FieldModification;
import ru.intertrust.cm.core.business.api.dto.FieldModificationImpl;
import ru.intertrust.cm.core.business.api.dto.Filter;
import ru.intertrust.cm.core.business.api.dto.GenericDomainObject;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.dto.ReferenceValue;
import ru.intertrust.cm.core.business.api.dto.StringValue;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.business.api.dto.impl.RdbmsId;
import ru.intertrust.cm.core.config.ConfigurationExplorer;
import ru.intertrust.cm.core.config.DomainObjectTypeConfig;
import ru.intertrust.cm.core.config.DynamicGroupConfig;
import ru.intertrust.cm.core.dao.access.AccessControlService;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.access.UserGroupGlobalCache;
import ru.intertrust.cm.core.dao.api.CollectionsDao;
import ru.intertrust.cm.core.dao.api.CurrentUserAccessor;
import ru.intertrust.cm.core.dao.api.DomainObjectDao;
import ru.intertrust.cm.core.dao.api.DomainObjectTypeIdCache;
import ru.intertrust.cm.core.dao.api.GlobalCacheManager;
import ru.intertrust.cm.core.dao.api.PersonManagementServiceDao;
import ru.intertrust.cm.core.dao.api.StatusDao;
import ru.intertrust.cm.core.dao.impl.doel.DoelResolver;
import ru.intertrust.cm.core.dao.impl.utils.DaoUtils;

/**
 * @author atsvetkov
 */
public class BaseDynamicGroupServiceImpl {

    @Autowired
    protected DoelResolver doelResolver;

    @Autowired
    protected DomainObjectTypeIdCache domainObjectTypeIdCache;

    @Autowired
    protected DomainObjectDao domainObjectDao;

    @Autowired
    protected StatusDao statusDao;

    @Autowired
    protected AccessControlService accessControlService;

    @Autowired
    protected ConfigurationExplorer configurationExplorer;

    @Autowired
    protected PersonManagementServiceDao personManagementService;

    @Autowired
    protected CollectionsDao collectionsService;

    @Autowired
    protected CurrentUserAccessor currentUserAccessor;
    
    @Autowired
    protected UserGroupGlobalCache userGroupGlobalCache;

    @Autowired
    private GlobalCacheManager globalCacheManager;

    private Map<String, Id> groupIds = new HashMap<String, Id>();
    
    
    public void setDoelResolver(DoelResolver doelResolver) {
        this.doelResolver = doelResolver;
        //doelResolver.setDomainObjectTypeIdCache(domainObjectTypeIdCache);
    }

    public void setDomainObjectDao(DomainObjectDao domainObjectDao) {
        this.domainObjectDao = domainObjectDao;
    }

    public void setConfigurationExplorer(ConfigurationExplorer configurationExplorer) {
        this.configurationExplorer = configurationExplorer;
    }

    public void setDomainObjectTypeIdCache(DomainObjectTypeIdCache domainObjectTypeIdCache) {
        this.domainObjectTypeIdCache = domainObjectTypeIdCache;
    }


    public void setAccessControlService(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    /**
     * Возвращает идентификатор группы пользователей по имени группы и идентификатору контекстного объекта
     * @param groupName
     *            имя динамической группы
     * @param contextObjectId
     *            идентификатор контекстного объекта
     * @return идентификатор группы пользователей
     */
    protected Id getUserGroupByGroupNameAndObjectId(String groupName, Id contextObjectId) {
        Id result = null;
        DomainObject group = personManagementService.findDynamicGroup(groupName, contextObjectId);
        if (group != null){
            result = group.getId(); 
        }
        return result;
    }

    public Id getUserGroupByGroupName(String groupName) {
        if (groupIds.containsKey(groupName)){
            return groupIds.get(groupName); 
        }
        
        AccessToken accessToken = accessControlService.createSystemAccessToken(this.getClass().getName());

        Filter filter = new Filter();
        filter.setFilter("byName");
        filter.addCriterion(0, new StringValue(groupName));

        IdentifiableObjectCollection resultCollection = collectionsService.findCollection("GroupByName",
                Collections.singletonList(filter), null, 0, 0, accessToken);

        if (resultCollection == null || resultCollection.size() == 0) {
            return null;
        }
        
        groupIds.put(groupName, resultCollection.getId(0));
        return resultCollection.getId(0);
    }

    /**
     * Возвращает строковое представление статуса доменного объекта
     * @param objectId
     *            идентификатор доменного объекта
     * @return статус доменного объекта
     */
    protected String getStatusFor(Id objectId) {
        final AccessToken accessToken = accessControlService.createSystemAccessToken(this.getClass().getName());
        if (globalCacheManager.isEnabled()) {
            final DomainObject object = domainObjectDao.find(objectId, accessToken);
            final Id statusId = object.getStatus();
            return statusId == null ? null : domainObjectDao.find(statusId, accessToken).getString("name");
        } else {
            String query = generateGetStatusForQuery(objectId);
            IdentifiableObjectCollection resultCollection = collectionsService.findCollectionByQuery(query,
                    Collections.singletonList(new ReferenceValue(objectId)), 0, 0, accessToken);

            if (resultCollection == null || resultCollection.size() == 0) {
                return null;
            }

            return resultCollection.get(0).getString("name");
        }
    }

    /**
     * Возвращает строковое представление статуса доменного объекта
     * @param domainObject
     *            доменный объект
     * @return статус доменного объекта
     */
    protected String getStatusFor(DomainObject domainObject) {
        return statusDao.getStatusNameById(domainObject.getStatus());
    }

    /**
     * Получение имени типа документа
     * @param objectId
     * @return
     */
    protected String getTypeName(Id objectId) {
        return domainObjectTypeIdCache.getName(objectId);
    }

    private String generateGetStatusForQuery(Id objectId) {
        RdbmsId id = (RdbmsId) objectId;

        //Получение типа верхнего уровня
        String parentType = configurationExplorer.getDomainObjectRootType(domainObjectTypeIdCache.getName(id.getTypeId()));
        DomainObjectTypeConfig typeConfig = configurationExplorer.getConfig(DomainObjectTypeConfig.class, parentType);        

        String tableName = getSqlName(typeConfig.getName());
        StringBuilder query = new StringBuilder();
        query.append("select s.").append(DaoUtils.wrap("name")).append(" from ").append(DaoUtils.wrap(tableName)).
                append(" o inner join ").append(DaoUtils.wrap(GenericDomainObject.STATUS_DO)).append(" s on ").
                append("s.").append(DaoUtils.wrap("id")).append(" = o.").append(DaoUtils.wrap(GenericDomainObject.STATUS_FIELD_NAME));
        query.append(" where o.").append(DaoUtils.wrap("id")).append(" = {0}");

        return query.toString();
    }

    protected Id createUserGroup(String dynamicGroupName, Id contextObjectId) {
        AccessToken accessToken = accessControlService.createSystemAccessToken("BaseDynamicGroupService");
        return domainObjectDao.save(createUserGroupDO(dynamicGroupName, contextObjectId), accessToken).getId();
    }

    protected List<DomainObject> createUserGroups(DomainObject contextObject, List<DynamicGroupConfig> configs) {
        AccessToken accessToken = accessControlService.createSystemAccessToken("BaseDynamicGroupService");
        ArrayList<DomainObject> userGroups = new ArrayList<>(configs.size());
        for (DynamicGroupConfig dynamicGroupConfig : configs) {
            if (applyFilter(contextObject, dynamicGroupConfig)){
                userGroups.add(createUserGroupDO(dynamicGroupConfig.getName(), contextObject.getId()));
            }
        }
        return domainObjectDao.save(userGroups, accessToken);
    }
    
    protected boolean applyFilter(DomainObject domainObject, DynamicGroupConfig config){
        boolean result = true;
        
        if (config.getContext().getDomainObject().getFilter() != null && !config.getContext().getDomainObject().getFilter().isEmpty()){
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(config.getContext().getDomainObject().getFilter());
    
            EvaluationContext context = new StandardEvaluationContext(domainObject);
            result = (Boolean) exp.getValue(context);
        }
        return result;
    }     

    private DomainObject createUserGroupDO(String groupName, Id contextObjectId) {
        GenericDomainObject userGroupDO = new GenericDomainObject();
        userGroupDO.setTypeName(GenericDomainObject.USER_GROUP_DOMAIN_OBJECT);
        userGroupDO.setString("group_name", groupName);
        if (contextObjectId != null) {
            userGroupDO.setReference("object_id", contextObjectId);
        }
        return userGroupDO;
    }

    protected List<FieldModification> getNewObjectModificationList(
            DomainObject domainObject) {

        final List<String> fields = domainObject.getFields();
        List<FieldModification> result = new ArrayList<>(fields.size());
        for (String fieldName : fields) {
            result.add(new FieldModificationImpl(fieldName, null, domainObject
                    .getValue(fieldName)));
        }

        return result;
    }

    protected List<FieldModification> getDeletedModificationList(
            DomainObject domainObject) {
        final List<String> fields = domainObject.getFields();
        List<FieldModification> result = new ArrayList<>(fields.size());
        for (String fieldName : fields) {
            result.add(new FieldModificationImpl(fieldName, domainObject
                    .getValue(fieldName), null));
        }

        return result;
    }

    /**
     * Преобразование списка Value в список Id
     * @param valueList
     * @return
     */
    protected List<Id> getIdList(List<Value> valueList) {
        List<Id> result = new ArrayList<>(valueList.size());
        for (Value value : valueList) {
            if (value.get() != null) {
                result.add((Id) value.get());
            }
        }
        return result;
    }

    protected List<Id> getIdListFromDomainObjectList(List<DomainObject> domainObjectList) {
        if (domainObjectList == null || domainObjectList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Id> result = new ArrayList<>(domainObjectList.size());
        if (domainObjectList != null) {
            for (DomainObject value : domainObjectList) {
                result.add(value.getId());
            }
        }
        return result;
    }

    /**
     * Добавление элементов коллекции без дублирования
     * @param targetCollection
     * @param sourceCollection
     */
    protected <T> void addAllWithoutDuplicate(Set<T> targetCollection, Collection<T> sourceCollection) {
        if (sourceCollection != null && targetCollection != null) {
            targetCollection.addAll(sourceCollection);
        }
    }


}
