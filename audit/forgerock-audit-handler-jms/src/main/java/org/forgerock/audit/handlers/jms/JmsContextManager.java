/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.audit.handlers.jms;

import static javax.naming.Context.*;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.util.Reject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the Connection to the JMS services and does the JNDI lookup for the JMS configuration settings.
 */
class JmsContextManager {
    private static final Logger logger = LoggerFactory.getLogger(JmsContextManager.class);

    /**
     * Prefix of the JNDI key for the JMS Topic to publish messages on.
     */
    private static final String TOPIC_JNDI_CONFIG_PREFIX = "topic.";

    private final ConnectionFactory connectionFactory;

    /**
     * Delivery mode for the MessageProducer to use.
     *
     * @see MessageProducer#setDeliveryMode(int)
     */
    private final DeliveryModeConfig deliveryMode;

    /**
     * Session acknowledgement mode for the Session to use.
     * @see Connection#createSession(boolean, int)
     */
    private SessionModeConfig sessionMode;

    /**
     * The Current JMS broker connection, if open.
     */
    private Connection connection;

    /**
     * The JMS Topic used to publish the audit TextMessages.
     */
    private Topic topic;

    /**
     * Given the configuration, this builds an JMS InitialContext. If usage of JNDI isn't desired, then both the
     * connectionFactory and the jmsTopic need to be populated.
     *
     * @param configuration The Audit Configuration.
     * @param connectionFactory Connection factory to use instead of looking it up in JNDI.
     * @param jmsTopic Topic to use instead of looking it up in JNDI.
     * @throws InternalServerErrorException
     */
    public JmsContextManager(JmsAuditEventHandlerConfiguration configuration, ConnectionFactory connectionFactory,
            Topic jmsTopic) throws ResourceException {

        Reject.ifNull(configuration.getDeliveryMode(), "JMS Delivery Mode is required");
        Reject.ifNull(configuration.getSessionMode(), "JMS Session Mode is required");
        sessionMode = configuration.getSessionMode();
        deliveryMode = configuration.getDeliveryMode();

        if (null == connectionFactory || null == jmsTopic) {
            try {
                Reject.ifNull(configuration.getJmsTopic(), "JMS Topic is required");
                Properties props = new Properties();
                props.setProperty(INITIAL_CONTEXT_FACTORY, configuration.getInitialContextFactory());
                props.setProperty(PROVIDER_URL, configuration.getProviderUrl());
                props.setProperty(TOPIC_JNDI_CONFIG_PREFIX + configuration.getJmsTopic(), configuration.getJmsTopic());
                InitialContext context = new InitialContext(props);
                this.topic = (Topic) context.lookup(configuration.getJmsTopic());
                this.connectionFactory = (ConnectionFactory) context.lookup("ConnectionFactory");
            } catch (NamingException e) {
                throw new InternalServerErrorException(
                        "Encountered issue building initial JMS context for JMS Audit Handler", e);
            }
        } else {
            this.connectionFactory = connectionFactory;
            this.topic = jmsTopic;
        }
        Reject.ifNull(this.connectionFactory, "Null ConnectionFactory is not permitted.");
        Reject.ifNull(this.topic, "Null topic is not permitted.");

    }

    /**
     * Opens the connection to the JMS services with the configured session mode.
     * @throws JMSException
     */
    public void openConnection() throws JMSException {
        connection = connectionFactory.createConnection();
        connection.start();
        logger.debug("JMS Connection created and started");
    }

    /**
     * Closes the connection to the JMS services.
     *
     * @throws JMSException
     */
    public void closeConnection() throws JMSException {
        try {
            connection.close();
            logger.debug("JMS Connection closed");
        } finally {
            // Set to null too allow for early garbage collection, rather than waiting for next openConnection.
            connection = null;
        }
    }

    /**
     * Creates and returns a jms session created from the connection with the sessionMode configured and without
     * transaction management.
     *
     * @return a new session.
     * @throws JMSException if trouble is encountered creating the session.
     * @throws IllegalStateException if the connection hasn't been opened.
     * @see Connection#createSession(boolean, int)
     */
    public Session createSession() throws JMSException {
        if (null == connection) {
            throw new IllegalStateException(
                    "JMS Connection not available to create session. Check coding logic that a connection is opened.");
        }
        return connection.createSession(false, sessionMode.getMode());
    }

    /**
     * Creates a producer from the passed in session using the configured JMS topic and deliveryMode.
     *
     * @param session the session to get the producer from.
     * @return a new producer from the session with the destination set to the configured JMS topic.
     * @throws JMSException if there is trouble creating the producer.
     * @see Session#createProducer(Destination)
     * @see MessageProducer#setDeliveryMode(int)
     */
    public MessageProducer createProducer(Session session) throws JMSException {
        MessageProducer producer = session.createProducer(topic);
        producer.setDeliveryMode(deliveryMode.getMode());
        return producer;
    }
}
