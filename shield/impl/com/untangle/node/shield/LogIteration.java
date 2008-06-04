/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.untangle.node.shield;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Date;
import java.util.List;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.untangle.uvm.IntfConstants;
import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.ParseException;

import com.untangle.uvm.ArgonException;
import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.localapi.ArgonInterface;
import com.untangle.uvm.localapi.LocalIntfManager;

import com.untangle.uvm.shield.ShieldRejectionEvent;
import com.untangle.uvm.shield.ShieldStatisticEvent;

public class LogIteration
{
    private final JSONObject start;
    private final Date startTime;
    private final Date endTime;

    private final ShieldStatisticEvent statisticEvent;
    private final List<ShieldRejectionEvent> rejectionEvents;

    private LogIteration( JSONObject start, Date startTime, Date endTime,
                          ShieldStatisticEvent statisticEvent, List<ShieldRejectionEvent> rejectionEvents )
    {
        this.start = start;
        this.startTime = startTime;
        this.endTime = endTime;
        this.statisticEvent = statisticEvent;
        this.rejectionEvents = rejectionEvents;
    }

    public JSONObject getStart()
    {
        return this.start;
    }

    public Date getStartTime()
    {
        return this.startTime;
    }

    public Date getEndTime()
    {
        return this.endTime;
    }

    public ShieldStatisticEvent getStatisticEvent()
    {
        return this.statisticEvent;
    }

    
    public List<ShieldRejectionEvent> getRejectionEvents()
    {
        return this.rejectionEvents;
    }
    
    static LogIteration parse( JSONObject iteration_js ) throws JSONException
    {
        JSONObject start = iteration_js.getJSONObject( "start_time" );
        Date startTime = parseDate( start );
        Date endTime = parseDate( iteration_js.getJSONObject( "end_time" ));
        
        ShieldStatisticEvent statisticEvent = parseStatisticEvent( iteration_js );
        List<ShieldRejectionEvent>  rejectionEvents = new LinkedList<ShieldRejectionEvent>();

        JSONObject user_js = iteration_js.getJSONObject( "user" );
        if ( user_js == null ) user_js = new JSONObject();

        String users[] = JSONObject.getNames( user_js );
        if ( users == null ) users = new String[0];
        for ( String user : users ) {
            try {
                InetAddress address = IPaddr.parse( user ).getAddr();
                
                parseUserData( rejectionEvents, address, user_js.getJSONArray( user ));
            } catch ( ParseException e ) {
                /* Ignore corrupted IP Addresses. */
                continue;
            } catch ( UnknownHostException e ) {
                /* Ignore corrupted IP Addresses. */
                continue;
            }
        }
        
        return new LogIteration( start, startTime, endTime, statisticEvent, rejectionEvents );
    }

    private static ShieldStatisticEvent parseStatisticEvent( JSONObject iteration_js ) throws JSONException
    {
        ShieldStatisticEvent event = new ShieldStatisticEvent();
        JSONArray globals_js = iteration_js.getJSONArray( "globals" );
        int length = 0;

        int accepted = 0;
        int limited = 0;
        int dropped = 0;

        int rejected = 0;
        int relaxed = 0;
        int lax = 0;
        int tight = 0;
        int closed = 0;

        if (( length = globals_js.length()) > 0 ) {
            for ( int c = 0 ; c < length ; c++ ) {
                JSONArray temp_js = globals_js.getJSONArray( c );
                if ( temp_js.length() != 6 ) continue;
                rejected += temp_js.getInt( 1 );
                dropped += temp_js.getInt( 2 );
                limited += temp_js.getInt( 3 );
                accepted += temp_js.getInt( 4 );
                /* errors are ignored. */
            }

            event.setAccepted( accepted );
            event.setLimited( limited );
            event.setDropped( dropped );
            event.setRejected( rejected );
        }

        JSONObject mode_js = iteration_js.getJSONObject( "mode" );
        event.setRelaxed( mode_js.optInt( "relaxed", 0 ));
        event.setLax( mode_js.optInt( "lax", 0 ));
        event.setTight( mode_js.optInt( "tight", 0 ));
        event.setClosed( mode_js.optInt( "closed", 0 ));
        
        return event;
    }

    private static void parseUserData( List<ShieldRejectionEvent>  rejectionEvents, 
                                       InetAddress address, JSONArray user_js ) throws JSONException
    {
        double reputation = user_js.getDouble( 0 );

        for ( int c = 1 ; c < user_js.length(); c++ ) {
            JSONArray temp_js = user_js.getJSONArray( c );
            if ( temp_js.length() != 6 ) continue;
            
            LocalIntfManager lim = LocalUvmContextFactory.context().localIntfManager();

            /* Mark it as internal */
            byte clientIntf = IntfConstants.INTERNAL_INTF;

            try {
                ArgonInterface ai = lim.getIntfByName( temp_js.optString( 0, "" ));
                if ( ai != null ) clientIntf = ai.getArgon();
            } catch ( ArgonException e ) {
                clientIntf = IntfConstants.INTERNAL_INTF;
            }

            int mode = 0;

            int limited = temp_js.getInt( 3 );
            int dropped = temp_js.getInt( 2 );
            int rejected = temp_js.getInt( 1 );

            rejectionEvents.add( new ShieldRejectionEvent( address, clientIntf, reputation, mode, limited, dropped, rejected ));
        }
    }
    
    private static Date parseDate( JSONObject date ) throws JSONException
    {
        if ( !"timeval".equals( date.getString( "type" ))) throw new JSONException( "invalid date." );
        
        /* Time in millis */
        long time = date.getInt( "tv_sec" ) * 1000;
        time += date.getInt( "tv_usec" ) / 1000;

        return new Date( time );
    }
}
