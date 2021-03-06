/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.siddhi.extension.io.nats.sink;

import io.siddhi.core.SiddhiAppRuntime;
import io.siddhi.core.SiddhiManager;
import io.siddhi.core.stream.input.InputHandler;
import io.siddhi.extension.io.nats.utils.NATSClient;
import io.siddhi.extension.io.nats.utils.ResultContainer;
import io.siddhi.extension.io.nats.utils.STANClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Test case to publish messages while authentication unable.
 */
public class NatsSinkAuthTestCase {

    GenericContainer simpleWebServer;
    private int port = 4222;

    private void startDocker(boolean isTls, String dockerImgName, String... commands) throws InterruptedException {
        simpleWebServer = new GenericContainer(dockerImgName);
        simpleWebServer.setPrivilegedMode(true);
        if (isTls) {
            simpleWebServer.addFileSystemBind("src/test/resources/certs", "/certs", BindMode.READ_WRITE);
        }
        simpleWebServer.setCommand(commands);
        simpleWebServer.start();
        port = simpleWebServer.getMappedPort(4222);
        Thread.sleep(500);
    }


    @Test
    public void natsCorePublishWithUserNameAndPass() throws InterruptedException, TimeoutException, IOException {
        startDocker(false, "nats", "--user", "test", "--pass", "1234");
        ResultContainer resultContainer = new ResultContainer(20, 8);
        NATSClient natsClient = new NATSClient("nats-test1", resultContainer, port);
        natsClient.setUsernameAndPassword("test".toCharArray(), "1234".toCharArray());
        natsClient.connectClient();
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "@App:name('Test-plan1')\n"
                + "@sink(type='nats', @map(type='xml'), "
                + "destination='nats-test1', "
                + "server.urls='" + "nats://localhost:" + port + "', "
                + "auth.type='user', username='test', password='1234')"
                + "define stream inputStream (name string, age int, country string);";

        natsClient.subscribe();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition);
        InputHandler inputStream = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();

