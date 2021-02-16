package cc.quarkus.qcc.graph;

import java.util.Objects;

import cc.quarkus.qcc.type.ArrayType;
import cc.quarkus.qcc.type.ValueType;
import cc.quarkus.qcc.type.definition.element.ExecutableElement;

/**
 * An extracted element of an array value.
 */
public final class ExtractElement extends AbstractValue implements Unschedulable {
    private final Value arrayValue;
    private final ArrayType arrayType;
    private final Value index;

    ExtractElement(Node callSite, ExecutableElement element, int line, int bci, Value arrayValue, Value index) {
        super(callSite, element, line, bci);
        this.arrayValue = arrayValue;
        arrayType = (ArrayType) arrayValue.getType();
        this.index = index;
    }

    @Override
    int calcHashCode() {
        return Objects.hash(arrayValue, index);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExtractElement && equals((ExtractElement) other);
    }

    public boolean equals(ExtractElement other) {
        return this == other || other != null && arrayValue.equals(other.arrayValue) && index.equals(other.index);
    }

    public ArrayType getArrayType() {
        return arrayType;
    }

    public Value getArrayValue() {
        return arrayValue;
    }

    public Value getIndex() {
        return index;
    }

    @Override
    public ValueType getType() {
        return arrayType.getElementType();
    }

    @Override
    public int getValueDependencyCount() {
        return 2;
    }

    @Override
    public Value getValueDependency(int index) throws IndexOutOfBoundsException {
        return index == 0 ? arrayValue : index == 1 ? this.index : Util.throwIndexOutOfBounds(index);
    }

    @Override
    public <T, R> R accept(ValueVisitor<T, R> visitor, T param) {
        return visitor.visit(param, this);
    }
}