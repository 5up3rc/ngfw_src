/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

#include <stdlib.h>
#include <time.h>

#include <mvutil/debug.h>
#include <mvutil/errlog.h>
#include <mvutil/list.h>

#include "netcap_trie.h"
#include "netcap_trie_support.h"


int  netcap_trie_base_init  ( netcap_trie_t* trie, netcap_trie_base_t* base, netcap_trie_level_t* parent, 
                              u_char type, u_char pos, u_char depth )
{
    if ( trie == NULL || base == NULL ) return errlogargs();

    base->type     = type;
    base->pos      = pos;
    base->depth    = depth;
    base->data     = NULL;
    base->parent   = parent;
    
    return 0;
}

void netcap_trie_base_destroy ( netcap_trie_t* trie, netcap_trie_base_t* base )
{
    if ( trie == NULL || base == NULL ) {
        errlogargs();
        return;
    }

    /* Clear out all of the pointers */
    base->parent = NULL;
    
    /* If necessary free the associated item */
    _data_destroy ( trie, (netcap_trie_element_t)base );
}

/* Returns the number of children that an item has (1 for terminal nodes) */
int  netcap_trie_element_children( netcap_trie_element_t element )
{
    if ( element.base == NULL ) {
        return errlogargs();
    }

    switch ( element.base->type ) {
    case NC_TRIE_BASE_LEVEL:
        return element.level->num_children;

    case NC_TRIE_BASE_ITEM:
        return 1;

    default:
        break;
    }

    return errlog( ERR_CRITICAL, "Invalid base item(type: %d)\n", element.base->type );    
}

void netcap_trie_element_destroy ( netcap_trie_t* trie, netcap_trie_element_t element ) {
    if ( trie == NULL || element.base == NULL ) {
        errlogargs();
        return;
    }

    switch ( element.base->type ) {
    case NC_TRIE_BASE_LEVEL:
        netcap_trie_level_destroy ( trie, element.level );
        break;

    case NC_TRIE_BASE_ITEM:
        netcap_trie_item_destroy ( trie, element.item );
        break;

    default:
        errlog(ERR_CRITICAL, "TRIE: Trying to destroy an unknown structure: %d\n", element.base->type );
    }
}

void netcap_trie_element_raze    ( netcap_trie_t* trie, netcap_trie_element_t element ) {
    if ( trie == NULL || element.base == NULL ) {
        errlogargs();
        return;
    }

    switch ( element.base->type ) {
    case NC_TRIE_BASE_LEVEL:
        netcap_trie_level_raze ( trie, element.level );
        break;

    case NC_TRIE_BASE_ITEM:
        netcap_trie_item_raze ( trie, element.item );
        break;

    default:
        errlog(ERR_CRITICAL, "TRIE: Trying to raze an unknown structure: %#010x %d\n", element, 
               element.base->type );
    }
}

