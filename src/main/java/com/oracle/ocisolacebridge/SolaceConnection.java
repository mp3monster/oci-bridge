package com.oracle.ocisolacebridge;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solace.messaging.MessagingService;
import com.solace.messaging.config.SolaceProperties.AuthenticationProperties;
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
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

/*
   * This is the Solace implementation of the Connection base - allows us to
   * connect
   * and send to Solace or pull from Solace messages
   */
class SolaceConnection extends ConnectionBase {

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
      logger.warn("Producer received response: " + arg0.toString());

    }
  }

  private static Logger logger = LoggerFactory.getLogger(SolaceConnection.class);

  public static final String TYPENAME = "Solace";
  private static final String TOPICNAME = "SOLACE_TOPICNAME";
  private static final String CLIENTNAME = "CLIENT_NAME";
  private static final String QUEUECONNECTION = "QUEUE";
  private static final String TOPICCONNECTION = "TOPIC";
  private static final String MESSAGETYPE = "MESSAGETYPE";

  private static final String[] JCSMPPROPPARAMS = new String[] { JCSMPProperties.AUTHENTICATION_SCHEME,
      JCSMPProperties.AUTHENTICATION_SCHEME,
      JCSMPProperties.HOST,
      JCSMPProperties.USERNAME,
      JCSMPProperties.VPN_NAME,
      JCSMPProperties.PASSWORD };

  private static final String[] SOLACEPROPPARAMS = new String[] {
      AuthenticationProperties.SCHEME,
      AuthenticationProperties.SCHEME_BASIC_USER_NAME,
      AuthenticationProperties.SCHEME_BASIC_PASSWORD,
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

  private Properties props = null;
  private boolean isPublisher = true;
  private MessagingService messagingService = null;
  // private MessagePublisher publisher = null;
  private PersistentMessagePublisher publisher = null;
  private DirectMessageReceiver receiver = null;
  private MessageHandler messageHandler = null;
  private JCSMPSession session = null;

  private JCSMPProperties solaceProps = new JCSMPProperties();

  static String[] getPropParams() {
    return SOLACEPROPPARAMS;
  }

  /*
   * Stores the properties provided
   */
  public SolaceConnection(Properties properties, boolean isPublisher) {
    super(TYPENAME);
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
          JCSMPProperties.VPN_NAME, "default");
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

    String clientname = props.getProperty(CLIENTNAME, "default");

    // prettyPropertiesToString(props, getConnectionName());

    // messagingService =
    // MessagingService.builder(ConfigurationProfile.V1).fromProperties(props)
    // .build(clientname).connect();
    // logger.debug("Building message service for Solace completed - app id is " +
    // messagingService.getApplicationId()
    // + "; is connected " + messagingService.isConnected());

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

  public void sendMessages(MessageList messages) {
    if ((messages == null) || (messages.size() == 0)) {
      logger.warn("No messages to send");
      return;
    } else {
      logger.info("Sending to Solace " + messages.size() + " messages on " + props.getProperty(TOPICNAME));
    }

    String msgType = props.getProperty(MESSAGETYPE, TOPICCONNECTION).toUpperCase();
    switch (msgType) {
      case QUEUECONNECTION:
        sendQueueMessages(messages);
        break;

      case TOPICCONNECTION:
        sendTopicMessages(messages);
        break;

      default:
        logger.error("Unknown message type for Solace - " + msgType);
    }
  }

  private void sendTopicMessages(MessageList messages) {
    logger.warn("Sending to topic not implemented yet");
    // Topic topic = Topic.of(props.getProperty(TOPICNAME));
  }

  /*
   */
  private void sendQueueMessages(MessageList messages) {
    for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
      String msg = messages.get(msgIdx);
      logger.info("sending:" + msg);

      if (!messagingService.isConnected()) {
        logger.warn("Solace sendMessages needs to reconnect messagingService");
        messagingService.connect();
      }
      try {
        // https://github.com/SolaceDev/solace-samples-java-jcsmp/blob/master/src/main/java/com/solace/samples/jcsmp/features/QueueProducer.java

        if ((session == null) || (session.isClosed())) {
          session = JCSMPFactory.onlyInstance().createSession(solaceProps);
        }

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
    BridgeCommons.prettyPropertiesToString(props, getConnectionName());
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
