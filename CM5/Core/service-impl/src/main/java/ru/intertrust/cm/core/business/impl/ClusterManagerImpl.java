package ru.intertrust.cm.core.business.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.annotation.security.RunAs;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.interceptor.Interceptors;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ejb.interceptor.SpringBeanAutowiringInterceptor;

import ru.intertrust.cm.core.business.api.ClusterManager;
import ru.intertrust.cm.core.business.api.CrudService;
import ru.intertrust.cm.core.business.api.InterserverLockingService;
import ru.intertrust.cm.core.business.api.dto.ClusterNodeInfo;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.StringValue;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.model.FatalException;

/**
 * Класс менеджера кластера. Записывает данные о своей активности, кроме того
 * Класс следит чтобы был только один ведущий менеджер кластера. Все ноды
 * постоянно обращаются к таблицам и следят за тем чтобы был один активный
 * менеджер кластера и если обнаружится что активный недоступен берут его
 * обязанности на себя
 * @author larin
 *
 */
@Singleton(name = "ClusterManager")
@Interceptors(SpringBeanAutowiringInterceptor.class)
@RunAs("system")
@Startup
public class ClusterManagerImpl implements ClusterManager{
    final private static org.slf4j.Logger logger = LoggerFactory.getLogger(ClusterManagerImpl.class);
    final private static long INTERVAL = 30 * 1000;
    final private static long DEAD_INTERVAL = 60 * 1000;
    final private static String TIMER_NAME = ClusterManager.class.getName();
    final private static String ALL_ROLE = "all";
    final private static String CLUSTER_MANAGER_LOCK_KEY = "CLUSTER_MANAGER_LOCK_KEY";

    private String nodeId;
    private Boolean mainClusterManager = null;
    private Set<String> activeRoles = new HashSet<String>(); 
    private Map<String, Boolean> roleRegister = new HashMap<String, Boolean>();
    
    //Реестр ролей и нод, которые имееют данную роль
    private Map<String, Set<String>> roleNodes = new HashMap<String, Set<String>>();
    private Map<String, Set<String>> nodeRoles = new HashMap<String, Set<String>>();

    private Map<String, ClusterNodeInfo> clusterNodeInfoMap = new HashMap<>();

    @Resource
    private TimerService timerService;

    @Autowired
    private CrudService crudService;
    
    @Autowired
    private ConfigurationLoader configurationLoader;

    @Autowired
    private InterserverLockingService interserverLockingService;

    private boolean isInternalLockUsed = false;

    private boolean isInitMainCluster = false;

    @org.springframework.beans.factory.annotation.Value("${cluster.manager:false}")
    private boolean canBeClusterMaster;

    @org.springframework.beans.factory.annotation.Value("${server.name:#{null}}")
    private String nodeName;


    @org.springframework.beans.factory.annotation.Value("${cluster.available.roles:" + ALL_ROLE + "}")
    private String availableRoles;

    @PostConstruct
    public void init() {
        nodeId = UUID.randomUUID().toString();
        timerService.createIntervalTimer(0, INTERVAL, new TimerConfig(TIMER_NAME, false));
        logger.debug("ClusterManager Timer initialized " + nodeId);
    }

    @PreDestroy
    public void deinit() {
        logger.debug("ClusterManager Timer uninitialize " + nodeId);
        //Удаляем информацию о ноде
        DomainObject nodeInfo = crudService.findAndLockByUniqueKey("cluster_node",
                Collections.singletonMap("node_id", (Value) new StringValue(nodeId)));
        crudService.delete(nodeInfo.getId());
    }

    @Override
    @Lock(LockType.READ)
    public boolean isMainServer() {
        return !configurationLoader.isConfigurationTableExist() || isMainClusterManager();
    }

