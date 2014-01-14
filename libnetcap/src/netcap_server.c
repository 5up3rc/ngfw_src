/**
 * $Id$
 */
#include "netcap_server.h"

#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/epoll.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <mvutil/debug.h>
#include <mvutil/errlog.h>
#include <mvutil/hash.h>
#include <mvutil/unet.h>
#include "libnetcap.h"
#include "netcap_globals.h"
#include "netcap_queue.h"
#include "netcap_hook.h"
#include "netcap_session.h"
#include "netcap_pkt.h"
#include "netcap_udp.h"
#include "netcap_tcp.h"
#include "netcap_interface.h"

#define EPOLL_INPUT_SET  EPOLLIN|EPOLLPRI|EPOLLERR|EPOLLHUP
#define EPOLL_OUTPUT_SET EPOLLOUT|EPOLLPRI|EPOLLERR|EPOLLHUP

#define EXIT 0xDEADD00D


/* #define _server_lock()       do{ while(lock_wrlock(&_server_lock)!=0) perrlog("LOCK_WRLOCK");  _server_lock_val--; errlog(ERR_CRITICAL,"wrlock (0x%08x)\n",pthread_self()); if (_server_lock_val!=0) { errlog(ERR_CRITICAL,"Invalid Lock val after lock: %i\n",_server_lock_val); }} while (0) */
/* #define _server_unlock()     do{ _active_threads--; _server_lock_val++; errlog(ERR_CRITICAL,"unlock (0x%08x)\n",pthread_self()); if (_server_lock_val!=1) { errlog(ERR_CRITICAL,"Invalid Lock val after unlock: %i\n",_server_lock_val); debug_backtrace(0,"UNLOCK:\n"); } while(lock_unlock(&_server_lock)!=0) perrlog("LOCK_UNLOCK") ; } while (0) */

#define _server_lock()       do{ while(lock_wrlock(&_server_lock)!=0) perrlog("lock_wrlock");  _server_lock_val--; if (_server_lock_val!=0) { errlog(ERR_CRITICAL,"Invalid Lock val after lock: %i\n",_server_lock_val); }} while (0)
#define _server_unlock()     do{ _active_threads--; _server_lock_val++; if (_server_lock_val!=1) { errlog(ERR_CRITICAL,"Invalid Lock val after unlock: %i\n",_server_lock_val); debug_backtrace(0,"UNLOCK:\n"); } while(lock_unlock(&_server_lock)!=0) perrlog("LOCK_UNLOCK") ; } while (0)

#define _epoll_print_stat(revents) do{ errlog(ERR_WARNING,"Message Incoming revents = 0x%08x \n",(revents)); \
                                       errlog(ERR_WARNING,"EPOLLIN:%i EPOLLOUT:%i EPOLLHUP:%i EPOLLERR:%i EPOLLERR:%i \n", \
                                              (revents) & EPOLLIN,  (revents) & EPOLLOUT, (revents) & EPOLLHUP, \
                                              (revents) & EPOLLERR, (revents) & EPOLLERR); \
                                       } while(0)

/**
 * the types of events the server handles
 */
typedef enum {
    POLL_MESSAGE = 1,      /* poll type for message queue */
    POLL_TCP_INCOMING,     /* poll type for accepting new tcp connections */
    POLL_NFQUEUE_INCOMING /* poll type for accepting netfilter queued packets */
} poll_type_t;
    
/**
 * epoll_info_t store auxillary information of the fd's in the _epoll_fd
 * each fd in _epoll_fd has a epoll_info (stored in _epoll_table)
 */
typedef struct epoll_info {
    /**
     * the fd of this epoll fd
     */
    int             fd;

    /**
     * the type of this epoll fd
     */
    poll_type_t     type;
    
    netcap_session_t* netcap_sess;
    
} epoll_info_t;



static int  _handle_tcp_incoming (epoll_info_t* info, int revents, int sock );
static int  _handle_message (epoll_info_t* info, int revents);
static int  _handle_nfqueue (epoll_info_t* info, int revents);

/* static int  _start_open_connection (struct in_addr* destaddr,u_short destport); */
static int  _epoll_info_add (int fd, int events, int type, netcap_session_t* netcap_sess);
static int  _epoll_info_del (epoll_info_t* info);
static int  _epoll_info_del_fd (int fd);

