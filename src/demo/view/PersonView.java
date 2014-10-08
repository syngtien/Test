package demo.view;

import demo.model.Person;
import demo.model.PersonDAO;

public class PersonView {
  public static void main(String [] args) {
    PersonDAO perDAO = new PersonDAO();
    Person per1 = new Person();
    Person per2 = new Person();
    per1.setPersonid("P01");
    per1.setLastname("Luka");
    
    per2.setPersonid("P02");
    per2.setLastname("Modric");
    
    perDAO.insert(per1);
    perDAO.insert(per2);
    
    System.out.println("Insert: \n" +perDAO.select());
  }

}
