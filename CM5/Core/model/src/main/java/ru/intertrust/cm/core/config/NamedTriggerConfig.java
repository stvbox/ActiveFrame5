package ru.intertrust.cm.core.config;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import ru.intertrust.cm.core.config.base.TopLevelConfig;

@Root(name = "named-trigger")
public class NamedTriggerConfig  implements TopLevelConfig {

    @Attribute(required = true)
    private String name;

    @Element(name = "trigger", required = true)
    private TriggerConfig trigger = new TriggerConfig();    

    public TriggerConfig getTrigger() {
        return trigger;
    }

    public void setTrigger(TriggerConfig trigger) {
        this.trigger = trigger;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        NamedTriggerConfig other = (NamedTriggerConfig) obj;
        
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (trigger == null) {
            if (other.trigger != null) {
                return false;
            }
        } else if (!trigger.equals(other.trigger)) {
            return false;
        }
        return true;
    }    
}
