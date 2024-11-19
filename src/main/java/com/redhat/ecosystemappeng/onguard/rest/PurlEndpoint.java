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
package com.redhat.ecosystemappeng.onguard.rest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.redhat.ecosystemappeng.onguard.model.PurlsRequest;
import com.redhat.ecosystemappeng.onguard.model.osv.OpenSourceVulnerability;
import com.redhat.ecosystemappeng.onguard.service.VulnerabilityService;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/purls")
public class PurlEndpoint {

  @Inject
  VulnerabilityService svc;

  @POST
  public Uni<Map<String, List<OpenSourceVulnerability>>> find(PurlsRequest request) {
    if (request == null || request.purls() == null || request.purls().isEmpty()) {
      return Uni.createFrom().item(Collections.emptyMap());
    }
    return svc.findByPurls(request.purls());
  }

  @POST
  @Path("/cves")
  public Uni<Map<String, List<String>>> findCves(PurlsRequest request) {
    if (request == null || request.purls() == null || request.purls().isEmpty()) {
      return Uni.createFrom().item(Collections.emptyMap());
    }
    return svc.findCvesByPurls(request.purls());
  }
}
