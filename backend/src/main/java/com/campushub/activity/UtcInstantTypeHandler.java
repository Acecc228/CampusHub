package com.campushub.activity;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

// JDBC 4.2 avoids java.sql.Timestamp's hybrid Julian/Gregorian calendar for DATETIME values.
@MappedTypes(Instant.class)
@MappedJdbcTypes(value = JdbcType.TIMESTAMP, includeNullJdbcType = true)
public class UtcInstantTypeHandler extends BaseTypeHandler<Instant> {
    @Override public void setNonNullParameter(PreparedStatement statement, int index, Instant value, JdbcType type) throws SQLException {
        statement.setObject(index, LocalDateTime.ofInstant(value, ZoneOffset.UTC));
    }
    @Override public Instant getNullableResult(ResultSet result, String column) throws SQLException {
        return instant(result.getObject(column, LocalDateTime.class));
    }
    @Override public Instant getNullableResult(ResultSet result, int column) throws SQLException {
        return instant(result.getObject(column, LocalDateTime.class));
    }
    @Override public Instant getNullableResult(CallableStatement statement, int column) throws SQLException {
        return instant(statement.getObject(column, LocalDateTime.class));
    }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
