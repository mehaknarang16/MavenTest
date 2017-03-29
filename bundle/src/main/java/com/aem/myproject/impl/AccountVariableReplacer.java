package com.aem.myproject.impl;

import com.adobe.granite.security.user.UserProperties;
import com.adobe.granite.security.user.UserPropertiesManager;
import java.util.Map;
import javax.jcr.RepositoryException;

public class AccountVariableReplacer
    extends VariableReplacer
{
  private final UserPropertiesManager userPropertiesManager;
  private final String userID;

  AccountVariableReplacer(UserPropertiesManager userPropertiesManager, String userID, Map<String, String> variableMap)
  {
    super(variableMap);
    this.userPropertiesManager = userPropertiesManager;
    this.userID = userID;
  }

  protected String getValue(String variable, String defaultValue)
      throws RepositoryException
  {
    UserProperties userDirectProps = this.userPropertiesManager.getUserProperties(this.userID, "");
    String val;
    try
    {
      val = userDirectProps.getProperty(variable);
    }
    catch (IllegalArgumentException e)
    {
      return null;
    }
    if ((val == null) || ("".equals(val)))
    {
      UserProperties profile = this.userPropertiesManager.getUserProperties(this.userID, "profile");
      if (profile != null) {
        val = profile.getProperty(variable);
      }
    }
    if ((val == null) || ("".equals(val)))
    {
      UserProperties preferences = this.userPropertiesManager.getUserProperties(this.userID, "preferences");
      if (preferences != null) {
        val = preferences.getProperty(variable.replaceAll("\\.", "/"));
      }
    }
    if ((val == null) || ("".equals(val))) {
      val = super.getValue(variable, defaultValue);
    }
    return val;
  }
}
