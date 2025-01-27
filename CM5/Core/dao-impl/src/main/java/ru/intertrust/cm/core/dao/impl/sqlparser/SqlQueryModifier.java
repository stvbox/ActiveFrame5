package ru.intertrust.cm.core.dao.impl.sqlparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.WithItem;
import ru.intertrust.cm.core.business.api.QueryModifierPrompt;
import ru.intertrust.cm.core.business.api.dto.Case;
import ru.intertrust.cm.core.business.api.dto.CaseInsensitiveHashMap;
import ru.intertrust.cm.core.business.api.dto.Filter;
import ru.intertrust.cm.core.business.api.dto.IdsIncludedFilter;
import ru.intertrust.cm.core.business.api.dto.impl.RdbmsId;
import ru.intertrust.cm.core.business.api.util.ObjectCloner;
import ru.intertrust.cm.core.config.ConfigurationExplorer;
import ru.intertrust.cm.core.config.DateTimeWithTimeZoneFieldConfig;
import ru.intertrust.cm.core.config.FieldConfig;
import ru.intertrust.cm.core.config.ReferenceFieldConfig;
import ru.intertrust.cm.core.dao.access.UserGroupGlobalCache;
import ru.intertrust.cm.core.dao.api.CurrentUserAccessor;
import ru.intertrust.cm.core.dao.api.DomainObjectDao;
import ru.intertrust.cm.core.dao.exception.CollectionQueryException;
import ru.intertrust.cm.core.dao.exception.DaoException;
import ru.intertrust.cm.core.dao.impl.CollectionsDaoImpl;
import ru.intertrust.cm.core.dao.impl.DomainObjectQueryHelper;
import ru.intertrust.cm.core.dao.impl.utils.DaoUtils;
import ru.intertrust.cm.core.model.FatalException;

import static java.util.Collections.singletonList;
import static ru.intertrust.cm.core.dao.api.DomainObjectDao.REFERENCE_POSTFIX;
import static ru.intertrust.cm.core.dao.api.DomainObjectDao.REFERENCE_TYPE_POSTFIX;
import static ru.intertrust.cm.core.dao.api.DomainObjectDao.TIME_ID_ZONE_POSTFIX;
import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getReferenceTypeColumnName;
import static ru.intertrust.cm.core.dao.impl.DataStructureNamingHelper.getServiceColumnName;
import static ru.intertrust.cm.core.dao.impl.utils.DaoUtils.wrap;

/**
 * Модифицирует SQL запросы. Добавляет поле Тип Объекта идентификатора в SQL
 * запрос получения данных для коллекции, добавляет ACL фильтр в SQL получения
 * данных для коллекции
 *
 * @author atsvetkov
 */
public class SqlQueryModifier {

    public static final String USER_ID_PARAM = "USER_ID_PARAM";
    private static final String USER_ID_VALUE = ":user_id";

    private static final String CASE_UNDEFINED_DO_NAME = "CASE_UNDEFINED_DO_NAME";

    private final ConfigurationExplorer configurationExplorer;

    private final UserGroupGlobalCache userGroupCache;
    private final CurrentUserAccessor currentUserAccessor;
    private final DomainObjectQueryHelper domainObjectQueryHelper;

    Map<String, Map<String, List<SelectItem>>> withItemColumnReplacementMap;

    public SqlQueryModifier(ConfigurationExplorer configurationExplorer, UserGroupGlobalCache userGroupCache,
                            CurrentUserAccessor currentUserAccessor, DomainObjectQueryHelper domainObjectQueryHelper) {
        this.configurationExplorer = configurationExplorer;
        this.userGroupCache = userGroupCache;
        this.currentUserAccessor = currentUserAccessor;
        this.domainObjectQueryHelper = domainObjectQueryHelper;
    }

    /**
     * Добавляет сервисные поля (Тип Объекта идентификатора, идентификатор
     * таймзоны и т.п.) в SQL запрос получения данных для коллекции. Переданный
     * SQL запрос должен быть запросом чтения (SELECT) либо объединением
     * запросов чтения (UNION). После ключевого слова FROM должно идти название
     * таблицы для Доменного Объекта, тип которого будет типом уникального
     * идентификатора возвращаемых записей.
     *
     * @return запрос с добавленным полем Тип Объекта идентификатора
     */
    public Select addServiceColumns(Select select) {
        withItemColumnReplacementMap = new HashMap<>();
        processSelect(select, new AddServiceColumnsQueryProcessor());
        return select;
    }

    /**
     * Оборачивает имена сущностей бд в кавычки и приводит их к нижнему регистру
     *
     * @return модифицированный запрос
     */
    public static String wrapAndLowerCaseNames(Select select) {
        select.accept(new WrapAndLowerCaseStatementVisitor());
        return select.toString();
    }

    /**
     * Создает count-запрос из select-запроса
     *
     * @param query запрос
     * @return модифицированный запрос throws FatalException если query не
     * является простым select-запросом (используется union и т.п.)
     */
    public static String transformToCountQuery(String query) {
        SqlQueryParser sqlParser = new SqlQueryParser(query);
        SelectBody selectBody = sqlParser.getSelectBody();
        if (!(selectBody instanceof PlainSelect)) {
            throw new FatalException("Counting prototype query must be provided for queries that are not plain selects");
        }

        PlainSelect plainSelect = (PlainSelect) selectBody;
        if (plainSelect.getSelectItems() == null) {
            plainSelect.setSelectItems(new ArrayList<>());
        }
        plainSelect.getSelectItems().clear();

        Function countExpression = new Function();
        countExpression.setName("count");
        countExpression.setAllColumns(true);
        plainSelect.getSelectItems().add(new SelectExpressionItem(countExpression));

        return sqlParser.getSelectStatement().toString();
    }

