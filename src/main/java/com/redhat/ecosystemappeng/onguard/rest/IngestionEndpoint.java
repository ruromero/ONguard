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

import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.ecosystemappeng.onguard.service.IngestionService;

import io.quarkus.vertx.http.ManagementInterface;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
public class IngestionEndpoint {

  private static final Logger LOGGER = Logger.getLogger(IngestionEndpoint.class);

  @Inject
  IngestionService svc;

  @Inject
  ObjectMapper mapper;

  public void registerManagementRoutes(@Observes ManagementInterface mi) {
    mi.router().get("/admin/status").handler(ctx -> {
      svc.getStatus().subscribe().with(i -> {
        if(i == null) {
          ctx.response().setStatusCode(NOT_FOUND.getStatusCode());
          return;
        }
        try {
          ctx.response().setStatusCode(OK.getStatusCode()).putHeader("Content-Type", MediaType.APPLICATION_JSON)
              .end(mapper.writeValueAsString(i));
        } catch (JsonProcessingException e) {
          ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.getStatusCode()).end(e.getMessage());
        }
      });
    });

    mi.router().get("/admin/export/cves").handler(ctx -> {
      ctx.response().putHeader("Content-Type", MediaType.TEXT_PLAIN);
      StringBuffer buffer = new StringBuffer();
      svc.exportVulnerabilities().subscribe().with(v -> {
        if (v != null) {
          buffer.append(v);
        }
      }, f -> {
        LOGGER.error("Unable to export cves data", f);
        ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.getStatusCode()).send(f.getMessage());
      }, () -> {
        ctx.response().send(buffer.toString());
      });
    });

    mi.router().get("/admin/export/aliases").handler(ctx -> {
      ctx.response().putHeader("Content-Type", MediaType.TEXT_PLAIN);
      StringBuffer buffer = new StringBuffer();
      svc.exportAliases().subscribe().with(a -> buffer.append(a), f -> {
        LOGGER.error("Unable to export aliases data", f);
        ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.getStatusCode()).send(f.getMessage());
      }, () -> {
        ctx.response().send(buffer.toString());
      });
    });

    mi.router().get("/admin/export/ingestions").handler(ctx -> {
      ctx.response().putHeader("Content-Type", MediaType.TEXT_PLAIN);
      svc.exportIngestion().subscribe().with(a -> ctx.response().end(a), f -> {
        LOGGER.error("Unable to export ingestions data", f);
        ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.getStatusCode()).send(f.getMessage());
      });
    });

    mi.router().post("/admin/import").handler(ctx -> {
      ctx.request().bodyHandler(h -> svc.importScript(h.getBytes()).subscribe().with(
        v -> ctx.response().setStatusCode(ACCEPTED.getStatusCode()),
        f -> ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.getStatusCode())));
      
    });
  }

}
