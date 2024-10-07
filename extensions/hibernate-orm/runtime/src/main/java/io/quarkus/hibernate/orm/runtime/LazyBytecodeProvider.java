package io.quarkus.hibernate.orm.runtime;

import java.util.function.Supplier;

import org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl;

import net.bytebuddy.ClassFileVersion;

/**
 * @author Marco Belladelli
 */
public final class LazyBytecodeProvider implements Supplier<BytecodeProviderImpl>, AutoCloseable {
    private BytecodeProviderImpl bytecodeProvider;

    @Override
    public BytecodeProviderImpl get() {
        if (bytecodeProvider == null) {
            bytecodeProvider = new BytecodeProviderImpl(ClassFileVersion.JAVA_V11);
        }
        return bytecodeProvider;
    }

    @Override
    public void close() {
        if (bytecodeProvider != null) {
            bytecodeProvider.resetCaches();
            bytecodeProvider = null;
        }
    }
}
