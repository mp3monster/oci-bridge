package com.oracle.ocibridge;

import java.util.ListIterator;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solace.messaging.MessagingService;
import com.solace.messaging.config.SolaceProperties.ServiceProperties;
import com.solace.messaging.publisher.PersistentMessagePublisher;
import com.solace.messaging.publisher.DirectMessagePublisher.FailedPublishEvent;
import com.solace.messaging.publisher.DirectMessagePublisher.PublishFailureListener;
import com.solace.messaging.receiver.DirectMessageReceiver;
import com.solace.messaging.receiver.InboundMessage;
import com.solace.messaging.receiver.MessageReceiver.MessageHandler;
import com.solace.messaging.resources.TopicSubscription;
import com.solace.messaging.util.LifecycleControl.TerminationEvent;
import com.solace.messaging.util.LifecycleControl.TerminationNotificationListener;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

//import com.oracle.oci.javasdk.javassist.bytecode.Descriptor.Iterator;

/*
   * This is the Solace implementation of the Connection base - allows us to
   * connect
   * and send to Solace or pull from Solace messages
   * Illustration of queue use - https://github.com/SolaceDev/solace-samples-java-jcsmp/blob/master/src/main/java/com/solace/samples/jcsmp/features/QueueProducer.java

   */
class SolaceConnection extends ConnectionBase {

  /**
   * As propertuies passed into Solace require a dot based notation we need to
   * perform a substitution - note dots are not allowed in environment variables
   * for Linux
   */
  private static final String INTERNAL_PREFIX = ".";

  public static final String TYPENAME = "Solace";

  /**
   *
   */
  private static final String DEFAULTVPN = "default";

  /**
   *
   */
  private static final int DEFAULT_SLEEP = 5000;

  static class SolaceHandler implements PublishFailureListener, TerminationNotificationListener,
      JCSMPStreamingPublishCorrelatingEventHandler {

    @Override
    public void onFailedPublish(FailedPublishEvent err) {
      logger.warn("Producer received error: " + err.getMessage());
    }

    @Override
    public void onTermination(TerminationEvent err) {
      logger.info("receiving termination event: " + err.getMessage());

    }

    @Override
    public void responseReceived(String messageID) {
      logger.info("Producer received response for msg: " + messageID);
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
      String logOut = "Producer received response: ";
      if (arg0 != null) {
        logOut += (arg0.toString());
      } else {
        logOut += (" empty param received");
      }
      logger.warn(logOut);

    }
  }

  private static Logger logger = LoggerFactory.getLogger(SolaceConnection.class);

  private static final String TOPICNAME = "SOLACE_TOPICNAME";
  private static final String CLIENTNAME = "CLIENT_NAME";
  private static final String QUEUECONNECTION = "QUEUE";
  private static final String TOPICCONNECTION = "TOPIC";
  private static final String MESSAGETYPE = "MESSAGE_TYPE";

  private static final String[] JCSMPPROPPARAMS = new String[] { JCSMPProperties.AUTHENTICATION_SCHEME,
      JCSMPProperties.HOST,
      JCSMPProperties.USERNAME,
      JCSMPProperties.VPN_NAME,
      JCSMPProperties.PASSWORD };

  private static final String[] SOLACEPROPPARAMS = new String[] {
      ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY,
      TOPICNAME,
      CLIENTNAME,
      MESSAGETYPE,
      BridgeCommons.CONNECTIONTYPE,
      JCSMPProperties.AUTHENTICATION_SCHEME,
      JCSMPProperties.HOST,
      JCSMPProperties.USERNAME,
      JCSMPProperties.VPN_NAME,
      JCSMPProperties.PASSWORD };

  private static final String[] ALIASEDPROPERTIES = new String[] { JCSMPProperties.AUTHENTICATION_SCHEME,
      JCSMPProperties.HOST,
      JCSMPProperties.USERNAME,
      JCSMPProperties.VPN_NAME,
      JCSMPProperties.PASSWORD,
      ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY };

  private enum ChannelType {
    QUEUE, TOPIC, NOTSET
  };

