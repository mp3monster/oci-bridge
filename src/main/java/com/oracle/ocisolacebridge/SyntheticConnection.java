package com.oracle.ocisolacebridge;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 */
class SyntheticConnection extends ConnectionBase {
  private static Logger logger = LoggerFactory.getLogger(SyntheticConnection.class);

  public static final String TYPENAME = "Synthetic";

  private boolean connected = false;
  private int totalSyntheticMsgs = 2;
  private int totalSentCount = 0;

  public SyntheticConnection(Properties properties) {
    super(TYPENAME);
    logger.info(TYPENAME + " props received " + properties.toString());
    props = properties;
  }

  public void connect() {
    connected = true;
    logger.info("Synthetic set as connected");
  }

  static String[] getPropParams() {
    String[] props = { BridgeCommons.CONNECTIONTYPE };
    return props;
  }

  public boolean connected() {
    return connected;
  }

  public MessageList getMessages() {
    MessageList messages = new MessageList();
    for (int msgCtr = 0; msgCtr < totalSyntheticMsgs; msgCtr++) {
      totalSentCount++;
      messages.add("Test Message " + totalSentCount);
    }
    return messages;
  }

  public String getConnectionName() {
    return TYPENAME;
  }

  public void sendMessages(MessageList messages) {
    for (int msgCtr = 0; msgCtr < messages.size(); msgCtr++) {
      logger.info("=======\nSynthetic received:\n" + messages.get(msgCtr) + "\n=======");
    }
  }

  @Override
  public void setTarget(ConnectionBase target) {
    logger.debug(TYPENAME + " setTarget " + target.getConnectionName());
  }

  @Override
  public void shutdown() {
    logger.debug(TYPENAME + " shutdown");
  }

}
