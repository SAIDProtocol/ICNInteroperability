/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab;

import edu.rutgers.winlab.icninteroperability.GatewayTwoDomains;
import edu.rutgers.winlab.icninteroperability.ip.DomainAdapterIP;
import edu.rutgers.winlab.jmfapi.JMFException;
import java.io.IOException;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author ubuntu
 */
public class Main {

    public static void main(String[] args) throws IOException, ConfigurationException, MalformedContentNameStringException, JMFException {
        DomainAdapterIP d1 = new DomainAdapterIP("IP:80", 80), d2 = new DomainAdapterIP("IP:10000", 10000);
        GatewayTwoDomains g2d = new GatewayTwoDomains(d1, d2);
        g2d.start();

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
