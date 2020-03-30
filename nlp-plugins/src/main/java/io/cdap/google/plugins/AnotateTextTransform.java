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

import com.google.cloud.language.v1.AnnotateTextResponse;
import com.google.protobuf.MessageOrBuilder;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.google.common.NLPMethod;

/**
 * Provides all the features that
 * Analyze entities, Analyze entity sentiment, Analyze sentiment,
 * Analyze syntax, Classify text provide in one call.
 *
 * The json returned by API:
 *
 * {
 *    "sentences":[
 *       {
 *          "text":{
 *             "content":"A military is a heavily-armed, highly organised force primarily intended for warfare,
 *             also known collectively as armed forces.",
 *             "beginOffset":-1
 *          },
 *          "sentiment":{
 *             "magnitude":0.7,
 *             "score":0.7
 *          }
 *       },
 *       ...
 *    ],
 *    "tokens":[
 *       {
 *          "text":{
 *             "content":"A",
 *             "beginOffset":-1
 *          },
 *          "partOfSpeech":{
 *             "tag":"DET"
 *          },
 *          "dependencyEdge":{
 *             "headTokenIndex":1,
 *             "label":"DET"
 *          },
 *          "lemma":"A"
 *       },
 *       ...
 *    ],
 *    "entities":[
 *       {
 *          "name":"military",
 *          "type":"ORGANIZATION",
 *          "salience":0.43371573,
 *          "mentions":[
 *             {
 *                "text":{
 *                   "content":"military",
 *                   "beginOffset":-1
 *                },
 *                "type":"COMMON",
 *                "sentiment":{
 *                   "magnitude":0.3,
 *                   "score":-0.3
 *                }
 *             }
 *          ],
 *          "sentiment":{
 *             "magnitude":0.3,
 *             "score":-0.3
 *          }
 *       },
 *       ...
 *    ],
 *    "documentSentiment":{
 *       "magnitude":1.0,
 *       "score":0.5
 *    },
 *    "language":"en",
 *    "categories":[
 *       {
 *          "name":"/Law \u0026 Government/Military",
 *          "confidence":0.98
 *       }
 *    ]
 * }
 *
 * is translated into:
 *
 * { # record
 *    "sentences":[ # array
 *       {
 *          "content":"A military is a heavily-armed, highly organised force primarily intended for warfare,
 *          also known collectively as armed forces.",
 *          "beginOffset":-1,
 *          "magnitude":0.7,
 *          "score":0.7
 *       },
 *       ...
 *    ],
 *    "tokens":[ # array
 *       { # record
 *          "content":"A",
 *          "beginOffset":-1,
 *          "tag":"DET",
 *          "headTokenIndex":1,
 *          "label":"DET",
 *          "lemma":"A"
 *       },
 *       ...
 *    ],
 *    "entities":[ # array
 *       { # record
 *          "name":"military",
 *          "type":"ORGANIZATION",
 *          "salience":0.43371573,
 *          "mentions":[ # array
 *             { # record
 *                "content":"military",
 *                "beginOffset":-1,
 *                "type":"COMMON",
 *                "magnitude":0.3,
 *                "score":-0.3
 *             }
 *          ],
 *          "magnitude":0.3,
 *          "score":-0.3
 *       },
 *       ...
 *    ],
 *    "magnitude":1.0,
 *    "score":0.5,
 *    "language":"en",
 *    "categories":[ # array
 *       { # record
 *          "name":"/Law \u0026 Government/Military",
 *          "confidence":0.98
 *       },
 *       ...
 *    ]
 * }
 *
 */
@Plugin(type = Transform.PLUGIN_TYPE)
@Name("NLPAnotateText")
@Description("Provides all the features that Analyze entities, Analyze entity sentiment, " +
  "Analyze sentiment, Analyze syntax, Classify text provide in one call.")
public class AnotateTextTransform extends NLPTransform {
  private static final Schema SCHEMA =
    Schema.recordOf(AnotateTextTransform.class.getSimpleName(),
                    Schema.Field.of("language", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("score", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("magnitude", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("tokens", Schema.arrayOf(TOKEN)),
                    Schema.Field.of("sentences", Schema.arrayOf(SENTENCE_SCORED)),
                    Schema.Field.of("entities", Schema.arrayOf(ENTITY_SCORED)),
                    Schema.Field.of("categories", Schema.arrayOf(CATEGORY)));

  public AnotateTextTransform(NLPConfig config) {
    super(config);
  }

  @Override
  protected StructuredRecord getRecordFromResponse(MessageOrBuilder message) {
    AnnotateTextResponse response = (AnnotateTextResponse) message;

    StructuredRecord.Builder builder = StructuredRecord.builder(SCHEMA);

    builder.set("language", response.getLanguage());

    builder.set("score", response.getDocumentSentiment().getScore());
    builder.set("magnitude", response.getDocumentSentiment().getMagnitude());

    builder.set("categories", getCategories(response.getCategoriesList()));
    builder.set("sentences", getSentences(response.getSentencesList(), SENTENCE_SCORED));
    builder.set("tokens", getTokens(response.getTokensList()));
    builder.set("entities", getEntities(response.getEntitiesList(), ENTITY_SCORED, MENTION_SCORED));

    return builder.build();
  }

  protected NLPMethod getMethod() {
    return NLPMethod.ANOTATE_TEXT;
  }
}
