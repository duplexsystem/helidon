/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.examples.openapi;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonPointer;
import javax.json.JsonString;

import io.helidon.common.http.MediaType;
import io.helidon.examples.openapi.internal.SimpleAPIModelReader;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT;

    static {
        TEST_JSON_OBJECT = JSON_BF.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testHelloWorld() {
        webClient.get()
                .path("/greet")
                .request(JsonObject.class)
                .thenAccept(jsonObject -> Assertions.assertEquals("Hello World!", jsonObject.getString("greeting")))
                .await();

        webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .thenAccept(jsonObject -> Assertions.assertEquals("Hello Joe!", jsonObject.getString("greeting")))
                .await();

        webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)
                .thenAccept(response -> Assertions.assertEquals(204, response.status().code()))
                .thenCompose(nothing -> webClient.get()
                        .path("/greet/Joe")
                        .request(JsonObject.class))
                .thenAccept(jsonObject -> Assertions.assertEquals("Hola Joe!", jsonObject.getString("greeting")))
                .await();

        webClient.get()
                .path("/health")
                .request()
                .thenAccept(response -> {
                    Assertions.assertEquals(200, response.status().code());
                    response.close();
                })
                .await();

        webClient.get()
                .path("/metrics")
                .request()
                .thenAccept(response -> {
                    Assertions.assertEquals(200, response.status().code());
                    response.close();
                })
                .await();
    }

    @Test
    public void testOpenAPI() {
        /*
         * If you change the OpenAPI endpoint path in application.yaml, then
         * change the following path also.
         */
        JsonObject jsonObject = webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .path("/openapi")
                .request(JsonObject.class)
                .await();
        JsonObject paths = jsonObject.getJsonObject("paths");

        JsonPointer jp = Json.createPointer("/" + escape("/greet/greeting") + "/put/summary");
        JsonString js = (JsonString) jp.getValue(paths);
        Assertions.assertEquals("Set the greeting prefix", js.getString(), "/greet/greeting.put.summary not as expected");

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.MODEL_READER_PATH)
                                        + "/get/summary");
        js = (JsonString) jp.getValue(paths);
        Assertions.assertEquals(SimpleAPIModelReader.SUMMARY, js.getString(),
                                "summary added by model reader does not match");

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.DOOMED_PATH));
        Assertions.assertFalse(jp.containsValue(paths), "/test/doomed should not appear but does");
    }

    private static String escape(String path) {
        return path.replace("/", "~1");
    }

}
