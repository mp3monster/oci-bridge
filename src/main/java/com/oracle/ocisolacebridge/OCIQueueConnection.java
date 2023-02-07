package com.oracle.ocisolacebridge;

import java.util.ArrayList;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.model.PutMessagesDetails;
import com.oracle.bmc.queue.model.PutMessagesDetailsEntry;
import com.oracle.bmc.queue.requests.PutMessagesRequest;
import com.oracle.bmc.streaming.responses.PutMessagesResponse;

/*
 * The OCI Connection implementation - connects and sends and receives
 */
class OCIQueueConnection extends ConnectionBase {
  private static Logger logger = LoggerFactory.getLogger(OCIQueueConnection.class);
  public static final String TYPENAME = "OCIQUEUE";

  private static final String OCI_USERID = "OCI_USERID";
  private static final String OCI_TENANT_ID = "OCI_TENNANT_ID";
  private static final String OCI_FINGERPRINT = "OCI_FINGERPRINT";
  private static final String OCI_QUEUEID = "OCI_QUEUEID";
  private static final String OCI_REGION = "OCI_REGION";
  private static final String OCI_PORT = "OCI_PORT";
  private static final String OCI_AUTHPROFILE = "OCI_AUTHPROFILE";
  private static final String OCI_AUTHFILE = "OCI_AUTHFILE";

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

  private QueueClient queueClient = null;

  static String[] getPropParams() {
    return OCIPROPPARAMS;
  }

  private OCIQueueConnection() {
    super(TYPENAME);
    logger.error("Shouldnt use default constructor");
  }

  public OCIQueueConnection(Properties properties) {
    super(TYPENAME);
    props = properties;
  }

  public boolean connected() {
    return (queueClient != null);
  }

  /*
   */
  public void connect() {
    logger.debug("Building connection for OCI");
    AuthenticationDetailsProvider provider = null;
    ConfigFileReader.ConfigFile configFile = null;

    String endpoint = null;
    String authPropsFile = (props.getProperty(OCI_AUTHFILE));
    if (authPropsFile != null) {
      // String profileGroup = props.getProperty(OCI_AUTHPROFILE, "DEFAULT");
      try {
        configFile = ConfigFileReader.parseDefault();
        // configFile = ConfigFileReader.parse(authPropsFile, profileGroup);
        provider = new ConfigFileAuthenticationDetailsProvider(configFile);
      } catch (Exception err) {
        logger.error("Caught error trying to get connection properties for OCI\n" + err.getMessage());
        err.printStackTrace();
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

    logger.info("OCI connection done");
  }

  /*
  */
  public MessageList getMessages() {
    logger.warn("OCI get messages TBD");
    return null;
  }

  public String getConnectionName() {
    return "OCI:" + props.getProperty(OCI_QUEUEID);
  }

  public void sendMessages(MessageList messages) {
    // TODO apply 20 message control
    ArrayList<PutMessagesDetailsEntry> batch = new ArrayList<>();
    for (int entry = 0; entry < messages.size(); entry++) {
      batch.add(PutMessagesDetailsEntry.builder().content(messages.get(entry)).build());
    }

    String queueId = props.getProperty(OCI_QUEUEID);
    PutMessagesDetails msgDetails = PutMessagesDetails.builder().messages(batch).build();
    PutMessagesRequest request = PutMessagesRequest.builder().queueId(queueId).putMessagesDetails(msgDetails).build();

    // Send request to the Client
    PutMessagesResponse response = null; // queueClient.putMessages(request);

    if (response != null) {
      logger.debug("Send message response:" + response.toString());
    }
  }

}
