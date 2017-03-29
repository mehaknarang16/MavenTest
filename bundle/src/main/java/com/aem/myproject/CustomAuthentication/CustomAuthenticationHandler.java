package com.aem.myproject.CustomAuthentication;

import java.io.IOException;
import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.auth.core.AuthUtil;
import org.apache.sling.auth.core.spi.AuthenticationFeedbackHandler;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.auth.core.spi.DefaultAuthenticationFeedbackHandler;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component(metatype = true, label = "Custom Authentication Handler")
@Service
@Properties({
    @Property(name = AuthenticationHandler.PATH_PROPERTY, value = "/"),
    @Property(name = Constants.SERVICE_DESCRIPTION, value = "Custom Authentication Handler")})

public class CustomAuthenticationHandler extends DefaultAuthenticationFeedbackHandler implements
    AuthenticationHandler,
    AuthenticationFeedbackHandler {

  static final String AUTH_TYPE = "CUSTOM_AUTH";
  static final String ATTR_HOST_NAME_FROM_REQUEST = "";
  static final String REQUEST_URL_SUFFIX = "/j_mycustom_security_check";
  private static final String REQUEST_METHOD = "POST";
  private static final String USER_NAME = "j_username";
  private static final String PASSWORD = "j_password";
  private final Logger log = LoggerFactory.getLogger(this.getClass());
  @Reference
  HttpServletRequest request;

  public boolean authenticationSucceeded(HttpServletRequest request, HttpServletResponse response,
      AuthenticationInfo authInfo) {
    log.debug("*** authenticationSucceeded ***");

    return super.authenticationSucceeded(request, response, authInfo);
  }


  public void dropCredentials(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    log.debug("*** dropCredentials ***");
  }

  public AuthenticationInfo extractCredentials(HttpServletRequest request,
      HttpServletResponse response) {

    log.debug("***extractCredentials ***");

    if (!AuthUtil.isValidateRequest(request)) {
      AuthUtil.setLoginResourceAttribute(request, request.getContextPath());
    }

    SimpleCredentials creds = new SimpleCredentials(request.getParameter(USER_NAME),
        request.getParameter(PASSWORD).toCharArray());
    //ATTR_HOST_NAME_FROM_REQUEST can be any thing this is just an example
    //creds.setAttribute(ATTR_HOST_NAME_FROM_REQUEST, request.getServerName());
    AuthenticationInfo authInfo = new AuthenticationInfo(CustomAuthenticationHandler.AUTH_TYPE,
        request.getParameter(USER_NAME), request.getParameter(PASSWORD).toCharArray());

    return authInfo;
  }

//    AuthenticationInfo authInfo = new AuthenticationInfo(CustomAuthenticationHandler.AUTH_TYPE, "userId", "pwd".toCharArray());
//    return authInfo;


  public boolean requestCredentials(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    log.debug("*** requestCredentials ***");
    response.sendRedirect("/");
    return true;
  }

  public void authenticationFailed(HttpServletRequest request,
      HttpServletResponse response, AuthenticationInfo authInfo) {
    log.debug("*** authenticationFailed ***");
    super.authenticationFailed(request, response, authInfo);
  }

  public String getUserId(Credentials credentials) {
    return request.getParameter(USER_NAME);
  }


}
