-- schema for release 3.0

-------------
-- settings |
-------------

-- com.metavize.tran.firewall.FirewallRule
CREATE TABLE settings.firewall_rule (
    rule_id int8 NOT NULL,
    is_traffic_blocker BOOL,
    protocol_matcher VARCHAR(255),
    src_ip_matcher VARCHAR(255),
    dst_ip_matcher VARCHAR(255),
    src_port_matcher VARCHAR(255),
    dst_port_matcher VARCHAR(255),
    inbound BOOL,
    outbound BOOL,
    name VARCHAR(255),
    category VARCHAR(255),
    description VARCHAR(255),
    live BOOL,
    alert BOOL,
    log BOOL,
    PRIMARY KEY (rule_id));

-- com.metavize.tran.firewall.FirewallSettings.firewallRuleList
CREATE TABLE settings.tr_firewall_rules (
    setting_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (setting_id, position));

-- com.metavize.tran.firewall.FirewallSettings
CREATE TABLE settings.tr_firewall_settings (
    settings_id int8 NOT NULL,
    tid int8 NOT NULL UNIQUE,
    is_quickexit BOOL,
    is_reject_silent BOOL,
    is_default_accept BOOL,
    PRIMARY KEY (settings_id));

-----------
-- events |
-----------

-- com.metavize.tran.firewall.FirewallEvent
CREATE TABLE events.tr_firewall_evt (
    event_id int8 NOT NULL,
    session_id int4,
    was_blocked bool,
    rule_index int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.firewall.FirewallStatisticEvent
CREATE TABLE events.tr_firewall_statistic_evt (
    event_id int8 NOT NULL,
    tcp_block_default int4,
    tcp_block_rule int4,
    tcp_pass_default int4,
    tcp_pass_rule int4,
    udp_block_default int4,
    udp_block_rule int4,
    udp_pass_default int4,
    udp_pass_rule int4,
    icmp_block_default int4,
    icmp_block_rule int4,
    icmp_pass_default int4,
    icmp_pass_rule int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

----------------
-- constraints |
----------------

-- indeces for reporting

CREATE INDEX tr_firewall_evt_sid_idx ON events.tr_firewall_evt (session_id);

-- foreign key constraints

ALTER TABLE settings.tr_firewall_rules
    ADD CONSTRAINT fk_tr_firewall_rules
        FOREIGN KEY (rule_id) REFERENCES settings.firewall_rule;
ALTER TABLE settings.tr_firewall_rules
    ADD CONSTRAINT fk_tr_firewall_rules_settings
        FOREIGN KEY (setting_id) REFERENCES settings.tr_firewall_settings;
ALTER TABLE settings.tr_firewall_settings
    ADD CONSTRAINT fk_tr_firewall_settings
        FOREIGN KEY (tid) REFERENCES settings.tid;
