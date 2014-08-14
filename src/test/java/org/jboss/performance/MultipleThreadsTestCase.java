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
public class MultipleThreadsTestCase extends AbstractTestCase {

    @Test
    public void MultipleThreadsTest() {


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


}
