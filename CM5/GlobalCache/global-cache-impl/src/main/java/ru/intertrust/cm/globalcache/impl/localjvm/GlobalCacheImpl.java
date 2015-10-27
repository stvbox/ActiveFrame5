package ru.intertrust.cm.globalcache.impl.localjvm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.intertrust.cm.core.business.api.dto.*;
import ru.intertrust.cm.core.business.api.dto.impl.RdbmsId;
import ru.intertrust.cm.core.business.api.util.ModelUtil;
import ru.intertrust.cm.core.config.*;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.access.UserSubject;
import ru.intertrust.cm.core.dao.api.DomainObjectDao;
import ru.intertrust.cm.core.dao.api.DomainObjectTypeIdCache;
import ru.intertrust.cm.core.dao.dto.CollectionTypesKey;
import ru.intertrust.cm.core.dao.dto.NamedCollectionTypesKey;
import ru.intertrust.cm.core.dao.dto.QueryCollectionTypesKey;
import ru.intertrust.cm.core.util.ObjectCloner;
import ru.intertrust.cm.globalcache.api.*;
import ru.intertrust.cm.globalcache.api.util.Size;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Denis Mitavskiy
 *         Date: 07.07.2015
 *         Time: 14:48
 */
public class GlobalCacheImpl implements GlobalCache {
    private static final Logger logger = LoggerFactory.getLogger(GlobalCacheImpl.class);
    private final Object READ_EXOTIC_VS_COMMIT_LOCK = new Object();

    @Autowired
    private ConfigurationExplorer explorer;

    @Autowired
    private DomainObjectDao domainObjectDao;

    @Autowired
    private DomainObjectTypeIdCache domainObjectTypeIdCache;

    private volatile long sizeLimit = 10 * 1024 * 1024;
    private CacheEntriesAccessSorter accessSorter;
    private Cleaner cleaner;
    private ScheduledExecutorService backgroundCleaner;
    private int backgroundCleanerDelaySeconds = 30;
    private long maxBackgroundCleanerRunTimeMillies = 100;
    private float oldRecordsRemovalFreeSpaceThreshold = 0.02f; // background cleaner will start removing old records when cache size exceeds 98% (1-0.02)
    private float spaceToFreeThreshold = 0.1f; // background cleaner will clean records until 10% of cache is free

    private Size size;
    private ObjectsTree objectsTree;
    private TypeUniqueKeyMapping uniqueKeyMapping;
    private UserObjectAccess userObjectAccess;
    private ObjectAccessDelegation objectAccessDelegation;
    private DomainObjectTypeChangeTime doTypeLastChangeTime;
    private DomainObjectTypeFullRetrieval domainObjectTypeFullRetrieval;
    private IdsByType idsByType;
    private CollectionsTree collectionsTree;

