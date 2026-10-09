package com.boydti.fawe.object.schematic;

import com.boydti.fawe.object.BlockStateRegistry;
import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.util.ReflectionUtils;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.DoubleTag;
import com.sk89q.jnbt.FloatTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.jnbt.NamedTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.MutableBlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.registry.BundledBlockData;
import com.sk89q.worldedit.world.registry.WorldData;
import com.sk89q.worldedit.world.storage.NBTConversions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StructureFormat implements ClipboardReader, ClipboardWriter {
    private static final int WARN_SIZE = 32;

    private NBTInputStream in;
    private NBTOutputStream out;

    public StructureFormat(NBTInputStream in) {
        this.in = in;
    }

    public StructureFormat(NBTOutputStream out) {
        this.out = out;
    }

    @Override
    public Clipboard read(WorldData data) throws IOException {
        return read(data, UUID.randomUUID());
    }

    public Clipboard read(WorldData worldData, UUID clipboardId) throws IOException {
        NamedTag rootTag = in.readNamedTag();
        if (!rootTag.getName().equals("")) {
            throw new IOException("Root tag does not exist or is not first");
        }
        Map<String, Tag> tags = ((CompoundTag) rootTag.getTag()).getValue();

        ListTag size = (ListTag) tags.get("size");
        int width = size.getInt(0);
        int height = size.getInt(1);
        int length = size.getInt(2);

        // Init clipboard
        Vector origin = new Vector(0, 0, 0);
        CuboidRegion region = new CuboidRegion(origin, origin.add(width, height, length).subtract(Vector.ONE));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region, clipboardId);
        // Blocks
        ListTag blocks = (ListTag) tags.get("blocks");
        if (blocks != null) {
            // Palette
            List<CompoundTag> palette = (List<CompoundTag>) (List<?>) tags.get("palette").getValue();
            int[] combinedArray = new int[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag compound = palette.get(i);
                Map<String, Tag> map = compound.getValue();
                String name = ((StringTag) map.get("Name")).getValue();
                BlockStateRegistry registry = BlockStateRegistry.get();
                if (registry != null) {
                    // The platform knows every block, modded ones included, by its real state names
                    Map<String, String> props = new HashMap<>();
                    Tag propertiesTag = map.get("Properties");
                    if (propertiesTag instanceof CompoundTag) {
                        for (Map.Entry<String, Tag> property : ((CompoundTag) propertiesTag).getValue().entrySet()) {
                            if (property.getValue() instanceof StringTag) {
                                props.put(property.getKey(), ((StringTag) property.getValue()).getValue());
                            }
                        }
                    }
                    int combined = registry.getCombined(name, props);
                    if (combined != -1) {
                        combinedArray[i] = combined;
                        continue;
                    }
                }
                BundledBlockData.BlockEntry blockEntry = BundledBlockData.getInstance().findById(name);
                if (blockEntry == null) {
                    Fawe.debug("Unknown block: " + name);
                    continue;
                }
                int id = blockEntry.legacyId;
                byte data = (byte) 0;
                CompoundTag properties = (CompoundTag) map.get("Properties");
                if (blockEntry.states == null || properties == null || blockEntry.states.isEmpty()) {
                    combinedArray[i] = FaweCache.getCombined(id, data);
                    continue;
                }
                for (Map.Entry<String, Tag> property : properties.getValue().entrySet()) {
                    BundledBlockData.FaweState state = blockEntry.states.get(property.getKey());
                    if (state == null) {
                        System.out.println("Invalid property: " + property.getKey());
                        continue;
                    }
                    BundledBlockData.FaweStateValue value = state.valueMap().get(((StringTag) property.getValue()).getValue());
                    if (value == null) {
                        System.out.println("Invalid property: " + property.getKey() + ":" + property.getValue());
                        continue;
                    }
                    data += value.data;
                }
                combinedArray[i] = FaweCache.getCombined(id, data);
            }
            // Populate blocks
            List<CompoundTag> blocksList = (List<CompoundTag>) (List<?>) tags.get("blocks").getValue();
            try {
                for (CompoundTag compound : blocksList) {
                    Map<String, Tag> blockMap = compound.getValue();
                    IntTag stateTag = (IntTag) blockMap.get("state");
                    ListTag posTag = (ListTag) blockMap.get("pos");
                    int combined = combinedArray[stateTag.getValue()];
                    int id = FaweCache.getId(combined);
                    int data = FaweCache.getData(combined);
                    BaseBlock block = FaweCache.getBlock(id, data);
                    // Any stored tile entity, not only those of blocks FAWE knows to have one
                    CompoundTag nbt = (CompoundTag) blockMap.get("nbt");
                    if (nbt != null) {
                        block = new BaseBlock(id, data, nbt);
                    }
                    clipboard.setBlock(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2), block);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Entities
        ListTag entities = (ListTag) tags.get("entities");
        if (entities != null) {
            List<CompoundTag> entityList = (List<CompoundTag>) (List<?>) entities.getValue();
            for (CompoundTag entityEntry : entityList) {
                Map<String, Tag> entityEntryMap = entityEntry.getValue();
                ListTag posTag = (ListTag) entityEntryMap.get("pos");
                CompoundTag nbtTag = (CompoundTag) entityEntryMap.get("nbt");
                // Vanilla writes "id"; older FAWE versions read "Id"
                String id = nbtTag.getString("id");
                if (id.isEmpty()) {
                    id = nbtTag.getString("Id");
                }
                Location location = NBTConversions.toLocation(clipboard, posTag, nbtTag.getListTag("Rotation"));
                if (!id.isEmpty()) {
                    BaseEntity state = new BaseEntity(id, nbtTag);
                    clipboard.createEntity(location, state);
                }
            }
        }
        return clipboard;
    }

    @Override
    public void write(Clipboard clipboard, WorldData worldData) throws IOException {
        write(clipboard, worldData, "FAWE");
    }

    public void write(Clipboard clipboard, WorldData worldData, String owner) throws IOException {
        Region region = clipboard.getRegion();
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();
        if (width > WARN_SIZE || height > WARN_SIZE || length > WARN_SIZE) {
            Fawe.debug("A structure longer than 32 is unsupported by minecraft (but probably still works)");
        }
        Map<String, Object> structure = FaweCache.asMap("version", 1, "author", owner);
        // ignored: version / owner
        MutableBlockVector mutable = new MutableBlockVector(0, 0, 0);
        // Palette index per combined id; a map because combined ids exceed 16 bits above id 4095
        Map<Integer, Integer> indexes = new HashMap<>();
        // Size
        structure.put("size", Arrays.asList(width, height, length));
        // Palette
        {
            ArrayList<HashMap<String, Object>> palette = new ArrayList<>();
            for (Vector point : region) {
                BaseBlock block = clipboard.getBlock(point);
                if (block.getId() == 217) continue; // Void
                int combined = FaweCache.getCombined(block);
                if (indexes.containsKey(combined)) {
                    continue;
                }
                HashMap<String, Object> paletteEntry = new HashMap<>();
                BlockStateRegistry registry = BlockStateRegistry.get();
                String registryName = registry != null ? registry.getName(block.getId()) : null;
                if (registryName != null) {
                    paletteEntry.put("Name", registryName);
                    Map<String, String> properties = registry.getProperties(block.getId(), block.getData());
                    if (properties != null && !properties.isEmpty()) {
                        paletteEntry.put("Properties", new HashMap<String, Object>(properties));
                    }
                    indexes.put(combined, palette.size());
                    palette.add(paletteEntry);
                    continue;
                }
                BundledBlockData.BlockEntry blockData = BundledBlockData.getInstance().findById(block.getId());
                if (blockData == null) {
                    Fawe.debug("Skipping unknown block id " + block.getId() + " in structure");
                    continue;
                }
                indexes.put(combined, palette.size());
                paletteEntry.put("Name", blockData.id);
                if (blockData.states != null && !blockData.states.isEmpty()) {
                    Map<String, Object> properties = new HashMap<>();
                    loop:
                    for (Map.Entry<String, BundledBlockData.FaweState> stateEntry : blockData.states.entrySet()) {
                        BundledBlockData.FaweState state = stateEntry.getValue();
                        for (Map.Entry<String, BundledBlockData.FaweStateValue> value : state.valueMap().entrySet()) {
                            if (value.getValue().isSet(block)) {
                                String stateName = stateEntry.getKey();
                                String stateValue = value.getKey();
                                properties.put(stateName, stateValue);
                            }
                        }
                    }
                    if (!properties.isEmpty()) {
                        paletteEntry.put("Properties", properties);
                    }
                }
                palette.add(paletteEntry);
            }
            if (!palette.isEmpty()) {
                structure.put("palette", palette);
            }
        }
        // Blocks
        {
            ArrayList<Map<String, Object>> blocks = new ArrayList<>();
            Vector min = region.getMinimumPoint();
            for (Vector point : region) {
                BaseBlock block = clipboard.getBlock(point);
                if (block.getId() == 217) continue; // Void
                int combined = FaweCache.getCombined(block);
                Integer index = indexes.get(combined);
                if (index == null) {
                    continue; // unknown block, left out of the palette
                }
                List<Integer> pos = Arrays.asList((int) (point.getX() - min.getX()), (int) (point.getY() - min.getY()), (int) (point.getZ() - min.getZ()));
                if (!block.hasNbtData()) {
                    blocks.add(FaweCache.asMap("state", index, "pos", pos));
                } else {
                    blocks.add(FaweCache.asMap("state", index, "pos", pos, "nbt", block.getNbtData()));
                }
            }
            if (!blocks.isEmpty()) {
                structure.put("blocks", blocks);
            }
        }
        // Entities
        {
            ArrayList<Map<String, Object>> entities = new ArrayList<>();
            for (Entity entity : clipboard.getEntities()) {
                Location loc = entity.getLocation();
                // Structure files store positions relative to their own origin corner
                Vector entityMin = region.getMinimumPoint();
                double ex = loc.getX() - entityMin.getX();
                double ey = loc.getY() - entityMin.getY();
                double ez = loc.getZ() - entityMin.getZ();
                List<Double> pos = Arrays.asList(ex, ey, ez);
                List<Integer> blockPos = Arrays.asList((int) Math.floor(ex), (int) Math.floor(ey), (int) Math.floor(ez));
                BaseEntity state = entity.getState();
                if (state != null) {
                    CompoundTag nbt = state.getNbtData();
                    Map<String, Tag> nbtMap = ReflectionUtils.getMap(nbt.getValue());
                    // Replace rotation data
                    nbtMap.put("Rotation", writeRotation(entity.getLocation(), "Rotation"));
                    nbtMap.put("id", new StringTag(state.getTypeId()));
                    Map<String, Object> entityMap = FaweCache.asMap("pos", pos, "blockPos", blockPos, "nbt", nbt);
                    entities.add(entityMap);
                }
            }
            if (!entities.isEmpty()) {
                structure.put("entities", entities);
            }
        }
        out.writeNamedTag("", FaweCache.asTag(structure));
        close();
    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
    }

    private Tag writeVector(Vector vector, String name) {
        List<DoubleTag> list = new ArrayList<DoubleTag>();
        list.add(new DoubleTag(vector.getX()));
        list.add(new DoubleTag(vector.getY()));
        list.add(new DoubleTag(vector.getZ()));
        return new ListTag(DoubleTag.class, list);
    }

    private Tag writeRotation(Location location, String name) {
        List<FloatTag> list = new ArrayList<FloatTag>();
        list.add(new FloatTag(location.getYaw()));
        list.add(new FloatTag(location.getPitch()));
        return new ListTag(FloatTag.class, list);
    }
}
