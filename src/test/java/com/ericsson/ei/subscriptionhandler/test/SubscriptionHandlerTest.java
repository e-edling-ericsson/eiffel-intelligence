/*
   Copyright 2017 Ericsson AB.
   For a full list of individual contributors, please see the commit history.
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.ericsson.ei.subscriptionhandler.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.reflect.Whitebox.invokeMethod;

import java.io.File;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ericsson.ei.App;
import com.ericsson.ei.controller.model.QueryResponse;
import com.ericsson.ei.jmespath.JmesPathInterface;
import com.ericsson.ei.mongodbhandler.MongoDBHandler;
import com.ericsson.ei.subscriptionhandler.InformSubscriber;
import com.ericsson.ei.subscriptionhandler.RunSubscription;
import com.ericsson.ei.subscriptionhandler.SendMail;
import com.ericsson.ei.subscriptionhandler.SendHttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.MongoClient;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.tests.MongodForTestsFactory;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = { App.class })
@AutoConfigureMockMvc
public class SubscriptionHandlerTest {

    private static final String aggregatedPath = "src/test/resources/AggregatedObject.json";
    private static final String aggregatedInternalPath = "src/test/resources/AggregatedDocumentInternalCompositionLatest.json";
    private static final String aggregatedPathForMapNotification = "src/test/resources/aggregatedObjectForMapNotification.json";
    private static final String subscriptionPath = "src/test/resources/SubscriptionObject.json";
    private static final String artifactRequirementSubscriptionPath = "src/test/resources/artifactRequirementSubscription.json";
    private static final String subscriptionPathForAuthorization = "src/test/resources/SubscriptionObjectForAuthorization.json";
    private static final String dbName = "MissedNotification";
    private static final String collectionName = "Notification";
    private static final String regex = "^\"|\"$";
    private static final String missedNotificationUrl = "/queryMissedNotifications";
    private static final boolean statusOk = true;

    private static HttpHeaders headersWithAuth = new HttpHeaders();
    private static HttpHeaders headersWithoutAuth = new HttpHeaders();
    private static String aggregatedObject;
    private static String aggregatedInternalObject;
    private static String aggregatedObjectMapNotification;
    private static String subscriptionData;
    private static String artifactRequirementSubscriptionData;
    private static String subscriptionDataForAuthorization;
    private static String url;
    private static String urlAuthorization;
    private static MongodForTestsFactory testsFactory;
    private static MongoClient mongoClient = null;
    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private RunSubscription runSubscription;

    @Autowired
    private MongoDBHandler mongoDBHandler;

    @Autowired
    private JmesPathInterface jmespath;

    private static String subscriptionRepeatFlagTruePath = "src/test/resources/SubscriptionRepeatFlagTrueObject.json";
    private static String subscriptionRepeatFlagTrueData;
    private static String subscriptionForMapNotificationPath = "src/test/resources/subscriptionForMapNotification.json";
    private static String subscriptionForMapNotification;

    @Autowired
    private InformSubscriber informSubscriber;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SendHttpRequest springRestTemplate;

    private static String subRepeatFlagDataBaseName = "eiffel_intelligence";
    private static String subRepeatFlagCollectionName = "subscription_repeat_handler";

    @Autowired
    private SendMail sendMail;

    @Mock
    private QueryResponse queryResponse;

    public static void setUpEmbeddedMongo() throws Exception {
        testsFactory = MongodForTestsFactory.with(Version.V3_4_1);
        mongoClient = testsFactory.newMongo();
        String port = "" + mongoClient.getAddress().getPort();
        System.setProperty("spring.data.mongodb.port", port);

        aggregatedObject = FileUtils.readFileToString(new File(aggregatedPath), "UTF-8");
        aggregatedInternalObject = FileUtils.readFileToString(new File(aggregatedInternalPath), "UTF-8");
        aggregatedObjectMapNotification = FileUtils.readFileToString(new File(aggregatedPathForMapNotification),
                "UTF-8");

        artifactRequirementSubscriptionData = FileUtils.readFileToString(new File(artifactRequirementSubscriptionPath),
                "UTF-8");
        subscriptionRepeatFlagTrueData = FileUtils.readFileToString(new File(subscriptionRepeatFlagTruePath), "UTF-8");
        subscriptionDataForAuthorization = FileUtils.readFileToString(new File(subscriptionPathForAuthorization),
                "UTF-8");
        subscriptionForMapNotification = FileUtils.readFileToString(new File(subscriptionForMapNotificationPath),
                "UTF-8");

        subscriptionData = FileUtils.readFileToString(new File(subscriptionPath), "UTF-8");
        url = new JSONObject(subscriptionData).getString("notificationMeta").replaceAll(regex, "").replaceAll("'", "");
        urlAuthorization = new JSONObject(subscriptionDataForAuthorization).getString("notificationMeta")
                .replaceAll(regex, "");

        headersWithAuth.add("Content-Type", "application/x-www-form-urlencoded");
        headersWithAuth.add("Authorization", "Basic dGVzdFVzZXJOYW1lOnRlc3RQYXNzd29yZA==");
        headersWithoutAuth.add("Content-Type", "application/json");
    }

    @BeforeClass
    public static void init() throws Exception {
        setUpEmbeddedMongo();
        System.setProperty("notification.ttl.value", "1");
    }

    @AfterClass
    public static void close() {
        mongoClient.close();
        testsFactory.shutdown();
    }

    @Before
    public void beforeTests() {
        mongoDBHandler.dropCollection(subRepeatFlagDataBaseName, subRepeatFlagCollectionName);
    }

    @PostConstruct
    public void initMocks() {
        mongoDBHandler.setMongoClient(mongoClient);
    }

    @Test
    public void runSubscriptionOnObjectTest() throws Exception {
        JsonNode subscriptionJson = mapper.readTree(subscriptionData);
        ArrayNode requirementNode = (ArrayNode) subscriptionJson.get("requirements");
        Iterator<JsonNode> requirementIterator = requirementNode.elements();

        boolean output = runSubscription.runSubscriptionOnObject(aggregatedObject, requirementIterator,
                subscriptionJson, "someID");
        assertTrue(output);
    }

    @Test
    public void runRequirementSubscriptionOnObjectTest() throws Exception {
        JsonNode subscriptionJson = mapper.readTree(artifactRequirementSubscriptionData);
        JsonNode aggregatedDocument = mapper.readTree(aggregatedInternalObject);
        ArrayNode requirementNode = (ArrayNode) subscriptionJson.get("requirements");
        Iterator<JsonNode> requirementIterator = requirementNode.elements();
        JsonNode aggregatedObject = aggregatedDocument.get("aggregatedObject");
        String aggregationStr = aggregatedObject.toString();
        boolean output = runSubscription.runSubscriptionOnObject(aggregationStr, requirementIterator, subscriptionJson,
                "someID");
        assertTrue(output);
    }

    @Test
    public void runSubscriptionOnObjectRepeatFlagFalseTest() throws Exception {
        JsonNode subscriptionJson = mapper.readTree(subscriptionData);
        ArrayNode requirementNode = (ArrayNode) subscriptionJson.get("requirements");
        Iterator<JsonNode> requirementIterator = requirementNode.elements();
        Iterator<JsonNode> requirementIterator2 = requirementNode.elements();

        boolean output1 = runSubscription.runSubscriptionOnObject(aggregatedObject, requirementIterator,
                subscriptionJson, "someID");
        boolean output2 = runSubscription.runSubscriptionOnObject(aggregatedObject, requirementIterator2,
                subscriptionJson, "someID");
        assertTrue(output1);
        assertFalse(output2);
    }

    @Test
    public void runSubscriptionOnObjectRepeatFlagTrueTest() throws Exception {
        JsonNode subscriptionJson = mapper.readTree(subscriptionRepeatFlagTrueData);
        ArrayNode requirementNode = (ArrayNode) subscriptionJson.get("requirements");
        Iterator<JsonNode> requirementIterator = requirementNode.elements();
        Iterator<JsonNode> requirementIterator2 = requirementNode.elements();

        boolean output1 = runSubscription.runSubscriptionOnObject(aggregatedObject, requirementIterator,
                subscriptionJson, "someID");
        boolean output2 = runSubscription.runSubscriptionOnObject(aggregatedObject, requirementIterator2,
                subscriptionJson, "someID");
        assertTrue(output1);
        assertTrue(output2);
    }

    @Test
    public void missedNotificationTest() throws Exception {
        informSubscriber.informSubscriber(aggregatedObject, mapper.readTree(subscriptionData));
        Iterable<String> outputDoc = mongoDBHandler.getAllDocuments(dbName, collectionName);
        Iterator<String> itr = outputDoc.iterator();
        String data = itr.next().toString();
        JsonNode jsonResult = null;
        JsonNode expectedOutput = null;
        ObjectMapper mapper = new ObjectMapper();
        expectedOutput = mapper.readTree(aggregatedObject);
        jsonResult = mapper.readTree(data);

        JsonNode output = jsonResult.get("AggregatedObject");
        assertEquals(expectedOutput, output);
    }

    @Test
    public void sendMailTest() throws Exception {
        Set<String> extRec = new HashSet<>();
        String recievers = "asdf.hklm@ericsson.se, affda.fddfd@ericsson.com, sasasa.dfdfdf@fdad.com, abcd.defg@gmail.com";
        extRec = (sendMail.extractEmails(recievers));
        assertEquals(String.valueOf(extRec.toArray().length), "4");
    }

    @Test
    public void testRestPostTrigger() throws Exception {
        JSONObject subscriptionDataJson = new JSONObject(subscriptionData);
        when(springRestTemplate.postDataMultiValue(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(statusOk);

        String jmesPathRule = "fileInformation[?extension=='jar'] | [0]";
        String urlWithoutJmespath = "http://127.0.0.1:3000/ei/buildParam?token='test_token'&json=";
        String urlWithJmesPath = urlWithoutJmespath + jmesPathRule;

        // Notify subscriber using url with jmes path
        subscriptionDataJson.put("notificationMeta", urlWithJmesPath);
        informSubscriber.informSubscriber(aggregatedObject, mapper.readTree(subscriptionDataJson.toString()));

        // Remove ' from url since JMESPATH does this
        urlWithoutJmespath = urlWithoutJmespath.replaceAll("'", "");

        // Create expected extraction
        String expectedExtraction = jmespath.runRuleOnEvent(jmesPathRule, aggregatedObject).toString();

        // Verify that springRestTemplate was called with the correct url and
        // extracted data
        verify(springRestTemplate, times(1)).postDataMultiValue(
                urlWithoutJmespath + URLEncoder.encode(expectedExtraction, "UTF8"),
                mapNotificationMessage(subscriptionDataJson.toString()), headersWithoutAuth);
    }

    @Test
    public void testRestPostTriggerForAuthorization() throws Exception {
        when(springRestTemplate.postDataMultiValue(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(statusOk);
        when(springRestTemplate.makeGetRequest(Mockito.any(), Mockito.any())).thenReturn(null);
        informSubscriber.informSubscriber(aggregatedObject, mapper.readTree(subscriptionDataForAuthorization));
        verify(springRestTemplate, times(1)).postDataMultiValue(urlAuthorization,
                mapNotificationMessage(subscriptionDataForAuthorization), headersWithAuth);
    }

    @Test
    public void testRestPostTriggerFailure() throws Exception {
        informSubscriber.informSubscriber(aggregatedObject, new ObjectMapper().readTree(subscriptionData));
        verify(springRestTemplate, times(4)).postDataMultiValue(url, mapNotificationMessage(subscriptionData),
                headersWithoutAuth);
        assertFalse(mongoDBHandler.getAllDocuments(dbName, collectionName).isEmpty());
    }

    @Test
    public void testQueryMissedNotificationEndPoint() throws Exception {
        String subscriptionName = new JSONObject(subscriptionData).getString("subscriptionName").replaceAll(regex, "");
        JSONObject input = new JSONObject(aggregatedObject);
        informSubscriber.informSubscriber(aggregatedObject, mapper.readTree(subscriptionData));
        MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get(missedNotificationUrl).param("SubscriptionName", subscriptionName))
                .andReturn();
        String response = result.getResponse().getContentAsString().replace("\\", "");
        assertEquals(
                "{\"queryResponseEntity\":" + input.toString() + "}",
                response);
        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
    }

    @Test
    public void testMapNotificationMessage() throws Exception {
        MultiValueMap<String, String> actual = invokeMethod(informSubscriber, "mapNotificationMessage",
                aggregatedObjectMapNotification, mapper.readTree(subscriptionForMapNotification));
        MultiValueMap<String, String> expected = new LinkedMultiValueMap<>();
        expected.add("", "{\"conclusion\":\"SUCCESSFUL\",\"id\":\"TC5\"}");
        assertEquals(expected, actual);
    }

    private MultiValueMap<String, String> mapNotificationMessage(String data) throws Exception {
        MultiValueMap<String, String> mapNotificationMessage = new LinkedMultiValueMap<>();

        ArrayNode arrNode = (ArrayNode) mapper.readTree(data).get("notificationMessageKeyValues");
        if (arrNode.isArray()) {
            for (final JsonNode objNode : arrNode) {
                if (!objNode.get("formkey").toString().replaceAll(regex, "").equals("Authorization")) {

                    mapNotificationMessage.add(objNode.get("formkey").toString().replaceAll(regex, ""), jmespath
                            .runRuleOnEvent(objNode.get("formvalue").toString().replaceAll(regex, ""), aggregatedObject)
                            .toString().replaceAll(regex, ""));
                }
            }
        }
        return mapNotificationMessage;
    }

    private static ResponseEntity<String> createResponseEntity(String body, HttpStatus httpStatus) {
        return new ResponseEntity<String>(body, new HttpHeaders(), httpStatus);
    }
}
