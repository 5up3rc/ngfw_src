/**
 * $Id$
 */
#include <stdlib.h>
#include <pthread.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <inttypes.h>
#include <sys/socket.h>
#include <mvutil/debug.h>
#include <mvutil/errlog.h>
#include <mvutil/unet.h>
#include <mvutil/list.h>
#include <mvutil/mvpoll.h>

#include <vector/vector.h>
#include <vector/relay.h>

#ifdef DEBUG_ON
#define VECTOR_DEBUG 1
#endif

#define MVPOLL_MAX_SIZE 1024
#define MVPOLL_INPUT_SET  MVPOLLIN  | MVPOLLERR | MVPOLLHUP
#define MVPOLL_OUTPUT_SET MVPOLLOUT | MVPOLLERR | MVPOLLHUP
#define MVPOLL_DISABLE_SET 0
#define MVPOLL_KEYSTUB_TABLE_SIZE 113
#define MVPOLL_KEYSTUB_TABLE_FLAGS HASH_FLAG_KEEP_LIST

#define _mvpoll_print_stat(revents) do{ errlog(ERR_WARNING,"Revents = 0x%08x \n",(revents)); \
                                       errlog(ERR_WARNING,"MVPOLLIN:%i MVPOLLOUT:%i MVPOLLHUP:%i MVPOLLERR:%i \n", \
                                              ((revents) & MVPOLLIN),  ((revents) & MVPOLLOUT), ((revents) & MVPOLLHUP), \
                                              ((revents) & MVPOLLERR)); \
                                       } while(0)

/**
 * mvpoll_keystub_t store auxillary information of the key's in the mvpoll_id_t
 * each key has a mvpoll_keystub
 */
typedef struct mvpoll_keystub {
    /**
     * the mvpoll event flags of this key
     */
    int            events;

    /**
     * The relay in which this key is related
     */
    relay_t*       relay;

    /**
     * boolean value
     * 1 if this key is for a sink of the relay
     * 0 if this key is for a source of the relay
     */
    int            issink;

} mvpoll_keystub_t;

static int  _vector_setup ( vector_t* vec );
static int  _vector_cleanup ( vector_t* vec );
static int  _vector_handle_message ( vector_t* vec, int revents );
static int  _vector_handle_src_event ( vector_t* vec, relay_t* relay, int revents );
static int  _vector_handle_src_error_event    ( vector_t* vec, relay_t* relay );
static int  _vector_handle_src_shutdown_event ( vector_t* vec, relay_t* relay );
static int  _vector_handle_src_input_event    ( vector_t* vec, relay_t* relay );
static int  _vector_handle_snk_event ( vector_t* vec, relay_t* relay, int revents );
static int  _vector_compress_relays ( vector_t* vec, relay_t* relay1, relay_t* relay2 );
static void _vector_check_for_compression( vector_t* vec, struct mvpoll_event* events, int num_events );

static int  _relay_add_queue ( relay_t* relay, event_t* event );
static int  _relay_remove_queue ( relay_t* relay, list_node_t* node );
static int  _relay_src_enable ( relay_t* relay );
static int  _relay_snk_enable ( relay_t* relay );
static int  _relay_src_disable ( relay_t* relay );
static int  _relay_snk_disable ( relay_t* relay );
static int  _relay_snk_close ( relay_t* relay );
static int  _relay_src_close ( relay_t* relay );

static int  _mvpoll_keystub_key_add  ( vector_t* vec, mvpoll_key_t* key, relay_t* relay, int issink );
static int  _mvpoll_keystub_key_sub  ( vector_t* vec, mvpoll_key_t* key );
static int  _mvpoll_keystub_add_events ( vector_t* vec, mvpoll_key_t* key, int events );
static int  _mvpoll_keystub_sub_events ( vector_t* vec, mvpoll_key_t* key, int events );

static int  _chain_debug_print_prefix ( int debug_level, list_t* chain, char* prefix );
static relay_t* _find_relay_with_source( vector_t* vec, source_t* src );
static relay_t* _find_relay_with_sink( vector_t* vec, sink_t* snk );


vector_t* vector_malloc ( void )
{
    vector_t* vec = calloc(1, sizeof(vector_t));

    if (!vec) {
        return errlogmalloc_null();
    }

    return vec;
}

vector_t* vector_create ( list_t* chain )
{
    vector_t* vec;

    if ( (vec = vector_malloc()) == NULL ) {
        return errlog_null(ERR_CRITICAL,"vector_malloc");
    }

    if ( vector_init(vec,chain) < 0 ) {
        vector_free(vec);
        return errlog_null(ERR_CRITICAL, "vector_init");
    }

    return vec;
}

int       vector_init ( vector_t* vec, list_t* chain )
{
    if (!vec || !chain) {
        return errlogargs();
    }

    vec->chain = chain;
    vec->max_timeout = -1;
    
    if (pipe(vec->msg_pipe)<0) {
        return perrlog("pipe");
    }
    
    /* Set both read & write fd to non-blocking */
    if ( unet_blocking_disable( vec->msg_pipe[0] ) < 0 )
        errlog( ERR_CRITICAL, "unet_blocking_disable" );
    if ( unet_blocking_disable( vec->msg_pipe[1] ) < 0 )
        errlog( ERR_CRITICAL, "unet_blocking_disable" );

    return 0;
}

int       vector_free ( vector_t* vec )
{
    if ( vec == NULL ) {
        return errlogargs();
    }

    free(vec);

    return 0;
}

int       vector_destroy ( vector_t* vec )
{
    if ( vec == NULL) {
        return errlogargs();
    }

    if (vec->msg_pipe[0]>=0 && close(vec->msg_pipe[0])<0)
        perrlog("close");
    vec->msg_pipe[0] = -1;
    if (vec->msg_pipe[1]>=0 && close(vec->msg_pipe[1])<0)
        perrlog("close");
    vec->msg_pipe[1] = -1;

    return 0;
}

int       vector_raze ( vector_t* vec )
{
    int err = 0;

    if ( vec == NULL) {
        return errlogargs();
    }
    
    if ( vector_destroy(vec) < 0 ) {
        perrlog("vector_destroy");
        err-=1;
    }

    if ( vector_free(vec) < 0 ) {
        perrlog("vector_free");
        err-=2;
    }

    return err;
}

