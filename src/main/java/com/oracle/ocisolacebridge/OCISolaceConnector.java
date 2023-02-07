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
import java.util.ArrayList;
import java.util.Iterator;

import com.solace.messaging.MessagingService;
import com.solace.messaging.config.SolaceConstants.AuthenticationConstants;
import com.solace.messaging.config.SolaceProperties.AuthenticationProperties;
import com.solace.messaging.config.SolaceProperties.TransportLayerProperties;
import com.solace.messaging.config.profile.ConfigurationProfile;
import com.solace.messaging.publisher.PersistentMessagePublisher;
import com.solace.messaging.publisher.MessagePublisher;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.receiver.DirectMessageReceiver;
import com.solace.messaging.receiver.InboundMessage;
import com.solace.messaging.resources.Topic;
import com.solace.messaging.resources.Queue;
import com.solace.messaging.resources.TopicSubscription;
import com.solace.messaging.util.LifecycleControl.TerminationEvent;
import com.solace.messaging.util.LifecycleControl.TerminationNotificationListener;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPStreamingPublishEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

import shaded.com.oracle.oci.javasdk.io.vavr.control.Try;

import com.solace.messaging.config.SolaceProperties.ServiceProperties;
import com.solace.messaging.publisher.DirectMessagePublisher.FailedPublishEvent;
import com.solace.messaging.publisher.DirectMessagePublisher.PublishFailureListener;

import com.solace.messaging.receiver.DirectMessageReceiver;
import com.solace.messaging.receiver.MessageReceiver.MessageHandler;

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.Region;
import com.oracle.bmc.queue.model.PutMessagesDetailsEntry;
import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.model.PutMessagesDetails;
import com.oracle.bmc.queue.requests.PutMessagesRequest;
import com.oracle.bmc.queue.responses.PutMessagesResponse;

