package com.aem.myproject.CustomAuthentication;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.auth.core.spi.DefaultAuthenticationFeedbackHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;


public class CustomAuthHandler extends DefaultAuthenticationFeedbackHandler {

  @Reference
  CustomAuthenticationHandler authHandler;

  private ServiceRegistration loginModule;

  @Activate
  protected void activate(ComponentContext componentContext) {
    this.loginModule = null;
    try {
      this.loginModule = CustomLoginModule
          .register(authHandler, componentContext.getBundleContext());
    } catch (Throwable t) {
    }

  }

  @Deactivate
  protected void deactivate(@SuppressWarnings("unused") ComponentContext componentContext) {
    if (loginModule != null) {
      loginModule.unregister();
      loginModule = null;
    }
  }

}
