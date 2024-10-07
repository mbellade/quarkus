package io.quarkus.hibernate.orm.runtime.optimizers;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.mapping.PersistentClass;

/**
 * @author Marco Belladelli
 */
public class InstantiatorDefinitions {
    public static final String INSTANTIATOR_SUFFIX = "$QuarkusInstantiationOptimizer";

    private final Map<String, InstantiatorHolder> optimizers;

    private InstantiatorDefinitions(Map<String, InstantiatorHolder> optimizers) {
        this.optimizers = optimizers;
    }

    public static InstantiatorDefinitions createFromMetadata(MetadataImplementor metadata) {
        Map<String, InstantiatorHolder> optimizerMap = new HashMap<>();
        metadata.visitRegisteredComponents(component -> {
            String className = component.getComponentClassName();
            if (className != null) {
                optimizerMap.put(className, InstantiatorHolder.of(className));
            }
        });
        for (PersistentClass pc : metadata.getEntityBindings()) {
            String className = pc.getClassName();
            if (className != null) {
                optimizerMap.put(className, InstantiatorHolder.of(className));
            }
        }
        return new InstantiatorDefinitions(optimizerMap);
    }

    public Map<String, InstantiatorHolder> getOptimizers() {
        return optimizers;
    }

    public static class InstantiatorHolder {
        private final Constructor<?> constructor;

        private InstantiatorHolder(Constructor<?> constructor) {
            this.constructor = constructor;
        }

        public static InstantiatorHolder of(String className) {
            try {
                Class<?> instantiatorClass = Class.forName(className + INSTANTIATOR_SUFFIX, false,
                        Thread.currentThread().getContextClassLoader());
                return new InstantiatorHolder(instantiatorClass.getDeclaredConstructor());
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // should never happen
                throw new RuntimeException(e);
            }
        }

        public Constructor<?> getConstructor() {
            return constructor;
        }
    }
}
