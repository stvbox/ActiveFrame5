package ru.intertrust.cm.core.config.migration;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import ru.intertrust.cm.core.config.base.TopLevelConfig;

/**
 * Java модель конфигурации миграционного скрипта
 */
@Root(name = "migration-script")
public class MigrationScriptConfig implements TopLevelConfig {
    @Attribute(name = "sequence-number")
    private int sequenceNumber;

    @Element(name = "before-auto-migration", required = false)
    private BeforeAutoMigrationConfig beforeAutoMigrationConfig;

    @Element(name = "after-auto-migration", required = false)
    private AfterAutoMigrationConfig afterAutoMigrationConfig;

    private String moduleName;

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public BeforeAutoMigrationConfig getBeforeAutoMigrationConfig() {
        return beforeAutoMigrationConfig;
    }

    public void setBeforeAutoMigrationConfig(BeforeAutoMigrationConfig beforeAutoMigrationConfig) {
        this.beforeAutoMigrationConfig = beforeAutoMigrationConfig;
    }

    public AfterAutoMigrationConfig getAfterAutoMigrationConfig() {
        return afterAutoMigrationConfig;
    }

    public void setAfterAutoMigrationConfig(AfterAutoMigrationConfig afterAutoMigrationConfig) {
        this.afterAutoMigrationConfig = afterAutoMigrationConfig;
    }

    @Override
    public String getName() {
        return (moduleName == null ? "" : moduleName) + String.valueOf(sequenceNumber);
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
}
