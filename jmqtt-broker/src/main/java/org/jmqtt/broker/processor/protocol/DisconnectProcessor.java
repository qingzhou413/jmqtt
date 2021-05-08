package org.jmqtt.broker.processor.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.jmqtt.broker.BrokerController;
import org.jmqtt.broker.common.log.JmqttLogger;
import org.jmqtt.broker.common.log.LogUtil;
import org.jmqtt.broker.common.model.Subscription;
import org.jmqtt.broker.processor.RequestProcessor;
import org.jmqtt.broker.remoting.session.ClientSession;
import org.jmqtt.broker.remoting.session.ConnectManager;
import org.jmqtt.broker.remoting.util.NettyUtil;
import org.jmqtt.broker.store.MessageStore;
import org.jmqtt.broker.store.SessionState;
import org.jmqtt.broker.store.SessionStore;
import org.jmqtt.broker.subscribe.SubscriptionMatcher;
import org.slf4j.Logger;

import java.util.Set;

/**
 * 客户端主动发起断开连接：正常断连
 * TODO mqtt5实现
 */
public class DisconnectProcessor implements RequestProcessor {

    private static final Logger              log = JmqttLogger.clientTraceLog;
    private              SessionStore        sessionStore;
    private              MessageStore        messageStore;
    private              SubscriptionMatcher subscriptionMatcher;

    public DisconnectProcessor(BrokerController brokerController) {
        this.sessionStore = brokerController.getSessionStore();
        this.messageStore = brokerController.getMessageStore();
        this.subscriptionMatcher = brokerController.getSubscriptionMatcher();
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        String clientId = NettyUtil.getClientId(ctx.channel());
        if (!ConnectManager.getInstance().containClient(clientId)) {
            LogUtil.warn(log,"[DISCONNECT] -> {} hasn't connect before", clientId);
        }
        ClientSession clientSession = ConnectManager.getInstance().getClient(clientId);

        // 1. 清理会话 或 重新设置该客户端会话状态
        clearSession(clientSession);

        // 3. 清理will消息
        clearWillMessage(clientSession.getClientId());

        // 4. 移除本节点上的连接
        ConnectManager.getInstance().removeClient(clientId);

        ctx.close();
    }

    private void clearSession(ClientSession clientSession) {
        if (clientSession.isCleanStart()) {
            Set<Subscription> subscriptions = sessionStore.getSubscriptions(clientSession.getClientId());
            for (Subscription subscription : subscriptions) {
                this.subscriptionMatcher.unSubscribe(subscription.getTopic(), clientSession.getClientId());
            }
            sessionStore.clearSession(clientSession.getClientId(),false);
        } else {
            SessionState sessionState = new SessionState(SessionState.StateEnum.OFFLINE, System.currentTimeMillis());
            this.sessionStore.storeSession(clientSession.getClientId(), sessionState);
        }
    }

    private void clearWillMessage(String clientId) {
        messageStore.clearWillMessage(clientId);
    }

}