  private Properties props = null;
  private boolean isPublisher = true;
  private MessagingService messagingService = null;
  private PersistentMessagePublisher publisher = null;
  private ChannelType channelType = ChannelType.NOTSET;
  private DirectMessageReceiver receiver = null;
  private MessageHandler messageHandler = null;

  private JCSMPSession session = null;
  private com.solacesystems.jcsmp.Queue queue = null;
  private XMLMessageProducer producer = null;
  private FlowReceiver queueReceiver = null;

  private JCSMPProperties solaceProps = new JCSMPProperties();

  static class MessageReceiver implements XMLMessageListener {
    ConnectionBaseInterface target = null;
    String myName = "";

    public MessageReceiver(ConnectionBaseInterface target, String myName) {
      this.target = target;
      this.myName = myName;
    }

    @Override
    public void onReceive(BytesXMLMessage msg) {
      MessageList messages = new MessageList();
      if (msg instanceof TextMessage) {
        String messageBody = ((TextMessage) msg).getText();
        logger.debug(myName + " TextMessage received: '%s'%n", messageBody);
        messages.add(messageBody);
      } else {
        logger.debug(myName + " - Message received.");
      }
      logger.debug(myName + " Message Dump:%n%s%n", msg.dump());

      if (target != null) {
        target.sendMessages(messages);
        logger.debug(myName + " Passed messages to " + target.getConnectionName());
      }

      msg.ackMessage();
    }

    @Override
    public void onException(JCSMPException e) {
      logger.error(myName + " Consumer received exception: %s%n", e);
    }
  }

  /**
   * @return String[]
   */
  static String[] getPropParams() {
    return SOLACEPROPPARAMS;
  }

  public SolaceConnection(Properties properties) {
    super(TYPENAME);
    init(properties, true);
  }

  /*
   * Stores the properties provided
   */
  public SolaceConnection(Properties properties, boolean isPublisher) {
    super(TYPENAME);
    init(properties, isPublisher);
  }

  private void init(Properties properties, boolean isPublisher) {
    props = deAliasProps(properties);
    this.isPublisher = isPublisher;
    logger.debug("creating a solace, as a publisher " + isPublisher);
  }

  private static Properties deAliasProps(Properties props) {
    for (int aliasedPropCtr = 0; aliasedPropCtr < ALIASEDPROPERTIES.length; aliasedPropCtr++) {
      String aliased = ALIASEDPROPERTIES[aliasedPropCtr].replace(BridgeCommons.PROP_PREFIX, INTERNAL_PREFIX);
      if (props.containsKey(aliased)) {
        props.put(ALIASEDPROPERTIES[aliasedPropCtr], props.getProperty(aliased));
        props.remove(aliased);
        logger.debug("replaced " + ALIASEDPROPERTIES[aliasedPropCtr] + "  with  " + aliased);
      }
    }

    return props;
  }

  /*
   * Generate a receiver status string
   */
  private String getTopicReceiverStatusSummary() {
    String status = "";
    status += "Receiver has been created=" + (receiver != null);
    if (receiver != null) {
      status += "\nReceiver is running =" + receiver.isRunning() +
          "\nReceiver is terminating =" + receiver.isTerminating() +
          "\nReceiver is terminated =" + receiver.isTerminated();
    }
    return status;
  }

  private String getQueueReceiverStatusSummary() {
    String status = "";
    status += "Queue Receiver has been created=" + (queueReceiver != null);
    if (queueReceiver != null) {
      status += "\nQ Receiver is closed =" + queueReceiver.isClosed() +
          "\nsession created = " + (session != null) +
          "\nproducer created =" + (producer != null) +
          "\nQ receiver created =" + (queueReceiver != null);
    }
    return status;
  }

