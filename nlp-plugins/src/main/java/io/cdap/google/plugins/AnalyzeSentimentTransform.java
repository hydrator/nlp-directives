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

import com.google.cloud.language.v1.AnalyzeSentimentResponse;
import com.google.protobuf.MessageOrBuilder;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.google.common.NLPMethod;

/**
 * Provides the prevailing emotional opinion within a provided text. The API returns two values: The score describes
 * the emotional leaning of the text from -1 (negative) to +1 (positive), with 0 being neutral.
 *
 * The magnitude measures the strength of the emotion.
 *
 * The json returned by API:
 * {
 *   "documentSentiment": {
 *     "magnitude": 0.8,
 *     "score": 0.8
 *   },
 *   "language": "en",
 *   "sentences": [
 *     {
 *       "text": {
 *         "content": "Enjoy your vacation!",
 *         "beginOffset": 0
 *       },
 *       "sentiment": {
 *         "magnitude": 0.8,
 *         "score": 0.8
 *       }
 *     }
 *   ]
 * }
 *
 * is translated into:
 *
 * { # record
 *   "magnitude": 0.8,
 *   "score": 0.8
 *   "language": "en",
 *   "sentences": [ # array
 *     { # record
 *       "content": "Enjoy your vacation!",
 *       "beginOffset": 0
 *       "magnitude": 0.8,
 *       "score": 0.8
 *     }
 *   ]
 * }
 */
@Plugin(type = Transform.PLUGIN_TYPE)
@Name("NLPAnalyzeSentiment")
@Description("Provides the prevailing emotional opinion within a provided text. The API returns two values: The " +
  "\"score\" describes the emotional leaning of the text from -1 (negative) to +1 (positive), with 0 being neutral.\n" +
  "The \"magnitude\" measures the strength of the emotion.")
public class AnalyzeSentimentTransform extends NLPTransform {
  private static final Schema SCHEMA =
    Schema.recordOf(AnalyzeSentimentTransform.class.getSimpleName(),
                    Schema.Field.of("language", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("score", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("magnitude", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("sentences", Schema.arrayOf(SENTENCE_SCORED)));

  public AnalyzeSentimentTransform(NLPConfig config) {
    super(config);
  }

  @Override
  protected StructuredRecord getRecordFromResponse(MessageOrBuilder message) {
    AnalyzeSentimentResponse response = (AnalyzeSentimentResponse) message;

    StructuredRecord.Builder builder = StructuredRecord.builder(SCHEMA);
    builder.set("language", response.getLanguage());

    builder.set("score", response.getDocumentSentiment().getScore());
    builder.set("magnitude", response.getDocumentSentiment().getMagnitude());

    builder.set("sentences", getSentences(response.getSentencesList(), SENTENCE_SCORED));

    return builder.build();
  }

  protected NLPMethod getMethod() {
    return NLPMethod.ANALYZE_SENTIMENT;
  }
}
