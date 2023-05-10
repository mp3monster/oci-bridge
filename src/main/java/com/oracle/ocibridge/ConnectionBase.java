package com.oracle.ocibridge;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Define the operations on the connection object.
 */
abstract class ConnectionBase implements ConnectionBaseInterface {
  private String connectionTypeName = null;
  Properties props = null;
  ConnectionBaseInterface target = null;

  private static Logger logger = LoggerFactory.getLogger(ConnectionBase.class.getName());

  private ConnectionBase() {
    logger.error("Prevent default constructor");
  }

  ConnectionBase(String typename) {
    connectionTypeName = typename;
  }

  @Override
  public void printProperties() {
    logger.info(BridgeCommons.prettyPropertiesToString(props, getConnectionName(), "\n"));
  }

  /**
   * @param target
   */
  @Override
  public void setTarget(ConnectionBaseInterface target) {
    logger.debug(connectionTypeName + " setTarget " + target.getConnectionName());
  }

  @Override
  public void shutdown() {
    logger.info(connectionTypeName + " shutdown triggered");
  }

}
