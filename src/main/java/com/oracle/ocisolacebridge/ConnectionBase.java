package com.oracle.ocisolacebridge;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Define the operations on the connection object.
 */
abstract class ConnectionBase {
  private String connectionTypeName = null;
  Properties props = null;
  ConnectionBase target = null;

  private static Logger logger = LoggerFactory.getLogger(ConnectionBase.class);

  abstract public void connect();

  abstract public boolean connected();

  abstract public MessageList getMessages();

  abstract public String getConnectionName();

  abstract public void sendMessages(MessageList messages);

  private ConnectionBase() {
    logger.error("Prevent default constructor");
  }

  ConnectionBase(String typename) {
    connectionTypeName = typename;
  }

  public void printProperties() {
    logger.info(BridgeCommons.prettyPropertiesToString(props, getConnectionName(), "\n"));
  }

  /**
   * @param target
   */
  public void setTarget(ConnectionBase target) {
    logger.debug(connectionTypeName + " setTarget " + target.getConnectionName());
  }

  public void shutdown() {
    logger.info(connectionTypeName + " shutdown triggered");
  }

}
