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
package com.redhat.ecosystemappeng.onguard.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.redhat.ecosystemappeng.onguard.test.WireMockExtensions.NVD_API_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.ecosystemappeng.onguard.service.nvd.NvdApi;
import com.redhat.ecosystemappeng.onguard.test.InjectWireMock;
import com.redhat.ecosystemappeng.onguard.test.WireMockExtensions;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class NvdApiTest {

  @RestClient
  NvdApi nvdApi;

  @InjectWireMock
  WireMockServer server;

  static {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @Test
  void testList() {
    var response = nvdApi.list(0, 200, "start_date", "end_date").subscribe()
        .withSubscriber(UniAssertSubscriber.create()).awaitItem().assertCompleted().getItem();
    assertEquals(1, response.totalResults());
    server.verify(1, getRequestedFor(urlPathEqualTo(NVD_API_PATH))
        .withQueryParam("startIndex", equalTo("0")));
  }

  @Test
  void testListWithRetry() {
    var response = nvdApi.list(10, 200, "start_date", "end_date").subscribe()
        .withSubscriber(UniAssertSubscriber.create()).awaitItem().assertCompleted().getItem();

    assertEquals(1, response.totalResults());
    server.verify(2, getRequestedFor(urlPathEqualTo(NVD_API_PATH))
        .withQueryParam("startIndex", equalTo("10")));
  }

}
