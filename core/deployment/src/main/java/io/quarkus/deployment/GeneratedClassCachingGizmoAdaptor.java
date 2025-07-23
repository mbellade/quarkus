package io.quarkus.deployment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;

public class GeneratedClassCachingGizmoAdaptor extends GeneratedClassGizmoAdaptor {
    private Map<String, byte[]> cache;

    public GeneratedClassCachingGizmoAdaptor(
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            boolean applicationClass) {
        super(generatedClasses, applicationClass);
    }

    @Override
    public void write(String className, byte[] bytes) {
        // Cache the generated class bytes
        if (cache == null) {
            cache = new ConcurrentHashMap<>();
        }
        final byte[] old = cache.put(className, bytes);
        assert old == null : "Class " + className + " was already generated and cached";
        super.write(className, bytes);
    }

    public byte[] getClassData(String className) {
        if (cache == null) {
            // If cache is not initialized, return null
            return null;
        }
        return cache.get(className.replace('.', '/'));
    }
}
