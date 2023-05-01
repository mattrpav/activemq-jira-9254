package io.hyte.activemq.jira;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KahaDBOpsTaskTest {

    // config
    protected static final int MIN_MESSAGE_LEN = 2_000;
    protected static final int MAX_MESSAGE_LEN = 500_000;

    // state
    protected static BrokerService brokerService;
    protected static KahaDBPersistenceAdapter kahaDBPersistenceAdapter;
    protected static URI clientUri;

    @BeforeClass
    public static void beforeClass() throws Exception {
        brokerService = BrokerFactory.createBroker(new URI("xbean:src/test/resources/conf/activemq.xml"));
        //brokerService.setDeleteAllMessagesOnStartup(true);
        brokerService.start();
        brokerService.waitUntilStarted();
        kahaDBPersistenceAdapter = (KahaDBPersistenceAdapter)brokerService.getPersistenceAdapter();
        clientUri = brokerService.getTransportConnectorByName("openwire").getPublishableConnectURI();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if(brokerService != null) {
            brokerService.stop();
            brokerService.waitUntilStopped();
        }
    }

    @Test
    public void testKahaDBOpsTest() throws Exception {
        assertNotNull(brokerService);
        String testQueueName = "amq.jira.9254";
        int messageCount = 20_000;
        System.out.println("Client URI: " + clientUri.toString());

        // 1. Load messages beyond highest of all page sizes
        long sendTimeMillis = publishTextMessages(testQueueName, messageCount);
        System.out.println("Loaded " + messageCount + " messages in: " + sendTimeMillis + " (ms)");

        // 2. Assert all db-*.log files are 2mb
        verfiyFiles();
    }

    protected static void verfiyFiles() {
        File dir = kahaDBPersistenceAdapter.getDirectory();
        File[] files = dir.listFiles(new FilenameFilter() {
            
            @Override
            public boolean accept(File dir, String name) {
                if(name.startsWith("db-") && name.endsWith("log")) {
                    return true;
                }
                return false;
            }
        });
        for(File dbFile : files) {
            assertTrue("db-*.log: " + dbFile.getName() + " size: " + dbFile.length() + " larger than max allowed: " + kahaDBPersistenceAdapter.getJournalMaxFileLength(), kahaDBPersistenceAdapter.getJournalMaxFileLength() >= dbFile.length());
        }
        System.out.println("Verified: " + files.length + " db-*.log files are less than or equal to length: " + kahaDBPersistenceAdapter.getJournalMaxFileLength());
    }

    protected static long publishTextMessages(String queueName, int count) throws JMSException {
        ActiveMQConnectionFactory activemqConnectionFactory = new ActiveMQConnectionFactory(clientUri);
        activemqConnectionFactory.setCopyMessageOnSend(false);
        activemqConnectionFactory.setProducerWindowSize(1_000_000);
        activemqConnectionFactory.setUseAsyncSend(true);

        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;
        try {
            ConnectionFactory connectionFactory = activemqConnectionFactory;
            connection = connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            messageProducer = session.createProducer(session.createQueue(queueName));

            long startTimeMillis = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                messageProducer.send(generateTextMessage(session, i), DeliveryMode.PERSISTENT, 4, 0l);
                if(i % 1_000 == 0) {
                    System.out.println("Published message: " + i);
                }
            }
            long endTimeMillis = System.currentTimeMillis();
            return (endTimeMillis - startTimeMillis);
        } finally {
            if(messageProducer != null) { try { messageProducer.close(); } finally { } }
            if(session != null) { try { session.close(); } finally { } }
            if(connection != null) { try { connection.close(); } finally { } }
        }
    }

    protected static Message generateTextMessage(Session session, int seqId) throws JMSException {
        Message textMessage = session.createTextMessage(generateRandomString(randomInt(MIN_MESSAGE_LEN, MAX_MESSAGE_LEN)));
        textMessage.setIntProperty("JMSXGroupSeq", seqId);
        return textMessage;
    }

    protected static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    protected static String generateRandomString(int length) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = length;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }
}
