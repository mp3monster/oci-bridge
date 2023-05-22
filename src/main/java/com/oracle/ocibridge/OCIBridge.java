package com.oracle.ocibridge;

/*
 * Notes:
 * Solace Javadoc - https://docs.solace.com/API-Developer-Online-Ref-Documentation/pubsubplus-java/index.html
 * Solace code frag https://www.solace.dev/
 * Solace Quick start setup https://solace.com/products/event-broker/software/getting-started/
 * Properties example https://github.com/SolaceSamples/solace-samples-java/blob/main/src/main/java/com/solace/samples/java/snippets/HowToConfigureServiceAccessWithProperties.java
 */
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OCIBridge extends Object {

  /**
   *
   */
  private static final String PROPS_FILE = "PropsFile";

  /**
   * A property for a specific connection when used we pass all properties wioth
   * the connection prefix rather than the named specific set provided as the
   * default set. This is ideal when we need to provide the target service more
   * configuration values than originally expected
   */
  private static final String PASS_ALL = "pass_all";

  /**
   * The character to separate the names of the source and destination connections
   */
  private static final String CONNECTOR_MAPPING_SEPARATOR = "-";
  /**
   * Delay time in milliseconds after each pass - if we dont want the system poll
   * through all the connections too quickly.
   */
  private static final String MILLI_DELAY_ON_MULTI_PASS = "milliDelayOnMultiPass";

  /**
   * Defines whether we need to make one pass or multiple passes. A single pass is
   * ideal for testing purposes
   */
  private static final String MULTI_PASS_DEFAULT = "False";
  private static final String IS_MULTI_PASS = "isMultiPass";

  /*
   * The name of the property that is used to define all the connections
   */
  private static final String CONNECTION_LIST = "ConnectionList";

  /**
   * separator between the connection name and the relevant property
   */
  private static final String PROP_PREFIX = BridgeCommons.PROP_PREFIX;

  private static Logger logger = LoggerFactory.getLogger(OCIBridge.class.getName());

  static ConnectionsMap connections = new ConnectionsMap();
  static Map allProps = new HashMap();

  static class ConnectionsMap extends HashMap<String, ConnectionPair> {
  }

  static class MessageList extends ArrayList<String> {
  }

  /*
   * Use this so we can see the parameters that are needed. Helpful when looking
   * at Solace config needs
   */
  static String listParams(String[] propParams) {
    String indent = "   ";
    String output = "";
    for (int paramIdx = 0; paramIdx < propParams.length; paramIdx++) {
      output = output + indent + propParams[paramIdx] + "\n";
    }
    return output;
  }

  /*
   * Works out what properties are needed based on a connection prefix
   * 
   * @props the list of properties to pull the relevant connections from
   * 
   * @prefix the name of the connection we want properties for
   */
  static String[] getPropParams(Map props, String prefix) {
    String[] params = null;
    String propName = prefix + PROP_PREFIX + BridgeCommons.CONNECTIONTYPE;
    String connType = (String) props.get(propName);
    if (connType != null) {
      switch (connType) {
        case OCIQueueConnection.TYPENAME:
          params = OCIQueueConnection.getPropParams();
          break;

        case SolaceConnection.TYPENAME:
          params = SolaceConnection.getPropParams();
          break;

        case SyntheticConnection.TYPENAME:
          params = SyntheticConnection.getPropParams();
          break;

        default:
          params = null;
          logger.warn("No connection type defined for " + prefix);
      }
    } else {
      logger.warn("No connection type defined for " + prefix + " expected property " + propName);
    }

    return params;
  }

  /*
   * Build a connection object based on the configuration params. This is the
   * factory for connections
   */
  static ConnectionBaseInterface createConnection(Properties props, String prefix, boolean receiveFromService) {
    ConnectionBaseInterface instance = null;
    final String TYPETAG = "<NOT SET>";
    String connType = TYPETAG;
    String propName = BridgeCommons.CONNECTIONTYPE;

    if ((props != null) && (prefix != null)) {
      connType = props.getProperty(propName, TYPETAG);
    } else {
      logger.warn(
          "Can't locate connection type - info incomplete. props" + (props == null) + " prefix is " + (prefix == null));
    }

    switch (connType) {
      case OCIQueueConnection.TYPENAME:
        instance = new OCIQueueConnection(props);
        break;

      case SolaceConnection.TYPENAME:
        instance = new SolaceConnection(props, !receiveFromService);
        break;

      case SyntheticConnection.TYPENAME:
        instance = new SyntheticConnection(props);
        break;

      default:
        logger.warn("Cant create connection for connection type " + connType + " for " + prefix
            + " using property " + propName);
        instance = null;
    }
    return instance;
  }

  /*
   * This class holds a source and target pair of connections we can then use the
   * class to manage
   * each connection setup.
   */
  static class ConnectionPair {

    ConnectionBaseInterface from = null;
    ConnectionBaseInterface to = null;

    public ConnectionPair(ConnectionBaseInterface from, ConnectionBaseInterface to) {
      this.from = from;
      this.to = to;
    }

    public boolean isConnected() {
      return (from != null) && (to != null) && from.connected() && to.connected();
    }

    public void connect() {
      if (to != null) {
        to.connect();
      }
      if (from != null) {
        from.connect();
      }
    }

    public void log() {
      logger.info("from:\n" + from.toString() + "\n to:\n" + to.toString() + "\n Is connected=" + isConnected());
    }

    public String getConnectionName() {
      final String UNDEFINED = "<UNDEFINED>";
      String fromName = UNDEFINED;
      String toName = UNDEFINED;

      if (from != null) {
        fromName = from.getConnectionName();
      }
      if (to != null) {
        toName = to.getConnectionName();
      }
      return fromName + " ---> " + toName;
    }
  }

  /*
   * Extract the necessary properties for a connection
   */
  private static Properties getProps(String prefix, String[] propList, Map allProps) {
    Properties props = new Properties();
    if ((prefix != null) && (prefix.length() > 0)) {
      prefix = prefix + PROP_PREFIX;
    }
    if (allProps == null) {
      logger.error("Trying to filter all props - nothing to process");
      return props;
    }
    if ((prefix != null)
        && Boolean.parseBoolean((String) allProps.getOrDefault(prefix + PASS_ALL, BridgeCommons.FALSE))) {
      logger.debug("Passing all properties for " + prefix);
      Iterator<String> iter = allProps.keySet().iterator();
      while (iter.hasNext()) {
        String key = iter.next();
        if (key.startsWith(prefix)) {
          props.put(key.substring(prefix.length()), allProps.get(key));
        }
      }
      for (int allPropIdx = 0; allPropIdx < allProps.size(); allPropIdx++) {
      }
    } else {
      if (propList != null) {
        for (int propIdx = 0; propIdx < propList.length; propIdx++) {
          String key = (prefix + propList[propIdx]).trim();
          logger.debug("looking for property " + key);
          String value = (String) allProps.get(key);
          if (value == null) {
            logger.warn("No config for " + propList[propIdx]);
          } else {
            props.put(propList[propIdx], value);
          }
        }
      } else {
        logger.info("No prop list for " + prefix.replace(BridgeCommons.PROP_PREFIX, ""));
      }
    }

    return props;
  }

  /*
   * Look to see of the properties contains a reference to a file. If it does then
   * load those properties BUT
   * don't override any set as env vars.
   */
  private static Map loadFileProps(Map<String, String> props) {
    HashMap<String, String> currentProps = new HashMap<String, String>();
    currentProps.putAll(props);
    logger.debug("Checking for file overrides");
    if ((currentProps != null) && (currentProps.containsKey(PROPS_FILE))) {
      String fileProps = currentProps.get(PROPS_FILE);
      logger.debug("checking for props file " + fileProps);
      if (fileProps != null) {
        File file = new File(fileProps);
        if (file.exists() && file.isFile() && file.canRead()) {
          logger.debug("loading props file " + fileProps);
          String line = null;
          try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while ((line = reader.readLine()) != null) {
              String[] keyValuePair = line.split("=", 2);
              if (keyValuePair.length > 1) {
                String key = keyValuePair[0].trim();
                if (currentProps.containsKey(key)) {
                  logger.info("Ignoring " + key + " from props file " + fileProps + " value already set");
                } else {
                  String value = keyValuePair[1].trim();
                  logger.info("Loadded from file key=>" + key + "< value=>" + value + "<");
                  currentProps.put(key, value);
                }
              } else {
                logger.warn("No Key:Value found in line, ignoring: " + line);
              }
            }
          } catch (IOException err) {
            logger.error("error processing config file" + err.getLocalizedMessage());
          }
        }
      }

    }
    return currentProps;
  }

  /*
   * Grab the environment properties as tease out each of the connection
   * constructs
   */
  private static void getAllProps() {
    // lower case all the keys - makes it easier to do matching later
    allProps = System.getenv();
    allProps = loadFileProps(allProps);

    String propSetStr = (String) allProps.get(CONNECTION_LIST);
    logger.info("Connections list=" + propSetStr);

    // retrieve the props-pairing
    if ((propSetStr != null) && (propSetStr.length() > 0)) {
      String[] propSets = propSetStr.split(",");
      for (int token = 0; token < propSets.length; token++) {
        try {
          String mapping = propSets[token].trim();
          int separatorPos = mapping.indexOf(CONNECTOR_MAPPING_SEPARATOR);
          String fromPrefix = mapping.substring(0, separatorPos);
          String toPrefix = mapping.substring(separatorPos + 1);
          Properties fromProps = getProps(fromPrefix, getPropParams(allProps, fromPrefix), allProps);
          Properties toProps = getProps(toPrefix, getPropParams(allProps, toPrefix), allProps);
          connections.put(mapping,
              new ConnectionPair(createConnection(fromProps, fromPrefix, true),
                  createConnection(toProps, toPrefix, false)));
        } catch (NullPointerException err) {
          logger.error(err.getMessage());
          logger.error(BridgeCommons.exceptionToString(err));
        }
      }
    }

  }

  private static boolean isMultiPass() {
    if (allProps == null) {
      logger.error("Not initialized with props");
    }
    logger.trace("getAllProps is " + IS_MULTI_PASS + " =" + allProps.get(IS_MULTI_PASS));
    boolean multiPass = Boolean.parseBoolean((String) allProps.getOrDefault(IS_MULTI_PASS, MULTI_PASS_DEFAULT));

    return multiPass;
  }

  /*
   * 
   */
  private static void singlePass() {
    logger.info("starting pass");
    Iterator<String> iter = connections.keySet().iterator();

    while (iter.hasNext()) {
      ConnectionPair connection = connections.get(iter.next());
      logger.info("Processing Connection: " + connection.getConnectionName());

      // if not connected then get connection
      if (!connection.isConnected()) {
        connection.connect();
      }

      if (connection.isConnected()) {
        connection.to.sendMessages(connection.from.getMessages());
      } else {
        logger.error("Unable to connect " + connection.getConnectionName());
      }

    }

    logger.debug("completed pass");

  }

  /*
   * We continue performing the bridge logic by looping around the single pass
   * logic
   */
  private static void multiPass() {
    boolean looping = true;
    final String requestor = "MultiPassLoop";
    int pause = Integer.parseInt((String) allProps.getOrDefault(MILLI_DELAY_ON_MULTI_PASS, "0"));

    while (looping) {
      try {
        singlePass();
        BridgeCommons.pause(requestor, pause);
      } catch (Exception err) {
        logger.error("Caught error in multi - pass \n" + BridgeCommons.exceptionToString(err));
        looping = false;
      }
    }
  }

  /*
   * Push to the console the state of play for logging configuration
   */
  private static void printLoggerInfo() {
    System.out.println("logger name " + logger.getName());
    System.out.println("logger class " + logger.getClass().getName());
    System.out.println("is debug enabled " + logger.isDebugEnabled());
    System.out.println("is info enabled " + logger.isInfoEnabled());
    System.out.println("is warn enabled " + logger.isWarnEnabled());
    System.out.println("is error enabled " + logger.isErrorEnabled());

  }

  private static void displayConnectionTypeParams() {
    logger.info("Solace props:\n" + listParams(SolaceConnection.getPropParams()));
    logger.info("OCI props:\n" + listParams(OCIQueueConnection.getPropParams()));
    logger.info("Synthetic props:\n" + listParams(SyntheticConnection.getPropParams()));
  }

  public static void main(String[] args) {
    String displayAllBasicSettings = System.getenv("DisplayAllSettings");
    if ((displayAllBasicSettings != null) && (displayAllBasicSettings.equalsIgnoreCase("True"))) {
      printLoggerInfo();
      displayConnectionTypeParams();
    }

    getAllProps();
    logger.debug("loaded all props");
    if ((displayAllBasicSettings != null) && (displayAllBasicSettings.equalsIgnoreCase("True"))) {
      logger.debug(BridgeCommons.prettyPropertiesToString(allProps, "All Props", ""));
    }

    logger.info("Is multi pass =" + isMultiPass());
    if (isMultiPass()) {
      multiPass();
    } else {
      singlePass();
    }

  }

}
