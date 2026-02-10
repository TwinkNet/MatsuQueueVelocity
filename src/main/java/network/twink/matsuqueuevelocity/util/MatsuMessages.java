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

    public String getNowQueued(String serverName) {
        return format(this.nowQueued, serverName, -1);
    }

    public void setNowOffline(String nowOffline) {
        this.nowOffline = nowOffline;
    }

    public String getNowOffline(String serverNam) {
        return format(this.nowOffline, serverNam, -1);
    }

    public void setPositionInQueue(String positionInQueue) {
        this.positionInQueue = positionInQueue;
    }

    public String getPositionInQueue(String serverNam, int position) {
        return format(this.positionInQueue, serverNam, position);
    }

    public void setConnecting(String connecting) {
        this.connecting = connecting;
    }

    public String getConnecting(String serverNam) {
        return format(this.connecting, serverNam, -1);
    }

    public void setPendingConnection(String pendingConnection) {
        this.pendingConnection = pendingConnection;
    }

    public String getPendingConnection(String serverNam) {
        return format(this.pendingConnection, serverNam, -1);
    }

    private String format(String template, String serverName, int pos) {
        return template.replace("{server}", serverName).replace("{pos}", pos > 0 ? pos+"" : "...");
    }

    public String formatTabListMessage(String template, String statusReplacement, int pos) {
        return template.replace("{status}", statusReplacement).replace("{pos}", pos > 0 ? pos+"" : "...");
    }
}
