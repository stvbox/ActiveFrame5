package ru.intertrust.cm.core.dao.impl.utils;

import ru.intertrust.cm.core.business.api.dto.*;
import ru.intertrust.cm.core.config.ConfigurationExplorer;
import ru.intertrust.cm.core.config.model.*;
import ru.intertrust.cm.core.dao.api.DomainObjectDao;
import ru.intertrust.cm.core.dao.api.DomainObjectTypeIdCache;
import ru.intertrust.cm.core.dao.impl.DataType;
import ru.intertrust.cm.core.dao.impl.DomainObjectCacheServiceImpl;
import ru.intertrust.cm.core.model.FatalException;
import ru.intertrust.cm.core.util.SpringApplicationContext;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getReferenceTypeColumnName;
import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getServiceColumnName;
import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getTimeZoneIdColumnName;

/**
 * Базовй класс для отображения {@link java.sql.ResultSet} на доменные объекты и
 * коллекции.
 *
 * @author atsvetkov
 */
public class BasicRowMapper {

    protected static final String TYPE_ID_COLUMN = DomainObjectDao.TYPE_COLUMN.toLowerCase();
    protected static final String REFERENCE_TYPE_POSTFIX = DomainObjectDao.REFERENCE_TYPE_POSTFIX.toLowerCase();
    protected static final String TIME_ZONE_ID_POSTFIX = DomainObjectDao.TIME_ID_ZONE_POSTFIX.toLowerCase();

    protected final String domainObjectType;
    protected final String idField;

    protected ConfigurationExplorer configurationExplorer;

    protected DomainObjectTypeIdCache domainObjectTypeIdCache;

    private DomainObjectCacheServiceImpl domainObjectCacheService;

    public BasicRowMapper(String domainObjectType, String idField, ConfigurationExplorer configurationExplorer,
            DomainObjectTypeIdCache domainObjectTypeIdCache) {
        this.domainObjectType = domainObjectType;
        this.idField = idField;
        this.configurationExplorer = configurationExplorer;
        this.domainObjectTypeIdCache = domainObjectTypeIdCache;
    }

    /**
     * Отображает типы колонок в конфигурации коллекции на
     * {@link ru.intertrust.cm.core.dao.impl.DataType}.
     *
     * @param columnType
     *            типы колонок в конфигурации
     * @return объект {@link ru.intertrust.cm.core.dao.impl.DataType}
     */
    protected DataType getColumnDataType(String columnType) {
        DataType result = null;
        if (columnType.equals("integer")) {
            result = DataType.INTEGER;
        } else if (columnType.equals("datetime")) {
            result = DataType.DATETIME;
        } else if (columnType.equals("string")) {
            result = DataType.STRING;
        } else if (columnType.equals("boolean")) {
            result = DataType.BOOLEAN;
        } else if (columnType.equals("decimal")) {
            result = DataType.DECIMAL;
        }
        return result;
    }

    protected DataType getColumnDataTypeByDbTypeName(String columnTypeName) {
        DataType result = null;
        if (columnTypeName.equals("int8")) {
            result = DataType.INTEGER;
        } else if (columnTypeName.equals("timestamp")) {
            result = DataType.DATETIME;
        } else if (columnTypeName.equals("varchar") || columnTypeName.equals("unknown")
                || columnTypeName.equals("text")) {
            result = DataType.STRING;
        } else if (columnTypeName.equals("bool")) {
            result = DataType.BOOLEAN;
        } else if (columnTypeName.equals("numeric")) {
            result = DataType.DECIMAL;
        }
        return result;
    }

