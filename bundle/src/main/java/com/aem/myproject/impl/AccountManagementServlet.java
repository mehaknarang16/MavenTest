package com.aem.myproject.impl;

import com.adobe.granite.oauth.jwt.JwsValidator;
import com.adobe.granite.security.user.UserProperties;
import com.adobe.granite.security.user.UserPropertiesManager;
import com.adobe.granite.security.user.UserPropertiesService;
import com.day.cq.mailer.MailService;
import com.day.cq.mailer.MailingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.oltu.oauth2.jwt.JWT;
import org.apache.oltu.oauth2.jwt.io.JWTReader;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrPropertyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true, name = "test servlet", description = "Manages confirmation requests when managing accounts")
@Service
@Properties({@Property(name = "sling.servlet.resourceTypes", value = {
    "security/accountmgr/confirm1"}, propertyPrivate = true),
    @Property(name = "sling.servlet.methods", value = {"GET", "POST"}, propertyPrivate = true)})
public class AccountManagementServlet
    extends SlingAllMethodsServlet {

  private static final String REQ_ATTR_OPERATION_NAME = "cq.account.operation";
  private static final String REQ_ATTR_OPERATION_STATUS = "cq.account.operationStatus";
  private static final String ACCOUNT_MANAGEMENT_SERVICE = "account-management-service";
  private static final String MAIL_CONFIG_PATH = "/etc/security/accountmgr/jcr:content";
  @Property(value = {
      "informnewaccount"}, label = "Node name", description = "Config node below /etc/security/accountmgr/jcr:content defining the mail template used to inform the user about the new account")
  private static final String INFORM_NEW_ACCOUNT_MAIL_NAME = "cq.accountmanager.config.informnewaccount.mail";
  @Property(value = {
      "informnewpwd"}, label = "Node name", description = "Config node below /etc/security/accountmgr/jcr:content defining the mail template used to inform the user about the new password")
  private static final String INFORM_NEW_PWD_MAIL_NAME = "cq.accountmanager.config.informnewpwd.mail";
  private final Logger log = LoggerFactory.getLogger(AccountManagementServlet.class);
  @Reference
  JwsValidator jwsValidator;
  @Reference
  private SlingRepository repository;
  @Reference
  private ResourceResolverFactory resolverFactory;
  @Reference
  private UserPropertiesService userPropertiesService;
  @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.DYNAMIC)
  private volatile MailService mailService;
  private String informNewAccountMail;
  private String informNewPwdMail;

  @Activate
  protected void activate(Map<String, Object> props)
      throws RepositoryException {
    this.informNewAccountMail = ("/etc/security/accountmgr/jcr:content/" + props
        .get("cq.accountmanager.config.informnewaccount.mail"));
    this.informNewPwdMail = ("/etc/security/accountmgr/jcr:content/" + props
        .get("cq.accountmanager.config.informnewpwd.mail"));
  }

  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    manageUserRequest(request);
  }

  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    manageUserRequest(request);
  }

  private void manageUserRequest(SlingHttpServletRequest request) {
    this.log.debug("Received a request to perform an account task");
    Session serviceSession = null;
    ResourceResolver resolver = null;
    try {
      serviceSession = getServiceSession();
      resolver = getResourceResolver(serviceSession);
      UserManager userManager = getUserManager(serviceSession);
      UserPropertiesManager userPropertiesManager = getUserPropertiesManager(resolver);

      String token = request.getParameter("ky");
      String requestUrlHost = new URL(request.getRequestURL().toString()).getHost();
      if (isTokenValid(token, requestUrlHost)) {
        String operation = getTokenField(token, "operation");
        boolean operationSucceeds = false;
        String userId = getTokenField(token, "userId");
        this.log.debug("The token is valid for user '{}'", userId);
        if ("create-account".equals(operation)) {
          operationSucceeds = enableAccount(serviceSession, userManager, userPropertiesManager,
              userId);
        } else if ("change-password".equals(operation)) {
          String pwd = request.getParameter("passwordreset");
          String confirmedPwd = request.getParameter("passwordreset_confirm");
          operationSucceeds = setPassword(serviceSession, userManager, userPropertiesManager,
              userId, pwd, confirmedPwd);
        }
        request.setAttribute("cq.account.operation", operation);
        request.setAttribute("cq.account.operationStatus", Boolean.valueOf(operationSucceeds));
      } else {
        this.log.error("The provided token is not valid.");
      }
    } catch (RepositoryException e) {
      this.log.error("Error performing the account task: ", e);
    } catch (MalformedURLException e) {
      this.log.error("Error while getting the request host: ", e);
    } finally {
      closeSession(serviceSession);
      closeResourceResolver(resolver);
    }
  }

  private boolean enableAccount(Session session, UserManager userManager,
      UserPropertiesManager userPropertiesManager, String userId)
      throws RepositoryException {
    User user = getUser(userManager, userId);
    if (user == null) {
      this.log.error("User '{}' does not exist", userId);
      return false;
    }
    user.disable(null);

    session.save();
    this.log.info("Account enabled for user '{}'", userId);
    informUser(session, userPropertiesManager, user, "create-account");
    return true;
  }

  private boolean setPassword(Session session, UserManager userManager,
      UserPropertiesManager userPropertiesManager, String userId, String pwd, String confirmedPwd)
      throws RepositoryException {
    User user = getUser(userManager, userId);
    if (user == null) {
      this.log.error("The user '{}' does not exist", userId);
      return false;
    }
    if ((StringUtils.isEmpty(pwd)) || (StringUtils.isEmpty(confirmedPwd))) {
      this.log.error("The provided password or the confirmed password for user '{}' cannot be null",
          userId);
      return false;
    }
    if (!StringUtils.equals(pwd, confirmedPwd)) {
      this.log.error("The provided password and the confirmed password for user '{}' are different",
          userId);
      return false;
    }
    user.changePassword(pwd);

    session.save();
    this.log.info("The provided password has been set for user '{}'", userId);
    informUser(session, userPropertiesManager, user, "change-password");
    return true;
  }

  private void informUser(Session session, UserPropertiesManager userPropertiesManager, User user,
      String operation)
      throws RepositoryException {
    String userId = user.getID();
    MailTemplate mailTemplate =
        "create-account".equals(operation) ? getInformNewAccountMail(session)
            : getInformNewPwdMail(session);
    if (mailTemplate == null) {
      this.log.error("Cannot inform user: mail template is not defined");
      return;
    }
    try {
      Map<String, String> replacements = new HashMap();
      replacements.put("userId", userId);
      sendMail(userPropertiesManager, mailTemplate, user, replacements);
      this.log.info("Information email sent to user '{}' about {}", userId, operation);
    } catch (EmailException e) {
      this.log.error("Failed to inform user '{}': ", userId, e);
    } catch (MailingException e) {
      this.log.error("Failed to inform user '{}': ", userId, e);
    }
  }

  private String getTokenField(String token, String name) {
    JWT ojwt = (JWT) new JWTReader().read(token);
    if (ojwt != null) {
      return (String) ojwt.getClaimsSet().getCustomField(name, String.class);
    }
    return null;
  }

  private boolean isTokenValid(String token, String hostname) {
    if (!this.jwsValidator.validate(token)) {
      return false;
    }
    String hostField = getTokenField(token, "host");
    return (hostField != null) && (!"".equals(hostField)) && (hostname != null) && (!""
        .equals(hostname)) && (hostField.equals(hostname));
  }

  private User getUser(UserManager userManager, String userId)
      throws RepositoryException {
    return (User) userManager.getAuthorizable(userId, User.class);
  }

  private String getEmail(UserPropertiesManager userPropertiesManager, String userId)
      throws RepositoryException {
    UserProperties profile = userPropertiesManager.getUserProperties(userId, "profile");
    return profile != null ? profile.getProperty("email") : null;
  }

  private MailTemplate getInformNewAccountMail(Session session) {
    return getMailTemplate(session, this.informNewAccountMail);
  }

  private MailTemplate getInformNewPwdMail(Session session) {
    return getMailTemplate(session, this.informNewPwdMail);
  }

  private MailTemplate getMailTemplate(Session session, String path) {
    try {
      if (session.itemExists(path)) {
        return MailTemplate.read(new JcrPropertyMap((Node) session.getItem(path)));
      }
    } catch (RepositoryException e) {
      this.log.error("Failed to read Mail configuration at {}: {}", path, e);
    }
    return null;
  }

  private void sendMail(UserPropertiesManager userPropertiesManager, MailTemplate mailTemplate,
      User user, Map<String, String> variableMap)
      throws RepositoryException, EmailException, MailingException {
    String emailAddress = getEmail(userPropertiesManager, user.getID());
    if ((this.mailService != null) && (emailAddress != null)) {
      SimpleEmail email = mailTemplate.createMail(
          new AccountVariableReplacer(userPropertiesManager, user.getID(), variableMap));

      email.addTo(emailAddress);
      this.mailService.send(email);
    } else {
      throw new EmailException("Failed to send email: email address is not defined");
    }
  }

  private Session getServiceSession()
      throws RepositoryException {
    return this.repository.loginService("account-management-service", null);
  }

  private ResourceResolver getResourceResolver(Session session)
      throws RepositoryException {
    Map<String, Object> authInfo = new HashMap();
    authInfo.put("user.jcr.session", session);
    ResourceResolver resolver;
    try {
      resolver = this.resolverFactory.getResourceResolver(authInfo);
    } catch (LoginException e) {
      throw new RepositoryException("Cannot login to the repository with the service user: {}", e);
    }
    return resolver;
  }

  private UserManager getUserManager(Session session)
      throws RepositoryException {
    return ((JackrabbitSession) session).getUserManager();
  }

  private UserPropertiesManager getUserPropertiesManager(ResourceResolver resolver)
      throws RepositoryException {
    return this.userPropertiesService.createUserPropertiesManager(resolver);
  }

  private void closeSession(Session session) {
    if (session != null) {
      session.logout();
    }
  }

  private void closeResourceResolver(ResourceResolver resolver) {
    if ((resolver != null) && (resolver.isLive())) {
      resolver.close();
    }
  }

  protected void bindRepository(SlingRepository paramSlingRepository) {
    this.repository = paramSlingRepository;
  }

  protected void unbindRepository(SlingRepository paramSlingRepository) {
    if (this.repository == paramSlingRepository) {
      this.repository = null;
    }
  }

  protected void bindResolverFactory(ResourceResolverFactory paramResourceResolverFactory) {
    this.resolverFactory = paramResourceResolverFactory;
  }

  protected void unbindResolverFactory(ResourceResolverFactory paramResourceResolverFactory) {
    if (this.resolverFactory == paramResourceResolverFactory) {
      this.resolverFactory = null;
    }
  }

  protected void bindUserPropertiesService(UserPropertiesService paramUserPropertiesService) {
    this.userPropertiesService = paramUserPropertiesService;
  }

  protected void unbindUserPropertiesService(UserPropertiesService paramUserPropertiesService) {
    if (this.userPropertiesService == paramUserPropertiesService) {
      this.userPropertiesService = null;
    }
  }

  protected void bindMailService(MailService paramMailService) {
    this.mailService = paramMailService;
  }

  protected void unbindMailService(MailService paramMailService) {
    if (this.mailService == paramMailService) {
      this.mailService = null;
    }
  }

  protected void bindJwsValidator(JwsValidator paramJwsValidator) {
    this.jwsValidator = paramJwsValidator;
  }

  protected void unbindJwsValidator(JwsValidator paramJwsValidator) {
    if (this.jwsValidator == paramJwsValidator) {
      this.jwsValidator = null;
    }
  }
}
