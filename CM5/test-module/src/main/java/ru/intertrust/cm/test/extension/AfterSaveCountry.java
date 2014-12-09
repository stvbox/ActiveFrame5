package ru.intertrust.cm.test.extension;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import ru.intertrust.cm.core.business.api.CollectionsService;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.DomainObjectPermission;
import ru.intertrust.cm.core.business.api.dto.FieldModification;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.dto.ReferenceValue;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.dao.access.PermissionServiceDao;
import ru.intertrust.cm.core.dao.access.UserGroupGlobalCache;
import ru.intertrust.cm.core.dao.api.CurrentUserAccessor;
import ru.intertrust.cm.core.dao.api.extension.AfterSaveExtensionHandler;
import ru.intertrust.cm.core.dao.api.extension.ExtensionPoint;

@ExtensionPoint(filter="country")
public class AfterSaveCountry implements AfterSaveExtensionHandler{
    @Autowired
    private CollectionsService collectionsService;
    @Autowired
    private PermissionServiceDao permissionService; 
    @Autowired
    private CurrentUserAccessor currentUserAccessor; 
    @Autowired
    protected UserGroupGlobalCache userGroupGlobalCache;
    
    @Override
    public void onAfterSave(DomainObject domainObject, List<FieldModification> changedFields) {
        // Проверяем что объект текущий виден в этой же транзакции запросом для проверки CMFIVE-1779
        List<Value> params = new ArrayList<Value>();
        params.add(new ReferenceValue(domainObject.getId()));
        IdentifiableObjectCollection collection = collectionsService.findCollectionByQuery("select * from country where id = {0}", params);
        assert(collection.size() > 0):"Collection with new object is empty";
        
        if (!userGroupGlobalCache.isPersonSuperUser(currentUserAccessor.getCurrentUserId())){
            // Проверяем наличие прав на изменение и удаление
            DomainObjectPermission permissions = permissionService.getObjectPermission(domainObject.getId(), currentUserAccessor.getCurrentUserId());
            assert(permissions.getPermission().contains(DomainObjectPermission.Permission.Write)) : "Not write permissions";
            assert(permissions.getPermission().contains(DomainObjectPermission.Permission.Delete)) : "Not delete permissions";
        }
        
    }

}