        for (int i = 0; i < 20; i++) {
            inputStream.send(new Object[]{"MIKE", i, "Germany"});
        }
        Thread.sleep(1000);
        Assert.assertTrue(resultContainer.assertMessageContent("<events><event><name>MIKE</name><age>19</age>" +
                "<country>Germany</country></event></events>"));
        siddhiManager.shutdown();
        simpleWebServer.close();
    }

    @Test(dependsOnMethods = "natsCorePublishWithUserNameAndPass")
    public void natsStreamingWithUserNameAndPassword() throws InterruptedException, TimeoutException, IOException {
        startDocker(false, "nats-streaming", "--user", "test", "--pass", "1234");
        ResultContainer resultContainer = new ResultContainer(2, 3);
        STANClient stanClient = new STANClient("test-cluster", "stan_test1", "nats://localhost:"
                + port, resultContainer);
        stanClient.setUsernameAndPassword("test".toCharArray(), "1234".toCharArray());
        stanClient.connect();
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "@App:name('Test-plan1')\n"
                + "@sink(type='nats', @map(type='xml'), "
                + "destination='nats-test1', "
                + "client.id='test-plan1-siddhi',"
                + "server.urls='" + "nats://localhost:" + port + "', "
                + "streaming.cluster.id='test-cluster', auth.type='user', username='test', password='1234'"
                + ")"
                + "define stream inputStream (name string, age int, country string);";

        stanClient.subsripeFromNow("nats-test1");
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition);
        InputHandler inputStream = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();

        inputStream.send(new Object[]{"JAMES", 23, "USA"});
        inputStream.send(new Object[]{"MIKE", 23, "Germany"});
        Thread.sleep(500);
        Assert.assertTrue(resultContainer.assertMessageContent("JAMES"));
        Assert.assertTrue(resultContainer.assertMessageContent("MIKE"));
        siddhiManager.shutdown();
        simpleWebServer.close();
    }

    @Test(dependsOnMethods = "natsStreamingWithUserNameAndPassword")
    public void natsCorePublishWithToken() throws InterruptedException, TimeoutException, IOException {
        startDocker(false, "nats", "--auth", "test");
        ResultContainer resultContainer = new ResultContainer(20, 8);
        NATSClient natsClient = new NATSClient("nats-test2", resultContainer, port);
        natsClient.setToken("test".toCharArray());
        natsClient.connectClient();
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "@App:name('Test-plan1')\n"
                + "@sink(type='nats', @map(type='xml'), "
                + "destination='nats-test2', "
                + "server.urls='" + "nats://localhost:" + port + "', "
                + "auth.type='token', token='test')"
                + "define stream inputStream (name string, age int, country string);";

        natsClient.subscribe();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition);
        InputHandler inputStream = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();

        for (int i = 0; i < 20; i++) {
            inputStream.send(new Object[]{"MIKE", i, "Germany"});
        }
        Thread.sleep(1000);
        Assert.assertTrue(resultContainer.assertMessageContent("<events><event><name>MIKE</name><age>19</age>" +
                "<country>Germany</country></event></events>"));
        siddhiManager.shutdown();
        simpleWebServer.close();
    }

    @Test(dependsOnMethods = "natsCorePublishWithToken")
    public void natsStreamingWithToken() throws InterruptedException, TimeoutException, IOException {
        startDocker(false, "nats-streaming", "--auth", "test");
        ResultContainer resultContainer = new ResultContainer(2, 3);
        STANClient stanClient = new STANClient("test-cluster", "stan_test1", "nats://localhost:"
                + port, resultContainer);
        stanClient.setToken("test".toCharArray());
        stanClient.connect();
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "@App:name('Test-plan1')\n"
                + "@sink(type='nats', @map(type='xml'), "
                + "destination='nats-test1', "
                + "client.id='test-plan1-siddhi',"
                + "server.urls='" + "nats://localhost:" + port + "', "
                + "streaming.cluster.id='test-cluster', auth.type='token', token='test'"
                + ")"
                + "define stream inputStream (name string, age int, country string);";

        stanClient.subsripeFromNow("nats-test1");
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition);
        InputHandler inputStream = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();

        inputStream.send(new Object[]{"JAMES", 23, "USA"});
        inputStream.send(new Object[]{"MIKE", 23, "Germany"});
        Thread.sleep(500);
        Assert.assertTrue(resultContainer.assertMessageContent("JAMES"));
        Assert.assertTrue(resultContainer.assertMessageContent("MIKE"));
        siddhiManager.shutdown();
        simpleWebServer.close();
    }

    @Test(dependsOnMethods = "natsStreamingWithToken")
    public void natsCorePublishWithTLS() throws Exception {
        startDocker(true, "nats", "--tls", "--tlscert", "certs/server.pem", "--tlskey", "certs/key.pem");
        ResultContainer resultContainer = new ResultContainer(20, 8);
        NATSClient natsClient = new NATSClient("nats-test2", resultContainer, port);
        natsClient.addSSL();
        natsClient.connectClient();
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "@App:name('Test-plan1')\n"
                + "@sink(type='nats', @map(type='xml'), "
                + "destination='nats-test2', "
                + "server.urls='" + "nats://localhost:" + port + "', "
                + "auth.type='tls', truststore.file='src/test/resources/truststore.jks', " +
                "truststore.password='password', truststore.algorithm='SunX509')"
                + "define stream inputStream (name string, age int, country string);";

        natsClient.subscribe();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition);
        InputHandler inputStream = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();

        for (int i = 0; i < 20; i++) {
            inputStream.send(new Object[]{"MIKE", i, "Germany"});
        }
        Thread.sleep(1000);
        Assert.assertTrue(resultContainer.assertMessageContent("<events><event><name>MIKE</name><age>19</age>" +
                "<country>Germany</country></event></events>"));
        siddhiManager.shutdown();
        simpleWebServer.close();
    }

}