    /**
     * Попадаем сюда раз в INTERVAL мс. Обновляем информацию о доступности ноды
     * и проверяем наличие ведущего менеджера
     * @param timer
     */
    @Timeout
    @Lock(LockType.READ)
    public void onTimeout(Timer timer) {

        mainClusterManager = !isCanBeMaster() ? false : interserverLockingService.selfSharedLock(CLUSTER_MANAGER_LOCK_KEY);

        if (configurationLoader.isConfigurationLoaded() && timer.getInfo() != null && timer.getInfo().equals(TIMER_NAME)) {
            //Обновляем информацию о ноде в базе
            DomainObject nodeInfo = crudService.findAndLockByUniqueKey("cluster_node",
                    Collections.singletonMap("node_id", (Value) new StringValue(nodeId)));
            if (nodeInfo == null) {
                nodeInfo = crudService.createDomainObject("cluster_node");
                nodeInfo.setString("node_id", nodeId);
                nodeInfo.setString("node_name", nodeName);
            }
            nodeInfo.setString("available_roles", availableRoles);
            nodeInfo.setTimestamp("last_available", new Date());
            crudService.save(nodeInfo);
            logger.debug("Update cluster node info for node " + nodeId);

            //Получаем информацию о менеджере кластера
            if(isCanBeMaster()){
                DomainObject clusterManagerInfo = getClusterManagerInfo();
                //Выполняем операции менеджера кластера
                if (isMainClusterManager()){
                    DomainObject lockedClusterManagerInfo = crudService.findAndLock(clusterManagerInfo.getId());
                    //Проверяем что объект никто не менял
                    if (lockedClusterManagerInfo.equals(clusterManagerInfo)) {
                        lockedClusterManagerInfo.setString("node_id", nodeId);
                        lockedClusterManagerInfo.setTimestamp("last_available", new Date());
                        crudService.save(lockedClusterManagerInfo);
                    }

                    manageRoles();
                }
            }
            //Зачитываем роли в реестр
            readRoleNodes();
        }
    }


    private boolean isCanBeMaster(){
        return canBeClusterMaster;
    }
    /**
     * Признак ведущего сервера.
     * @return true, если ведущий
     */
    private boolean isMainClusterManager() {
        return mainClusterManager != null ? mainClusterManager.booleanValue() : 
            (mainClusterManager = !isCanBeMaster() ? false : interserverLockingService.selfSharedLock(CLUSTER_MANAGER_LOCK_KEY)).booleanValue();
    }

    @Override
    public Map<String, ClusterNodeInfo> getNodesInfo() {
       return clusterNodeInfoMap;
    }

    @Override
    public ClusterNodeInfo getClusterManagerNodeInfo() {
        return clusterNodeInfoMap.get(getClusterManagerInfo().getString("node_id"));
    }

    /**
     * Зачитываем информацию о ролях текущей ноды
     */
    private void readRoleNodes() {
        activeRoles.clear();
        roleNodes.clear();
        nodeRoles.clear();

        clusterNodeInfoMap.clear();

        List<DomainObject> nodeInfos = crudService.findAll("cluster_node");
        for (DomainObject nodeInfo : nodeInfos) {

            Set<String> activeRoles = toSet(nodeInfo.getString("active_roles"));
            if (nodeInfo.getString("node_id").equals(nodeId)){
                this.activeRoles = activeRoles;
            }
            
            nodeRoles.put(nodeInfo.getString("node_id"), activeRoles);
            
            for (String activeRole : activeRoles) {
                Set<String> nodes = roleNodes.get(activeRole);
                if (nodes == null){
                    nodes = new HashSet<String>();
                    roleNodes.put(activeRole, nodes);
                }
                nodes.add(nodeInfo.getString("node_id"));
            }

            ClusterNodeInfo nInfo = buildNodeInfo(nodeInfo);
            clusterNodeInfoMap.put(nInfo.getNodeId(), nInfo);

        }
    }

    private ClusterNodeInfo buildNodeInfo(DomainObject nodeInfo) {
        ClusterNodeInfo info = new ClusterNodeInfo();
        info.setNodeId(nodeInfo.getString("node_id"));
        info.setLastAvailable(nodeInfo.getTimestamp("last_available"));
        info.setActiveRoles( toSet(nodeInfo.getString("active_roles")));
        info.setAvailableRoles( toSet(nodeInfo.getString("available_roles")));
        info.setNodeName(nodeInfo.getString("node_name"));
        return info;
    }

