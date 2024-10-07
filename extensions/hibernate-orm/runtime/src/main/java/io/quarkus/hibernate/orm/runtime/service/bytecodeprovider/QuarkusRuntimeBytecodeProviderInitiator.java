package io.quarkus.hibernate.orm.runtime.service.bytecodeprovider;

import java.util.Map;

import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import io.quarkus.hibernate.orm.runtime.customized.QuarkusRuntimeProxyFactoryFactory;
import io.quarkus.hibernate.orm.runtime.reflection.ReflectionOptimizerDefinitions;

public final class QuarkusRuntimeBytecodeProviderInitiator implements StandardServiceInitiator<BytecodeProvider> {

    private final QuarkusRuntimeProxyFactoryFactory statefulProxyFactory;
    private final ReflectionOptimizerDefinitions optimizerDefinitions;

    public QuarkusRuntimeBytecodeProviderInitiator(QuarkusRuntimeProxyFactoryFactory statefulProxyFactory,
            ReflectionOptimizerDefinitions optimizerDefinitions) {
        this.statefulProxyFactory = statefulProxyFactory;
        this.optimizerDefinitions = optimizerDefinitions;
    }

    @Override
    public BytecodeProvider initiateService(Map configurationValues, ServiceRegistryImplementor registry) {
        //This one disables any use of bytecode enhancement at runtime, but is slightly more lenient
        //than the "none" option which will throw an exception on any attempt of using it;
        //also, needs to carry the statefulProxyFactory.
        return new RuntimeBytecodeProvider(statefulProxyFactory, optimizerDefinitions);
    }

    @Override
    public Class<BytecodeProvider> getServiceInitiated() {
        return BytecodeProvider.class;
    }

}
