package io.hyte.activemq.jira;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.Queue;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KahaDBFileSizeIncorrectReadTest implements MessageListener {

    // state
    protected static BrokerService brokerService;
    protected static KahaDBPersistenceAdapter kahaDBPersistenceAdapter;
    protected static URI clientUri;
    protected CountDownLatch countDownLatch = null;

    @BeforeClass
    public static void beforeClass() throws Exception {
        System.setProperty("activemq.data", "target/activemq-data");
        brokerService = BrokerFactory.createBroker(new URI("xbean:src/test/resources/conf/activemq-jira-9254-read.xml"));
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
    public void testKahaDBFileSizeIncorrectRead() throws Exception {
        assertNotNull(brokerService);
        String testQueueName = "amq.jira.9254";
        System.out.println("Client URI: " + clientUri.toString());

        Queue tmpQueue = null;
        Set<Destination> tmpDests = brokerService.getBroker().getDestinations(new ActiveMQQueue(testQueueName));
        for(Destination tmpDest : tmpDests) {
            tmpQueue = (Queue)tmpDest;
        }
        int queueSize = (int)tmpQueue.getDestinationStatistics().getMessages().getCount();

        countDownLatch = new CountDownLatch(queueSize);

        ActiveMQConnectionFactory activemqConnectionFactory = new ActiveMQConnectionFactory(clientUri);
        activemqConnectionFactory.setCopyMessageOnSend(false);
        activemqConnectionFactory.setProducerWindowSize(1_000_000);
        activemqConnectionFactory.setUseAsyncSend(true);

        Connection connection = null;
        Session session = null;
        MessageConsumer messageConsumer = null;
        try {
            ConnectionFactory connectionFactory = activemqConnectionFactory;
            connection = connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
//            session.setMessageListener(this);
            messageConsumer = session.createConsumer(session.createQueue(testQueueName));
            Message recvMessage = null;
            do {
                recvMessage = messageConsumer.receive(2000l);
                countDownLatch.countDown();
            } while (recvMessage != null);
        } catch (JMSException e) {
            fail(e.getMessage());
        } finally {
            if(messageConsumer != null) { try { messageConsumer.close(); } finally { } }
            if(session != null) { try { session.close(); } finally { } }
            if(connection != null) { try { connection.close(); } finally { } }
        }

        System.out.println("Recv: " + countDownLatch.getCount());
    }

    @Override
    public void onMessage(Message message) {
        countDownLatch.countDown();
    }

    
}
