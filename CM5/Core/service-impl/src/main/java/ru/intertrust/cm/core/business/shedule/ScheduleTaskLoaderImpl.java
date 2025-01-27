package ru.intertrust.cm.core.business.shedule;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Remote;
import javax.ejb.Singleton;
import javax.interceptor.Interceptors;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;

import ru.intertrust.cm.core.business.api.ClusterManager;
import ru.intertrust.cm.core.business.api.ScheduleService;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.GenericDomainObject;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObject;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.schedule.ScheduleTask;
import ru.intertrust.cm.core.business.api.schedule.ScheduleTaskConfig;
import ru.intertrust.cm.core.business.api.schedule.ScheduleTaskDefaultParameters;
import ru.intertrust.cm.core.business.api.schedule.ScheduleTaskHandle;
import ru.intertrust.cm.core.business.api.schedule.ScheduleTaskLoader;
import ru.intertrust.cm.core.business.api.schedule.SheduleTaskReestrItem;
import ru.intertrust.cm.core.business.api.schedule.SheduleType;
import ru.intertrust.cm.core.dao.access.AccessControlService;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.api.ClassPathScanService;
import ru.intertrust.cm.core.dao.api.CollectionsDao;
import ru.intertrust.cm.core.dao.api.DomainObjectDao;
import ru.intertrust.cm.core.model.ScheduleException;
import ru.intertrust.cm.core.util.SpringBeanAutowiringInterceptor;
import ru.intertrust.cm.core.util.SpringApplicationContext;

/**
 * EJB загрузчик классов периодических заданий
 *
 * @author larin
 */
