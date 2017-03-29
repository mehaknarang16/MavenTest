package com.aem.myproject.impl;

import com.adobe.cq.account.api.AccountManagementService;
import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.oauth.jwt.JwsBuilder;
import com.adobe.granite.oauth.jwt.JwsBuilderFactory;
import com.adobe.granite.security.user.UserProperties;
import com.adobe.granite.security.user.UserPropertiesManager;
import com.adobe.granite.security.user.UserPropertiesService;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.mailer.MailService;
import com.day.cq.mailer.MailingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrPropertyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component(label = "Management Service" ,metatype = true)
@Service(AccountService.class)
@Property(name="service.description", value={"Account Manager for non-logged in users"})

public class AccountService {
  private final Logger log = LoggerFactory.getLogger(AccountService.class);
  protected static final String CREATE_ACCOUNT_OPERATION = "create-account";
  protected static final String CHANGE_PASSWORD_OPERATION = "change-password";
  protected static final String TOKEN_FIELD_OPERATION = "operation";
  protected static final String TOKEN_FIELD_USER_ID = "userId";
  protected static final String TOKEN_FIELD_HOST = "host";
  private static final String ACCOUNT_MANAGEMENT_SERVICE = "account-management-service";
  private static final String PN_CONFIRMATION_PAGE = "confirmationPage";
  private static final String PN_INTERMEDIATE_PATH = "intermediatePath";
  private static final String PN_MEMBER_OF = "memberOf";
  private static final String PROPERTY_ID = "userId";
  private static final String PF_REP = "rep:";
  private static final String PASSWORD = "password";
  private static final long DEFAULT_CLAIM_MAX_VALIDITY_PERIOD = 600L;
  @Property(longValue={600L}, label="Validity period of the manager token", description="Max validity period of the manager token (in seconds)")
  private static final String CLAIM_MAX_VALIDITY_PERIOD = "cq.accountmanager.token.validity.period";
  private static final String MAIL_CONFIG_PATH = "/etc/security/accountmgr/jcr:content";
  @Property(value={"requestnewaccount"}, label="Node name", description="Config node below /etc/security/accountmgr/jcr:content defining the mail template used when requesting a new account")
  private static final String CREATE_ACCOUNT_REQUEST_MAIL_NAME = "cq.accountmanager.config.requestnewaccount.mail";
  @Property(value={"requestnewpwd"}, label="Node name", description="Name of the node below /etc/security/accountmgr/jcr:content defining the mail template used when requesting a password change")
  private static final String CHANGE_PASSWORD_REQUEST_MAIL_NAME = "cq.accountmanager.config.requestnewpwd.mail";
  @Reference
  private SlingRepository repository;
  @Reference
  private ResourceResolverFactory resolverFactory;
  @Reference
  private UserPropertiesService userPropertiesService;
  @Reference(cardinality= ReferenceCardinality.MANDATORY_UNARY, policy= ReferencePolicy.DYNAMIC)
  private volatile MailService mailService;
  @Reference
  private JwsBuilderFactory jwsBuilderFactory;
  private String createAccountRequestMail;
  private String changePasswordRequestMail;
  private long tokenExpiry;

  @Activate
  protected void activate(Map<String, Object> props)
      throws RepositoryException
  {
    this.createAccountRequestMail = ("/etc/security/accountmgr/jcr:content/" + props.get("cq.accountmanager.config.requestnewaccount.mail"));
    this.changePasswordRequestMail = ("/etc/security/accountmgr/jcr:content/" + props.get("cq.accountmanager.config.requestnewpwd.mail"));
    this.tokenExpiry = PropertiesUtil.toLong(props.get("cq.accountmanager.token.validity.period"), 600L);
  }

