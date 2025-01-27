package ru.intertrust.cm.core.business.impl.search;

import ru.intertrust.cm.core.business.api.dto.SearchFilter;
import ru.intertrust.cm.core.business.api.dto.SearchQuery;

import java.util.List;

/**
 * Интерфейс предназначен для реализации классами (адаптерами), выполняющими преобразование
 * фильтров расширенного поиска ({@link SearchFilter}) в части запроса к Solr.
 * Реализующие классы вызываются при формировании поискового запроса к Solr по {@link SearchQuery}.
 * Выбор нужного адаптера осуществляет {@link ImplementorFactory} по конфигурации в beans.xml.
 * 
 * @author apirozhkov
 *
 * @param <F> Класс поисковых фильтров, обрабатываемых данным адаптером
 */
public interface FilterAdapter<F extends SearchFilter> {

    /**
     * Формирует строку, которая может быть вставлена в поисковый запрос Solr (параметр q=).
     * Строка должна быть составлена таким образом, чтобы обеспечить корректный синтаксис запроса
     * с учётом того, что она может быть объединена с другими подобными строками через оператор AND или OR.
     * Реализация должна вернуть null, если добавление фильтра в запрос не требуется. 
     * 
     * <p>В метод передаётся также исходный поисковый запрос (содержащий, скорее всего, и тот фильтр,
     * который передан в первом параметре), данные из которого могут быть необходимы для корректного
     * построения фильтра. Например, в некоторых случаях может понадобиться информация о запрошенных
     * областях поиска.
     * 
     * @param filter Преобразуемый фильтр
     * @param query Исходный поисковый запрос
     * @return Строка, вставляемая в запрос к Solr, или null
     */
    String getFilterString(F filter, SearchQuery query);

    /**
     * Определяет, является ли переданный фильтр составным, т.е. включает несколько разных полей.
     * Обработка составных фильтров осуществляется специальными адаптерами, реализующими интерфейс
     * {@link CompositeFilterAdapter}. Таким образом, классы, реализующие <i>только</i> {@link FilterAdapter},
     * должны возвращать false. Для составных фильтров метод {@link #getFilterString(SearchFilter, SearchQuery)}
     * не вызывается.
     * @param filter Обрабатываемый фильтр
     * @return true, если фильтр включает несколько полей
     */
    boolean isCompositeFilter(F filter);

    /**
     * Получение списка имен полей, которые использует данный фильтр
     * @param filter
     * @param query
     * @return
     */
    List<String> getFieldNames(F filter, SearchQuery query);

}
