/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import static edu.rutgers.winlab.common.HTTPUtility.CROSS_DOMAIN_HOST_IP;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_DATE_FORMAT;
import static edu.rutgers.winlab.common.MFUtility.CROSS_DOMAIN_HOST_MF;
import static edu.rutgers.winlab.common.NDNUtility.CROSS_DOMAIN_HOST_NDN;
import static edu.rutgers.winlab.common.NDNUtility.suppressNDNLog;
import static edu.rutgers.winlab.consumer.RunDynamicConsumerPressure.SYSTEM_OUT_NAME;
import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestStatic;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.support.Tuple;

/**
 *
 * @author root
 */
public class RunStaticConsumerPressure {

    private static void usage() {
        System.err.printf("Usage: java %s <nameCount> <zipfVal> <consumerType> <consumerName> <dstDomain>%n", RunConsumer.class.getName());
        System.err.printf("    nameCount, zipfVal: parameters to generate zipf requests%n");
        System.err.printf("    consumerType dstDomain: %s|%s|%s%n", CROSS_DOMAIN_HOST_IP, CROSS_DOMAIN_HOST_NDN, CROSS_DOMAIN_HOST_MF);
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

        Long version = null;
        try {
            version = HTTP_DATE_FORMAT.parse(extra).getTime();
        } catch (ParseException | RuntimeException e) {
        }
        return new CanonicalRequestStatic(domain, name, version, handler);
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

    public static ArrayList<Tuple<String, String>> generateZipfRequests(int itemCount, double s) {
        ArrayList<Tuple<String, String>> ret = new ArrayList<>();
        double sum = 0;
        for (int i = 1; i <= itemCount; i++) {
            sum += Math.pow(1.0 / i, s);
        }
        double multiply = Math.pow(itemCount, s) * sum;
        int total = 0;
        ArrayList<Integer> values = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            int count = (int) Math.round(multiply / Math.pow(i, s) / sum);
            for (int j = 0; j < count; j++) {
                values.add(i);
            }
            total += count;
        }
        Random rand = new Random(0);
        while (!values.isEmpty()) {
            int pos = rand.nextInt(values.size());
            int val = values.remove(pos);
            ret.add(new Tuple("/" + val + ".txt", "Tuesday, 12 December 2017 00:00:00 000"));
        }

        return ret;
    }

    public static void main(String[] args) throws InterruptedException {

        suppressNDNLog();
        if (args.length < 4) {
            usage();
            return;
        }

        int itemCount;
        double zipfVal;
        try {
            itemCount = Integer.parseInt(args[0]);
            zipfVal = Double.parseDouble(args[1]);
        } catch(RuntimeException e) {
            System.err.println("Cannot parse nameCount or zipfVal.");
            usage();
            return;
        }
        
        ArrayList<Tuple<String, String>> namesAndVersions = generateZipfRequests(itemCount, zipfVal);
        //read names from file

        Thread[] threads = new Thread[namesAndVersions.size()];
        try {

            for (int i = 0; i < namesAndVersions.size(); i++) {
                DataConsumer consumer = getConsumerFromType(args[2], args[3]);
                if (consumer == null) {
                    return;
                }
                CanonicalRequest request = getRequest(args[4], namesAndVersions.get(i).first(), namesAndVersions.get(i).second(), DATA_HANDLER);
                if (request == null) {
                    return;
                }
                threads[i] = new Thread(new RequestThread(consumer, request));
            }

        } catch (IOException | ConfigurationException | JMFException | RuntimeException e) {
            e.printStackTrace(System.err);
        }
        for (int i = 0; i < namesAndVersions.size(); i++) {
            threads[i].start();
            Thread.sleep(1);
        }
        for (int i = 0; i < namesAndVersions.size(); i++) {
            threads[i].join();
        }
        System.out.printf("Succeed: %d, failed: %d%n", successCount, failCount);
        System.exit(0);
    }
}
