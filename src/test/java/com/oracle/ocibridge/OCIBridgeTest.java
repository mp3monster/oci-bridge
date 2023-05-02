package com.oracle.ocibridge;

import static org.junit.Assert.assertTrue;
import java.util.Properties;
import java.util.Map;

import org.junit.Test;

import com.oracle.bmc.util.VisibleForTesting;

/**
 * Unit test for simple App.
 */
public class OCIBridgeTest extends OCIBridge {

  /**
   * Define the test values as constants so we can test values
   */

  private static final String SYNTHETIC1_TEST = "Synthetic1__test";

  private static final String OCI_QUEUE_OCI_USERID = "OCIQueue__OCI_USERID";
  private static final String OCI_QUEUE_TYPE = "OCIQueue__Type";
  private static final String OCI_QUEUE_OCI_REGION = "OCIQueue__OCI_REGION";
  private static final String OCI_QUEUE_OCI_QUEUEID = "OCIQueue__OCI_QUEUEID";
  private static final String OCI_QUEUE_OCI_TENANT_ID = "OCIQueue__OCI_TENANT_ID";
  private static final String OCI_QUEUE_OCI_FINGERPRINT = "OCIQueue__OCI_FINGERPRINT";

  private static final String REGION = "us-ashburn-1";
  private static final String FINGERPRINT = "94:f7:6c:48:7a:50:87:73:bd:3d:32:a1:44:4e:2c:81";
  private static final String TEST_OCID_USER = "ocid1.user.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String TEST_OCID_TENANCY = "ocid1.tenancy.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String TEST_OCID_QUEUE = "ocid1.queue.oc1..aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  private static final String SYNTHETIC1_TYPE = "Synthetic1__Type";
  private static final String SYNTHETIC1_TYPE_VALUE = "widget";

  private static final String OCI_QUEUE_POLLDURATIONSECS = "OCIQueue__POLLDURATIONSECS";
  private static final String OCI_QUEUE_POLLDURATIONSECS_VALUE = "5";

  private static final String CLOUD_SOLACE_VPN_NAME = "cloudSolace__vpn_name";
  private static final String CLOUD_SOLACE_VPN_NAME_VALUE = "testVPNName";

  private static final String CLOUD_SOLACE_SOLACE_TOPICNAME = "cloudSolace__SOLACE_TOPICNAME";
  private static final String CLOUD_SOLACE_SOLACE_TOPICNAME_VALUE = "testTopic";

  private static final String CLOUD_SOLACE_PASSWORD = "cloudSolace__password";
  private static final String CLOUD_SOLACE_PASSWORD_VALUE = "testPassword";

  private static final String OCI_QUEUE_QUEUENAME = "OCIQueue__QUEUENAME";
  private static final String OCI_QUEUE_QUEUENAME_VALUE = "pw-demo-queue";

  private static final String CLOUD_SOLACE_USERNAME = "cloudSolace__username";
  private static final String CLOUD_SOLACE_USERNAME_VALUE = "testUsername";

  private static final String CLOUD_SOLACE_TYPE = "cloudSolace__Type";
  private static final String CLOUD_SOLACE_TYPE_VALUE = SolaceConnection.TYPENAME;

  private static final String CLOUD_SOLACE_SOLACE_PORT = "cloudSolace__SOLACE_PORT";
  private static final String CLOUD_SOLACE_SOLACE_PORT_VALUE = "55443";

  private static final String CLOUD_SOLACE_HOST = "cloudSolace__host";
  private static final String CLOUD_SOLACE_HOST_VALUE = "xxx";

  private static final String CLOUD_SOLACE_SOLACE_MESSAGING_AUTHENTICATION_SCHEME = "cloudSolace__solace.messaging.authentication.scheme";
  private static final String CLOUD_SOLACE_AUTHENTICATION_SCHEME_BASIC_VALUE = "cloudSolace__AUTHENTICATION_SCHEME_BASIC";
  private static final String CLOUD_SOLACE_MESSAGE_TYPE_VALUE = "queue";
  private static final String CLOUD_SOLACE_MESSAGE_TYPE = "cloudSolace__MESSAGE_TYPE";

  /**
   * Rigorous Test :-)
   */
  @Test
  public void shouldAnswerWithTrue() {
    assertTrue(true);
  }

  public static Properties initialiseProperties() {
    Properties props = new Properties();
    props.setProperty(SYNTHETIC1_TYPE, SyntheticConnection.TYPENAME);
    props.setProperty(OCI_QUEUE_TYPE, OCIQueueConnection.TYPENAME);
    props.setProperty(OCI_QUEUE_OCI_USERID, TEST_OCID_USER);
    props.setProperty(OCI_QUEUE_OCI_TENANT_ID, TEST_OCID_TENANCY);
    props.setProperty(OCI_QUEUE_OCI_FINGERPRINT, FINGERPRINT);
    props.setProperty(OCI_QUEUE_OCI_REGION, REGION);
    props.setProperty(OCI_QUEUE_OCI_QUEUEID, TEST_OCID_QUEUE);
    props.setProperty(OCI_QUEUE_POLLDURATIONSECS, OCI_QUEUE_POLLDURATIONSECS_VALUE);
    props.setProperty(OCI_QUEUE_QUEUENAME, OCI_QUEUE_QUEUENAME_VALUE);
    props.setProperty(CLOUD_SOLACE_TYPE, CLOUD_SOLACE_TYPE_VALUE);
    props.setProperty(CLOUD_SOLACE_SOLACE_PORT, CLOUD_SOLACE_SOLACE_PORT_VALUE);
    props.setProperty(CLOUD_SOLACE_HOST, CLOUD_SOLACE_HOST_VALUE);
    props.setProperty(CLOUD_SOLACE_MESSAGE_TYPE, CLOUD_SOLACE_MESSAGE_TYPE_VALUE);
    props.setProperty(CLOUD_SOLACE_SOLACE_MESSAGING_AUTHENTICATION_SCHEME,
        CLOUD_SOLACE_AUTHENTICATION_SCHEME_BASIC_VALUE);
    props.setProperty(CLOUD_SOLACE_USERNAME, CLOUD_SOLACE_USERNAME_VALUE);
    props.setProperty(CLOUD_SOLACE_PASSWORD, CLOUD_SOLACE_PASSWORD_VALUE);
    props.setProperty(CLOUD_SOLACE_SOLACE_TOPICNAME, CLOUD_SOLACE_SOLACE_TOPICNAME_VALUE);
    props.setProperty(CLOUD_SOLACE_VPN_NAME, CLOUD_SOLACE_VPN_NAME_VALUE);

    return props;
  }

  @Test
  public void testSolaceProps() {
    ConnectionBase connector = new SolaceConnection(initialiseProperties());
    Properties props = connector.props;
    System.out.println("Test result:\n" + BridgeCommons.prettyPropertiesToString((Map) props, "", ""));

  }
}