static int   _message_pipe[2]; 
static lock_t _server_lock;
static int   _epoll_fd;
static ht_t  _epoll_table;
static volatile int _server_lock_val = 1;
static volatile int _server_threads = 0;
static volatile int _active_threads = 0;

static struct {
    struct {
        int *sock_array;
        int count;
    } tcp;
    
} _server = {
    .tcp = {
        .sock_array = NULL,
        .count      = -1
    },
};

int  netcap_server_init (void)
{
    int c;

    if (lock_init(&_server_lock,LOCK_FLAG_NOTRACK_READERS)<0)
        return perrlog("lock_init");

    if (pipe(_message_pipe)<0) 
        return perrlog("pipe");
    if ((_epoll_fd = epoll_create(EPOLL_MAX_EVENT))<0)
        return perrlog("epoll_create");
    if (ht_init(&_epoll_table, EPOLL_MAX_EVENT+1, int_hash_func, int_equ_func, HASH_FLAG_KEEP_LIST)<0)
        return perrlog("ht_init");

    if (_epoll_info_add(_message_pipe[0], EPOLL_INPUT_SET,POLL_MESSAGE,NULL)<0)
        return perrlog("_epoll_info_add");
    if (_epoll_info_add(netcap_nfqueue_get_sock(),EPOLL_INPUT_SET,POLL_NFQUEUE_INCOMING,NULL)<0)
        return perrlog("_epoll_info_add");

    if ((( _server.tcp.count = netcap_tcp_redirect_socks( &_server.tcp.sock_array )) < 0 ) || 
        ( _server.tcp.sock_array == NULL )) {
        return errlog( ERR_CRITICAL, "netcap_tcp_redirect_sockets\n" );
    }
    
    /* Add the TCP Redirect port to EPOLL */
    for ( c = 0 ; c < _server.tcp.count ; c++ ) {
        int fd = _server.tcp.sock_array[c];
        debug( 11, "NETCAP: Inserting socket: %d\n", fd );
        
        if ( _epoll_info_add( fd, EPOLL_INPUT_SET, POLL_TCP_INCOMING, NULL ) < 0 ) {
            return perrlog( "_epoll_info_add" );
        }
    }
    
    return 0;
}

int  netcap_server_shutdown (void)
{
    int tries=0;
    int numtries = 20;

    while ( _server_threads > 0 && tries < numtries ) {
        if ( netcap_server_sndmsg(NETCAP_MSG_SHUTDOWN,NULL ) < 0 )
            errlog(ERR_CRITICAL,"Failed shutdown netcap server\n");

        usleep( 100000 );
        tries++;
    }
    if (tries == numtries) 
        errlog(ERR_WARNING,"Couldnt shutdown Netcap Server (%i Threads remain)\n",_server_threads);
        
    if (_epoll_info_del_fd(_message_pipe[0])<0) 
        perrlog("_epoll_info_del_fd");
    if (_epoll_info_del_fd(netcap_nfqueue_get_sock())<0) 
        perrlog("_epoll_info_del_fd");
        
    if (( _server.tcp.count > 0 ) && ( _server.tcp.sock_array != NULL )) {
        int c;
        
        for  ( c = 0 ; c < _server.tcp.count ; c++ ) {
            int fd = _server.tcp.sock_array[c];
            if ( _epoll_info_del_fd( fd ) < 0 ) perrlog( "_epoll_info_del_fd\n" );
        }
    }

    if (ht_destroy(&_epoll_table)>0)
        errlog(ERR_WARNING,"Entries left in epoll table\n");
    if (lock_destroy(&_server_lock)<0) 
        perrlog("sem_destroy");

    if (close(_message_pipe[0])<0)
        perrlog("close");
    if (close(_message_pipe[1])<0)
        perrlog("close");
    if (close(_epoll_fd)<0)
        perrlog("close");

    
    
        
    return 0;
}