  /*
   * checks to see if we're connected to Solace
   * 
   * @Returns: True when connected
   */
  public boolean connected() {
    boolean connected = false;
    logger.debug("Checking connectivity for " + getConnectionName()
        + " on channel " + channelType + " as publisher " + isPublisher);
    switch (channelType) {
      case QUEUE:
        connected = ((session != null) && (!session.isClosed()));
        if (!isPublisher && connected) {
          connected = (queueReceiver != null) && (!queueReceiver.isClosed());
        }
        if (isPublisher && connected) {
          connected = (producer != null) && (!producer.isClosed());
        }

        if (!connected) {
          logger.info("Not connected Status=" + getQueueReceiverStatusSummary());
        }
        break;

      case TOPIC:
        connected = messageHandler != null;

        if (!isPublisher && connected) {
          connected = (receiver != null) && (receiver.isRunning()) && (!receiver.isTerminated())
              && (!receiver.isTerminating());
        }

        if (!connected) {
          logger.info("Not connected Status=" + getTopicReceiverStatusSummary());
        }
        break;

      default:
        connected = false;
        logger.warn("Trying to test Solace connection when config not complete");
    }

    return connected;
  }

  private void setupQueue(boolean publish) {
    try {
      session = JCSMPFactory.onlyInstance().createSession(solaceProps);
      session.connect();
      String qName = props.getProperty(TOPICNAME);
      logger.debug("setting up for queue " + qName + " to publish " + publish);
      queue = JCSMPFactory.onlyInstance().createQueue(qName);
      if (publish) {
        logger.debug(getConnectionName() + " - build queue producer");
        producer = session.getMessageProducer(new SolaceHandler());
      } else {
        logger.debug(getConnectionName() + " build queue consumer");
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

        EndpointProperties endpoint_props = new EndpointProperties();
        endpoint_props.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
        logger.debug(getConnectionName() + " build queue about to set receiver");
        queueReceiver = session.createFlow(new MessageReceiver(target, getConnectionName()), flow_prop, endpoint_props);
        queueReceiver.start();

      }
    } catch (Exception setupErr) {
      logger.error(getConnectionName()
          + " Problem with setting up queue :" + setupErr.getMessage() + "\n"
          + BridgeCommons.exceptionToString(setupErr));
      session = null;
      queue = null;
      producer = null;
    }
  }

