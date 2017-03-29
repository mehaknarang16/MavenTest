package com.aem.myproject.servlet;


import com.aem.myproject.impl.UserCreationService;
import java.io.IOException;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;

@Service(ResetPassword.class)
@SlingServlet(paths = {"/bin/resetPassword"}, methods = "POST", metatype = true)

public class ResetPassword extends SlingAllMethodsServlet {

  @Reference
  UserCreationService userCreationService;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws IOException {
    Boolean status;

    try {
      String username = request.getParameter("name");

      JSONObject obj = new JSONObject();
      obj.put("username", username);

      status = userCreationService.reset(username);

      obj.put("status", status);

      if (status == true) {
        response.getWriter().print("Please check your email");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
