/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.api.impl;

import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.base.Errors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListMapTypeSupport extends TypeSupport<LinkedHashMap> {

  @Override
  @SuppressWarnings("unchecked")
  public LinkedHashMap convert(Object value) {
    if (value instanceof LinkedHashMap) {
      return (LinkedHashMap) value;
    } else if(value instanceof List) {
      List list = (List) value;
      LinkedHashMap<String, Field> listMap = new LinkedHashMap<>(list.size());
      for (int i = 0; i < list.size(); i++) {
        listMap.put(i + "", (Field)list.get(i));
      }
      return listMap;
    } else if(value instanceof Map) {
      Map map = (Map) value;
      return new LinkedHashMap(map);
    }
    throw new IllegalArgumentException(Utils.format(Errors.API_21.getMessage(),
      value.getClass().getSimpleName(), value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object convert(Object value, TypeSupport targetTypeSupport) {
    if (targetTypeSupport instanceof ListMapTypeSupport || targetTypeSupport instanceof MapTypeSupport) {
      return value;
    } if (targetTypeSupport instanceof ListTypeSupport && value instanceof LinkedHashMap) {
      LinkedHashMap listMap = (LinkedHashMap) value;
      return new ArrayList<>(listMap.values());
    } else {
      throw new IllegalArgumentException(Utils.format(Errors.API_22.getMessage(), targetTypeSupport));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object clone(Object value) {
    LinkedHashMap<String, Field> listMap = null;
    if (value != null) {
      listMap = deepCopy((LinkedHashMap<String, Field>)value);
    }
    return listMap;
  }

  private LinkedHashMap<String, Field> deepCopy(LinkedHashMap<String, Field> listMap) {
    LinkedHashMap<String, Field> copy = new LinkedHashMap<>(listMap.size());
    for (String key: listMap.keySet()) {
      Field field = listMap.get(key);
      Utils.checkNotNull(field, Utils.formatL("ListMap has null element at '{}' pos", key));
      copy.put(key, field.clone());
    }
    return copy;
  }

  @Override
  public Object create(Object value) {
    return clone(value);
  }

}