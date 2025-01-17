package com.mybatisflex.core.dialect;

import com.mybatisflex.core.querywrapper.QueryWrapper;

/**
 * limit 和 offset 参数的处理器
 */
public interface LimitOffsetProcesser {

    /**
     * MySql 的处理器
     * 适合 {@link DbType#MYSQL,DbType#MARIADB,DbType#H2,DbType#CLICK_HOUSE,DbType#XCloud}
     */
    LimitOffsetProcesser MYSQL = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            sql.append(" LIMIT ").append(limitOffset).append(", ").append(limitRows);
        } else if (limitRows != null) {
            sql.append(" LIMIT ").append(limitRows);
        }
        return sql;
    };

    /**
     * Postgresql 的处理器
     * 适合  {@link DbType#POSTGRE_SQL,DbType#SQLITE,DbType#H2,DbType#HSQL,DbType#KINGBASE_ES,DbType#PHOENIX}
     * 适合  {@link DbType#SAP_HANA,DbType#IMPALA,DbType#HIGH_GO,DbType#VERTICA,DbType#REDSHIFT}
     * 适合  {@link DbType#OPENGAUSS,DbType#TDENGINE,DbType#UXDB}
     */
    LimitOffsetProcesser POSTGRESQL = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            sql.append(" LIMIT ").append(limitOffset).append(" OFFSET ").append(limitRows);
        } else if (limitRows != null) {
            sql.append(" LIMIT ").append(limitRows);
        }
        return sql;
    };

    /**
     * derby 的处理器
     * 适合  {@link DbType#DERBY,DbType#ORACLE_12C,DbType#SQL_SERVER,DbType#POSTGRE_SQL}
     */
    LimitOffsetProcesser DERBY = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            // OFFSET ** ROWS FETCH NEXT ** ROWS ONLY")
            sql.append(" OFFSET ").append(limitOffset).append("  ROWS FETCH NEXT ").append(limitRows).append(" ROWS ONLY");
        } else if (limitRows != null) {
            // FETCH FIRST 20 ROWS ONLY
            sql.append(" FETCH FIRST ").append(limitRows).append(" ROWS ONLY");
        }
        return sql;
    };

    /**
     * db2 的处理器
     * 适合  {@link DbType#DB2,DbType#SQL_SERVER2005}
     */
    LimitOffsetProcesser DB2 = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            // OFFSET ** ROWS FETCH NEXT ** ROWS ONLY")
            sql.append(" OFFSET ").append(limitOffset).append("  ROWS FETCH NEXT ").append(limitRows).append(" ROWS ONLY");
        } else if (limitRows != null) {
            // FETCH FIRST 20 ROWS ONLY
            sql.append(" FETCH FIRST ").append(limitRows).append(" ROWS ONLY");
        }
        return sql;
    };

    /**
     * Informix 的处理器
     * 适合  {@link DbType#INFORMIX}
     * 文档 {@link <a href="https://www.ibm.com/docs/en/informix-servers/14.10?topic=clause-restricting-return-values-skip-limit-first-options">https://www.ibm.com/docs/en/informix-servers/14.10?topic=clause-restricting-return-values-skip-limit-first-options</a>}
     */
    LimitOffsetProcesser INFORMIX = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            // SELECT SKIP 2 FIRST 1 * FROM
            sql.insert(6, " SKIP " + limitOffset + " FIRST " + limitRows);
        } else if (limitRows != null) {
            sql.insert(6, " FIRST " + limitRows);
        }
        return sql;
    };

    /**
     * Firebird 的处理器
     * 适合  {@link DbType#FIREBIRD}
     */
    LimitOffsetProcesser FIREBIRD = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            // ROWS 2 TO 3
            sql.append(" ROWS ").append(limitOffset).append(" TO ").append(limitOffset + limitRows);
        } else if (limitRows != null) {
            sql.insert(6, " FIRST " + limitRows);
        }
        return sql;
    };

    /**
     * Oracle11g及以下数据库的处理器
     * 适合  {@link DbType#ORACLE,DbType#DM,DbType#GAUSS}
     */
    LimitOffsetProcesser ORACLE = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null) {
            if (limitOffset == null) {
                limitOffset = 0;
            }
            StringBuilder newSql = new StringBuilder("SELECT * FROM (SELECT TEMP_DATAS.*, ROWNUM RN FROM (");
            newSql.append(sql);
            newSql.append(") TEMP_DATAS WHERE  ROWNUM <=").append(limitOffset + limitRows).append(") WHERE RN >").append(limitOffset);
            return newSql;
        }
        return sql;
    };

    /**
     * Sybase 处理器
     * 适合  {@link DbType#SYBASE}
     */
    LimitOffsetProcesser SYBASE = (sql, queryWrapper, limitRows, limitOffset) -> {
        if (limitRows != null && limitOffset != null) {
            //SELECT TOP 1 START AT 3 * FROM
            sql.insert(6, " TOP " + limitRows + " START AT " + (limitRows + limitOffset));
        } else if (limitRows != null) {
            sql.insert(6, " TOP " + limitRows);
        }
        return sql;
    };


    /**
     * 处理构建 limit 和 offset
     *
     * @param sql          已经构建的 sql
     * @param queryWrapper 参数内容
     * @param limitRows    用户传入的 limit 参数 可能为 null
     * @param limitOffset  用户传入的 offset 参数，可能为 null
     */
    StringBuilder process(StringBuilder sql, QueryWrapper queryWrapper, Integer limitRows, Integer limitOffset);
}