int  netcap_server (void)
{
    struct epoll_event events[EPOLL_MAX_EVENT];
    int i,num_events;

    _server_threads++;
 entry:
    _server_lock();
    if (++_active_threads != 1) 
        errlog(ERR_CRITICAL,"Constraint FAILED! Too many threads in server: %i\n", _active_threads);
        
    while(1) {

        debug(10,"netcap_server: epoll()\n");
        if ((num_events = epoll_wait(_epoll_fd,events,EPOLL_MAX_EVENT,-1)) < 0) {
            _server_unlock();
            perrlog("epoll_wait");
            usleep(10000); /* just in case - prevent spinning */
            goto entry;
        }
        debug(10,"netcap_server: epoll() return\n");

        /**
         * handle events
         */
        for(i=0;i<num_events;i++) {
            epoll_info_t* info = ht_lookup(&_epoll_table,(void*)(long)events[i].data.fd);

            if (!info) {
                errlog(ERR_CRITICAL,"Constraint failed: epoll_info_t missing!\n");
                continue;
            }

            switch(info->type) {
            case POLL_MESSAGE:
                /* Thread exit point */
                if ( _handle_message(info, events[i].events) == EXIT )
                    return 0;
                break;
            case POLL_TCP_INCOMING:
                _handle_tcp_incoming(info, events[i].events, events[i].data.fd );
                break;
            case POLL_NFQUEUE_INCOMING:
                _handle_nfqueue(info, events[i].events);
            }

            break;
        }

        /**
         * wait on the sem's before restarting while(1) 
         */
        _server_lock();
        if (++_active_threads != 1) 
            errlog(ERR_CRITICAL,"Constraint FAILED! Too many threads in server: %i\n", _active_threads);
    }
    
    _server_unlock();
    _server_threads--;
    return errlog(ERR_CRITICAL,"Statement should not be reached\n");
}

int  netcap_server_sndmsg (netcap_mesg_t msg, void* arg)
{
    char buf[sizeof(netcap_mesg_t)+sizeof(void*)];
    int nbyt;

    memcpy(buf,&msg,sizeof(netcap_mesg_t));
    memcpy(buf+sizeof(netcap_mesg_t),&arg,sizeof(void*));
           
    if ((nbyt = write(_message_pipe[1],buf,sizeof(buf)))<0) 
        return perrlog("write");
    if (nbyt < sizeof(buf)) 
        return errlog(ERR_CRITICAL,"truncated write\n");

    debug(10,"Server message (%i,%08x) sent\n",(int)msg,arg);

    return nbyt;
}


static int  _handle_message (epoll_info_t* info, int revents)
{
    char buf[sizeof(netcap_mesg_t)+sizeof(void*)];
    netcap_mesg_t* msg = (netcap_mesg_t*)buf;
    void** arg = (void**)(buf+sizeof(netcap_mesg_t));
    int ret = -1;
    
    if (!(revents & EPOLLIN)) {
        _epoll_print_stat(revents);
        _server_unlock();
        return -1;
    }

    ret = read(_message_pipe[0],&buf,sizeof(buf));

    debug(10,"Server message (%i,%08x) received\n",*msg,*arg);

    if (ret < sizeof(buf)) {
        perrlog("read");
    } else {
        switch(*msg) {

        case NETCAP_MSG_REFRESH:
            _server_unlock();
            break;

        case NETCAP_MSG_NULL:
            _server_unlock();
            errlog(ERR_CRITICAL,"Null Message Received\n");
            break;

        case NETCAP_MSG_ADD_SUB:
            _server_unlock();
            errlog( ERR_CRITICAL, "NETCAP_MSG_ADD_SUB is unsupported\n" );
            break;
            
        case NETCAP_MSG_REM_SUB:
            _server_unlock();            
            errlog( ERR_CRITICAL, "NETCAP_MSG_REM_SUB is unsupported\n" );
            break;


        case NETCAP_MSG_SHUTDOWN:
            debug(5,"NETCAP: Shutdown Received, Thread Terminating\n");
            _server_unlock();
            netcap_server_sndmsg(NETCAP_MSG_SHUTDOWN,NULL); /* tell the next guy to exit */

            _server_threads--;
            return EXIT;
            break;
                    
        default:
            _server_unlock();
            errlog(ERR_CRITICAL,"Unknown message: %i arg:%08x\n",*msg,*arg);
            break;
        }
    }

    return 0;
}

static int  _handle_tcp_incoming (epoll_info_t* info, int revents, int fd )
{
    struct sockaddr_in cli_addr;
    u_int cli_addrlen = sizeof(cli_addr);
    int cli_sock;

    if ( !info ) {
        _server_unlock();
        return errlogargs();
    }

    if (!(revents & EPOLLIN)) {
        _epoll_print_stat(revents);
        _server_unlock();
        return -1;
    }

    /**
     * Accept the connection 
     */
    cli_sock = accept( fd , (struct sockaddr *) &cli_addr, &cli_addrlen);
    if (cli_sock < 0) {
        _server_unlock();
        return perrlog("accept");
    }

    /**
     * if they only want a half complete connection.  Connections are always unfinished
     */
    _server_unlock();
    if ( netcap_tcp_accept_hook( cli_sock, cli_addr ) < 0 ) {
        if (close(cli_sock)<0)
            perrlog("close");
    }
    return 0;
}

