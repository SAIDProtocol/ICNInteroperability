/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import static edu.rutgers.winlab.common.HTTPUtility.CROSS_DOMAIN_HOST_IP;
import static edu.rutgers.winlab.common.MFUtility.CROSS_DOMAIN_HOST_MF;
import static edu.rutgers.winlab.common.NDNUtility.CROSS_DOMAIN_HOST_NDN;
import static edu.rutgers.winlab.common.NDNUtility.suppressNDNLog;
import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestDynamic;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.IOException;
import java.util.Random;
import org.ccnx.ccn.config.ConfigurationException;

/**
 *
 * @author root
 */
public class RunDynamicConsumerPressure {

    public static final String SYSTEM_OUT_NAME = "System.out";

    private static void usage() {
        System.err.printf("Usage: java %s <count> <consumerType> <consumerName> <dstDomain> <name> [<input>]%n", RunConsumer.class.getName());
        System.err.printf("    count: # of requests generated%n");
        System.err.printf("    consumerType dstDomain: %s|%s|%s%n", CROSS_DOMAIN_HOST_IP, CROSS_DOMAIN_HOST_NDN, CROSS_DOMAIN_HOST_MF);
        System.err.printf("    the last component would be seen as input. Empty string will be used if this component does not exist%n");
    }

    private static DataConsumer getConsumerFromType(String type, String name) throws ConfigurationException, IOException, JMFException {
        switch (type) {
            case CROSS_DOMAIN_HOST_IP:
                return new DataConsumerIP();
            case CROSS_DOMAIN_HOST_NDN:
                return new DataConsumerNDN(name);
            case CROSS_DOMAIN_HOST_MF:
                return new DataConsumerMF(name);
            default:
                System.err.printf("Cannot understand consumer type %s%n", type);
                usage();
                return null;
        }
    }

    private static CanonicalRequest getRequest(String domain, String name, String extra, DataHandler handler) {
        switch (domain) {
            case CROSS_DOMAIN_HOST_IP:
            case CROSS_DOMAIN_HOST_NDN:
            case CROSS_DOMAIN_HOST_MF:
                break;
            default:
                System.err.printf("Cannot understand dstDomain type %s%n", domain);
                usage();
                return null;
        }

        return new CanonicalRequestDynamic(domain, null, name, extra == null ? new byte[0] : extra.getBytes(), handler);
    }

    private static int successCount = 0, failCount = 0;

    private static final DataHandler DATA_HANDLER = new DataHandler() {
        @Override
        public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished) {
            if (finished) {
                System.err.println("Handle data succeeded!!");
                synchronized (SYSTEM_OUT_NAME) {
                    successCount++;
                }
            }
        }

        @Override
        public void handleDataFailed(DemultiplexingEntity demux) {
            System.err.println("Handle data failed!!");
            synchronized (SYSTEM_OUT_NAME) {
                failCount++;
            }
        }
    };

    private static class RequestThread implements Runnable {

        private final DataConsumer consumer;
        private final CanonicalRequest request;

        public RequestThread(DataConsumer consumer, CanonicalRequest request) {
            this.consumer = consumer;
            this.request = request;
        }

        @Override
        public void run() {
            try {
                consumer.request(request);
            } catch (IOException | RuntimeException ex) {
                synchronized (SYSTEM_OUT_NAME) {
                    failCount++;
                }
                System.err.println("Request data failed!!");
            }
        }

    }

    public static void main(String[] args) throws InterruptedException {
        suppressNDNLog();
        if (args.length < 5) {
            usage();
            return;
        }
        int count;

        try {
            count = Integer.parseInt(args[0]);
        } catch (RuntimeException e) {
            usage();
            return;
        }

        Thread[] threads = new Thread[count];
        Random rand = new Random(System.currentTimeMillis());
        try {

            for (int i = 0; i < count; i++) {
                DataConsumer consumer = getConsumerFromType(args[1], args[2] + "|" + rand.nextLong());
                if (consumer == null) {
                    return;
                }
                CanonicalRequest request = getRequest(args[3], args[4] + "/" + rand.nextLong(), args.length < 6 ? null : args[5], DATA_HANDLER);
                if (request == null) {
                    return;
                }
                threads[i] = new Thread(new RequestThread(consumer, request));
            }

        } catch (IOException | ConfigurationException | JMFException | RuntimeException e) {
            e.printStackTrace(System.err);
        }
        for (int i = 0; i < count; i++) {
            threads[i].start();
            Thread.sleep(1);
        }
        for (int i = 0; i < count; i++) {
            threads[i].join();
        }
        System.out.printf("Succeed: %d, failed: %d%n", successCount, failCount);
        System.exit(0);
    }

}
