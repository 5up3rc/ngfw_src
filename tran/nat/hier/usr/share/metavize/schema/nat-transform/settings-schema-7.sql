-- settings schema for release 3.2

-------------
-- settings |
-------------

-- com.metavize.tran.nat.NatSettings.dhcpLeaseList
CREATE TABLE settings.tr_dhcp_leases (
    setting_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (setting_id, position));

-- com.metavize.tran.nat.NatSettings
CREATE TABLE settings.tr_nat_settings (
    settings_id int8 NOT NULL,
    tid int8 NOT NULL UNIQUE,
    setup_state INT2,
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

----------------
-- constraints |
----------------

-- foreign key constraints

ALTER TABLE settings.tr_nat_settings
    ADD CONSTRAINT fk_tr_nat_settings
        FOREIGN KEY (tid) REFERENCES settings.tid;
