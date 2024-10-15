package org.wikimedia.search.extra.analysis.textify;

import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsException;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractCharFilterFactory;
import org.opensearch.index.analysis.Analysis;

import com.google.common.collect.Maps;

public class LimitedMappingCharFilterFactory extends AbstractCharFilterFactory {

    private final Map<Integer, Integer> oneCharMap;

    LimitedMappingCharFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name);

        List<Map.Entry<Integer, Integer>> mappings = Analysis.parseWordList(env, settings, "mappings", LimitedMappingCharFilterFactory::parse);
        if (mappings == null) {
            throw new SettingsException("mapping requires `mappings` to be configured");
        }
        final Set<Integer> seen = new HashSet<>();
        oneCharMap = mappings.stream()
            .peek(entry -> {
                if (!seen.add(entry.getKey())) {
                    throw new SettingsException("Multiple mappings for [" + entry.getKey() + "]");
                }
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Reader create(Reader reader) {
        // add LimitedMapping
        return new LimitedMappingCharFilter(oneCharMap, reader);
    }

    private static final Pattern MAPPING_PATTERN =
        Pattern.compile("^(.+)=>(.*)$", Pattern.DOTALL);

    protected static Map.Entry<Integer, Integer> parse(String mapping) {
        Matcher m = MAPPING_PATTERN.matcher(mapping);
        if (!m.find()) {
            throw new SettingsException("Invalid mapping rule: [" + mapping + "]");
        }
        Integer src = parseChar(m.group(1), false); // no map *from* empty
        Integer dst = parseChar(m.group(2), true); // map *to* empty is ok
        return Maps.immutableEntry(src, dst);
    }

    private static Integer parseChar(String s, boolean allowEmpty) {
        int len = s.length();

        if (len == 0) {
            if (allowEmpty) {
                return -1;
            }
        } else {
            char c = s.charAt(0);
            if (len == 1) {
                return (int) c;
            }
            if (c == '\\') {
                if (len == 2) {
                    c = s.charAt(1);
                    switch (c) {
                        case '\\':
                        case '\'':
                        case '"':
                            return (int) c;
                        case 't':
                            return (int) '\t';
                        case 'n':
                            return (int) '\n';
                        case 'r':
                            return (int) '\r';
                        default:
                            break;
                    }
                } else if (len == 6 && s.charAt(1) == 'u') {
                    return Integer.parseInt(s.substring(2, 6), 16);
                }
            }
        }
        throw new SettingsException("Invalid escaped character: [" + s + "]");
    }

}
