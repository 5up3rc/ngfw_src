/**
 * $Id$
 */
package com.untangle.node.ips.options;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;

import com.untangle.node.ips.IpsDetectionEngine;
import com.untangle.node.ips.IpsRule;
import com.untangle.node.ips.IpsRuleSignatureImpl;
import com.untangle.node.ips.IpsSessionInfo;

public abstract class IpsOption
{
    protected final IpsRuleSignatureImpl signature;

    protected final boolean negationFlag;

    private static final Logger log = Logger.getLogger(IpsOption.class);

    protected IpsOption(OptionArg arg)
    {
        this.signature = arg.getSignature();
        this.negationFlag = arg.getNegationFlag();
    }

    // Overriden in concrete children that are runnable
    public boolean runnable()
    {
        return false;
    }

    // Overriden in concrete children that are runnable
    public boolean run(IpsSessionInfo sessionInfo)
    {
        return true;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public static IpsOption buildOption(IpsDetectionEngine engine,
                                        IpsRuleSignatureImpl signature,
                                        IpsRule rule,
                                        String optionName,
                                        String params,
                                        boolean initializeSettingsTime)
    {
        boolean negationFlag = false;
        if(params.charAt(0) == '!')  {
            negationFlag = true;
            params = params.replaceFirst("!","").trim();
        }

        if(params.charAt(0) == '\"' && params.charAt(params.length()-1) == '\"')
            params = params.substring(1,params.length()-1);

        // XXX get rid of this reflection

        OptionArg oa = new OptionArg(engine, rule, signature, params,
                                     initializeSettingsTime, negationFlag);

        IpsOption option = null;

        optionName = optionName.toLowerCase();
        char ch = optionName.charAt(0);
        try {
            optionName = optionName.replaceFirst("" + ch, "" + (char)(ch - 'a' + 'A'));
        } catch(PatternSyntaxException e) {
            log.error("Bad option name", e);
        }

        try {
            // First look for a three arg one, then the two arg one
            // (since most don't care about initializeSettingsTime).
            Class clazz = Class.forName("com.untangle.node.ips.options."+optionName+"Option");
            Constructor constructor = clazz.getConstructor(new Class[] { OptionArg.class });
            option = (IpsOption)constructor.newInstance(new Object[] { oa });
        } catch (ClassNotFoundException e) {
            log.info("Could not load option: " + optionName + ", ignoring rule: " + signature.getSid());
            signature.remove(true);
        } catch (NoSuchMethodException e) {
            log.warn("Could not load option", e);
        } catch (InstantiationException e) {
            log.warn("Could not load option", e);
        } catch (IllegalAccessException e) {
            log.warn("Could not load option", e);
        } catch (InvocationTargetException e) {
            log.warn("Could not load option", e);
        }

        return option;
    }

    public boolean optEquals(IpsOption o)
    {
        return negationFlag == o.negationFlag;
    }

    public int optHashCode()
    {
        return 17 * 37 + (negationFlag ? 1 : 0);
    }
}

