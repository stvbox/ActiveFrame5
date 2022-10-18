package ru.intertrust.common.versioncollector;

import java.util.ArrayList;
import java.util.List;

public enum DiscoveryVersionCollectorService {
    INSTANCE;

    public List<ComponentVersion> getAllVersions() {
        return new ArrayList<>();
    }
}
