package dev.tins.worldguardextraflagsplus.listeners;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.command.tool.DoubleActionTraceTool;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.InputType;
import com.sk89q.worldedit.event.platform.PlayerInputEvent;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.tins.worldguardextraflagsplus.Config;
import dev.tins.worldguardextraflagsplus.Messages;
import dev.tins.worldguardextraflagsplus.flags.Flags;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationWandListenerTest {
	MockedStatic<Config> config;
	MockedStatic<Messages> messages;
	MockedStatic<Bukkit> bukkitStatic;
	Player player;
	org.bukkit.entity.Player bukkit;
	LocalSession session;
	NavigationWand original;
	AtomicReference<Tool> bound;
	NavigationWandListener listener;
	RegionQuery query;
	LocalPlayer local;
	Location from;
	com.sk89q.worldguard.session.SessionManager wgSessions;
	EventBus bus;
	BaseItemStack item;
	ItemType type;
	NavigationWandListener.ToolAccess access;

	@BeforeEach void setup() throws Exception {
		config = mockStatic(Config.class);
		messages = mockStatic(Messages.class);
		bukkitStatic = mockStatic(Bukkit.class);
		config.when(() -> Config.isFlagEnabled(anyString())).thenReturn(true);
		SessionManager manager = mock(SessionManager.class);
		session = mock(LocalSession.class);
		player = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(true);
		when(manager.get(player)).thenReturn(session);
		UUID id = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		bukkit = mock(org.bukkit.entity.Player.class);
		bukkitStatic.when(() -> Bukkit.getPlayer(id)).thenReturn(bukkit);
		WorldGuardPlugin wg = mock(WorldGuardPlugin.class);
		local = mock(LocalPlayer.class);
		when(wg.wrapPlayer(bukkit)).thenReturn(local);
		RegionContainer regions = mock(RegionContainer.class);
		query = mock(RegionQuery.class);
		when(regions.createQuery()).thenReturn(query);
		from = mock(Location.class);
		when(player.getLocation()).thenReturn(from);
		when(query.queryState(eq(from), eq(local), any(StateFlag.class))).thenReturn(StateFlag.State.DENY);
		wgSessions = mock(com.sk89q.worldguard.session.SessionManager.class);
		item = mock(BaseItemStack.class);
		type = mock(ItemType.class);
		when(item.getType()).thenReturn(type);
		when(player.getItemInHand(HandSide.MAIN_HAND)).thenReturn(item);
		original = mock(NavigationWand.class);
		bound = new AtomicReference<>(original);
		when(session.getTool(type)).thenAnswer(i -> bound.get());
		access = mock(NavigationWandListener.ToolAccess.class);
		when(access.get(session, player, item)).thenAnswer(i -> bound.get());
		doAnswer(i -> { bound.set(i.getArgument(2)); return null; }).when(access).set(eq(session), eq(item), any(Tool.class));
		bus = new EventBus();
		listener = new NavigationWandListener(manager, bus, wg, regions, wgSessions, Logger.getAnonymousLogger(), access);
		bus.register(listener);
	}

	@AfterEach void cleanup() {
		if (listener != null) listener.close();
		if (bukkitStatic != null) bukkitStatic.close();
		if (messages != null) messages.close();
		if (config != null) config.close();
	}

	/** Models PlatformManager, which deliberately ignores the cancelled marker. */
	public final class Dispatcher {
		Runnable queued;
		boolean deferred;
		Dispatcher(boolean deferred) { this.deferred = deferred; }
		@Subscribe public void input(PlayerInputEvent event) {
			Tool captured = bound.get();
			queued = () -> {
				if (captured instanceof DoubleActionTraceTool tool) {
					if (event.getInputType() == InputType.PRIMARY) tool.actSecondary(null, null, player, session);
					else tool.actPrimary(null, null, player, session);
				}
			};
			if (!deferred) queued.run();
		}
	}

	@Test void blocksSynchronousWorldEditBothClicksAndRestoresBinding() {
		Dispatcher dispatcher = new Dispatcher(false); bus.register(dispatcher);
		for (InputType input : InputType.values()) {
			PlayerInputEvent event = new PlayerInputEvent(player, input); bus.post(event);
			assertTrue(event.isCancelled()); assertSame(original, bound.get());
		}
		verifyNoInteractions(original);
		messages.verify(() -> Messages.sendMessageWithCooldown(bukkit, "navwand-jumpto-denied"));
		messages.verify(() -> Messages.sendMessageWithCooldown(bukkit, "navwand-thru-denied"));
	}

	@Test void blocksFaweDeferredActionsAfterOriginalBindingIsRestored() throws Exception {
		Dispatcher dispatcher = new Dispatcher(true); bus.register(dispatcher);
		for (InputType input : InputType.values()) {
			bus.post(new PlayerInputEvent(player, input));
			assertSame(original, bound.get());
			// Run on another thread with no input-event stack or thread-local context.
			Thread thread = new Thread(dispatcher.queued); thread.start(); thread.join();
		}
		verifyNoInteractions(original);
	}

	@Test void independentFlagsAllowRightAndBlockLeft() {
		when(query.queryState(from, local, Flags.NAVWAND_THRU)).thenReturn(StateFlag.State.ALLOW);
		Dispatcher dispatcher = new Dispatcher(false); bus.register(dispatcher);
		bus.post(new PlayerInputEvent(player, InputType.PRIMARY));
		bus.post(new PlayerInputEvent(player, InputType.SECONDARY));
		verify(original).actPrimary(null, null, player, session);
		verify(original, never()).actSecondary(any(), any(), any(), any());
	}

	@Test void independentFlagsAllowLeftAndBlockRight() {
		when(query.queryState(from, local, Flags.NAVWAND_JUMPTO)).thenReturn(StateFlag.State.ALLOW);
		Dispatcher dispatcher = new Dispatcher(false); bus.register(dispatcher);
		bus.post(new PlayerInputEvent(player, InputType.PRIMARY));
		bus.post(new PlayerInputEvent(player, InputType.SECONDARY));
		verify(original).actSecondary(null, null, player, session);
		verify(original, never()).actPrimary(any(), any(), any(), any());
	}

	@Test void bypassPreservesOriginalTool() throws Exception {
		when(wgSessions.hasBypass(eq(local), any())).thenReturn(true);
		bus.post(new PlayerInputEvent(player, InputType.PRIMARY));
		verify(access, never()).set(any(), any(), any());
		messages.verifyNoInteractions();
	}

	@Test void usesActualBoundToolForAnyMaterial() throws Exception {
		for (String material : new String[] {"minecraft:compass", "minecraft:feather", "minecraft:stick"}) {
			when(type.getId()).thenReturn(material);
			PlayerInputEvent event = new PlayerInputEvent(player, InputType.PRIMARY);
			bus.post(event); assertTrue(event.isCancelled()); assertSame(original, bound.get());
		}
		verify(access, times(6)).set(eq(session), eq(item), any());
	}

	@Test void ordinaryItemOrBrushIsNotBlocked() throws Exception {
		Tool brush = mock(Tool.class); bound.set(brush);
		PlayerInputEvent event = new PlayerInputEvent(player, InputType.PRIMARY); bus.post(event);
		assertFalse(event.isCancelled()); verify(access, never()).set(any(), any(), any());
	}

	@Test void unsetFlagAllowsAction() {
		when(query.queryState(from, local, Flags.NAVWAND_JUMPTO)).thenReturn(null);
		PlayerInputEvent event = new PlayerInputEvent(player, InputType.PRIMARY); bus.post(event);
		assertFalse(event.isCancelled()); assertSame(original, bound.get());
	}

	@Test void disabledLeftFlagLeavesRightFlagActive() {
		config.when(() -> Config.isFlagEnabled("navwand-jumpto")).thenReturn(false);
		PlayerInputEvent left = new PlayerInputEvent(player, InputType.PRIMARY); bus.post(left);
		PlayerInputEvent right = new PlayerInputEvent(player, InputType.SECONDARY); bus.post(right);
		assertFalse(left.isCancelled()); assertTrue(right.isCancelled());
	}

	@Test void missingActionPermissionDoesNotInterfere() {
		when(player.hasPermission("worldedit.navigation.jumpto.tool")).thenReturn(false);
		PlayerInputEvent event = new PlayerInputEvent(player, InputType.PRIMARY); bus.post(event);
		assertFalse(event.isCancelled()); messages.verifyNoInteractions();
	}

	@Test void restoresOriginalItemObjectAndDoesNotLeakIntoNextAllowedClick() throws Exception {
		bus.post(new PlayerInputEvent(player, InputType.PRIMARY));
		verify(access).set(session, item, original);
		when(query.queryState(from, local, Flags.NAVWAND_JUMPTO)).thenReturn(StateFlag.State.ALLOW);
		Dispatcher dispatcher = new Dispatcher(false); bus.register(dispatcher);
		bus.post(new PlayerInputEvent(player, InputType.PRIMARY));
		verify(original).actSecondary(null, null, player, session);
	}

	@Test void unrelatedBindingChangeIsNotOverwritten() throws Exception {
		PlayerInputEvent event = new PlayerInputEvent(player, InputType.PRIMARY);
		listener.beforeInput(event); Tool other = mock(Tool.class); bound.set(other);
		listener.afterInput(event); assertSame(other, bound.get());
		verify(access, never()).set(session, item, original);
	}

	@Test void closeRestoresAnOutstandingBinding() {
		listener.beforeInput(new PlayerInputEvent(player, InputType.PRIMARY));
		assertInstanceOf(BlockedNavigationTool.class, bound.get()); listener.close();
		assertSame(original, bound.get());
	}
}
