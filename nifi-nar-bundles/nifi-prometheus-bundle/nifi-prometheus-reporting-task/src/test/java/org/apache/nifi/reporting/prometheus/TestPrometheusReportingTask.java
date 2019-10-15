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
package org.apache.nifi.reporting.prometheus;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.nifi.controller.status.ProcessGroupStatus;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.state.MockStateManager;
import org.apache.nifi.util.MockComponentLog;
import org.apache.nifi.util.MockConfigurationContext;
import org.apache.nifi.util.MockReportingContext;
import org.apache.nifi.util.MockReportingInitializationContext;
import org.apache.nifi.util.MockVariableRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestPrometheusReportingTask {
    private static final String TEST_INIT_CONTEXT_ID = "test-init-context-id";
    private static final String TEST_INIT_CONTEXT_NAME = "test-init-context-name";
    private static final String TEST_TASK_ID = "test-task-id";
    private MockReportingInitializationContext reportingInitContextStub;
    private MockReportingContext reportingContextStub;
    private MockConfigurationContext configurationContextStub; // new
    private PrometheusReportingTask testedReportingTask;
    private ProcessGroupStatus rootGroupStatus;

    @Before
    public void setup() {
        testedReportingTask = new PrometheusReportingTask();
        rootGroupStatus = new ProcessGroupStatus();
        reportingInitContextStub = new MockReportingInitializationContext(TEST_INIT_CONTEXT_ID, TEST_INIT_CONTEXT_NAME,
                new MockComponentLog(TEST_TASK_ID, testedReportingTask));

        reportingContextStub = new MockReportingContext(Collections.emptyMap(),
                new MockStateManager(testedReportingTask), new MockVariableRegistry());

        reportingContextStub.setProperty(PrometheusReportingTask.INSTANCE_ID.getName(), "localhost");

        configurationContextStub = new MockConfigurationContext(reportingContextStub.getProperties(),
                reportingContextStub.getControllerServiceLookup());

        rootGroupStatus.setId("1234");
        rootGroupStatus.setFlowFilesReceived(5);
        rootGroupStatus.setBytesReceived(10000);
        rootGroupStatus.setFlowFilesSent(10);
        rootGroupStatus.setBytesSent(20000);
        rootGroupStatus.setQueuedCount(100);
        rootGroupStatus.setQueuedContentSize(1024L);
        rootGroupStatus.setBytesRead(60000L);
        rootGroupStatus.setBytesWritten(80000L);
        rootGroupStatus.setActiveThreadCount(5);
        rootGroupStatus.setName("root");
        rootGroupStatus.setFlowFilesTransferred(5);
        rootGroupStatus.setBytesTransferred(10000);
        rootGroupStatus.setOutputContentSize(1000L);
        rootGroupStatus.setInputContentSize(1000L);
        rootGroupStatus.setOutputCount(100);
        rootGroupStatus.setInputCount(1000);

    }

    @Test
    public void testOnTrigger() throws IOException, InterruptedException, InitializationException {
        testedReportingTask.initialize(reportingInitContextStub);
        testedReportingTask.onScheduled(configurationContextStub);
        reportingContextStub.getEventAccess().setProcessGroupStatus(rootGroupStatus);
        testedReportingTask.onTrigger(reportingContextStub);

        String content = getMetrics();
        Assert.assertTrue(content.contains(
                "nifi_amount_flowfiles_received{instance=\"localhost\",component_type=\"RootProcessGroup\",component_name=\"root\",component_id=\"1234\",parent_id=\"\",} 5.0"));
        Assert.assertTrue(content.contains(
                "nifi_amount_threads_active{instance=\"localhost\",component_type=\"RootProcessGroup\",component_name=\"root\",component_id=\"1234\",parent_id=\"\",} 5.0"));

        // Rename the component
        rootGroupStatus.setName("rootroot");
        content = getMetrics();
        Assert.assertFalse(content.contains(
                "nifi_amount_flowfiles_received{instance=\"localhost\",component_type=\"RootProcessGroup\",component_name=\"root\",component_id=\"1234\",parent_id=\"\",} 5.0"));
        Assert.assertFalse(content.contains(
                "nifi_amount_threads_active{instance=\"localhost\",component_type=\"RootProcessGroup\",component_name=\"root\",component_id=\"1234\",parent_id=\"\",} 5.0"));
        Assert.assertTrue(content.contains(
                "nifi_amount_flowfiles_received{instance=\"localhost\",component_type=\"RootProcessGroup\",component_name=\"rootroot\",component_id=\"1234\",parent_id=\"\",} 5.0"));
        Assert.assertTrue(content.contains(
                "nifi_amount_threads_active{instance=\"localhost\",component_type=\"RootProcessGroup\",component_name=\"rootroot\",component_id=\"1234\",parent_id=\"\",} 5.0"));
    }

    private String getMetrics() throws IOException {
        URL url = new URL("http://localhost:9092/metrics");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int status = con.getResponseCode();
        Assert.assertEquals(HttpURLConnection.HTTP_OK, status);

        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet("http://localhost:9092/metrics");
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);

        return content;
    }

}
