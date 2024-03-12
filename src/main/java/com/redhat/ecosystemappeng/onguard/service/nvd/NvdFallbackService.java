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

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.ecosystemappeng.onguard.model.Vulnerability;
import com.redhat.ecosystemappeng.onguard.model.nvd.NvdResponse;
import com.redhat.ecosystemappeng.onguard.repository.VulnerabilityRepository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class NvdFallbackService implements FallbackHandler<NvdResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(NvdFallbackService.class);

  @Inject
  VulnerabilityRepository repository;

  @Inject
  ManagedExecutor executor;

  @Inject
  NvdService nvdService;

  @ConfigProperty(name = "CircuitBreaker/delay", defaultValue = "30")
  Long delay;

  private void updateMetrics(String cveId) {
    var metrics = nvdService.getCveMetrics(cveId);
    if (metrics == null) {
      LOGGER.debug("Unable to retrieve metrics from NVD for CVE {}", cveId);
      return;
    }
    var vuln = repository.get(cveId);
    if (vuln != null) {
      var newVuln = Vulnerability.builder(vuln).metrics(metrics).lastModified(new Date()).build();
      repository.save(newVuln);
      repository.setAliases(List.of(cveId), cveId);
    }
  }

  @Override
  public NvdResponse handle(ExecutionContext context) {
    if (shouldHandle(context.getFailure())) {
      Uni.createFrom()
          .item((String) context.getParameters()[0])
          .onItem().delayIt().by(Duration.ofSeconds(delay)).invoke(cveId -> updateMetrics(cveId))
          .runSubscriptionOn(executor).subscribeAsCompletionStage();
    }
    return new NvdResponse(0, 0, 0, null, null, new Date(), Collections.emptyList());
  }

  private boolean shouldHandle(Throwable failure) {
    if (failure == null) {
      return true;
    }
    var cause = failure;
    if (failure.getCause() != null && !(failure instanceof WebApplicationException)) {
      cause = failure.getCause();
    }
    if (cause instanceof WebApplicationException) {
      var error = (WebApplicationException) cause;
      var status = error.getResponse().getStatus();
      return status != 404;
    }
    return true;
  }
}
