package dev.tins.worldguardextraflagsplus.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;
import dev.tins.worldguardextraflagsplus.Config;
import dev.tins.worldguardextraflagsplus.Messages;
import dev.tins.worldguardextraflagsplus.flags.Flags;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Controls actual navigation-wand teleports from the departure region. */
public final class NavigationWandListener implements Listener {
	private final WorldGuardPlugin worldGuard;
	private final RegionContainer regions;
	private final SessionManager sessions;

	public NavigationWandListener(WorldGuardPlugin worldGuard, RegionContainer regions, SessionManager sessions) {
		this.worldGuard = worldGuard;
		this.regions = regions;
		this.sessions = sessions;
	}

	/**
	 * WorldEdit processes cancelled clicks, so gate the actual teleport instead.
	 * A generic PLUGIN cause or recent-click timer would catch unrelated teleports.
	 * This requires synchronous WorldEdit tool calls; deferred forks need their own integration.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		if (event.isCancelled() || event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
		boolean jumptoEnabled = Config.isFlagEnabled("navwand-jumpto");
		boolean thruEnabled = Config.isFlagEnabled("navwand-thru");
		if (!jumptoEnabled && !thruEnabled) return;

		NavigationWandOrigin.Action action = NavigationWandOrigin.findAction(Thread.currentThread().getStackTrace());
		if (action == null) return;
		boolean jumpto = action == NavigationWandOrigin.Action.JUMPTO;
		if (!(jumpto ? jumptoEnabled : thruEnabled)) return;

		LocalPlayer local = worldGuard.wrapPlayer(event.getPlayer());
		if (sessions.hasBypass(local, BukkitAdapter.adapt(event.getFrom().getWorld()))) return;
		StateFlag flag = jumpto ? Flags.NAVWAND_JUMPTO : Flags.NAVWAND_THRU;
		if (regions.createQuery().queryState(BukkitAdapter.adapt(event.getFrom()), local, flag) == StateFlag.State.DENY) {
			event.setCancelled(true);
			Messages.sendMessageWithCooldown(event.getPlayer(), jumpto ? "navwand-jumpto-denied" : "navwand-thru-denied");
		}
	}
}
