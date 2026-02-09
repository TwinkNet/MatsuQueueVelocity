package network.twink.matsuqueuevelocity.server;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;

import java.util.Optional;

public class MatsuServer {

    private final String velocityName;
    private final String displayName;
    private boolean isOnline;

    public MatsuServer(String velocityName, String displayName) {
        this.velocityName = velocityName;
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVelocityName() {
        return velocityName;
    }

    public RegisteredServer getServer(MatsuQueuePlugin plugin) {
        Optional<RegisteredServer> optional = plugin.getProxyServer().getServer(this.velocityName);
        return optional.orElse(null);
    }

    public void updateOnline(MatsuQueuePlugin plugin) {
        getServer(plugin).ping().thenAccept(sp -> isOnline = sp != null);
    }

    public boolean isOnline() {
        return isOnline;
    }
}
