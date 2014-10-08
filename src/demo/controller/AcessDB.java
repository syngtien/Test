package demo.controller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class AcessDB {
//  static String url;
//  static String user;
//  static String password;
//  static Connection conn;
  public static Connection connect() {
	    String url;
	    String user;
	    String password;
	    Connection conn;
	    Properties p = new Properties();
	    try {
			p.load(new FileInputStream("database.properties"));
			url = p.getProperty("url");
			user = p.getProperty("user");
			password = p.getProperty("password");
			  Class.forName("com.mysql.jdbc.Driver");
			  conn = DriverManager.getConnection(url + "?user=" +user+ "&password=" +password);
		
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	   
	  }
	
    
  }
  
 
 


