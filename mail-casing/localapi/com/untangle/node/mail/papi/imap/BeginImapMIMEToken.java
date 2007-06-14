/*
 * $HeadURL:$
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

import com.untangle.uvm.tapi.event.TCPStreamer;
import com.untangle.node.mail.papi.BeginMIMEToken;
import com.untangle.node.mail.papi.MIMEAccumulator;
import com.untangle.node.mail.papi.MessageInfo;


/**
 * Token reprsenting the Begining
 * of a MIME message in Imap.
 * Adds the {@link #getMessageLength Message Length}
 * attribute to the base BeginMIMEToken and adds
 * a new method for {@link #toImapTCPStreamer streaming in the unparser}.
 *
 */
public class BeginImapMIMEToken
    extends BeginMIMEToken {

    private final int m_msgLen;

    public BeginImapMIMEToken(MIMEAccumulator accumulator,
                              MessageInfo messageInfo,
                              int msgLen) {
        super(accumulator, messageInfo);
        m_msgLen = msgLen;
    }

    /**
     * Gets the Message length (from the LITERAL declaration
     * of octets in IMAP).
     *
     * @return the message length
     */
    public int getMessageLength() {
        return m_msgLen;
    }

    /**
     * Get a TCPStreamer for the initial contents
     * of this message, including the opening literal declaration.
     *
     * @return the TCPStreamer
     */
    public TCPStreamer toImapTCPStreamer(boolean disposeAccumulatorWhenDone) {
        return new CLLTCPStreamer(
                                  super.toUnstuffedTCPStreamer(),
                                  getMessageLength(),
                                  disposeAccumulatorWhenDone);
    }

    /**
     * Little class which closes the MIMEAccumulator
     * when completed.
     */
    class CLLTCPStreamer
        extends LiteralLeadingTCPStreamer {

        private final boolean m_disposeAccumulatorWhenDone;

        CLLTCPStreamer(TCPStreamer wrapped,
                       int literalLength,
                       boolean disposeAccumulatorWhenDone) {
            super(wrapped, literalLength);
            m_disposeAccumulatorWhenDone = disposeAccumulatorWhenDone;
        }

        @Override
        protected void doneStreaming() {
            if(m_disposeAccumulatorWhenDone && getMIMEAccumulator() != null) {
                getMIMEAccumulator().dispose();
            }
        }
    }
}
