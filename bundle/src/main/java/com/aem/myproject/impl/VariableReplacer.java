package com.aem.myproject.impl;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.RepositoryException;

public class VariableReplacer
{
  private final Map<String, String> variables;
  private static final char ESCAPE_CHAR = '\\';

  VariableReplacer(Map<String, String> variables)
  {
    this.variables = (variables == null ? new HashMap<String, String>() : variables);
  }

  public String replaceVariables(String in)
      throws RepositoryException
  {
    StringBuffer sb = new StringBuffer();
    replaceVariables(in, sb);
    return sb.toString();
  }

  public StringBuffer replaceVariables(String in, StringBuffer out)
      throws RepositoryException
  {
    StringBuffer var = new StringBuffer();
    int state = 0;
    for (int i = 0; i < in.length(); i++)
    {
      char c = in.charAt(i);
      switch (state)
      {
        case 0:
          if (c == '$')
          {
            state = 1;
          }
          else if (c == '#')
          {
            state = 3;
            var.setLength(0);
          }
          else if (c == '\\')
          {
            state = 4;
          }
          else
          {
            out.append(c);
          }
          break;
        case 1:
          if (c == '{')
          {
            state = 2;
            var.setLength(0);
          }
          else
          {
            out.append('$');
            out.append(c);
            state = 0;
          }
          break;
        case 2:
          if (c == '}')
          {
            if (var.length() == 0) {
              out.append("${}");
            } else {
              out.append(getValue(var.toString(), ""));
            }
            state = 0;
          }
          else
          {
            var.append(c);
          }
          break;
        case 3:
          if (c == '#')
          {
            if (var.length() == 0) {
              out.append("##");
            } else {
              out.append(getValue(var.toString(), ""));
            }
            state = 0;
          }
          else if (!Character.isJavaIdentifierPart(c))
          {
            out.append('#');
            out.append(var);
            out.append(c);
            state = 0;
          }
          else
          {
            var.append(c);
          }
          break;
        case 4:
          if (c == '\\') {
            out.append('\\');
          } else {
            out.append(c);
          }
          state = 0;
      }
    }
    if (state == 1)
    {
      out.append('$');
    }
    else if (state == 2)
    {
      out.append('$');
      out.append('{');
      out.append(var);
    }
    else if (state == 3)
    {
      out.append('#');
      out.append(var);
    }
    return out;
  }

  protected String getValue(String variable, String defaultValue)
      throws RepositoryException
  {
    String val = (String)this.variables.get(variable);
    return val == null ? defaultValue : val;
  }
}
