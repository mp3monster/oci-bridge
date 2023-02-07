package com.oracle.ocisolacebridge;

import java.util.Iterator;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeCommons {
  static final String CONNECTIONTYPE = "Type";
  static final boolean EXITONERR = true;

  private static Logger logger = LoggerFactory.getLogger(OCISolaceConnector.class);

  public static void prettyPropertiesToString(Properties props, String propDescription) {
    if (props == null) {
      logger.warn(propDescription + " IS NULL");
    } else {
      logger.info("\nProps for " + propDescription + " ...");
      Iterator iter = props.keySet().iterator();
      while (iter.hasNext()) {
        String key = (String) iter.next();
        String value = props.getProperty(key);
        logger.info(propDescription + ": " + key + "==" + value);
      }
      logger.info("\n\n");

    }
  }
}