    /**
     * Заполняет модель {@see FieldValueModel} из объекта {@see ResultSet}.
     *
     * @param rs
     *            {@see ResultSet}
     * @param valueModel
     *            модель {@see FieldValueModel}
     * @param columnName
     *            имя колонки, которая извлекается из {@see ResultSet}
     * @throws SQLException
     */
    protected void fillValueModel(ResultSet rs, FieldValueModel valueModel, String columnName, FieldConfig fieldConfig) throws SQLException {
        Value value = null;
        Id id = null;
        Id parentId = null;

        if (idField.equalsIgnoreCase(columnName)) {
            id = readId(rs, columnName);
        } else if (fieldConfig != null && StringFieldConfig.class.equals(fieldConfig.getClass())) {
            String fieldValue = rs.getString(columnName);
            if (!rs.wasNull()) {
                value = new StringValue(fieldValue);
            } else {
                value = new StringValue();
            }
        } else if (fieldConfig != null && LongFieldConfig.class.equals(fieldConfig.getClass())) {
            value = readLongValue(rs, columnName);
        } else if (fieldConfig != null && DecimalFieldConfig.class.equals(fieldConfig.getClass())) {
            BigDecimal fieldValue = rs.getBigDecimal(columnName);
            if (!rs.wasNull()) {
                value = new DecimalValue(fieldValue);
            } else {
                value = new DecimalValue();
            }
        } else if (fieldConfig != null && ReferenceFieldConfig.class.equals(fieldConfig.getClass())) {
            String typeColumnName = getReferenceTypeColumnName((ReferenceFieldConfig) fieldConfig).toLowerCase();
            value = readReferenceValue(rs, columnName, typeColumnName);
        } else if (fieldConfig != null && DateTimeFieldConfig.class.equals(fieldConfig.getClass())) {
            value = readTimestampValue(rs, valueModel, columnName);
        } else if (fieldConfig != null && DateTimeWithTimeZoneFieldConfig.class.equals(fieldConfig.getClass())) {
            String timeZoneIdColumnName =
                    getTimeZoneIdColumnName((DateTimeWithTimeZoneFieldConfig) fieldConfig).toLowerCase();
            value = readDateTimeWithTimeZoneValue(rs, columnName, timeZoneIdColumnName);
        }

        if (id != null) {
            valueModel.setId(id);
        }
        if (parentId != null) {
            valueModel.setParentId(parentId);
        }

        valueModel.setValue(value);
    }

    /**
     * Заполняет поля доменного объекта (id, parent или атрибут) из модели
     * {@see FieldValueModel}.
     *
     * @param object
     *            исходный доменного объекта
     * @param valueModel
     *            модель {@see FieldValueModel}
     * @param fieldConfig
     *            имя поля, нужно если заполняется обычное поле
     */
    protected void fillObjectValue(GenericDomainObject object, FieldValueModel valueModel, FieldConfig fieldConfig) {
        if (valueModel.getId() != null) {
            object.setId(valueModel.getId());
        }
        if (valueModel.getModifiedDate() != null) {
            object.setModifiedDate(valueModel.getModifiedDate());
        }
        if (valueModel.getCreatedDate() != null) {
            object.setCreatedDate(valueModel.getCreatedDate());
        }
        if (valueModel.getValue() != null) {
            object.setValue(fieldConfig.getName(), valueModel.getValue());
        }
    }

    protected DomainObject buildDomainObject(ResultSet rs, ColumnModel columnModel) throws SQLException {
        GenericDomainObject object = new GenericDomainObject();
        object.setTypeName(domainObjectType);

        for (String columnName : columnModel.getColumnNames()) {
            FieldValueModel valueModel = new FieldValueModel();
            FieldConfig fieldConfig = configurationExplorer.getFieldConfig(domainObjectType, columnName);
            fillValueModel(rs, valueModel, columnName, fieldConfig);
            fillObjectValue(object, valueModel, fieldConfig);
        }

        // TODO добавлено Лариным. М. после выноса системных арибутов в
        // родительский класс надо будет убрать эти 2 строчки
        object.setCreatedDate(object.getTimestamp("created_date"));
        object.setModifiedDate(object.getTimestamp("updated_date"));

        object.resetDirty();

        if (object.getId() != null) {
            getDomainObjectCacheService().putObjectToCache(object);
        }

        return object;
    }

