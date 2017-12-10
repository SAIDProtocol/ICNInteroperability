/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

import static edu.rutgers.winlab.common.NDNUtility.suppressNDNLog;
import edu.rutgers.winlab.icninteroperability.ip.DomainAdapterIP;
import edu.rutgers.winlab.icninteroperability.mf.DomainAdapterMF;
import edu.rutgers.winlab.icninteroperability.ndn.DomainAdapterNDN;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ubuntu
 */
public class RunGateway {

    public static void usage() {
        System.out.printf("usage: java %s <type>%n", RunGateway.class.getName());
        System.out.println("  type: ipip    run IP(80)-IP(10000) GW");
        System.out.println("  type: ipndn   run IP(80)-NDN GW");
        System.out.println("  type: ipmf    run IP(80)-MF(4096) GW");
        System.out.println("  type: mfndn   run MF(4096)-NDN GW");
    }

    public static void runGatewayTwoIPs() throws IOException {
        System.out.println("Starting IP(80)-IP(10000) GW");
        DomainAdapterIP d1 = new DomainAdapterIP("IP:80", 80), d2 = new DomainAdapterIP("IP:10000", 10000);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }

    public static void runGatewayIPNDN() throws IOException {
        suppressNDNLog();
        System.out.println("Starting IP(80)-NDN GW");
        DomainAdapterIP d1 = new DomainAdapterIP("d1", 80);
        DomainAdapterNDN d2 = new DomainAdapterNDN("d2");
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }

    public static void runGatewayIPMF() throws IOException, JMFException {
        System.out.println("Starting IP(80)-MF(4096) GW");
        DomainAdapter d1 = new DomainAdapterIP("IP:80", 80),
                d2 = new DomainAdapterMF("d2:4096", 4096);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }

    public static void runGatewayMFNDN() throws IOException, JMFException {
        suppressNDNLog();
        System.out.println("Starting MF(4096)-NDN GW");
        DomainAdapter d1 = new DomainAdapterMF("d1:4096", 4096),
                d2 = new DomainAdapterNDN("d2");

        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }
    private static final Logger LOG = Logger.getLogger(RunGateway.class.getName());

    private static final TimerTask MEMORY_USAGE_REPORT_TASK = new TimerTask() {
        @Override
        public void run() {
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long memory = runtime.totalMemory() - runtime.freeMemory();
            LOG.log(Level.INFO, "Memory usage: {0}", memory);
        }
    };

    public static void main(String[] args) throws IOException, JMFException {
        args = new String[]{"ipndn"};
        if (args.length == 0) {
            usage();
            return;
        }
        Timer t = new Timer("MemoryUsageReport", true);
        t.scheduleAtFixedRate(MEMORY_USAGE_REPORT_TASK, 0, 1000);
        switch (args[0]) {
            case "ipip":
                runGatewayTwoIPs();
                break;
            case "ipndn":
                runGatewayIPNDN();
                break;
            case "ipmf":
                runGatewayIPMF();
                break;
            case "mfndn":
                runGatewayMFNDN();
                break;
            default:
                usage();
                break;
        }
    }

}