    /**
     * Операции менеджера кластеров. Распределение ролей
     */
    private void manageRoles() {
        //Информация о singleton ролях
        Set<String> singletonRolesReg = new HashSet<String>();
        //Информация о singleton ролях которые надо распределить. Нужен для определения нераспределенных ролей
        Set<String> singletonRoles = new HashSet<String>();
        //Информация о multyble ролях
        Set<String> multybleRolesReg = new HashSet<String>();
        //Информация о multyble ролях которые надо распределить. Нужен для определения нераспределенных ролей
        Set<String> multybleRoles = new HashSet<String>();
        //Цикл по всем ролям и построение списка синглтон и мултибле ролей
        for (String roleName : roleRegister.keySet()) {
            if (roleRegister.get(roleName)){
                singletonRoles.add(roleName);
                singletonRolesReg.add(roleName);
            }else{
                multybleRolesReg.add(roleName);
                multybleRoles.add(roleName);
            }
        }
        
        //Зачитываем информацию о всех нодах
        List<DomainObject> clusterNodeInfos = crudService.findAll("cluster_node");
        List<ActiveNodeInfo> activeNodeInfos = new ArrayList<ActiveNodeInfo>();

        //отсеиваем только работающие ноды
        for (DomainObject clusterNodeInfo : clusterNodeInfos) {
            if (!isDead(clusterNodeInfo)){
                ActiveNodeInfo activeNodeInfo = new ActiveNodeInfo(clusterNodeInfo); 
                activeNodeInfos.add(activeNodeInfo);
                //Проверяем не имеет ли нода синглтон роль
                //Цикл по ролям.                
                for (String singletonRole : singletonRolesReg) {
                    //Проверка не распределялась ли эта роль ранее, на другой ноде
                    if(!singletonRoles.contains(singletonRole)){
                        //Это роль была распределена ранее, удаляем ее из активных ролей ноды
                        activeNodeInfo.removeActiveRole(singletonRole);
                    }else if (activeNodeInfo.getActiveRoles().contains(singletonRole)){
                        //Роль распределена на текущую проверяемую ноду, удалить ее из списка нераспределенных
                        singletonRoles.remove(singletonRole);
                    }
                }
            }else{
                //Удаляем информацию о данной недоступной ноде
                crudService.delete(clusterNodeInfo.getId());
            }
        }
        
        //Распределение оставшихся singlton ролей
        for (ActiveNodeInfo activeNodeInfo : activeNodeInfos) {
            //Цикл по ролям                
            for (String singletonRole : singletonRolesReg) {
                //Проверка на то что еще не распределена роль
                if(singletonRoles.contains(singletonRole)){
                    if (activeNodeInfo.getAvailableRoles().contains(singletonRole) || activeNodeInfo.getAvailableRoles().contains(ALL_ROLE)){
                        //Нода подходит, даем ей эту роль
                        activeNodeInfo.addActiveRole(singletonRole);
                        //Роль распределена, удалить ее из списка нераспределенных
                        singletonRoles.remove(singletonRole);
                    }
                }
            }
        }
        
        //Проверяем остались ли нераспределенные singletonRole
        if (singletonRoles.size() > 0){
            logger.error("In claster not found nodes with roles: " + singletonRoles, new FatalException());
        }
        
        //Распределение multible ролей
        for (ActiveNodeInfo activeNodeInfo : activeNodeInfos) {
            for (String multybleRole : multybleRolesReg) {
                if (activeNodeInfo.getAvailableRoles().contains(multybleRole) || activeNodeInfo.getAvailableRoles().contains(ALL_ROLE)){
                    //Нода подходит, даем ей эту роль
                    activeNodeInfo.addActiveRole(multybleRole);
                    multybleRoles.remove(multybleRole);
                }
            }
        }

        //Проверяем остались ли нераспределенные multybleRole
        if (multybleRoles.size() > 0){
            logger.error("In claster not found nodes with roles: " + multybleRoles, new FatalException());
        }
        
        //Сохранение изменений
        for (ActiveNodeInfo activeNodeInfo : activeNodeInfos) {
            activeNodeInfo.saveChanges();
        }        
    }       
    
