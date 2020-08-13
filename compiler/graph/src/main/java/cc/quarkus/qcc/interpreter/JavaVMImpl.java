package cc.quarkus.qcc.interpreter;

import static java.lang.Math.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import cc.quarkus.qcc.graph.ClassType;
import cc.quarkus.qcc.graph.GraphFactory;
import cc.quarkus.qcc.type.definition.DefinedTypeDefinition;
import cc.quarkus.qcc.type.definition.Dictionary;
import cc.quarkus.qcc.type.definition.ModuleDefinition;
import cc.quarkus.qcc.type.definition.VerifiedTypeDefinition;

final class JavaVMImpl implements JavaVM {
    private boolean exited;
    private int exitCode = -1;
    private final Lock vmLock = new ReentrantLock();
    private final Condition stopCondition = vmLock.newCondition();
    private final Condition signalCondition = vmLock.newCondition();
    private final ArrayDeque<Signal> signalQueue = new ArrayDeque<>();
    private final Set<JavaThread> threads = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<JavaThread> attachedThread = new ThreadLocal<>();
    private final Dictionary bootstrapDictionary;
    private final JavaClassImpl classClass;
    private final ConcurrentMap<JavaObject, Dictionary> classLoaderLoaders = new ConcurrentHashMap<>();
    private final ConcurrentMap<Dictionary, JavaObject> loaderClassLoaders = new ConcurrentHashMap<>();
    private final ConcurrentMap<ClassType, JavaClassImpl> loadedClasses = new ConcurrentHashMap<>();
    private final Map<String, BootModule> bootstrapModules;
    private final GraphFactory graphFactory;

