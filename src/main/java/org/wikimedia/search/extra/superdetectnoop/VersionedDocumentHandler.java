package org.wikimedia.search.extra.superdetectnoop;

import static org.wikimedia.search.extra.superdetectnoop.ChangeHandler.TypeSafe.nullAndTypeSafe;

/**
 * Detects if the stored version of the document is newer than the provided
 * version, and noop's the entire update. Necessary because the elasticsearch
 * _update api, which is used to run super_detect_noop, does not support native
 * versioning.
 *
 * Only works properly with whole numbers up to 2^63-1
 */
public class VersionedDocumentHandler implements ChangeHandler<Number> {
    public static class Recognizer implements ChangeHandler.Recognizer {
        private static final String DESCRIPTION = "documentVersion";

        @Override
        public ChangeHandler<Object> build(String description) {
            if (!description.equals(DESCRIPTION)) {
                return null;
            }
            return INSTANCE;
        }
    }

    private static final ChangeHandler<Object> INSTANCE =
            nullAndTypeSafe(Number.class, new VersionedDocumentHandler());

    private VersionedDocumentHandler() {
        // Only a single instance is used
    }

    @Override
    public ChangeHandler.Result handle(Number oldVersion, Number newVersion) {
        return ChangeHandler.NoopDocument.forBoolean(oldVersion.longValue() > newVersion.longValue(), newVersion);
    }
}
