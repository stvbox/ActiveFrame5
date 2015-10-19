package ru.intertrust.cm.globalcache.api;

import ru.intertrust.cm.core.business.api.dto.*;
import ru.intertrust.cm.core.dao.access.AccessToken;

import java.util.*;

/**
 * @author Denis Mitavskiy
 *         Date: 07.07.2015
 *         Time: 14:44
 */
public interface GlobalCache {
    void init();

    void notifyCreate(String transactionId, DomainObject obj, AccessToken accessToken);

    void notifyUpdate(String transactionId, DomainObject obj, AccessToken accessToken);

    void notifyDelete(String transactionId, Id id);

    void notifyRead(String transactionId, Id id, DomainObject obj, AccessToken accessToken);

    void notifyReadByUniqueKey(String transactionId, String type, Map<String, Value> uniqueKey, DomainObject obj, long time, AccessToken accessToken);

    void notifyRead(String transactionId, Collection<DomainObject> objects, AccessToken accessToken);

    void notifyReadAll(String transactionId,String type, boolean exactType, Collection<DomainObject> objects, AccessToken accessToken);

    void notifyReadPossiblyNullObjects(String transactionId, Collection<Pair<Id, DomainObject>> idsAndObjects, AccessToken accessToken);

    void notifyLinkedObjectsRead(String transactionId, Id id, String linkedType, String linkedField, boolean exactType,
                                 List<DomainObject> linkedObjects, long time, AccessToken accessToken);

    void notifyLinkedObjectsIdsRead(String transactionId, Id id, String linkedType, String linkedField, boolean exactType,
                                 Set<Id> linkedObjectsIds, long time, AccessToken accessToken);

    void notifyCollectionRead(String transactionId, String name, Set<String> domainObjectTypes, Set<String> filterNames, List<? extends Filter> filterValues,
                              SortOrder sortOrder, int offset, int limit,
                              IdentifiableObjectCollection collection, long time, AccessToken accessToken);

    void notifyCollectionRead(String transactionId, String query, Set<String> domainObjectTypes, List<? extends Value> paramValues, int offset, int limit,
                              IdentifiableObjectCollection collection, long time, AccessToken accessToken);

    void notifyCommit(DomainObjectsModification modification, AccessChanges accessChanges);

    DomainObject getDomainObject(String transactionId, Id id, AccessToken accessToken);

    DomainObject getDomainObject(String transactionId, String type, Map<String, Value> uniqueKey, AccessToken accessToken);

    ArrayList<DomainObject> getDomainObjects(String transactionId, Collection<Id> ids, AccessToken accessToken);

    List<DomainObject> getLinkedDomainObjects(String transactionId, Id domainObjectId, String linkedType,
                                              String linkedField, boolean exactType, AccessToken accessToken);

    List<Id> getLinkedDomainObjectsIds(String transactionId, Id domainObjectId, String linkedType,
                                       String linkedField, boolean exactType, AccessToken accessToken);

    List<DomainObject> getAllDomainObjects(String transactionId, String type, boolean exactType, AccessToken accessToken);

    IdentifiableObjectCollection getCollection(String transactionId, String name, List<? extends Filter> filterValues, SortOrder sortOrder,
                                               int offset, int limit, AccessToken accessToken);

    IdentifiableObjectCollection getCollection(String transactionId, String query, List<? extends Value> paramValues, int offset, int limit, AccessToken accessToken);

    void setSizeLimitBytes(long bytes);

    long getSizeLimitBytes();
}