    JavaVMImpl(final Builder builder) {
        Map<String, BootModule> bootstrapModules = new HashMap<>();
        Dictionary bootstrapDictionary = new Dictionary();
        for (Path path : builder.bootstrapModules) {
            // open all bootstrap JARs (MR bootstrap JARs not supported)
            JarFile jarFile;
            try {
                jarFile = new JarFile(path.toFile(), true, ZipFile.OPEN_READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String moduleInfo = "module-info.class";
            ByteBuffer buffer;
            try {
                buffer = getJarEntryBuffer(jarFile, moduleInfo);
                if (buffer == null) {
                    // ignore non-module
                    continue;
                }
            } catch (IOException e) {
                for (BootModule toClose : bootstrapModules.values()) {
                    try {
                        toClose.close();
                    } catch (IOException e2) {
                        e.addSuppressed(e2);
                    }
                }
                throw new RuntimeException(e);
            }
            ModuleDefinition moduleDefinition = ModuleDefinition.create(bootstrapDictionary, buffer);
            bootstrapModules.put(moduleDefinition.getName(), new BootModule(jarFile, moduleDefinition));
        }
        BootModule javaBase = bootstrapModules.get("java.base");
        if (javaBase == null) {
            throw new RuntimeException("Bootstrap failed: no java.base module found");
        }
        this.bootstrapModules = bootstrapModules;
        try {
            defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/lang/Object");
            DefinedTypeDefinition classClassDefined = defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/lang/Class");
            defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/io/Serializable");
            defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/lang/reflect/GenericDeclaration");
            defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/lang/reflect/Type");
            defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/lang/reflect/AnnotatedElement");
            VerifiedTypeDefinition classClassVerified = classClassDefined.verify();
            classClass = new JavaClassImpl(this, classClassVerified, true /* special ctor for Class.class */);
            defineBootClass(bootstrapDictionary, javaBase.jarFile, "java/lang/ClassLoader");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.bootstrapDictionary = bootstrapDictionary;
        this.graphFactory = builder.graphFactory;
    }

    private static ByteBuffer getJarEntryBuffer(final JarFile jarFile, final String fileName) throws IOException {
        final ByteBuffer buffer;
        JarEntry jarEntry = jarFile.getJarEntry(fileName);
        if (jarEntry == null) {
            jarFile.close();
            return null;
        }
        try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
            buffer = ByteBuffer.wrap(inputStream.readAllBytes());
        }
        return buffer;
    }

    private static DefinedTypeDefinition defineBootClass(final Dictionary bootstrapLoader, final JarFile javaBase, String name) throws IOException {
        ByteBuffer bytes = getJarEntryBuffer(javaBase, name + ".class");
        if (bytes == null) {
            throw new IllegalArgumentException("Initial class finder cannot find bootstrap class \"" + name + "\"");
        }
        return bootstrapLoader.defineClass(name, bytes);
    }

    public JavaClass defineClass(final String name, final JavaObject classLoader, final ByteBuffer bytes) {
        Dictionary dictionary = getDictionaryFor(classLoader);
        VerifiedTypeDefinition def = dictionary.defineClass(name, bytes).verify();
        JavaClassImpl javaClass = new JavaClassImpl(this, def);
        registerJavaClassOf(def.getClassType(), javaClass);
        return javaClass;
    }

    private static final AtomicLong anonCounter = new AtomicLong();

    public JavaClass defineAnonymousClass(final JavaClass hostClass, final ByteBuffer bytes) {
        String newName = hostClass.getTypeDefinition().getName() + "/" + anonCounter.getAndIncrement();
        return defineClass(newName, getClassLoaderFor(hostClass.getTypeDefinition().getDefiningClassLoader()), bytes);
    }

    public JavaThread newThread(final String threadName, final JavaObject threadGroup, final boolean daemon) {
        return new JavaThreadImpl(threadName, threadGroup, daemon, this);
    }

    void tryAttach(JavaThread thread) throws IllegalStateException {
        if (attachedThread.get() != null) {
            throw new IllegalStateException("Thread is already attached");
        }
        attachedThread.set(thread);
    }

    void detach(JavaThread thread) throws IllegalStateException {
        JavaThread existing = attachedThread.get();
        if (existing != thread) {
            throw new IllegalStateException("Thread is not attached");
        }
        attachedThread.remove();
    }

    public JavaThread currentThread() {
        return attachedThread.get();
    }

    public void deliverSignal(final Signal signal) {
        vmLock.lock();
        try {
            signalQueue.addLast(signal);
            signalCondition.notify();
        } finally {
            vmLock.unlock();
        }
    }

    Signal awaitSignal() throws InterruptedException {
        vmLock.lockInterruptibly();
        try {
            Signal signal;
            for (;;) {
                signal = signalQueue.pollFirst();
                if (signal != null) {
                    return signal;
                }
                signalCondition.await();
            }
        } finally {
            vmLock.unlock();
        }
    }

    public int awaitTermination() throws InterruptedException {
        vmLock.lockInterruptibly();
        try {
            while (! threads.isEmpty()) {
                stopCondition.await();
            }
            return max(0, exitCode);
        } finally {
            vmLock.unlock();
        }
    }

    void exit(int status) {
        vmLock.lock();
        try {
            if (! exited) {
                exitCode = status;
                exited = true;
            }
        } finally {
            vmLock.unlock();
        }
    }

    public void close() {
        vmLock.lock();
        try {
            exit(134); // SIGKILL-ish
            for (JavaThread thread : threads) {
                thread.close();
            }
            // todo: this is all probably wrong; we need to figure out lifecycle more accurately
            for (BootModule bootModule : bootstrapModules.values()) {
                try {
                    bootModule.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        } finally {
            vmLock.unlock();
        }
    }

    JavaClassImpl getClassClass() {
        return classClass;
    }

    Dictionary getDictionaryFor(final JavaObject classLoader) {
        Dictionary dictionary = classLoaderLoaders.get(classLoader);
        if (dictionary == null) {
            throw new IllegalStateException("Class loader object is unknown");
        }
        return dictionary;
    }

    JavaObject getClassLoaderFor(final Dictionary dictionary) {
        JavaObject classLoader = loaderClassLoaders.get(dictionary);
        if (classLoader == null) {
            throw new IllegalStateException("Class loader object is unknown");
        }
        return classLoader;
    }

    JavaClassImpl getJavaClassOf(final ClassType type) {
        return loadedClasses.get(type);
    }

    void registerJavaClassOf(final ClassType classType, final JavaClassImpl javaClass) {
        if (loadedClasses.putIfAbsent(classType, javaClass) != null) {
            throw new IllegalStateException("Class registered twice");
        }
    }

    void registerDictionaryFor(final JavaObject classLoader, final Dictionary dictionary) {
        if (classLoaderLoaders.putIfAbsent(classLoader, dictionary) != null) {
            throw new IllegalStateException("Class loader already registered");
        }
        if (loaderClassLoaders.putIfAbsent(dictionary, classLoader) != null) {
            throw new IllegalStateException("Class loader already registered (partially)");
        }
    }

    static final class BootModule implements Closeable {
        private final JarFile jarFile;
        private final ModuleDefinition moduleDefinition;

        BootModule(final JarFile jarFile, final ModuleDefinition moduleDefinition) {
            this.jarFile = jarFile;
            this.moduleDefinition = moduleDefinition;
        }

        public void close() throws IOException {
            jarFile.close();
        }
    }
}