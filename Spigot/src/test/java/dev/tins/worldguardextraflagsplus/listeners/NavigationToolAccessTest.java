package dev.tins.worldguardextraflagsplus.listeners;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.world.item.ItemType;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationToolAccessTest {
	@Test void usesRuntimeSpecificLookupAndPreservesItemForRestore() throws Exception {
		LocalSession session = mock(LocalSession.class);
		Player player = mock(Player.class);
		BaseItemStack item = mock(BaseItemStack.class);
		ItemType type = mock(ItemType.class);
		when(item.getType()).thenReturn(type);
		NavigationWand wand = mock(NavigationWand.class);
		NavigationWandListener.ToolAccess access = new NavigationWandListener.ToolAccess();
		Method faweLookup;
		try { faweLookup = LocalSession.class.getMethod("getTool", Player.class); }
		catch (NoSuchMethodException ex) { faweLookup = null; }
		if (faweLookup == null) {
			when(session.getTool(type)).thenReturn(wand);
			assertSame(wand, access.get(session, player, item));
			access.set(session, item, wand);
			verify(session).setTool(type, wand);
			System.out.println("NAVWAND_RUNTIME=WorldEdit item-type API");
		} else {
			when((Tool) faweLookup.invoke(session, player)).thenReturn(wand);
			assertSame(wand, access.get(session, player, item));
			verify(session, never()).getTool(type);
			access.set(session, item, wand);
			LocalSession.class.getMethod("setTool", BaseItem.class, Tool.class).invoke(verify(session), item, wand);
			System.out.println("NAVWAND_RUNTIME=FAWE player lookup and original-item API");
		}
	}
}