int       vector ( vector_t* vec )
{
    int i,num_events;
    int timeout = -1;
    struct mvpoll_event events[MVPOLL_MAX_SIZE];
    /* The ticks can't start on zero or 1 because it is used to detect shutdown's on sinks
     * and sources that occur during the same tick.  If it did start with 0, there would be a slim
     * chance that the flag snk_shutdown and tick would match, which would be somewhat confusing.
     */
    int tick = 2;
    
    if (_vector_setup(vec)<0)
        return perrlog("vector_setup");

    /**
     * loop until termination
     */
    while (1) {
        tick++;
        /* tick should never be zero */
        if ( tick == 0 ) tick++;

        /**
         * Exit if no relay's remain
         */
        if (vec->live_relay_count<1) 
            break;

        timeout = vec->max_timeout;

        /**
         * wait on events
         */
        if ((num_events = mvpoll_wait(vec->mvp,events,MVPOLL_MAX_SIZE,timeout))<0) {
            perrlog("mvpoll_wait");
            if (errno != EINTR) 
                goto vector_out;
            else
                continue;
        }

        debug(10,"VECTOR(0x%016"PRIxPTR"): ----tick---- %08d\n", (uintptr_t) vec, tick );


        if (num_events == 0) {
            debug(9,"VECTOR(0x%016"PRIxPTR"): Session Timeout\n", (uintptr_t) vec );
            goto vector_out;
        }

        /**
         * handle events
         */
        for (i=0;i<num_events;i++) {
            mvpoll_key_t* key = (mvpoll_key_t*)events[i].key;
            if ( key == NULL ) {
                errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): NULL event key\n", (uintptr_t) vec );
                goto vector_out;
            }

            mvpoll_keystub_t* stub = (mvpoll_keystub_t*)key->arg;
            
            if (!stub) {
                errlog(ERR_WARNING,"VECTOR(0x%016"PRIxPTR"): Missing Mvpoll Stub (key:0x%016"PRIxPTR")\n", (uintptr_t) vec, (uintptr_t) events[i].key);
                goto vector_out;
            }

#if VECTOR_DEBUG
            debug(10,"VECTOR(0x%016"PRIxPTR"): -Event(%d)- (key: 0x%016"PRIxPTR") (stub: 0x%016"PRIxPTR") (mvp:0x%016"PRIxPTR") (relay:0x%016"PRIxPTR",%d) (events:0x%08x)\n",
                  (uintptr_t) vec, i,
                  (uintptr_t) events[i].key,
                  (uintptr_t) stub,
                  (uintptr_t) vec->mvp,
                  (uintptr_t) stub->relay, 
                  ( stub->relay == NULL ) ? 0 : list_length( &stub->relay->event_q ), events[i].events);
#endif
            /**
             * Msg key event
             */
            if (key == vec->msg_key) {
                switch (_vector_handle_message(vec, events[i].events)) {
                case VECTOR_MSG_SHUTDOWN:
                    goto vector_out;
                case VECTOR_MSG_NULL:
                    break;
                case -1:
                    errlog(ERR_CRITICAL,"Failed to handle message\n");
                    break;
                }
                continue;
            }
                
            /**
             * Normal key event
             */
            else {
                /* Update the key event mask */
                key->events = events[i].events;

                if ( stub->issink ) {
                    if ( !stub->relay->snk_shutdown ) {
                        if (_vector_handle_snk_event(vec,stub->relay, events[i].events)<0) {
                            errlog( ERR_CRITICAL, "_vector_handle_snk_event\n" );
                            goto vector_out;
                        }
                        if ( stub->relay->snk_shutdown == 1 )
                            stub->relay->snk_shutdown = tick;

                    } else {
                        if ( stub->relay->snk_shutdown == tick ) {
                            /* This is not an error(no vector_out), EG
                             * one tick that contains a source and then sink, and source
                             * event where the source receives an error which skips
                             * the relay and places it directly into the sink.
                             */
                            debug( 6, "VECTOR(0x%016"PRIxPTR"): src shutdown snk with event in same tick\n", (uintptr_t) vec );
                        } else {
                            errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): Mishandled Event(snk shutdown)\n", (uintptr_t) vec );
                            goto vector_out;
                        }
                    }
                } else {
                    if ( !stub->relay->src_shutdown ) {
                        if (_vector_handle_src_event(vec,stub->relay, events[i].events)<0) {
                            errlog(ERR_WARNING,"Mishandled Event\n");
                            goto vector_out;
                        }

                        /* Indicate that the sink was shutdown on this tick, if necessary */
                        if ( stub->relay->snk_shutdown == 1 ) {
                            stub->relay->snk_shutdown = tick;
                        }
                    } else {
                        if ( stub->relay->snk_shutdown == tick ) {
                            /* This is not an error(no vector_out), EG
                             * one tick that contains a sink and source
                             * event that where the sink event closes the
                             * source.
                             */
                            debug( 6, "VECTOR(0x%016"PRIxPTR"): snk shutdown then src event in same tick\n", (uintptr_t) vec );
                        } else {
                            errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): Mishandled Event(src shutdown)\n", (uintptr_t) vec );
                            goto vector_out;
                        }
                    }
                }
            }
        } // for each event

        _vector_check_for_compression( vec, events, num_events );
        
    } // while true
        
vector_out:
    
    debug(10,"VECTOR(0x%016"PRIxPTR"): Shutting down...\n", (uintptr_t) vec );
    if (_vector_cleanup(vec)<0)
        perrlog("_vector_cleanup");
    return 0;
}

int       vector_send_msg ( vector_t* vec, vector_msg_t msg, void* arg )
{
    char buf[sizeof(vector_msg_t)+sizeof(void*)];
    int nbyt;
    
    if (!vec)
        return errlogargs();

    if (vec->msg_pipe[1]<0)
        return errlog(ERR_CRITICAL,"Invalid message pipe: %i\n", vec->msg_pipe[1]);
    
    memcpy(buf,&msg,sizeof(vector_msg_t));
    memcpy(buf+sizeof(vector_msg_t),&arg,sizeof(void*));
           
    if ((nbyt = write(vec->msg_pipe[1],buf,sizeof(buf)))<0) 
        return perrlog("write");
    if (nbyt < sizeof(buf)) 
        return errlog(ERR_CRITICAL,"truncated write\n");

#if VECTOR_DEBUG
    debug(10,"Vector message (%i,0x%016"PRIxPTR") sent (fd:%i)\n",(int)msg, (uintptr_t) arg,vec->msg_pipe[1] );
#endif
    
    return nbyt;
}

void      vector_set_timeout ( vector_t* vec, int timeout_msec )
{
    if (!vec)
        errlogargs();
    else {
        if ( timeout_msec == -1 ) {
            vec->max_timeout = -1;
        } else {
            vec->max_timeout = timeout_msec;
        }
    }
}

void      vector_print ( vector_t* vec )
{
    _chain_debug_print_prefix( 0, vec->chain, "VECTOR: Description: " );
}

/**
 * vector_compress tells the vector engine to reorganize this vector to squishing together the sink and source.
 * 
 * Before a compression it can look like this:
 * client ... ( src1 --> relay1 --> snk1 ) incomingSocketQueue -> application -> outgoingSocketQueue -> ( src2 --> relay2 --> snk2 ) ... server
 * If you call compress_relays( snk1, src2) it will change to this:
 * client ... ( src1 --> relay3 --> snk2 ) ... server
 * 
 * Effectively removing the application between sink and source from the chain.
 *
 * It accepts the inner sink and source as arguments instead of the relay because thats what the application knows.
 * It will use these sink and source to find the appropriate relay.
 * When those relays are "idle" it will compresss them.
 * It must schedule it later so that the application can write any final events after calling compress_relays()
 */
