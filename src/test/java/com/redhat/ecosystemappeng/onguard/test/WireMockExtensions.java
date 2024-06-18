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
package com.redhat.ecosystemappeng.onguard.test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.ecosystemappeng.onguard.model.osv.PackageRef;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryRequest;
import com.redhat.ecosystemappeng.onguard.model.osv.QueryRequestItem;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import jakarta.ws.rs.core.MediaType;

public class WireMockExtensions implements QuarkusTestResourceLifecycleManager {

  public static final String CONTENT_TYPE = "Content-Type";

  public static final String OSV_QUERY_API_PATH = "/v1/querybatch";
  public static final String OSV_VULN_API_PATH = "/v1/vulns/{id}";

  public static final String PURL_WITH_VULNS = "pkg:maven/io.smallrye.config/smallrye-config@3.3.2.redhat-00001?repository_url=https%3A%2F%2Fmaven.repository.redhat.com%2Fga%2F&type=jar";
  public static final String PURL_WITH_ERROR = "pkg:maven/com.example/error@0.0.0?type=jar";

  private final WireMockServer server = new WireMockServer(options().dynamicPort());

  @Override
  public Map<String, String> start() {
    server.start();

    stubOsv();

    Map<String, String> props = new HashMap<>();
    props.put("quarkus.rest-client.osv-api.url", server.baseUrl());
    return props;
  }

  private void stubOsv() {
    var mapper = new ObjectMapper();
    var reqWithVulns = new QueryRequest(List.of(new QueryRequestItem(new PackageRef(PURL_WITH_VULNS))));
    var reqWithError = new QueryRequest(List.of(new QueryRequestItem(new PackageRef(PURL_WITH_ERROR))));

    try {
      server.stubFor(post(urlPathEqualTo(OSV_QUERY_API_PATH))
          .willReturn(ok().withBodyFile("osv-data/empty.json")
              .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)));

      server.stubFor(post(urlPathEqualTo(OSV_QUERY_API_PATH))
          .withRequestBody(equalToJson(mapper.writeValueAsString(reqWithVulns)))
          .willReturn(ok().withBodyFile("osv-data/vulnerability.json")
              .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)));

      server.stubFor(post(urlPathEqualTo(OSV_QUERY_API_PATH))
          .withRequestBody(equalToJson(mapper.writeValueAsString(reqWithError))).willReturn(serverError()));

    } catch (JsonProcessingException e) {
      fail(e);
    }

  }

  @Override
  public void stop() {
    if (server != null) {
      server.stop();
    }
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(server,
        new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
  }
}