@Singleton
@Local(ScheduleTaskLoader.class)
@Remote(ScheduleTaskLoader.Remote.class)
@Interceptors(SpringBeanAutowiringInterceptor.class)
public class ScheduleTaskLoaderImpl implements ScheduleTaskLoader, ScheduleTaskLoader.Remote {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleTaskLoaderImpl.class);

    private Hashtable<String, SheduleTaskReestrItem> reestr = new Hashtable<String, SheduleTaskReestrItem>();

    @Autowired
    private DomainObjectDao domainObjectDao;

    @Autowired
    private CollectionsDao collectionsDao;

    @Autowired
    private AccessControlService accessControlService;

    @Autowired
    private ClassPathScanService scanner;

    //@Autowired
    //private SpringApplicationContext springApplicationContext;

    //Флаг готовности сервиса к работе
    private boolean isLoaded = false;

    @Autowired
    private ClusterManager clusterManager;

    //Флаг активности сервиса. Прикладное приложение должно само активизировать сервис после старта
    @Value("${schedule.service.enableOnStart:true}")
    private boolean enable;

    private int lastNodeIndex = -1;

    /**
     * Установка spring контекста
     */
    @Override
    public void load() throws BeansException {
        initReestr();
        initStorage();
        isLoaded = true;
    }

    /**
     * Инициализация доменных объектов
     */
    private void initStorage() {
        for (SheduleTaskReestrItem item : reestr.values()) {
            //Автоматически создаем только Singleton задачи
            if (item.getConfiguration().type() == SheduleType.Singleton) {
                //Получение доменного объекта по классу
                DomainObject taskDo = getTaskDomainObject(item.getScheduleTask().getClass().getName());
                if (taskDo == null) {
                    createTaskDomainObject(item);
                }
            }
        }

        //Деактивируем все задачи что есть в базе, если их нет в реестре (удалили класс или модуль)
        AccessToken accessToken = accessControlService.createSystemAccessToken(this.getClass().getName());
        IdentifiableObjectCollection collection =
                collectionsDao.findCollectionByQuery("select id, task_class, name from schedule where active = 1", 0, 0, accessToken);
        for (IdentifiableObject row : collection) {
            SheduleTaskReestrItem item = reestr.get(row.getString("task_class"));
            if (item == null) {
                DomainObject taskDo = domainObjectDao.find(row.getId(), accessToken);
                taskDo.setBoolean("active", false);
                domainObjectDao.save(taskDo, accessToken);
                logger.warn("Schedule service not found class " + row.getString("task_class") + ". Deactivate schedule task " + row.getString("name"));
            }
        }
    }

    public DomainObject createTaskDomainObject(SheduleTaskReestrItem item) {
        return createTaskDomainObject(item, null);
    }

    public DomainObject createTaskDomainObject(SheduleTaskReestrItem item, String name) {
        AccessToken accessToken = accessControlService.createSystemAccessToken(this.getClass().getName());

        DomainObject task = createDomainObject("schedule");
        if (name == null) {
            task.setString(ScheduleService.SCHEDULE_NAME, item.getConfiguration().name());
        } else {
            task.setString(ScheduleService.SCHEDULE_NAME, name);
        }
        task.setString(ScheduleService.SCHEDULE_TASK_CLASS, item.getScheduleTask().getClass().getName());
        task.setLong(ScheduleService.SCHEDULE_TASK_TYPE, item.getConfiguration().type().toLong());
        task.setString(ScheduleService.SCHEDULE_YEAR, item.getConfiguration().year());
        task.setString(ScheduleService.SCHEDULE_MONTH, item.getConfiguration().month());
        task.setString(ScheduleService.SCHEDULE_DAY_OF_MONTH, item.getConfiguration().dayOfMonth());
        task.setString(ScheduleService.SCHEDULE_DAY_OF_WEEK, item.getConfiguration().dayOfWeek());
        task.setString(ScheduleService.SCHEDULE_HOUR, item.getConfiguration().hour());
        task.setString(ScheduleService.SCHEDULE_MINUTE, item.getConfiguration().minute());
        task.setLong(ScheduleService.SCHEDULE_TIMEOUT, item.getConfiguration().timeout());
        task.setLong(ScheduleService.SCHEDULE_PRIORITY, item.getConfiguration().priority());
        task.setString(ScheduleService.SCHEDULE_PARAMETERS, getDefaultParameters(item.getConfiguration()));
        task.setBoolean(ScheduleService.SCHEDULE_ACTIVE, item.getConfiguration().active());
        task.setBoolean(ScheduleService.SCHEDULE_ALL_NODES, item.getConfiguration().allNodes());
        task.setBoolean(ScheduleService.SCHEDULE_TASK_TRANSACTIONAL_MANAGEMENT, item.getConfiguration().taskTransactionalManagement());
        return domainObjectDao.save(task, accessToken);
    }

    protected String getDefaultParameters(ScheduleTask configuration) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Strategy strategy = new AnnotationStrategy();
            Serializer serializer = new Persister(strategy);
            String result = null;
            Class<? extends ScheduleTaskDefaultParameters> defaultConfigClass = configuration.configClass();
            if (!defaultConfigClass.equals(ScheduleTaskDefaultParameters.class)) {
                ScheduleTaskDefaultParameters configClass = defaultConfigClass.newInstance();
                out = new ByteArrayOutputStream();
                ScheduleTaskConfig config = new ScheduleTaskConfig();
                config.setParameters(configClass.getDefaultParameters());

                serializer.write(config, out);
                result = out.toString("utf8");
            }

            return result;
        } catch (Exception ex) {
            throw new ScheduleException(
                    "Error on get schedule task default parameters", ex);
        } finally {
            try {
                out.close();
            } catch (Exception ignoreEx) {
            }
        }
    }

    /**
     * Создание нового доменного обьекта переданного типа
     *
     * @param type
     * @return
     */
    private DomainObject createDomainObject(String type) {
        GenericDomainObject domainObject = new GenericDomainObject();
        domainObject.setTypeName(type);
        Date currentDate = new Date();
        domainObject.setCreatedDate(currentDate);
        domainObject.setModifiedDate(currentDate);
        return domainObject;
    }

    private DomainObject getTaskDomainObject(String taskClass) {
        DomainObject result = null;
        String query = "select t.id from schedule t where t.task_class = '" + taskClass + "'";
        AccessToken accessToken = accessControlService.createSystemAccessToken(this.getClass().getName());
        IdentifiableObjectCollection collection = collectionsDao.findCollectionByQuery(query, 0, 0, accessToken);
        if (collection.size() > 0) {
            result = domainObjectDao.find(collection.get(0).getId(), accessToken);
        }
        return result;
    }

    /**
     * инициализация реестра переодических задач
     */
    private void initReestr() {
        try {
            // Сканирование класспаса
            for (BeanDefinition bd : scanner.findClassesByAnnotation(ScheduleTask.class)) {
                String className = bd.getBeanClassName();
                // Получение найденного класса
                Class<?> scheduleTaskClass = Class.forName(className);
                // Получение анотации ScheduleTask
                ScheduleTask annatation = (ScheduleTask) scheduleTaskClass
                        .getAnnotation(ScheduleTask.class);

                // Проверка наличия анотации в классе
                if (annatation != null) {

                    if (ScheduleTaskHandle.class.isAssignableFrom(scheduleTaskClass)) {

                        // создаем экземпляр класса Добавляем класс как спринговый бин с поддержкой autowire
                        ScheduleTaskHandle scheduleTask = (ScheduleTaskHandle) SpringApplicationContext.getContext()
                                .getAutowireCapableBeanFactory().createBean(scheduleTaskClass,
                                        AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
                        reestr.put(scheduleTaskClass.getName(), new SheduleTaskReestrItem(scheduleTask, annatation));
                        logger.info("Register ScheduleTaskHandle=" + scheduleTaskClass.getName());
                    }
                }
            }

        } catch (Exception ex) {
            throw new ScheduleException("Error on init schedule task classes", ex);
        }

    }

    @Lock(LockType.READ)
    @Override
    public List<SheduleTaskReestrItem> getSheduleTaskReestrItems(boolean singletonOnly) {
        List<SheduleTaskReestrItem> result = new ArrayList<SheduleTaskReestrItem>();
        for (SheduleTaskReestrItem item : reestr.values()) {
            if (singletonOnly) {
                if (item.getConfiguration().type() == SheduleType.Singleton) {
                    result.add(item);
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @Lock(LockType.READ)
    @Override
    public SheduleTaskReestrItem getSheduleTaskReestrItem(String className) {
        return reestr.get(className);
    }

    @Lock(LockType.READ)
    @Override
    public ScheduleTaskHandle getSheduleTaskHandle(String className) {
        return reestr.get(className).getScheduleTask();
    }

    @Lock(LockType.READ)
    @Override
    public boolean isLoaded() {
        return isLoaded;
    }

    @Lock(LockType.READ)
    @Override
    public boolean isEnable() {
        return enable;
    }

    @Override
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * Получение следующей ноды из списка серверов имеющих роль
     * schedule_executor
     *
     * @return
     */
    @Override
    public String getNextNodeId() {
        String result = null;
        Set<String> nodeIds = clusterManager.getNodesWithRole(ScheduleService.SCHEDULE_EXECUTOR_ROLE_NAME);
        if (!nodeIds.isEmpty()) {
            int nextIndex = lastNodeIndex + 1 > nodeIds.size() - 1 ? 0 : lastNodeIndex + 1;
            lastNodeIndex = nextIndex;
            result = nodeIds.toArray(new String[nodeIds.size()])[nextIndex];
        }
        return result;
    }

    public String getNextNodeId(List<String> taskNodeNames) {
        String result = null;
        Set<String> nodeIds = clusterManager.getNodesWithRole(ScheduleService.SCHEDULE_EXECUTOR_ROLE_NAME);
        List<String> filteredNodeIds = new ArrayList<>();
        for (String nodeId : nodeIds) {
            if (taskNodeNames.contains(clusterManager.getNodesInfo().get(nodeId).getNodeName())) {
                filteredNodeIds.add(nodeId);
            }
        }
        if (filteredNodeIds.size() == 1) {
            result = filteredNodeIds.get(0);
        } else if (filteredNodeIds.size() > 1) {
            result = filteredNodeIds.get(new Random().nextInt(filteredNodeIds.size()));
        }
        return result;
    }

    @Lock(LockType.READ)
    @Override
    public boolean taskNeedsTransaction(String className) {
        return !reestr.get(className).getConfiguration().taskTransactionalManagement();
    }
}
