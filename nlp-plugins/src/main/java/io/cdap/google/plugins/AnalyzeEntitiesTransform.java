/*
 *  Copyright © 2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.google.plugins;

import com.google.cloud.language.v1.AnalyzeEntitiesResponse;
import com.google.protobuf.MessageOrBuilder;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.google.common.NLPMethod;

/**
 * Detects known entities like public figures or landmarks from a given text.
 *
 * The json returned by API:
 *
 * {
 *   "entities": [
 *     {
 *       "name": "1600 Pennsylvania Ave NW, Washington, DC",
 *       "type": "ADDRESS",
 *       "metadata": {
 *         "country": "US",
 *         "sublocality": "Fort Lesley J. McNair",
 *         "locality": "Washington",
 *         "street_name": "Pennsylvania Avenue Northwest",
 *         "broad_region": "District of Columbia",
 *         "narrow_region": "District of Columbia",
 *         "street_number": "1600"
 *       },
 *       "salience": 0,
 *       "mentions": [
 *         {
 *           "text": {
 *             "content": "1600 Pennsylvania Ave NW, Washington, DC",
 *             "beginOffset": 60
 *           },
 *           "type": "TYPE_UNKNOWN"
 *         }
 *       ]
 *       }
 *     }
 *     ...
 *   ],
 *   "language": "en"
 * }
 *
 * is translated into:
 *
 * { # record
 *   "entities": [ # array
 *     { # record
 *       "name": "1600 Pennsylvania Ave NW, Washington, DC",
 *       "type": "ADDRESS",
 *       "metadata": { # map<string,string>
 *         "country": "US",
 *         "sublocality": "Fort Lesley J. McNair",
 *         "locality": "Washington",
 *         "street_name": "Pennsylvania Avenue Northwest",
 *         "broad_region": "District of Columbia",
 *         "narrow_region": "District of Columbia",
 *         "street_number": "1600"
 *       },
 *       "salience": 0,
 *       "mentions": [ # array
 *         { # record
 *           "content": "1600 Pennsylvania Ave NW, Washington, DC",
 *           "beginOffset": 60
 *           "type": "TYPE_UNKNOWN"
 *         }
 *       ]
 *       }
 *     }
 *     ...
 *   ],
 *   "language": "en"
 * }
 */
@Plugin(type = Transform.PLUGIN_TYPE)
@Name("NLPAnalyzeEntities")
@Description("Detects known entities like public figures or landmarks from a given text.")
public class AnalyzeEntitiesTransform extends NLPTransform {
  private static final Schema MENTION =
    Schema.recordOf("mentionsRecord",
                    Schema.Field.of("content",
                                    Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("beginOffset",
                                    Schema.of(Schema.Type.INT)),
                    Schema.Field.of("type", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

  private static final Schema ENTITY =
    Schema.recordOf("entitiesRecord",
                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("type", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("metadata", Schema.nullableOf(Schema.mapOf(Schema.of(Schema.Type.STRING),
                                                                               Schema.of(Schema.Type.STRING)))),
                    Schema.Field.of("salience", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("mentions", Schema.arrayOf(MENTION)));

  private static final Schema SCHEMA =
    Schema.recordOf(AnalyzeEntitiesTransform.class.getSimpleName(),
                    Schema.Field.of("language", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("entities", Schema.arrayOf(ENTITY)));

  public AnalyzeEntitiesTransform(NLPConfig config) {
    super(config);
  }

  @Override
  protected StructuredRecord getRecordFromResponse(MessageOrBuilder message) {
    AnalyzeEntitiesResponse response = (AnalyzeEntitiesResponse) message;

    StructuredRecord.Builder builder = StructuredRecord.builder(SCHEMA);
    builder.set("language", response.getLanguage());
    builder.set("entities", getEntities(response.getEntitiesList(), ENTITY, MENTION));
    return builder.build();
  }

  protected NLPMethod getMethod() {
    return NLPMethod.ANALYZE_ENTITIES;
  }
}
