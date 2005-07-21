-- schema for release 2.5

-------------
-- settings |
-------------

-- com.metavize.tran.nat.DhcpLeaseRule
CREATE TABLE settings.dhcp_lease_rule (
    rule_id int8 NOT NULL,
    mac_address varchar(255),
    hostname varchar(255),
    static_address inet,
    is_resolve_mac bool,
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

-- com.metavize.tran.nat.NatSettings.dhcpLeaseList
CREATE TABLE settings.tr_dhcp_leases (
    setting_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (setting_id, position));

-- com.metavize.tran.nat.RedirectRule
CREATE TABLE settings.redirect_rule (
    rule_id int8 NOT NULL,
    is_dst_redirect bool,
    redirect_port int4,
    redirect_addr inet,
    protocol_matcher varchar(255),
    src_ip_matcher varchar(255),
    dst_ip_matcher varchar(255),
    src_port_matcher varchar(255),
    dst_port_matcher varchar(255),
    src_intf_matcher varchar(255),
    dst_intf_matcher varchar(255),
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

-- com.metavize.tran.nat.NatSettings
CREATE TABLE settings.tr_nat_settings (
    settings_id int8 NOT NULL,
    tid int8 NOT NULL UNIQUE,
    nat_enabled bool,
    nat_internal_addr inet,
    nat_internal_subnet inet,
    dmz_enabled bool,
    dmz_address inet,
    dhcp_enabled bool,
    dhcp_s_address inet,
    dhcp_e_address inet,
    dhcp_lease_time int4,
    dns_enabled bool,
    dns_local_domain varchar(255),
    dmz_logging_enabled bool,
    PRIMARY KEY (settings_id));

-- com.metavize.tran.nat.NatSettings.redirectList
CREATE TABLE settings.tr_nat_redirects (
    setting_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (setting_id, position));

-- com.metavize.tran.nat.NatSettings.dnsStaticHostList
CREATE TABLE settings.tr_nat_dns_hosts (
    setting_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (setting_id, position));

-- com.metavize.tran.nat.DnsStaticHostRule
CREATE TABLE settings.dns_static_host_rule (
    rule_id int8 NOT NULL,
    hostname_list varchar(255),
    static_address inet,
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

-----------
-- events |
-----------

-- com.metavize.tran.nat.DhcpLeaseEvent
CREATE TABLE events.tr_nat_evt_dhcp (
    event_id int8 NOT NULL,
    mac varchar(255),
    hostname varchar(255),
    ip inet,
    end_of_lease timestamp,
    event_type int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.nat.DhcpAbsoluteLease
CREATE TABLE events.dhcp_abs_lease (
    event_id int8 NOT NULL,
    mac varchar(255),
    hostname varchar(255),
    ip inet, end_of_lease timestamp,
    event_type int4,
    PRIMARY KEY (event_id));

-- com.metavize.tran.nat.DhcpAbsoluteEvent
CREATE TABLE events.tr_nat_evt_dhcp_abs (
    event_id int8 NOT NULL,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.nat.DhcpAbsoluteEvent.absoluteLeaseList
CREATE TABLE events.tr_nat_evt_dhcp_abs_leases (
    event_id int8 NOT NULL,
    lease_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (event_id, position));

-- com.metavize.tran.nat.RedirectEvent
CREATE TABLE events.tr_nat_redirect_evt (
    event_id int8 NOT NULL,
    session_id int4,
    rule_id int8,
    rule_index int4,
    is_dmz bool,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

-- com.metavize.tran.nat.NatStatisticEvent
CREATE TABLE events.tr_nat_statistic_evt (
    event_id int8 NOT NULL,
    nat_sessions int4,
    dmz_sessions int4,
    tcp_incoming int4,
    tcp_outgoing int4,
    udp_incoming int4,
    udp_outgoing int4,
    icmp_incoming int4,
    icmp_outgoing int4,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

----------------
-- constraints |
----------------

-- foreign key constraints

ALTER TABLE settings.tr_nat_settings
    ADD CONSTRAINT fk_tr_nat_settings
        FOREIGN KEY (tid) REFERENCES settings.tid;
ALTER TABLE settings.tr_nat_redirects
    ADD CONSTRAINT fk_tr_nat_redirects
        FOREIGN KEY (setting_id) REFERENCES settings.tr_nat_settings;
ALTER TABLE settings.tr_nat_redirects
    ADD CONSTRAINT fk_tr_nat_redirects_rule
        FOREIGN KEY (rule_id) REFERENCES settings.redirect_rule;
ALTER TABLE settings.tr_dhcp_leases
    ADD CONSTRAINT fk_tr_dhcp_leases
        FOREIGN KEY (setting_id) REFERENCES settings.tr_nat_settings;
ALTER TABLE settings.tr_dhcp_leases
    ADD CONSTRAINT fk_tr_dhcp_leases_rule
        FOREIGN KEY (rule_id) REFERENCES settings.dhcp_lease_rule;
ALTER TABLE settings.tr_nat_dns_hosts
    ADD CONSTRAINT fk_tr_nat_dns_hosts
        FOREIGN KEY (setting_id) REFERENCES settings.tr_nat_settings;
ALTER TABLE settings.tr_nat_dns_hosts
    ADD CONSTRAINT fk_tr_nat_dns_hosts_rule
        FOREIGN KEY (rule_id) REFERENCES settings.dns_static_host_rule;
