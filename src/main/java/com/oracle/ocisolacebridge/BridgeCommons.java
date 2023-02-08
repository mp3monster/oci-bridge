package com.oracle.ocisolacebridge;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeCommons {
  /**
   *
   */
  private static final String TRUE = "True";
  /**
   *
   */
  private static final String FALSE = "False";
  /**
   *
   */
  private static final String TESTING = "Testing";
  static final String CONNECTIONTYPE = "Type";
  static final boolean EXITONERR = System.getProperty(TESTING, FALSE).equalsIgnoreCase(TRUE);

  private static Logger logger = LoggerFactory.getLogger(OCISolaceConnector.class);

  public static String prettyPropertiesToString(Properties props, String propDescription, String postfix) {
    String output = "";
    if (props == null) {
      output = propDescription + " IS NULL";
    } else {
      output += ("Props for " + propDescription + " ...\n");
      Iterator iter = props.keySet().iterator();
      while (iter.hasNext()) {
        String key = (String) iter.next();
        String value = props.getProperty(key);
        output += (propDescription + ": " + key + "==" + value + "\n");
      }
      output += postfix;

    }
    return output;
  }

  public static String exceptionToString(Exception err) {
    StringWriter sw = new StringWriter();
    err.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
