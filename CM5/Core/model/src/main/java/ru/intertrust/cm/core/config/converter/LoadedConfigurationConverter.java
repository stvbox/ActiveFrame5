package ru.intertrust.cm.core.config.converter;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.stream.InputNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.intertrust.cm.core.config.DomainObjectTypeConfig;
import ru.intertrust.cm.core.config.base.Configuration;
import ru.intertrust.cm.core.config.base.LoadedConfiguration;
import ru.intertrust.cm.core.config.base.TopLevelConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Конвертер для сериализации загруженной ранее конфигурации.
 * Игнорирует ошибки сериализации за исключением ошибок в {@link ru.intertrust.cm.core.config.DomainObjectTypeConfig}
 * @author vmatsukevich
 *         Date: 7/11/13
 *         Time: 8:26 PM
 */
public class LoadedConfigurationConverter extends ConfigurationConverter {

    private Logger logger = LoggerFactory.getLogger(LoadedConfigurationConverter.class);

    private final List<String> creationErrorList = new ArrayList<>();
    private final List<String> creationWarningList = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public LoadedConfiguration read(InputNode inputNode) throws Exception {
        LoadedConfiguration configuration = create();

        ConfigurationClassesCache configurationClassesCache = ConfigurationClassesCache.getInstance();
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        InputNode nexNode;

        while ((nexNode = inputNode.getNext()) != null) {
            Class nodeClass = configurationClassesCache.getClassByTagName(nexNode.getName());
            if (nodeClass == null) {
                continue;
            }

            TopLevelConfig deserializedObject = null;
            try {
                deserializedObject = (TopLevelConfig) serializer.read(nodeClass, nexNode);
            } catch(Exception e) {
                InputNode nameNode = nexNode.getAttribute("name");
                String errorMessage = "Failed to serialize configuration item: type='" + nexNode.getName() + "'" +
                        (nameNode != null && nameNode.getValue() != null ? ", name='" + nameNode.getValue() + "'" : "");

                if (DomainObjectTypeConfig.class.equals(nodeClass)) {
                    creationErrorList.add(errorMessage);
                } else {
                    creationWarningList.add(errorMessage);
                }
            }

            if (deserializedObject != null) {
                getList(configuration).add(deserializedObject);
            }
        }

        if (!creationWarningList.isEmpty()) {
            logger.warn("Failed to deserialize loaded configuration",
                    new ConfigurationDeserializationException(creationWarningList));
        }

        if (!creationErrorList.isEmpty()) {
            throw new ConfigurationDeserializationException(creationErrorList);
        }

        return configuration;
    }

    @Override
    public LoadedConfiguration create() {
        return new LoadedConfiguration();
    }

}