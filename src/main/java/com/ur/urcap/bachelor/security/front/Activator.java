package com.ur.urcap.bachelor.security.front;

import com.ur.urcap.api.contribution.InstallationNodeService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator
{

    public void start(BundleContext context) throws Exception
    {
        SecurityInstallationNodeService netSecInstall = new SecurityInstallationNodeService();

        context.registerService(InstallationNodeService.class, netSecInstall, null);
    }

    public void stop(BundleContext context) throws Exception
    {
        // TODO add deactivation code here
    }

}
