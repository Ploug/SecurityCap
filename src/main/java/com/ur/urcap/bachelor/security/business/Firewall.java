/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ur.urcap.bachelor.security.business;

import com.ur.urcap.bachelor.security.business.shell.ShellCommunicator;
import com.ur.urcap.bachelor.security.exceptions.UnsuccessfulCommandException;
import com.ur.urcap.bachelor.security.services.ShellComService;
import com.ur.urcap.bachelor.security.services.ShellCommandResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.sf.jIPtables.log.LogListener;
import net.sf.jIPtables.log.LogTracker;
import net.sf.jIPtables.log.Packet;
import net.sf.jIPtables.rules.Chain;
import com.ur.urcap.bachelor.security.services.ActivityListener;

/**
 *
 * @author frede
 */
public class Firewall
{

    private Chain output;
    private Chain input;
    private ShellComService shellCom;
    private final String folderName = "Firewall";
    private final String fs = System.getProperty("file.separator");
    private final String libPath = fs + "usr" + fs + "lib" + fs + "jni" + fs;
    private Pattern ipPattern;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
    private final List<LogActivity> interestingActivity = new ArrayList();
    private List<LogActivity> allActivity = new ArrayList();

    private ActivityListener activityListener;

    private int count = 0;
    private final String IPADDRESS_PATTERN
            = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
            + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
            + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
            + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

    private static Firewall instance = null;

    public static Firewall getInstance()
    {

        if (instance == null)
        {
            instance = new Firewall();
        }
        return instance;
    }

    private final LogListener logActivity = new LogListener()
    {
        @Override
        public void onNewLog(Packet newPacket)
        {
            LogActivity logActivity = new LogActivity(sdf.format(new Date()));

            /*if (newPacket.getSourceAddress().toString().contains("localhost"))   should do this in a real scenario
            {
                return;
            } */
            String packetInString = newPacket.toString();
            boolean interesting = false;
            if (newPacket.getPrefix().equals("INDROPPED"))
            {
                int startIndex = packetInString.indexOf("dport=") + "dport=".length();
                String port = packetInString.substring(startIndex, packetInString.indexOf(",", startIndex));
                logActivity.message = "Packet from " + newPacket.getSourceAddress() + " to port: " + port + " was dropped.";
                interesting = true;
            }
            else if (newPacket.getPrefix().equals("SSHATTEMPT"))
            {
                logActivity.message = newPacket.getSourceAddress() + " is messaging the SSH port";
                interesting = true;
            }
            else if (newPacket.getPrefix().equals("HTTPATTEMPT"))
            {
                logActivity.message = newPacket.getSourceAddress() + " is messaging the HTTP port";
                interesting = true;
            }
            else if (newPacket.getPrefix().equals("PORTSCAN"))
            {
                logActivity.message = "Port scanning attempt from " + newPacket.getSourceAddress() + " denied.";
                interesting = true;
            }
            else if (newPacket.getPrefix().equals("PORTSCAN"))
            {
                logActivity.message = "Port scanning attempt from " + newPacket.getSourceAddress() + " denied.";
                interesting = true;
            }
            else if (newPacket.getPrefix().equals("SSHBRUTE"))
            {
                logActivity.message = "SSH bruteforce denied from " + newPacket.getSourceAddress() + ", 60 seconds pause.";
                interesting = true;
            }

            if (interesting && !interestingActivity.contains(logActivity))
            {
                interestingActivity.add(logActivity);
            }
            allActivity.add(logActivity);
            if (activityListener != null)
            {
                activityListener.activityUpdate();
            }

            System.out.println("count: " + (count++));
            System.out.println("newPacket: " + newPacket.toString());
            for (LogActivity s : interestingActivity)
            {
                System.out.println(s.toString());
            }

        }

    };

    // Setup standard firewall settings
    private Firewall()
    {
        System.load(libPath + "libjiptables_log.so");
        System.load(libPath + "libjiptables_conntrack.so");

        LogTracker tracker = LogTracker.getInstance();
        tracker.addLogListener(logActivity);

        ipPattern = Pattern.compile(IPADDRESS_PATTERN);
        shellCom = new ShellCommunicator();

        setDefaultRules();

    }

    public String[] getAllActivity(int amount)
    {

        String[] retVal;
        if (amount > allActivity.size() || amount < 1)
        {
            retVal = new String[allActivity.size()];
        }
        else
        {
            retVal = new String[amount];
        }

        for (int i = 0; i < retVal.length; i++)
        {
            retVal[i] = allActivity.get(allActivity.size() - retVal.length + i).toString(); // Getting "amount" latest
        }

        return retVal;

    }

    /**
     *
     * @param amount amount of activities to show. -1 gives all possible
     * @return
     */
    public String[] getInterestingActivity(int amount)
    {
        String[] retVal;
        if (amount > interestingActivity.size() || amount < 1)
        {
            retVal = new String[interestingActivity.size()];
        }
        else
        {
            retVal = new String[amount];
        }

        for (int i = 0; i < retVal.length; i++)
        {
            retVal[i] = interestingActivity.get(interestingActivity.size() - retVal.length + i).toString(); // Getting "amount" latest
        }

        return retVal;

    }

