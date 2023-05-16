package com.oracle.ocibridge;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 */
class SyntheticConnection extends ConnectionBase {
  /**
   *
   */
  private static final String TOTAL_MSGS = "total_msgs";

  private static Logger logger = LoggerFactory.getLogger(SyntheticConnection.class);

  public static final String TYPENAME = "Synthetic";

  private boolean connected = false;
  private int totalSyntheticMsgs = 2;
  private int totalSentCount = 0;

  private void init(Properties properties, boolean pretendToSend) {
    totalSyntheticMsgs = (Integer.parseInt((String) props.getOrDefault(TOTAL_MSGS, "2")));
    logger.info(TYPENAME + " props received " + BridgeCommons.prettyPropertiesToString(properties, TYPENAME, ""));
  }

  public SyntheticConnection(Properties properties) {
    super(TYPENAME);
    props = properties;
    init(properties, false);
  }

  public SyntheticConnection(Properties properties, boolean isSender) {
    super(TYPENAME);
    props = properties;
    init(properties, isSender);
  }

  public void connect() {
    connected = true;
    logger.info("Synthetic set as connected");
  }

  /**
   * @return String[]
   */
  static String[] getPropParams() {
    String[] props = { BridgeCommons.CONNECTIONTYPE };
    return props;
  }

  public boolean connected() {
    return connected;
  }

  public MessageList getMessages() {
    MessageList messages = new MessageList();
    logger.debug("totalSyntheticMsgs=" + totalSyntheticMsgs);
    for (int msgCtr = 0; msgCtr < totalSyntheticMsgs; msgCtr++) {
      totalSentCount++;
      messages.add("Test Message " + totalSentCount);
      logger.info("-----\nSynthetic retrieved:\n" + messages.get(msgCtr) + "\n-----");
    }
    return messages;
  }

  public String getConnectionName() {
    return TYPENAME;
  }

  public void sendMessages(MessageList messages) {
    for (int msgCtr = 0; msgCtr < messages.size(); msgCtr++) {
      logger.info("=======\nSynthetic sent:\n" + messages.get(msgCtr) + "\n=======");
    }
  }

  @Override
  public void setTarget(ConnectionBaseInterface target) {
    logger.debug(TYPENAME + " setTarget " + target.getConnectionName());
  }

  @Override
  public void shutdown() {
    logger.debug(TYPENAME + " shutdown");
  }

}
