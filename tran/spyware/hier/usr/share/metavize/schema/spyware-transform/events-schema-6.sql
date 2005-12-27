-- schema for release 3.2

-----------
-- events |
-----------

-- com.metavize.tran.spyware.SpywareAccessEvent
CREATE TABLE events.tr_spyware_evt_access (
    event_id int8 NOT NULL,
    pl_endp_id int8,
    ipmaddr inet,
    ident text,
    blocked bool,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareActiveXEvent
CREATE TABLE events.tr_spyware_evt_activex (
    event_id int8 NOT NULL,
    request_id int8,
    ident text,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareCookieEvent
CREATE TABLE events.tr_spyware_evt_cookie (
    event_id int8 NOT NULL,
    request_id int8,
    ident text,
    to_server bool,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareBlacklistEvent
CREATE TABLE events.tr_spyware_evt_blacklist (
    event_id int8 NOT NULL,
    request_id int8,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareStatisticEvent
CREATE TABLE events.tr_spyware_statistic_evt (
    event_id int8 NOT NULL,
    pass int4,
    cookie int4,
    activeX int4,
    url int4,
    subnet_access int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- indeces for reporting

CREATE INDEX tr_spyware_cookie_rid_idx
    ON events.tr_spyware_evt_cookie (request_id);
CREATE INDEX tr_spyware_bl_rid_idx
    ON events.tr_spyware_evt_blacklist (request_id);
CREATE INDEX tr_spyware_ax_rid_idx
    ON events.tr_spyware_evt_activex (request_id);
CREATE INDEX tr_spyware_acc_plepid_idx
    ON events.tr_spyware_evt_access (pl_endp_id);