  public boolean requestAccount(String userId, String pwd, Map<String, RequestParameter[]> properties, String requestUrl, String configurationPath)
      throws RepositoryException
  {
    this.log.debug("User {} would like to create an account", userId);
    Session serviceSession = getServiceSession();
    ResourceResolver resolver = getResourceResolver(serviceSession);
    boolean requestIsSuccessful = false;
    try
    {
      Resource configurationRes = resolver.getResource(configurationPath);
      ValueMap configurationProps = (ValueMap)configurationRes.adaptTo(ValueMap.class);
      String groupId = (String)configurationProps.get("memberOf", "");
      String intermediatePath = (String)configurationProps.get("intermediatePath", null);
//       String groupId ="administrators";
//       String intermediatePath="/home/users/shiv";

      User user = createAccount(resolver, userId, pwd, groupId, intermediatePath, properties);
      if (user != null)
      {
        user.disable("user creation not yet confirmed");

        serviceSession.save();
        this.log.info("An account has been created for user {}. The account is disabled until the user confirms its creation", userId);
        MailTemplate requestMail = getCreateAccountRequestMail(serviceSession);
        if (requestMail != null)
        {
          String emailAddress = getSubmittedEmail(userId, properties);

          String token = buildJwt(userId, emailAddress, requestUrl, "create-account");
          String confirmationPage = (String)configurationProps.get("confirmationPage", "");
//          String confirmationPage="/content/geometrixx/en/toolbar";
          URL confirmationUrl = getNextStepPageURL(requestUrl, confirmationPage);
          if (confirmationUrl != null)
          {
            StringBuilder action = new StringBuilder(confirmationUrl.toExternalForm());
            action.append("?ky=").append(token);
            Map<String, String> replacements = new HashMap();
            replacements.put("actionurl", action.toString());
            replacements.put("userId", userId);
            replacements.put("pwd",pwd);
            sendMail(requestMail, emailAddress, replacements);
            this.log.info("Instruction email sent to user {} to create an account", userId);
            requestIsSuccessful = true;
          }
          else
          {
            this.log.error("Request to create an account failed: the confirmation URL is not defined");
          }
        }
        else
        {
          this.log.error("Request to create an account failed: no mail template configured");
        }
      }
    }
    catch (MalformedURLException e)
    {
      this.log.error("Request to create an account failed: {}", e);
    }
    catch (EmailException e)
    {
      this.log.error("Request to create an account failed: {}", e);
    }
    catch (MailingException e)
    {
      this.log.error("Request to create an account failed: {}", e);
    }
    catch (CryptoException e)
    {
      this.log.error("Request to create an account failed: {}", e);
    }
    finally
    {
      closeSession(serviceSession);
      closeResourceResolver(resolver);
    }
    return requestIsSuccessful;
  }

  public boolean requestPasswordReset(String userId, String requestUrl, String configPath)
      throws RepositoryException
  {
    this.log.debug("User {} would like to reset his password", userId);

    Session serviceSession = getServiceSession();
    ResourceResolver resolver = getResourceResolver(serviceSession);
    UserManager userManager = getUserManager(serviceSession);
    UserPropertiesManager userPropertiesManager = getUserPropertiesManager(resolver);

    boolean requestIsSuccessful = false;
    User user = getUser(userManager, userId);
    try
    {
      if (user != null)
      {
        Resource configurationRes = resolver.getResource(configPath);
        ValueMap configurationProps = (ValueMap)configurationRes.adaptTo(ValueMap.class);

        MailTemplate mailTemplate = getChangePasswordRequestMail(serviceSession);
        if (mailTemplate != null)
        {
          String email = getUserEmail(userPropertiesManager, userId);

          String token = buildJwt(userId, email, requestUrl, "change-password");
          String confirmationPage = (String)configurationProps.get("confirmationPage", "");
          URL nextPageURL = getNextStepPageURL(requestUrl, confirmationPage);
          if (nextPageURL != null)
          {
            StringBuilder action = new StringBuilder(nextPageURL.toExternalForm());
            action.append("?ky=").append(token);
            Map<String, String> replacements = new HashMap();
            replacements.put("actionurl", action.toString());
            replacements.put("userId", userId);
            sendMail(userPropertiesManager, mailTemplate, user, replacements);
            requestIsSuccessful = true;
            this.log.info("Instruction email sent to user {} to reset his password", userId);
          }
          else
          {
            this.log.error("Could not send mail: the next step page URL is not defined");
          }
        }
      }
      else
      {
        this.log.error("Failed to request a password change: the user {} does not exist", userId);
      }
    }
    catch (MalformedURLException e)
    {
      this.log.error("Failed to retrieve the request URL: {}", e);
    }
    catch (EmailException e)
    {
      this.log.error("Failed to Send E-Mail: {}", e);
    }
    catch (CryptoException e)
    {
      this.log.error("Failed to create hmac token for password request confirmation: {}", e);
    }
    finally
    {
      closeSession(serviceSession);
      closeResourceResolver(resolver);
    }
    return requestIsSuccessful;
  }

