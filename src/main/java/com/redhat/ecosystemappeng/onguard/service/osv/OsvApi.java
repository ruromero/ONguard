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

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.redhat.ecosystemappeng.onguard.model.osv.OsvVulnerability;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryRequest;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryResult;

import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/v1")
@RegisterRestClient(configKey = "osv-api")
public interface OsvApi {
    
    @GET
    @Path("/vulns/{vulnId}")
    Uni<OsvVulnerability> getVuln(@PathParam("vulnId") String vulnId);

    @POST
    @Path("/querybatch")
    @CacheResult(cacheName = "osvQueryBatch")
    Uni<QueryResult> queryBatch(QueryRequest request);

}
