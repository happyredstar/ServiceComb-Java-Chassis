/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.demo.springmvc.client;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import io.servicecomb.bizkeeper.BizkeeperExceptionUtils;
import io.servicecomb.core.exception.CseException;
import io.servicecomb.demo.CodeFirstRestTemplate;
import io.servicecomb.demo.TestMgr;
import io.servicecomb.provider.pojo.RpcReference;
import io.servicecomb.provider.springmvc.reference.CseHttpEntity;
import io.servicecomb.serviceregistry.RegistryUtils;
import io.servicecomb.swagger.invocation.Response;

@Component
public class CodeFirstRestTemplateSpringmvc extends CodeFirstRestTemplate {

  @RpcReference(microserviceName = "springmvc", schemaId = "codeFirst")
  private CodeFirstSprigmvcIntf intf;

  @Override
  protected void testOnlyRest(RestTemplate template, String cseUrlPrefix) {
    try {
      testUpload(template, cseUrlPrefix);
    } catch (IOException e) {
      e.printStackTrace();
    }

    super.testOnlyRest(template, cseUrlPrefix);
  }

  @Override
  protected void testExtend(RestTemplate template, String cseUrlPrefix) {
    super.testExtend(template, cseUrlPrefix);

    testResponseEntity("springmvc", template, cseUrlPrefix);
    testCodeFirstTestForm(template, cseUrlPrefix);
    testIntf();
    testFallback(template, cseUrlPrefix);
  }

  private void testUpload(RestTemplate template, String cseUrlPrefix) throws IOException {
    String file1Content = "hello world";
    File file1 = File.createTempFile("upload1", ".txt");
    FileUtils.writeStringToFile(file1, file1Content);

    String file2Content = " bonjour";
    File someFile = File.createTempFile("upload2", ".txt");
    FileUtils.writeStringToFile(someFile, file2Content);

    String templateResult = testRestTemplateUpload(template, cseUrlPrefix, file1, someFile);
    TestMgr.check(file1Content + file2Content, templateResult);
  }

  private String testRestTemplateUpload(RestTemplate template, String cseUrlPrefix, File file1, File someFile) {
    MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
    map.add("file1", new FileSystemResource(file1));
    map.add("someFile", new FileSystemResource(someFile));

    return template.postForObject(
        cseUrlPrefix + "/upload",
        new HttpEntity<>(map),
        String.class);
  }

  private void testFallback(RestTemplate template, String cseUrlPrefix) {
    String result = template.getForObject(cseUrlPrefix + "/fallback/returnnull/hello", String.class);
    TestMgr.check(result, "hello");
    result = template.getForObject(cseUrlPrefix + "/fallback/returnnull/throwexception", String.class);
    TestMgr.check(result, null);

    result = template.getForObject(cseUrlPrefix + "/fallback/throwexception/hello", String.class);
    TestMgr.check(result, "hello");
    try {
      result = template.getForObject(cseUrlPrefix + "/fallback/throwexception/throwexception", String.class);
      TestMgr.check(false, true);
    } catch (Exception e) {
      TestMgr.check(((CseException) e.getCause().getCause().getCause()).getMessage(),
          BizkeeperExceptionUtils.createBizkeeperException(BizkeeperExceptionUtils.CSE_HANDLER_BK_FALLBACK,
              null,
              "springmvc.codeFirst.fallbackThrowException").getMessage());
    }

    result = template.getForObject(cseUrlPrefix + "/fallback/fromcache/hello", String.class);
    TestMgr.check(result, "hello");
    result = template.getForObject(cseUrlPrefix + "/fallback/fromcache/throwexception", String.class);
    TestMgr.check(result, "hello");

    result = template.getForObject(cseUrlPrefix + "/fallback/force/hello", String.class);
    TestMgr.check(result, "mockedreslut");
  }

  private void testIntf() {
    Date date = new Date();

    String srcName = RegistryUtils.getMicroservice().getServiceName();

    ResponseEntity<Date> responseEntity = intf.responseEntity(date);
    TestMgr.check(date, responseEntity.getBody());
    TestMgr.check("h1v {x-cse-src-microservice=" + srcName + "}", responseEntity.getHeaders().getFirst("h1"));
    TestMgr.check("h2v {x-cse-src-microservice=" + srcName + "}", responseEntity.getHeaders().getFirst("h2"));

    checkStatusCode("springmvc", 202, responseEntity.getStatusCode());

    Response cseResponse = intf.cseResponse();
    TestMgr.check("User [name=nameA, age=100, index=0]", cseResponse.getResult());
    TestMgr.check("h1v {x-cse-src-microservice=" + srcName + "}", cseResponse.getHeaders().getFirst("h1"));
    TestMgr.check("h2v {x-cse-src-microservice=" + srcName + "}", cseResponse.getHeaders().getFirst("h2"));
  }

  private void testResponseEntity(String microserviceName, RestTemplate template, String cseUrlPrefix) {
    Map<String, Object> body = new HashMap<>();
    Date date = new Date();
    body.put("date", date);

    CseHttpEntity<Map<String, Object>> httpEntity = new CseHttpEntity<>(body);
    httpEntity.addContext("contextKey", "contextValue");

    String srcName = RegistryUtils.getMicroservice().getServiceName();

    ResponseEntity<Date> responseEntity =
        template.exchange(cseUrlPrefix + "responseEntity", HttpMethod.POST, httpEntity, Date.class);
    TestMgr.check(date, responseEntity.getBody());
    TestMgr.check("h1v {contextKey=contextValue, x-cse-src-microservice=" + srcName + "}",
        responseEntity.getHeaders().getFirst("h1"));
    TestMgr.check("h2v {contextKey=contextValue, x-cse-src-microservice=" + srcName + "}",
        responseEntity.getHeaders().getFirst("h2"));
    checkStatusCode(microserviceName, 202, responseEntity.getStatusCode());

    responseEntity =
        template.exchange(cseUrlPrefix + "responseEntity", HttpMethod.PATCH, httpEntity, Date.class);
    TestMgr.check(date, responseEntity.getBody());
    TestMgr.check("h1v {contextKey=contextValue, x-cse-src-microservice=" + srcName + "}",
        responseEntity.getHeaders().getFirst("h1"));
    TestMgr.check("h2v {contextKey=contextValue, x-cse-src-microservice=" + srcName + "}",
        responseEntity.getHeaders().getFirst("h2"));
    checkStatusCode(microserviceName, 202, responseEntity.getStatusCode());
  }

  protected void testCodeFirstTestForm(RestTemplate template, String cseUrlPrefix) {
    HttpHeaders formHeaders = new HttpHeaders();
    formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    Map<String, String> map = new HashMap<>();
    String code = "servicecomb%2bwelcome%40%23%24%25%5e%26*()%3d%3d";
    map.put("form1", code);
    HttpEntity<Map<String, String>> formEntiry = new HttpEntity<>(map, formHeaders);
    TestMgr.check(code + "null",
        template.postForEntity(cseUrlPrefix + "/testform", formEntiry, String.class).getBody());
    map.put("form2", "");
    TestMgr.check(code + "", template.postForEntity(cseUrlPrefix + "/testform", formEntiry, String.class).getBody());
  }
}
