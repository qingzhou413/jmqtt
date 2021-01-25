package org.jmqtt.broker.processor.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import org.jmqtt.broker.BrokerController;
import org.jmqtt.broker.acl.PubSubPermission;
import org.jmqtt.broker.common.log.LoggerName;
import org.jmqtt.broker.common.model.Message;
import org.jmqtt.broker.common.model.MessageHeader;
import org.jmqtt.broker.processor.RequestProcessor;
import org.jmqtt.broker.remoting.session.ClientSession;
import org.jmqtt.broker.remoting.session.ConnectManager;
import org.jmqtt.broker.remoting.util.MessageUtil;
import org.jmqtt.broker.remoting.util.NettyUtil;
import org.jmqtt.broker.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端publish消息到jmqtt broker
 * TODO mqtt5实现,流控处理
 */
public class PublishProcessor extends AbstractMessageProcessor implements RequestProcessor {
    private Logger log = LoggerFactory.getLogger(LoggerName.MESSAGE_TRACE);

    private PubSubPermission pubSubPermission;

    private SessionStore sessionStore;

    public PublishProcessor(BrokerController controller){
        super(controller.getMessageStore());
        this.sessionStore = controller.getSessionStore();
        this.pubSubPermission = controller.getPubSubPermission();
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        try{
            MqttPublishMessage publishMessage = (MqttPublishMessage) mqttMessage;
            MqttQoS qos = publishMessage.fixedHeader().qosLevel();
            Message innerMsg = new Message();
            String clientId = NettyUtil.getClientId(ctx.channel());
            ClientSession clientSession = ConnectManager.getInstance().getClient(clientId);
            String topic = publishMessage.variableHeader().topicName();
            if(!this.pubSubPermission.publishVerify(clientId,topic)){
                log.warn("[PubMessage] permission is not allowed");
                clientSession.getCtx().close();
                return;
            }
            innerMsg.setPayload(MessageUtil.readBytesFromByteBuf(((MqttPublishMessage) mqttMessage).payload()));
            innerMsg.setClientId(clientId);
            innerMsg.setType(Message.Type.valueOf(mqttMessage.fixedHeader().messageType().value()));
            Map<String,Object> headers = new HashMap<>();
            headers.put(MessageHeader.TOPIC,publishMessage.variableHeader().topicName());
            headers.put(MessageHeader.QOS,publishMessage.fixedHeader().qosLevel().value());
            headers.put(MessageHeader.RETAIN,publishMessage.fixedHeader().isRetain());
            headers.put(MessageHeader.DUP,publishMessage.fixedHeader().isDup());
            innerMsg.setHeaders(headers);
            innerMsg.setMsgId(publishMessage.variableHeader().packetId());
            switch (qos){
                case AT_MOST_ONCE:
                    processMessage(innerMsg);
                    break;
                case AT_LEAST_ONCE:
                    processQos1(ctx,innerMsg);
                    break;
                case EXACTLY_ONCE:
                    processQos2(ctx,innerMsg);
                    break;
                default:
                    log.warn("[PubMessage] -> Wrong mqtt message,clientId={}", clientId);
            }
        } catch (Throwable tr) {
            log.warn("[PubMessage] -> Solve mqtt pub message exception:{}", tr.getMessage());
        } finally {
            ReferenceCountUtil.release(mqttMessage.payload());
        }
    }

    private void processQos2(ChannelHandlerContext ctx, Message innerMsg) {
        int originMessageId = innerMsg.getMsgId();
        log.debug("[PubMessage] -> Process qos2 message,clientId={}", innerMsg.getClientId());
        boolean flag = sessionStore.cacheInflowMsg(innerMsg.getClientId(), innerMsg);
        if (!flag) {
            log.warn("[PubMessage] -> cache qos2 pub message failure,clientId={}", innerMsg.getClientId());
        }
        MqttMessage pubRecMessage = MessageUtil.getPubRecMessage(originMessageId);
        ctx.writeAndFlush(pubRecMessage);
    }

    private void processQos1(ChannelHandlerContext ctx, Message innerMsg) {
        int originMessageId = innerMsg.getMsgId();
        processMessage(innerMsg);
        log.info("[PubMessage] -> Process qos1 message,clientId={}", innerMsg.getClientId());
        MqttPubAckMessage pubAckMessage = MessageUtil.getPubAckMessage(originMessageId);
        ctx.writeAndFlush(pubAckMessage);
    }

}