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

import com.google.cloud.language.v1.AnalyzeSyntaxResponse;
import com.google.protobuf.MessageOrBuilder;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.google.common.NLPMethod;

/**
 * For a given text, Google’s syntax analysis will return a breakdown of all words with a rich set of
 * linguistic information for each token.
 *
 * The json returned by API:
 *
 * {
 *   "sentences": [
 *     {
 *       "text": {
 *         "content": "Google, headquartered in Mountain View, unveiled the
 *         new Android phone at the Consumer Electronic Show.",
 *         "beginOffset": 0
 *       }
 *     },
 *     {
 *       "text": {
 *         "content": "Sundar Pichai said in his keynote that users love their new Android phones.",
 *         "beginOffset": 105
 *       }
 *     }
 *   ],
 *   "tokens": [
 *     {
 *       "text": {
 *         "content": "Google",
 *         "beginOffset": 0
 *       },
 *       "partOfSpeech": {
 *         "tag": "NOUN",
 *         "aspect": "ASPECT_UNKNOWN",
 *         "case": "CASE_UNKNOWN",
 *         "form": "FORM_UNKNOWN",
 *         "gender": "GENDER_UNKNOWN",
 *         "mood": "MOOD_UNKNOWN",
 *         "number": "SINGULAR",
 *         "person": "PERSON_UNKNOWN",
 *         "proper": "PROPER",
 *         "reciprocity": "RECIPROCITY_UNKNOWN",
 *         "tense": "TENSE_UNKNOWN",
 *         "voice": "VOICE_UNKNOWN"
 *       },
 *       "dependencyEdge": {
 *         "headTokenIndex": 7,
 *         "label": "NSUBJ"
 *       },
 *       "lemma": "Google"
 *     }
 *   ],
 *   "language": "en"
 * }
 *
 * is translated into:
 *
 * { # record
 *   "sentences": [ # array
 *     { # record.
 *       "content": "Google, headquartered in Mountain View, unveiled the new Android phone
 *       at the Consumer Electronic Show.",
 *       "beginOffset": 0
 *     },
 *     {
 *       "content": "Sundar Pichai said in his keynote that users love their new Android phones.",
 *       "beginOffset": 105
 *     }
 *   ],
 *   "tokens": [ # array
 *     { # record.
 *       "content": "Google",
 *       "beginOffset": 0
 *       "tag": "NOUN",
 *       "apect": "ASPECT_UNKNOWN",
 *       "case": "CASE_UNKNOWN",
 *       "speechForm": "FORM_UNKNOWN",
 *       "gender": "GENDER_UNKNOWN",
 *       "mood": "MOOD_UNKNOWN",
 *       "number": "SINGULAR",
 *       "person": "PERSON_UNKNOWN",
 *       "proper": "PROPER",
 *       "reciprocity": "RECIPROCITY_UNKNOWN",
 *       "tense": "TENSE_UNKNOWN",
 *       "voice": "VOICE_UNKNOWN"
 *       "dependencyEdgeHeadTokenIndex": 7,
 *       "dependencyEdgeLabel": "NSUBJ"
 *       "lemma": "Google"
 *     }
 *   ],
 *   "language": "en"
 * }
 */
@Plugin(type = Transform.PLUGIN_TYPE)
@Name("NLPAnalyzeSyntax")
@Description("For a given text, Google’s syntax analysis will return a breakdown of all words with a rich " +
  "set of linguistic information for each token")
public class AnalyzeSyntaxTransform extends NLPTransform {
  private static final Schema SENTENCE =
    Schema.recordOf("sentencesRecord",
                    Schema.Field.of("content",
                                    Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("beginOffset",
                                    Schema.of(Schema.Type.INT)));

  private static final Schema SCHEMA =
    Schema.recordOf(AnalyzeSyntaxTransform.class.getSimpleName(),
                    Schema.Field.of("language", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("sentences", Schema.arrayOf(SENTENCE)),
                    Schema.Field.of("tokens", Schema.arrayOf(TOKEN))
    );

  public AnalyzeSyntaxTransform(NLPConfig config) {
    super(config);
  }

  @Override
  protected StructuredRecord getRecordFromResponse(MessageOrBuilder message) {
    AnalyzeSyntaxResponse response = (AnalyzeSyntaxResponse) message;

    StructuredRecord.Builder builder = StructuredRecord.builder(SCHEMA);
    builder.set("language", response.getLanguage());

    builder.set("sentences", getSentences(response.getSentencesList(), SENTENCE));
    builder.set("tokens", getTokens(response.getTokensList()));

    return builder.build();
  }

  protected NLPMethod getMethod() {
    return NLPMethod.ANALYZE_SYNTAX;
  }
}
