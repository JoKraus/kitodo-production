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

package org.kitodo.data.elasticsearch.index.type;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.kitodo.data.database.beans.Filter;

/**
 * Type class for Filter bean.
 */
public class FilterType extends BaseType<Filter> {

    @Override
    public HttpEntity createDocument(Filter filter) {
        Integer user = filter.getUser() != null ? filter.getUser().getId() : 0;

        JsonObject propertyObject = Json.createObjectBuilder()
                .add("value", preventNull(filter.getValue()))
                .add("user", user)
                .build();

        return new NStringEntity(propertyObject.toString(), ContentType.APPLICATION_JSON);
    }
}
