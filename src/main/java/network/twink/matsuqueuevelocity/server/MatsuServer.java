package network.twink.matsuqueuevelocity.server;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.TextComponent;
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
        getServer(plugin).ping()
                .thenAccept(sp -> {
                    if (sp.getDescriptionComponent() instanceof TextComponent tx) {
                        isOnline = true;
                        // we need to make sure the MOTD is actually showing up or the first player
                        // that connects will get kicked with a yellow message that says "rm".
                        // I assume it's because they're connecting before the world has loaded
                        // this check seems to do the trick.
                    }
                })
                .exceptionally(ex -> {
                    isOnline = false;
                    return null;
                });
    }

    public boolean isOnline() {
        return isOnline;
    }
}
