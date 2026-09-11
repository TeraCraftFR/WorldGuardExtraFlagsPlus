package dev.tins.worldguardextraflagsplus.listeners;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.InputType;
import com.sk89q.worldedit.event.platform.PlayerInputEvent;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.util.eventbus.EventHandler;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;
import dev.tins.worldguardextraflagsplus.Config;
import dev.tins.worldguardextraflagsplus.Messages;
import dev.tins.worldguardextraflagsplus.flags.Flags;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Gates the tool before WorldEdit/FAWE dispatches or queues it. */
public final class NavigationWandListener {
	private final com.sk89q.worldedit.session.SessionManager worldEditSessions;
	private final com.sk89q.worldedit.util.eventbus.EventBus eventBus;
	private final WorldGuardPlugin worldGuard;
	private final RegionContainer regions;
	private final SessionManager sessions;
	private final Logger logger;
	private final ToolAccess tools;
	private final Map<PlayerInputEvent, Binding> pending = new ConcurrentHashMap<>();

	public NavigationWandListener(WorldEdit worldEdit, WorldGuardPlugin worldGuard, RegionContainer regions,
			SessionManager sessions, Logger logger) {
		this(worldEdit.getSessionManager(), worldEdit.getEventBus(), worldGuard, regions, sessions, logger, new ToolAccess());
	}

	NavigationWandListener(com.sk89q.worldedit.session.SessionManager worldEditSessions,
			com.sk89q.worldedit.util.eventbus.EventBus eventBus, WorldGuardPlugin worldGuard, RegionContainer regions,
			SessionManager sessions, Logger logger, ToolAccess tools) {
		this.worldEditSessions = worldEditSessions;
		this.eventBus = eventBus;
		this.worldGuard = worldGuard;
		this.regions = regions;
		this.sessions = sessions;
		this.logger = logger;
		this.tools = tools;
	}

	@Subscribe(priority = EventHandler.Priority.VERY_EARLY)
	public void beforeInput(PlayerInputEvent event) {
		boolean left = event.getInputType() == InputType.PRIMARY;
		String flagName = left ? "navwand-jumpto" : "navwand-thru";
		if (!Config.isFlagEnabled(flagName)) return;
		Player player = event.getPlayer();
		if (!player.hasPermission(left ? "worldedit.navigation.jumpto.tool" : "worldedit.navigation.thru.tool")) return;
		LocalSession session = worldEditSessions.get(player);
		BaseItem item = player.getItemInHand(HandSide.MAIN_HAND);
		try {
			Tool original = tools.get(session, player, item);
			if (!(original instanceof NavigationWand)) return;
			org.bukkit.entity.Player bukkit = Bukkit.getPlayer(player.getUniqueId());
			if (bukkit == null) return;
			LocalPlayer local = worldGuard.wrapPlayer(bukkit);
			boolean bypass = sessions.hasBypass(local, player.getWorld());
			StateFlag flag = left ? Flags.NAVWAND_JUMPTO : Flags.NAVWAND_THRU;
			StateFlag.State state = regions.createQuery().queryState(player.getLocation(), local, flag);
			if (Config.isNavigationWandDebug()) {
				logger.info("[Navwand] player=" + bukkit.getName() + " action=" + flagName
						+ " item=" + item.getType().getId() + " state=" + state + " bypass=" + bypass);
			}
			if (bypass || state != StateFlag.State.DENY) return;
			BlockedNavigationTool blocker = new BlockedNavigationTool();
			Binding binding = new Binding(session, item, original, blocker);
			tools.set(session, item, blocker);
			pending.put(event, binding);
			// Cancelling alone does not stop PlatformManager. Its lookup must receive
			// a harmless tool, including when FAWE captures it in an asynchronous task.
			event.setCancelled(true);
			Messages.sendMessageWithCooldown(bukkit, flagName + "-denied");
		} catch (Exception exception) {
			logger.log(Level.SEVERE, "[Navwand] Cannot intercept navigation tool", exception);
		}
	}

	@Subscribe(priority = EventHandler.Priority.VERY_LATE)
	public void afterInput(PlayerInputEvent event) {
		Binding binding = pending.remove(event);
		if (binding != null) restore(binding);
	}

	private void restore(Binding binding) {
		try {
			// Do not overwrite a binding deliberately changed by another listener.
			if (binding.session().getTool(binding.item().getType()) == binding.blocker()) {
				tools.set(binding.session(), binding.item(), binding.original());
			}
		} catch (Exception exception) {
			logger.log(Level.SEVERE, "[Navwand] Cannot restore navigation tool", exception);
		}
	}

	public void close() {
		eventBus.unregister(this);
		pending.values().forEach(this::restore);
		pending.clear();
	}

	private record Binding(LocalSession session, BaseItem item, Tool original, Tool blocker) {}

	/** FAWE resolves defaults and per-item tools via getTool(Player). */
	static class ToolAccess {
		private final Method faweGet;
		private final Method faweSet;
		ToolAccess() {
			Method get = null;
			Method set = null;
			try {
				get = LocalSession.class.getMethod("getTool", Player.class);
				set = LocalSession.class.getMethod("setTool", BaseItem.class, Tool.class);
			} catch (NoSuchMethodException ignored) {
				get = null;
			}
			faweGet = get;
			faweSet = set;
		}
		Tool get(LocalSession session, Player player, BaseItem item) throws Exception {
			return faweGet == null ? session.getTool(item.getType()) : (Tool) faweGet.invoke(session, player);
		}
		void set(LocalSession session, BaseItem item, Tool tool) throws Exception {
			if (faweGet == null) session.setTool(item.getType(), tool);
			else faweSet.invoke(session, item, tool);
		}
	}
}
