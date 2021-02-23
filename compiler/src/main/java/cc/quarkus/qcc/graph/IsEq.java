package cc.quarkus.qcc.graph;

import cc.quarkus.qcc.type.BooleanType;
import cc.quarkus.qcc.type.definition.element.ExecutableElement;

/**
 *
 */
public final class IsEq extends AbstractBooleanCompare implements CommutativeBinaryValue {
    IsEq(final Node callSite, final ExecutableElement element, final int line, final int bci, final Value v1, final Value v2, final BooleanType booleanType) {
        super(callSite, element, line, bci, v1, v2, booleanType);
    }

    public <T, R> R accept(final ValueVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }
}