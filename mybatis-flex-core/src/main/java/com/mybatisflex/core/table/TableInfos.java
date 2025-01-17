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
package com.mybatisflex.core.table;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.FlexConsts;
import com.mybatisflex.core.exception.FlexExceptions;
import com.mybatisflex.core.util.ClassUtil;
import com.mybatisflex.core.util.CollectionUtil;
import com.mybatisflex.core.util.StringUtil;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.util.MapUtil;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TableInfos {

    /**
     * 支持映射到数据库的数据类型
     */
    private static final Set<Class<?>> defaultSupportColumnTypes = CollectionUtil.newHashSet(
            int.class, Integer.class,
            short.class, Short.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            boolean.class, Boolean.class,
            Date.class, java.sql.Date.class, LocalDate.class, LocalDateTime.class, LocalTime.class,
            byte[].class, Byte[].class,
            BigInteger.class, BigDecimal.class,
            char.class, String.class
    );


    private static Map<Class<?>, TableInfo> tableInfoMap = new ConcurrentHashMap<>();


    public static TableInfo ofMapperClass(Class<?> mapperClass) {
        return MapUtil.computeIfAbsent(tableInfoMap, mapperClass, key -> {
            Class<?> entityClass = getEntityClass(key);
            return entityClass != null ? ofEntityClass(entityClass) : null;
        });
    }


    public static TableInfo ofEntityClass(Class<?> entityClass) {
        return MapUtil.computeIfAbsent(tableInfoMap, entityClass, key -> createTableInfo(entityClass));
    }


    private static Class<?> getEntityClass(Class<?> mapperClass) {
        Type[] genericInterfaces = mapperClass.getGenericInterfaces();
        if (genericInterfaces.length == 1) {
            Type type = genericInterfaces[0];
            if (type instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                return getEntityClass((Class<?>) type);
            }
        }

        return null;
    }


    private static TableInfo createTableInfo(Class<?> entityClass) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setEntityClass(entityClass);
        tableInfo.setReflector(new Reflector(entityClass));


        //初始化表名
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null) {
            tableInfo.setTableName(table.value());
            tableInfo.setSchema(table.schema());
            tableInfo.setUseCached(table.useCached());
            tableInfo.setCamelToUnderline(table.camelToUnderline());
        } else {
            //默认为类名转驼峰下划线
            tableInfo.setTableName(StringUtil.camelToUnderline(entityClass.getSimpleName()));
        }

        //初始化字段相关
        List<ColumnInfo> columnInfoList = new ArrayList<>();
        List<IdInfo> idInfos = new ArrayList<>();

        Field idField = null;

        String logicDeleteColumn = null;
        String versionColumn = null;

        //数据插入时，默认插入数据字段
        Map<String, String> onInsertColumns = new HashMap<>();

        //数据更新时，默认更新内容的字段
        Map<String, String> onUpdateColumns = new HashMap<>();

        //大字段列
        Set<String> largeColumns = new LinkedHashSet<>();


        List<Field> entityFields = ClassUtil.getAllFields(entityClass);
        for (Field field : entityFields) {

            //只支持基本数据类型，不支持比如 list set 或者自定义的类等
            if (!defaultSupportColumnTypes.contains(field.getType())) {
                continue;
            }

            Column column = field.getAnnotation(Column.class);
            if (column != null && column.ignore()) {
                continue; // ignore
            }

            //列名
            String columnName = column != null && StringUtil.isNotBlank(column.value())
                    ? column.value()
                    : (tableInfo.isCamelToUnderline() ? StringUtil.camelToUnderline(field.getName()) : field.getName());

            //逻辑删除字段
            if (column != null && column.isLogicDelete()) {
                if (logicDeleteColumn == null) {
                    logicDeleteColumn = columnName;
                } else {
                    throw FlexExceptions.wrap("The logic delete column of entity[%s] must be less then 2.", entityClass.getName());
                }
            }

            //乐观锁版本字段
            if (column != null && column.version()) {
                if (versionColumn == null) {
                    versionColumn = columnName;
                } else {
                    throw FlexExceptions.wrap("The version column of entity[%s] must be less then 2.", entityClass.getName());
                }
            }

            if (column != null && StringUtil.isNotBlank(column.onInsertValue())) {
                onInsertColumns.put(columnName, column.onInsertValue().trim());
            }


            if (column != null && StringUtil.isNotBlank(column.onUpdateValue())) {
                onUpdateColumns.put(columnName, column.onUpdateValue().trim());
            }


            if (column != null && column.isLarge()) {
                largeColumns.add(columnName);
            }


            Id id = field.getAnnotation(Id.class);
            ColumnInfo columnInfo;
            if (id != null) {
                columnInfo = new IdInfo(columnName, field.getName(), field.getType(), id);
                idInfos.add((IdInfo) columnInfo);
            } else {
                columnInfo = new ColumnInfo();
                columnInfoList.add(columnInfo);
            }

            columnInfo.setColumn(columnName);
            columnInfo.setProperty(field.getName());
            columnInfo.setPropertyType(field.getType());

            if (FlexConsts.DEFAULT_PRIMARY_FIELD.equals(field.getName())) {
                idField = field;
            }
        }


        if (idInfos.isEmpty() && idField != null) {
            int index = -1;
            for (int i = 0; i < columnInfoList.size(); i++) {
                ColumnInfo columnInfo = columnInfoList.get(i);
                if (FlexConsts.DEFAULT_PRIMARY_FIELD.equals(columnInfo.getProperty())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                ColumnInfo removedColumnInfo = columnInfoList.remove(index);
                idInfos.add(new IdInfo(removedColumnInfo));
            }
        }

        tableInfo.setLogicDeleteColumn(logicDeleteColumn);
        tableInfo.setVersionColumn(versionColumn);

        if (!onInsertColumns.isEmpty()) {
            tableInfo.setOnInsertColumns(onInsertColumns);
        }

        if (!onUpdateColumns.isEmpty()) {
            tableInfo.setOnUpdateColumns(onUpdateColumns);
        }

        if (!largeColumns.isEmpty()) {
            tableInfo.setLargeColumns(largeColumns.toArray(new String[0]));
        }

        tableInfo.setColumnInfoList(columnInfoList);
        tableInfo.setPrimaryKeyList(idInfos);


        return tableInfo;
    }
}
