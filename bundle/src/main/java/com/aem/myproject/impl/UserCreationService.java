package com.aem.myproject.impl;


import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

@Component(label = "user creation service", immediate = true)
@Service(UserCreationService.class)
public class UserCreationService {

  @Reference
  AccountService accountManagementService;

  @Reference
  ResourceResolverFactory resourceResolverFactory;

  public void getUser() {
    ResourceResolver resourceResolver;

    try {

      Map<String, Object> param = new HashMap<String, Object>();
      param.put(ResourceResolverFactory.SUBSERVICE, "customservice");
      resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);

      UserManager usrMgr = resourceResolver.adaptTo(UserManager.class);
      Authorizable userObj = usrMgr.getAuthorizable("mehak8");

      Map<String, RequestParameter[]> profilemap = new HashMap<String, RequestParameter[]>();
      String email = "mehak@accunitysoft.com";
      profilemap.put("email", new RequestParameter[]{new Parameters(email)});
      String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
      String password = RandomStringUtils.random(10, characters);
      if (userObj == null) {
        accountManagementService
            .requestAccount("mehak8", password, profilemap, "http://localhost:4502",
                "/content/properties");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void createUsers(String username, String lastname, String email) {

    ResourceResolver resourceResolver;
    try {

      Map<String, Object> param = new HashMap<String, Object>();
      param.put(ResourceResolverFactory.SUBSERVICE, "customservice");
      resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);

      UserManager usrMgr = resourceResolver.adaptTo(UserManager.class);
      Authorizable userObj = usrMgr.getAuthorizable(username);

      Map<String, RequestParameter[]> profilemap = new HashMap<String, RequestParameter[]>();
      profilemap.put("email", new RequestParameter[]{new Parameters(email)});
      profilemap.put("lastname", new RequestParameter[]{new Parameters(lastname)});
      String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
      //String digits="0123456789";
      String password = RandomStringUtils.random(10, characters);
      //password += RandomStringUtils.random( 5, digits );

      if (userObj == null) {
        accountManagementService
            .requestAccount(username, password, profilemap, "http://localhost:4502",
                "/content/properties");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public Boolean reset(String username) {

    ResourceResolver resourceResolver;
    Boolean passwordReset = false;

    try {

      Map<String, Object> param = new HashMap<String, Object>();
      param.put(ResourceResolverFactory.SUBSERVICE, "customservice");
      resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);

      UserManager usrMgr = resourceResolver.adaptTo(UserManager.class);
      Authorizable userObj = usrMgr.getAuthorizable(username);

      if (userObj == null) {
        passwordReset = accountManagementService
            .requestPasswordReset(username, "http://localhost:4502",
                "/content/properties/reset");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return passwordReset;
  }
}

