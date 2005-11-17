-- events schema for release-3.1

CREATE SCHEMA events;

SET search_path TO settings,events,public;

-- com.metavize.mvvm.engine.LoginEvent
CREATE TABLE events.mvvm_login_evt (
    event_id int8 NOT NULL,
    client_addr inet,
    login text,
    local bool,
    succeeded bool,
    reason char(1),
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.mvvm.tran.PipelineEndpoints
CREATE TABLE events.pl_endp (
    event_id int8 NOT NULL,
    time_stamp timestamp,
    session_id int4,
    proto int2,
    create_date timestamp,
    client_intf int2,
    server_intf int2,
    c_client_addr inet,
    s_client_addr inet,
    c_server_addr inet,
    s_server_addr inet,
    c_client_port int4,
    s_client_port int4,
    c_server_port int4,
    s_server_port int4,
    policy_id int8,
    policy_inbound bool,
    PRIMARY KEY (event_id));

-- com.metavize.mvvm.tran.PipelineStats
CREATE TABLE events.pl_stats (
    event_id int8 NOT NULL,
    time_stamp timestamp,
    pl_endp_id int8,
    raze_date timestamp,
    c2p_bytes int8,
    s2p_bytes int8,
    p2c_bytes int8,
    p2s_bytes int8,
    c2p_chunks int8,
    s2p_chunks int8,
    p2c_chunks int8,
    p2s_chunks int8,
    PRIMARY KEY (event_id));

-- com.metavize.mvvm.shield.ShieldRejectionEvent
CREATE TABLE events.shield_rejection_evt (
    event_id int8 NOT NULL,
    client_addr inet,
    client_intf int2,
    reputation float8,
    mode int4,
    limited int4,
    dropped int4,
    rejected int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.mvvm.shield.ShieldStatisticEvent
CREATE TABLE events.shield_statistic_evt (
    event_id int8 NOT NULL,
    accepted int4,
    limited  int4,
    dropped  int4,
    rejected int4,
    relaxed  int4,
    lax      int4,
    tight    int4,
    closed   int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

----------------
-- constraints |
----------------

-- indeces for reporting

CREATE INDEX pl_endp_sid_idx ON events.pl_endp (session_id);
CREATE INDEX pl_endp_cdate_idx ON events.pl_endp (create_date);
CREATE INDEX pl_stats_plepid_idx ON events.pl_stats (pl_endp_id);

ALTER TABLE events.pl_stats
    ADD CONSTRAINT fk_plstats_to_plendp
    FOREIGN KEY (pl_endp_id) REFERENCES events.pl_endp;