    private void setDefaultRules()
    {
        try
        {   //for portscanning protection
            shellCom.doCommand("iptables -N port-scanning");
            // drop all incoming packets by default, accept forwarding and output packets by default
            shellCom.doCommand("iptables --policy INPUT ACCEPT");
            shellCom.doCommand("iptables --policy FORWARD ACCEPT");
            shellCom.doCommand("iptables --policy OUTPUT ACCEPT");
        }
        catch (UnsuccessfulCommandException ex)
        {
            Logger.getLogger(Firewall.class.getName()).log(Level.SEVERE, null, ex);
        }

        //Allow localhost 
        appendIpTablesRule("INPUT -s localhost -j ACCEPT");
        //Allow established 
        appendIpTablesRule("INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT");
        appendIpTablesRule("OUTPUT -m conntrack --ctstate ESTABLISHED -j ACCEPT");

        //SSH Brute force protection
        appendIpTablesRule("INPUT -p tcp --dport ssh -m conntrack --ctstate NEW -m recent --set");
        appendIpTablesRule("INPUT -p tcp --dport ssh -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 10 -j NFLOG --nflog-prefix SSHBRUTE"); // Logging ssh brute attempts
        appendIpTablesRule("INPUT -p tcp --dport ssh -m conntrack --ctstate NEW -m recent --update --seconds 60 --hitcount 10 -j DROP  ");
        //Port scanning protection
        appendIpTablesRule("port-scanning -p tcp --tcp-flags SYN,ACK,FIN,RST RST -m limit --limit 1/s --limit-burst 2 -j RETURN ");
        appendIpTablesRule("port-scanning -j NFLOG --nflog-prefix PORTSCAN"); // Logging port scanning attempts
        appendIpTablesRule("port-scanning -j DROP");
        //Allow http and ssh tcp connections
        appendIpTablesRule("INPUT -p tcp --dport 22 -m conntrack --ctstate NEW,ESTABLISHED -j NFLOG --nflog-prefix SSHATTEMPT");
        appendIpTablesRule("INPUT -p tcp --dport 22 -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT");

        appendIpTablesRule("INPUT -p tcp --dport 80 -m conntrack --ctstate NEW,ESTABLISHED -j NFLOG --nflog-prefix HTTPATTEMPT");
        appendIpTablesRule("INPUT -p tcp --dport 80 -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT");

        //Allow icmp.  (General conclusion is the only con is possible DDOS attack, while there is several pros, such as MTU discovery and pinging)
        appendIpTablesRule("INPUT -p icmp -j ACCEPT");

        //Allow Loopback Connections
        appendIpTablesRule("INPUT -i lo -j ACCEPT");
        appendIpTablesRule("OUTPUT -o lo -j ACCEPT");

        //Drop invalid packets
        appendIpTablesRule("INPUT -m conntrack --ctstate INVALID -j DROP");

        // logging
        appendIpTablesRule("INPUT -j NFLOG --nflog-prefix INDROPPED");

    }

    /**
     * For now it is all protocols.
     *
     * @param port the port number to accept
     */
    public void acceptPort(int port) throws UnsuccessfulCommandException
    {
        if (port > 0 && port < 65536)
        {
            appendIpTablesRule("INPUT -p tcp --dport " + port + " -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT");
        }
        else
        {
            throw new UnsuccessfulCommandException("Port is not correct range");
        }

    }

    /**
     * For now it is all protocols.
     *
     * @param port the port number to deny
     */
    public void denyPort(int port) throws UnsuccessfulCommandException
    {
        if (port > 0 && port < 65535)
        {
            try
            {
                shellCom.doCommand("iptables -D INPUT -p tcp --dport " + port + " -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT");
            }
            catch (UnsuccessfulCommandException ex)
            {
                Logger.getLogger(Firewall.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        else
        {
            throw new UnsuccessfulCommandException("Port is not correct range");
        }
    }

    public void blockIP(String IP) throws UnsuccessfulCommandException
    {

        if (ipPattern.matcher(IP).matches())
        {
            appendIpTablesRule("INPUT -s " + IP + " -j DROP");
        }
        else
        {
            throw new UnsuccessfulCommandException("IP address is not correct format");
        }

    }

    public void setActivityListener(ActivityListener cb)
    {
        activityListener = cb;

    }

    // Helper method
    private void deleteIpTablesRule(String rule)
    {
        try
        {
            shellCom.doCommand("iptables -D " + rule);

        }
        catch (UnsuccessfulCommandException ex)
        {
            Logger.getLogger(Firewall.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void insertIpTablesRule(String rule, int number)
    {
        int spaceIndex = rule.indexOf(" ");
        String s = rule.substring(0, spaceIndex + 1);
        String s2 = rule.substring(spaceIndex);
        String actualRule = s + number + s2;
        try
        {
            // Only do stuff if it isn't already done
            ShellCommandResponse response = shellCom.doCommand("iptables -C " + rule);
            if (response.getExitValue() != 0)
            {
                shellCom.doCommand("iptables -I " + actualRule);
            }

        }
        catch (UnsuccessfulCommandException ex)
        {
            Logger.getLogger(Firewall.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Helper method
    private void appendIpTablesRule(String rule)
    {
        try
        {
            // Only do stuff if it isn't already done
            ShellCommandResponse response = shellCom.doCommand("iptables -C " + rule);
            if (response.getExitValue() != 0)
            {
                shellCom.doCommand("iptables -A " + rule);
            }

        }
        catch (UnsuccessfulCommandException ex)
        {
            Logger.getLogger(Firewall.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void persist()
    {
        try
        {
            shellCom.doCommand("invoke-rc.d iptables-persistent save");
        }
        catch (UnsuccessfulCommandException ex)
        {
            Logger.getLogger(Firewall.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
