/**
 * $Id$
 */

package com.untangle.node.captive_portal;

import com.untangle.uvm.node.RuleMatcher;

// THIS IS FOR ECLIPSE - @formatter:off

/**
 * This is a matching criteria for a Capture Control Rule
 * Example: "Destination Port" == "80"
 * Example: "HTTP Host" == "salesforce.com"
 *
 * A CaptureRule has a set of these to determine what traffic to match
 */

// THIS IS FOR ECLIPSE - @formatter:on

@SuppressWarnings("serial")
public class CaptureRuleMatcher extends RuleMatcher
{
    public CaptureRuleMatcher()
    {
        super();
    }

    public CaptureRuleMatcher(MatcherType matcherType, String value)
    {
        super(matcherType, value);
    }

    public CaptureRuleMatcher(MatcherType matcherType, String value, Boolean invert)
    {
        super(matcherType, value, invert);
    }
}
