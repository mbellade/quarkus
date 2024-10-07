package io.quarkus.hibernate.orm.runtime.reflection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.IndexBackref;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.internal.PropertyAccessStrategyBackRefImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyIndexBackRefImpl;
import org.hibernate.property.access.spi.BuiltInPropertyAccessStrategies;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.service.ServiceRegistry;
import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.runtime.LazyBytecodeProvider;

public class ReflectionOptimizerDefinitions {
    private final Map<Class<?>, ReflectionOptimizer> reflectionOptimizerMap;
    private static final Logger LOGGER = Logger.getLogger(ReflectionOptimizerDefinitions.class.getName());

    private ReflectionOptimizerDefinitions(Map<Class<?>, ReflectionOptimizer> reflectionOptimizerMap) {
        this.reflectionOptimizerMap = reflectionOptimizerMap;
    }

    public static ReflectionOptimizerDefinitions createFromMetadata(MetadataImplementor metadata,
            ServiceRegistry serviceRegistry, LazyBytecodeProvider lazyBytecode) {
        StrategySelector strategySelector = serviceRegistry.getService(StrategySelector.class);
        Map<Class<?>, ReflectionOptimizer> optimizerMap = new HashMap<>();
        for (PersistentClass pc : metadata.getEntityBindings()) {
            final Class<?> mappedClass = pc.getMappedClass();

            // todo marco : building this is duplicating work that will be done during Hibernate final bootstrap
            //  maybe we can avoid doing it? I'm not sure
            Map<String, PropertyAccess> propertyAccessMap = new HashMap<>();
            for (Property property : pc.getPropertyClosure()) {
                propertyAccessMap.put(property.getName(), makePropertyAccess(property, mappedClass, strategySelector));
            }
            ReflectionOptimizer reflectionOptimizer = lazyBytecode.get().getReflectionOptimizer(mappedClass,
                    propertyAccessMap);
            optimizerMap.put(mappedClass, reflectionOptimizer);

            // todo marco : the following block should be deleted, it's just temporary tests
            if (reflectionOptimizer != null) {
                ReflectionOptimizer.InstantiationOptimizer instantiationOptimizer = reflectionOptimizer
                        .getInstantiationOptimizer();
                ReflectionOptimizer.AccessOptimizer accessOptimizer = reflectionOptimizer.getAccessOptimizer();

                Object instance = null;
                if (instantiationOptimizer != null) {
                    instance = instantiationOptimizer.newInstance();
                }
                if (accessOptimizer != null) {
                    final String[] propertyNames = accessOptimizer.getPropertyNames();
                    if (instance != null) {
                        final Object[] propertyValues = accessOptimizer.getPropertyValues(instance);
                        assert propertyValues != null;
                    }
                }
            }
        }

        // todo marco : do the same for embeddables
        //  problem: polymorphic embeddables have some custom logic
        //  solution: unify creation of property access map in public-static method within Hibernate?

        return new ReflectionOptimizerDefinitions(optimizerMap);
    }

    private static PropertyAccess makePropertyAccess(Property bootAttributeDescriptor, Class<?> mappedClass,
            StrategySelector strategySelector) {
        PropertyAccessStrategy strategy = bootAttributeDescriptor.getPropertyAccessStrategy(mappedClass);

        if (strategy == null) {
            final String propertyAccessorName = bootAttributeDescriptor.getPropertyAccessorName();
            if (StringHelper.isNotEmpty(propertyAccessorName)) {
                // handle explicitly specified attribute accessor
                strategy = strategySelector.resolveStrategy(PropertyAccessStrategy.class, propertyAccessorName);
            } else {
                if (bootAttributeDescriptor instanceof Backref) {
                    final Backref backref = (Backref) bootAttributeDescriptor;
                    strategy = new PropertyAccessStrategyBackRefImpl(backref.getCollectionRole(), backref
                            .getEntityName());
                } else if (bootAttributeDescriptor instanceof IndexBackref) {
                    final IndexBackref indexBackref = (IndexBackref) bootAttributeDescriptor;
                    strategy = new PropertyAccessStrategyIndexBackRefImpl(
                            indexBackref.getCollectionRole(),
                            indexBackref.getEntityName());
                } else {
                    // for now...
                    strategy = BuiltInPropertyAccessStrategies.MIXED.getStrategy();
                }
            }
        }

        if (strategy == null) {
            throw new HibernateException(
                    String.format(
                            Locale.ROOT,
                            "Could not resolve PropertyAccess for attribute `%s#%s`",
                            mappedClass.getName(),
                            bootAttributeDescriptor.getName()));
        }

        return strategy.buildPropertyAccess(mappedClass, bootAttributeDescriptor.getName(), true);
    }

    public ReflectionOptimizer getReflectionOptimizerForClass(Class<?> persistentClass) {
        return reflectionOptimizerMap.get(persistentClass);
    }
}
