package net.camacraft.walljumpunbound.logic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches blocks against the config's block list. Each entry is one of:
 * <ul>
 *   <li>a block id: {@code minecraft:ice} (a bare {@code ice} means {@code minecraft:ice})</li>
 *   <li>a block tag, with a leading hash: {@code #minecraft:logs}</li>
 *   <li>a wildcard pattern, where {@code *} is any run of characters and
 *   {@code ?} any single one: {@code minecraft:*_glass}, {@code *:*_ore}</li>
 *   <li>a regular expression between slashes, matched against the whole id:
 *   {@code /minecraft:(oak|spruce)_.*&#47;}</li>
 * </ul>
 * The list is compiled once and only again when its contents change, which
 * happens on a config edit or when a server sends its own list.
 */
public final class BlockListMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("walljumpunbound");

    private static List<String> compiledFrom;
    private static final Set<ResourceLocation> IDS = new HashSet<>();
    private static final List<TagKey<Block>> TAGS = new ArrayList<>();
    private static final List<Pattern> PATTERNS = new ArrayList<>();

    private BlockListMatcher() {
    }

    /** Whether the block is named by the list, by id, tag, wildcard or expression. */
    public static boolean matches(List<String> list, BlockState state) {
        if (!list.equals(compiledFrom)) compile(list);
        if (IDS.isEmpty() && TAGS.isEmpty() && PATTERNS.isEmpty()) return false;

        for (TagKey<Block> tag : TAGS) {
            if (state.is(tag)) return true;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (IDS.contains(id)) return true;
        String name = id.toString();
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(name).matches()) return true;
        }
        return false;
    }

    private static void compile(List<String> list) {
        compiledFrom = new ArrayList<>(list);
        IDS.clear();
        TAGS.clear();
        PATTERNS.clear();

        for (String raw : list) {
            String entry = raw == null ? "" : raw.trim();
            if (entry.isEmpty()) continue;

            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag == null) {
                    LOGGER.warn("Block list entry '{}' is not a valid block tag and was ignored", raw);
                    continue;
                }
                TAGS.add(TagKey.create(Registries.BLOCK, tag));
            } else if (entry.length() >= 2 && entry.startsWith("/") && entry.endsWith("/")) {
                addPattern(raw, entry.substring(1, entry.length() - 1));
            } else if (entry.indexOf('*') >= 0 || entry.indexOf('?') >= 0) {
                addPattern(raw, globToRegex(entry));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id == null) {
                    LOGGER.warn("Block list entry '{}' is not a valid block id and was ignored", raw);
                    continue;
                }
                IDS.add(id);
            }
        }
        LOGGER.info("Block list compiled: {} ids, {} tags, {} patterns", IDS.size(), TAGS.size(), PATTERNS.size());
    }

    private static void addPattern(String raw, String regex) {
        try {
            PATTERNS.add(Pattern.compile(regex));
        } catch (PatternSyntaxException e) {
            LOGGER.warn("Block list entry '{}' is not a valid pattern and was ignored: {}", raw, e.getDescription());
        }
    }

    /** A wildcard pattern as a regular expression: everything literal except * and ?. */
    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) regex.append(Pattern.quote(literal.toString()));
        return regex.toString();
    }
}
