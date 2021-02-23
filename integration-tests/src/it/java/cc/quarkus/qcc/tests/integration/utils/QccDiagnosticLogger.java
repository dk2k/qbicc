package cc.quarkus.qcc.tests.integration.utils;

import java.util.function.Consumer;

import cc.quarkus.qcc.context.Diagnostic;
import org.jboss.logging.Logger;

public class QccDiagnosticLogger implements Consumer<Iterable<Diagnostic>> {
    private final Logger logger;

    public QccDiagnosticLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void accept(Iterable<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            String message = diagnostic.toString();
            switch (diagnostic.getLevel()) {
                case ERROR:
                    logger.error(message);
                case WARNING:
                    logger.warn(message);
                    break;
                case NOTE:
                case INFO:
                    logger.info(message);
                    break;
                case DEBUG:
                    logger.debug(message);
                    break;
            }
        }
    }

}