  private User createAccount(ResourceResolver resolver, final String userId, String pwd, String groupId, String intermediatePath, Map<String, RequestParameter[]> properties)
      throws RepositoryException
  {
    UserManager userManager = (UserManager)resolver.adaptTo(UserManager.class);

    Authorizable authorizable = userManager.getAuthorizable(userId);
    if (authorizable != null)
    {
      this.log.error("Cannot create an account for user {}: an authorizable with the same ID already exists", userId);
      return null;
    }
    try
    {
      Principal principal = new Principal()
      {
        public String getName()
        {
          return userId;
        }
      };
      User user = userManager.createUser(userId, pwd, principal, intermediatePath);
      this.log.debug("User {} created in the transient space", userId);

      Group group = (Group)userManager.getAuthorizable(groupId, Group.class);
      if (group != null) {
        group.addMember(user);
      }
      setUserProperties(resolver, userId, properties);
      this.log.debug("Properties set in the transient space for user {}", userId);

      return user;
    }
    catch (Exception e)
    {
      this.log.error("Cannot create account for userId {}: {}", userId, e);
    }
    return null;
  }

  private void setUserProperties(ResourceResolver resolver, String userId, Map<String, RequestParameter[]> userProps)
      throws RepositoryException
  {
    if (userId.contains("@"))
    {
      UserPropertiesManager userPropertiesManager = (UserPropertiesManager)resolver.adaptTo(UserPropertiesManager.class);
      UserProperties profile = userPropertiesManager.getUserProperties(userId, "profile");
      if (profile == null) {
        profile = userPropertiesManager.createUserProperties(userId, "profile");
      }
      Node profileNode = profile.getNode();
      profileNode.setProperty("email", userId);
      this.log.debug("Set user-id {} initially as mail", userId);
    }
    setUserProperties(resolver, userId, userProps, false);
  }

  private boolean setUserProperties(ResourceResolver resolver, String userId, Map<String, RequestParameter[]> userProps, boolean autoSave)
      throws RepositoryException
  {
    if (userProps == null)
    {
      this.log.info("The user properties are not defined");
      return false;
    }
    UserManager userManager = (UserManager)resolver.adaptTo(UserManager.class);
    User user = (User)userManager.getAuthorizable(userId, User.class);
    UserPropertiesManager userPropertiesManager = (UserPropertiesManager)resolver.adaptTo(UserPropertiesManager.class);
    UserProperties userDirectProps = userPropertiesManager.getUserProperties(userId, "");
    if (userDirectProps == null) {
      userDirectProps = userPropertiesManager.createUserProperties(userId, "");
    }
    if ((user == null) || (userId.equals("anonymous")))
    {
      this.log.info("The user does not exist or is anonymous");
      return false;
    }
    UserProperties profile = userPropertiesManager.getUserProperties(userId, "profile");
    if (profile == null) {
      profile = userPropertiesManager.createUserProperties(userId, "profile");
    }
    UserProperties preferences = userPropertiesManager.getUserProperties(userId, "preferences");
    if (preferences == null) {
      preferences = userPropertiesManager.createUserProperties(userId, "preferences");
    }
    for (String name : userProps.keySet()) {
      if ((name.equals("userId")) || (name.startsWith("password")) || (name.equals("intermediatePath")))
      {
        this.log.debug("Skipped addition of {}, is key-property", name);
      }
      else
      {
        RequestParameter[] params = (RequestParameter[])userProps.get(name);
        if (name.startsWith("rep:"))
        {
          if ((params.length > 0) && (params[0].isFormField()))
          {
            Node userDirectPropsNode = userDirectProps.getNode();
            userDirectPropsNode.setProperty(name, params[0].getString());
            this.log.debug("Set {} as a user property", name);
          }
          else
          {
            this.log.debug("Skipped addition of {}, is not a String", name);
          }
        }
        else {
          try
          {
            Object value = null;
            if (params.length == 1)
            {
              RequestParameter param = params[0];
              if (param.getSize() == 0L) {
                continue;
              }
              value = param.isFormField() ? param.getString() : param.getInputStream();
            }
            else if (params.length > 0)
            {
              boolean isString = params[0].isFormField();
              Collection<Object> vals = new HashSet();
              for (RequestParameter param : params) {
                if (param.getSize() != 0L) {
                  if (isString) {
                    vals.add(param.getString());
                  } else {
                    vals.add(param.getInputStream());
                  }
                }
              }
              value = vals.toArray(new Object[vals.size()]);
            }
            if (name.startsWith("preferences"))
            {
              Node preferencesNode = preferences.getNode();
              JcrUtil.setProperty(preferencesNode, name.substring("preferences".length() + 1), value);
            }
            else
            {
              Node profileNode = profile.getNode();
              JcrUtil.setProperty(profileNode, name, value);
            }
          }
          catch (IOException e)
          {
            this.log.warn("Failed to access value for {}: {}", name, e.getMessage());
          }
          catch (IllegalArgumentException iae)
          {
            this.log.warn("Cannot set the property {}: {}", name, iae.getMessage());
          }
        }
      }
    }
    if (autoSave) {
      try
      {
        resolver.commit();
      }
      catch (PersistenceException e)
      {
        this.log.error("Could not persist the changes in the repository: {}", e.getMessage());
      }
    }
    return true;
  }

