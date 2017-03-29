package com.aem.myproject.sling.models;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageFilter;
import com.day.cq.wcm.foundation.Navigation;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;


@Model(adaptables = {Resource.class ,SlingHttpServletRequest.class})

public class Test {

  @SlingObject
  ResourceResolver resourceResolver;

  @Inject @Optional
  PrintWriter out;

  Resource resource;

  @Inject
  @Optional
  private String pathbrowser;

  private String pagePath = "/content/geometrixx/en";


  public String getName() {

    return "shivani";
  }

  public String getPath() {
    ValueMap valueMap = resource.adaptTo(ValueMap.class);
    String path = valueMap.get("pathbrowser", String.class);
    return path;
  }

  public String getPathbrowser() {
    return pathbrowser;
  }

  public List<Resource> getChildren() {

    List<Resource> list = new ArrayList<Resource>();
    getPath();
    //String pathbrowser=getPath();
    Resource res = resourceResolver.getResource(pathbrowser);
    if (res != null) {
      //Iterator<Resource> child = resourceResolver.listChildren(res);
      Iterator<Resource> child=resource.listChildren();
      while (child.hasNext()) {
        Resource r = child.next();
        list.add(r);
      }
    }
    return list;
  }

  public List<Page> getPageChild() {

    List<Page> list = new ArrayList<Page>();

    resource = resourceResolver.getResource(pagePath);
    if (resource.isResourceType("cq:Page")) {
      Page page = resource.adaptTo(Page.class);
      Iterator<Page> iterator = page.listChildren();
      while (iterator.hasNext()) {
        list.add(iterator.next());
      }

    }
    return list;
  }

   public  void  getNavigation()
   {
     int absParent = 2;
      resource = resourceResolver.getResource(pagePath);
      Page currentPage = resource.adaptTo(Page.class);

     Navigation nav = new Navigation(currentPage, absParent, new PageFilter(),3);

     for (Navigation.Element e: nav) {
       switch (e.getType()) {
         case NODE_OPEN:
         {   out.print("<ul>");
             break; }
         case ITEM_BEGIN:
         {
           if(e.hasChildren())
           {  out.print("<li>");
             out.print("<a href="+e.getPath()+".html>"+e.getRawTitle()+"</a>");
              //out.print("</li>");
           }
           else
           { out.print("<li>");
             out.print("<a href="+e.getPath()+".html>"+e.getRawTitle()+"</a>");
             //out.print("</li>");
           }

           break;
         }
         case ITEM_END:
         { out.print("</li>");
           break; }
         case NODE_CLOSE:
         { out.print("</ul>");
           break; }
       }
     }

   }

}


