/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.ecosystemappeng.onguard.model.osv;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record Partition(List<String> items, int size) {
  
  public Stream<List<String>> stream() {
    List<List<String>> partitions = new ArrayList<>();
    if(items == null) {
      return Stream.empty();
    }
    int pos = 0;
    while(pos < items.size()) {
      var to = pos + size;
      if(to > items.size()) {
        to = items.size();
      }
      partitions.add(items.subList(pos, to));
      pos = to;
    }
    return partitions.stream();
  }
}
