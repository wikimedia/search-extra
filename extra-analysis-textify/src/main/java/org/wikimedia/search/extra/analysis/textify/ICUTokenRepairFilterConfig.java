package org.wikimedia.search.extra.analysis.textify;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

public final class ICUTokenRepairFilterConfig {

    static final int DEFAULT_MAX_TOK_LEN = 100;
    static final int MIN_MAX_TOK_LEN = 2;
    static final int MAX_MAX_TOK_LEN = 5000;
    static final Boolean DEFAULT_KEEP_CAMEL_SPLIT = Boolean.TRUE;
    static final Boolean DEFAULT_MERGE_NUM_ONLY = Boolean.FALSE;

    private static final Boolean DEFAULT_IS_TYPE_ALLOW_LIST = Boolean.FALSE;
    private static final Set<Integer> DEFAULT_TYPE_DENYLIST_SET =
        unmodifiableSet(new HashSet<>(Arrays.asList(
            TextifyUtils.TOKEN_TYPE_EMOJI,
            TextifyUtils.TOKEN_TYPE_IDEOGRAPHIC,
            TextifyUtils.TOKEN_TYPE_HANGUL
        )));

    private static final Boolean DEFAULT_FILTER_SCRIPTS = Boolean.TRUE;
    private static final List<String> DEFAULT_SCRIPT_GROUPS =
        Arrays.asList("Armenian+Coptic+Cyrillic+Greek+Latin", "Lao+Thai", "Latin+Tifinagh",
            "Cherokee+Latin", "Gothic+Latin", "Canadian_Aboriginal+Latin");

    protected int maxTokenLength = DEFAULT_MAX_TOK_LEN;
    protected boolean keepCamelSplit = DEFAULT_KEEP_CAMEL_SPLIT;
    protected boolean mergeNumOnly = DEFAULT_MERGE_NUM_ONLY;
    protected boolean isTypeAllowList = DEFAULT_IS_TYPE_ALLOW_LIST;
    protected Set<Integer> mergeableTypes = DEFAULT_TYPE_DENYLIST_SET;
    protected boolean filterScriptPairs = DEFAULT_FILTER_SCRIPTS;
    @Nullable protected Table<Integer, Integer, Boolean> mergeableScriptPairs;

    public ICUTokenRepairFilterConfig() {
        this(
            DEFAULT_MAX_TOK_LEN,
            DEFAULT_KEEP_CAMEL_SPLIT,
            DEFAULT_MERGE_NUM_ONLY,
            DEFAULT_IS_TYPE_ALLOW_LIST,
            DEFAULT_TYPE_DENYLIST_SET,
            DEFAULT_FILTER_SCRIPTS,
            TextifyUtils.parseICUTokenRepairScriptList(DEFAULT_SCRIPT_GROUPS)
        );
    }

    public ICUTokenRepairFilterConfig(int maxTokLen, boolean keepCamSpl, boolean mrgNumOnly,
            boolean isAllow, Set<Integer> typeSet, boolean filterScripts,
            @Nullable Table<Integer, Integer, Boolean> scriptPairs) {
        setMaxTokenLength(maxTokLen);
        setKeepCamelSplit(keepCamSpl);
        setMergeNumOnly(mrgNumOnly);
        setTypeLimits(isAllow, typeSet);
        setScriptLimits(filterScripts, scriptPairs);
    }

    public void setKeepCamelSplit(boolean keepCamSpl) {
        keepCamelSplit = keepCamSpl;
    }

    public void setMergeNumOnly(boolean mrgNumOnly) {
        mergeNumOnly = mrgNumOnly;
    }

    public void setMaxTokenLength(int maxTokLen) {
        if (maxTokLen < MIN_MAX_TOK_LEN || maxTokLen > MAX_MAX_TOK_LEN) {
            throw new IllegalArgumentException("ICU Token Repair invalid argument: maximum " +
                "token length must be between " + MIN_MAX_TOK_LEN + " and " + MAX_MAX_TOK_LEN);
        }
        maxTokenLength = maxTokLen;
    }

    public void setNoTypeLimits() {
        setTypeLimits(false, emptySet());
    }

    public void setTypeLimits(boolean isAllow, Set<Integer> typeSet) {
        isTypeAllowList = isAllow;
        mergeableTypes = unmodifiableSet(typeSet);
    }

    public void setNoScriptLimits() {
        filterScriptPairs = false;
        mergeableScriptPairs = null;
    }

    public void setScriptLimits(String scriptGroups) {
        setScriptLimits(true, TextifyUtils.parseICUTokenRepairScriptList(scriptGroups));
    }

    /* external input should be string-based, using parseICUTokenRepairScriptList */
    protected void setScriptLimits(boolean filterScripts,
            @Nullable Table<Integer, Integer, Boolean> scriptPairs) {
        filterScriptPairs = filterScripts;
        if (filterScriptPairs) {
            if (scriptPairs == null) {
                scriptPairs = HashBasedTable.create();
            }
            mergeableScriptPairs = ImmutableTable.copyOf(scriptPairs);
        } else {
            mergeableScriptPairs = null;
        }
    }

}
