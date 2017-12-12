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

    public static class Counter {

        private static final Logger LOG = Logger.getLogger(Counter.class.getName());
        private static final Runtime RUNTIME = Runtime.getRuntime();

        private static final int IDX_REQUEST_INCOMING = 0;
        private static final int IDX_REQUEST_OUTGOING = 1;
        private static final int IDX_MEMORY = 2;
        private static final int IDX_TIME = 3;
//        private static final int GC_ROUND_COUNT = 4;

        private static int count = 0;
        private final Long[] requests = new Long[]{0L, 0L, 0L, 0L};

        private void logStatus() {
            count++;
//            if (count % GC_ROUND_COUNT == 0)
//                RUNTIME.gc();
            requests[IDX_MEMORY] = RUNTIME.totalMemory() - RUNTIME.freeMemory();
            requests[IDX_TIME] = System.nanoTime();
            LOG.log(Level.INFO, "{3}:{0}:{1}:{2}", requests);
        }

        public synchronized void incomingRequestsAdded(int val) {
            requests[IDX_REQUEST_INCOMING] += val;
            logStatus();
        }

        public synchronized void incomingRequestsRemoved(int val) {
            requests[IDX_REQUEST_INCOMING] -= val;
            logStatus();
        }

        public synchronized void outgoingRequestsAdded(int val) {
            requests[IDX_REQUEST_OUTGOING] += val;
            logStatus();
        }

        public synchronized void outgoingRequestsRemoved(int val) {
            requests[IDX_REQUEST_OUTGOING] -= val;
            logStatus();
        }
    }

    public static void usage() {
        System.out.printf("usage: java %s <type>%n", RunGateway.class.getName());
        System.out.println("  type: ipip    run IP(80)-IP(10000) GW");
        System.out.println("  type: ipndn   run IP(80)-NDN GW");
        System.out.println("  type: ipmf    run IP(80)-MF(4096) GW");
        System.out.println("  type: mfndn   run MF(4096)-NDN GW");
    }

    public static void runGatewayTwoIPs() throws IOException {
        System.out.println("Starting IP(80)-IP(10000) GW");
        Counter counter = new Counter();
        DomainAdapterIP d1 = new DomainAdapterIP("IP:80", 80,
                counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved);
        DomainAdapterIP d2 = new DomainAdapterIP("IP:10000", 10000,
                counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }

    public static void runGatewayIPNDN() throws IOException {
        suppressNDNLog();
        System.out.println("Starting IP(80)-NDN GW");
        Counter counter = new Counter();
        DomainAdapterIP d1 = new DomainAdapterIP("d1", 80,
                counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved);
        DomainAdapterNDN d2 = new DomainAdapterNDN("d2",
                counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }

    public static void runGatewayIPMF() throws IOException, JMFException {
        System.out.println("Starting IP(80)-MF(4096) GW");
        Counter counter = new Counter();
        DomainAdapter d1 = new DomainAdapterIP("IP:80", 80,
                counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved),
                d2 = new DomainAdapterMF("d2:4096", 4096,
                        counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                        counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }

    public static void runGatewayMFNDN() throws IOException, JMFException {
        suppressNDNLog();
        System.out.println("Starting MF(4096)-NDN GW");
        Counter counter = new Counter();
        DomainAdapter d1 = new DomainAdapterMF("d1:4096", 4096,
                counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved),
                d2 = new DomainAdapterNDN("d2",
                        counter::incomingRequestsAdded, counter::incomingRequestsRemoved,
                        counter::outgoingRequestsAdded, counter::outgoingRequestsRemoved);

        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();
    }
    private static final Logger LOG = Logger.getLogger(RunGateway.class.getName());

//    private static final TimerTask MEMORY_USAGE_REPORT_TASK = new TimerTask() {
//        @Override
//        public void run() {
//            Runtime runtime = Runtime.getRuntime();
//            runtime.gc();
//            long memory = runtime.totalMemory() - runtime.freeMemory();
//            LOG.log(Level.INFO, "Memory usage: {0}", memory);
//        }
//    };
    public static void main(String[] args) throws IOException, JMFException {
        if (args.length == 0) {
            usage();
            return;
        }
//        Timer t = new Timer("MemoryUsageReport", true);
//        t.scheduleAtFixedRate(MEMORY_USAGE_REPORT_TASK, 0, 1000);
        Thread gcThread = new Thread(() -> {
            while (true) {
                System.gc();
            }
        });
        gcThread.setDaemon(true);
        gcThread.start();
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