void      vector_compress ( vector_t* vec, sink_t* sink, source_t* source )
{
    debug( 8, "vector_compress( snk: 0x%016"PRIxPTR", src: 0x%016"PRIxPTR" )\n", (uintptr_t)sink, (uintptr_t)source );

    if ( vec == NULL || sink == NULL || source == NULL ) {
        errlogargs();
        return;
    }

    /**
     * Add it to the list of relays to compress, the actual compression will take place later
     */
    if ( list_add_tail( &vec->compress_relays, sink ) < 0 ) {
        perrlog("list_add_tail");
        return;
    }
    if ( list_add_tail( &vec->compress_relays, source ) < 0 ) {
        perrlog("list_add_tail");
        return;
    }

    return;
}

static void _vector_check_for_compression( vector_t* vec, struct mvpoll_event* events, int num_events )
{
    if ( list_length( &vec->compress_relays ) < 2 )
        return;

    /**
     * Check for any relays that should be compressed
     */
    list_node_t* step;
    step = list_head( &vec->compress_relays );
    sink_t* sink = list_node_val(step);
    step = list_node_next( step );
    source_t* source = list_node_val(step);

    relay_t* relay1 = _find_relay_with_sink( vec, sink );
    relay_t* relay2 = _find_relay_with_source( vec, source );

    if ( relay1 == NULL || relay2 == NULL ) {
        errlog( ERR_CRITICAL, "Unable to find relay: sink: 0x%016"PRIxPTR" -> 0x%016"PRIxPTR"  source: 0x%016"PRIxPTR" -> 0x%016"PRIxPTR"\n",
                (uintptr_t)sink, (uintptr_t)relay1, (uintptr_t)source, (uintptr_t)relay2);
        void* value;
        if ( list_pop_head ( &vec->compress_relays, &value ) < 0 ) {
            perrlog("list_pop_head");
        }
        if ( list_pop_head ( &vec->compress_relays, &value ) < 0 ) {
            perrlog("list_pop_head");
        }
        return;
    } 

    /**
     * Check that the relays to be compressed aren't involved in any current events
     * This avoids accidently losing events when releasing the session and writing the last bit of data
     */
    int relays_involved_in_current_event = 0;
    int i;
    for (i=0;i<num_events;i++) {
        mvpoll_key_t* key = (mvpoll_key_t*)events[i].key;
        if ( key != NULL ) {
            mvpoll_keystub_t* stub = (mvpoll_keystub_t*)key->arg;
            if ( stub != NULL ) {
                if ( stub->relay == relay1 || stub->relay == relay2 )
                    relays_involved_in_current_event = 1;
            }
        }
    }
                
    /**
     * We should only compress the two relays if they are both idle and empty
     */
    if ( relay1->src_enabled && relay2->src_enabled &&
         !relay1->snk_enabled && !relay2->snk_enabled &&
         list_length(&relay1->event_q) == 0 && list_length(&relay2->event_q) == 0 &&
         !relays_involved_in_current_event ) {

        /**
         * Remove these two relays from the list
         */
        void* value;
        if ( list_pop_head ( &vec->compress_relays, &value ) < 0 ) {
            perrlog("list_pop_head");
        }
        if ( list_pop_head ( &vec->compress_relays, &value ) < 0 ) {
            perrlog("list_pop_head");
        }

        /**
         * Collapse the two relays into one
         */
        debug( 8, "found good opportunity to compress: 0x%016"PRIxPTR" -> 0x%016"PRIxPTR"\n", (uintptr_t)relay1, (uintptr_t)relay2 );
        if ( _vector_compress_relays( vec, relay1, relay2 ) < 0 ) {
            perrlog("_vector_compress_relays");
        }
    }
}

static int _vector_compress_relays ( vector_t* vec, relay_t* relay1, relay_t* relay2 )
{
    _chain_debug_print_prefix( 8, vec->chain, "VECTOR: BEFORE COMPRESS" );

    if ( vec == NULL || relay1 == NULL || relay2 == NULL || relay1 == relay2 ) {
        return errlogargs();
    }
    if ( relay1->src_shutdown ) {
        return errlog( ERR_WARNING,"Can not compress relays. relay1 src is shutdown\n" );
    }
    if ( relay1->snk_shutdown ) {
        return errlog( ERR_WARNING,"Can not compress relays. relay1 snk is shutdown\n" );
    }
    if ( relay2->src_shutdown ) {
        return errlog( ERR_WARNING,"Can not compress relays. relay2 src is shutdown\n" );
    }
    if ( relay2->snk_shutdown ) {
        return errlog( ERR_WARNING,"Can not compress relays. relay2 snk is shutdown\n" );
    }
    if ( list_length(&relay1->event_q) > 0 ) {
        return errlog( ERR_WARNING,"Can not compress relays. relay1 has %i events in event_q\n", list_length(&relay1->event_q) );
    }
    if ( list_length(&relay2->event_q) > 0 ) {
        return errlog( ERR_WARNING,"Can not compress relays. relay2 has %i events in event_q\n", list_length(&relay2->event_q) );
    }

    // The goal is to "compress" relay1 and relay2.
    // This means we go from this:
    //   ( src1 -> snk1 ) node/app ( src2 -> snk2 )
    // To:
    //   ( src1 -> snk2 )
    // This effectively removes the node/app from this pipeline by connecting relay1's src to relay2's sink in a new relay3.

    // Gameplan.
    // This is called when the app wants to remove (release) itself from the pipeline.
    // To do this we will create a new relay (relay3) which is src1 to snk2 and add it to the vector list
    // Then we remove relay1 and relay2

    // remove relay1 and relay2 from chain
    int current_length = list_length( vec->chain );
    if ( list_remove_val( vec->chain, relay1 ) < 0 ) {
        return errlog( ERR_CRITICAL,"Failed to remove relay1: 0x%016"PRIxPTR"\n", (uintptr_t)relay1 );
    }
    if ( list_remove_val( vec->chain, relay2 ) < 0 ) {
        return errlog( ERR_CRITICAL,"Failed to remove relay2: 0x%016"PRIxPTR"\n", (uintptr_t)relay2 );
    }
    int new_length = list_length( vec->chain );
    if ( current_length != new_length + 2 ) {
        return errlog(ERR_CRITICAL,"Failed to find relays in chain\n");
    }

    source_t* src1 = relay1->src;
    sink_t*   snk1 = relay1->snk;
    source_t* src2 = relay2->src;
    sink_t*   snk2 = relay2->snk;
    mvpoll_key_t* src1_key = src1->get_event_key( src1 );  
    mvpoll_key_t* snk1_key = snk1->get_event_key( snk1 );  
    mvpoll_key_t* src2_key = src2->get_event_key( src2 );  
    mvpoll_key_t* snk2_key = snk2->get_event_key( snk2 );  
    mvpoll_keystub_t* snk1_keystub = (mvpoll_keystub_t*)snk1_key->arg;
    mvpoll_keystub_t* src2_keystub = (mvpoll_keystub_t*)src2_key->arg;

    /**
     * remove keystub from mvpoll for snk1 and src2
     */
    snk1_keystub->relay = NULL; /* set to NULL so it doesnt complain "Removing stub on open source" */
    src2_keystub->relay = NULL; /* set to NULL so it doesnt complain "Removing stub on open source" */
    if ( _mvpoll_keystub_key_sub( vec, snk1_key ) < 0 ) {
        return errlog(ERR_CRITICAL,"Unable to remove relay1 snk key\n");
    }
    if ( _mvpoll_keystub_key_sub( vec, src2_key ) < 0 ) {
        return errlog(ERR_CRITICAL,"Unable to remove relay1 src key\n");
    }
    // mvpoll_print( vec->mvp );

    /**
     * Create replacement relay3
     */
    relay_t* relay3 = relay_create();
    relay3->src = src1;
    relay3->snk = snk2;
    relay3->my_vec = vec;
    relay3->src_enabled = 1;
    relay3->snk_enabled = 0;
    relay3->src_shutdown = 0;
    relay3->snk_shutdown = 0;

    if ( list_add_tail( vec->chain, relay3 ) < 0 ) {
        return perrlog("list_add_tail");
    }
    vec->live_relay_count--; /* removed 2, added 1 = -1 */

    /**
     * Change remaining src/snk to point at relay3
     */
    mvpoll_keystub_t* stub;
    stub = (mvpoll_keystub_t*)snk2_key->arg; 
    stub->relay = relay3; /* change this sink to belong to relay1 */
    stub = (mvpoll_keystub_t*)src1_key->arg; 
    stub->relay = relay3; /* change this sink to belong to relay1 */

    /**
     * Free unused resource associated with relay1 & relay2
     * But not the re-used source and sink
     */
    free( snk1_keystub );
    free( src2_keystub );

    snk1->raze( snk1 );
    src2->raze( src2 );
    relay_free( relay1 );
    relay_free( relay2 );
    
    _chain_debug_print_prefix( 8, vec->chain, "VECTOR: AFTER COMPRESS" );
    return 0;
}

