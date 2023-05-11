package com.oracle.ocibridge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.model.DeleteMessagesDetails;
import com.oracle.bmc.queue.model.DeleteMessagesDetailsEntry;
import com.oracle.bmc.queue.model.GetMessage;
import com.oracle.bmc.queue.model.PutMessagesDetails;
import com.oracle.bmc.queue.model.PutMessagesDetailsEntry;
import com.oracle.bmc.queue.requests.DeleteMessagesRequest;
import com.oracle.bmc.queue.requests.GetMessagesRequest;
import com.oracle.bmc.queue.requests.PutMessagesRequest;
import com.oracle.bmc.queue.responses.DeleteMessagesResponse;
import com.oracle.bmc.queue.responses.PutMessagesResponse;

/*
 * The OCI Connection implementation - connects and sends and receives
 */
class OCIQueueConnection extends ConnectionBase {
  private static Logger logger = LoggerFactory.getLogger(OCIQueueConnection.class.getName());
  public static final String TYPENAME = "OCIQUEUE";

  private static final String OCI_USERID = "OCI_USERID";
  private static final String OCI_TENANT_ID = "OCI_TENANT_ID";
  private static final String OCI_FINGERPRINT = "OCI_FINGERPRINT";
  private static final String OCI_QUEUEID = "OCI_QUEUEID";
  private static final String OCI_REGION = "OCI_REGION";
  private static final String OCI_PORT = "OCI_PORT";
  private static final String OCI_AUTHPROFILE = "OCI_AUTHPROFILE";
  private static final String OCI_AUTHFILE = "OCI_AUTHFILE";
  private static final String POLLDURATIONSECS = "POLLDURATIONSECS";
  private static final String QUEUENAME = "QUEUENAME";

  private static final String OCI_URL = "OCI_URL";
  private static final String ENDPOINTPREFIX = "https://cell-1.queue.messaging.";
  private static final String ENDPOINTPOSTFIX = ".oci.oraclecloud.com";

  private static final String[] OCIPROPPARAMS = new String[] { OCI_URL,
      OCI_PORT,
      OCI_REGION,
      OCI_QUEUEID,
      OCI_FINGERPRINT,
      OCI_TENANT_ID,
      OCI_USERID,
      OCI_AUTHPROFILE,
      OCI_AUTHFILE,
      BridgeCommons.CONNECTIONTYPE };

  private static final String[] OCIREQUIREDCREDPROPS = new String[] { OCI_FINGERPRINT, OCI_TENANT_ID, OCI_USERID };
  private static final String[] OCIREQUIREDADDRESSPROPS = new String[] { OCI_REGION, OCI_QUEUEID };

  private QueueClient queueClient = null;
  private String queueId = null;

  /**
   * @return String[]
   */
  static String[] getPropParams() {
    return OCIPROPPARAMS;
  }

  private static boolean checkPropsSet(String[] propList, Properties properties) {
    boolean ok = true;
    for (int propIdx = 0; propIdx < propList.length; propIdx++) {
      if (properties.get(propList[propIdx]) == null) {
        logger.warn("Expected configuration for " + propList[propIdx]);
        return false;
      }
    }
    return ok;
  }

  /*
   * Make sure we have the necessary properties - if we don't have a connection
   * file then need connection properties.
   * Need properties to formulate the URL. Port can be defaulted
   */
  private static boolean checkProps(Properties properties) {
    boolean ok = false;
    if (properties.get(OCI_AUTHFILE) == null) {
      ok = checkPropsSet(OCIREQUIREDCREDPROPS, properties) && checkPropsSet(
          OCIREQUIREDADDRESSPROPS, properties);
    } else {
      ok = checkPropsSet(
          OCIREQUIREDADDRESSPROPS, properties);
    }
    return ok;
  }

  private OCIQueueConnection() {
    super(TYPENAME);
    logger.error("Shouldn't use default constructor");
  }

  public OCIQueueConnection(Properties properties) {
    super(TYPENAME);
    props = properties;
    checkProps(props);
  }

  public OCIQueueConnection(Properties properties, boolean sendToOCI) {
    super(TYPENAME);
    props = properties;
    checkProps(props);
  }

  public boolean connected() {
    return (queueClient != null);
  }

