import reports.engine
import reports.sql_helper as sql_helper
import mx
import sys

from psycopg2.extensions import DateFromMx
from psycopg2.extensions import TimestampFromMx
from reports.engine import Column
from reports.engine import FactTable
from reports.engine import Node
from reports.sql_helper import print_timing

class FtpCasing(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-casing-ftp', 'FTP')

    def parents(self):
        return ['untangle-vm']

    @print_timing
    def setup(self):
        self.__create_ftp_events()

        ft = FactTable('reports.ftp_totals', 'reports.ftp_events',
                       'time_stamp',
                       [Column('hostname', 'text'),
                        Column('username', 'text')],
                       [Column('hits', 'bigint', 'count(*)')])

        # remove obsolete columns
        sql_helper.drop_column('reports', 'ftp_totals', 's2c_bytes')
        sql_helper.drop_column('reports', 'ftp_totals', 'c2s_bytes')

        reports.engine.register_fact_table(ft)

    @print_timing
    def __create_ftp_events(self):
        sql_helper.create_fact_table("""\
CREATE TABLE reports.ftp_events (
    event_id bigserial,
    time_stamp timestamp without time zone,
    session_id bigint,
    client_intf smallint,
    server_intf smallint,
    c_client_addr inet,
    s_client_addr inet,
    c_server_addr inet,
    s_server_addr inet,
    policy_id bigint,
    username text,
    hostname text,
    request_id bigint,
    method character(1),
    uri text,
    clam_clean boolean,
    clam_name text,
    virusblocker_clean boolean,
    virusblocker_name text)""")

        # If the new index does not exist, create it
        if not sql_helper.index_exists("reports", "ftp_events", "request_id", unique=True):
            sql_helper.create_index("reports", "ftp_events", "request_id", unique=True);

        # If the new index does not exist, create it
        if not sql_helper.index_exists("reports", "ftp_events", "event_id", unique=True):
            sql_helper.create_index("reports", "ftp_events", "event_id", unique=True);

        # rename the old commtouch columns
        sql_helper.rename_column("reports", "ftp_events", "commtouchav_clean", "virusblocker_clean");
        sql_helper.rename_column("reports", "ftp_events", "commtouchav_name", "virusblocker_name");

        sql_helper.create_index("reports", "ftp_events", "session_id");
        sql_helper.create_index("reports", "ftp_events", "policy_id");
        sql_helper.create_index("reports", "ftp_events", "time_stamp");

    def reports_cleanup(self, cutoff):
        sql_helper.drop_fact_table("ftp_events", cutoff)
        sql_helper.drop_fact_table("ftp_totals", cutoff)

reports.engine.register_node(FtpCasing())
