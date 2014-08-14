package org.jboss.performance;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: john
 * Date: 8/14/14
 * Time: 4:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class SingleThreadTestCase extends AbstractTestCase {

    @Test
    public void SingleCommandTestCase() {


        monitor.run();

        List<String> result = null;
        result = monitor.getResult();

        Assert.assertNotNull(result);
    }



}
