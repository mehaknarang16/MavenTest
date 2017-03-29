package com.aem.myproject.impl;

import javax.jcr.RepositoryException;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.ValueMap;

class MailTemplate
{
  private final String from;
  private final String subject;
  private final String body;
  private static final String PN_FROM = "from";
  private static final String PN_BODY = "body";
  private static final String PN_SUBJECT = "subject";

  MailTemplate(String from, String subject, String body)
  {
    this.from = from;
    this.subject = subject;
    this.body = body;
  }

  protected SimpleEmail createMail(VariableReplacer replacer)
      throws EmailException, RepositoryException
  {
    SimpleEmail email = new SimpleEmail();
    email.setFrom(this.from);
    email.setSubject(replacer == null ? this.subject : replacer.replaceVariables(this.subject));
    email.setMsg(replacer == null ? this.body : replacer.replaceVariables(this.body));
    return email;
  }

  protected static MailTemplate read(ValueMap map)
  {
    if ((map.containsKey("from")) && (map.containsKey("body")))
    {
      String from = (String)map.get("from", "");
      String body = (String)map.get("body", "");
      if ((from.length() > 0) && (body.length() > 0)) {
        return new MailTemplate(from, (String)map.get("subject", ""), body);
      }
    }
    return null;
  }
}

