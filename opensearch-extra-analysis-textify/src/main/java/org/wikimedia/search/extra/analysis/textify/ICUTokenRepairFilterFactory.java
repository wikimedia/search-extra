package org.wikimedia.search.extra.analysis.textify;

import static java.util.Collections.emptySet;
import static org.wikimedia.search.extra.analysis.textify.ICUTokenRepairFilterConfig.DEFAULT_MAX_TOK_LEN;
import static org.wikimedia.search.extra.analysis.textify.ICUTokenRepairFilterConfig.DEFAULT_KEEP_CAMEL_SPLIT;
import static org.wikimedia.search.extra.analysis.textify.ICUTokenRepairFilterConfig.DEFAULT_MERGE_NUM_ONLY;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.lucene.analysis.TokenStream;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsException;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

public class ICUTokenRepairFilterFactory extends AbstractTokenFilterFactory {

    private static final String SETTINGS_EXCP_PREFIX = "icu_token_repair configuration error: ";

    public static final String MAX_TOK_LEN_KEY = "max_token_length";
    public static final String KEEP_CAMEL_KEY = "keep_camel_split";
    public static final String NUM_ONLY_KEY = "merge_numbers_only";
    public static final String ALLOW_TYPES_KEY = "allow_types";
    public static final String DENY_TYPES_KEY = "deny_types";
    public static final String TYPE_PRESET_KEY = "type_preset";
    public static final String ALLOW_SCRIPTS_KEY = "allow_scripts";
    public static final String SCRIPT_PRESET_KEY = "script_preset";

    private ICUTokenRepairFilterConfig icuTokRepConfig = new ICUTokenRepairFilterConfig();

    ICUTokenRepairFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        icuTokRepConfig.setMaxTokenLength(settings.getAsInt(MAX_TOK_LEN_KEY, DEFAULT_MAX_TOK_LEN));
        icuTokRepConfig.setKeepCamelSplit(settings.getAsBoolean(KEEP_CAMEL_KEY, DEFAULT_KEEP_CAMEL_SPLIT));
        icuTokRepConfig.setMergeNumOnly(settings.getAsBoolean(NUM_ONLY_KEY, DEFAULT_MERGE_NUM_ONLY));

        parseTypeLimits(settings);
        parseScriptLimits(settings);

    }

    private void parseTypeLimits(Settings settings) throws SettingsException {
        int typesConfigSeen = 0;

        if (settings.get(ALLOW_TYPES_KEY) != null) {
            icuTokRepConfig.setTypeLimits(true, parseTypeList(settings.getAsList(ALLOW_TYPES_KEY)));
            typesConfigSeen++;
        }

        if (settings.get(DENY_TYPES_KEY) != null) {
            icuTokRepConfig.setTypeLimits(false, parseTypeList(settings.getAsList(DENY_TYPES_KEY)));
            typesConfigSeen++;
        }

        String typePreset = settings.get(TYPE_PRESET_KEY);
        if (typePreset != null) {
            enableTypePreset(typePreset);
            typesConfigSeen++;
        }

        if (typesConfigSeen > 1) {
            throw new SettingsException(SETTINGS_EXCP_PREFIX + "Only one of " + ALLOW_TYPES_KEY
                + ", " + DENY_TYPES_KEY + ", or " + TYPE_PRESET_KEY + " is allowed.");
        }
    }

    protected static Set<Integer> parseTypeList(@Nullable List<String> listOfTypeStrings)
            throws SettingsException {
        if (listOfTypeStrings == null || listOfTypeStrings.isEmpty()) {
            return emptySet();
        }

        ListIterator<String> iter = listOfTypeStrings.listIterator();
        Set<Integer> typeSet = new HashSet<>();

        while (iter.hasNext()) {
            int typeNum = TextifyUtils.getTokenType(iter.next().toUpperCase(Locale.ENGLISH));
            if (typeNum == -1) { // unknown or explicit "<OTHER>" == not allowed
                throw new SettingsException(SETTINGS_EXCP_PREFIX + "Token type " +
                    iter.previous() + " unknown or not allowed");
            }
            typeSet.add(typeNum);
        }

        return typeSet;
    }

    private void enableTypePreset(String typePreset) throws SettingsException {
        switch (typePreset.toLowerCase(Locale.ENGLISH)) {
            case "all": // create deny list, but deny nothing
                icuTokRepConfig.setNoTypeLimits();
                break;
            case "none": // create allow list, but allow nothing
                icuTokRepConfig.setTypeLimits(true, emptySet());
                break;
            case "default": // do nothing, default is enabled
                break;
            default:
                throw new SettingsException(SETTINGS_EXCP_PREFIX + "Unknown value for " +
                    TYPE_PRESET_KEY + ": " + typePreset);
        }
    }

    private void parseScriptLimits(Settings settings) throws SettingsException {
        int scriptConfigSeen = 0;

        if (settings.get(ALLOW_SCRIPTS_KEY) != null) {
            icuTokRepConfig.setScriptLimits(true,
                TextifyUtils.parseICUTokenRepairScriptList(settings.getAsList(ALLOW_SCRIPTS_KEY)));
            scriptConfigSeen++;
        }

        String scriptPreset = settings.get(SCRIPT_PRESET_KEY);
        if (scriptPreset != null) {
            enableScriptPreset(scriptPreset);
            scriptConfigSeen++;
        }

        if (scriptConfigSeen > 1) {
            throw new SettingsException(SETTINGS_EXCP_PREFIX + "Only one of " + ALLOW_SCRIPTS_KEY
                + " or " + SCRIPT_PRESET_KEY + " is allowed.");
        }
    }

    private void enableScriptPreset(String scriptPreset) throws SettingsException {
        switch (scriptPreset.toLowerCase(Locale.ENGLISH)) {
            case "all": // don't filter by scripts
                icuTokRepConfig.setNoScriptLimits();
                break;
            case "none": // do filter by scripts, but don't allow anything
                icuTokRepConfig.setScriptLimits("");
                break;
            case "default": // do nothing, default is enabled
                break;
            default:
                throw new SettingsException(SETTINGS_EXCP_PREFIX + "Unknown value for " +
                    SCRIPT_PRESET_KEY + ": " + scriptPreset);
        }
    }

    @Override public TokenStream create(TokenStream tokenStream) {
        return new ICUTokenRepairFilter(tokenStream, icuTokRepConfig);
    }

}
