package cc.quarkus.qcc.machine.llvm.debuginfo;

/**
 *
 */
public interface DILocation extends MetadataNode {
    DILocation comment(String comment);
}