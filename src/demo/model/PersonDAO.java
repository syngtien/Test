package demo.model;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import demo.controller.AcessDB;

public class PersonDAO {
  private Connection conn;
  private PreparedStatement pstm;
  private ResultSet rs;
  
  private final String SELECT = "SELECT * FROM PERSON";
  private final String INSERT = "INSERT INTO PERSON (PERSONID, LASTNAME) VALUES (?,?)";
  private final String UPDATE = "UPDATE PERSON SET LASTNAME=? WHERE PERSONID =?";
  private final String DELETE = "DELETE FROM PERSON WHERE PERSON ID =?";
  
  public List select() {
    List persons = new ArrayList();
    Person per = null;
    try {
      conn = AcessDB.connect();
      pstm = conn.prepareStatement(SELECT);
      rs = pstm.executeQuery();
      while (rs.next()) {
        per = new Person();
        per.setPersonid(rs.getString("personid"));
        per.setLastname(rs.getString("lastname"));
        persons.add(per);
      }
    } catch (Exception e ) {
        System.err.println("An error occurred because: "+e.getMessage());
        e.printStackTrace();
    }
    return persons;
  }
  public void insert (Person per) {
    try {
      conn = AcessDB.connect();
      pstm = conn.prepareStatement(INSERT);
      pstm.setString(1, per.getPersonid());
      pstm.setString(2, per.getLastname());
      pstm.executeUpdate();
    } catch (Exception e) { 
    	System.err.println("An error occurred because: "+e.getMessage());
        e.printStackTrace();
    }
    }
  public void update (Person per) {
    try {
      conn = AcessDB.connect();
      pstm = conn.prepareStatement(UPDATE);
      pstm.setString(1, per.getPersonid());
      pstm.setString(2, per.getLastname());
      pstm.executeUpdate();
    } catch (Exception e) { 
    	System.err.println("An error occurred because: "+e.getMessage());
        e.printStackTrace();
    }
  }
  public void delete (String personid) {
    try {
      conn = AcessDB.connect();
      pstm = conn.prepareStatement(DELETE);
      pstm.setString(1, personid);
    } catch (Exception e) { 
    	System.err.println("An error occurred because: "+e.getMessage());
        e.printStackTrace();
    } 
	  
  }
}

