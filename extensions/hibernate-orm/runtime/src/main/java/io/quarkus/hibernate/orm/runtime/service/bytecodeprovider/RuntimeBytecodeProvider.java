package io.quarkus.hibernate.orm.runtime.service.bytecodeprovider;

import java.util.Map;

import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.bytecode.enhance.spi.Enhancer;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.bytecode.spi.ProxyFactoryFactory;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.property.access.spi.PropertyAccess;

import io.quarkus.hibernate.orm.runtime.customized.QuarkusRuntimeProxyFactoryFactory;
import io.quarkus.hibernate.orm.runtime.reflection.ReflectionOptimizerDefinitions;

final class RuntimeBytecodeProvider implements BytecodeProvider {

    private final QuarkusRuntimeProxyFactoryFactory statefulProxyFactory;
    private final ReflectionOptimizerDefinitions optimizerDefinitions;

    public RuntimeBytecodeProvider(QuarkusRuntimeProxyFactoryFactory statefulProxyFactory,
            ReflectionOptimizerDefinitions optimizerDefinitions) {
        this.statefulProxyFactory = statefulProxyFactory;
        this.optimizerDefinitions = optimizerDefinitions;
    }

    @Override
    public ProxyFactoryFactory getProxyFactoryFactory() {
        return statefulProxyFactory;
    }

    @Override
    public ReflectionOptimizer getReflectionOptimizer(
            Class clazz,
            String[] getterNames,
            String[] setterNames,
            Class[] types) {
        return optimizerDefinitions.getReflectionOptimizerForClass(clazz);
    }

    @Override
    public ReflectionOptimizer getReflectionOptimizer(Class<?> clazz, Map<String, PropertyAccess> propertyAccessMap) {
        return optimizerDefinitions.getReflectionOptimizerForClass(clazz);
    }

    @Override
    public Enhancer getEnhancer(EnhancementContext enhancementContext) {
        return null;
    }
}
