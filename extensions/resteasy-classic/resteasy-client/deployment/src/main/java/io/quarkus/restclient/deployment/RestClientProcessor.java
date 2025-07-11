package io.quarkus.restclient.deployment;

import java.io.Closeable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Providers;
import jakarta.ws.rs.sse.SseEventSource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.annotation.RegisterProviders;
import org.eclipse.microprofile.rest.client.ext.DefaultClientHeadersFactoryImpl;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ProxyBuilderImpl;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ResteasyClientProxy;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.microprofile.client.DefaultResponseExceptionMapper;
import org.jboss.resteasy.microprofile.client.RestClientProxy;
import org.jboss.resteasy.microprofile.client.async.AsyncInterceptorRxInvokerProvider;
import org.jboss.resteasy.microprofile.client.publisher.MpPublisherMessageBodyReader;
import org.jboss.resteasy.plugins.providers.sse.client.SseEventSourceImpl;
import org.jboss.resteasy.spi.ResteasyConfiguration;

import io.quarkus.arc.BeanDestroyer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.InterceptorResolverBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InterceptorInfo;
import io.quarkus.arc.processor.ScopeInfo;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.StaticInitConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.restclient.NoopHostnameVerifier;
import io.quarkus.restclient.config.RegisteredRestClient;
import io.quarkus.restclient.config.RestClientsConfig;
import io.quarkus.restclient.config.deployment.RestClientConfigUtils;
import io.quarkus.restclient.runtime.PathFeatureHandler;
import io.quarkus.restclient.runtime.PathTemplateInjectionFilter;
import io.quarkus.restclient.runtime.RestClientBase;
import io.quarkus.restclient.runtime.RestClientRecorder;
import io.quarkus.resteasy.common.deployment.JaxrsProvidersToRegisterBuildItem;
import io.quarkus.resteasy.common.deployment.RestClientBuildItem;
import io.quarkus.resteasy.common.deployment.ResteasyInjectionReadyBuildItem;
import io.quarkus.resteasy.common.spi.ResteasyDotNames;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;

class RestClientProcessor {
    private static final Logger log = Logger.getLogger(RestClientProcessor.class);

    private static final DotName REST_CLIENT = DotName.createSimple(RestClient.class.getName());
    private static final DotName REGISTER_REST_CLIENT = DotName.createSimple(RegisterRestClient.class.getName());

    private static final DotName SESSION_SCOPED = DotName.createSimple(SessionScoped.class.getName());

    private static final DotName PATH = DotName.createSimple(Path.class.getName());

    private static final DotName REGISTER_PROVIDER = DotName.createSimple(RegisterProvider.class.getName());
    private static final DotName REGISTER_PROVIDERS = DotName.createSimple(RegisterProviders.class.getName());
    private static final DotName REGISTER_CLIENT_HEADERS = DotName.createSimple(RegisterClientHeaders.class.getName());

    private static final DotName CLIENT_REQUEST_FILTER = DotName.createSimple(ClientRequestFilter.class.getName());
    private static final DotName CLIENT_RESPONSE_FILTER = DotName.createSimple(ClientResponseFilter.class.getName());
    private static final DotName CLIENT_HEADER_PARAM = DotName.createSimple(ClientHeaderParam.class.getName());

    private static final String PROVIDERS_SERVICE_FILE = "META-INF/services/" + Providers.class.getName();

    @BuildStep
    void setupProviders(BuildProducer<NativeImageResourceBuildItem> resources,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyDefinition) {

        proxyDefinition.produce(new NativeImageProxyDefinitionBuildItem("jakarta.ws.rs.ext.Providers"));
        resources.produce(new NativeImageResourceBuildItem(PROVIDERS_SERVICE_FILE));
    }

    @BuildStep
    void setupClientBuilder(BuildProducer<NativeImageResourceBuildItem> resources,
            BuildProducer<ServiceProviderBuildItem> serviceProviders) {
        resources.produce(new NativeImageResourceBuildItem("META-INF/services/jakarta.ws.rs.client.ClientBuilder"));
        serviceProviders.produce(new ServiceProviderBuildItem(SseEventSource.Builder.class.getName(),
                SseEventSourceImpl.SourceBuilder.class.getName()));
    }