  private void sendMail(UserPropertiesManager userPropertiesManager, MailTemplate mailTemplate, User user, Map<String, String> variableMap)
      throws RepositoryException, EmailException, MailingException
  {
    String address = getUserEmail(userPropertiesManager, user.getID());
    if ((this.mailService != null) && (address != null))
    {
      SimpleEmail email = mailTemplate.createMail(new AccountVariableReplacer(userPropertiesManager, user.getID(), variableMap));

      email.addTo(address);
      this.mailService.send(email);
    }
    else
    {
      throw new EmailException("Failed to send email to " + address);
    }
  }

  private void sendMail(MailTemplate mailTemplate, String address, Map<String, String> variableMap)
      throws RepositoryException, EmailException, MailingException
  {
    if ((this.mailService != null) && (address != null))
    {
      SimpleEmail email = mailTemplate.createMail(new VariableReplacer(variableMap));
      email.addTo(address);
      this.mailService.send(email);
    }
    else
    {
      throw new EmailException("Failed to send email to " + address);
    }
  }

  private String getSubmittedEmail(String userId, Map<String, RequestParameter[]> properties)
      throws RepositoryException
  {
    String email = userId.contains("@") ? userId : null;
    if (email == null) {
      email = properties.containsKey("email") ? ((RequestParameter[])properties.get("email"))[0].getString() : null;
    }
    return email;
  }

  private String getUserEmail(UserPropertiesManager userPropertiesManager, String userId)
      throws RepositoryException
  {
    UserProperties profile = userPropertiesManager.getUserProperties(userId, "profile");
    return profile != null ? profile.getProperty("email") : null;
  }

  private MailTemplate getCreateAccountRequestMail(Session session)
  {
    return getMailTemplate(session, this.createAccountRequestMail);
  }

  private MailTemplate getChangePasswordRequestMail(Session session)
  {
    return getMailTemplate(session, this.changePasswordRequestMail);
  }

  private MailTemplate getMailTemplate(Session session, String path)
  {
    try
    {
      if (session.itemExists(path)) {
        return MailTemplate.read(new JcrPropertyMap((Node)session.getItem(path)));
      }
    }
    catch (RepositoryException e)
    {
      this.log.warn("Failed to read Mail configuration at {}: {}", path, e.getMessage());
    }
    return null;
  }

