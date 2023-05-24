package com.oracle.ocibridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;

public class OCIStreaming extends ConnectionBase {

  public static final String TYPENAME = "OCISTREAM";
  private static Logger logger = LoggerFactory.getLogger(OCIQueueConnection.class.getName());

  private static final String OCI_USERID = "OCI_USERID";
  private static final String OCI_TENANT_ID = "OCI_TENANT_ID";
  private static final String OCI_FINGERPRINT = "OCI_FINGERPRINT";
  private static final String OCI_REGION = "OCI_REGION";
  private static final String OCI_PORT = "OCI_PORT";
  private static final String OCI_AUTHPROFILE = "OCI_AUTHPROFILE";
  private static final String OCI_AUTHFILE = "OCI_AUTHFILE";
  private static final String OCI_URL = "OCI_URL";

  private static final String[] OCIPROPPARAMS = new String[] { OCI_URL,
      OCI_PORT,
      OCI_REGION,
      OCI_FINGERPRINT,
      OCI_TENANT_ID,
      OCI_USERID,
      OCI_AUTHPROFILE,
      OCI_AUTHFILE,
      BridgeCommons.CONNECTIONTYPE };

  /**
   * @return String[]
   */
  static String[] getPropParams() {
    return OCIPROPPARAMS;
  }

  private OCIStreaming() {
    super(TYPENAME);
    logger.error("Constructor in OCIStreaming incorrectly called");
  };

  public OCIStreaming(Properties properties) {
    super(TYPENAME);
    props = properties;
    logger.error("Constructor in OCIStreaming called not fully implemented");

  }

  @Override
  public void connect() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'connect'");
  }

  @Override
  public boolean connected() {
    // TODO Auto-generated method stub
    logger.error("unimplemented method for OCIStreaming");
    return false;
  }

  @Override
  public MessageList getMessages() {
    // TODO Auto-generated method stub
    MessageList messageList = new MessageList();
    logger.error("unimplemented method for OCIStreaming - returning empty message list");
    return messageList;
  }

  @Override
  public String getConnectionName() {
    logger.error("unimplemented method for OCIStreaming");
    return "OCIStreaming - not implemented";
  }

  @Override
  public void sendMessages(MessageList messages) {
    logger.error("unimplemented method for OCIStreaming");
  }

}
