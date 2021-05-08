package org.jmqtt.broker.store.rdb.daoobject;

import java.io.Serializable;


public class SubscriptionDO implements Serializable {

    private static final long serialVersionUID = 12213131231231L;

    private Long id;

    private String clientId;

    private String topic;

    private Integer qos;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getQos() {
        return qos;
    }

    public void setQos(Integer qos) {
        this.qos = qos;
    }
}
