package ru.intertrust.cm.core.business.api;

import ru.intertrust.cm.core.business.api.dto.*;

import java.util.Collection;
import java.util.List;

/**
 * Сервис, обеспечивающий базовые CRUD-операции (C-Create-Создание R-Read-Чтение U-Update-Модификация D-Delete-Удаление)
 * над доменными объектами. Операции над наборами доменных объектов выполняются в рамках единой транзакции. В случае
 * возникновения исключительной ситуации, следует учитывать факт того, что, например, набор доменных объектов может быть
 * сохранен лишь частично, поэтому принудительная фиксация (commit) транзакции должна быть тщательно взвешена
 * для подобных случаев. Обычной практикой при возникновении исключений в CRUD-операциях является откат транзакции.
 * Система не гарантирует последовательности сохранения/модификации/удаления наборов доменных объектов. Например, если
 * сохраняются объекты {1, 2, 3}, то последовательность, в который они попадут в хранилище может быть {3, 2, 1}. Однако
 * результат сохранения будет возвращён методом в соответствии с оригинальным порядком объектов: {1, 2, 3},
 * если метод явно не специфицирует другое поведение.
 * <p/>
 * Author: Denis Mitavskiy
 * Date: 22.05.13
 * Time: 22:01
 */
public interface CrudService {
    public interface Remote extends CrudService {
    }

    /**
     * Создаёт идентифицируемый объект. Заполняет необходимые атрибуты значениями, сгенерированными согласно правилам.
     * Идентификатор объекта при этом не определяется.
     * См. некое описание
     *
     * @return новый идентифицируемый объект
     */
    IdentifiableObject createIdentifiableObject();

    /**
     * Создаёт доменный объект определённого типа, не сохраняя в СУБД. Заполняет необходимые атрибуты значениями,
     * сгенерированными согласно правилам, определённым для данного объекта. Идентификатор объекта при этом
     * не генерируется.
     *
     * @param name название доменного объекта, который нужно создать
     * @return сохранённый доменного объект
     * @throws IllegalArgumentException, если доменного объекта данного типа не существует
     * @throws NullPointerException,     если type есть null.
     */
    DomainObject createDomainObject(String name);

    /**
     * Сохраняет доменный объект. Если объект не существует в системе, создаёт его и заполняет отсутствующие атрибуты
     * значениями, сгенерированными согласно правилам, определённым для данного объекта (например, будет сгенерирован и
     * заполнен идентификатор объекта). Оригинальный Java-объект измененям не подвергается, изменения отражены в
     * возвращённом объекте.
     *
     * @param domainObject доменный объект, который нужно сохранить
     * @return сохранённый доменный объект
     * @throws IllegalArgumentException, если состояние объекта не позволяет его сохранить (например, если атрибут
     *                                   содержит данные неверного типа, или обязательный атрибут не определён)
     * @throws NullPointerException,     если доменный объект есть null.
     */
    DomainObject save(DomainObject domainObject);

    /**
     * Сохраняет список доменных объектов. Если какой-то объект не существует в системе, создаёт его и заполняет
     * отсутствующие атрибуты значениями, сгенерированными согласно правилам, определённым для данного объекта
     * (например, будет сгенерирован и заполнен идентификатор объекта). Оригинальные Java-объекты измененям
     * не подвергаются, изменения отражены в возвращённых объектах.
     *
     * @param domainObjects список доменных объектов, которые нужно сохранить
     * @return список сохранённых доменных объектов, упорядоченный аналогично оригинальному
     * @throws IllegalArgumentException, если состояние хотя бы одного объекта не позволяет его сохранить (например,
     *                                   если атрибут содержит данные неверного типа, или обязательный атрибут не определён)
     * @throws NullPointerException,     если список или хотя бы один доменный объект в списке есть null
     */
    List<DomainObject> save(List<DomainObject> domainObjects);

    /**
     * Возвращает true, если доменный объект существует и false в противном случае.
     *
     * @param id уникальный идентификатор доменного объекта в системе
     * @return true, если доменный объект существует и false в противном случае
     * @throws NullPointerException, если id есть null
     */
    boolean exists(Id id);

    /**
     * Возвращает доменный объект по его уникальному идентификатору в системе
     *
     * @param id уникальный идентификатор доменного объекта в системе
     * @return доменный объект с данным идентификатором или null, если объект не существует
     * @throws NullPointerException, если id есть null
     */
    DomainObject find(Id id);

    /**
     * Возвращает доменные объекты по их уникальным идентификаторам в системе.
     *
     * @param ids уникальные идентификаторы доменных объектов в системе
     * @return список найденных доменных объектов, упорядоченный аналогично оригинальному. Не найденные доменные объекты
     *         в результирующем списке представлены null.
     * @throws NullPointerException, если список или хотя бы один идентификатор в списке есть null
     */
    List<DomainObject> find(List<Id> ids);

    /**
     * Удаляет доменный объект по его уникальному идентификатору. Не осуществляет никаких действий, если объект
     * не существует
     *
     * @param id уникальный идентификатор доменного объекта в системе
     * @throws NullPointerException, если id есть null
     */
    void delete(Id id);

    /**
     * Удаляет доменные объекты по их уникальным идентификаторам. Не осуществляет никаких действий, если какой-либо объект
     * не существует
     *
     * @param ids уникальные идентификаторы доменных объектов, которых необходимо удалить, в системе
     * @return количество удалённых доменных объектов
     * @throws NullPointerException, если список или хотя бы один идентификатор в списке есть null
     */
    int delete(Collection<Id> ids);

    /**
     * Получает список связанных доменных объектов по типу объекта и указанному полю
     *
     * @param domainObjectId уникальный идентификатор доменного объекта в системе
     * @param linkedType     тип доменного объекта в системе
     * @param linkedField    название поля по которому связан объект
     * @return список связанных доменных объектов
     */
    List<DomainObject> findLinkedDomainObjects(Id domainObjectId, String linkedType, String linkedField);

    /**
     * Получает список идентификаторов связанных доменных объектов по типу объекта и указанному полю
     *
     * @param domainObjectId уникальный идентификатор доменного объекта в системе
     * @param linkedType     тип доменного объекта в системе
     * @param linkedField    название поля по которому связан объект
     * @return список идентификаторов связанных доменных объектов
     */
    List<Id> findLinkedDomainObjectsIds(Id domainObjectId, String linkedType, String linkedField);

    /**
     * Получает список доменных объектов Вложений для переданного доменного объекта
     *
     * @param domainObjectId уникальный идентификатор доменного объекта в системе
     * @param childType      тип доменного объекта в системе
     * @return список доменных объектов Вложений для переданного доменного объекта
     */
    @Deprecated
    List<DomainObject> findChildren(Id domainObjectId, String childType);
}