    protected DomainObjectVersion buildDomainObjectVersion(ResultSet rs) throws SQLException {
        GenericDomainObjectVersion object = new GenericDomainObjectVersion();

        int typeId = domainObjectTypeIdCache.getId(domainObjectType);

        // Установка полей версии
        object.setId(new RdbmsId(typeId, rs.getLong(DomainObjectDao.ID_COLUMN)));
        object.setDomainObjectId(new RdbmsId(typeId, rs.getLong(DomainObjectDao.DOMAIN_OBJECT_ID)));
        object.setModifiedDate(rs.getTimestamp(DomainObjectDao.UPDATED_DATE_COLUMN));
        object.setVersionInfo(rs.getString(DomainObjectDao.INFO));
        object.setIpAddress(rs.getString(DomainObjectDao.IP_ADDRESS));
        object.setComponent(rs.getString(DomainObjectDao.COMPONENT));
        object.setOperation(getOperation(rs.getInt(DomainObjectDao.OPERATION_COLUMN)));

        setDomainObjectFields(object, rs, domainObjectType);

        object.resetDirty();

        return object;
    }

    private DomainObjectVersion.AuditLogOperation getOperation(int operation){
        DomainObjectVersion.AuditLogOperation result = null;
        if (operation == 1){
            result = DomainObjectVersion.AuditLogOperation.CREATE;
        }else if(operation == 2){
            result = DomainObjectVersion.AuditLogOperation.UPDATE;
        }else{
            result = DomainObjectVersion.AuditLogOperation.DELETE;
        }
        return result;
    }

    private void setDomainObjectFields(GenericDomainObjectVersion object, ResultSet rs, String type) throws SQLException {
        if (type != null) {
            DomainObjectTypeConfig doConfig = configurationExplorer.getConfig(DomainObjectTypeConfig.class, type);
            // Установка полей доменного объекта
            for (FieldConfig fieldConfig : doConfig.getDomainObjectFieldsConfig().getFieldConfigs()) {
                FieldValueModel valueModel = new FieldValueModel();
                fillValueModel(rs, valueModel, fieldConfig.getName(), fieldConfig);
                object.setValue(fieldConfig.getName(), valueModel.getValue());
            }
            setDomainObjectFields(object, rs, doConfig.getExtendsAttribute());
        }
    }

    protected DomainObjectCacheServiceImpl getDomainObjectCacheService() {
        if (domainObjectCacheService == null) {
            domainObjectCacheService = SpringApplicationContext.getContext().getBean("domainObjectCacheService",
                    DomainObjectCacheServiceImpl.class);
        }
        return domainObjectCacheService;
    }

    protected LongValue readLongValue (ResultSet rs, String columnName) throws SQLException {
        Long longValue = rs.getLong(columnName);
        if (!rs.wasNull()) {
            return new LongValue(longValue);
        } else {
            return new LongValue();
        }
    }

    protected RdbmsId readId(ResultSet rs, String columnName) throws SQLException {
        Long longValue = rs.getLong(columnName);
        if (rs.wasNull()) {
            throw new FatalException("Id field can not be null for object " + domainObjectType);
        }

        Integer idType = null;
        if (columnName.equals(DomainObjectDao.ID_COLUMN.toLowerCase())){
            idType = rs.getInt(TYPE_ID_COLUMN);
        }else{
            idType = rs.getInt(getServiceColumnName(columnName, REFERENCE_TYPE_POSTFIX).toLowerCase());
        }
        if (rs.wasNull()) {
            throw new FatalException("Id type field can not be null for object " + domainObjectType);
        }

        return new RdbmsId(idType, longValue);
    }

    protected Value readReferenceValue(ResultSet rs, String columnName, String typeColumnName) throws SQLException {
        Long longValue = rs.getLong(columnName);
        if (rs.wasNull()) {
            return new ReferenceValue();
        } else {
            Integer typeId = rs.getInt(typeColumnName);
            if (!rs.wasNull()) {
                return new ReferenceValue(new RdbmsId(typeId, longValue));
            } else {
                throw new FatalException("Reference type field can not be null for object " + domainObjectType);
            }
        }
    }

    protected Value readTimestampValue(ResultSet rs, FieldValueModel valueModel, String columnName)
            throws SQLException {
        Calendar gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Timestamp timestamp = rs.getTimestamp(columnName, gmtCalendar);

        TimestampValue value;
        if (!rs.wasNull()) {
            Date date = new Date(timestamp.getTime());
            value = new TimestampValue(date);

            if (DomainObjectDao.CREATED_DATE_COLUMN.equalsIgnoreCase(columnName)) {
                valueModel.setCreatedDate(date);
            } else if (DomainObjectDao.UPDATED_DATE_COLUMN.equalsIgnoreCase(columnName)) {
                valueModel.setModifiedDate(date);
            }
        } else {
            value = new TimestampValue();
        }

        return value;
    }

