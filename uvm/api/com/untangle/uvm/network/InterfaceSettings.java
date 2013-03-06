/**
 * $Id: InterfaceSettings.java 32043 2012-05-31 21:31:47Z dmorris $
 */
package com.untangle.uvm.network;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONString;

import com.untangle.uvm.node.IPMaskedAddress;

/**
 * Interface settings.
 */
@SuppressWarnings("serial")
public class InterfaceSettings implements Serializable, JSONString
{
    private static final Logger logger = Logger.getLogger( InterfaceSettings.class );

    private static InetAddress V4_PREFIX_NETMASKS[];

    private int     interfaceId; /* the ID of the physical interface (1-254) */
    private String  name; /* human name: ie External, Internal, Wireless */

    private String  physicalDev; /* physical interface name: eth0, etc */
    private String  systemDev; /* iptables interface name: eth0, eth0:0, eth0.1, etc */
    private String  symbolicDev; /* symbolic interface name: eth0, eth0:0, eth0.1, etc */

    private boolean isWan = false; /* is a WAN interface? */

    public static enum ConfigType { ADDRESSED, BRIDGED, DISABLED };
    private ConfigType configType = ConfigType.DISABLED; /* config type */

    private Integer bridgedTo; /* device to bridge to in "bridged" case */
    
    public static enum V4ConfigType { STATIC, AUTO, PPPOE };
    private V4ConfigType v4ConfigType = V4ConfigType.AUTO; /* IPv4 config type */
    
    private InetAddress v4StaticAddress; /* the address  of this interface if configured static, or dhcp override */ 
    private Integer     v4StaticPrefix; /* the netmask of this interface if configured static, or dhcp override */
    private InetAddress v4StaticGateway; /* the gateway  of this interface if configured static, or dhcp override */
    private InetAddress v4StaticDns1; /* the dns1  of this interface if configured static */
    private InetAddress v4StaticDns2; /* the dns2  of this interface if configured static */
    private InetAddress v4AutoAddressOverride; /* the dhcp override address (null means don't override) */ 
    private Integer     v4AutoPrefixOverride; /* the dhcp override netmask (null means don't override) */ 
    private InetAddress v4AutoGatewayOverride; /* the dhcp override gateway (null means don't override) */ 
    private InetAddress v4AutoDns1Override; /* the dhcp override dns1 (null means don't override) */
    private InetAddress v4AutoDns2Override; /* the dhcp override dns2 (null means don't override) */

    private List<InterfaceAlias> v4Aliases = new LinkedList<InterfaceAlias>();
    
    private String      v4PPPoEUsername; /* PPPoE Username */
    private String      v4PPPoEPassword; /* PPPoE Password */
    private Boolean     v4PPPoEUsePeerDns; /* If the DNS should be determined via PPP */
    private InetAddress v4PPPoEDns1; /* the dns1  of this interface if configured static */
    private InetAddress v4PPPoEDns2; /* the dns2  of this interface if configured static */

    private Boolean     v4NatEgressTraffic;
    private Boolean     v4NatIngressTraffic;
    
    public static enum V6ConfigType { STATIC, AUTO };
    private V6ConfigType v6ConfigType = V6ConfigType.AUTO; /* IPv6 config type */
    
    private InetAddress v6StaticAddress; /* the address  of this interface if configured static, or dhcp override */ 
    private Integer     v6StaticPrefixLength; /* the netmask  of this interface if configured static, or dhcp override */
    private InetAddress v6StaticGateway; /* the gateway  of this interface if configured static, or dhcp override */
    private InetAddress v6StaticDns1; /* the dns1  of this interface if configured static, or dhcp override */
    private InetAddress v6StaticDns2; /* the dns2  of this interface if configured static, or dhcp override */

    private Boolean dhcpEnabled; /* is DHCP serving enabled on this interface? */
    private InetAddress dhcpRangeStart; /* where do DHCP leases start? example: 192.168.2.100*/
    private InetAddress dhcpRangeEnd; /* where do DHCP leases end? example: 192.168.2.200 */
    private Integer dhcpLeaseDuration; /* DHCP lease duration in seconds */
    private InetAddress dhcpGatewayOverride; /* DHCP gateway override, if null defaults to this interface's IP */
    private Integer     dhcpPrefixOverride; /* DHCP netmask override, if null defaults to this interface's netmask */
    private InetAddress dhcpDnsOverride; /* DHCP DNS override, if null defaults to this interface's IP */
    
