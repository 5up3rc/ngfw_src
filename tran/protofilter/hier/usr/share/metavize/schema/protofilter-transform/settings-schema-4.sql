-- settings schema for release 3.2

-------------
-- settings |
-------------

-- com.metavize.tran.protofilter.ProtoFilterSettings
CREATE TABLE settings.tr_protofilter_settings (
    settings_id int8 NOT NULL,
    tid int8 NOT NULL UNIQUE,
    bytelimit int4,
    chunklimit int4,
    unknownstring varchar(255),
    stripzeros bool,
    PRIMARY KEY (settings_id));

-- com.metavize.tran.protofilter.ProtoFilterPattern
CREATE TABLE settings.tr_protofilter_pattern (
    rule_id int8 NOT NULL,
    metavize_id int4,
    protocol text,
    description text,
    category text,
    definition text,
    quality text,
    blocked bool,
    alert bool,
    log bool,
    settings_id int8,
    position int4,
    PRIMARY KEY (rule_id));

----------------
-- constraints |
----------------

-- foreign key constraints

ALTER TABLE settings.tr_protofilter_settings
    ADD CONSTRAINT fk_tr_protofilter_settings
        FOREIGN KEY (tid) REFERENCES settings.tid;

ALTER TABLE settings.tr_protofilter_pattern
    ADD CONSTRAINT fk_tr_protofilter_pattern
        FOREIGN KEY (settings_id) REFERENCES settings.tr_protofilter_settings;
