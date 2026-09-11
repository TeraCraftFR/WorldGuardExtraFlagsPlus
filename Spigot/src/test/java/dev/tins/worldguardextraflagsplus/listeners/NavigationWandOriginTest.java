package dev.tins.worldguardextraflagsplus.listeners;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationWandOriginTest {
	private StackTraceElement frame(String owner, String method) {
		return new StackTraceElement(owner, method, "Example.java", 1);
	}
	private StackTraceElement wand(String method) {
		return frame("com.sk89q.worldedit.command.tool.NavigationWand", method);
	}

	@Test void identifiesLeftClickInsideNestedCalls() {
		assertEquals(NavigationWandOrigin.Action.JUMPTO, NavigationWandOrigin.findAction(new StackTraceElement[] {
			frame("org.bukkit.craftbukkit.entity.CraftPlayer", "teleport"), wand("actSecondary")
		}));
	}
	@Test void identifiesRightClick() {
		assertEquals(NavigationWandOrigin.Action.THRU, NavigationWandOrigin.findAction(new StackTraceElement[] {wand("actPrimary")}));
	}
	@Test void ignoresNavigationCommandsAndOtherPlugins() {
		for (String owner : new String[] {"com.sk89q.worldedit.command.NavigationCommands", "other.plugin.NavigationWand", "com.earth2me.essentials.Teleport"}) {
			assertNull(NavigationWandOrigin.findAction(new StackTraceElement[] {frame(owner, "actSecondary")}));
		}
	}
	@Test void ignoresOtherWandMethods() {
		assertNull(NavigationWandOrigin.findAction(new StackTraceElement[] {wand("canUse")}));
	}
	@Test void doesNotLeakAnActionIntoLaterTeleports() {
		assertNotNull(NavigationWandOrigin.findAction(new StackTraceElement[] {wand("actPrimary")}));
		assertNull(NavigationWandOrigin.findAction(new StackTraceElement[0]));
	}
	@Test void closestActionOwnsTheTeleport() {
		assertEquals(NavigationWandOrigin.Action.THRU, NavigationWandOrigin.findAction(new StackTraceElement[] {wand("actPrimary"), wand("actSecondary")}));
	}
}