    private List<IPMaskedAddress> aliases; /* alias addresses for static & dhcp */

    private Integer downloadBandwidthKbps;
    private Integer uploadBandwidthKbps;
    
    public InterfaceSettings() { }

    public String toJSONString()
    {
        JSONObject jO = new JSONObject(this);
        return jO.toString();
    }

    public int getInterfaceId( ) { return this.interfaceId; }
    public void setInterfaceId( int newValue ) { this.interfaceId = newValue; }

    public String getName( ) { return this.name; }
    public void setName( String newValue ) { this.name = newValue; }

    public String getPhysicalDev( ) { return this.physicalDev; }
    public void setPhysicalDev( String newValue ) { this.physicalDev = newValue; }

    public String getSystemDev( ) { return this.systemDev; }
    public void setSystemDev( String newValue ) { this.systemDev = newValue; }

    public String getSymbolicDev( ) { return this.symbolicDev; }
    public void setSymbolicDev( String newValue ) { this.symbolicDev = newValue; }

    public boolean getIsWan( ) { return this.isWan; }
    public void setIsWan( boolean newValue ) { this.isWan = newValue; }

    public ConfigType getConfigType( ) { return this.configType; }
    public void setConfigType( ConfigType newValue ) { this.configType = newValue; }
    
    public Integer getBridgedTo( ) { return this.bridgedTo; }
    public void setBridgedTo( Integer newValue ) { this.bridgedTo = newValue; }
    
    public V4ConfigType getV4ConfigType( ) { return this.v4ConfigType; }
    public void setV4ConfigType( V4ConfigType newValue ) { this.v4ConfigType = newValue; }

    public InetAddress getV4StaticAddress( ) { return this.v4StaticAddress; }
    public void setV4StaticAddress( InetAddress newValue ) { this.v4StaticAddress = newValue; }

    public Integer getV4StaticPrefix( ) { return this.v4StaticPrefix; }
    public void setV4StaticPrefix( Integer newValue ) { this.v4StaticPrefix = newValue; }
    
    public InetAddress getV4StaticGateway( ) { return this.v4StaticGateway; }
    public void setV4StaticGateway( InetAddress newValue ) { this.v4StaticGateway = newValue; }
    
    public InetAddress getV4StaticDns1( ) { return this.v4StaticDns1; }
    public void setV4StaticDns1( InetAddress newValue ) { this.v4StaticDns1 = newValue; }

    public InetAddress getV4StaticDns2( ) { return this.v4StaticDns2; }
    public void setV4StaticDns2( InetAddress newValue ) { this.v4StaticDns2 = newValue; }

    public InetAddress getV4AutoAddressOverride( ) { return this.v4AutoAddressOverride; }
    public void setV4AutoAddressOverride( InetAddress newValue ) { this.v4AutoAddressOverride = newValue; }
    
    public Integer getV4AutoPrefixOverride( ) { return this.v4AutoPrefixOverride; }
    public void setV4AutoPrefixOverride( Integer newValue ) { this.v4AutoPrefixOverride = newValue; }
    
    public InetAddress getV4AutoGatewayOverride( ) { return this.v4AutoGatewayOverride; }
    public void setV4AutoGatewayOverride( InetAddress newValue ) { this.v4AutoGatewayOverride = newValue; }

    public InetAddress getV4AutoDns1Override( ) { return this.v4AutoDns1Override; }
    public void setV4AutoDns1Override( InetAddress newValue ) { this.v4AutoDns1Override = newValue; }

    public InetAddress getV4AutoDns2Override( ) { return this.v4AutoDns2Override; }
    public void setV4AutoDns2Override( InetAddress newValue ) { this.v4AutoDns2Override = newValue; }

    public String getV4PPPoEUsername( ) { return this.v4PPPoEUsername; }
    public void setV4PPPoEUsername( String newValue ) { this.v4PPPoEUsername = newValue; }

    public String getV4PPPoEPassword( ) { return this.v4PPPoEPassword; }
    public void setV4PPPoEPassword( String newValue ) { this.v4PPPoEPassword = newValue; }

