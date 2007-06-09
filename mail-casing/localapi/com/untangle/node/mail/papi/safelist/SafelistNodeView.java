/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.untangle.node.mail.papi.safelist;

import java.util.List;

import com.untangle.node.mime.EmailAddress;

/**
 * Interface for the nodes to query the
 * safelist.  This is not intended to be
 * "remoted" to any UI.
 */
public interface SafelistNodeView {

    /**
     * Test if the given sender is safelisted
     * for the given recipients.  Implementations
     * of Safelist are permitted to ignore the
     * recipient set and simply maintain a "global"
     * list.
     * <br><br>
     * Note that if separate Safelists are maintained
     * for each recipient, this method should return
     * <code>true</code> if <b>any</b> of the recipients
     * have declared the sender on a safelist.
     *
     * @param envelopeSender the sender of the message
     *        as declared on the envelop (SMTP-only).  Obviously,
     *        this may be null
     * @param mimeFrom the sender of the email, as declared on
     *        the FROM header of the MIME message.  May be null,
     *        but obviously if this <b>and</b> the envelope
     *        sender are null false will be returned.
     * @param recipients the recipient(s) of the message
     *
     * @return true if the sender (either the envelope
     *         or MIME) is safelisted.  False does not mean
     *         that the sender is blacklisted - just not safelisted
     *
     */
    public boolean isSafelisted(EmailAddress envelopeSender,
                                EmailAddress mimeFrom,
                                List<EmailAddress> recipients);


}
