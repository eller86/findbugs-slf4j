package pkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsingGetMessage {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    void method(Throwable t) {
        logger.info("My message is {}", t.getMessage());
        logger.info("My {} is {}", "message", t.getMessage());
        logger.info("My {} {} {}", "message", "is", t.getMessage());
    }
}