package com.ur.urcap.bachelor.security.front;

import com.ur.urcap.api.contribution.InstallationNodeContribution;
import com.ur.urcap.api.contribution.InstallationNodeService;
import com.ur.urcap.api.domain.URCapAPI;
import com.ur.urcap.api.domain.data.DataModel;
import com.ur.urcap.bachelor.security.business.Firewall;
import java.io.InputStream;

public class SecurityInstallationNodeService implements InstallationNodeService
{

    public SecurityInstallationNodeService()
    {
      Firewall.getInstance(); //initialize
    }

    @Override
    public InstallationNodeContribution createInstallationNode(URCapAPI api, DataModel model)
    {
        return new SecurityInstallationNodeContribution(model);
    }

    @Override
    public String getTitle()
    {
        return "Security";
    }

    @Override
    public InputStream getHTML()
    {
        InputStream is = this.getClass().getResourceAsStream("/com/ur/urcap/bachelor/networksecurity/impl/installation.html");
        return is;
    }
}
