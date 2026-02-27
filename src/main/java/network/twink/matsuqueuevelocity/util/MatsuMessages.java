package network.twink.matsuqueuevelocity.util;

public class MatsuMessages {

    private String nowQueued, nowOffline, positionInQueue, connecting, pendingConnection,
            waitingConnection, estimatedTime, queuedCommandConfirmation, alreadyQueuedCommandConfirmation,
            alreadyConnectedCommandConfirmation, internalError, tabAltPositionInQueue;

    public void setNowQueued(String s) { this.nowQueued = s; }
    public String getNowQueued(String server) { return baseFormat(nowQueued, server, null, -1, 0, false); }

    public void setQueuedCommandConfirmation(String s) { this.queuedCommandConfirmation = s; }
    public String getQueuedCommandConfirmation(String server) { return baseFormat(queuedCommandConfirmation, server, null, -1, 0, false); }

    public void setAlreadyQueuedCommandConfirmation(String s) { this.alreadyQueuedCommandConfirmation = s; }
    public String getAlreadyQueuedCommandConfirmation(String server) { return baseFormat(alreadyQueuedCommandConfirmation, server, null, -1, 0, false); }

    public void setAlreadyConnectedCommandConfirmation(String s) { this.alreadyConnectedCommandConfirmation = s; }
    public String getAlreadyConnectedCommandConfirmation(String server) { return baseFormat(alreadyConnectedCommandConfirmation, server, null, -1, 0, false); }

    public void setInternalError(String s) { this.internalError = s; }
    public String getInternalError() { return baseFormat(internalError, null, null, -1, 0, false); }

    public void setNowOffline(String s) { this.nowOffline = s; }
    public String getNowOffline(String server) { return baseFormat(nowOffline, server, null, -1, 0, false); }

    public void setPositionInQueue(String s) { this.positionInQueue = s; }
    public String getPositionInQueue(String server, int pos) { return baseFormat(positionInQueue, server, null, pos, 0, false); }

    public void setTabAltPositionInQueue(String s) { this.tabAltPositionInQueue = s; }
    public String getTabAltPositionInQueue(int pos) { return pos < 0 ? "" : tabAltPositionInQueue; }

    public void setConnecting(String s) { this.connecting = s; }
    public String getConnecting(String server) { return baseFormat(connecting, server, null, -1, 0, false); }

    public void setPendingConnection(String s) { this.pendingConnection = s; }
    public String getPendingConnection(String server) { return baseFormat(pendingConnection, server, null, -1, 0, false); }

    public void setWaitingConnection(String s) { this.waitingConnection = s; }
    public String getWaitingConnection(String server) { return baseFormat(waitingConnection, server, null, -1, 0, false); }

    public void setEstimatedTime(String s) { this.estimatedTime = s; }
    public String getEstimatedTime(String server, int pos, long avgTime, boolean doMagic) {
        return baseFormat(estimatedTime, server, null, pos, avgTime, doMagic);
    }
    public String getEstimatedTime(String server, int pos, long avgTime) {
        return getEstimatedTime(server, pos, avgTime, true);
    }

    public String formatTabListMessage(String template, String status, int pos, long avgTime, boolean doMagic) {
        return baseFormat(template, null, status, pos, avgTime, doMagic);
    }

    public String formatTabListMessage(String template, String status, int pos, long avgTime) {
        return formatTabListMessage(template, status, pos, avgTime, true);
    }

    private String baseFormat(String template, String server, String status, int pos, long timeValue, boolean calculateEta) {
        if (template == null) return "";

        long seconds = calculateEta ? calculateSeconds(pos, timeValue) : timeValue;
        String etaStr = (timeValue > 0) ? formatTime(seconds) : "...";
        String posStr = (pos > 0) ? String.valueOf(pos) : "...";

        String result = template.replace("{alt-pos}", getTabAltPositionInQueue(pos)).replace("{pos}", posStr).replace("{eta}", etaStr);
        if (server != null) result = result.replace("{server}", server);
        if (status != null) result = result.replace("{status}", status);

        return result;
    }

    private long calculateSeconds(int pos, long avgTimeBetweenJoins) {
        return (avgTimeBetweenJoins <= 0) ? -1L : (pos * avgTimeBetweenJoins) / 1000;
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds < 0) return "...";

        long months = totalSeconds / 2592000; // 30 days
        long days = (totalSeconds % 2592000) / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (months > 0) sb.append(months).append("mo ");
        if (days > 0)   sb.append(days).append("d ");
        if (hours > 0)  sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}