/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab;

import edu.rutgers.winlab.icninteroperability.GatewayTwoDomains;
import edu.rutgers.winlab.icninteroperability.ip.DomainAdapterIP;
import edu.rutgers.winlab.jmfapi.JMFException;
import edu.rutgers.winlab.provider.ProviderIP;
import java.io.IOException;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author ubuntu
 */
public class Main {

    public static void runGatewayTwoIPs() throws IOException {
        System.out.println("Starting IP(80)-IP(10000) GW");
        DomainAdapterIP d1 = new DomainAdapterIP("IP:80", 80), d2 = new DomainAdapterIP("IP:10000", 10000);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();

    }

    public static void runProvider(int port, String folder) throws IOException {
        System.out.printf("Starting IP Provider on %d, folder %s%n", port, folder);
        ProviderIP provider = new ProviderIP(80, folder);
        provider.start();
    }

    public static void usage() {
        System.out.println("usage: java -jar XXX.jar %type% [%params%]");
        System.out.println("  type: ipip                  run IP(80)-IP(10000) GW");
        System.out.println("  type: ipp %port% %folder%   run IP provider with port and folder");
    }

    public static void main(String[] args) throws IOException, ConfigurationException, MalformedContentNameStringException, JMFException {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "ipip":
                runGatewayTwoIPs();
                break;
            case "ipp":
                if (args.length < 3) {
                    usage();
                    return;
                }
                runProvider(Integer.parseInt(args[1]), args[2]);
                break;
            default:
                usage();
                return;
        }

//        JMFAPI handle = new JMFAPI();
//        handle.jmfopen("basic", new GUID(0xaabbcc));
//        CCNHandle handle = CCNHandle.open();
//        
//        
//        try (CCNVersionedInputStream cis = new CCNVersionedInputStream(ContentName.fromNative("/test"), handle)) {
//            System.out.printf("Version: %s%n", cis.getVersion());
//            
//            byte[] buf = new byte[4096];
//            int total = 0, read;
//            do {
//                System.out.printf("available: %d curr: %s ", cis.available(), cis.currentSegmentName());
//                read = cis.read(buf, 0, buf.length);
//                System.out.printf("read: %d%n", read);
//                total += read;
//            } while (read > 0);
//            System.out.printf("Total read: %d%n", total);
//        }
//        handle.close();
    }
}
