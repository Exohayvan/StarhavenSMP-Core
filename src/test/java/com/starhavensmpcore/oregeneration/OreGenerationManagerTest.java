package com.starhavensmpcore.oregeneration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.starhavensmpcore.items.CustomBlockRegistry;
import com.starhavensmpcore.items.GenerationRules;
import com.starhavensmpcore.market.StarhavenSMPCoreTestPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.Chunk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OreGenerationManagerTest {

    private ServerMock server;
    private StarhavenSMPCoreTestPlugin plugin;
    private OreGenerationManager manager;

    @Before
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StarhavenSMPCoreTestPlugin.class);
        deleteOreDatabase();
        manager = new OreGenerationManager(plugin, new CustomBlockRegistry());
    }

    @After
    public void tearDown() throws Exception {
        if (manager != null) {
            manager.shutdown();
        }
        MockBukkit.unmock();
        deleteOreDatabase();
    }

    @Test
    public void markChunkGeneratedInsertsAndPreventsDuplicate() throws Exception {
        UUID worldId = UUID.randomUUID();
        boolean first = invokeMarkChunkGenerated("test_ore", worldId, 4, 7);
        boolean second = invokeMarkChunkGenerated("test_ore", worldId, 4, 7);

        assertTrue(first);
        assertFalse(second);
        assertTrue(invokeIsGenerated("test_ore", worldId, 4, 7));
    }

    @Test
    public void markChunkGeneratedReturnsFalseWhenConnectionMissing() throws Exception {
        manager.shutdown();
        boolean result = invokeMarkChunkGenerated("test_ore", UUID.randomUUID(), 1, 2);
        assertFalse(result);
    }

    @Test
    public void markChunkGeneratedRespectsLegacyVoidstone() throws Exception {
        UUID worldId = UUID.randomUUID();
        insertLegacyVoidstone(worldId, 1, 2);

        boolean result = invokeMarkChunkGenerated("voidstone_ore", worldId, 1, 2);

        assertFalse(result);
        List<?> entries = invokeLoadGeneratedChunks("voidstone_ore");
        assertEquals(1, entries.size());
    }

    @Test
    public void loadGeneratedChunksReturnsEmptyForInvalidId() throws Exception {
        List<?> nullEntries = invokeLoadGeneratedChunks(null);
        List<?> emptyEntries = invokeLoadGeneratedChunks("");
        assertTrue(nullEntries.isEmpty());
        assertTrue(emptyEntries.isEmpty());
    }

    @Test
    public void scanChunkForRepairCountsTargetMatches() throws Exception {
        WorldMock world = server.addSimpleWorld("oregen-world");
        Chunk chunk = world.getChunkAt(0, 0);
        int y = world.getMinHeight();
        Block block = world.getBlockAt(1, y, 1);
        block.setType(Material.NOTE_BLOCK, false);
        BlockData data = block.getBlockData();

        GenerationRules rules = new GenerationRules(Collections.emptySet(), y, y, 1.0, 0.0, 0, 0, 1, 0.0,
                Collections.singleton(Material.NOTE_BLOCK));

        Object result = invokeScanChunkForRepair(chunk, data, rules);
        int matches = getResultField(result, "targetMatches");
        int registered = getResultField(result, "registeredBlocks");

        assertEquals(1, matches);
        assertEquals(0, registered);
    }

    private void deleteOreDatabase() throws Exception {
        File db = new File(plugin.getDataFolder(), "ore_generation.db");
        if (db.exists()) {
            db.delete();
        }
    }

    private boolean invokeMarkChunkGenerated(String oreId, UUID worldId, int chunkX, int chunkZ) throws Exception {
        Method method = OreGenerationManager.class.getDeclaredMethod(
                "markChunkGenerated", String.class, UUID.class, int.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(manager, oreId, worldId, chunkX, chunkZ);
    }

    private boolean invokeIsGenerated(String oreId, UUID worldId, int chunkX, int chunkZ) throws Exception {
        Method method = OreGenerationManager.class.getDeclaredMethod(
                "isGenerated", String.class, UUID.class, int.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(manager, oreId, worldId, chunkX, chunkZ);
    }

    private List<?> invokeLoadGeneratedChunks(String oreId) throws Exception {
        Method method = OreGenerationManager.class.getDeclaredMethod("loadGeneratedChunks", String.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(manager, oreId);
    }

    private void insertLegacyVoidstone(UUID worldId, int chunkX, int chunkZ) throws Exception {
        Connection connection = getConnection();
        assertNotNull(connection);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO voidstone_ore_chunks (world, chunk_x, chunk_z, generated_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, worldId.toString());
            insert.setInt(2, chunkX);
            insert.setInt(3, chunkZ);
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    private Connection getConnection() throws Exception {
        Field field = OreGenerationManager.class.getDeclaredField("connection");
        field.setAccessible(true);
        return (Connection) field.get(manager);
    }

    private Object invokeScanChunkForRepair(Chunk chunk, BlockData data, GenerationRules rules) throws Exception {
        Method method = OreGenerationManager.class.getDeclaredMethod(
                "scanChunkForRepair", Chunk.class, BlockData.class, GenerationRules.class, java.util.Map.class);
        method.setAccessible(true);
        return method.invoke(manager, chunk, data, rules, null);
    }

    private int getResultField(Object result, String fieldName) throws Exception {
        Field field = result.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(result);
    }
}
