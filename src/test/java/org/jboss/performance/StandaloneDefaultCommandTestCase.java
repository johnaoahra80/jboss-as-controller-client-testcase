package org.jboss.performance;

import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: john
 * Date: 8/14/14
 * Time: 1:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class StandaloneDefaultCommandTestCase {

    private static String host = "localhost";
    private static int port = 9999;

    private InetAddress serverHost = null;
    private ModelControllerClient unauthenticatedClient;
    private Monitor monitor;

    @Before
    public void setupClient() {
        try {
            serverHost = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        // Try connecting to the server without authentication first

        unauthenticatedClient = ModelControllerClient.Factory.create(serverHost, port);

        String subsystem = "datasources";

        LinkedHashMap<String, String> defaultAddresses = new LinkedHashMap<>();

        monitor = new MonitorSubsystem(unauthenticatedClient, subsystem, defaultAddresses);

        monitor.setMonitorAddress("data-source", "ExampleDS");

    }

    @Test
    public void SingleCommandTestCase() {


        monitor.run();

        List<String> result = null;
        result = monitor.getResult();

        Assert.assertNotNull(result);
    }


    @Test
    public void MultipleCommandsTestCase() {


        ExecutorService es = Executors.newFixedThreadPool(4);
        List<Future<Monitor>> futures = new ArrayList<Future<Monitor>>();

        for (int i = 0; i < 10; i++) {
            Future<Monitor> submit;
            submit = (Future<Monitor>) es.submit(monitor);

            futures.add(submit);

        }

        for (Future<Monitor> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                ;
            }
        }

        es.shutdown();

        try {
            boolean finshed = es.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        List<String> result = null;
        result = monitor.getResult();


        Assert.assertNotNull(result);
    }


    interface Monitor extends Runnable {

        public String getName();

        public void setName(String name);

        public ModelNode getMonitorAddress();

        public void setMonitorAddress(String value, String key);

        public List<String> getMonitorLabelWithRuntimeValues();

        public String getMonitorLabel();

        public boolean operationSucceeded();

        public List<String> getResult();

        public String getFailureReason();

    }

    private class MonitorSubsystem implements Monitor {

        private ModelControllerClient client;
        private ModelNode operation;
        private ModelNode address;
        private ModelNode returnValue;
        private Subsystem subsystem;

        private SubsystemOperationMessageHandler subsystemOperationMessageHandler;

        public MonitorSubsystem(ModelControllerClient client, String subsystemName, LinkedHashMap<String, String> addresses) {

            this.client = client;

            operation = new ModelNode();
            operation.get("operation").set("read-resource");

            address = operation.get("address");

            operation.get("include-runtime").set(true);
            operation.get("recursive").set(true);
            operation.get("operations").set(true);

            returnValue = new ModelNode();

            subsystem = new DatasourcesSubsystem(addresses);  //SubsystemFactory.createSubsystem(subsystemName, addresses);

            address.add("subsystem", subsystemName);

            this.subsystemOperationMessageHandler = new SubsystemOperationMessageHandler();
        }

        public String getName() {

            return subsystem.getName();

        }

        public void setName(String name) {

            subsystem.setName(name);

        }

        public ModelNode getMonitorAddress() {

            return address;

        }

        public void setMonitorAddress(String value, String key) {

            address.add(value, key);

            return;

        }

        public List<String> getMonitorLabelWithRuntimeValues() {

            List<String> runtimeValueList = new ArrayList<String>();

            List<List<String>> runtimeAttributes = subsystem.getRuntimeAttributes();

            for (List<String> runtimeAttributeList : runtimeAttributes) {
                String runtimeAttributeValue = runtimeAttributeList.get(runtimeAttributeList.size() - 1);
                runtimeValueList.add(address.asString() + " " + runtimeAttributeValue);
            }

            return runtimeValueList;

        }

        public String getMonitorLabel() {

            return address.asString();

        }

        public boolean operationSucceeded() {

            return subsystem.didOperationSucceed(returnValue);

        }

        public List<String> getResult() {

            return subsystem.getRuntimeAttributeResults(returnValue);

        }

        public String getFailureReason() {

            return subsystem.getFailureReason(returnValue);

        }

        @Override
        public void run() {

            try {
                returnValue = client.execute(operation, this.subsystemOperationMessageHandler);
            } catch (IOException e) {
                returnValue = null;
            }

        }


        private class SubsystemOperationMessageHandler implements OperationMessageHandler {

            private Logger logger = Logger.getLogger(SubsystemOperationMessageHandler.class);

            @Override
            public void handleReport(MessageSeverity severity, String message) {
                logger.trace("[" + severity.toString() + "]: " + message);
            }
        }


    }

    private class Subsystem {

        private List<List<String>> runtimeAttributes;
        private String name;

        protected LinkedHashMap<String, String> addresses;

        public Subsystem(String name, LinkedHashMap<String, String> addresses) {
            this.name = name;
            runtimeAttributes = new ArrayList<List<String>>();

            this.addresses = addresses;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean didOperationSucceed(ModelNode returnValue) {
            return returnValue != null && returnValue.get("outcome").asString().equals("success");
        }

        protected void addRuntimeAttributes(List<String> newAttributes) {
            runtimeAttributes.add(newAttributes);
        }

        public List<List<String>> getRuntimeAttributes() {
            return runtimeAttributes;
        }

        public List<String> getRuntimeAttributeResults(ModelNode returnValue) {

            ModelNode tempNode = returnValue;
            List<String> result = new ArrayList<String>();

            for (List<String> runtimeAttribute : runtimeAttributes) {
                tempNode = returnValue;
                for (String runtimeAttributeAddress : runtimeAttribute) {
                    tempNode = tempNode.get(runtimeAttributeAddress);
                }
                result.add(tempNode.asString());
            }

            return result;

        }

        public String getFailureReason(ModelNode returnValue) {
            return returnValue.asString();
        }

    }

    private class DatasourcesSubsystem extends Subsystem {

        public DatasourcesSubsystem(LinkedHashMap<String, String> addresses) {

            super("datasources", addresses);

            //  Prepared statement cache access count

            List<String> runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("jdbc");
            runtimeAttributeAddresses.add("PreparedStatementCacheAccessCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Prepared statement cache add count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("jdbc");
            runtimeAttributeAddresses.add("PreparedStatementCacheAddCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Prepared statement cache current size

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("jdbc");
            runtimeAttributeAddresses.add("PreparedStatementCacheCurrentSize");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Prepared statement cache delete count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("jdbc");
            runtimeAttributeAddresses.add("PreparedStatementCacheDeleteCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Prepared statement cache hit count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("jdbc");
            runtimeAttributeAddresses.add("PreparedStatementCacheHitCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Prepared statement cache miss count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("jdbc");
            runtimeAttributeAddresses.add("PreparedStatementCacheMissCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool active count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("ActiveCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool available count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("AvailableCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool average blocking time

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("AverageBlockingTime");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool average creation time

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("AverageCreationTime");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool created count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("CreatedCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool destroyed count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("DestroyedCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool max creation time

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("MaxCreationTime");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool max used count

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("MaxUsedCount");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool max wait time

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("MaxWaitTime");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool time out

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("TimedOut");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool total blocking time

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("TotalBlockingTime");

            addRuntimeAttributes(runtimeAttributeAddresses);

            //  Pool total creation time

            runtimeAttributeAddresses = new ArrayList<String>();

            runtimeAttributeAddresses.add("result");
            runtimeAttributeAddresses.add("statistics");
            runtimeAttributeAddresses.add("pool");
            runtimeAttributeAddresses.add("TotalCreationTime");

            addRuntimeAttributes(runtimeAttributeAddresses);

        }

    }

}