    @BuildStep
    NativeImageProxyDefinitionBuildItem addProxy() {
        return new NativeImageProxyDefinitionBuildItem(ResteasyConfiguration.class.getName());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setup(BuildProducer<FeatureBuildItem> feature,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            RestClientRecorder restClientRecorder) {

        feature.produce(new FeatureBuildItem(Feature.RESTEASY_CLIENT));

        restClientRecorder.setRestClientBuilderResolver();

        additionalBeans.produce(new AdditionalBeanBuildItem(RestClient.class));

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(DefaultResponseExceptionMapper.class.getName(),
                AsyncInterceptorRxInvokerProvider.class.getName(),
                ResteasyProviderFactoryImpl.class.getName(),
                ProxyBuilderImpl.class.getName(),
                ClientRequestFilter[].class.getName(),
                ClientResponseFilter[].class.getName(),
                jakarta.ws.rs.ext.ReaderInterceptor[].class.getName()).build());

        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder(ResteasyClientBuilder.class.getName(), NoopHostnameVerifier.class.getName())
                        .methods().build());
    }

    @BuildStep
    UnremovableBeanBuildItem makeConfigUnremovable() {
        return UnremovableBeanBuildItem.beanTypes(RestClientsConfig.class);
    }

    @BuildStep
    List<RestClientPredicateProviderBuildItem> transformAnnotationProvider(
            List<RestClientAnnotationProviderBuildItem> annotationProviders) {
        List<RestClientPredicateProviderBuildItem> result = new ArrayList<>();
        for (RestClientAnnotationProviderBuildItem annotationProvider : annotationProviders) {
            result.add(new RestClientPredicateProviderBuildItem(annotationProvider.getProviderClass().getName(),
                    new Predicate<ClassInfo>() {
                        @Override
                        public boolean test(ClassInfo classInfo) {
                            // register the provider to every Rest client annotated with annotationName
                            return classInfo.hasAnnotation(annotationProvider.getAnnotationName());
                        }
                    }));
        }
        return result;
    }

    @BuildStep
    void processInterfaces(
            CombinedIndexBuildItem combinedIndexBuildItem,
            BeanArchiveIndexBuildItem beanArchiveIndexBuildItem,
            Capabilities capabilities,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            NativeConfig nativeConfig,
            List<RestClientPredicateProviderBuildItem> restClientProviders,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyDefinition,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchy,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ServiceProviderBuildItem> serviceProvider,
            BuildProducer<RestClientBuildItem> restClient) {

        // According to the spec only rest client interfaces annotated with RegisterRestClient are registered as beans
        Map<DotName, ClassInfo> interfaces = new HashMap<>();
        Set<Type> returnTypes = new HashSet<>();

        IndexView index = CompositeIndex.create(beanArchiveIndexBuildItem.getIndex(), combinedIndexBuildItem.getIndex());

        findInterfaces(index, interfaces, returnTypes, REGISTER_REST_CLIENT, classInfo -> true);
        // in there, we are overly cautious it could be an interface for a server class
        findInterfaces(index, interfaces, returnTypes, PATH,
                classInfo -> index.getAllKnownImplementors(classInfo.name()).isEmpty());

        if (interfaces.isEmpty()) {
            return;
        }

        warnAboutNotWorkingFeaturesInNative(nativeConfig, interfaces);

        for (Map.Entry<DotName, ClassInfo> entry : interfaces.entrySet()) {
            String iName = entry.getKey().toString();
            // the native image proxy definitions have to be separate because
            // MP REST Client impl creates a JDK proxy that delegates to a resteasy JDK proxy
            proxyDefinition.produce(new NativeImageProxyDefinitionBuildItem(iName, ResteasyClientProxy.class.getName()));
            proxyDefinition.produce(
                    new NativeImageProxyDefinitionBuildItem(iName, RestClientProxy.class.getName(), Closeable.class.getName()));
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(iName).methods().build());
        }

        // Incoming headers
        // required for the non-arg constructor of DCHFImpl to be included in the native image
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(DefaultClientHeadersFactoryImpl.class.getName()).methods()
                .build());

        // Register Interface return types for reflection
        for (Type returnType : returnTypes) {
            reflectiveHierarchy
                    .produce(ReflectiveHierarchyBuildItem
                            .builder(returnType)
                            .ignoreTypePredicate(ResteasyDotNames.IGNORE_TYPE_FOR_REFLECTION_PREDICATE)
                            .ignoreFieldPredicate(ResteasyDotNames.IGNORE_FIELD_FOR_REFLECTION_PREDICATE)
                            .ignoreMethodPredicate(ResteasyDotNames.IGNORE_METHOD_FOR_REFLECTION_PREDICATE)
                            .source(getClass().getSimpleName() + " > " + returnType)
                            .build());
        }

        final Config config = ConfigProvider.getConfig();

        for (Map.Entry<DotName, ClassInfo> entry : interfaces.entrySet()) {
            DotName restClientName = entry.getKey();
            ClassInfo classInfo = entry.getValue();

            Optional<String> configKey;
            Optional<String> baseUri;
            AnnotationInstance instance = classInfo.declaredAnnotation(REGISTER_REST_CLIENT);
            if (instance != null) {
                AnnotationValue configKeyValue = instance.value("configKey");
                configKey = configKeyValue == null ? Optional.empty() : Optional.of(configKeyValue.asString());
                AnnotationValue baseUriValue = instance.value("baseUri");
                baseUri = baseUriValue == null ? Optional.empty() : Optional.of(baseUriValue.asString());
            } else {
                configKey = Optional.empty();
                baseUri = Optional.empty();
            }

            ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem.configure(restClientName);
            // The spec is not clear whether we should add superinterfaces too - let's keep aligned with SmallRye for now
            configurator.addType(restClientName);
            configurator.addQualifier(REST_CLIENT);
            List<String> clientProviders = checkRestClientProviders(entry.getValue(), restClientProviders);
            configurator.scope(computeDefaultScope(capabilities, config, entry, configKey));
            configurator.creator(m -> {
                // return new RestClientBase(proxyType, baseUri).create();
                ResultHandle interfaceHandle = m.loadClassFromTCCL(restClientName.toString());
                ResultHandle baseUriHandle = baseUri.isPresent() ? m.load(baseUri.get()) : m.loadNull();
                ResultHandle configKeyHandle = configKey.isPresent() ? m.load(configKey.get()) : m.loadNull();
                ResultHandle restClientProvidersHandle;
                if (!clientProviders.isEmpty()) {
                    restClientProvidersHandle = m.newArray(Class.class, clientProviders.size());
                    for (int i = 0; i < clientProviders.size(); i++) {
                        m.writeArrayValue(restClientProvidersHandle, i, m.loadClassFromTCCL(clientProviders.get(i)));
                    }
                } else {
                    restClientProvidersHandle = m.loadNull();
                }
                ResultHandle baseHandle = m.newInstance(
                        MethodDescriptor.ofConstructor(RestClientBase.class, Class.class, String.class,
                                String.class,
                                Class[].class),
                        interfaceHandle, baseUriHandle, configKeyHandle, restClientProvidersHandle);
                ResultHandle ret = m.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(RestClientBase.class, "create", Object.class), baseHandle);
                m.returnValue(ret);
            });
            configurator.destroyer(BeanDestroyer.CloseableDestroyer.class);

            syntheticBeans.produce(configurator.done());
            restClient.produce(new RestClientBuildItem(classInfo, configKey, baseUri));
        }
    }

    @BuildStep
    void generateRestClientConfigBuilder(
            List<RestClientBuildItem> restClients,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<StaticInitConfigBuilderBuildItem> staticInitConfigBuilder,
            BuildProducer<RunTimeConfigBuilderBuildItem> runTimeConfigBuilder) {

        List<RegisteredRestClient> registeredRestClients = restClients.stream()
                .map(rc -> new RegisteredRestClient(
                        rc.getClassInfo().name().toString(),
                        rc.getClassInfo().simpleName(),
                        rc.getConfigKey().orElse(null)))
                .toList();

        RestClientConfigUtils.generateRestClientConfigBuilder(registeredRestClients, generatedClass, staticInitConfigBuilder,
                runTimeConfigBuilder);
    }

    @BuildStep
    void clientTracingFeature(Capabilities capabilities,
            Optional<MetricsCapabilityBuildItem> metricsCapability, BuildProducer<ResteasyJaxrsProviderBuildItem> producer) {
        if (isRequired(capabilities, metricsCapability)) {
            producer.produce(new ResteasyJaxrsProviderBuildItem(PathFeatureHandler.class.getName()));
            producer.produce(new ResteasyJaxrsProviderBuildItem(PathTemplateInjectionFilter.class.getName()));
        }
    }

    private boolean isRequired(Capabilities capabilities,
            Optional<MetricsCapabilityBuildItem> metricsCapability) {
        return (capabilities.isPresent(Capability.OPENTELEMETRY_TRACER) ||
                (metricsCapability.isPresent()
                        && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER)));
    }

    private static List<String> checkRestClientProviders(ClassInfo classInfo,
            List<RestClientPredicateProviderBuildItem> restClientProviders) {
        return restClientProviders.stream().filter(p -> p.appliesTo(classInfo))
                .map(p -> p.getProviderClass()).collect(Collectors.toList());
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem activateSslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(Feature.RESTEASY_CLIENT);
    }

    // currently default methods on a rest-client interface
    // that is annotated with ClientHeaderParam
    // leads to NPEs (see https://github.com/quarkusio/quarkus/issues/10249)
    // so let's warn users about its use
    private void warnAboutNotWorkingFeaturesInNative(NativeConfig nativeConfig, Map<DotName, ClassInfo> interfaces) {
        if (!nativeConfig.enabled()) {
            return;
        }
        Set<DotName> dotNames = new HashSet<>();
        for (ClassInfo interfaze : interfaces.values()) {
            if (interfaze.declaredAnnotation(CLIENT_HEADER_PARAM) != null) {
                boolean hasDefault = false;
                for (MethodInfo method : interfaze.methods()) {
                    if (isDefault(method.flags())) {
                        hasDefault = true;
                        break;
                    }
                }
                if (hasDefault) {
                    dotNames.add(interfaze.name());
                }
            }
        }
        if (!dotNames.isEmpty()) {
            log.warnf("rest-client interfaces that contain default methods and are annotated with '@" + CLIENT_HEADER_PARAM
                    + "' might not work properly in native mode. Offending interfaces are: "
                    + dotNames.stream().map(d -> "'" + d.toString() + "'").collect(Collectors.joining(", ")));
        }
    }

    private static boolean isDefault(short flags) {
        return ((flags & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC);
    }

    private void findInterfaces(IndexView index, Map<DotName, ClassInfo> interfaces, Set<Type> returnTypes,
            DotName annotationToFind, Predicate<ClassInfo> additionalConstraints) {
        for (AnnotationInstance annotation : index.getAnnotations(annotationToFind)) {
            AnnotationTarget target = annotation.target();
            ClassInfo theInfo;
            if (target.kind() == AnnotationTarget.Kind.CLASS) {
                theInfo = target.asClass();
            } else if (target.kind() == AnnotationTarget.Kind.METHOD) {
                theInfo = target.asMethod().declaringClass();
            } else {
                continue;
            }

            if (!Modifier.isInterface(theInfo.flags()) || !additionalConstraints.test(theInfo)) {
                continue;
            }

            interfaces.put(theInfo.name(), theInfo);

            // Find Return types
            processInterfaceReturnTypes(theInfo, returnTypes);
            for (Type interfaceType : theInfo.interfaceTypes()) {
                ClassInfo interfaceClassInfo = index.getClassByName(interfaceType.name());
                if (interfaceClassInfo != null) {
                    processInterfaceReturnTypes(interfaceClassInfo, returnTypes);
                }
            }
        }
    }

    private void processInterfaceReturnTypes(ClassInfo classInfo, Set<Type> returnTypes) {
        for (MethodInfo method : classInfo.methods()) {
            Type type = method.returnType();
            if (!type.name().toString().startsWith("java.lang")) {
                returnTypes.add(type);
            }
        }
    }

    private ScopeInfo computeDefaultScope(Capabilities capabilities, Config config, Map.Entry<DotName, ClassInfo> entry,
            Optional<String> configKey) {
        ScopeInfo scopeToUse = null;

        ClassInfo classInfo = entry.getValue();
        Optional<String> scopeConfig = RestClientConfigUtils.findConfiguredScope(config, classInfo, configKey);

        Optional<String> configuredGlobalDefaultScope = RestClientConfigUtils.getDefaultScope(config);
        BuiltinScope globalDefaultScope;

        if (configuredGlobalDefaultScope.isPresent()) {
            globalDefaultScope = builtinScopeFromName(DotName.createSimple(configuredGlobalDefaultScope.get()));
            if (globalDefaultScope == null) {
                log.warnf("Unable to map the global REST client scope: '%s' to a scope. Using @Dependent",
                        configuredGlobalDefaultScope.get());
                globalDefaultScope = BuiltinScope.DEPENDENT;
            }
        } else {
            globalDefaultScope = BuiltinScope.DEPENDENT;
        }

        if (scopeConfig.isPresent()) {
            final DotName scope = DotName.createSimple(scopeConfig.get());
            final BuiltinScope builtinScope = builtinScopeFromName(scope);
            if (builtinScope != null) { // override default @Dependent scope with user defined one.
                scopeToUse = builtinScope.getInfo();
            } else if (capabilities.isPresent(Capability.SERVLET)) {
                if (scope.equals(SESSION_SCOPED) || scope.toString().equalsIgnoreCase(SESSION_SCOPED.withoutPackagePrefix())) {
                    scopeToUse = new ScopeInfo(SESSION_SCOPED, true);
                }
            }

            if (scopeToUse == null) {
                log.warn(String.format(
                        "Unsupported default scope %s provided for REST client %s. Defaulting to @Dependent.",
                        scope, entry.getKey()));
            }
        } else {
            final Set<DotName> annotations = classInfo.annotationsMap().keySet();
            for (final DotName annotationName : annotations) {
                final BuiltinScope builtinScope = BuiltinScope.from(annotationName);
                if (builtinScope != null) {
                    scopeToUse = builtinScope.getInfo();
                    break;
                }
                if (annotationName.equals(SESSION_SCOPED)) {
                    scopeToUse = new ScopeInfo(SESSION_SCOPED, true);
                    break;
                }
            }
        }

        // Initialize a default @Dependent scope as per the spec
        return scopeToUse != null ? scopeToUse : globalDefaultScope.getInfo();
    }

    @BuildStep
    IgnoreClientProviderBuildItem ignoreMPPublisher() {
        // hack to remove a provider that is manually registered QuarkusRestClientBuilder
        return new IgnoreClientProviderBuildItem(MpPublisherMessageBodyReader.class.getName());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerProviders(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            JaxrsProvidersToRegisterBuildItem jaxrsProvidersToRegisterBuildItem,
            List<IgnoreClientProviderBuildItem> ignoreClientProviderBuildItems,
            CombinedIndexBuildItem combinedIndexBuildItem,
            ResteasyInjectionReadyBuildItem injectorFactory,
            RestClientRecorder restClientRecorder, Capabilities capabilities) {

        for (IgnoreClientProviderBuildItem item : ignoreClientProviderBuildItems) {
            jaxrsProvidersToRegisterBuildItem.getProviders().remove(item.getProviderClassName());
            jaxrsProvidersToRegisterBuildItem.getContributedProviders().remove(item.getProviderClassName());
        }

        restClientRecorder.initializeResteasyProviderFactory(injectorFactory.getInjectorFactory(),
                jaxrsProvidersToRegisterBuildItem.useBuiltIn(),
                jaxrsProvidersToRegisterBuildItem.getProviders(), jaxrsProvidersToRegisterBuildItem.getContributedProviders());

        if (!capabilities.isPresent(Capability.RESTEASY) && !capabilities.isPresent(Capability.RESTEASY_REACTIVE)) {
            // ResteasyProviderFactory will use our implementation when accessing instance statically. That's not
            // necessary when RESTEasy classic is present as then provider factory with correct provider classes is generated.
            restClientRecorder.setResteasyProviderFactoryInstance();
        }

        // register the providers for reflection
        for (String providerToRegister : jaxrsProvidersToRegisterBuildItem.getProviders()) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(providerToRegister).build());
        }

        // now we register all values of @RegisterProvider for constructor reflection

        IndexView index = combinedIndexBuildItem.getIndex();
        List<AnnotationInstance> allInstances = new ArrayList<>(index.getAnnotations(REGISTER_PROVIDER));
        for (AnnotationInstance annotation : index.getAnnotations(REGISTER_PROVIDERS)) {
            allInstances.addAll(Arrays.asList(annotation.value().asNestedArray()));
        }
        for (AnnotationInstance annotationInstance : allInstances) {
            reflectiveClass
                    .produce(ReflectiveClassBuildItem.builder(annotationInstance.value().asClass().name().toString())
                            .build());
        }

        // Register @RegisterClientHeaders for reflection
        for (AnnotationInstance annotationInstance : index.getAnnotations(REGISTER_CLIENT_HEADERS)) {
            AnnotationValue value = annotationInstance.value();
            if (value != null) {
                reflectiveClass
                        .produce(ReflectiveClassBuildItem.builder(annotationInstance.value().asClass().name().toString())
                                .build());
            }
        }

        // now retain all un-annotated implementations of ClientRequestFilter and ClientResponseFilter
        // in case they are programmatically registered by applications
        for (ClassInfo info : index.getAllKnownImplementors(CLIENT_REQUEST_FILTER)) {
            reflectiveClass
                    .produce(ReflectiveClassBuildItem.builder(info.name().toString()).build());
        }
        for (ClassInfo info : index.getAllKnownImplementors(CLIENT_RESPONSE_FILTER)) {
            reflectiveClass
                    .produce(ReflectiveClassBuildItem.builder(info.name().toString()).build());
        }
    }

    @BuildStep
    AdditionalBeanBuildItem registerProviderBeans(CombinedIndexBuildItem combinedIndex) {
        IndexView index = combinedIndex.getIndex();
        List<AnnotationInstance> allInstances = new ArrayList<>(index.getAnnotations(REGISTER_PROVIDER));
        for (AnnotationInstance annotation : index.getAnnotations(REGISTER_PROVIDERS)) {
            allInstances.addAll(Arrays.asList(annotation.value().asNestedArray()));
        }
        allInstances.addAll(index.getAnnotations(REGISTER_CLIENT_HEADERS));
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
        for (AnnotationInstance annotationInstance : allInstances) {
            // Make sure all providers not annotated with @Provider but used in @RegisterProvider are registered as beans
            AnnotationValue value = annotationInstance.value();
            if (value != null) {
                builder.addBeanClass(value.asClass().name().toString());
            }
        }
        return builder.build();
    }

    @BuildStep
    void unremovableInterceptors(List<RestClientBuildItem> restClientInterfaces, BeanArchiveIndexBuildItem beanArchiveIndex,
            InterceptorResolverBuildItem interceptorResolver, BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {

        if (restClientInterfaces.isEmpty()) {
            return;
        }

        IndexView index = beanArchiveIndex.getIndex();
        Set<DotName> interceptorBindings = interceptorResolver.getInterceptorBindings();
        Set<String> unremovableInterceptors = new HashSet<>();

        for (RestClientBuildItem restClient : restClientInterfaces) {
            ClassInfo restClientClass = index.getClassByName(DotName.createSimple(restClient.getInterfaceName()));
            if (restClientClass != null) {
                Set<AnnotationInstance> classLevelBindings = new HashSet<>();
                for (AnnotationInstance annotationInstance : restClientClass.declaredAnnotations()) {
                    if (interceptorBindings.contains(annotationInstance.name())) {
                        classLevelBindings.add(annotationInstance);
                    }
                }
                for (MethodInfo method : restClientClass.methods()) {
                    if (Modifier.isStatic(method.flags())) {
                        continue;
                    }
                    Set<AnnotationInstance> bindings = new HashSet<>(classLevelBindings);
                    for (AnnotationInstance annotationInstance : method.annotations()) {
                        if (annotationInstance.target().kind() == Kind.METHOD
                                && interceptorBindings.contains(annotationInstance.name())) {
                            bindings.add(annotationInstance);
                        }
                    }
                    if (bindings.isEmpty()) {
                        continue;
                    }
                    List<InterceptorInfo> interceptors = interceptorResolver.get().resolve(
                            InterceptionType.AROUND_INVOKE,
                            bindings);
                    if (!interceptors.isEmpty()) {
                        interceptors.stream().map(InterceptorInfo::getBeanClass).map(Object::toString)
                                .forEach(unremovableInterceptors::add);
                    }
                }
            }
        }
        if (!unremovableInterceptors.isEmpty()) {
            unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(unremovableInterceptors));
        }
    }

    private static BuiltinScope builtinScopeFromName(DotName scopeName) {
        BuiltinScope scope = BuiltinScope.from(scopeName);
        if (scope == null) {
            for (BuiltinScope builtinScope : BuiltinScope.values()) {
                if (builtinScope.getName().withoutPackagePrefix().equalsIgnoreCase(scopeName.toString())) {
                    scope = builtinScope;
                }
            }
        }
        return scope;
    }
}
