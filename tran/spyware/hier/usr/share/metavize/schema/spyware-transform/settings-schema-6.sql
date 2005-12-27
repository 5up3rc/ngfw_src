-- schema for release 3.2

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
    activex_details text,
    cookie_details text,
    spyware_details text,
    block_all_activex_details text,
    url_blacklist_details text,
    subnet_version int4 NOT NULL,
    activex_version int4 NOT NULL,
    cookie_version int4 NOT NULL,
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

----------------
-- constraints |
----------------

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
    ADD CONSTRAINT fk_tr_spyware_cr_rule
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
