package com.untangle.uvm.webui.servlet;

import java.io.IOException;

import java.util.Random;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jabsorb.JSONSerializer;
import org.jabsorb.serializer.MarshallException;

import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.Period;
import com.untangle.uvm.node.HostName;
import com.untangle.uvm.networking.AddressSettings;
import com.untangle.uvm.networking.BasicNetworkSettings;
import com.untangle.uvm.networking.NetworkUtil;
import com.untangle.uvm.networking.LocalNetworkManager;
import com.untangle.uvm.networking.Interface;
import com.untangle.uvm.security.RegistrationInfo;
import com.untangle.uvm.toolbox.UpgradeSettings;

import com.untangle.uvm.webui.jabsorb.serializer.EnumSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.ExtendedListSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.ExtendedSetSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.HostNameSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.IPMaddrSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.IPaddrSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.LazyInitializerSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.MimeTypeSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.RFC2253NameSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.TimeZoneSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.URLSerializer;

/**
 * A servlet which will display the start page
 *
 * @author Catalin Matei <cmatei@untangle.com>
 */
public class SetupSettingsServlet extends HttpServlet
{
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
    {
        LocalUvmContext context = LocalUvmContextFactory.context();
            
        JSONSerializer js = new JSONSerializer();
        try {
            js.registerDefaultSerializers();

            // general serializers
            js.registerSerializer(new EnumSerializer());
            js.registerSerializer(new URLSerializer());
            // uvm related serializers
            js.registerSerializer(new IPMaddrSerializer());
            js.registerSerializer(new IPaddrSerializer());
            js.registerSerializer(new HostNameSerializer());
            js.registerSerializer(new TimeZoneSerializer());
            js.registerSerializer(new MimeTypeSerializer());
            js.registerSerializer(new RFC2253NameSerializer());
            // hibernate related serializers
            js.registerSerializer(new LazyInitializerSerializer());
            js.registerSerializer(new ExtendedListSerializer());
            js.registerSerializer(new ExtendedSetSerializer());
        } catch ( Exception e ) {
            throw new ServletException( "Unable to load the default serializer", e );
        }

        LocalNetworkManager nm = context.networkManager();
        AddressSettings addressSettings = nm.getAddressSettings();
        HostName hostname = addressSettings.getHostName();
        if ( hostname.isEmpty() || !hostname.isQualified()) {
            addressSettings.setHostName( NetworkUtil.DEFAULT_HOSTNAME );
        }               

        RegistrationInfo ri = new RegistrationInfo();
        ri.setMisc( new java.util.Hashtable<String,String>());
        
        // pick a random time.
        UpgradeSettings upgrade = context.toolboxManager().getUpgradeSettings();

        BasicNetworkSettings networkSettings = nm.getBasicSettings();
        try {
            request.setAttribute( "addressSettings", js.toJSON( addressSettings ));
            request.setAttribute( "interfaceArray", js.toJSON( nm.getInterfaceList( true )));
            request.setAttribute( "registrationInfo", js.toJSON( ri ));
            request.setAttribute( "users", js.toJSON( context.adminManager().getAdminSettings()));
            request.setAttribute( "upgradeSettings", js.toJSON( upgrade ));

            request.setAttribute( "mailSettings", js.toJSON( context.mailSender().getMailSettings()));
            request.setAttribute( "networkSettings", js.toJSON( networkSettings ));
        } catch ( MarshallException e ) {
            throw new ServletException( "Unable to serializer JSON", e );
        }

        String url="/WEB-INF/jsp/setupSettings.jsp";
        ServletContext sc = getServletContext();
        RequestDispatcher rd = sc.getRequestDispatcher(url);
        response.setContentType("text/javascript");
        rd.forward(request, response);
    }
}
