/**
 * A local fork of OpenSearch's JSON log layout.
 *
 * <p>{@link org.wikimedia.search.extra.logging.SingleLineJsonLayout} is a drop-in replacement for
 * {@code org.opensearch.common.logging.OpenSearchJsonLayout} that guarantees every log record is a
 * single line of valid JSON. It exists to work around
 * <a href="https://github.com/opensearch-project/OpenSearch/issues/4673">opensearch-project/OpenSearch#4673</a>,
 * where the stock layout injects raw newlines into the {@code stacktrace} field, breaking line-based
 * log agents.
 */
@javax.annotation.ParametersAreNonnullByDefault
@org.wikimedia.search.extra.util.FieldsAreNonNullByDefault
@org.wikimedia.search.extra.util.ReturnTypesAreNonNullByDefault
package org.wikimedia.search.extra.logging;
