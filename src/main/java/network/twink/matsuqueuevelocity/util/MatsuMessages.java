package network.twink.matsuqueuevelocity.util;

public class MatsuMessages {

    private String nowQueued;
    private String nowOffline;
    private String positionInQueue;
    private String connecting;
    private String pendingConnection;


    public void setNowQueued(String nowQueued) {
        this.nowQueued = nowQueued;
    }

    public String getNowQueued() {
        return nowQueued;
    }

    public void setNowOffline(String nowOffline) {
        this.nowOffline = nowOffline;
    }

    public String getNowOffline() {
        return nowOffline;
    }

    public void setPositionInQueue(String positionInQueue) {
        this.positionInQueue = positionInQueue;
    }

    public String getPositionInQueue() {
        return positionInQueue;
    }

    public void setConnecting(String connecting) {
        this.connecting = connecting;
    }

    public String getConnecting() {
        return connecting;
    }

    public void setPendingConnection(String pendingConnection) {
        this.pendingConnection = pendingConnection;
    }

    public String getPendingConnection() {
        return pendingConnection;
    }

    public String format(String template, String serverName, String status, int pos) {
        return template.replace("{server}", serverName).replace("{status}", status).replace("{pos}", pos+"");
    }
}