  private URL getNextStepPageURL(String requestUrl, String confirmationPage)
      throws MalformedURLException
  {
    if ((confirmationPage == null) || ("".equals(confirmationPage))) {
      return null;
    }
    if (!confirmationPage.endsWith(".html")) {
      confirmationPage = confirmationPage + ".html";
    }
    URL requestUrlObj = new URL(requestUrl);
    String protocol = requestUrlObj.getProtocol();
    String host = requestUrlObj.getHost();
    int port = requestUrlObj.getPort();
    return new URL(protocol, host, port, confirmationPage);
  }

  private User getUser(UserManager userManager, String userId)
      throws RepositoryException
  {
    return (User)userManager.getAuthorizable(userId, User.class);
  }

  private String buildJwt(String userId, String email, String requestUrl, String operation)
      throws CryptoException, MalformedURLException
  {
    JwsBuilder builder = this.jwsBuilderFactory.getInstance("HS256").setExpiresIn(this.tokenExpiry);
    URL requestUrlObj = new URL(requestUrl);
    String host = requestUrlObj.getHost();

    Map<String, String> claims = new LinkedHashMap();
    claims.put("userId", userId);
    claims.put("host", host);
    claims.put("operation", operation);
    for (Map.Entry<String, String> claim : claims.entrySet()) {
      builder.setCustomClaimsSetField((String)claim.getKey(), claim.getValue());
    }
    return builder.build();
  }

  private Session getServiceSession()
      throws RepositoryException
  {
    return this.repository.loginService("account-management-service",null);
  }

  private ResourceResolver getResourceResolver(Session session)
      throws RepositoryException
  {
    Map<String, Object> authInfo = new HashMap();
    authInfo.put("user.jcr.session", session);
    ResourceResolver resolver;
    try
    {
      resolver = this.resolverFactory.getResourceResolver(authInfo);
    }
    catch (LoginException e)
    {
      throw new RepositoryException("Cannot login to the repository with the service user: {}", e);
    }
    return resolver;
  }

  private UserManager getUserManager(Session session)
      throws RepositoryException
  {
    return ((JackrabbitSession)session).getUserManager();
  }

  private UserPropertiesManager getUserPropertiesManager(ResourceResolver resolver)
      throws RepositoryException
  {
    return this.userPropertiesService.createUserPropertiesManager(resolver);
  }

  private void closeSession(Session session)
  {
    if (session != null) {
      session.logout();
    }
  }

  private void closeResourceResolver(ResourceResolver resolver)
  {
    if ((resolver != null) && (resolver.isLive())) {
      resolver.close();
    }
  }

  protected void bindRepository(SlingRepository paramSlingRepository)
  {
    this.repository = paramSlingRepository;
  }

  protected void unbindRepository(SlingRepository paramSlingRepository)
  {
    if (this.repository == paramSlingRepository) {
      this.repository = null;
    }
  }

  protected void bindResolverFactory(ResourceResolverFactory paramResourceResolverFactory)
  {
    this.resolverFactory = paramResourceResolverFactory;
  }

  protected void unbindResolverFactory(ResourceResolverFactory paramResourceResolverFactory)
  {
    if (this.resolverFactory == paramResourceResolverFactory) {
      this.resolverFactory = null;
    }
  }

  protected void bindUserPropertiesService(UserPropertiesService paramUserPropertiesService)
  {
    this.userPropertiesService = paramUserPropertiesService;
  }

  protected void unbindUserPropertiesService(UserPropertiesService paramUserPropertiesService)
  {
    if (this.userPropertiesService == paramUserPropertiesService) {
      this.userPropertiesService = null;
    }
  }

  protected void bindMailService(MailService paramMailService)
  {
    this.mailService = paramMailService;
  }

  protected void unbindMailService(MailService paramMailService)
  {
    if (this.mailService == paramMailService) {
      this.mailService = null;
    }
  }

  protected void bindJwsBuilderFactory(JwsBuilderFactory paramJwsBuilderFactory)
  {
    this.jwsBuilderFactory = paramJwsBuilderFactory;
  }

  protected void unbindJwsBuilderFactory(JwsBuilderFactory paramJwsBuilderFactory)
  {
    if (this.jwsBuilderFactory == paramJwsBuilderFactory) {
      this.jwsBuilderFactory = null;
    }
  }
}

