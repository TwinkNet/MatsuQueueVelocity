package network.twink.matsuqueuevelocity.util;

public class MatsuMessages {

    private String nowQueued;
    private String nowOffline;
    private String positionInQueue;
    private String connecting;
    private String pendingConnection;
    private String waitingConnection;
    private String estimatedTime;

    public String getEstimatedTime(String serverName, int positionInQueue, long avgTimeBetweenJoins, boolean doMagic) {
        return format(this.estimatedTime, serverName, positionInQueue, avgTimeBetweenJoins, doMagic);
    }

    public String getEstimatedTime(String serverName, int positionInQueue, long avgTimeBetweenJoins) {
        return format(this.estimatedTime, serverName, positionInQueue, avgTimeBetweenJoins, true);
    }

    public void setEstimatedTime(String estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

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

    public void setWaitingConnection(String waitingConnection) {
        this.waitingConnection = waitingConnection;
    }

    public String getWaitingConnection(String serverNam) {
        return format(this.waitingConnection, serverNam, -1);
    }

    private String format(String template, String serverName, int pos) {
        return format(template, serverName, pos, -1, false);
    }

    public String formatTabListMessage(String template, String statusReplacement, int pos) {
        return formatTabListMessage(template, statusReplacement, pos, -1, false);
    }

    private String format(String template, String serverName, int pos, long estimatedTime, boolean doMagic) {
        long seconds = doMagic ? magic(pos, estimatedTime) : estimatedTime;
        return template.replace("{server}", serverName).replace("{pos}", pos > 0 ? pos + "" : "...")
                .replace("{eta}", estimatedTime > 0 ? formatTime(seconds) : "...");
    }

    public String formatTabListMessage(String template, String statusReplacement, int pos, long estimatedTime, boolean doMagic) {
        long seconds = doMagic ? magic(pos, estimatedTime) : estimatedTime;
        return template.replace("{status}", statusReplacement).replace("{pos}", pos > 0 ? pos + "" : "...")
                .replace("{eta}", estimatedTime > 0 ? formatTime(seconds) : "...");
    }

    private String format(String template, String serverName, int pos, long estimatedTime) {
        return format(template, serverName, pos, estimatedTime, true);
    }

    public String formatTabListMessage(String template, String statusReplacement, int pos, long estimatedTime) {
        return formatTabListMessage(template, statusReplacement, pos, estimatedTime, true);
    }

    private static long magic(int pos, long avgTimeBetweenJoins) {
        long seconds;
        if (avgTimeBetweenJoins == -1L) {
            seconds = -1L;
        } else {
            seconds = (pos * avgTimeBetweenJoins) / 1000;
        }
        return seconds;
    }

    private String formatTime(long seconds) {
        int minutes = 0;
        int hours = 0;
        int days = 0;
        int months = 0;
        while (seconds >= 60) {
            minutes++;
            seconds = seconds - 60;
        }
        while (minutes >= 60) {
            hours++;
            minutes = minutes - 60;
        }
        while (hours >= 24) {
            days++;
            hours = hours - 24;
        }
        while (days >= 30) {
            months++;
            days = days - 30;
        }
        StringBuilder sb = new StringBuilder();
        if (months > 0) {
            sb.append(months).append("mo ");
        }
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > -1) {
            sb.append(seconds).append("s ");
        }
        return sb.toString().trim();
    }
}