    /**
     * Немедленный повторный запуск по таймеру
     */
    private void reRunTimer() {
        timerService.createTimer(0, TIMER_NAME);
    }


    /**
     * Получение информации о текущем менеджере кластера. Если нет ни одного то создание записи
     * @return
     */
    private DomainObject getClusterManagerInfo() {
        List<DomainObject> clusterManagerInfos = crudService.findAll("cluster_manager");
        DomainObject clusterManager = null;
        if (clusterManagerInfos.size() == 0) {
            clusterManager = crudService.createDomainObject("cluster_manager");
            clusterManager.setString("node_id", nodeId);
            clusterManager.setTimestamp("last_available", new Date());
            //Это поле нужно для гарантированно единственной записи
            clusterManager.setLong("singleton_key", 0L);
            clusterManager = crudService.save(clusterManager);
            mainClusterManager = true;
            logger.info("Accept cluster manager role " + nodeId);
        } else {
            clusterManager = clusterManagerInfos.get(0);
        }
        return clusterManager;
    }

    /**
     * Проверка доступности ноды или менеджера кластера. Принимает на вход доменные объекты типа cluster_node или cluster_manager
     * @param domainObject
     * @return
     */
    private boolean isDead(DomainObject domainObject) {
        Date lastAvailable = domainObject.getTimestamp("last_available");
        return System.currentTimeMillis() - lastAvailable.getTime() > DEAD_INTERVAL;
    }

    @Override
    @Lock(LockType.READ)
    public boolean hasRole(String roleName) {
        return activeRoles.contains(roleName);
    }

    @Override
    public void regRole(String roleName, boolean singleton) {
        roleRegister.put(roleName, singleton);        
    }
    
    private class ActiveNodeInfo{
        private DomainObject nodeDomainObject;
        private Set<String> availableRoles;
        private Set<String> activeRoles;
        private boolean changed = false;
        
        public ActiveNodeInfo(DomainObject nodeInfo){
            nodeDomainObject = nodeInfo;
            availableRoles = toSet(nodeInfo.getString("available_roles"));
            activeRoles = toSet(nodeInfo.getString("active_roles"));
        }
        
        public void addActiveRole(String newRole){
            activeRoles.add(newRole);
            changed = true;
        }
        
        public void removeActiveRole(String oldRole){
            activeRoles.remove(oldRole);
            changed = true;
        }

        public Set<String> getActiveRoles(){
            return activeRoles;
        }
        
        public Set<String> getAvailableRoles(){
            return availableRoles;
        }

        public boolean isChanged(){
            return changed;
        }
        
        private String getActiveRolesAsSting(){
            String result = null;
            for (String role : activeRoles) {
                if (result == null){
                    result = role;
                }else{
                    result += "," + role;
                }
            }
            return result;
        }
        
        public void saveChanges(){
            if (changed){
                nodeDomainObject.setString("active_roles", getActiveRolesAsSting());
                crudService.save(nodeDomainObject);
            }
        }
        
        public DomainObject getNodeDomainObject() {
            return nodeDomainObject;
        }
    }

    @Override
    @Lock(LockType.READ)
    public String getNodeId() {
        return nodeId;
    }

    @Override
    @Lock(LockType.READ)
    public Set<String> getNodesWithRole(String roleName) {
        return roleNodes.get(roleName) == null ? new HashSet<String>() : roleNodes.get(roleName);
    }
    
    private Set<String> toSet(String value){
        Set<String> result = new HashSet<String>(); 
        if (value != null && !value.isEmpty()){
            String[] valuesArr = value.split(",");
            for (String item : valuesArr) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    @Lock(LockType.READ)
    public Set<String> getNodeIds() {        
        return nodeRoles.keySet();
    }
}
