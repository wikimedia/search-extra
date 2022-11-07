/*
 * The WMF licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *** Source Information ***
 *
 * This code combines implementation details and linguistic information
 * from two main sources.
 *
 * ** Light Stemmer for Czech **
 *
 * The implementation is based on the lucene-solr "Light Stemmer for
 * Czech", which is licensed from ASF under the Apache License, Version
 * 2.0. Source code is available here:
 * https://github.com/apache/lucene-solr/blob/master/lucene/analysis/common/src/java/org/apache/lucene/analysis/cz/CzechStemmer.java
 *
 * ** stemm-sk **
 *
 * The Slovak-specific suffix information is adapted from stemm-sk, which
 * is Copyright (c) 2015 Marek Šuppa and licensed under the MIT
 * License (included below, as required). Source code is available here:
 * https://github.com/mrshu/stemm-sk/
 *
 * | Slovak-specific suffix information Copyright (c) 2015 Marek Šuppa
 * |
 * | Permission is hereby granted, free of charge, to any
 * | person obtaining a copy of this software and associated
 * | documentation files (the "Software"), to deal in the
 * | Software without restriction, including without limitation
 * | the rights to use, copy, modify, merge, publish,
 * | distribute, sublicense, and/or sell copies of the
 * | Software, and to permit persons to whom the Software is
 * | furnished to do so, subject to the following conditions:
 * |
 * | The above copyright notice and this permission notice
 * | shall be included in all copies or substantial portions of
 * | the Software.
 *
 * ** Additional Sources **
 *
 * The stemm-sk source code includes its own additional sources. The
 * Light Stemmer for Czech source code references the paper "Indexing
 * and stemming approaches for the Czech language" by Dolamic and Savoy
 * (2009), which is also the ultimate source of the main Czech
 * implementation that stemm-sk is based on. The paper is available
 * here: http://portal.acm.org/citation.cfm?id=1598600 .
 *
 * ** Additional Changes **
 *
 * - Updates to conform to findbugs/spotbugs/checkstyle errors.
 *
 * - Added prefix stripping based on review of Slovak morphology and
 * comparison to Polish.
 */

package org.wikimedia.search.extra.analysis.slovak;

import static org.apache.lucene.analysis.util.StemmerUtil.deleteN;
import static org.apache.lucene.analysis.util.StemmerUtil.endsWith;
import static org.apache.lucene.analysis.util.StemmerUtil.startsWith;


public class SlovakStemmer {

    /*
     * Stem an input buffer of Slovak text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     *
     * <p><b>NOTE</b>: Input is expected to be in lowercase,
     * but with diacritical marks</p>
     */
    public int stem(char[] s, int len) {
        len = removeCase(s, len);
        len = removePossessives(s, len);
        return removePrefixes(s, len);
    }

    private int removePrefixes(char[] s, int len) {
        if (len > 5 && startsWith(s, len, "naj")) {
            return deleteN(s, 0, len, 3);
        }
        return len;
    }

    @SuppressWarnings({"NPathComplexity", "CyclomaticComplexity"})
    private int removeCase(char[] s, int len) {
        if (len > 7 && endsWith(s, len, "atoch")) {
            return len - 5;
        }

        if (len > 6 && endsWith(s, len, "aťom")) {
            return palatalize(s, len - 3);
        }

        if (len > 5) {
            if (endsWith(s, len, "och") ||
                endsWith(s, len, "ich") ||
                endsWith(s, len, "ích") ||
                endsWith(s, len, "ého") ||
                endsWith(s, len, "ami") ||
                endsWith(s, len, "emi") ||
                endsWith(s, len, "ému") ||
                endsWith(s, len, "ete") ||
                endsWith(s, len, "eti") ||
                endsWith(s, len, "iho") ||
                endsWith(s, len, "ího") ||
                endsWith(s, len, "ími") ||
                endsWith(s, len, "imu") ||
                endsWith(s, len, "aťa")) {
                return palatalize(s, len - 2);
            }
            if (endsWith(s, len, "ách") ||
                endsWith(s, len, "ata") ||
                endsWith(s, len, "aty") ||
                endsWith(s, len, "ých") ||
                endsWith(s, len, "ové") ||
                endsWith(s, len, "ovi") ||
                endsWith(s, len, "ými")) {
                return len - 3;
            }
        }

        if (len > 4) {
            if (endsWith(s, len, "om")) {
                return palatalize(s, len - 1);
            }
            if (endsWith(s, len, "es") ||
                endsWith(s, len, "ém") ||
                endsWith(s, len, "ím")) {
                return palatalize(s, len - 2);
            }
            if (endsWith(s, len, "úm") ||
                endsWith(s, len, "at") ||
                endsWith(s, len, "ám") ||
                endsWith(s, len, "os") ||
                endsWith(s, len, "us") ||
                endsWith(s, len, "ým") ||
                endsWith(s, len, "mi") ||
                endsWith(s, len, "ou") ||
                endsWith(s, len, "ej")) {
                return len - 2;
            }
        }

        if (len > 3) {
            switch (s[len - 1]) {
            case 'e':
            case 'i':
            case 'í':
                return palatalize(s, len);
            case 'ú':
            case 'y':
            case 'a':
            case 'o':
            case 'á':
            case 'é':
            case 'ý':
                return len - 1;
            default:
            }
        }

        return len;
    }

    private int removePossessives(char[] s, int len) {
        if (len > 5) {
            if (endsWith(s, len, "ov")) {
                return len - 2;
            }
            if (endsWith(s, len, "in")) {
                return palatalize(s, len - 1);
            }
        }

        return len;
    }

    @SuppressWarnings({"CyclomaticComplexity"})
    private int palatalize(char[] s, int len) {
        assert len > 3;

        if (endsWith(s, len, "ci") ||
            endsWith(s, len, "ce") ||
            endsWith(s, len, "či") ||
            endsWith(s, len, "če")) { // [cč][ie] -> k
            s[len - 2] = 'k';
        } else if (endsWith(s, len, "zi") ||
            endsWith(s, len, "ze") ||
            endsWith(s, len, "ži") ||
            endsWith(s, len, "že")) { // [zž][ie] -> h
            s[len - 2] = 'h';
        } else if (endsWith(s, len, "čte") ||
            endsWith(s, len, "čti") ||
            endsWith(s, len, "čtí")) { // čt[eií] -> ck
            s[len - 3] = 'c';
            s[len - 2] = 'k';
        } else if (endsWith(s, len, "šte") ||
            endsWith(s, len, "šti") ||
            endsWith(s, len, "ští")) { // št[eií] -> sk
            s[len - 3] = 's';
            s[len - 2] = 'k';
        }

        return len - 1;
    }
}
