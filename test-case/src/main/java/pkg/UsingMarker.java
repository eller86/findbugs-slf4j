package pkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Simple class to reproduce issue 15.
 *
 * @see https://github.com/KengoTODA/findbugs-slf4j/issues/15
 */
public class UsingMarker {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  void method() {
    Marker marker = MarkerFactory.getMarker("my marker");
    logger.error(marker, "Hello, marker");
    logger.error(marker, "Hello, {}");
    logger.error(marker, "Hello, {}", "world");
  }
}
