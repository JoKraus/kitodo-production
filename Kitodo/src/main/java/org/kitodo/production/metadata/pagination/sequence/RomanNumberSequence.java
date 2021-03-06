/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.metadata.pagination.sequence;

import java.util.ArrayList;

import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyRomanNumeralHelper;

public class RomanNumberSequence extends ArrayList<String> {

    /**
     * Constructor.
     *
     * @param start
     *            as int
     * @param end
     *            as int
     * @param increment
     *            as int
     */
    public RomanNumberSequence(int start, int end, int increment) {
        generateElements(start, end, increment);
    }

    private void generateElements(int start, int end, int increment) {
        LegacyRomanNumeralHelper r = new LegacyRomanNumeralHelper();
        for (int i = start; i <= end; i = (i + increment)) {
            r.setValue(i);
            this.add(r.toString());
        }
    }

}
