package io.quarkus.hibernate.orm.deployment.spi;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.ClassCreator;

/**
 * Additional JPA metamodel class that we need to index.
 * This is used to add classes that are not part of the application code.
 * <p>
 * Note that for classes that are generated at build time, e.g. via
 * {@link ClassCreator Gizmo}, we need to store the class bytes
 * to ensure they can be indexed (we cannot rely on the {@link GeneratedClassBuildItem}
 * as that would create a circular build dependency in the ORM extension).
 *
 * @author Stéphane Épardaud
 */
public final class AdditionalJpaModelBuildItem extends MultiBuildItem {

    private final String className;
    private final byte[] classBytes;

    public AdditionalJpaModelBuildItem(String className) {
        this(className, null);
    }

    public AdditionalJpaModelBuildItem(String className, byte[] classBytes) {
        Objects.requireNonNull(className);
        this.className = className;
        this.classBytes = classBytes;
    }

    public String getClassName() {
        return className;
    }

    public byte[] getClassBytes() {
        return classBytes;
    }
}
