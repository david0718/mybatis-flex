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
package com.mybatisflex.core.util;

import java.util.Arrays;
import java.util.Objects;

public class ArrayUtil {


    /**
     * 判断数组是否为空
     *
     * @param array
     * @param <T>
     * @return 空 true
     */
    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length == 0;
    }


    /**
     * 判断数组是否不为空
     *
     * @param array
     * @param <T>
     * @return
     */
    public static <T> boolean isNotEmpty(T[] array) {
        return !isEmpty(array);
    }


    /**
     * 合并两个数组为一个新的数组
     *
     * @param first  第一个数组
     * @param second 第二个数组
     * @param <T>
     * @return 新的数组
     */
    public static <T> T[] concat(T[] first, T[] second) {
        if (first == null && second == null) {
            throw new IllegalArgumentException("not allow first and second are null.");
        } else if (isEmpty(first)) {
            return second;
        } else if (isEmpty(second)) {
            return first;
        } else {
            T[] result = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }
    }


    /**
     * 查看数组中是否包含某一个值
     *
     * @param arrays 数组
     * @param object 用于检测的值
     * @param <T>
     * @return true 包含
     */
    public static <T> boolean contains(T[] arrays, T object) {
        if (isEmpty(arrays)) {
            return false;
        }
        for (T array : arrays) {
            if (Objects.equals(array, object)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 急速构建数组
     */
    public static <T> T[] asArray(T... elements) {
        return elements;
    }


}
