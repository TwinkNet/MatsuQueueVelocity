package network.twink.matsuqueuevelocity.server;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.TextComponent;
import network.twink.matsuqueuevelocity.MatsuQueuePlugin;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
                    if (sp.getDescriptionComponent() instanceof TextComponent) {
                        if (!isOnline) {
                            plugin.getProxyServer().getScheduler()
                                    .buildTask(plugin,
                                            () ->
                                                    /*
                                                    check if the server is STILL online
                                                    if so, isOnline will be set immediately.
                                                    this has to be this way because we can't
                                                    let players join the server too quickly after it
                                                    comes online (it usually fails because the server isn't ready
                                                    or the world isn't initialised)

                                                    this entire plugin is incredibly hacky sorry
                                                    */
                                                    this.updateOnlineNow(plugin)).delay(2L, TimeUnit.SECONDS)
                                            .schedule();
                        }
                    }
                })
                .exceptionally(ex -> {
                    isOnline = false;
                    return null;
                });
    }

    private void updateOnlineNow(MatsuQueuePlugin plugin) {
        getServer(plugin).ping()
                .thenAccept(sp -> {
                    if (sp.getDescriptionComponent() instanceof TextComponent) {
                        isOnline = true;
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
