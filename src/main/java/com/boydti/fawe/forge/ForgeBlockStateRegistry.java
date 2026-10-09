package com.boydti.fawe.forge;

import com.boydti.fawe.object.BlockStateRegistry;
import com.google.common.base.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

public class ForgeBlockStateRegistry extends BlockStateRegistry {

    private static Block byId(int id) {
        Block block = Block.getBlockById(id);
        if (block == null || (block == Blocks.AIR && id != 0)) {
            return null;
        }
        return block;
    }

    @Override
    public String getName(int id) {
        Block block = byId(id);
        if (block == null) {
            return null;
        }
        ResourceLocation name = Block.REGISTRY.getNameForObject(block);
        return name != null ? name.toString() : null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, String> getProperties(int id, int data) {
        Block block = byId(id);
        if (block == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        try {
            IBlockState state = block.getStateFromMeta(data);
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                IProperty property = entry.getKey();
                result.put(property.getName(), property.getName(entry.getValue()));
            }
        } catch (Throwable ignore) {
            // A mod rejected this meta; store the block without properties
        }
        return result;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int getCombined(String name, Map<String, String> properties) {
        Block block = Block.REGISTRY.containsKey(new ResourceLocation(name)) ? Block.getBlockFromName(name) : null;
        if (block == null) {
            return -1;
        }
        IBlockState state = block.getDefaultState();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                IProperty property = block.getBlockState().getProperty(entry.getKey());
                if (property == null) {
                    continue;
                }
                Optional value = property.parseValue(entry.getValue());
                if (value.isPresent()) {
                    state = state.withProperty(property, (Comparable) value.get());
                }
            }
        }
        int meta;
        try {
            meta = block.getMetaFromState(state);
        } catch (Throwable e) {
            meta = 0;
        }
        return (Block.getIdFromBlock(block) << 4) | (meta & 15);
    }
}
