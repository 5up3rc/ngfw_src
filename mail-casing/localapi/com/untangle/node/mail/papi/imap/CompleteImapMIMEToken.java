/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.untangle.node.mail.papi.imap;

import com.untangle.uvm.vnet.Pipeline;
import com.untangle.uvm.vnet.event.TCPStreamer;
import com.untangle.node.mail.papi.CompleteMIMEToken;
import com.untangle.node.mail.papi.MIMETCPStreamer;
import com.untangle.node.mail.papi.MessageInfo;
import com.untangle.node.mime.MIMEMessage;
import org.apache.log4j.Logger;


/**
 * Class representing a Complete MIME message.
 * This will be issued if an upstream Node
 * has buffered a complete message.
 * <br><br>
 * Adds a different {@link #toImapTCPStreamer streaming}
 * method which causes the leading literal ("{nnn}\r\n")
 * to be prepended.
 */
public class CompleteImapMIMEToken
    extends CompleteMIMEToken {

    private final Logger m_logger =
        Logger.getLogger(CompleteImapMIMEToken.class);

    public CompleteImapMIMEToken(MIMEMessage msg,
                                 MessageInfo msgInfo) {
        super(msg, msgInfo);
    }

    /**
     * Create a TCPStreamer for Imap (which includes the leading literal).
     */
    public TCPStreamer toImapTCPStreamer(Pipeline pipeline,
                                         boolean disposeMessageWhenDone) {

        MIMETCPStreamer mimeStreamer = createMIMETCPStreamer(pipeline, disposeMessageWhenDone);
        int len = (int) mimeStreamer.getFileLength();

        m_logger.debug("About to return a Literal-leading streamer for literal length: " + len);

        return new LiteralLeadingTCPStreamer(
                                             mimeStreamer,
                                             len);
    }
}