static int  _vector_handle_message ( vector_t* vec, int revents )
{
    char buf[sizeof(vector_msg_t)+sizeof(void*)];
    vector_msg_t* msg = (vector_msg_t*)buf;

#if VECTOR_DEBUG
    void** arg = (void**)(buf+sizeof(vector_msg_t));
#endif

    int ret = -1;
    
    if (!(revents & MVPOLLIN)) {
        _mvpoll_print_stat(revents);
        return -1;
    }

    if ((ret = read(vec->msg_pipe[0],&buf,sizeof(buf)))<sizeof(buf))
        return perrlog("read");

#if VECTOR_DEBUG 
    debug(10, "VECTOR(0x%016"PRIxPTR"): Server message (%i,0x%016"PRIxPTR") received\n", (uintptr_t) vec, *msg, (uintptr_t) *arg );
#endif
    
    switch(*msg) {
    case VECTOR_MSG_SHUTDOWN:
        return VECTOR_MSG_SHUTDOWN;

    case VECTOR_MSG_NULL:
        errlog(ERR_CRITICAL,"Null Message Received\n");
        break;

    default:
        errlog(ERR_CRITICAL,"Unknown Message: %i\n", *msg);
        break;
    }
    
    return VECTOR_MSG_NULL;
}

static int  _vector_handle_src_event ( vector_t* vec, relay_t* relay, int revents )
{
    if (!vec)
        return errlogargs();

    if (!relay) 
        return errlog(ERR_CRITICAL, "Stub doesn't contain a relay\n" );

    if (relay->src_shutdown) {
        /* This is only an error if the sink hasn't been shutdown yet */
        errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): Got source event for closed source (relay: 0x%016"PRIxPTR", revents: 0x%08x)\n", 
                (uintptr_t) vec, (uintptr_t) relay, revents );
        _mvpoll_print_stat(revents);
        
        /* This has a lower debug number since it only occurs when there is an error */
        _chain_debug_print_prefix( 3, vec->chain, "VECTOR: Description: ");
        
        return -1;
    }

    /**
     * handle source events
     */
    if ( revents & MVPOLLERR ) {
        return _vector_handle_src_error_event( vec, relay );
    } else if ( revents & MVPOLLIN ) {
        return _vector_handle_src_input_event( vec, relay );
    } else if ( revents & MVPOLLHUP ) {
        return _vector_handle_src_shutdown_event( vec, relay );
    } else {
        errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): Got source event with invalid event mask: 0x%08x", 
                (uintptr_t) vec, revents );
        /* do the default thing anyway */
        return _vector_handle_src_input_event( vec, relay );
    }
}

static int  _vector_handle_src_input_event    ( vector_t* vec, relay_t* relay )
{
    event_t* evt;

    if ( !relay->src_enabled ) {
        errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): Got input source event from disabled source (relay: 0x%016"PRIxPTR")\n",
                (uintptr_t) vec, (uintptr_t) relay );

        /* This has a lower debug number since it only occurs when there is an error */
        _chain_debug_print_prefix( 3, vec->chain, "VECTOR: Description: ");
        // relay_debug_print( 3, "VECTOR: relay: ", relay );
        
        return -1;
    }

    /* INPUT events cannot extend the relay queue */
    if ( list_length(&relay->event_q) >= relay->event_q_max_len )
        return errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): Constraint failed. Relay full.\n", (uintptr_t) vec );

    /* Retrieve the event and verify it is non-null */
    if (( evt = relay->src->get_event( relay->src, relay->snk )) == NULL )
        return errlog( ERR_CRITICAL, "Failed to get Event\n" );

    if ( relay->event_hook )
        relay->event_hook( vec, relay, evt, relay->event_hook_arg );

    if ( _relay_add_queue( relay, evt ) < 0 )
        return errlog( ERR_CRITICAL, "_relay_add_queue\n" );

    /* If this is not a shutdown event, nothing left to do */
    if ( event_is_shutdown( evt->type )) {
        /* Should an error here return an error, or just shutdown */
        if ( _relay_src_close( relay ) < 0 )
            return errlog( ERR_CRITICAL, "_relay_src_close\n" );
    } 

    return 0;
}

