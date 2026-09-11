package dev.tins.worldguardextraflagsplus.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.SessionManager;
import dev.tins.worldguardextraflagsplus.Config;
import dev.tins.worldguardextraflagsplus.Messages;
import dev.tins.worldguardextraflagsplus.flags.Flags;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationWandListenerTest {
	private MockedStatic<Config> config;
	private MockedStatic<Messages> messages;
	private MockedStatic<BukkitAdapter> adapter;
	private MockedStatic<NavigationWandOrigin> origin;
	private Player player;
	private ItemStack item;
	private LocalPlayer local;
	private SessionManager sessions;
	private RegionQuery query;
	private Location from;
	private com.sk89q.worldedit.util.Location weFrom;
	private com.sk89q.worldedit.world.World weWorld;
	private NavigationWandListener listener;

	@BeforeEach void setup() {
		config = mockStatic(Config.class);
		messages = mockStatic(Messages.class);
		adapter = mockStatic(BukkitAdapter.class);
		origin = mockStatic(NavigationWandOrigin.class);
		config.when(() -> Config.isFlagEnabled("navwand-jumpto")).thenReturn(true);
		origin.when(() -> NavigationWandOrigin.findAction(any())).thenReturn(NavigationWandOrigin.Action.JUMPTO);
		config.when(() -> Config.isFlagEnabled("navwand-thru")).thenReturn(true);

		player = mock(Player.class);
		item = mock(ItemStack.class);
		World world = mock(World.class);
		from = new Location(world, 0, 70, 0);
		when(player.getLocation()).thenReturn(from);
		weWorld = mock(com.sk89q.worldedit.world.World.class);
		weFrom = mock(com.sk89q.worldedit.util.Location.class);
		adapter.when(() -> BukkitAdapter.adapt(world)).thenReturn(weWorld);
		adapter.when(() -> BukkitAdapter.adapt(from)).thenReturn(weFrom);
		WorldGuardPlugin wg = mock(WorldGuardPlugin.class);
		local = mock(LocalPlayer.class);
		when(wg.wrapPlayer(player)).thenReturn(local);
		RegionContainer regions = mock(RegionContainer.class);
		query = mock(RegionQuery.class);
		when(regions.createQuery()).thenReturn(query);
		when(query.queryState(weFrom, local, Flags.NAVWAND_JUMPTO)).thenReturn(StateFlag.State.DENY);
		sessions = mock(SessionManager.class);
		listener = new NavigationWandListener(wg, regions, sessions);
	}

	@AfterEach void cleanup() {
		origin.close(); adapter.close(); messages.close(); config.close();
	}

	private PlayerTeleportEvent teleport(PlayerTeleportEvent.TeleportCause cause) {
		return new PlayerTeleportEvent(player, from, new Location(from.getWorld(), 30, 80, 30), cause);
	}

	@Test void blocksActualNavigationFromDeniedSource() {
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertTrue(event.isCancelled());
		verify(query).queryState(weFrom, local, Flags.NAVWAND_JUMPTO);
	}

	@Test void preservesUnrelatedPluginTeleportEvenWhileHoldingCompass() {
		origin.when(() -> NavigationWandOrigin.findAction(any())).thenReturn(null);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertFalse(event.isCancelled());
	}

	@Test void preservesCommandsPearlsAndPortals() {
		for (PlayerTeleportEvent.TeleportCause cause : new PlayerTeleportEvent.TeleportCause[] {
				PlayerTeleportEvent.TeleportCause.COMMAND, PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
				PlayerTeleportEvent.TeleportCause.NETHER_PORTAL, PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT}) {
			PlayerTeleportEvent event = teleport(cause);
			listener.onTeleport(event);
			assertFalse(event.isCancelled(), cause.toString());
		}
	}

	@Test void honorsWorldGuardBypass() {
		when(sessions.hasBypass(local, weWorld)).thenReturn(true);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertFalse(event.isCancelled());
	}

	@Test void allowsNavigationOutsideDeniedRegions() {
		when(query.queryState(weFrom, local, Flags.NAVWAND_JUMPTO)).thenReturn(StateFlag.State.ALLOW);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertFalse(event.isCancelled());
	}

	@Test void respectsDisabledFlag() {
		config.when(() -> Config.isFlagEnabled("navwand-jumpto")).thenReturn(false);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertFalse(event.isCancelled());
	}

	@Test void supportsAnyItemWithoutInspectingInventory() {
		when(item.getType()).thenReturn(Material.STONE);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertTrue(event.isCancelled());
		verify(player, never()).getInventory();
	}

	@Test void blocksRightClickWithItsOwnFlagAndMessage() {
		origin.when(() -> NavigationWandOrigin.findAction(any())).thenReturn(NavigationWandOrigin.Action.THRU);
		when(query.queryState(weFrom, local, Flags.NAVWAND_THRU)).thenReturn(StateFlag.State.DENY);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertTrue(event.isCancelled());
		verify(query).queryState(weFrom, local, Flags.NAVWAND_THRU);
		messages.verify(() -> Messages.sendMessageWithCooldown(player, "navwand-thru-denied"));
	}

	@Test void denyingLeftClickDoesNotDenyRightClick() {
		origin.when(() -> NavigationWandOrigin.findAction(any())).thenReturn(NavigationWandOrigin.Action.THRU);
		when(query.queryState(weFrom, local, Flags.NAVWAND_THRU)).thenReturn(StateFlag.State.ALLOW);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertFalse(event.isCancelled());
	}

	@Test void disablingRightClickFlagDoesNotDisableLeftClick() {
		config.when(() -> Config.isFlagEnabled("navwand-thru")).thenReturn(false);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertTrue(event.isCancelled());
	}

	@Test void disablingLeftClickFlagDoesNotDisableRightClick() {
		config.when(() -> Config.isFlagEnabled("navwand-jumpto")).thenReturn(false);
		origin.when(() -> NavigationWandOrigin.findAction(any())).thenReturn(NavigationWandOrigin.Action.THRU);
		when(query.queryState(weFrom, local, Flags.NAVWAND_THRU)).thenReturn(StateFlag.State.DENY);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertTrue(event.isCancelled());
	}

	@Test void unsetRegionFlagAllowsNavigation() {
		when(query.queryState(weFrom, local, Flags.NAVWAND_JUMPTO)).thenReturn(null);
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		listener.onTeleport(event);
		assertFalse(event.isCancelled());
	}

	@Test void preservesAlreadyCancelledTeleportWithoutMessage() {
		PlayerTeleportEvent event = teleport(PlayerTeleportEvent.TeleportCause.PLUGIN);
		event.setCancelled(true);
		listener.onTeleport(event);
		assertTrue(event.isCancelled());
		messages.verifyNoInteractions();
	}
}
