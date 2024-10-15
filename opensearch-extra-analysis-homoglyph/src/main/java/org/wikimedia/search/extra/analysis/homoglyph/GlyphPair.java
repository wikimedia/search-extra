package org.wikimedia.search.extra.analysis.homoglyph;

public class GlyphPair {
    private final String original;
    private final String mirror;

    public GlyphPair(String original, String mirror) {
        this.original = original;
        this.mirror = mirror;
    }

    public static GlyphPair gp(String original, String mirror) {
        return new GlyphPair(original, mirror);
    }

    public String getOriginal() {
        return original;
    }

    public String getMirror() {
        return mirror;
    }

    public GlyphPair swap() {
        return new GlyphPair(mirror, original);
    }

    public String toString() {
        return "(" + original + "," + mirror + ")";
    }
}
