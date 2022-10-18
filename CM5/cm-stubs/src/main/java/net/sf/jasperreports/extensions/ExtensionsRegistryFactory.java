package net.sf.jasperreports.extensions;

import net.sf.jasperreports.engine.JRPropertiesMap;

public interface ExtensionsRegistryFactory {
    ExtensionsRegistry createRegistry(String registryId, JRPropertiesMap properties);
}