  /*
   */
  public void connect() {
    logger.debug("Building connection for OCI \n");
    AuthenticationDetailsProvider provider = null;
    ConfigFileReader.ConfigFile configFile = null;

    String endpoint = null;
    String authPropsFile = (props.getProperty(OCI_AUTHFILE));
    if (authPropsFile != null) {
      String profileGroup = props.getProperty(OCI_AUTHPROFILE, "DEFAULT");
      try {
        configFile = ConfigFileReader.parse(authPropsFile, profileGroup);
        provider = new ConfigFileAuthenticationDetailsProvider(configFile);
      } catch (Exception err) {
        logger.error("Caught error trying to get connection properties for OCI\n" + err.getMessage()
            + BridgeCommons.exceptionToString(err));
        if (BridgeCommons.EXITONERR) {
          System.exit(1);
        }
      }
    } else {
      provider = SimpleAuthenticationDetailsProvider.builder()
          .fingerprint(props.getProperty(OCI_FINGERPRINT))
          .tenantId(props.getProperty(OCI_TENANT_ID))
          .region(Region.fromRegionCodeOrId(props.getProperty(OCI_REGION)))
          .userId(props.getProperty(OCI_USERID))
          .build();

      logger.warn("Alternate auth not setup");
    }
    queueClient = QueueClient.builder().build(provider);
    endpoint = ENDPOINTPREFIX + props.getProperty(OCI_REGION) + ENDPOINTPOSTFIX;
    queueClient.setEndpoint(endpoint);
    queueId = props.getProperty(OCI_QUEUEID);

    logger.info("OCI connection done");
  }

  /*
   */
  private void deleteMessages(List<String> receipts) {
    if ((receipts == null) || receipts.isEmpty()) {
      logger.debug("No deletion receipts to handle");
      return;
    }
    logger.debug(getConnectionName() + " deleting " + receipts.size() + " messages");
    List<DeleteMessagesDetailsEntry> entries = new ArrayList<DeleteMessagesDetailsEntry>();
    Iterator receiptsIter = receipts.iterator();

    while (receiptsIter.hasNext()) {
      String receipt = (String) receiptsIter.next();
      logger.debug(getConnectionName() + " delete request for receipt " + receipt);
      entries.add(DeleteMessagesDetailsEntry.builder().receipt(receipt).build());
    }
    DeleteMessagesResponse batchResponse = queueClient
        .deleteMessages(DeleteMessagesRequest.builder().queueId(queueId)
            .deleteMessagesDetails(DeleteMessagesDetails.builder().entries(entries).build())
            .build());

    String errors = batchResponse.getDeleteMessagesResult().getServerFailures().toString();

    // the returned value is a string representation of a number of errors that
    // occurred during deletion
    // to be defensive - ensure we have a non null value back
    if ((errors != null) && (errors.length() > 0) && (!errors.equals("0"))) {
      logger.warn("Errors deleting messages:" + errors + "\n" +
          batchResponse.getDeleteMessagesResult().toString());
    } else {
      logger.info("Delete successful for " + entries.size() + " message(s))");
    }
  }

  /*
  */
  public MessageList getMessages() {
    logger.info("OCI get messages for " + getConnectionName());
    MessageList returnMessages = new MessageList();
    ArrayList<String> receipts = new ArrayList<String>();
    int pollDurationSecs = Integer.parseInt(props.getProperty(POLLDURATIONSECS, "0"));

    List<GetMessage> messages = queueClient.getMessages(GetMessagesRequest.builder()
        .queueId(queueId)
        .timeoutInSeconds(pollDurationSecs)
        .build()).getGetMessages().getMessages();

    // iterate through each message - send message and receipt to the console and
    // then acknowledge the receipt
    // for each messages
    if (!messages.isEmpty()) {

      Iterator iter = messages.iterator();
      while (iter.hasNext()) {
        GetMessage message = (GetMessage) iter.next();
        String receipt = message.getReceipt();
        String content = message.getContent();
        logger.info(" message:" + content + " | receipt:" + receipt);
        returnMessages.add(content);
        receipts.add(receipt);
      }
    }
    deleteMessages(receipts);
    return returnMessages;
  }

  public String getConnectionName() {
    String name = props.getProperty(QUEUENAME);
    if (name == null) {
      name = props.getProperty(OCI_QUEUEID);
    }
    return "OCI:" + name;
  }

  public void sendMessages(MessageList messages) {
    if (messages.size() > 20) {
      logger.debug("Have " + messages.size() + " to send so going to break into batches");
      int batchCtr = 0;
      while (messages.size() > 20) {
        MessageList subSet = (MessageList) messages.subList(0, 19);
        sendMessages(subSet);
        logger.debug("Send batch " + ++batchCtr);
        messages.removeAll(subSet);
      }
    }
    ArrayList<PutMessagesDetailsEntry> batch = new ArrayList<>();
    for (int entry = 0; entry < messages.size(); entry++) {
      batch.add(PutMessagesDetailsEntry.builder().content(messages.get(entry)).build());
    }

    PutMessagesDetails msgDetails = PutMessagesDetails.builder().messages(batch).build();
    PutMessagesRequest request = PutMessagesRequest.builder().queueId(queueId).putMessagesDetails(msgDetails).build();

    // Send request to the Client
    PutMessagesResponse response = queueClient.putMessages(request);

    if (response != null) {
      logger.debug("Send message response:" + response.toString());
    }
    batch.clear();
  }

  @Override
  public void shutdown() {
    logger.info(getConnectionName() + " shutdown triggered");

    if (queueClient != null) {
      queueClient.close();
    }

  }
}
