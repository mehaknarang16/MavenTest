package com.aem.myproject.sling.models;


import com.aem.myproject.impl.UserCreationService;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import javax.inject.Inject;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Source;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

@Model(adaptables = Resource.class)

public class Users {

  @Inject
  @Source("osgi-services")
  UserCreationService userCreationService;


  @SlingObject
  ResourceResolver resourceResolver;

  public String getName() {

    return "shivani";
  }

  public void getAccount() {

    userCreationService.getUser();
  }

  public void getFile() {

    FileInputStream XlsxFileToRead = null;
    XSSFWorkbook workbook = null;

    try {
      XlsxFileToRead = new FileInputStream("D:\\users.xlsx");

      workbook = new XSSFWorkbook(XlsxFileToRead);

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    XSSFSheet sheet = workbook.getSheetAt(0);
    XSSFRow row;

    Iterator rows = sheet.rowIterator();

    while (rows.hasNext()) {
      row = (XSSFRow) rows.next();
      String name = "";
      String lastname = "";
      String email = "";

      XSSFCell cell0 = (XSSFCell) row.getCell(0);
      XSSFCell cell1 = (XSSFCell) row.getCell(1);
      XSSFCell cell2 = (XSSFCell) row.getCell(2);

      name = cell0.getStringCellValue();
      lastname = cell1.getStringCellValue();
      email = cell2.getStringCellValue();

      userCreationService.createUsers(name, lastname, email);

      try {
        XlsxFileToRead.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
  }
}