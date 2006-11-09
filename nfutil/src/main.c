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

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <libnetfilter_queue/libnetfilter_queue.h>

#define COMMAND_UNBIND  "unbind"

typedef enum
{
    CMD_UNKNOWN = 0,
    CMD_UNBIND = 1
} _command_t;

static void _parse_args ( int argc, char** argv );
static void _usage      ( char** argv );

/* Various commands */
static void _unbind     ( void );

static struct
{
    void (*command) ( void );
} _nfutil = {
    .command = NULL
};

/* A program to execute a few operations related to the global state
 * of the NF Queue */
int main( int argc, char** argv )
{
    /* May exit on usage error */
    _parse_args( argc, argv );

    if ( _nfutil.command != NULL ) _nfutil.command();
    else _usage( argv );

    return 0;
}

static void _parse_args( int argc, char** argv )
{
    if ( argc < 2 ) _usage( argv );

    char* command = argv[1];

    if ( strncmp( command, COMMAND_UNBIND, sizeof( COMMAND_UNBIND )) == 0 ) {
        /* nothing to parse */
        _nfutil.command = _unbind;
    } else {
        _usage( argv );
    }
}


static void _usage     ( char** argv )
{
    /* pretty lame usage right now, but it may have more utility later */
    fprintf( stderr, "\tUSAGE: %s %s\n", argv[0], COMMAND_UNBIND );

    exit( -1 );
}

/* Various commands */
static void _unbind     ( void )
{
    struct nfq_handle *h;
    
    if (( h = nfq_open()) == NULL ) {
        fprintf( stderr, "nfq_open [%s]\n", strerror( errno ));
        exit(1);
    }
    
    printf("unbinding existing nf_queue handler for AF_INET (if any)\n");
    if ( nfq_unbind_pf( h, AF_INET ) < 0 ) {
        fprintf( stderr, "nfq_unbind_pf [%s]\n", strerror( errno ));
        exit(1);
    }

    nfq_close( h );
}

