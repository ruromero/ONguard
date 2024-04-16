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

import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import com.redhat.ecosystemappeng.onguard.model.nvd.NvdResponse;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/rest/json/cves/2.0/")
@RegisterRestClient(configKey = "nvd-api")
@CircuitBreaker(delay = NvdApi.NVD_API_WINDOW_SECS, delayUnit = ChronoUnit.SECONDS)
public interface NvdApi {

  static final long NVD_API_WINDOW_SECS = 30;

  @GET
  @ClientHeaderParam(name = "apiKey", value = "${api.nvd.apikey}")
  @Produces(MediaType.APPLICATION_JSON)
  @Retry(delay = NVD_API_WINDOW_SECS, delayUnit = ChronoUnit.SECONDS)
  Uni<NvdResponse> list(
      @RestQuery("startIndex") Integer startIndex,
      @RestQuery("resultsPerPage") Integer resultsPerPage,
      @RestQuery("lastModStartDate") String lastModStartDate,
      @RestQuery("lastModEndDate") String lastModEndDate);
}
