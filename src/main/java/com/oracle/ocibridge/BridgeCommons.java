package com.oracle.ocibridge;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Utilities class for the OCI Bridge
 */
public class BridgeCommons {

  public static final String TRUE = "True";
  public static final String FALSE = "False";

  private static final String TESTING = "Testing";
  static final String CONNECTIONTYPE = "Type";
  static final boolean EXITONERR = System.getProperty(TESTING, FALSE).equalsIgnoreCase(TRUE);

  private BridgeCommons() {
    logger.warn("Private constructor invoked - not expected");
  }

  /**
   * separator between the connection name and the relevant property
   */
  public static final String PROP_PREFIX = "__";

  private static Logger logger = LoggerFactory.getLogger(BridgeCommons.class.getName());

  /**
   * Takes the map or properties object and returns a string with the properties
   * on separate lines.
   * Wrapped with a prefix and post fix
   * 
   * @param props           the map or properties file to be pretty printed
   * @param propDescription and descriptor to be added to the output
   * @param postfix         and post string output needed
   * @return String the formatted output
   */
  public static String prettyPropertiesToString(Map props, String propDescription, String postfix) {
    String output = "";
    if (props == null) {
      output = propDescription + " IS NULL";
    } else {
      output += ("Props for " + propDescription + " ...\n");
      Iterator iter = props.keySet().iterator();
      while (iter.hasNext()) {
        String key = (String) iter.next();
        String value = (String) props.get(key);
        output += (propDescription + ": " + key + "==" + value + "\n");
      }
      output += postfix;

    }
    return output;
  }

  /*
   * Converts an exception including the stacktrace to a printable string
   */
  public static String exceptionToString(Exception err) {
    StringWriter sw = new StringWriter();
    err.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  static void pause(String requestor, int millis) {
    if (millis > 0) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException err) {
        logger.info("Disturbed while napping for " + requestor);
      }
    }
  }
}