  void setupTopicPublisher() {
    logger.info("** TODOS setting up Solace Topic publisher");

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
        Thread.sleep(DEFAULT_SLEEP);
      } catch (InterruptedException interrupt) {
        logger.warn(
            "Disturbed waiting for publisher to be ready - slept for " + (sleepCtr * sleepTime) / 1000 + " seconds");
      }
    }
    logger.debug("Solace publisher is ready");
  }

  void setupTopicReceiver() {
    logger.info("** TODOS setting up Solace Topic receiver");
    if (receiver == null) {
      // TopicSubscription topic = TopicSubscription.of(props.getProperty(TOPICNAME));
      if (messagingService != null) {
        TopicSubscription topic = TopicSubscription.of("*");
        receiver = messagingService.createDirectMessageReceiverBuilder()
            .withSubscriptions(topic).build().start();
      } else {
        logger.error("No messagingService configured yet");
      }
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

    if (props.getProperty(JCSMPProperties.AUTHENTICATION_SCHEME) == null) {
      logger.warn("Defaulting config " + JCSMPProperties.AUTHENTICATION_SCHEME);
      props.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME,
          JCSMPProperties.AUTHENTICATION_SCHEME_BASIC);
    }

    if (props.getProperty(JCSMPProperties.VPN_NAME) == null) {
      props.setProperty(
          JCSMPProperties.VPN_NAME, DEFAULTVPN);
      logger.warn("Setting " + JCSMPProperties.VPN_NAME + "to default");
    }
    /*
     * if (props.getProperty(ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY)
     * == null) {
     * props.setProperty(ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY,
     * "true");
     * }
     */
    for (int jcsmpPropsIdx = 0; jcsmpPropsIdx < JCSMPPROPPARAMS.length; jcsmpPropsIdx++) {
      String value = props.getProperty(JCSMPPROPPARAMS[jcsmpPropsIdx]);
      solaceProps.setProperty(JCSMPPROPPARAMS[jcsmpPropsIdx], value);

      logger.debug("Mapped " + JCSMPPROPPARAMS[jcsmpPropsIdx] + " to " + value);
    }

    String clientname = props.getProperty(CLIENTNAME, DEFAULTVPN);

    switch (props.getProperty(MESSAGETYPE, "NOT-SET").toUpperCase()) {
      case QUEUECONNECTION:
        channelType = ChannelType.QUEUE;
        setupQueue(isPublisher);
        break;

      case TOPICCONNECTION:
        channelType = ChannelType.TOPIC;
        if (isPublisher) {
          setupTopicPublisher();
        } else {
          setupTopicReceiver();
        }
        break;

      default:
        channelType = ChannelType.NOTSET;
        logger.error("Unknown connection type for Solace connection");
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
    /*
     * TopicSubscription topic = TopicSubscription.of(props.getProperty(TOPICNAME));
     * 
     * if (!messagingService.isConnected()) {
     * logger.warn("reconnecting");
     * messagingService.connect();
     * }
     * receiver = messagingService.createDirectMessageReceiverBuilder()
     * .withSubscriptions(topic).build().start();
     * logger.debug("receive created");
     * 
     * InboundMessage message = receiver.receiveMessage(1);
     * if (message != null) {
     * String recdMessage = message.dump();
     * logger.info("message is:" + recdMessage);
     * messages.add(recdMessage);
     * } else {
     * logger.warn("null message object");
     * }
     */
    return messages;
  }

  public void sendMessages(MessageList messages) {
    if ((messages == null) || (messages.isEmpty())) {
      logger.warn("No messages to send");
      return;
    } else {
      logger.info("Sending to Solace " + messages.size() + " messages on " + props.getProperty(TOPICNAME));
    }

    switch (channelType) {
      case QUEUE:
        sendQueueMessages(messages);
        break;

      case TOPIC:
        sendTopicMessages(messages);
        break;

      default:
        logger.error("Unknown message type for Solace - " + channelType);
    }
  }

  private void sendTopicMessages(MessageList messages) {
    logger.warn("**** Sending to topic not implemented yet");
    if (messagingService == null) {
      logger.error("messagingService not available");
      return;
    }
    if (!messagingService.isConnected()) {
      logger.warn("Solace sendMessages needs to reconnect messagingService");
      messagingService.connect();
    }
    ListIterator messageIter = messages.listIterator();
    while (messageIter.hasNext()) {
      String msg = (String) messageIter.next() + " to " + getConnectionName();
      logger.info("**TODO implement send of message: " + msg);
    }
    // Topic topic = Topic.of(props.getProperty(TOPICNAME));
  }

  /*
   */
  private void sendQueueMessages(MessageList messages) {
    if (!connected()) {
      logger.warn(getConnectionName() + " sending messages - but need to connect");
      connect();
    }

    for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
      String msg = messages.get(msgIdx);
      logger.info("sending:" + msg);

      try {

        /*
         * if ((session == null) || (session.isClosed())) {
         * logger.warn("Send Solace Queue message - but not yet connected");
         * setupQueue(isPublisher);
         * }
         */

        TextMessage solaceMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        solaceMsg.setDeliveryMode(DeliveryMode.PERSISTENT);
        solaceMsg.setText(msg);
        if (producer != null) {
          producer.send(solaceMsg, queue);
        } else {
          logger.warn(getConnectionName() + " cant send message - producer not created");
        }

      } catch (Exception publishErr) {
        logger.error("Problem with publishing message:" + publishErr.getMessage() + "\n"
            + BridgeCommons.exceptionToString(publishErr));
      }
    }
    logger.debug("Send done");
  }

  public void printProperties() {
    logger.info(BridgeCommons.prettyPropertiesToString(props, getConnectionName(), "\n"));
  }

  public void setTarget(ConnectionBaseInterface target) {
    this.target = target;
    logger.debug(TYPENAME + " setTarget " + target.getConnectionName());
  }

  public void shutdown() {
    logger.info(TYPENAME + " shutdown for " + getConnectionName());
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

    if (session != null) {
      session.closeSession();
      session = null;
    }

    if (producer != null) {
      producer.close();
      producer = null;
    }

    if (queueReceiver != null) {
      queueReceiver.close();
      queueReceiver = null;
    }
  }

}
