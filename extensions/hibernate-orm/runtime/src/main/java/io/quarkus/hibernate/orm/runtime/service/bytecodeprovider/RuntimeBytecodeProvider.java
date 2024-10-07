package io.quarkus.hibernate.orm.runtime.service.bytecodeprovider;

import java.util.Map;

import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.bytecode.enhance.spi.Enhancer;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.bytecode.spi.ProxyFactoryFactory;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.property.access.spi.PropertyAccess;

import io.quarkus.hibernate.orm.runtime.customized.QuarkusRuntimeProxyFactoryFactory;
import io.quarkus.hibernate.orm.runtime.optimizers.InstantiatorDefinitions;

final class RuntimeBytecodeProvider implements BytecodeProvider {

    private final QuarkusRuntimeProxyFactoryFactory statefulProxyFactory;
    private final InstantiatorDefinitions instantiatorDefinitions;

    public RuntimeBytecodeProvider(QuarkusRuntimeProxyFactoryFactory statefulProxyFactory,
            InstantiatorDefinitions instantiatorDefinitions) {
        this.statefulProxyFactory = statefulProxyFactory;
        this.instantiatorDefinitions = instantiatorDefinitions;
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
        return null;
    }

    @Override
    public ReflectionOptimizer getReflectionOptimizer(Class<?> clazz, Map<String, PropertyAccess> propertyAccessMap) {
        InstantiatorDefinitions.InstantiatorHolder holder = instantiatorDefinitions.getOptimizers().get(clazz.getName());
        if (holder != null) {
            try {
                ReflectionOptimizer.InstantiationOptimizer optimizer = (ReflectionOptimizer.InstantiationOptimizer) holder
                        .getConstructor().newInstance();
                return new ReflectionOptimizer() {
                    @Override
                    public InstantiationOptimizer getInstantiationOptimizer() {
                        return optimizer;
                    }

                    @Override
                    public AccessOptimizer getAccessOptimizer() {
                        return null;
                    }
                };
            } catch (Exception e) {
                // ignored
            }
        }
        return null;
    }

    @Override
    public Enhancer getEnhancer(EnhancementContext enhancementContext) {
        return null;
    }
}
