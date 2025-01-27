package ru.intertrust.cm.core.business.impl.search;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Vitaliy.Orlov on 03.07.2019.
 */
@Component
public class SolrSearchConfiguration {
    @Autowired
    private Environment env;

    @Value("${search.solr.url}")
    private String solrUrl;

    @Value("${search.solr.enable:true}")
    private boolean isSolrEnable;

    @Value("${search.solr.timeout:180000}")
    private int queryTimeout;

    @Value("${search.solr.data}")
    private String solrDataDir;

    @Value("${search.solr.home}")
    private String solrHome;

    @Value("${search.solr.collection:CM5}")
    private String solrCollection;

    @Value("${search.solr.cntx.servers:}")
    private String solrCntxServers;

    @Value("${search.solr.cntx.mode:legacy}")
    private String solrCntxMode;

    private Map<String, SolrCntxServerDescription> solrCntxServerDescriptionMap = null;

    public String getSolrUrl() {
        return solrUrl;
    }

    public void setSolrUrl(String solrUrl) {
        this.solrUrl = solrUrl;
    }

    public boolean isSolrEnable() {
        return isSolrEnable;
    }

    public void setSolrEnable(boolean solrEnable) {
        isSolrEnable = solrEnable;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public String getSolrDataDir() {
        return solrDataDir;
    }

    public void setSolrDataDir(String solrDataDir) {
        this.solrDataDir = solrDataDir;
    }

    public String getSolrHome() {
        return solrHome;
    }

    public void setSolrHome(String solrHome) {
        this.solrHome = solrHome;
    }

    public String getSolrCollection() {
        return solrCollection;
    }

    public void setSolrCollection(String solrCollection) {
        this.solrCollection = solrCollection;
    }

    public String getSolrCntxServers() {
        return solrCntxServers;
    }

    public void setSolrCntxServers(String solrCntxServers) {
        this.solrCntxServers = solrCntxServers;
    }

    public String getSolrCntxMode() {
        return solrCntxMode != null ? solrCntxMode.trim() : "legacy";
    }

    public void setSolrCntxMode(String solrCntxMode) {
        this.solrCntxMode = solrCntxMode;
    }

    public Map<String, SolrCntxServerDescription> getSolrCntxServerDescriptionMap() {
        if (solrCntxServerDescriptionMap == null) {
            solrCntxServerDescriptionMap = new HashMap<>();
            String solrCntxServers = getSolrCntxServers();
            if (solrCntxServers != null) {
                String solrCntxServer[] = solrCntxServers.split(";");
                for (String key : solrCntxServer) {
                    if (key != null && !key.trim().isEmpty()) {
                        solrCntxServerDescriptionMap.put(key.trim(),  new SolrCntxServerDescription(key.trim(), env));
                    }
                }
            }
        }
        return solrCntxServerDescriptionMap;
    }

}