static int  _vector_handle_src_error_event    ( vector_t* vec, relay_t* relay )
{
    event_t* evt;

    /* Retrieve the event, this is at shutdown, so it may return NULL */
    if (( evt = relay->src->get_event( relay->src, relay->snk )) == NULL )
        errlog( ERR_WARNING, "relay->src->get_event\n" );

    /* This must be a shutdown event */
    if ( evt == NULL || !event_is_shutdown( evt->type )) {
        mvpoll_key_t* key;
        int key_type;
        
        /* This check guarantees that a NULL key is not dereferenced */
        if (( key = relay->src->get_event_key( relay->src )) == NULL ) {
            errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): Null key\n", (uintptr_t) vec );
            key_type = 0xDEAD;
        } else {
            key_type = key->type;
        }
        
        if (evt == NULL) {
            errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): ERR without a shutdown event (event NULL/%08x)\n", (uintptr_t) vec, key_type );
        } else {
               /* This has been changed from an errlog to a debug
                  statment. This is not an abnormal case - epoll will
                  give us an error flag when a reset has been given to
                  us, but will give us the data first if there data in
                  the buffer. However we are not guaranteed to have
                  room in the relay for this event. Since a
                  error/reset is coming and we don't guarantee data
                  integrity when a error/reset is coming, we just drop
                  the event totally, and replace with an error
                  shutdown event - dmorris */
            debug( 1, "VECTOR(0x%016"PRIxPTR"): ERR without a shutdown event (event type: %08x/%08x)\n", (uintptr_t) vec, evt->type, key_type );
            
            /* Free the event, and return, if there is an error, no need to queue it */
            evt->raze( evt );
        }
                
        if (( evt = event_create( EVENT_BASE_ERROR_SHUTDOWN )) == NULL )
            return errlog( ERR_CRITICAL, "event_create\n" );
    }

    if ( relay->event_hook )
        relay->event_hook( vec, relay, evt, relay->event_hook_arg );
    
    if ( _relay_add_queue( relay, evt ) < 0 )
        return errlog( ERR_CRITICAL, "_relay_add_queue\n" );
    
    if ( _relay_src_close( relay ) < 0 )
        return errlog( ERR_CRITICAL, "_relay_src_close\n" );


    return 0;
}

static int  _vector_handle_src_shutdown_event ( vector_t* vec, relay_t* relay )
{
    event_t* evt;

    /* Retrieve the event, this is at shutdown, so it may return NULL */
    if (( evt = relay->src->get_event( relay->src, relay->snk )) == NULL )
        errlog( ERR_WARNING, "relay->src->get_event\n" );

    /* This must be a shutdown event */
    if (( evt == NULL ) || !event_is_shutdown( evt->type )) {
        mvpoll_key_t* key;
        //int key_type;

        /* This check guarantees that a NULL key is not dereferenced */
        if (( key = relay->src->get_event_key( relay->src )) == NULL ) {
            /* This check just guarantees that the a NULL key is not dereferenced */
            errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): Null key\n", (uintptr_t) vec );
            //key_type = 0xDEAD;
        } else {
            //key_type = key->type;
        }

        if (evt == NULL) {
            /* this happens semi-regularly. Do not print a warning. */
            //errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): HUP without a shutdown event (event NULL/%08x)\n", (uintptr_t) vec, key_type );
        } else {
            /* this happens semi-regularly. Do not print a warning. */
            //errlog( ERR_WARNING, "VECTOR(0x%016"PRIxPTR"): HUP without a shutdown event (event type: %08x/%08x)\n", (uintptr_t) vec, evt->type, key_type );
        
            /* Free the event */
            evt->raze( evt );
        }
        
        if (( evt = event_create( EVENT_BASE_SHUTDOWN )) == NULL )
            return errlog( ERR_CRITICAL, "event_create\n" );
    }

    if ( relay->event_hook )
        relay->event_hook( vec, relay, evt, relay->event_hook_arg );
    
    if ( _relay_add_queue( relay, evt ) < 0 )
        return errlog( ERR_CRITICAL, "_relay_add_queue\n" );

    if ( _relay_src_close( relay ) < 0 )
        return errlog( ERR_CRITICAL, "_relay_src_close\n" );

    return 0;
}



static int  _vector_handle_snk_event ( vector_t* vec, relay_t* relay, int revents )
{
    if (!vec || !relay)
        return errlogargs();
    if (relay->snk_shutdown) 
        return errlog(ERR_CRITICAL,"Got sink event for closed sink: 0x%08x\n",revents);

    if (revents & MVPOLLHUP || revents & MVPOLLERR) {
        if (_relay_snk_close(relay)<0)
            perrlog("_relay_snk_close");
        return 0;
    }
    if (!relay->snk_enabled) {
        errlog(ERR_CRITICAL,"Got sink event for disabled sink (relay: 0x%016"PRIxPTR", revents: 0x%08x)\n", (uintptr_t) relay, revents );
        _mvpoll_print_stat(revents);
        _chain_debug_print_prefix( 3, vec->chain, "VECTOR: Description: " );
        // relay_debug_print(10,"VECTOR: relay: ",relay);
        return -1;
    }

    else if (revents & MVPOLLOUT) {
        list_node_t* node;
        event_t* evt;
        event_action_t action;
        
        if (list_length(&relay->event_q) == 0)
            return errlog(ERR_CRITICAL,"Constraint failed. No outgoing events.\n");
        if (!(node = list_tail(&relay->event_q)))
            return errlog(ERR_CRITICAL,"Constraint failed. list_tail failed.\n");
        if (!(evt = list_node_val(node)))
            return errlog(ERR_CRITICAL,"Constraint failed. Null event.\n");

        action = relay->snk->send_event(relay->snk,evt);
        //debug( 8, "send_event: %d\n", action );
            
        switch (action) {
        case EVENT_ACTION_ERROR:
            evt->raze(evt);
            if (_relay_remove_queue(relay,node)<0)
                return errlog( ERR_CRITICAL, "_relay_remove_queue\n");

            return errlog(ERR_CRITICAL,"Error in sending event, Dropping\n");
        case EVENT_ACTION_NOTHING:
            break;
        case EVENT_ACTION_DEQUEUE:
            evt->raze(evt);
            if (_relay_remove_queue(relay,node)<0)
                return errlog( ERR_CRITICAL, "_relay_remove_queue\n");
            break;
        case EVENT_ACTION_SHUTDOWN:
            evt->raze(evt);
            if (_relay_remove_queue(relay,node)<0)
                return errlog( ERR_CRITICAL, "_relay_remove_queue\n");
            if (list_length(&relay->event_q)>0) 
                 errlog(ERR_WARNING,"Non-empty Queue after shutdown, dropping all events\n");
            if (_relay_snk_close(relay)<0)
                perrlog("_relay_snk_close");
            break;
        }
    }
    else {
        errlog(ERR_CRITICAL,"Unknown Event\n");
        _mvpoll_print_stat(revents);
        return -1;
    }
    return 0;
}

