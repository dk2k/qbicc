package cc.quarkus.qcc.graph;

/**
 *
 */
public final class And extends AbstractBinaryValue implements CommutativeBinaryValue {
    And(final Value v1, final Value v2) {
        super(v1, v2);
    }

    public <T, R> R accept(final ValueVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }
}