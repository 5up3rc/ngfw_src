/*
 * $HeadURL:$
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
/* @author: Dirk Morris <dmorris@untangle.com> */
#include <stdlib.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <mvutil/list.h>

#include <vector/relay.h>


relay_t* relay_create ()
{
    relay_t* relay = calloc(1,sizeof(relay_t));

    if (!relay)
        return errlogmalloc_null();

    if (list_init(&relay->event_q,0)<0) {
        free (relay);
        return perrlog_null("list_init");
    }

    relay->event_q_max_len = 1;
    relay->event_hook = NULL;
    
    return relay;
}

void     relay_free ( relay_t* relay )
{
    if (!relay)
        return;

    if (list_length(&relay->event_q)>0) {
        list_node_t* step;
        debug(10,"RELAY: Freeing lost events\n");
        for (step = list_head(&relay->event_q) ; step ; step = list_node_next(step)) {
            event_t* evt = (event_t*)list_node_val(step);
            
            if ( evt == NULL ) 
                errlog( ERR_CRITICAL, "NULL event in relay\n" );
            else 
                evt->raze(evt);
        }
    }
    
    if (list_destroy(&relay->event_q));
    
    free (relay);
}

void     relay_set_src ( relay_t* relay, source_t* src )
{
    if (!relay) {errlogargs();return;}
    relay->src = src;
}

void     relay_set_snk ( relay_t* relay, sink_t* snk )
{
    if (!relay) {errlogargs();return;}
    relay->snk = snk;
}

int      relay_debug_print ( int level, char* prefix, relay_t* relay )
{
    errlogimpl();
    //debug(level,"%s: (0x%08x->0x%08x) eventq:%i\n",prefix,relay->src_key_cached,relay->snk_key_cached, list_length(&relay->event_q));
    return 0;
}

void     relay_set_event_hook ( relay_t* relay, relay_event_hook_t hook )
{
    if (!relay) {errlogargs();return;}
    relay->event_hook = hook;
}

void     relay_set_event_hook_arg ( relay_t* relay, void* arg )
{
    if (!relay) {errlogargs();return;}
    relay->event_hook_arg = arg;
}

