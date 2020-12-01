package cc.quarkus.qcc.type.generic;

/**
 *
 */
public interface MethodDeclarationSignature {
    int getTypeParameterCount();

    TypeParameter getTypeParameter(int index) throws IndexOutOfBoundsException;

    int getParameterCount();

    TypeSignature getParameterType(int index) throws IndexOutOfBoundsException;

    /**
     * Determine if this method signature has a return type.
     *
     * @return {@code true} if there is a return type, or {@code false} if the method returns {@code void}
     */
    boolean hasReturnType();

    TypeSignature getReturnType() throws IllegalArgumentException;

    int getThrowsCount();

    ThrowableTypeSignature getThrowsType(int index);

    static MethodDeclarationSignature parseMethodDeclarationSignature(ParsingCache cache, String signature) {
        return Parsing.parseMethodDeclarationSignature(cache, signature);
    }
}