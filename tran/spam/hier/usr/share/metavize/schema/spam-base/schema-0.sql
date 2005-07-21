-- schema for release 2.5

-------------
-- settings |
-------------

CREATE TABLE settings.tr_spam_smtp_config (
    config_id int8 NOT NULL,
    scan bool NOT NULL,
    msg_action char(1) NOT NULL,
    notify_action char(1) NOT NULL,
    notes varchar(255),
    PRIMARY KEY (config_id));

CREATE TABLE settings.tr_spam_pop_config (
    config_id int8 NOT NULL,
    scan bool NOT NULL,
    msg_action char(1) NOT NULL,
    notes varchar(255),
    PRIMARY KEY (config_id));

CREATE TABLE settings.tr_spam_imap_config (
    config_id int8 NOT NULL,
    scan bool NOT NULL,
    msg_action char(1) NOT NULL,
    notes varchar(255),
    PRIMARY KEY (config_id));

CREATE TABLE settings.tr_spam_settings (
    settings_id int8 NOT NULL,
    tid int8 NOT NULL UNIQUE,
    smtp_inbound int8 NOT NULL,
    smtp_outbound int8 NOT NULL,
    pop_inbound int8 NOT NULL,
    pop_outbound int8 NOT NULL,
    imap_inbound int8 NOT NULL,
    imap_outbound int8 NOT NULL,
    PRIMARY KEY (settings_id));

-----------
-- events |
-----------

CREATE TABLE events.tr_spam_evt_smtp (
    event_id int8 NOT NULL,
    msg_id int8,
    score float4,
    is_spam bool,
    action char(1),
    vendor_name varchar(255),
    time_stamp timestamp,
    PRIMARY KEY (event_id));

CREATE TABLE events.tr_spam_evt (
    event_id int8 NOT NULL,
    msg_id int8,
    score float4,
    is_spam bool,
    action char(1),
    vendor_name varchar(255),
    time_stamp timestamp,
    PRIMARY KEY (event_id));

----------------
-- constraints |
----------------

-- foreign key constraints

ALTER TABLE settings.tr_spam_settings
    ADD CONSTRAINT fk_in_ss_smtp_cfg
    FOREIGN KEY (smtp_inbound)
    REFERENCES settings.tr_spam_smtp_config;

ALTER TABLE settings.tr_spam_settings
    ADD CONSTRAINT fk_out_ss_smtp_cfg
    FOREIGN KEY (smtp_outbound)
    REFERENCES settings.tr_spam_smtp_config;

ALTER TABLE settings.tr_spam_settings
    ADD CONSTRAINT fk_in_ss_pop_cfg
    FOREIGN KEY (pop_inbound)
    REFERENCES settings.tr_spam_pop_config;

ALTER TABLE settings.tr_spam_settings
    ADD CONSTRAINT fk_out_ss_pop_cfg
    FOREIGN KEY (pop_outbound)
    REFERENCES settings.tr_spam_pop_config;

ALTER TABLE settings.tr_spam_settings
    ADD CONSTRAINT fk_in_ss_imap_cfg
    FOREIGN KEY (imap_inbound)
    REFERENCES settings.tr_spam_imap_config;

ALTER TABLE settings.tr_spam_settings
    ADD CONSTRAINT fk_out_ss_imap_cfg
    FOREIGN KEY (imap_outbound)
    REFERENCES settings.tr_spam_imap_config;
