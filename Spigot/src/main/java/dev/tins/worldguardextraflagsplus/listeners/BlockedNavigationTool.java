package dev.tins.worldguardextraflagsplus.listeners;

import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.command.tool.DoubleActionTraceTool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Platform;

/** Per-input no-op captured by FAWE instead of the real navigation tool. */
final class BlockedNavigationTool implements DoubleActionTraceTool {
	@Override public boolean canUse(Actor actor) { return true; }
	@Override public boolean actPrimary(Platform platform, LocalConfiguration config, Player player, LocalSession session) {
		return true;
	}
	@Override public boolean actSecondary(Platform platform, LocalConfiguration config, Player player, LocalSession session) {
		return true;
	}
}
