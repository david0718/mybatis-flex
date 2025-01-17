/**
 * Copyright (c) 2022-2023, Mybatis-Flex (fuhai999@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mybatisflex.core.mybatis;

import com.mybatisflex.core.FlexConsts;
import com.mybatisflex.core.key.MultiEntityKeyGenerator;
import com.mybatisflex.core.key.MultiRowKeyGenerator;
import com.mybatisflex.core.key.MybatisKeyGeneratorUtil;
import com.mybatisflex.core.row.RowMapper;
import com.mybatisflex.core.key.RowKeyGenerator;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfos;
import com.mybatisflex.core.util.CollectionUtil;
import com.mybatisflex.core.util.StringUtil;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Map;

public class FlexConfiguration extends Configuration {


    public FlexConfiguration(Environment environment) {
        super(environment);
        setMapUnderscoreToCamelCase(true);
        setLogImpl(StdOutImpl.class);
        initDefaultMappers();
    }

    public FlexConfiguration() {
        setLogImpl(StdOutImpl.class);
        initDefaultMappers();
    }

    /**
     * 设置 mybatis-flex 默认的 Mapper
     * 当前只有 RowMapper {@link RowMapper}
     */
    private void initDefaultMappers() {
        addMapper(RowMapper.class);
    }


    /**
     * 为原生 sql 设置参数
     */
    @Override
    public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        String mappedStatementId = mappedStatement.getId();
        // 以 "!selectKey" 结尾的 mappedStatementId，是用于主键生成的，无需为其设置参数
        if (!mappedStatementId.endsWith(SelectKeyGenerator.SELECT_KEY_SUFFIX)
                && parameterObject instanceof Map
                && ((Map<?, ?>) parameterObject).containsKey(FlexConsts.SQL_ARGS)) {
            return new SqlArgsParameterHandler(mappedStatement, (Map) parameterObject, boundSql);
        } else {
            return super.newParameterHandler(mappedStatement, parameterObject, boundSql);
        }
    }

    /**
     * 替换为 FlexRoutingStatementHandler，主要用来为实体类的多主键做支持
     * FlexRoutingStatementHandler 和 原生的 RoutingStatementHandler 对比，没有任何性能影响
     */
    @Override
    public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        StatementHandler statementHandler = new FlexRoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
        statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
        return statementHandler;
    }

    @Override
    public void addMappedStatement(MappedStatement ms) {
        //替换 RowMapper.insertRow 的主键生成器
        //替换 RowMapper.insertBatchWithFirstRowColumns 的主键生成器
        if (ms.getId().startsWith("com.mybatisflex.core.row.RowMapper.insert")) {
            ms = replaceRowKeyGenerator(ms);
        }
        //entity insert methods
        else if (StringUtil.endsWithAny(ms.getId(), "insert", FlexConsts.METHOD_INSERT_BATCH)
                && ms.getKeyGenerator() == NoKeyGenerator.INSTANCE) {
            ms = replaceEntityKeyGenerator(ms);
        }
        //entity select
        else if (StringUtil.endsWithAny(ms.getId(), "selectOneById", "selectListByIds"
                , "selectListByQuery", "selectCountByQuery")) {
            ms = replaceResultHandler(ms);
        }

        super.addMappedStatement(ms);
    }


    /**
     * 替换 entity 查询的 ResultHandler
     */
    private MappedStatement replaceResultHandler(MappedStatement ms) {

        TableInfo tableInfo = getTableInfo(ms);
        if (tableInfo == null) {
            return ms;
        }

        String resultMapId = tableInfo.getEntityClass().getName();

        ResultMap resultMap;
        if (hasResultMap(resultMapId)) {
            resultMap = getResultMap(resultMapId);
        } else {
            resultMap = tableInfo.buildResultMap(this);
            this.addResultMap(resultMap);
        }

        return new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), ms.getSqlSource(), ms.getSqlCommandType())
                .resource(ms.getResource())
                .fetchSize(ms.getFetchSize())
                .timeout(ms.getTimeout())
                .statementType(ms.getStatementType())
                .keyGenerator(NoKeyGenerator.INSTANCE)
                .keyProperty(ms.getKeyProperties() == null ? null : String.join(",", ms.getKeyProperties()))
                .keyColumn(ms.getKeyColumns() == null ? null : String.join(",", ms.getKeyColumns()))
                .databaseId(databaseId)
                .lang(ms.getLang())
                .resultOrdered(ms.isResultOrdered())
                .resultSets(ms.getResultSets() == null ? null : String.join(",", ms.getResultSets()))
                .resultMaps(CollectionUtil.newArrayList(resultMap)) // 替换resultMap
                .resultSetType(ms.getResultSetType())
                .flushCacheRequired(ms.isFlushCacheRequired())
                .useCache(ms.isUseCache())
                .cache(ms.getCache())
                .build();
    }

    /**
     * 生成新的、已替换主键生成器的 MappedStatement
     *
     * @param ms MappedStatement
     * @return replaced MappedStatement
     */
    private MappedStatement replaceRowKeyGenerator(MappedStatement ms) {


        KeyGenerator keyGenerator = new RowKeyGenerator(ms);
        if (ms.getId().endsWith("insertBatchWithFirstRowColumns")) {
            keyGenerator = new MultiRowKeyGenerator(keyGenerator);
        }

        return new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), ms.getSqlSource(), ms.getSqlCommandType())
                .resource(ms.getResource())
                .fetchSize(ms.getFetchSize())
                .timeout(ms.getTimeout())
                .statementType(ms.getStatementType())
                .keyGenerator(keyGenerator) // 替换主键生成器
                .keyProperty(ms.getKeyProperties() == null ? null : String.join(",", ms.getKeyProperties()))
                .keyColumn(ms.getKeyColumns() == null ? null : String.join(",", ms.getKeyColumns()))
                .databaseId(databaseId)
                .lang(ms.getLang())
                .resultOrdered(ms.isResultOrdered())
                .resultSets(ms.getResultSets() == null ? null : String.join(",", ms.getResultSets()))
                .resultMaps(ms.getResultMaps())
                .resultSetType(ms.getResultSetType())
                .flushCacheRequired(ms.isFlushCacheRequired())
                .useCache(ms.isUseCache())
                .cache(ms.getCache())
                .build();
    }

    /**
     * 生成新的、已替换主键生成器的 MappedStatement
     *
     * @param ms MappedStatement
     * @return replaced MappedStatement
     */
    private MappedStatement replaceEntityKeyGenerator(MappedStatement ms) {

        TableInfo tableInfo = getTableInfo(ms);
        if (tableInfo == null) {
            return ms;
        }

        KeyGenerator keyGenerator = MybatisKeyGeneratorUtil.createTableKeyGenerator(tableInfo, ms);
        if (keyGenerator == NoKeyGenerator.INSTANCE) {
            return ms;
        }

        //批量插入
        if (ms.getId().endsWith(FlexConsts.METHOD_INSERT_BATCH)) {
            keyGenerator = new MultiEntityKeyGenerator(keyGenerator);
        }

        return new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), ms.getSqlSource(), ms.getSqlCommandType())
                .resource(ms.getResource())
                .fetchSize(ms.getFetchSize())
                .timeout(ms.getTimeout())
                .statementType(ms.getStatementType())
                .keyGenerator(keyGenerator) // 替换主键生成器
                .keyProperty(tableInfo.getMappedStatementKeyProperties())
                .keyColumn(tableInfo.getMappedStatementKeyColumns())
                .databaseId(databaseId)
                .lang(ms.getLang())
                .resultOrdered(ms.isResultOrdered())
                .resultSets(ms.getResultSets() == null ? null : String.join(",", ms.getResultSets()))
                .resultMaps(ms.getResultMaps())
                .resultSetType(ms.getResultSetType())
                .flushCacheRequired(ms.isFlushCacheRequired())
                .useCache(ms.isUseCache())
                .cache(ms.getCache())
                .build();
    }

    private TableInfo getTableInfo(MappedStatement ms) {
        String mapperClassName = ms.getId().substring(0, ms.getId().lastIndexOf("."));
        try {
            Class<?> mapperClass = Class.forName(mapperClassName);
            return TableInfos.ofMapperClass(mapperClass);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }


}
