/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.junit.Test;

/**
 *
 * @author root
 */
public class RunGatewayTest {

    private static final Logger LOG = Logger.getLogger(RunGatewayTest.class.getName());

    public RunGatewayTest() {
    }

    private String testString() {
        System.out.println("HERE!");
        return "HERE!";
    }

    @Test
    public void testSomeMethod() {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.SEVERE);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(Level.SEVERE);
        }

        LOG.log(Level.INFO, () -> String.format("HERE!%s", testString()));
//        LOG.log(Level.INFO, String.format("HERE!%s", testString()));
    }

}
