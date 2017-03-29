package com.aem.myproject.CustomAuthentication;


import java.security.Principal;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.security.auth.callback.CallbackHandler;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.jcr.jackrabbit.server.security.AuthenticationPlugin;
import org.apache.sling.jcr.jackrabbit.server.security.LoginModulePlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CustomLoginModule implements LoginModulePlugin {

  private final CustomAuthenticationHandler authHandler;


  private final Logger log = LoggerFactory.getLogger(this.getClass());


  private CustomLoginModule(final CustomAuthenticationHandler authHandler) {
    log.debug("*** loginModule constructor ***");
    this.authHandler = authHandler;
  }

  static ServiceRegistration register(final CustomAuthenticationHandler authHandler,
      final BundleContext bundleContext) {
    CustomLoginModule plugin = new CustomLoginModule(authHandler);
    Hashtable<String, Object> properties = new Hashtable<String, Object>();
    properties.put(Constants.SERVICE_DESCRIPTION,
        "LoginModulePlugin Support for OpenIDAuthenticationHandler");
    properties.put(Constants.SERVICE_VENDOR,
        bundleContext.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));

    return bundleContext.registerService(LoginModulePlugin.class.getName(), plugin, properties);
  }

  @SuppressWarnings("unchecked")
  public void doInit(final CallbackHandler callbackHandler, final Session session,
      final Map options) {
    log.debug("*** loginModule doInit ***");
    return;
  }


  public boolean canHandle(Credentials credentials) {
    return StringUtils.isNotBlank(authHandler.getUserId(credentials));
  }


  public AuthenticationPlugin getAuthentication(final Principal principal,
      final Credentials creds) {
    return new AuthenticationPlugin() {
      public boolean authenticate(Credentials credentials) throws RepositoryException {
        log.debug("*** loginModule getAuthetication ***");
        return StringUtils.isNotBlank(authHandler.getUserId(credentials));
      }

    };

  }


  public Principal getPrincipal(final Credentials credentials) {
    log.debug("*** loginModule getPrincipal ***");
    return null;
  }


  @SuppressWarnings("unchecked")
  public void addPrincipals(final Set principals) {
    log.debug("*** loginModule addPrincipals ***");
  }


  public int impersonate(final Principal principal, final Credentials credentials) {
    log.debug("*** loginModule impersonate ***");
    return LoginModulePlugin.IMPERSONATION_DEFAULT;
  }
}