static int  _handle_nfqueue (epoll_info_t* info, int revents)
{
    netcap_pkt_t* pkt = NULL;
    u_char*       buf = NULL;

    int _critical_section()
    {
        debug( 10, "SERVER Handle nf_queue.\n" );

        if ( info == NULL ) return errlogargs();

        if ( info->fd != netcap_nfqueue_get_sock()) return errlogcons();
        
        /* if there are any events in the input set, just read out */
        if (!( revents & ( EPOLL_INPUT_SET ))) {
            _epoll_print_stat( revents );
            return -1;
        }

        if (( buf = malloc( QUEUE_MAX_MESG_SIZE )) == NULL ) return errlogmalloc();

        if (( pkt = netcap_pkt_create()) == NULL ) return errlogmalloc();

        if ( netcap_nfqueue_read( buf, QUEUE_MAX_MESG_SIZE, pkt ) < 0 ) {
            return errlog( ERR_CRITICAL, "netcap_nfqueue_read\n" );
        }
        
        if ( revents & EPOLLHUP ) {
            /* fatal */
            errlog( ERR_CRITICAL, "HUP on queue socket\n" );
        }

        return 0;
    }

    int ret = _critical_section();
    
    _server_unlock();

    if (( ret < 0 ) || ( pkt == NULL )) {
        if ( pkt != NULL ) {
            /* Do not free either buffer in pkt_raze */
            pkt->buffer = NULL;
            pkt->data = NULL;
            
            netcap_pkt_raze( pkt );
        }
        
        if ( buf != NULL ) {
            free( buf );
            buf = NULL;
        }

        pkt = NULL;

        return errlog( ERR_CRITICAL, "_critical_section\n" );
    }
    
    /* Actually call the handle */
    switch ( pkt->proto ) {
    case IPPROTO_TCP:
        return global_tcp_syn_hook( pkt );
        
    case IPPROTO_UDP:
        return netcap_udp_call_hooks( pkt, NULL );
        
    default:
        netcap_pkt_action_raze( pkt, NF_DROP );
        return errlog(ERR_CRITICAL,"Unknown protocol  %d from QUEUE\n", pkt->proto );        
    }
}

static int  _epoll_info_add (int fd, int events, int type, netcap_session_t* netcap_sess)
{
    epoll_info_t* info;
    struct epoll_event ev;

    if ( fd < 0 ) return errlogargs();
    
    info = malloc(sizeof(epoll_info_t));
    if (!info)
        return errlogmalloc();

    info->fd    = fd;
    info->type  = type;
    info->netcap_sess = netcap_sess;
    
    bzero(&ev,sizeof(struct epoll_event));
    ev.data.fd = fd;
    ev.events  = events;

    if (epoll_ctl(_epoll_fd,EPOLL_CTL_ADD,fd,&ev)<0)
        return perrlog("epoll_ctl");

    if (ht_add(&_epoll_table,(void*)(long)fd,(void*)info)<0)
        return perrlog("ht_add");

    return 0;
}

static int  _epoll_info_del_fd (int fd)
{
    epoll_info_t* epi = ht_lookup(&_epoll_table,(void*)(long)fd);

    if (!epi)
        return errlog(ERR_CRITICAL, "epoll_info_t for %d not found\n", fd );

    return _epoll_info_del(epi);
}

static int  _epoll_info_del (epoll_info_t* info)
{
    struct epoll_event ev;

    if (info->fd == -1)
        return errlogargs();

    if (!info)
        return errlog(ERR_CRITICAL,"Invalid argument");

    bzero(&ev,sizeof(struct epoll_event));
    ev.data.fd = info->fd;
    ev.events  = 0;

    if (epoll_ctl(_epoll_fd,EPOLL_CTL_DEL,ev.data.fd,&ev)<0)
        return perrlog("epoll_ctl");

    if (ht_remove(&_epoll_table,(void*)(long)info->fd)<0)
        return perrlog("ht_remove");

    free(info);
    
    return 0;
}

