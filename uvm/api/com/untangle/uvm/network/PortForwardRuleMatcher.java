/**
 * $Id: PortForwardRuleMatcher.java,v 1.00 2011/08/24 14:54:43 dmorris Exp $
 */
package com.untangle.uvm.network;

import com.untangle.uvm.node.RuleMatcher;


/**
 * This is a matching criteria for a PortForward Rule
 * Example: "Destination Port" == "80"
 *
 * A PortForwardRule has a set of these to determine what traffic to match
 */
@SuppressWarnings("serial")
public class PortForwardRuleMatcher extends RuleMatcher
{
    public PortForwardRuleMatcher( )
    {
        super();
    }

    public PortForwardRuleMatcher( MatcherType matcherType, String value )
    {
        super( matcherType, value );
    }

    public PortForwardRuleMatcher( MatcherType matcherType, String value, Boolean invert )
    {
        super( matcherType, value, invert );
    }
}