    public void activate() {
        logger.warn("========================= INITIALIZING GLOBAL CACHE =======================================");
        logger.warn("===========================================================================================");

        init();

        cleaner = new Cleaner();

        backgroundCleaner = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r) {
                    {setDaemon(true);}
                };
            }
        });
        backgroundCleaner.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                cleanInvalidEntriesAndFreeSpace();
            }
        }, backgroundCleanerDelaySeconds, backgroundCleanerDelaySeconds, TimeUnit.SECONDS);
    }

    @Override
    public void deactivate() {
        backgroundCleaner.shutdownNow();
        size = null;
        objectsTree = null;
        uniqueKeyMapping = null;
        userObjectAccess = null;
        accessSorter = null;
        objectAccessDelegation = null;
        doTypeLastChangeTime = null;
        domainObjectTypeFullRetrieval = null;
        idsByType = null;
        collectionsTree = null;
        backgroundCleaner = null;
        cleaner = null;
    }

    @Override
    public void clear() {
        init();
    }

    private void init() {
        size = new Size();
        objectsTree = new ObjectsTree(10000, 16, size);
        uniqueKeyMapping = new TypeUniqueKeyMapping(16, size);
        userObjectAccess = new UserObjectAccess(16, size);
        accessSorter = new CacheEntriesAccessSorter(10000, size);
        objectAccessDelegation = new ObjectAccessDelegation(16, size);
        final int typesQty = explorer.getConfigs(DomainObjectTypeConfig.class).size();
        doTypeLastChangeTime = new DomainObjectTypeChangeTime(typesQty);
        domainObjectTypeFullRetrieval = new DomainObjectTypeFullRetrieval(typesQty, size);
        idsByType = new IdsByType(16, typesQty * 2, size);
        collectionsTree = new CollectionsTree(10000, 16, size);
    }

    @Override
    public void setSizeLimitBytes(long bytes) {
        sizeLimit = bytes;
        logger.info("Cache size limit is set to: " + bytes + " bytes");
    }

    @Override
    public long getSizeLimitBytes() {
        return sizeLimit;
    }

    @Override
    public void notifyCreate(String transactionId, DomainObject obj, AccessToken accessToken) {
    }

    @Override
    public void notifyUpdate(String transactionId, DomainObject obj, AccessToken accessToken) {
    }

    @Override
    public void notifyDelete(String transactionId, Id id) {
    }

    @Override
    public void notifyRead(String transactionId, Id id, DomainObject obj, AccessToken accessToken) {
        // todo: if object is saved in transaction (not committed) and then someone reads LINKED domain object where this one is in results
        // todo: he should see the changes... logic:
        /*
        1) find linked in TLog, if it's there - perfect - it's returned
        2) find linked in cache. suppose they're found. real Objects should be taken from TLog first and then - from Global Cache
         */
        final UserSubject subject = getUserSubject(accessToken);
        createOrUpdateDomainObjectEntries(id, obj, subject);
    }

    @Override
    public void notifyReadByUniqueKey(String transactionId, String type, Map<String, Value> uniqueKey, DomainObject obj, long time, AccessToken accessToken) {
        final UserSubject subject = getUserSubject(accessToken);
        final UniqueKey key = new UniqueKey(uniqueKey);
        final UniqueKeyIdMapping uniqueKeyIdMapping = uniqueKeyMapping.getOrCreateUniqueKeyIdMapping(type);
        accessSorter.logAccess(new UniqueKeyAccessKey(type, key));
        synchronized (READ_EXOTIC_VS_COMMIT_LOCK) { // this lock doesn't allow to process 2 keys simultaneously
            if (obj == null) {
                if (!retrievedAfterLastCommitOfMatchingTypes(type, true, time)) {
                    return;
                }
                processNullUniqueKeyRetrieval(key, uniqueKeyIdMapping, subject);
                return;
            } else {
                processUniqueKeyRetrieval(key, uniqueKeyIdMapping, obj, subject);
            }
        }

        createOrUpdateDomainObjectEntries(obj.getId(), obj, subject);
    }

    private void processUniqueKeyRetrieval(UniqueKey key, UniqueKeyIdMapping uniqueKeyIdMapping, DomainObject obj, UserSubject subject) {
        final Id cachedId = uniqueKeyIdMapping.getId(key);
        if (cachedId != null && !cachedId.equals(obj.getId()) || uniqueKeyIdMapping.isNullValue(key)) { // most likely commit signal hasn't reached cache yet
            uniqueKeyIdMapping.clear(cachedId); // just in case
            uniqueKeyIdMapping.clearNullValue(key);
            return;
        }
        uniqueKeyIdMapping.setMapping(obj, key);
        assureCacheSizeLimit();
    }

    private void processNullUniqueKeyRetrieval(UniqueKey key, UniqueKeyIdMapping uniqueKeyIdMapping, UserSubject subject) {
        final Id cachedId = uniqueKeyIdMapping.getId(key);
        if (subject == null) {
            if (cachedId != null) { // most likely, object has been removed, but there was no signal on commit yet
                uniqueKeyIdMapping.clear(cachedId); // just in case
                uniqueKeyIdMapping.clearNullValue(key);
            } else {
                uniqueKeyIdMapping.setMapping(null, key);
            }
        } else {
            // for User there're 2 possibilities - no rights or no removal signal yet
            if (cachedId != null) {
                userObjectAccess.setAccess(subject, cachedId, false);
            }
        }
        assureCacheSizeLimit();
    }

    @Override
    public void notifyRead(String transactionId, Collection<DomainObject> objects, AccessToken accessToken) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        final UserSubject subject = getUserSubject(accessToken);
        for (DomainObject object : objects) {
            createOrUpdateDomainObjectEntries(object.getId(), object, subject);
        }
    }

    @Override
    public void notifyReadAll(String transactionId, String type, boolean exactType, Collection<DomainObject> objects, AccessToken accessToken) {
        if (objects == null) {
            objects = Collections.EMPTY_LIST;
        }
        final UserSubject subject = getUserSubject(accessToken);
        boolean setFullyRetrieved = true;
        for (DomainObject object : objects) {
            final Action action = createOrUpdateDomainObjectEntries(object.getId(), object, subject);
            if (action.clearDomainObject()) {
                setFullyRetrieved = false;
            }
        }
        if (setFullyRetrieved) {
            domainObjectTypeFullRetrieval.setTypeFullyRetrieved(type, exactType, subject, true);
        }
    }

    @Override
    public void notifyReadPossiblyNullObjects(String transactionId, Collection<Pair<Id, DomainObject>> idsAndObjects, AccessToken accessToken) {
        if (idsAndObjects == null || idsAndObjects.isEmpty()) {
            return;
        }

        final UserSubject subject = getUserSubject(accessToken);
        for (Pair<Id, DomainObject> idAndObject : idsAndObjects) {
            createOrUpdateDomainObjectEntries(idAndObject.getFirst(), idAndObject.getSecond(), subject);
        }
    }

    @Override
    public void notifyLinkedObjectsRead(String transactionId, Id id, String linkedType, String linkedField, boolean exactType,
                                        List<DomainObject> linkedObjects, long time, AccessToken accessToken) {
        String lock = linkedType.toLowerCase().intern(); // todo:
        synchronized (READ_EXOTIC_VS_COMMIT_LOCK) { // todo: this lock doesn't allow to process 2 retrievals simultaneously.
            if (!retrievedAfterLastCommitOfMatchingTypes(linkedType, exactType, time)) {
                return; // don't put anything as list has been retrieved before the last commit occured
            }
            ObjectNode node = objectsTree.getDomainObjectNode(id);
            if (node == null) {
                node = objectsTree.addDomainObjectNode(id, new ObjectNode(id, null));
            }
            final UserSubject userSubject = getUserSubject(accessToken);
            final LinkedObjectsKey key = new LinkedObjectsKey(linkedType, linkedField, exactType);
            // todo: do something if node already exists?

            final LinkedHashSet<Id> ids = new LinkedHashSet<>((int) (linkedObjects.size() / 0.75f + 1));
            boolean setLinkedObjects = true;
            for (DomainObject linkedObject : linkedObjects) {
                final Id linkedObjectId = linkedObject.getId();
                ids.add(linkedObjectId);
                final Action action = createOrUpdateDomainObjectEntries(linkedObjectId, linkedObject, userSubject);
                if (action.clearDomainObject()) {
                    setLinkedObjects = false;
                }
            }
            if (!setLinkedObjects) {
                return;
            }

            LinkedObjectsNode linkedObjectsNode = new LinkedObjectsNode(ids);
            if (userSubject == null) {
                node.setSystemLinkedObjectsNode(key, linkedObjectsNode);
            } else {
                node.setUserLinkedObjectsNode(key, linkedObjectsNode, userSubject);
            }
        }
        assureCacheSizeLimit();
    }

    @Override
    public void notifyLinkedObjectsIdsRead(String transactionId, Id id, String linkedType, String linkedField, boolean exactType, Set<Id> linkedObjectsIds, long time, AccessToken accessToken) {
        String lock = linkedType.toLowerCase().intern(); // todo:
        synchronized (READ_EXOTIC_VS_COMMIT_LOCK) {
            final UserSubject userSubject = getUserSubject(accessToken);
            if (userSubject != null) { // cache only System Access
                return;
            }
            if (!retrievedAfterLastCommitOfMatchingTypes(linkedType, exactType, time)) {
                return;
            }
            ObjectNode node = objectsTree.getDomainObjectNode(id);
            if (node == null) {
                node = objectsTree.addDomainObjectNode(id, new ObjectNode(id, null));
            }
            final LinkedObjectsKey key = new LinkedObjectsKey(linkedType, linkedField, exactType);
            LinkedObjectsNode linkedObjectsNode = new LinkedObjectsNode(linkedObjectsIds);
            node.setSystemLinkedObjectsNode(key, linkedObjectsNode);
        }
        assureCacheSizeLimit();
    }

    private Set<? extends Filter> cloneFiltersToSet(List<? extends Filter> filters) { // special case for those who inherits Filter class
        final HashSet<Filter> result = new HashSet<>(filters.size() * 2);
        for (Filter filter : filters) {
            result.add(new Filter(filter));
        }
        return ObjectCloner.getInstance().cloneObject(result);
    }

    public void notifyCollectionRead(String transactionId, String name, Set<String> domainObjectTypes, Set<String> filterNames, List<? extends Filter> filterValues,
                                     SortOrder sortOrder, int offset, int limit,
                                     IdentifiableObjectCollection collection, long time, AccessToken accessToken) {
        final CollectionTypesKey key = new NamedCollectionTypesKey(name, filterNames); // todo clone names
        final UserSubject subject = getUserSubject(accessToken);
        final Set<? extends Filter> filterValuesSet = cloneFiltersToSet(filterValues);
        final CollectionSubKey subKey = new NamedCollectionSubKey(subject, filterValuesSet, sortOrder, offset, limit); // todo clone sort order (or just clone key)
        notifyCollectionRead(key, subKey, domainObjectTypes, collection, time);
    }

    @Override
    public void notifyCollectionRead(String transactionId, String query, Set<String> domainObjectTypes, List<? extends Value> paramValues,
                                     int offset, int limit, IdentifiableObjectCollection collection, long time, AccessToken accessToken) {
        final CollectionTypesKey key = new QueryCollectionTypesKey(query);
        final UserSubject subject = getUserSubject(accessToken);
        final CollectionSubKey subKey = new QueryCollectionSubKey(subject, paramValues, offset, limit); // todo clone key
        notifyCollectionRead(key, subKey, domainObjectTypes, collection, time);
    }

    @Override
    public void notifyCommit(DomainObjectsModification modification, AccessChanges accessChanges) {
        synchronized (READ_EXOTIC_VS_COMMIT_LOCK) { // for new objects there's a chance that they may not get to linked objects list
            for (DomainObject created : modification.getCreatedDomainObjects()) {
                doTypeLastChangeTime.setLastModificationTime(created.getTypeName(), System.currentTimeMillis(), created.getModifiedDate().getTime());
            }
        }

        for (DomainObject created : modification.getCreatedDomainObjects()) {
            doTypeLastChangeTime.setLastModificationTime(created.getTypeName(), System.currentTimeMillis(), created.getModifiedDate().getTime());
            final Id id = created.getId();
            if (modification.wasSaved(id)) {
                continue; // will be processed later (during saved objects processing)
            }
            updateUniqueKeys(created);
            createOrUpdateDomainObjectEntries(id, created, null);
            updateLinkedObjectsOfParents(null, created);
        }
        for (DomainObject updated : modification.getSavedAndChangedStatusDomainObjects()) {
            doTypeLastChangeTime.setLastModificationTime(updated.getTypeName(), System.currentTimeMillis(), updated.getModifiedDate().getTime());
            final Id id = updated.getId();
            final Action updateAction = createOrUpdateDomainObjectEntries(id, updated, null);
            updateUniqueKeys(updated);
            updateLinkedObjectsOfParents(updateAction.getDomainObjectBefore(), updated);
        }
        for (DomainObject deleted : modification.getDeletedDomainObjects()) {
            final String typeName = deleted.getTypeName();
            doTypeLastChangeTime.setLastModificationTime(typeName, System.currentTimeMillis(), 0);
            cleaner.deleteObjectAndItsAccessEntires(deleted.getId(), typeName, deleted, false);
        }
        final PersonAccessChanges personAccessChanges = (PersonAccessChanges) accessChanges;
        if (personAccessChanges.getObjectsQty() == 0) {
            return;
        }
        final Set<String> objectTypesAccessChanged = accessChanges.getObjectTypesAccessChanged();
        final HashSet<String> allTypesAffected = new HashSet<>(objectTypesAccessChanged);
        for (String type : objectTypesAccessChanged) {
            Collection<String> typesAffected = explorer.getAllTypesDelegatingAccessCheckToInLowerCase(type);
            for (String typeAffected : typesAffected) {
                doTypeLastChangeTime.setLastRightsChangeTime(typeAffected, System.currentTimeMillis());
                clearUsersFullRetrieval(typeAffected);
            }
        }
        if (personAccessChanges.clearFullAccessLog()) {
            userObjectAccess.getSize().detachFromTotal();
            userObjectAccess = new UserObjectAccess(16, size);
            return;
        }
        final HashMap<Id, HashMap<Id, Boolean>> personAccessByObject = personAccessChanges.getPersonAccessByObject();
        int count = 0;
        for (Map.Entry<Id, HashMap<Id, Boolean>> accessByObject : personAccessByObject.entrySet()) {
            final Id objectId = accessByObject.getKey();
            boolean atLeastOnePersonAccessGranted = false;
            for (Map.Entry<Id, Boolean> personAccess : accessByObject.getValue().entrySet()) {
                final Boolean accessGranted = personAccess.getValue();
                // todo: update only if access already set!
                userObjectAccess.setAccess(new UserSubject((int) ((RdbmsId) personAccess.getKey()).getId()), objectId, accessGranted);
                if (++count % 100 == 0) {
                    assureCacheSizeLimit();
                }
                if (!atLeastOnePersonAccessGranted && accessGranted == Boolean.TRUE) {
                    atLeastOnePersonAccessGranted = true;
                }
            }

            if (atLeastOnePersonAccessGranted) {
                final Set<Id> objectsAccessRightsChangedFor = objectAccessDelegation.getObjectsByDelegate(objectId);
                if (objectsAccessRightsChangedFor == null || objectsAccessRightsChangedFor.isEmpty()) {
                    clearParentsLinkedObjects(objectId, ObjectNode.LinkedObjects.User);
                } else {
                    final Set<Id> objectsWithDelegateRightsChangedFor;
                    synchronized (objectsAccessRightsChangedFor) { // there's a synchronized set and we should sync on it during iteration
                        objectsWithDelegateRightsChangedFor = new HashSet<>(objectsAccessRightsChangedFor);
                    }
                    objectsWithDelegateRightsChangedFor.add(objectId);
                    clearParentsLinkedObjects(objectsWithDelegateRightsChangedFor, ObjectNode.LinkedObjects.User);
                }
            }
        }
    }

    private static int __count;
    @Override
    public DomainObject getDomainObject(String transactionId, Id id, AccessToken accessToken) {
        if (++__count % 1000 == 0) {
            logger.warn("------------------------------------------------- Cache size: " + new DecimalFormat("##########################0.00").format(((double) size.get()) / 1024 / 1024) + " MB");
        }
        return getClonedDomainObject(id, getUserSubject(accessToken));
    }

    private DomainObject getClonedDomainObject(Id id, UserSubject userSubject) {
        return getClone(getNotClonedDomainObject(id, userSubject));
    }

    private DomainObject getNotClonedDomainObject(Id id, UserSubject userSubject) {
        DomainObject result;
        final ObjectNode cachedNode = objectsTree.getDomainObjectNode(id);
        if (cachedNode == null) {
            result = null;
        } else if (userSubject == null) {
            result = cachedNode.getDomainObject();
        } else {
            result = cachedNode.getDomainObject();
            if (GenericDomainObject.isAbsent(result)) {
                result = AbsentDomainObject.INSTANCE;
            } else if (result != null) {
                final Id accessObjectId = result.getAccessObjectId();
                accessSorter.logAccess(accessObjectId);

                final Boolean accessGranted = userObjectAccess.isAccessGranted(userSubject, accessObjectId);
                if (accessGranted == Boolean.FALSE) {
                    result = AbsentDomainObject.INSTANCE;
                } else if (accessGranted == null) {
                    result = null; // it can be null if not retrieved yet
                }
            }
        }
        if (result != null) {
            accessSorter.logAccess(id);
        }
        return result;
    }

    @Override
    public DomainObject getDomainObject(String transactionId, String type, Map<String, Value> uniqueKey, AccessToken accessToken) {
        final UniqueKeyIdMapping uniqueKeyIdMapping = uniqueKeyMapping.getUniqueKeyIdMapping(type);
        if (uniqueKeyIdMapping == null) {
            return null;
        }
        final UniqueKey key = new UniqueKey(uniqueKey);
        final Id id = uniqueKeyIdMapping.getId(key);
        if (id == null) {
            if (uniqueKeyIdMapping.isNullValue(key)) {
                accessSorter.logAccess(new UniqueKeyAccessKey(type, key));
                return AbsentDomainObject.INSTANCE;
            } else {
                return null;
            }
        } else {
            accessSorter.logAccess(new UniqueKeyAccessKey(type, key));
            return getDomainObject(transactionId, id, accessToken);
        }
    }

    public ArrayList<DomainObject> getDomainObjects(String transactionId, Collection<Id> ids, AccessToken accessToken) {
        ArrayList<DomainObject> result = new ArrayList<>(ids.size());
        for (Id id : ids) {
            result.add(getDomainObject(transactionId, id, accessToken));
        }
        return result;
    }

    @Override
    public List<DomainObject> getLinkedDomainObjects(String transactionId, Id domainObjectId, String linkedType,
                                                          String linkedField, boolean exactType, AccessToken accessToken) {
        final List<Id> ids = getLinkedDomainObjectsIds(transactionId, domainObjectId, linkedType, linkedField, exactType, accessToken);
        if (ids == null || ids.isEmpty()) {
            return (List) ids;
        }
        final UserSubject userSubject = getUserSubject(accessToken);
        final ArrayList<DomainObject> result = new ArrayList<>(ids.size());
        for (Id id : ids) {
            final DomainObject domainObject = getNotClonedDomainObject(id, userSubject);
            if (domainObject == null) { // object is absent for some reasons - for example, only linked ids has been retrieved, or it has been swept out
                return null;
            }
            if (!GenericDomainObject.isAbsent(domainObject)) {
                result.add(domainObject);
            }
        }
        for (int i = 0; i < result.size(); ++i) {
            result.set(i, getClone(result.get(i)));
        }
        return result;
    }

    public List<Id> getLinkedDomainObjectsIds(String transactionId, Id domainObjectId, String linkedType,
                                                     String linkedField, boolean exactType, AccessToken accessToken) {
        final ObjectNode node = objectsTree.getDomainObjectNode(domainObjectId);
        if (node == null) {
            return null;
        }
        final UserSubject userSubject = getUserSubject(accessToken);
        final LinkedObjectsKey key = new LinkedObjectsKey(linkedType, linkedField, exactType);
        final LinkedObjectsNode linkedObjectsNode = node.getLinkedObjectsNode(key, userSubject);
        if (linkedObjectsNode == null) {
            return null;
        }
        return linkedObjectsNode.getIds();
    }

    @Override
    public List<DomainObject> getAllDomainObjects(String transactionId, String type, boolean exactType, AccessToken accessToken) {
        final UserSubject subject = getUserSubject(accessToken);
        if (!domainObjectTypeFullRetrieval.isTypeFullyRetrieved(type, exactType, subject)) {
            return null;
        }
        ArrayList<ConcurrentMap<Id, Id>> idMaps = new ArrayList<>();
        ConcurrentMap<Id, Id> exactTypeIds = idsByType.getIds(type);
        int totalSize = 0;
        if (exactTypeIds != null) {
            idMaps.add(exactTypeIds);
            totalSize = exactTypeIds.size();
        }
        Collection<DomainObjectTypeConfig> children = explorer.findChildDomainObjectTypes(type, true);
        for (DomainObjectTypeConfig child : children) {
            exactTypeIds = idsByType.getIds(child.getName());
            if (exactTypeIds != null) {
                idMaps.add(exactTypeIds);
                totalSize += exactTypeIds.size();
            }
        }
        ArrayList<DomainObject> result = new ArrayList<>(totalSize);
        for (ConcurrentMap<Id, Id> idMap : idMaps) {
            for (Id id : idMap.keySet()) {
                final DomainObject domainObject = getClonedDomainObject(id, subject);
                if (domainObject != null && !GenericDomainObject.isAbsent(domainObject)) {
                    result.add(domainObject);
                }
            }
        }
        if (result.isEmpty()) {
            return result;
        }
        Collections.sort(result, new Comparator<DomainObject>() {
            @Override
            public int compare(DomainObject o1, DomainObject o2) {
                return o1.getId().toStringRepresentation().compareTo(o2.getId().toStringRepresentation());
            }
        });
        return result;
    }

    @Override
    public IdentifiableObjectCollection getCollection(String transactionId, String name, List<? extends Filter> filterValues,
                                                      SortOrder sortOrder, int offset, int limit, AccessToken accessToken) {
        final CollectionTypesKey key = new SizeableNamedCollectionTypesKey(name, ModelUtil.getFilterNames(filterValues));
        final UserSubject subject = getUserSubject(accessToken);
        sortOrder = ObjectCloner.getInstance().cloneObject(sortOrder);
        final Set<? extends Filter> filterValuesSet = filterValues == null ? null : cloneFiltersToSet(filterValues);
        final CollectionSubKey subKey = new SizeableNamedCollectionSubKey(subject, filterValuesSet, sortOrder, offset, limit);
        return getCollection(key, subKey);
    }

    @Override
    public IdentifiableObjectCollection getCollection(String transactionId, String query, List<? extends Value> paramValues,
                                                      int offset, int limit, AccessToken accessToken) {
        final CollectionTypesKey key = new SizeableQueryCollectionTypesKey(query);
        final UserSubject subject = getUserSubject(accessToken);
        paramValues = paramValues == null ? null : ObjectCloner.getInstance().cloneObject(paramValues);
        final CollectionSubKey subKey = new SizeableQueryCollectionSubKey(subject, paramValues, offset, limit);
        return getCollection(key, subKey);
    }

    public float getFreeSpacePercentage() {
        final long size = this.size.get();
        final long limit = sizeLimit;
        if (size >= limit) {
            return 0.0f;
        }
        return 1.0f - size / (float) limit;
    }

    protected void assureCacheSizeLimit() {
        cleaner.clean();
    }

    public void cleanInvalidEntriesAndFreeSpace() {
        final long startTime = System.currentTimeMillis();
        try {
            freeSpace(startTime);
            cleanInvalidEntries(startTime);
        } catch (Throwable e) {
            logger.error("Exception cleaning invalid entries", e);
        }
    }

    protected void freeSpace(final long startTime) {
        if (getFreeSpacePercentage() > oldRecordsRemovalFreeSpaceThreshold) {
            return;
        }
        for (int i = 0; ; ++i) {
            if (i % 10 == 0) {
                if (System.currentTimeMillis() - startTime > maxBackgroundCleanerRunTimeMillies || getFreeSpacePercentage() > spaceToFreeThreshold) {
                    return;
                }
            }
            deleteEldestEntry();
        }
    }

    protected void cleanInvalidEntries(final long startTime) {
        int counter = 0;
        final Set<Map.Entry<CollectionTypesKey, CollectionBaseNode>> entries = collectionsTree.getEntries();
        for (Map.Entry<CollectionTypesKey, CollectionBaseNode> entry : entries) {
            final CollectionBaseNode baseNode = entry.getValue();
            final Set<String> collectionTypes = baseNode.getCollectionTypes();
            if (collectionTypes == null) {
                continue;
            }
            long minSystemValidTime = getMaxSavedTimeOfTypes(collectionTypes);
            long minUserValidTime = getMaxModifiedTimeOfTypes(collectionTypes);
            final Set<Map.Entry<CollectionSubKey, CollectionNode>> allCollectionNodes = baseNode.getAllCollectionNodes();
            for (Map.Entry<CollectionSubKey, CollectionNode> node : allCollectionNodes) {
                if (++counter % 100 == 0 && System.currentTimeMillis() - startTime > maxBackgroundCleanerRunTimeMillies) {
                    return;
                }
                final CollectionSubKey subKey = node.getKey();
                final CollectionNode collectionNode = node.getValue();
                final long timeRetrieved = collectionNode.getTimeRetrieved();
                final boolean invalid = subKey.subject == null ? timeRetrieved < minSystemValidTime : timeRetrieved < minUserValidTime;
                if (invalid) {
                    baseNode.removeCollectionNode(subKey);
                }
            }
        }
    }

    /**
     * Only this operation is dangerous during cache cleaning. It's moved to a separate method on purpose - in order to be able to
     * synchronize this operation only, without locking the whole cache for the time of cleaning operation
     */
    protected void deleteEldestEntry() {
        cleaner.deleteEldest();
    }

    private boolean retrievedAfterLastCommitOfMatchingTypes(String type, boolean exactType, long retrieveTime) {
        if (typeSavedAfterOrSameTime(type, retrieveTime)) {
            return false;
        }
        if (exactType) { // checked earlier
            return true;
        }
        final Collection<DomainObjectTypeConfig> children = explorer.findChildDomainObjectTypes(type, true);
        for (DomainObjectTypeConfig child : children) {
            if (typeSavedAfterOrSameTime(child.getName(), retrieveTime)) {
                return false;
            }
        }
        return true;
    }

    private void clearUsersFullRetrieval(String type) {
        domainObjectTypeFullRetrieval.clearUsersTypeStatus(type, null);
        final String[] typeParents = explorer.getDomainObjectTypesHierarchy(type);
        if (typeParents != null) {
            for (String parent : typeParents) {
                domainObjectTypeFullRetrieval.clearUsersTypeStatus(type, false);
            }
        }
    }

    private void clearFullRetrieval(String type, UserSubject userSubject) {
        if (userSubject == null) {
            domainObjectTypeFullRetrieval.clearTypeStatus(type);
        } else {
            domainObjectTypeFullRetrieval.clearTypeStatusForUser(userSubject, type);
        }
        final String[] typeParents = explorer.getDomainObjectTypesHierarchy(type);
        if (typeParents != null) {
            if (userSubject == null) {
                for (String parent : typeParents) {
                    domainObjectTypeFullRetrieval.clearTypeStatus(parent, false);
                }
            } else {
                for (String parent : typeParents) {
                    domainObjectTypeFullRetrieval.clearTypeStatusForUser(userSubject, parent, false);
                }
            }
        }
    }

    private void clearParentsLinkedObjects(Collection<Id> ids, ObjectNode.LinkedObjects linkedObjects) {
        for (Id id : ids) {
            clearParentsLinkedObjects(id, linkedObjects);
        }
    }

    private void clearParentsLinkedObjects(Id id, ObjectNode.LinkedObjects linkedObjects) {
        final ObjectNode node = objectsTree.getDomainObjectNode(id);
        if (node == null) {
            return;
        }
        final DomainObject object = node.getDomainObject();
        if (object == null) { // it doesn't participate in any linked objects list
            return;
        }

        final String objectType = object.getTypeName();
        final ArrayList<ReferenceFieldConfig> referenceFieldConfigs = getReferenceFieldConfigs(objectType); // todo change to explorer.getDomainObjectReferenceFields after implementation
        for (ReferenceFieldConfig referenceFieldConfig : referenceFieldConfigs) {
            final String fieldName = referenceFieldConfig.getName();
            final Id parentObjectId = object.getReference(fieldName);
            if (parentObjectId == null) {
                continue;
            }
            final ObjectNode parentNode = objectsTree.getDomainObjectNode(parentObjectId);
            if (parentNode == null) { // don't worry - parent has never been retrieved
                continue;
            }
            final String[] typeParents = explorer.getDomainObjectTypesHierarchy(objectType);
            if (typeParents != null) {
                for (String parent : typeParents) {
                    parentNode.clearLinkedObjects(new LinkedObjectsKey(parent, fieldName, false), linkedObjects);
                }
            }
            parentNode.clearLinkedObjects(new LinkedObjectsKey(objectType, fieldName, false), linkedObjects);
            parentNode.clearLinkedObjects(new LinkedObjectsKey(objectType, fieldName, true), linkedObjects);
        }
    }

    /**
     * This method should not be call on read - only on commit
     * @param object
     */
    private void updateLinkedObjectsOfParents(DomainObject previousState, DomainObject object) {
        if (object == null && previousState == null) {
            return;
        }
        final String objectType = object == null ? previousState.getTypeName() : object.getTypeName();
        final ArrayList<ReferenceFieldConfig> referenceFieldConfigs = getReferenceFieldConfigs(objectType); // todo change to explorer.getDomainObjectReferenceFields after implementation
        final String[] typeParents = explorer.getDomainObjectTypesHierarchy(objectType);

        if (previousState == null) { // just created object
            for (ReferenceFieldConfig referenceFieldConfig : referenceFieldConfigs) {
                final String fieldName = referenceFieldConfig.getName();
                final Id parentObjectId = object.getReference(fieldName);
                addLinkedObject(parentObjectId, object.getId(), objectType, fieldName, typeParents);
            }
            assureCacheSizeLimit();
        } else if (object == null) { // has been deleted
            for (ReferenceFieldConfig referenceFieldConfig : referenceFieldConfigs) {
                final String fieldName = referenceFieldConfig.getName();
                final Id previousParentObjectId = previousState.getReference(fieldName);
                removeLinkedObject(previousParentObjectId, previousState.getId(), objectType, fieldName, typeParents);
            }
            // ATTENTION! don't clean here - it's not necessary and moreover will cause infinite recursion
        } else {
            final Id id = object.getId();
            for (ReferenceFieldConfig referenceFieldConfig : referenceFieldConfigs) {
                final String fieldName = referenceFieldConfig.getName();
                final Id parentObjectId = object.getReference(fieldName);
                final Id previousParentObjectId = previousState.getReference(fieldName);
                if (Objects.equals(previousParentObjectId, parentObjectId)) {
                    continue;
                }
                // remove linked objects from the previous parent, add objects to the new
                removeLinkedObject(previousParentObjectId, id, objectType, fieldName, typeParents);
                addLinkedObject(parentObjectId, id, objectType, fieldName, typeParents);
            }
            assureCacheSizeLimit();
        }
    }

    private void addLinkedObject(Id parentId, Id id, String objectType, String fieldName, String[] typeParents) {
        if (parentId == null) {
            return;
        }
        final ObjectNode parentNode = objectsTree.getDomainObjectNode(parentId);
        if (parentNode == null) {
            return;
        }
        if (typeParents != null) {
            for (String parent : typeParents) {
                final LinkedObjectsKey linkedObjectsKey = new LinkedObjectsKey(parent, fieldName, false);
                if (parentNode != null) {
                    addIdToLinkedObjectsNode(id, parentNode, linkedObjectsKey);
                }
            }
        }
        if (parentNode != null) {
            addIdToLinkedObjectsNode(id, parentNode, new LinkedObjectsKey(objectType, fieldName, false));
            addIdToLinkedObjectsNode(id, parentNode, new LinkedObjectsKey(objectType, fieldName, true));
        }
    }

    private void removeLinkedObject(Id parentId, Id id, String objectType, String fieldName, String[] typeParents) {
        if (parentId == null) {
            return;
        }
        final ObjectNode parentNode = objectsTree.getDomainObjectNode(parentId);
        if (parentNode == null) {
            return;
        }
        if (typeParents != null) {
            for (String parent : typeParents) {
                final LinkedObjectsKey linkedObjectsKey = new LinkedObjectsKey(parent, fieldName, false);
                if (parentNode != null) {
                    removeIdFromLinkedObjectsNode(id, parentNode, linkedObjectsKey);
                }
            }
        }
        if (parentNode != null) {
            removeIdFromLinkedObjectsNode(id, parentNode, new LinkedObjectsKey(objectType, fieldName, false));
            removeIdFromLinkedObjectsNode(id, parentNode, new LinkedObjectsKey(objectType, fieldName, true));
        }
    }

    private void addIdToLinkedObjectsNode(final Id id, final ObjectNode node, final LinkedObjectsKey linkedObjectsKey) {
        final LinkedObjectsNode linkedObjectsNode = node.getLinkedObjectsNode(linkedObjectsKey, null);
        if (linkedObjectsNode != null) {
            linkedObjectsNode.add(id);
        }
        node.clearLinkedObjects(linkedObjectsKey, ObjectNode.LinkedObjects.User);
    }

    private void removeIdFromLinkedObjectsNode(final Id id, final ObjectNode node, final LinkedObjectsKey linkedObjectsKey) {
        final LinkedObjectsNode linkedObjectsNode = node.getLinkedObjectsNode(linkedObjectsKey, null);
        if (linkedObjectsNode != null) {
            linkedObjectsNode.remove(id);
        }
        node.clearLinkedObjects(linkedObjectsKey, ObjectNode.LinkedObjects.User);
    }

    private ArrayList<ReferenceFieldConfig> getReferenceFieldConfigs(String objectType) {
        final DomainObjectTypeConfig config = explorer.getDomainObjectTypeConfig(objectType);
        final List<FieldConfig> fieldConfigs = config.getFieldConfigs();
        final ArrayList<ReferenceFieldConfig> referenceFieldConfigs = new ArrayList<>();
        for (FieldConfig fieldConfig : fieldConfigs) {
            if (fieldConfig.getClass() == ReferenceFieldConfig.class) {
                referenceFieldConfigs.add((ReferenceFieldConfig) fieldConfig);
            }
        }
        return referenceFieldConfigs;
    }

    protected DomainObject getClone(DomainObject object) {
        return object == null || GenericDomainObject.isAbsent(object) ? object : ObjectCloner.getInstance().cloneObject(object);
    }

    private Action createOrUpdateDomainObjectEntries(Id id, DomainObject obj, UserSubject subject) {
        synchronized (id) { // todo fix
            accessSorter.logAccess(id);
            final ObjectNode cachedNode = objectsTree.getDomainObjectNode(id);
            final Action action = Action.getAction(cachedNode, obj, subject);

            // todo: ugly code - rework
            if (action.isNodeInitializedWithRealObject()) {
                final Id accessObjectId = getAccessObjectId(id, action.domainObject);
                objectAccessDelegation.setDelegation(id, accessObjectId);
                if (accessObjectId != id) {
                    accessSorter.logAccess(accessObjectId);
                }
            }
            if (action instanceof CreateEntry) {
                final ObjectNode node = objectsTree.addDomainObjectNode(id, createDomainObjectNode(id, action.domainObject));
                if (action.rights() == CreateEntry.Rights.Set) {
                    setAccess(id, action.getObjectToFindAccessObjectBy(), subject, action.userRights);
                }
                assureCacheSizeLimit();
                return action;
            }

            final UpdateEntry.NodeDomainObject updateAction = ((UpdateEntry) action).shouldUpdateDomainObject();
            if (updateAction == UpdateEntry.NodeDomainObject.Set) {
                updateDomainObjectNode(id, action.domainObject, cachedNode);
            } else if (updateAction == UpdateEntry.NodeDomainObject.Clear) {
                clearDomainObjectNode(id, cachedNode);
            }
            final Action.Rights rightsAction = action.rights();
            if (rightsAction == UpdateEntry.Rights.Set) {
                setAccess(id, action.getObjectToFindAccessObjectBy(), subject, action.userRights);
            } else if (rightsAction == UpdateEntry.Rights.Clear) {
                clearAccess(id, action.getObjectToFindAccessObjectBy(), subject);
            }
            assureCacheSizeLimit();
            return action;
        }
    }

    private DomainObject registerAndCloneDomainObject(Id id, DomainObject domainObject) {
        if (domainObject != null && !GenericDomainObject.isAbsent(domainObject)) {
            idsByType.setIdType(domainObject.getId(), domainObject.getTypeName());
        }
        return getClone(domainObject);
    }

    private ObjectNode createDomainObjectNode(Id id, DomainObject domainObject) {
        return new ObjectNode(id, registerAndCloneDomainObject(id, domainObject));
    }

    private void updateDomainObjectNode(Id id, DomainObject domainObject, ObjectNode cachedNode) {
        cachedNode.setDomainObject(registerAndCloneDomainObject(id, domainObject));
    }

    private void clearDomainObjectNode(Id id, ObjectNode cachedNode) {
        clearParentsLinkedObjects(Collections.singletonList(id), ObjectNode.LinkedObjects.All);
        clearFullRetrieval(domainObjectTypeIdCache.getName(id), null);
        cachedNode.setDomainObject(null);
    }

    private void updateUniqueKeys(DomainObject object) {
        // todo: think about synchronization with commit... key is committed and after that a signal that it's empty appears...
        final UniqueKeyIdMapping uniqueKeyIdMapping = uniqueKeyMapping.getUniqueKeyIdMapping(object.getTypeName());
        if (uniqueKeyIdMapping == null) {
            return;
        }
        uniqueKeyIdMapping.updateMappings(object, getUniqueKeys(object));
        assureCacheSizeLimit();
    }

    private List<UniqueKey> getUniqueKeys(DomainObject object) {
        final DomainObjectTypeConfig typeConfig = explorer.getDomainObjectTypeConfig(object.getTypeName());
        final List<UniqueKeyConfig> uniqueKeyConfigs = typeConfig.getUniqueKeyConfigs();
        if (uniqueKeyConfigs == null || uniqueKeyConfigs.isEmpty()) {
            return null;
        }
        ArrayList<UniqueKey> uniqueKeys = new ArrayList<>(uniqueKeyConfigs.size());
        for (UniqueKeyConfig uniqueKeyConfig : uniqueKeyConfigs) {
            final List<UniqueKeyFieldConfig> fields = uniqueKeyConfig.getUniqueKeyFieldConfigs();
            final HashMap<String, Value> valueMap = new HashMap<>((int) (fields.size() / 0.75f + 1));
            for (UniqueKeyFieldConfig field : fields) {
                valueMap.put(field.getName(), object.getValue(field.getName()));
            }
            uniqueKeys.add(new UniqueKey(valueMap));
        }
        return uniqueKeys;
    }

    private void setAccess(Id id, DomainObject obj, UserSubject subject, boolean accessGranted) {
        final Id accessObjectId = getAccessObjectId(id, obj);
        if (accessObjectId != null) {
            userObjectAccess.setAccess(subject, accessObjectId, accessGranted);
        }
    }

    private void clearAccess(Id id, DomainObject obj, UserSubject subject) {
        final Id accessObjectId = getAccessObjectId(id, obj);
        if (accessObjectId != null) {
            userObjectAccess.clearAccess(subject, accessObjectId);
        }
        clearFullRetrieval(domainObjectTypeIdCache.getName(id), subject);
    }

    private UserSubject getUserSubject(AccessToken accessToken) {
        UserSubject subject;
        if (accessToken.getAccessLimitationType() == AccessToken.AccessLimitationType.UNLIMITED) {
            subject = null;
        } else {
            subject = (UserSubject) accessToken.getSubject();
        }
        return subject;
    }

    private Id getAccessObjectId(Id id, DomainObject obj) {
        if (obj == null) { // when object is unknown it's possible to find access ID only in case it's the same - when matrix reference isn't defined
            final String objectType = domainObjectTypeIdCache.getName(id);
            final String accessObjectType = explorer.getMatrixReferenceTypeName(objectType);
            final Id accessObjectId;
            if (accessObjectType == null) {
                accessObjectId = id;
            } else {
                accessObjectId = null; // matrix reference is defined, so access id we don't know
            }
            return accessObjectId;
        } else {
            final Id accessObjectId = obj.getAccessObjectId();
            if (accessObjectId == null) {
                logger.error("Access object is null for object with ID: " + obj.getId());
            }
            return accessObjectId;
        }
    }

    private void notifyCollectionRead(CollectionTypesKey key, CollectionSubKey subKey, Set<String> domainObjectTypes,
                                     IdentifiableObjectCollection collection, long time) {
        CollectionBaseNode baseNode = collectionsTree.getBaseNode(key);
        if (baseNode == null) {
            baseNode = new CollectionBaseNode(domainObjectTypes == null ? Collections.EMPTY_SET : domainObjectTypes);
            baseNode = collectionsTree.addBaseNode(key, baseNode);
        }
        synchronized (subKey) { // todo fix
            CollectionNode collectionNode = new CollectionNode(ObjectCloner.getInstance().cloneObject(collection), time);
            baseNode.setCollectionNode(subKey, collectionNode);
        }
        accessSorter.logAccess(new CollectionAccessKey(key, subKey));
        assureCacheSizeLimit();
    }

    private IdentifiableObjectCollection getCollection(CollectionTypesKey key, CollectionSubKey subKey) {
        final CollectionBaseNode baseNode = collectionsTree.getBaseNode(key);
        if (baseNode == null) {
            return null;
        }
        final CollectionNode collectionNode = baseNode.getCollectionNode(subKey);
        if (collectionNode == null) {
            return null;
        }
        final long timeRetrieved = collectionNode.getTimeRetrieved();
        final Set<String> collectionTypes = baseNode.getCollectionTypes();
        if (collectionTypes != null) {
            for (String type : collectionTypes) {
                // in case of user access, rights changes should be taken into account
                final boolean invalidNode = subKey.subject == null ? typeSavedAfterOrSameTime(type, timeRetrieved) : typeChangedAfterOrSameTime(type, timeRetrieved);
                if (invalidNode) {
                    baseNode.removeCollectionNode(subKey);
                    return null;
                }
            }
        }
        final IdentifiableObjectCollection collection = collectionNode.getCollection();
        accessSorter.logAccess(new CollectionAccessKey(key, subKey));
        return ObjectCloner.getInstance().cloneObject(collection);
    }

    private boolean typeSavedAfterOrSameTime(String type, long time) {
        final ModificationTime lastModificationTime = doTypeLastChangeTime.getLastModificationTime(type);
        return lastModificationTime != null && lastModificationTime.lastSaveAfterOrEqual(time);
    }

    private boolean typeChangedAfterOrSameTime(String type, long time) {
        final ModificationTime lastModificationTime = doTypeLastChangeTime.getLastModificationTime(type);
        return lastModificationTime != null && lastModificationTime.lastChangeAfterOrEqual(time);
    }

    private long getMaxSavedTimeOfTypes(Set<String> types) {
        long maxSaveTime = 0;
        for (String type : types) {
            final ModificationTime lastModificationTime = doTypeLastChangeTime.getLastModificationTime(type);
            if (lastModificationTime == null) {
                continue;
            }
            final long saveTime = lastModificationTime.getSaveTime();
            if (saveTime > maxSaveTime) {
                maxSaveTime = saveTime;
            }
        }
        return maxSaveTime;
    }

    private long getMaxModifiedTimeOfTypes(Set<String> types) {
        long maxChangedTime = 0;
        for (String type : types) {
            final ModificationTime lastModificationTime = doTypeLastChangeTime.getLastModificationTime(type);
            if (lastModificationTime == null) {
                continue;
            }
            final long changeTime = lastModificationTime.getModificationTime();
            if (changeTime > maxChangedTime) {
                maxChangedTime = changeTime;
            }
        }
        return maxChangedTime;
    }

    private class Cleaner {
        private static final int CLEAN_ATTEMPTS = 100;

        public void checkSize() {
            logger.warn("Cache size: " + size.get());
            if (size.get() > sizeLimit) {
                logger.warn("OVER LIMIT: " + size.get());
            }
        }

        public void clean() {
            if (size.get() < sizeLimit) {
                return;
            }
            for (int i = 0; i < CLEAN_ATTEMPTS; ++i) {
                deleteEldest();
                if (size.get() < sizeLimit) {
                    return;
                }
            }
            logger.warn("After " + CLEAN_ATTEMPTS + " attempts size: " + size + " is still larger than limit: " + sizeLimit / Size.BYTES_IN_MEGABYTE + " MB");
        }

        public void deleteEldest() {
            final Object eldest = accessSorter.getEldest();
            if (eldest == null) {
                logger.warn("Access log is empty...");
                return;
            }
            if (eldest instanceof Id) {
                deleteDomainObject((Id) eldest);
            } else if (eldest instanceof CollectionAccessKey) {
                deleteCollection((CollectionAccessKey) eldest);
            } else {
                deleteUniqueKey(((UniqueKeyAccessKey) eldest));
            }
        }

        private void deleteDomainObject(Id id) {
            deleteObjectAndItsAccessEntires(id, null, null, true);
        }

        private void deleteCollection(CollectionAccessKey collectionAccessKey) {
            final CollectionBaseNode baseNode = collectionsTree.getBaseNode(collectionAccessKey.key);
            if (baseNode != null) {
                baseNode.removeCollectionNode(collectionAccessKey.subKey);
            }
            accessSorter.remove(collectionAccessKey);
        }

        /**
         * Deletes cache entries related to specific object
         * @param id ID of the object to remove
         * @param typeName Object Type Name, optional; if empty - resolved automatically
         * @param domainObject optional parameter, it's possible that domain object is absent (unknown)
         */
        private void deleteObjectAndItsAccessEntires(Id id, String typeName, DomainObject domainObject, boolean isCacheCleaning) {
            final ObjectNode cachedNode = objectsTree.deleteDomainObjectNode(id);
            if (typeName == null || domainObject == null) {
                if (cachedNode != null) {
                    domainObject = cachedNode.getRealDomainObjectOrNothing();
                }
                if (typeName == null) {
                    typeName = domainObject != null ? domainObject.getTypeName() : domainObjectTypeIdCache.getName(id);
                }
            }
            // if object is a delegate access rights for all dependent objects are cleared
            userObjectAccess.clearAccess(objectAccessDelegation.getObjectsByDelegate(id));
            // access is cleared for object itself, NOT for access object, as access object clearing would remove access for all objects referencing it
            userObjectAccess.clearAccess(id); // todo: this can be done in a separate thread
            objectAccessDelegation.removeId(id);
            deleteUniqueKeys(typeName, id);
            if (domainObject != null) {
                if (isCacheCleaning) {
                    clearParentsLinkedObjects(id, ObjectNode.LinkedObjects.All);
                } else {
                    updateLinkedObjectsOfParents(domainObject, null);
                }
            }
            if (isCacheCleaning) {
                clearFullRetrieval(typeName, null);
            }
            idsByType.removeId(id, typeName);
            accessSorter.remove(id);
        }

        private void deleteUniqueKey(UniqueKeyAccessKey uniqueKeyAccessKey) {
            final UniqueKeyIdMapping uniqueKeyIdMapping = uniqueKeyMapping.getUniqueKeyIdMapping(uniqueKeyAccessKey.typeName);
            if (uniqueKeyIdMapping != null) {
                uniqueKeyIdMapping.clear(uniqueKeyAccessKey.uniqueKey);
            }
            accessSorter.remove(uniqueKeyAccessKey);
        }

        private void deleteUniqueKeys(String typeName, Id id) {
            final UniqueKeyIdMapping uniqueKeyIdMapping = uniqueKeyMapping.getUniqueKeyIdMapping(typeName);
            if (uniqueKeyIdMapping == null) {
                return;
            }
            uniqueKeyIdMapping.clear(id);
        }
    }

    private static class CollectionAccessKey {
        public final CollectionTypesKey key;
        public final CollectionSubKey subKey;

        public CollectionAccessKey(CollectionTypesKey key, CollectionSubKey subKey) {
            this.key = key;
            this.subKey = subKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CollectionAccessKey that = (CollectionAccessKey) o;

            if (!key.equals(that.key)) return false;
            if (!subKey.equals(that.subKey)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + subKey.hashCode();
            return result;
        }
    }

    private static class UniqueKeyAccessKey {
        public final String typeName;
        public final UniqueKey uniqueKey;

        public UniqueKeyAccessKey(String typeName, UniqueKey uniqueKey) {
            this.typeName = typeName;
            this.uniqueKey = uniqueKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UniqueKeyAccessKey that = (UniqueKeyAccessKey) o;

            if (!typeName.equalsIgnoreCase(that.typeName)) return false;
            if (!uniqueKey.equals(that.uniqueKey)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = typeName.toLowerCase().hashCode();
            result = 31 * result + uniqueKey.hashCode();
            return result;
        }
    }

    private static abstract class Action {
        public enum Rights {
            Clear,
            Set,
            Nothing;
        }

        protected DomainObject domainObject;
        protected Boolean userRights;
        protected UserSubject user;

        public static Action getAction(final ObjectNode node, DomainObject domainObject, UserSubject subject) {
            Action action = node == null ? new CreateEntry() : new UpdateEntry(node.getDomainObject());
            action.init(domainObject, subject);
            return action;
        }

        public void init(DomainObject domainObject, UserSubject subject) {
            user = subject;
            if (domainObject != null) {
                this.domainObject = domainObject;
                userRights = subject == null ? null : Boolean.TRUE;
            } else {
                if (subject == null) { // null-object and system access means object doesn't exist
                    this.domainObject = AbsentDomainObject.INSTANCE; // todo: no need to support objects that do not exist!
                    userRights = null; // will be cleared
                } else { // we don't know if it exists, but we know user doesn't have rights to it
                    this.domainObject = null;
                    userRights = Boolean.FALSE;
                }
            }
        }

        public abstract boolean clearDomainObject();
        public abstract DomainObject getDomainObjectBefore();
        public abstract Rights rights();
        public abstract DomainObject getObjectToFindAccessObjectBy();
        public abstract boolean isNodeInitializedWithRealObject();
    }

    private static class CreateEntry extends Action {
        @Override
        public DomainObject getDomainObjectBefore() {
            return null;
        }

        public Rights rights() {
            if (user == null || GenericDomainObject.isAbsent(domainObject)) {
                return Rights.Nothing;
            } else {
                return Rights.Set;
            }
        }

        public boolean clearDomainObject() {
            return false;
        }

        public DomainObject getObjectToFindAccessObjectBy() {
            return GenericDomainObject.isAbsent(domainObject) ? null : domainObject;
        }

        public boolean isNodeInitializedWithRealObject() {
            return domainObject != null && !GenericDomainObject.isAbsent(domainObject);
        }
    }

    private static class UpdateEntry extends Action {
        private final DomainObject cachedDomainObject;

        public UpdateEntry(DomainObject cachedDomainObject) {
            this.cachedDomainObject = cachedDomainObject;
        }

        @Override
        public DomainObject getDomainObjectBefore() {
            return cachedDomainObject;
        }

        public enum NodeDomainObject {
            Clear,
            Set,
            Nothing
        }

        public Rights rights() {
            if (user == null) {
                return Rights.Nothing;
            } else if (GenericDomainObject.isAbsent(domainObject)) {
                return Rights.Clear;
            } else {
                return Rights.Set;
            }
        }

        public NodeDomainObject shouldUpdateDomainObject() {
            if (cachedDomainObject == null) {
                return NodeDomainObject.Set;
            }
            if (domainObject == null) {
                return NodeDomainObject.Nothing;
            }
            // suspicious situation... todo: log
            if (GenericDomainObject.isAbsent(cachedDomainObject)) {
                return NodeDomainObject.Set;
            }

            int comparison = domainObject.getModifiedDate().compareTo(cachedDomainObject.getModifiedDate());
            if (comparison > 0) {
                return NodeDomainObject.Set;
            } else if (comparison == 0 && !domainObject.equals(cachedDomainObject)) {
                return NodeDomainObject.Clear;
            } else {
                return NodeDomainObject.Nothing;
            }
        }

        public DomainObject getObjectToFindAccessObjectBy() {
            if (domainObject != null && !GenericDomainObject.isAbsent(domainObject)) {
                return domainObject;
            }
            return GenericDomainObject.isAbsent(cachedDomainObject) ? null : cachedDomainObject;
        }

        public boolean clearDomainObject() {
            return shouldUpdateDomainObject() == NodeDomainObject.Clear;
        }

        public boolean isNodeInitializedWithRealObject() {
            return (cachedDomainObject == null || GenericDomainObject.isAbsent(cachedDomainObject)) && domainObject != null && !GenericDomainObject.isAbsent(domainObject);
        }
    }
}