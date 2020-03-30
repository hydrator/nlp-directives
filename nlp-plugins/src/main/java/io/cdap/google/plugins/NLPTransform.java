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

import com.google.cloud.language.v1.ClassificationCategory;
import com.google.cloud.language.v1.Entity;
import com.google.cloud.language.v1.EntityMention;
import com.google.cloud.language.v1.PartOfSpeech;
import com.google.cloud.language.v1.Sentence;
import com.google.cloud.language.v1.Token;
import com.google.protobuf.MessageOrBuilder;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.format.UnexpectedFormatException;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.InvalidEntry;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.google.common.NLPMethod;
import io.cdap.google.common.NLPMethodExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyses an input text via Google Language API. And returns the results of the analysis in form of a record.
 * All available Google NLP Methods are supported:
 * - Syntax Analysis
 * - Sentiment Analysis
 * - Entity Analysis
 * - Entity Sentiment Analysis
 * - Text Classification
 * - Anotate Text
 */
public abstract class NLPTransform extends Transform<StructuredRecord, StructuredRecord> {
  protected static final Schema MENTION_SCORED =
    Schema.recordOf("mentionsRecord",
                    Schema.Field.of("content",
                                    Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("beginOffset",
                                    Schema.of(Schema.Type.INT)),
                    Schema.Field.of("type",
                                    Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("magnitude",
                                    Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("score",
                                    Schema.nullableOf(Schema.of(Schema.Type.FLOAT))));

  protected static final Schema TOKEN =
    Schema.recordOf("tokensRecord",
                    Schema.Field.of("content", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("beginOffset", Schema.nullableOf(Schema.of(Schema.Type.INT))),
                    Schema.Field.of("tag", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("aspect", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("case", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("speechForm", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("gender", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("mood", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("number", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("person", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("proper", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("reciprocity", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("tense", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("voice", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                    Schema.Field.of("headTokenIndex", Schema.nullableOf(Schema.of(Schema.Type.INT))),
                    Schema.Field.of("label", Schema.nullableOf(Schema.of(Schema.Type.INT))),
                    Schema.Field.of("lemma", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

  protected static final Schema ENTITY_SCORED =
    Schema.recordOf("entitiesRecord",
                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("type", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("metadata", Schema.nullableOf(Schema.mapOf(Schema.of(Schema.Type.STRING),
                                                                               Schema.of(Schema.Type.STRING)))),
                    Schema.Field.of("salience", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("magnitude", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("score", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("mentions", Schema.arrayOf(MENTION_SCORED)));

  protected static final Schema SENTENCE_SCORED =
    Schema.recordOf("sentencesRecord",
                    Schema.Field.of("content", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("beginOffset", Schema.nullableOf(Schema.of(Schema.Type.INT))),
                    Schema.Field.of("score", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))),
                    Schema.Field.of("magnitude", Schema.nullableOf(Schema.of(Schema.Type.FLOAT))));

  protected static final Schema CATEGORY =
    Schema.recordOf("categoriesRecord",
                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                    Schema.Field.of("confidence", Schema.of(Schema.Type.FLOAT)));

  private static final String ERROR_SCHEMA_BODY_PROPERTY = "body";
  private static final Schema STRING_ERROR_SCHEMA = Schema.recordOf("stringError",
                                                                    Schema.Field.of(ERROR_SCHEMA_BODY_PROPERTY,
                                                                                    Schema.of(Schema.Type.STRING)));
  private final NLPConfig config;

  public NLPTransform(NLPConfig config) {
    this.config = config;
  }

  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    Schema inputSchema = pipelineConfigurer.getStageConfigurer().getInputSchema();
    FailureCollector failureCollector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    config.validate(failureCollector, inputSchema);
    failureCollector.getOrThrowException();
  }

  @Override
  public void transform(StructuredRecord structuredRecord, Emitter<StructuredRecord> emitter) {
    String text = structuredRecord.get(config.getSourceField());
    try (NLPMethodExecutor nlpMethodExecutor = getMethod().createExecutor(
      config.getServiceAccountFilePath(), config.getLanguageCode(), config.getEncodingType())) {
      try {
        emitter.emit(getRecordFromResponse(nlpMethodExecutor.getResponse(text)));
      } catch (Exception e) {
        switch (config.getErrorHandling()) {
          case SKIP:
            break;
          case SEND:
            StructuredRecord.Builder builder = StructuredRecord.builder(STRING_ERROR_SCHEMA);
            builder.set(ERROR_SCHEMA_BODY_PROPERTY, text);
            emitter.emitError(new InvalidEntry<>(400, e.getMessage(), builder.build()));
            break;
          case STOP:
            throw e;
          default:
            throw new UnexpectedFormatException(
              String.format("Unknown error handling strategy '%s'", config.getErrorHandling()));
        }
      }
    }
  }

  protected abstract NLPMethod getMethod();
  protected abstract StructuredRecord getRecordFromResponse(MessageOrBuilder message);

  protected static List<StructuredRecord> getEntities(List<Entity> entities, Schema entitySchema,
                                                      Schema mentionSchema) {
    List<StructuredRecord> entityRecords = new ArrayList<>();

    for (Entity entity : entities) {
      StructuredRecord.Builder entityBuilder = StructuredRecord.builder(entitySchema);
      entityBuilder.set("name", entity.getName());
      entityBuilder.set("type", entity.getType().toString());
      entityBuilder.set("metadata", entity.getMetadataMap());
      entityBuilder.set("salience", entity.getSalience());

      if (entitySchema.getField("magnitude") != null) {
        entityBuilder.set("magnitude", entity.getSentiment().getMagnitude());
        entityBuilder.set("score", entity.getSentiment().getScore());
      }

      List<StructuredRecord> mentions = new ArrayList<>();
      for (EntityMention entityMention : entity.getMentionsList()) {
        StructuredRecord.Builder mentionBuilder = StructuredRecord.builder(mentionSchema);
        mentionBuilder.set("content", entityMention.getText().getContent());
        mentionBuilder.set("beginOffset", entityMention.getText().getBeginOffset());
        mentionBuilder.set("type", entityMention.getType().toString());

        if (mentionSchema.getField("magnitude") != null) {
          mentionBuilder.set("magnitude", entityMention.getSentiment().getMagnitude());
          mentionBuilder.set("score", entityMention.getSentiment().getScore());
        }

        mentions.add(mentionBuilder.build());
      }
      entityBuilder.set("mentions", mentions);

      entityRecords.add(entityBuilder.build());
    }

    return entityRecords;
  }

  protected static List<StructuredRecord> getSentences(List<Sentence> sentences, Schema sentenceSchema) {
    List<StructuredRecord> sentenceRecords = new ArrayList<>();
    for (Sentence sentence : sentences) {
      StructuredRecord.Builder sentenceBuilder = StructuredRecord.builder(sentenceSchema);
      sentenceBuilder.set("content", sentence.getText().getContent());
      sentenceBuilder.set("beginOffset", sentence.getText().getBeginOffset());

      if (sentenceSchema.getField("magnitude") != null) {
        sentenceBuilder.set("magnitude", sentence.getSentiment().getMagnitude());
        sentenceBuilder.set("score", sentence.getSentiment().getScore());
      }

      sentenceRecords.add(sentenceBuilder.build());
    }
    return sentenceRecords;
  }

  protected static List<StructuredRecord> getCategories(List<ClassificationCategory> categories) {
    List<StructuredRecord> categoryRecords = new ArrayList<>();

    for (ClassificationCategory category : categories) {
      StructuredRecord.Builder categoryBuilder = StructuredRecord.builder(CATEGORY);
      categoryBuilder.set("name", category.getName());
      categoryBuilder.set("confidence", category.getConfidence());

      categoryRecords.add(categoryBuilder.build());
    }

    return categoryRecords;
  }

  protected static List<StructuredRecord> getTokens(List<Token> tokens) {
    List<StructuredRecord> tokenRecords = new ArrayList<>();
    for (Token token : tokens) {
      StructuredRecord.Builder tokenBuilder = StructuredRecord.builder(TOKEN);
      tokenBuilder.set("content", token.getText().getContent());
      tokenBuilder.set("beginOffset", token.getText().getBeginOffset());

      PartOfSpeech partOfSpeech = token.getPartOfSpeech();
      tokenBuilder.set("tag", partOfSpeech.getTag().toString());
      tokenBuilder.set("aspect", partOfSpeech.getAspect().toString());
      tokenBuilder.set("case", partOfSpeech.getCase().toString());
      tokenBuilder.set("speechForm", partOfSpeech.getForm().toString());
      tokenBuilder.set("gender", partOfSpeech.getGender().toString());
      tokenBuilder.set("mood", partOfSpeech.getMood().toString());
      tokenBuilder.set("number", partOfSpeech.getNumber().toString());
      tokenBuilder.set("person", partOfSpeech.getPerson().toString());
      tokenBuilder.set("proper", partOfSpeech.getProper().toString());
      tokenBuilder.set("reciprocity", partOfSpeech.getReciprocity().toString());
      tokenBuilder.set("tense", partOfSpeech.getTense().toString());
      tokenBuilder.set("voice", partOfSpeech.getVoice().toString());

      tokenBuilder.set("headTokenIndex", token.getDependencyEdge().getHeadTokenIndex());
      tokenBuilder.set("label", token.getDependencyEdge().getLabelValue());

      tokenBuilder.set("lemma", token.getLemma());

      tokenRecords.add(tokenBuilder.build());
    }
    return tokenRecords;
  }
}