    public Boolean getV4PPPoEUsePeerDns( ) { return this.v4PPPoEUsePeerDns; }
    public void setV4PPPoEUsePeerDns( Boolean newValue ) { this.v4PPPoEUsePeerDns = newValue; }

    public InetAddress getV4PPPoEDns1( ) { return this.v4PPPoEDns1; }
    public void setV4PPPoEDns1( InetAddress newValue ) { this.v4PPPoEDns1 = newValue; }

    public InetAddress getV4PPPoEDns2( ) { return this.v4PPPoEDns2; }
    public void setV4PPPoEDns2( InetAddress newValue ) { this.v4PPPoEDns2 = newValue; }

    public Boolean getV4NatEgressTraffic( ) { return this.v4NatEgressTraffic; }
    public void setV4NatEgressTraffic( Boolean newValue ) { this.v4NatEgressTraffic = newValue; }

    public Boolean getV4NatIngressTraffic( ) { return this.v4NatIngressTraffic; }
    public void setV4NatIngressTraffic( Boolean newValue ) { this.v4NatIngressTraffic = newValue; }

    public List<InterfaceAlias> getV4Aliases( ) { return this.v4Aliases; }
    public void setV4Aliases( List<InterfaceAlias> newValue ) { this.v4Aliases = newValue; }
    
    public V6ConfigType getV6ConfigType( ) { return this.v6ConfigType; }
    public void setV6ConfigType( V6ConfigType newValue ) { this.v6ConfigType = newValue; }

    public InetAddress getV6StaticAddress( ) { return this.v6StaticAddress; }
    public void setV6StaticAddress( InetAddress newValue ) { this.v6StaticAddress = newValue; }

    public Integer getV6StaticPrefixLength( ) { return this.v6StaticPrefixLength; }
    public void setV6StaticPrefixLength( Integer newValue ) { this.v6StaticPrefixLength = newValue; }

    public InetAddress getV6StaticGateway( ) { return this.v6StaticGateway; }
    public void setV6StaticGateway( InetAddress newValue ) { this.v6StaticGateway = newValue; }

    public InetAddress getV6StaticDns1( ) { return this.v6StaticDns1; }
    public void setV6StaticDns1( InetAddress newValue ) { this.v6StaticDns1 = newValue; }

    public InetAddress getV6StaticDns2( ) { return this.v6StaticDns2; }
    public void setV6StaticDns2( InetAddress newValue ) { this.v6StaticDns2 = newValue; }
    
    public List<IPMaskedAddress> getAliases( ) { return this.aliases; }
    public void setAliases( List<IPMaskedAddress> newValue ) { this.aliases = newValue; }

    public Boolean getDhcpEnabled() { return this.dhcpEnabled; }
    public void setDhcpEnabled( Boolean newValue ) { this.dhcpEnabled = newValue; }

    public InetAddress getDhcpRangeStart() { return this.dhcpRangeStart; }
    public void setDhcpRangeStart( InetAddress newValue ) { this.dhcpRangeStart = newValue; }

    public InetAddress getDhcpRangeEnd() { return this.dhcpRangeEnd; }
    public void setDhcpRangeEnd( InetAddress newValue ) { this.dhcpRangeEnd = newValue; }

    public Integer getDhcpLeaseDuration() { return this.dhcpLeaseDuration; }
    public void setDhcpLeaseDuration( Integer newValue ) { this.dhcpLeaseDuration = newValue; }

    public InetAddress getDhcpGatewayOverride() { return this.dhcpGatewayOverride; }
    public void setDhcpGatewayOverride( InetAddress newValue ) { this.dhcpGatewayOverride = newValue; }

    public Integer getDhcpPrefixOverride() { return this.dhcpPrefixOverride; }
    public void setDhcpPrefixOverride( Integer newValue ) { this.dhcpPrefixOverride = newValue; }

    public InetAddress getDhcpDnsOverride() { return this.dhcpDnsOverride; }
    public void setDhcpDnsOverride( InetAddress newValue ) { this.dhcpDnsOverride = newValue; }

    public Integer getDownloadBandwidthKbps() { return this.downloadBandwidthKbps; }
    public void setDownloadBandwidthKbps( Integer newValue ) { this.downloadBandwidthKbps = newValue; }

    public Integer getUploadBandwidthKbps() { return this.uploadBandwidthKbps; }
    public void setUploadBandwidthKbps( Integer newValue ) { this.uploadBandwidthKbps = newValue; }
    
