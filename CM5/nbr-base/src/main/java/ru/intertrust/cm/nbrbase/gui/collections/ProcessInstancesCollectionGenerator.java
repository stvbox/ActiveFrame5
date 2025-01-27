package ru.intertrust.cm.nbrbase.gui.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import ru.intertrust.cm.core.business.api.CollectionsService;
import ru.intertrust.cm.core.business.api.ProcessService;
import ru.intertrust.cm.core.business.api.dto.BooleanValue;
import ru.intertrust.cm.core.business.api.dto.DateTimeValue;
import ru.intertrust.cm.core.business.api.dto.Filter;
import ru.intertrust.cm.core.business.api.dto.GenericIdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.dto.SortOrder;
import ru.intertrust.cm.core.business.api.dto.StringValue;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.business.api.workflow.ProcessInstanceInfo;
import ru.intertrust.cm.core.config.BooleanFieldConfig;
import ru.intertrust.cm.core.config.DateTimeFieldConfig;
import ru.intertrust.cm.core.config.FieldConfig;
import ru.intertrust.cm.core.config.StringFieldConfig;
import ru.intertrust.cm.core.dao.api.component.CollectionDataGenerator;
import ru.intertrust.cm.core.dao.api.component.ServerComponent;
import ru.intertrust.cm.core.gui.model.DomainObjectMappingId;

@ServerComponent(name = "process.instances.collection")
public class ProcessInstancesCollectionGenerator implements CollectionDataGenerator {

    @Autowired
    private ProcessService processService;

    @Autowired
    private CollectionsService collectionsService;

    @Override
    public IdentifiableObjectCollection findCollection(List<? extends Filter> filters, SortOrder sortOrder, int offset, int limit) {
        GenericIdentifiableObjectCollection result = new GenericIdentifiableObjectCollection();

        String name = null;
        Date startDateBegin = null;
        Date startDateEnd = null;
        Date finishDateBegin = null;
        Date finishDateEnd = null;

        for (Filter filter : filters) {
            if (filter.getFilter().equalsIgnoreCase("byName")){
                name = ((StringValue) filter.getCriterion(0)).get();
            }else if (filter.getFilter().equalsIgnoreCase("bySatrtDate")){
                startDateBegin = ((DateTimeValue) filter.getCriterion(0)).get();
                startDateEnd = ((DateTimeValue) filter.getCriterion(1)).get();
            }else if (filter.getFilter().equalsIgnoreCase("byFinishDate")){
                finishDateBegin = ((DateTimeValue) filter.getCriterion(0)).get();
                finishDateEnd = ((DateTimeValue) filter.getCriterion(1)).get();
            }
        }

        List<ProcessInstanceInfo> instancesInfos = processService.getProcessInstanceInfos(
                offset, limit, name, startDateBegin, startDateEnd, finishDateBegin, finishDateEnd, sortOrder);

        List<FieldConfig> fieldConfigs = new ArrayList<>();
        fieldConfigs.add(new StringFieldConfig("name", true, false, 256, false));
        fieldConfigs.add(new StringFieldConfig("version", true, false, 256, false));
        fieldConfigs.add(new DateTimeFieldConfig("start_date", false, false));
        fieldConfigs.add(new DateTimeFieldConfig("finish_date", false, false));
        fieldConfigs.add(new BooleanFieldConfig("suspended", false, false));
        result.setFieldsConfiguration(fieldConfigs);

        int rowNumber = 0;
        for (ProcessInstanceInfo instancesInfo : instancesInfos) {
            result.setId(rowNumber, new DomainObjectMappingId("process_instance", instancesInfo.getId()));
            result.set("name", rowNumber, new StringValue(instancesInfo.getName()));
            result.set("version", rowNumber, new StringValue(getProcessDefinitionVersion(instancesInfo.getDefinitionId())));
            result.set("start_date", rowNumber, new DateTimeValue(instancesInfo.getStart()));
            result.set("finish_date", rowNumber, new DateTimeValue(instancesInfo.getFinish()));
            result.set("suspended", rowNumber, new BooleanValue(instancesInfo.isSuspended()));

            rowNumber++;
        }

        return result;
    }

    private String getProcessDefinitionVersion(String definitionId) {
        IdentifiableObjectCollection collection = collectionsService.findCollectionByQuery(
                "select version from process_definition where definition_id = {0}",
                Collections.singletonList(new StringValue(definitionId)));
        if (collection.size() > 0){
            return collection.get(0).getString("version");
        }
        return null;
    }

    @Override
    public int findCollectionCount(List<? extends Filter> filterValues) {
        return 0;
    }
}
