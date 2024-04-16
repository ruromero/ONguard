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
package com.redhat.ecosystemappeng.onguard.service.nvd;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.redhat.ecosystemappeng.onguard.model.nvd.NvdResponse;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NvdServiceImpl implements NvdService {

  @RestClient
  NvdApi nvdApi;

  @Override
  public Uni<NvdResponse> bulkLoad(Integer index, Integer pageSize, LocalDateTime since) {
    if (since == null) {
      return nvdApi.list(index, pageSize, null, null);
    }
    return nvdApi.list(index, pageSize,
          DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(since.atZone(ZoneId.systemDefault())),
          DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(LocalDateTime.now().atZone(ZoneId.systemDefault())));
  }

}
