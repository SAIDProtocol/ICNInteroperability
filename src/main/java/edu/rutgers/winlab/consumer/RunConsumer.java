/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.consumer;

import static edu.rutgers.winlab.common.HTTPUtility.CROSS_DOMAIN_HOST_IP;
import static edu.rutgers.winlab.common.HTTPUtility.HTTP_DATE_FORMAT;
import static edu.rutgers.winlab.common.NDNUtility.CROSS_DOMAIN_HOST_NDN;
import static edu.rutgers.winlab.common.NDNUtility.suppressNDNLog;
import edu.rutgers.winlab.icninteroperability.DataHandler;
import edu.rutgers.winlab.icninteroperability.DemultiplexingEntity;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestDynamic;
import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequestStatic;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import org.ccnx.ccn.config.ConfigurationException;

/**
 *
 * @author ubuntu
 */
public class RunConsumer {

    public static final String SYSTEM_OUT_NAME = "System.out";

    private static void usage() {
        System.err.printf("Usage: java %s <output> <static|dynamic> <consumerType> <consumerName> <dstDomain> <name> <[exclude|input]>%n", RunConsumer.class.getName());
        System.err.printf("    output: File name for output. print to System.out if output==%s%n", SYSTEM_OUT_NAME);
        System.err.printf("    consumerType dstDomain: %s|%s%n", CROSS_DOMAIN_HOST_IP, CROSS_DOMAIN_HOST_NDN);
        System.err.printf("    for static, the last component would be seen as exclude [using HTTP date format]. if cannot be parsed, null will be used%n");
        System.err.printf("    for dynamic, the last component would be seen as input. Empty string will be used if this component does not exist%n");
    }

    private static DataConsumer getConsumerFromType(String type, String name) throws ConfigurationException, IOException {
        switch (type) {
            case CROSS_DOMAIN_HOST_IP:
                return new DataConsumerIP();
            case CROSS_DOMAIN_HOST_NDN:
                return new DataConsumerNDN(name);
            default:
                System.err.printf("Cannot understand consumer type %s%n", type);
                usage();
                return null;
        }
    }

    private static CanonicalRequest getRequest(String type, String domain, String name, String extra, DataHandler handler) {
        switch (domain) {
            case CROSS_DOMAIN_HOST_IP:
            case CROSS_DOMAIN_HOST_NDN:
                break;
            default:
                System.err.printf("Cannot understand dstDomain type %s%n", domain);
                usage();
                return null;
        }

        switch (type) {
            case "static":
                Long version = null;
                try {
                    version = HTTP_DATE_FORMAT.parse(extra).getTime();
                } catch (Exception e) {
                }
                return new CanonicalRequestStatic(domain, name, version, handler);
            case "dynamic":
                return new CanonicalRequestDynamic(domain, null, name, extra == null ? new byte[0] : extra.getBytes(), handler);
            default:
                System.err.printf("Cannot understand command type:%s%n", type);
                return null;
        }
    }

    public static void main(String[] args) throws ConfigurationException, IOException {
        suppressNDNLog();
        if (args.length < 6) {
            usage();
            return;
        }
        try {
            try (OutputStream os = args[0].equals(SYSTEM_OUT_NAME) ? System.out : new FileOutputStream(args[0])) {
                DataHandler handler = new DataHandler() {
                    @Override
                    public void handleDataRetrieved(DemultiplexingEntity demux, byte[] data, int size, Long time, boolean finished) {
                        try {
                            os.write(data, 0, size);
                            if (finished) {
                                os.flush();
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace(System.err);
                        }
                    }

                    @Override
                    public void handleDataFailed(DemultiplexingEntity demux) {
                        System.err.println("Handle data failed!!");
                    }
                };

                DataConsumer consumer = getConsumerFromType(args[2], args[3]);
                if (consumer == null) {
                    return;
                }
                CanonicalRequest request = getRequest(args[1], args[4], args[5], args.length < 7 ? null : args[6], handler);
                if (request == null) {
                    return;
                }
                long version = consumer.request(request);
                System.err.printf("Version is: %s (%d)%n", HTTP_DATE_FORMAT.format(new Date(version)), version);

                os.flush();
            }
        } catch(IOException | ConfigurationException | RuntimeException e) {
            e.printStackTrace(System.err);
        }
        System.exit(0);
        
    }
}
