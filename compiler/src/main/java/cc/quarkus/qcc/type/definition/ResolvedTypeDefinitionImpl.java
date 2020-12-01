package cc.quarkus.qcc.type.definition;

import cc.quarkus.qcc.graph.literal.TypeIdLiteral;
import cc.quarkus.qcc.type.annotation.Annotation;
import cc.quarkus.qcc.type.definition.element.ConstructorElement;
import cc.quarkus.qcc.type.definition.element.FieldElement;
import cc.quarkus.qcc.type.definition.element.InitializerElement;
import cc.quarkus.qcc.type.definition.element.MethodElement;
import cc.quarkus.qcc.type.descriptor.MethodDescriptor;

/**
 *
 */
final class ResolvedTypeDefinitionImpl implements ResolvedTypeDefinition {
    private final ValidatedTypeDefinition delegate;
    private volatile PreparedTypeDefinitionImpl prepared;

    ResolvedTypeDefinitionImpl(final ValidatedTypeDefinition delegate) {
        this.delegate = delegate;
    }

    // delegation

    public TypeIdLiteral getTypeId() {
        return delegate.getTypeId();
    }

    public ResolvedTypeDefinition getSuperClass() {
        ValidatedTypeDefinition superClass = delegate.getSuperClass();
        return superClass == null ? null : superClass.resolve();
    }

    public ResolvedTypeDefinition getInterface(final int index) throws IndexOutOfBoundsException {
        return delegate.getInterface(index).resolve();
    }

    public FieldSet getInstanceFieldSet() {
        return delegate.getInstanceFieldSet();
    }

    public FieldSet getStaticFieldSet() {
        return delegate.getStaticFieldSet();
    }

    public ClassContext getContext() {
        return delegate.getContext();
    }

    public String getInternalName() {
        return delegate.getInternalName();
    }

    public int getModifiers() {
        return delegate.getModifiers();
    }

    public String getSuperClassInternalName() {
        return delegate.getSuperClassInternalName();
    }

    public int getInterfaceCount() {
        return delegate.getInterfaceCount();
    }

    public String getInterfaceInternalName(final int index) throws IndexOutOfBoundsException {
        return delegate.getInterfaceInternalName(index);
    }

    public int getFieldCount() {
        return delegate.getFieldCount();
    }

    public int getMethodCount() {
        return delegate.getMethodCount();
    }

    public FieldElement getField(final int index) {
        return delegate.getField(index);
    }

    public MethodElement getMethod(final int index) {
        return delegate.getMethod(index);
    }

    public ConstructorElement getConstructor(final int index) {
        return delegate.getConstructor(index);
    }

    public InitializerElement getInitializer() {
        return delegate.getInitializer();
    }

    public boolean internalNameEquals(final String internalName) {
        return delegate.internalNameEquals(internalName);
    }

    public boolean hasSuperClass() {
        return delegate.hasSuperClass();
    }

    public boolean superClassInternalNameEquals(final String internalName) {
        return delegate.superClassInternalNameEquals(internalName);
    }

    public boolean interfaceInternalNameEquals(final int index, final String internalName) throws IndexOutOfBoundsException {
        return delegate.interfaceInternalNameEquals(index, internalName);
    }

    public int getConstructorCount() {
        return delegate.getConstructorCount();
    }

    public int getVisibleAnnotationCount() {
        return delegate.getVisibleAnnotationCount();
    }

    public Annotation getVisibleAnnotation(final int index) {
        return delegate.getVisibleAnnotation(index);
    }

    public int getInvisibleAnnotationCount() {
        return delegate.getInvisibleAnnotationCount();
    }

    public Annotation getInvisibleAnnotation(final int index) {
        return delegate.getInvisibleAnnotation(index);
    }

    public MethodDescriptor resolveMethodDescriptor(final int argument) throws ResolutionFailedException {
        return delegate.resolveMethodDescriptor(argument);
    }

    // next phase

    public PreparedTypeDefinitionImpl prepare() throws PrepareFailedException {
        PreparedTypeDefinitionImpl prepared = this.prepared;
        if (prepared != null) {
            return prepared;
        }
        ResolvedTypeDefinition superClass = getSuperClass();
        if (superClass != null) {
            superClass.prepare();
        }
        int interfaceCount = getInterfaceCount();
        for (int i = 0; i < interfaceCount; i ++) {
            getInterface(i).prepare();
        }
        synchronized (this) {
            prepared = this.prepared;
            if (prepared == null) {
                prepared = new PreparedTypeDefinitionImpl(this);
                this.prepared = prepared;
            }
        }
        return prepared;
    }
}