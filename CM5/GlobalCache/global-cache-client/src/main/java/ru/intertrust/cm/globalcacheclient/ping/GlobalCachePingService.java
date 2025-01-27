package ru.intertrust.cm.globalcacheclient.ping;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ru.intertrust.cm.core.business.api.dto.CacheInvalidation;
import ru.intertrust.cm.core.business.api.dto.globalcache.PingData;
import ru.intertrust.cm.core.business.api.dto.globalcache.PingRequest;
import ru.intertrust.cm.core.dao.api.ClusterManagerDao;
import ru.intertrust.cm.core.model.FatalException;
import ru.intertrust.cm.globalcacheclient.cluster.GlobalCacheJmsHelper;

/**
 * Сервис проверки отправки и получения уведомлений о сбросе кэша в кластере
 * @author larin
 *
 */
@RestController
public class GlobalCachePingService {
    private static final Logger logger = LoggerFactory.getLogger(GlobalCachePingService.class);
    
    @Autowired
    private ClusterManagerDao clusterManagerDao;

    @Autowired
    private GlobalCacheJmsHelper jmsHelper;
    
    
    private static Map<String, PingResult> pingResults = new Hashtable<String, PingResult>();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public GlobalCachePingService() {
        logger.info("Init Global Cache Ping Service");
    }
    
    @RequestMapping(value = "/globalcache/ping/{timeout}", method = RequestMethod.GET)
    public PingResult ping(@PathVariable(value = "timeout") Integer timeout) {
        try {
            logger.info("Ping start from " + clusterManagerDao.getNodeName());

            String nodeName = clusterManagerDao.getNodeName();
            if (nodeName == null) {
                nodeName = "not_config";
            }
            // Создаем объект результата проверки и сохраняем его в глобальной статической мапе
            PingResult result = new PingResult();
            String requestId = UUID.randomUUID().toString();
            pingResults.put(requestId, result);
            result.setRequestId(requestId);
            result.setInitiator(nodeName);
            result.setPingDate(dateFormat.format(new Date()));
            
            // Формируем ping запрос
            CacheInvalidation pingMessage = new CacheInvalidation();
            PingData pingData = new PingData();
            pingData.setRequest(new PingRequest());
            pingData.getRequest().setNodeName(nodeName);
            pingData.getRequest().setSendTime(System.currentTimeMillis());
            pingData.getRequest().setRequestId(requestId);
            pingMessage.setDiagnosticData(pingData);

            //Отправляем ping запрос
            jmsHelper.sendClusterNotification(pingMessage);
            
            // Ожидаем ответы не более заданного таймаута
            Thread.currentThread().sleep(timeout);
            
            //Удаляем из мапы информацию о запросе
            pingResults.remove(requestId);
            
            //Возвращаем результат
            logger.info("Ping finish");
            return result;
        } catch (Exception ex) {
            throw new FatalException("Error execute ping command", ex);
        }
    }
    
    /**
     * Сохраняем результат ping ответа
     * @param requestId
     * @param nodeInfo
     */
    public static void setPingResult(String requestId, PingNodeInfo nodeInfo) {
        PingResult result = pingResults.get(requestId);
        if (result != null) {
            result.getNodeInfos().add(nodeInfo);
        }
    }


}
