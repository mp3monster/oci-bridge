package com.oracle.ocisolacebridge;

/*
 * Notes:
 * Maven with Solace https://docs.solace.com/API/API-Developer-Guide-Java/Java-build-projects.htm?utm_source=pocket_reader
 * Solace Javadoc - https://docs.solace.com/API-Developer-Online-Ref-Documentation/pubsubplus-java/index.html
 * Solace code frag https://www.solace.dev/
 * Solace Quick start setup https://solace.com/products/event-broker/software/getting-started/
 */
import java.util.Properties;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OCISolaceConnector extends Object {

  private static Logger logger = LoggerFactory.getLogger(OCISolaceConnector.class);

  static boolean multiPass = false;
  static ConnectionsMap myConnectionMappings = new ConnectionsMap();
  static HashMap<String, ConnectionBase> myIndividualConnections = new HashMap<String, ConnectionBase>();

  static class ConnectionsMap extends HashMap<String, ConnectionPair> {
  }

  /**
   * Use this so we can see the parameters that are needed. Helpful when looking
   * at Solace config needs
   * 
   * @param propParams
   * @return String
   */
  static String listParams(String[] propParams) {
    String indent = "   ";
    String output = "";
    for (int paramIdx = 0; paramIdx < propParams.length; paramIdx++) {
      output = output + indent + propParams[paramIdx] + "\n";
    }
    return output;
  }

  /**
   * @param props
   * @param prefix
   * @return String[]
   */
  static String[] getPropParams(Map props, String prefix) {
    String[] params = null;
    String propName = prefix + "." + BridgeCommons.CONNECTIONTYPE;
    String connType = (String) props.get(propName);
    logger.debug("get Prop params for " + propName + " with type " + connType);
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
      }
    } else {
      logger.info("No connection type defined for " + prefix + " expected property " + propName);
    }

    return params;
  }

  /**
   * Build a connection object based on the configuration params. This is the
   * factory for connections
   * 
   * @param props
   * @param prefix
   * @return ConnectionBase
   */
  static ConnectionBase createConnection(Properties props, String prefix, boolean isPublisher) {
    ConnectionBase instance = null;
    final String TYPETAG = "<NOT SET>";
    String connType = TYPETAG;
    String propName = BridgeCommons.CONNECTIONTYPE;

    if ((props != null) && (prefix != null)) {
      connType = props.getProperty(propName, TYPETAG);
    } else {
      logger.info(
          "Can't locate connection type - info incomplete. props" + (props == null) + " prefix is " + (prefix == null));
    }

    switch (connType) {
      case OCIQueueConnection.TYPENAME:
        instance = new OCIQueueConnection(props);
        break;

      case SolaceConnection.TYPENAME:
        instance = new SolaceConnection(props, isPublisher);
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

    ConnectionBase from = null;
    ConnectionBase to = null;

    public ConnectionPair(ConnectionBase from, ConnectionBase to) {
      this.from = from;
      this.to = to;

      if ((from == null) || (to == null)) {
        logger.warn("Connection pair from set " + (from != null) + " | to is set " + (to != null));
      } else {
        logger.debug("Connection pair created with " + from.getConnectionName() + " --> " + to.getConnectionName());
      }
    }

    public boolean isConnected() {
      logger.debug("from null=" +
          (from == null) +
          " connected =" +
          from.connected() +
          "|| to null=" +
          (to == null) +
          " connected=" +
          to.connected());
      return (from != null) && (to != null) && from.connected() && to.connected();
    }

    /*
    */
    public void connect() {
      if ((to == null) || (from == null)) {
        logger.warn("Missing a connection");
      }
      if (to != null) {
        to.connect();
      }
      if (from != null) {
        from.connect();
      }
    }

    /*
    */
    public void log() {
      logger.info("from:\n" + from.toString());
      logger.info("to:\n" + to.toString());
      logger.info("Is connected=" + isConnected());
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

  /**
   * @param prefix
   * @param propList
   * @param allProps
   * @return Properties
   */
  /*
   * Extract the necessary properties for a connection
   */
  private static Properties getProps(String prefix, String[] propList, Map allProps) {
    Properties props = new Properties();
    if ((prefix != null) && (prefix.length() > 0)) {
      prefix = prefix + ".";
    }

    if (propList != null) {
      for (int propIdx = 0; propIdx < propList.length; propIdx++) {
        String value = (String) allProps.get(prefix + propList[propIdx]);
        if (value == null) {
          logger.warn("INFO no config for " + propList[propIdx]);
        } else {
          props.put(propList[propIdx], value);
        }
      }
    } else {
      logger.warn("No prop list");
    }
    return props;
  }

  /*
   * Grab the environment properties as tease out each of the connection
   * constructs
   */
  private static void getAllProps(Map allProps) {
    String propSetStr = (String) allProps.get("ConnectionList");
    logger.debug("Connections list=" + propSetStr);

    // retrieve the props-pairing
    if ((propSetStr != null) && (propSetStr.length() > 0)) {
      String[] propSets = propSetStr.split(",");
      for (int token = 0; token < propSets.length; token++) {
        try {
          String mapping = propSets[token].trim();
          int separatorPos = mapping.indexOf("-");
          String fromPrefix = mapping.substring(0, separatorPos);
          String toPrefix = mapping.substring(separatorPos + 1);
          Properties fromProps = getProps(fromPrefix, getPropParams(allProps, fromPrefix), allProps);
          Properties toProps = getProps(toPrefix, getPropParams(allProps, toPrefix), allProps);

          ConnectionBase from = createConnection(fromProps, fromPrefix, false);
          ConnectionBase to = createConnection(toProps, toPrefix, true);
          if (from != null) {
            myIndividualConnections.put(from.getConnectionName(), from);
            from.setTarget(to);
            // from.connect();
          }
          if (to != null) {
            myIndividualConnections.put(to.getConnectionName(), to);
            // to.connect();
          }
          myConnectionMappings.put(mapping, new ConnectionPair(from, to));
        } catch (NullPointerException err) {
          logger.error(err.getMessage() + "\nTrace is:\n" + BridgeCommons.exceptionToString(err));
          if (BridgeCommons.EXITONERR) {
            System.exit(1);
          }
        }
      }
    }

  }

  /*
   * 
   */
  private static void singlePass() {
    logger.info("Starting pass...");
    Iterator<String> iter = myConnectionMappings.keySet().iterator();

    while (iter.hasNext()) {
      ConnectionPair connection = myConnectionMappings.get(iter.next());
      logger.info("Processing Connection: " + connection.getConnectionName());

      // if not connected then get connection
      if (!connection.isConnected()) {
        connection.connect();
      }

      if (connection.isConnected()) {
        connection.to.sendMessages(connection.from.getMessages());
      } else {
        logger.warn("Unable to connect " + connection.getConnectionName());
      }
      logger.info("... Completed pass");

    }

  }

  private static void multiPass() {
    int interPassDelay = 1000;
    boolean looping = true;
    logger.info("About to start multi-pass...");

    while (looping) {
      try {
        singlePass();
        Thread.sleep(interPassDelay);
        logger.info(" ------------- \n\n");
      } catch (Exception err) {
        looping = false;
        logger.error("Caught error - " + err.getMessage() + "\n" + BridgeCommons.exceptionToString(err));
        if (BridgeCommons.EXITONERR) {
          System.exit(1);
        }

      }
    }
  }

  private static void testConnections() {
    logger.info("Starting connection test...");
    Iterator<String> iter = myIndividualConnections.keySet().iterator();

    while (iter.hasNext()) {
      String connectionName = iter.next();
      ConnectionBase connection = myIndividualConnections.get(connectionName);
      logger.info("Processing Connection: " + connectionName);
      connection.connect();
      logger.info(connectionName + " connected");
      MessageList messages = new MessageList();
      messages.add("Hello world");
      logger.info(connectionName + " about to send");
      connection.sendMessages(messages);
      logger.info(connectionName + " SENT");
      connection.shutdown();
      logger.info(connectionName + " DONE");
    }
  }

  static void listPropertiesForConnections() {
    Iterator<String> iter = myIndividualConnections.keySet().iterator();
    while (iter.hasNext()) {
      String connectionName = iter.next();
      ConnectionBase connection = myIndividualConnections.get(connectionName);
      logger.info("Properties for connection " + connection.getConnectionName());
      connection.printProperties();
      logger.info("\n --", connectionName);
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    logger.debug("Logging set as:" + logger.getClass().getName());
    Map allProps = System.getenv();
    boolean testConnections = ((String) allProps.getOrDefault("Testing", "False")).equalsIgnoreCase("True");

    getAllProps(allProps);

    if (((String) (allProps.getOrDefault("ListConnectorProps", "False"))).equalsIgnoreCase("True")) {
      listPropertiesForConnections();
    }

    if (testConnections) {
      testConnections();
    } else {
      multiPass = ((String) allProps.getOrDefault("isMultiPass", "False")).equalsIgnoreCase("TRUE");
      logger.debug("Is a multi-pass run -->" + multiPass);

      logger.info("retrieved config ...");
      try {
        if (multiPass) {
          multiPass();
        } else {
          singlePass();
        }
      } catch (RuntimeException err) {
        logger.error("Caught err:" + err.getMessage());
      }
    }

  }

}
