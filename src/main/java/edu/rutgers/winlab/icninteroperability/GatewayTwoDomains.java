/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.icninteroperability;

import edu.rutgers.winlab.icninteroperability.canonical.CanonicalRequest;
import java.util.function.BiFunction;

/**
 *
 * @author ubuntu
 */
public class GatewayTwoDomains extends Gateway {

    private static class TwoDomainRouting implements BiFunction<DomainAdapter, CanonicalRequest, DomainAdapter> {

        private final DomainAdapter d1, d2;

        public TwoDomainRouting(DomainAdapter d1, DomainAdapter d2) {
            this.d1 = d1;
            this.d2 = d2;
        }

        @Override
        public DomainAdapter apply(DomainAdapter t, CanonicalRequest u) {
            return t == d1 ? d2 : d1;
        }
    }

    public GatewayTwoDomains(DomainAdapter adapter1, DomainAdapter adapter2) {
        super(new DomainAdapter[]{adapter1, adapter2}, new TwoDomainRouting(adapter1, adapter2));
    }

}
