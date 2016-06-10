{
    "uniqueId": "bandwidth-control-OpyMCmkUGS",
    "category": "Bandwidth Control",
    "description": "The bandwidth usage by priority.",
    "displayOrder": 800,
    "enabled": true,
    "javaClass": "com.untangle.node.reports.ReportEntry",
    "orderDesc": false,
    "units": "bytes/s",
    "readOnly": true,
    "table": "session_minutes",
    "timeDataInterval": "AUTO",
    "timeDataDynamicValue": "(s2c_bytes+c2s_bytes)/60",
    "timeDataDynamicColumn": "bandwidth_control_priority",
    "timeDataDynamicLimit": "10",
    "timeDataDynamicAggregationFunction": "sum",
    "timeDataDynamicAllowNull": true,
    "timeStyle": "AREA_STACKED",
    "title": "Top Priorities Usage",
    "type": "TIME_GRAPH_DYNAMIC"
}
