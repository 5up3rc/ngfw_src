Ext.define('Ung.util.Util', {
    alternateClassName: 'Util',
    singleton: true,

    defaultColors: ['#7cb5ec', '#434348', '#90ed7d', '#f7a35c', '#8085e9', '#f15c80', '#e4d354', '#2b908f', '#f45b5b', '#91e8e1'],

    subNav: [
        '->',
        { text: 'Sessions'.t(), iconCls: 'fa fa-list', href: '#sessions', hrefTarget: '_self', bind: { userCls: '{activeItem === "sessions" ? "pressed" : ""}' } },
        { text: 'Hosts'.t(), iconCls: 'fa fa-th-list', href: '#hosts', hrefTarget: '_self', bind: { userCls: '{activeItem === "hosts" ? "pressed" : ""}' } },
        { text: 'Devices'.t(), iconCls: 'fa fa-desktop', href: '#devices', hrefTarget: '_self', bind: { userCls: '{activeItem === "devices" ? "pressed" : ""}' } },
        { text: 'Users'.t(), iconCls: 'fa fa-users', href: '#users', hrefTarget: '_self', bind: { userCls: '{activeItem === "users" ? "pressed" : ""}' } }
    ],

    baseCategories: [
        { name: 'hosts', type: 'system', displayName: 'Hosts' },
        { name: 'devices', type: 'system', displayName: 'Devices' },
        { name: 'network', type: 'system', displayName: 'Network' },
        { name: 'administration', type: 'system', displayName: 'Administration' },
        { name: 'system', type: 'system', displayName: 'System' },
        { name: 'events', type: 'system', displayName: 'Events' },
        { name: 'shield', type: 'system', displayName: 'Shield' },
        { name: 'users', type: 'system', displayName: 'Users' }
    ],

    appDescription: {
        'web-filter': 'Web Filter scans and categorizes web traffic to monitor and enforce network usage policies.'.t(),
        'web-monitor': 'Web monitor scans and categorizes web traffic to monitor and enforce network usage policies.'.t(),
        'virus-blocker': 'Virus Blocker detects and blocks malware before it reaches users desktops or mailboxes.'.t(),
        'virus-blocker-lite': 'Virus Blocker Lite detects and blocks malware before it reaches users desktops or mailboxes.'.t(),
        'spam-blocker': 'Spam Blocker detects, blocks, and quarantines spam before it reaches users mailboxes.'.t(),
        'spam-blocker-lite': 'Spam Blocker Lite detects, blocks, and quarantines spam before it reaches users mailboxes.'.t(),
        'phish-blocker': 'Phish Blocker detects and blocks phishing emails using signatures.'.t(),
        'web-cache': 'Web Cache stores and serves web content from local cache for increased speed and reduced bandwidth usage.'.t(),
        'bandwidth-control': 'Bandwidth Control monitors, manages, and shapes bandwidth usage on the network'.t(),
        'ssl-inspector': 'SSL Inspector allows for full decryption of HTTPS and SMTPS so that other applications can process the encrytped streams.'.t(),
        'application-control': 'Application Control scans sessions and identifies the associated applications allowing each to be flagged and/or blocked.'.t(),
        'application-control-lite': 'Application Control Lite identifies, logs, and blocks sessions based on the session content using custom signatures.'.t(),
        'captive-portal': 'Captive Portal allows administrators to require network users to complete a defined process, such as logging in or accepting a network usage policy, before accessing the internet.'.t(),
        'firewall': 'Firewall is a simple application that flags and blocks sessions based on rules.'.t(),
        'ad-blocker': 'Ad Blocker blocks advertising content and tracking cookies for scanned web traffic.'.t(),
        'reports': 'Reports records network events to provide administrators the visibility and data necessary to investigate network activity.'.t(),
        'policy-manager': 'Policy Manager enables administrators to create different policies and handle different sessions with different policies based on rules.'.t(),
        'directory-connector': 'Directory Connector allows integration with external directories and services, such as Active Directory, RADIUS, or Google.'.t(),
        'wan-failover': 'WAN Failover detects WAN outages and re-routes traffic to any other available WANs to maximize network uptime.'.t(),
        'wan-balancer': 'WAN Balancer spreads network traffic across multiple internet connections for better performance.'.t(),
        'ipsec-vpn': 'IPsec VPN provides secure network access and tunneling to remote users and sites using IPsec, GRE, L2TP, Xauth, and IKEv2 protocols.'.t(),
        'openvpn': 'OpenVPN provides secure network access and tunneling to remote users and sites using the OpenVPN protocol.'.t(),
        'intrusion-prevention': 'Intrusion Prevention blocks scans, detects, and blocks attacks and suspicious traffic using signatures.'.t(),
        'configuration-backup': 'Configuration Backup automatically creates backups of settings uploads them to My Account and Google Drive.'.t(),
        'branding-manager': 'The Branding Settings are used to set the logo and contact information that will be seen by users (e.g. reports).'.t(),
        'live-support': 'Live Support provides on-demand help for any technical issues.'.t()
    },

    bytesToHumanReadable: function (bytes, si) {
        var thresh = si ? 1000 : 1024;
        if(Math.abs(bytes) < thresh) {
            return bytes + ' B';
        }
        var units = si ? ['kB','MB','GB','TB','PB','EB','ZB','YB'] : ['KiB','MiB','GiB','TiB','PiB','EiB','ZiB','YiB'];
        var u = -1;
        do {
            bytes /= thresh;
            ++u;
        } while(Math.abs(bytes) >= thresh && u < units.length - 1);
        return bytes.toFixed(1)+' '+units[u];
    },

    formatBytes: function (bytes, decimals) {
        if (bytes === 0) {
            return '0';
        }
        //bytes = bytes * 1000;
        var k = 1000, // or 1024 for binary
            dm = decimals || 3,
            sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'],
            i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    },

    iconTitle: function (text, icon) {
        var icn = icon.split('-') [0],
            size = icon.split('-') [1] || 24;
        return '<i class="material-icons" style="font-size: ' + size + 'px">' +
                icn + '</i> <span style="vertical-align: middle;">' +
                text + '</span>';
    },

    successToast: function (message) {
        Ext.toast({
            html: '<i class="fa fa-check fa-lg"></i> ' + message,
            // minWidth: 200,
            bodyPadding: '12 12 12 40',
            baseCls: 'toast',
            border: false,
            bodyBorder: false,
            // align: 'b',
            align: 'br',
            autoCloseDelay: 5000,
            slideInAnimation: 'easeOut',
            slideInDuration: 300,
            hideDuration: 0,
            paddingX: 10,
            paddingY: 50
        });
    },

    exceptionToast: function (message) {
        var msg = [];
        if (typeof message === 'object') {
            if (message.name && message.code) {
                msg.push('<strong>Name:</strong> ' + message.name + ' (' + message.code + ')');
            }
            if (message.message) {
                msg.push('<strong>Error:</strong> ' + message.message);
            }
        } else {
            msg = [message];
        }
        Ext.toast({
            html: '<i class="fa fa-exclamation-triangle fa-lg"></i> <span style="font-weight: bold; color: yellow;">Exception!</span><br/>' + msg.join('<br/>'),
            bodyPadding: '10 10 10 45',
            baseCls: 'toast',
            cls: 'exception',
            border: false,
            bodyBorder: false,
            align: 'br',
            autoCloseDelay: 5000,
            slideInAnimation: 'easeOut',
            slideInDuration: 300,
            hideDuration: 0,
            paddingX: 10,
            paddingY: 50
        });
    },

    invalidFormToast: function (fields) {
        if (!fields || fields.length === 0) {
            return;
        }

        var str = [];
        fields.forEach(function (field) {
            str.push('<span class="field-name">' + field.label + '</span>: <br/> <span class="field-error">' + field.error.replace(/<\/?[^>]+(>|$)/g, '') + '</span>');
        });

        // var store = [];
        // fields.forEach(function (field) {
        //     console.log(field);
        //     store.push({ label: field.getFieldLabel(), error: field.getActiveError().replace(/<\/?[^>]+(>|$)/g, ''), field: field });
        // });

        Ext.toast({
            html: '<i class="fa fa-exclamation-triangle fa-lg"></i> <span style="font-weight: bold; font-size: 14px; color: yellow;">Check invalid fields!</span><br/><br/>' + str.join('<br/>'),
            bodyPadding: '10 10 10 45',
            baseCls: 'toast-invalid-frm',
            border: false,
            bodyBorder: false,
            align: 'br',
            autoCloseDelay: 5000,
            slideInAnimation: 'easeOut',
            slideInDuration: 300,
            hideDuration: 0,
            paddingX: 10,
            paddingY: 50
            // items: [{
            //     xtype: 'dataview',
            //     store: {
            //         data: store
            //     },
            //     tpl:     '<tpl for=".">' +
            //         '<div style="margin-bottom: 10px;">' +
            //         '<span class="field-name">{label}</span>:' +
            //         '<br/><span>{error}</span>' +
            //         '</div>' +
            //     '</tpl>',
            //     itemSelector: 'div',
            //     listeners: {
            //         select: function (el, field) {
            //             field.get('field').focus();
            //         }
            //     }
            // }]
        });
    },

    getInterfaceListSystemDev: function (wanMatchers, anyMatcher, systemDev) {
        var networkSettings = rpc.networkSettings,
            data = [], intf, i;

        // Note: using strings as keys instead of numbers, needed for the checkboxgroup column widget component to function

        for (i = 0; i < networkSettings.interfaces.list.length; i += 1) {
            intf = networkSettings.interfaces.list[i];
            data.push([systemDev ? intf.systemDev.toString() : intf.interfaceId.toString(), intf.name]);
        }

        if (systemDev) {
            data.push(['tun0', 'OpenVPN']);
        } else {
            data.push(['250', 'OpenVPN']); // 0xfa
            data.push(['251', 'L2TP']); // 0xfb
            data.push(['252', 'Xauth']); // 0xfc
            data.push(['253', 'GRE']); // 0xfd
        }
        if (wanMatchers) {
            data.unshift(['wan', 'Any WAN'.t()]);
            data.unshift(['non_wan', 'Any Non-WAN'.t()]);
        }
        if (anyMatcher) {
            data.unshift(['any', 'Any'.t()]);
        }
        return data;
    },
    getInterfaceList: function (wanMatchers, anyMatcher) {
        return Util.getInterfaceListSystemDev(wanMatchers, anyMatcher, false);
    },

    getInterfaceAddressedList: function() {
        var data = [];
        Ext.Array.each(rpc.networkSettings.interfaces.list, function (intf) {
            if (intf.configType === 'ADDRESSED') {
                data.push([intf.interfaceId, intf.name]);
            }
        });
        return data;
    },

    bytesToMBs: function(value) {
        return Math.round(value/10000)/100;
    },

    // used for render purposes
    interfacesListNamesMap: function () {
        var map = {
            'wan': 'Any WAN'.t(),
            'non_wan': 'Any Non-WAN'.t(),
            'any': 'Any'.t(),
            '250': 'OpenVPN',
            '251': 'L2TP',
            '252': 'Xauth',
            '253': 'GRE'
        };
        var i, intf;

        for (i = 0; i < rpc.networkSettings.interfaces.list.length; i += 1) {
            intf = rpc.networkSettings.interfaces.list[i];
            map[intf.systemDev] = intf.name;
            map[intf.interfaceId] = intf.name;
        }
        return map;
    },

    urlValidator: function (val) {
        var res = val.match(/(http(s)?:\/\/.)?(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)/g);
        return res ? true : 'Url missing or in wrong format!'.t();
    },

    /**
     * Helper method that lists the order in which classes are loaded
     */
    getClassOrder: function () {
        var classes = [], extClasses = [];

        Ext.Loader.history.forEach(function (cls) {
            if (cls.indexOf('Ung') === 0) {
                classes.push(cls.replace('Ung', 'app').replace(/\./g, '/') + '.js');
            } else {
                extClasses.push(cls);
            }
        });

        classes.pop();

        Ext.create('Ext.Window', {
            title: 'Untangle Classes Load Order',
            width: 600,
            height: 600,

            // Constraining will pull the Window leftwards so that it's within the parent Window
            modal: true,
            draggable: false,
            resizable: false,
            layout: {
                type: 'hbox',
                align: 'stretch',
                pack: 'end'
            },
            items: [{
                xtype: 'textarea',
                border: false,
                flex: 1,
                editable: false,
                fieldStyle: {
                    background: '#FFF',
                    fontSize: '11px'
                },
                value: classes.join('\r\n')
            }, {
                xtype: 'textarea',
                border: false,
                flex: 1,
                editable: false,
                fieldStyle: {
                    background: '#FFF',
                    fontSize: '11px'
                },
                value: extClasses.join('\r\n')
            }]
        }).show();
    },

    getV4NetmaskList: function(includeNull) {
        var data = [];
        if (includeNull) {
            data.push( [null,'\u00a0'] );
        }
        data.push( [32,'/32 - 255.255.255.255'] );
        data.push( [31,'/31 - 255.255.255.254'] );
        data.push( [30,'/30 - 255.255.255.252'] );
        data.push( [29,'/29 - 255.255.255.248'] );
        data.push( [28,'/28 - 255.255.255.240'] );
        data.push( [27,'/27 - 255.255.255.224'] );
        data.push( [26,'/26 - 255.255.255.192'] );
        data.push( [25,'/25 - 255.255.255.128'] );
        data.push( [24,'/24 - 255.255.255.0'] );
        data.push( [23,'/23 - 255.255.254.0'] );
        data.push( [22,'/22 - 255.255.252.0'] );
        data.push( [21,'/21 - 255.255.248.0'] );
        data.push( [20,'/20 - 255.255.240.0'] );
        data.push( [19,'/19 - 255.255.224.0'] );
        data.push( [18,'/18 - 255.255.192.0'] );
        data.push( [17,'/17 - 255.255.128.0'] );
        data.push( [16,'/16 - 255.255.0.0'] );
        data.push( [15,'/15 - 255.254.0.0'] );
        data.push( [14,'/14 - 255.252.0.0'] );
        data.push( [13,'/13 - 255.248.0.0'] );
        data.push( [12,'/12 - 255.240.0.0'] );
        data.push( [11,'/11 - 255.224.0.0'] );
        data.push( [10,'/10 - 255.192.0.0'] );
        data.push( [9,'/9 - 255.128.0.0'] );
        data.push( [8,'/8 - 255.0.0.0'] );
        data.push( [7,'/7 - 254.0.0.0'] );
        data.push( [6,'/6 - 252.0.0.0'] );
        data.push( [5,'/5 - 248.0.0.0'] );
        data.push( [4,'/4 - 240.0.0.0'] );
        data.push( [3,'/3 - 224.0.0.0'] );
        data.push( [2,'/2 - 192.0.0.0'] );
        data.push( [1,'/1 - 128.0.0.0'] );
        data.push( [0,'/0 - 0.0.0.0'] );

        return data;
    },

    validateForms: function (view) {
        var invalidFields = [];

        view.query('form[withValidation]').forEach(function (form) {
            if (form.isDirty()) {
                form.query('field{isValid()==false}').forEach(function (field) {
                    invalidFields.push({ label: field.getFieldLabel(), error: field.getActiveError() });
                    // invalidFields.push(field);
                });
            }
        });

        if (invalidFields.length > 0) {
            Util.invalidFormToast(invalidFields);
            return false;
        }
        return true;
    },

    urlValidator2: function (url) {
        if (url.match(/^([^:]+):\/\// ) !== null) {
            return 'Site cannot contain URL protocol.'.t();
        }
        if (url.match(/^([^:]+):\d+\// ) !== null) {
            return 'Site cannot contain port.'.t();
        }
        // strip "www." from beginning of rule
        if (url.indexOf('www.') === 0) {
            url = url.substr(4);
        }
        // strip "*." from beginning of rule
        if (url.indexOf('*.') === 0) {
            url = url.substr(2);
        }
        // strip "/" from the end
        if (url.indexOf('/') === url.length - 1) {
            url = url.substring(0, url.length - 1);
        }
        if (url.trim().length === 0) {
            return 'Invalid URL specified'.t();
        }
        return true;
    },

    // formats a timestamp - expects a timestamp integer or an onject literal with 'time' property
    timestampFormat: function(v) {
        if (!v || typeof v === 'string') {
            return 0;
        }
        var date = new Date();
        if (typeof v === 'object' && v.time) {
            date.setTime(v.time);
        } else {
            date.setTime(v);
        }
        return Ext.util.Format.date(date, 'timestamp_fmt'.t());
    },

    getStoreUrl: function(){
        // non API store URL used for links like: My Account, Forgot Password
        return rpc.storeUrl.replace('/api/v1', '/store/open.php');
    },

    getAbout: function (forceReload) {
        if (rpc.about === undefined) {
            var query = "";
            query = query + "uid=" + rpc.serverUID;
            query = query + "&" + "version=" + rpc.fullVersion;
            query = query + "&" + "webui=true";
            query = query + "&" + "lang=" + rpc.languageSettings.language;
            query = query + "&" + "applianceModel=" + rpc.applianceModel;

            rpc.about = query;
        }
        return rpc.about;
    },

    weekdaysMap: {
        '1': 'Sunday'.t(),
        '2': 'Monday'.t(),
        '3': 'Tuesday'.t(),
        '4': 'Wednesday'.t(),
        '5': 'Thursday'.t(),
        '6': 'Friday'.t(),
        '7': 'Saturday'.t()
    },

    keyStr : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",

    base64encode: function(input) {
        if (typeof(base64encode) === 'function') {
            return base64encode(input);
        }
        var output = "";
        var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
        var i = 0;
        input = Util.utf8Encode(input);
        while (i < input.length) {
            chr1 = input.charCodeAt(i++);
            chr2 = input.charCodeAt(i++);
            chr3 = input.charCodeAt(i++);
            enc1 = chr1 >> 2;
            enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
            enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
            enc4 = chr3 & 63;
            if (isNaN(chr2)) {
                enc3 = enc4 = 64;
            } else if (isNaN(chr3)) {
                enc4 = 64;
            }
            output = output +
            Util.keyStr.charAt(enc1) + Util.keyStr.charAt(enc2) +
            Util.keyStr.charAt(enc3) + Util.keyStr.charAt(enc4);
        }
        return output;
    },

    utf8Encode : function (string) {
        string = string.replace(/\r\n/g,"\n");
        var utftext = "";
        for (var n = 0; n < string.length; n++) {
            var c = string.charCodeAt(n);
            if (c < 128) {
                utftext += String.fromCharCode(c);
            }
            else if((c > 127) && (c < 2048)) {
                utftext += String.fromCharCode((c >> 6) | 192);
                utftext += String.fromCharCode((c & 63) | 128);
            }
            else {
                utftext += String.fromCharCode((c >> 12) | 224);
                utftext += String.fromCharCode(((c >> 6) & 63) | 128);
                utftext += String.fromCharCode((c & 63) | 128);
            }
        }
        return utftext;
    }

});
