-- schema for release 3.0

-------------
-- settings |
-------------

-- com.metavize.tran.spyware.SpywareSettings
CREATE TABLE settings.tr_spyware_settings (
    settings_id int8 NOT NULL,
    tid int8 NOT NULL UNIQUE,
    activex_enabled bool,
    cookie_enabled bool,
    spyware_enabled bool,
    block_all_activex bool,
    url_blacklist_enabled bool,
    activex_details varchar(255),
    cookie_details varchar(255),
    spyware_details varchar(255),
    block_all_activex_details varchar(255),
    url_blacklist_details varchar(255),
    PRIMARY KEY (settings_id));

-- com.metavize.tran.spyware.SpywareSettings.cookieRules
CREATE TABLE settings.tr_spyware_cr (
    settings_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (settings_id, position));

-- com.metavize.tran.spyware.SpywareSettings.activeXRules
CREATE TABLE settings.tr_spyware_ar (
    settings_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (settings_id, position));

-- com.metavize.tran.spyware.SpywareSettings.subnetRules
CREATE TABLE settings.tr_spyware_sr (
    settings_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (settings_id, position));

-- com.metavize.tran.spyware.SpywareSettings.domainWhitelist
CREATE TABLE settings.tr_spyware_wl (
    settings_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (settings_id, position));

-----------
-- events |
-----------

-- com.metavize.tran.spyware.SpywareAccessEvent
CREATE TABLE events.tr_spyware_evt_access (
    event_id int8 NOT NULL,
    session_id int4,
    request_id int8,
    ipmaddr inet,
    ident varchar(255),
    blocked bool,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareActiveXEvent
CREATE TABLE events.tr_spyware_evt_activex (
    event_id int8 NOT NULL,
    session_id int4,
    request_id int8,
    ident varchar(255),
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareCookieEvent
CREATE TABLE events.tr_spyware_evt_cookie (
    event_id int8 NOT NULL,
    session_id int4,
    request_id int8,
    ident varchar(255),
    to_server bool,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.spyware.SpywareBlacklistEvent
CREATE TABLE events.tr_spyware_evt_blacklist (
    event_id int8 NOT NULL,
    session_id int4,
    request_id int8,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

----------------
-- constraints |
----------------

-- indeces for reporting

CREATE INDEX tr_spyware_cookie_rid_idx
    ON events.tr_spyware_evt_cookie (request_id);
CREATE INDEX tr_spyware_bl_rid_idx
    ON events.tr_spyware_evt_blacklist (request_id);
CREATE INDEX tr_spyware_ax_rid_idx
    ON events.tr_spyware_evt_activex (request_id);
CREATE INDEX tr_spyware_acc_sid_idx
    ON event.tr_spyware_evt_access (session_id);

-- foreign key constraints

ALTER TABLE settings.tr_spyware_ar
    ADD CONSTRAINT fk_tr_spyware_ar
    FOREIGN KEY (settings_id) REFERENCES settings.tr_spyware_settings;

ALTER TABLE settings.tr_spyware_ar
    ADD CONSTRAINT fk_tr_spyware_ar_rule
    FOREIGN KEY (rule_id) REFERENCES settings.string_rule;

ALTER TABLE settings.tr_spyware_settings
    ADD CONSTRAINT fk_tr_spyware_settings
    FOREIGN KEY (tid) REFERENCES settings.tid;

ALTER TABLE settings.tr_spyware_cr
    ADD CONSTRAINT fk_tr_spyware_cr
    FOREIGN KEY (settings_id) REFERENCES settings.tr_spyware_settings;

ALTER TABLE settings.tr_spyware_cr
    ADD CONSTRAINT settings.fk_tr_spyware_cr_rule
    FOREIGN KEY (rule_id) REFERENCES settings.string_rule;

ALTER TABLE settings.tr_spyware_sr
    ADD CONSTRAINT fk_tr_spyware_sr
    FOREIGN KEY (settings_id) REFERENCES settings.tr_spyware_settings;

ALTER TABLE settings.tr_spyware_sr
    ADD CONSTRAINT fk_tr_spyware_sr_rule
    FOREIGN KEY (rule_id) REFERENCES settings.ipmaddr_rule;

ALTER TABLE settings.tr_spyware_wl
    ADD CONSTRAINT fk_tr_spyware_wl
    FOREIGN KEY (rule_id) REFERENCES settings.string_rule;
