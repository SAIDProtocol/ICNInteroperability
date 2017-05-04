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

/**
 *
 * @author ubuntu
 */
public class RunGateway {

    public static void usage() {
        System.out.printf("usage: java %s <type>%n", RunGateway.class.getName());
        System.out.println("  type: ipip    run IP(80)-IP(10000) GW");
        System.out.println("  type: ipndn   run IP(80)-NDN GW");
        System.out.println("  type: ipmf   run IP(80)-MF(4096) GW");
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

    public static void main(String[] args) throws IOException, JMFException {
        if (args.length == 0) {
            usage();
            return;
        }
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
            default:
                usage();
                break;
        }
    }

}
