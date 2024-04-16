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
package com.redhat.ecosystemappeng.onguard.service.osv;

import java.util.Collections;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.redhat.ecosystemappeng.onguard.model.Vulnerability;
import com.redhat.ecosystemappeng.onguard.model.osv.PackageRef;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryRequest;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryRequestItem;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryResult;
import com.redhat.ecosystemappeng.onguard.model.osv.VulnerabilityRef;
import com.redhat.ecosystemappeng.onguard.repository.VulnerabilityRepository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OsvServiceImpl implements OsvService {

  @RestClient
  OsvApi osvApi;

  @Inject
  VulnerabilityRepository repository;

  @Override
  public Multi<List<String>> query(List<String> purls) {
    List<QueryRequestItem> queries = purls.stream().map(purl -> new QueryRequestItem(new PackageRef(purl))).toList();
    return osvApi.queryBatch(new QueryRequest(queries))
        .map(QueryResult::results)
        .onItem().ifNull().continueWith(Collections.emptyList())
        .onItem().transformToMulti(i -> Multi.createFrom().items(i.stream()))
        .onItem().transform(item -> {
          if (item.vulns() == null) {
            List<VulnerabilityRef> vulns = Collections.emptyList();
            return vulns;
          }
          return item.vulns();
        }).onItem().transform(refs -> refs.stream().map(VulnerabilityRef::id).toList());
  }

  @Override
  public Uni<Vulnerability> get(String alias) {
    return osvApi.getVuln(alias)
        .onItem().ifNotNull()
        .transformToUni(osvVuln -> {
          var cveId = osvVuln.id();
          var vuln = Uni.createFrom()
              .item(Vulnerability.builder().cveId(cveId).summary(osvVuln.summary()).description(osvVuln.details())
                  .affected(osvVuln.affected()).severities(osvVuln.severities()).build());
          if (osvVuln.aliases() != null && cveId != null) {
            return repository.setAliases(osvVuln.aliases(), cveId).replaceWith(vuln);
          }
          return vuln;
        });
  }

}