static int  _vector_setup ( vector_t* vec )
{
    list_t* chain;
    list_node_t* step;
    relay_t* relay;
    mvpoll_key_t* key;
    
    if (!vec)
        return errlogargs();
    
    /**
     * Initialize the dead relay list
     */
    if (list_init(&vec->dead_relays,0)<0)
        return perrlog("list_init");
    if (list_init(&vec->compress_relays,0)<0)
        return perrlog("list_init");
    
    /**
     * Create the Mvpoll key
     */
    if ((vec->mvp = mvpoll_create(MVPOLL_MAX_SIZE))<0)
        return perrlog("mvpoll_create");

    chain = vec->chain;
    _chain_debug_print_prefix(9,chain,"VECTOR: Description: ");
    
    /**
     * add the message key
     */
    if (!(vec->msg_key = mvpoll_key_fd_create(vec->msg_pipe[0])))
        return perrlog("mvpoll_key_fd_create");
    if (_mvpoll_keystub_key_add(vec,vec->msg_key,NULL,0)<0) 
        return perrlog("_mvpoll_keystub_add_fd");
    if (_mvpoll_keystub_add_events(vec,vec->msg_key, MVPOLL_INPUT_SET)<0) 
        return perrlog("_mvpoll_keystub_add_events");
        
    vec->live_relay_count = list_length(chain);

    /**
     * add all the keys, add their mvpoll stub
     */
    for (step = list_head(chain) ; step ; step = list_node_next(step)) {
        relay = list_node_val(step);
        
        if (!relay) 
            return errlog(ERR_CRITICAL,"Constraint failed\n");

        relay->my_vec = vec;
        
        /**
         * Handle Source key:
         * If not in mvpoll_keystub table, add it.
         * Set the relay(_src) to this relay because this key is the source of this relay
         */
        if (!(key = relay->src->get_event_key(relay->src)))
            return perrlog("get_event_key");
        if (_mvpoll_keystub_key_add(vec,key,relay,0)<0) 
            return perrlog("_mvpoll_keystub_set");

        /**
         * Start listening for input
         */
        if (_mvpoll_keystub_add_events(vec,key,MVPOLL_INPUT_SET)<0)
            return perrlog("_mvpoll_keystub_add_events");

        /**
         * Now handle the Sink key
         */
        if (!(key = relay->snk->get_event_key(relay->snk)))
            return perrlog("get_event_key");
        if (_mvpoll_keystub_key_add(vec,key,relay,1)<0) 
            return perrlog("_mvpoll_keystub_key_add");

        /**
         * Start listening for error and shutdown conditions
         */
        if (_mvpoll_keystub_add_events(vec,key,MVPOLLERR | MVPOLLHUP))
            return perrlog("_mvpoll_keystub_add_events");

        /**
         * Enable the Source, Disable the Snk
         */
        relay->src_shutdown = 0;
        relay->snk_shutdown = 0;
        relay->src_enabled = 1;
        relay->snk_enabled = 0;
    }

    return 0;
}

static int  _vector_cleanup ( vector_t* vec )
{
    relay_t* relay;
    list_node_t* step;
    mvpoll_key_t* key;
    mvpoll_keystub_t* stub;

    /**
     * Stop all relays (close source; close sink)
     */
    if ( vec->chain == NULL )
        errlog(ERR_WARNING,"Empty relay list\n");
    else {

        /**
         * For each relay in the list
         */
        
        for (step = list_head(vec->chain); step; step = list_node_next(step)) {
            relay = (relay_t*)list_node_val(step);
                            
            /**
             * Close the source
             * remove the key from mvpoll
             */
            key = relay->src->get_event_key(relay->src);
            stub = (mvpoll_keystub_t*)key->arg;

            if (!relay->src_shutdown)
                _relay_src_close(relay);
            key->arg = NULL;
            free(stub);
            
            /**
             * Close the sink
             * remove the key from mvpoll
             */
            key = relay->snk->get_event_key(relay->snk);
            stub = (mvpoll_keystub_t*)key->arg;

            if (!relay->snk_shutdown) 
                _relay_snk_close(relay);
            key->arg = NULL;
            free(stub);
        }
    }
    
    if (vec->live_relay_count > 0)
        errlog(ERR_CRITICAL,"Failed to close all relays. (%i active)\n",vec->live_relay_count);

    /**
     * Cleanup Dead Relays
     */
    for (step = list_head(&vec->dead_relays); step; step = list_node_next(step)) {
        relay = list_node_val(step);

        if (!relay) {
            errlog(ERR_CRITICAL,"Constraint failed. Null relay\n");
            continue;
        }

        /**
         * List of things to do:
         * Destroy the source and sink
         * Destroy and free the relay itself
         * (which free's events in queue, and the queue)
         */
        if (relay->src) 
            relay->src->raze(relay->src);
        if (relay->snk)
            relay->snk->raze(relay->snk);
        relay_free(relay);
    }

    /**
     * Remove special key for msg_pipe
     */
    stub = (mvpoll_keystub_t*) vec->msg_key->arg;
    if (_mvpoll_keystub_key_sub(vec,vec->msg_key)<0) 
        perrlog("_mvpoll_keystub_key_sub");
    mvpoll_key_raze(vec->msg_key);
    free(stub);

    if (mvpoll_raze(vec->mvp)<0)
        perrlog("mvpoll_raze");

    if (list_destroy(&vec->dead_relays)<0)
        perrlog("list_destroy");
    if (list_destroy(&vec->compress_relays)<0)
        perrlog("list_destroy");
    
    return 0;
}

/**
 * Adds event the queue
 * Enables writing of sink (if needed)
 * Disables further reading of source if queue is full
 */
static int _relay_add_queue ( relay_t* relay, event_t* event )
{
    /**
     * Error events skip the relay and go directly to the sink.
     * Don't worry about clearing the relay, any events in there are automatically
     * removed at the end of vectoring.
     */
    if ( event_is_shutdown_error( event->type )) {
        /* Push the error event to the next sink */
        if ( !relay->snk_shutdown ) {
            event_action_t action;
            action = relay->snk->send_event( relay->snk, event );
            switch ( action ) {
            case EVENT_ACTION_SHUTDOWN:
                break;
            case EVENT_ACTION_ERROR:
            case EVENT_ACTION_NOTHING:
            case EVENT_ACTION_DEQUEUE:
            default:
                errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): Non-shutdown action(%d) to an error crumb",
                        (uintptr_t) relay->my_vec, action );
            }

            /* Raze the event */
            event->raze( event );

            /* Close the sink */
            if ( _relay_snk_close( relay ) < 0 ) perrlog( "_relay_snk_close" );
        } else {
            /* Raze the event */
            event->raze( event );

            /* This is an error because a source cannot be open if the corresponding sink is closed. */
            errlog( ERR_CRITICAL, "VECTOR(0x%016"PRIxPTR"): error crumb for shutdown sink\n", (uintptr_t) relay->my_vec );
        }        
    } else {
        if (list_add_head(&relay->event_q,(void*)event)<0)
            return errlog(ERR_CRITICAL,"Event Dropped\n");
        
        if (list_length(&relay->event_q) >= relay->event_q_max_len)
            if (_relay_src_disable(relay)<0) return -1;
        
        if (list_length(&relay->event_q) == 1)
            if (_relay_snk_enable(relay)<0) return -1;
    }

    return 0;
}

/**
 * Removes event the queue
 * Enables reading of source (if needed)
 * Disables futher writing of sink if queue is empty 
 */
