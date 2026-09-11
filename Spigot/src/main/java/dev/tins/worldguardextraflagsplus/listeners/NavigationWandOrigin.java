package dev.tins.worldguardextraflagsplus.listeners;

/** Identifies the actual WorldEdit tool invocation, independently of its bound item. */
final class NavigationWandOrigin {
	enum Action { JUMPTO, THRU }

	private NavigationWandOrigin() {}

	static Action findAction(StackTraceElement[] stack) {
		for (StackTraceElement frame : stack) {
			if (frame.getClassName().equals("com.sk89q.worldedit.command.tool.NavigationWand")) {
				// The closest invocation owns the teleport if tool calls are nested.
				if (frame.getMethodName().equals("actSecondary")) return Action.JUMPTO;
				if (frame.getMethodName().equals("actPrimary")) return Action.THRU;
			}
		}
		return null;
	}
}