    protected Value readDateTimeWithTimeZoneValue(ResultSet rs, String columnName, String timeZoneIdColumnName)
            throws SQLException {
        Calendar gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Timestamp timestamp = rs.getTimestamp(columnName, gmtCalendar);

        DateTimeWithTimeZoneValue value;
        if (!rs.wasNull()) {
            String timeZoneId = rs.getString(timeZoneIdColumnName);
            if (!rs.wasNull()) {
                DateTimeWithTimeZone dateTimeWithTimeZone = getDateTimeWithTimeZone(timestamp, timeZoneId);
                value = new DateTimeWithTimeZoneValue(dateTimeWithTimeZone);
            } else {
                throw new FatalException("TimeZone id field can not be null for object " + domainObjectType);
            }
        } else {
            value = new DateTimeWithTimeZoneValue();
        }

        return value;
    }

    private DateContext getDateTimeWithTimeZoneContext(String timeZoneId) {
        if (timeZoneId.startsWith("GMT")) {
            long offset = Long.parseLong(timeZoneId.substring(4))*3600000;
            return new UtcOffsetContext(offset);
        } else {
            return new TimeZoneContext(timeZoneId);
        }
    }

    private DateTimeWithTimeZone getDateTimeWithTimeZone(Timestamp timestamp, String timeZoneId) {
        DateTimeWithTimeZone dateTimeWithTimeZone = new DateTimeWithTimeZone();
        dateTimeWithTimeZone.setContext(getDateTimeWithTimeZoneContext(timeZoneId));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId));
        calendar.setTime(timestamp);

        dateTimeWithTimeZone.setYear(calendar.get(Calendar.YEAR));
        dateTimeWithTimeZone.setMonth(calendar.get(Calendar.MONTH));
        dateTimeWithTimeZone.setDayOfMonth(calendar.get(Calendar.DAY_OF_MONTH));
        dateTimeWithTimeZone.setHours(calendar.get(Calendar.HOUR_OF_DAY));
        dateTimeWithTimeZone.setMinutes(calendar.get(Calendar.MINUTE));
        dateTimeWithTimeZone.setSeconds(calendar.get(Calendar.SECOND));
        dateTimeWithTimeZone.setMilliseconds(calendar.get(Calendar.MILLISECOND));

        return dateTimeWithTimeZone;
    }

    // protected void fillValueModelWithSystemFields(SystemField systemFields,)

    /**
     * Обертывает заполненное поле (атрибут), поле parent или поле id в доменном
     * объекте.
     *
     * @author atsvetkov
     */
    protected class FieldValueModel {
        private Id id = null;
        private Id parentId = null;
        private Value value = null;
        private Date createdDate = null;
        private Date modifiedDate = null;

        public Id getId() {
            return id;
        }

        public void setId(Id id) {
            this.id = id;
        }

        public Value getValue() {
            return value;
        }

        public void setValue(Value value) {
            this.value = value;
        }

        public Id getParentId() {
            return parentId;
        }

        public void setParentId(Id parentId) {
            this.parentId = parentId;
        }

        public Date getCreatedDate() {
            return createdDate;
        }

        public void setCreatedDate(Date createdDate) {
            this.createdDate = createdDate;
        }

        public Date getModifiedDate() {
            return modifiedDate;
        }

        public void setModifiedDate(Date modifiedDate) {
            this.modifiedDate = modifiedDate;
        }
    }

    /**
     * Модель для хранкения названия колонолк и названия колонки-первичного
     * ключа для доменного объекта.
     *
     * @author atsvetkov
     */
    protected class ColumnModel {

        private String idField;
        private List<String> columnNames;

        public List<String> getColumnNames() {
            if (columnNames == null) {
                columnNames = new ArrayList<String>();
            }
            return columnNames;
        }

        public String getIdField() {
            return idField;
        }

        public void setIdField(String idField) {
            this.idField = idField;
        }
    }
}