static int _relay_remove_queue ( relay_t* relay, list_node_t* node )
{
    if (list_remove(&relay->event_q,node)<0)
        return errlog(ERR_CRITICAL,"Failed to dequeue event\n");

    if (list_length(&relay->event_q) <= 0)
        if (_relay_snk_disable(relay)<0) return -1;

    if (list_length(&relay->event_q) == (relay->event_q_max_len-1) && !relay->src_shutdown )
        if (_relay_src_enable(relay)<0) return -1;

    return 0;
}

/**
 * Enables the source of a relay
 * Changes all flags and adjusts mvpoll events accordingly
 */
static int _relay_src_enable ( relay_t* relay )
{
    mvpoll_key_t* key;
    if (relay->src_enabled) return 0;

    if (!(key = relay->src->get_event_key(relay->src)))
        return errlog(ERR_CRITICAL,"Invalid key: 0x%016"PRIxPTR"\n",(uintptr_t) key );
    if (relay->src_shutdown)
        return errlog(ERR_CRITICAL,"Enable on Shutdown Source: key=0x%016"PRIxPTR".\n", (uintptr_t) key );
    
#if VECTOR_DEBUG
    debug( 10, "VECTOR(0x%016"PRIxPTR"): Enable Read on 0x%016"PRIxPTR"\n", (uintptr_t) relay->my_vec, (uintptr_t) key );
#endif

    relay->src_enabled = 1;
    if (_mvpoll_keystub_add_events(relay->my_vec, key, MVPOLLIN)<0)
        return perrlog("_mvpoll_keystub_add_events");

    return 0;
}

/**
 * Enables the sink of a relay
 * Changes all flags and adjusts mvpoll events accordingly
 */
static int _relay_snk_enable ( relay_t* relay )
{
    mvpoll_key_t* key;
    if (relay->snk_enabled) return 0;

    if (relay->snk_shutdown)
        return errlog(ERR_CRITICAL,"Enable on Shutdown Sink.\n");
    if (!(key = relay->snk->get_event_key(relay->snk))) 
        return errlog(ERR_CRITICAL,"Invalid FD: 0x%016"PRIxPTR".\n",(uintptr_t) key );

#if VECTOR_DEBUG
    debug(10,"VECTOR(0x%016"PRIxPTR"): Enable Write on key: 0x%016"PRIxPTR"  (relay: 0x%016"PRIxPTR")\n", (uintptr_t) relay->my_vec, (uintptr_t) key, (uintptr_t) relay );
#endif
    
    relay->snk_enabled = 1;
    if (key != NULL && _mvpoll_keystub_add_events(relay->my_vec, key, MVPOLLOUT)<0)
        return perrlog("_mvpoll_keystub_add_events");

    return 0;
}

/**
 * Disables the source of a relay
 * Changes all flags and adjusts mvpoll events accordingly
 */
static int _relay_src_disable ( relay_t* relay )
{
    mvpoll_key_t* key;
    if (!relay->src_enabled) return 0;

    if (!(key = relay->src->get_event_key(relay->src)))
        return 0; /* Source is closed - mvpoll auto removed it */
    if (relay->src_shutdown)
        return errlog(ERR_CRITICAL,"Disable on Shutdown Source.\n");

#if VECTOR_DEBUG
    debug( 10, "VECTOR(0x%016"PRIxPTR"): Disable Read on key: 0x%016"PRIxPTR"\n", (uintptr_t) relay->my_vec, (uintptr_t) key );
#endif
    
    relay->src_enabled = 0;
    if (_mvpoll_keystub_sub_events(relay->my_vec, key, MVPOLLIN)<0)
        return perrlog("_mvpoll_keystub_sub_events");
    
    return 0;
}

/**
 * Disables the sink of a relay
 * Changes all flags and adjusts mvpoll events accordingly
 */
static int _relay_snk_disable ( relay_t* relay )
{
    mvpoll_key_t* key;
    if (!relay->snk_enabled) return 0;
    
    if (!(key = relay->snk->get_event_key(relay->snk)))
        return 0; /* Sink is closed - mvpoll auto removed it */
    if (relay->snk_shutdown)
        return errlog(ERR_CRITICAL,"Disable on Shutdown Sink.\n");

#if VECTOR_DEBUG
    debug(10, "VECTOR(0x%016"PRIxPTR"): Disable Write on key: 0x%016"PRIxPTR"\n", (uintptr_t) relay->my_vec, (uintptr_t) key);
#endif
    
    relay->snk_enabled = 0;
    if (_mvpoll_keystub_sub_events(relay->my_vec, key, MVPOLLOUT)<0)
        return perrlog("_mvpoll_keystub_sub_events");

    return 0;
}

/**
 * First we shutdown the source (if it isnt already shutdown)
 * Then we schedule an event in the queue
 * Note: We may overfill the queue, because this is an extraordinary event
 * shutdown events are now vectored just like other events, therefore if the
 * user wanted to schedule an event, it would have been done already.
 */
static int _relay_src_close ( relay_t* relay )
{
    mvpoll_key_t* key;
    
    /**
     * Close the Source
     */
    if (!relay->src_shutdown) {
        if (_relay_src_disable(relay)<0)
            perrlog("_relay_src_disable");

        debug( 10, "VECTOR(0x%016"PRIxPTR"): Closing Source (0x%016"PRIxPTR")\n",
               (uintptr_t) relay->my_vec, 
               (uintptr_t) relay->src->get_event_key(relay->src));
        
        if (relay->src->shutdown(relay->src)<0)
            errlog(ERR_CRITICAL,"Failed to close source\n");

        /* You have to set shutdown before calling key_sub, or else
         * the sanity check in key_sub will fail.  */
        relay->src_shutdown = 1;
        
        if (( key = relay->src->get_event_key( relay->src )) == NULL )
            return errlog( ERR_CRITICAL, "relay->src->get_event_key\n" );
        else {
            /* Remove the stub from mvpoll */
            if (_mvpoll_keystub_key_sub(relay->my_vec,key)<0)
                return errlog(ERR_CRITICAL,"Unable to remove mvpoll stub\n");
        }
    }
    
    return 0;    
}

/**
 * First we shutdown the sink
 * This potientially puts the relay in an invalid state
 * (source open, sink shutdown)
 * So we immediately close the source if it is open.
 */
