package io.unicid.registry.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.jboss.logging.Logger;

public class DateUtils {
  private static final Logger logger = Logger.getLogger(DateUtils.class);

  public static LocalDate parseDateString(String dateString, String formatterPattern) {
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatterPattern);
      LocalDate date = LocalDate.parse(dateString, formatter);
      int currentYear = LocalDate.now().getYear();
      if (date.getYear() > currentYear) date = date.minusYears(100);
      return date;
    } catch (DateTimeParseException e) {
      logger.error("DateUtils: parseDateString: " + e);
    }
    return null;
  }
}
