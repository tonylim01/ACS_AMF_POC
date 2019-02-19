/* Copyright 2018 (C) UANGEL CORPORATION <http://www.uangel.com> */

/**
 * Acs AMF
 * @file RmqClient.java
 * @author Tony Lim
 *
 */

package media.platform.amf.rmqif.module;

import com.uangel.svc.oam.Level;
import media.platform.amf.common.StringUtil;
import media.platform.amf.config.AmfConfig;
import media.platform.amf.AppInstance;
import media.platform.amf.config.UserConfig;
import media.platform.amf.core.rabbitmq.transport.RmqSender;
import media.platform.amf.oam.AlarmHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class RmqClient {

    private static final Logger logger = LoggerFactory.getLogger(RmqClient.class);

    private static Map<String, RmqClient> clients = null;
    private boolean isAlarmSent = false;

    public static RmqClient getInstance(String queueName) {
        if (clients == null) {
            clients = new HashMap<>();
        }

        RmqClient client = clients.get(queueName);
        if (client == null) {
            UserConfig config = AppInstance.getInstance().getUserConfig();
            if (config == null) {
                return null;
            }

            String host, user, pass;

            if (StringUtil.compareString(queueName, config.getAwfQueue())) {
                host = config.getAwfRmqHost();
                user = config.getAwfRmqUser();
                pass = config.getAwfRmqPass();
            }
            else {
                host = config.getRmqHost();
                user = config.getRmqUser();
                pass = config.getRmqPass();
            }

            client = new RmqClient(queueName, host, user, pass);
            clients.put(queueName, client);
        }

        return client;
    }

    public static boolean hasInstance(String queueName) {
        if (clients == null) {
            return false;
        }

        return clients.containsKey(queueName);
    }

    public static void closeAllClients() {
        if (clients == null) {
            return;
        }

        for (Map.Entry<String, RmqClient> entry: clients.entrySet()) {
            RmqClient client = entry.getValue();
            if (client != null) {
                client.closeSender();
            }
        }
    }

    private RmqSender sender = null;
    private boolean isConnected = false;
    private String queueName = null;

    private String rmqHost, rmqUser, rmqPass;

    public RmqClient(String queueName, String host, String user, String pass) {

        this.queueName = queueName;
        this.rmqHost = host;
        this.rmqUser = user;
        this.rmqPass = pass;

        this.isConnected = createSender(queueName);
    }

    private boolean createSender(String queueName) {
        sender = new RmqSender(rmqHost, rmqUser, rmqPass, queueName);
        return sender.connectClient();
    }

    public void closeSender() {
        if (sender != null) {
            sender.close();
            sender = null;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean send(String msg) {
        return send(msg.getBytes(Charset.defaultCharset()), msg.length());
    }

    public boolean send(byte[] msg, int size) {
        if (sender == null) {
            if (createSender(queueName) == false) {
                sendAlarm();
                logger.error("Failed to create RMQ client [{}]", queueName);
                return false;
            }
            if (sender == null) {
                logger.error("Null RMQ sender [{}]", queueName);
                return false;
            }
        }

        if (!sender.isOpened()) {
            logger.error("RMQ sender is not opened [{}]", queueName);
            if (!sender.connectClient()) {
                logger.error("Failed to connect RMQ client [{}]", queueName);
                sendAlarm();
                return false;
            }
        }

        boolean result = sender.send(msg, size);
        if (!result) {
            sendAlarm();
        }
        else if (isAlarmSent) {
            isAlarmSent = false;
        }

        return result;
    }

    private void sendAlarm() {
        if (!isAlarmSent) {
            isAlarmSent = true;
            new AlarmHandler().onApplicationEvent("AMF_RABBITMQ_CONNECT_FAIL", Level.NOR,
                    "Rabbitmq not connected");

        }
    }
}
