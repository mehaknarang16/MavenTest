package com.aem.myproject.sling.models;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;


@Model(adaptables = Resource.class)
public class ConfigureUsers {

  public String getName() {

    return "shivani";
  }

}