static int _relay_snk_close (relay_t* relay)
{
    mvpoll_key_t* key;

    if (!relay->snk_shutdown) {
        if (_relay_snk_disable(relay)<0)
            perrlog("_relay_snk_disable");

        debug(10,"VECTOR(0x%016"PRIxPTR"): Closing Sink (0x%016"PRIxPTR")\n",
              (uintptr_t) relay->my_vec, 
              (uintptr_t) relay->snk->get_event_key(relay->snk));

        if (relay->snk->shutdown(relay->snk)<0)
            errlog(ERR_CRITICAL,"Failed to close source\n");

        /* You have to set shutdown before calling key_sub, or else
         * the sanity check in key_sub will fail.  */
        relay->snk_shutdown=1;

        if (( key = relay->snk->get_event_key( relay->snk )) == NULL )
            errlog( ERR_CRITICAL, "relay->src->get_event_key\n" );
        else {
            /* Remove the stub from mvpoll */
            if (_mvpoll_keystub_key_sub(relay->my_vec,key)<0)
                errlog(ERR_CRITICAL,"Unable to remove mvpoll stub\n");
        }
    }
    relay->snk_shutdown=1;

    /**
     * Leftover events are freed in relay_free at cleanup time
     */
    
    /**
     * Close the Source of this relay as well
     */
    if (!relay->src_shutdown) 
        _relay_src_close( relay );
    
    /**
     * Sanity check, plus retire relay
     */
    if (relay->my_vec) {
        vector_t* vec = relay->my_vec;
        debug(10,"VECTOR(0x%016"PRIxPTR"): Retiring relay (%i/%i remain) (0x%016"PRIxPTR")\n",
              (uintptr_t) vec,
              vec->live_relay_count-1,
              list_length(&vec->dead_relays)+vec->live_relay_count,
              (uintptr_t) relay);
        if (list_add_tail(&vec->dead_relays,relay)<0)
            perrlog("list_add");
        vec->live_relay_count--;
    }

    return 0;
}

/**
 * Add an fd to the mvpoll_keystub table of type 'type'.
 * You will still need to add events and set relay_src and relay_snk
 */
static int  _mvpoll_keystub_key_add ( vector_t* vec, mvpoll_key_t* key, relay_t* relay, int issink )
{
    mvpoll_keystub_t* stub;

    if (key == NULL || !vec)
        return errlogargs();
    
    stub = malloc(sizeof(mvpoll_keystub_t));
    if (!stub)
        return errlogmalloc();

    stub->relay   = relay;
    stub->events  = 0;
    stub->issink  = issink;
    key->arg      = stub;
    
    if (mvpoll_ctl(vec->mvp,MVPOLL_CTL_ADD,key,0)<0)
        return perrlog("mvpoll_ctl");

    return 0;
}

/**
 * This will remove all FD state from the mvpoll_keystub table
 * All relay's this FD participates in should be dead
 */
static int  _mvpoll_keystub_key_sub ( vector_t* vec, mvpoll_key_t* key )
{
    mvpoll_keystub_t* stub;

    if (mvpoll_ctl(vec->mvp,MVPOLL_CTL_DEL,key,0)<0)
        return perrlog("mvpoll_ctl");

    stub = (mvpoll_keystub_t*)key->arg;

    debug( 8, "_mvpoll_keystub_key_sub: 0x%016"PRIxPTR"\n", (uintptr_t)stub );

    /**
     * Sanity check
     * We do this when compressing relays
     */
    if (stub->relay) {
        if (!stub->issink && !stub->relay->src_shutdown)
            errlog(ERR_CRITICAL,"Removing stub on open source\n");
        if (stub->issink  && !stub->relay->snk_shutdown)
            errlog(ERR_CRITICAL,"Removing stub on open sink\n");
    }

    return 0;
}

/**
 * Add (mvpoll) events to a given key
 */
static int  _mvpoll_keystub_add_events ( vector_t* vec, mvpoll_key_t* key, int events )
{
    mvpoll_keystub_t* stub;
    int orig_events;
    
    if (!vec || !key)
        return errlogargs();
    
    if (!(stub = (mvpoll_keystub_t*)key->arg))
        return errlogcons();

    orig_events = stub->events;
    stub->events |= events;

    /**
     * If actually changed, call mvpoll_ctl
     */
    if (stub->events !=  orig_events) {
/* #if VECTOR_DEBUG */
/*         debug(10,"VECTOR: mvpoll_ctl(%i,MVPOLL_CTL_MOD,0x%08x,0x%08x)\n",vec->mvp,key,stub->events); */
/* #endif */
        if (mvpoll_ctl(vec->mvp,MVPOLL_CTL_MOD,key,stub->events)<0)
            return perrlog("mvpoll_ctl");
    }
        
    return 0;
}

/**
 * Remove (mvpoll) events to a given key
 */
static int  _mvpoll_keystub_sub_events ( vector_t* vec, mvpoll_key_t* key, int events )
{
    mvpoll_keystub_t* stub = (mvpoll_keystub_t*)key->arg;
    int orig_events;
    
    if (!stub)
        return errlogcons();

    orig_events = stub->events;
    stub->events &= ~events;

    /**
     * If actually changed, call mvpoll_ctl
     */
    if (stub->events !=  orig_events) {
/* #if VECTOR_DEBUG */
/*         debug(10,"VECTOR: mvpoll_ctl(%i,MVPOLL_CTL_MOD,0x%08x,0x%08x)\n",vec->mvp,key,stub->events); */
/* #endif */
        if (mvpoll_ctl(vec->mvp,MVPOLL_CTL_MOD,key,stub->events)<0)
            return perrlog("mvpoll_ctl");
    }

    return 0;
}

/**
 * Prints a chain
 */
static int  _chain_debug_print_prefix ( int debug_level, list_t* chain, char* prefix )
{
    int i;
    list_node_t* step;
    
    if (!chain) 
        return errlogargs();

    if ( debug_level > debug_get_mylevel())
        return 0;

    debug(debug_level,"%s: Chain: 0x%016"PRIxPTR"\n", prefix, (uintptr_t)chain);
    for (i=0, step = list_head(chain) ; step ; i++, step = list_node_next(step)) {
        relay_t* relay = list_node_val(step);

        if (!relay || !relay->src || !relay->snk || 
            !relay->src->get_event_key || !relay->snk->get_event_key) {
            errlog( ERR_CRITICAL, "Constraint failed.\n" );
            continue;
        }
        
        debug( debug_level, "%s: Relay: %i 0x%016" PRIxPTR " (src: 0x%016" PRIxPTR " (%i) -> [%i] -> snk: 0x%016" PRIxPTR " (%i)\n",
               prefix,
               i,
               (uintptr_t) relay,
               (uintptr_t) relay->src,
               relay->src_enabled,
               list_length(&relay->event_q),
               (uintptr_t) relay->snk,
               relay->snk_enabled);
    }

    return 0;
}

static relay_t* _find_relay_with_source( vector_t* vec, source_t* src )
{
    list_node_t* step;
    
    for (step = list_head(vec->chain); step; step = list_node_next(step)) {
        relay_t* relay = (relay_t*)list_node_val(step);
        if ( relay == NULL )
            continue;
        if ( relay->src == src )
            return relay;
    }

    return NULL;
}
    
static relay_t* _find_relay_with_sink( vector_t* vec, sink_t* snk )
{
    list_node_t* step;
    
    for (step = list_head(vec->chain); step; step = list_node_next(step)) {
        relay_t* relay = (relay_t*)list_node_val(step);
        if ( relay == NULL )
            continue;
        if ( relay->snk == snk )
            return relay;
    }

    return NULL;
}