import org.bouncycastle.pqc.crypto.MessageSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OCISolaceConnector extends Object {

  private static final boolean EXITONERR = true;

  private static final String CONNECTIONTYPE = "Type";
  private static Logger logger = LoggerFactory.getLogger(OCISolaceConnector.class);

  static boolean multiPass = false;
  static ConnectionsMap myConnectionMappings = new ConnectionsMap();
  static HashMap<String, ConnectionBase> myIndividualConnections = new HashMap<String, ConnectionBase>();

  public static void prettyPropertiesToString(Properties props, String propDescription) {
    if (props == null) {
      logger.warn(propDescription + " IS NULL");
    } else {
      logger.info("\nProps for " + propDescription + " ...");
      Iterator iter = props.keySet().iterator();
      while (iter.hasNext()) {
        String key = (String) iter.next();
        String value = props.getProperty(key);
        logger.info(propDescription + ": " + key + "==" + value);
      }
      logger.info("\n\n");

    }
  }

  static class ConnectionsMap extends HashMap<String, ConnectionPair> {
  }

  static class MessageList extends ArrayList<String> {
  }

  /*
   * Define the operations on the connection object.
   */
  static interface ConnectionBase {

    public void connect();

    public boolean connected();

    public MessageList getMessages();

    public String getConnectionName();

    public void sendMessages(MessageList messages);

    public void printProperties();

    public void setTarget(ConnectionBase target);

    public void shutdown();

  }

  /*
  */
  static class SyntheticConnection implements ConnectionBase {
    public static final String TYPENAME = "Synthetic";
    private Properties props = null;

    private boolean connected = false;
    private int totalSyntheticMsgs = 2;
    private int totalSentCount = 0;

    public SyntheticConnection(Properties properties) {
      logger.info(TYPENAME + " props received " + properties.toString());
      props = properties;
    }

    public void connect() {
      connected = true;
      logger.info("Synthetic set as connected");
    }

    static String[] getPropParams() {
      String[] props = { CONNECTIONTYPE };
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
      logger.debug("get name for " + TYPENAME);
      return TYPENAME;
    }

    public void sendMessages(MessageList messages) {
      for (int msgCtr = 0; msgCtr < messages.size(); msgCtr++) {
        logger.info("Synthetic received:" + messages.get(msgCtr));
      }
    }

    public void printProperties() {
      prettyPropertiesToString(props, getConnectionName());
    }

    public void setTarget(ConnectionBase target) {
      logger.debug(TYPENAME + " setTarget " + target.getConnectionName());
    }

    public void shutdown() {
      logger.debug(TYPENAME + " shutdown");
    }

  }

  /*
   * The OCI Connection implementation - connects and sends and receives
   */
  static class OCIConnection implements ConnectionBase {
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
        CONNECTIONTYPE };

    private Properties props = null;

    private QueueClient queueClient = null;

    static String[] getPropParams() {
      return OCIPROPPARAMS;
    }

    public OCIConnection(Properties properties) {
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
          if (EXITONERR) {
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
      PutMessagesResponse response = queueClient.putMessages(request);

      if (response != null) {
        logger.debug("Send message response:" + response.toString());
      }
    }

    public void printProperties() {
      prettyPropertiesToString(props, getConnectionName());
    }

    public void setTarget(ConnectionBase target) {
      logger.debug(TYPENAME + " setTarget " + target.getConnectionName());
    }

    public void shutdown() {
      logger.debug(TYPENAME + " shutdown");
    }

  }

  static class SolaceHandler implements PublishFailureListener, TerminationNotificationListener,
      JCSMPStreamingPublishCorrelatingEventHandler {

    @Override
    public void onFailedPublish(FailedPublishEvent e) {
      logger.warn("Producer received error: " + e.getMessage());
    }

    @Override
    public void onTermination(TerminationEvent e) {
      logger.error("receiving termination event: " + e.getMessage());

    }

    @Override
    public void responseReceived(String messageID) {
      System.out.println("Producer received response for msg: " + messageID);
    }

    @Override
    public void handleError(String messageID, JCSMPException e, long timestamp) {
      logger.warn("Producer received error for msg: %s@%s - %s%n", messageID, timestamp, e);
    }

    @Override
    public void handleErrorEx(Object arg0, JCSMPException arg1, long arg2) {
      logger.warn("Producer received error: " + arg1.getMessage());

    }

    @Override
    public void responseReceivedEx(Object arg0) {
      logger.warn("Producer received response: " + arg0.toString());

    }
  }

  /*
   * This is the Solace implementation of the Connection base - allows us to
   * connect
   * and send to Solace or pull from Solace messages
   */
  static class SolaceConnection implements ConnectionBase {

    public static final String TYPENAME = "Solace";
    private static final String DEFAULTPORT = "77777";
    private static final String TOPICNAME = "SOLACE_TOPICNAME";
    private static final String ADDR = "SOLACE_ADDR";
    private static final String PORT = "SOLACE_PORT";
    private static final String VPN = "SOLACE_VPN";
    private static final String CLIENTNAME = "CLIENT_NAME";

    private static final String[] SOLACEPROPPARAMS = new String[] { ADDR,
        PORT,
        VPN,
        AuthenticationProperties.SCHEME,
        AuthenticationProperties.SCHEME_BASIC_USER_NAME,
        AuthenticationProperties.SCHEME_BASIC_PASSWORD,
        ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY,
        TOPICNAME,
        CLIENTNAME,
        VPN,
        CONNECTIONTYPE };

    private Properties props = null;
    private boolean isPublisher = true;
    private MessagingService messagingService = null;
    // private MessagePublisher publisher = null;
    private PersistentMessagePublisher publisher = null;
    private DirectMessageReceiver receiver = null;
    private MessageHandler messageHandler = null;
    private ConnectionBase target = null;

    private JCSMPProperties solaceProps = new JCSMPProperties();

    static String[] getPropParams() {
      return SOLACEPROPPARAMS;
    }

    /*
     * Stores the properties provided
     */
    public SolaceConnection(Properties properties, boolean isPublisher) {
      props = properties;
      this.isPublisher = isPublisher;
    }

    /*
     * checks to see if we're connected to Solace
     * 
     * @Returns: True when connected
     */
    public boolean connected() {
      return ((publisher != null) && (messagingService != null));
    }

    void setupPublisher() {
      logger.info("Setting up Solace message publisher");

      publisher = messagingService.createPersistentMessagePublisherBuilder().build();

      publisher.setTerminationNotificationListener(new SolaceHandler());
      // setPublishFailureListener(new ErrorHandler());
      publisher.start();

      while (!publisher.isReady()) {
        final int sleepTime = 5000;
        int sleepCtr = 0;
        logger.info("Waiting on Solace publisher to be ready");
        try {
          sleepCtr++;
          Thread.sleep(5000);
        } catch (InterruptedException interrupt) {
          logger.warn(
              "Disturbed waiting for publisher to be ready - slept for " + (sleepCtr * sleepTime) / 1000 + " seconds");
        }
      }
      logger.debug("Solace publisher is ready");
    }

    void setupReceiver() {
      logger.info("Setting up Solace message receiver");
      if (receiver == null) {
        // TopicSubscription topic = TopicSubscription.of(props.getProperty(TOPICNAME));
        TopicSubscription topic = TopicSubscription.of("*");
        receiver = messagingService.createDirectMessageReceiverBuilder()
            .withSubscriptions(topic).build().start();
      } else {
        if (!receiver.isRunning()) {
          logger.warn("Receive not running - starting (again)...");
          receiver.start();
        }
      }
      if (messageHandler == null) {
        messageHandler = (inboundMessage) -> {
          String message = inboundMessage.dump();
          if (target != null) {
            MessageList messages = new MessageList();
            messages.add(message);
            target.sendMessages(messages);
          }
          logger.info("message hander read >>" + message);
        };
      }
    }

    /*
     * Constructs the connection to the Solace broker
     * configuration is taken from the properties object
     */
    public void connect() {
      logger.info("Building connection for Solace");

      if (props.getProperty(TransportLayerProperties.HOST) == null) {
        String address = props.getProperty(ADDR) + ":" + props.getProperty(PORT, DEFAULTPORT);
        props.setProperty(TransportLayerProperties.HOST, address);
      }
      if (props.getProperty(AuthenticationProperties.SCHEME) == null) {
        props.setProperty(AuthenticationProperties.SCHEME, AuthenticationConstants.AUTHENTICATION_SCHEME_BASIC);
      }

      if (props.getProperty(ServiceProperties.VPN_NAME) == null) {
        props.setProperty(ServiceProperties.VPN_NAME, props.getProperty(VPN, "default"));
      }

      if (props.getProperty(ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY) == null) {
        props.setProperty(ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY, "true");
      }

      String clientname = props.getProperty(CLIENTNAME, "default");

      prettyPropertiesToString(props, getConnectionName());

      messagingService = MessagingService.builder(ConfigurationProfile.V1).fromProperties(props)
          .build(clientname).connect();
      logger.debug("Building message service for Solace completed - app id is " + messagingService.getApplicationId()
          + "; is connected " + messagingService.isConnected());

      if (isPublisher) {
        setupPublisher();
      } else {
        setupReceiver();
      }

      logger.debug("Building connection for Solace completed");
    }

    /*
     */
    public String getConnectionName() {
      return TYPENAME + ":" + props.getProperty(TOPICNAME);
    }

    /*
     */
    public MessageList getMessages() {
      logger.debug("Solace get messages");
      MessageList messages = new MessageList();
      TopicSubscription topic = TopicSubscription.of(props.getProperty(TOPICNAME));

      if (!messagingService.isConnected()) {
        logger.warn("reconnecting");
        messagingService.connect();
      }
      receiver = messagingService.createDirectMessageReceiverBuilder()
          .withSubscriptions(topic).build().start();
      logger.debug("receive created");

      InboundMessage message = receiver.receiveMessage(1);
      if (message != null) {
        String recdMessage = message.dump();
        logger.info("message is:" + recdMessage);
        messages.add(recdMessage);
      } else {
        logger.warn("null message object");
      }
      return messages;
    }

    /*
     */
    public void sendMessages(MessageList messages) {
      // Topic topic = Topic.of(props.getProperty(TOPICNAME));

      if (messages == null) {
        logger.warn("No messages to send");
        return;
      }
      logger.info("Sending to Solace " + messages.size() + " messages on " + props.getProperty(TOPICNAME));
      for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
        String msg = messages.get(msgIdx);
        logger.info("sending:" + msg);

        if (!messagingService.isConnected()) {
          logger.warn("Solace sendMessages needs to reconnect messagingService");
          messagingService.connect();
        }
        // OutboundMessage message = messagingService.messageBuilder().build(msg);
        try {
          // publisher.(message);
          // https://github.com/SolaceDev/solace-samples-java-jcsmp/blob/master/src/main/java/com/solace/samples/jcsmp/features/QueueProducer.java
          solaceProps.setProperty(JCSMPProperties.HOST, props.getProperty(TransportLayerProperties.HOST));
          solaceProps.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME,
              props.getProperty(AuthenticationProperties.SCHEME));
          solaceProps.setProperty(JCSMPProperties.USERNAME,
              props.getProperty(AuthenticationProperties.SCHEME_BASIC_USER_NAME));
          solaceProps.setProperty(JCSMPProperties.PASSWORD,
              props.getProperty(AuthenticationProperties.SCHEME_BASIC_PASSWORD));
          solaceProps.setProperty(JCSMPProperties.VPN_NAME, props.getProperty(ServiceProperties.VPN_NAME));
          logger.debug("Solace Props:\n" + solaceProps.toString());

          JCSMPSession session = JCSMPFactory.onlyInstance().createSession(solaceProps);
          TextMessage solaceMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
          solaceMsg.setDeliveryMode(DeliveryMode.PERSISTENT);
          solaceMsg.setText(msg);
          // Send message directly to the queue

          session.connect();
          XMLMessageProducer prod = session.getMessageProducer(new SolaceHandler());
          String qName = props.getProperty(TOPICNAME);
          final com.solacesystems.jcsmp.Queue queue = JCSMPFactory.onlyInstance().createQueue(qName);
          prod.send(solaceMsg, queue);

        } catch (Exception publishErr) {
          logger.error("Problem with publishing message:" + publishErr.getMessage());
          publishErr.printStackTrace();
        }
      }
      logger.debug("Send done");
    }

    public void printProperties() {
      prettyPropertiesToString(props, getConnectionName());
    }

    public void setTarget(ConnectionBase target) {
      this.target = target;
      logger.debug(TYPENAME + " setTarget " + target.getConnectionName());
    }

    public void shutdown() {
      logger.debug(TYPENAME + " shutdown for " + getConnectionName());
      if (messagingService != null) {
        logger.debug("Disconnecting messageService");
        messagingService.disconnect();
        messagingService = null;
        logger.debug("Disconnected messageService");

      }
      // private MessagePublisher publisher = null;
      if (publisher != null) {
        logger.debug("Disconnecting publisher");
        publisher.terminate(0);
        publisher = null;
        logger.debug("Disconnected publisher");
      }
      if (receiver != null) {
        logger.debug("Disconnecting receiver");
        receiver.terminate(0);
        receiver = null;
        logger.debug("Disconnected receiver");

      }
    }

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
    String propName = prefix + "." + CONNECTIONTYPE;
    String connType = (String) props.get(propName);
    logger.debug("get Prop params for " + propName + " with type " + connType);
    if (connType != null) {
      switch (connType) {
        case OCIConnection.TYPENAME:
          params = OCIConnection.getPropParams();
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
    String propName = CONNECTIONTYPE;

    if ((props != null) && (prefix != null)) {
      connType = props.getProperty(propName, TYPETAG);
    } else {
      logger.info(
          "Can't locate connection type - info incomplete. props" + (props == null) + " prefix is " + (prefix == null));
    }

    switch (connType) {
      case OCIConnection.TYPENAME:
        instance = new OCIConnection(props);
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
      logger.debug("from null=" + (from == null) + " connected =" +
          from.connected() + "|| to null=" + (to == null) + " connected=" +
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
  private static void getAllProps() {
    Map allProps = System.getenv();
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

          ConnectionBase from = createConnection(fromProps, fromPrefix, true);
          ConnectionBase to = createConnection(toProps, toPrefix, false);
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
          logger.error(err.getMessage());
          err.printStackTrace();
          if (EXITONERR) {
            System.exit(1);
          }
        }
      }
    }

    multiPass = ((String) allProps.getOrDefault("isMultiPass", "False")).equalsIgnoreCase("TRUE");
    logger.debug("Is a multi-pass run -->" + multiPass);
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
    boolean looping = true;
    logger.info("About to start multi-pass...");

    while (looping) {
      try {
        singlePass();
        Thread.sleep(1000);
        logger.info(" ------------- \n\n");
      } catch (Exception err) {
        looping = false;
        logger.error("Caught error - " + err.getMessage());
        err.printStackTrace();
        if (EXITONERR) {
          System.exit(1);
        }

      }
    }
  }

  private static void testConnections() {
    logger.info("Starting connectiobn test...");
    Iterator<String> iter = myIndividualConnections.keySet().iterator();

    while (iter.hasNext()) {
      String connectionName = iter.next();
      ConnectionBase connection = myIndividualConnections.get(connectionName);
      logger.info("\n\nProcessing Connection: " + connectionName);
      connection.connect();
      logger.info(connectionName + " connected");
      MessageList messages = new MessageList();
      messages.add("Hello world");
      logger.info(connectionName + " about to send");
      connection.sendMessages(messages);
      logger.info(connectionName + " SENT");
      connection.shutdown();
      logger.info(connectionName + " DONE\n\n");
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    logger.debug("Logging set as:" + logger.getClass().getName());
    getAllProps();

    // logger.info("Solace props:\n" +
    // listParams(SolaceConnection.getPropParams()));
    // logger.info("OCI props:\n" +
    // listParams(OCIConnection.getPropParams()));

    boolean testConnections = true;
    if (testConnections) {
      testConnections();
    } else {
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
