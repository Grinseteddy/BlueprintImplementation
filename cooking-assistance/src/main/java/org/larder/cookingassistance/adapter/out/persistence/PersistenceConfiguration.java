package org.larder.cookingassistance.adapter.out.persistence;

import java.sql.SQLException;
import java.util.List;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

/** Spring Data JDBC setup of the module: jsonb conversion for answers. */
@Configuration
class PersistenceConfiguration extends AbstractJdbcConfiguration {

  @Override
  protected List<?> userConverters() {
    return List.of(new AnswerJsonToJsonb(), new JsonbToAnswerJson());
  }

  @WritingConverter
  static final class AnswerJsonToJsonb implements Converter<AnswerJson, PGobject> {
    @Override
    public PGobject convert(AnswerJson answer) {
      PGobject jsonb = new PGobject();
      jsonb.setType("jsonb");
      try {
        jsonb.setValue(answer.value());
      } catch (SQLException e) {
        throw new IllegalArgumentException("Answer is not valid JSON", e);
      }
      return jsonb;
    }
  }

  @ReadingConverter
  static final class JsonbToAnswerJson implements Converter<PGobject, AnswerJson> {
    @Override
    public AnswerJson convert(PGobject jsonb) {
      return new AnswerJson(jsonb.getValue());
    }
  }
}
