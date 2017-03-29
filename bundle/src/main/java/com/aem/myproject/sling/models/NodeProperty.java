package com.aem.myproject.sling.models;

import javax.inject.Inject;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.xml.soap.Node;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

@Model(adaptables = Resource.class)

public class NodeProperty {

  @SlingObject
  ResourceResolver resourceResolver;

  Resource resource;

  Session session;

  javax.jcr.Node node;

  @Inject @Optional
  String propertyName;

   @Inject @Optional
   String propertyValue;

  private String path = "/content/nodeProperty";

  public void modifyNodeProperty()

  {
       try {
       resource = resourceResolver.getResource(path);
        session=resourceResolver.adaptTo(Session.class);
         node=resource.adaptTo(javax.jcr.Node.class);
         //javax.jcr.Node node=session.getNode(path);
       node.setProperty(propertyName,propertyValue);
       session.save();
       }

       catch(Exception e){
         System.out.println("Exception"+ e.getCause().toString());
       }

  }


  public void deleteNodeProperty()
  {
    try {
      resource = resourceResolver.getResource(path);
      session = resourceResolver.adaptTo(Session.class);
      node = resource.adaptTo(javax.jcr.Node.class);
      //node.setProperty(propertyName,(Value)null);
      node.getProperty(propertyName).remove();
      session.save();
    }
    catch(Exception e) {
      System.out.println("Exception" + e.getCause().toString());
    }
  }
}