    public SelectBody addIdBasedFilters(SelectBody selectBody, final List<? extends Filter> filterValues, final String idField) {
        return processSelectBody(selectBody, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                addIdBasedFiltersInPlainSelect(plainSelect, filterValues, idField);
            }
        });
    }

    public Map<String, FieldConfig> buildColumnToConfigMapForParameters(Select select) {
        final Map<String, List<FieldData>> columnToTableMapping = new HashMap<>();

        // TODO перенести всю логику поиска конфигурации колонок в
        // CollectingColumnConfigVisitor
        processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                buildColumnToConfigMapInPlainSelect(plainSelect, columnToTableMapping);
            }
        });

        processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                buildColumnToConfigMapUsingVisitor(plainSelect, columnToTableMapping);
            }
        });

        // Если будут проблемы, то придется тащить новую map и дальше
        return normalize(columnToTableMapping);
    }

    private void buildColumnToConfigMapUsingVisitor(PlainSelect plainSelect, final Map<String, List<FieldData>> columnToTableMapping) {
        CollectingColumnConfigVisitor collectColumnConfigVisitor = new CollectingColumnConfigVisitor(configurationExplorer, plainSelect);

        plainSelect.accept(collectColumnConfigVisitor);

        for (String column : collectColumnConfigVisitor.getColumnToConfigMapping().keySet()) {
            List<FieldData> fieldDataList = collectColumnConfigVisitor.getColumnToConfigMapping().get(column);
            if (fieldDataList != null) {
                // Объединим ранее полученные данные с новыми
                fieldDataList.forEach(newFieldData -> FieldDataHelper.addFieldData(columnToTableMapping, newFieldData));
            }
        }
    }

    public Map<String, FieldConfig> buildColumnToConfigMapForSelectItems(Select select) {
        final Map<String, List<FieldData>> columnToTableMapping = new HashMap<>();

        processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                buildColumnToConfigMapInPlainSelect(plainSelect, columnToTableMapping);
            }
        });
        processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                buildSelectItemConfigMapUsingVisitor(plainSelect, columnToTableMapping);
            }
        });

        return normalize(columnToTableMapping);
    }

    private Map<String, FieldConfig> normalize(Map<String, List<FieldData>> columnToTableMapping) {
        if (columnToTableMapping == null) {
            return null;
        }

        // После обработки, мы можем привести Map к старому виду, т.к. в процессе должны будут создаться алиасы, которые мы вынесем в key
        Map<String, FieldConfig> result = new HashMap<>();
        columnToTableMapping.forEach((key, value) -> result.put(key, value.isEmpty() ? null : value.get(0).getFieldConfig()));
        return result;
    }

    private Map<String, List<FieldData>> buildColumnToConfigMapForSelectItems(SelectBody selectBody) {
        final Map<String, List<FieldData>> columnToTableMapping = new CaseInsensitiveHashMap<>();

        processSelectBody(selectBody, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                buildColumnToConfigMapInPlainSelect(plainSelect, columnToTableMapping);
            }
        });
        processSelectBody(selectBody, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                buildSelectItemConfigMapUsingVisitor(plainSelect, columnToTableMapping);
            }
        });

        return columnToTableMapping;
    }

    private void buildSelectItemConfigMapUsingVisitor(PlainSelect plainSelect, final Map<String, List<FieldData>> columnToTableMapping) {
        CollectingSelectItemConfigVisitor collectSelectItemConfigVisitor = new CollectingSelectItemConfigVisitor(configurationExplorer, plainSelect);

        plainSelect.accept(collectSelectItemConfigVisitor);

        for (String column : collectSelectItemConfigVisitor.getColumnToConfigMapping().keySet()) {
            List<FieldData> fieldDataList = collectSelectItemConfigVisitor.getColumnToConfigMapping().get(column);
            if (fieldDataList != null) {
                fieldDataList.forEach(newFieldData -> FieldDataHelper.addFieldData(columnToTableMapping, newFieldData));
            }
        }
    }

    /**
     * Заменяет параметризованный фильтр по Reference полю (например, t.id =
     * {0}) на рабочий вариант этого фильтра {например, t.id = 1 and t.id_type =
     * 2 }
     */
    public String modifyQueryWithParameters(Select select, final QueryModifierPrompt prompt) {

        final Map<String, String> replaceExpressions = new HashMap<>();

        select = processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                ReferenceParamsProcessingVisitor modifyReferenceFieldParameter =
                        new ReferenceParamsProcessingVisitor(prompt, false);

                plainSelect.accept(modifyReferenceFieldParameter);
                replaceExpressions.putAll(modifyReferenceFieldParameter.getReplaceExpressions());

            }
        });

        String modifiedQuery = select.toString();

        for (Map.Entry<String, String> entry : replaceExpressions.entrySet()) {
            modifiedQuery = modifiedQuery.replaceAll(Pattern.quote(entry.getKey()), Matcher.quoteReplacement(entry.getValue()));
        }

        return modifiedQuery;
    }

    /**
     * Заменяет параметризованный фильтр по Reference полю (например, t.id =
     * {0}) на рабочий вариант этого фильтра {например, t.id = 1 and t.id_type =
     * 2 }
     * @param prompt
     *            TODO
     */
    public String modifyQueryWithReferenceFilterValues(Select select, final QueryModifierPrompt prompt) {
        final Map<String, String> replaceExpressions = new HashMap<>();

        select = processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                ReferenceParamsProcessingVisitor visitor = new ReferenceParamsProcessingVisitor(prompt, true);
                plainSelect.accept(visitor);
                replaceExpressions.putAll(visitor.getReplaceExpressions());
            }
        });

        String modifiedQuery = select.toString();
        for (Map.Entry<String, String> entry : replaceExpressions.entrySet()) {
            modifiedQuery = modifiedQuery.replaceAll(Pattern.quote(entry.getKey()), Matcher.quoteReplacement(entry.getValue()));
        }

        return modifiedQuery;
    }

    public void addAclQuery(Select select) {
        final AddAclVisitor visitor = domainObjectQueryHelper.createVisitor(configurationExplorer, userGroupCache, currentUserAccessor);
        select.accept(visitor);
    }

    public void checkDuplicatedColumns(Select select) {
        processSelect(select, new QueryProcessor() {
            @Override
            protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
                checkDuplicatedColumnsInPlainSelect(plainSelect);
            }
        });
    }

    private void checkDuplicatedColumnsInPlainSelect(PlainSelect plainSelect) {
        Set<String> columns = new HashSet<>();
        for (Object selectItem : plainSelect.getSelectItems()) {
            if (!(selectItem instanceof SelectExpressionItem)) {
                continue;
            }

            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
            if (selectExpressionItem.getAlias() == null) {
                continue;
            }

            String column = selectExpressionItem.getAlias().getName() + ":" +
                    (selectExpressionItem.getExpression() instanceof Column ?
                            ((Column) selectExpressionItem.getExpression()).getColumnName() : "");
            column = DaoUtils.unwrap(Case.toLower(column));
            if (!columns.add(column)) {
                throw new CollectionQueryException("Collection query contains duplicated columns: " + plainSelect);
            }
        }
    }

    private void addServiceColumnsInPlainSelect(PlainSelect plainSelect, String withName) {
        boolean isWith = withName != null;
        if (withName == null && plainSelect.getFromItem() instanceof Table) {
            withName = ((Table) plainSelect.getFromItem()).getName();
        }

        Map<String, List<FieldData>> columnToConfigMapForSelectItems = buildColumnToConfigMapForSelectItems(plainSelect);

        if (plainSelect.getFromItem() instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
            processSelectBody(subSelect.getSelectBody(), new AddServiceColumnsQueryProcessor());
        }

        List<SelectItem> selectItems = new ArrayList<>(plainSelect.getSelectItems().size());

        for (SelectItem selectItem : plainSelect.getSelectItems()) {
            List<SelectItem> selectItemReplacement = new ArrayList<>();
            selectItemReplacement.add(selectItem);

            if (!(selectItem instanceof SelectExpressionItem)) {
                selectItems.add(selectItem);
                continue;
            }

            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;

            if (selectExpressionItem.getExpression() instanceof Column) {
                SelectExpressionItem serviceExpressionItem = getServiceExpression(selectExpressionItem, columnToConfigMapForSelectItems);
                if (serviceExpressionItem != null) {
                    selectItemReplacement.add(serviceExpressionItem);
                }
            } else if (selectExpressionItem.getExpression() instanceof CaseExpression) {
                CaseExpression caseExpression = (CaseExpression) selectExpressionItem.getExpression();
                boolean returnsId = caseExpressionReturnsId(caseExpression, plainSelect);

                if (returnsId) {
                    // TODO клон CaseExpression не работает
                    CaseExpression idTypeExpression = ObjectCloner.getInstance().cloneObject(caseExpression, caseExpression.getClass());

                    for (Expression whenExpression : idTypeExpression.getWhenClauses()) {
                        WhenClause whenClause = (WhenClause) whenExpression;
                        if (whenClause.getThenExpression() instanceof Column) {
                            Column column = (Column) whenClause.getThenExpression();
                            List<FieldData> fieldDataList = columnToConfigMapForSelectItems.get(getColumnName(column));
                            changeColumnName(column, fieldDataList);
                        }
                    }

                    if (idTypeExpression.getElseExpression() instanceof Column) {
                        Column column = (Column) idTypeExpression.getElseExpression();
                        final List<FieldData> fieldDataList = columnToConfigMapForSelectItems.get(getColumnName(column));
                        changeColumnName(column, fieldDataList);
                    }

                    SelectExpressionItem idTypeSelectExpressionItem = new SelectExpressionItem();
                    idTypeSelectExpressionItem.setExpression(idTypeExpression);
                    if (selectExpressionItem.getAlias() != null) {
                        idTypeSelectExpressionItem.setAlias(
                                new Alias(getReferenceTypeColumnName(DaoUtils.unwrap(selectExpressionItem.getAlias().getName())), false));
                    }

                    if (!containsExpressionInPlainselect(plainSelect, idTypeSelectExpressionItem)) {
                        selectItemReplacement.add(idTypeSelectExpressionItem);
                    }
                }
            } else if (selectExpressionItem.getAlias() != null && selectExpressionItem.getAlias().getName().endsWith(REFERENCE_POSTFIX)) {
                Alias alias = selectExpressionItem.getAlias();
                if (selectExpressionItem.getExpression() instanceof NullValue) {
                    SelectExpressionItem referenceFieldTypeItem = new SelectExpressionItem();
                    referenceFieldTypeItem.setAlias(new Alias(getServiceColumnName(DaoUtils.unwrap(alias.getName()), REFERENCE_TYPE_POSTFIX), false));
                    referenceFieldTypeItem.setExpression(new NullValue());
                    selectItemReplacement.add(referenceFieldTypeItem);
                } else if (selectExpressionItem.getExpression() instanceof StringValue) {
                    StringValue stringValue = (StringValue) selectExpressionItem.getExpression();
                    RdbmsId id = new RdbmsId(stringValue.getValue());

                    SelectExpressionItem referenceFieldIdItem = new SelectExpressionItem();
                    referenceFieldIdItem.setAlias(alias);
                    referenceFieldIdItem.setExpression(new LongValue(String.valueOf(id.getId())));
                    selectItemReplacement.set(0, referenceFieldIdItem);

                    SelectExpressionItem referenceFieldTypeItem = new SelectExpressionItem();
                    referenceFieldTypeItem.setAlias(new Alias(getServiceColumnName(DaoUtils.unwrap(alias.getName()), REFERENCE_TYPE_POSTFIX), false));
                    referenceFieldTypeItem.setExpression(new LongValue(String.valueOf(id.getTypeId())));
                    selectItemReplacement.add(referenceFieldTypeItem);
                } else {
                    throw new DaoException("Unsupported Id constant type " +
                            selectExpressionItem.getExpression().getClass().getName() +
                            ". Only null and string constants can represent Id");
                }
            }

            addAndReplaceColumns(withName, isWith, selectItems, selectExpressionItem, selectItemReplacement);
        }

        plainSelect.setSelectItems(selectItems);

        if (plainSelect.getGroupBy() != null) {
            List<Expression> groupByExpressions = new ArrayList<>(plainSelect.getGroupBy().getGroupByExpressions());
            for (Expression expression : plainSelect.getGroupBy().getGroupByExpressions()) {
                groupByExpressions.add(expression);

                if (!(expression instanceof Column)) {
                    continue;
                }

                SelectExpressionItem selectExpressionItem = new SelectExpressionItem(expression);
                SelectExpressionItem serviceExpressionItem = getServiceExpression(selectExpressionItem, columnToConfigMapForSelectItems);

                if (serviceExpressionItem == null) {
                    selectExpressionItem = findSelectExpressionItemByAlias(plainSelect, ((Column) expression).getColumnName());
                    if (selectExpressionItem != null) {
                        serviceExpressionItem = getServiceExpression(selectExpressionItem, columnToConfigMapForSelectItems);
                    }
                }
                if (serviceExpressionItem != null) {
                    if (serviceExpressionItem.getAlias() != null && serviceExpressionItem.getAlias().getName() != null &&
                            !serviceExpressionItem.getAlias().getName().isEmpty()) {
                        groupByExpressions.add(new Column(new Table(), serviceExpressionItem.getAlias().getName()));
                    } else {
                        groupByExpressions.add(serviceExpressionItem.getExpression());
                    }
                }
            }

            plainSelect.getGroupBy().setGroupByExpressions(groupByExpressions);
        }
    }

    private void changeColumnName(Column column, List<FieldData> fieldDataList) {
        if (fieldDataList != null) {
            if (fieldDataList.size() == 1) {
                if (fieldDataList.get(0).getFieldConfig() instanceof ReferenceFieldConfig) {
                    column.setColumnName(wrap(getReferenceTypeColumnName(column.getColumnName())));
                }
            } else {
                // См. коммент в запросе CMSIX-7023, почему сделал так
                boolean allTheSame = areAllTheSameClass(fieldDataList);
                if (allTheSame) {
                    if (fieldDataList.get(0).getFieldConfig() instanceof ReferenceFieldConfig) {
                        column.setColumnName(wrap(getReferenceTypeColumnName(column.getColumnName())));
                        return;
                    }
                }
                throw new FatalException("Multiple field configs found with different types. It's unsupported configuration for this moment");
            }
        }
    }

    /**
     * Сравнивает все {@link FieldConfig} по классу. Если одинаковые, то мы знаем как обрабатывать
     * Если <code>NULL</code>, то будем считать, что это нормально (хотя на самом деле нет) см. коммент CMSIX-7023
     *
     * @param fieldDataList
     * @return
     */
    private boolean areAllTheSameClass(List<FieldData> fieldDataList) {
        boolean allTheSame = true;
        Class<?> firstClass = null;
        for (FieldData fd : fieldDataList) {
            if (fd.getFieldConfig() == null) {
                continue;
            }
            Class<?> clazz = fd.getFieldConfig().getClass();
            if (firstClass == null) {
                firstClass = clazz;
                continue;
            }
            if (!clazz.equals(firstClass)) {
                allTheSame = false;
            }
        }
        return allTheSame;
    }

    private void addAndReplaceColumns(String tableName, boolean isWith, List<SelectItem> selectItems,
                                      SelectExpressionItem selectItem, List<SelectItem> expressionReplacement) {
        if (!isWith) {
            Map<String, List<SelectItem>> aliasServiceColumns = withItemColumnReplacementMap.get(tableName);
            if (aliasServiceColumns != null) {
                List<SelectItem> replacementColumns = aliasServiceColumns.get(getSelectExpressionItemName(selectItem));
                if (replacementColumns != null && replacementColumns.size() > 1) {
                    selectItems.addAll(replacementColumns);
                    return;
                }
            }
        } else {
            Map<String, List<SelectItem>> replacementMap = withItemColumnReplacementMap.get(tableName);
            if (replacementMap == null) {
                replacementMap = new HashMap<>();
                withItemColumnReplacementMap.put(tableName, replacementMap);
            }

            List<SelectItem> withSelectItemsReplacement = new ArrayList<>(expressionReplacement.size());
            for (SelectItem replacementSelectItem : expressionReplacement) {
                if (replacementSelectItem instanceof SelectExpressionItem &&
                        ((SelectExpressionItem) replacementSelectItem).getExpression() instanceof Column) {
                    Column replacementColumn = (Column) ((SelectExpressionItem) replacementSelectItem).getExpression();
                    Column newReplacementColumn = new Column();
                    newReplacementColumn.setColumnName(replacementColumn.getColumnName());

                    withSelectItemsReplacement.add(new SelectExpressionItem(newReplacementColumn));
                } else {
                    withSelectItemsReplacement.add(replacementSelectItem);
                }
            }

            replacementMap.put(getSelectExpressionItemName(selectItem), withSelectItemsReplacement);
        }

        selectItems.addAll(expressionReplacement);
    }

    private String getSelectExpressionItemName(SelectExpressionItem selectExpressionItem) {
        String result = null;
        if (selectExpressionItem.getAlias() != null && selectExpressionItem.getAlias().getName() != null) {
            result = selectExpressionItem.getAlias().getName();
        } else {
            if (selectExpressionItem.getExpression() instanceof Column) {
                result = ((Column) selectExpressionItem.getExpression()).getColumnName();
            } else {
                result = selectExpressionItem.toString();
            }
        }
        return DaoUtils.unwrap(result);
    }

    private SelectExpressionItem findSelectExpressionItemByAlias(PlainSelect plainSelect, String alias) {
        for (Object selectItem : plainSelect.getSelectItems()) {
            if (!(selectItem instanceof SelectExpressionItem)) {
                continue;
            }

            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
            if (selectExpressionItem.getAlias() != null && alias.equals(selectExpressionItem.getAlias().getName())) {
                return selectExpressionItem;
            }
        }

        return null;
    }

    private SelectExpressionItem getServiceExpression(SelectExpressionItem selectExpressionItem,
                                                      Map<String, List<FieldData>> columnToConfigMapForSelectItems) {
        if (!(selectExpressionItem.getExpression() instanceof Column)) {
            return null;
        }

        Column column = (Column) selectExpressionItem.getExpression();
        // предотвращаем добавление в SELECT поля access_object_it_type
        if (column.getColumnName().equalsIgnoreCase(DomainObjectDao.ACCESS_OBJECT_ID)) {
            return null;
        }

        final List<FieldData> fieldDataList = columnToConfigMapForSelectItems.get(getSelectExpressionItemName(selectExpressionItem));
        if (fieldDataList != null) {
            if (fieldDataList.size() == 1) {
                return changeSelectExpressionItem(selectExpressionItem, fieldDataList);
            } else {
                boolean allTheSame = areAllTheSameClass(fieldDataList);
                if (allTheSame) {
                    return changeSelectExpressionItem(selectExpressionItem, fieldDataList);
                }
                throw new FatalException("Multiple field configs found with different types. It's unsupported configuration for this moment");
            }
        }

        return null;
    }

    private SelectExpressionItem changeSelectExpressionItem(SelectExpressionItem selectExpressionItem, List<FieldData> fieldDataList) {
        FieldConfig fieldConfig = fieldDataList.get(0).getFieldConfig();
        if (fieldConfig instanceof ReferenceFieldConfig) {
            return createReferenceFieldTypeSelectItem(selectExpressionItem);
        } else if (fieldConfig instanceof DateTimeWithTimeZoneFieldConfig) {
            return createTimeZoneIdSelectItem(selectExpressionItem);
        }
        return null;
    }

    private String getColumnName(Column column) {
        return DaoUtils.unwrap(Case.toLower(column.getColumnName()));
    }

    private boolean containsExpressionInPlainselect(PlainSelect plainSelect, SelectExpressionItem selectExpressionItem) {
        String plainSelectQuery = plainSelect.toString().replaceAll("\\s+", " ").trim();
        String selectExpressionItemQuery = selectExpressionItem.toString().replaceAll("\\s+", " ").trim();
        return plainSelectQuery.indexOf(selectExpressionItemQuery) > 0;
    }

    private boolean caseExpressionReturnsId(CaseExpression caseExpression, PlainSelect plainSelect) {
        for (Expression whenExpression : caseExpression.getWhenClauses()) {
            WhenClause whenClause = (WhenClause) whenExpression;
            if (whenClause.getThenExpression() instanceof Column) {
                Column column = (Column) whenClause.getThenExpression();
                FieldConfig fieldConfig = configurationExplorer.getFieldConfig(
                        getDOTypeName(plainSelect, column, false), DaoUtils.unwrap(column.getColumnName()));

                if (fieldConfig instanceof ReferenceFieldConfig) {
                    return true;
                }
            }
        }

        if (caseExpression.getElseExpression() instanceof Column) {
            Column column = (Column) caseExpression.getElseExpression();
            FieldConfig fieldConfig = configurationExplorer.getFieldConfig(
                    getDOTypeName(plainSelect, column, false), DaoUtils.unwrap(column.getColumnName()));

            if (fieldConfig instanceof ReferenceFieldConfig) {
                return true;
            }
        }

        return false;
    }

    private void buildColumnToConfigMapInPlainSelect(PlainSelect plainSelect,
                                                     Map<String, List<FieldData>> columnToConfigMap) {
        for (Object selectItem : plainSelect.getSelectItems()) {
            if (!(selectItem instanceof SelectExpressionItem)) {
                continue;
            }

            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;

            if (selectExpressionItem.getExpression() instanceof Column) {
                Column column = (Column) selectExpressionItem.getExpression();

                String fieldName = DaoUtils.unwrap(Case.toLower(column.getColumnName()));
                String columnName = selectExpressionItem.getAlias() != null ?
                        DaoUtils.unwrap(Case.toLower(selectExpressionItem.getAlias().getName())) : fieldName;

                if (columnToConfigMap.get(columnName) == null) {
                    FieldData fieldData = getFieldData(plainSelect, selectExpressionItem);
                    final List<FieldData> fieldDataList = columnToConfigMap.computeIfAbsent(columnName, v -> new ArrayList<>());
                    if (fieldData != null) {
                        // Раньше в Map лежал null (см. историю), но пусть лучше будет пустой list
                        fieldData.setColumnName(columnName.toLowerCase());
                        fieldDataList.add(fieldData);
                    }
                }
            } else if (selectExpressionItem.getExpression() instanceof SubSelect) {

                SubSelect subSelect = (SubSelect) selectExpressionItem.getExpression();
                if (subSelect.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plainSubSelect = (PlainSelect) subSelect.getSelectBody();
                    buildColumnToConfigMapUsingVisitor(plainSubSelect, columnToConfigMap);
                }

            } else if (selectExpressionItem.getExpression() instanceof CaseExpression) {
                CaseExpression caseExpression = (CaseExpression) selectExpressionItem.getExpression();
                boolean returnsId = caseExpressionReturnsId(caseExpression, plainSelect);
                if (returnsId) {
                    addReferenceFieldConfig(selectExpressionItem, columnToConfigMap);
                }
            } else if (selectExpressionItem.getAlias() != null &&
                    selectExpressionItem.getAlias().getName().endsWith(REFERENCE_POSTFIX) &&
                    (selectExpressionItem.getExpression() instanceof NullValue ||
                            selectExpressionItem.getExpression() instanceof LongValue)) {
                addReferenceFieldConfig(selectExpressionItem, columnToConfigMap);
            }
        }
    }

    private void addReferenceFieldConfig(SelectExpressionItem selectExpressionItem, Map<String, List<FieldData>> columnToConfigMap) {
        if (selectExpressionItem.getAlias() == null) {
            return;
        }

        String name = DaoUtils.unwrap(Case.toLower(selectExpressionItem.getAlias().getName()));
        if (columnToConfigMap.get(name) == null) {
            FieldConfig fieldConfig = new ReferenceFieldConfig();
            fieldConfig.setName(name);
            final FieldData fieldData = new FieldData(fieldConfig, CASE_UNDEFINED_DO_NAME);
            fieldData.setColumnName(name);
            FieldDataHelper.addFieldData(columnToConfigMap, fieldData);
        }
    }

    private void addIdBasedFiltersInPlainSelect(PlainSelect plainSelect, List<? extends Filter> filterValues,
                                                String idField) {
        if (filterValues == null) {
            return;
        }

        Table whereTable = new Table();
        whereTable.setName(getTableAlias(plainSelect));

        Expression where = plainSelect.getWhere();
        if (where == null) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new LongValue("1"));
            equalsTo.setRightExpression(new LongValue("1"));
            plainSelect.setWhere(equalsTo);
            where = plainSelect.getWhere();
        }

        for (Filter filter : filterValues) {
            String name = filter.getFilter();
            if (!name.startsWith("idsIncluded") && !name.startsWith("idsExcluded")) {
                continue;
            }

            Expression expression = null;

            if (!filter.getCriterionKeys().isEmpty()) {
                InExpression inExpression = new InExpression();
                inExpression.setNot(name.startsWith("idsExcluded"));
                inExpression.setLeftExpression(new Column(whereTable, wrap(idField)));
                inExpression
                        .setRightItemsList(new ExpressionList(singletonList(new Column(CollectionsDaoImpl.PARAM_NAME_PREFIX + filter.getFilter()
                                + "0"))));
                expression = inExpression;
            }

            // Пустой фильтр разрешенных идентификаторов, поэтому используем
            // условие, гарантирующее пустой результат
            if (expression == null && filter instanceof IdsIncludedFilter) {
                EqualsTo emptyResultExpression = new EqualsTo();
                emptyResultExpression.setLeftExpression(new LongValue("0"));
                emptyResultExpression.setRightExpression(new LongValue("1"));

                expression = emptyResultExpression;
            }

            if (expression != null) {
                where = new AndExpression(where, expression);
            }
        }

        plainSelect.setWhere(where);
    }

    /**
     * Возвращает имя таблицы, в которой находится данная колонка. Если алиас
     * для таблицы не был использован в SQL запросе, то берется название первой
     * таблицы в FROM выражении. Если поле вычисляемое, то возвращается null.
     * Если тип доменного объекта не найден, возвращается null.
     *
     * @param plainSelect SQL запрос
     * @param column      колока (поле) в запросе.
     */
    public static String getDOTypeName(PlainSelect plainSelect, Column column, boolean forSubSelect) {
        Column clonedColumn = new Column(column.getTable(), column.getColumnName());

        if (hasEvaluatedExpressionWithSameAliasInPlainSelect(plainSelect, clonedColumn)) {
            return null;
        }

        if (plainSelect.getFromItem() instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
            PlainSelect plainSubSelect = getPlainSelect(subSelect.getSelectBody());
            // если название таблицы у колонки совпадает с алиасом подзапроса,
            // то таблица данной колоки - первая таблица
            // из From выражения подзапроса.
            if (clonedColumn.getTable() != null && clonedColumn.getTable().getName() != null && subSelect.getAlias() != null
                    && clonedColumn.getTable().getName().equals(subSelect.getAlias().getName())) {
                clonedColumn.setTable(null);
                return getDOTypeName(plainSubSelect, clonedColumn, true);
            }

            String resultType = processJoins(plainSelect, clonedColumn);
            if (resultType != null) {
                return resultType;
            } else {
                return getDOTypeName(plainSubSelect, clonedColumn, true);
            }
        } else if (plainSelect.getFromItem() instanceof Table) {
            Table fromItem = (Table) plainSelect.getFromItem();

            if (forSubSelect) {
                for (Object selectItem : plainSelect.getSelectItems()) {
                    if (selectItem instanceof AllColumns || selectItem instanceof AllTableColumns) {
                        return DaoUtils.unwrap(fromItem.getName());
                    }
                }
            }

            // если колока колока не имеет названия таблицы - берется перая
            // таблица из from выражения
            if ((clonedColumn.getTable() == null || clonedColumn.getTable().getName() == null)) {
                return DaoUtils.unwrap(fromItem.getName());
            }

            if ((fromItem.getAlias() != null && clonedColumn.getTable().getName().equals(fromItem.getAlias().getName())) ||
                    clonedColumn.getTable().getName().equals(fromItem.getName())) {
                return DaoUtils.unwrap(fromItem.getName());
            }

            List<?> joinList = plainSelect.getJoins();

            if (joinList != null) {
                for (Object joinObject : joinList) {
                    Join join = (Join) joinObject;

                    if (join.getRightItem() instanceof SubSelect) {
                        SubSelect subSelect = (SubSelect) join.getRightItem();
                        if (clonedColumn.getTable() != null && clonedColumn.getTable().getName() != null) {
                            if (subSelect.getAlias() != null && clonedColumn.getTable().getName().equalsIgnoreCase(subSelect.getAlias().getName())) {
                                PlainSelect plainSubSelect = getPlainSelect(subSelect.getSelectBody());
                                return getDOTypeName(plainSubSelect, clonedColumn, true);
                            }
                        }
                    } else if (join.getRightItem() instanceof Table) {
                        Table joinTable = (Table) join.getRightItem();
                        if (joinTable.getAlias() != null
                                && clonedColumn.getTable().getName().equalsIgnoreCase(joinTable.getAlias().getName()) ||
                                clonedColumn.getTable().getName().equalsIgnoreCase(joinTable.getName())) {
                            return DaoUtils.unwrap(joinTable.getName());
                        }

                    }

                }
            }
        }
        return null;
    }

    private static String processJoins(PlainSelect plainSelect, Column column) {
        if (column == null) {
            return null;
        }
        List<?> joinList = plainSelect.getJoins();

        if (joinList != null) {
            for (Object joinObject : joinList) {
                Join join = (Join) joinObject;

                if (join.getRightItem() instanceof SubSelect) {
                    SubSelect subSelect = (SubSelect) join.getRightItem();
                    if (column.getTable() != null && column.getTable().getName() != null) {
                        if (subSelect.getAlias() != null && column.getTable().getName().equalsIgnoreCase(subSelect.getAlias().getName())) {
                            PlainSelect plainSubSelect = getPlainSelect(subSelect.getSelectBody());
                            return getDOTypeName(plainSubSelect, column, true);
                        }
                    }
                } else if (join.getRightItem() instanceof Table) {
                    try {
                        Table joinTable = (Table) join.getRightItem();
                        if (joinTable.getAlias() != null && column.getTable() != null && column.getTable().getName() != null) {
                            if (column.getTable().getName().equals(joinTable.getAlias().getName()) ||
                                    column.getTable().getName().equals(joinTable.getName())) {
                                return DaoUtils.unwrap(joinTable.getName());
                            }

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

            }
        }
        return null;
    }

    /**
     * Проверяет, объявлена ли колонка (основного SQL запроса) в подзапросе как
     * вычисляемая колонка
     */
    private static boolean hasEvaluatedExpressionWithSameAliasInPlainSelect(PlainSelect plainSelect, Column column) {
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                if (!SelectExpressionItem.class.equals(selectItem.getClass())) {
                    continue;
                }
                SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
                Expression expressionValue = selectExpressionItem.getExpression();
                if (isEvaluatedExpression(expressionValue)) {

                    if (selectExpressionItem.getAlias() != null) {
                        String columnName = DaoUtils.unwrap(column.getColumnName());
                        String expressionAliasName = DaoUtils.unwrap(selectExpressionItem.getAlias().getName());
                        if (columnName.equalsIgnoreCase(expressionAliasName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isEvaluatedExpression(Expression expressionValue) {
        return expressionValue instanceof StringValue || expressionValue instanceof Function || expressionValue instanceof Concat
                || expressionValue instanceof CaseExpression || expressionValue instanceof CastExpression;
    }

    // TODO
    private FieldData getFieldData(PlainSelect plainSelect, SelectExpressionItem selectExpressionItem) {
        if (!(selectExpressionItem.getExpression() instanceof Column)) {
            return null;
        }

        Column column = (Column) selectExpressionItem.getExpression();

        if (plainSelect.getFromItem() instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
            PlainSelect plainSubSelect = getPlainSelect(subSelect.getSelectBody());
            return getFieldConfigFromSubSelect(plainSubSelect, column);
        } else if (plainSelect.getFromItem() instanceof Table) {
            String fieldName = DaoUtils.unwrap(Case.toLower(column.getColumnName()));
            final String doTypeName = getDOTypeName(plainSelect, column, false);
            final FieldConfig fieldConfig = configurationExplorer.getFieldConfig(doTypeName, fieldName);
            return new FieldData(fieldConfig, doTypeName);
        }

        return null;
    }

    private FieldData getFieldConfigFromSubSelect(PlainSelect plainSelect, Column upperLevelColumn) {
        for (Object selectItem : plainSelect.getSelectItems()) {
            if (selectItem instanceof AllColumns) {
                String fieldName = DaoUtils.unwrap(Case.toLower(upperLevelColumn.getColumnName()));
                final String doTypeName = getDOTypeName(plainSelect, upperLevelColumn, true);
                final FieldConfig fieldConfig = configurationExplorer.getFieldConfig(doTypeName, fieldName);
                return new FieldData(fieldConfig, doTypeName);
            } else if (!(selectItem instanceof SelectExpressionItem)) {
                continue;
            }

            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;

            if (selectExpressionItem.getAlias() != null && upperLevelColumn.getColumnName().equals(selectExpressionItem.getAlias().getName())) {
                if (selectExpressionItem.getExpression() instanceof CaseExpression) {
                    CaseExpression caseExpression = (CaseExpression) selectExpressionItem.getExpression();
                    if (caseExpressionReturnsId(caseExpression, plainSelect)) {
                        ReferenceFieldConfig fieldConfig = new ReferenceFieldConfig();
                        final String name = DaoUtils.unwrap(Case.toLower(selectExpressionItem.getAlias().getName()));
                        fieldConfig.setName(name);
                        final FieldData fieldData = new FieldData(fieldConfig, CASE_UNDEFINED_DO_NAME);
                        fieldData.setColumnName(name);
                        return fieldData;
                    }
                }

                return getFieldData(plainSelect, selectExpressionItem);
            }

            if (selectExpressionItem.getExpression() instanceof Column) {
                Column column = (Column) selectExpressionItem.getExpression();
                if (upperLevelColumn.getColumnName().equals(column.getColumnName())) {
                    return getFieldData(plainSelect, selectExpressionItem);
                }
            }
        }

        return null;
    }

    private static String getTableAlias(PlainSelect plainSelect) {
        if (plainSelect.getFromItem() instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
            return subSelect.getAlias() != null ? subSelect.getAlias().getName() : null;
        } else if (plainSelect.getFromItem() instanceof Table) {
            Table table = (Table) plainSelect.getFromItem();
            return table.getAlias() != null ? table.getAlias().getName() : table.getName();
        }

        return "";
    }

    private SelectExpressionItem createReferenceFieldTypeSelectItem(SelectExpressionItem selectExpressionItem) {
        return generateServiceColumnExpression(selectExpressionItem, REFERENCE_TYPE_POSTFIX);
    }

    private SelectExpressionItem createTimeZoneIdSelectItem(SelectExpressionItem selectExpressionItem) {
        return generateServiceColumnExpression(selectExpressionItem, TIME_ID_ZONE_POSTFIX);
    }

    private SelectExpressionItem generateServiceColumnExpression(SelectExpressionItem selectExpressionItem, String postfix) {
        Column column = (Column) selectExpressionItem.getExpression();

        SelectExpressionItem referenceFieldTypeItem = new SelectExpressionItem();

        if (selectExpressionItem.getAlias() != null) {
            String serviceColumnAlias = createServiceColumnAlias(selectExpressionItem, postfix);
            referenceFieldTypeItem.setAlias(new Alias(serviceColumnAlias, false));
        }

        String tableName = column.getTable() != null && column.getTable().getName() != null ? column.getTable().getName() : "";
        String columnName = getServiceColumnName(DaoUtils.unwrap(column.getColumnName()), postfix);
        referenceFieldTypeItem.setExpression(new Column(new Table(tableName), columnName));

        return referenceFieldTypeItem;
    }

    private String createServiceColumnAlias(SelectExpressionItem selectExpressionItem, String postfix) {
        String baseAlias = DaoUtils.unwrap(selectExpressionItem.getAlias().getName());
        return wrap(getServiceColumnName(baseAlias, postfix));
    }

    private static PlainSelect getPlainSelect(SelectBody selectBody) {
        if (selectBody instanceof PlainSelect) {
            return (PlainSelect) selectBody;
        } else if (selectBody instanceof SetOperationList) {
            SetOperationList union = (SetOperationList) selectBody;
            return (PlainSelect) union.getSelects().get(0);
        } else {
            throw new IllegalArgumentException("Unsupported type of select body: " + selectBody.getClass());
        }
    }

    /**
     * Метод создан для уменьшения количества раз распраршивания SQL запроса.
     * Принимает распаршенный запрос в качестве параметра.
     */
    private SelectBody processSelectBody(SelectBody selectBody, QueryProcessor processor) {
        return processor.process(selectBody, null);
    }

    private Select processSelect(Select select, QueryProcessor processor) {
        select = processor.process(select);
        return select;
    }

    private abstract static class QueryProcessor {

        public Select process(Select select) {
            if (select.getWithItemsList() != null) {
                for (WithItem withItem : select.getWithItemsList()) {
                    process(withItem.getSelectBody(), withItem.getName());
                }
            }

            process(select.getSelectBody(), null);
            return select;
        }

        public SelectBody process(SelectBody selectBody, String withItemName) {
            if (selectBody.getClass().equals(PlainSelect.class)) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                processPlainSelect(plainSelect, withItemName);
                return plainSelect;
            } else if (selectBody.getClass().equals(SetOperationList.class)) {
                SetOperationList union = (SetOperationList) selectBody;
                List<?> plainSelects = union.getSelects();
                for (Object plainSelect : plainSelects) {
                    processPlainSelect((PlainSelect) plainSelect, withItemName);
                }
                return union;
            } else {
                throw new IllegalArgumentException("Unsupported type of select body: " + selectBody.getClass());
            }
        }

        protected abstract void processPlainSelect(PlainSelect plainSelect, String withItemName);

    }

    private class AddServiceColumnsQueryProcessor extends QueryProcessor {

        @Override
        protected void processPlainSelect(PlainSelect plainSelect, String withItemName) {
            addServiceColumnsInPlainSelect(plainSelect, withItemName);
        }
    }

}
