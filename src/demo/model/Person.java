package demo.model;

public class Person {
  private String personid;
  private String lastname;
  public String getPersonid() {
	return personid;
  }
  public void setPersonid(String personid) {
	this.personid = personid;
  }
  public String getLastname() {
	return lastname;
  }
  public void setLastname(String lastname) {
	this.lastname = lastname;
  }
  public String toString() {
    return "personid" + personid + "lastname" +lastname+ "\n";
  }
  

}	
