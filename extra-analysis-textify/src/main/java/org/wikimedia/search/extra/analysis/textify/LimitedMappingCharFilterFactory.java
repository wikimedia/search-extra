package org.wikimedia.search.extra.analysis.textify;

import static java.util.Collections.unmodifiableMap;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractCharFilterFactory;
import org.elasticsearch.index.analysis.Analysis;

public class LimitedMappingCharFilterFactory extends AbstractCharFilterFactory {

    private final Map<Integer, Integer> oneCharMap;

    LimitedMappingCharFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name);

        List<String> mappings = Analysis.getWordList(env, settings, "mappings");
        if (mappings == null) {
            throw new SettingsException("mapping requires `mappings` to be configured");
        }
        oneCharMap = parseMappings(mappings);
    }

    @Override
    public Reader create(Reader reader) {
        // add LimitedMapping
        return new LimitedMappingCharFilter(oneCharMap, reader);
    }

    private static final Pattern MAPPING_PATTERN =
        Pattern.compile("^(.+)=>(.*)$", Pattern.DOTALL);

    protected static Map<Integer, Integer> parseMappings(List<String> mappings) {
        Map<Integer, Integer> map = new HashMap<>();
        for (String mapping : mappings) {
            Matcher m = MAPPING_PATTERN.matcher(mapping);
            if (!m.find()) {
                throw new SettingsException("Invalid mapping rule: [" + mapping + "]");
            }
            Integer src = parseChar(m.group(1), false); // no map *from* empty
            Integer dst = parseChar(m.group(2), true); // map *to* empty is ok
            if (map.get(src) != null) {
                throw new SettingsException("Multiple mappings for [" + src + "]");
            }
            map.put(src, dst);
        }
        return unmodifiableMap(map);
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
