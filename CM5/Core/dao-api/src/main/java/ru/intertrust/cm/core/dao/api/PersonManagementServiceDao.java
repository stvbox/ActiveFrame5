package ru.intertrust.cm.core.dao.api;

import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.Id;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Сервис для более удобной работы с пользователями и группами
 * @author larin
 * 
 */
public interface PersonManagementServiceDao {

    /**
     * Получение списка персон, входящих в группу
     * @param groupId
     * @return
     */
    List<DomainObject> getPersonsInGroup(Id groupId);

    /**
     * Получение списка персон входящих в группу, с учетом вхождения группы в группу
     * @param groupId
     * @return
     */
    List<DomainObject> getAllPersonsInGroup(Id groupId);

    /**
     * Проверка входит ли персона в группу с учетом вхождения группы в группу
     * @param groupId
     * @return
     */
    boolean isPersonInGroup(Id groupId, Id personId);

    /**
     * Получения списка групп, в которые входит персона с учетом наследования
     * @param personId
     * @return
     */
    List<DomainObject> getPersonGroups(Id personId);

    /**
     * Проверка вхождения группы в группу.
     * @param parent
     * @param child
     * @param recursive
     *            учитывать вхождение группы в группу
     * @return
     */
    boolean isGroupInGroup(Id parent, Id child, boolean recursive);

    /**
     * Получение всех родительских групп для группы, с учетом вхождения группы в группу
     * @param child
     * @return
     */
    List<DomainObject> getAllParentGroup(Id child);

    /**
     * Получение групп, непосредственно входящие в группу
     * @param parentGroup
     * @return
     */
    List<DomainObject> getChildGroups(Id parentGroup);

    /**
     * Получение групп, в которые непосредственно входит переданная группа
     * @param childGroup
     * @return
     */
    List<DomainObject> getParentGroups(Id childGroup);

    /**
     * Получение групп входящих в группу с учетом вхождения группы в группу
     * @param parent
     * @return
     */
    List<DomainObject> getAllChildGroups(Id parent);

    /**
     * Добавление персоны в группу
     * @param group
     * @param person
     */
    void addPersonToGroup(Id group, Id person);

    /**
     * Добавление группы в группу
     * @param parent
     * @param child
     */
    void addGroupToGroup(Id parent, Id child);

    /**
     * Добавление группы в группу
     * @param parent
     * @param children
     */
    void addGroupsToGroup(Id parent, Collection<Id> children);

    /**
     * Удаление персоны из группы
     * @param group
     * @param person
     */
    void remotePersonFromGroup(Id group, Id person);


    /**
     * Удаление персон из группы
     * @param group
     * @param persons
     */
    void remotePersonsFromGroup(Id group, List<Id> persons);

    /**
     * Удаление группы из группы
     * @param parent
     * @param child
     */
    void remoteGroupFromGroup(Id parent, Id child);


    /**
     * Удаление группы из группы
     * @param parent
     * @param childs
     */
    void remoteGroupFromGroups(Id parent, List<Id> childs);



    /**
     * Получение идентификатора группы по имени
     * @param groupName
     */
    Id getGroupId(String groupName);

    /**
     * Очистка состава группы. Удаляет как персоны члены группы так и группы члены группы
     * @param groupId
     */
    void removeGroupMembers(Id groupId);

    /**
     * Получение динамической группы
     * @param name
     *            имя динамической группы
     * @param contectId
     *            контекст динамической группы
     * @return
     */
    DomainObject findDynamicGroup(String name, Id contectId);
    
    /**
     * Получение всех групп верхнего уровня 
     */
    public Set<Id> getAllRootGroup();

    /**
     * Пересчет "развернутого" состава групп, входящих в группу.
     * @param groupId идентификатор группы, для которой производится пересчет
     */
    void recalcGroupGroup(Id groupId);

    /**
     * Пересчет "развернутого" состава групп, входящих в группу. 
     * Пересчет производится для группы переданной в качестве параметра
     * и для всех групп входящих в эту группу
     * @param groupId идентификатор группы, для которой производится пересчет
     * @return
     */
    Set<Id> recalcGroupGroupForGroupAndChildGroups(Id groupId);
}
