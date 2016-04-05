package org.wikimedia.search.extra.idhashmod;

import java.io.IOException;
import java.util.Locale;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;

/**
 * Parses the {@link IdHashModFilter}.
 */
public class IdHashModFilterParser implements QueryParser {
    public static final String[] NAMES = new String[] { "id_hash_mod", "id-hash-mod", "idHashMod" };

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        Integer mod = null;
        Integer match = null;
        XContentParser parser = parseContext.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                switch (currentFieldName) {
                case "mod":
                    mod = parser.intValue();
                    break;
                case "match":
                    match = parser.intValue();
                    break;
                default:
                    throw new QueryParsingException(parseContext, "[id-mod-hash] filter does not support [" + currentFieldName
                            + "]");
                }
            }
        }

        if (mod == null) {
            throw new QueryParsingException(parseContext, "[id-mod-hash] filter requires the \"mod\" parameter");
        }
        if (mod < 0) {
            throw new QueryParsingException(parseContext, "[id-mod-hash] \"mod\" must be positive");
        }
        if (match == null) {
            throw new QueryParsingException(parseContext, "[id-mod-hash] filter requires the \"match\" parameter");
        }
        if (match < 0) {
            throw new QueryParsingException(parseContext, "[id-mod-hash] \"match\" must be positive");
        }
        if (match >= mod) {
            throw new QueryParsingException(parseContext, String.format(Locale.ROOT,
                    "If match is >= mod it won't find anything. match = %s and mod = %s", match, mod));
        }
        IndexFieldData<?> uidFieldData = parseContext.getForField(parseContext.fieldMapper("_uid"));
        return new IdHashModFilter(uidFieldData, mod, match);
    }
}