    public static class InterfaceAlias
    {
        private InetAddress v4StaticAddress; /* the address  of this interface if configured static, or dhcp override */ 
        private Integer     v4StaticPrefix; /* the netmask of this interface if configured static, or dhcp override */

        public InterfaceAlias() {}
        
        public InetAddress getV4StaticAddress( ) { return this.v4StaticAddress; }
        public void setV4StaticAddress( InetAddress newValue ) { this.v4StaticAddress = newValue; }

        public Integer getV4StaticPrefix( ) { return this.v4StaticPrefix; }
        public void setV4StaticPrefix( Integer newValue ) { this.v4StaticPrefix = newValue; }

        public InetAddress getV4StaticNetmask( )
        {
            if (this.v4StaticPrefix == null || this.v4StaticPrefix < 0 || this.v4StaticPrefix > 32 )
                return null;
            return V4_PREFIX_NETMASKS[this.v4StaticPrefix];
        }
    }

    /**
     * Below is a collection of convenience methods
     * These are getters without setters so they can be used, and they will be in the JSON equivalent of this object.
     * However, there are not actually settings - they are derived from other settings.
     */

    public boolean getDisabled()
    {
        return getConfigType() == ConfigType.DISABLED;
    }

    public boolean getBridged()
    {
        return getConfigType() == ConfigType.BRIDGED;
    }

    public boolean getAddressed()
    {
        return getConfigType() == ConfigType.ADDRESSED;
    }

    public InetAddress getV4StaticNetmask( )
    {
        if (this.v4StaticPrefix == null || this.v4StaticPrefix < 0 || this.v4StaticPrefix > 32 )
            return null;
        return this.V4_PREFIX_NETMASKS[this.v4StaticPrefix];
    }

    public InetAddress getV4AutoNetmaskOverride( )
    {
        if (this.v4AutoPrefixOverride == null || this.v4AutoPrefixOverride < 0 || this.v4AutoPrefixOverride > 32 )
            return null;
        return this.V4_PREFIX_NETMASKS[this.v4AutoPrefixOverride];
    }
    
    public InetAddress getDhcpNetmaskOverride()
    {
        if (this.dhcpPrefixOverride == null || this.dhcpPrefixOverride < 0 || this.dhcpPrefixOverride > 32 )
            return null;
        return this.V4_PREFIX_NETMASKS[this.dhcpPrefixOverride];
    }

    static
    {
        try {
            V4_PREFIX_NETMASKS = new InetAddress[]{
                InetAddress.getByName("0.0.0.0"),
                InetAddress.getByName("128.0.0.0"),
                InetAddress.getByName("192.0.0.0"),
                InetAddress.getByName("224.0.0.0"),
                InetAddress.getByName("240.0.0.0"),
                InetAddress.getByName("248.0.0.0"),
                InetAddress.getByName("252.0.0.0"),
                InetAddress.getByName("254.0.0.0"),
                InetAddress.getByName("255.0.0.0"),
                InetAddress.getByName("255.128.0.0"),
                InetAddress.getByName("255.192.0.0"),
                InetAddress.getByName("255.224.0.0"),
                InetAddress.getByName("255.240.0.0"),
                InetAddress.getByName("255.248.0.0"),
                InetAddress.getByName("255.252.0.0"),
                InetAddress.getByName("255.254.0.0"),
                InetAddress.getByName("255.255.0.0"),
                InetAddress.getByName("255.255.128.0"),
                InetAddress.getByName("255.255.192.0"),
                InetAddress.getByName("255.255.224.0"),
                InetAddress.getByName("255.255.240.0"),
                InetAddress.getByName("255.255.248.0"),
                InetAddress.getByName("255.255.252.0"),
                InetAddress.getByName("255.255.254.0"),
                InetAddress.getByName("255.255.255.0"),
                InetAddress.getByName("255.255.255.128"),
                InetAddress.getByName("255.255.255.192"),
                InetAddress.getByName("255.255.255.224"),
                InetAddress.getByName("255.255.255.240"),
                InetAddress.getByName("255.255.255.248"),
                InetAddress.getByName("255.255.255.252"),
                InetAddress.getByName("255.255.255.254"),
                InetAddress.getByName("255.255.255.255")
            };
        } catch (Exception e) {}

    }
}
