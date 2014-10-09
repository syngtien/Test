/*******************************************************************************
 * Copyright 2002-2014 Business Brain Showa-ota Inc. All Rights Reserved.
 * �V�X�e����   : MBB
 * ���W���[���� : DBACCESS
 * ���W���[��ID : DbAccess
 *
 * �⑫:
 * �@�f�[�^�x�[�X�A�N�Z�X�c�[��(jp.co.bbs.unit.tools.servlets�z���N���X�œƗ�)
 * 
 ******************************************************************************/
package jp.co.bbs.unit.tools.servlets;

import java.io.*;
import java.sql.*;
import java.net.*;
import java.math.BigDecimal;
import java.security.Principal;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.*;
import javax.naming.*;

import java.util.*;
import java.util.zip.*;

import jp.co.bbs.unit.tools.servlets.debug.*;
import jp.co.bbs.unit.tools.servlets.util.DbAccessUtils;
import jp.co.bbs.unit.tools.servlets.util.DocumentManager;
import jp.co.bbs.unit.tools.servlets.util.ClassManager;
import jp.co.bbs.unit.tools.servlets.util.ExcelManager;
import jp.co.bbs.unit.tools.servlets.util.JavaExecutor;
import jp.co.bbs.unit.tools.servlets.util.LocalDataSource;
import jp.co.bbs.unit.tools.servlets.util.MBBClassLoader;
import jp.co.bbs.unit.tools.servlets.util.MD5Sum;
import jp.co.bbs.unit.tools.servlets.util.ObscureString;
import jp.co.bbs.unit.tools.servlets.util.SQLTokenizer;
import jp.co.bbs.unit.tools.servlets.util.TableManager;
import jp.co.bbs.unit.tools.servlets.util.UpdateResult;

/**
 * DB�A�N�Z�X
 * 
 *  service() ->
 *    �^�u�ɂ���āA�ȉ����\�b�h�ɐU�蕪����
 *     doTables()   �e�[�u���ꗗ (�f�t�H���g)
 *     doEdit()     �e�[�u���f�[�^�ҏW
 *     doCommand()  �R�}���h����
 *     doResult()   �R�}���h���s�i���ʕ\���j
 *     doMBB()      MBB���j���[
 *    �O���i�u���E�U�j����_�E�����[�h�Ƃ��ČĂяo�����
 *     doDownload() 
 *    
 *  config ... ���ݒ�
 *  sca n   ... ���ڑ��c�[��
 *   ��r���Ƃ��Ďw�肷��URL�́Adbaccess�͏ȗ��\�i���̏ꍇ��/�ŏI��邱�Ɓj
 *   �ڑ����W���[���iWEB-INF/update/new,del ��update�t�H���_�j��zip�ɂ܂Ƃ߂ăt�@�C���C���|�[�g
 *   �ɂ��ڑ��������\�i���ڈڑ��ł��Ȃ����p�j
 *   
 *  ���̑�
 *   UnitToolUser.cfg ���Q�Ƃ��āAmain()��肢�����̃o�b�`���������s���邱�Ƃ��\
 *   ��main()�Q��
 */
public class DbAccess extends HttpServlet {
  /** �o�[�W�����Ǘ��p */
  //public static final String _id ="$Id: DbAccess.java,v 1.15 2010/09/02 12:19:54 1110 Exp $";
  public static final String version = "1.210"; // ���W���[���}�C�i�[�ɕ����Đ����Ƃ��ăo�[�W�����̑召�𔻕ʂ���
  private static final String tabs[] = {"Tables", "Edit", "Command", "Result", "MBB"};

  private static final int T_CR   = 0;
  private static final int T_VCR  = 1;
  private static final int T_NUM  = 2;
  private static final int T_DT   = 3;
  private static final int T_TS   = 4;
  private static final int T_BLOB = 5;

  private static final int SCAN_LIST = 0;
  private static final int SCAN_COMMIT = 1;

  private static final int IMPORT_NORMAL = 0;
  private static final int IMPORT_DELETE = 1;
  private static final int IMPORT_CHECK = 2;
  
  private static final String DEFAULT_CHARSET = "UTF-8";

  private static String DATABASE_CHARSET = DEFAULT_CHARSET;
  
  private static boolean hasDataFieldInfo = false;
  
  public static final String DBACCESS_CONFIG = "DBACCESS_CONFIG";
  
  public static final String DBACCESS_IMPORTLOG = "DBACCESS_IMPORTLOG";

//  public static final String DBACCESS_UPDATELOG = "DBACCESS_UPDATELOG";

  public static final String DBACCESS_SERVICELOG = "SERVICELOG";

  // jp.co.bbs.unit.sys.MultipartHttpServlet��STORE_KEY�ƈ�v�����邱��
  public static final String DBACCESS_UPLOADFILES = "UPLOADFILES";

  // ���̃e�[�u���ɂ͐ݒ��񓙂�ۑ�����
  public static final String DBACCESS_CONFIG_CREATE =
    "CREATE TABLE " + DBACCESS_CONFIG + " ("
    + "PROPERTYID VARCHAR(100) NOT NULL,"
    + "VALUE VARCHAR(1000),"
    + "UPDATECOMPANYID VARCHAR(20),"
    + "UPDATEUSERID VARCHAR(20),"
    + "UPDATEPROCESSID VARCHAR(50),"
    + "TIMESTAMPVALUE VARCHAR(30),"
    + "PRIMARY KEY (PROPERTYID)"
    + ")";
  // ���̃e�[�u���ɂ͕ύX��񗚗𓙂�ۑ�����
  public static final String DBACCESS_IMPORTLOG_CREATE =
    "CREATE TABLE " + DBACCESS_IMPORTLOG + " ("
    + "REMOTEADDRESS VARCHAR(40) NOT NULL,"
    + "EXECTIMESTAMPVALUE VARCHAR(30) NOT NULL,"
    + "TABLEID VARCHAR(100) NOT NULL,"
    + "KEY1 VARCHAR(100) NOT NULL,"
    + "KEY2 VARCHAR(100) NOT NULL,"
    + "KEY3 VARCHAR(100) NOT NULL,"
    + "LOGINFO VARCHAR(1000),"
    + "UPDATECOMPANYID VARCHAR(20),"
    + "UPDATEUSERID VARCHAR(20),"
    + "UPDATEPROCESSID VARCHAR(50),"
    + "TIMESTAMPVALUE VARCHAR(30),"
    + "PRIMARY KEY (REMOTEADDRESS, EXECTIMESTAMPVALUE, TABLEID, KEY1, KEY2, KEY3)"
    + ")";
//  public static final String DBACCESS_UPDATELOG_CREATE =
//    "CREATE TABLE " + DBACCESS_UPDATELOG + " ("
//    + "MODULEID VARCHAR(1000) NOT NULL,"
//    + "UPDATETIMESTAMPVALUE VARCHAR(30) NOT NULL,"
//    + "UPDATEDESCRIPTION VARCHAR(1000),"
//    + "KEY1 VARCHAR(100),"
//    + "KEY2 VARCHAR(100),"
//    + "KEY3 VARCHAR(100),"
//    + "UPDATECOMPANYID VARCHAR(20),"
//    + "UPDATEUSERID VARCHAR(20),"
//    + "UPDATEPROCESSID VARCHAR(50),"
//    + "TIMESTAMPVALUE VARCHAR(30),"
//    + "PRIMARY KEY (MODULEID, UPDATETIMESTAMPVALUE)"
//    + ")";
  // jp.co.bbs.unit.sys.MultipartHttpServlet��STORE_KEY�̃e�[�u���ƈ�v�����邱��
  public static final String DBACCESS_UPLOADFILES_CREATE =
    "CREATE TABLE " + DBACCESS_UPLOADFILES + " ("
    + "FILEID VARCHAR(20) NOT NULL,"
    + "CLIENTNAME VARCHAR(255),"
    + "FILENAME VARCHAR(100),"
    + "CONTENTTYPE VARCHAR(100),"
    + "REQUESTID VARCHAR(100),"
    + "FILEDATA BLOB,"
    + "UPDATECOMPANYID VARCHAR(20),"
    + "UPDATEUSERID VARCHAR(20),"
    + "UPDATEPROCESSID VARCHAR(50),"
    + "TIMESTAMPVALUE VARCHAR(30),"
    + "PRIMARY KEY (FILEID)"
    + ")";
  
  // DBACCESS_CONFIG��L���ɂ��邩�ǂ����̃t���O�i�f�t�H���g�F�L���j
  private static boolean configEnabled = false;
  // DBACCESS_IMPORTLOG��L���ɂ��邩�ǂ����̃t���O�i�f�t�H���g�F�L���j
  private static boolean importLogEnabled = false;
  // SERVICELOG��L���ɂ��邩�ǂ����̃t���O�i�f�t�H���g�F�L���j
  private static boolean serviceLogEnabled = false;
  
  public static final String PACKAGE_BASE = "jp.co.bbs.unit.";

  private static final String CREATE_SQL_FILE = "00CREATE.sql";
  private static final String CREATE_INDEX_FILE = "00CREATEIDX.sql";
  
  private static final String ADMIN_MODE = "1";
  private static final String USER_MODE = "2";

  /** �Ǘ��҃��[�h�p�X���[�h(�f�t�H���g) */
  private static String DBACCESS_ADMINPASSWORD = "mbbadmin";
  /** ���[�U���[�h�p�X���[�h(�f�t�H���g) */
  private static String DBACCESS_USERPASSWORD = "mbbuser";

  private static Vector adminMenus = new Vector(); // �Ǘ��҃��j���[
  private static Vector userMenus = new Vector(); // ���[�U���j���[
  
//  private static String EOL = System.getProperty("line.separator");
  private static String EOL = "\r\n"; // OS�ˑ��ł͂Ȃ��Œ�ɂ���
  private static String SQL_CONCAT = "||"; // MSSQL�̏ꍇ��"+"��ݒ肷��
  
  private static Vector addTables = new Vector(); // �ǉ��\���e�[�u���i�X�L�[�}�J�^���O�ŕ\������Ȃ����̂��o���j
  private static Vector compareModules = new Vector(); // ��r�Ώۂ̃��W���[��
  
  private String[] dataSourceNames = null; // ��`���ꂽ�f�[�^�\�[�X��
  private String[] dataSourceDispNames = null; // ��`���ꂽ�f�[�^�\�[�X��
  private DataSource[] dataSources = null; // �f�[�^�\�[�X���L���b�V������ϐ�
  private String[] dbmsTypes = null;
  private String[] schemas = null;
  private String[] dbUsers = null;
  private Vector[] traceLogs = null; // SQL DEBUG�p���O
  
  private String title = null; // �^�C�g��
  private String bgColor = null; // �w�i�F
  private String classesPath = null; // �N���X�p�X
  private String bodyStyle = null; // BODY�̃X�^�C��

  
  private static Vector debugLog = new Vector(); // DEBUG�p���O

  // �R���e�L�X�g���[�g�̐�΃p�X
  private String contextRoot = null;
  private String appPath = null;
  private String updateWorkPath = null;
  private String excelTemplateFile = null;
  private String restartCommand = null;
  private boolean srcExists = false;
  
  // �X�e�[�W���OURL�i�R�s�[���j
  // �⑫�F ���؊��ł́A�J����or�J���e�X�g����DBACCESS��URL�A�{�Ԋ��ł�
  //      ���؊���URL���w�肷��BDBACCESS�̃p�X���[�h������ꍇ�́A?password=pass
  //      �܂Ŏw�肷��
  private String stagingURL = null;
  private String stagingPass = null;
  private String stagingProxy = null;
  
  private String deployReportFileName = null; // �f�v���C�˗��t�@�C���쐬�pEXCEL�e���v���[�g
  
  private boolean dataFieldIdCheck = true;
  private boolean packageUseClassCheck = true;
  
  private Hashtable tempFiles = new Hashtable(); // upload�ō쐬�����temp�t�@�C��
  private Hashtable tempOrigFiles = new Hashtable(); // upload���ꂽtemp�t�@�C���̃I���W�i���t�@�C����
//  private Hashtable importTables = new Hashtable(); // �C���|�[�g���ꂽ�e�[�u�������i�[�����
  
  private static final String ERROR_COLOR = "#ff0000";
  private static final String INFO_COLOR = "#0000ff";
  private static final String DIFF_COLOR = "#ffc0c0";
  private static final String TABLE_HEADER_COLOR = "#c0c0c0";
  private static final String DIFF_OLDER_COLOR = "#ff0000";
  private static final String DIFF_NEWER_COLOR = "#0000ff";
  private static final String DIFF_DELETED_COLOR = "#ff00ff";
  private static final String DIFF_SCHEDULED_COLOR = "#c0c0c0";
  
  private static final int OBJ_TYPE_PTABLE = 0; // �����e�[�u��
  private static final int OBJ_TYPE_PVIEW  = 1; // �����r���[
  private static final int OBJ_TYPE_MTABLE = 2; // MBB�V�X�e���e�[�u��
  private static final int OBJ_TYPE_SYNONYM  = 3; // �V�m�j��(Oracle�p)
  
  private static final String[] TABLE_TYPES = {
    "�����e�[�u��",
    "�����r���[",
    "MBB�V�X�e���e�[�u��",
    "�V�m�j��"
  };
  
  private static final String[][] MBB_MENU = {
    {"CONFIG", "�ݒ�"},
    {"SCAN", "���W���[���ڑ�"},
    {"FUNCTION", "�@�\�}�X�^"},
    {"TABLE", "�e�[�u���}�X�^"},
    {"FUNCTIONMASTER", "�@�\�}�X�^(����/�G�N�X�|�[�g)"},
    {"TABLEMASTER", "�e�[�u���}�X�^(����/�G�N�X�|�[�g)"},
    {"DATAFIELDMASTER", "�f�[�^�t�B�[���h�}�X�^(����/�G�N�X�|�[�g)"},
    {"PACKAGEMASTER", "�p�b�P�[�W�}�X�^(����/�G�N�X�|�[�g)"},
    {"CLASSTYPEMASTER", "�N���X�^�C�v�}�X�^(����/�G�N�X�|�[�g)"},
    {"PROCESSMASTER", "�v���Z�X�}�X�^(����/�G�N�X�|�[�g)"},
    {"PAGEMASTER", "�y�[�W�}�X�^(����/�G�N�X�|�[�g)"},
    {"APPLICATIONMASTER", "�A�v���P�[�V�����}�X�^(����/�G�N�X�|�[�g)"},
    {"MESSAGEMASTER", "���b�Z�[�W�}�X�^(����/�G�N�X�|�[�g)"},
    {"MENUMASTER", "���j���[�}�X�^(����/�G�N�X�|�[�g)"},
    {"MENUITEMMASTER", "���j���[�A�C�e���}�X�^(����/�G�N�X�|�[�g)"},
    {"IMPORT", "�t�@�C���C���|�[�g"},
    {"LOGOUT", "���O�A�E�g"}
  };
  private static final Hashtable mbbMenus = new Hashtable();
  
  private static String backup_path = null; // �f�[�^�o�b�N�A�b�v�p�X 
  
  static {
    // ���j���[������
    for (int i = 0; i < MBB_MENU.length; ++i) {
      mbbMenus.put(MBB_MENU[i][0], MBB_MENU[i][1]);
    }
  }
  
  /**
   * �T�[�u���b�g����������
   */
  public void init() throws ServletException {

    contextRoot = getServletConfig().getServletContext().getRealPath("/");
    appPath = getServletConfig().getServletContext().getRealPath("/");
    try {
      appPath = new File(appPath).getCanonicalPath();
    } catch(IOException ioe) {}
    log_debug("appPath=" + appPath);
    if (new File(appPath, "src").exists()) {
      srcExists = true;
    }
    // MBB�W���̃f�[�^�\�[�X�擾
    String dataSourceName = getInitParameter("DATA_SOURCE_NAME");
    String dataSourceDispName = getInitParameter("DATA_SOURCE_DISPNAME");
    String schema = getInitParameter("SCHEMA");
    addDataSource(dataSourceName, schema, dataSourceDispName);
    for (int i = 2; i <= 9; ++i) {
      String addDataSourceName = getInitParameter("DATA_SOURCE_NAME" + i);
      if (addDataSourceName != null) {
        String addSchema = getInitParameter("SCHEMA" + i);
        String addDataSourceDispName = getInitParameter("DATA_SOURCE_DISPNAME" + i);
        addDataSource(addDataSourceName, addSchema, addDataSourceDispName);
      }
    }
    title = getInitParameter("TITLE");
    bgColor = getInitParameter("BGCOLOR");
    bodyStyle = getInitParameter("BODYSTYLE");
    classesPath = getInitParameter("CLASSES_PATH");
    
    String addTables = getInitParameter("ADDTABLES");
    if (addTables != null) {
      for (StringTokenizer st = new StringTokenizer(addTables, ","); st.hasMoreTokens(); ) {
        this.addTables.add(st.nextToken());
      }
    }
    
    // �C���|�[�g�`�F�b�N�Ɋւ���J�X�^�}�C�Y
    String ignoreDataFieldIdCheck = getInitParameter("IGNORE_DATAFIELDID_CHECK");
    if (ignoreDataFieldIdCheck != null && (ignoreDataFieldIdCheck.equals("1") || ignoreDataFieldIdCheck.equals("true"))) {
      dataFieldIdCheck = false;
    }
    String ignorePackageUseClassCheck = getInitParameter("IGNORE_PACKAGEUSECLASS_CHECK");
    if (ignorePackageUseClassCheck != null && (ignorePackageUseClassCheck.equals("1") || ignorePackageUseClassCheck.equals("true"))) {
      packageUseClassCheck = false;
    }
    
    String pass = getInitParameter("PASSWORD");
    if (pass != null) {
      DBACCESS_ADMINPASSWORD = pass;
    }
    String userpass = getInitParameter("USERPASSWORD");
    if (userpass != null) {
      DBACCESS_USERPASSWORD = userpass;
    }

    // �Ǘ��҃��j���[�̐ݒ�
    String adminMenu = getInitParameter("ADMINMENU");
    if (adminMenu == null) {
      // �Ǘ��҃��j���[�̃f�t�H���g�i�]���̃��j���[�Ɠ����\���j
      adminMenus.add("FUNCTION");
      adminMenus.add("TABLE");
      adminMenus.add("FUNCTIONMASTER");
      adminMenus.add("TABLEMASTER");
      adminMenus.add("DATAFIELDMASTER");
      adminMenus.add("PACKAGEMASTER");
      adminMenus.add("CLASSTYPEMASTER");
      adminMenus.add("PROCESSMASTER");
      adminMenus.add("PAGEMASTER");
      adminMenus.add("APPLICATIONMASTER");
      adminMenus.add("MESSAGEMASTER");
      adminMenus.add("MENUMASTER");
      adminMenus.add("MENUITEMMASTER");
      adminMenus.add("IMPORT");
      adminMenus.add("LOGOUT");
    } else {
      StringTokenizer st = new StringTokenizer(adminMenu, ",");
      while (st.hasMoreTokens()) {
        adminMenus.add(st.nextToken().trim());
      }
    }
    
    // ���[�U���j���[�̐ݒ�
    String userMenu = getInitParameter("USERMENU");
    if (userMenu == null) {
      // �f�t�H���g��IMPORT�̂�
      userMenus.add("IMPORT");
      userMenus.add("LOGOUT");
    } else {
      StringTokenizer st = new StringTokenizer(userMenu, ",");
      while (st.hasMoreTokens()) {
        userMenus.add(st.nextToken().trim());
      }
    }
    
    // �o�b�N�A�b�v�p�X
    backup_path = getInitParameter("BACKUP_PATH");
    if (backup_path != null) {
      File path = new File(backup_path);
      if (!path.exists()) {
        path.mkdirs();
      }
      if (!path.exists()) {
        backup_path = null;
      }
    }
    log_debug("backup_path=" + backup_path);
    
    // �X�e�[�W���O����URL���擾
    stagingURL = getInitParameter("STAGING_URL");
    log_debug("stagingURL=" + stagingURL);
    if (stagingURL != null) {
      if (stagingURL.endsWith("/")) {
        // "/"�ŏI���ꍇ��dbaccess��⊮����
        stagingURL = stagingURL + "dbaccess";
      }
    }
    
    // stagingProxy�̐ݒ�
    String stagingProxy = getInitParameter("STAGING_PROXY");
    if (stagingProxy != null && stagingProxy.trim().length() > 0) {
      this.stagingProxy = stagingProxy;
      log_debug("stagingProxy=" + stagingProxy);
    }
    // update�p�X�̐ݒ�
    String updateWorkPath = getInitParameter("UPDATE_WORK_PATH");
    if (updateWorkPath != null && new File(updateWorkPath).exists()) {
      this.updateWorkPath = updateWorkPath;
      log_debug("updateWorkPath=" + updateWorkPath);
    }
    // excelTemplate�t�@�C���̐ݒ�
    String excelTemplateFile = getInitParameter("EXCEL_TEMPLATE_FILE");
    if (excelTemplateFile != null && new File(excelTemplateFile).exists()) {
      this.excelTemplateFile = excelTemplateFile;
      log_debug("excelTemplateFile=" + excelTemplateFile);
    }
    
    // DBACCESS_CONFIG���g�p���Ȃ��ꍇ�Ɏw�肷��
    String ignore_config = getInitParameter("IGNORE_CONFIG");
    
    if (!isTrue(ignore_config)) {
      // �����̎w�肪�Ȃ���ΗL���ɂ���
      configEnabled = true;
      log_debug("configEnabled=true");
    } else {
      log_debug("ignore_config=true");
    }
    
    // DBACCESS_IMPORTLOG���o�͂��Ȃ��ꍇ�Ɏw�肷��
    String ignore_importLog = getInitParameter("IGNORE_IMPORTLOG");
    
    String add_moduleitems = getInitParameter("ADD_MODULE_ITEMS");
    if (add_moduleitems != null && add_moduleitems.trim().length() > 0) {
      String[] addItems = add_moduleitems.split(",");
      String[] newItems = new String[DEFAULT_MOD_ITEMS.length + addItems.length];
      System.arraycopy(DEFAULT_MOD_ITEMS, 0, newItems, 0, DEFAULT_MOD_ITEMS.length);
      System.arraycopy(addItems, 0, newItems, DEFAULT_MOD_ITEMS.length, addItems.length);
      DEFAULT_MOD_ITEMS = newItems;
      log_debug("ADD_MODULE_ITEMS=" + add_moduleitems);
    }
    
    Connection conn = null;
    try {
      conn = dataSources[0].getConnection();
      conn.setAutoCommit(false);
      //conn.close(); ���\�b�h�̍Ō��close����
    } catch (SQLException e) {
      e.printStackTrace();
      throw new ServletException(e);
    }
    if (conn != null) {
      // DB�Ɋ֘A���鏉���������͂����ł����Ȃ�
      if (isMSSql(0)) {
        SQL_CONCAT = "+";
      }
      if (isDerby(0)) {
        // TODO: ����DBMS�̏ꍇ�ɂ��Ă��������K�v����
        TableManager.DBTYPE = "DERBY";
      }
      // schema�̕␳
      for (int i = 0; i < schemas.length; ++i) {
        if (isMSSql(i)) {
          schemas[i] = "dbo"; // TODO: �ݒ�ɂ���Ă�dbo�ȊO������̂ł́H
        } else if (isPgSql(i)) {
          schemas[i] = "public"; // TODO: �ݒ�ɂ���Ă�public�ȊO������̂ł́H
        } else if (isMySql(i)) {
          // MySQL�̏ꍇ�̓��[�U���ƃX�L�[�}�����قȂ�
          String url = null;
          try {
            url = conn.getMetaData().getURL();
          } catch (SQLException e) {}
          if (url != null && url.lastIndexOf("/") != -1) {
            schemas[i] = url.substring(url.lastIndexOf("/") + 1);
          } else {
            schemas[i] = null;
          }
        } else if (isDerby(i)) {
          schemas[i] = schemas[i].toUpperCase();
        }
      }
      if (DbAccessUtils.isTableExists(conn, schemas[0], DBACCESS_SERVICELOG)) {
        serviceLogEnabled = true;
        log_debug("serviceLogEnabled=true");
      }
      
      
      if (!isTrue(ignore_importLog)) {
        if (DbAccessUtils.isTableExists(conn, schemas[0], DBACCESS_IMPORTLOG)) {
          importLogEnabled = true;
          log_debug("importLogEnabled=true");
        } else {
          log_debug("DBACCESS_IMPORTLOG not found.");
          // �ŏ��̓e�[�u���������\�������邽��CREATE��������݂�
          try {
            if (DbAccessUtils.executeSQL(conn, DBACCESS_IMPORTLOG_CREATE)) {
              importLogEnabled = true;
              log_debug("importLogEnabled=true");
            } else {
              log_debug("DBACCESS_IMPORTLOG cannot create.");
            }
          } catch (SQLException e) {
            log_debug(e);
            log_debug("DBACCESS_IMPORTLOG cannot create.");
          }
        }
      } else {
        log_debug("ignore_importLog=true");
      }
      
      if (!isTrue(ignore_config)) {
        if (!DbAccessUtils.isTableExists(conn, schemas[0], DBACCESS_CONFIG)) {
          // �ŏ��̓e�[�u���������\�������邽��CREATE�����݂�
          try {
            if (!DbAccessUtils.executeSQL(conn, DBACCESS_CONFIG_CREATE)) {
              configEnabled = false;
              log_debug("configEnabled=false");
            }
          } catch (SQLException e) {
            configEnabled = false;
            log_debug(e);
            log_debug("DBACCESS_CONFIG cannot create.");
            log_debug("configEnabled=false");
          }
        } else {
          loadConfig(conn);
        }
      } else {
        log_debug("ignore_config=true");
      }
      // DATAFIELDINFO�����݂��邩�`�F�b�N
      if (DbAccessUtils.isTableExists(conn, schemas[0], "DATAFIELDINFO")) {
        hasDataFieldInfo = true;
      }
      
      try {
        conn.commit();
      } catch (SQLException e) {}
      try {
        conn.close();
      } catch (SQLException e) {}
    }
    
  }
  
  /**
   * DBACCESS_CONFIG��ǂݍ��݁A���s���ϐ��ɃZ�b�g����
   * @param conn
   */
  private void loadConfig(Connection conn) {
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT PROPERTYID, VALUE FROM " + DBACCESS_CONFIG + " ORDER BY PROPERTYID");
      rs = stmt.executeQuery();
      while (rs.next()) {
        String propertyId = rs.getString(1);
        String value = rs.getString(2);
        if (value == null || value.trim().length() == 0) {
          value = "";
        }
        if ("password".equals(propertyId)) {
          DBACCESS_ADMINPASSWORD = value;
        } else if ("userpassword".equals(propertyId)) {
          if (value.length() > 0) {
            // �u�����N�̏ꍇ�̓f�t�H���g�p�X���[�h�̂܂܂ɂ���
            DBACCESS_USERPASSWORD = value;
          }
        } else if ("title".equals(propertyId)) {
          title = value;
        } else if ("bgcolor".equals(propertyId)) {
          bgColor = value;
        } else if ("bodystyle".equals(propertyId)) {
          bodyStyle = value;
        } else if ("applicationpath".equals(propertyId)) {
          appPath = value;
          log_debug(DBACCESS_CONFIG + ":appPath=" + appPath);
        } else if ("stagingurl".equals(propertyId)) {
          stagingURL = value;
          log_debug(DBACCESS_CONFIG + ":stagingURL=" + stagingURL);
        } else if ("stagingpass".equals(propertyId)) {
          stagingPass = value;
          log_debug(DBACCESS_CONFIG + ":stagingPass=" + stagingPass);
        } else if ("stagingproxy".equals(propertyId)) {
          stagingProxy = value;
          log_debug(DBACCESS_CONFIG + ":stagingProxy=" + stagingProxy);
        } else if ("updateworkpath".equals(propertyId)) {
          updateWorkPath = value;
          log_debug(DBACCESS_CONFIG + ":updateWorkPath=" + updateWorkPath);
        } else if ("templatefile".equals(propertyId)) {
          excelTemplateFile = value;
          log_debug(DBACCESS_CONFIG + ":excelTemplateFile=" + excelTemplateFile);
        } else if ("restartcommand".equals(propertyId)) {
          restartCommand = value;
          log_debug(DBACCESS_CONFIG + ":restartCommand=" + restartCommand);
        } else if ("adminmenu".equals(propertyId)) {
          adminMenus.clear();
          StringTokenizer st = new StringTokenizer(value, ",");
          while (st.hasMoreTokens()) {
            adminMenus.add(st.nextToken().trim());
          }
        } else if ("usermenu".equals(propertyId)) {
          userMenus.clear();
          StringTokenizer st = new StringTokenizer(value, ",");
          while (st.hasMoreTokens()) {
            userMenus.add(st.nextToken().trim());
          }
        } else if (propertyId.startsWith("module")) {
          if (!compareModules.contains(value)) {
            compareModules.add(value);
          }
        }
      }
    } catch (SQLException e) {
      
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    if (compareModules.size() == 0) {
      // �f�t�H���g�̔�r�Ώ�
      for (int i = 0; i < DEFAULT_MOD_ITEMS.length; ++i) {
        if (isSupportedModuleType(DEFAULT_MOD_ITEMS[i])) {
          compareModules.add(DEFAULT_MOD_ITEMS[i]);
        }
      }
    }
  }

  private static final String DEFAULT_IGNORE_PATH = "backup/,exports/,logs/,META-INF/,process/,src/,sysnode/,temp/,tmp/,tempclasses/,tool/,update/";
  private Vector ignoreModules = new Vector();
  
  private void loadIgnoreModules() {
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      loadIgnoreModules(conn);
    } catch (SQLException e) {
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
    }
  }
  private void loadIgnoreModules(Connection conn) {
    ignoreModules.clear();
    PreparedStatement stmt = null;
    ResultSet rs = null;
    int cnt = 0;
    try {
      stmt = conn.prepareStatement("SELECT PROPERTYID, VALUE FROM " + DBACCESS_CONFIG + " WHERE PROPERTYID LIKE 'ignorepath%' ORDER BY PROPERTYID");
      rs = stmt.executeQuery();
      TreeMap tmap = new TreeMap();
      while (rs.next()) {
        String propertyid = rs.getString(1);
        String value = rs.getString(2);
        if (value != null && value.trim().length() > 0) {
          try {
            int n = Integer.parseInt(propertyid.substring(10));
            tmap.put(new Integer(n), value);
          } catch (Exception e) {
            log_debug(e);
          }
        }
        cnt++;
      }
      if (cnt == 0) { // ������Ԃ̓f�t�H���g���Z�b�g
        saveConfigItem(conn, "ignorepath", DEFAULT_IGNORE_PATH);
        ignoreModules.add(DEFAULT_IGNORE_PATH);
      } else {
        ignoreModules.addAll(tmap.values());
      }
    } catch (SQLException e) {
      
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
  }
  private void clearConfigItems(Connection conn, String itemkey) {
    PreparedStatement stmt = null;
    try {
      stmt = conn.prepareStatement("DELETE FROM " + DBACCESS_CONFIG + " WHERE PROPERTYID LIKE '" + itemkey + "%'");
      stmt.executeUpdate();
    } catch (SQLException e) {
      log_debug(e);
    } finally {
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
  }
  private void saveConfigItem(Connection conn, String key, String path) {
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT COUNT(PROPERTYID) FROM " + DBACCESS_CONFIG + " WHERE PROPERTYID LIKE '" + key + "%'");
      rs = stmt.executeQuery();
      int cnt = 0;
      if (rs.next()) {
        cnt = rs.getInt(1) + 1;
      }
      rs.close();
      rs = null;
      stmt.close();
      stmt = null;
      stmt = conn.prepareStatement("INSERT INTO " + DBACCESS_CONFIG
          + " (PROPERTYID,VALUE,UPDATECOMPANYID,UPDATEUSERID,UPDATEPROCESSID,TIMESTAMPVALUE) VALUES (?,?,?,?,?,?)"
          );
      stmt.setString(1, key + cnt);
      stmt.setString(2, path);
      stmt.setString(3, "MBB");
      stmt.setString(4, "ADMIN");
      stmt.setString(5, "DBACCESS");
      stmt.setString(6, new Timestamp(System.currentTimeMillis()).toString());
      int i = stmt.executeUpdate();
    } catch (SQLException e) {
      
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
  }
  private boolean isIgnorePath(String path, String excludePath) {
    return isIgnorePath(ignoreModules, path, excludePath);
  }
  private static boolean isIgnorePath(Vector ignoreModules, String path, String excludePath) {
    for (Iterator ite = ignoreModules.iterator(); ite.hasNext(); ) {
      String[] ignorePaths = ((String)ite.next()).split(",");
      for (int i = 0; i < ignorePaths.length; ++i) {
        String ignorePath = ignorePaths[i];
        if (ignorePath.indexOf("%") != -1) {
          ignorePath = ignorePath.replaceAll("%2c", ",");
          ignorePath = ignorePath.replaceAll("%25", "%"); // �Ō�ɓW�J
        }
        if (ignorePath.indexOf("*") != -1) {
          // "*"���܂܂��ꍇ�̓��C���h�J�[�h�Ƃ��Ĉ���
          if (path.matches(ignorePath.replaceAll("\\*", ".*"))) {
            return true;
          }
          continue;
        }
        if (ignorePath.endsWith("/") && !isExcludePath(ignorePath, excludePath)) {
          // "/"�ŏI���ꍇ�́A���̃t�H���_�S�Ė���
          if (path.startsWith(ignorePath)
              || path.equals(ignorePath.substring(0, ignorePath.length() - 1))) {
            return true;
          }
          continue;
        }
        if (path.equals(ignorePath)) {
          // ��L�ȊO�͊��S��v
          return true;
        }
      }
    }
    return false;
  }
  private static boolean isExcludePath(String path, String excludePath) {
    if (path == null || excludePath == null) {
      return false;
    }
    String[] paths = excludePath.split(",");
    for (int i = 0; i < paths.length; ++i) {
      if (path.equals(paths[i])) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * �f�[�^�\�[�X�̒ǉ��i���\�[�X���擾�j
   * @param dataSourceName
   * @param schema
   * @param dataSourceDispName
   * @throws ServletException
   */
  private void addDataSource(String dataSourceName, String schema, String dataSourceDispName) 
    throws ServletException {
    Hashtable params = new Hashtable();
    //��WebSphere�p?(�ʏ��INITIAL_CONTEXT_FACTORY�͎w�肵�Ȃ��ĉ�)
    String initCtxFactory = getInitParameter("INITIAL_CONTEXT_FACTORY");
    if (initCtxFactory != null) {
      // WebSphere�̏ꍇ�A"com.ibm.websphere.naming.WsnInitialContextFactory"��ݒ�
      params.put(Context.INITIAL_CONTEXT_FACTORY, initCtxFactory);
    }
    //dataSourceName = "java:comp/env/jdbc/MBBTESTSource";
    if (dataSourceName == null) {
      dataSourceName = "java:comp/env/jdbc/mbbds"; // �f�t�H���g�f�[�^�\�[�X��
    } else if (!dataSourceName.startsWith("java:comp/env/")) {
      dataSourceName = "java:comp/env/" + dataSourceName;
    }
    DataSource dataSource = null;
    InitialContext initCtx = null;
    try {
      initCtx = new InitialContext(params);
      dataSource = (DataSource)initCtx.lookup(dataSourceName);
    } catch(Exception e) {
      log_debug(e);
    }
    if (dataSource == null && initCtx != null) {
      // �Č���
      try {
        dataSource = (DataSource)initCtx.lookup(dataSourceName.substring(14));// java:comp/env/�ȍ~�ōČ���
      } catch(Exception e) {
        log_debug(e);
      }
    }
    addDataSource(dataSourceName, dataSource, schema, dataSourceDispName);
  }
  
  /**
   * �f�[�^�\�[�X�̒ǉ�
   * @param dataSourceName
   * @param dataSource
   * @param schema
   * @param dataSourceDispName
   * @throws ServletException
   */
  private void addDataSource(String dataSourceName, DataSource dataSource, String schema, String dataSourceDispName)
    throws ServletException {
    String dbmsType = null;
    String dbUser = null;
    if (dataSource != null) {
      // DBMS�^�C�v�̔���
      Connection conn = null;
      try {
        conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        dbmsType = DbAccessUtils.getDBMSType(conn);
        dbUser = conn.getMetaData().getUserName();
        if (schema == null) {
          // �������p�����[�^�ŃX�L�[�}�����w��i�f�t�H���g�j
          schema = dbUser;
        } else if (schema.trim().length() == 0) {
          // �������p�����[�^�ŃX�L�[�}�����w�肪����ꍇ�Ńu�����N�̏ꍇ��null���Z�b�g����
          schema = null;
        }
      } catch (SQLException e) {
        log_debug("dataSourceName=" + dataSourceName);
        log_debug(e);
        e.printStackTrace();
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {
          }
        }
      }
    } else {
      log_debug("�f�[�^�\�[�X[" + dataSourceName + "]�ɃA�N�Z�X�ł��܂���ł���.");
    }
      
    if (dbmsType != null) {
      if (dataSourceNames == null) {
        dataSourceNames = new String[1];
        dataSourceDispNames = new String[1];
        dataSources = new DataSource[1];
        schemas = new String[1];
        dbmsTypes = new String[1];
        dbUsers = new String[1];
        traceLogs = new TraceLogManager[1];
      } else {
        //
        String[] newDataSourceNames = new String[dataSourceNames.length + 1];
        System.arraycopy(dataSourceNames, 0, newDataSourceNames, 0, dataSourceNames.length);
        dataSourceNames = newDataSourceNames;
        //
        String[] newDataSourceDispNames = new String[dataSourceDispNames.length + 1];
        System.arraycopy(dataSourceDispNames, 0, newDataSourceDispNames, 0, dataSourceDispNames.length);
        dataSourceDispNames = newDataSourceDispNames;
        //
        DataSource[] newDataSources = new DataSource[dataSources.length + 1];
        System.arraycopy(dataSources, 0, newDataSources, 0, dataSources.length);
        dataSources = newDataSources;
        //
        String[] newSchemas = new String[schemas.length + 1];
        System.arraycopy(schemas, 0, newSchemas, 0, schemas.length);
        schemas = newSchemas;
        //
        String[] newDbmsTypes = new String[dbmsTypes.length + 1];
        System.arraycopy(dbmsTypes, 0, newDbmsTypes, 0, dbmsTypes.length);
        dbmsTypes = newDbmsTypes;
        //
        String[] newDbUsers = new String[dbUsers.length + 1];
        System.arraycopy(dbUsers, 0, newDbUsers, 0, dbUsers.length);
        dbUsers = newDbUsers;
        //
        TraceLogManager[] newTraceLogs = new TraceLogManager[traceLogs.length + 1];
        System.arraycopy(traceLogs, 0, newTraceLogs, 0, traceLogs.length);
        traceLogs = newTraceLogs;
      }
      int index = dataSourceNames.length - 1;
      dataSourceNames[index] = dataSourceName;
      dataSources[index] = dataSource;
      dbmsTypes[index] = dbmsType;
      dbUsers[index] = dbUser;
      schemas[index] = schema;
      if (dataSourceDispName != null) {
        dataSourceDispNames[index] = dataSourceDispName;
      } else {
        dataSourceDispNames[index] = schema;
      }
    } else {
      if (dataSourceNames == null) { // �ŏ��̐ڑ��̏ꍇ�́A�v���I�Ȃ̂ŃG���[�Ƃ���
        throw new ServletException("�f�[�^�\�[�X�ɐڑ��ł��܂���[" + dataSourceName + "]");
      }
    }
  }
  
  private void removeDataSource(String dataSourceName) {
    for (int i = dataSourceDispNames.length - 1; i > 0; --i) {
      if (dataSourceName.equals(dataSourceDispNames[i]) || dataSourceName.equals(dataSourceNames[i])) {
        String[] newDataSourceNames = new String[dataSourceNames.length - 1];
        System.arraycopy(dataSourceNames, 0, newDataSourceNames, 0, i);
        if (i < dataSourceNames.length - 1) {
          System.arraycopy(dataSourceNames, i + 1, newDataSourceNames, i, dataSourceNames.length - 1 - i);
        }
        dataSourceNames = newDataSourceNames;
        //
        String[] newDataSourceDispNames = new String[dataSourceDispNames.length - 1];
        System.arraycopy(dataSourceDispNames, 0, newDataSourceDispNames, 0, i);
        if (i < dataSourceDispNames.length - 1) {
          System.arraycopy(dataSourceDispNames, i + 1, newDataSourceDispNames, i, dataSourceDispNames.length - 1 - i);
        }
        dataSourceDispNames = newDataSourceDispNames;
        //
        DataSource[] newDataSources = new DataSource[dataSources.length - 1];
        System.arraycopy(dataSources, 0, newDataSources, 0, i);
        if (i < dataSources.length - 1) {
          System.arraycopy(dataSources, i + 1, newDataSources, i, dataSources.length - 1 - i);
        }
        dataSources = newDataSources;
        //
        String[] newSchemas = new String[schemas.length - 1];
        System.arraycopy(schemas, 0, newSchemas, 0, i);
        if (i < schemas.length - 1) {
          System.arraycopy(schemas, i + 1, newSchemas, i, schemas.length - 1 - i);
        }
        schemas = newSchemas;
        //
        String[] newDbmsTypes = new String[dbmsTypes.length - 1];
        System.arraycopy(dbmsTypes, 0, newDbmsTypes, 0, i);
        if (i < dbmsTypes.length - 1) {
          System.arraycopy(dbmsTypes, i + 1, newDbmsTypes, i, dbmsTypes.length - 1 - i);
        }
        dbmsTypes = newDbmsTypes;
        //
        String[] newDbUsers = new String[dbUsers.length - 1];
        System.arraycopy(dbUsers, 0, newDbUsers, 0, i);
        if (i < dbUsers.length - 1) {
          System.arraycopy(dbUsers, i + 1, newDbUsers, i, dbUsers.length - 1 - i);
        }
        dbUsers = newDbUsers;
        //
        TraceLogManager[] newTraceLogs = new TraceLogManager[traceLogs.length - 1];
        System.arraycopy(traceLogs, 0, newTraceLogs, 0, i);
        if (i < traceLogs.length - 1) {
          System.arraycopy(traceLogs, i + 1, newTraceLogs, i, traceLogs.length - 1 - i);
        }
        traceLogs = newTraceLogs;
        
      }
    }
  }
  
  private static boolean isTrue(String b) {
    if (b == null) {
      return false;
    }
    if ("1".equals(b) || "true".equalsIgnoreCase(b) || "on".equalsIgnoreCase(b)) {
      return true;
    }
    return false;
  }
  

  private static boolean isBlank(String s) {
    if (s == null) {
      return true;
    }
    if (s.trim().length() == 0) {
      return true;
    }
    return false;
  }
  
  // JavaApp����Ă΂ꂽ�ꍇ�̃_�~�[�L����
  private static Hashtable dummySession = new Hashtable();
  // �Z�b�V�����Ɋi�[�����I�u�W�F�N�g���擾����
  private static Object getSessionObject(HttpServletRequest request, String key) {
    if (request == null) {
      return dummySession.get(key);
    }
    HttpSession session = request.getSession(false);
    if (session != null) {
      return session.getAttribute(key);
    }
    return null;
  }
  // �Z�b�V�����փI�u�W�F�N�g���i�[����
  private static void setSessionObject(HttpServletRequest request, String key, Object object) {
    if (request == null) {
      if (object != null) {
        dummySession.put(key, object);
      }
      return;
    }
    if (object == null) {
      // object��null�̏ꍇ�̓Z�b�V�����������
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.removeAttribute(key);
      }
      return;
    }
    HttpSession session = request.getSession();
    if (session != null) {
      session.setAttribute(key, object);
    }
  }

  // Servlet�̎󂯌�
  public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException {

    request = handleUploadFile(request);
    
    // �p�X���[�h�`�F�b�N
    String loginMode = ADMIN_MODE;
    if (!checkPassword(request, response)) {
      // �p�X���[�h�ݒ�ςŖ��F�؂̏ꍇ�͏I��
      return;
    }
    
    loginMode = (String)request.getSession().getAttribute("DBACCESS");
    
    String tab = request.getParameter("tab");
    String command = request.getParameter("command");
    if (tab != null) {
      log_debug("tab="+tab+",command="+command);
    }
    if (tab == null || tab.trim().length() == 0) {
      if (command != null) {
        tab = "Result";
      } else {
        tab = tabs[0];
      }
    }
    
    if (command != null && command.trim().length() > 0) {
      String cmd = new StringTokenizer(command).nextToken();
      if (cmd.equals("config") || cmd.equals("scan")) {
        // Command���� "config" �� "scan"�@����n�܂�R�}���h�����
        // ���ꂽ�ꍇ�́AMBB�^�u�֑J��
        tab = "MBB";
      }
    }
    
    // ���[�U�[���[�h�̏ꍇ��MBB�^�O�̂݊J��
    if (loginMode.equals(USER_MODE)) {
      tab = "MBB";
    }
    
    String _command = request.getParameter("_command");
    if (_command != null && _command.equalsIgnoreCase("download")) {
      // _command=download���w�肳��Ă���΃_�E�����[�h������
      log_debug("_command=" + _command);
      doDownload(request, response);
      return;
    }
    
    if (tab.equalsIgnoreCase("Tables")) {
      // �e�[�u���ꗗ
      doTables(request, response);
    } else if (tab.equalsIgnoreCase("Edit")) {
      // �e�[�u���ҏW
      doEdit(request, response);
    } else if (tab.equals("Command")) {
      // �R�}���h����
      doCommandInput(request, response);
    } else if (tab.equalsIgnoreCase("Result")) {
      // �R�}���h���s���ʏo��
      doCommandResult(request, response);
    } else if (tab.equalsIgnoreCase("MBB")) {
      // MBB���j���[
      doMBB(request, response, loginMode);
    } else {
      // �f�t�H���g�̓e�[�u���ꗗ
      doTables(request, response);
    }
  }
  
  public void destroy() {
    for (Enumeration enu = tempFiles.elements(); enu.hasMoreElements(); ) {
      File temp = (File)enu.nextElement();
      deleteTempFile(temp);
    }
    super.destroy();
  }
  
  private void deleteTempFile(File tempFile) {
    if (tempFile == null) {
      return;
    }
    if (tempFile.exists()) {
      tempFile.delete();
      log_debug("delete temp file: " + tempFile.getAbsolutePath());
    }
    tempOrigFiles.remove(tempFile.getName());
  }
  
  // �������O�o�b�t�@��DEBUG���o�͗p
  public static synchronized void log_debug(String str) {
    debugLog.add(str);
    while (debugLog.size() > 1000) {
      debugLog.remove(debugLog.size() - 1);
    }
  }
  public static void log_debug(Throwable e) {
    // ���O��StackTrace�������o��
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    e.printStackTrace(new PrintStream(baos));
    log_debug(baos.toString());
  }
  /**
   * �p�X���[�h�`�F�b�N
   * @param out
   * @param request
   * @return
   * @throws ServletException 
   */
  private boolean checkPassword(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    
    HttpSession session = request.getSession();
    String key = (String)session.getAttribute("DBACCESS");
    String logout = request.getParameter("logout");
    if (logout != null) {
      session.removeAttribute("DBACCESS");
      key = null;
    }
    if (key == null) {
      // �p�X���[�h�`�F�b�N(web.xml�Ńu�����N���w�肷��΃m�[�`�F�b�N)
      String password = request.getParameter("password");
      String command = request.getParameter("_command");
      if (DbAccessUtils.comparePassword(password, DBACCESS_ADMINPASSWORD) >= 0) {
        // �p�X���[�h���͂���p�X���[�h��v�����ꍇ
        // �Z�b�V�����ɔF�؍σL�[���Z�b�g
        session.setAttribute("DBACCESS", ADMIN_MODE);
        insertServiceLog(request, "ADMIN_MODE");
      } else if (DbAccessUtils.comparePassword(password, DBACCESS_USERPASSWORD) >= 0) {
        // �Z�b�V�����ɔF�؍σL�[���Z�b�g
        session.setAttribute("DBACCESS", USER_MODE);
        insertServiceLog(request, "USER_MODE");
      } else {
        // �p�X���[�h�����͂܂��́A��v���Ȃ��ꍇ
        response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
        PrintWriter out = null;
        try {
          out = new PrintWriter(new BufferedWriter(new
              OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
        } catch (Exception e) {
          throw new ServletException(e);
        }
        try {
          // �p�X���[�h���̓t�H�[����\�����I��
          printHtmlHeader(out);
          out.print("<body");
          if (bodyStyle != null && bodyStyle.trim().length() > 0) {
            out.print(" style=\"" + escapeInputValue(bodyStyle) + "\"");
          }
          if (bgColor != null && bgColor.trim().length() > 0) {
            out.print(" bgcolor=\"" + escapeInputValue(bgColor) + "\"");
          }
          out.println(">");
          if (password != null) {
            // �p�X���[�h�����͂���Ă���ꍇ�̓G���[���b�Z�[�W��\��
            out.println("<font color=\"" + ERROR_COLOR + "\">�p�X���[�h���Ⴂ�܂�.</font><br>");
          }
          out.println("<form method=\"post\" action=\"?\">");
          out.println("�p�X���[�h����͂��Ă�������: <input type=\"password\" name=\"password\">");
          out.println("<input type=\"submit\" value=\"login\">");
          out.println("</form>");
          out.println("</body>");
          out.println("</html>");
          out.flush();
        } catch(IOException e) {
        }
        return false;
      }
    }
    return true;
  }

  private void doDownload(HttpServletRequest request, HttpServletResponse response) {

    String report = request.getParameter("report");
    if (report != null && report.length() > 0) {
      // report(HTML TABLE)��EXCEL�`���ɕϊ����o�͂���
      String dlFileName = "list.xls";
      response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
      response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
      try {
        ExcelManager.tableToExcel(response.getOutputStream(), report, null);
      } catch (IOException e) {
        log_debug(e);
        try {
          e.printStackTrace(new PrintStream(response.getOutputStream()));
        } catch (IOException e1) {
        }
      }
      return;
    }
    String xlsDoc = request.getParameter("xlsdoc");
    if (xlsDoc != null) {
      // Excel�h�L�������g�̃_�E�����[�h
      String[] pageids = request.getParameterValues("pageid");
      if (pageids != null && pageids.length > 0) {
        Connection conn = null;
        try {
          conn = getConnection();
          conn.setAutoCommit(false);
          String dlFileName = pageids[0] + ".xls";
          response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
          response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
          try {
            OutputStream os = response.getOutputStream();
            File templateFile = new File(appPath, "excel/templates/pagelayout.xls");
            File cssFile = new File(appPath, "css/common.css");
            DocumentManager.createPageDocument(conn, templateFile.getAbsolutePath(), pageids[0], os, cssFile.getAbsolutePath());
            os.flush();
            os.close();
          } catch (IOException e) {
            log_debug(e);
            try {
              e.printStackTrace(new PrintStream(response.getOutputStream()));
            } catch (IOException e1) {
            }
          }
        } catch (SQLException e) {
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException e) {}
          }
        }
      }
      String[] tableids = request.getParameterValues("tableid");
      if (tableids != null && tableids.length > 0) {
        Connection conn = null;
        try {
          conn = getConnection();
          conn.setAutoCommit(false);
          String dlFileName = tableids[0] + ".xls";
          response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
          response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
          try {
            OutputStream os = response.getOutputStream();
            File templateFile = new File(appPath, "excel/templates/tablelayout.xls");
            DocumentManager.createTableDocument(conn, templateFile.getAbsolutePath(), tableids[0], os);
            os.flush();
            os.close();
          } catch (IOException e) {
            log_debug(e);
            try {
              e.printStackTrace(new PrintStream(response.getOutputStream()));
            } catch (IOException e1) {
            }
          }
        } catch (SQLException e) {
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException e) {}
          }
        }
      }
      return;
    }
    String addignorepath = request.getParameter("addignorepath");
    if (addignorepath != null && addignorepath.trim().length() > 0) {
      // ���O�p�X�̒ǉ�
      Connection conn = null;
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        loadIgnoreModules(conn);
        if (!isIgnorePath(addignorepath, null)) {
          if (addignorepath.indexOf("%") != -1) {
            addignorepath = addignorepath.replaceAll("%", "%25");
          }
          if (addignorepath.indexOf(",") != -1) {
            addignorepath = addignorepath.replaceAll(",", "%2c");
          }
          saveConfigItem(conn, "ignorepath", addignorepath);
        }
        conn.commit();
      } catch (SQLException e) {
        
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {}
        }
      }
      response.setContentType("text/plain");
      try {
        response.getWriter().print(addignorepath);
      } catch (IOException e) {}
      return;
    }
    
    boolean forDelete = DbAccessUtils.getBoolValue(request.getParameter("fordelete"));
    boolean filenameid = DbAccessUtils.getBoolValue(request.getParameter("filenameid"));
    boolean toexcel = DbAccessUtils.getBoolValue(request.getParameter("toexcel"));
    String filenamets = request.getParameter("filenamets");
    
    String object = request.getParameter("object");
    String table = request.getParameter("table");
    String[] ids = request.getParameterValues("id");
    String id = request.getParameter("id");
    String[] files = request.getParameterValues("file");
    String shallow = request.getParameter("shallow");
    if (id == null && files != null && files.length == 1 && files[0].startsWith("mbb/")) {
      // file�̃p�X��mbb/����n�܂�ꍇ�́ADB��`�̂̃_�E�����[�h�ɕϊ�
      id = files[0];
      files = null;
      filenameid = true;
    }
    if (files == null && (table == null || table.trim().length() == 0) && id != null && id.indexOf("/") != -1) {
      // id�Ƀp�X�w�肳�ꂽ�ꍇ�i�R���e�L�X�g���j���[���ۑ��j
      if (id.startsWith("mbb/")) {
        // MBB���W���[��
        String[] params = id.split("/");
        table = params[1].toUpperCase() + "MASTER";
        ids = new String[] {params[params.length - 1]};
      } else {
        files = new String[] {id};
      }
    }
    if (files != null && files.length > 0) {
      // �R���e�L�X�g���t�@�C���̃_�E�����[�h�i���R�s�[�Ŏg�p�j
      String dlFileName = "files.zip";
      if (files.length == 1) {
        dlFileName = new File(files[0]).getName();
        if (dlFileName.indexOf(".") != -1) {
          dlFileName = dlFileName.substring(0, dlFileName.lastIndexOf(".")) + ".zip";
        }
      }
      response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
      response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
      String fileName = null;
      try {
        OutputStream out = response.getOutputStream();
        ZipOutputStream zos = new ZipOutputStream(out);
        int entryCount = 0;
        for (int i = 0; i < files.length; ++i) {
          fileName = files[i].replaceAll("\\.\\.", ""); // �T�j�^�C�Y
          File file = new File(appPath, fileName);
          if (file.exists()) {
            ZipEntry ze = new ZipEntry(fileName);
            ze.setTime(file.lastModified());
            ze.setSize(file.length());
            zos.putNextEntry(ze);
            DbAccessUtils.writeFile(zos, file);
            zos.closeEntry();
            entryCount++;
          } else if (fileName.endsWith(".java")) {
            log_debug("path=" + new File(appPath, "src").getAbsolutePath());
            DbAccessUtils.writeZipFileFromSrcZip(zos, fileName, new File(appPath, "src"));
          }
        }
        if (entryCount > 0) {
          zos.finish();
        }
        zos.close();
        out.flush();
      } catch (Exception e) {
        log_debug("fileName=" + fileName);
        log_debug(e);
      }
      return;
    }
    if (object != null && object.trim().length() > 0) {
      // �f�[�^�x�[�X�I�u�W�F�N�g��DDL���擾
      if (isOracle(0)) {
        String dlFileName = object + ".zip";
        if (ids != null && ids.length == 1) {
          dlFileName = ids[0] + ".zip";
        }
        String object_type = object.toUpperCase();
        if (object_type.indexOf("_") != -1) {
          object_type = object_type.replaceAll("_", " ");
        }
        response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        Connection conn = null;
        String objectName = null;
        try {
          conn = getConnection();
          conn.setAutoCommit(false);
          OutputStream out = response.getOutputStream();
          ZipOutputStream zos = new ZipOutputStream(out);
          for (int i = 0; i < ids.length; ++i) {
            objectName = ids[i];
            String ddl = getObjectDDL(conn, object_type, objectName);
            ZipEntry ze = new ZipEntry(getEntryFileName(objectName) + ".sql");
            ze.setTime(getObjectLastModified(conn, object.toUpperCase(), objectName));
            byte[] bytes = ddl.getBytes("UTF-8");
            ze.setSize(bytes.length);
            zos.putNextEntry(ze);
            DbAccessUtils.writeFile(zos, new ByteArrayInputStream(bytes));
            zos.closeEntry();
          }
          zos.finish();
          zos.close();
          out.flush();
        } catch (Exception e) {
          log_debug("objectName=" + objectName);
          log_debug(e);
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException se) {}
          }
        }
        return;
      }
    }
    
    String fileext = ".csv";
    if (forDelete) {
      fileext += ".del";
    }
    String fileBase = table;
    if (table.toUpperCase().endsWith("MASTER")) {
      fileBase = table.substring(0, table.length() - 6);
    }
    if (toexcel) {
      
      Connection conn = null;
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        // EXCEL�o��
        response.setHeader("Content-type", "application/octet-stream");
        response.setHeader("Content-disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, table) + ".xls\"");
        BufferedOutputStream out = new BufferedOutputStream(response.getOutputStream());
        File template = new File(appPath, "excel/templates/" + table + ".xls");
        Properties option = null;
        if (!template.exists()) {
          template = null;
        }
        int r = ExcelManager.excelExportFromTable(conn, table, out, null, template, option);
        insertSQLLog("EXPORT " + table, Integer.toString(r), null, null, getLoginInfos(request));
        out.flush();
        out.close();
      } catch (Exception e) {
        log_debug(e);
        try {
          // �G���[����
          response.reset();
          response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
          PrintWriter out = null;
          out = new PrintWriter(new BufferedWriter(new
              OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
          out.println("<html>");
          out.print("<body");
          if (bodyStyle != null && bodyStyle.trim().length() > 0) {
            out.print(" style=\"" + escapeInputValue(bodyStyle) + "\"");
          }
          if (bgColor != null && bgColor.trim().length() > 0) {
            out.print(" bgcolor=\"" + escapeInputValue(bgColor) + "\"");
          }
          out.println(">");
          out.println("<pre style=\"color:" + ERROR_COLOR +";\">");
          out.println("�V�X�e���G���[�F");
          e.printStackTrace(out);
          out.println("</pre>");
          out.println("</body>");
          out.println("</html>");
          out.flush();
        } catch (Exception e2) {
        }
        
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException se) {}
        }
      }
      return;

    } else {

      if (ids == null || ids.length == 0) {
        // �Ώ�ID���w�肳��Ă��Ȃ��ꍇ�̃G���[����
        response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
        PrintWriter out = null;
        try {
          out = new PrintWriter(new BufferedWriter(new
              OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
          out.println("<html>");
          out.print("<body");
          if (bodyStyle != null && bodyStyle.trim().length() > 0) {
            out.print(" style=\"" + escapeInputValue(bodyStyle) + "\"");
          }
          if (bgColor != null && bgColor.trim().length() > 0) {
            out.print(" bgcolor=\"" + escapeInputValue(bgColor) + "\"");
          }
          out.println(">");
          out.println("�G���[�F �Ώۂ�I�����Ă��������B");
          out.println("<script language=\"javascript\">");
          out.println("alert('�Ώۂ�I�����Ă��������B');");
          out.println("history.back();");
          out.println("</script>");
          out.println("</body>");
          out.println("</html>");
        } catch(Exception e) {
          log_debug(e);
        } finally {
          if (out != null) {
            out.flush();
            out.close();
          }
        }
        return;
      }
      
    }
    
    OutputStream out = null;
    Connection conn = null;
    try {
      
      String datetime = "";
      if (filenamets == null || !filenamets.equals("0")) {
      
        try {
          long timestamp = System.currentTimeMillis();
          Calendar cal = Calendar.getInstance();
          cal.setTimeInMillis(timestamp);
          int year = cal.get(Calendar.YEAR);
          int month = cal.get(Calendar.MONTH) + 1;
          int day = cal.get(Calendar.DAY_OF_MONTH);
          datetime = "_";
          datetime += Integer.toString(year);
          datetime += month < 10 ? "0" + month : Integer.toString(month);
          datetime += day < 10 ? "0" + day : Integer.toString(day);
          if (filenamets != null && filenamets.equals("2")) {
            // ���Ԃ�t��
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int min = cal.get(Calendar.MINUTE) + 1;
            int sec = cal.get(Calendar.SECOND);
            datetime += hour < 10 ? "0" + hour : Integer.toString(hour);
            datetime += min < 10 ? "0" + min : Integer.toString(min);
            datetime += sec < 10 ? "0" + sec : Integer.toString(sec);
          }
        } catch(Exception e) {
          datetime = "";
        }
        
      }
      
      // table�ɂ���ē��e�����������肵��zip�ɂ܂Ƃ߂ďo��
      
      // ID���t�@�C�����ɂ���ꍇ�͍ŏ���ID���x�[�X���Ƃ���
      if (filenameid) {
        fileBase = ids[0];
      }

      conn = getConnection(); // ���C�A�E�g���擾�p
      conn.setAutoCommit(false);
      Hashtable zipentries = new Hashtable(); // �d���G���g�����o�p���[�N
      if (table.equalsIgnoreCase("FUNCTIONMASTER")) {
        conn.setAutoCommit(false);
        Vector functionParams = null;
        Vector applicationParams = null;
        Vector processParams = null;
        Vector pageParams = null;
        String fileName = fileBase + datetime + ".zip";
        String dlFileName = fileName;
        if (forDelete) {
          dlFileName = "del_" + fileName;
        }
        response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        
        out = response.getOutputStream();
        ZipOutputStream zos = new ZipOutputStream(out);
        
        for (int i = 0; i < ids.length; ++i) {
          // �@�\�}�X�^�̃G���g���̏o��
          String entryFileName = "function/" + getEntryFileName(ids[i]) + fileext;
          if (zipentries.get(entryFileName) != null) {
            log_debug(entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
            continue;
          } else {
            zipentries.put(entryFileName, entryFileName);
          }
          ZipEntry ze = new ZipEntry(entryFileName);
          long ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "FUNCTIONMASTER", new String[]{"FUNCTIONID"}, ids[i]));
          if (ts != -1) {
            ze.setTime(ts);
          }
          zos.putNextEntry(ze);
          
          if (functionParams == null) {
            functionParams = getRelationParams(conn, "FUNCTIONMASTER");
          }
          printExportMCSV(zos, new String[]{ids[i]}, functionParams);
          
          zos.closeEntry();
          
          if (shallow == null || !shallow.equals("1")) {
            // shallow=1�łȂ��ꍇ�͋@�\�}�X�^�Ŏg�p���Ă���e�@�\�\�����܂߂�
            Vector functionComp = getTableData(conn, "FUNCTIONCOMPOSITIONMASTER", "FUNCTIONCOMPOSITIONID,FUNCTIONCOMPOSITIONCLASS", new String[]{"FUNCTIONID"}, new String[]{ids[i]});
            
            for (int j = 0; j < functionComp.size(); ++j) {
              // �e�\���G���g���̏o��
              String[] comps = (String[])functionComp.get(j);
              String compid = comps[0];
              String compclass = comps[1];
              if (compclass != null && compclass.equals("1")) {
                // APPLICATIONMASTER
                entryFileName =  "application/" + getEntryFileName(compid) + fileext;
                if (zipentries.get(entryFileName) != null) {
                  log_debug(compid + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
                  continue;
                } else {
                  zipentries.put(entryFileName, entryFileName);
                }
                ze = new ZipEntry(entryFileName);
                ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "APPLICATIONMASTER", new String[]{"APPLICATIONID"}, compid));
                if (ts != -1) {
                  ze.setTime(ts);
                }
                zos.putNextEntry(ze);
                
                if (applicationParams == null) {
                  applicationParams = getRelationParams(conn, "APPLICATIONMASTER");
                }
                printExportMCSV(zos, new String[]{compid}, applicationParams);
                
                zos.closeEntry();
              } else if (compclass != null && compclass.equals("2")) {
                // PROCESSMASTER
                entryFileName =  "process/" + getEntryFileName(compid) + fileext;
                if (zipentries.get(entryFileName) != null) {
                  log_debug(compid + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
                  continue;
                } else {
                  zipentries.put(entryFileName, entryFileName);
                }
                ze = new ZipEntry(entryFileName);
                ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "PROCESSMASTER", new String[]{"PROCESSID"}, compid));
                if (ts != -1) {
                  ze.setTime(ts);
                }
                zos.putNextEntry(ze);
                
                if (processParams == null) {
                  processParams = getRelationParams(conn, "PROCESSMASTER");
                }
                printExportMCSV(zos, new String[]{compid}, processParams);
                
                zos.closeEntry();
                
                //2014/04/16 classtype �G�N�X�|�[�g start
                String option = request.getParameter("option");
                if(option != null && option.startsWith("CLASSTYPE")){
                  // CLASSTYPE
                  ArrayList classTypes = DbAccessUtils.getClassTypeByProcessId(conn, compid);
                  Vector classTypeParams = null;
                  for (int m = 0; m < classTypes.size(); m++) {
                    compid = (String)classTypes.get(m);
                    if(!option.equals("CLASSTYPE_ALL")){
                      if(compid.startsWith("item.") || compid.startsWith("mbb.")){
                        //MBB�ŗL�̃N���X�^�C�v�͑ΏۊO
                        continue;
                      }
                    }
                    entryFileName =  "classtype/" + getEntryFileName(compid) + fileext;
                    if (zipentries.get(entryFileName) != null) {
                      log_debug(compid + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
                      continue;
                    } else {
                      zipentries.put(entryFileName, entryFileName);
                    }
                    ze = new ZipEntry(entryFileName);
                    ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "CLASSTYPEMASTER", new String[]{"CLASSTYPE"}, compid));
                    if (ts != -1) {
                      ze.setTime(ts);
                    }
                    zos.putNextEntry(ze);
                    
                    if (classTypeParams == null) {
                      classTypeParams = getRelationParams(conn, "CLASSTYPEMASTER");
                    }
                    printExportMCSV(zos, new String[]{compid}, classTypeParams);
                    
                    zos.closeEntry();
                    
                    //class�t�@�C�����o��
                    String entryName = getEntryFileName(compid).replace(".", "/");
                    String classFilePath = "jp/co/bbs/unit/" + entryName + ".class";
                    File classFile = new File(appPath, "WEB-INF/classes/" + classFilePath);
                    if(!classFile.exists()){
                      classFilePath = entryName + ".class";
                      classFile = new File(appPath, "WEB-INF/classes/" + classFilePath);
                    }
                    
                    if(!classFile.exists()){
                      continue;
                    }
                    
                    entryFileName =  "module/WEB-INF/classes/" + classFilePath;
                    if (zipentries.get(entryFileName) != null) {
                      log_debug(compid + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
                      continue;
                    } else {
                      zipentries.put(entryFileName, entryFileName);
                    }
                    ze = new ZipEntry(entryFileName);
                    ts = classFile.lastModified();
                    if (ts != -1) {
                      ze.setTime(ts);
                    }
                    zos.putNextEntry(ze);
                    zipArchive(zos, classFile);
                    zos.closeEntry();
                  }
                }
                //2014/04/16 classtype �G�N�X�|�[�g end
                
              } else if (compclass != null && compclass.equals("3")) {
                // PAGEMASTER
                entryFileName =  "page/" + getEntryFileName(compid) + fileext;
                if (zipentries.get(entryFileName) != null) {
                  log_debug(compid + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
                  continue;
                } else {
                  zipentries.put(entryFileName, entryFileName);
                }
                ze = new ZipEntry(entryFileName);
                ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "PAGEMASTER", new String[]{"PAGEID"}, compid));
                if (ts != -1) {
                  ze.setTime(ts);
                }
                zos.putNextEntry(ze);
                
                if (pageParams == null) {
                  pageParams = getRelationParams(conn, "PAGEMASTER");
                }
                printExportMCSV(zos, new String[]{compid}, pageParams);
                
                zos.closeEntry();
              }
              //2014/04/24 �t�@�C���@�G�N�X�|�[�g start
              else if (compclass != null && compclass.equals("4")) {
                // �t�@�C��
                entryFileName =  "module/" + compid;
                File file = new File(appPath, compid);
                
                if(!file.exists()){
                  log_debug(compid + "�͑��݂��Ȃ�");
                  continue;
                }
                
                if (zipentries.get(entryFileName) != null) {
                  log_debug(compid + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
                  continue;
                } else {
                  zipentries.put(entryFileName, entryFileName);
                }
                ze = new ZipEntry(entryFileName);
                ts = file.lastModified();
                if (ts != -1) {
                  ze.setTime(ts);
                }
                zos.putNextEntry(ze);
                zipArchive(zos, file);
                zos.closeEntry();
              }
              //2014/04/24 �t�@�C���@�G�N�X�|�[�g end
            }
          }
        }
        zos.finish();
        
      } else if (table.equalsIgnoreCase("TABLEMASTER")) {
        Vector tableParams = null;
        Vector datafieldParams = null;
        String fileName = fileBase + datetime + ".zip";

        String dlFileName = fileName;
        if (forDelete) {
          dlFileName = "del_" + fileName;
        }
        response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        
        out = response.getOutputStream();
        ZipOutputStream zos = new ZipOutputStream(out);
        
        for (int i = 0; i < ids.length; ++i) {
          // �e�[�u�����C�A�E�g�̃G���g���̏o��
          String entryFileName = "table/" + getEntryFileName(ids[i]) + fileext;
          if (zipentries.get(entryFileName) != null) {
            log_debug(entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
            continue;
          } else {
            zipentries.put(entryFileName, entryFileName);
          }
          ZipEntry ze = new ZipEntry(entryFileName);
          long ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "TABLEMASTER", new String[]{"TABLEID"}, ids[i]));
          if (ts != -1) {
            ze.setTime(ts);
          }
          zos.putNextEntry(ze);
          
          if (tableParams == null) {
            tableParams = getRelationParams(conn, "TABLEMASTER");
          }
          printExportMCSV(zos, new String[]{ids[i]}, tableParams);
          
          zos.closeEntry();
          
          // ���C�A�E�g�����擾���āA�g�p���Ă���f�[�^�t�B�[���h���܂߂�
          Hashtable tableLayoutInfo = getTableLayout(conn, ids[i]);
          
          Vector fields = new Vector();
          fields.addAll((Vector)tableLayoutInfo.get("$base$"));
          fields.addAll((Vector)tableLayoutInfo.get("$name$"));
          fields.addAll((Vector)tableLayoutInfo.get("$info$"));
          
          for (int j = 0; j < fields.size(); ++j) {
            // �e�f�[�^�t�B�[���h�̃G���g���̏o��
            String datafieldid = (String)fields.get(j);
            entryFileName =  "datafield/" + getEntryFileName(datafieldid) + fileext;
            if (zipentries.get(entryFileName) != null) {
              log_debug(ids[i] + ":" + entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
              continue;
            } else {
              zipentries.put(entryFileName, entryFileName);
            }
            ze = new ZipEntry(entryFileName);
            ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, "DATAFIELDMASTER", new String[]{"DATAFIELDID"}, datafieldid));
            if (ts != -1) {
              ze.setTime(ts);
            }
            zos.putNextEntry(ze);
            
            if (datafieldParams == null) {
              datafieldParams = getRelationParams(conn, "DATAFIELDMASTER");
            }
            printExportMCSV(zos, new String[]{datafieldid}, datafieldParams);
            
            zos.closeEntry();
            
          }
        }
        zos.finish();
      } else {
        // ���̑��̃e�[�u���̏ꍇ
        Vector tableParams = getRelationParams(conn, table);
        String fileName = fileBase + datetime + ".zip";

        String dlFileName = fileName;
        if (forDelete) {
          dlFileName = "del_" + fileName;
        }
        response.setContentType("application/download; name=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + DbAccessUtils.encodeFileName(request, dlFileName) + "\"");

        out = response.getOutputStream();
        ZipOutputStream zos = new ZipOutputStream(out);
        
        for (int i = 0; i < ids.length; ++i) {
          String entryFileName = getEntryFileName(ids[i]) + fileext;
          if (zipentries.get(entryFileName) != null) {
            log_debug(entryFileName + "�͏d���G���g���̂��߃X�L�b�v");
            continue;
          } else {
            zipentries.put(entryFileName, entryFileName);
          }
          ZipEntry ze = new ZipEntry(entryFileName);
          ze.setMethod(ZipEntry.DEFLATED);
          if (tableParams != null) {
            String[] params = (String[])tableParams.get(0);
            if (params.length > 1) {
              long ts = 0L;
              ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, table, shiftArray(params), ids[i]));
              if (ts != -1) {
                ze.setTime(ts);
              }
            }
          }
          zos.putNextEntry(ze);
          String[] values = ids[i].split(",", -1);
          
          printExportMCSV(zos, values, tableParams);
          
          zos.closeEntry();
          
        }
        zos.finish();
        zos.close();
      }

      out.close();
    } catch (Exception e) {
      log_debug(e);
      e.printStackTrace(new PrintWriter(out));
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
    }
    
  }
  
  private void doExportToCSV(HttpServletResponse response, String command) {
    response.setHeader("Content-type", "application/octet-stream");
    response.setHeader("Content-disposition", "attachment; filename=\"data.csv\"");
    BufferedWriter bw = null;
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      log_debug(command);
      String charset = "UTF-8";
      if (command.toUpperCase().startsWith("SJIS/")) {
        command = command.substring(5);
        charset = "Windows-31J";
      }
      bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), charset));
      conn = getConnection();
      conn.setAutoCommit(false);
      stmt = conn.prepareStatement(command);
      rs = stmt.executeQuery();
      ResultSetMetaData meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();
      for (int i = 0; i < columnCount; ++i) {
        String name = meta.getColumnName(i + 1);
        if (i > 0) {
          bw.write(",");
        }
        bw.write(DbAccessUtils.escapeCSV(name));
      }
      bw.write("\r\n");
      while (rs.next()) {
        for (int i = 0; i < columnCount; ++i) {
          String value = rs.getString(i + 1);
          if (i > 0) {
            bw.write(",");
          }
          bw.write(DbAccessUtils.escapeCSV(value));
        }
        bw.write("\r\n");
      }
    } catch (SQLException e) {
    } catch (IOException e) {
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
      if (conn != null) {
        try {
          conn.rollback();
          conn.close();
        } catch (SQLException e) {}
      }
      if (bw != null) {
        try {
          bw.flush();
          bw.close();
        } catch (IOException e) {}
      }
    }
  }
  /**
   * �t�@�C�����Ƃ��Ĉ�����悤�ɕs���ȕ������ϊ�����
   * @param name
   * @return
   */
  private static String getEntryFileName(String name) {
    if (name == null || name.length() == 0) {
      // ����0�̏ꍇ�͂Ƃ肠����"1"��Ԃ�
      return "1";
    }
    int maxlen = 247; // �ő咷(".csv.del"�����킹�čő�255����)
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < name.length(); ++i) {
      char c = name.charAt(i);
//      if (c == '_' || (c >= '0' && c <= '9') || (c >= '@' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
//        // �g���镶��
//        sb.append(c);
//      } else {
//        sb.append(toHexChar(c));
//      }
      if (c == '%' || c == '\\' || c == '/' || c == ':' || c == '*' ||
          c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
        // �t�@�C�����Ƃ��Ďg���Ȃ�������ϊ�
        sb.append(DbAccessUtils.toHexChar(c));
      } else {
        sb.append(c);
      }
      if (sb.length() >= maxlen) {
        // �����t�@�C�����̓J�b�g
        sb.setLength(maxlen);
        break;
      }
    }
    
    return sb.toString();
  }
  
  /**
   * �e�[�u���ꗗ���
   * @param out
   * @param request
   * @throws ServletException 
   */
  private void doTables(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    String tab = "Tables";
    
    String table_name = request.getParameter("table_name");
    if (table_name == null) {
      table_name = "";
    }
    String count = request.getParameter("count");
    String textexport = request.getParameter("textexport");
    if (textexport == null) {
      textexport = "0";
    }
    String withinfo = request.getParameter("withinfo");
    if (withinfo == null) {
      withinfo = "0";
    }
    if (request.getParameter("edit") != null) {
      doEdit(request, response);
      return;
    }
    if (request.getParameter("export") != null) {
      doCommandResult(request, response);
      return;
    }
    int objType = 0;
    String tabletype = request.getParameter("tabletype");
    if (tabletype != null) {
      try {
        objType = Integer.parseInt(tabletype);
      } catch(Exception e) {}
    }
    
    response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
    PrintWriter out = null;
    try {
      out = new PrintWriter(new BufferedWriter(new
          OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
    } catch (Exception e) {
      throw new ServletException(e);
    }
    printHeader(request, out, tab);
    printTabs(out, "Tables");

    out.println("<nobr>");
    printTableTypes(out, objType);
    out.println("<span id=\"selected_table\" onclick=\"var r=document.selection.createRange();r.moveStart('word',-1);r.moveEnd('word');r.select()\""
        + " ondblclick=\"doCommand('Result','command','desc '+this.innerText);return false;\""
        + "></span></nobr><br>");
    out.println("<nobr>");
    try {
      printObjectList(out, table_name, 20, count, objType);
    } catch (Exception e) {
      printError(out, e);
    }
    out.flush();
    try {
      printColumns(out, table_name, 20);
    } catch (Exception e) {
      printError(out, e);
    }
    out.flush();
    out.println("</nobr>");
    out.println("<br>");
    out.println("<input type=\"submit\" name=\"edit\" value=\"Edit\" onclick=\"doCommand('Tables','edit','1');return false;\">");
    out.println("<input type=\"submit\" name=\"desc\" value=\"Desc\" onclick=\"doCommand('Tables','desc','1');return false;\">");
    out.println("<input type=\"submit\" name=\"count\" value=\"Count\" onclick=\"doCommand('Tables','count','1');return false;\">");
    if (ExcelManager.isEnabled()) {
      out.println("<input type=\"submit\" name=\"excel\" value=\"Excel\" onclick=\"doExportToExcel();return false;\">");
    }
    out.println("<input type=\"submit\" name=\"export\" value=\"Export\" onclick=\"doCommand('Tables','export','1');return false;\">");
    out.println("<input type=\"checkbox\" id=\"textexport\" name=\"textexport\" value=\"1\"");
    if (textexport.equals("1")) {
      out.println("checked");
    }
    out.println("><label for=\"textexport\"><span class=\"text\">TAB��؂�e�L�X�g�`��&nbsp;&nbsp;</span></label>");
    out.println("<input type=\"checkbox\" id=\"withinfo\" name=\"withinfo\" value=\"1\"");
    if (withinfo.equals("1")) {
      out.println("checked");
    }
    out.println("><label for=\"withinfo\"><span class=\"text\">���E���̂��܂�&nbsp;&nbsp;</span></label>");
    //out.println("Filter:<input type=\"text\" name=\"filter\" value=\"\">");
    
    printFooter(out, tab);
    out.flush();
    out.close();
  }

  /**
   * �e�[�u���ҏW���
   * @param out
   * @param request
   * @throws ServletException 
   */
  private void doEdit(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    String tab = "Edit";
    response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
    PrintWriter out = null;
    try {
      out = new PrintWriter(new BufferedWriter(new
          OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
    } catch (Exception e) {
      throw new ServletException(e);
    }
    printHeader(request, out, tab);
    
    String edit_table = request.getParameter("edit_table");
    String table_name = request.getParameter("table_name");
    if (edit_table == null) {
      edit_table = request.getParameter("table_name");
    }
    if (table_name != null && !edit_table.equals(table_name)) {
      edit_table = table_name;
    }
    String lines = request.getParameter("lines");
    if (lines == null || lines.trim().length() == 0) {
      lines = "100";
    }
    String edit_filter = request.getParameter("edit_filter");
    String order = request.getParameter("order");
    if (order == null) {
      order = "";
    }
    String direction = request.getParameter("direction");
    if (direction == null) {
      direction = "";
    }
    String edit_command = request.getParameter("edit_command");
    int objType = 0;
    String tabletype = request.getParameter("tabletype");
    if (tabletype != null) {
      try {
        objType = Integer.parseInt(tabletype);
      } catch(Exception e) {}
    }

    if (!isBlank(edit_command)) {
      // �ҏW(Edit��ʂ���ǉ��A�X�V�A�폜)���ɌĂ΂��
      execSql(out, request, edit_command);
    }
    if (!isBlank(edit_table)) {
      // �e�[�u�����w�肪����ꍇ
      edit_table = getSQLObjectName(0, edit_table);
      String sql = null;
      boolean export = false;
      if (!isBlank(edit_filter)) {
        if (edit_filter.startsWith("E:")) {
          export = true;
          edit_filter = edit_filter.substring(2);
          sql = edit_filter;
          if (sql.trim().length() == 0) {
            sql = "SELECT/E * FROM " + edit_table;
          } else {
            if (sql.toUpperCase().startsWith("SELECT") && !sql.toUpperCase().startsWith("SELECT/E")) {
              sql = "SELECT/E" + sql.substring(6);
            }
          }
        } else {
          sql = edit_filter;
        }
      } else {
        sql = "SELECT * FROM " + edit_table;
      }
      if (!isBlank(order)) {
        sql += " ORDER BY " + order + " " + direction;
      }

      if (export) {
        printTabs(out, "Result");
        out.println("<input type=\"hidden\" name=\"table_name\" value=\"" + escapeInputValue(edit_table) + "\">");
        out.println("<input type=\"hidden\" name=\"tabletype\" value=\"" + objType + "\">");
        out.println("<input type=\"hidden\" name=\"edit_table\" value=\"" + edit_table + "\">");
        out.println("<input type=\"hidden\" name=\"edit_command\" value=\"\">");
        out.println("<input type=\"hidden\" name=\"edit_filter\" value=\"" + DbAccessUtils.escapeInputValue(edit_filter) + "\">");
        out.println("<input type=\"hidden\" name=\"order\" value=\"" + order + "\">");
        out.println("<input type=\"hidden\" name=\"direction\" value=\"" + direction + "\">");
      } else {
        printTabs(out, "Edit");
        out.println("<input type=\"hidden\" name=\"edit_table\" value=\"" + escapeInputValue(edit_table) + "\">");
        out.println("<input type=\"hidden\" name=\"tabletype\" value=\"" + objType + "\">");
        out.println("<input type=\"hidden\" name=\"edit_command\" value=\"\">");
        out.println("<input type=\"hidden\" name=\"edit_filter\" value=\"" + DbAccessUtils.escapeInputValue(edit_filter) + "\">");
        out.println("<input type=\"hidden\" name=\"order\" value=\"" + order + "\">");
        out.println("<input type=\"hidden\" name=\"direction\" value=\"" + direction + "\">");
        printInputTableName(out, edit_table, lines, objType);
      }

      printExecuteSQL(out, request, sql, true, order, direction, "0", lines, edit_filter);
    } else {
      // �e�[�u�����w�肪�����ꍇ�́A�e�[�u���ꗗ�R���{�{�b�N�X�̂ݕ\��
      printTabs(out, "Edit");

      printInputTableName(out, edit_table, lines, objType);
    }
    
    printFooter(out, tab);
    out.close();
  }

  private static String escapeInputValue(String v) {
    if (v == null) {
      return "";
    }
    v = v.replaceAll("\"", "&quot;");
    return v;
  }
  
  /**
   * �R�}���h���͉��
   * @param request Servlet.service��request
   * @param response Servlet.service��response
   */
  private void doCommandInput(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    String tab = "Command";
    // Command�^�u��I��
    String command = request.getParameter("command");
    String execsql = request.getParameter("execsql");
    String autocommit = request.getParameter("autocommit");
    if (command == null) {
      command = "";
    } else {
      if ((execsql != null) && (command.trim().length() > 0)) {
        // execsql�ɒl���w�肳�ꂽ�ꍇ�́A�R�}���h���s���ʉ�ʂ�\��
        doCommandResult(request, response);
        return;
      }
    }
    if (autocommit == null) {
      autocommit = "0";
    } else {
      autocommit = "1";
    }
    String table_name = request.getParameter("table_name");
    if (table_name == null) {
      table_name = "";
    }
    log_debug("command=" + command);

    response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
    PrintWriter out = null;
    try {
      out = new PrintWriter(new BufferedWriter(new
          OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
    } catch (Exception e) {
      throw new ServletException(e);
    }
    printHeader(request, out, tab);
    printTabs(out, "Command");
    out.println("<input type=\"hidden\" name=\"table_name\" value=\"" + table_name + "\">");

    printCommandInputArea(out, command, autocommit);
    printFooter(out, tab);
    out.close();
  }

  /**
   * �R�}���h���s���ʕ\�����
   * @param request Servlet.service��request
   * @param response Servlet.service��response
   */
  private void doCommandResult(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    String tab = "Result";
    String command = request.getParameter("command");
    if (command == null) {
      if (request.getParameter("export") != null) {
        command = "export";
      } else {
        command = "";
      }
    }
    // ����R�}���h�̏���(�ʏ�̎��s���ʉ�ʈȊO��\���������)
    if (command.toUpperCase().startsWith("ADD TABLE ")) {
      // �e�[�u���ꗗ�Ƀe�[�u��ID��ǉ�����i���^�f�[�^����擾�ł��Ȃ��Ώۂ𓯗l�Ɉ����p�j
      String t = command.substring(10).trim().toUpperCase();
      if (t.length() > 0) {
        this.addTables.add(t);
        doTables(request, response);
        return;
      }
    } else if (command.toUpperCase().startsWith("REMOVE TABLE ")) {
      // �e�[�u���ꗗ����e�[�u��ID���폜����
      String t = command.substring(13).trim().toUpperCase();
      if (t.length() > 0) {
        this.addTables.remove(t);
        doTables(request, response);
        return;
      }
    }
    
    // �ȉ��A���s���ʉ�ʂɕ\�����鏈��
    
    String table_name = request.getParameter("table_name");
    if (table_name == null) {
      table_name = "";
    }
    String textexport = request.getParameter("textexport");
    if (textexport == null) {
      textexport = "0";
    }
    String withinfo = request.getParameter("withinfo");
    if (withinfo == null) {
      withinfo = "0";
    }
    String filter = request.getParameter("filter");
    if (filter == null) {
      filter = "";
    }
    String autocommit = request.getParameter("autocommit");
    if (autocommit == null) {
      autocommit = "0";
    } else {
      autocommit = "1";
    }
    String datasource = request.getParameter("datasource");
    
    StringTokenizer st = new StringTokenizer(command);
    String cmd = "";
    if (st.hasMoreTokens()) {
      cmd = st.nextToken();
      if ("export".equals(cmd) && st.hasMoreTokens()) {
        table_name = st.nextToken();
      }
    }
    if (command != null && command.toUpperCase().startsWith("CSV/")) {
      doExportToCSV(response, command.substring(4));
      return;
    }
    
    response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
    PrintWriter out = null;
    try {
      out = new PrintWriter(new BufferedWriter(new
          OutputStreamWriter(response.getOutputStream(), DEFAULT_CHARSET)));
    } catch (Exception e) {
      throw new ServletException(e);
    }
    printHeader(request, out, tab);
    printTabs(out, "Result");
    
    //�R�}���h���s�i���s���ʉ�ʂ̕\���j

    // ����hidden�ɋL�����Ă���
    out.println("<input type=\"hidden\" name=\"table_name\" value=\"" + table_name + "\">");
    out.println("<input type=\"hidden\" name=\"textexport\" value=\"" + textexport + "\">");
    out.println("<input type=\"hidden\" name=\"withinfo\" value=\"" + withinfo + "\">");
    out.println("<input type=\"hidden\" name=\"command\" value=\"" + DbAccessUtils.escapeInputValue(command) + "\">");

    // ����R�}���h�̏���
    if (cmd.equalsIgnoreCase("help")) {
      // �w���v�\��
      printHelp(out, command);
    } else if (command.toUpperCase().startsWith("ADD DATASOURCE")) {
      // �f�[�^�\�[�X�̒ǉ�
      // add datasource name=jdbc/addds1;url=jdbc:oracle:thin:@127.0.0.1:1521:MBBDEMO;username=user;password=pass;driverClassName=oracle.jdbc.OracleDriver
      String param = command.substring(14).trim();
      LocalDataSource lds = new LocalDataSource(param);
      Connection conn = null;
      try {
        conn = lds.getConnection();
        addDataSource(lds.getDataSourceName(), lds, lds.getSchema(), lds.getDataSourceDispName());
      } catch (SQLException e) {
        printError(out, e);
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {}
        }
      }
      printHelp(out, command);
    } else if (command.toUpperCase().startsWith("REMOVE DATASOURCE")) {
      // �f�[�^�\�[�X�̍폜
      // remove datasource <name>
      String name = command.substring(17).trim();
      removeDataSource(name);
      printHelp(out, command);
    } else if (cmd.equalsIgnoreCase("check")) {
      // �`�F�b�N
      printCheck(out, request, command);
    } else if (cmd.equalsIgnoreCase("company")) {
      // ���ݒ�
      printCompany(out, command);
    } else if (cmd.equalsIgnoreCase("compare")) {
      // ��DB�Ƃ̔�r
      printCompare(out, command, datasource);
    } else if (cmd.equalsIgnoreCase("copy")) {
      // �f�[�^�R�s
      printCopyTableData(out, command);
    } else if (cmd.equalsIgnoreCase("count")) {
      // �J�E���g
      printCount(out, command);
    } else if (cmd.equalsIgnoreCase("desc")) {
      // �e�[�u����`�̕\��
      printDesc(out, command);
    } else if (cmd.equalsIgnoreCase("replace") || cmd.toLowerCase().startsWith("replace/")
            || cmd.equalsIgnoreCase("grep") || cmd.toLowerCase().startsWith("grep/")
            || cmd.equalsIgnoreCase("grepm") || cmd.toLowerCase().startsWith("grepm/")
            ) {
        // �����^�u��
        printFindReplace(out, command, 0);
    } else if (cmd.equalsIgnoreCase("find") || cmd.equalsIgnoreCase("findm")
        || cmd.startsWith("find/") || cmd.startsWith("findm/")) {
      // �B������
      printFindReplace(out, command, 1);
    } else if (cmd.equalsIgnoreCase("ddl") && command.toLowerCase().startsWith("ddl to ")) {
      // ddl file export
      printDDLExportToFile(out, command);
    } else if (cmd.equalsIgnoreCase("sql")) {
      // sql
      printSQL(out, command);
    } else if (cmd.equalsIgnoreCase("export")) {
      // �G�N�X�|�[�g����
      if (command.toLowerCase().startsWith("export to ")) {
        // file export
        printExportToFile(out, command, table_name);
      } else {
        out.println("<textarea cols=\"80\" rows=\"20\">");
        printExport(out, table_name, textexport, withinfo, filter);
        out.println("</textarea>");
      }
    } else if (cmd.equalsIgnoreCase("import") || cmd.toLowerCase().startsWith("import/r")) {
      // �C���|�[�g����
      if (command.toLowerCase().startsWith("import from ")) {
        // file import
        printImportFromFile(out, command.substring(12), autocommit, true);
      } else if (command.toLowerCase().startsWith("import append from ")) {
        // file import
        printImportFromFile(out, command.substring(19), autocommit, false);
      } else {
        // �ʏ��import����
        printImport(out, command, autocommit);
      }
    } else if (cmd.equalsIgnoreCase("show") || cmd.equalsIgnoreCase("clear") ) {
      // ���O�ɑ΂��鑀��
      printLog(out, command);
    } else if (cmd.equalsIgnoreCase("restart")) {
      // ���X�^�[�g�R�}���h���s
      printRestart(out, command);
    } else if (command.length() > 0) {
      // �ʏ�SQL���s
      printExecuteSQL(out, request, command, false, null, null, autocommit, "0", null);
    }
    
    printFooter(out, tab);
    out.close();
  }

  /**
   * MBB���j���[
   * @param out
   * @param request
   */
  private void doMBB(HttpServletRequest request, HttpServletResponse response, String loginMode) throws ServletException {
    String tab = "MBB";
    response.setContentType("text/html; charset=\"" + DEFAULT_CHARSET + "\"");
    OutputStream os = null;
    PrintWriter out = null;
    try {
      String mbbmenu = request.getParameter("mbbmenu");
      String command = request.getParameter("command");
      if ("scan all".equals(command)) {
        String acceptedEncodings = request.getHeader("accept-encoding");
        if (acceptedEncodings != null && acceptedEncodings.indexOf("gzip") != -1) {
          // gzip�Ή��̏ꍇ�AGZIPOutputStream�ň��k���ĕԂ�
          response.setHeader("Content-Encoding", "gzip");
          os = new GZIPOutputStream(response.getOutputStream());
          log("gzip");
        }
      }
      if (os == null) {
        os = response.getOutputStream();
      }
      
      out = new PrintWriter(new BufferedWriter(new
          OutputStreamWriter(os, DEFAULT_CHARSET)));

      printHeader(request, out, tab);
      
      if (command != null && command.trim().length() > 0) {
        String cmd = new StringTokenizer(command).nextToken();
        if (cmd.equals("config")) {
          mbbmenu = "CONFIG";
        } else if (cmd.equals("scan")) {
          mbbmenu = "SCAN";
        }
      }
      boolean noTab = false;
      if ("COMPARE".equals(mbbmenu) && command != null && command.startsWith("compare ")) {
        // ���̃P�[�X�̓|�b�v�A�b�v�\��
        noTab = true;
      }

      // �\�����j���[�̑I��
      Vector menus = userMenus;
      if (loginMode != null && loginMode.equals(ADMIN_MODE) && !noTab) {
        printTabs(out, "MBB");
        menus = adminMenus;
      } else {
        out.println("<input type=\"hidden\" name=\"tab\" value=\"MBB\">");
      }
      
      // �����^�G�N�X�|�[�g���j���[
      String[][] expmenu = {
          {"�@�\�}�X�^", "FUNCTIONMASTER", "FUNCTIONID", "�@�\ID"},
          {"�e�[�u���}�X�^", "TABLEMASTER", "TABLEID", "�e�[�u��ID"},
          {"�f�[�^�t�B�[���h�}�X�^", "DATAFIELDMASTER", "DATAFIELDID", "�f�[�^�t�B�[���hID"},
          {"�p�b�P�[�W�}�X�^", "PACKAGEMASTER", "PACKAGEID", "�p�b�P�[�WID"},
          {"�N���X�^�C�v�}�X�^", "CLASSTYPEMASTER", "CLASSTYPE", "�N���X�^�C�v"},
          {"�v���Z�X�}�X�^", "PROCESSMASTER", "PROCESSID", "�v���Z�XID", },
          {"�y�[�W�}�X�^", "PAGEMASTER", "PAGEID", "�y�[�WID"},
          {"�A�v���P�[�V�����}�X�^", "APPLICATIONMASTER", "APPLICATIONID", "�A�v���P�[�V����ID"},
          {"���b�Z�[�W�}�X�^", "MESSAGEMASTER", "MESSAGEID", "���b�Z�[�WID"},
          {"���j���[�}�X�^", "MENUMASTER", "MENUID", "���j���[ID"},
          {"���j���[�A�C�e���}�X�^", "MENUITEMMASTER", "MENUITEMID", "���j���[�A�C�e��ID"},
        };
      
      if (mbbmenu == null) {
        // ���j���[�A�C�e�����I���i���j���[�\���j
        out.print("<table>");
        for (Iterator ite = menus.iterator(); ite.hasNext(); ) {
          String menuItem = (String)ite.next();
          if (menuItem.equals("CONFIG")) {
            doPrintMBBMenu(out, "CONFIG", (String)mbbMenus.get("CONFIG"));
          } else if (menuItem.equals("FUNCTION")) {
            doPrintMBBMenu(out, "FUNCTION", (String)mbbMenus.get("FUNCTION"));
          } else if (menuItem.equals("TABLE")) {
            doPrintMBBMenu(out, "TABLE", (String)mbbMenus.get("TABLE"));
          } else if (menuItem.equals("IMPORT")) {
            doPrintMBBMenu(out, "IMPORT&upload=1", (String)mbbMenus.get("IMPORT"));
          } else if (menuItem.equals("SCAN")) {
            doPrintMBBMenu(out, "SCAN", (String)mbbMenus.get("SCAN"));
          }
          //doPrintMBBMenu(out, "CLASS", "�N���X�t�@�C��", loginMode);
          else if (menuItem.endsWith("MASTER")) {
            // �����^�G�N�X�|�[�g���j���[
            String[] item = null;
            for (int i = 0; i < expmenu.length; ++i) {
              if (menuItem.equals(expmenu[i][1])) {
                item = expmenu[i];
                break;
              }
            }
            if (item != null) {
              doPrintMBBMenu(out, item[1], (String)mbbMenus.get(item[1]));
            }
          } else if (menuItem.equals("LOGOUT")) {
            doPrintMBBMenu(out, "LOGOUT&logout=1", (String)mbbMenus.get("LOGOUT"));
          }
        }
        out.print("</table>");
        out.flush();
      } else if (mbbmenu.equalsIgnoreCase("CONFIG")) {
        // config�R�}���h���s
        printMBBConfig(out, request);
      } else if (mbbmenu.equalsIgnoreCase("SCAN")) {
        // �t�@�C���X�L����
        printMBBScanModules(out, request);
      } else if (mbbmenu.equalsIgnoreCase("COMPARE")) {
        // ���W���[����r
        printMBBCompareModule(out, request);
      } else if (mbbmenu.equalsIgnoreCase("FUNCTION")) {
        printMBBFunctions(out, request);
      } else if (mbbmenu.equalsIgnoreCase("TABLE")) {
        printMBBTables(out, request);
      } else if (mbbmenu.equalsIgnoreCase("CLASS")) {
        // TODO: ������
        printMBBClass(out, request);
      } else if (mbbmenu.equalsIgnoreCase("IMPORT")) {
        printImportUploadedFiles(out, request);
      } else {
        for (int i = 0; i < expmenu.length; ++i) {
          String[] item = expmenu[i];
          if (mbbmenu.equalsIgnoreCase(item[1])) {
            printMBBSearchExport(out, request, item[0] + "(�����^�G�N�X�|�[�g)", item[1], item[2], item[3]);
          }
        }
      }
      printFooter(out, tab);
      
    } catch (Exception e) {
      throw new ServletException(e);
    } finally {
      if (out != null) {
        try {
          out.flush();
          if (os instanceof GZIPOutputStream) {
            try {
              ((GZIPOutputStream)os).finish();
            } catch (IOException e) {
              log("", e);
            }
          }
          out.close();
        } catch (Exception e) {
          log("", e);
        }
      }
    }
  }
  
  private void doPrintMBBMenu(PrintWriter out, String menuId, String name) {
    String tmpMenuId = menuId;
    if (tmpMenuId.indexOf("&") != -1) {
      tmpMenuId = tmpMenuId.substring(0, tmpMenuId.indexOf("&"));
    }
    out.print("<tr><td><a href=\"?tab=MBB&mbbmenu=" + menuId + "\">" + name + "</a></td></tr>");
  }
  
  /**
   * �t�@�C���C���|�[�g
   * @param out
   * @param request
   */
  private void printImportUploadedFiles(PrintWriter out, HttpServletRequest request) {
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"IMPORT\">");
    out.println("<input type=\"hidden\" name=\"upload\" value=\"1\">");
    out.println("<table>");
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>�t�@�C���C���|�[�g</td></tr>");
    out.println("</table>");
    try {
      String fileName = request.getParameter("uploadfile");
      String[] files = request.getParameterValues("file"); // ���̉�ʂ��߂��Ă����ꍇ�ɑI��Ώۂ��i�[�����
      Vector filesArray = new Vector();
      if (files != null) {
        for (int i = 0; i < files.length; ++i) {
          filesArray.add(files[i]);
        }
      }
      boolean commit = request.getParameter("commit") != null;
      boolean cancel = request.getParameter("cancel") != null;
      boolean confirm = request.getParameter("doconfirm") != null;
      boolean checkonly = request.getParameter("checkonly") != null;
      boolean createtable = request.getParameter("createtable") != null;
      if (createtable) {
        String[] tables = request.getParameterValues("table");
        if (tables != null && tables.length > 0) {
          out.println("<pre>");
          Connection conn = getConnection();
          conn.setAutoCommit(false);
          ClassManager classManager = new ClassManager(appPath);
          for (int i = 0; i < tables.length; ++i) {
            if (tables[i].startsWith("V") && tables[i].indexOf("_") != -1) {
              // V����n�܂�_���܂܂��ꍇ��VIEW�Ɣ��f���ăX�L�b�v����
              out.println("�e�[�u����`[" + tables[i] + "]�̍č\�z�̓X�L�b�v���܂����B\n");
              continue;
            }
            StringBuffer emsg = new StringBuffer();
            int[] err = checkTableLayout(classManager, conn, tables[i], null); // �����e�[�u���Ɣ�r
            if (createTableFromTableLayoutMaster(conn, tables[i], tables[i], emsg, getLoginInfos(request))) {
              out.println("�e�[�u��[" + tables[i] + "]���č\�z���܂����B\n");
            } else {
              out.println("<font color=\"" + ERROR_COLOR + "\">�e�[�u��[" + tables[i] + "]�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font>\n");
            }
            if (err[1] == 1 || err[1] == 2) {
              // ���̃e�[�u�������݂��Ȃ����ύX�̂���ꍇ
              if (createTableFromTableLayoutMaster(conn, tables[i], DbAccessUtils.getNameTableName(tables[i]), emsg, getLoginInfos(request))) {
                out.println("�e�[�u��[" + DbAccessUtils.getNameTableName(tables[i]) + "]���č\�z���܂����B\n");
              } else {
                out.println("<font color=\"" + ERROR_COLOR + "\">�e�[�u��[" + DbAccessUtils.getNameTableName(tables[i]) + "]�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font>\n");
              }
            }
            if (err[2] == 1 || err[2] == 2) {
              // ���e�[�u�������݂��Ȃ����ύX�̂���ꍇ
              if (createTableFromTableLayoutMaster(conn, tables[i], DbAccessUtils.getInfoTableName(tables[i]), emsg, getLoginInfos(request))) {
                out.println("�e�[�u��[" + DbAccessUtils.getInfoTableName(tables[i]) + "]���č\�z���܂����B\n");
              } else {
                out.println("<font color=\"" + ERROR_COLOR + "\">�e�[�u��[" + DbAccessUtils.getInfoTableName(tables[i]) + "]�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font>\n");
              }
            }
            
          }
          out.println("</pre>");
          conn.close();
        }
        cancel = true;
      }
      if (request.getParameter("import") != null) {
        // �`�F�b�N��ʂŃC���|�[�g���s���������ꍇ
        checkonly = false;
        commit = true;
      }
      File tempFile = null;
      String tempFileName = request.getParameter("tempfile");
      if (tempFileName != null) {
        tempFile = (File)tempFiles.get(tempFileName);
      } else if (fileName != null && fileName.trim().length() > 0) {
        String uploadFileName = (String)request.getAttribute(fileName);
        if (uploadFileName != null) {
          tempFile = (File)tempFiles.get(uploadFileName);
        }
      }
      if (cancel) {
        deleteTempFile(tempFile);
        setSessionObject(request, "importTables", null);
        tempFile = null;
      }
      if (tempFile == null) {
        // �t�@�C���A�b�v���[�h�O
        out.print("<table>");
        out.print("<tr><td><nobr>");
        out.println("�A�b�v���[�h�t�@�C���F<input type=\"file\" name=\"uploadfile\" size=\"50\">");
        //out.println("<input type=\"submit\" name=\"commit\" value=\"���s\">");
        out.println("<input type=\"submit\" name=\"doconfirm\" value=\"�m�F\">");
        out.print("</nobr></td></tr>");
        out.println("</table>");
      } else {
        Connection conn = null;
        try {
          // �A�b�v���[�h�t�@�C��������ꍇ
          if (fileName == null && confirm) {
            fileName = tempFile.getName();
          }
          out.println("<input type=\"hidden\" name=\"tempfile\" value=\"" + tempFile.getName() + "\">");
          String chkstyle = " style=\"width:40px;\"";
          String tsstyle = " style=\"width:160px;\"";
          if (fileName != null && (confirm || fileName.toLowerCase().endsWith(".jar") || fileName.toLowerCase().endsWith(".zip"))) {
            // �m�F���[�h(�g���q��jar,zip�̏ꍇ�͋����m�F)
            conn = getConnection();
            conn.setAutoCommit(false);
            int chkcount = 0;
            boolean xlsData = false; // xls�f�[�^�C���|�[�g�p�t���O
            String fname = new File(fileName).getName();
            if (fileName.toLowerCase().endsWith(".jar") || fileName.toLowerCase().endsWith(".zip")) {
              // �t�@�C����jar/zip�̏ꍇ�́A�G���g���ꗗ��\��
              out.println("<table><tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\"><td colspan=\"2\"><input type=\"checkbox\" onclick=\"checkAll('file', this.checked);\">�S��&nbsp;&nbsp;[" + fname + "]</td><td" + tsstyle + ">�ŏI�X�V����(�t�@�C��)</td><td" + tsstyle + ">�ŏI�X�V����(�f�[�^�x�[�X)</td></tr>");
              String zename = null;
              try {
                ZipFile zip = new ZipFile(tempFile, ZipFile.OPEN_READ);
                Hashtable tableParams = new Hashtable();
                for (Enumeration enu = zip.entries(); enu.hasMoreElements(); ) {
                  ZipEntry ze = (ZipEntry)enu.nextElement();
                  String[] params = null;
                  zename = ze.getName();
                  String dbts = null;
                  String ts = null;
                  String check = "";
                  String check_checked = "";
                  String errmsg = "";
                  boolean delfile = false;
                  boolean modfile = false;
                  
                  if (zename.toLowerCase().endsWith(".csv.del")) {
                    delfile = true;
                  }
                  if (zename.startsWith("update/") && !zename.endsWith("/")) {
                    // update���W���[���̏ꍇ
                    String zfname = tempFile.getName() + "!" + zename;
                    check = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                    check_checked = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                    modfile = true;
                    ++chkcount;
                  } 
                  //2014/04/24 module �Ή� start
                  else if (zename.startsWith("module/")) {
                    // module�ꍇ
                    if(!zename.endsWith("/")){
                      String zfname = tempFile.getName() + "!" + zename;
                      check = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                      check_checked = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                      modfile = true;
                      ++chkcount;
                    }else{
                      //�t�H���_�͔�\��
                      continue;
                    }
                  } 
                  //2014/04/24 module �Ή� end
                  else if (zename.toLowerCase().endsWith(".csv") || delfile) {
                    try {
                      params = getCSVInfos(conn, zip.getInputStream(ze), tableParams);
                    } catch (IOException e) {
                      errmsg = "&nbsp;<font color=\"" + ERROR_COLOR + "\">(" + e.getMessage() + ")</font>";
                    }
                    String zfname = tempFile.getName() + "!" + zename;
                    if (filesArray.contains(zfname)) {
                      // �f�t�H���g�I��Ώ�(�O��ʂőI������Ă���)
                      check = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\" checked>";
                      check_checked = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\" checked>";
                    } else {
                      if (filesArray.size() > 0) {
                        // �f�t�H���g��I��Ώ�(�O��ʂŔ�I������Ă���)
                        check = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                        check_checked = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                      } else {
                        // �f�t�H���g(�������)
                        check = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\">";
                        check_checked = "&nbsp;&nbsp;<input type=\"checkbox\" name=\"file\" value=\"" + zfname + "\" checked>";
                      }
                    }
                    ++chkcount;
                  }
                  String dbuuser = "";
                  if (params != null) {
                    ts = params[3]; // TIMESTAMPVALUE
                    if (params[1] != null &&  params[2] != null) {
                      String[] recordUpdateInfos = getRecordUpdateInfo(conn, params[0], params[1].split(",", -1), params[2]);
                      if (recordUpdateInfos != null && recordUpdateInfos.length > 3) {
                        dbts = recordUpdateInfos[3];
                        dbuuser = recordUpdateInfos[0] + "," + recordUpdateInfos[1] + ",";
                      } else {
                        dbts = getTimestamp(conn, params[0], params[1].split(",", -1), params[2]);
                      }
                    }
                  } else {
                    try {
                      ts = new Timestamp(ze.getTime()).toString();
                    } catch(Exception e) {}
                  }
                  String dispts = DbAccessUtils.getDispTimestamp(ts);
                  if (dispts.length() == 0) {
                    dispts = "(�s��)";
                  }
                  if (dbts == null || params == null) {
                    // DB�ɑ��݂��Ȃ��ꍇ�܂��͑ΏۊO�t�@�C���̏ꍇ
                    if (delfile) {
                      // �폜�̏ꍇ
                      out.println("<tr><td" + chkstyle + "></td><td>" + zename + errmsg + "</td><td>(�폜)</td><td></td></tr>");
                    } else if (modfile) {
                      // ���W���[���t�@�C���̏ꍇ
                      out.println("<tr><td" + chkstyle + ">" + check_checked + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_NEWER_COLOR + ";\">" + dispts + "</td><td></td></tr>");
                    } else if (params == null) {
                      // �ΏۊO�t�@�C���̏ꍇ�̓`�F�b�N�{�b�N�X�Ȃ�
                      out.println("<tr><td" + chkstyle + "></td><td>" + zename + errmsg + "</td><td>" + dispts + "</td><td></td></tr>");
                    } else {
                      // DB�ɑ��݂��Ȃ��ꍇ�i�V�K�H�j
                      out.println("<tr><td" + chkstyle + ">" + check_checked + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_NEWER_COLOR + ";\">" + dispts + "</td><td>N/A</td></tr>");
                    }
                  } else {
                    String dispdbts = DbAccessUtils.getDispTimestamp(dbts);
                    int cmp = dispts.compareTo(dispdbts);
                    if (delfile) {
                      // �폜�̏ꍇ
                      String tmpcheck = check_checked;
                      String delcolor = DIFF_NEWER_COLOR;
                      int exists = 0;
                      if (zename.toLowerCase().startsWith("datafield/")) {
                        // �e�[�u���폜�̏ꍇ�͎��������������Ń`�F�b�N
                        exists = 1;
                      }
                      if (params != null && params[0].equals("DATAFIELDMASTER") && isDataFieldExists(conn, params[2], exists)) {
                        // �Ώۂ��f�[�^�t�B�[���h���e�[�u�����C�A�E�g�Ŏg�p���̏ꍇ
                        tmpcheck = check;
                        delcolor = ERROR_COLOR;
                      }
                      out.println("<tr><td" + chkstyle + ">" + tmpcheck + "</td><td>" + zename + "</td><td style=\"color:" + delcolor + ";\">(�폜)</td><td>" + dbuuser + dispdbts + "</td></tr>");
                    } else if (cmp == 0 || dispts.startsWith("(")) {
                      // �����܂��͕s��
                      out.println("<tr><td" + chkstyle + ">" + check + "</td><td>" + zename + "</td><td>" + dispts + "</td><td>" + dbuuser + dispdbts + "</td></tr>");
                    } else if (cmp > 0) {
                      // IMPORT�t�@�C���̕����V����
                      out.println("<tr><td" + chkstyle + ">" + check_checked + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_NEWER_COLOR + ";\">" + dispts + "</td><td>" + dbuuser + dispdbts + "</td></tr>");
                    } else {
                      // DB�̕����V����
                      out.println("<tr><td" + chkstyle + ">" + check + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_OLDER_COLOR + ";\">" + dispts + "</td><td>" + dbuuser + dispdbts + "</td></tr>");
                    }
                  }
                }
                zip.close();
              } catch (IOException e) {
                printError(out, "[" + tempFile.getAbsolutePath() + " : " + zename + "]");
                printError(out, e);
              }
            } else {
              // fileName��.jar,.zip�ȊO
              try {
                Hashtable tableParams = new Hashtable();
                String[] params = null;
                out.println("<table><tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\"><td colspan=\"2\">[" + fname + "]</td><td" + tsstyle + ">�ŏI�X�V����(�t�@�C��)</td><td" + tsstyle + ">�ŏI�X�V����(�f�[�^�x�[�X)</td></tr>");
                String name = tempFile.getName();
                String zename = new File(fileName).getName();
                String dbts = null;
                String ts = null;
                String check = "";
                String check_checked = "";
                boolean delfile = false;
                if (zename.toLowerCase().endsWith(".csv.del")) {
                  delfile = true;
                }
                if (zename.toLowerCase().endsWith(".csv") || delfile) {
                  params = getCSVInfos(conn, new FileInputStream(tempFile), tableParams);
                  check = "<input type=\"checkbox\" name=\"file\" value=\"" + name + "\">";
                  check_checked = "<input type=\"checkbox\" name=\"file\" value=\"" + name + "\" checked>";
                  ++chkcount;
                } else if (zename.toLowerCase().endsWith(".xls")) {
                  check = "<input type=\"checkbox\" name=\"file\" value=\"" + name + "\">";
                  ++chkcount;
                  xlsData = true;
                }
                if (params != null) {
                  ts = params[3];
                  if (params[1] != null &&  params[2] != null) {
                    String values = null;
                    if (params[4] != null) {
                      values = params[4] + "," + params[2];
                    } else {
                      values = params[2];
                    }
                    dbts = getTimestamp(conn, params[0], shiftArray(params), values);
                  }
                }
                String dispts = DbAccessUtils.getDispTimestamp(ts);
                if (dispts.length() == 0) {
                  dispts = "(�s��)";
                }
                if (dbts == null || params == null) {
                  // DB�ɑ��݂��Ȃ�(�V�K)�ꍇ�܂��͑ΏۊO�t�@�C���̏ꍇ
                  if (delfile) {
                    // �폜�̏ꍇ
                    out.println("<tr><td" + chkstyle + "></td><td>" + zename + "</td><td>(�폜)</td><td></td></tr>");
                  } else if (params == null) {
                    if (zename.toLowerCase().endsWith(".xls")) {
                      // EXCEL�̏ꍇ�̓`�F�b�N�{�b�N�X�\��
                      out.println("<tr><td" + chkstyle + ">" + check + "</td><td>" + zename + "</td><td></td><td></td></tr>");
                    } else {
                      // ����O�t�@�C���̏ꍇ�̓`�F�b�N�{�b�N�X�Ȃ��i���Ή��j
                      out.println("<tr><td" + chkstyle + "></td><td>" + zename + "</td><td>" + dispts + "</td><td></td></tr>");
                    }
                  } else {
                    // DB�ɑ��݂��Ȃ��ꍇ�i�V�K�H�j
                    out.println("<tr><td" + chkstyle + ">" + check_checked + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_NEWER_COLOR + ";\">" + dispts + "</td><td>N/A</td></tr>");
                  }
                } else {
                  String dispdbts = DbAccessUtils.getDispTimestamp(dbts);
                  int cmp = dispts.compareTo(dispdbts);
                  if (delfile) {
                    // �폜�̏ꍇ
                    String tmpcheck = check_checked;
                    String delcolor = "#0000ff";
                    if (params != null && params[0].equals("DATAFIELDMASTER") && isDataFieldExists(conn, params[2], 0)) {
                      // �Ώۂ��f�[�^�t�B�[���h���e�[�u�����C�A�E�g�Ŏg�p���̏ꍇ
                      tmpcheck = check;
                      delcolor = ERROR_COLOR;
                    }
                    out.println("<tr><td" + chkstyle + ">" + tmpcheck + "</td><td>" + zename + "</td><td style=\"color:" + delcolor + ";\">(�폜)</td><td>" + dispdbts + "</td></tr>");
                  } else if (cmp == 0 || dispts.startsWith("(")) {
                    // �����܂��͕s��
                    out.println("<tr><td" + chkstyle + ">" + check + "</td><td>" + zename + "</td><td>" + dispts + "</td><td>" + dispdbts + "</td></tr>");
                  } else if (cmp > 0) {
                    // IMPORT�t�@�C���̕����V����
                    out.println("<tr><td" + chkstyle + ">" + check_checked + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_NEWER_COLOR + ";\">" + dispts + "</td><td>" + dispdbts + "</td></tr>");
                  } else {
                    // DB�̕����V����
                    out.println("<tr><td" + chkstyle + ">" + check + "</td><td>" + zename + "</td><td style=\"color:" + DIFF_OLDER_COLOR + ";\">" + dispts + "</td><td>" + dispdbts + "</td></tr>");
                  }
                }
              } catch (IOException e) {
                out.println("<tr><td colspan=\"2\"><font color=\"" + ERROR_COLOR + "\">" + e.getMessage() + "</font></td><td></td><td></td></tr>");
                chkcount = 0;
              }
            }
            out.println("</table>");
            out.println("<input type=\"submit\" name=\"cancel\" value=\"�L�����Z��\">");
            if (chkcount > 0) {
              out.println("<input type=\"submit\" name=\"commit\" value=\"���s\">");
              out.print("<input type=\"checkbox\" name=\"checkonly\" value=\"1\"");
              if (checkonly) {
                out.print(" checked");
              }
              out.print("><span class=\"text\">�`�F�b�N�̂�(");
              if (!dataFieldIdCheck) {
                out.print("�N���X�^�C�v�E�p�b�P�[�W�̑��݃`�F�b�N");
              } else {
                out.print("�f�[�^�t�B�[���h�E�N���X�^�C�v�E�p�b�P�[�W�̑��݃`�F�b�N");
              }
              out.println(")</span>");
              if (xlsData) {
                out.print("<span class=\"text\">&nbsp;�u��:</span>");
                out.print("<select name=\"replace\"><option value=\"update\">�X�V</option><option value=\"insert\">�ǉ�</option><option value=\"replace\">�S�u��</option></select>");
              }
            } else {
              out.println("<input type=\"submit\" name=\"cancel\" value=\"���s\" disabled>");
            }
          } else if (commit) {
            // import���s
            if (tempFile.exists()) {
              if (checkonly) {
                out.println("<span class=\"text\">���`�F�b�N�̂ݎ��s�F</span><br>");
                out.println("<input type=\"hidden\" name=\"checkonly\" value=\"1\">");
              }
              int r = printImportUploadedExec(out, request, checkonly);
              if (checkonly) {
                out.println("<input type=\"submit\" name=\"cancel\" value=\"�L�����Z��\" title=\"�t�@�C���A�b�v���[�h��ʂ֖߂�܂�\">");
                out.println("<input type=\"submit\" name=\"doconfirm\" value=\"�߂�\">");
                out.println("<input type=\"submit\" name=\"import\" value=\"�C���|�[�g���s\">");
              } else {
                out.println("<input type=\"submit\" name=\"cancel\" value=\"�I��\" title=\"�t�@�C���A�b�v���[�h��ʂ֖߂�܂�\">");
                out.println("<input type=\"submit\" name=\"doconfirm\" value=\"�ΏۍđI��\">");
                if (r == 1) {
                  // �����e�[�u���č\�z
                  out.println("<input type=\"submit\" name=\"createtable\" value=\"�e�[�u���č\�z\" onclick=\"return confirm('�����e�[�u�����č\�z���܂��B���C�A�E�g�̌݊��������ꍇ�́A�Ώۃe�[�u���̃f�[�^�͑S�ď�������܂�����낵���ł���?');\">");
                }
              }
            } else {
              // �A�b�v���[�h�t�@�C�������݂��Ȃ��ꍇ
              out.println("<table>");
              out.println("<tr><td colspan=\"3\">�A�b�v���[�h�t�@�C�����ǂݍ��߂܂���ł����B</td></tr>");
              out.println("</table>");
              out.println("<input type=\"submit\" name=\"cancel\" value=\"�L�����Z��\">");
            }
          }
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException e) {}
          }
        }
      }
      out.flush();
    } catch(Exception e) {
      printError(out, e);
    }
  }
  /**
   * �f�[�^�t�B�[���h���������݂��邩�`�F�b�N
   * @param conn
   * @param zename
   * @return true:2�ȏ㑶�݁Afalse:1����
   */
  private boolean isDataFieldExists(Connection conn, String zename, int exists) {
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      String dfid = zename.toUpperCase().substring(zename.indexOf("/") + 1);
      if (dfid.indexOf(".") != -1) {
        dfid = dfid.substring(0, dfid.indexOf("."));
      }
      //�e�[�u�����C�A�E�g�Ńt�B�[���hID���g�p����Ă��邩�`�F�b�N
      stmt = conn.prepareStatement("SELECT COUNT(DATAFIELDID) FROM TABLELAYOUTMASTER WHERE DATAFIELDID=?");
      stmt.setString(1, dfid);
      rs = stmt.executeQuery();
      if (rs.next()) {
        int cnt = rs.getInt(1);
        if (cnt > exists) {
          return true;
        }
      }
      rs.close();
      stmt.close();
      //ITEMDEFINITIONMASTER�Ńt�B�[���hID���g�p����Ă��邩�`�F�b�N
      stmt = conn.prepareStatement("SELECT COUNT(FIELDID) FROM ITEMDEFINITIONMASTER WHERE FIELDID=?");
      stmt.setString(1, dfid);
      rs = stmt.executeQuery();
      if (rs.next()) {
        int cnt = rs.getInt(1);
        if (cnt > 0) {
          return true;
        }
      }
      
    } catch(SQLException e) {
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {}
      }
    }
    return false;
  }
  
  /**
   * CSV�t�@�C���̏����擾����i�ŏ��̃e�[�u����1���R�[�h�ڂ̂݁j
   * @param is CSV�t�@�C����InputStream
   * @param tableParamsCache �ė��p����e�[�u���p�����[�^(getRelationParams()�̃L���b�V��)
   * @return String[] // TABLEID, KEYFIELDID, KEYVALUE, TIMESTAMP
   * @throws IOException
   */
  private String[] getCSVInfos(Connection conn, InputStream is, Hashtable tableParamsCache) throws IOException {
    String[] infos = new String[5]; // TABLEID, KEYFIELDID, KEYVALUE, TIMESTAMP, [COMPANYID]
    BufferedReader br = new BufferedReader(new InputStreamReader(is, DEFAULT_CHARSET));
    String tableName = null;
    String columnNames = null;
    String firstData = null;
    String line = null;
    int lineNo = 0;
    while ((line = br.readLine()) != null) {
      ++lineNo;
      if (isTableMCSVLine(line)) {
        if (tableName != null) {
          break;
        }
        tableName = getTableNameFromMCSVLine(line);
        continue;
      }
      if (tableName == null) {
        throw new IOException("�T�|�[�g����Ă��Ȃ�CSV�`���ł��B");
      }
      while (!isCSVLineComplete(line)) {
        String nextLine = br.readLine();
        ++lineNo;
        if (nextLine == null) {
          throw new IOException("�s���ȍs�f�[�^�����o���܂����B[" + tableName + "][�sNo=" + lineNo + "]");
        }
        line = line + EOL + nextLine;
      }
      if (columnNames == null) {
        columnNames = line;
      } else {
        firstData = line;
        break;
      }
    }
    if (tableName == null || columnNames == null || firstData == null) {
      throw new IOException("�T�|�[�g����Ă��Ȃ�CSV�`���ł��B");
    }
    infos[0] = tableName;
    Vector fieldSet = getFieldSet(columnNames, firstData);
    Vector tp = (Vector)tableParamsCache.get(tableName.toUpperCase());
    if (tp == null) {
      tp = getRelationParams(conn, tableName);
      if (tp != null) {
        // �擾�ł����ꍇ�́A����ė��p���邽�߂�tableParams�ɒǉ�
        tableParamsCache.put(tableName.toUpperCase(), tp);
      }
    }
    if (tp != null && tp.size() > 0 && ((String[])tp.get(0)).length > 1) {
      String[] tinfo = (String[])tp.get(0);
      StringBuffer keys = new StringBuffer();
      for (int i = 1; i < tinfo.length - 1; ++i) {
        if (i > 1) {
          keys.append(",");
        }
        keys.append(tinfo[i]);
      }
      infos[1] = keys.toString();
      StringBuffer values = new StringBuffer();
      infos[3] = "";
      for (int j = 0; j < fieldSet.size(); ++j) {
        String[] field = (String[])fieldSet.get(j);
        if ("TIMESTAMPVALUE".equalsIgnoreCase(field[0])) {
          infos[3] = field[1];
          break;
        }
      }
      for (int i = 1; i < tinfo.length - 1; ++i) {
        String key = tinfo[i];
        String value = "";
        for (int j = 0; j < fieldSet.size(); ++j) {
          String[] field = (String[])fieldSet.get(j);
          if (key.equalsIgnoreCase(field[0])) {
            value = field[1];
            break;
          }
        }
        if (i > 1) {
          values.append(",");
        }
        values.append(value);
      }
      infos[2] = values.toString();
    }
    return infos;
  }
  
  /**
   * �X�V����Ԃ�
   * @param request
   * @return String[] 0:REMOTEADDR,1:COMPANYID,2:USERID,3:TIMESTAMP(null)
   */
  private String[] getLoginInfos(HttpServletRequest request) {
    String[] updateInfos = new String[4];
    updateInfos[0] = request.getRemoteAddr();
    HttpSession session = request.getSession(false);
    if (session == null) {
      // �Z�b�V����������Ă��Ȃ��ꍇ�H
      updateInfos[1] = "(NOLOGIN)";
      updateInfos[2] = "(NOLOGIN)";
    } else {
      Map sessionData = (Map)session.getAttribute("SESSIONDATA");
      if (sessionData == null) {
        updateInfos[1] = "(NOLOGIN)";
        updateInfos[2] = "(NOLOGIN)";
      } else {
        String companyId = (String)sessionData.get("COMPANYID");
        String userId = (String)sessionData.get("USERID");
        if (companyId != null && companyId.trim().length() > 0) {
          updateInfos[1] = companyId;
        } else {
          updateInfos[1] = "(NOLOGIN)";
        }
        if (userId != null && userId.trim().length() > 0) {
          updateInfos[2] = userId;
        } else {
          updateInfos[2] = "(NOLOGIN)";
        }
      }
    }
    return updateInfos;
  }
  
  /**
   * �t�@�C���C���|�[�g�̎��s
   * @param out
   * @param request
   */
  private int printImportUploadedExec(PrintWriter out, HttpServletRequest request, boolean checkonly) {
    String[] files = request.getParameterValues("file");
    String[] loginInfos = getLoginInfos(request);
    String replace = request.getParameter("replace");
    Hashtable importTables = (Hashtable)getSessionObject(request, "importTables");
    if (importTables == null) {
      importTables = new Hashtable();
      setSessionObject(request, "importTables", importTables);
    }

    int ret = 0;
    int mode = IMPORT_NORMAL; // 0:�C���|�[�g�A1:�폜�A2:�`�F�b�N�̂�
    if (checkonly) {
      mode = IMPORT_CHECK;
    }
    out.println("<table>");
    if (files != null) {
      long timestamp = System.currentTimeMillis();
      ZipFile zip = null;
      Connection conn = null;
      int count = 0;
      boolean hasErrors = false;
      for (int i = 0; i < files.length; ++i) { // HTTP�p�����[�^�Ŏw�肳�ꂽ�S�Ă�file�ɑ΂��J��Ԃ�
        String name = files[i];
        InputStream is = null;
        StringBuffer msg = new StringBuffer();
        try {
          int p = name.indexOf("!");
          if (p != -1) {
            String zipfilename = name.substring(0, p);
            if (zip == null) {
              // �t�@�C������!���܂܂��ꍇ�́AZip�t�@�C�����̃G���g���Ɣ��f
              // zip�͍ŏ��̂ݐ����ŁA�S�Ă�file�͓���Zip���̃G���g����z��B
              zip = new ZipFile((File)tempFiles.get(zipfilename), ZipFile.OPEN_READ);
            }
            name = name.substring(p + 1);
            if (zip != null) {
              ZipEntry entry = zip.getEntry(name);
              if (entry == null) {
                // �G���g����������Ȃ��ꍇ�A�ēxZIP�t�@�C�����I�[�v�����ĒT���Ă݂�
                if (zip != null) {
                  try {
                    zip.close();
                  } catch(Exception e) {
                    log_debug(e);
                  }
                  zip = new ZipFile((File)tempFiles.get(zipfilename), ZipFile.OPEN_READ);
                  entry = zip.getEntry(name);
                }
              }
              is = zip.getInputStream(entry);
            }
          } else {
            // ZIP,JAR�ȊO�̃t�@�C��(CSV)�̏ꍇ�́A�A�b�v���[�h�t�@�C���𒼐ڃI�[�v��
            is = new FileInputStream((File)tempFiles.get(name));
          }
          //2014/04/28�@�������쐬�@start
          String timestampStr = DbAccessUtils.toTimestampString(timestamp);
          timestampStr = timestampStr.replaceAll(" ", "_");
          timestampStr = timestampStr.replaceAll(":", "-");
          if (timestampStr.indexOf(".") != -1) {
            timestampStr = timestampStr.substring(0, timestampStr.indexOf("."));
          }
          String importPath = "WEB-INF/import/" + timestampStr;
          File oldFile = new File(appPath, importPath + "/old/" + name);
          File newFile = new File(appPath, importPath + "/new/" + name);

          DbAccessUtils.writeFile(newFile, is);
          newFile.setLastModified(zip.getEntry(name).getTime());
          is.close();
          is = new FileInputStream(newFile);
          //2014/04/28�@�������쐬�@end
          
          if (name.startsWith("update/")) {
            // �X�V���W���[���̃C���|�[�g
            File updatePath = new File(appPath, "WEB-INF");
            File updateFile = new File(updatePath, name);
            DbAccessUtils.writeFile(updateFile, is);
            is.close();
          }
          //2014/04/24 module�Ή� start
          else if (name.startsWith("module/")) {
            // module�̃C���|�[�g
            File updateFile = new File(appPath, name.substring(7));
            //�X�V���t�@�C�����o�b�N�A�b�v
            DbAccessUtils.copyFile(updateFile, oldFile);
            
            //�t�@�C���X�V
            DbAccessUtils.writeFile(updateFile, is);
            updateFile.setLastModified(zip.getEntry(name).getTime());
            is.close();
          }
          //2014/04/24 module�Ή� end
          else {
            // �ʏ�̃C���|�[�g
            if (conn == null) {
              // ���[�v�̍ŏ��̂�Connection�𐶐�
              conn = getConnection();
              conn.setAutoCommit(false);
            }
            if (name.endsWith(".del") && mode == IMPORT_NORMAL) {
              // �g���q��.del�̏ꍇ�͍폜���[�h
              mode = IMPORT_DELETE;
            }
            if (name.toLowerCase().endsWith(".xls")) {
              // EXCEL�t�@�C���̃C���|�[�g
              File file = (File)tempFiles.get(name);
              BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
              int replaceMode = TableManager.REPLACE_PKEY;
              if (replace != null) {
                if ("update".equals(replace)) {
                  replaceMode = TableManager.REPLACE_PKEY;
                } else if ("insert".equals(replace)) {
                  replaceMode = TableManager.REPLACE_NONE;
                } else if ("replace".equals(replace)) {
                  replaceMode = TableManager.REPLACE_ALL;
                }
              }
              UpdateResult r = ExcelManager.excelImportToTable(conn, bis, null, -1, replaceMode, null);
              if (r != null) {
                // DBACCESS_IMPORTLOG�֏����o�͂���
                insertSQLLog("IMPORT " + r.getTableName(), r.toString(), null, null, loginInfos);
                msg.append(r.getTableName()).append(":").append(r.getInsertCount()).append("���C���|�[�g���܂���");
                Vector subTables = r.getTableList();
                if (subTables != null && subTables.size() > 0) {
                  for (Iterator ite = subTables.iterator(); ite.hasNext(); ) {
                    UpdateResult subTable = (UpdateResult)ite.next();
                    msg.append(",").append(subTable.getTableName()).append(":").append(subTable.getInsertCount()).append("���C���|�[�g���܂���");
                    if (subTable.getTableList().size() > 0) {
                      for (Iterator ite2 = subTable.getTableList().iterator(); ite2.hasNext(); ) {
                        UpdateResult subTable2 = (UpdateResult)ite2.next();
                        msg.append(",").append(subTable2.getTableName()).append(":").append(subTable2.getInsertCount()).append("���C���|�[�g���܂���");
                      }
                    }
                  }
                }
                if (r.hasError()) {
                  msg.append("(�G���[�F").append(r.getErrorInfo().size()).append("��)");
                  String errorDetail = r.getErrorDetail();
                  if (errorDetail != null && errorDetail.trim().length() > 0) {
                    if (errorDetail.length() > 200) {
                      // ��������ƃt���[�Y����̂�200�����x�Ő؂�
                      errorDetail = errorDetail.substring(0, 200) + "...";
                    }
                    msg.append(" ").append(errorDetail);
                  }
                  // ���O�փG���[�����o�͂���
                  for (Iterator ite = r.getErrorInfo().iterator(); ite.hasNext(); ) {
                    log_debug("IMPORT_ERROR:" + (String)ite.next());
                  }
                }
              } else {
                msg.append("���T�|�[�g�C�t�@�C���`���F").append(name);
              }
            } else {
              //2014/04/28 �C���|�[�g�O�ɁA�����f�[�^���o�b�N�A�b�v start
              String tableName = null;
              InputStream nis = new FileInputStream(newFile);
              BufferedReader br = new BufferedReader(new InputStreamReader(nis, DEFAULT_CHARSET));
              String line = br.readLine();
              if (isTableMCSVLine(line)) { // �ŏ��̍s��[�e�[�u����]���ǂ����`�F�b�N
                tableName = getTableNameFromMCSVLine(line);
              }
              nis.close();
              
              int idx = name.indexOf(".csv");
              String[] csvFileNames = (name.substring(0, idx)).split("/");
              int tlen = csvFileNames.length;
              if(tlen > 0 && tableName != null){
                File dir = oldFile.getParentFile();
                if (!dir.exists()) {
                  dir.mkdirs();
                }
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(oldFile));
                String compid = csvFileNames[tlen - 1];
                Vector params = getRelationParams(conn, tableName);
                printExportMCSV(bos, new String[]{compid}, params);
                bos.flush();
                bos.close();
                //�X�V������ݒ�
                String keyField = tableName.substring(0, tableName.length() - "MASTER".length()) + "ID";
                if(tableName.equals("CLASSTYPEMASTER")){
                  keyField = "CLASSTYPE";
                }
                long ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, tableName, new String[]{keyField}, compid));
                if (ts != -1) {
                  oldFile.setLastModified(ts);
                }
              }
              //2014/04/28 �C���|�[�g�O�ɁA�����f�[�^���o�b�N�A�b�v end
              
              // MCSV�f�[�^�̃C���|�[�g�����s
              String m = importMCSVData(request, conn, is, timestamp, mode, loginInfos);
              msg.append(m);
            }
          }
          ++count;
        } catch(Throwable e) {
          log_debug(e);
          // �G���[�����������ꍇ�́AByteArray�o�R��StackTrace�𕶎��񉻂��\��
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          e.printStackTrace(new PrintStream(baos));
          msg.append(baos.toString());
          hasErrors = true;
        } finally {
          if (is != null) {
            try {
              is.close();
            } catch (IOException e) {}
          }
        }
        String dispName = name;
        if (zip == null) {
          dispName = new File((String)tempOrigFiles.get(name)).getName();
        }
        if (hasErrors) {
          out.println("<tr><td></td><td>" + dispName + "</td><td><pre><font color=\"" + ERROR_COLOR + "\">" + msg + "</font></pre></td></tr>");
          // �G���[���������ꍇ�͂����Œ��f
          break;
        } else {
          if (mode == IMPORT_CHECK && msg != null && msg.length() == 0) {
            msg.append("�G���[�͌�����܂���ł���");
          }
          out.println("<tr><td></td><td>" + dispName + "<input type=\"hidden\" name=\"file\" value=\"" + files[i] + "\"></td><td><nobr>" + msg + "</nobr></td></tr>");
        }
        out.flush();
      }
      if (zip != null) {
        try {
          zip.close();
        } catch (IOException e) {}
      }
      
      if (conn != null) {
        try {
          if (hasErrors || checkonly) {
            conn.rollback();
          } else {
            conn.commit();
          }
        } catch(SQLException e) {}
        try {
          conn.close();
        } catch(SQLException e) {}
        conn = null;
      }
      
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        Hashtable counts = new Hashtable();
        // �e�[�u���}�X�^���܂܂�Ă����ꍇ�͕����e�[�u����CREATE����
        Vector created = new Vector();
        if (importTables.size() > 0) {
          ClassManager classManager = new ClassManager(appPath);
          for (Iterator ite = new TreeSet(importTables.keySet()).iterator(); ite.hasNext(); ) {
            String tableName = (String)ite.next();
            int countBase = DbAccessUtils.countTable(conn, tableName);
            int countName = DbAccessUtils.countTable(conn, DbAccessUtils.getNameTableName(tableName));
            int countInfo = DbAccessUtils.countTable(conn, DbAccessUtils.getInfoTableName(tableName));
            if (mode != IMPORT_NORMAL) {
              created.add(tableName);
            } else {
              int[] err = checkTableLayout(classManager, conn, tableName, null); // �����e�[�u���Ɣ�r
              if (countBase <= 0 && !tableName.startsWith("V_")) {
                // �f�[�^������0(�܂��͑��݂��Ȃ�)���ύX����̏ꍇ�͎��������č\�z
                if (err[0] > 0) {
                  StringBuffer emsg = new StringBuffer();
                  if (createTableFromTableLayoutMaster(conn, tableName, tableName, emsg, getLoginInfos(request))) {
                    out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"" + INFO_COLOR + "\">�����e�[�u�����č쐬���܂����B</font></td></tr>");
                    if (emsg.length() > 0) {
                      out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"" + ERROR_COLOR + "\">" + emsg + "</font></td></tr>");
                    }
                    created.add(tableName);
                  } else {
                    out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"" + ERROR_COLOR + "\">�����e�[�u���̍č쐬�Ɏ��s���܂����B(" + emsg + ")</font></td></tr>");
                  }
                }
              } else {
                counts.put(tableName, new Integer(countBase));
              }
              if (countName <= 0 && (err[1] == 1 || err[1] == 2)) {
                // ���̃e�[�u�������݂��Ȃ����ύX�̂���ꍇ
                StringBuffer emsg = new StringBuffer();
                if (createTableFromTableLayoutMaster(conn, tableName, DbAccessUtils.getNameTableName(tableName), emsg, getLoginInfos(request))) {
                  out.println("<tr><td></td><td>" + DbAccessUtils.getNameTableName(tableName) + "</td><td><font color=\"" + INFO_COLOR + "\">�����e�[�u��(����)���č쐬���܂����B</font></td></tr>");
                  if (emsg.length() > 0) {
                    out.println("<tr><td></td><td>" + DbAccessUtils.getNameTableName(tableName) + "</td><td><font color=\"" + ERROR_COLOR + "\">" + emsg + "</font></td></tr>");
                  }
                  created.add(DbAccessUtils.getNameTableName(tableName));
                } else {
                  out.println("<tr><td></td><td>" + DbAccessUtils.getNameTableName(tableName) + "</td><td><font color=\"" + ERROR_COLOR + "\">�����e�[�u��(����)�̍č쐬�Ɏ��s���܂����B(" + emsg + ")</font></td></tr>");
                }
              }
              if (countInfo <= 0 && (err[2] == 1 || err[2] == 2)) {
                // ���e�[�u�������݂��Ȃ����ύX�̂���ꍇ
                StringBuffer emsg = new StringBuffer();
                if (createTableFromTableLayoutMaster(conn, tableName, DbAccessUtils.getInfoTableName(tableName), emsg, getLoginInfos(request))) {
                  out.println("<tr><td></td><td>" + DbAccessUtils.getInfoTableName(tableName) + "</td><td><font color=\"" + INFO_COLOR + "\">�����e�[�u��(���)���쐬���܂����B</font></td></tr>");
                  if (emsg.length() > 0) {
                    out.println("<tr><td></td><td>" + DbAccessUtils.getInfoTableName(tableName) + "</td><td><font color=\"" + ERROR_COLOR + "\">" + emsg + "</font></td></tr>");
                  }
                  created.add(DbAccessUtils.getInfoTableName(tableName));
                } else {
                  out.println("<tr><td></td><td>" + DbAccessUtils.getInfoTableName(tableName) + "</td><td><font color=\"" + ERROR_COLOR + "\">�����e�[�u��(���)�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font></td></tr>");
                }
              }
            }
            try {
              // �X�V���ꂽ�e�[�u����`���N���X�����擾
              PreparedStatement cn = conn.prepareStatement("SELECT PROPERTYID, VALUE FROM TABLEINFO WHERE TABLEID=? AND PROPERTYID IN ('CLASSNAME','CLASSPACKAGEID','JAVAPACKAGEID')");
              cn.setString(1, tableName);
              ResultSet rs = cn.executeQuery();
              String javaPackageId = null;
              String className = null;
              String classPackageId = null;
              while (rs.next()) {
                String propertyId = rs.getString(1);
                String value = rs.getString(2);
                if ("JAVAPACKAGEID".equals(propertyId)) {
                  javaPackageId = value;
                } else if ("CLASSNAME".equals(propertyId)) {
                  className = value;
                } else if ("CLASSPACKAGEID".equals(propertyId)) {
                  classPackageId = value;
                }
              }
              rs.close();
              cn.close();
              String classType = null;
              if (javaPackageId != null && className != null && !javaPackageId.startsWith(".") && className.trim().length() > 0) {
                classType = javaPackageId.trim() + "." + className.trim();
              }
              
              String[] classTypes = (String[])importTables.get(tableName);
              String oldClassType = classTypes[0];
              String csvClassType = classTypes[1];
              boolean modClassType = false;
              if (oldClassType != null && oldClassType.trim().length() > 0 && csvClassType != null && !csvClassType.equals(oldClassType)) {
                modClassType = true;
              }
              if (mode == IMPORT_CHECK) {
                if (modClassType) {
                  out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"" + ERROR_COLOR + "\">�N���X�^�C�v���ύX����Ă܂��B(" + oldClassType + "->" + csvClassType + ")</font></td></tr>");
                }
              } else if (mode == IMPORT_NORMAL) {
                // �X�V���[�h
                if (classType != null) {
                  // �N���X�����������Ǝv����ꍇ�̓N���X�^�C�v�}�X�^��u��������
                  // �e�[�u���}�X�^�̏����擾
                  PreparedStatement ts = conn.prepareStatement("SELECT UPDATECOMPANYID, UPDATEUSERID, UPDATEPROCESSID, TIMESTAMPVALUE,"
                      + " (SELECT COUNT(1) FROM TABLELAYOUTMASTER WHERE TABLEID=a.TABLEID AND DATAFIELDID='STARTDATE' AND DATAFIELDCLASS='1')"
                      + " FROM TABLEMASTER a WHERE TABLEID=?");
                  ts.setString(1, tableName);
                  ResultSet trs = ts.executeQuery();
                  String updateCompanyId = null;
                  String updateUserId = null;
                  String updateProcessId = null;
                  String timestampValue = null;
                  int startDateCount = 0;
                  if (trs.next()) {
                    updateCompanyId = trs.getString(1);
                    updateUserId = trs.getString(2);
                    updateProcessId = trs.getString(3);
                    timestampValue = trs.getString(4);
                    startDateCount = trs.getInt(5);
                  }
                  trs.close();
                  ts.close();
                  // ��U�폜�������ւ���
                  PreparedStatement stmt = conn.prepareStatement("DELETE FROM CLASSTYPEMASTER WHERE CLASSTYPE=?");
                  stmt.setString(1, classType);
                  stmt.executeUpdate();
                  stmt.close();
                  stmt = conn.prepareStatement("DELETE FROM CLASSTYPEINFO WHERE CLASSTYPE=?");
                  stmt.setString(1, classType);
                  stmt.executeUpdate();
                  stmt.close();
                  stmt = conn.prepareStatement("DELETE FROM CLASSTYPENAME WHERE CLASSTYPE=?");
                  stmt.setString(1, classType);
                  stmt.executeUpdate();
                  stmt.close();
                  stmt = conn.prepareStatement("INSERT INTO CLASSTYPEMASTER (CLASSTYPE,PACKAGEID,INHERITEDCLASSTYPE,CLASSCLASS,BASECLASSTYPE,UPDATECOMPANYID,UPDATEUSERID,UPDATEPROCESSID,TIMESTAMPVALUE) VALUES (?,?,?,?,?,?,?,?,?)");
                  stmt.setString(1, classType);
                  if (classPackageId != null) {
                    stmt.setString(2, classPackageId);
                  } else {
                    stmt.setString(2, javaPackageId);
                  }
                  if (startDateCount == 0) {
                    stmt.setString(3, "EntityObject");
                  } else {
                    stmt.setString(3, "EntityObjectForStartDate");
                  }
                  stmt.setString(4, "1");
                  stmt.setString(5, "GroupObject");
                  stmt.setString(6, updateCompanyId);
                  stmt.setString(7, updateUserId);
                  stmt.setString(8, updateProcessId);
                  stmt.setString(9, timestampValue);
                  stmt.executeUpdate();
                  stmt.close();
                  stmt = conn.prepareStatement("INSERT INTO CLASSTYPENAME (CLASSTYPE,DISPLANGID,PROPERTYID,NAMEVALUE) SELECT ?, DISPLANGID, PROPERTYID, NAMEVALUE FROM TABLENAME WHERE TABLEID = ? AND PROPERTYID IN ('OFFICIALNAME', 'SHORTNAME')");
                  stmt.setString(1, classType);
                  stmt.setString(2, tableName);
                  stmt.executeUpdate();
                  stmt.close();
                  out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"blue\">�N���X�^�C�v�}�X�^(" + classType + ")��o�^���܂����B</font></td></tr>");
                  if (modClassType) {
                    int oldCount = 0;
                    if (oldClassType.indexOf(".") != -1) {
                      String oldJavaPackageId = oldClassType.substring(0, oldClassType.lastIndexOf("."));
                      String oldClassName = oldClassType.substring(oldClassType.lastIndexOf(".") + 1);
                      stmt = conn.prepareStatement("SELECT COUNT(1) FROM TABLEINFO a INNER JOIN TABLEINFO b on b.TABLEID=a.TABLEID"
                          + " AND a.PROPERTYID='JAVAPACKAGEID' AND a.VALUE=? AND b.PROPERTYID='CLASSNAME' AND b.VALUE=?");
                      stmt.setString(1, oldJavaPackageId);
                      stmt.setString(2, oldClassName);
                      rs = stmt.executeQuery();
                      if (rs.next()) {
                        oldCount = rs.getInt(1);
                      }
                      rs.close();
                      stmt.close();
                    }
                    if (oldCount == 0) {
                      // oldClassType�����ɑ��݂��Ȃ��ꍇ�͐VclassType�ɋ����X�V�������Ȃ�
                      stmt = conn.prepareStatement("UPDATE ITEMDEFINITIONMASTER SET CLASSTYPE = ? WHERE CLASSTYPE = ?");
                      stmt.setString(1, csvClassType);
                      stmt.setString(2, oldClassType);
                      int i = stmt.executeUpdate();
                      stmt.close();
                      if (i > 0) {
                        out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"green\">���ڒ�`�̃N���X�^�C�v(" + oldClassType + ")��(" + csvClassType + ")�ɒu�����܂����B(�Ώ�:" + i + "��)</font></td></tr>");
                      }
                      stmt = conn.prepareStatement("UPDATE DATAFIELDMASTER SET DEFAULTCLASSTYPE = ? WHERE DEFAULTCLASSTYPE = ?");
                      stmt.setString(1, csvClassType);
                      stmt.setString(2, oldClassType);
                      i = stmt.executeUpdate();
                      stmt.close();
                      if (i > 0) {
                        out.println("<tr><td></td><td>" + tableName + "</td><td><font color=\"green\">�f�[�^���ڃ}�X�^�̃f�t�H���g�N���X�^�C�v(" + oldClassType + ")��(" + csvClassType + ")�ɒu�����܂����B(�Ώ�:" + i + "��)</font></td></tr>");
                      }
                    }
                  }
                }
              }
            } catch(SQLException e) {
              log_debug("INFO: " + tableName + " : " + e.getMessage());
            }
          }
        }
        // �\�z�ł����e�[�u���͑Ώۂ���폜
        for (Iterator ite = created.iterator(); ite.hasNext(); ) {
          importTables.remove(ite.next());
        }
        if (created.size() > 0 && isOracle(0)) {
          // Oracle�̏ꍇ�ŁA�e�[�u���̍č\�z�������Ȃ����ꍇ�́A�O�̂���INVALID���ăR���p�C������
          recompileInvalidDBObjects(loginInfos);
        }
        if (importTables.size() > 0) {
          // �č\�z���K�v�ɂȂ�Ǝv����e�[�u��
          ClassManager entityClassManager = new ClassManager(appPath);
          for (Iterator ite = new TreeSet(importTables.keySet()).iterator(); ite.hasNext(); ) {
            String tableName = (String)ite.next();
            String checked = "";
            String color = "blue";
            StringBuffer comments = new StringBuffer();
            int[] err = checkTableLayout(entityClassManager, conn, tableName, comments);
            if (err[0] != 0 || err[1] > 0 || err[2] > 0) {
              // �_��:�����e�[�u����`���قȂ�ꍇ
              checked = " checked";
              color = ERROR_COLOR;
            }
            Integer cnt = (Integer)counts.get(tableName);
            if (cnt != null) {
              comments.append("(���R�[�h����=").append(cnt).append(")");
            }
            String check = "<input type=\"checkbox\" name=\"table\" value=\"" + tableName + "\"" + checked + ">";
            out.println("<tr><td>" + check + "</td><td>" + tableName + "</td><td>�e�[�u����`�X�V&nbsp;&nbsp;<font color=\"" + color + "\">" + comments + "</font></td></tr>");
            ret = 1;
          }
          importTables.clear();
        }
        
      } catch (SQLException e) {
        log_debug(e);
      } finally {
        if (conn != null) {
          try {
            conn.commit();
          } catch(SQLException e) {}
          try {
            conn.close();
          } catch(SQLException e) {}
        }
        
      }
      
    } else {
      out.println("<tr><td colspan=\"3\">�C���|�[�g�Ώۃt�@�C��������܂���B</td></tr>");
    }
    out.println("</table>");
    return ret;
  }
  

  /**
   * �e�[�u���̍č\�z
   * @param conn
   * @param tableName
   * @return true:�����Afalse�F�G���[
   */
  private boolean createTableFromTableLayoutMaster(Connection conn, String baseTableName, String tableName, StringBuffer errorInfo, String[] loginInfos) {
    boolean ret = true;
    String ts = Long.toString(System.currentTimeMillis()); // 13��
    String bak_tableName = tableName;
    if (bak_tableName.length() > 15) {
      if (bak_tableName.endsWith("NAME") || bak_tableName.endsWith("INFO")) {
        if (bak_tableName.length() < 19) {
          // NAME or INFO�̈ꕔ���܂܂��̂�OK
          bak_tableName = bak_tableName.substring(0, 15);
        } else {
          // �Ō��N�܂���I�ɂ���
          bak_tableName = bak_tableName.substring(0, 14) + bak_tableName.charAt(bak_tableName.length() - 4);
        }
      } else {
        bak_tableName = bak_tableName.substring(0, 15);
      }
    }
    bak_tableName = "\"$" + bak_tableName + ts + "$\"";
    String[] oldColNames = null;
    StringBuffer createIndex = null;
    if (isOracle(0)) {
      // �Ώ�DB��Oracle�̏ꍇ�A�f�[�^������Έێ��������Ȃ�
      PreparedStatement stmt = null;
      ResultSet rs = null;
      int count = 0;
      try {
        // ���R�[�h�������J�E���g����
        stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
        rs = stmt.executeQuery();
        if (rs.next()) {
          count = rs.getInt(1);
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
        if (count > 0) {
          // �f�[�^�����݂���ꍇ
          oldColNames = getPhysicalColumnNames(conn, tableName);
          // �o�b�N�A�b�v�e�[�u�����쐬(CREATE-SELECT)
          String sql = "CREATE TABLE " + bak_tableName + " AS SELECT * FROM " + tableName;
          stmt = conn.prepareStatement(sql);
          stmt.executeUpdate();
          insertSQLLog(sql, null, null, null, loginInfos);
          String createIndexSQL = DbAccessUtils.getCreateNonUniqueIndexSQL(conn, schemas[0], tableName);
          if (createIndexSQL != null && createIndexSQL.trim().length() > 0) {
            createIndex = new StringBuffer();
            createIndex.append(createIndexSQL);
          }
        }
      } catch(SQLException se) {
        // �e�[�u�������݂��Ȃ��ꍇ
        log_debug(se.getMessage());
//        if (errorInfo != null) {
//          errorInfo.append(se.getMessage());
//        }
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch(SQLException e) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch(SQLException e) {}
        }
      }
    } else {
      // �Ώ�DB��Oracle�ȊO�̏ꍇ
      PreparedStatement stmt = null;
      ResultSet rs = null;
      PreparedStatement stmti = null;
      try {
        // �S���R�[�h��SELECT
        stmt = conn.prepareStatement("SELECT * FROM " + tableName);
        rs = stmt.executeQuery();
        int cnt = 0;
        int cc = 0;
        while (rs.next()) {
          if (cnt == 0) {
            oldColNames = getPhysicalColumnNames(conn, tableName);
            // �o�b�N�A�b�v�e�[�u����CREATE(CREATE/INSERT-SELECT)
            ResultSetMetaData m = rs.getMetaData();
            StringBuffer colname = new StringBuffer();
            StringBuffer createstr = new StringBuffer();
            StringBuffer insertstr = new StringBuffer();
            StringBuffer insertpara = new StringBuffer();
            Vector colnames = new Vector();
            cc = m.getColumnCount();
            for (int i = 0; i < cc; i++) {
              String name = m.getColumnName(i + 1).toUpperCase();
              colname.append(name);
              colnames.add(name);
              if (i == 0) {
                createstr.append("CREATE TABLE " + bak_tableName + " (");
                insertstr.append("INSERT INTO " + bak_tableName + " (");
              }
              String type_name = m.getColumnTypeName(i + 1);
              if (type_name == null) {
                type_name = "CHAR";
              } else {
                type_name = type_name.toUpperCase();
              }
              int size = m.getColumnDisplaySize(i + 1);
              if (type_name.equals("CHARACTER")) {
                type_name = "CHAR";
              }
              createstr.append("  ").append(name).append(" ").append(type_name).append(" ");
              if (i > 0) {
                insertstr.append(",");
                insertpara.append(",");
              }
              insertstr.append(name);
              insertpara.append("?");
              if (type_name.startsWith("VARCHAR") || type_name.equals("CHAR")) {
                createstr.append("(").append(size).append(") ");
              } else if (type_name.startsWith("DEC")) {
                int prec = m.getPrecision(i + 1);
                int scale = m.getScale(i + 1);
                createstr.append("(").append(prec).append(",").append(scale).append(") ");
              }
              if (m.isNullable(i + 1) == 0) {
                createstr.append("NOT NULL ");
              }
              if (i < cc - 1) {
                colname.append(",");
                createstr.append(",");
              } else {
                createstr.append(")");
              }
            }
            String sql = createstr.toString();
            PreparedStatement stmt2 = conn.prepareStatement(sql);
            stmt2.execute();
            stmt2.close();
            insertSQLLog(sql, null, null, null, loginInfos);
            insertstr.append(") VALUES (").append(insertpara.toString()).append(")");
            sql = insertstr.toString();
            stmti = conn.prepareStatement(sql);
            insertSQLLog(sql, null, null, null, loginInfos);
          }
          for (int i = 0; i < cc; ++i) {
            stmti.setString(i + 1, rs.getString(i + 1));
          }
          stmti.executeUpdate();
          cnt++;
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
        if (stmti != null) {
          stmti.close();
          stmti = null;
        }
      } catch(SQLException se) {
        // �e�[�u�������݂��Ȃ��ꍇ
        log_debug(se.getMessage());
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch(SQLException e) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch(SQLException e) {}
        }
      }
    }
    String sql = null;
    try {
      sql = DbAccessUtils.getCreateTableSQLFromTablelayoutMaster(conn, baseTableName);
    } catch (SQLException e) {
      log_debug(e.getMessage());
    }
    if (sql == null) {
      log_debug("getCreateTableSQL(): null :" + baseTableName + "," + tableName);
      // SQL�����擾�ł��Ȃ��ꍇ
      return false;
    }
    if (createIndex != null) {
      sql = sql + createIndex.toString();
    }
    StringTokenizer st = new StringTokenizer(sql, ";");
    while (st.hasMoreTokens()) {
      String ddl = st.nextToken();
      if (ddl.startsWith("\n")) {
        ddl = ddl.substring(1); // �擪�̉��s���X�L�b�v
      }
      if (!ddl.startsWith("CREATE TABLE " + tableName+ " ")
          && !ddl.equals("DROP TABLE " + tableName)
          && !ddl.startsWith("CREATE INDEX ")) {
        log_debug("skip:" + ddl);
        continue;
      }
      log_debug(ddl);
      Statement stmt = null;
      try {
        stmt = conn.createStatement();
        stmt.execute(ddl);
        insertSQLLog(ddl, null, null, null, loginInfos);
      } catch(SQLException se) {
        log_debug(se.getMessage());
        if (ddl.startsWith("DROP")) {
          if (DbAccessUtils.isTableExists(conn, schemas[0], tableName)) {
            if (errorInfo != null) {
              errorInfo.append(se.getMessage());
            }
          }
          continue;
        }
        if (errorInfo != null) {
          errorInfo.append(se.getMessage());
        }
        ret = false;
        break;
      } finally {
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {}
        }
      }
    }
    if (oldColNames != null) {
      // �o�b�N�A�b�v�e�[�u�����쐬�ł����ꍇ
      if (ret) {
        boolean inserr = false;
        // �V�����e�[�u���̃J���������擾
        String[] newColNames = getPhysicalColumnNames(conn, tableName);
        Vector notNullColNames = getNotNullColumnNames(conn, tableName);
        Vector oldCols = new Vector(Arrays.asList(oldColNames));
        Vector copyCols = new Vector();
        Vector notNullCols = new Vector();
        for (int i = 0; i < newColNames.length; ++i) {
          if (oldCols.contains(newColNames[i])) {
            // ���e�[�u���ɓ������������Ȃ�Βǉ�
            copyCols.add(newColNames[i]);
          } else if (notNullColNames.contains(newColNames[i])) {
            notNullCols.add(newColNames[i]);
          }
        }
        StringBuffer insert = new StringBuffer();
        StringBuffer fieldList = new StringBuffer();
        insert.append("INSERT INTO ").append(tableName);
        insert.append(" (");
        for (int i = 0; i < copyCols.size(); ++i) {
          if (i > 0) {
            fieldList.append(",");
          }
          fieldList.append(copyCols.get(i));
        }
        StringBuffer selectFieldList = new StringBuffer();
        selectFieldList.append(fieldList.toString());
        if (notNullCols.size() > 0) {
          // �������t�B�[���h��Not Null���������ꍇ�͒ǉ��ݒ�
          for (int i = 0; i < notNullCols.size(); ++i) {
            fieldList.append(",");
            fieldList.append(notNullCols.get(i));
            selectFieldList.append(",' '"); // TODO: �������O��Ƃ��Ă��邪�ŏI�I�ɂ�TYPE���f������
          }
        }
        insert.append(fieldList.toString());
        insert.append(") SELECT ").append(selectFieldList.toString()).append(" FROM ").append(bak_tableName);
        Statement stmt = null;
        try {
          stmt = conn.createStatement();
          sql = insert.toString();
          int cnt = stmt.executeUpdate(sql);
          insertSQLLog(sql, Integer.toString(cnt), null, null, loginInfos);
        } catch(SQLException se) {
          log_debug(se.getMessage());
          if (errorInfo != null) {
            errorInfo.append(se.getMessage());
          }
          inserr = true;
        } finally {
          if (stmt != null) {
            try {
              stmt.close();
            } catch (SQLException e) {}
            stmt = null;
          }
        }
        if (!inserr) {
          // �G���[���Ȃ���΃o�b�N�A�b�v�e�[�u���̍폜
          try {
            stmt = conn.createStatement();
            sql = "DROP TABLE " + bak_tableName;
            stmt.execute(sql);
            insertSQLLog(sql, null, null, null, loginInfos);
          } catch(SQLException se) {
            log_debug(se.getMessage());
            if (errorInfo != null) {
              errorInfo.append(se.getMessage());
            }
            ret = false;
          } finally {
            if (stmt != null) {
              try {
                stmt.close();
              } catch (SQLException e) {}
            }
          }
        }
      }
    }
    return ret;
  }
  /**
   * OutputStream��CSV�f�[�^���o��(exportMCSVData��relationParams���Ăяo��)
   * @param os �o�͐�
   * @param keyvalues
   * @param relationParams
   */
  private boolean printExportMCSV(OutputStream os, String[] keyvalues, Vector relationParams) {
    boolean exported = false;
    StringBuffer sb = new StringBuffer();
    sb.append("keyvalues=");
    for (int i = 0; i < keyvalues.length; ++i) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(keyvalues[i]);
    }
    sb.append("\n");
    for (int i = 0; i < relationParams.size(); ++i) {
      String[] p = (String[])relationParams.get(i);
      sb.append("relationParams[" + i + "]=");
      for (int j = 0; j < p.length; ++j) {
        if (j > 0) {
          sb.append(",");
        }
        sb.append(p[j]);
      }
      sb.append("\n");
    }
    log_debug(sb.toString());
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      for (int i = 0; i < relationParams.size(); ++i) {
        boolean ok = exportMCSVData(conn, os, (String[])relationParams.get(i), keyvalues);
        if (ok) {
          // 1���ł��o�͂ł����ꍇ��true��Ԃ�
          exported = true;
        }
      }
    } catch(Exception e) {
      // ��ʂɂ͖߂��Ȃ��̂ŁA�Ƃ肠�����o�͐�stream�ɏo�͂���...
      e.printStackTrace(new PrintWriter(os));
      log_debug(e);
      exported = false;
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
    }
    return exported;
  }
  
  /**
   * MBB�}���`���C�A�E�gCSV�`���̃f�[�^��OutputStream�֏o��
   * @param conn �f�[�^�擾��Connection
   * @param os �o�͐�OutputStream
   * @param params �擾�f�[�^���{�e�[�u����,�L�[1�̃J������,�L�[2�̃J������...,�\�[�g��}
   * @param keys {�L�[1�̒l�A�L�[2�̒l...}
   * @return true:�f�[�^����Afalse:�f�[�^�Ȃ��i�w�b�_�݂̂�0���f�[�^�̃t�@�C���������j
   */
  private boolean exportMCSVData(Connection conn, OutputStream os, String[] params, String[] keys) {
    boolean exported = false;
    try {
      os.write(("[" + params[0] + "]" + EOL).getBytes(DEFAULT_CHARSET));
      String tmp = params[0];
      for (int i = 1; i < params.length; ++i) {
        tmp += "," + params[i];
      }
      log_debug(tmp);
      boolean pagemsg = false;
      if (params[0].equalsIgnoreCase("PAGEMESSAGE")) {
        pagemsg = true;
      }
      StringBuffer sql = new StringBuffer();
      sql.append("SELECT * FROM ").append(params[0]).append(" WHERE");
      for (int i = 1; i < params.length - 1; ++i) {
        if (i > 1) {
          sql.append(" AND");
        }
        if (pagemsg) {
          // PAGEMESSAGE�̏ꍇ�̓��ꒊ�o����
          sql.append(" ").append(params[i]).append(" LIKE ?");
        } else if (params[0].equalsIgnoreCase("ITEMDEFINITIONMASTER")) {
          // ITEMDEFINITIONMASTER�̏ꍇ�̓��ꒊ�o����
          sql.append(" ").append(params[i]).append("=(SELECT ITEMDEFINITIONID FROM PROCESSMASTER WHERE PROCESSID=?)");
        } else {
          sql.append(" ").append(params[i]).append("=?");
        }
      }
      // �\�[�g���̐ݒ�(params�̍Ō�̃f�[�^���\�[�g��,"ORDER BY"�t��)
      if (params[params.length - 1] != null) {
        sql.append(" ").append(params[params.length - 1]);
      }
      PreparedStatement stmt = conn.prepareStatement(sql.toString());
      for (int i = 1; i < params.length - 1; ++i) {
        if (pagemsg) {
          // PAGEMESSAGE�̏ꍇ�̂ݓ���ȃL�[�𐶐�
          String key = keys[i - 1];
          int p = key.lastIndexOf("_");
          if (p != -1) {
            key = key.substring(0, p) + "@%";
          }
          stmt.setString(i, key);
        } else {
          stmt.setString(i, keys[i - 1]);
        }
      }
      ResultSet rs = stmt.executeQuery();
      ResultSetMetaData meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();
      for (int i = 0; i < columnCount; ++i) {
        if (i > 0) {
          os.write(",".getBytes(DEFAULT_CHARSET));
        }
        os.write(meta.getColumnName(i + 1).getBytes(DEFAULT_CHARSET));
      }
      os.write(EOL.getBytes());
      int count = 0;
      while (rs.next()) {
        for (int i = 0; i < columnCount; ++i) {
          if (i > 0) {
            os.write(",".getBytes());
          }
          String value = DbAccessUtils.escapeCSV(rs.getString(i + 1));
          os.write(value.getBytes(DEFAULT_CHARSET));
        }
        os.write(EOL.getBytes());
        count++;
      }
      rs.close();
      stmt.close();
      if (count > 0) {
        exported = true;
      }
    } catch(SQLException e) {
      log_debug(e);
      try {
        e.printStackTrace(new PrintWriter(os));
        os.flush();
      } catch(IOException ex) {}
    } catch(IOException e) {
      log_debug(e);
      try {
        e.printStackTrace(new PrintWriter(os));
        os.flush();
      } catch(IOException ex) {}
    }
    return exported;
  }
  
  /**
   * 1���̃}���`���C�A�E�g�`����CSV�f�[�^���C���|�[�g����
   * @param conn �C���|�[�g��R�l�N�V����
   * @param csv csv�f�[�^�̓��̓X�g���[��
   * @param timestamp �f�[�^��TIMESTAMPVALUE���܂܂�Ȃ��ꍇ�Ɏg�p����
   * @param mode 0:�ʏ�A1:�폜�̂݁A2:�`�F�b�N�̂�
   * @return
   * @throws IOException
   * @throws SQLException
   */
  private String importMCSVData(HttpServletRequest request, Connection conn, InputStream csv, long timestamp, int mode, String[] updateInfos) throws IOException, SQLException {
    StringBuffer msg = new StringBuffer();
    BufferedReader br = new BufferedReader(new InputStreamReader(csv, DEFAULT_CHARSET));
    String line = null;
    String tableName = null;
    String columnNames = null;
    Vector delParams = null;
    Vector opLog = new Vector(); // ���s���O�i���s���ʉ�ʕ\���p�j
    int lineNo = 0;
    int logCount = 0;
    // CSV�t�@�C�����s�P�ʂœǂݍ���
    PreparedStatement impStmt = null; // �ė��p�pstatement
    while ((line = br.readLine()) != null) {
      ++lineNo;
      if (isTableMCSVLine(line)) { // �ŏ��̍s��[�e�[�u����]���ǂ����`�F�b�N
        tableName = getTableNameFromMCSVLine(line);
        columnNames = null;
        if (lineNo == 1) {
          // �폜�p�����[�^�Ƃ��āA�ŏ��̃e�[�u���̂݊֘A�����擾
          delParams = getRelationParams(conn, tableName);
        }
        if (impStmt != null) {
          try {
            impStmt.close();
          } catch (SQLException e) {}
        }
        impStmt = null; // �e�[�u�����ς�����close
        continue;
      }
      if (tableName == null) {
        throw new IOException("�T�|�[�g����Ă��Ȃ�CSV�`���ł��B");
      }
      while (!isCSVLineComplete(line)) {
        String nextLine = br.readLine();
        ++lineNo;
        if (nextLine == null) {
          throw new IOException("�s���ȍs�f�[�^�����o���܂����B[" + tableName + "][�sNo=" + lineNo + "]");
        }
        line = line + EOL + nextLine;
      }
      if (columnNames == null) {
        // �ŏ��̍s�̓J�������Ƃ��ď�������
        columnNames = line;
      } else {
        if (!hasDataFieldInfo && tableName.equals("DATAFIELDINFO")) {
          // DATAFIELDINFO���T�|�[�g�̏ꍇ�͂Ȃɂ����Ȃ�
          continue;
        }
        if (importLogEnabled && logCount == 0) {
          // ���O�̏o�́i�ŏ���1�s�݂̂����Ȃ��j
          insertImportLog(conn, tableName, columnNames, line, timestamp, mode, updateInfos);
          logCount++;
        }
        // �s�̃C���|�[�g�܂��͍폜
        opLog = insertCSVLine(request, conn, impStmt, tableName, columnNames, line, delParams, timestamp, opLog, mode);
        if (opLog != null && opLog.size() > 0) {
          // opLog�̍Ō�ɃC���|�[�g�Ɏg�p����Statement������̂ŁA�����impStmt�ɃZ�b�g���Ďg���܂킷
          Object lastStmt = opLog.get(opLog.size() - 1);
          if (lastStmt instanceof PreparedStatement) {
            impStmt = (PreparedStatement)opLog.remove(opLog.size() - 1);
          }
        }
        if (delParams != null && delParams.size() > 1) {
          // �폜�p�����[�^��2���ȏ�擾�ł����ꍇ�́A�Ȍ�DELETE�͔��s���Ȃ�����delParams���N���A
          delParams = null;
        }
        if (mode == 1) {
          // �폜���[�h�̏ꍇ�͍ŏ���1�s�̏��őΏۃe�[�u���̍폜�͊������邽�߂����ŏI��
          break;
        }
      }
    }
    if (impStmt != null) {
      try {
        impStmt.close();
      } catch (SQLException e) {}
    }
    impStmt = null;
    if (opLog.size() > 0) {
      for (int i = 0; i < opLog.size(); ++i) {
        if (i > 0) {
          msg.append(",");
        }
        msg.append(opLog.get(i));
      }
    }
    return msg.toString();
  }
  
  /**
   * �}���`���C�A�E�gCSV�̃e�[�u�����G���g�����`�F�b�N
   * @param line
   * @return true:�e�[�u�����Afalse:�f�[�^�^�J�������s
   */
  private static boolean isTableMCSVLine(String line) {
    if (line == null) {
      return false;
    }
    if (line.startsWith("[") && line.endsWith("]")) {
      String t = getTableNameFromMCSVLine(line);
      if (t.indexOf(" ") != -1 || t.indexOf(",") != -1) {
        return false;
      }
      return true;
    }
    return false;
  }
  
  /**
   * �}���`�J����CSV�̃e�[�u�����s���A�e�[�u�����𒊏o���ĕԂ�
   * @param line �s�f�[�^�i[�e�[�u����]��z��j
   * @return "�e�[�u����"��Ԃ�
   */
  private static String getTableNameFromMCSVLine(String line) {
    if (line == null) {
      return null;
    }
    return line.substring(1, line.length() - 1);
  }
  
  /**
   * CSV�̍s���������Ă��邩�`�F�b�N
   * @param line
   * @return true:�����Afalse:���s�Ɍp��
   */
  private boolean isCSVLineComplete(String line) {
    boolean dq = false;
    for (int i = 0; i < line.length(); ++i) {
      char c = line.charAt(i);
      if (!dq) {
        // �_�u���N�H�[�g�O�̏���
        if (c == '"') {
          dq = true;
        }
      } else {
        // �_�u���N�H�[�g���̏���
        if (c == '"') {
          if (i < line.length() - 2 && line.charAt(i + 1) == '"') {
            // �_�u���N�H�[�g���Q�����ꍇ�͏�Ԃ�ς����ɃX�L�b�v
            ++i;
          } else {
            dq = false;
          }
        }
      }
    }
    return !dq;
  }
  
  /**
   * CSV1�s���̃C���T�[�g�����idelParams���w�肳��Ă����DELETE���ŏ��Ɏ��s�j
   * @param conn �}����R�l�N�V����
   * @param tableName �}���Ώۃe�[�u����
   * @param columnNames �J������
   * @param line �f�[�^�s
   * @param relationParams �폜�p�����[�^�inull�̏ꍇ�͍폜�����s���Ȃ��j
   * @param timestamp ��փ^�C���X�^���v�i�f�[�^�Ƀ^�C���X�^���v���܂܂�Ȃ��ꍇ�Ɏg�p�j
   * @param opLog ���s���O(���O��ǉ����ĕԂ�)
   * @param mode 0:�ʏ�A1:�폜�̂݁A2:�`�F�b�N�̂�
   * @return ���s���O�i�u�e�[�u����:�I�y���[�V����:���s�s���v�̃��X�g�j
   * @throws IOException
   * @throws SQLException
   */
  private String oldEntityClassName = null; // �ꂵ�����C���X�^���X�ϐ��őΉ��E�E�E
  private String entityClassName = null; // �ꂵ�����C���X�^���X�ϐ��őΉ��E�E�E
  private Vector insertCSVLine(HttpServletRequest request, Connection conn, PreparedStatement stmt, String tableName, String columnNames, String line, Vector relationParams, long timestamp, Vector opLog, int mode) throws IOException, SQLException {
    Vector fieldSet = getFieldSet(columnNames, line);
    Hashtable fields = new Hashtable();
    StringBuffer fieldsetstr = new StringBuffer();
    StringBuffer valuesstr = new StringBuffer();
    String check_classType = null; // �N���X�^�C�v�}�X�^���݃`�F�b�N�p
    String check_packageId = null;
    String check_dataFieldId = null;
    boolean remark = false;
    fieldsetstr.append(columnNames);
    boolean classTypeMaster = false;
    boolean packageMaster = false;
    boolean dataFieldMaster = false;
    boolean tableInfo = false;
    if (tableName.equalsIgnoreCase("CLASSTYPEMASTER")) {
      classTypeMaster = true;
    } else if (tableName.equalsIgnoreCase("PACKAGEMASTER")) {
      packageMaster = true;
    } else if (tableName.equalsIgnoreCase("DATAFIELDMASTER")) {
      dataFieldMaster = true;
    } else if (tableName.equalsIgnoreCase("TABLEINFO")) {
      tableInfo = true;
    }
    
    for (int i = 0; i < fieldSet.size(); ++i) {
      if (i > 0) {
        valuesstr.append(",");
      }
      valuesstr.append("?");
      String[] f = (String[])fieldSet.get(i);
      String name = f[0].toUpperCase();
      String value = f[1];
      if (value == null) {
        value = "";
      }
      fields.put(name, value);
      // �t�B�[���h�����`�F�b�N�Ώۂ𔻕ʂ���
      if (!classTypeMaster && "CLASSTYPE".equalsIgnoreCase(name)) {
        check_classType = value;
      }
      if (!packageMaster && "PACKAGEID".equalsIgnoreCase(name)) {
        check_packageId = value;
      }
      if (!dataFieldMaster && "FIELDID".equalsIgnoreCase(name)) {
        check_dataFieldId = value;
      }
      if (tableInfo && "PROPERTYID".equalsIgnoreCase(name)) {
        if ("JAVAPACKAGEID".equalsIgnoreCase(value)) {
          String[] v = (String[])fieldSet.get(i + 1);
          if (v.length > 1) {
            if (entityClassName == null) {
              entityClassName = v[1];
            } else {
              entityClassName = v[1] + "." + entityClassName;
            }
          }
        } else if ("CLASSNAME".equalsIgnoreCase(value)) {
          String[] v = (String[])fieldSet.get(i + 1);
          if (v.length > 1) {
            if (entityClassName == null) {
              entityClassName = v[1];
            } else {
              entityClassName = entityClassName + "." + v[1];
            }
          }
        }
      }
      if (name.equals("REMARK") && value.startsWith("*")) {
        remark = true;
      }
      if (tableName.equalsIgnoreCase("TABLEMASTER") && name.equalsIgnoreCase("TABLEID")) {
        // ����̃N���X�����擾
        String[] entityClassInfo = DbAccessUtils.getEntityClassName(conn, value);
        oldEntityClassName = entityClassInfo[0];
      }
      if (tableName.equalsIgnoreCase("TABLELAYOUTMASTER") && name.equalsIgnoreCase("TABLEID")) {
        if (oldEntityClassName == null) {
          oldEntityClassName = "";
        }
        Hashtable importTables = (Hashtable)getSessionObject(request, "importTables");
        if (importTables == null) {
          importTables = new Hashtable();
          setSessionObject(request, "importTables", importTables);
        }
        if (!importTables.containsKey(value)) {
          importTables.put(value, new String[]{oldEntityClassName, entityClassName});
          oldEntityClassName = null; // �g���������
          entityClassName = null; // �g���������
        }
      }
    }
    if (remark || mode == 1) {
      // �R�����g�s�͂܂��͍폜���̓`�F�b�N�����Ȃ�
      check_classType = null;
      check_packageId = null;
      check_dataFieldId = null;
    }
    
    if (check_classType != null && check_classType.trim().length() > 0) {
      // �N���X�^�C�v�}�X�^�`�F�b�N
      String msg = null;
      if ((msg = checkClassType(conn, check_classType)) != null) {
        // �N���X�^�C�v�G���[
        log_debug(msg);
        opLog.add(msg);
      }
    }
    if (check_packageId != null && check_packageId.trim().length() > 0) {
      // �p�b�P�[�W�}�X�^�`�F�b�N
      String msg = null;
      if ((msg = checkPackage(conn, check_packageId, tableName)) != null) {
        // �p�b�P�[�W�G���[
        log_debug(msg);
        opLog.add(msg);
      }
    }
    if (dataFieldIdCheck && check_dataFieldId != null && check_dataFieldId.trim().length() > 0) {
      // �f�[�^�t�B�[���h�`�F�b�N
      String msg = null;
      if ((msg = checkDataField(conn, check_dataFieldId)) != null) {
        // �f�[�^�t�B�[���h�G���[
        log_debug(msg);
        opLog.add(msg);
      }
    }
    
    if (mode == 2) {
      // �`�F�b�N�݂̂̏ꍇ�͏I��
      return opLog;
    }
    
    // �ߋ��̃G�N�X�|�[�g�`���Ń^�C���X�^���v���܂܂�Ȃ�CSV�f�[�^�ɑ΂��Ă̏���
    if (fields.get("TIMESTAMPVALUE") == null && (
        tableName.equalsIgnoreCase("PAGEMASTER") || 
        tableName.equalsIgnoreCase("VIEWPAGEMASTER") || 
        tableName.equalsIgnoreCase("PROCESSMASTER") || 
        tableName.equalsIgnoreCase("PROCESSDEFINITIONMASTER") || 
        tableName.equalsIgnoreCase("PROCESSITEMRELDEFMASTER") || 
        tableName.equalsIgnoreCase("ITEMDEFINITIONMASTER")
        )
      ) {
      if (fields.get("UPDATECOMPANYID") == null) {
        valuesstr.append(",?");
        fieldsetstr.append(",UPDATECOMPANYID");
        fieldSet.add(new String[]{"UPDATECOMPANYID", "MBB"});
      }
      if (fields.get("UPDATEUSERID") == null) {
        valuesstr.append(",?");
        fieldsetstr.append(",UPDATEUSERID");
        fieldSet.add(new String[]{"UPDATEUSERID", "MBB"});
      }
      if (fields.get("UPDATEPROCESSID") == null) {
        valuesstr.append(",?");
        fieldsetstr.append(",UPDATEPROCESSID");
        fieldSet.add(new String[]{"UPDATEPROCESSID", "DBACCESS"});
      }
      valuesstr.append(",?");
      fieldsetstr.append(",TIMESTAMPVALUE");
      // TODO: �̂�DB2�H�͔����Ƀ^�C���X�^���v�̏����ɕs���
      // ���邪DB2��JDBC�h���C�o�̕s��Ƃ��Ă�����C�������Ɗ��҂��A���̂܂܂ɂ��Ă����B
      fieldSet.add(new String[]{"TIMESTAMPVALUE", new Timestamp(timestamp).toString()});
    }
    
    if (relationParams != null) {
      // �֘A�e�[�u���f�[�^�̍폜(��A�̃f�[�^�̍ŏ��̂P��̂݊֘A�e�[�u���f�[�^���܂Ƃ߂č폜)
      for (int i = 0; i < relationParams.size(); ++i) {
        String[] params = (String[])relationParams.get(i);
        if (params.length <= 2) {
          // �S���폜�ɂȂ��Ă���ꍇ�͊댯�Ȃ̂ł����Ȃ�Ȃ��i�v���C�}���L�[���擾�ł��Ȃ��P�[�X����j
          log_debug("WARN: �폜�L�[���擾�ł��܂���B[" + params[0] + "]");
          break;
        }
        if (params[0].equalsIgnoreCase("ITEMDEFINITIONMASTER") ||
            params[0].equalsIgnoreCase("PAGEMESSAGE")) {
          // �P���ɍ폜�ł��Ȃ��e�[�u���̓X�L�b�v�i���̓��ꏈ���Ŏ��s�j
          continue;
        }
        StringBuffer deletesql = new StringBuffer();
        deletesql.append("DELETE FROM ").append(params[0]);
        for (int j = 1; j < params.length - 1; ++j) {
          if (j == 1) {
            deletesql.append(" WHERE");
          } else {
            deletesql.append(" AND");
          }
          deletesql.append(" ").append(params[j]).append("=?");
        }
        log_debug(deletesql.toString());
        // �f�[�^�̍폜
        PreparedStatement delstmt = conn.prepareStatement(deletesql.toString());
        for (int j = 1; j < params.length - 1; ++j) { // �ŏ��ƍŌ�͓���ȈӖ�
          String f = params[j];
          delstmt.setString(j, (String)fields.get(f));
        }
        int r = delstmt.executeUpdate();
        log_debug("delete: " + r);
        if (r > 0) {
          addOpLog(opLog, params[0]+":D:", r);
        }
        delstmt.close();
      }
      // �ꕔ�̃e�[�u��ID�̏ꍇ�́A����ȍ폜�������Ȃ�
      if (tableName.equalsIgnoreCase("PROCESSMASTER")) {
        // �v���Z�X��`�̏ꍇ
        String itemdefinitionId = (String)fields.get("ITEMDEFINITIONID");
        if (itemdefinitionId != null) {
          String deletesql = "DELETE FROM ITEMDEFINITIONMASTER WHERE ITEMDEFINITIONID=?";
          log_debug(deletesql);
          // �f�[�^�̍폜
          PreparedStatement delstmt = conn.prepareStatement(deletesql);
          delstmt.setString(1, itemdefinitionId);
          int r = delstmt.executeUpdate();
          log_debug("delete: " + r);
          if (r > 0) {
            addOpLog(opLog, "ITEMDEFINITIONMASTER:D:", r);
          }
          delstmt.close();
        }
      } else if (tableName.equalsIgnoreCase("PAGEMASTER")) {
        // ��ʒ�`�̏ꍇ
        String pageId = (String)fields.get("PAGEID");
        if (pageId != null) {
          int p = pageId.lastIndexOf("_");
          if (p != -1) {
            pageId = pageId.substring(0, p);
          }
          String deletesql = "DELETE FROM PAGEMESSAGE WHERE PAGEMESSAGEID LIKE ?";
          log_debug(deletesql);
          // �f�[�^�̍폜
          PreparedStatement delstmt = conn.prepareStatement(deletesql);
          delstmt.setString(1,  pageId + "@%");
          int r = delstmt.executeUpdate();
          log_debug("delete: " + r);
          if (r > 0) {
            addOpLog(opLog, "PAGEMESSAGE:D:", r);
          }
          delstmt.close();
        }
      }
      // �폜���[�h�̏ꍇ�́A�����ŏI��
      if (mode == 1) {
        return opLog;
      }
    }
    
    // �f�[�^�̑}��
    //log_debug(insertsql.toString());
    PreparedStatement inststmt = null;
    if (stmt == null) {
      // INSERT���̐���
      StringBuffer insertsql = new StringBuffer();
      insertsql.append("INSERT INTO ").append(tableName).append(" (").append(fieldsetstr);
      insertsql.append(") VALUES (").append(valuesstr).append(")");
      
      inststmt = conn.prepareStatement(insertsql.toString());
    } else {
      inststmt = stmt;
      inststmt.clearParameters();
    }
    for (int i = 0; i < fieldSet.size(); ++i) {
      String[] f = (String[])fieldSet.get(i);
      inststmt.setString(i + 1, f[1]);
    }
    int r = inststmt.executeUpdate();
    //inststmt.close();
    //log_debug("insert: " + r);
    if (r > 0) {
      addOpLog(opLog, tableName + ":I:", r);
    }
    opLog.add(inststmt);//opLog�o�R�ŌĂяo�����ɖ߂��Ďg���܂킷�悤�ɂ���
    
    return opLog;
  }
  
  private void addOpLog(Vector opLog, String key, int r) {
    for (int i = 0; i < opLog.size(); ++i) {
      String line = (String)opLog.get(i);
      if (line.startsWith(key)) {
        int n = Integer.parseInt(line.substring(key.length()));
        n += r;
        opLog.set(i, key + n);
        return;
      }
    }
    opLog.add(key + r);
  }
 
  /**
   * �N���X�^�C�v�}�X�^�ɑ��݂��邩�`�F�b�N���A�G���[������΃��b�Z�[�W��Ԃ�
   * @param conn
   * @param classType
   * @return �G���[�������ꍇnull
   */
  private static String checkClassType(Connection conn, String classType) {
    PreparedStatement cstmt = null;
    ResultSet crs = null;
    try {
      // �N���X�^�C�v�}�X�^���݃`�F�b�N
      cstmt = conn.prepareStatement("SELECT PACKAGEID FROM CLASSTYPEMASTER WHERE CLASSTYPE=?");
      cstmt.setString(1, classType);
      crs = cstmt.executeQuery();
      if (!crs.next()) {
        return "�N���X�^�C�v�}�X�^�ɑ��݂��Ȃ�: " + classType;
      }
    } catch(SQLException e) {
      return "SQLException:" + e.getMessage();
    } finally {
      try {
        if (crs != null) {
          crs.close();
        }
        if (cstmt != null) {
          cstmt.close();
        }
      } catch (SQLException e) {}
    }
    return null;
  }
  private String checkPackage(Connection conn, String packageId, String tableId) {
    PreparedStatement cstmt = null;
    ResultSet crs = null;
    try {
      // �p�b�P�[�W�}�X�^���݃`�F�b�N
      cstmt = conn.prepareStatement("SELECT PACKAGEID FROM PACKAGEMASTER WHERE PACKAGEID=?");
      cstmt.setString(1, packageId);
      crs = cstmt.executeQuery();
      if (!crs.next()) {
        // �p�b�P�[�W�}�X�^�ɑ��݂��Ȃ�
        return "�p�b�P�[�W�}�X�^�ɑ��݂��Ȃ�: " + packageId;
      }
      // �G���[�������ꍇ�́A����Ƀp�b�P�[�W�g�p�\�敪���`�F�b�N
      if (packageUseClassCheck && tableId != null) {
        String useClass = null;
        if (tableId.startsWith("TABLE")) {
          useClass = "USETABLECLASS";
        } else if (tableId.startsWith("FUNCTION")) {
          useClass = "USEFUNCTIONCLASS";
        } else if (tableId.startsWith("PROCESS")) {
          useClass = "USEPROCESSCLASS";
        } else if (tableId.startsWith("PAGE")) {
          useClass = "USEPAGECLASS";
        } else if (tableId.startsWith("CLASSTYPE")) {
          useClass = "USECLASSTYPECLASS";
        }
        
        if (useClass != null) {
          crs.close();
          cstmt.close();
          cstmt = conn.prepareStatement("SELECT PACKAGEID,VALUE FROM PACKAGEINFO WHERE PACKAGEID=? AND PROPERTYID=?");
          cstmt.setString(1, packageId);
          cstmt.setString(2, useClass);
          crs = cstmt.executeQuery();
          if (crs.next()) {
            String uc = crs.getString(2);
            if (uc != null && uc.equals("0")) {
              return "�g�p�p�b�P�[�W���s��: " + packageId;
            }
          }
        }
      }
    } catch(SQLException e) {
      return "SQLException:" + e.getMessage();
    } finally {
      try {
        if (crs != null) {
          crs.close();
        }
        if (cstmt != null) {
          cstmt.close();
        }
      } catch (SQLException e) {}
    }
    return null;
  }
  /**
   * �f�[�^�t�B�[���hID�̑��݃`�F�b�N
   * @param conn
   * @param dataFieldId
   * @return
   */
  private static String checkDataField(Connection conn, String dataFieldId) {
    PreparedStatement cstmt = null;
    ResultSet crs = null;
    try {
      if (dataFieldId.startsWith("-")
      /* �ȉ���������ID�͑��݃`�F�b�N���Ȃ� */
      || dataFieldId.equals("SKEY")
      || dataFieldId.equals("PKEY")
      || dataFieldId.equals("LIST")
      || (dataFieldId.startsWith("LIST") && dataFieldId.length() == 5)
      || dataFieldId.equals("LINE")
      || (dataFieldId.startsWith("LINE") && dataFieldId.length() == 5)
      || dataFieldId.equals("ENTITY")
      || (dataFieldId.startsWith("ENTITY") && dataFieldId.length() == 7)
      || dataFieldId.equals("HEAD")
      || dataFieldId.equals("VIEW")
      ) {
        return null;
      }
      if (dataFieldId.indexOf(".") != -1) {
        dataFieldId = dataFieldId.substring(dataFieldId.lastIndexOf(".") + 1);
      }
      // �f�[�^�t�B�[���h�}�X�^���݃`�F�b�N
      cstmt = conn.prepareStatement("SELECT DATAFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID=?");
      cstmt.setString(1, dataFieldId);
      crs = cstmt.executeQuery();
      if (!crs.next()) {
        // �N���X�^�C�v�}�X�^�ɑ��݂��Ȃ�
        return "�f�[�^�t�B�[���h�}�X�^�ɑ��݂��Ȃ�: " + dataFieldId;
      }
    } catch(SQLException e) {
      return "SQLException:" + e.getMessage();
    } finally {
      try {
        if (crs != null) {
          crs.close();
        }
        if (cstmt != null) {
          cstmt.close();
        }
      } catch (SQLException e) {}
    }
    return null;
  }
  
  /**
   * �֘A����e�[�u���̃e�[�u��ID�A�L�[����Ԃ�
   * @param tableName ��ʃe�[�u��ID
   * @return �e�[�u���L�[����Vector��Ԃ�
   */
  private Vector getRelationParams(Connection conn, String tableName) {
    Vector sqls = new Vector();
    // �e�[�u��ID�A�֘A�L�[ID�A�\�[�g���i�L�[�ɑ΂���1�s�����Ԃ��Ȃ��ꍇ��null�j
    if (tableName.equalsIgnoreCase("FUNCTIONMASTER")) {
      sqls.add(new String[]{"FUNCTIONMASTER","FUNCTIONID",null});
      sqls.add(new String[]{"FUNCTIONNAME","FUNCTIONID","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"FUNCTIONCOMPOSITIONMASTER","FUNCTIONID","ORDER BY FUNCTIONCOMPOSITIONCLASS,FUNCTIONCOMPOSITIONID"});
    } else if (tableName.equalsIgnoreCase("PROCESSMASTER")) {
      sqls.add(new String[]{"PROCESSMASTER","PROCESSID",null});
      sqls.add(new String[]{"PROCESSNAME","PROCESSID","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"ITEMDEFINITIONMASTER","ITEMDEFINITIONID","ORDER BY ITEMID"});
      sqls.add(new String[]{"PROCESSDEFINITIONMASTER","PROCESSID","ORDER BY LINEID"});
      sqls.add(new String[]{"PROCESSITEMRELDEFMASTER","PROCESSID","ORDER BY LINEID"});
    } else if (tableName.equalsIgnoreCase("PAGEMASTER")) {
      sqls.add(new String[]{"PAGEMASTER","PAGEID",null});
      sqls.add(new String[]{"PAGENAME","PAGEID","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"VIEWPAGEMASTER","PAGEID","ORDER BY OBJECTID"});
      sqls.add(new String[]{"VIEWPAGEINFO","PAGEID","ORDER BY OBJECTID,PROPERTYID"});
      sqls.add(new String[]{"PAGEMESSAGE","PAGEMESSAGEID","ORDER BY LANGID,PAGEMESSAGEID"});
    } else if (tableName.equalsIgnoreCase("TABLEMASTER")) {
      sqls.add(new String[]{"TABLEMASTER","TABLEID",null});
      sqls.add(new String[]{"TABLENAME","TABLEID","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"TABLEINFO","TABLEID","ORDER BY PROPERTYID"});
      sqls.add(new String[]{"TABLELAYOUTMASTER","TABLEID", "ORDER BY DATAFIELDORDER"});
    } else if (tableName.equalsIgnoreCase("DATAFIELDMASTER")) {
      sqls.add(new String[]{"DATAFIELDMASTER","DATAFIELDID",null});
      sqls.add(new String[]{"DATAFIELDNAME","DATAFIELDID","ORDER BY DISPLANGID,PROPERTYID"});
      if (hasDataFieldInfo) {
        sqls.add(new String[]{"DATAFIELDINFO","DATAFIELDID","ORDER BY PROPERTYID"});
      }
      sqls.add(new String[]{"DATAFIELDVALUEMASTER","DATAFIELDID","ORDER BY DATAFIELDVALUE"});
      sqls.add(new String[]{"DATAFIELDVALUENAME","DATAFIELDID","ORDER BY DISPLANGID,DATAFIELDVALUE,PROPERTYID"});
    } else if (tableName.equalsIgnoreCase("CLASSTYPEMASTER")) {
      sqls.add(new String[]{"CLASSTYPEMASTER","CLASSTYPE",null});
      sqls.add(new String[]{"CLASSTYPENAME","CLASSTYPE","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"CLASSTYPEINFO","CLASSTYPE","ORDER BY PROPERTYID"});
      sqls.add(new String[]{"CLASSPROPERTYMASTER","CLASSTYPE","ORDER BY CLASSPROPERTYID"});
      sqls.add(new String[]{"CLASSPROPERTYNAME","CLASSTYPE","ORDER BY DISPLANGID,CLASSPROPERTYID,PROPERTYID"});
      sqls.add(new String[]{"CLASSPROPERTYINFO","CLASSTYPE","ORDER BY PROPERTYID"});
    } else if (tableName.equalsIgnoreCase("MENUMASTER")) {
      sqls.add(new String[]{"MENUMASTER","COMPANYID","MENUID",null});
      sqls.add(new String[]{"MENUNAME","COMPANYID","MENUID","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"MENUITEMMASTER","COMPANYID","MENUID","ORDER BY MENUITEMID"});
      sqls.add(new String[]{"MENUITEMNAME","COMPANYID","MENUID","ORDER BY DISPLANGID,MENUITEMID,PROPERTYID"});
      sqls.add(new String[]{"MENUITEMINFO","COMPANYID","MENUID","ORDER BY MENUITEMID,PROPERTYID"});
    } else if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
      sqls.add(new String[]{"MENUITEMMASTER","COMPANYID","MENUID","MENUITEMID",null});
      sqls.add(new String[]{"MENUITEMNAME","COMPANYID","MENUID","MENUITEMID","ORDER BY DISPLANGID,PROPERTYID"});
      sqls.add(new String[]{"MENUITEMINFO","COMPANYID","MENUID","MENUITEMID","ORDER BY PROPERTYID"});
    }
    if (sqls.size() == 0) {
      // ���̑��̃e�[�u���̏ꍇ�́A�v���C�}���L�[��Ԃ�
      Vector pkeys = null;
      boolean hasName = false;
      boolean hasInfo = false;
      Hashtable tableLayoutInfo = getTableLayout(conn, tableName);
      if (tableLayoutInfo != null) {
        pkeys = (Vector)tableLayoutInfo.get("$pkey$");
        Vector names = (Vector)tableLayoutInfo.get("$name$");
        if (names != null && names.size() > 0) {
          hasName = true;
        }
        Vector infos = (Vector)tableLayoutInfo.get("$info$");
        if (infos != null && infos.size() > 0) {
          hasInfo = true;
        }
      }
      String baseName = tableName.toUpperCase();
      if (baseName.endsWith("MASTER")) {
        baseName = baseName.substring(0, baseName.length() - 6);
      }
      if (pkeys == null) {
        // �e�[�u�����C�A�E�g�}�X�^����擾�ł��Ȃ��ꍇ�͕�����`���擾�iDBMS�ˑ��j
        pkeys = getPrimaryKeys(tableName);
        if (pkeys != null) {
          Vector name = getObjectNames(baseName + "%", OBJ_TYPE_PTABLE);
          for (int i = 0; i < name.size(); ++i) {
            String tmp = (String)name.get(i);
            if (tmp.toUpperCase().equals(baseName + "NAME")) {
              // NAME�e�[�u�������������ꍇ
              hasName = true;
            } else if (tmp.toUpperCase().equals(baseName + "INFO")) {
              // INFO�e�[�u�������������ꍇ
              hasInfo = true;
            }
          }
        }
      }
      if (pkeys != null && pkeys.size() > 0) {
        String[] params = new String[pkeys.size() + 2];
        params[0] = tableName;
        for (int i = 0; i < pkeys.size(); ++i) {
          params[i + 1] = (String)pkeys.get(i);
        }
        params[params.length - 1] = null;
        sqls.add(params);

        // NAME,INFO�e�[�u��������΁A������v���C�}���L�[�Œǉ�
        if (hasName) {
          // NAME�e�[�u��������ꍇ
          params = new String[pkeys.size() + 2];
          params[0] = baseName + "NAME";
          for (int j = 0; j < pkeys.size(); ++j) {
            params[j + 1] = (String)pkeys.get(j);
          }
          params[params.length - 1] = "ORDER BY DISPLANGID,PROPERTYID";
          sqls.add(params);
        }
        if (hasInfo) {
          // INFO�e�[�u��������ꍇ
          params = new String[pkeys.size() + 2];
          params[0] = baseName + "INFO";
          for (int j = 0; j < pkeys.size(); ++j) {
            params[j + 1] = (String)pkeys.get(j);
          }
          params[params.length - 1] = "ORDER BY PROPERTYID";
          sqls.add(params);
        }
      }
    }
    return sqls;
  }
  
  /**
   * �C���|�[�g���O���o�͂���(����MBB�V�X�e���}�X�^�p)
   * @param conn
   * @param tableName
   * @param columnNames
   * @param line
   * @param timestamp
   * @param mode
   * @param updateInfos
   * @throws IOException
   * @throws SQLException
   */
  private void insertImportLog(Connection conn, String tableName, String columnNames, String line, long timestamp, int mode, String[] updateInfos) throws IOException, SQLException {
    if (!importLogEnabled) {
      log_debug("importLog disabled.");
      return;
    }
    PreparedStatement stmt = null;
    try {
      Vector params = getRelationParams(conn, tableName);
      if (params == null || params.size() == 0) {
        // �p�����[�^��񂪎擾�ł��Ȃ��e�[�u���Ɋւ��Ă͉������Ȃ�
        return;
      }
      String[] param = (String[])params.get(0);
      int tsIndex = -1;
      if (columnNames != null) {
        tsIndex = Arrays.asList(columnNames.split(",")).indexOf("TIMESTAMPVALUE");
      }
      String[] values = null;
      if (line != null) {
        values = line.split(",", -1);
      }
      
      String key1 = " ";
      if (values != null && values.length > 0 && param.length > 1 && param[1] != null) {
        key1 = values[0];
      }
      String key2 = " ";
      if (values != null && values.length > 1 && param.length > 2 && param[2] != null) {
        key2 = values[1];
      }
      String key3 = " ";
      if (values != null && values.length > 2 && param.length > 3 && param[3] != null) {
        key3 = values[2];
      }
      
      String oldTs = null;
      PreparedStatement oldStmt = null;
      ResultSet oldRs = null;
      try {
        // �Â��^�C���X�^���v���擾����
        StringBuffer oldsql = new StringBuffer();
        oldsql.append("SELECT TIMESTAMPVALUE FROM ").append(tableName);
        oldsql.append(" WHERE ");
        Vector keyValues = new Vector();
        for (int i = 1; i < param.length; ++i) {
          String key = param[i];
          if (key == null) {
            break;
          }
          if (i > 1) {
            oldsql.append(" AND ");
          }
          oldsql.append(key).append("=?");
          keyValues.add(values[i - 1]);
        }
        String sql = oldsql.toString();
        log_debug(sql);
        oldStmt = conn.prepareStatement(sql);
        for (int i = 0; i < keyValues.size(); ++i) {
          oldStmt.setString(i + 1, (String)keyValues.get(i));
        }
        oldRs = oldStmt.executeQuery();
        if (oldRs.next()) {
          oldTs = oldRs.getString(1);
        }
      } catch (Exception e) {
      } finally {
        if (oldRs != null) {
          try {
            oldRs.close();
          } catch (SQLException se) {}
        }
        if (oldStmt != null) {
          try {
            oldStmt.close();
          } catch (SQLException se) {}
        }
      }
      
      String sql = "INSERT INTO " + DBACCESS_IMPORTLOG + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
      stmt = conn.prepareStatement(sql);
      stmt.setString(1, updateInfos[0]); // REMOTEADDRESS
      stmt.setString(2, getExecTimestampValue(updateInfos)); // EXECTIEMSTAMPVALUE
      stmt.setString(3, tableName); // TABLEID
      stmt.setString(4, key1); // KEY1
      stmt.setString(5, key2); // KEY2
      stmt.setString(6, key3); // KEY3
      StringBuffer logInfos = new StringBuffer();
      if (mode == 1) {
        logInfos.append("DELETE,");
      }
      if (oldTs != null) {
        logInfos.append("OLDTS=").append(oldTs).append(",");
      }
      if (mode == 0 && tsIndex != -1 && values != null && values.length >= tsIndex) {
        logInfos.append("TS=").append(values[tsIndex]);
      }
      if (logInfos.length() == 0) {
        logInfos.append(" ");
      }
      stmt.setString(7, logInfos.toString()); // LOGINFOS
      stmt.setString(8, updateInfos[1]); // UPDATECOMPANYID
      stmt.setString(9, updateInfos[2]); // UPDATEUSERID
      stmt.setString(10, "DBACCESS"); // UPDATEPROCESSID
      stmt.setString(11, DbAccessUtils.toTimestampString(timestamp)); // TIMESTAMPVALUE
      stmt.executeUpdate();
    } finally {
      if (stmt != null) {
        stmt.close();
      }
    }
  }
  

  private void insertSQLLog(String sql, String key1, String key2, String key3, String[] updateInfos) {
    log_debug("insertSQLLog(): " + sql);
    if (!importLogEnabled) {
      log_debug("importLog disabled.");
      return;
    }
    if (key1 == null) {
      key1 = " ";
    }
    if (key2 == null) {
      key2 = " ";
    }
    if (key3 == null) {
      key3 = " ";
    }
    PreparedStatement stmt = null;
    
    String insertSql = "INSERT INTO " + DBACCESS_IMPORTLOG + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      String ts = getExecTimestampValue(updateInfos);
      stmt = conn.prepareStatement(insertSql);
      stmt.setString(1, updateInfos[0]); // REMOTEADDRESS
      stmt.setString(2, ts); // EXECTIEMSTAMPVALUE
      stmt.setString(3, trim(DbAccessUtils.getTableNameFromSQL(sql), 100, DATABASE_CHARSET)); // TABLEID
      stmt.setString(4, trim(key1, 100, DATABASE_CHARSET)); // KEY1
      stmt.setString(5, trim(key2, 100, DATABASE_CHARSET)); // KEY2
      stmt.setString(6, trim(key3, 100, DATABASE_CHARSET)); // KEY3
      stmt.setString(7, trim(sql, 1000, DATABASE_CHARSET)); // LOGINFOS
      stmt.setString(8, updateInfos[1]); // UPDATECOMPANYID
      stmt.setString(9, updateInfos[2]); // UPDATEUSERID
      stmt.setString(10, "DBACCESS"); // UPDATEPROCESSID
      stmt.setString(11, ts); // TIMESTAMPVALUE
      stmt.executeUpdate();
      stmt.close();
      stmt = null;
      conn.commit();
    } catch (SQLException e) {
      log_debug(e);
      try {
        conn.rollback();
      } catch (SQLException e1) {}
    } finally {
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e1) {}
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {}
      }
    }
  }
  private void insertServiceLog(HttpServletRequest request, String loginMode) {
    log_debug("insertServiceLog()");
    if (!serviceLogEnabled) {
      log_debug("serviceLog disabled.");
      return;
    }
    PreparedStatement stmt = null;
    
    // IPADDRESS,EXECTIMESTAMPVALUE,THREADNAME,APPLICATIONID,PROCESSID,PROCESSMODE,STATUS,EXECTIMEMS,COMPANYID,USERID,REMARK 
    String insertSql = "INSERT INTO " + DBACCESS_SERVICELOG + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      String remoteAddr = request.getRemoteAddr();
      String ts = DbAccessUtils.toTimestampString(System.currentTimeMillis());
      String threadName = Thread.currentThread().getName();
      HttpSession session = request.getSession();
      Hashtable sessionData = (Hashtable)session.getAttribute("GLOBALSESSIONDATA");
      if (sessionData == null) {
        sessionData = (Hashtable)session.getAttribute("SESSIONDATA");
      }
      String companyId = "MBB";
      String userId = loginMode;
      if (sessionData != null) {
        String tmp = (String)sessionData.get("COMPANYID");
        if (tmp != null && tmp.trim().length() > 0) {
          companyId = tmp;
        }
        tmp = (String)sessionData.get("USERID");
        if (tmp != null && tmp.trim().length() > 0) {
          userId = tmp;
        }
      }
      stmt = conn.prepareStatement(insertSql);
      stmt.setString(1, trim(remoteAddr, 20, DATABASE_CHARSET)); // REMOTEADDRESS
      stmt.setString(2, ts); // EXECTIEMSTAMPVALUE
      stmt.setString(3, trim(threadName, 100, DATABASE_CHARSET)); // THREADNAME
      stmt.setString(4, trim("DBACCESS", 50, DATABASE_CHARSET)); // APPLICATIONID
      stmt.setString(5, " "); // PROCESSID
      stmt.setString(6, trim("3", 1, DATABASE_CHARSET)); // PROCESSMODE
      stmt.setString(7, trim("OK", 20, DATABASE_CHARSET)); // STATUS
      stmt.setString(8, "0"); // EXECTIMEMS
      stmt.setString(9, companyId); // UPDATECOMPANYID
      stmt.setString(10, userId); // UPDATEUSERID
      stmt.setString(11, loginMode); // REMARK
      stmt.executeUpdate();
      stmt.close();
      stmt = null;
      conn.commit();
    } catch (SQLException e) {
      log_debug(e);
      try {
        conn.rollback();
      } catch (SQLException e1) {}
    } finally {
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e1) {}
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {}
      }
    }
  }
  
  /**
   * updateInfos��4�Ԗڂ��g���ďd���̂Ȃ�(�ł��낤)Timestamp���擾����
   * @param updateInfos
   * @return
   */
  private static String getExecTimestampValue(String[] updateInfos) {
    long systs = System.currentTimeMillis();
    String newts = DbAccessUtils.toTimestampString(systs);
    if (updateInfos == null ||  updateInfos.length < 4) {
      return newts;
    }
    if (updateInfos[3] == null) {
      updateInfos[3] = newts;
      return newts;
    }
    while (DbAccessUtils.compareTimestamp(newts, updateInfos[3]) <= 0) {
      // �Ō�Ɏg�p�����^�C���X�^���v�Ɠ����ꍇ
      systs++;
      newts = DbAccessUtils.toTimestampString(systs);
    }
    updateInfos[3] = newts;
    return newts;
  }
  /**
   * {name,value}��Vector�𐶐����ĕԂ�
   * @param columns
   * @param values
   * @return
   */
  private Vector getFieldSet(String columns, String values) {
    Vector colset = new Vector();
    StringTokenizer st = new StringTokenizer(columns, ",");
    int start = 0;
    while (st.hasMoreTokens()) {
      String name = st.nextToken();
      String value = null;
      if (start < values.length()) {
        int end = getNextValueEnd(values, start);
        value = DbAccessUtils.unescapeCSV(values.substring(start, end));
        start = end + 1;
      }
      colset.add(new String[]{name, value});
    }
    return colset;
  }
  
  // ���̃J���}�̈ʒuor�I���̈ʒu��Ԃ�
  private int getNextValueEnd(String line, int start) {
    boolean dq = false;
    int i = start;
    for (; i < line.length(); ++i) {
      char c = line.charAt(i);
      if (dq) {
        if (c == '"') {
          dq = false;
        }
      } else {
        if (c == ',') {
          break;
        } else if (c == '"') {
          dq = true;
        }
      }
    }
    return i;
  }
    
  private File createTempFile(String baseFileName) throws IOException {
    String suffix = null;
    int sufp = baseFileName.lastIndexOf(".");
    if (sufp != -1) {
      suffix = baseFileName.substring(sufp);
    }
    File tempFile = File.createTempFile("dbaccess", suffix);
    tempFiles.put(tempFile.getName(), tempFile);
    tempOrigFiles.put(tempFile.getName(), baseFileName);
    return tempFile;
  }
  
  private HttpServletRequest handleUploadFile(HttpServletRequest request) {
    // multipart/form-data�̏ꍇ�AHttpServletRequest�̃��b�p��Ԃ�
    final HttpServletRequest rawRequest = request;
    return new HttpServletRequest() {
      private HttpServletRequest _request = rawRequest;
      private Hashtable parameters = new Hashtable();
      private Hashtable uploadFiles = new Hashtable();
      private static final int BUFFER_SIZE = 1024 * 1024;
      private byte[] readBuffer = null;
      private int readBufferStart = 0;
      private static final String CT_MULTIPART = "multipart/form-data";
      {
        if (_request.getContentType() != null && _request.getContentType().toLowerCase().indexOf(CT_MULTIPART) != -1) {
          // �}���`�p�[�g�̏���
          String tmp = _request.getContentType().substring(CT_MULTIPART.length()+1);
          int p = tmp.toLowerCase().lastIndexOf("boundary=");
          String boundary = "--" + tmp.substring(p + "boundary=".length());
          byte[] boundaryBytes = boundary.getBytes();

          log_debug("=== upload start ===");
          try {
            // inputstream���o�b�t�@�Ɋi�[
            InputStream inputStream = _request.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
              int r = inputStream.read(buffer, 0, buffer.length);
              if (r == -1) {
                break;
              }
              if (r > 0) {
                baos.write(buffer, 0, r);
              }
            }
            readBuffer = baos.toByteArray();
            readBufferStart = 0;
            baos = null;
            inputStream.close();
            
            String line = readLine();
            while (line != null) {
              log_debug("LINE:" + line);
              if (line.startsWith(boundary)) {
                if (line.equals(boundary + "--")) {
                  // �I��
                  break;
                }
                String header = readLine();
                String name = null;
                String filename = null;
                StringBuffer value = new StringBuffer();
                // �w�b�_�̏���
                while (header != null) {
                  log_debug("HEAD:" + header);
                  if (header.trim().length() == 0) {
                    break;
                  }
                  if (header.toLowerCase().startsWith("content-disposition: form-data;")) {
                    StringTokenizer st = new StringTokenizer(header, ";");
                    while (st.hasMoreTokens()) {
                      String tk = st.nextToken();
                      int peq = tk.indexOf("=");
                      if (peq != -1) {
                        String tmpName = tk.substring(0, peq).trim();
                        String tmpValue = tk.substring(peq + 1).trim();
                        if (tmpName.equalsIgnoreCase("name")) {
                          if (tmpValue.startsWith("\"")) {
                            tmpValue = tmpValue.substring(1, tmpValue.length() - 1);
                          }
                          name = tmpValue;
                        } else if (tmpName.equalsIgnoreCase("filename")) {
                          if (tmpValue.startsWith("\"")) {
                            tmpValue = tmpValue.substring(1, tmpValue.length() - 1);
                          }
                          filename = tmpValue;
                          if (filename != null) {
                            if (filename.lastIndexOf("/") != -1) {
                              filename = filename.substring(filename.lastIndexOf("/") + 1);
                            }
                            if (filename.lastIndexOf("\\") != -1) {
                              filename = filename.substring(filename.lastIndexOf("\\") + 1);
                            }
                          }
                        }
                      }
                    }
                  }
                  header = readLine();
                }
                if (filename != null && filename.trim().length() > 0) {
                  byte[] b = readBytes(boundaryBytes);
                  File tempFile = createTempFile(filename);
                  FileOutputStream fos = new FileOutputStream(tempFile);
                  fos.write(b, 0, b.length - 2);
                  fos.close();
                  log_debug("upload file: filename=[" + filename + "],size=[" + (b.length - 2) + "]");
                  uploadFiles.put(filename, tempFile.getName());
                  value.append(filename);
                  line = boundary;
                } else {
                  line = readLine();
                }
                while (line != null && !line.startsWith(boundary)) {
                  if (value.length() > 0) {
                    value.append("\n");
                  }
                  value.append(line);
                  line = readLine();
                }
                if (name != null) {
                  String[] values = null;
                  values = (String[])parameters.get(name);
                  if (values != null) {
                    String[] newvalues = new String[values.length + 1];
                    System.arraycopy(values, 0, newvalues, 0, values.length);
                    newvalues[values.length] = value.toString();
                    values = newvalues;
                    log_debug("param: name=[" + name + "],value=[" + value + "],index=[" + values.length + "]");
                  } else {
                    values = new String[] {value.toString()};
                    log_debug("param: name=[" + name + "],value=[" + value + "]");
                  }
                  parameters.put(name, values);
                }
              }
            }
            log_debug("=== upload end ===");

          } catch(Exception e) {
            log_debug(e);
          } finally{
          }
        } else {
          // �ʏ�̃��N�G�X�g
          
        }
      }
      
      // �o�b�t�@����1�s��Ԃ�
      private String readLine() {
        String line = null;
        for (int i = readBufferStart; i < readBuffer.length; ++ i) {
          if (readBuffer[i] == 0x0a) {
            if (i > readBufferStart && readBuffer[i - 1] == 0x0d) {
              try {
                line = new String(readBuffer, readBufferStart, i - readBufferStart - 1, DEFAULT_CHARSET);
              } catch (UnsupportedEncodingException e) {
                line = new String(readBuffer, readBufferStart, i - readBufferStart - 1);
              }
            } else {
              try {
                line = new String(readBuffer, readBufferStart, i - readBufferStart, DEFAULT_CHARSET);
              } catch (UnsupportedEncodingException e) {
                line = new String(readBuffer, readBufferStart, i - readBufferStart);
              }
            }
            readBufferStart = i + 1;
            break;
          }
        }
        if (line == null) {
          // �f�[�^�̏I���ɒB�����ꍇ
          readBufferStart = readBuffer.length;
        }
        return line;
      }
      
      // �o�b�t�@���玟��boundary�܂Ńo�C�g�f�[�^���擾���Ԃ�
      private byte[] readBytes(byte[] boundary) {
        byte[] bytes = null;
        for (int i = readBufferStart; i < readBuffer.length; ++i) {
          if (readBuffer[i] == boundary[0]) {
            // boundary�̐擪�Ɉ�v�����ꍇ
            boolean found = true;
            for (int j = 0; j < boundary.length; ++j) {
              if (readBuffer[i + j] != boundary[j]) {
                // �r���͈�v���Ȃ��ꍇ
                found = false;
                break;
              }
            }
            if (found) {
              bytes = new byte[i - readBufferStart];
              System.arraycopy(readBuffer, readBufferStart, bytes, 0, i - readBufferStart);
              readBufferStart = i;
              break;
            }
          }
        }
        return bytes;
      }
      
      
      public String getAuthType() {
        return _request.getAuthType();
      }
      public String getContextPath() {
        return _request.getContextPath();
      }
      public Cookie[] getCookies() {
        return _request.getCookies();
      }
      public long getDateHeader(String arg0) {
        return _request.getDateHeader(arg0);
      }
      public String getHeader(String arg0) {
        return _request.getHeader(arg0);
      }
      public Enumeration getHeaderNames() {
        return _request.getHeaderNames();
      }
      public Enumeration getHeaders(String arg0) {
        return _request.getHeaders(arg0);
      }
      public int getIntHeader(String arg0) {
        return _request.getIntHeader(arg0);
      }
      public String getMethod() {
        return _request.getMethod();
      }
      public String getPathInfo() {
        return _request.getPathInfo();
      }
      public String getPathTranslated() {
        return _request.getPathTranslated();
      }
      public String getQueryString() {
        return _request.getQueryString();
      }
      public String getRemoteUser() {
        return _request.getRemoteUser();
      }
      public String getRequestURI() {
        return _request.getRequestURI();
      }
      public StringBuffer getRequestURL() {
        return _request.getRequestURL();
      }
      public String getRequestedSessionId() {
        return _request.getRequestedSessionId();
      }
      public String getServletPath() {
        return _request.getServletPath();
      }
      public HttpSession getSession() {
        return _request.getSession();
      }
      public HttpSession getSession(boolean arg0) {
        return _request.getSession(arg0);
      }
      public Principal getUserPrincipal() {
        return _request.getUserPrincipal();
      }
      public boolean isRequestedSessionIdFromCookie() {
        return _request.isRequestedSessionIdFromCookie();
      }
      public boolean isRequestedSessionIdFromURL() {
        return false;
      }
      public boolean isRequestedSessionIdFromUrl() { // deprecated
        //return _request.isRequestedSessionIdFromUrl();
        return false;
      }
      public boolean isRequestedSessionIdValid() {
        return _request.isRequestedSessionIdValid();
      }
      public boolean isUserInRole(String arg0) {
        return _request.isUserInRole(arg0);
      }
      public Object getAttribute(String arg0) {
        Object obj = uploadFiles.get(arg0);
        if (obj != null) {
          return obj;
        }
        return _request.getAttribute(arg0);
      }
      public Enumeration getAttributeNames() {
        return _request.getAttributeNames();
      }
      public String getCharacterEncoding() {
        return _request.getCharacterEncoding();
      }
      public int getContentLength() {
        return _request.getContentLength();
      }
      public String getContentType() {
        return _request.getContentType();
      }
      public ServletInputStream getInputStream() throws IOException {
        return _request.getInputStream();
      }
      public String getLocalAddr() { // Servlet API 2.4
        //return _request.getLocalAddr();
        return null;
      }
      public String getLocalName() { // Servlet API 2.4
        //return _request.getLocalName();
        return null;
      }
      public int getLocalPort() { // Servlet API 2.4
        //return _request.getLocalPort();
        return 0;
      }
      public Locale getLocale() {
        return _request.getLocale();
      }
      public Enumeration getLocales() {
        return null;
      }
      public String getParameter(String arg0) {
        String[] values = getParameterValues(arg0);
        if (values == null || values.length == 0) {
          return _request.getParameter(arg0);
        }
        return values[0];
      }
      public Map getParameterMap() {
        return (Map)parameters.clone();
      }
      public Enumeration getParameterNames() {
        return parameters.keys();
      }
      public String[] getParameterValues(String arg0) {
        String[] values = (String[])parameters.get(arg0);
        if (values != null) {
          return values;
        }
        String[] rawvalues = _request.getParameterValues(arg0);
        if (rawvalues != null) {
          values = new String[rawvalues.length];
          for (int i = 0; i < rawvalues.length; ++i) {
            try {
              values[i] = new String(rawvalues[i].getBytes("8859_1"), "UTF-8");
            } catch(Exception e) {
              values[i] = rawvalues[i];
            }
          }
        }
        return values;
      }
      public String getProtocol() {
        return _request.getProtocol();
      }
      public BufferedReader getReader() throws IOException {
        return _request.getReader();
      }
      public String getRealPath(String arg0) { // deprecated
        //return _request.getRealPath(arg0);
        return null;
      }
      public String getRemoteAddr() {
        return _request.getRemoteAddr();
      }
      public String getRemoteHost() {
        return _request.getRemoteHost();
      }
      public int getRemotePort() { // Servlet API 2.4
        return 0;
      }
      public RequestDispatcher getRequestDispatcher(String arg0) {
        return _request.getRequestDispatcher(arg0);
      }
      public String getScheme() {
        return _request.getScheme();
      }
      public String getServerName() {
        return _request.getServerName();
      }
      public int getServerPort() {
        return _request.getServerPort();
      }
      public boolean isSecure() {
        return _request.isSecure();
      }
      public void removeAttribute(String arg0) {
        _request.removeAttribute(arg0);
      }
      public void setAttribute(String arg0, Object arg1) {
        _request.setAttribute(arg0, arg1);
      }
      public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
        _request.setCharacterEncoding(arg0);
      }
    };
  }

  // �e�[�u���f�[�^�̃R�s�[
  private void printCopyTableData(PrintWriter out, String command) {
    //
    Connection connFrom = null;
    Connection connTo = null;
    try {
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "copy"���X�L�b�v
      String table = null;
      if (st.hasMoreTokens()) {
        table = st.nextToken();
      }
      int index = 1;
      if (st.hasMoreTokens()) {
        index = Integer.parseInt(st.nextToken());
      }
      
      connFrom = getConnection(index);
      connFrom.setAutoCommit(false);
      connTo = getConnection();
      if (connTo != null) {
        connTo.setAutoCommit(false);
      }
      if (table != null && connFrom != null && connTo != null) {
        Vector tables = new Vector();
        String[] stables = table.split(",");
        for (int i = 0; i < stables.length; ++i) {
          if (stables[i].toUpperCase().equals("[PROCESS]")) {
            tables.add("PROCESSMASTER");
            tables.add("PROCESSNAME");
            tables.add("ITEMDEFINITIONMASTER");
            tables.add("PROCESSDEFINITIONMASTER");
            tables.add("PROCESSITEMRELDEFMASTER");
          } else if (stables[i].toUpperCase().equals("[PAGE]")) {
            tables.add("PAGEMASTER");
            tables.add("PAGENAME");
            tables.add("PAGEINFO");
            tables.add("VIEWPAGEMASTER");
            tables.add("VIEWPAGEINFO");
            tables.add("PAGEMESSAGE");
          } else if (stables[i].startsWith("[") && stables[i].endsWith("]")) {
            String t = stables[i].substring(1, stables[i].length() - 1);
            tables.add(t + "MASTER");
            tables.add(t + "NAME");
            tables.add(t + "INFO");
          } else {
            tables.add(stables[i]);
          }
        }
        out.println("<pre>");
        out.println(command);
        for (int i = 0; i < tables.size(); ++i) {
          String tableId = (String)tables.get(i);
          try {
            int cnt = copyTableData(connFrom, connTo, tableId);
            out.println(tableId + ": "+ cnt + "�s�R�s�[���܂���");
          } catch (SQLException e) {
            out.println(tableId + ": "+ e.getMessage());
          }
          out.flush();
        }
        out.println("</pre>");
        connTo.commit();
      } else {
        if (table == null) {
          out.print("�e�[�u�������w�肵�Ă��������B");
        } else {
          out.print("�R�s�[�ł��܂���ł����B[" + table + "]");
        }
      }
    } catch(Exception e) {
      printError(out, e);
    } finally {
      if (connFrom != null) {
        try {
          connFrom.close();
        } catch (SQLException e1) {
        }
      }
      if (connTo != null) {
        try {
          connTo.close();
        } catch (SQLException e1) {
        }
      }
    }
    
  }
  
  private static Vector getCompanyTables(Connection conn) throws SQLException {
    // COMPANYID�̊܂܂��TABLELAYOUTMASTER������
    String sql = "SELECT DISTINCT TABLEID, DATAFIELDCLASS FROM TABLELAYOUTMASTER WHERE TABLEID IN (SELECT TABLEID FROM TABLELAYOUTMASTER WHERE DATAFIELDID='COMPANYID') ORDER BY TABLEID";
    PreparedStatement stmt = conn.prepareStatement(sql);
    Vector tables = new Vector();
    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
      String tableId = rs.getString(1);
      String dfclass = rs.getString(2);
      if (dfclass != null && dfclass.equals("1")) {
        tables.add(DbAccessUtils.getBaseTableName(tableId));
      } else if (dfclass != null && dfclass.equals("3")) {
        tables.add(DbAccessUtils.getNameTableName(tableId));
      } else if (dfclass != null && dfclass.equals("4")) {
        tables.add(DbAccessUtils.getInfoTableName(tableId));
      }
    }
    rs.close();
    stmt.close();
    return tables;
  }
  
  private static String[] getCompanySelectItems(Connection conn, String tableId) throws SQLException {
    String sql = "SELECT DATAFIELDID, (SELECT PHYSICALFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) PHYSICALFIELDID, DATAFIELDCLASS FROM TABLELAYOUTMASTER a WHERE TABLEID=? ORDER BY DATAFIELDORDER";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setString(1, tableId);
    StringBuffer sb = new StringBuffer();
    StringBuffer pkey = new StringBuffer();
    sb.append("COMPANYID");
    pkey.append("COMPANYID");
    boolean name = false;
    boolean info = false;
    boolean deleteclass = false;
    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
      String dataFieldId = rs.getString(1);
      String physicalFieldId = rs.getString(2);
      String dataFieldClass = rs.getString(3);
      if (physicalFieldId != null && physicalFieldId.trim().length() > 0 && !physicalFieldId.equals(dataFieldId)) {
        // �f�[�^����ID�ƕ�������ID���قȂ�ꍇ
        dataFieldId = physicalFieldId;
      }
      if (dataFieldId.equals("COMPANYID")) {
        if (!dataFieldClass.equals("1")) {
          // �L�[�ȊO�̏ꍇ�͑ΏۊO�Ƃ���
          sb.setLength(0);
          break;
        }
        continue;
      }
      if (dataFieldClass != null && (dataFieldClass.equals("1") || dataFieldClass.equals("2"))) {
        // ��{�t�B�[���h
        sb.append(",");
        sb.append(dataFieldId);
        if (dataFieldId.equals("DELETECLASS")) {
          deleteclass = true;
        }
      }
      if (dataFieldClass != null && dataFieldClass.equals("1")) {
        // �v���C�}���L�[�t�B�[���h
        pkey.append(",");
        pkey.append(dataFieldId);
      }
      if (dataFieldClass != null && dataFieldClass.equals("3")) {
        // ���̃t�B�[���h
        name = true;
      }
      if (dataFieldClass != null && dataFieldClass.equals("4")) {
        // ���t�B�[���h
        info = true;
      }

    }
    rs.close();
    stmt.close();
    String nameStr = "";
    String infoStr = "";
    if (name) {
      nameStr = pkey.toString() + ",DISPLANGID,PROPERTYID,NAMEVALUE";
      if (deleteclass) {
        nameStr += ",DELETECLASS";
      }
    }
    if (info) {
      infoStr = pkey.toString() + ",PROPERTYID,VALUE";
      if (deleteclass) {
        infoStr += ",DELETECLASS";
      }
    }
    return new String[] {sb.toString(), nameStr, infoStr};
  }
  
  // �e�[�u���f�[�^�̃R�s�[
  private void printCompany(PrintWriter out, String command) {
    //
    Connection conn = null;
    String tableId = null;
    try {
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "copy"���X�L�b�v
      String subcmd = null;
      String id1 = null;
      String id2 = null;
      String table = null;
      if (st.hasMoreTokens()) {
        subcmd = st.nextToken();
      }
      if (st.hasMoreTokens()) {
        id1 = st.nextToken();
      }
      if (st.hasMoreTokens()) {
        id2 = st.nextToken();
      }
      if (st.hasMoreTokens()) {
        table = st.nextToken();
      }
      conn = getConnection();
      conn.setAutoCommit(false);
      if (subcmd != null) {
        Vector tables = null;
        if (table == null) {
          tables = getCompanyTables(conn);
        } else {
          tables = getObjectNames(table, OBJ_TYPE_PTABLE);
          Vector allTables = getCompanyTables(conn);
          for (Iterator ite = tables.iterator(); ite.hasNext(); ) {
            if (!allTables.contains(ite.next())) {
              ite.remove();
            }
          }
        }
        if (subcmd.equalsIgnoreCase("copy") && id1 != null && id2 != null) {
          // COMPANY COPY <FROMID> <TOID>
          PreparedStatement stmt = null;
          out.println("<pre>");
          if (tables.size() == 0) {
            out.println("���ʑΏۃe�[�u����������܂���[" + table + "]");
          }
          for (Iterator ite = tables.iterator(); ite.hasNext();) {
            tableId = (String)ite.next();
            if (tableId.endsWith("NAME") || tableId.endsWith("INFO") || tableId.indexOf("_") != -1) {
              continue;
            }
            String cntsql = "SELECT COUNT(*) FROM " + tableId + " WHERE COMPANYID=?";
            try {
              int cnt = 0;
              stmt = conn.prepareStatement(cntsql);
              stmt.setString(1, id1);
              ResultSet rs = stmt.executeQuery();
              if (rs.next()) {
                cnt = rs.getInt(1);
              }
              rs.close();
              stmt.close();
              if (cnt == 0) {
                // 0���Ȃ玟�̃e�[�u����
                out.println(tableId + ": �Ώۃf�[�^�Ȃ�[COMPANYID=" + id1 + "]");
                continue;
              }
            } catch (Exception e) {
              // �G���[�Ȃ玟�̃e�[�u����
              continue;
            }
            
            String[] fields = getCompanySelectItems(conn, tableId);
            if (!fields[0].startsWith("COMPANYID")) {
              // �擪��COMPANYID�łȂ��ꍇ�̓X�L�b�v
              continue;
            }
            boolean hasName = false;
            boolean hasInfo = false;
            if (fields[1].length() > 0) {
              hasName = true;
            }
            if (fields[2].length() > 0) {
              hasInfo = true;
            }
            // ��U���ʐ���폜
            String delsql = "DELETE FROM " + tableId + " WHERE COMPANYID=?";
            out.println(delsql);
            stmt = conn.prepareStatement(delsql);
            stmt.setString(1, id2);
            int delcnt = 0;
            try {
              delcnt = stmt.executeUpdate();
            } catch (SQLException e) {
              out.println("<font color=\"" + ERROR_COLOR + "\">" + e.getMessage() + "</font>\n");
              continue;
            }
            stmt.close();
            if (hasName) {
              delsql = "DELETE FROM " + DbAccessUtils.getNameTableName(tableId) + " WHERE COMPANYID=?";
              out.println(delsql);
              stmt = conn.prepareStatement(delsql);
              stmt.setString(1, id2);
              delcnt = stmt.executeUpdate();
              stmt.close();
            }
            if (hasInfo) {
              delsql = "DELETE FROM " + DbAccessUtils.getInfoTableName(tableId) + " WHERE COMPANYID=?";
              out.println(delsql);
              stmt = conn.prepareStatement(delsql);
              stmt.setString(1, id2);
              delcnt = stmt.executeUpdate();
              stmt.close();
            }
            // ��Е���
            String inssql = "INSERT INTO " + tableId + "(" + fields[0] + ") SELECT '" + id2 + "'" + fields[0].substring(9) + " FROM " + tableId + " WHERE COMPANYID=?";
            out.println(inssql);
            int inscnt = 0;
            try {
              stmt = conn.prepareStatement(inssql);
              stmt.setString(1, id1);
              inscnt = stmt.executeUpdate();
              stmt.close();
              if (inscnt > 0) {
                out.println(tableId + ": " + inscnt + "�����ʂ��܂����B");
              }
            } catch (SQLException e) {
              // �G���[�����������玟��
              printError(out, e);
              if (stmt != null) {
                try {
                  stmt.close();
                } catch (Exception ex) {}
              }
              continue;
            }
            if (hasName) {
              inssql = "INSERT INTO " + DbAccessUtils.getNameTableName(tableId) + "(" + fields[1] + ") SELECT '" + id2 + "'" + fields[1].substring(9) + " FROM " + DbAccessUtils.getNameTableName(tableId) + " WHERE COMPANYID=?";
              out.println(inssql);
              stmt = conn.prepareStatement(inssql);
              stmt.setString(1, id1);
              inscnt = stmt.executeUpdate();
              stmt.close();
              if (inscnt > 0) {
                out.println(DbAccessUtils.getNameTableName(tableId) + ": " + inscnt + "�����ʂ��܂����B");
              }
            }
            if (hasInfo) {
              inssql = "INSERT INTO " + DbAccessUtils.getInfoTableName(tableId) + "(" + fields[2] + ") SELECT '" + id2 + "'" + fields[2].substring(9)+ " FROM " + DbAccessUtils.getInfoTableName(tableId) + " WHERE COMPANYID=?";
              out.println(inssql);
              stmt = conn.prepareStatement(inssql);
              stmt.setString(1, id1);
              inscnt = stmt.executeUpdate();
              stmt.close();
              if (inscnt > 0) {
                out.println(DbAccessUtils.getInfoTableName(tableId) + ": " + inscnt + "�����ʂ��܂����B");
              }
            }
            out.flush();
          }
          out.println("</pre>");
          conn.commit();
        } else if (subcmd.equalsIgnoreCase("update") && id1 != null && id2 != null) {
          // COMPANY UPDATE <FROMID> <TOID>
          PreparedStatement stmt = null;
          out.println("<pre>");
          for (Iterator ite = tables.iterator(); ite.hasNext();) {
            tableId = (String)ite.next();
            if (tableId.indexOf("_") != -1) {
              // �A���_�[�X�R�A���܂܂��΃X�L�b�v
              out.println(tableId + ": skip");
              continue;
            }
            try {
              String updtsql = "UPDATE " + tableId + " SET COMPANYID=? WHERE COMPANYID=?";
              stmt = conn.prepareStatement(updtsql);
              stmt.setString(1, id2);
              stmt.setString(2, id1);
              int cnt = stmt.executeUpdate();
              if (cnt > 0) {
                out.println(tableId + ": " + cnt + "���X�V���܂����B");
              }
              stmt.close();
              out.flush();
            } catch (SQLException e) {
              out.println(tableId);
              printError(out, e.getMessage());
            }
          }
          out.println("</pre>");
          conn.commit();

        } else if (subcmd.equalsIgnoreCase("delete") && id1 != null) {
          // COMPANY DELETE <COMPANYID>
          PreparedStatement stmt = null;
          out.println("<pre>");
          for (Iterator ite = tables.iterator(); ite.hasNext();) {
            tableId = (String)ite.next();
            if (tableId.indexOf("_") != -1) {
              // �A���_�[�X�R�A���܂܂��΃X�L�b�v
              out.println(tableId + ": skip");
              continue;
            }
            try {
              if (id1.equalsIgnoreCase("not") && id2 != null) {
                // id2�ȊO�w��
                String delsql = "DELETE FROM " + tableId + " WHERE COMPANYID<>?";
                stmt = conn.prepareStatement(delsql);
                stmt.setString(1, id2);
                int cnt = stmt.executeUpdate();
                if (cnt > 0) {
                  out.println(tableId + ": " + cnt + "���폜���܂����B");
                }
                stmt.close();
              } else {
                String delsql = "DELETE FROM " + tableId + " WHERE COMPANYID=?";
                stmt = conn.prepareStatement(delsql);
                stmt.setString(1, id1);
                int cnt = stmt.executeUpdate();
                if (cnt > 0) {
                  out.println(tableId + ": " + cnt + "���폜���܂����B");
                }
                stmt.close();
              }
              out.flush();
            } catch (SQLException e) {
              out.println(tableId);
              printError(out, e.getMessage());
            }
          }
          out.println("</pre>");
          conn.commit();
        }
      } else {
        out.print("�R�}���h�p�����[�^���s���ł��B");
      }
    } catch(Exception e) {
      if (tableId != null) {
        out.println(tableId);
      }
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {
        }
      }
    }
    
  }
  /**
   * �e�[�u���f�[�^���������āA�I���^�G�N�X�|�[�g�������Ȃ�
   * @param out
   * @param request
   */
  private void printMBBSearchExport(PrintWriter out, HttpServletRequest request, String menuName, String tableName, String keyFieldId, String keyName) {
    String option = request.getParameter("option");
    if(option != null){
      out.println("<input type=\"hidden\" name=\"option\" value=\"" + option + "\">");
    }
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"" + tableName + "\">");
    out.println("<table>");
    String title = menuName;
    boolean classTypeScan = false;
    if (tableName != null && tableName.equalsIgnoreCase("CLASSTYPEMASTER")) {
      if (classesPath != null && new File(classesPath).exists()) {
        classTypeScan = true;
        title = "<span title=\"" + classesPath + "\">" + title + "</span>";
      }
    }
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>" + title + "</td></tr>");
    out.println("</table>");
    
    String dispLangId = "JA";
    Connection conn = null;
    Connection conn2 = null;
    String conn2schema = null;
    
    String datasource = request.getParameter("datasource");
    boolean diffonly = request.getParameter("diffonly") != null;
    boolean hasPackageId = false;
    boolean hasCompanyId = false;
    boolean hasStartDate = false;
    Vector pkey = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      if (datasource == null) {
        datasource = "2";
      }
      int index = Integer.parseInt(datasource) - 1;
      conn2 = getConnection(index);
      if (conn2 != null) {
        conn2.setAutoCommit(false);
        conn2schema = schemas[index];
      }
      Hashtable layout = getTableLayout(conn, tableName);
      if (layout != null) {
        Vector base = (Vector)layout.get("$base$");
        if (base != null) {
          if (base.contains("PACKAGEID") && !tableName.equalsIgnoreCase("PACKAGEMASTER")) {
            hasPackageId = true;
          }
        }
        pkey =(Vector)layout.get("$pkey$");
        if (pkey != null && pkey.size() > 1) {
          if (pkey.contains("COMPANYID")) {
            hasCompanyId = true;
          }
          if (pkey.contains("STARTDATE")) {
            hasStartDate = true;
          }
        }
      }
    } catch (SQLException e) {
      log_debug(e);
      printError(out, e);
      if (conn != null) {
        try {
          conn.close();
        } catch(SQLException se) {}
      }
      return;
    }

    String keyid = request.getParameter("keyid");
    if (keyid == null) {
      keyid = "";
    }
    String search = request.getParameter("search");
    String namesearch = request.getParameter("namesearch");
    String order = request.getParameter("order");
    if (order == null) {
      order = "TIMESTAMPVALUE";
    }
    String limitcount = request.getParameter("limitcount");
    if (limitcount == null) {
      limitcount = "100";
    }
    int skipcount = 0;
    int p = limitcount.indexOf(",");
    if (p != -1) {
      try {
        skipcount = Integer.parseInt(limitcount.substring(0, p).trim());
      } catch (Exception e) {}
      limitcount = limitcount.substring(p + 1);
    }
    String selectedPackageId = request.getParameter("packageid");
    String selectedCompanyId = request.getParameter("companyid");
    boolean classmode = request.getParameter("classmode") != null;

    if (classmode) {
      String classType = request.getParameter("classtype");
      out.println("<table>");
      out.print("<tr><td><input type=\"hidden\" name=\"keyid\" value=\"" + keyid + "\"></td>");
      out.print("<td><input type=\"submit\" name=\"search\" value=\"�߂�\"></td>");
      out.println("</tr>");
      out.println("</table>");
      out.println("</form>");
      return;
    }
    // �����̈�
    out.println("<table>");
    if (hasPackageId) {
      // �p�b�P�[�W�̒��o����
      TreeMap topPackages = new TreeMap();
      out.print("<tr><td>�p�b�P�[�W�F</td><td><select name=\"packageid\"");
      out.print(" onchange=\"_putCookie('packageid',this.value)\"");
      out.print(">");
      out.print("<option value=\"\">�S��");
      try {
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT DISTINCT PACKAGEID,");
        sql.append("(SELECT NAMEVALUE FROM PACKAGENAME WHERE PACKAGEID=a.PACKAGEID AND DISPLANGID=? AND PROPERTYID='OFFICIALNAME') PACKAGENAME");
        sql.append(" FROM ").append(tableName).append(" a ORDER BY PACKAGEID");
        log_debug(sql.toString());
        PreparedStatement stmt = conn.prepareStatement(sql.toString());
        stmt.setString(1, dispLangId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          String packageId = rs.getString(1);
          if (packageId == null || packageId.trim().length() == 0) {
            packageId = " ";
          }
          String packageName = rs.getString(2);
          if (packageName == null) {
            packageName = "";
          }
          int pp = packageId.indexOf(".");
          if (pp != -1) {
            String topPackage = packageId.substring(0, pp);
            Integer count = (Integer)topPackages.get(topPackage);
            if (count == null) {
              count = new Integer(1);
            } else {
              count = new Integer(count.intValue() + 1);
            }
            topPackages.put(topPackage, count);
          }
          String sel = "";
          if (packageId.equals(selectedPackageId)) {
            sel =" selected";
          }
          out.print("<option value=\"" + packageId + "\"" + sel + ">" + packageId  + ": " + packageName);
        }
        rs.close();
        stmt.close();
        for (Iterator ite = topPackages.keySet().iterator(); ite.hasNext(); ) {
          String top = (String)ite.next();
          Integer count = (Integer)topPackages.get(top);
          if (count.intValue() > 1) {
            String sel = "";
            if (selectedPackageId != null && selectedPackageId.endsWith(".%") && top.equals(selectedPackageId.substring(0, selectedPackageId.length() - 2))) {
              sel =" selected";
            }
            out.print("<option value=\"" + top + ".%\"" + sel + ">" + top  + ".*");
          }
        }
      } catch(SQLException e) {}
      out.print("</select></td><td colspan=\"3\"></td></tr>");
    }
    if (hasCompanyId) {
      // ��Ђ̒��o�����i��Ђ̓v���C�}���L�[�̂��߁u�S�āv�͎g�p���Ȃ��j
      out.print("<tr><td>��ЁF</td><td><select name=\"companyid\">");
      try {
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT DISTINCT COMPANYID,");
        sql.append("(SELECT NAMEVALUE FROM COMPANYNAME WHERE COMPANYID=a.COMPANYID AND DISPLANGID=? AND PROPERTYID='OFFICIALNAME') COMPANYNAME");
        sql.append(" FROM ").append(tableName).append(" a ORDER BY COMPANYID");
        log_debug(sql.toString());
        PreparedStatement stmt = conn.prepareStatement(sql.toString());
        stmt.setString(1, dispLangId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          String companyId = rs.getString(1);
          if (companyId == null || companyId.trim().length() == 0) {
            companyId = " ";
          }
          String companyName = rs.getString(2);
          if (companyName == null) {
            companyName = "";
          }
          String sel = "";
          if (companyId.equals(selectedCompanyId)) {
            sel =" selected";
          }
          out.print("<option value=\"" + companyId + "\"" + sel + ">" + companyId  + ": " + companyName);
        }
        rs.close();
        stmt.close();
      } catch(SQLException e) {}
      out.print("</select></td><td colspan=\"3\"></td></tr>");
    }
    out.print("<tr><td>" + keyName +  "�F</td><td><input type=\"text\" name=\"keyid\" value=\"" + keyid + "\" size=\"24\"></td>");
    out.print("<td><input type=\"submit\" name=\"search\" value=\"����\"></td>");
    out.print("<td><input type=\"submit\" name=\"namesearch\" value=\"���̌���\"></td>");
    if (classTypeScan) {
      out.println("<td><input type=\"submit\" name=\"classmode\" value=\"class�擾\"></td>");
    }
    String checked0 = ""; // ID��
    String checked1 = ""; // �X�V��
    String checked2 = ""; // �p�b�P�[�WID
    if (order.equals(keyFieldId)) {
      checked0 = " checked";
    } else if (order.equals("PACKAGEID")) {
      checked2 = " checked";
    } else {
      checked1 = " checked";
    }
    out.print("<td>");
    out.print("<input type=\"radio\" name=\"order\" id=\"order_0\" value=\"" + keyFieldId + "\"" + checked0);
    out.print(" onclick=\"_putCookie('order','0')\"");
    out.print("><label for=\"order_0\">ID��&nbsp;</label>");
    out.print("<input type=\"radio\" name=\"order\" id=\"order_1\" value=\"TIMESTAMPVALUE\"" + checked1);
    out.print(" onclick=\"_putCookie('order','1')\"");
    out.print("><label for=\"order_1\">�X�V��&nbsp;</label>");
    if (hasPackageId && !keyFieldId.equals("PACKAGEID")) {
      out.print("<input type=\"radio\" name=\"order\" id=\"order_2\" value=\"PACKAGEID\"" + checked2);
      out.print(" onclick=\"_putCookie('order','2')\"");
      out.print("><label for=\"order_2\">�p�b�P�[�W��&nbsp;</label>");
    }
    out.print("</td>");
    String tmplimitcount = null;
    if (skipcount == 0) {
      tmplimitcount = limitcount;
    } else {
      tmplimitcount = skipcount + "," + limitcount;
    }
    out.print("<td>&nbsp;&nbsp;�ő�\�������F</td><td><input type=\"text\" id=\"limitcount\" name=\"limitcount\" value=\"" + tmplimitcount + "\" size=\"3\" maxlength=\"10\" style=\"text-align:right;\"");
    out.print(" onchange=\"_putCookie('limitcount',this.value);\"></td>");
    if (dataSources.length > 2) {
      out.print("<td>&nbsp;&nbsp;��r�ΏہF</td>");
      out.print("<td><select name=\"datasource\"");
      out.print(" onchange=\"_putCookie('datasource',this.selectedIndex);\"");
      out.print(">");
      
      int sel = -1;
      try {
        if (datasource != null) {
          sel = Integer.parseInt(datasource);
        }
      } catch (Exception e) {}
      for (int i = 1; i < dataSources.length; ++i) {
        String s = "";
        if (i == (sel - 1)) {
          s = " selected";
        }
        out.print("<option value=\"" + (i + 1) + "\"" + s + ">" + dataSourceDispNames[i]);
      }
      out.print("</select></td>");
    }
    if (dataSources.length > 1) {
      out.print("<td>");
      out.print("<input type=\"checkbox\" id=\"diffonly\" name=\"diffonly\" value=\"1\"");
      out.print(" onclick=\"_putCookie('diffonly',this.checked?this.value:'');\"");
      if (diffonly) {
        out.print(" checked");
      }
      out.print("><label for=\"diffonly\">���ق̂�</label>");
      out.print("</td>");
    }
    out.println("</tr>");
    out.println("</table>");
    // Cookie����̕���
    out.println("<script>");
    if (hasPackageId && !keyFieldId.equals("PACKAGEID")) {
      out.println("if(_getCookie('packageid')){");
      out.println(" document.getElementById('packageid').value=_getCookie('packageid');");
      out.println("}");
    }
    out.println("if(_getCookie('limitcount')){");
    out.println(" document.getElementById('limitcount').value=_getCookie('limitcount');");
    out.println("}");
    out.println("if(_getCookie('order')){");
    out.println(" try{");
    out.println("  document.getElementById('order_'+_getCookie('order')).click();");
    out.println(" }catch(e){}");
    out.println("}");
    if (conn2 != null) {
      out.println("if(_getCookie('diffonly')=='1'){");
      out.println(" document.getElementById('diffonly').checked=true;");
      out.println("}");
    }
    if (dataSources.length > 2) {
      // �ǉ��f�[�^�\�[�X��2�ȏ�o�^����Ă���ꍇ��cookie�ɑI��Ώۂ��L������
      out.println("var _dsi=_getCookie('datasource');");
      out.println("if(_dsi){");
      out.println(" document.getElementById('datasource').selectedIndex=_dsi;");
      out.println("}");
    }
    out.println("</script>");
    out.println("</form>");
    
    // �������ƈꗗ���̃t�H�[���𕪂���
    out.println("<form method=\"post\" action=\"?\">");
    if (hasCompanyId) {
      out.println("<input type=\"hidden\" name=\"hascompanyid\" value=\"1\">");
    }
    if (hasStartDate) {
      out.println("<input type=\"hidden\" name=\"hasstartdate\" value=\"1\">");
    }
    if (search != null || keyid.trim().length() > 0) {
      int count = -1;
      try {
        String propertyId = "OFFICIALNAME";
        if (tableName.equalsIgnoreCase("MESSAGEMASTER")) {
          propertyId = "MESSAGE";
        }

        // �ꗗ�擾SQL�̑g�ݗ���
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT ");
        if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
          sql.append("MENUID").append(SQL_CONCAT).append("','").append(SQL_CONCAT).append("MENUITEMID");
        } else {
          sql.append(keyFieldId);
        }
        sql.append(",(SELECT NAMEVALUE FROM ").append(DbAccessUtils.getNameTableName(tableName));
        sql.append(" WHERE ");
        if (hasCompanyId) {
          sql.append(" COMPANYID=a.COMPANYID AND ");
        }
        if (hasStartDate) {
          sql.append(" STARTDATE=a.STARTDATE AND ");
        }
        if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
          sql.append(" MENUID=a.MENUID AND ");
        }
        sql.append(keyFieldId).append("=a.").append(keyFieldId).append(" AND DISPLANGID=? AND PROPERTYID='");
        sql.append(propertyId).append("') NAMEVALUE,");
        sql.append(" UPDATECOMPANYID, UPDATEUSERID, TIMESTAMPVALUE");
        if (hasCompanyId) {
          sql.append(", COMPANYID");
        }
        if (hasStartDate) {
          sql.append(", STARTDATE");
        }
        if (hasPackageId) {
          sql.append(", PACKAGEID");
        }
        // �֘A���̎擾�i�e�[�u���ʁj
        if (tableName.equalsIgnoreCase("PROCESSMASTER")) {
          sql.append(", (SELECT COUNT(PROCESSID) FROM PROCESSMASTER WHERE ITEMDEFINITIONID=a.ITEMDEFINITIONID)");
          sql.append(" RELITEMCOUNT");
        } else if (tableName.equalsIgnoreCase("FUNCTIONMASTER")) {
          sql.append(", (SELECT COUNT(FUNCTIONCOMPOSITIONID) FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=a.FUNCTIONID)");
          sql.append(" RELITEMCOUNT");
        } else if (tableName.equalsIgnoreCase("DATAFIELDMASTER")) {
          sql.append(", (SELECT COUNT(TABLEID) FROM TABLELAYOUTMASTER WHERE DATAFIELDID=a.DATAFIELDID)");
          sql.append(" RELITEMCOUNT");
        } else if (tableName.equalsIgnoreCase("MENUMASTER")) {
          sql.append(", (SELECT COUNT(MENUITEMID) FROM MENUITEMMASTER WHERE COMPANYID=a.COMPANYID AND MENUID=a.MENUID)");
          sql.append(" RELITEMCOUNT");
        } else if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
          sql.append(", (SELECT COUNT(MENUID) FROM MENUMASTER WHERE COMPANYID=a.COMPANYID AND MENUID=a.MENUID)");
          sql.append(" RELITEMCOUNT");
          sql.append(", MENUID, MENUITEMID");
        } else {
          sql.append(",0");
          sql.append(" RELITEMCOUNT");
        }
        sql.append(" FROM ").append(tableName).append(" a WHERE ");
        
        // �����L�[��","�܂���"|"�ŕ����ɕ���
        String[] keys = getSearchKeys(keyid);
        
        // �L�[����������ꍇ�́A"("�ň͂�OR�Őڑ�����
        if (keys.length > 1) {
          sql.append("(");
        }
        for (int i = 0; i < keys.length; ++i) {
          if (i > 0) {
            sql.append(" OR ");
          }
          if (namesearch != null) {
            // ���̌���
            sql.append(keyFieldId).append(" IN (SELECT ").append(keyFieldId);
            sql.append(" FROM ").append(DbAccessUtils.getNameTableName(tableName));
            sql.append(" WHERE PROPERTYID='").append(propertyId).append("'");
            sql.append(" AND NAMEVALUE LIKE ?)");
          } else {
            sql.append(keyFieldId).append(" LIKE ?");
          }
        }
        if (keys.length > 1) {
          sql.append(")");
        }
        //
        if (selectedPackageId != null && selectedPackageId.length() > 0) {
          // �p�b�P�[�W���o
          if (selectedPackageId.equals(" ")) {
            sql.append(" AND (PACKAGEID = ? OR PACKAGEID IS NULL)");
          } else {
            if (selectedPackageId.endsWith(".%")) {
              sql.append(" AND (PACKAGEID = ? OR PACKAGEID LIKE ?)");
            } else {
              sql.append(" AND PACKAGEID = ?");
            }
          }
        }
        if (selectedCompanyId != null && selectedCompanyId.length() > 0) {
          // ��В��o
          sql.append(" AND COMPANYID = ?");
        }
        if (order.equals(keyFieldId)) {
          if (keyFieldId.equals("MENUITEMID")) {
            sql.append(" ORDER BY MENUID, MENUITEMID");
          } else {
            sql.append(" ORDER BY ").append(keyFieldId);
          }
        } else if (order.equals("PACKAGEID")) {
          sql.append(" ORDER BY PACKAGEID, ").append(keyFieldId);
        } else {
          sql.append(" ORDER BY TIMESTAMPVALUE DESC");
        }
        for (int i = 0; i < keys.length; ++i) {
          if (keys[i].indexOf("%") == -1 && keys[i].indexOf("_") == -1) {
            if (namesearch != null || keyFieldId.equalsIgnoreCase("CLASSTYPE")) {
              // ���̌���(or�N���X�^�C�v�̏ꍇ��ID������)�̓f�t�H���g������v
              keys[i] = "%" + keys[i] + "%";
            } else {
              // ���̌����ȊO�ł̓f�t�H���g�O����v
              keys[i] = keys[i] + "%";
            }
          }
        }
        log_debug(sql.toString());
        PreparedStatement stmt = conn.prepareStatement(sql.toString());
        int paramidx = 1;
        stmt.setString(paramidx++, dispLangId);
        for (int i = 0; i < keys.length; ++i) {
          stmt.setString(paramidx++, keys[i]);
        }
        if (selectedPackageId != null && selectedPackageId.length() > 0) {
          if (selectedPackageId.endsWith(".%")) {
            stmt.setString(paramidx++, selectedPackageId.substring(0, selectedPackageId.length() - 2));
            stmt.setString(paramidx++, selectedPackageId);
          } else {
            stmt.setString(paramidx++, selectedPackageId);
          }
        }
        if (selectedCompanyId != null && selectedCompanyId.length() > 0) {
          stmt.setString(paramidx++, selectedCompanyId);
        }
        ResultSet rs = stmt.executeQuery();
        PreparedStatement stmt2 = null;
        PreparedStatement stmt3 = null;
        if (conn2 != null && pkey != null) {
          // conn2���w�肳��Ă���ꍇ�́Aconn2������^�C���X�^���v���擾
          StringBuffer sql2 = new StringBuffer();
          sql2.append("SELECT UPDATECOMPANYID, UPDATEUSERID, TIMESTAMPVALUE");
          sql2.append(" FROM ").append(tableName).append(" WHERE");
          for (int i = 0; i < pkey.size(); ++i) {
            String key = (String)pkey.get(i);
            if (i > 0) {
              sql2.append(" AND");
            }
            sql2.append(" ").append(key).append("=?");
          }
          stmt2 = conn2.prepareStatement(sql2.toString());
        }
        String maxmsg = "";
        long maxcount = 0;
        try {
          if (limitcount.length() > 0) {
            maxcount = Long.parseLong(limitcount.trim());
          }
        } catch (Exception e) {}
        int colCompanyId = 0;
        int colStartDate = 0;
        int colPackageId = 0;
        MBBClassLoader classLoader = new MBBClassLoader(appPath);
        while (rs.next()) {
          if (skipcount > 0) {
            --skipcount;
            continue;
          }
          if (count == -1) {
            // �s�w�b�_
            out.println("<input type=\"hidden\" name=\"_command\" value=\"download\">");
            out.println("<input type=\"hidden\" name=\"table\" value=\"" + tableName + "\">");
            out.println("<input type=\"submit\" value=\"�G�N�X�|�[�g\">");
            out.println("&nbsp;&nbsp;<span id=\"_top_message\" style=\"font-size:10pt;color:#666666;\"></span>");
            out.println("<table id=\"checklisttable\">");
            out.print("<tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\"><th><span title=\"�S�ă`�F�b�N/����\"><input type=\"checkbox\" id=\"checkall\" onclick=\"checkAll('id', this.checked);\"></span></th>");
            if (hasCompanyId) {
              out.print("<th>���</th>");
              colCompanyId = 6;
              colStartDate = 7;
            }
            
            out.print("<th>" + keyName + "</th>");
            if (hasStartDate) {
              out.print("<th>�J�n��</th>");
              if (!hasCompanyId) {
                // ��{�I�ɂ͂��̃P�[�X�͖����H�i��`���͉̂\�j
                colStartDate = 6;
              }
            }
            if (hasPackageId) {
              out.print("<th>�p�b�P�[�WID</th>");
              colPackageId = 6;
              if (colStartDate > 0) {
                colPackageId = colStartDate + 1;
              } else if (colCompanyId > 0) {
                colPackageId = colCompanyId + 1;
              }
            }
            out.print("<th>����</th><th>�X�V���</th>");
            if (conn2 != null) {
              out.print("<th>�����[�g��r(" + conn2schema + ")");
              out.print("</th>");
            }
            out.println("</tr>");
            ++count;
          }
          String id = rs.getString(1);
          String name = rs.getString(2);
          String idvalue = id;
          String companyId = null;
          String startDate = null;
          String packageId = null;
          if (hasCompanyId) {
            companyId = rs.getString(colCompanyId);
            idvalue = companyId + "," + idvalue;
          }
          if (hasStartDate) {
            startDate = rs.getString(colStartDate);
            idvalue += "," + startDate;
          }
          if (hasPackageId) {
            packageId = rs.getString(colPackageId);
            if (packageId == null) {
              packageId = "(null)";
            }
          }
          if (name == null) {
            // ���̂�NULL�̃P�[�X��PAGEMASTER�œ��{��ȊO�̃y�[�W�̉\��������
            if (stmt3 == null) {
              StringBuffer namesql = new StringBuffer();
              namesql.append("SELECT DISPLANGID, NAMEVALUE FROM ").append(DbAccessUtils.getNameTableName(tableName));
              namesql.append(" WHERE ");
              if (hasCompanyId) {
                namesql.append(" COMPANYID=? AND");
              }
              if (hasStartDate) {
                namesql.append(" STARTDATE=? AND");
              }
              if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
                namesql.append(" MENUID=? AND");
              }
              namesql.append(" ").append(keyFieldId).append("=? AND PROPERTYID='");
              namesql.append(propertyId).append("' ORDER BY DISPlANGID DESC");
              stmt3 = conn.prepareStatement(namesql.toString());
            }
            stmt3.clearParameters();
            int idx = 1;
            if (hasCompanyId) {
              stmt3.setString(idx++, companyId);
            }
            if (hasStartDate) {
              stmt3.setString(idx++, startDate);
            }
            if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
              String[] ids = id.split(",");
              stmt3.setString(idx++, ids[0]);
              stmt3.setString(idx++, ids[1]);
            } else {
              stmt3.setString(idx++, id);
            }
            ResultSet rs3 = stmt3.executeQuery();
            while (rs3.next()) {
              String nameDispLangId = rs3.getString(1);
              String nameValue = rs3.getString(2);
              if (nameValue != null && nameValue.trim().length() > 0) {
                name = nameValue;
              }
              if (id.endsWith("_" + nameDispLangId)) {
                break;
              }
            }
            rs3.close();
            if (name == null) {
              // ���̂��擾�ł��Ȃ��ꍇ��ID���g�p����
              name = id;
            }
          }
          String ts1 = rs.getString(5);
          String ts = rs.getString(3) + "," + rs.getString(4) + "," + ts1;
          int relitemcount = rs.getInt("RELITEMCOUNT");
          int diffstate = 0;
          String ts2 = null;
          String updt2 = null;
          if (conn2 != null) {
            // �����[�g�ƃ^�C���X�^���v��r
            if (conn2 != null && pkey != null) {
              stmt2.clearParameters();
              for (int i = 0; i < pkey.size(); ++i) {
                String key = (String)pkey.get(i);
                stmt2.setString(i + 1, rs.getString(key));
              }
              ResultSet rs2 = stmt2.executeQuery();
              if (rs2.next()) {
                updt2 = rs2.getString(1) + "," + rs2.getString(2);
                ts2 = rs2.getString(3);
              }
              rs2.close();
            }
            if (ts2 == null) {
              diffstate = 2; // �����[�g�Ȃ�
            } else {
              diffstate = DbAccessUtils.compareTimestamp(ts1, ts2);
            }
          }
          if (diffonly && diffstate == 0) {
            // ���ق݂̂œ����ꍇ�̓X�L�b�v
            continue;
          }
          ++count;
          
          out.print("<tr><td>&nbsp;&nbsp;<input type=\"checkbox\" name=\"id\" value=\"" + idvalue + "\"></td>");
          if (hasCompanyId) {
            out.print("<td>" + companyId + "</td>");
          }
          if (classTypeScan) {
            String className = PACKAGE_BASE + id;
            try {
              if (classLoader.loadClass(className) != null) {
                out.print("<td>" + id + "</td>");
              } else {
                // ���݂��Ȃ�
                out.print("<td><span style=\"color:#ffff00;\">" + id + "</span></td>");
              }
            } catch (Exception e) {
              out.print("<td><span style=\"color:" + DIFF_OLDER_COLOR + ";\">" + id + "</span></td>");
            }
          } else {
            out.print("<td>" + id);
            // �ȉ��͓���̃e�[�u���ɂ����āAID�̉E���ɏ������֘A����\������
            if (tableName.equalsIgnoreCase("PROCESSMASTER")) {
              if (relitemcount > 1) {
                // �v���Z�X�}�X�^�F�����̃v���Z�X�ō��ڒ�`�����L����ꍇ
                out.print("&nbsp;<font color=\"" + ERROR_COLOR + "\" size=\"-2\" title=\"���ڒ�`���L\">" + relitemcount + "</font>");
              }
            } else if (tableName.equalsIgnoreCase("FUNCTIONMASTER")) {
              if (relitemcount > 0) {
                // ���ڂ��g�p����e�[�u����
                out.print("&nbsp;<font color=\"" + INFO_COLOR + "\" size=\"-2\" title=\"�@�\�\����\">" + relitemcount + "</font>");
              }
            } else if (tableName.equalsIgnoreCase("DATAFIELDMASTER")) {
                if (relitemcount > 0) {
                  // ���ڂ��g�p����e�[�u����
                  out.print("&nbsp;<font color=\"" + INFO_COLOR + "\" size=\"-2\" title=\"�Q�ƃe�[�u����\">" + relitemcount + "</font>");
                }
            } else if (tableName.equalsIgnoreCase("MENUMASTER")) {
              if (relitemcount > 0) {
                // ���j���[�A�C�e����
                out.print("&nbsp;<font color=\"" + INFO_COLOR + "\" size=\"-2\" title=\"���j���[�A�C�e����\">" + relitemcount + "</font>");
              }
            } else if (tableName.equalsIgnoreCase("MENUITEMMASTER")) {
              if (relitemcount == 0) {
                // ���j���[����0�͎g�p����Ȃ����j���[�A�C�e��
                out.print("&nbsp;<font color=\"" + ERROR_COLOR + "\" size=\"-2\" title=\"���j���[ID�Ȃ�\">*</font>");
              }
            }
            out.print("</td>");
          }
          if (hasStartDate) {
            out.print("<td>" + startDate + "</td>");
          }
          if (hasPackageId) {
            out.print("<td>" + packageId + "</td>");
          }
          out.print("<td>");
          boolean doc = false;
          if (DocumentManager.isActive()) {
            if (tableName.equalsIgnoreCase("PAGEMASTER")) {
              out.print("<a href=\"?_command=download&xlsdoc=1&pageid=" + id + "\">");
              doc = true;
            } else if (tableName.equalsIgnoreCase("TABLEMASTER")) {
              out.print("<a href=\"?_command=download&xlsdoc=1&tableid=" + id + "\">");
              doc = true;
            }
          }
          out.print(DbAccessUtils.escapeHTML(name));
          if (doc) {
            out.print("</a>");
          }
          out.print("</td>");
          if (diffstate == -1) {
            // �����[�g�̕����V���� 
            out.print("<td><font color=\"" + DIFF_OLDER_COLOR + "\">" + ts + "</font></td>");
          } else if (diffstate > 0) {
            out.print("<td><font color=\"" + DIFF_NEWER_COLOR + "\">" + ts + "</font></td>");
          } else {
            out.print("<td>" + ts + "</td>");
          }
          if (conn2 != null) {
            // �����[�g�ƃ^�C���X�^���v��r
            if (diffstate == 2) {
              out.print("<td>N/A</td>");
            } else if (diffstate == 0) {
              out.print("<td></td>");
            } else {
              // ���ق���
              String a = null;
              String cmd = "&execsql=1";
              if (datasource != null) {
                cmd = cmd + "&datasource=" + datasource;
              }
              if (tableName.equalsIgnoreCase("FUNCTIONMASTER")) {
                // �@�\�̏ꍇ
                a = "<a href=\"dbaccess?tab=MBB&mbbmenu=function&datasource=1&packageid=" + packageId + "&functionid=" + id + "\">";
              } else if (tableName.equalsIgnoreCase("APPLICATIONMASTER")) {
                // �A�v���P�[�V�����̏ꍇ
                a = "<a href=\"dbaccess?tab=Command&command=compare%20applicationid=" + id + cmd + "\">";
              } else if (tableName.equalsIgnoreCase("PROCESSMASTER")) {
                // �v���Z�X�̏ꍇ
                a = "<a href=\"dbaccess?tab=Command&command=compare%20processid=" + id + cmd + "\">";
              } else if (tableName.equalsIgnoreCase("PAGEMASTER")) {
                // �y�[�W�̏ꍇ
                a = "<a href=\"dbaccess?tab=Command&command=compare%20pageid=" + id + cmd + "\">";
              } else if (tableName.equalsIgnoreCase("TABLEMASTER")) {
                // �e�[�u���̏ꍇ
                a = "<a href=\"dbaccess?tab=Command&command=compare%20tableid=" + id + cmd + "\">";
              } else if (tableName.equalsIgnoreCase("DATAFIELDMASTER")) {
                // �f�[�^�t�B�[���h�̏ꍇ
                a = "<a href=\"dbaccess?tab=Command&command=compare%20datafieldid=" + id + cmd + "\">";
              }
              out.print("<td>");
              if (a != null) {
                out.print(a);
              }
              out.print(updt2 + "," + ts2);
              if (a != null) {
                out.print("</a>");
              }
              out.print("</td>");
            }
          }
          out.print("</tr>");
          if (maxcount > 0 && count >= maxcount) {
            // �\�������ɒB�����璆�f
            maxmsg = "(���f)";
            break;
          }
        }
        rs.close();
        stmt.close();
        if (stmt2 != null) {
          stmt2.close();
        }
        if (stmt3 != null) {
          stmt3.close();
        }
        
        if (count <= 0) {
          out.println("<table>");
          out.print("<tr><td></td><td style=\"color:#666666;\" colspan=\"2\"><span id=\"_bottom_message\">�Ώۃf�[�^�͌�����܂���ł����B</span></td><td></td></tr>");
          out.println("</table>");
          // �S�ă`�F�b�N�𖳌�������
          out.println("<script language=\"javascript\">");
          out.println("if (document.getElementById('checkall')) {");
          out.println("  document.getElementById('checkall').disabled=true;");
          out.println("}");
          out.println("</script>");
        } else {
          out.print("<tr><td></td><td style=\"color:#666666;\" colspan=\"2\"><span id=\"_bottom_message\">" + count + "���\�����܂����B" + maxmsg + "</span></td><td></td></tr>");
          out.println("</table>");
          
          if (tableName.equalsIgnoreCase("FUNCTIONMASTER")) {
            //�@�\�}�X�^�̏ꍇ�A�I�v�V������ǉ�
            String check0 = "";
            String check1 = "";
            String check2 = "";
            if (option == null || option.length() < 1) {
              check0 = " checked";
            } else if (option.equals("CLASSTYPE")) {
              check1 = " checked";
            } else if (option.equals("CLASSTYPE_ALL")) {
              check2 = " checked";
            }
            out.print("<nobr class=\"text\">");
            out.print("<input type=\"radio\" name=\"option\" id=\"option_0\" value=\"\"" + check0 + "><label for=\"option_0\">�N���X�^�C�v���܂܂Ȃ�&nbsp;</label>");
            out.print("<input type=\"radio\" name=\"option\" id=\"option_1\" value=\"CLASSTYPE\"" + check1 + "><label for=\"option_1\">�V�X�e���N���X�^�C�v�ȊO���܂�&nbsp;</label>");
            out.print("<input type=\"radio\" name=\"option\" id=\"option_2\" value=\"CLASSTYPE_ALL\"" + check2 + "><label for=\"option_2\">�S�N���X�^�C�v���܂�&nbsp;</label>");
            out.print("<br>");
          }
          // ��ʉ����̃{�^����
          out.print("<nobr class=\"text\">");
          out.println("<input type=\"submit\" value=\"�G�N�X�|�[�g\">");
          // �폜�p�t�@�C���쐬�G�N�X�|�[�g
          out.print("&nbsp;&nbsp;&nbsp;");
          //out.println("<input type=\"submit\" name=\"fordelete\" value=\"�G�N�X�|�[�g(�폜�p)\">");
          out.print("<input type=\"checkbox\" name=\"fordelete\" id=\"fordelete\">");
          out.print("<label for=\"fordelete\">�폜�p�t�@�C���쐬</label>");
          // �I�v�V�����{�^��
          out.print("&nbsp;&nbsp;&nbsp;");
          out.print("<span id=\"_optionslabel\" style=\"\">");
          out.print("<a href=\"javascript:void(0);\" onclick=\"document.getElementById('_optionslabel').style.display='none';document.getElementById('_options').style.display='';\">�t�@�C�����I�v�V����&gt;&gt;</a>");
          out.print("</span>");
          out.print("<span id=\"_options\" style=\"display:none;\" class=\"text\">");
          out.print("�t�@�C�����I�v�V����:");
          out.print("&nbsp;");
          out.print("<input type=\"checkbox\" name=\"filenameid\" id=\"filenameid\">");
          out.print("<label for=\"filenameid\">ID���t�@�C�����ɂ���</label>");
          out.print("&nbsp;&nbsp;");
          out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_1\" value=\"1\" checked>");
          out.print("<label for=\"filenamets_1\" class=\"text\">���s���t��t��</label>");
          out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_2\" value=\"2\">");
          out.print("<label for=\"filenamets_2\" class=\"text\">���s���t���Ԃ�t��</label>");
          out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_0\" value=\"0\">");
          out.print("<label for=\"filenamets_0\" class=\"text\">������t�����Ȃ�</label>");
          if (ExcelManager.isEnabled()) {
            //TODO:TEST �ŏI�I�ɂ͏����e���v���[�g��o�^���Ă�����g�p����悤�ɂ�����
            out.println("<input type=\"button\" value=\"Excel\" onclick=\"doExcelReport(document.forms['downloadform'],document.getElementById('checklisttable').innerHTML);return false;\">");
          }
          out.print("</span>");
          //
          out.println("</nobr>");
          
          if (maxmsg.length() > 0) {
            out.println("<script language=\"javascript\">");
            out.println("if (document.getElementById('_top_message')) {");
            out.println("  document.getElementById('_top_message').innerHTML=document.getElementById('_bottom_message').innerHTML;");
            out.println("  window.status=document.getElementById('_bottom_message').innerHTML;");
            out.println("}");
            out.println("</script>");
          } else {
            out.println("<script language=\"javascript\">");
            out.println("if (document.getElementById('_bottom_message')) {");
            out.println("  window.status=document.getElementById('_bottom_message').innerHTML;");
            out.println("}");
            out.println("</script>");
          }
        }

      } catch(Exception e) {
        log_debug(e);
        printError(out, e);
      }
    }
    
    if (conn != null) {
      try {
        conn.close();
      } catch(SQLException e) {}
    }
    if (conn2 != null) {
      try {
        conn2.close();
      } catch(SQLException e) {}
    }
    
  }
  
  
  private String[] getSearchKeys(String str) {
    Vector array = new Vector();
    String sep = ",";
    if (str.indexOf("|") != -1) {
      sep = "|";
    }
    StringTokenizer st = new StringTokenizer(str, sep);
    while (st.hasMoreTokens()) {
      array.add(st.nextToken());
    }
    if (array.size() == 0) {
      array.add("");
    }
    String[] sarray = new String[array.size()];
    for (int i = 0; i < array.size(); ++i) {
      sarray[i] = (String)array.get(i);
    }
    return sarray;
  }

  private void printDataSources(PrintWriter out, String selectedDataSource) {
    out.print("<tr><td>");
    out.print("�f�[�^�x�[�X�F");
    out.print("<td>");
    out.print("<select name=\"datasource\"\" onchange=\"doTab('MBB');\">");
    out.print("<option value=\"1\">" + schemas[0] + "@" + dataSourceNames[0]);
    for (int i = 1; i < dataSourceNames.length; ++i) {
      String sel = "";
      if (selectedDataSource != null && Integer.parseInt(selectedDataSource) - 1 == i) {
        sel = " selected";
      }
      out.print("<option value=\"" + (i + 1) + "\"" + sel + ">" + schemas[i] + "@" + dataSourceNames[i]);
    }
    out.print("</select>");
  }
  
  /**
   * MBB���j���[���ݒ��\��
   * @param out
   * @param request
   */
  private void printMBBConfig(PrintWriter out, HttpServletRequest request) {
    String command = request.getParameter("command");
    String option = null;
    if (command != null && command.trim().length() > 0) {
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "scan"���X�L�b�v
      if (st.hasMoreTokens()) {
        option = st.nextToken();
      }
    }
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"CONFIG\">");
    out.println("<table>");
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>�ݒ�</td></tr>");
    out.println("</table>");
    String errorMessage = null;
    String infoMessage = null;
    boolean adminpassword_error = false;
    boolean userpassword_error = false;
    boolean applicationpath_error = false;
    boolean stagingurl_error = false;
    boolean stagingproxy_error = false;
    boolean updateworkpath_error = false;
    boolean templatefile_error = false;
    
    String adminpassword = DBACCESS_ADMINPASSWORD;
    String adminpassword2 = DBACCESS_ADMINPASSWORD;
    String userpassword = DBACCESS_USERPASSWORD;
    String userpassword2 = DBACCESS_USERPASSWORD;
    String applicationpath = this.appPath;
    String stagingurl = this.stagingURL;
    String stagingpass = this.stagingPass;
    if (stagingurl != null && stagingurl.indexOf("password=") != -1) {
      int p = stagingurl.indexOf("password=");
      stagingpass = stagingurl.substring(p + 9);
      stagingurl = stagingurl.substring(0, p);
      if (stagingurl.endsWith("?") || stagingurl.endsWith(";")) {
        stagingurl = stagingurl.substring(0, stagingurl.length() - 1);
      }
    }
    String stagingproxy = this.stagingProxy;
    String updateworkpath = this.updateWorkPath;
    String templatefile = this.excelTemplateFile;
    String restartCommand = this.restartCommand;
    
    if (option != null && "save".equals(option)) { // �ۑ�
      // ���͒l�`�F�b�N
      adminpassword = request.getParameter("adminpassword");
      adminpassword2 = request.getParameter("adminpassword2");
      userpassword = request.getParameter("userpassword");
      userpassword2 = request.getParameter("userpassword2");
      if (adminpassword == null) adminpassword = "";
      if (userpassword == null) userpassword = "";
      if (adminpassword2 != null && !adminpassword.equals(adminpassword2)) {
        adminpassword_error = true;
        errorMessage = "�ē��̓p�X���[�h����v���܂���";
      } else if (userpassword2 != null && !userpassword.equals(userpassword2)) {
        userpassword_error = true;
        errorMessage = "�ē��̓p�X���[�h����v���܂���";
      } else if (adminpassword.length() > 0 && userpassword.trim().length() == 0) {
        // adminpassword�̐ݒ肪����userpassword���u�����N�̏ꍇ�A���[�U���[�h�ł����A�N�Z�X�ł��Ȃ��Ȃ�
        userpassword_error = true;
        errorMessage = "���[�U�p�X���[�h�͕K�{�ł�";
      }
      applicationpath = request.getParameter("applicationpath");
      if (!new File(applicationpath).exists()) {
        applicationpath_error = true;
        errorMessage = "�t�H���_�ɃA�N�Z�X�ł��܂���";
      }
      stagingurl = request.getParameter("stagingurl");
      stagingpass = request.getParameter("stagingpass");
      stagingproxy = request.getParameter("stagingproxy");
      if (stagingurl != null && stagingurl.trim().length() > 0) {
        // �ڑ����o�[�W�������L�����`�F�b�N����
        try {
          String testurl = stagingurl;
          if (testurl.endsWith("/")) {
            testurl = testurl + "dbaccess";
          }
          URLConnection uc = DbAccessUtils.getURLConnection(testurl, stagingproxy);
          BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
          String line = null;
          String version = "0";
          while ((line = br.readLine()) != null) {
            if (line.indexOf("<title>") != -1) {
              if (line.indexOf("ver.") != -1) {
                version = getVersion(line);
              }
              break;
            }
          }
          if (compareVersion(version, "1.78") < 0) {
            stagingurl_error = true;
            errorMessage = "��r�����DBACCESS�̃o�[�W�������Â����ߐڑ��ł��܂���[" + version + ">=1.78]";
          }
        } catch (Exception e) {
          stagingurl_error = true;
          errorMessage = "��r����֐ڑ��ł��܂���[" + e.getMessage() + "]";
        }
      }
      updateworkpath = request.getParameter("updateworkpath");
      if (updateworkpath != null && updateworkpath.trim().length() > 0) {
        if (!new File(updateworkpath).isDirectory()) {
          updateworkpath_error = true;
          errorMessage = "�t�H���_�ɃA�N�Z�X�ł��܂���";
        }
      }
      templatefile = request.getParameter("templatefile");
      if (templatefile != null && templatefile.trim().length() > 0) {
        if (!new File(templatefile).exists()) {
          templatefile_error = true;
          errorMessage = "EXCEL�e���v���[�g�t�@�C���ɃA�N�Z�X�ł��܂���";
        }
      }
      restartCommand = request.getParameter("restartcommand");
      // 
      if (errorMessage == null) {
        // �G���[��������Εۑ�
        Connection conn = null;
        try {
          conn = getConnection();
          conn.setAutoCommit(false);
          String[] updateInfos = new String[]{"127.0.0.1", "MBB", "ADMIN", null};
          if (adminpassword2 != null && DbAccessUtils.comparePassword(adminpassword, DBACCESS_ADMINPASSWORD) == -1) {
            saveConfig(conn, "password", MD5Sum.md5Sum(adminpassword), updateInfos);
          }
          if (userpassword2 != null && DbAccessUtils.comparePassword(userpassword, DBACCESS_USERPASSWORD) == -1) {
            saveConfig(conn, "userpassword", MD5Sum.md5Sum(userpassword), updateInfos);
          }
          // �^�C�g��
          String title = request.getParameter("title");
          saveConfig(conn, "title", title, updateInfos);
          // �w�i�F
          String bgcolor = request.getParameter("bgcolor");
          saveConfig(conn, "bgcolor", bgcolor, updateInfos);
          // BODY�X�^�C��
          String bodystyle = request.getParameter("bodystyle");
          saveConfig(conn, "bodystyle", bodystyle, updateInfos);
          // ��r���
          saveConfig(conn, "stagingurl", stagingurl, updateInfos);
          // ��r����p�X���[�h
          saveConfig(conn, "stagingpass", stagingpass, updateInfos);
          // ��r��ڑ�Proxy
          saveConfig(conn, "stagingproxy", stagingproxy, updateInfos);
          // update��ƃp�X
          saveConfig(conn, "updateworkpath", updateworkpath, updateInfos);
          // �e���v���[�g�t�@�C��
          saveConfig(conn, "templatefile", templatefile, updateInfos);
          // ���X�^�[�g�R�}���h
          saveConfig(conn, "restartcommand", restartCommand, updateInfos);
          // �Ǘ��҃��j���[
          String adminmenu = request.getParameter("adminmenu");
          saveConfig(conn, "adminmenu", adminmenu, updateInfos);
          // ���[�U���j���[
          String usermenu = request.getParameter("usermenu");
          saveConfig(conn, "usermenu", usermenu, updateInfos);
          
          // ��r�Ώۃ��W���[��
          String[] modules = request.getParameterValues("module");
          if (modules != null) {
            clearConfigItems(conn, "module");
            compareModules.clear();
            for (int i = 0; i < modules.length; ++i) {
              saveConfigItem(conn, "module", modules[i]);
              if (!compareModules.contains(modules[i])) {
                compareModules.add(modules[i]);
              }
            }
          }
          // ���O���W���[��
          String[] listItems = request.getParameterValues("_ignoreList");
          if (listItems != null) {
            clearConfigItems(conn, "ignorepath");
            ignoreModules.clear();
            for (int i = 0; i < listItems.length; ++i) {
              saveConfigItem(conn, "ignorepath", listItems[i]);
              ignoreModules.add(listItems[i]);
            }
          }
          // �ۑ����𔽉f������
          loadConfig(conn);
          conn.commit();
          infoMessage = "�ݒ����ۑ����܂����B";
        } catch (Exception e) {
          printError(out, e);
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException e) {}
          }
        }
      }
    }
    if (adminpassword2 == null) {
      // �񊈐��������ꍇ�i���ύX�j�́A�����̂��Z�b�g����
      adminpassword2 = adminpassword;
    }
    if (userpassword2 == null) {
      // �񊈐��������ꍇ�i���ύX�j�́A�����̂��Z�b�g����
      userpassword2 = userpassword;
    }
    // �ݒ���(HTML-TABLE: 5��Ń��C�A�E�g)
    out.println("<table>");
    out.println("<col width=\"120\"><col width=\"200\"><col width=\"120\"><col width=\"200\"><col width=\"100\">");
    // �R���e�L�X�g���[�g�p�X�͕\���̂�
    out.print("<tr>");
    out.print("<td>�R���e�L�X�g���[�g</td>");
    out.print("<td colspan=\"5\">" + contextRoot + "</td>");
    out.println("</tr>");
    // �Ǘ��҃p�X���[�h
    out.print("<tr");
    if (adminpassword_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>�Ǘ��҃p�X���[�h</td>");
    out.print("<td><input type=\"password\" name=\"adminpassword\" value=\"" + escapeInputValue(adminpassword)
        + "\" onchange=\"var p=document.getElementsByName('adminpassword2')[0];p.disabled=false;p.value='';p.focus();\"");
    out.print(" onkeypress=\"if(event.keyCode==13){this.onblur=null;this.onchange();return false;}\"");
    if (adminpassword != null && adminpassword.trim().length() >= 32) {
      // 32�����ȏ�̃p�X���[�h������i�ʏ�̓n�b�V���l�j
      out.print(" onfocus=\"if(this.value.length==32)this.value='';\"");
      out.print(" onblur=\"if(this.value=='')this.value='" + escapeInputValue(adminpassword) + "';\"");
    } else {
      out.print(" onfocus=\"this.select();\"");
    }
    out.print("></td>");
    out.print("<td>(�ē���)</td>");
    out.print("<td><input type=\"password\" name=\"adminpassword2\" value=\"" + escapeInputValue(adminpassword2)
        + "\" onfocus=\"this.select();\"");
    if (!adminpassword_error) {
      // �G���[���Ȃ���Ώ�����Ԃ͔񊈐�
      out.print(" disabled");
    }
    out.print("></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ���[�U�p�X���[�h
    out.print("<tr");
    if (userpassword_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>���[�U�p�X���[�h</td>");
    out.print("<td><input type=\"password\" name=\"userpassword\" value=\"" + escapeInputValue(userpassword)
        + "\" onchange=\"var p=document.getElementsByName('userpassword2')[0];p.disabled=false;p.value='';p.focus();\"");
    out.print(" onkeypress=\"if(event.keyCode==13){this.onblur=null;this.onchange();return false;}\"");
    if (userpassword != null && userpassword.trim().length() >= 32) {
      // 32�����ȏ�̃p�X���[�h������i�ʏ�̓n�b�V���l�j
      out.print(" onfocus=\"if(this.value.length==32)this.value='';\"");
      out.print(" onblur=\"if(this.value=='')this.value='" + escapeInputValue(userpassword) + "';\"");
    } else {
      out.print(" onfocus=\"this.select();\"");
    }
    out.print("></td>");
    out.print("<td>(�ē���)</td>");
    out.print("<td><input type=\"password\" name=\"userpassword2\" value=\"" + escapeInputValue(userpassword2)
        + "\" onfocus=\"this.select();\"");
    if (!userpassword_error) {
      // �G���[���Ȃ���Ώ�����Ԃ͔񊈐�
      out.print(" disabled");
    }
    out.print(">");
    out.print("<span title=\"���[�U�p�X���[�h�͊Ǘ��҃p�X���[�h�����ݒ�̏ꍇ�͖����ł�\">&nbsp;?</span>");
    out.print("</td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ���^�C�g��
    out.print("<tr>");
    out.print("<td>���^�C�g��</td>");
    out.print("<td><input type=\"text\" name=\"title\" value=\"" + escapeInputValue(title) + "\"></td>");
    out.print("<td>�w�i�F</td>");
    out.print("<td><input type=\"text\" name=\"bgcolor\" value=\"" + escapeInputValue(bgColor) + "\"></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // BODY�X�^�C��
    out.print("<tr>");
    out.print("<td>BODY�X�^�C��</td>");
    out.print("<td colspan=\"3\"><input type=\"text\" name=\"bodystyle\" value=\"" + escapeInputValue(bodyStyle) + "\" size=\"60\"></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // �A�v���P�[�V�����p�X
    out.print("<tr");
    if (applicationpath_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>�A�v���P�[�V�����p�X</td>");
    out.print("<td colspan=\"3\"><input type=\"text\" name=\"applicationpath\" value=\"" + escapeInputValue(applicationpath) + "\" size=\"60\"></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ��r���
    out.print("<tr");
    if (stagingurl_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>��r���</td>");
    out.print("<td colspan=\"3\"><nobr><input type=\"text\" name=\"stagingurl\" value=\"" + escapeInputValue(stagingurl) + "\" size=\"60\">");
    out.print("&nbsp;�p�X���[�h<input type=\"password\" name=\"stagingpass\" value=\"" + escapeInputValue(stagingpass) + "\" size=\"8\">");
    out.print("</nobr></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ��r���proxy
    out.print("<tr");
    if (stagingproxy_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>��r��ڑ�Proxy</td>");
    out.print("<td colspan=\"3\"><nobr><input type=\"text\" name=\"stagingproxy\" value=\"" + escapeInputValue(stagingproxy) + "\" size=\"60\">");
    out.print("</nobr></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ��r�Ώ�
    out.print("<tr");
    out.print(">");
    out.print("<td>��r��Ώ�</td>");
    out.print("<td colspan=\"3\"><nobr>");
    for (int i = 0, j = 0; i < DEFAULT_MOD_ITEMS.length; ++i) {
      String modType = DEFAULT_MOD_ITEMS[i];
      if (!isSupportedModuleType(modType)) {
        continue;
      }
      String checked = "";
      if (compareModules.contains(modType)) {
        checked = " checked";
      }
      out.print("<input type=\"checkbox\" name=\"module\" value=\"" + escapeInputValue(modType) + "\"" + checked + ">" + modType + "&nbsp;");
      if ((((j++) + 1) % 6) == 0) {
        out.println("<br>");
      }
    }
    out.print("</nobr></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ���O�Ώ�
    out.print("<tr");
    out.print(">");
    out.print("<td>���O�Ώ�</td>");
    out.print("<td colspan=\"3\"><nobr>");
    out.print("<select id=\"_ignoreList\" name=\"_ignoreList\" size=\"4\" style=\"width:280px;\" onclick=\"selectListItem(this,false)\" ondblclick=\"selectListItem(this,true)\">");
    loadIgnoreModules();
    for (Iterator ite = ignoreModules.iterator(); ite.hasNext(); ) {
      String path = (String)ite.next(); // �Ώۂ�","���܂܂��ꍇ��"%2c"�ň����i","���Ə��O���f���ɕ�������邽�߁j
      out.print("<option value=\"" + escapeInputValue(path) + "\">" + path + "</option>");
    }
    out.print("</select>");
    out.print("<input type=\"button\" id=\"_idel\" style=\"width:32px;\" value=\"�폜\" onclick=\"removeListItem(document.getElementById('_ignoreList'))\" disabled>");
    out.print("&nbsp;�p�X<input type=\"text\" id=\"_ignoreItem\" name=\"_ignoreItem\" value=\"\" size=\"20\">");
    out.print("<input type=\"button\" id=\"_iadd\" style=\"width:32px;\" value=\"�ǉ�\" onclick=\"appendListItem(document.getElementById('_ignoreList'));document.getElementById('_ignoreItem').value='';\">");
    out.print("<span title=\"�p�X��','���܂܂��ꍇ��'%2c'���w�肵�Ă�������\">&nbsp;?</span>");
    out.print("</nobr></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // update��ƃp�X
    out.print("<tr");
    if (updateworkpath_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>�X�V��ƃp�X</td>");
    out.print("<td colspan=\"3\"><input type=\"text\" name=\"updateworkpath\" value=\"" + escapeInputValue(updateworkpath) + "\" size=\"60\"></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // Excel�e���v���[�g�t�@�C��
    out.print("<tr");
    if (templatefile_error) {
      out.print(" style=\"background-color:#ff0000;\"");
    }
    out.print(">");
    out.print("<td>EXCEL�e���v���[�g</td>");
    out.print("<td colspan=\"3\"><input type=\"text\" name=\"templatefile\" value=\"" + escapeInputValue(templatefile) + "\" size=\"60\"></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // ���X�^�[�g�R�}���h
    out.print("<tr");
    out.print(">");
    out.print("<td>���X�^�[�g�R�}���h</td>");
    out.print("<td colspan=\"3\"><input type=\"text\" name=\"restartcommand\" value=\"" + escapeInputValue(restartCommand) + "\" size=\"60\"></td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    // MBB���j���[�ݒ�
    out.print("<tr>");
    out.print("<td colspan=\"4\">MBB���j���[�ݒ�</td>");
    out.print("<td>");
    out.print("</td>");
    out.println("</tr>");
    out.print("<tr>");
    out.print("<td colspan=\"4\">");
    out.print("<table>");
    out.print("<tr>");
    out.print("<td width=\"10\">");
    out.print("</td>");
    out.print("<td>");
    out.print("<select id=\"_mbbMenu\" size=\"7\" style=\"width:200px;\" onchange=\"selectMenu(this)\">");
    for (int i = 0; i < MBB_MENU.length; ++i) {
      String menuId = MBB_MENU[i][0];
      String menuText = MBB_MENU[i][1];
      out.print("<option value=\"" + menuId + "\">" + menuText);
      out.print("</option>");
    }
    out.print("</select>");
    out.print("</td>");
    out.print("<td>");
    out.print("<input type=\"button\" id=\"_aadd\" style=\"width:24px;\" value=\"&gt;&gt;\" onclick=\"addMenu(this)\" disabled><br>");
    out.print("<input type=\"button\" id=\"_adel\" style=\"width:24px;\" value=\"&lt;&lt;\" onclick=\"delMenu(this)\" disabled><br>");
    out.print("<input type=\"button\" id=\"_aup\" style=\"width:24px;\" value=\"��\" onclick=\"upMenu(this)\" disabled><br>");
    out.print("<input type=\"button\" id=\"_adown\" style=\"width:24px;\" value=\"��\" onclick=\"downMenu(this)\" disabled><br>");
    out.print("</td>");
    out.print("<td>");
    out.print("�Ǘ��҃��j���[<br>");
    out.print("<select id=\"_adminMenu\" size=\"6\" style=\"width:200px;\" onchange=\"selectMenu(this)\">");
    for (int i = 0; i < adminMenus.size(); ++i) {
      String menuId = (String)adminMenus.get(i);
      String menuText = (String)mbbMenus.get(menuId);
      out.print("<option value=\"" + menuId + "\">" + menuText);
      out.print("</option>");
    }
    out.print("</select>");
    out.print("</td>");
    out.print("<td>");
    out.print("<input type=\"button\" id=\"_uadd\" style=\"width:24px;\" value=\"&gt;&gt;\" onclick=\"addMenu(this)\" disabled><br>");
    out.print("<input type=\"button\" id=\"_udel\" style=\"width:24px;\" value=\"&lt;&lt;\" onclick=\"delMenu(this)\" disabled><br>");
    out.print("<input type=\"button\" id=\"_uup\" style=\"width:24px;\" value=\"��\" onclick=\"upMenu(this)\" disabled><br>");
    out.print("<input type=\"button\" id=\"_udown\" style=\"width:24px;\" value=\"��\" onclick=\"downMenu(this)\" disabled><br>");
    out.print("</td>");
    out.print("<td>");
    out.print("���[�U���j���[<br>");
    out.print("<select id=\"_userMenu\" size=\"6\" style=\"width:200px;\" onchange=\"selectMenu(this)\">");
    for (int i = 0; i < userMenus.size(); ++i) {
      String menuId = (String)userMenus.get(i);
      String menuText = (String)mbbMenus.get(menuId);
      out.print("<option value=\"" + menuId + "\">" + menuText);
      out.print("</option>");
    }
    out.print("</select>");
    out.print("</td>");
    out.print("</tr>");
    out.print("</table>");
    out.print("</td>");
    out.print("<td>");
    out.print("<input type=\"hidden\" id=\"adminmenu\" name=\"adminmenu\" value=\"" + listToStr(adminMenus) + "\">");
    out.print("<input type=\"hidden\" id=\"usermenu\" name=\"usermenu\" value=\"" + listToStr(userMenus) + "\">");
    out.print("</td>");
    out.println("</tr>");
////TODO
//    // �ڑ��Ώېݒ�
//    out.print("<tr>");
//    out.print("<td colspan=\"4\">�ڑ��Ώېݒ�</td>");
//    out.print("<td></td>");
//    out.println("</tr>");
//    out.print("<tr>");
//    out.print("<td colspan=\"4\">");
//    out.print("</td>");
//    out.print("<td></td>");
//    out.println("</tr>");
//    
    out.println("</table>");
    
    out.print("<span style=\"font-size:10pt;\">");
    out.print("<input type=\"submit\" name=\"save\" value=\"�ۑ�\" onclick=\"if(confirm('�ݒ����ۑ����܂��B��낵���ł���?')){selectAllListItem(document.getElementById('_ignoreList'));doCommand('MBB','command','config save');}return false;\">");
    out.println("</span>");
    if (errorMessage != null && errorMessage.trim().length() > 0) {
      out.println("<script language=\"javascript\">");
      out.println("alert('" + errorMessage + "');");
      out.println("</script>");
    }
    if (infoMessage != null && infoMessage.trim().length() > 0) {
      out.println("<script language=\"javascript\">");
      out.println("window.status='" + infoMessage + "';");
      out.println("</script>");
    }
  }
  public static int compareVersion(String version1, String version2) {
    int major1 = getMajorVersion(version1);
    int major2 = getMajorVersion(version2);
    if (major1 < major2) {
      return -1;
    } else if (major1 > major2) {
      return 1;
    }
    int minor1 = getMinorVersion(version1);
    int minor2 = getMinorVersion(version2);
    if (minor1 < minor2) {
      return -1;
    } else if (minor1 > minor2) {
      return 1;
    }
    return 0;
  }
  private static int getMajorVersion(String version) {
    if (version == null) {
      return -1;
    }
    if (version.startsWith(".")) {
      version = "0" + version;
    }
    if (version.indexOf(".") == -1) {
      try {
        return Integer.parseInt(version);
      } catch (Exception e) {
        return -1;
      }
    } else {
      try {
        return Integer.parseInt(version.substring(0, version.indexOf(".")));
      } catch (Exception e) {
        return -1;
      }
    }
  }
  private static int getMinorVersion(String version) {
    if (version == null || version.indexOf(".") == -1) {
      return -1;
    }
    try {
      return Integer.parseInt(version.substring(version.indexOf(".") + 1));
    } catch (Exception e) {
      return -1;
    }
  }
  private static String listToStr(List list) {
    StringBuffer sb = new StringBuffer();
    for (Iterator ite = list.iterator(); ite.hasNext(); ) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(ite.next());
    }
    return sb.toString();
  }
  private static String getVersion(String ver) {
    if (ver == null) {
      return "0";
    }
    int p = ver.indexOf("ver.");
    if (p != -1) {
      ver = ver.substring(p + 4);
    }
    StringBuffer sb = new StringBuffer();
    int pc = 0;
    for (int i = 0; i < ver.length(); ++i) {
      char c = ver.charAt(i);
      if (c >= '0' && c <= '9') {
        sb.append(c);
      } else if (c == '.') {
        if (pc >= 1) {
          break;
        } else {
          sb.append(c);
          pc ++;
        }
      } else {
        break;
      }
    }
    if (sb.length() == 0) {
      return "0";
    }
    try {
      return sb.toString();
    } catch (Exception e) {
      return "0";
    }
  }
  private void saveConfig(Connection conn, String key, String value, String[] updateInfo) throws SQLException {
    PreparedStatement stmt = null;
    try {
      // ��U�ΏۃL�[���폜����
      String dsql = "DELETE FROM " + DBACCESS_CONFIG + " WHERE PROPERTYID = ?";
      stmt = conn.prepareStatement(dsql);
      stmt.setString(1, key);
      stmt.executeUpdate();
      stmt.close();
      stmt = null;
      // �ΏۃL�[��INSERT
      String isql = "INSERT INTO " + DBACCESS_CONFIG + " (PROPERTYID,VALUE,UPDATECOMPANYID,UPDATEUSERID,UPDATEPROCESSID,TIMESTAMPVALUE) VALUES (?,?,?,?,?,?)";
      stmt = conn.prepareStatement(isql);
      stmt.setString(1, key);
      stmt.setString(2, value);
      stmt.setString(3, updateInfo[1]);
      stmt.setString(4, updateInfo[2]);
      stmt.setString(5, "DBACCESS");
      String ts = DbAccessUtils.toTimestampString(System.currentTimeMillis());
      stmt.setString(6, ts);
      stmt.executeUpdate();
      stmt.close();
      stmt = null;
    } finally {
      if (stmt != null) {
        stmt.close();
      }
    }
  }
  /**
   * MBB���j���[���@�\�}�X�^��\��
   * @param out
   * @param request
   */
  private void printMBBFunctions(PrintWriter out, HttpServletRequest request) {
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"FUNCTION\">");
    out.println("<table>");
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>�@�\�}�X�^</td></tr>");
    out.println("</table>");
    String selectedDataSource = request.getParameter("datasource");
    String selectedPackageId = request.getParameter("packageid");
    String selectedFunctionId = request.getParameter("functionid");
    String deletefunction = request.getParameter("deletefunction");
    String copyfunction = request.getParameter("copyfunction");
    String updatefunction = request.getParameter("updatefunction");
    String filenameid = request.getParameter("filenameid");
    String filenamets = request.getParameter("filenamets");
    out.println("<table>");
    out.println("<tr><td>");
    Connection conn = null;
    Connection conn2 = null;
    boolean currentConnection = true;
    String localSchema = schemas[0];
    String remoteSchema = null;
    if (schemas.length > 1) {
      remoteSchema = schemas[1];
    }
    String localDB = "1";
    String remoteDB = "2";
    if (selectedDataSource != null && !selectedDataSource.equals("1")) {
      // ���[�J���ȊO��DB���I�����ꂽ�ꍇ
      try {
        int selds = Integer.parseInt(selectedDataSource);
        if (selds > 1 && selds <= schemas.length)
        //1�ȊO���I�΂��ΑΏۂ�����A�����[�g��1�Ƃ���
        localSchema = schemas[selds - 1];
        remoteSchema = schemas[0];
        localDB = selectedDataSource;
        remoteDB = "1";
        currentConnection = false;
      } catch (Exception e) {}
    }
    try {

      if (copyfunction != null) {
        // �@�\�}�X�^��DB�ԕ��ʏ����iDS2��DS1�j
        Connection connFrom = null;
        if (selectedDataSource != null) {
          connFrom = getConnection(selectedDataSource);
        }
        Connection connTo = getConnection(localDB);
        setAutoCommit(connFrom, "0");
        setAutoCommit(connTo, "0");
        copyFunction(connFrom, connTo, selectedFunctionId);
        if (connFrom != null) {
          // �O�̂��߃��[���o�b�N
          connFrom.rollback();
          connFrom.close();
        }
        if (connTo != null) {
          // �R�~�b�g
          connTo.commit();
          connTo.close();
        }
      } else if (deletefunction != null) {
        // �@�\�}�X�^�̍폜
        conn = getConnection(localDB);
        conn.setAutoCommit(false);
        deleteFunction(conn, selectedFunctionId, false);
        conn.commit();
        conn.close();
      } else if (updatefunction != null) {
        // �@�\�}�X�^�̍X�V�i�X�V���̐ݒ�j
        conn = getConnection(localDB);
        conn.setAutoCommit(false);
        updateFunction(conn, request);
        conn.commit();
        conn.close();
      }

      out.print("<table>");
      if (dataSources.length > 1) {
        printDataSources(out, selectedDataSource);
      }

      conn = getConnection(localDB);
      conn.setAutoCommit(false);
      if (dataSources.length > 1) {
        conn2 = getConnection(remoteDB);
        conn2.setAutoCommit(false);
      }
      
      Hashtable packages = getPackages(conn);

      String sql = "SELECT PACKAGEID, COUNT(FUNCTIONID) FROM FUNCTIONMASTER GROUP BY PACKAGEID ORDER BY PACKAGEID";
      PreparedStatement stmt = conn.prepareStatement(sql);
      ResultSet rs = stmt.executeQuery();
      out.print("<tr><td>");
      out.print("�p�b�P�[�W�F");
      out.print("<td>");
      out.println("<select name=\"packageid\" onchange=\"doTab('MBB');\">");
      boolean hasSelected = false;
      while (rs.next()) {
        String packageId = rs.getString(1);
        if (packageId == null)  {
          packageId = "";
        }
        String packageCount = rs.getString(2);
        String packageName = (String)packages.get(packageId);
        if (packageName == null) {
          packageName = "";
        }
        String sel = "";
        if (selectedPackageId == null) {
          selectedPackageId = packageId;
        }
        if (selectedPackageId != null && selectedPackageId.equals(packageId)) {
          sel = " selected";
          hasSelected = true;
        }
        out.println("<option value=\"" + packageId + "\"" + sel + ">" + packageId + " " + packageName + " (" + packageCount + ")");
      }
      String packageId = "%";
      String packageName = "�S��";
      String selp = "";
      if (selectedPackageId != null && selectedPackageId.equals(packageId)) {
        selp = " selected";
        hasSelected = true;
      }
      out.println("<option value=\"%\"" + selp + ">" + packageId + " " + packageName);
      if (!hasSelected && selectedPackageId != null) {
        out.println("<option value=\"" + selectedPackageId + "\" selected>" + selectedPackageId + "  (0)");
      }
      out.println("</select>");
      rs.close();
      stmt.close();
      if (selectedPackageId != null) {
        String isnull = "";
        if (selectedPackageId.length() == 0) {
          selectedPackageId = " ";
          isnull = " OR PACKAGEID IS NULL";
        }
        String packageCond = "PACKAGEID=?";
        if ("%".equals(selectedPackageId)) {
          packageCond = "PACKAGEID LIKE ?";
        }
        sql = "SELECT FUNCTIONID, (SELECT NAMEVALUE FROM FUNCTIONNAME WHERE FUNCTIONID=a.FUNCTIONID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') FROM FUNCTIONMASTER a WHERE " + packageCond + isnull + " ORDER BY FUNCTIONID";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, selectedPackageId);
        rs = stmt.executeQuery();
        out.print("<tr><td>");
        out.print("�@�\�F");
        out.print("<td>");
        out.println("<select name=\"functionid\" onchange=\"doTab('MBB');\">");
        String firstFunctionId = null;
        boolean selected = false;
        if (deletefunction != null) {
          out.println("<option value=\"" + selectedFunctionId + "\" selected>" + selectedFunctionId + " (�폜����܂���)");
        }
        while (rs.next()) {
          String functionId = rs.getString(1);
          String functionName = rs.getString(2);
          String sel = "";
          if (firstFunctionId == null) {
            firstFunctionId = functionId;
          }
          if (selectedFunctionId != null && selectedFunctionId.equals(functionId)) {
            sel = " selected";
            selected = true;
          }
          if (!compareFunctionComposition(functionId, conn, conn2)) {
            out.println("<option value=\"" + functionId + "\"" + sel + " style=\"color:" + DIFF_COLOR + ";\">" + functionId + " " + functionName + "*");
          } else {
            out.println("<option value=\"" + functionId + "\"" + sel + ">" + functionId + " " + functionName);
          }
        }
        out.println("</select>");
        rs.close();
        stmt.close();
        if (!selected) {
          selectedFunctionId = firstFunctionId;
        }
      }
      out.print("</table>");
      out.print("<input type=\"submit\" name=\"refresh\" value=\"�ĕ\��\" onclick=\"doCommand('MBB','refresh','1');return false;\">");
      if (selectedDataSource == null || selectedDataSource.equals("1")) {
        out.println("<input type=\"button\" value=\"�G�N�X�|�[�g\" onclick=\"doDownload('FUNCTIONMASTER','"
            + selectedFunctionId
            + "',document.getElementById('filenameid').checked,document.getElementById('filenamets_0').checked"
            + ",document.getElementById('filenamets_1').checked,document.getElementById('filenamets_2').checked"
            + ");\">");
        // �I�v�V�����{�^��
        out.print("&nbsp;&nbsp;&nbsp;");
        out.print("<span id=\"_optionslabel\" style=\"\">");
        out.print("<a href=\"javascript:void(0);\" onclick=\"document.getElementById('_optionslabel').style.display='none';document.getElementById('_options').style.display='';\">�t�@�C�����I�v�V����&gt;&gt;</a>");
        out.print("</span>");
        out.print("<span id=\"_options\" style=\"display:none;\" class=\"text\">");
        out.print("�t�@�C�����I�v�V����:");
        out.print("&nbsp;");
        out.print("<input type=\"checkbox\" name=\"filenameid\" id=\"filenameid\"");
        if (filenameid != null) {
          out.print(" checked");
        }
        out.print(">");
        out.print("<label for=\"filenameid\">ID���t�@�C�����ɂ���</label>");
        out.print("&nbsp;&nbsp;");
        out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_1\" value=\"1\"");
        if (filenamets == null || filenamets.equals("1")) {
          out.print(" checked");
        }
        out.print(">");
        out.print("<label for=\"filenamets_1\" class=\"text\">���s���t��t��</label>");
        out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_2\" value=\"2\"");
        if (filenamets != null && filenamets.equals("2")) {
          out.print(" checked");
        }
        out.print(">");
        out.print("<label for=\"filenamets_2\" class=\"text\">���s���t���Ԃ�t��</label>");
        out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_0\" value=\"0\"");
        if (filenamets != null && filenamets.equals("0")) {
          out.print(" checked");
        }
        out.print(">");
        out.print("<label for=\"filenamets_0\" class=\"text\">������t�����Ȃ�</label>");
        out.print("</span>");
      }
      out.print("<hr>");
      if (selectedFunctionId != null && deletefunction == null) {
        // �@�\�\�����̕\��
        sql = "SELECT FUNCTIONCOMPOSITIONID, FUNCTIONCOMPOSITIONCLASS, TIMESTAMPVALUE FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=? ORDER BY FUNCTIONCOMPOSITIONCLASS, FUNCTIONCOMPOSITIONID";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, selectedFunctionId);
        rs = stmt.executeQuery();
        out.println("<table style=\"font-size:10pt;\">");
        String schemastr = "";
        if (localSchema != null) {
          schemastr = "(" + localSchema + ")";
        }
        out.println("<tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\"><th>�@�\�\��ID<th>����<th>�p�b�P�[�WID<th>�^�C���X�^���v" + schemastr);
        if (dataSourceNames.length > 1) {
          if (currentConnection) {
            out.println("<th>�����[�g�^�C���X�^���v(" + remoteSchema + ")");
          } else {
            out.println("<th>���[�J���^�C���X�^���v(" + remoteSchema + ")");
          }
        }
        while (rs.next()) {
          String functionCompositionId = rs.getString(1);
          String functionCompositionClass = rs.getString(2);
          String functionCompositionTS = rs.getString(3);
          String timestampValue = "";
          String timestampValue2 = "";
          String packageId1 = "";
          String packageId2 = "";
          String functionCompositionName = "";
          if (functionCompositionClass.equals("1")) {
            // �A�v���P�[�V�����}�X�^
            PreparedStatement astmt = conn.prepareStatement("SELECT TIMESTAMPVALUE, (SELECT NAMEVALUE FROM APPLICATIONNAME WHERE APPLICATIONID=a.APPLICATIONID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') FROM APPLICATIONMASTER a WHERE APPLICATIONID=?");
            astmt.setString(1, functionCompositionId);
            ResultSet ars = astmt.executeQuery();
            if (ars.next()) {
              timestampValue = DbAccessUtils.formatTimestampValue(ars.getString(1));
              functionCompositionName = ars.getString(2);
            }
            ars.close();
            astmt.close();
            if (conn2 != null) {
              astmt = conn2.prepareStatement("SELECT TIMESTAMPVALUE FROM APPLICATIONMASTER a WHERE APPLICATIONID=?");
              astmt.setString(1, functionCompositionId);
              ars = astmt.executeQuery();
              if (ars.next()) {
                timestampValue2 = DbAccessUtils.formatTimestampValue(ars.getString(1));
              }
              ars.close();
              astmt.close();
            }
          } else if (functionCompositionClass.equals("2")) {
            // �v���Z�X�}�X�^
            PreparedStatement astmt = conn.prepareStatement("SELECT PACKAGEID,TIMESTAMPVALUE, (SELECT NAMEVALUE FROM PROCESSNAME WHERE PROCESSID=a.PROCESSID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') FROM PROCESSMASTER a WHERE PROCESSID=?");
            astmt.setString(1, functionCompositionId);
            ResultSet ars = astmt.executeQuery();
            if (ars.next()) {
              packageId1 = ars.getString(1);
              timestampValue = DbAccessUtils.formatTimestampValue(ars.getString(2));
              functionCompositionName = ars.getString(3);
            }
            ars.close();
            astmt.close();
            if (conn2 != null) {
              astmt = conn2.prepareStatement("SELECT PACKAGEID,TIMESTAMPVALUE FROM PROCESSMASTER a WHERE PROCESSID=?");
              astmt.setString(1, functionCompositionId);
              ars = astmt.executeQuery();
              if (ars.next()) {
                packageId2 = ars.getString(1);
                timestampValue2 = DbAccessUtils.formatTimestampValue(ars.getString(2));
              }
              ars.close();
              astmt.close();
            }
          } else if (functionCompositionClass.equals("3")) {
            // �y�[�W�}�X�^
            PreparedStatement astmt = conn.prepareStatement("SELECT PACKAGEID,TIMESTAMPVALUE,"
                + " (SELECT NAMEVALUE FROM PAGENAME WHERE PAGEID=a.PAGEID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') NAMEVALUE,"
                + " (SELECT MAX(NAMEVALUE) FROM PAGENAME WHERE PAGEID=a.PAGEID AND PROPERTYID='OFFICIALNAME') NAMEVALUE2"
                + " FROM PAGEMASTER a WHERE PAGEID=?");
            astmt.setString(1, functionCompositionId);
            ResultSet ars = astmt.executeQuery();
            if (ars.next()) {
              packageId1 = ars.getString(1);
              timestampValue = DbAccessUtils.formatTimestampValue(ars.getString(2));
              functionCompositionName = ars.getString(3);
              if (functionCompositionName == null) {
                functionCompositionName = ars.getString(4);
              }
            }
            ars.close();
            astmt.close();
            if (conn2 != null) {
              astmt = conn2.prepareStatement("SELECT PACKAGEID,TIMESTAMPVALUE FROM PAGEMASTER WHERE PAGEID=?");
              astmt.setString(1, functionCompositionId);
              ars = astmt.executeQuery();
              if (ars.next()) {
                packageId2 = ars.getString(1);
                timestampValue2 = DbAccessUtils.formatTimestampValue(ars.getString(2));
              }
              ars.close();
              astmt.close();
            }
          } else {
            // �A�v���E�v���Z�X�E��ʈȊO�͋@�\�\���}�X�^�̃^�C���X�^���v��\��
            timestampValue = functionCompositionTS;
          }
          boolean hasRemote = false;
          if (timestampValue2 != null && timestampValue2.trim().length() > 0) {
            hasRemote = true;
          }
          String trstyle = "";
          if (hasRemote && DbAccessUtils.compareTimestamp(timestampValue, timestampValue2) != 0) {
            trstyle = " style=\"background-color:" + DIFF_COLOR + ";\"";
          }
          String compare1 = "";
          String compare2 = "";
          if (dataSources.length > 1 && hasRemote) {
            String cmd = "&execsql=1";
            if (currentConnection) {
              cmd = cmd + "&datasource=" + remoteDB;
            } else {
              cmd = cmd + "&datasource=" + selectedDataSource;
            }
            if (functionCompositionClass != null && functionCompositionClass.equals("2")) {
              // �v���Z�X�̏ꍇ
              compare1 = "<a href=\"dbaccess?tab=Command&command=compare%20processid=" + functionCompositionId + cmd + "\">";
              compare2 = "</a>";
            } else if (functionCompositionClass != null && functionCompositionClass.equals("3")) {
              // �y�[�W�̏ꍇ
              compare1 = "<a href=\"dbaccess?tab=Command&command=compare%20pageid=" + functionCompositionId + cmd + "\">";
              compare2 = "</a>";
            } else if (functionCompositionClass != null && functionCompositionClass.equals("1")) {
              // �A�v���P�[�V�����̏ꍇ
              compare1 = "<a href=\"dbaccess?tab=Command&command=compare%20applicationid=" + functionCompositionId + cmd + "\">";
              compare2 = "</a>";
            }
          }
          String pkg = packageId1;
          if (hasRemote && packageId1 != null && !packageId1.equals(packageId2)) {
            pkg = packageId1 + " (" + packageId2 + ")";
          }
          out.println("<tr" + trstyle + "><td>" + compare1 + functionCompositionId + compare2 + "<td>" + functionCompositionName + "<td>" + pkg + "<td>" + timestampValue + "<td>" + timestampValue2);
        }
        out.println("</table>");
        rs.close();
        stmt.close();
        out.println("<input type=\"submit\" name=\"deletefunction\" value=\"�@�\�폜\" onclick=\"if(confirm('�@�\�폜�����s���܂��B��낵���ł���?')){doCommand('MBB','deletefunction','1');}return false;\">");
      }
      if (selectedDataSource != null && !selectedDataSource.equals("1") && selectedFunctionId != null) {
        out.println("<input type=\"submit\" name=\"copyfunction\" value=\"�@�\�ڑ�(" + localSchema + "->" + remoteSchema + ")\" onclick=\"if(confirm('�@�\�ڑ�[" + localSchema + "->" + remoteSchema + "]�����s���܂��B��낵���ł���?')){doCommand('MBB','copyfunction','1');}return false;\">");
      } else {
        String updateCompanyId = request.getParameter("updatecompanyid");
        String updateUserId = request.getParameter("updateuserid");
        String updateProcessId = request.getParameter("updateprocessid");
        String timestampvalue = request.getParameter("timestampvalue");
        if (updateCompanyId == null) {
          updateCompanyId = "";
        }
        if (updateUserId == null) {
          updateUserId = "";
        }
        if (updateProcessId == null) {
          updateProcessId = "DBACCESS";
        }
        if (timestampvalue == null) {
          timestampvalue =  new Timestamp(System.currentTimeMillis()).toString();
        }
        out.print("<span style=\"font-size:10pt;\">");
        out.print("<input type=\"submit\" name=\"updatefunction\" value=\"�@�\�ꊇ�X�V\" onclick=\"if(confirm('�@�\�y�ы@�\�\��ID�̍X�V�����ꊇ�X�V���܂��B��낵���ł���?')){doCommand('MBB','updatefunction','1');}return false;\">");
        out.print("�X�V��ЃR�[�h:<input type=\"text\" name=\"updatecompanyid\" size=\"5\" value=\"" + updateCompanyId + "\">");
        out.print("�X�V���[�U�R�[�h:<input type=\"text\" name=\"updateuserid\" size=\"5\" value=\"" + updateUserId + "\">");
        out.print("�X�V�v���Z�XID:<input type=\"text\" name=\"updateprocessid\" size=\"10\" value=\"" + updateProcessId + "\">");
        out.print("�^�C���X�^���v:<input type=\"text\" name=\"timestampvalue\" size=\"20\" value=\"" + timestampvalue + "\">");
        out.println("</span>");
      }
      
    } catch (SQLException e) {
      if (conn != null) {
        try {
          conn.rollback();
        } catch (SQLException ex) {}
      }
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
      if (conn2 != null) {
        try {
          conn2.close();
        } catch (SQLException e) {}
      }
    }
    out.println("</table>");
  }
  
  /**
   * �@�\�\������v���邩�`�F�b�N
   * @param functionId
   * @param conn
   * @param conn2
   * @return ��v����(�܂���conn2==null)�Ftrue
   */
  private boolean compareFunctionComposition(String functionId, Connection conn, Connection conn2) {
    if (conn2 == null) {
      return true;
    }
    boolean diff = false;
    try {
      PreparedStatement stmt1 = conn.prepareStatement("SELECT FUNCTIONCOMPOSITIONID,FUNCTIONCOMPOSITIONCLASS,TIMESTAMPVALUE FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=? ORDER BY FUNCTIONCOMPOSITIONCLASS,FUNCTIONCOMPOSITIONID");
      stmt1.setString(1, functionId);
      PreparedStatement stmt2 = conn2.prepareStatement("SELECT FUNCTIONCOMPOSITIONID,FUNCTIONCOMPOSITIONCLASS,TIMESTAMPVALUE FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=? ORDER BY FUNCTIONCOMPOSITIONCLASS,FUNCTIONCOMPOSITIONID");
      stmt2.setString(1, functionId);
      ResultSet rs1 = stmt1.executeQuery();
      ResultSet rs2 = stmt2.executeQuery();
      while (rs1.next()) {
        if (!rs2.next()) {
          // rs2���I��
          diff = true;
          break;
        }
        String fc1 = rs1.getString(1);
        String fcc1 = rs1.getString(2);
        String ts1 = rs1.getString(3);
        String fc2 = rs2.getString(1);
        String fcc2 = rs2.getString(2);
        String ts2 = rs2.getString(3);
        if (!fc1.equals(fc2) || (fcc1 != null && !fcc1.equals(fcc2)) || DbAccessUtils.compareTimestamp(ts1, ts2) != 0) {
          // �@�\�\�����ɈႢ������΂����ɏI��
          diff = true;
          break;
        }
        
        // �e�\��ID�̎��ۂ̃^�C���X�^���v�����擾
        String sql = null;
        boolean pkg = false;
        if (fcc1 != null && fcc1.equals("1")) {
          // �A�v���P�[�V�����}�X�^
          sql = "SELECT TIMESTAMPVALUE FROM APPLICATIONMASTER WHERE APPLICATIONID=?";
        } else if (fcc1 != null && fcc1.equals("2")) {
          sql = "SELECT TIMESTAMPVALUE,PACKAGEID FROM PROCESSMASTER WHERE PROCESSID=?";
          pkg = true;
        } else if (fcc1 != null && fcc1.equals("3")) {
          sql = "SELECT TIMESTAMPVALUE,PACKAGEID FROM PAGEMASTER WHERE PAGEID=?";
          pkg = true;
        }
        PreparedStatement astmt = conn.prepareStatement(sql);
        astmt.setString(1, fc1);
        ResultSet ars = astmt.executeQuery();
        if (ars.next()) {
          ts1 = DbAccessUtils.formatTimestampValue(ars.getString(1));
          if (pkg) {
            ts1 += ars.getString(2);
          }
        }
        ars.close();
        astmt.close();
        astmt = conn2.prepareStatement(sql);
        astmt.setString(1, fc2);
        ars = astmt.executeQuery();
        if (ars.next()) {
          ts2 = DbAccessUtils.formatTimestampValue(ars.getString(1));
          if (pkg) {
            ts2 += ars.getString(2);
          }
        }
        ars.close();
        astmt.close();
        if (ts1 == null) {
          if (ts2 != null) {
            diff = true;
            break;
          }
          ts1 = "";
        }
        if (!ts1.equals(ts2)) {
          diff = true;
          break;
        }
      }
      if (rs2.next()) {
        // rs2���܂�����
        diff = true;
      }
      rs1.close();
      rs2.close();
      stmt1.close();
      stmt2.close();
    } catch(SQLException e) {
      
    }
    return !diff;
  }
  
  /**
   * MBB���j���[���e�[�u���}�X�^��\��
   * @param out
   * @param request
   */
  private void printMBBTables(PrintWriter out, HttpServletRequest request) {
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"TABLE\">");
    out.println("<table>");
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>�e�[�u���}�X�^</td></tr>");
    out.println("</table>");
    String selectedDataSource = request.getParameter("datasource");
    String selectedPackageId = request.getParameter("packageid");
    String selectedTableId = request.getParameter("tableid");
    String deletetablelayout = request.getParameter("deletetablelayout");
    String copytablelayout = request.getParameter("copytablelayout");
    String copydatafield = request.getParameter("copydatafield");
    boolean currentConnection = true;
    String localSchema = schemas[0];
    String remoteSchema = null;
    if (schemas.length > 1) {
      remoteSchema = schemas[1];
    }
    String localDB = "1";
    String remoteDB = "2";
    if (selectedDataSource != null && !selectedDataSource.equals("1")) {
      // ���[�J���ȊO��DB��I�����ꂽ�ꍇ
      try {
        int selds = Integer.parseInt(selectedDataSource);
        if (selds > 1 && selds <= schemas.length)
        //1�ȊO���I�΂��ΑΏۂ�����A�����[�g��1�Ƃ���
        localSchema = schemas[selds - 1];
        remoteSchema = schemas[0];
        localDB = selectedDataSource;
        remoteDB = "1";
        currentConnection = false;
      } catch (Exception e) {}
    }
    out.println("<table>");
    out.println("<tr><td>");
    Connection conn = null;
    Connection conn2 = null;
    try {

      if (copytablelayout != null) {
        // �e�[�u�����C�A�E�g��DB�ԕ��ʏ����iDS2��DS1�j
        // TODO: export/import�Ƌ@�\�����Ԃ邪�������x�����Ⴂ�H
        Connection connFrom = getConnection(remoteDB);
        Connection connTo = getConnection(localDB);
        setAutoCommit(connFrom, "0");
        setAutoCommit(connTo, "0");
        copyTableLayout(connFrom, connTo, selectedTableId, copydatafield != null);
        if (connFrom != null) {
          // �O�̂��߃��[���o�b�N
          connFrom.rollback();
          connFrom.close();
        }
        if (connTo != null) {
          // �R�~�b�g
          connTo.commit();
          connTo.close();
        }
      } else if (deletetablelayout != null) {
        // �e�[�u�����C�A�E�g�̍폜
        if (selectedDataSource == null || selectedDataSource.equals("1")) {
          conn = getConnection();
        } else {
          conn = getConnection(selectedDataSource);
        }
        conn.setAutoCommit(false);
        deleteTableLayout(conn, selectedTableId);
        conn.commit();
        String droptable = request.getParameter("droptable");
        if (droptable != null && droptable.length() > 0) {
          try {
            String sql = "DROP TABLE " + droptable;
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.execute();
            stmt.close();
          } catch(Exception e) {
            log_debug(e);
          }
          if (droptable.length() > 6 && droptable.endsWith("MASTER")) {
            droptable = droptable.substring(0, droptable.length() - 6);
          }
          try {
            String sql = "DROP TABLE " + droptable + "INFO";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.execute();
            stmt.close();
          } catch(Exception e) {}
          try {
            String sql = "DROP TABLE " + droptable + "NAME";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.execute();
            stmt.close();
          } catch(Exception e) {}
          conn.commit();
        }
        conn.close();
      }

      out.print("<table>");
      if (dataSources.length > 1) {
        printDataSources(out, selectedDataSource);
      }

      conn = getConnection(localDB);
      conn.setAutoCommit(false);
      if (dataSources.length > 1) {
        conn2 = getConnection(remoteDB);
        if (conn2 != null) {
          conn2.setAutoCommit(false);
        }
      }
      
      Hashtable packages = getPackages(conn);

      String sql = "SELECT PACKAGEID, COUNT(PACKAGEID) FROM TABLEMASTER GROUP BY PACKAGEID ORDER BY PACKAGEID";
      PreparedStatement stmt = conn.prepareStatement(sql);
      ResultSet rs = stmt.executeQuery();
      out.print("<tr><td>");
      out.print("�p�b�P�[�W�F");
      out.print("<td>");
      out.println("<select name=\"packageid\" onchange=\"doTab('MBB');\">");
      boolean hasSelected = false;
      while (rs.next()) {
        String packageId = rs.getString(1);
        String packageCount = rs.getString(2);
        String packageName = (String)packages.get(packageId);
        if (packageName == null) {
          packageName = "";
        }
        String sel = "";
        if (selectedPackageId == null) {
          selectedPackageId = packageId;
        }
        if (selectedPackageId != null && selectedPackageId.equals(packageId)) {
          sel = " selected";
          hasSelected = true;
        }
        out.println("<option value=\"" + packageId + "\"" + sel + ">" + packageId + " " + packageName + " (" + packageCount + ")");
      }
      String packageId = "%";
      String packageName = "�S��";
      String selp = "";
      if (selectedPackageId != null && selectedPackageId.equals(packageId)) {
        selp = " selected";
        hasSelected = true;
      }
      out.println("<option value=\"%\"" + selp + ">" + packageId + " " + packageName);
      if (!hasSelected && selectedPackageId != null) {
        out.println("<option value=\"" + selectedPackageId + "\" selected>" + selectedPackageId + "  (0)");
      }
      out.println("</select>");
      rs.close();
      stmt.close();
      if (selectedPackageId != null) {
        String isnull = "";
        if (selectedPackageId.length() == 0) {
          selectedPackageId = " ";
          isnull = " OR PACKAGEID IS NULL";
        }
        String packageCond = "PACKAGEID=?";
        if ("%".equals(selectedPackageId)) {
          packageCond = "PACKAGEID LIKE ?";
        }
        sql = "SELECT a.TABLEID, a.TIMESTAMPVALUE, (SELECT NAMEVALUE FROM TABLENAME WHERE TABLEID=a.TABLEID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') FROM TABLEMASTER a WHERE " + packageCond + isnull + " ORDER BY TABLEID";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, selectedPackageId);
        rs = stmt.executeQuery();
        out.print("<tr><td>");
        out.print("�_���e�[�u���F");
        out.print("<td>");
        out.println("<select name=\"tableid\" onchange=\"doTab('MBB');\">");
        String firstTableId = null;
        boolean selected = false;
        if (deletetablelayout != null) {
          out.println("<option value=\"" + selectedTableId + "\" selected>" + selectedTableId + " (�폜����܂���)");
        }
        while (rs.next()) {
          String tableId = rs.getString(1);
          String ts1 = rs.getString(2);
          String tableName = rs.getString(3);
          String sel = "";
          if (firstTableId == null) {
            firstTableId = tableId;
          }
          if (selectedTableId != null && selectedTableId.equals(tableId)) {
            sel = " selected";
            selected = true;
          }
          String ts2 = null;
          if (conn2 != null) {
            ts2 = getTableTimestamp(tableId, conn2);
          }
          if (conn2 == null || ts1 != null && ts1.equals(ts2)) {
            out.println("<option value=\"" + tableId + "\"" + sel + ">" + tableId + " " + tableName);
          } else {
            // ��r���ق���
            out.println("<option value=\"" + tableId + "\" style=\"color:" + DIFF_OLDER_COLOR + ";\" title=\"" + ts1 + ":" + ts2 + "\"" + sel + ">" + tableId + " " + tableName);
          }
        }
        out.println("</select>");
        rs.close();
        stmt.close();
        if (!selected) {
          selectedTableId = firstTableId;
        }
      }
      out.print("</table>");
      out.print("<input type=\"submit\" name=\"refresh\" value=\"�ĕ\��\" onclick=\"doCommand('MBB','refresh','1');return false;\">");
      if (selectedDataSource == null || selectedDataSource.equals("1")) {
        out.println("<input type=\"button\" value=\"�G�N�X�|�[�g\" onclick=\"doDownload('TABLEMASTER','"
            + selectedTableId
            + "',document.getElementById('filenameid').checked,document.getElementById('filenamets_0').checked"
            + ",document.getElementById('filenamets_1').checked,document.getElementById('filenamets_2').checked"
            + ");\">");
        // �I�v�V�����{�^��
        out.print("&nbsp;&nbsp;&nbsp;");
        out.print("<span id=\"_optionslabel\" style=\"\">");
        out.print("<a href=\"javascript:void(0);\" onclick=\"document.getElementById('_optionslabel').style.display='none';document.getElementById('_options').style.display='';\">�t�@�C�����I�v�V����&gt;&gt;</a>");
        out.print("</span>");
        out.print("<span id=\"_options\" style=\"display:none;\" class=\"text\">");
        out.print("�t�@�C�����I�v�V����:");
        out.print("&nbsp;");
        out.print("<input type=\"checkbox\" name=\"filenameid\" id=\"filenameid\">");
        out.print("<label for=\"filenameid\">ID���t�@�C�����ɂ���</label>");
        out.print("&nbsp;&nbsp;");
        out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_1\" value=\"1\" checked>");
        out.print("<label for=\"filenamets_1\" class=\"text\">���s���t��t��</label>");
        out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_2\" value=\"2\">");
        out.print("<label for=\"filenamets_2\" class=\"text\">���s���t���Ԃ�t��</label>");
        out.print("<input type=\"radio\" name=\"filenamets\" id=\"filenamets_0\" value=\"0\">");
        out.print("<label for=\"filenamets_0\" class=\"text\">������t�����Ȃ�</label>");
        out.print("</span>");
      }
      out.print("<hr>");
      if (selectedTableId != null && deletetablelayout == null) {
        // �e�[�u�����(TIMESTAMPVALUE)�̕\��
        PreparedStatement astmt = null;
        ResultSet ars = null;
        String tableTS1 = null;
        String tableTS2 = null;
        sql = "SELECT TIMESTAMPVALUE FROM TABLEMASTER WHERE TABLEID=?";
        astmt = conn.prepareStatement(sql);
        astmt.setString(1, selectedTableId);
        ars = astmt.executeQuery();
        if (ars.next()) {
          tableTS1 = ars.getString(1);
        }
        ars.close();
        astmt.close();
        if (conn2 != null) {
          try {
            astmt = conn2.prepareStatement(sql);
            astmt.setString(1, selectedTableId);
            ars = astmt.executeQuery();
            if (ars.next()) {
              tableTS2 = ars.getString(1);
            }
            ars.close();
          } catch(SQLException se) {
          } finally {
            if (astmt != null) {
              astmt.close();
            }
          }
        }

        if (dataSourceNames.length > 1) {
          out.println("<table style=\"font-size:10pt;\">");
          out.println("<tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\"><th>�e�[�u��ID<th>�^�C���X�^���v(" + localSchema + ")");
          if (currentConnection) {
            out.println("<th>�����[�g�^�C���X�^���v(" + remoteSchema + ")");
          } else {
            out.println("<th>���[�J���^�C���X�^���v(" + remoteSchema + ")");
          }
          String trstyle = "";
          if (tableTS1 != null && tableTS1.trim().length() > 0 && !tableTS1.equals(tableTS2)) {
            trstyle = " style=\"background-color:" + DIFF_COLOR + ";\"";
          }
          out.println("<tr" + trstyle + "><td>" + selectedTableId + "<td>" + tableTS1 + "<td>" + tableTS2);
          out.println("</table>");
        }
        
        sql = "SELECT TABLEID, DATAFIELDID, DATAFIELDCLASS,"
          + " (SELECT DATATYPE FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DATATYPE,"
          + " (SELECT DIGIT FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DIGIT,"
          + " (SELECT DECIMALPLACE FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DECIMALPLACE,"
          + " (SELECT TIMESTAMPVALUE FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DFTIMESTAMPVALUE,"
          + " (SELECT NAMEVALUE FROM DATAFIELDNAME WHERE DATAFIELDID=a.DATAFIELDID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') DATAFIELDNAME"
          + " FROM TABLELAYOUTMASTER a WHERE TABLEID=? ORDER BY DATAFIELDORDER, DATAFIELDID";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, selectedTableId);
        rs = stmt.executeQuery();
        out.println("<table style=\"font-size:10pt;\">");
        out.println("<tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\"><th>�f�[�^�t�B�[���hID<th><th>����<th>����<th>�^�C���X�^���v(" + localSchema + ")");
        if (dataSourceNames.length > 1) {
          if (currentConnection) {
            out.println("<th>�����[�g�^�C���X�^���v(" + remoteSchema + ")");
          } else {
            out.println("<th>���[�J���^�C���X�^���v(" + remoteSchema + ")");
          }
        }
        while (rs.next()) {
          String tableId = rs.getString(1);
          String datafieldId = rs.getString(2);
          String datafieldClass = rs.getString(3);
          String dataType = rs.getString(4);
          String digit = rs.getString(5);
          String decimalPlace = rs.getString(6);
          String datafieldClass2 = null;
          String timestampValue = rs.getString(7);
          String timestampValue2 = "";
          String datafieldName = rs.getString(8);
          if (conn2 != null) {
            astmt = conn2.prepareStatement("SELECT (SELECT DATAFIELDCLASS FROM TABLELAYOUTMASTER WHERE TABLEID=? AND DATAFIELDID=a.DATAFIELDID) DATAFIELDCLASS, TIMESTAMPVALUE FROM DATAFIELDMASTER a WHERE DATAFIELDID=?");
            astmt.setString(1, tableId);
            astmt.setString(2, datafieldId);
            ars = astmt.executeQuery();
            if (ars.next()) {
              datafieldClass2 = ars.getString(1);
              timestampValue2 = ars.getString(2);
            }
            ars.close();
            astmt.close();
          }
          String trstyle = "";
          if (datafieldClass != null && datafieldClass2 != null && !datafieldClass.equals(datafieldClass2)) {
            // datafieldClass��null�̏ꍇ�͍l������Ă��Ȃ����A�ʏ�͂��肦�Ȃ�
            datafieldClass = datafieldClass + " [" + datafieldClass2 + "]";
            trstyle = " style=\"background-color:" + DIFF_COLOR + ";\"";
          }
          if (timestampValue2 != null && timestampValue2.trim().length() > 0 && DbAccessUtils.compareTimestamp(timestampValue, timestampValue2) != 0) {
            trstyle = " style=\"background-color:" + DIFF_COLOR + ";\"";
          }
          String compare1 = "";
          String compare2 = "";
          if (dataSourceNames.length > 1) {
            String cmd = "&execsql=1";
            if (currentConnection) {
              cmd = cmd + "&datasource=" + remoteDB;
            } else {
              cmd = cmd + "&datasource=" + selectedDataSource;
            }
            compare1 = "<a href=\"dbaccess?tab=Command&command=compare%20datafieldid=" + datafieldId + cmd + "\">";
            compare2 = "</a>";
          }
          String dataFieldInfo = datafieldClass;
          if ("1".equals(datafieldClass)) {
            dataFieldInfo = dataFieldInfo + " �L�[";
          } else if ("2".equals(datafieldClass)) {
            dataFieldInfo = dataFieldInfo + " ��{";
          } else if ("3".equals(datafieldClass)) {
            dataFieldInfo = dataFieldInfo + " ����";
          } else if ("4".equals(datafieldClass)) {
            dataFieldInfo = dataFieldInfo + " ���";
          }
          String dataFieldDesc = dataType;
          if (!"DT".equals(dataType) && !"TS".equals(dataType)) {
            if ("NUM".equals(dataType)) {
              if ("0".equals(decimalPlace)) {
                dataFieldDesc = dataFieldDesc + " (" + digit + ")";
              } else {
                dataFieldDesc = dataFieldDesc + " (" + digit + "," + decimalPlace + ")";
              }
            } else {
              dataFieldDesc = dataFieldDesc + " (" + digit + ")";
            }
          }
          out.println("<tr" + trstyle + "><td>" + compare1 + datafieldId + compare2 + "<td>" + dataFieldInfo + "<td>" + dataFieldDesc + "<td>" + datafieldName + "<td>" + timestampValue + "<td>" + timestampValue2);
        }
        out.println("</table>");
        rs.close();
        stmt.close();
        out.println("<input type=\"submit\" name=\"deletetablelayout\" value=\"���C�A�E�g�폜\" onclick=\"if(confirm('�e�[�u�����C�A�E�g�폜�����s���܂��B��낵���ł���?\\n(�f�[�^�t�B�[���h�͍폜����܂���)')){doCommand('MBB','deletetablelayout','1');}return false;\">");
        out.println("&nbsp;&nbsp;<input type=\"checkbox\" name=\"droptable\" value=\"" + selectedTableId + "\">�����e�[�u�����폜");
      }
      if (selectedDataSource != null && !selectedDataSource.equals("1") && selectedTableId != null) {
        out.println("&nbsp;&nbsp;<input type=\"submit\" name=\"copytablelayout\" value=\"���C�A�E�g�ڑ�(" + localSchema + "->" + remoteSchema + ")\" onclick=\"if(confirm('�e�[�u�����C�A�E�g�ڑ������s���܂��B��낵���ł���?\\n(�����e�[�u����CREATE����܂���)')){doCommand('MBB','copytablelayout','1');}return false;\">");
        out.println("&nbsp;&nbsp;<input type=\"checkbox\" name=\"copydatafield\" value=\"1\">�f�[�^�t�B�[���h/�f�[�^�t�B�[���h�l���ڑ�");
      }
      
    } catch (SQLException e) {
      if (conn != null) {
        try {
          conn.rollback();
        } catch (SQLException ex) {}
      }
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
      if (conn2 != null) {
        try {
          conn2.close();
        } catch (SQLException e) {}
      }
    }
    out.println("</table>");
  }
  private void printMBBClass(PrintWriter out, HttpServletRequest request) {
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"CLASS\">");
    out.println("<table>");
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>�N���X�t�@�C��</td></tr>");
    out.println("</table>");
    try {
    } finally {
    }
    out.println("</table>");
  }
  /**
   * �@�\�\������v���邩�`�F�b�N
   * @param tableId
   * @param conn
   * @return �^�C���X�^���v
   */
  private String getTableTimestamp(String tableId, Connection conn) {
    if (conn == null) {
      return null;
    }
    String ts = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT TIMESTAMPVALUE FROM TABLEMASTER WHERE TABLEID=?");
      stmt.setString(1, tableId);
      rs = stmt.executeQuery();
      if (rs.next()) {
        ts = rs.getString(1);
      }
    } catch (SQLException e) {
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    return ts;
  }
  
  /**
   * �p�b�P�[�W�}�X�^���S�Ẵp�b�P�[�WID�A�p�b�P�[�W���̂��擾���Ԃ�
   * @param conn �擾����R�l�N�V����
   * @return �p�b�P�[�WID���L�[�ɂ�������(OFFICIALNAME)��Hashtable
   * @throws SQLException
   */
  private Hashtable getPackages(Connection conn) throws SQLException {
    Hashtable packages = new Hashtable();
    String sql = "SELECT PACKAGEID, (SELECT NAMEVALUE FROM PACKAGENAME WHERE PACKAGEID=a.PACKAGEID AND DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME') FROM PACKAGEMASTER a ORDER BY PACKAGEID";
    PreparedStatement stmt = conn.prepareStatement(sql);
    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
      String packageId = rs.getString(1);
      String packageName = rs.getString(2);
      if (packageName == null) {
        packageName = "";
      }
      packages.put(packageId, packageName);
    }
    return packages;
  }
  
  /**
   * �e�[�u���̑S�f�[�^��cnnFrom����connTo�֕��ʂ���.
   * ���ʐ�e�[�u���̃f�[�^�͑S�č폜�����.
   * @param connFrom ���ʌ��R�l�N�V����
   * @param connTo ���ʐ�R�l�N�V����
   * @param tablename �e�[�u����
   * @return �R�s�[���ꂽ�s��
   * @throws SQLException
   */
  private int copyTableData(Connection connFrom, Connection connTo, String tablename) throws SQLException {
    PreparedStatement selstmt = null;
    PreparedStatement inststmt = null;
    ResultSet rs = null;
    int cnt = 0;
    deleteTableData(connTo, tablename);
    selstmt = connFrom.prepareStatement("SELECT * FROM " + tablename);
    inststmt = null;
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet(tablename, rs));
      }
      insertValues(inststmt, rs);
      ++cnt;
    }
    if (rs != null) {
      rs.close();
    }
    if (selstmt != null) {
      selstmt.close();
    }
    if (inststmt != null) {
      inststmt.close();
    }
    return cnt;
  }
  
  private static void deleteTableData(Connection conn, String tablename) throws SQLException {
    PreparedStatement stmt = null;
    String sql = null;
    int cnt = 0;
    sql = "DELETE FROM " + tablename;
    stmt = conn.prepareStatement(sql);
    cnt = stmt.executeUpdate();
    log_debug("DELETE " + tablename + " : " + cnt);
    stmt.close();
  }

  // TABLEMASTER,TABLELAYOUTMASTER�̕��ʁi�R�s�[���U�폜�j
  private void copyTableLayout(Connection connFrom, Connection connTo, String tableId, boolean datafield) throws SQLException {
    PreparedStatement selstmt = null;
    PreparedStatement inststmt = null;
    ResultSet rs = null;
    deleteTableLayout(connTo, tableId);
    // TABLEMASTER
    selstmt = connFrom.prepareStatement("SELECT * FROM TABLEMASTER WHERE TABLEID=?");
    inststmt = null;
    selstmt.setString(1, tableId);
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("TABLEMASTER", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    // TABLENAME
    selstmt = connFrom.prepareStatement("SELECT * FROM TABLENAME WHERE TABLEID=?");
    inststmt = null;
    selstmt.setString(1, tableId);
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("TABLENAME", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    // TABLEINFO
    selstmt = connFrom.prepareStatement("SELECT * FROM TABLEINFO WHERE TABLEID=?");
    inststmt = null;
    selstmt.setString(1, tableId);
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("TABLEINFO", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    // TABLELAYOUTMASTER
    selstmt = connFrom.prepareStatement("SELECT * FROM TABLELAYOUTMASTER WHERE TABLEID=?");
    inststmt = null;
    selstmt.setString(1, tableId);
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("TABLELAYOUTMASTER", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    
    if (datafield) {
      // �f�[�^�t�B�[���h�֘A�̕���
      PreparedStatement selstmt2 = null;
      selstmt2 = connTo.prepareStatement("SELECT DATAFIELDID FROM TABLELAYOUTMASTER WHERE TABLEID=?");
      selstmt2.setString(1, tableId);
      ResultSet rs2 = selstmt2.executeQuery();
      while (rs2.next()) {
        String datafieldId = rs2.getString(1);
        // ��U�ڑ���̃f�[�^�t�B�[���h���폜
        deleteDataField(connTo, datafieldId);
        // DATAFIELDMASTER
        selstmt = connFrom.prepareStatement("SELECT * FROM DATAFIELDMASTER WHERE DATAFIELDID=?");
        inststmt = null;
        selstmt.setString(1, datafieldId);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("DATAFIELDMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        // DATAFIELDNAME
        selstmt = connFrom.prepareStatement("SELECT * FROM DATAFIELDNAME WHERE DATAFIELDID=?");
        inststmt = null;
        selstmt.setString(1, datafieldId);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("DATAFIELDNAME", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        // DATAFIELDINFO
        if (hasDataFieldInfo) {
          selstmt = connFrom.prepareStatement("SELECT * FROM DATAFIELDINFO WHERE DATAFIELDID=?");
          inststmt = null;
          selstmt.setString(1, datafieldId);
          rs = selstmt.executeQuery();
          while (rs.next()) {
            if (inststmt == null) {
              inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("DATAFIELDINFO", rs));
            }
            insertValues(inststmt, rs);
          }
          rs.close();
          selstmt.close();
          if (inststmt != null) {
            inststmt.close();
          }
        }
        // DATAFIELDVALUEMASTER
        selstmt = connFrom.prepareStatement("SELECT * FROM DATAFIELDVALUEMASTER WHERE DATAFIELDID=?");
        inststmt = null;
        selstmt.setString(1, datafieldId);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("DATAFIELDVALUEMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        // DATAFIELDNAME
        selstmt = connFrom.prepareStatement("SELECT * FROM DATAFIELDVALUENAME WHERE DATAFIELDID=?");
        inststmt = null;
        selstmt.setString(1, datafieldId);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("DATAFIELDVALUENAME", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
      }
      rs2.close();
      selstmt2.close();
    }
    
  }
  
  private static void deleteTableLayout(Connection conn, String tableId) throws SQLException {
    PreparedStatement stmt = null;
    int cnt = 0;
    stmt = conn.prepareStatement("DELETE FROM TABLEMASTER WHERE TABLEID=?");
    stmt.setString(1, tableId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE TABLEMASTER : " + tableId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM TABLENAME WHERE TABLEID=?");
    stmt.setString(1, tableId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE TABLENAME : " + tableId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM TABLEINFO WHERE TABLEID=?");
    stmt.setString(1, tableId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE TABLEINFO : " + tableId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM TABLELAYOUTMASTER WHERE TABLEID=?");
    stmt.setString(1, tableId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE TABLELAYOUTMASTER : " + tableId + " " + cnt);
    stmt.close();
    stmt = null;
  }

  // DATAFIELDMASTER/DATAFIELDVALUEMASTER�̍폜
  private static void deleteDataField(Connection conn, String datafieldId) throws SQLException {
    PreparedStatement stmt = null;
    int cnt = 0;
    stmt = conn.prepareStatement("DELETE FROM DATAFIELDMASTER WHERE DATAFIELDID=?");
    stmt.setString(1, datafieldId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE DATAFIELDMASTER : " + datafieldId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM DATAFIELDNAME WHERE DATAFIELDID=?");
    stmt.setString(1, datafieldId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE DATAFIELDNAME : " + datafieldId + " " + cnt);
    stmt.close();
    stmt = null;
    if (hasDataFieldInfo) {
      stmt = conn.prepareStatement("DELETE FROM DATAFIELDINFO WHERE DATAFIELDID=?");
      stmt.setString(1, datafieldId);
      cnt = stmt.executeUpdate();
      log_debug("DELETE DATAFIELDINFO : " + datafieldId + " " + cnt);
      stmt.close();
      stmt = null;
    }
    stmt = conn.prepareStatement("DELETE FROM DATAFIELDVALUEMASTER WHERE DATAFIELDID=?");
    stmt.setString(1, datafieldId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE DATAFIELDVALUEMASTER : " + datafieldId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM DATAFIELDVALUENAME WHERE DATAFIELDID=?");
    stmt.setString(1, datafieldId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE DATAFIELDVALUENAME : " + datafieldId + " " + cnt);
    stmt.close();
    stmt = null;
  }
  
  private static void copyFunction(Connection connFrom, Connection connTo, String functionId) throws SQLException {
    PreparedStatement selstmt = null;
    PreparedStatement inststmt = null;
    ResultSet rs = null;
    deleteFunction(connTo, functionId, true);
    selstmt = connFrom.prepareStatement("SELECT * FROM FUNCTIONMASTER WHERE FUNCTIONID=?");
    inststmt = null;
    selstmt.setString(1, functionId);
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("FUNCTIONMASTER", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    selstmt = connFrom.prepareStatement("SELECT * FROM FUNCTIONNAME WHERE FUNCTIONID=?");
    inststmt = null;
    selstmt.setString(1, functionId);
    rs = selstmt.executeQuery();
    while (rs.next()) {
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("FUNCTIONNAME", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    selstmt = connFrom.prepareStatement("SELECT * FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=? ORDER BY FUNCTIONCOMPOSITIONCLASS, FUNCTIONCOMPOSITIONID");
    inststmt = null;
    selstmt.setString(1, functionId);
    rs = selstmt.executeQuery();
    Vector functionItems = new Vector();
    while (rs.next()) {
      String functionCompositionId = rs.getString("FUNCTIONCOMPOSITIONID");
      String functionCompositionClass = rs.getString("FUNCTIONCOMPOSITIONCLASS");
      functionItems.add(new String[]{functionCompositionId,functionCompositionClass});
      if (inststmt == null) {
        inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("FUNCTIONCOMPOSITIONMASTER", rs));
      }
      insertValues(inststmt, rs);
    }
    rs.close();
    selstmt.close();
    if (inststmt != null) {
      inststmt.close();
    }
    for (int i = 0; i < functionItems.size(); ++i) {
      String[] item = (String[])functionItems.get(i);
      if (item[1].equals("1")) {
        // �A�v���P�[�V����
        selstmt = connFrom.prepareStatement("SELECT * FROM APPLICATIONMASTER WHERE APPLICATIONID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("APPLICATIONMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM APPLICATIONNAME WHERE APPLICATIONID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("APPLICATIONNAME", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
      } else if (item[1].equals("2")) {
        // �v���Z�X
        selstmt = connFrom.prepareStatement("SELECT * FROM PROCESSMASTER WHERE PROCESSID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        String itemdefinitionId = null;
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PROCESSMASTER", rs));
          }
          insertValues(inststmt, rs);
          itemdefinitionId = rs.getString("ITEMDEFINITIONID");
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM PROCESSNAME WHERE PROCESSID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PROCESSNAME", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM PROCESSDEFINITIONMASTER WHERE PROCESSID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PROCESSDEFINITIONMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM PROCESSITEMRELDEFMASTER WHERE PROCESSID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PROCESSITEMRELDEFMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        // TODO: ���ڒ�`�����L���Ă���ꍇ�́A�ύX�ɂ��s��������������\������
        selstmt = connTo.prepareStatement("SELECT COUNT(*) FROM ITEMDEFINITIONMASTER WHERE ITEMDEFINITIONID=?");
        selstmt.setString(1, itemdefinitionId);
        rs = selstmt.executeQuery();
        int toItemdef = 0;
        if (rs.next()) {
          toItemdef = rs.getInt(1);
        }
        rs.close();
        selstmt.close();
        if (toItemdef > 0) {
          // �R�s�[��ɑ��݂���ꍇ�i���@�\�Ƌ��L����Ă���j�͈�U�폜�i�댯�I�j
          PreparedStatement delstmt = connTo.prepareStatement("DELETE FROM ITEMDEFINITIONMASTER WHERE ITEMDEFINITIONID=?");
          delstmt.setString(1, itemdefinitionId);
          delstmt.executeUpdate();
          delstmt.close();
        }
        
        selstmt = connFrom.prepareStatement("SELECT * FROM ITEMDEFINITIONMASTER WHERE ITEMDEFINITIONID=?");
        inststmt = null;
        selstmt.setString(1, itemdefinitionId);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("ITEMDEFINITIONMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        
      } else if (item[1].equals("3")) {
        // ���
        selstmt = connFrom.prepareStatement("SELECT * FROM PAGEMASTER WHERE PAGEID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PAGEMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM PAGENAME WHERE PAGEID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PAGENAME", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM VIEWPAGEMASTER WHERE PAGEID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("VIEWPAGEMASTER", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        selstmt = connFrom.prepareStatement("SELECT * FROM VIEWPAGEINFO WHERE PAGEID=?");
        inststmt = null;
        selstmt.setString(1, item[0]);
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("VIEWPAGEINFO", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        String pagemessageId = getPageMessageId(item[0]);
        selstmt = connFrom.prepareStatement("SELECT * FROM PAGEMESSAGE WHERE PAGEMESSAGEID LIKE ?");
        inststmt = null;
        selstmt.setString(1, pagemessageId + "%");
        rs = selstmt.executeQuery();
        while (rs.next()) {
          if (inststmt == null) {
            inststmt = connTo.prepareStatement(DbAccessUtils.getInsertSQLFromResultSet("PAGEMESSAGE", rs));
          }
          insertValues(inststmt, rs);
        }
        rs.close();
        selstmt.close();
        if (inststmt != null) {
          inststmt.close();
        }
        
      }

    }
  }
  
  private static void insertValues(PreparedStatement stmt, ResultSet rs) throws SQLException {
    stmt.clearParameters();
    for (int i = 0; i < rs.getMetaData().getColumnCount(); ++i) {
      String colname = rs.getMetaData().getColumnName(i + 1);
      stmt.setString(i + 1, rs.getString(colname));
    }
    stmt.executeUpdate();
  }

  private static void deleteFunction(Connection conn, String functionId, boolean force) throws SQLException {
    PreparedStatement stmt = null;
    ResultSet rs = null;
    String sql = null;
    int cnt = 0;
    sql = "SELECT FUNCTIONCOMPOSITIONID, FUNCTIONCOMPOSITIONCLASS FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=? ORDER BY FUNCTIONCOMPOSITIONCLASS, FUNCTIONCOMPOSITIONID";
    stmt = conn.prepareStatement(sql);
    stmt.setString(1, functionId);
    rs = stmt.executeQuery();
    Vector functionItems = new Vector();
    while (rs.next()) {
      String functionCompositionId = rs.getString(1);
      String functionCompositionClass = rs.getString(2);
      functionItems.add(new String[]{functionCompositionId,functionCompositionClass});
    }
    rs.close();
    rs = null;
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM FUNCTIONMASTER WHERE FUNCTIONID=?");
    stmt.setString(1, functionId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE FUNCTIONMASTER : " + functionId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM FUNCTIONNAME WHERE FUNCTIONID=?");
    stmt.setString(1, functionId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE FUNCTIONNAME : " + functionId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=?");
    stmt.setString(1, functionId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE FUNCTIONCOMPOSITIONMASTER : " + functionId + " " + cnt);
    stmt.close();
    stmt = null;
    for (int i = 0; i < functionItems.size(); ++i) {
      String[] item = (String[])functionItems.get(i);
      if (item[1].equals("1")) {
        // �A�v���P�[�V�����}�X�^�폜
        stmt = conn.prepareStatement("SELECT COUNT(FUNCTIONCOMPOSITIONID) FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONCOMPOSITIONID=? AND FUNCTIONCOMPOSITIONCLASS='1'");
        stmt.setString(1, item[0]);
        rs = stmt.executeQuery();
        if (rs.next()) {
          cnt = rs.getInt(1);
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
        if (cnt == 0 || force) {
          // ���̋@�\���Q�Ƃ��Ȃ���΍폜
          deleteApplication(conn, item[0]);
        }
      } else if (item[1].equals("2")) {
        // �v���Z�X��`�폜
        stmt = conn.prepareStatement("SELECT COUNT(FUNCTIONCOMPOSITIONID) FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONCOMPOSITIONID=? AND FUNCTIONCOMPOSITIONCLASS='2'");
        stmt.setString(1, item[0]);
        rs = stmt.executeQuery();
        if (rs.next()) {
          cnt = rs.getInt(1);
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
        if (cnt == 0 || force) {
          // ���̋@�\���Q�Ƃ��Ȃ���΍폜
          deleteProcess(conn, item[0]);
        }
      } else if (item[1].equals("3")) {
        // ��ʒ�`�폜
        stmt = conn.prepareStatement("SELECT COUNT(FUNCTIONCOMPOSITIONID) FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONCOMPOSITIONID=? AND FUNCTIONCOMPOSITIONCLASS='3'");
        stmt.setString(1, item[0]);
        rs = stmt.executeQuery();
        if (rs.next()) {
          cnt = rs.getInt(1);
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
        if (cnt == 0 || force) {
          // ���̋@�\���Q�Ƃ��Ȃ���΍폜
          deletePage(conn, item[0]);
        }
      }
    }
  }

  private static void deleteApplication(Connection conn, String applicationId) throws SQLException {
    PreparedStatement stmt = null;
    try {
      int cnt = 0;
      stmt = conn.prepareStatement("DELETE FROM APPLICATIONMASTER WHERE APPLICATIONID=?");
      stmt.setString(1, applicationId);
      cnt = stmt.executeUpdate();
      log_debug("DELETE APPLICATIONMASTER : " + applicationId + " " + cnt);
      stmt.close();
      stmt = null;
      stmt = conn.prepareStatement("DELETE FROM APPLICATIONNAME WHERE APPLICATIONID=?");
      stmt.setString(1, applicationId);
      cnt = stmt.executeUpdate();
      log_debug("DELETE APPLICATIONNAME : " + applicationId + " " + cnt);
    } finally {
      if (stmt != null) {
        stmt.close();
      }
    }
  }

  private static void deleteProcess(Connection conn, String processId) throws SQLException {
    PreparedStatement stmt = null;
    ResultSet rs = null;
    String itemdefinitionId = null;
    int cnt = 0;
    stmt = conn.prepareStatement("SELECT ITEMDEFINITIONID FROM PROCESSMASTER WHERE PROCESSID=?");
    stmt.setString(1, processId);
    rs = stmt.executeQuery();
    if (rs.next()) {
      itemdefinitionId = rs.getString(1);
    }
    rs.close();
    stmt.close();
    stmt = conn.prepareStatement("DELETE FROM PROCESSMASTER WHERE PROCESSID=?");
    stmt.setString(1, processId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE PROCESSMASTER : " + processId + " " + cnt);
    stmt.close();
    stmt = conn.prepareStatement("DELETE FROM PROCESSNAME WHERE PROCESSID=?");
    stmt.setString(1, processId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE PROCESSNAME : " + processId + " " + cnt);
    stmt.close();
    stmt = conn.prepareStatement("DELETE FROM PROCESSDEFINITIONMASTER WHERE PROCESSID=?");
    stmt.setString(1, processId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE PROCESSDEFINITIONMASTER : " + processId + " " + cnt);
    stmt.close();
    stmt = conn.prepareStatement("DELETE FROM PROCESSITEMRELDEFMASTER WHERE PROCESSID=?");
    stmt.setString(1, processId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE PROCESSITEMRELDEFMASTER : " + processId + " " + cnt);
    stmt.close();
    stmt = conn.prepareStatement("SELECT COUNT(ITEMDEFINITIONID) FROM PROCESSMASTER WHERE ITEMDEFINITIONID=?");
    stmt.setString(1, itemdefinitionId);
    rs = stmt.executeQuery();
    int id = 0;
    if (rs.next()) {
      id = rs.getInt(1);
    }
    rs.close();
    stmt.close();
    if (id == 0) {
      // ���ڒ�`ID�Q�Ƃ��S�ĂȂ��Ȃ����ꍇ�̂ݍ폜
      stmt = conn.prepareStatement("DELETE FROM ITEMDEFINITIONMASTER WHERE ITEMDEFINITIONID=?");
      stmt.setString(1, itemdefinitionId);
      cnt = stmt.executeUpdate();
      log_debug("DELETE ITEMDEFINITIONMASTER : " + itemdefinitionId + " " + cnt);
      stmt.close();
    }
  }

  private static void deletePage(Connection conn, String pageId) throws SQLException {
    PreparedStatement stmt = null;
    int cnt = 0;
    stmt = conn.prepareStatement("DELETE FROM PAGEMASTER WHERE PAGEID=?");
    stmt.setString(1, pageId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE PAGEMASTER : " + pageId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM PAGENAME WHERE PAGEID=?");
    stmt.setString(1, pageId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE PAGEAME : " + pageId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM VIEWPAGEMASTER WHERE PAGEID=?");
    stmt.setString(1, pageId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE VIEWPAGEMASTER : " + pageId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM VIEWPAGEINFO WHERE PAGEID=?");
    stmt.setString(1, pageId);
    cnt = stmt.executeUpdate();
    log_debug("DELETE VIEWPAGEINFO : " + pageId + " " + cnt);
    stmt.close();
    stmt = null;
    stmt = conn.prepareStatement("DELETE FROM PAGEMESSAGE WHERE PAGEMESSAGEID LIKE ?");
    String pageMessageId = getPageMessageId(pageId);
    stmt.setString(1, pageMessageId + "%");
    cnt = stmt.executeUpdate();
    log_debug("DELETE PAGEMESSAGE : " + pageMessageId + " " + cnt);
    stmt.close();
  }

  private static void updateFunction(Connection conn, HttpServletRequest request) throws SQLException {
    String functionId = request.getParameter("functionid");
    String updateCompanyId = request.getParameter("updatecompanyid");
    String updateUserId = request.getParameter("updateuserid");
    String updateProcessId = request.getParameter("updateprocessid");
    String timestampvalue = request.getParameter("timestampvalue");
    PreparedStatement stmt = null;
    ResultSet rs = null;
    String sql = null;
    int cnt = 0;
    sql = "SELECT FUNCTIONCOMPOSITIONID, FUNCTIONCOMPOSITIONCLASS FROM FUNCTIONCOMPOSITIONMASTER WHERE FUNCTIONID=? ORDER BY FUNCTIONCOMPOSITIONCLASS, FUNCTIONCOMPOSITIONID";
    stmt = conn.prepareStatement(sql);
    stmt.setString(1, functionId);
    rs = stmt.executeQuery();
    Vector functionItems = new Vector();
    while (rs.next()) {
      String functionCompositionId = rs.getString(1);
      String functionCompositionClass = rs.getString(2);
      functionItems.add(new String[]{functionCompositionId,functionCompositionClass});
    }
    rs.close();
    stmt.close();
    stmt = conn.prepareStatement("UPDATE FUNCTIONMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE FUNCTIONID=?");
    stmt.setString(1, updateCompanyId);
    stmt.setString(2, updateUserId);
    stmt.setString(3, updateProcessId);
    stmt.setString(4, timestampvalue);
    stmt.setString(5, functionId);
    cnt = stmt.executeUpdate();
    log_debug("UPDATE FUNCTIONMASTER : " + functionId + " " + cnt);
    stmt.close();
    stmt = conn.prepareStatement("UPDATE FUNCTIONCOMPOSITIONMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE FUNCTIONID=?");
    stmt.setString(1, updateCompanyId);
    stmt.setString(2, updateUserId);
    stmt.setString(3, updateProcessId);
    stmt.setString(4, timestampvalue);
    stmt.setString(5, functionId);
    cnt = stmt.executeUpdate();
    log_debug("UPDATE FUNCTIONCOMPOSITIONMASTER : " + functionId + " " + cnt);
    stmt.close();
    for (int i = 0; i < functionItems.size(); ++i) {
      String[] item = (String[])functionItems.get(i);
      if (item[1].equals("1")) {
        // �A�v���P�[�V�����}�X�^�X�V
        stmt = conn.prepareStatement("UPDATE APPLICATIONMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE APPLICATIONID=?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        stmt.setString(5, item[0]);
        cnt = stmt.executeUpdate();
        log_debug("UPDATE APPLICATIONMASTER : " + item[0] + " " + cnt);
      } else if (item[1].equals("2")) {
        // �v���Z�XID��荀�ڒ�`ID�̎擾
        String itemdefinitionId = null;
        stmt = conn.prepareStatement("SELECT ITEMDEFINITIONID FROM PROCESSMASTER WHERE PROCESSID=?");
        stmt.setString(1, item[0]);
        rs = stmt.executeQuery();
        if (rs.next()) {
          itemdefinitionId = rs.getString(1);
        }
        rs.close();
        stmt.close();
        // �v���Z�X��`�X�V
        stmt = conn.prepareStatement("UPDATE PROCESSMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE PROCESSID=?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        stmt.setString(5, item[0]);
        cnt = stmt.executeUpdate();
        log_debug("UPDATE PROCESSMASTER : " + item[0] + " " + cnt);
        stmt = conn.prepareStatement("UPDATE PROCESSDEFINITIONMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE PROCESSID=?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        stmt.setString(5, item[0]);
        cnt = stmt.executeUpdate();
        log_debug("UPDATE PROCESSDEFINITIONMASTER : " + item[0] + " " + cnt);
        stmt = conn.prepareStatement("UPDATE PROCESSITEMRELDEFMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE PROCESSID=?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        stmt.setString(5, item[0]);
        cnt = stmt.executeUpdate();
        log_debug("UPDATE PROCESSITEMRELDEFMASTER : " + item[0] + " " + cnt);
        if (itemdefinitionId != null) {
          stmt = conn.prepareStatement("UPDATE ITEMDEFINITIONMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE ITEMDEFINITIONID=?");
          stmt.setString(1, updateCompanyId);
          stmt.setString(2, updateUserId);
          stmt.setString(3, updateProcessId);
          stmt.setString(4, timestampvalue);
          stmt.setString(5, itemdefinitionId);
          cnt = stmt.executeUpdate();
          log_debug("UPDATE ITEMDEFINITIONMASTER : " + item[0] + " " + cnt);
        }
      } else if (item[1].equals("3")) {
        // ��ʒ�`�X�V
        stmt = conn.prepareStatement("UPDATE PAGEMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE PAGEID=?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        stmt.setString(5, item[0]);
        cnt = stmt.executeUpdate();
        log_debug("UPDATE PAGEMASTER : " + item[0] + " " + cnt);
        stmt = conn.prepareStatement("UPDATE VIEWPAGEMASTER SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE PAGEID=?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        stmt.setString(5, item[0]);
        cnt = stmt.executeUpdate();
        log_debug("UPDATE VIEWPAGEMASTER : " + item[0] + " " + cnt);
        stmt = conn.prepareStatement("UPDATE PAGEMESSAGE SET UPDATECOMPANYID=?,UPDATEUSERID=?,UPDATEPROCESSID=?,TIMESTAMPVALUE=? WHERE PAGEMESSAGEID LIKE ?");
        stmt.setString(1, updateCompanyId);
        stmt.setString(2, updateUserId);
        stmt.setString(3, updateProcessId);
        stmt.setString(4, timestampvalue);
        String pageMessageId = getPageMessageId(item[0]);
        stmt.setString(5, pageMessageId + "%");
        cnt = stmt.executeUpdate();
        log_debug("UPDATE PAGEMESSAGE : " + pageMessageId + " " + cnt);
      }
    }
  }
  
  private static String getPageMessageId(String pageId) {
    if (pageId.endsWith("_JA")) {
      return pageId.substring(0, pageId.length() - 3) + "@";
    }
    return pageId + "@";
  }

  
  private static String getHiddenValueString(String s) {
    if (s == null) {
      return "$NULL$";
    }
    return DbAccessUtils.escapeInputValue(s);
  }
  
  private String getVersionString(String envmsg) {
    StringBuffer sb = new StringBuffer();
    if (title != null) {
      sb.append(title).append(" ");
    }
    sb.append("DBACCESS");
    if (envmsg != null) {
      sb.append(" (").append(envmsg).append(")");
    }
    sb.append(" ver.").append(version);
    return sb.toString();
  }
  
  private void printHtmlHeader(PrintWriter out) throws IOException {
    out.println("<html>");
    out.println("<head>");
    out.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
    String envmsg = null;
    if (schemas[0] != null) {
      envmsg = schemas[0] + "@" + dataSourceNames[0];
    } else {
      envmsg = dataSourceNames[0];
    }
    out.println("<title>" + getVersionString(envmsg) + "</title>");
    out.println("<!-- MBB DbAccess for " + dbmsTypes[0] + ". -->");
    out.println("</head>");
  }

  private void printHeader(HttpServletRequest request, PrintWriter out, String tab) throws ServletException {
    boolean upload = false;
    if (request.getParameter("upload") != null) {
      upload = true;
    }
    String mbbmenu = request.getParameter("mbbmenu");
    if (mbbmenu == null) {
      String command = request.getParameter("command");
      if (command != null) {
        if (command.equals("config")) {
          mbbmenu = "CONFIG";
        } else if (command.equals("scan")) {
          mbbmenu = "SCAN";
        }
      }
    }
    String update = request.getParameter("update");
    try {
      printHtmlHeader(out);
    } catch (IOException e) {
      throw new ServletException(e);
    }
    out.println("<style type=\"text/css\">");
    out.println("<!--");
    out.println("table {");
    out.println(" font-size: 10pt;");
    out.println("}");
    out.println("th {");
    out.println(" white-space: nowrap;");
    out.println("}");
    out.println("td {");
    out.println(" white-space: nowrap;");
    out.println("}");
    out.println(".selected {");
    out.println(" border-style: solid solid none solid;");
    out.println(" border-width: 1px;");
    out.println(" border-color: #000000;");
    out.println(" padding: 1px 6px 1px 6px;");
    out.println("}");
    out.println(".unselected {");
    out.println(" border-style: solid none solid solid;");
    out.println(" border-width: 1px;");
    out.println(" border-color: #cccccc #cccccc #000000 #cccccc;");
    out.println(" padding: 1px 4px 1px 4px;");
    out.println("}");
    out.println(".spacer {");
    out.println(" border-style: none none solid solid;");
    out.println(" border-width: 1px;");
    out.println(" border-color: #000000 #000000 #000000 #cccccc;");
    out.println(" width: 100%;");
    out.println("}");
    out.println(".text {");
    out.println(" font-size: 10pt;");
    out.println("}");
    out.println(".pre-wrap {");
    out.println(" white-space: pre;");
    out.println(" white-space: pre-wrap;");
    out.println(" white-space: pre-line;");
    out.println(" white-space: -pre-wrap;");
    out.println(" white-space: -o-pre-wrap;");
    out.println(" white-space: -moz-pre-wrap;");
    out.println(" white-space: -hp-pre-wrap;");
    out.println(" word-wrap: break-word;");
    out.println("}");
    out.println("-->");
    out.println("</style>");
    out.println("<script language=\"javascript\">");
    out.println("function doTab(tab) {");
    out.println("  document.forms[0].elements['tab'].value = tab;");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    out.println("function doCommand(tab,key,value) {");
    out.println("  document.forms[0].elements['tab'].value = tab;");
    out.println("  var input = document.forms[0].elements[key];");
    out.println("  if(input&&input.tagName=='INPUT'&&input.type=='hidden') {");
    out.println("    input.value=value;");
    out.println("  } else {");
    out.println("    input = document.createElement('input');");
    out.println("    if (document.all) {");
    out.println("      input.type='hidden';");
    out.println("      input.name=key;");
    out.println("      input.value=value;");
    out.println("    } else {");
    out.println("      input.setAttribute('type','hidden');");
    out.println("      input.setAttribute('name',key);");
    out.println("      input.setAttribute('value',value);");
    out.println("    }");
    out.println("    document.forms[0].appendChild(input);");
    out.println("  }");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    if (ExcelManager.isEnabled()) {
      if ("Tables".equals(tab)) {
        out.println("function doExportToExcel() {");
        out.println("  if(document.forms[0].elements['table_name'].value==''){");
        out.println("    alert('�Ώۃe�[�u����I�����Ă�������');");
        out.println("    return false;");
        out.println("  }");
        out.println("  document.forms[1].elements['table'].value = document.forms[0].elements['table_name'].value;");
        out.println("  document.forms[1].elements['withinfo'].value = document.forms[0].elements['withinfo'].checked ? '1' : '0';");
        out.println("  document.forms[1].submit();");
        out.println("  return true;");
        out.println("}");
      }
      out.println("function doExcelReport(form,report) {");
      out.println("  var command = form.elements['command'];");
      out.println("  var input = form.elements['report'];");
      out.println("  if (!input) {");
      out.println("    input = document.createElement('input');");
      out.println("    if (document.all) {");
      out.println("      input.type='hidden';");
      out.println("      input.name='report';");
      out.println("      input.value=report;");
      out.println("    } else {");
      out.println("      input.setAttribute('type','hidden');");
      out.println("      input.setAttribute('name','report');");
      out.println("      input.setAttribute('value',report);");
      out.println("    }");
      out.println("    form.appendChild(input);");
      out.println("  } else {");
      out.println("    input.value=report;");
      out.println("  }");
      out.println("  if (!command) {");
      out.println("    command = document.createElement('input');");
      out.println("    if (document.all) {");
      out.println("      command.type='hidden';");
      out.println("      command.name='command';");
      out.println("      command.value='scan compare';");
      out.println("    } else {");
      out.println("      command.setAttribute('type','hidden');");
      out.println("      command.setAttribute('name','command');");
      out.println("      command.setAttribute('value','scan compare');");
      out.println("    }");
      out.println("    form.appendChild(command);");
      out.println("  } else {");
      out.println("    command.value='scan compare';");
      out.println("  }");
      out.println("  form.submit();");
      out.println("  return true;");
      out.println("}");
    }
    out.println("function doTableChange() {");
    out.println("  document.forms[0].elements['edit_filter'].value = '';");
    out.println("  document.forms[0].elements['lines'].value = '100';");
    out.println("}");
    out.println("function doTableSelect(elm) {");
    out.println("  document.getElementById('selected_table').innerText = elm.value;");
//    out.println("  if(elm.value.match(/MASTER$/)){");
//    out.println("    document.forms[0].elements['withinfo'].checked=true;");
//    out.println("  }else{");
//    out.println("    document.forms[0].elements['withinfo'].checked=false;");
//    out.println("  }");
    out.println("}");
    out.println("function doKeyDown(elm) {");
    out.println("  if(event&&event.keyCode==46){"); // delete�L�[
    out.println("    var t = elm.value;");
    out.println("    var o = 'TABLE';");
    out.println("    if (document.getElementById('tabletype').value == '1') {");
    out.println("      o = 'VIEW';");
    out.println("    } if (document.getElementById('tabletype').value == '3') {");
    out.println("      o = 'SYNONYM';");
    out.println("    }");
    out.println("    if(t&&(t.charAt(0)=='$'||t!=t.toUpperCase()))t='\"'+t+'\"';");
    out.println("    if(confirm('DROP '+o+' '+t)){");
    out.println("      doCommand('Result','command','DROP '+o+' '+t);");
    out.println("      ");
    out.println("    }");
    out.println("  }");
    out.println("}");
    out.println("function doColumnSelect(elm) {");
    out.println("  document.getElementById('selected_table').innerText = elm.value;");
    out.println("}");
    out.println("function doEdit(sql, order, direction) {");
    out.println("  document.forms[0].elements['tab'].value = 'Edit';");
    out.println("  document.forms[0].elements['edit_command'].value = sql;");
    out.println("  document.forms[0].elements['order'].value = order;");
    out.println("  document.forms[0].elements['direction'].value = direction;");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    // INSERT SQL
    out.println("function doInsertSQL(line, cols) {");
    out.println("  document.forms[0].elements['tab'].value = 'Edit';");
    out.println("  sql = 'INSERT INTO ' + document.forms[0].elements['edit_table'].value + ' VALUES (';");
    out.println("  for(col = 0; col < cols; col++) {");
    out.println("    elementname = 'field_' + line + '_' + col;");
    out.println("    element = document.forms[0].elements[elementname];");
    out.println("    typeelement = document.forms[0].elements[element.name + '_type'];");
    out.println("    if (col > 0) sql = sql + ',';");
    out.println("    sql = sql + SQLSetValue(element.value,typeelement.value);");
    out.println("  }");
    out.println("  sql = sql + ')';");
    out.println("  document.forms[0].elements['edit_command'].value = sql;");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    // DELETE SQL
    out.println("function doDeleteSQL(line, cols) {");
    out.println("  document.forms[0].elements['tab'].value = 'Edit';");
    out.println("  sql = 'DELETE FROM ' + document.forms[0].elements['edit_table'].value + ' WHERE ';");
    out.println("  for(col = 0; col < cols; col++) {");
    out.println("    elementname = 'ofield_' + line + '_' + col;");
    out.println("    element = document.forms[0].elements[elementname];");
    out.println("    typeelement = document.forms[0].elements[element.name + '_type'];");
    out.println("    if (typeelement.value != '5'){");
    out.println("      if (col > 0) sql = sql + ' AND ';");
    out.println("      sql = sql + SQLWhereValue(element.value,element.name,typeelement.value);");
    out.println("    }");
    out.println("  }");
    out.println("  document.forms[0].elements['edit_command'].value = sql;");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    // UPDATE SQL
    out.println("function doUpdateSQL(line, cols) {");
    out.println("  document.forms[0].elements['tab'].value = 'Edit';");
    out.println("  sql = 'UPDATE ' + document.forms[0].elements['edit_table'].value + ' SET ';");
    out.println("  for(col = 0; col < cols; col++) {");
    out.println("    elementname = 'field_' + line + '_' + col;");
    out.println("    element = document.forms[0].elements[elementname];");
    out.println("    typeelement = document.forms[0].elements[element.name + '_type'];");
    out.println("    if (col > 0) sql = sql + ' , ';");
    out.println("    sql = sql + element.name + '=' + SQLSetValue(element.value,typeelement.value);");
    out.println("  }");
    out.println("  sql = sql + ' where ';");
    out.println("  for(col = 0; col < cols; col++) {");
    out.println("    elementname = 'ofield_' + line + '_' + col;");
    out.println("    element = document.forms[0].elements[elementname];");
    out.println("    typeelement = document.forms[0].elements[element.name + '_type'];");
    out.println("    if (col > 0) sql = sql + ' and ';");
    out.println("    sql = sql + SQLWhereValue(element.value,element.name,typeelement.value);");
    out.println("  }");
    //out.println("alert(sql);");
    out.println("  document.forms[0].elements['edit_command'].value = sql;");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    // SELECT SQL
    out.println("function doSelectSQL(line, cols) {");
    out.println("  document.forms[0].elements['tab'].value = 'Edit';");
    out.println("  sql = 'SELECT * FROM ' + document.forms[0].elements['edit_table'].value + ' WHERE ';");
    out.println("  w = 0;");
    out.println("  for(col = 0; col < cols; col++) {");
    out.println("    elementname = 'field_' + line + '_' + col;");
    out.println("    element = document.forms[0].elements[elementname];");
    out.println("    typeelement = document.forms[0].elements[element.name + '_type'];");
    out.println("    if (element.value.length > 0) {");
    out.println("      if (w > 0) sql = sql + ' AND ';");
    out.println("      sql = sql + SQLWhereSelectValue(element.value,element.name,typeelement.value);");
    out.println("      w++;");
    out.println("    }");
    out.println("  }");
    out.println("  document.forms[0].elements['edit_command'].value = '';");
    out.println("  document.forms[0].elements['lines'].value = '0';");
    out.println("  if (w > 0) {");
    out.println("    document.forms[0].elements['edit_filter'].value = sql;");
    out.println("  } else {");
    out.println("    document.forms[0].elements['edit_filter'].value = '';");
    out.println("    document.forms[0].elements['lines'].value = '100';");
    out.println("  }");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    // EXPORT SQL
    out.println("function doExportSQL(line, cols) {");
    out.println("  document.forms[0].elements['tab'].value = 'Edit';");
    out.println("  sql = 'SELECT/E * FROM ' + document.forms[0].elements['edit_table'].value + ' WHERE ';");
    out.println("  w = 0;");
    out.println("  for(col = 0; col < cols; col++) {");
    out.println("    elementname = 'field_' + line + '_' + col;");
    out.println("    element = document.forms[0].elements[elementname];");
    out.println("    typeelement = document.forms[0].elements[element.name + '_type'];");
    out.println("    if (element.value.length > 0) {");
    out.println("      if (w > 0) sql = sql + ' AND ';");
    out.println("      sql = sql + SQLWhereSelectValue(element.value,element.name,typeelement.value);");
    out.println("      w++;");
    out.println("    }");
    out.println("  }");
    out.println("  document.forms[0].elements['lines'].value = '0';");
    out.println("  if (w > 0) {");
    out.println("    document.forms[0].elements['edit_filter'].value = sql;");
    out.println("  } else {");
    out.println("    if (document.forms[0].elements['edit_filter'].value != '') {");
    out.println("      document.forms[0].elements['edit_filter'].value = 'E:' + document.forms[0].elements['edit_filter'].value;");
    out.println("    } else {");
    out.println("      document.forms[0].elements['edit_filter'].value = 'E:';");
    out.println("      document.forms[0].elements['lines'].value = '100';");
    out.println("    }");
    out.println("  }");
    out.println("  document.forms[0].submit();");
    out.println("  return true;");
    out.println("}");
    if (isOracle(0)) {
      /***** begin ORACLE Version ******/
      out.println("function SQLSetValue(s,type) {");
      out.println("  if (type == 4) {");
      out.println("    if (s.indexOf('.') >= 0) {");
      out.println("      s = s.substring(0, s.indexOf('.'));");
      out.println("      s = \"TO_DATE('\" + s + \"','YYYY-MM-DD HH24:MI:SS')\";");
      out.println("      return s;");
      out.println("    } else {");
      out.println("      return \"NULL\";");
      out.println("    }");
      out.println("  }");
      out.println("  if (s == '' && type != 2) {");
      out.println("   return \"' '\";");
      out.println("  }");
      out.println("  return \"'\" + s.replace(/'/g,\"''\") + \"'\";");
      out.println("}");
      out.println("function SQLWhereValue(s,name,type) {");
      out.println("  if (s == '$NULL$') {");
      out.println("    return name + \" IS NULL\";");
      out.println("  }");
      out.println("  if (s == '') {");
      out.println("    return name + \" IS NULL\";");
      out.println("  }");
      out.println("  if (type == 4) {");
      out.println("    if (s.indexOf('.') >= 0 || s.length == 19) {");
      out.println("      s = s.substring(0, s.indexOf('.'));");
      out.println("      s = \"TO_DATE('\" + s + \"','YYYY-MM-DD HH24:MI:SS')\";");
      out.println("      return name + \"=\" + s;");
      out.println("    } else {");
      out.println("      return name + \" IS NULL\";");
      out.println("    }");
      out.println("  }");
      out.println("  return name + \"='\" + s.replace(/'/g,\"''\") + \"'\";");
      out.println("}");
      /***** end ORACLE Version *****/
    } else {
      /***** begin DB2 Version *****/
      out.println("function SQLSetValue(s,type) {");
      out.println("  if (type == 4) {");
      out.println("    if (s == '') {");
      out.println("      return \"NULL\";");
      out.println("    }");
      out.println("  }");
      out.println("  if (s.length == 0) {");
      out.println("    if (type == 0 || type == 1) {");
      out.println("      return \"''\";");
      out.println("    } else {");
      out.println("      return 'NULL';");
      out.println("    }");
      out.println("  }");
      out.println("  if (type == 2) {");
      out.println("    return s;");
      out.println("  } else {");
      out.println("    return \"'\" + s.replace(/'/g,\"''\") + \"'\";");
      out.println("  }");
      out.println("}");
      out.println("function SQLWhereValue(s,name,type) {");
      out.println("  if (s == '$NULL$') {");
      out.println("    return name + \" IS NULL\";");
      out.println("  }");
      out.println("  if (SQLSetValue(s,type) == 'NULL') {");
      out.println("    return name + \" IS NULL\";");
      out.println("  }");
      out.println("  return name + \"='\" + s.replace(/'/g,\"''\") + \"'\";");
      out.println("}");
      /***** end DB2 Version *****/
    }
    out.println("function SQLWhereSelectValue(s,name,type) {");
    out.println("  ns = SQLSetValue(s,type);");
    out.println("  if (ns.indexOf('%') >= 0 || ns.indexOf('_') >= 0) {");
    out.println("    return name + \" LIKE \" + ns + \"\";");
    out.println("  }");
    out.println("  return SQLWhereValue(s,name,type);");
    out.println("}");
    
    out.println("function checkAll(name, value) {");
    out.println("  items = document.getElementsByName(name);");
    out.println("  for(i = 0;i < items.length; ++i) {");
    out.println("    if (items[i].tagName == 'INPUT' && items[i].type == 'checkbox') {");
    out.println("      items[i].checked = value;");
    out.println("      if(items[i].onclick){");
    out.println("        items[i].onclick();");
    out.println("      }");
    out.println("    }");
    out.println("  }");
    out.println("}");
    out.println("function checkAllClass(name, cls) {");
    out.println("  items = document.getElementsByName(name);");
    out.println("  for(i = 0;i < items.length; ++i) {");
    out.println("    if (items[i].tagName == 'INPUT' && items[i].type == 'checkbox' && items[i].className == cls) {");
    out.println("      items[i].checked = true;");
    out.println("      if(items[i].onclick){");
    out.println("        items[i].onclick();");
    out.println("      }");
    out.println("    }");
    out.println("  }");
    out.println("}");

    if ("MBB".equals(tab)) {
      out.println("function doDownload(table, id, filenameid, filenamets0, filenamets1, filenamets2) {");
      out.println("  document.forms[1].table.value=table;");
      out.println("  document.forms[1].id.value=id;");
      out.println("  if(filenameid){");
      out.println("    document.forms[1].filenameid.value='1';");
      out.println("  }else{");
      out.println("    document.forms[1].filenameid.value='';");
      out.println("  }");
      out.println("  if(filenamets0){");
      out.println("    document.forms[1].filenamets.value='0';");
      out.println("  }else if(filenamets1){");
      out.println("    document.forms[1].filenamets.value='1';");
      out.println("  }else if(filenamets2){");
      out.println("    document.forms[1].filenamets.value='2';");
      out.println("  }else{");
      out.println("    document.forms[1].filenamets.value='';");
      out.println("  }");
      out.println("  document.forms[1].submit();");
      out.println("}");
      out.println("var _firstChecked = false;");
      out.println("function _doSelectLine(elm) {");
      out.println("  if (document.all) evt = event;");
      out.println("  if (evt.button == 1) {");
      out.println("    elm.getElementsByTagName('INPUT')[0].checked=_firstChecked;");
      out.println("  }");
      out.println("}");
      out.println("function _doCheckListTable() {");
      out.println("  var table = document.getElementById('checklisttable');");
      out.println("  if(table) {");
      out.println("    var trs = table.getElementsByTagName('TR');");
      out.println("    for(var i = 1; trs && i < trs.length; ++i){");
      out.println("      var tds = trs[i].childNodes;");
      out.println("      if(tds[0].innerHTML=='')break;");
      out.println("      for(var j = 1; tds && j < tds.length; ++j){");
      out.println("        tds[j].onmousedown=function(){_firstChecked=!this.parentNode.getElementsByTagName('INPUT')[0].checked;};");
      out.println("        tds[j].onmousemove=function(){_doSelectLine(this.parentNode)};");
      out.println("      }");
      out.println("    }");
      out.println("  }");
      out.println("}");
      out.println("function _putCookie(key, value) {");
      out.println("  document.cookie=key+'='+value;");
      out.println("}");
      out.println("function _getCookie(key) {");
      out.println("  cookies=document.cookie.split(';');");
      out.println("  for(i = 0; i < cookies.length;i++){");
      out.println("    if(cookies[i].substring(0,1)==' '){");
      out.println("      cookies[i] = cookies[i].substring(1);");
      out.println("    }");
      out.println("    if(cookies[i].substring(0, key.length+1)==key+'='){");
      out.println("      return cookies[i].substring(key.length+1);");
      out.println("    }");
      out.println("  }");
      out.println("  return null;");
      out.println("}");
      if ("CONFIG".equals(mbbmenu)) {
        out.println("function selectListItem(list,ovr) {");
        out.println("  var ip=document.getElementById('_ignoreItem');");
        out.println("  if(list.selectedIndex!=-1){");
        out.println("    if(ip.value==''||ovr){");
        out.println("      ip.value=list.options[list.selectedIndex].value;");
        out.println("    }");
        out.println("    document.getElementById('_idel').disabled=false;");
        out.println("    list.title=list.options[list.selectedIndex].value;");
        out.println("  }");
        out.println("}");
        out.println("function appendListItem(list) {");
        out.println("  var ip=document.getElementById('_ignoreItem');");
        out.println("  var idx=list.length;");
        out.println("  if(list.selectedIndex!=-1){");
        out.println("    for(var i=list.length-1;i>list.selectedIndex;--i){");
        out.println("      list.options[i+1]=new Option(list.options[i].text,list.options[i].value);");
        out.println("    }");
        out.println("    idx=list.selectedIndex+1;");
        out.println("  }");
        out.println("  list.options[idx]=new Option(ip.value,ip.value);");
        out.println("  list.selectedIndex=idx;");
        out.println("}");
        out.println("function removeListItem(list) {");
        out.println("  var ip=document.getElementById('_ignoreItem');");
        out.println("  if(list.selectedIndex!=-1){");
        out.println("    var idx=list.selectedIndex;");
        out.println("    ip.value=list.options[idx].value;");
        out.println("    list.options[idx]=null;");
        out.println("    if(idx<list.length){");
        out.println("      list.selectedIndex=idx;");
        out.println("    }else if(list.length>0){");
        out.println("      list.selectedIndex=idx-1;");
        out.println("    }");
        out.println("  }");
        out.println("  if(list.selectedIndex!=-1){");
        out.println("    ip.value=list.options[list.selectedIndex].value;");
        out.println("  }else{");
        out.println("    document.getElementById('_idel').disabled=true;");
        out.println("  }");
        out.println("}");
        out.println("function selectAllListItem(list) {");
        out.println("  list.style.visibility='hidden';");
        out.println("  list.multiple=true;");
        out.println("  for(var i=0;i<list.length;++i){");
        out.println("    list.options[i].selected=true;");
        out.println("  }");
        out.println("}");
        out.println("function selectMenu(list) {");
        out.println("  if(list.id=='_mbbMenu'){");
        out.println("    if(hasMenuItem(document.getElementById('_adminMenu'),list.options[list.selectedIndex].value)){");
        out.println("      document.getElementById('_aadd').disabled=true;");
        out.println("    }else{");
        out.println("      document.getElementById('_aadd').disabled=false;");
        out.println("    }");
        out.println("    if(hasMenuItem(document.getElementById('_userMenu'),list.options[list.selectedIndex].value)){");
        out.println("      document.getElementById('_uadd').disabled=true;");
        out.println("    }else{");
        out.println("      document.getElementById('_uadd').disabled=false;");
        out.println("    }");
        out.println("  }else if(list.id=='_adminMenu'){");
        out.println("    if(list.selectedIndex==-1){");
        out.println("      document.getElementById('_adel').disabled=true;");
        out.println("      document.getElementById('_aup').disabled=true;");
        out.println("      document.getElementById('_adown').disabled=true;");
        out.println("    }else{");
        out.println("      document.getElementById('_adel').disabled=false;");
        out.println("      if(list.selectedIndex==0){");
        out.println("        document.getElementById('_aup').disabled=true;");
        out.println("      }else{");
        out.println("        document.getElementById('_aup').disabled=false;");
        out.println("      }");
        out.println("      if(list.selectedIndex<list.length-1){");
        out.println("        document.getElementById('_adown').disabled=false;");
        out.println("      }else{");
        out.println("        document.getElementById('_adown').disabled=true;");
        out.println("      }");
        out.println("    }");
        out.println("    var m=document.getElementById('adminmenu');");
        out.println("    m.value='';");
        out.println("    for(var i=0;i<list.length;++i){");
        out.println("      if(i>0){");
        out.println("        m.value=m.value+',';");
        out.println("      }");
        out.println("      m.value=m.value+list.options[i].value;");
        out.println("    }");
        out.println("  }else if(list.id=='_userMenu'){");
        out.println("    if(list.selectedIndex==-1){");
        out.println("      document.getElementById('_udel').disabled=true;");
        out.println("      document.getElementById('_uup').disabled=true;");
        out.println("      document.getElementById('_udown').disabled=true;");
        out.println("    }else{");
        out.println("      document.getElementById('_udel').disabled=false;");
        out.println("      if(list.selectedIndex==0){");
        out.println("        document.getElementById('_uup').disabled=true;");
        out.println("      }else{");
        out.println("        document.getElementById('_uup').disabled=false;");
        out.println("      }");
        out.println("      if(list.selectedIndex<list.length-1){");
        out.println("        document.getElementById('_udown').disabled=false;");
        out.println("      }else{");
        out.println("        document.getElementById('_udown').disabled=true;");
        out.println("      }");
        out.println("    }");
        out.println("    var m=document.getElementById('usermenu');");
        out.println("    m.value='';");
        out.println("    for(var i=0;i<list.length;++i){");
        out.println("      if(i>0){");
        out.println("        m.value=m.value+',';");
        out.println("      }");
        out.println("      m.value=m.value+list.options[i].value;");
        out.println("    }");
        out.println("  }");
        out.println("}");
        out.println("function addMenu(btn) {");
        out.println("  var list;");
        out.println("  if(btn.id=='_aadd'){");
        out.println("    list=document.getElementById('_adminMenu');");
        out.println("  }else if(btn.id=='_uadd'){");
        out.println("    list=document.getElementById('_userMenu');");
        out.println("  }");
        out.println("  var item=document.getElementById('_mbbMenu').options[document.getElementById('_mbbMenu').selectedIndex];");
        out.println("  var idx=list.length;");
        out.println("  for(var i=list.length-1;i>list.selectedIndex;--i){");
        out.println("    list.options[i+1]=new Option(list.options[i].text,list.options[i].value);");
        out.println("  }");
        out.println("  idx=list.selectedIndex+1;");
        out.println("  list.options[idx]=new Option(item.text,item.value);");
        out.println("  list.selectedIndex=idx;");
        out.println("  btn.disabled=true;");
        out.println("  selectMenu(list);");
        out.println("}");
        out.println("function delMenu(btn) {");
        out.println("  var list;");
        out.println("  if(btn.id=='_adel'){");
        out.println("    list=document.getElementById('_adminMenu');");
        out.println("  }else if(btn.id=='_udel'){");
        out.println("    list=document.getElementById('_userMenu');");
        out.println("  }");
        out.println("  list.options[list.selectedIndex]=null;");
        out.println("  btn.disabled=true;");
        out.println("  selectMenu(list);");
        out.println("}");
        out.println("function upMenu(btn) {");
        out.println("  var list;");
        out.println("  if(btn.id=='_aup'){");
        out.println("    list=document.getElementById('_adminMenu');");
        out.println("  }else if(btn.id=='_uup'){");
        out.println("    list=document.getElementById('_userMenu');");
        out.println("  }");
        out.println("  if(list.selectedIndex!=-1){");
        out.println("    var idx=list.selectedIndex;");
        out.println("    var opt=new Option(list.options[idx].text,list.options[idx].value);");
        out.println("    list.options[idx]=new Option(list.options[idx-1].text,list.options[idx-1].value);");
        out.println("    list.options[idx-1]=opt;");
        out.println("    list.selectedIndex=idx-1;");
        out.println("    selectMenu(list);");
        out.println("  }");
        out.println("}");
        out.println("function downMenu(btn) {");
        out.println("  var list;");
        out.println("  if(btn.id=='_adown'){");
        out.println("    list=document.getElementById('_adminMenu');");
        out.println("  }else if(btn.id=='_udown'){");
        out.println("    list=document.getElementById('_userMenu');");
        out.println("  }");
        out.println("  if(list.selectedIndex!=-1){");
        out.println("    var idx=list.selectedIndex;");
        out.println("    var opt=new Option(list.options[idx].text,list.options[idx].value);");
        out.println("    list.options[idx]=new Option(list.options[idx+1].text,list.options[idx+1].value);");
        out.println("    list.options[idx+1]=opt;");
        out.println("    list.selectedIndex=idx+1;");
        out.println("    selectMenu(list);");
        out.println("  }");
        out.println("}");
        out.println("function hasMenuItem(list,item) {");
        out.println("  for(var i=0;i<list.length;++i){");
        out.println("    if(list.options[i].value==item)return true;");
        out.println("  }");
        out.println("  return false;");
        out.println("}");
      }
      if ("SCAN".equals(mbbmenu)) {
        out.println("function _getXMLHttp() {");
        out.println("  var xmlhttp;");
        out.println("  try {");
        out.println("    xmlhttp = new ActiveXObject(\"Msxml2.XMLHTTP\");");
        out.println("  } catch (e) {");
        out.println("    try {");
        out.println("      xmlhttp = new ActiveXObject(\"Microsoft.XMLHTTP\");");
        out.println("    } catch (E) {");
        out.println("      xmlhttp = false;");
        out.println("    }");
        out.println("  }");
        out.println("  if (!xmlhttp && typeof XMLHttpRequest!='undefined') {");
        out.println("    xmlhttp = new XMLHttpRequest();");
        out.println("  }");
        out.println("  return xmlhttp;");
        out.println("}");
        out.println("var selectedMenuItem = null;");
        out.println("var selectedItem = null;");
        out.println("var selectedMenuId = 0;");
        out.println("var deleteMode = 0;");
        out.println("function getText(item) {");
        out.println("  if (item.innerText) {");
        out.println("    return item.innerText;");
        out.println("  } else if (item.textContent) {");
        out.println("    return item.textContent;");
        out.println("  }");
        out.println("  return '';");
        out.println("}");
        out.println("function selectItem(evt,menu) {");
        out.println("  if(!evt){");
        out.println("    evt=event;");
        out.println("  }");
        out.println("  selectedMenuId=menu;");
        out.println("  var item=evt.srcElement;");
        out.println("  if(!item){");
        out.println("    item=evt.target;");
        out.println("  }");
        out.println("  if(!item||item.tagName!='NOBR'){");
        out.println("    if(item.tagName!='A'||item.parentNode.tagName!='NOBR'){");
        out.println("      return;");
        out.println("    }");
        out.println("  }");
        out.println("  if(item==selectedMenuItem){");
        out.println("    return;");
        out.println("  }");
        out.println("  unselectItem();");
        out.println("  if(item){");
        out.println("    item.style.backgroundColor='#0000ff';");
        out.println("    item.style.color='#ffffff';");
        out.println("    selectedMenuItem=item;");
        out.println("  }");
        out.println("}");
        out.println("function unselectItem() {");
        out.println("  if(selectedMenuItem!=null){");
        out.println("    selectedMenuItem.style.backgroundColor='';");
        out.println("    selectedMenuItem.style.color='#000000';");
        out.println("    selectedMenuItem=null;");
        out.println("  }");
        out.println("}");
        out.println("function clickItem(item) {");
        out.println("  if(selectedMenuId==1){"); // ���O�Ώۂɒǉ�
        out.println("    if(!confirm('['+getText(item)+'] �̐ݒ��ۑ����܂��B��낵���ł���?')){");
        out.println("      return false;");
        out.println("    }");
        out.println("  }else if(selectedMenuId==2){"); // �t�ڑ�
        out.println("    if(!confirm('['+getText(item)+'] �����s���܂��B��낵���ł���?(�ڑ���A�ڑ���ŃC���X�g�[�����K�v�ɂȂ�܂�)')){");
        out.println("      return false;");
        out.println("    }");
        out.println("  }else if(selectedMenuId==3){"); // �ۑ�
        out.println("    return true;");
//        out.println("    if(!confirm('['+getText(item)+'] ��ۑ����܂��B��낵���ł���?')){");
//        out.println("      return;");
//        out.println("    }");
//        out.println("    doDownload('', getText(selectedItem), true, false, false, false);");
//        out.println("    return;");
        out.println("  }");
        out.println("  var _xmlhttp = _getXMLHttp();");
        out.println("  _xmlhttp.onreadystatechange = function() {");
        out.println("    if(_xmlhttp.readyState == 4){");
        out.println("      if(_xmlhttp.status == 200){");
        out.println("        selectedItem.style.color='#cccccc';");
        out.println("        var checkbox=selectedItem.parentNode.parentNode.childNodes[0];");
        out.println("        if(checkbox.tagName=='INPUT'){");
        out.println("          checkbox.disabled=true;");
        out.println("        }");
        out.println("        checkbox.childNodes[0].disabled=true;");
        out.println("        selectedItem=null;");
        out.println("      }else{");
        out.println("        alert('error:'+_xmlhttp.status);");
        out.println("      }");
        out.println("    }");
        out.println("  };");
        out.println("  var _senddata=null;");
        out.println("  if(selectedMenuId==1){");
        // ���O�Ώۂɒǉ�
        out.println("    _senddata='dbaccess?_command=download&addignorepath='+encodeURI(getText(selectedItem));");
        out.println("  } else if(selectedMenuId==2){");
        // �t�ڑ�
        out.println("    _senddata='dbaccess?tab=MBB&mbbmenu=SCAN&command=scan+rollback&item='+encodeURI(getText(selectedItem))+'&del='+deleteMode;");
        out.println("  }");
        out.println("  _xmlhttp.open('GET', _senddata);");
        out.println("  _xmlhttp.send(null);");
        out.println("}");
        out.println("function _doContextMenu(evt) {");
        out.println("  if(!evt){");
        out.println("    evt=event;");
        out.println("  }");
        out.println("  var item=evt.srcElement;");
        out.println("  if(!item){");
        out.println("    item=evt.target;");
        out.println("  }");
        out.println("  if(item&&item.tagName=='FONT'){");
        out.println("    var contextmenu = document.getElementById('contextmenu');");
        out.println("    if(contextmenu){");
        out.println("      var itemText=getText(item);");
        out.println("      if(!itemText||itemText.indexOf('!')!=-1){");
        out.println("        return;");
        out.println("      }");
        out.println("      contextmenu.style.display='';");
        out.println("      contextmenu.style.left=evt.clientX+document.body.scrollLeft;");
        out.println("      contextmenu.style.top=evt.clientY+document.body.scrollTop;");
        out.println("      var i=0;");
        out.println("      var item1=contextmenu.childNodes[i];");
        out.println("      if(item1&&item1.nodeType==3){");
        out.println("        contextmenu.style.width='';");
        out.println("        item1=contextmenu.childNodes[++i];");
        out.println("      }");
        out.println("      item1.innerHTML='<nobr>'+itemText+' �����O�Ώۂɒǉ�</nobr>';");
        out.println("      var item2=contextmenu.childNodes[++i];");
        out.println("      if(item2&&item2.nodeType==3){");
        out.println("        contextmenu.style.width='';");
        out.println("        item2=contextmenu.childNodes[++i];");
        out.println("      }");
        out.println("      if(item.className=='new'){");
        out.println("        item2.innerHTML='<nobr>'+itemText+' ���t�ڑ�(�ڑ����폜)</nobr>';");
        out.println("        deleteMode=1;");
        out.println("      }else{");
        out.println("        item2.innerHTML='<nobr>'+itemText+' ���t�ڑ�</nobr>';");
        out.println("        deleteMode=0;");
        out.println("      }");
        out.println("      var item3=contextmenu.childNodes[++i];");
        out.println("      if(item3&&item3.nodeType==3){");
        out.println("        contextmenu.style.width='';");
        out.println("        item3=contextmenu.childNodes[++i];");
        out.println("      }");
        out.println("      if(item.className=='new'){");
        out.println("        item3.style.display='none';");
        out.println("      }else{");
        out.println("        item3.style.display='';");
        out.println("        item3.innerHTML='<nobr><a href=\"?_command=download&file='+itemText+'\" style=\"text-decoration:none;color:#000000;\" onclick=\"_closeContextMenu()\">'+itemText+' ���_�E�����[�h</a></nobr>';");
        out.println("      }");
        out.println("      selectedItem=item;");
        out.println("      return false;");
        out.println("    }");
        out.println("  }");
        out.println("}");
        out.println("function _doMouseDown(evt) {");
        out.println("  if(!evt){");
        out.println("    evt=event;");
        out.println("  }");
        out.println("  var item=evt.srcElement;");
        out.println("  if(selectedMenuItem!=null){");
        out.println("    if(clickItem(item)){");
        out.println("      return;");
        out.println("    }");
        out.println("  }");
        out.println("  _closeContextMenu();");
        out.println("}");
        out.println("function _closeContextMenu() {");
        out.println("  var contextmenu = document.getElementById('contextmenu');");
        out.println("  if(contextmenu){");
        out.println("    contextmenu.style.display='none';");
        out.println("  }");
        out.println("}");
      }
    }
    
    out.println("</script>");
    
    out.print("<body");
    if (bodyStyle != null && bodyStyle.trim().length() > 0) {
      out.print(" style=\"" + escapeInputValue(bodyStyle) + "\"");
    }
    if (bgColor != null && bgColor.trim().length() > 0) {
      out.print(" bgcolor=\"" + escapeInputValue(bgColor) + "\"");
    }
    if (tab != null && tab.equalsIgnoreCase("MBB")) {
      out.print(" onLoad=\"_doCheckListTable();\"");
      if ("SCAN".equals(mbbmenu)) {
        out.print(" onContextMenu=\"return _doContextMenu(event);\" onMouseDown=\"_doMouseDown(event);\"");
      }
    }
    out.println(">");
    if ("SCAN".equals(mbbmenu)) {
      out.println("<div id=\"contextmenu\" style=\"position:absolute;display:none;width:100px;font-size:10pt;border-width:1px;border-style:solid;border-color:#000000;padding:2px;background-color:#ffffff;\">");
      out.println("<div onmousemove=\"selectItem(event,1)\" onmouseout=\"unselectItem()\"></div>");
      if (update != null) {
        out.println("<div style=\"display:none;\"></div>");
      } else {
        out.println("<div onmousemove=\"selectItem(event,2)\" onmouseout=\"unselectItem()\"></div>");
      }
      out.println("<div onmousemove=\"selectItem(event,3)\" onmouseout=\"unselectItem()\"></div>");
      out.println("</div>");
    }
    if (!upload) {
      out.println("<form method=\"post\" action=\"" + "?" + "\">");
    } else {
      // �A�b�v���[�h�p
      out.println("<form method=\"post\" action=\"" + "?" + "\" enctype=\"multipart/form-data\">");
    }
  }

  private void printFooter(PrintWriter out, String tab) throws ServletException {
    try {
      out.println("</form>");
      if (tab != null && (tab.equalsIgnoreCase("MBB") || tab.equalsIgnoreCase("Result"))) {
        // �_�E�����[�h�p���s�t�H�[�� // doDownload()�ŌĂ΂��
        out.println("<form name=\"downloadform\" method=\"post\" action=\"?\">");
        out.println("<input type=\"hidden\" name=\"_command\" value=\"download\">");
        out.println("<input type=\"hidden\" name=\"table\" value=\"\">");
        out.println("<input type=\"hidden\" name=\"id\" value=\"\">");
        out.println("<input type=\"hidden\" name=\"filenameid\" value=\"\">");
        out.println("<input type=\"hidden\" name=\"filenamets\" value=\"\">");
        out.println("<input type=\"submit\" id=\"downloadbtn\" value=\"\" style=\"display:none;\">");
        out.println("</form>");
      } else if (tab != null && tab.equalsIgnoreCase("Tables")) {
        // �_�E�����[�h�p���s�t�H�[��(�e�[�u���ꗗ�p)
        out.println("<form name=\"downloadform\" method=\"post\" action=\"?\">");
        out.println("<input type=\"hidden\" name=\"_command\" value=\"download\">");
        out.println("<input type=\"hidden\" name=\"table\" value=\"\">");
        out.println("<input type=\"hidden\" name=\"toexcel\" value=\"1\">");
        out.println("<input type=\"hidden\" name=\"withinfo\" value=\"\">");
        out.println("</form>");
      }
      out.println("</body>");
      out.println("</html>");
      out.flush();
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }
  
  /**
   * �e�[�u����(�����e�[�u��ID)����������Vector�ŕԂ�
   * @param conn DB�R�l�N�V����
   * @param tablePattern
   * @return String��Vector�A������΋�(null��Ԃ����Ƃ͂Ȃ�)
   */
  private Vector getObjectNames(String tablePattern, int objType) {
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      return getObjectNames(conn, schemas[0], tablePattern, objType);
    } catch (SQLException e) {
      return null;
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
    }
  }
  /**
   * �K�v�ɉ�����SQL�I�u�W�F�N�g���ɃN�H�[�e�[�V������t������
   * @param index
   * @param name
   * @return
   */
  private String getSQLObjectName(int index, String name) {
    if (isMySql(index)) {
      return name;
    }
    try {
      if (name.startsWith("$")
          || !name.equals(name.toUpperCase())
          || name.length() != name.getBytes("UTF-8").length) {
        name = "\"" + name + "\"";
      }
    } catch (UnsupportedEncodingException e) {}
    return name;
  }
  
  private static Vector getObjectNames(Connection conn, String schema, String tablePattern, int objType) {
    Vector tables = new Vector();
    if (tablePattern != null && tablePattern.indexOf("|") != -1) {
      // �p�^�[����|���܂܂��ꍇ�́A|�ŕ������e�[�u�����w��Ƃ���
      StringTokenizer tst = new StringTokenizer(tablePattern, "|");
      while (tst.hasMoreTokens()) {
        String tname = tst.nextToken();
        if (tname == null ||
            tname.indexOf("%") != -1 ||
            tname.indexOf("_") != -1) {
          tables = getObjectNames(conn, tables, schema, tname, objType);
        } else {
          if (!contains(tables, tname)) {
            tables.add(tname);
          }
        }
      }
    } else {
      // |���܂܂�Ȃ��ꍇ
      // tablePattern��null�܂���SQL���K�\���̏ꍇ�̓e�[�u�����X�g�擾
      if (tablePattern == null ||
          tablePattern.indexOf("%") != -1 ||
          tablePattern.indexOf("_") != -1) {
        tables = getObjectNames(conn, tables, schema, tablePattern, objType);
      } else {
        if (!contains(tables, tablePattern)) {
          tables.add(tablePattern.toUpperCase());
        }
      }
    }
    return tables;
  }
  
  // array�ɃL�[���܂܂�邩�`�F�b�N�i�܂܂���true�j
  private static boolean contains(Vector array, String key) {
    if (key == null) {
      return false;
    }
    if (array == null || array.size() == 0) {
      return false;
    }
    for (int i = 0; i < array.size(); ++i) {
      Object obj = array.get(i);
      if (key.equalsIgnoreCase(obj.toString())) {
        return true;
      }
    }
    return false;
  }

  private static Vector getObjectNames(Connection conn, Vector tables, String schema, String tablePattern, int objType) {
    try {
      ResultSet rs = null;
      if (tablePattern == null) {
        tablePattern = "%";
      }
      
      String[] tableType = null;
      Vector tableFilter = null;
      if (objType == OBJ_TYPE_PTABLE) {
        tableType = new String[]{"TABLE"};
      } else if (objType == OBJ_TYPE_PVIEW) {
        tableType = new String[]{"VIEW"};
      } else if (objType == OBJ_TYPE_SYNONYM) {
        tableType = new String[]{"SYNONYM"};
      } else if (objType == OBJ_TYPE_MTABLE) {
        tableType = new String[]{"TABLE"};
        // MBB�V�X�e���e�[�u��
        String[] tableBase = {
            "APPLICATION", "CLASSTYPE", "CLASSPROPERTY", "DATAFIELD", "DATAFIELDVALUE",
            "TABLE", "PACKAGE", "FUNCTION", "JOB",
            "LANGUAGE", "MESSAGE", "ROLE", "USER",
            "CUSTOMVIEWPAGE", "PAGE", "PROCESS", "VIEWPAGE", "VIEWDATACLASS"
        };
        tableFilter = new Vector();
        for (int i = 0; i < tableBase.length; ++i) {
          tableFilter.add(tableBase[i] + "MASTER");
          tableFilter.add(tableBase[i] + "NAME");
          tableFilter.add(tableBase[i] + "INFO");
        }
        tableFilter.add("FUNCTIONCOMPOSITIONMASTER");
        tableFilter.add("TABLELAYOUTMASTER");
        tableFilter.add("LICENSEMASTER");
        tableFilter.add("PAGEMESSAGE");
        tableFilter.add("CUSTOMPAGEMESSAGE");
        tableFilter.add("ITEMDEFINITIONMASTER");
        tableFilter.add("PROCESSDEFINITIONMASTER");
        tableFilter.add("PROCESSITEMRELDEFMASTER");
        tableFilter.add("SERVICELOG");
        tableFilter.add("JOBDEFINITIONMASTER");
        tableFilter.add("JOBLOG");
        tableFilter.add("JOBSTATUS");
        tableFilter.add("JOBSTATUSDETAIL");
        tableFilter.add("JOBSTATUSDETAILMESSAGE");
        tableFilter.add("TASKSCHEDULE");
        tableFilter.add("NAMEDICTIONARYMASTER");
        tableFilter.add("TABLE_VIEW_COLUMN_LIST");
      } else {
        tableType = new String[]{"TABLE"};
      }
      rs = conn.getMetaData().getTables(null, schema, tablePattern.toUpperCase(), tableType);
      String dbtype = DbAccessUtils.getDBMSType(conn);
      boolean ucase = false;
      if ("MYSQL".equals(dbtype)
          || "PGSQL".equals(dbtype)) {
        ucase = true;
      }
      while (rs.next()) {
        String table_name = rs.getString("TABLE_NAME");
        if (table_name != null && isSystemTable(table_name)) {
          continue;
        }
        if (ucase) {
          table_name = table_name.toUpperCase();
        }
        if (tableFilter != null && !contains(tableFilter, table_name)) {
          continue;
        }
        if (!contains(tables, table_name)) {
          tables.add(table_name);
        }
      }
      rs.close();
      
//      if (tables.size() == 0) {
//        // schema���T�|�[�g���Ȃ��P�[�X�H
//        rs = conn.getMetaData().getTables(null, null, tablePattern, tableType);
//        while (rs.next()) {
//          String table_name = rs.getString("TABLE_NAME");
//          if (table_name != null && isSystemTable(table_name)) {
//            continue;
//          }
//          if (!containsVector(tables, table_name)) {
//            tables.add(table_name);
//          }
//        }
//        rs.close();
//      }
    } catch (Exception e) {
      log_debug(e);
      e.printStackTrace();
    }
    if (addTables.size() > 0 && tablePattern == null) {
      tables.addAll(addTables);
    }
    return tables;
  }

  private Vector getPrimaryKeys(String tablename) {
    Connection conn = null;
    Vector pkeys = new Vector();
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      pkeys = DbAccessUtils.getPrimaryKeys(conn, schemas[0], tablename);
      if (pkeys.size() == 0) {
        boolean name = false;
        boolean info = false;
        stmt = conn.prepareStatement("SELECT (SELECT PHYSICALFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DATAFIELDID FROM TABLELAYOUTMASTER a WHERE TABLEID=? AND DATAFIELDCLASS='1' ORDER BY DATAFIELDORDER");
        if (tablename.toUpperCase().endsWith("NAME")) {
          tablename = tablename.substring(0, tablename.length() - 4);
          name = true;
        } else if (tablename.toUpperCase().endsWith("INFO")) {
          tablename = tablename.substring(0, tablename.length() - 4);
          info = true;
        } else if (tablename.toUpperCase().endsWith("_SAVE")) {
          tablename = tablename.substring(0, tablename.length() - 5);
        }
        stmt.setString(1, tablename);
        rs = stmt.executeQuery();
        while (rs.next()) {
          //String column_name = rs.getString(1).toUpperCase();
          String column_name = rs.getString(1);
          pkeys.add(column_name);
        }
        rs.close();
        rs = null;
        if (pkeys.size() == 0 && !tablename.toUpperCase().endsWith("MASTER")) {
          stmt.clearParameters();
          stmt.setString(1, tablename + "MASTER");
          rs = stmt.executeQuery();
          while (rs.next()) {
            //String column_name = rs.getString(1).toUpperCase();
            String column_name = rs.getString(1);
            pkeys.add(column_name);
          }
          rs.close();
          rs = null;
        }
        stmt.close();
        stmt = null;
        if (pkeys.size() > 0) {
          if (name) {
            pkeys.add("DISPLANGID");
            pkeys.add("PROPERTYID");
          } else if (info) {
            pkeys.add("PROPERTYID");
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {
        }
      }
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
        }
      }
    }
    return pkeys;
  }
  /**
   * �����e�[�u���A�����r���[���̑I������\������
   * @param out
   * @param objType
   */
  private void printTableTypes(PrintWriter out, int objType) {
    out.print("<select id=\"tabletype\" name=\"tabletype\" onchange=\"doTab('Tables')\">");
    for (int i = 0; i < TABLE_TYPES.length; ++i) {
      if (!isOracle(0) && i > 2) {
        break;
      }
      out.print("<option value=\"" + i + "\"");
      if (i == objType) {
        out.print(" selected");
      }
      out.print(">" + TABLE_TYPES[i]);
    }
    out.println("</select>");
    out.flush();
  }

  /**
   * �R���{�{�b�N�X�ɃI�u�W�F�N�g�ꗗ��\������
   * @param out �o�͐�
   * @param def_table �I������Ă���e�[�u����
   * @param rows SELECT�^�O��rows
   * @param count ������\�����邩
   * @param objType OBJ_TYPE_PTABLE,OBJ_TYPE_PVIEW,OBJ_TYPE_SYNONYM,OBJ_TYPE_MTABLE
   */
  private void printObjectList(PrintWriter out, String def_table, int rows, String count, int objType) {
    if (rows > 0) {
      out.print("<select name=\"table_name\" onkeydown=\"doKeyDown(this);\" onclick=\"doTableSelect(this)\" ondblclick=\"doTab('Edit')\" size=\"" + Integer.toString(rows) + "\">");
    } else {
      out.print("<select name=\"table_name\" onchange=\"doTableChange()\">");
    }
    Vector sortedObjects = new Vector();
    Vector processedObjects = new Vector();
    Vector objects = getObjectNames(null, objType);
    if (objects == null) {
      return;
    }
    // sortedObject�ɁAMASTER,NAME,INFO�̏��ɕ��ׂȂ���
    for (Iterator ite = new TreeSet(objects).iterator(); ite.hasNext(); ) {
      String objectName = (String)ite.next();
      if (processedObjects.contains(objectName)) {
        continue;
      }
      sortedObjects.add(objectName);
      String objectBaseName = null;
      if (objectName.endsWith("MASTER")) {
        objectBaseName = objectName.substring(0, objectName.length() - 6);
      } else {
        objectBaseName = objectName;
      }
      String nameObjectName = objectBaseName + "NAME";
      String infoObjectName = objectBaseName + "INFO";
      if (objects.contains(nameObjectName)) {
        if (sortedObjects.contains(nameObjectName)) {
          sortedObjects.remove(nameObjectName);
        }
        sortedObjects.add(nameObjectName);
        processedObjects.add(nameObjectName);
      }
      if (objects.contains(infoObjectName)) {
        if (sortedObjects.contains(infoObjectName)) {
          sortedObjects.remove(infoObjectName);
        }
        sortedObjects.add(infoObjectName);
        processedObjects.add(infoObjectName);
      }
    }
    //Iterator ite = new TreeSet(objects).iterator();
    Iterator ite = sortedObjects.iterator();
    Vector errorObjects = new Vector();
    Hashtable lTables = new Hashtable();
    Hashtable tablePackageId = new Hashtable();
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      if (isOracle(0) && objType == OBJ_TYPE_PVIEW) {
        // VIEW�̏ꍇ�A�G���[�Ώۂ��擾����
        Statement stmt = null;
        ResultSet rs = null;
        try {
          stmt = conn.createStatement();
          rs = stmt.executeQuery("SELECT OBJECT_NAME FROM USER_OBJECTS WHERE STATUS='INVALID'");
          while (rs.next()) {
            errorObjects.add(rs.getString(1));
          }
        } catch(SQLException e) {
        } finally {
          if (rs != null) {
            try {
              rs.close();
            } catch (SQLException e) {}
          }
          if (stmt != null) {
            try {
              stmt.close();
            } catch (SQLException e) {}
          }
        }
      }
      DbAccessUtils.getTableDefInfo(conn, lTables, tablePackageId);
      while(ite.hasNext()) {
        String table_name = (String)ite.next();
        out.print("<option value=\"" + table_name + "\"");
        String tableName = (String)lTables.get(table_name);
        if (tableName == null && (table_name.endsWith("NAME") || table_name.endsWith("INFO"))) {
          String baseTableName = table_name.substring(0, table_name.length() - 4);
          tableName = (String)lTables.get(baseTableName + "MASTER");
          if (tableName == null) {
            tableName = (String)lTables.get(baseTableName);
          }
          if (tableName != null) {
            if (table_name.endsWith("NAME")) {
              tableName = tableName + "(����)";
            } else {
              tableName = tableName + "(���)";
            }
          }
        }
        if (errorObjects.contains(table_name)) {
          // �G���[�Ώۂ̔w�i��Ԃ�����
          out.print(" style=\"background-color:" + ERROR_COLOR + ";\"");
        } else if (tableName != null) {
          out.print(" title=\"" + DbAccessUtils.escapeHTML(tableName)+ "\"");
        } else {
          out.print(" style=\"color:#a0a0a0;\"");
        }
        if (def_table != null && def_table.length() >= 3 && def_table.startsWith("\"") && def_table.endsWith("\"")) {
          def_table = def_table.substring(1, def_table.length() - 1);
        }
        if (table_name.equals(def_table)) {
          out.print(" selected>");
        } else {
          out.print(">");
        }
        out.print(table_name);
        if (count != null) {
          String sql = "SELECT COUNT(*) FROM " + table_name;
          Statement stmt = null;
          ResultSet rs = null;
          try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            int c = 0;
            if (rs.next()) {
              c = rs.getInt(1);
            }
            out.print(" (" + c + ")");
          } catch(SQLException e) {
          } finally {
            if (rs != null) {
              try {
                rs.close();
              } catch (SQLException e) {}
            }
            if (stmt != null) {
              try {
                stmt.close();
              } catch (SQLException e) {}
            }
          }
        }
      }
    } catch(SQLException e) {
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {}
      }
    }
    out.print("</select>");
  }

  private Vector getColumnsInfo(String table_name, Connection c) {
    Connection conn = c;
    Vector colInfos = new Vector();
    try {
      if (c == null) {
        conn = getConnection();
        conn.setAutoCommit(false);
      }
      if (!dbUsers[0].equalsIgnoreCase(schemas[0]) && !table_name.startsWith("\"") && table_name.indexOf(".") == -1) {
        table_name = schemas[0] + "." + DbAccessUtils.getBaseTableName(table_name);
      }
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery("SELECT * FROM " + table_name);
      ResultSetMetaData m = rs.getMetaData();
      for(int i = 0; i < m.getColumnCount(); i++) {
        String column_name = m.getColumnName(i + 1).toUpperCase();
        String type_name = m.getColumnTypeName(i + 1);
        int column_size = m.getColumnDisplaySize(i + 1);
        int type = m.getColumnType(i + 1);
        int scale = m.getScale(i + 1);
        int precision = m.getPrecision(i + 1);
        if (type_name.equalsIgnoreCase("NUMBER")) {
          type_name = "DECIMAL";
          type = 3;
          // TODO: SQL�I�ɂ�NUMBER�̕����悢��������Ȃ����ADB2��NUMBER�͑Ή����Ă��Ȃ�
          // ORACLE�́ADECIMAL��NUMBER�Ƃ��Ĉ���
        }
        Object[] col = new Object[] {
          column_name, type_name, new Integer(column_size),
          new Integer(type), new Integer(scale), new Integer(precision) };
        colInfos.add(col);
      }
      rs.close();
      stmt.close();
    } catch(SQLException e) {
    } finally {
      if (conn != null && c == null) {
        try {
          conn.close();
        }
        catch(SQLException e) {
        }
      }
    }
    return colInfos;
  }

  private void printColumns(PrintWriter out, String table_name, int rows) {
    if (rows > 0) {
      out.print("<select name=\"column_name\" size=\"" + Integer.toString(rows) + "\" onclick=\"doColumnSelect(this)\">");
    }
    else {
      out.print("<select name=\"column_name\">");
    }
    if (!isBlank(table_name)) {
      Vector colmnsInfo = getColumnsInfo(table_name, null);
      for(int i = 0; i < colmnsInfo.size(); i++) {
        Object[] cols = (Object[])colmnsInfo.get(i);
        String column_name = (String)cols[0];
        String type_name = (String)cols[1];
        int column_size = ((Integer)cols[2]).intValue();
        int type = ((Integer)cols[3]).intValue();
        int scale = ((Integer)cols[4]).intValue();
        int precision = ((Integer)cols[5]).intValue();
        out.print("<option value=\"" + column_name + "\">");
        out.print(column_name + " " + type_name + " ");
        switch (getLocalType(type)) {
        case T_NUM:
          out.print(" (");
          out.print(Integer.toString(precision));
          if (scale > 0) {
            out.print(",");
            out.print(Integer.toString(scale));
          }
          out.print(")");
          break;
        case T_CR:
        case T_VCR:
          out.print(" (");
          out.print(Integer.toString(column_size));
          out.print(")");
          break;
        default:
          break;
        }
        out.print(" [" + type + "]");
      }
    }
    out.print("</select>");
  }


  private void execSql(PrintWriter out, HttpServletRequest request, String sql) {
    Connection conn = null;
    PreparedStatement updtstmt = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      String s = getParameterSQL(sql);
      String v[] = getParameterValues(sql, getParameterCount(s));
      updtstmt = conn.prepareStatement(s);
      log_debug(s);
      for(int i = 0; i < v.length; i++) {
        updtstmt.setString(i + 1, v[i].replaceAll("''", "'"));
        log_debug((i + 1) + "=" + v[i]);
      }
      int r = updtstmt.executeUpdate();
      log_debug("update = " + r);
      insertSQLLog(sql, Integer.toString(r), null, null, getLoginInfos(request));
      conn.commit();
    } catch(Exception e) {
      try {
        conn.rollback();
      } catch (SQLException e1) {
      }
      out.println("<br><font color=\"blue\">");
      out.println(sql);
      out.println("</font><br>");
      printError(out, e);
    } finally {
      if (updtstmt != null) {
        try {
          updtstmt.close();
        } catch(SQLException e) {
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch(SQLException e) {
        }
      }
    }
  }

  /* JDBC�̃f�[�^�^�C�v�����[�J���f�[�^�^�C�v�ɕϊ� */
  private static int getLocalType(int type) {
    switch(type) {
    case java.sql.Types.VARCHAR:
      return T_VCR;
    case java.sql.Types.CHAR:
      return T_CR;
    case java.sql.Types.DECIMAL:
    case java.sql.Types.DOUBLE:
    case java.sql.Types.BIGINT:
    case java.sql.Types.INTEGER:
    case java.sql.Types.FLOAT:
    case java.sql.Types.NUMERIC:
    case java.sql.Types.REAL:
    case java.sql.Types.SMALLINT:
    case java.sql.Types.TINYINT:
      return T_NUM;
    case java.sql.Types.TIMESTAMP:
    case 1111: /* DB2 Timestamp */
      return T_TS;
    case java.sql.Types.DATE:
      return T_DT;
    case java.sql.Types.BLOB:
      return T_BLOB;
    }
    return T_CR; /* Unknown */
  }

  /* TEXT�̍ő���͒� */
  private static int getMaxSize(int type, int size, int prec, int scale) {
    switch(getLocalType(type)) {
    case T_CR:
    case T_VCR:
      return size;
    case T_TS:
      return 29;
    case T_NUM:
      // �������{�P���ĕԂ��B�����_�̏ꍇ�͏����_�����{�P
      if (prec == 0 && scale == 0) {
        return size + 1;
      }
      if (scale == 0) {
        return prec + 1;
      }
      return prec + scale + 2; // �����_�������Z
    }
    return size;
  }

  /* TEXT�̕\���T�C�Y */
  private int getTextSize(int type, int size, int prec, int scale) {
    switch(getLocalType(type)) {
    case T_CR:
    case T_VCR:
      if (size >= 50) {
        return 30;
      }
      if (size > 20) {
        return 20;
      }
      return size;
    case T_TS:
      return 30;
    case T_NUM:
      if (prec == 0 && scale == 0) {
        return size + 1;
      }
      if (scale == 0) {
        return prec + 1;
      } else {
        return prec + scale + 2;
      }
    }
    return 0;
  }

  
  private void printExecuteSQL(PrintWriter out, HttpServletRequest request, String cmd, boolean edit, String order, String direction, String autocommit, String lines, String edit_filter) {
    log_debug("printExecuteSQL={cmd=" + cmd + ",edit=" + edit + ",order=" + order + ",direction=" + direction + ",autocommit=" + autocommit + ",lines=" + lines + ",edit_filter="+ edit_filter+"}");
    Connection conn = null;
    try {
      conn = getConnection();
      setAutoCommit(conn, autocommit);
      if (cmd.replaceAll("/\\s*$", "/").endsWith("/")) {
        // /�ŏI���ꍇ�͂��̂܂܎��s����
        Statement rawstmt = null;
        try {
          String sql = cmd.substring(0, cmd.lastIndexOf("/"));
          out.print("<font color=\"blue\"><pre>");
          out.print(DbAccessUtils.escapeInputValue(sql));
          out.println("</pre></font><br>");
          rawstmt = conn.createStatement();
          // TODO: '�͒P����replaceAll�ł悢�̂��H
          rawstmt.execute(sql.replaceAll("'", "''"));
        } catch (SQLException e) {
          throw e;
        } finally {
          if (rawstmt != null) {
            try {
              rawstmt.close();
            } catch (SQLException se) {}
          }
        }
        return;
      }
      String line = getLine(cmd);
      String org_line = line;
      line = line.replace('\r', ' ');
      line = line.replace('\n', ' ');
      log_debug(line);
      boolean err = false;
      PreparedStatement updtstmt = null;
      String lastsql = null;
      int maxlines = Integer.parseInt(lines);
      String[] updateInfos = getLoginInfos(request);
      boolean maxbreak = false;
      int selects = 0;
      while(line.length() > 0) {
        String sql = line.trim();
        if (sql.length() > 0) {
          log_debug("SQL=" + sql);
          if ((sql.length() > 6) && (
              sql.substring(0, 6).compareToIgnoreCase("SELECT") == 0
              || sql.substring(0, 4).compareToIgnoreCase("WITH") == 0
              || sql.substring(0, 6).compareToIgnoreCase("VALUES") == 0
              || (sql.startsWith("(") && sql.substring(1).trim().substring(0, 6).compareToIgnoreCase("SELECT") == 0)
              )
           ) {
            long startTime = System.currentTimeMillis();
            // SELECT
            try {
              boolean export = false;
              String table_name = "";
              if (sql.substring(6,7).equals("/")) {
                String scnt = sql.substring(7, sql.indexOf(" ", 7));
                if (scnt.equalsIgnoreCase("E")) {
                  export = true;
                } else {
                  maxlines = Integer.parseInt(scnt);
                }
                sql = sql.substring(0, 6) + sql.substring(sql.indexOf(" ", 7));
              }
              int p = -1;
              if ((p = sql.toUpperCase().indexOf(" FROM ")) != -1) {
                p += 6;
                int pp = sql.indexOf(" ", p + 1);
                if (pp == -1) {
                  table_name = sql.substring(p).trim();
                } else {
                  table_name = sql.substring(p, pp).trim();
                }
                if (schemas[0] != null && !dbUsers[0].equalsIgnoreCase(schemas[0]) && !table_name.startsWith("\"") && table_name.indexOf(".") == -1) {
                  sql = sql.replaceFirst(" " + table_name, " " + schemas[0] + "." + table_name);
                }
              }
              String[] columnNames;
              int[] columnTypes;
              int[] columnMaxSizes;
              int[] columnTextSizes;
              Statement stmt = conn.createStatement();
              ResultSet rs = stmt.executeQuery(sql);
              ResultSetMetaData m = rs.getMetaData();
              if (export) {
                // INSERT SQL����\��
                out.println("<pre>");
                while (rs.next()) {
                  out.print("INSERT INTO " + table_name + " (");
                  for (int i = 0; i < m.getColumnCount(); ++i) {
                    if (i > 0) {
                      out.print(",");
                    }
                    out.print(m.getColumnName(i + 1));
                  }
                  out.print(") VALUES (");
                  for (int i = 0; i < m.getColumnCount(); ++i) {
                    if (i > 0) {
                      out.print(",");
                    }
                    if (isNumericType(m.getColumnTypeName(i + 1))) {
                      out.print(rs.getString(i + 1));
                    } else {
                      out.print(escapeSQLstr(rs.getString(i + 1)));
                    }
                  }
                  out.println(");");
                }
                out.println("");
                out.println("</pre>");
                rs.close();
                stmt.close();
                break;
                // EXPORT
              }
//              if (!edit) {
//                out.println("<span ondblclick=\"this.childNodes(0).style.display='';\"><span class=\"text\" style=\"color:#888888;display:none;\">" + sql + "</span>&nbsp</span><br>");
//              }
              out.print("<table border=\"0\" bgcolor=\"#000000\" cellspacing=\"0\" cellpadding=\"1\">");
              out.print("<tr><td><table border=\"0\" cellspacing=\"1\" bgcolor=\"#808080\" id=\"result" + (++selects) + "\">");
              out.print("<tr bgcolor=\"#dddddd\">");
              int cc = m.getColumnCount();
              columnNames = new String[cc];
              columnMaxSizes = new int[cc];
              columnTypes = new int[cc];
              columnTextSizes = new int[cc];
              // �w�b�_���i�J�������j
              if (edit) {
                out.print("<td align=\"center\" title=\"Delete\">D");
                out.print("<td align=\"center\" title=\"Update\">U");
              }
              for(int i = 0; i < cc; i++) {
                String columnName = m.getColumnName(i + 1).toUpperCase();
                columnNames[i] = columnName;
                int type = m.getColumnType(i + 1);
                columnTypes[i] = type;
                int size = m.getColumnDisplaySize(i + 1);
                int prec = m.getPrecision(i + 1); // 10�i����
                int scale = m.getScale(i + 1); // �����_�ȉ�����
                if (size < 0) {
                  size = 0;
                }
                if (prec < 0) {
                  prec = 0;
                }
                if (scale < 0) {
                  scale = 0;
                }
                columnMaxSizes[i] = getMaxSize(type, size, prec, scale);
                columnTextSizes[i] = getTextSize(type, size, prec, scale);
                out.print("<td align=\"center\">");
                if (edit) {
                  /* �w�b�_���N���b�N����ƃ\�[�g���� */
                  String direc = "asc";
                  if (direction != null && direction.equals("asc")) {
                    direc = "desc";
                  }
                  String columnTypeName = m.getColumnTypeName(i + 1);
                  String columnTypeString = columnTypeName;
                  if (columnTypeName.equalsIgnoreCase("char") || columnTypeName.equalsIgnoreCase("varchar") || columnTypeName.equalsIgnoreCase("varchar2")) {
                    columnTypeString = columnTypeString + " (" + Integer.toString(prec) + ")";
                  } else if (columnTypeName.equalsIgnoreCase("number") || columnTypeName.equalsIgnoreCase("decimal")) {
                    columnTypeString = columnTypeString + " (" + Integer.toString(prec) + "," + Integer.toString(scale) + ")";
                  }
                  out.print("<a href=\"#\" onclick=\"doEdit('','" + columnName + "','" + direc + "');\" title=\"" + columnTypeString + "\">");
                }
                out.print(columnName);
                if (edit) {
                  out.print("</a>");
                  if (columnName.equals(order)) {
                    if (direction.equals("asc")) {
                      out.print("&nbsp;��");
                    }
                    else {
                      out.print("&nbsp;��");
                    }
                  } else {
                    out.print("&nbsp;");
                  }
                  out.print("<input type=\"hidden\" id=\"" + columnName + "_type\" value=\"" + getLocalType(columnTypes[i]) + "\">");
                }
              }
              if (edit) {
                out.print("<td align=\"center\" title=\"Update\">U");
                out.print("<td align=\"center\" title=\"Delete\">D");
              }
              // �w�b�_�������܂�
              //
              // �f�[�^��
              int rec = 0;
              while(rs.next()) {
                // �ő�s���ɒB������I��
                if (maxlines > 0 && rec >= maxlines) {
                  maxbreak = true;
                  break;
                }
                String[] values = new String[cc];
                for(int i = 0; i < cc; i++) {
                  String value = null;
                  if (columnTypes[i] == Types.BLOB) {
                    InputStream is = rs.getBinaryStream(i + 1);
                    if (is != null) {
                      value = "(BLOB)\0";
                    }
                  } else {
                    value = rs.getString(i + 1);
                  }
                  values[i] = value;
                }
                out.print("<tr bgcolor=\"#ffffff\">");
                if (edit) {
                  // DELETE�{�^��
                  out.print("<td><input type=\"button\" value=\"D\"");
                  out.print(" onclick=\"doDeleteSQL(" + rec + "," + cc + ")\"");
                  out.println(">");
                  // UPDATE�{�^��
                  out.print("<td><input type=\"button\" value=\"U\"");
                  out.print(" onclick=\"doUpdateSQL(" + rec + "," + cc + ")\"");
                  out.println(">");
                }
                // �f�[�^�\��
                for(int i = 0; i < cc; i++) {
                  String value = (String)values[i];
                  if (getLocalType(columnTypes[i]) == T_NUM) {
                    if (value == null) {
                      out.print("<td bgcolor=\"#AAAAAA\" title=\"NULL\" align=\"right\">");
                    } else {
                      out.print("<td align=\"right\">");
                    }
                  } else {
                    if (value == null) {
                      out.print("<td bgcolor=\"#AAAAAA\" title=\"NULL\">");
                    } else if (value.endsWith("\0")) {
                      out.print("<td bgcolor=\"#AAAAFF\" title=\"BLOB\">");
                    } else {
                      if (edit && value.indexOf("\n") != -1) {
                        // �ҏW���[�h�ŉ��s���܂܂��ꍇ�͐Ԃ�����
                        out.print("<td bgcolor=\"" + ERROR_COLOR + "\" title=\"���s���܂܂�܂�\">");
                      } else {
                        out.print("<td>");
                      }
                    }
                  }
                  if (edit) {
                    out.print("<input type=\"text\" id=\"field_" + Integer.toString(rec) + "_" + Integer.toString(i) + "\" name=\""+ columnNames[i].toUpperCase() +"\" size=\"" + Integer.toString(columnTextSizes[i]) + "\" maxlength=\"" + Integer.toString(columnMaxSizes[i]) + "\"");
                    out.print(" value=\"");
                    out.print(DbAccessUtils.escapeInputValue(value));
                    out.print("\"");
                    out.print(">");
                    out.print("<input type=\"hidden\" id=\"ofield_" + Integer.toString(rec) + "_" + Integer.toString(i) + "\" name=\""+ columnNames[i].toUpperCase() + "\"");
                    out.print(" value=\"");
                    out.print(getHiddenValueString(value));
                    out.print("\"");
                    out.print(">");
                  } else {
                    if (value != null && value.indexOf("\n") != -1) {
                      value = DbAccessUtils.replaceAll(value, "\n", "<br>");
                    }
                    out.print(DbAccessUtils.escapeInputValue(value));
                  }
                }
                if (edit) {
                  // UPDATE�{�^��
                  out.print("<td><input type=\"button\" value=\"U\"");
                  out.print(" onclick=\"doUpdateSQL(" + rec + "," + cc + ")\"");
                  out.println(">");
                  // DELETE�{�^��
                  out.print("<td><input type=\"button\" value=\"D\"");
                  out.print(" onclick=\"doDeleteSQL(" + rec + "," + cc + ")\"");
                  out.println(">");
                }
                out.println("");
                rec++;
              }
              rs.close();
              // �ŏI�s�F���R�[�h�ǉ��p
              if (edit) {
                if (isBlank(edit_filter)) {
                  out.print("<tr bgcolor=\"#ffffff\">");
                } else {
                  /* �t�B���^������ꍇ�͐F���O���[�ŕ\�� */
                  out.print("<tr bgcolor=\"#dddddd\">");
                }
                //out.print("<td>");
                out.print("<td><input type=\"button\" value=\"S\"");
                out.print(" onclick=\"doSelectSQL(" + rec + "," + cc + ")\"");
                out.println(">");
                out.print("<td><input type=\"button\" value=\"I\"");
                out.print(" onclick=\"doInsertSQL(" + rec + "," + cc + ")\"");
                out.println(">");
                for(int i = 0; i < cc; i++) {
                  if (getLocalType(columnTypes[i]) == T_NUM) {
                    out.print("<td align=\"right\">");
                  } else {
                    out.print("<td>");
                  }
                  out.print("<input type=\"text\" id=\"field_" + Integer.toString(rec) + "_" + Integer.toString(i) + "\" name=\""+ columnNames[i].toUpperCase() + "\" size=\"" + Integer.toString(columnTextSizes[i]) + "\" maxlength=\"" + Integer.toString(columnMaxSizes[i]) + "\"");
                  out.print(" value=\"\">");
                }
                out.print("<td><input type=\"button\" value=\"I\"");
                out.print(" onclick=\"doInsertSQL(" + rec + "," + cc + ")\"");
                out.println(">");
                out.print("<td><input type=\"button\" value=\"E\"");
                out.print(" onclick=\"doExportSQL(" + rec + "," + cc + ")\"");
                out.println(">");
              }
              out.println("</table>");
              // �Ō�̂ݏo�� TODO:
              out.println("</table>");
              if (edit) {
                // �ҏW���[�h
                out.println("D=Delete,U=Update,S=Select,I=Insert,E=Export/���X�V��1�s�P�ʂł̂݉\�ł�");
              } else {
                // SQL���s����
                long endTime = System.currentTimeMillis();
                out.println(rec + "records./" + Long.toString(endTime-startTime) + "msec.<br>");
                if (ExcelManager.isEnabled()) {
                  out.println("<input type=\"button\" value=\"Excel\" onclick=\"doExcelReport(document.forms['downloadform'],document.getElementById('result" + selects + "').innerHTML);return false;\">");
                }
              }
              if (maxbreak) {
                // �S�Ẵ��R�[�h���\���ł��Ȃ������ꍇ�̃}�[�N
                out.println("<br>*<br>");
              }
              stmt.close();
              
            } catch(SQLException e) {
              out.println("<font color=\"" + ERROR_COLOR + "\">" + sql + "</font><br>");
              printError(out, e);
              err = true;
            }
          } else if (sql.length() > 6 && sql.substring(0, 6).toUpperCase().startsWith("SLEEP ")) {
            // �e�X�g�pSLEEP�R�}���h
            String time = sql.substring(6).trim();
            Thread.sleep(Long.parseLong(time));
            out.println("<span>" + sql + "</span><br>");
          } else {
            // SELECT�ȊO
            out.println("<br>");
            try {
              boolean noParam = false;
              sql = removeComment(sql);
              if ((sql.length() > 6) && sql.substring(0, 6).compareToIgnoreCase("create") == 0) {
                noParam = true;
              }
              if (sql.startsWith("\\")) {
                sql = sql.substring(1);
                noParam = true;
              }
              int r = -1;
              if (sql.indexOf("'") == -1 || noParam) {
                // ���e�����p�����[�^�̖����P�[�X
                out.print("<font color=\"blue\">");
                out.print(DbAccessUtils.escapeInputValue(sql));
                out.println("</font><br>");
                if (sql.startsWith("/")) {
                  sql = sql.substring(1);
                }
                // commit�Ȃ�Ζ���
                if (sql.compareToIgnoreCase("commit") != 0) {
                  Statement stmt = conn.createStatement();
                  r = stmt.executeUpdate(sql);
                  stmt.close();
                }
              } else {
                
                sql = org_line.trim(); // ���s�̊܂܂ꂽ�X�e�[�g�����g�ɖ߂�
                sql = removeComment(sql);
                
                // ���e�����p�����[�^�̂���P�[�X(PreparedStatement�Ŏ��s����)
                String s = getParameterSQL(sql);
                String v[] = getParameterValues(sql, getParameterCount(s));
                out.print("<font color=\"blue\">");
                out.print(s);
                out.print("; {");
                for(int i = 0; i < v.length; i++) {
                  out.print(DbAccessUtils.escapeHTML(v[i]));
                  if (i < v.length - 1) {
                    out.print(", ");
                  }
                  else {
                    out.println("}");
                  }
                }
                out.println("</font><br>");
                if (lastsql == null || !s.toUpperCase().equals(lastsql.toUpperCase())) {
                  if (updtstmt != null) {
                    updtstmt.close();
                  }
                  updtstmt = conn.prepareStatement(s);
                  lastsql = s;
                }
                for(int i = 0; i < v.length; i++) {
                  updtstmt.setString(i + 1, v[i]);
                }
                r = updtstmt.executeUpdate();
              }
              if (r >= 0) {
                out.println("<br>update " + r + " records.<br>");
              }
              insertSQLLog(sql, Integer.toString(r), null, null, updateInfos);
            } catch(SQLException e) {
              printError(out, e);
              err = true;
              if (!autocommit.equals("1")) {
                break;
              }
            }
          }
          out.flush();
        }
        if (line.length() < cmd.length()) {
          cmd = cmd.substring(org_line.length() + 1);
          line = getLine(cmd);
          org_line = line;
          line = line.replace('\r', ' ');
          line = line.replace('\n', ' ');
        } else {
          line = "";
        }
      } // line���Ȃ��Ȃ�܂ŌJ��Ԃ�
      if (updtstmt != null) {
        updtstmt.close();
      }
      if (!autocommit.equals("1")) {
        if (!err) {
          conn.commit();
        } else {
          conn.rollback();
        }
      }
    } catch(Exception e) {
      //�G���[
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch(SQLException e) {
        }
      }
    }
  }
  
  private static String removeComment(String str) {
    if (str.indexOf("/*") == -1) {
      return str;
    }
    int p = -1;
    while ((p = str.indexOf("/*")) != -1) {
      int q = str.indexOf("*/", p);
      if (q == -1) {
        break;
      }
      str = str.substring(0, p) + str.substring(q + 2);
    }
    while (str.length() > 0 && (str.charAt(0) == ' ' || str.charAt(0) == '\r' || str.charAt(0) == '\n' || str.charAt(0) == '\t')) {
      str = str.substring(1);
    }
    return str;
  }
  
  private boolean isNumericType(String typeName) {
    if (typeName.equalsIgnoreCase("number") || typeName.equalsIgnoreCase("decimal")) {
      return true;
    } else {
      return false;
    }
  }

  // �X�e�[�g�����g����͂���1�s����Ԃ�
  private static String getLine(String cmd) {
    int p = cmd.indexOf(";");
    if (p < 0) {
      // �Z�~�R������������Ȃ���΁A�S��1�s�Ɣ��f���߂�
      return cmd;
    }
    boolean inqt = false;
    for (int i = 0; i < cmd.length(); ++i) {
      char c = cmd.charAt(i);
      if (!inqt && c == ';') {
        // �N�H�[�e�[�V�����O���Z�~�R�����ŁA�����Ő؂�
        return cmd.substring(0, i);
      } else if (!inqt && c == '\'') {
        // �N�H�[�e�[�V�����O�ŃV���O���N�H�[�g���������ꍇ
        inqt = true;
      } else if (inqt && c == '\'') {
        // �N�H�[�e�[�V�������ŃV���O���N�H�[�g���������ꍇ
        if ((i < cmd.length() - 1) && (cmd.charAt(i + 1) == '\'')) {
          // �V���O���N�H�[�g��2�����ꍇ�́A�G�X�P�[�v����Ă���
          ++i;
        } else {
          // �N�H�[�e�[�V������
          inqt = false;
        }
      }
    }
    return cmd;
  }

  // ���e������?�ɒu������
  private String getParameterSQL(String sql) {
    StringBuffer newsql = new StringBuffer();
    int q = 0;
    int p = sql.indexOf("'");
    if (p == -1) {
      return sql;
    }
    while(true) {
      if (q < p) {
        newsql.append(sql.substring(q, p));
        newsql.append("?");
      }
      //���N�H�[�g��T��
      while(true) {
        p = sql.indexOf("'", p + 1);
        if ((p == -1) || ((p >= 0) && (sql.length() > p + 1) && (sql.charAt(p + 1) != '\''))) {
          break;
        }
        if (p == (sql.length() - 1)) {
          // ������̍Ō�ł���Ε��N�H�[�g�Ɣ���
          break;
        }
        p = p + 1;
      }
      if (p == -1) {
        break;
      }
      else {
        q = p + 1;
        // ���̊J�n�N�H�[�g�̌���
        p = sql.indexOf("'", q);
        if (p == -1) {
          // �Ȃ���ΏI��
          newsql.append(sql.substring(q));
          break;
        }
      }
    }
    return newsql.toString();
  }

  // ���e������String[]�ɂ��ĕԂ�
  private String[] getParameterValues(String sql, int count) {
    String[] params = new String[count];
    int q = 0;
    int p = sql.indexOf("'");
    if (p == -1) {
      return params;
    }
    int idx = 0;
    while(true) {
      q = p + 1; // ���e�����J�n�ʒu
      //���N�H�[�g��T��
      while(true) {
        p = sql.indexOf("'", p + 1);
        if (p == -1) {
          // ������ƕ��ĂȂ��P�[�X
          break;
        }
        else {
        }
        if ((sql.length() > p + 1) && (sql.charAt(p + 1) != '\'')) {
          // ���������N�H�[�g�̎��ɃN�H�[�g��������Ε��N�H�[�g�Ɣ��f���I��
          break;
        }
        if (p == (sql.length() - 1)) {
          // ������̍Ō�ł���Ε��N�H�[�g�Ɣ���
          break;
        }
        p = p + 1;
      }
      if (p == -1) {
        break;
      } else {
        params[idx] = sql.substring(q, p);
        idx ++;
        //���̊J�n�N�H�[�g
        p = sql.indexOf("'", p + 1);
        if (p == -1) {
          break;
        }
      }
    }
    return params;
  }

  // ?�̐��𐔂��ĕԂ�
  private static int getParameterCount(String sql) {
    int c = 0;
    int p = sql.indexOf("?");
    while(p > 0) {
      c++;
      p = sql.indexOf("?", p + 1);
    }
    return c;
  }
  
  private static int getBytes(String s, String charset) {
    int len;
    try {
      len = s.getBytes(charset).length;
    } catch (UnsupportedEncodingException e) {
      len = s.length();
    }
    return len;
  }
  
  private static String trim(String s, int length, String charset) {
    int len = getBytes(s, charset);
    while (len > length) {
      s = s.substring(0, s.length() - 1);
      len = getBytes(s, charset);
    }
    return s;
  }

  private void printExport(PrintWriter out, String table_name, String textexport, String withinfo, String filter) {
    Vector tablenames = new Vector();
    if (!isBlank(table_name)) {
      tablenames.add(table_name);
    } else {
      // �e�[�u�������w�肳��Ă��Ȃ��ꍇ�͑S�����e�[�u�������擾
      tablenames = getObjectNames(null, OBJ_TYPE_PTABLE);
    }
    String basetn = null;
    boolean textmode = false;
    boolean hasinfo = false;
    boolean hasname = false;
    if (textexport.equals("1")) {
      textmode = true;
    }
    if (withinfo.equals("1")) {
      if (table_name.toUpperCase().endsWith("MASTER")) {
        basetn = table_name.substring(0, table_name.length() - 6);
      } else {
        basetn = table_name;
      }
      Vector alltables = getObjectNames(null, OBJ_TYPE_PTABLE);
      if (alltables.contains(basetn + "INFO")) {
        hasinfo = true;
      }
      if (alltables.contains(basetn + "NAME")) {
        hasname = true;
      }
    }
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      Vector infoprops = new Vector();
      Vector nameprops = new Vector();
      if (hasinfo) {
        // PROPERTYID�ꗗ���擾����
        String selectsql = "SELECT DISTINCT PROPERTYID FROM " + basetn + "INFO ORDER BY PROPERTYID";
        stmt = conn.createStatement();
        rs = stmt.executeQuery(selectsql);
        while (rs.next()) {
          infoprops.add(rs.getString(1));
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
      }
      if (hasname) {
        // PROPERTYID�ꗗ���擾����
        String selectsql = "SELECT DISTINCT PROPERTYID,DISPLANGID FROM " + basetn + "NAME ORDER BY PROPERTYID,DISPLANGID";
        stmt = conn.createStatement();
        rs = stmt.executeQuery(selectsql);
        while (rs.next()) {
          nameprops.add(rs.getString(1) + ":" + rs.getString(2));
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
      }
      for(int n = 0; n < tablenames.size(); n++) {
        String tn = (String)tablenames.get(n);
        Vector pkeys = getPrimaryKeys(tn);
        String selectsql = "SELECT * FROM " + tn;
        if (filter.trim().length() > 0) {
         selectsql = selectsql + " WHERE " + filter;
        }
        if (pkeys.size() > 0) {
          // �v���C�}���L�[�Ń\�[�g
          selectsql = selectsql + " ORDER BY ";
          for (int i = 0; i < pkeys.size(); ++i) {
            if (i > 0) {
              selectsql = selectsql + ",";
            }
            selectsql = selectsql + pkeys.get(i);
          }
        }
        stmt = conn.createStatement();
        rs = stmt.executeQuery(selectsql);
        ResultSetMetaData m = rs.getMetaData();
        StringBuffer colname = new StringBuffer();
        StringBuffer createstr = new StringBuffer();
        Vector colnames = new Vector();
        // CREATE TABLE���̍쐬textexport�̏ꍇ�̓w�b�_�̐���
        //createstr.append("DROP TABLE " + tn + ";\n");
        int cc = m.getColumnCount();
        for (int i = 0; i < cc; i++) {
          String name = m.getColumnName(i + 1).toUpperCase();
          colname.append(name);
          colnames.add(name);
          if (!textmode) {
            // ��e�L�X�g���[�h��CEATE��������
            if (i == 0) {
              createstr.append("CREATE TABLE " + tn + " (" + EOL);
            }
            String type_name = m.getColumnTypeName(i + 1);
            if (type_name == null) {
              type_name = "CHAR";
            } else {
              type_name = type_name.toUpperCase();
            }
            int size = m.getColumnDisplaySize(i + 1);
            // PostgreSQL�̏ꍇ
            //if(type_name.equals("VARCHAR") && size > 255) {
            //  type_name = "TEXT";
            //}
            if (type_name.equals("CHARACTER")) {
              type_name = "CHAR";
            }
            createstr.append("  ").append(name).append(" ").append(type_name).append(" ");
            if (type_name.startsWith("VARCHAR") || type_name.equals("CHAR")) {
              createstr.append("(").append(size).append(") ");
            } else if (type_name.startsWith("DEC")) {
              int prec = m.getPrecision(i + 1);
              int scale = m.getScale(i + 1);
              createstr.append("(").append(prec).append(",").append(scale).append(") ");
            }
            if (m.isNullable(i + 1) == 0) {
              createstr.append("NOT NULL ");
            }
            if (i < cc - 1) {
              colname.append(",");
              createstr.append(",").append(EOL);
            } else {
              if (pkeys.size() > 0) {
                createstr.append(",").append(EOL);
                createstr.append(" PRIMARY KEY (");
                for (int k = 0; k < pkeys.size(); ++k) {
                  if (k > 0) {
                    createstr.append(",");
                  }
                  createstr.append(pkeys.get(k));
                }
                createstr.append(")").append(EOL);
              }
              createstr.append(");");
            }
          } else {
            // �e�L�X�g���[�h
            if (i < cc - 1) {
              colname.append("\t");
            }
          }
        }

        if (!textmode) {
          out.println(createstr.toString());
        } else {
          for (Iterator ite = infoprops.iterator(); ite.hasNext(); ) {
            colname.append("\t").append((String)ite.next());
          }
          for (Iterator ite = nameprops.iterator(); ite.hasNext(); ) {
            colname.append("\t").append((String)ite.next());
          }
          out.println("import " + tn);
          out.println(colname);
        }
        while (rs.next()) {
          StringBuffer colvalue = new StringBuffer();
          Hashtable colvalues = new Hashtable();
          for (int i = 0; i < cc; i++) {
            String value = rs.getString(i + 1);
            if (value == null) {
              colvalues.put(colnames.get(i), "");
              if (!textmode) {
                colvalue.append("null");
              } else {
                colvalue.append(" ");
              }
            } else {
              colvalues.put(colnames.get(i), value);
              if (!textmode) {
                colvalue.append(escapeSQLstr(value));
              } else {
                colvalue.append(DbAccessUtils.escape(value));
              }
            }
            if (i < cc - 1) {
              if (!textmode) {
                colvalue.append(",");
              } else {
                colvalue.append("\t");
              }
            }
          }
          if (hasinfo) {
            // INFO����"[INFO:propertyid]value"�`���Œǉ�
            String infosql = "SELECT PROPERTYID, VALUE FROM " + basetn + "INFO WHERE ";
            for (int j = 0; j < pkeys.size(); j++) {
              if (j > 0) {
                infosql += " AND ";
              }
              infosql += pkeys.get(j) + "=?";
            }
            infosql += " ORDER BY PROPERTYID";
            PreparedStatement pstmt = conn.prepareStatement(infosql);
            for (int j = 0; j < pkeys.size(); j++) {
              pstmt.setString(j + 1, (String)colvalues.get(pkeys.get(j)));
            }
            ResultSet infors = pstmt.executeQuery();
            int i = 0;
            while (infors.next()) {
              String propertyid = infors.getString(1);
              String value = infors.getString(2);
              if (value == null) {
                value = "";
              }
              // infoprops���̈ʒu���������A�قȂ��NULL�o��
              while (i < infoprops.size() && !propertyid.equalsIgnoreCase((String)infoprops.get(i))) {
                ++i;
                colvalue.append("\t");
              }
              colvalue.append("\t").append(DbAccessUtils.escape(value));
              ++i;
            }
            infors.close();
            pstmt.close();
            while (i < infoprops.size()) {
              ++i;
              colvalue.append("\t");
            }
          }
          if (hasname) {
            // NAME����"[NAME:propertyid:langid]value"�`���Œǉ�
            String namesql = "SELECT PROPERTYID,DISPLANGID,NAMEVALUE FROM " + basetn + "NAME WHERE ";
            for (int j = 0; j < pkeys.size(); j++) {
              if (j > 0) {
                namesql += " AND ";
              }
              namesql += pkeys.get(j) + "=?";
            }
            namesql += " ORDER BY PROPERTYID,DISPLANGID";
            PreparedStatement pstmt = conn.prepareStatement(namesql);
            for (int j = 0; j < pkeys.size(); j++) {
              pstmt.setString(j + 1, (String)colvalues.get(pkeys.get(j)));
            }
            ResultSet infors = pstmt.executeQuery();
            int i = 0;
            while (infors.next()) {
              String propertyid = infors.getString(1);
              String displangid = infors.getString(2);
              String namevalue = infors.getString(3);
              if (namevalue == null) {
                namevalue = "";
              }
              // nameprops���̈ʒu���������A�قȂ��NULL�o��
              while (i < nameprops.size() && !(propertyid+":"+displangid).equalsIgnoreCase((String)nameprops.get(i))) {
                ++i;
                colvalue.append("\t");
              }
              colvalue.append("\t").append(DbAccessUtils.escape(namevalue));
              ++i;
            }
            infors.close();
            pstmt.close();
            while (i < nameprops.size()) {
              ++i;
              colvalue.append("\t");
            }
          }
          if (!textmode) {
            out.println("INSERT INTO " + tn + " (" + colname.toString() + ") VALUES (" + colvalue.toString() + ");");
          } else {
            out.println(colvalue);
          }
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
      }
    } catch(SQLException e) {
      e.printStackTrace(out);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {
        }
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch(SQLException e) {
        }
      }
    }
  }

  private static Vector getTabStrings(String s) {
    Vector v = new Vector();
    int i = 0;
    int j = 0;
    while (s.startsWith("\r") || s.startsWith("\n")) {
      s = s.substring(1);
    }
    while((j = s.indexOf("\t", i)) >= 0) {
      v.add(s.substring(i, j));
      i = j + 1;
    }
    s = s.substring(i);
    while (s.endsWith("\r") || s.endsWith("\n")) {
      s = s.substring(0, s.length() - 1);
    }
    v.add(s);
    return v;
  }
  
  
  /**
   * �R�}���h���͗�����̃C���|�[�g����
   * @param out
   * @param cmd
   * @param autocommit
   */
  private void printImport(PrintWriter out, String cmd, String autocommit) {
    Connection conn = null;
    try {
      Timestamp ts = new Timestamp(System.currentTimeMillis());
      conn = getConnection();
      setAutoCommit(conn, autocommit);
      String line = DbAccessUtils.getCRLine(cmd); // import tablename
      boolean replace = false;
      int cmdlen = 7;
      if (line.startsWith("import/r")){
        replace = true;
        cmdlen += 2;
      }
      String table_name = DbAccessUtils.strip(line.substring(cmdlen));
      String companyId = null;
      if (table_name.indexOf("/") != -1) {
        int p = table_name.indexOf("/");
        companyId = table_name.substring(p + 1);
        table_name = table_name.substring(0, p);
      }
      Vector pkeys = getPrimaryKeys(table_name);
      String insertsql = "INSERT INTO " + table_name;
      String insertinfosql = null;
      String insertnamesql = null;
      PreparedStatement infostmt = null;
      PreparedStatement namestmt = null;
      boolean err = false;
      PreparedStatement iststmt = null;
      cmd = cmd.substring(line.length());
      line = DbAccessUtils.getCRLine(cmd);
      Vector tmpcolnames = getTabStrings(line);
      Hashtable tableLayout = getTableLayout(conn, table_name);
      Vector names = (Vector)tableLayout.get("$name$");
      Vector infos = (Vector)tableLayout.get("$info$");
      Hashtable nameFields = new Hashtable();
      Hashtable infoFields = new Hashtable();
      Vector colnames = getBaseColumns(conn, table_name, tmpcolnames);
      String insertvals = "";
      if (colnames.size() == 0) {
        colnames.addAll(tmpcolnames);
      }
      
      insertsql = insertsql + "(";
      for (int i = colnames.size() - 1; i > 0; --i) {
        String cn = (String)colnames.get(i);
        if (cn.trim().length() == 0) {
          // �u�����N�̏ꍇ�폜
          colnames.remove(i);
        } else {
          break;
        }
      }
      for (int i = 0; i < colnames.size(); i++) {
        if (i != 0) {
          insertsql = insertsql + ",";
          insertvals = insertvals + ",";
        }
        insertsql = insertsql + colnames.get(i);
        insertvals = insertvals + "?";
      }
      insertsql = insertsql + ")";
      insertsql = insertsql + " VALUES (" + insertvals + ")";
      cmd = cmd.substring(line.length());
      line = DbAccessUtils.getCRLine(cmd);
      out.println("<i>"+insertsql+"</i>");
      out.println("<table>");
      String deleteclass = null;
      Hashtable pkeyvalues = new Hashtable();
      if (replace) {
        String delsql = "DELETE FROM " + table_name;
        PreparedStatement delstmt = conn.prepareStatement(delsql);
        int delcnt = delstmt.executeUpdate();
        delstmt.close();
        out.print("<tr><td><i>" + delsql + "</i><td><i>�폜����=" + delcnt + "</i></td>");
        if (infos.size() > 0) {
          delsql = "DELETE FROM " + DbAccessUtils.getInfoTableName(table_name);
          delstmt = conn.prepareStatement(delsql);
          delcnt = delstmt.executeUpdate();
          delstmt.close();
          out.print("<tr><td><i>" + delsql + "</i><td><i>�폜����=" + delcnt + "</i></td>");
        }
        if (names.size() > 0) {
          delsql = "DELETE FROM " + DbAccessUtils.getNameTableName(table_name);
          delstmt = conn.prepareStatement(delsql);
          delcnt = delstmt.executeUpdate();
          delstmt.close();
          out.print("<tr><td><i>" + delsql + "</i><td><i>�폜����=" + delcnt + "</i></td>");
        }
      }
      if (tableLayout.size() > 0) {
        // ���A���̂ɂ��邩�������Ă���Ώ���ۑ����Ă���
        for (int i = 0; i < tmpcolnames.size(); ++i) {
          String colname = (String)tmpcolnames.get(i);
          int p = colname.indexOf(":");
          if (p != -1) {
            colname = colname.substring(0, p);
          }
          boolean name = false;
          for (Iterator ite = names.iterator(); ite.hasNext();) {
            if (colname.equalsIgnoreCase((String)ite.next())) {
              nameFields.put(colname, new Integer(i));
              name = true;
              break;
            }
          }
          if (!name) {
            for (Iterator ite = infos.iterator(); ite.hasNext();) {
              if (colname.equalsIgnoreCase((String)ite.next())) {
                infoFields.put(colname, new Integer(i));
                break;
              }
            }
          }
        }
      }
      
      iststmt = conn.prepareStatement(insertsql);
      while (line.length() > 0) {
        Vector vals = getTabStrings(line); // �f�[�^�s���擾
        for (int i = 0; i < tmpcolnames.size(); ++i) {
          String colname = (String)tmpcolnames.get(i);
          if (i == 0) {
            out.print("<tr>");
          }
          // BASE�t�B�[���h�Ƃ��đ��݂��邩����
          boolean found = false;
          for (int j = 0; j < colnames.size(); ++j) {
            String tmp = (String)colnames.get(j);
            if (colname.equals(tmp)) {
              found = true;
              break;
            }
          }
          if (!found) {
            continue; // BASE�t�B�[���h�Ƃ��đ��݂��Ȃ��ꍇ��for�֖߂�
          }
          out.print("<td>");
          String v = (String)vals.get(i);
          if (companyId != null && colname.equalsIgnoreCase("COMPANYID")) {
            v = companyId;
          }
          out.print(v);
          // �p�����[�^�ɒl���Z�b�g
          if (i < pkeys.size()) {
            pkeyvalues.put(colname, v);
          }
          if (replace) {
            // replace���[�h�̏ꍇ�́A�������œ��͒l���g�p
            iststmt.setString(i + 1, DbAccessUtils.unescape(v));
          } else {
            // replace���[�h�łȂ��ꍇ�́A���荀�ڂ͒l��u��������
            if (colname.equalsIgnoreCase("TIMESTAMPVALUE") 
                || colname.equalsIgnoreCase("REGISTRATIONTIMESTAMPVALUE")) {
              if (!isDB2(0)) {
                iststmt.setString(i + 1, ts.toString());
              } else {
                iststmt.setTimestamp(i + 1, ts);
              }
            } else {
              iststmt.setString(i + 1, DbAccessUtils.unescape(v));
            }
          }
          if (colname.equalsIgnoreCase("DELETECLASS")) {
            // INFO,NAME�p�ɑޔ�
            deleteclass = v;
          }
        }
        try {
          if (iststmt.executeUpdate() != 1) {
            // �G���[?
          }
        } catch (SQLException e) {
          if (autocommit.equals("1")) {
            out.println("<font color=\"" + ERROR_COLOR + "\">[ERROR]</font>");
          } else {
            throw e;
          }
        }
        // INFO�̏���
        if (infoFields.size() > 0) {
          for (Iterator ite = infoFields.keySet().iterator(); ite.hasNext();) {
            String key = (String)ite.next();
            int i = ((Integer)infoFields.get(key)).intValue();
            String value = (String)vals.get(i);
            if (value.length() == 0) {
              // ������0�̏ꍇ�̓u�����N�ɒu��������(���l���ڂ̏ꍇ�͕K���l�������Ă��邱�Ƃ�z��)
              value = " ";
            }
            if (insertinfosql == null) {
              // ����̂�INSERT�����쐬
              insertinfosql = "INSERT INTO " + DbAccessUtils.getInfoTableName(table_name) + " (";
              String tmp = "";
              for (int j = 0; j < pkeys.size(); j++) {
                if (j > 0) {
                  insertinfosql += ",";
                  tmp += ",";
                }
                insertinfosql += pkeys.get(j);
                tmp += "?";
              }
              insertinfosql += ",PROPERTYID,VALUE";
              tmp += ",?,?";
              if (deleteclass != null) {
                insertinfosql += ",DELETECLASS";
                tmp += ",?";
              }
              insertinfosql += ") VALUES (" + tmp + ")";
              infostmt = conn.prepareStatement(insertinfosql);
            }
            infostmt.clearParameters();
            for (int j = 0; j < pkeys.size(); j++) {
              infostmt.setString(j + 1, (String)pkeyvalues.get(pkeys.get(j)));
            }
            //PROPERTYID
            String propertyid = (String)tmpcolnames.get(i);
            out.println("<tr><td></td><td><i>[INFO:" + propertyid + "]</i></td><td>" + value + "</td></tr>");
            infostmt.setString(pkeys.size() + 1, propertyid);
            infostmt.setString(pkeys.size() + 2, DbAccessUtils.unescape(value));
            if (deleteclass != null) {
              infostmt.setString(pkeys.size() + 3, deleteclass);
            }
            infostmt.executeUpdate();
          }
        }
        // NAME�̏���
        if (nameFields.size() > 0) {
          for (Iterator ite = nameFields.keySet().iterator(); ite.hasNext();) {
            String key = (String)ite.next();
            int i = ((Integer)nameFields.get(key)).intValue();
            if (i >= vals.size()) {
              // �f�[�^������
              continue;
            }
            String value = (String)vals.get(i);
            if (value.length() == 0) {
              // ������0�̏ꍇ�̓u�����N�ɒu��������(NAME�͕������ڈȊO���肦�Ȃ��O��)
              value = " ";
            }
            if (insertnamesql == null) {
              // ����̂�INSERT�����쐬
              insertnamesql = "INSERT INTO " + DbAccessUtils.getNameTableName(table_name) + " (";
              String tmp = "";
              for (int j = 0; j < pkeys.size(); j++) {
                if (j > 0) {
                  insertnamesql += ",";
                  tmp += ",";
                }
                insertnamesql += pkeys.get(j);
                tmp += "?";
              }
              insertnamesql += ",DISPLANGID,PROPERTYID,NAMEVALUE";
              tmp += ",?,?,?";
              if (deleteclass != null) {
                insertnamesql += ",DELETECLASS";
                tmp += ",?";
              }
              insertnamesql += ") VALUES (" + tmp + ")";
              namestmt = conn.prepareStatement(insertnamesql);
            }
            namestmt.clearParameters();
            for (int j = 0; j < pkeys.size(); j++) {
              namestmt.setString(j + 1, (String)pkeyvalues.get(pkeys.get(j)));
            }
            //PROPERTYID
            String propertyid = (String)tmpcolnames.get(i);
            String displangid = "JA"; // �f�t�H���gJA�A�v���p�e�BID:�`������΂���ɒu��������
            int p = propertyid.indexOf(":");
            if (p != -1) {
              displangid = propertyid.substring(p + 1);
              propertyid = propertyid.substring(0, p);
            }
            out.println("<tr><td></td><td><i>[NAME:" + propertyid + ":" + displangid + "]</i></td><td>" + value + "</td></tr>");
            namestmt.setString(pkeys.size() + 1, displangid);
            namestmt.setString(pkeys.size() + 2, propertyid);
            namestmt.setString(pkeys.size() + 3, DbAccessUtils.unescape(value));
            if (deleteclass != null) {
              namestmt.setString(pkeys.size() + 4, deleteclass);
            }
            namestmt.executeUpdate();
          }
        }
        out.println("\n");
        if (line.length() < cmd.length()) {
          cmd = cmd.substring(line.length());
          line = DbAccessUtils.getCRLine(cmd);
        } else {
          line = "";
        }
      }
      out.println("</table>");
      if (iststmt != null) {
        iststmt.close();
      }
      if (infostmt != null) {
        infostmt.close();
      }
      if (namestmt != null) {
        namestmt.close();
      }
      if (!autocommit.equals("1")) {
        if (!err) {
          conn.commit();
        } else {
          conn.rollback();
        }
      }
    } catch (Exception e) {
      //�G���[
      printError(out, e);
      e.printStackTrace(out);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        }
        catch (SQLException e) {
        }
      }
    }
  }
  
  
  /**
   * �f�[�^�x�[�X���f�[�^�𒊏o����Vector�i������String[]�j�ɂ��ĕԂ�
   * @param conn
   * @param tableName
   * @param columnname
   * @param where
   * @return
   */
  private Vector getTableData(Connection conn, String tableName, String columnname, String[] keyNames, String[] params) {
    Vector result = new Vector();
    try {
      StringBuffer sql = new StringBuffer();
      StringBuffer order = new StringBuffer();
      sql.append("SELECT ").append(columnname).append(" FROM ").append(tableName);
      for (int i = 0; i < keyNames.length; ++i) {
        if (keyNames[i] == null) {
          break;
        }
        if (i == 0) {
          sql.append(" WHERE ");
          order.append(" ORDER BY ");
        } else {
          sql.append(" AND ");
          order.append(", ");
        }
        sql.append(keyNames[i]).append("=?");
        order.append(keyNames[i]);
      }
      sql.append(order.toString());
      log_debug("SQL=" + sql);
      PreparedStatement stmt = conn.prepareStatement(sql.toString());
      if (params != null) {
        for (int i = 0; i < params.length; ++i) {
          stmt.setString(i + 1, params[i]);
          log_debug("params[" + (i + 1) + "=" + params[i] + "]");
        }
      }
      ResultSet rs = stmt.executeQuery();
      int columnCount = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        String[] values = new String[columnCount];
        for (int i = 0; i < columnCount; ++i) {
          values[i] = rs.getString(i + 1);
        }
        result.add(values);
      }
      rs.close();
      stmt.close();
    } catch(SQLException e) {
      log_debug(e);
    }
    return result;
  }

  /**
   * �w��e�[�u�����^�C���X�^���v�l���擾���ĕԂ�
   * @param conn
   * @param tableName
   * @param keyField
   * @param keyValue
   * @param companyId
   * @return
   */
  private String getTimestamp(Connection conn, String tableName, String[] keyFields, String keyValue) {
    Vector tsdata = null;
    tsdata = getTableData(conn, tableName, "TIMESTAMPVALUE", keyFields, keyValue.split(",", -1));
    if (tsdata.size() > 0) {
      String[] dat = (String[])tsdata.get(0);
      if (dat != null && dat.length > 0) {
        return dat[0];
      }
      return "";
    }
    return null;
  }
  private String[] getRecordUpdateInfo(Connection conn, String tableName, String[] keyFields, String keyValue) {
    Vector tsdata = null;
    tsdata = getTableData(conn, tableName, "UPDATECOMPANYID,UPDATEUSERID,UPDATEPROCESSID,TIMESTAMPVALUE", keyFields, keyValue.split(",", -1));
    if (tsdata.size() > 0) {
      String[] dat = (String[])tsdata.get(0);
      return dat;
    }
    return null;
  }
  private static String[] shiftArray(String[] strs) {
    if (strs == null || strs.length <= 1) {
      return new String[]{};
    }
    String[] newstrs = new String[strs.length - 1];
    System.arraycopy(strs, 1, newstrs, 0, strs.length - 1);
    return newstrs;
  }
  /**
   * �e�[�u�����C�A�E�g�iTABLELAYOUTMASTER�j����Ԃ�
   * @param conn
   * @param tablename
   * @return Hashtable�`����"$pkey$","$base$","$info$","$name$"��DATAFIELDID��Vector�ŕԂ�
   */
  private static Hashtable getTableLayout(Connection conn, String tablename) {
    Hashtable fields = new Hashtable();
    try {
      PreparedStatement stmt = conn.prepareStatement("SELECT DATAFIELDID,DATAFIELDCLASS FROM TABLELAYOUTMASTER WHERE TABLEID=? ORDER BY DATAFIELDORDER");
      stmt.setString(1, tablename);
      ResultSet rs = stmt.executeQuery();
      Vector pkey = new Vector();
      Vector base = new Vector();
      Vector info = new Vector();
      Vector name = new Vector();
      while (rs.next()) {
        String datafieldId = rs.getString(1);
        String datafieldClass = rs.getString(2);
        if (datafieldClass != null) {
          fields.put(datafieldId, datafieldClass);
          if (datafieldClass.equals("1")) { // key
            pkey.add(datafieldId);
            base.add(datafieldId);
          } else if (datafieldClass.equals("2")) { // base
            base.add(datafieldId);
          } else if (datafieldClass.equals("3")) { // name
            name.add(datafieldId);
          } else { // info
            info.add(datafieldId);
          }
        }
      }
      rs.close();
      stmt.close();
      if (fields.size() == 0) {
        return fields;
      }
      fields.put("$pkey$", pkey);
      fields.put("$base$", base);
      fields.put("$info$", info);
      fields.put("$name$", name);
    } catch(SQLException e) {
    }
    return fields;
  }
  // �e�[�u�����C�A�E�g�}�X�^�A�f�[�^�t�B�[���h�}�X�^���_���e�[�u����`�����擾
  private static Hashtable getTableLayoutFull(Connection conn, String tablename) {
    Hashtable fields = new Hashtable();
    try {
      String sql = "SELECT (SELECT PHYSICALFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) FIELDID,"
        + "(SELECT CLASSPROPERTYID FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) CLASSPROPERTYID,"
        + "(SELECT DIGIT FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DIGIT,"
        + "(SELECT DECIMALPLACE FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DECIMALPLACE,"
        + "(SELECT DATATYPE FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) DATATYPE,"
        + "DATAFIELDCLASS, DATAFIELDID FROM TABLELAYOUTMASTER a WHERE TABLEID=? ORDER BY DATAFIELDORDER";
      PreparedStatement stmt = conn.prepareStatement(sql);
      stmt.setString(1, tablename);
      ResultSet rs = stmt.executeQuery();
      Vector pkey = new Vector(); // �L�[����
      Vector base = new Vector(); // �L�[����+��{����
      Vector info = new Vector(); // ��񍀖�
      Vector name = new Vector(); // ���̍���
      while (rs.next()) {
        String physicalFieldId = rs.getString(1); // �����t�B�[���hID
        String classPropertyId = rs.getString(2); // �����t�B�[���hID
        Integer digit = new Integer(rs.getInt(3)); // ����
        Integer decimalPlace = new Integer(rs.getInt(4)); // �����_�ȉ�����
        String dataType = rs.getString(5); // �f�[�^�^�C�v
        String dataFieldClass = rs.getString(6); // ���ڋ敪
        String dataFieldId = rs.getString(7); // �f�[�^����ID
        boolean notNull = "1".equals(dataFieldClass);
        Object[] data = new Object[]{physicalFieldId, dataType, digit, decimalPlace, new Boolean(notNull), dataFieldId, dataFieldClass, classPropertyId};
        // �������ږ�,�f�[�^�^�C�v,����,�����_�ȉ�����,NOT NULL, �f�[�^�t�B�[���hID(�_�����ږ�),�f�[�^�敪(1:�L�[,2:��{,3:����,4:���),�N���X�v���p�e�BID
        if (dataFieldClass != null) {
          if (dataFieldClass.equals("1")) { // key
            pkey.add(data);
            base.add(data);
          } else if (dataFieldClass.equals("2")) { // base
            base.add(data);
          } else if (dataFieldClass.equals("3")) { // name
            name.add(data);
          } else { // info
            info.add(data);
          }
        }
        fields.put(dataFieldId, data);
      }
      rs.close();
      stmt.close();
      if (fields.size() == 0 && !tablename.equals(tablename.toUpperCase())) {
        // �啶�����������قȂ�ꍇ�H
        return getTableLayoutFull(conn, tablename.toUpperCase());
      }
      fields.put("$pkey$", pkey);
      fields.put("$base$", base);
      fields.put("$info$", info);
      fields.put("$name$", name);
    } catch(SQLException e) {
    }
    return fields;
  }
  
  private Vector getBaseColumns(Connection conn, String tablename, Vector columns) {
    Vector retcolumns = new Vector();
    Hashtable fields = getTableLayout(conn, tablename);
    if (columns != null) {
      
      for (Iterator ite = columns.iterator(); ite.hasNext(); ) {
        String col = (String)ite.next();
        String datafieldClass = (String)fields.get(col.toUpperCase());
        if (datafieldClass != null && (datafieldClass.equals("1")||datafieldClass.equals("2"))) {
          // BASE�t�B�[���h�̂�Array�ɃZ�b�g
          retcolumns.add(col);
        }
      }
    } else {
      // columns��null�̏ꍇ�́A
      return (Vector)fields.get("$base$");
    }
    return retcolumns;
  }
  
  
  /**
   * ���ʃ^�u�i��ʏ㕔�jHTML���o�͂���
   * @param out �o�͐�
   * @param tabname �I�����Ă���^�u��
   */
  private void printTabs(PrintWriter out, String tabname) {
    try {
      out.println("<input type=\"hidden\" name=\"tab\" value=\"" + tabname + "\">");
      out.println("<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-bottom: 4px;\"><tr>");
      for(int i = 0; i < tabs.length; i++) {
        if (tabname.equalsIgnoreCase(tabs[i])) {
          out.println("<td class=\"selected\"><font size=\"2\">" + tabs[i] + "</font></td>");
        } else {
          out.print("<td class=\"unselected\"><font size=\"2\">");
          out.print("<a href=\"#\" onclick=\"doTab('" + tabs[i] + "');\">");
          out.print(tabs[i] + "</a></font></td>");
        }
      }
      out.println("<td class=\"spacer\" align=\"right\"><span style=\"font-size:9pt;color:#888888;\">" + getVersionString(null) + "</span>&nbsp;</td></tr></table>");
      out.flush();
    } catch(Exception e) {
      e.printStackTrace(out);
    }
  }

  private void printInputTableName(PrintWriter out, String table_name, String lines, int objType) {
    try {
      out.print("<nobr style=\"font-size:10pt;\">");
      out.print(TABLE_TYPES[objType] + ":");
      printObjectList(out, table_name, 0, null, objType);
      out.print("&nbsp;Max Lines:");
      out.print("<input type=\"text\" name=\"lines\" size=\"4\" value=\"" + lines + "\">");
      out.println("<input type=\"button\" name=\"edit\" value=\"Refresh\" onclick=\"doEdit('','','');\">");
      out.print("&nbsp;");
      out.print(table_name);
      out.println("</nobr>");
    } catch(Exception e) {
      e.printStackTrace(out);
    }
  }

  private void printCommandInputArea(PrintWriter out, String command, String autocommit) throws ServletException {
    try {
      out.println("<textarea name=\"command\" cols=\"60\" rows=\"10\">");
      if (command.trim().length() > 0) {
        out.print(DbAccessUtils.escapeInputValue(command));
      }
      out.println("</textarea><br>");
      String checked = "";
      if (autocommit.equals("1")) {
        checked = " checked";
      }
      out.println("<input type=\"checkbox\" name=\"autocommit\"" + checked + ">AutoCommit<br>");
      out.println("<input type=\"button\" name=\"execsql\" value=\"Exec\" onclick=\"doCommand('Command','execsql','1');return false;\"><br>");
      out.println("��\"<a href=\"?command=help\">help</a>\"�œ���R�}���h�̃w���v���\������܂��B<br>");
    }
    catch(Exception e) {
      throw new ServletException(e);
    }
  }

  
  private static String escapeSQLcond(String value) {
    if (value == null) {
      return " IS NULL";
    }
    return "=" + escapeSQLstr(value);
  }
  
  private static String escapeSQLstr(String value) {
    if (value == null) {
      return "NULL";
    }
    return "'" + escapeSQL(value) + "'";
  }
  
  private static String escapeSQL(String value) {
    return value.replaceAll("'", "''");
  }
  

  private String getDSName(String name) {
    int i = name.lastIndexOf("/");
    if (i >= 0) {
      String dsname = name.substring(i + 1);
      String prefix = name.substring(0, i);
      if (prefix.endsWith("jdbc")) {
        return "jdbc/" + dsname;
      }
      return dsname.toUpperCase();
    }
    return name.toUpperCase();
  }
  
  private void printExportToFile(PrintWriter out, String command, String table_name) {
    Connection conn = null;
    OutputStreamWriter crewriter = null;
    OutputStreamWriter creidxwriter = null;
    Vector tablenames = new Vector();
    String companyValue = null;
    String outputdir = command.substring(10); /* �o�̓t�H���_�� */
    int cid = 0;
    if ((cid = outputdir.indexOf(";")) >= 0) {
      String params = outputdir.substring(cid + 1).trim();
      if (params.toUpperCase().startsWith("TABLES=")) {
        // TABLES=tableId:tableId:...
        String tables = params.substring(7);
        if (tables.indexOf(":") >= 0) {
          StringTokenizer st = new StringTokenizer(tables,":");
          while (st.hasMoreElements()) {
            tablenames.add(st.nextElement());
          }
        } else {
          tablenames.add(params);
        }
      } else{
        companyValue = params;
      }
      outputdir = outputdir.substring(0, cid);
    }
    outputdir = DbAccessUtils.replaceDirectory(outputdir);
    if (tablenames.size() == 0) {
      tablenames = getObjectNames(null, OBJ_TYPE_PTABLE);
    }
    File dir = new File(outputdir);
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        out.println("cannot create directory [" + outputdir + "]");
        return;
      }
    }
    Statement stmt = null;
    ResultSet rs = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      stmt = conn.createStatement();
      Hashtable cnames = new Hashtable();
      File cresql = new File(outputdir, CREATE_SQL_FILE);
      crewriter = new OutputStreamWriter(new FileOutputStream(cresql));
      if (isOracle(0)) {
        File creidxsql = new File(outputdir, CREATE_INDEX_FILE);
        creidxwriter = new OutputStreamWriter(new FileOutputStream(creidxsql));
      }
      for(int n = 0; n < tablenames.size(); n++) {
        crewriter.write("DROP TABLE " + tablenames.get(n) + ";" + EOL);
      }
      for(int n = 0; n < tablenames.size(); n++) {
        String tn = (String)tablenames.get(n);
        if (tn.startsWith("DBACCESS") || tn.indexOf("$") != -1) {
          // DBACCESS����n�܂�e�[�u���⁐�̊܂܂��e�[�u�����X�L�b�v
          continue;
        }
        if (tn.equalsIgnoreCase("CMREPORTBLOBDATA") || tn.equalsIgnoreCase("UPLOADFILES")) {
          // BLOB���g�p���Ă���e�[�u�����X�L�b�v
          continue;
        }
        String sql = "SELECT * FROM " + tn;
        rs = stmt.executeQuery(sql);
        ResultSetMetaData m = rs.getMetaData();
        String createSql = null;
        if (isOracle(0)) {
          // TODO: �e�[�u���X�y�[�X�����Œ�ɂ��Ă���E�E�E
          createSql = DbAccessUtils.getCreateTableSQLFromResultSetMetaData(cnames, m, tn, "USERS", "INDX");
        } else {
          createSql = DbAccessUtils.getCreateTableSQLFromResultSetMetaData(cnames, m, tn, null, null);
        }
        crewriter.write(createSql + ";" + EOL);
        if (creidxwriter != null) {
          String createIndex = DbAccessUtils.getCreateIndexSQLFromTablelayoutMaster(conn, tn);
          creidxwriter.write(createIndex);
        }
        //create.append("drop table " + tn + ";\n");
        int cc = m.getColumnCount();
        String reSql = null;
        String order = "";
        // �t�B�[���h���̎擾
        for(int i = 0; i < cc; i++) {
          String name = m.getColumnName(i + 1).toUpperCase();
          if (companyValue != null && name.equalsIgnoreCase("COMPANYID")) {
            reSql = sql + " WHERE COMPANYID='" + escapeSQL(companyValue) + "'";
          }
          if (i == 0) {
            order = " ORDER BY " + name;
          } else {
            order = order + "," + name;
          }
        }
        if (reSql != null) {
          rs.close();
          rs = null;
          try {
            rs = stmt.executeQuery(reSql + order);
          } catch (SQLException e) {
            out.println("<font color=\"" + ERROR_COLOR + "\">ERROR:" + reSql + order + "</font><br>" + EOL);
            continue;
          }
        } else {
          rs.close();
          rs = null;
          try {
            rs = stmt.executeQuery(sql + order);
          } catch (SQLException e) {
            out.println("<font color=\"" + ERROR_COLOR + "\">ERROR:" + sql + order + "</font><br>" + EOL);
            continue;
          }
        }
        String filename = outputdir + "/" + tn + ".txt";
        out.println("export to " + filename);
        File outputfile = new File(filename);
        int reccnt = DbAccessUtils.writeToText(outputfile, rs);
        out.println(" (" + reccnt + " records)<br>");
        out.flush();
      }
    } catch (SQLException e) {
      e.printStackTrace(out);
    } catch (IOException e) {
      e.printStackTrace(out);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {
        }
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch(SQLException e) {
        }
      }
      if (crewriter != null) {
        try {
          crewriter.close();
        } catch(IOException e) {
        }
      }
      if (creidxwriter != null) {
        try {
          creidxwriter.close();
        } catch(IOException e) {
        }
      }
    }
  }

  /**
   * Oracle�p
   * @param out
   * @param command
   */
  private void printDDLExportToFile(PrintWriter out, String command) {
    Connection conn = null;
    OutputStreamWriter crewriter = null;
    int dbindex = 0;
    int p = command.lastIndexOf("@");
    if (p != -1) {
      String idx = command.substring(p + 1).trim();
      command = command.substring(0, p).trim();
      dbindex = Integer.parseInt(idx);
    }
    // ddl to 
    String outputdir = command.substring(7); /* �o�̓t�H���_�� */
    if (outputdir.trim().equals("*") || outputdir.trim().equals(".")) {
      outputdir = appPath + File.separator + "ddl";
    } else {
      outputdir = DbAccessUtils.replaceDirectory(outputdir);
    }
    File dir = new File(outputdir);
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        out.println("cannot create directory [" + outputdir + "]");
        return;
      }
    }

    try {
      conn = getConnection(dbindex);
      conn.setAutoCommit(false);
      int cnt = 0;
      File allsql = null;
      //2013/11/27 DB��ނ��A�쐬�Ώە��𕪂���
      ArrayList fileNames = new ArrayList();
      ArrayList objectTypes = new ArrayList();
      
      if (isOracle(dbindex)) {
        //Oracle
        fileNames.add("all_table.sql");
        objectTypes.add("TABLE");
        
        fileNames.add("all_view.sql");
        objectTypes.add("VIEW");

        fileNames.add("all_type.sql");
        objectTypes.add("TYPE");

        fileNames.add("all_sequence.sql");
        objectTypes.add("SEQUENCE");

        fileNames.add("all_procedure.sql");
        objectTypes.add("PROCEDURE");

        fileNames.add("all_function.sql");
        objectTypes.add("FUNCTION");

        fileNames.add("all_package.sql");
        objectTypes.add("PACKAGE");

        fileNames.add("all_trigger.sql");
        objectTypes.add("TRIGGER");

        fileNames.add("all_java.sql");
        objectTypes.add("JAVA SOURCE");
      }else if (isDerby(dbindex)) {
        //Derby
        fileNames.add("all_table.sql");
        objectTypes.add("T");
        
        fileNames.add("all_view.sql");
        objectTypes.add("V");
      }else if (isMSSql(dbindex)) {
        //SQL�T�[�o
        fileNames.add("all_table.sql");
        objectTypes.add("USER_TABLE");
        
        fileNames.add("all_view.sql");
        objectTypes.add("VIEW");
        
        fileNames.add("all_scale_function.sql");
        objectTypes.add("SQL_SCALAR_FUNCTIO");
        
        fileNames.add("all_inline_table_valued_function.sql");
        objectTypes.add("SQL_INLINE_TABLE_VALUED_FUNCTION");
        
        fileNames.add("all_stored_procedure.sql");
        objectTypes.add("SQL_STORED_PROCEDURE");
      }else if (isMySql(dbindex)) {
        //MYSQL
        fileNames.add("all_table.sql");
        objectTypes.add("BASE TABLE");

        fileNames.add("all_view.sql");
        objectTypes.add("VIEW");
        
        fileNames.add("all_function.sql");
        objectTypes.add("FUNCTION");

        fileNames.add("all_procedure.sql");
        objectTypes.add("PROCEDURE");
      }else{
        //���̑���DB�̏ꍇ������ǉ�����K�v
        out.println("�Ή�����Ă��Ȃ�DB�ł��B");
        return;
      }

      //�o�͑Ώۏ����擾���A�t�@�C���֏o�͂���
      for (int i = 0; i < fileNames.size(); i++) {
        String fileName = (String)fileNames.get(i);
        String objectType = (String)objectTypes.get(i);
        
        allsql = new File(outputdir, fileName);
        crewriter = new OutputStreamWriter(new FileOutputStream(allsql));
        
        //Oracle�̏ꍇ
        if (isOracle(dbindex)) {
          crewriter.write("set sqlblanklines on" + EOL);
        }
        cnt = printDDLExport(out, crewriter, outputdir, conn, schemas[0], objectType, null);
        out.flush();
        crewriter.flush();
        crewriter.close();
        
        //�f�[�^�������[���̏ꍇ�A�t�@�C�����폜
        if (cnt == 0) {
          allsql.delete();
        }
      }
    } catch (SQLException e) {
      e.printStackTrace(out);
    } catch (IOException e) {
      e.printStackTrace(out);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        }
        catch(SQLException e) {
        }
      }
      if (crewriter != null) {
        try {
          crewriter.close();
        }
        catch(IOException e) {
        }
      }
    }
  }
  /**
   * �eDB�̃V�X�e�������擾���A�t�@�C���֏o�͂���
   * @param out
   * @param crewriter
   * @param dir
   * @param conn
   * @param owner
   * @param objectType
   * @param objectName
   * @return
   * @throws SQLException
   * @throws IOException
   */
  // Oracle��p(Oracle�ȊO�̓e�[�u���̂ݎb��Ή��ADerby��VIEW����)
  private int printDDLExport(PrintWriter out, 
                             OutputStreamWriter crewriter, 
                             String dir, 
                             Connection conn, 
                             String owner, 
                             String objectType, 
                             String objectName) throws SQLException, IOException {
    String sql = null;
    ArrayList paras = new ArrayList(); //����
    
    //DB��ނ𔻕�
    if (isOracle(0)) {
      //Oracle�̏ꍇ
      sql = "SELECT OBJECT_NAME, DBMS_METADATA.GET_DDL(REPLACE(OBJECT_TYPE,' ','_'), OBJECT_NAME, OWNER) DDL "
          + " FROM ALL_OBJECTS"
          + " WHERE OWNER=? AND OBJECT_TYPE=?";
      paras.add(owner); //OWNER
      paras.add(objectType); //OBJECT_TYPE
      
      if (objectName == null) {
        sql = sql + " AND OBJECT_NAME NOT LIKE '%$%' ORDER BY OBJECT_NAME";
      } else {
        sql = sql + " AND OBJECT_NAME = ?";
        paras.add(objectName); //OBJECT_NAME
      }
    } else if (isDerby(0)) {
      //Derby
      if (owner != null) {
        owner = owner.toUpperCase();
      }
      sql = "SELECT A.TABLENAME, B.VIEWDEFINITION "
          + "  FROM SYS.SYSTABLES A "
          + "LEFT JOIN SYS.SYSVIEWS B "
          + " ON B.TABLEID=A.TABLEID "
          + " WHERE A.SCHEMAID IN (SELECT SCHEMAID FROM SYS.SYSSCHEMAS WHERE SCHEMANAME=?) AND A.TABLETYPE=?";
      paras.add(owner); //SCHEMANAME
      paras.add(objectType); //TABLETYPE
      
      if (objectName == null) {
        sql = sql + " AND A.TABLENAME NOT LIKE '%$%' ORDER BY A.TABLENAME";
      } else {
        sql = sql + " AND A.TABLENAME = ?";
        paras.add(objectName); //TABLENAME
      }
    } else if (isMSSql(0)) {
      //SQL�T�[�o
      sql = "SELECT name, object_definition(object_id) "
          + "  FROM sys.all_objects "
          + " WHERE SCHEMA_NAME(schema_id) = SCHEMA_NAME() "
          + "   AND TYPE_DESC=?";
      paras.add(objectType); //TYPE_DESC
      
      if (objectName == null) {
        sql = sql + " ORDER BY name";
      } else {
        sql = sql + " AND name = ?";
        paras.add(objectName); //NAME
      }
    } else if (isMySql(0)) {
      //MySQL�̏ꍇ 2013/11/25
      sql = "SELECT a.object_name, null " +
            " FROM (" +
            "     SELECT " +
            "        TABLE_SCHEMA AS object_schema " +
            "       ,TABLE_NAME   AS object_name " +
            "       ,TABLE_TYPE   AS object_type " +
            "     FROM INFORMATION_SCHEMA.TABLES " +
            "     UNION ALL " +
            "     SELECT " +
            "        ROUTINE_SCHEMA  AS object_schema " +
            "       ,ROUTINE_NAME    AS object_name " +
            "       ,ROUTINE_TYPE    AS object_type " +
            "     FROM INFORMATION_SCHEMA.ROUTINES " +
            "     UNION ALL " +
            "     SELECT " +
            "        TRIGGER_SCHEMA  AS object_schema " +
            "       ,TRIGGER_NAME    AS object_name " +
            "       ,'TRIGGER'       AS object_type " +
            "     FROM INFORMATION_SCHEMA.TRIGGERS " +
            " ) a " +
            "    WHERE a.object_schema=DATABASE() " +
            "      AND a.object_type=?";
      paras.add(objectType); //object_type

      if (objectName == null) {
        sql = sql + " ORDER BY a.object_name";
      } else {
        sql = sql + " AND a.object_name = ?";
        paras.add(objectName); //object_name
      }
    }else{
      //���̑�DB �� ����C������K�v������
      sql = null;
    }
    
    if (sql == null) {
      return 0;
    }
    
    PreparedStatement stmt = conn.prepareStatement(sql);
    for (int idx = 0; idx < paras.size(); idx++) {
      stmt.setString(idx + 1, (String)paras.get(idx));
    }

    ResultSet rs = stmt.executeQuery();
    int cnt = 0;
    while (rs.next()) {
      String object_name = rs.getString(1);
      String ddl = rs.getString(2);
      if (ddl != null) {
        cnt++;
        ddl = ddl.trim();
        // ���s�̕␳(��U\n�ɒu�����A\n��S��EOL�ɒu��)
        ddl = ddl.replaceAll("\r\n", "\n");
        ddl = ddl.replaceAll("\r", "\n");
        ddl = ddl.replaceAll("\n", EOL);
      } else {
        //�擾�ł��Ȃ��ꍇ�A���ʏ���
        //DB��ނ𔻕�
        if (isMSSql(0)) {
          ddl = DbAccessUtils.getCreateObjectDDLForMsSql(conn, objectType, object_name);
        } else if (isMySql(0)) {
          ddl = DbAccessUtils.getCreateObjectDDLForMySql(conn, objectType, object_name);
        } else {
          //Derby�̏ꍇ
          if (objectType.equals("T")) {
            // ORACLE�ȊO�p(SELECT����ResultSetMetaData��萶��)
            Statement dstmt = null;
            ResultSet dr = null;
            try {
              dstmt = conn.createStatement();
              dr = dstmt.executeQuery("SELECT * FROM " + object_name + " WHERE 1=0");
              ResultSetMetaData m = dr.getMetaData();
              ddl = DbAccessUtils.getCreateTableSQLFromResultSetMetaData(null, m, object_name, null, null);
            } catch (SQLException e) {
              if (dstmt != null) {
                try {
                  dstmt.close();
                } catch (SQLException e2) {}
              }
              if (dr != null) {
                try {
                  dr.close();
                } catch (SQLException e2) {}
              }
              continue;
            }
          } else {
            continue;
          }
        }
      }
      if (owner != null) {
        // "OWNER".����������
        String ownerstr = " \"" + owner.toUpperCase() + "\".\"";
        ddl = ddl.replaceAll(ownerstr, " \"");
      }

      File outFile = null;
      if (dir != null) {
        File outputDir = new File(dir, objectType);
        if (!outputDir.exists()) {
          outputDir.mkdirs();
        }
        String fileName = object_name + ".sql";
        outFile = new File(outputDir, fileName);
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8");
        if (objectType.equals("PACKAGE") || objectType.equals("TYPE")) {
          int p = ddl.indexOf("CREATE OR REPLACE " + objectType + " BODY ");
          if (p != -1) {
            String ddlh = ddl.substring(0, p).trim();
            osw.write(ddlh);
            osw.write(EOL + "/");
            osw.close();
            crewriter.write("@" + objectType + File.separator + fileName + EOL);
            String ddlb = ddl.substring(p).trim();
            fileName = object_name + "B" + ".sql";
            outFile = new File(outputDir, fileName);
            osw = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8");
            osw.write(ddlb);
            osw.write(EOL + "/" + EOL);
            osw.close();
            crewriter.write("@" + objectType + File.separator + fileName + EOL);
          } else {
            osw.write(ddl);
            osw.write(EOL + "/" + EOL);
            osw.close();
            crewriter.write("@" + objectType + File.separator + fileName + EOL);
          }
        } else {
          if (objectType.equals("TRIGGER")) {
            int p = ddl.lastIndexOf("ALTER TRIGGER ");
            if (p != -1) {
              ddl = ddl.substring(0, p);
            }
          } else if (objectType.equals("VIEW")) {
            StringBuffer view = new StringBuffer();
            view.append(ddl);
            ddl = view.toString();
          }
          ddl = ddl.replaceFirst("\\s*$", "");
          osw.write(ddl);
          osw.write(EOL + "/");
          osw.close();
          crewriter.write("@" + objectType + File.separator + fileName + EOL);
        }
      } else {
        if (ddl.endsWith(";")) {
          crewriter.write(ddl + EOL);
        } else {
          crewriter.write(ddl + ";" + EOL);
        }
      }
      if (out != null) {
        if (outFile != null && outFile.exists()) {
          out.println(objectType + "\t" + object_name + "\t" + outFile.getAbsolutePath() + "<br>");
        } else {
          out.println(objectType + "\t" + object_name + "<br>");
        }
        out.flush();
      }
    }
    rs.close();
    stmt.close();
    return cnt;
  }
  
  private int printJavaDDLExport(PrintWriter out, OutputStreamWriter crewriter, Connection conn, String owner, String objectType) throws SQLException, IOException {
    if (!isOracle(0)) { // Oracle�ȊO�͖��T�|�[�g
      return 0;
    }
    String sql = "SELECT NAME, DBMS_METADATA.GET_DDL('JAVA_SOURCE', NAME, OWNER) DDL FROM ALL_JAVA_CLASSES"
      + " WHERE OWNER=? AND ACCESSIBILITY='PUBLIC' ORDER BY NAME";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setString(1, owner);
    ResultSet rs = stmt.executeQuery();
    int cnt = 0;
    while (rs.next()) {
      String object_name = rs.getString(1);
      String ddl = rs.getString(2);
      if (ddl != null && ddl.length() > 0) {
        cnt++;
        crewriter.write(ddl.replaceAll("<br>", EOL) + ";");
        out.println(objectType + "\t" + object_name + "<br>");
      }
    }
    rs.close();
    stmt.close();
    return cnt;
  }

  private static String[] DEFAULT_MOD_ITEMS = {
    "MBB/TABLE",
    "MBB/APPLICATION",
    "MBB/PROCESS",
    "MBB/PAGE",
    "MBB/PACKAGE",
    "MBB/MESSAGE",
    "MBB/DATAFIELD",
    "MBB/MENU",
    "MBB/CLASSTYPE",
    "MBB/MENUITEM",
    "MBB/FUNCTION",
    "DB/VIEW",
    "DB/TYPE",
    "DB/TYPE BODY",
    "DB/FUNCTION",
    "DB/PROCEDURE",
    "DB/PACKAGE",
    "DB/PACKAGE BODY",
    "DB/SEQUENCE",
    "DB/TRIGGER",
    "DB/INDEX",
    "DB/SYNONYM"
  };
  private static final List hasPackageItems = Arrays.asList(
    new String[] {"MBB/TABLE", "MBB/PROCESS", "MBB/PAGE", "MBB/CLASSTYPE", "MBB/FUNCTION"}
  );
  private static final String getModSelectSQL(String moditem) {
    StringBuffer sb = new StringBuffer();
    int p = moditem.indexOf("/");
    if (p != -1) {
      String modtype = moditem.substring(0, p);
      moditem = moditem.substring(p + 1);
      if (modtype.equals("DB")) {
        // DB/����n�܂郂�W���[���́ADDL���擾����SQL��Ԃ�
        return getDbObjectSelectSQL_Oracle(moditem);
      }
    }
    if ("TEXTID".equals(moditem)) {
      sb.append("SELECT a.TEXTID").append(SQL_CONCAT).append("','").append(SQL_CONCAT).append("a.LANGID").append(" TEXTIDID,");
      sb.append(" ' ' PACKAGE,");
      sb.append(" a.UPDATECOMPANYID, a.UPDATEUSERID, a.UPDATEPROCESSID, a.TIMESTAMPVALUE,");
      sb.append(" a.VIEWTEXTNAMEVALUE NAMEVALUE FROM TEXTIDMASTER a");
      sb.append(" ORDER BY a.TEXTID, a.LANGID");
      return sb.toString();
    }
    String keyId = null;
    if ("CLASSTYPE".equals(moditem)) {
      keyId = moditem;
    } else {
      keyId = moditem + "ID";
    }
    if ("MENUITEM".equals(moditem)) {
      sb.append("SELECT a.COMPANYID").append(SQL_CONCAT).append("','").append(SQL_CONCAT).append("a.MENUID").append(SQL_CONCAT).append("','").append(SQL_CONCAT).append("a.MENUITEMID MENUITEMID,");
    } else if ("MENU".equals(moditem)) {
        sb.append("SELECT a.COMPANYID").append(SQL_CONCAT).append("','").append(SQL_CONCAT).append("a.MENUID MENUID,");
    } else {
      sb.append("SELECT a.").append(keyId).append(",");
    }
    if (hasPackageItems.contains("MBB/" + moditem)) {
      sb.append(" a.PACKAGEID PACKAGE,");
    } else {
      sb.append(" ' ' PACKAGE,");
    }
    sb.append(" a.UPDATECOMPANYID, a.UPDATEUSERID, a.UPDATEPROCESSID, a.TIMESTAMPVALUE,");
    sb.append(" b.NAMEVALUE FROM ").append(moditem).append("MASTER a");
    sb.append(" LEFT JOIN ").append(moditem).append("NAME b");
    sb.append(" ON b.").append(keyId).append(" = a.").append(keyId);
    if ("MENUITEM".equals(moditem)) {
      sb.append(" AND b.COMPANYID=a.COMPANYID AND b.MENUID=a.MENUID");
    } else if ("MENU".equals(moditem)) {
      sb.append(" AND b.COMPANYID=a.COMPANYID");
    }

    sb.append(" AND b.DISPLANGID='JA' AND b.PROPERTYID='");
    if ("MESSAGE".equals(moditem)) {
      sb.append("MESSAGE");
    } else {
      sb.append("OFFICIALNAME");
    }
    sb.append("'");
    sb.append(" ORDER BY ").append(keyId);
    return sb.toString();
  }
  
  private static final String getDbObjectSelectSQL_Oracle(String object_type) {
    StringBuffer sb = new StringBuffer();
    sb.append("SELECT OBJECT_NAME, ' ' PACKAGE,");
    sb.append(" ' ' UPDATECOMPANYID, ' ' UPDATEUSERID, ' ' UPDATEPROCESSID, LAST_DDL_TIME TIMESTAMPVALUE,");
    sb.append(" ' ' NAMEVALUE");
    sb.append(" FROM USER_OBJECTS WHERE OBJECT_TYPE='").append(object_type).append("'");
    if (object_type.indexOf("INDEX") != -1) {
      sb.append(" AND OBJECT_NAME NOT LIKE 'SYS%'");
    }
    return sb.toString();
  }
  private static final String getModBasePath(String moditem) {
    return moditem.toLowerCase().replaceAll(" ", "_") + "/";
  }
  
//  private static final String getObjectDDL(Connection conn, String objectType, String objectName) throws SQLException {
//    if (objectType.startsWith("DB/")) {
//      objectType = objectType.substring(3);
//    }
//    String sql = "SELECT DBMS_METADATA.GET_DDL(OBJECT_TYPE, OBJECT_NAME) FROM USER_OBJECTS WHERE OBJECT_TYPE=? AND OBJECT_NAME=?";
//    PreparedStatement stmt = null;
//    ResultSet rs = null;
//    try {
//      DatabaseMetaData meta = conn.getMetaData();
//      String schema = meta.getUserName();
//      stmt = conn.prepareStatement(sql);
//      stmt.setString(1, objectType);
//      stmt.setString(2, objectName);
//      rs = stmt.executeQuery();
//      if (rs.next()) {
//        String ddl = rs.getString(1);
//        ddl = ddl.replaceAll("\"" + schema + "\"\\.", "");
//        ddl = ddl.replaceAll("^\\s+", "");
//        ddl = ddl.replaceAll("\\s+$", "");
//        ddl = ddl.replaceAll("\r\n", "\r");
//        ddl = ddl.replaceAll("\n", "\r");
//        ddl = ddl.replaceAll("\r", EOL);
//        return ddl;
//      }
//    } finally {
//      if (rs != null) {
//        try {
//          rs.close();
//        } catch (SQLException e) {}
//      }
//      if (stmt != null) {
//        try {
//          stmt.close();
//        } catch (SQLException e) {}
//      }
//    }
//    return null;
//  }
  // TODO* printDDLExport�Ə������J�u���Ă�
  private final String getObjectDDL(Connection conn, String objectType, String objectName) throws SQLException {
    if (objectType.startsWith("DB/")) {
      objectType = objectType.substring(3);
    }
    String sql = null;
    String owner = null;
    ArrayList paras = new ArrayList(); //����
    
    //DB��ނ𔻕�
    if (isOracle(0)) {
      //Oracle�̏ꍇ
      owner = schemas[0];
      sql = "SELECT OBJECT_NAME, DBMS_METADATA.GET_DDL(REPLACE(OBJECT_TYPE,' ','_'), OBJECT_NAME, OWNER) DDL "
          + " FROM ALL_OBJECTS"
          + " WHERE OWNER=? AND OBJECT_TYPE=?";
      paras.add(owner); //OWNER
      paras.add(objectType); //OBJECT_TYPE
      
      if (objectName == null) {
        sql = sql + " AND OBJECT_NAME NOT LIKE '%$%' ORDER BY OBJECT_NAME";
      } else {
        sql = sql + " AND OBJECT_NAME = ?";
        paras.add(objectName); //OBJECT_NAME
      }
    } else if (isDerby(0)) {
      //Derby
      owner = schemas[0];
      if (owner != null) {
        owner = owner.toUpperCase();
      }
      sql = "SELECT A.TABLENAME, B.VIEWDEFINITION "
          + "  FROM SYS.SYSTABLES A "
          + "LEFT JOIN SYS.SYSVIEWS B "
          + " ON B.TABLEID=A.TABLEID "
          + " WHERE A.SCHEMAID IN (SELECT SCHEMAID FROM SYS.SYSSCHEMAS WHERE SCHEMANAME=?) AND A.TABLETYPE=?";
      paras.add(owner); //SCHEMANAME
      paras.add(objectType); //TABLETYPE
      
      if (objectName == null) {
        sql = sql + " AND A.TABLENAME NOT LIKE '%$%' ORDER BY A.TABLENAME";
      } else {
        sql = sql + " AND A.TABLENAME = ?";
        paras.add(objectName); //TABLENAME
      }
    } else if (isMSSql(0)) {
      //SQL�T�[�o
      sql = "SELECT NAME, object_definition(object_id) "
          + "FROM sys.all_objects "
          + "WHERE SCHEMA_NAME(schema_id) = SCHEMA_NAME() "
          + "  AND TYPE_DESC=?";
      paras.add(objectType); //TYPE_DESC
      
      if (objectName == null) {
        sql = sql + " ORDER BY NAME";
      } else {
        sql = sql + " AND NAME = ?";
        paras.add(objectName); //NAME
      }
    } else if (isMySql(0)) {
      //MySQL�̏ꍇ 2013/11/25
      sql = "SELECT a.object_name, null " +
            " FROM (" +
            "     SELECT " +
            "        TABLE_SCHEMA AS object_schema " +
            "       ,TABLE_NAME   AS object_name " +
            "       ,TABLE_TYPE   AS object_type " +
            "     FROM INFORMATION_SCHEMA.TABLES " +
            "     UNION ALL " +
            "     SELECT " +
            "        ROUTINE_SCHEMA  AS object_schema " +
            "       ,ROUTINE_NAME    AS object_name " +
            "       ,ROUTINE_TYPE    AS object_type " +
            "     FROM INFORMATION_SCHEMA.ROUTINES " +
            "     UNION ALL " +
            "     SELECT " +
            "        TRIGGER_SCHEMA  AS object_schema " +
            "       ,TRIGGER_NAME    AS object_name " +
            "       ,'TRIGGER'       AS object_type " +
            "     FROM INFORMATION_SCHEMA.TRIGGERS " +
            " ) a " +
            "    WHERE a.object_schema=DATABASE() " +
            "      AND a.object_type=?";
      paras.add(objectType); //object_type

      if (objectName == null) {
        sql = sql + " ORDER BY a.object_name";
      } else {
        sql = sql + " AND a.object_name = ?";
        paras.add(objectName); //object_name
      }
    }else{
      //���̑�DB �� ����C������K�v������
      sql = null;
    }
    
    if (sql == null) {
      return null;
    }
    
    StringBuffer allDdl = new StringBuffer();
    PreparedStatement stmt = null;
    ResultSet rs = null;

  try{
    stmt = conn.prepareStatement(sql);
    for (int idx = 0; idx < paras.size(); idx++) {
      stmt.setString(idx+1, (String)paras.get(idx));
    }

    rs = stmt.executeQuery();
    int cnt = 0;
    while (rs.next()) {
      String object_name = rs.getString(1);
      String ddl = rs.getString(2);
      if (ddl != null) {
        cnt++;
        ddl = ddl.trim();
        // ���s�̕␳(��U\n�ɒu�����A\n��S��EOL�ɒu��)
        ddl = ddl.replaceAll("\r\n", "\n");
        ddl = ddl.replaceAll("\r", "\n");
        ddl = ddl.replaceAll("\n", EOL);
      } else {
        //�擾�ł��Ȃ��ꍇ�A���ʏ���
        //DB��ނ𔻕�
        if (isMSSql(0)) {
          ddl = DbAccessUtils.getCreateObjectDDLForMsSql(conn, objectType, object_name);
        } else if (isMySql(0)) {
          ddl = DbAccessUtils.getCreateObjectDDLForMySql(conn, objectType, object_name);
        } else {
          //Derby�̏ꍇ
          if (objectType.equals("T")) {
            // ORACLE�ȊO�p(SELECT����ResultSetMetaData��萶��)
            Statement dstmt = null;
            ResultSet dr = null;
            try {
              dstmt = conn.createStatement();
              dr = dstmt.executeQuery("SELECT * FROM " + object_name + " WHERE 1=0");
              ResultSetMetaData m = dr.getMetaData();
              ddl = DbAccessUtils.getCreateTableSQLFromResultSetMetaData(null, m, object_name, null, null);
            } catch (SQLException e) {
              if (dstmt != null) {
                try {
                  dstmt.close();
                } catch (SQLException e2) {}
              }
              if (dr != null) {
                try {
                  dr.close();
                } catch (SQLException e2) {}
              }
              continue;
            }
          } else {
            continue;
          }
        }
      }
      if (owner != null) {
        // "OWNER".����������
        String ownerstr = " \"" + owner.toUpperCase() + "\".\"";
        ddl = ddl.replaceAll(ownerstr, " \"");
      }
      allDdl.append(ddl);
    }
    } catch (SQLException e) {
      log_debug(e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
   if (allDdl.length() > 0) {
    return allDdl.toString();
   }
   return null;
  }
  
  // Oracle�̂�
  private static final long getObjectLastModified(Connection conn, String objectType, String objectName) throws SQLException {
    String sql = "SELECT LAST_DDL_TIME FROM USER_OBJECTS WHERE OBJECT_TYPE=? AND OBJECT_NAME=?";
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement(sql);
      stmt.setString(1, objectType);
      stmt.setString(2, objectName);
      rs = stmt.executeQuery();
      if (rs.next()) {
        Timestamp ts = rs.getTimestamp(1);
        if (ts != null) {
          return ts.getTime();
        }
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    return -1;
  }
  
  private static final String getTableIdFromPath(String path) {
    if (path == null) {
      return null;
    }
    if (path.startsWith("mbb/")) {
      return path.substring(4, path.indexOf("/", 4)).toUpperCase() + "MASTER";
    } else if (path.startsWith("db/")) {
      return path.substring(3, path.indexOf("/", 3)).toUpperCase();
    }
    return null;
  }
  private static final String getKeyFieldIdFromPath(String path) {
    if (path == null) {
      return null;
    }
    if (path.startsWith("mbb/")) {
      return path.substring(4, path.indexOf("/", 4)).toUpperCase() + "ID";
    }
    return null;
  }
  private static final String getModuleIdFromPath(String path) {
    if (path == null) {
      return null;
    }
    if (path.startsWith("mbb/") || path.startsWith("db/")) {
      String id = path.substring(path.lastIndexOf("/") + 1);
      return id;
    }
    return null;
  }
  private static final long getMBBLastModified(Connection conn, String path) {
    if (path.startsWith("db/")) {
      try {
        return getObjectLastModified(conn, getTableIdFromPath(path), getModuleIdFromPath(path));
      } catch (SQLException e) {
        log_debug(e);
        return -1;
      }
    }
    String tableId = getTableIdFromPath(path);
    String where = null;
    if ("MENUITEMMASTER".equals(tableId)) {
      where = "COMPANYID=? AND MENUID=? AND MENUITEMID=?";
    } else {
      where = getKeyFieldIdFromPath(path) + "=?";
    }
    String sql = "SELECT TIMESTAMPVALUE FROM " + tableId
      + " WHERE " + where;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement(sql);
      String id = getModuleIdFromPath(path);
      if ("MENUITEMMASTER".equals(tableId)) {
        String[] ids = id.split(",", -1);
        if (ids.length > 0) {
          stmt.setString(1, ids[0]);
        }
        if (ids.length > 1) {
          stmt.setString(2, ids[1]);
        }
        if (ids.length > 2) {
          stmt.setString(3, ids[2]);
        }
        // �l������Ȃ��ꍇ�́A�p�����[�^�s����SQL�G���[�𔭐�������
      } else {
        stmt.setString(1, id);
      }
      rs = stmt.executeQuery();
      if (rs.next()) {
        Timestamp ts = rs.getTimestamp(1);
        if (ts != null) {
          return ts.getTime();
        }
      }
    } catch (Exception e) {
      log_debug(e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    return -1;
  }
  
  /**
   * ���W���[���X�L�����������Ȃ�
   *   scan (scan compare)
   *   scan all
   *   scan http://�E�E�E
   *   scan commitall
   * @param out
   * @param command
   * @param updateInfo
   */
  private void printMBBScanModules(PrintWriter out, HttpServletRequest request) {
    
    // �ŏ��ɍŐV�̏�ԁi���O�Ώہj���擾����
    loadIgnoreModules();
    
    // TODO: id���t�@�C�����Ƃ��ĕs���ȏꍇ(:��/���܂܂��P�[�X)�̑Ή������T�|�[�g
    
    String command = request.getParameter("command");
    if (command == null || command.trim().length() == 0) {
      command = "scan";
    }
    String[] loginInfos = getLoginInfos(request);
    String[] getfiles = request.getParameterValues("file");
    String[] delfiles = request.getParameterValues("delfile");
    String remote_url = request.getParameter("url");
    String update = request.getParameter("update");
    boolean remote = false;

    StringTokenizer st = new StringTokenizer(command);
    st.nextToken(); // "scan"���X�L�b�v
    String option = null;
    String option2 = null;
    if (st.hasMoreTokens()) {
      option = st.nextToken();
      if (st.hasMoreTokens()) {
        option2 = st.nextToken();
      }
      if (st.hasMoreTokens()) {
        option2 = option2 + " " + st.nextToken();
      }
    }
    String findKey = "";
    if (option2 != null && option2.startsWith("find:") && option2.length() > 5) {
      // ID�ɂ�闚������
      findKey = option2.substring(5);
    }
    if ("commitall".equals(option) && getfiles == null) {
      option = "commit";
      getfiles = null;
    }
    
    String compareTarget = null; // dbaccess�̎�O�܂ł�URL
    String url = null; // ���ۂɃA�N�Z�X����URL
    if (stagingURL != null && stagingURL.trim().length() > 0) {
      url = stagingURL;
      if (url.endsWith("/")) {
        url = url + "dbaccess";
      }
      if (stagingPass != null) {
        url = DbAccessUtils.appendParameter(url, "password", stagingPass);
      }
    }
    if ("retrieve".equals(option) && remote_url != null && remote_url.trim().length() > 0) {
      url = remote_url;
      remote = true;
    }
    if (option != null && option.startsWith("http:") || update != null) {
      if (option.startsWith("http:")) {
        update = option;
        option = "compare";
      }
    }
    if (update != null && update.startsWith("http:")) {
      url = update;
      String url1 = url;
      String url2 = "";
      if (url.indexOf("?") != -1) {
        url1 = url.substring(0, url.indexOf("?"));
        url2 = url.substring(url.indexOf("?"));
      }
      if (!url1.endsWith("/dbaccess")) {
        if (url1.endsWith("/")) {
          url1 = url1 + "dbaccess";
        } else {
          url1 = url1 + "/dbaccess";
        }
        url = url1 + url2;
      }
    } else {
      update = null;
    }
    log_debug("url="+url);
    // compareTarget�ɕ\���p��URL��ݒ肷��
    if (url != null && url.indexOf("?") != -1) {
      compareTarget = url.substring(0, url.indexOf("?"));
    } else {
      compareTarget = url;
    }
    if (compareTarget != null) {
      compareTarget = compareTarget.substring(0, compareTarget.lastIndexOf("/") + 1);
    }

    File updatePath = null;
    if (updateWorkPath == null || updateWorkPath.trim().length() == 0) {
      // �f�t�H���g��appPath�z����WEB-INF/update�t�H���_�Ƃ���
      updatePath = new File(appPath, "WEB-INF/update");
    } else {
      updatePath = new File(updateWorkPath);
    }
    
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"SCAN\">");
    if (update != null) {
      out.println("<input type=\"hidden\" name=\"update\" value=\"" + DbAccessUtils.escapeInputValue(update) + "\">");
    }
    out.println("<table>");
    out.println("<tr><td><a href=\"dbaccess?tab=MBB\">MBB</a></td><td>-</td><td>���W���[���ڑ�</td></tr>");
    out.println("</table>");
    out.flush();
    
    if ((option == null || !option.startsWith("all")) &&
        (compareTarget == null || compareTarget.trim().length() == 0)) {
      // �ڑ���URL���ݒ�̏ꍇ�Aupdate/new�t�H���_������Έڑ�����null�̂܂�
      // �i�߂�B�i���W���[���A�b�v���[�h��t�ڑ��̏ꍇ�j
      File newFolder = new File(updatePath, "new");
      if (!newFolder.exists() || newFolder.list().length == 0) {
        out.print("<span class=\"text\">");
        out.print("��r��(�ڑ���)������`����Ă��܂���(config�R�}���h�Őݒ肵�Ă�������)");
        out.print("</span>");
        return;
      } else {
        compareTarget = null;
        if (option == null) {
          option = "compare";
        }
      }
    }
    if ("rollback".equals(option)) {
      // rollback�̏ꍇ��item���t�ڑ�
      String item = request.getParameter("item");
      String del = request.getParameter("del");
      String localUrl = request.getRequestURL().toString();
      if (localUrl.indexOf("password=") == -1) {
        if (DBACCESS_ADMINPASSWORD != null && DBACCESS_ADMINPASSWORD.trim().length() > 0) {
          localUrl = localUrl + "?password=" + DBACCESS_ADMINPASSWORD;
        }
      }
      try {
        log_debug("localUrl="+localUrl);
        log_debug("remoteUrl="+stagingURL);
        log_debug("item="+item);
        DbAccessUtils.rollbackRequest(localUrl, stagingURL, stagingProxy, item, del, null);
      } catch (IOException e) {
        log("rollback", e);
      }
      return;
    }
    
    // scan all �܂��� scan {���W���[���^�C�v...} �̏ꍇ�̓e�L�X�g�ŕԂ����߈ȉ��͕\�����Ȃ�
    if (option == null || (!option.startsWith("all") && !option.startsWith("{"))) {
      out.print("<nobr class=\"text\">");
      if (compareTarget != null) {
        StringBuffer title = new StringBuffer();
        title.append("�X�V��ƃp�X�F").append(updatePath.getAbsolutePath());
        out.print("<span class=\"text\" title=\"" + title.toString() + "\">");
        if (update == null) {
          // �ʏ�
          out.print("��r��(�ڑ���)���F " + compareTarget);
        } else {
          // �ڑ��������w�肳�ꂽ�ꍇ(�X�V�̂�)
          out.print("��r��(�ڑ���)���F (" + compareTarget + ")");
        }
        out.print("&nbsp;</span>");
        if (stagingProxy != null && stagingProxy.trim().length() > 0) {
          out.print("<span title=\"Proxy:" + stagingProxy + "\">*</span>");
        }
        if (!new File(appPath, "src").exists()) {
          out.print("<span title=\"src�t�H���_��Java�\�[�X���i�[����ƃ\�[�X�̍��ق��r���邱�Ƃ��ł��܂�\">?</span>");
        }
        // ��r�{�^��
        out.println("<input type=\"button\" id=\"comparebtn\" value=\"��r\" onclick=\"doCommand('MBB','command','scan compare');return false;\">");
        // �����{�^��
        out.println("<input type=\"button\" id=\"comparebtn\" value=\"����\" onclick=\"doCommand('MBB','command','scan history');return false;\">");
        // �I�v�V����
        out.print("&nbsp;&nbsp;&nbsp;");
        out.print("<span id=\"_optionslabel\" style=\"\">");
        out.print("<a href=\"javascript:void(0);\" onclick=\"document.getElementById('_optionslabel').style.display='none';document.getElementById('_options').style.display='';\">��������&gt;&gt;</a>");
        out.print("</span>");
        out.print("<span id=\"_options\" class=\"text\" style=\"display:none;\">");
        out.print("�����L�[");
        out.print("<input type=\"text\" id=\"findkey\" value=\"" + DbAccessUtils.escapeInputValue(findKey) + "\">");
        out.print("<input type=\"button\" id=\"findbtn\" value=\"��������\" onclick=\"doCommand('MBB','command','scan history find:'+document.getElementById('findkey').value);return false;\">");
        out.print("</span>");
      }
      out.println("</nobr><br>");
      // update�t�H���_��alert.txt�t�@�C��������΁A���̃t�@�C���̒���\������(�t�@�C���̒���"break"�̍s������΁A�����ŏI�����ď��������f�j
      // ���������[�X���ɓ��t�@�C�����쐬���A�X�V�r�����ł̈ڑ���}������
      File alertFile = new File(updatePath, "alert.txt");
      if (alertFile.exists()) {
        out.print("<span class=\"text\" style=\"color:#ff0000;\">");
        try {
          BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(alertFile), "UTF-8"));
          String line = null;
          while ((line = br.readLine()) != null) {
            if (line.equals("break")) {
              if (option != null && !option.equals("history")) {
                option = null;
              }
              break;
            }
            out.println(line);
          }
          br.close();
        } catch (Exception e) {
          out.print(e.getMessage());
        }
        out.println("</span><br>");
      }
      out.flush();
      if (option == null) {
        // option�Ȃ��̏ꍇ�͏I��
        return;
      }
    }
    
    if (option.startsWith("all") || option.startsWith("{")) {
      // �ʏ�͕ʊ�����̃��N�G�X�g�ŌĂ΂��iappPath�z���̑S�t�@�C������Ԃ��j
      out.println("<pre>");
      Vector ignorePath = new Vector();
      String m = request.getParameter("m");
      String ip = request.getParameter("ip");
      if (ip == null) {
        log_debug("ignorePath=" + DEFAULT_IGNORE_PATH + " (default)");
        ignorePath.add(DEFAULT_IGNORE_PATH);
      } else {
        // ���O�Ώۂ��w�肳��Ă���ꍇ
        log_debug("ignorePath=" + ip);
        String[] ips = ip.split(",");
        for (int i = 0; i < ips.length; ++i) {
          ignorePath.add(ips[i]);
        }
      }
      File rootPath = new File(appPath);
      File[] files = rootPath.listFiles();
      int filecnt = 0;
      for (int i = 0; i < files.length; ++i) {
        String path = getFilePath(appPath, files[i]);
        if (isIgnorePath(ignorePath, path, null)) {
          continue;
        }
        try {
          filecnt += printMBBScanModulesCompareFiles(out, null, null, null, appPath, files[i], null, null, loginInfos, SCAN_LIST, 0, ignorePath, update);
        } catch (Exception e) {
          printError(out, e);
        }
      }
      // DB�̏����Ԃ�
      int dbcnt = 0;
      Connection conn = null;
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        String[] mod_items = DEFAULT_MOD_ITEMS;
        if (m != null && m.trim().length() > 0) {
          log_debug("modules=" + m);
          mod_items = m.split(",");
        }
        dbcnt = printMBBScanModulesListDBModules(out, conn, mod_items);
      } catch (SQLException e) {
        log_debug(e);
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {}
        }
      }
      out.println("</pre>");
      out.flush();
      log_debug("scan: file=" + filecnt + ",db=" + dbcnt);
      return;
    }
    
    File updateNewPath = new File(updatePath, "new");
    File updateDelPath = new File(updatePath, "del");
    // �L�����Z������
    if ("cancel".equals(option)) {
      // �X�V�Ώۂ̃L�����Z��
      if (getfiles != null) {
        for (int i = 0; i < getfiles.length; ++i) {
          File file = new File(updateNewPath, getfiles[i]);
          if (file.exists()) {
            if (DbAccessUtils.deleteFile(file, updateNewPath)) {
              log_debug("CANCEL=" + getfiles[i]);
              String srcPath = getSourcePathFromClass(getfiles[i]);
              if (srcPath != null) {
                File srcFile = new File(updateNewPath, srcPath);
                if (srcFile.exists()) {
                  if (DbAccessUtils.deleteFile(srcFile, updateNewPath)) {
                    log_debug("CANCEL=" + srcPath);
                  }
                }
              }
            } else {
              log_debug("ERROR: CANCEL=" + getfiles[i]);
            }
          } else {
            log_debug("ERROR: CANCEL=" + getfiles[i] + " (not found)");
          }
        }
      }
      // �폜�Ώۂ̃L�����Z��
      if (delfiles != null) {
        for (int i = 0; i < delfiles.length; ++i) {
          String filePath = delfiles[i];
          File file = new File(updateDelPath, filePath);
          if (file.exists()) {
            if (DbAccessUtils.deleteFile(file, updateDelPath)) {
              log_debug("CANCEL=" + delfiles[i]);
            } else {
              log_debug("ERROR: CANCEL=" + delfiles[i]);
            }
          } else {
            log_debug("ERROR: CANCEL=" + delfiles[i] + " (not found)");
          }
        }
      }
      option = "compare";
    }
    // �擾����
    int retrieveCount = 0;
    if ("retrieve".equals(option)) {
      // retrive�i�擾���s�j�̏ꍇ�`�F�b�N�Ώۂ�update�t�H���_�Ƀt�@�C�����擾����
      if (!updatePath.exists()) {
        updatePath.mkdir();
      }
      if (getfiles != null) {
        StringBuffer cookies = new StringBuffer();
        for (int i = 0; i < getfiles.length; ++i) {
          try {
            log_debug("DL=" + getfiles[i]);
            DbAccessUtils.getRemoteFile(new File(updateNewPath, DbAccessUtils.escapeFileName(getfiles[i])), url, stagingProxy, getfiles[i], cookies);
            String srcPath = getSourcePathFromClass(getfiles[i]);
            if (srcPath != null) {
              try {
                DbAccessUtils.getRemoteFile(new File(updateNewPath, DbAccessUtils.escapeFileName(srcPath)), url, stagingProxy, srcPath, cookies);
              } catch (Exception e) {} // �\�[�X���擾�ł��Ȃ��ꍇ�͖���
            }
            retrieveCount++;
          } catch (Exception e) {
            log_debug(e);
          }
        }
      }
      // �폜�Ώۂ��`�F�b�N�����ꍇ�́A���[�J���t�@�C�����폜�Ώۃt�H���_�ɃR�s�[����
      if (delfiles != null) {
        Connection conn = null;
        try {
          conn = getConnection();
          conn.setAutoCommit(false);
          for (int i = 0; i < delfiles.length; ++i) {
            try {
              String delpath = delfiles[i];
              log_debug("DEL=" + delpath);
              if (delpath.startsWith("mbb/") || delpath.startsWith("db/")) {
                if (delpath.startsWith("mbb/")) {
                  // MBB���W���[���̍폜�t�H���_�ւ̃G�N�X�|�[�g
                  String table = delpath.substring(4);
                  int p = table.indexOf("/");
                  String id = table.substring(p + 1);
                  table = table.substring(0, p);
                  exportMBBModule(conn, new File(updateDelPath, DbAccessUtils.escapeFileName(delpath)), table, id);
                } else {
                  // DB�I�u�W�F�N�g�̍폜�t�H���_�ւ̃G�N�X�|�[�g
                  String type = delpath.substring(3);
                  int p = type.indexOf("/");
                  String id = type.substring(p + 1);
                  if (!id.startsWith("\"")) {
                    id = id.toUpperCase();
                  }
                  type = type.substring(0, p).toUpperCase();
                  exportDBObject(conn, new File(updateDelPath, DbAccessUtils.escapeFileName(delpath)), type, id);
                }
              } else {
                DbAccessUtils.copyFile(new File(appPath, DbAccessUtils.escapeFileName(delfiles[i])), new File(updateDelPath, DbAccessUtils.escapeFileName(delfiles[i])));
              }
              retrieveCount++;
            } catch (IOException e) {
              log_debug(e);
            }
          }
        } catch (SQLException e) {
          log_debug(e);
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException e) {}
          }
        }
      }
      if (!remote) {
        option = "compare";
      }
    }
    int updateMBBCount = 0; // �X�V���ꂽMBB��`�̐�
    int updateDBCount = 0; // �X�V���ꂽDB�I�u�W�F�N�g��
    int updateFileCount = 0; // �X�V���ꂽ�t�@�C����
    int updateJSPCount = 0; // �X�V���ꂽJSP�t�@�C����
    int classes = 0; // �X�V�Ώۂ̃N���X�̐�
    int errorDBCount = 0; // INVALID�̐�
    TreeMap retrivedFiles = new TreeMap(); // ���Ƀ��[�J���Ɏ擾�������
    TreeMap scheduledDelFiles = new TreeMap(); // ���[�J���ō폜�\��̏��
    if (updateNewPath.exists()) {
      log_debug("updateNewPath=" + updateNewPath.getAbsolutePath());
      classes += getLocalFileList(retrivedFiles, null, updateNewPath);
      log_debug("retrivedFiles.size=" + retrivedFiles.size());
    }
    if (updateDelPath.exists()) {
      log_debug("updateDelPath=" + updateDelPath.getAbsolutePath());
      classes += getLocalFileList(scheduledDelFiles, null, updateDelPath);
      log_debug("scheduledDelFiles.size=" + scheduledDelFiles.size());
    }
    int srcFiles = 0;
    for (Iterator ite = retrivedFiles.keySet().iterator(); ite.hasNext(); ) {
      String path = (String)ite.next();
      log_debug("retrivedFiles:path="+path);
      if (path.startsWith("src/")) {
        srcFiles++;
      }
    }
    int retrievedFileCount = retrivedFiles.size() + scheduledDelFiles.size() - srcFiles;
    int selectMode = 0; // 0:������ԁA1:�擾����A2:�擾��
    if (retrievedFileCount > 0) {
      if (retrieveCount == retrievedFileCount) {
        // �擾���ォ�擾�ρ��擾���̏ꍇ�i���܂�ɕ��������ɃL�����Z���Ǝ擾�����s���Đ������R�Ɉ�v���Č딻�肳���\���͂���j
        selectMode = 1;
      } else {
        // �擾�ό�ŊԂ��󂢂��ꍇ�i�f�t�H���g�I�����Ȃ��j
        selectMode = 2;
      }
    }
    
    if ("commit_all".equals(option)) {
      Vector agetfiles = DbAccessUtils.getFileList(updateNewPath, null);
      Vector adelfiles = DbAccessUtils.getFileList(updateDelPath, null);
      getfiles = (String[])agetfiles.toArray(new String[]{});
      delfiles = (String[])adelfiles.toArray(new String[]{});
      option = "commit";
    }
    if ("commit".equals(option)) {
      // update���f
      TreeMap commitNewFiles = new TreeMap(); // �C���X�g�[���Ώۂ̎擾�σt�@�C�����X�g
      TreeMap commitDelFiles = new TreeMap(); // �폜�Ώۂ̃t�@�C�����X�g
      if (getfiles != null) {
        for (int i = 0; i < getfiles.length; ++i) {
          String path = getfiles[i];
          File file = new File(updateNewPath, DbAccessUtils.escapeFileName(path));
          if (file.exists()) {
            commitNewFiles.put(path, retrivedFiles.get(path));
          }
        }
      }
      if (delfiles != null) {
        for (int i = 0; i < delfiles.length; ++i) {
          String path = delfiles[i];
          File file = new File(updateDelPath, DbAccessUtils.escapeFileName(path));
          if (file.exists()) {
            commitDelFiles.put(path, scheduledDelFiles.get(path));
          }
        }
      }
      out.println("<pre class=\"text\">");
      if (commitNewFiles.size() > 0 || commitDelFiles.size() > 0) {
        out.println("�I�����ꂽ�擾�σ��W���[�����V�X�e���֔��f���Ă��܂�...");
        out.println("���Ώۃ��W���[���̔��f�ɃV�X�e���ċN�����K�v�ȏꍇ��A�X�V�ɂ�莩���ċN�������ꍇ������܂�");
        out.flush();
        long currentTime = System.currentTimeMillis();
        String timestamp = DbAccessUtils.toTimestampString(currentTime);
        timestamp = timestamp.replaceAll(" ", "_");
        timestamp = timestamp.replaceAll(":", "-");
        if (timestamp.indexOf(".") != -1) {
          timestamp = timestamp.substring(0, timestamp.indexOf("."));
        }
        // �^�C���X�^���v�t�H���_�ֈ�U���ݎ擾�ς̃t�@�C�����o�b�N�A�b�v
        File backupPath = new File(updatePath, timestamp);
        File backupNewPath = new File(backupPath, "update/new");
        File backupDelPath = new File(backupPath, "update/del");
        out.println("���݂̃V�X�e�����W���[����[" + backupPath.getAbsolutePath() + "]�֕ۑ�����܂�");
        out.flush();
        try {
          Connection conn = null;
          try {
            conn = getConnection();
            conn.setAutoCommit(false);
            // �X�V�Ώۂ̌���̂��o�b�N�A�b�v�t�H���_�փG�N�X�|�[�g�E�R�s�[�������Ȃ�
            for (Iterator ite = commitNewFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              boolean copyOk = DbAccessUtils.copyFile(new File(updateNewPath, path), new File(backupNewPath, path));
              String srcPath = getSourcePathFromClass(path);
              if (copyOk && srcPath != null) {
                File srcFile = new File(updateNewPath, srcPath);
                if (srcFile.exists()) {
                  DbAccessUtils.copyFile(srcFile, new File(backupNewPath, srcPath));
                }
              }
              if (path.startsWith("mbb/") || path.startsWith("db/")) {
                // MBB��`�̂܂���DB�I�u�W�F�N�g
                if (path.startsWith("mbb/")) {
                  // MBB���W���[���̃o�b�N�A�b�v�t�H���_�ւ̃G�N�X�|�[�g
                  String table = path.substring(4);
                  int p = table.indexOf("/");
                  String id = table.substring(p + 1);
                  table = table.substring(0, p);
                  try {
                    exportMBBModule(conn, new File(backupPath, DbAccessUtils.escapeFileName(path)), table, id);
                  } catch (Exception e) {
                    log_debug(e);
                  }
                } else {
                  // DB�I�u�W�F�N�g(DDL)�̃o�b�N�A�b�v�t�H���_�ւ̃G�N�X�|�[�g
                  String type = path.substring(3);
                  int p = type.indexOf("/");
                  String id = type.substring(p + 1);
                  if (!id.startsWith("\"")) {
                    id = id.toUpperCase();
                  }
                  type = type.substring(0, p).toUpperCase();
                  try {
                    exportDBObject(conn, new File(backupPath, DbAccessUtils.escapeFileName(path)), type, id);
                  } catch (Exception e) {
                    log_debug(e);
                  }
                }
              } else {
                // ���݂̃t�@�C�����o�b�N�A�b�v�t�H���_�փR�s�[
                File currentFile = new File(appPath, path);
                if (currentFile.exists()) {
                  DbAccessUtils.copyFile(currentFile, new File(backupPath, path));
                  if (srcPath != null) {
                    // Java�\�[�X�����݂���΃o�b�N�A�b�v
                    File srcFile = new File(appPath, srcPath);
                    if (srcFile.exists()) {
                      DbAccessUtils.copyFile(srcFile, new File(backupPath, srcPath));
                    }
                  }
                }
              }
            }
            // �폜�Ώۂ̌���̂��o�b�N�A�b�v�t�H���_�փG�N�X�|�[�g�E�R�s�[�������Ȃ�
            for (Iterator ite = commitDelFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              DbAccessUtils.copyFile(new File(updateDelPath, path), new File(backupDelPath, path));
              if (path.startsWith("mbb/") || path.startsWith("db/")) {
                // ���݂̏�Ԃ��o�b�N�A�b�v�t�H���_�փG�N�X�|�[�g����
                if (path.startsWith("mbb/")) {
                  // MBB���W���[���̃o�b�N�A�b�v�t�H���_�ւ̃G�N�X�|�[�g
                  String table = path.substring(4);
                  int p = table.indexOf("/");
                  String id = table.substring(p + 1);
                  table = table.substring(0, p);
                  try {
                    exportMBBModule(conn, new File(backupPath, DbAccessUtils.escapeFileName(path)), table, id);
                  } catch (Exception e) {
                    log_debug(e);
                  }
                } else if (path.startsWith("db/")) {
                  // DB�I�u�W�F�N�g(DDL)�̃o�b�N�A�b�v�t�H���_�ւ̃G�N�X�|�[�g
                  String type = path.substring(3);
                  int p = type.indexOf("/");
                  String id = type.substring(p + 1);
                  if (!id.startsWith("\"")) {
                    id = id.toUpperCase();
                  }
                  type = type.substring(0, p).toUpperCase();
                  try {
                    exportDBObject(conn, new File(backupPath, DbAccessUtils.escapeFileName(path)), type, id);
                  } catch (Exception e) {
                    log_debug(e);
                  }
                }
              } else {
                // ���݂̃t�@�C�����o�b�N�A�b�v�t�H���_�փR�s�[
                File currentFile = new File(appPath, path);
                if (currentFile.exists()) {
                  DbAccessUtils.copyFile(currentFile, new File(backupPath, path));
                  String srcPath = getSourcePathFromClass(path);
                  if (srcPath != null) {
                    // Java�\�[�X�����݂���΃o�b�N�A�b�v
                    File srcFile = new File(appPath, srcPath);
                    if (srcFile.exists()) {
                      DbAccessUtils.copyFile(srcFile, new File(backupPath, srcPath));
                    }
                  }
                }
              }
            }
            // �X�V�̎��s
            // MBB��`�̂��ɃC���|�[�g����
            for (Iterator ite = commitNewFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              if (path.startsWith("mbb/")) {
                out.println("[" + path + "]���C���X�g�[�����Ă��܂�...");
                out.flush();
                File commitFile = new File(updateNewPath, path);
                // �G�N�X�|�[�g�t�@�C���̃C���|�[�g
                ZipInputStream zis = new ZipInputStream(new FileInputStream(commitFile));
                ZipEntry ze = zis.getNextEntry();
                log_debug("import " + ze.getName());
                String msg = importMCSVData(request, conn, zis, currentTime, IMPORT_NORMAL, loginInfos);
                log_debug(msg);
                zis.close();
                DbAccessUtils.deleteFile(commitFile, updatePath);
                ite.remove();
                updateMBBCount ++;
              }
            }
            for (Iterator ite = commitDelFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              if (path.startsWith("mbb/")) {
                out.println("[" + path + "]���폜���Ă��܂�...");
                out.flush();
                File commitFile = new File(updateDelPath, path);
                // �폜�p�G�N�X�|�[�g�t�@�C���̃C���|�[�g
                ZipInputStream zis = new ZipInputStream(new FileInputStream(commitFile));
                ZipEntry ze = zis.getNextEntry();
                log_debug("import " + ze.getName());
                String msg = importMCSVData(request, conn, zis, currentTime, IMPORT_DELETE, loginInfos);
                log_debug(msg);
                zis.close();
                DbAccessUtils.deleteFile(commitFile, updatePath);
                ite.remove();
                updateMBBCount ++;
              }
            }
            conn.commit();
          } catch (Exception e) {
            // �G���[�̏ꍇ�́A���[���o�b�N���A�G���[�\��
            if (conn != null) {
              try {
                conn.rollback();
              } catch (SQLException se) {}
            }
            if (updateMBBCount > 0) {
              out.println("�G���[���������܂����B�C���|�[�g�͒��f���ꃍ�[���o�b�N�������Ȃ��܂�.");
              out.flush();
            }
            printError(out, e);
            return;
          } finally {
            if (conn != null) {
              try {
                conn.close();
              } catch (SQLException se) {}
              conn = null;
            }
          }
          
          // DB�I�u�W�F�N�g�̃C���|�[�g(�擾����SQL�t�@�C���̎��s)
          try {
            conn = getConnection();
            conn.setAutoCommit(false);
            for (Iterator ite = commitNewFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              if (path.startsWith("db/")) {
                out.println("[" + path + "]���C���X�g�[�����Ă��܂�...");
                out.flush();
                File commitFile = new File(updateNewPath, path);
                // �G�N�X�|�[�g�t�@�C���̃C���|�[�g
                ZipInputStream zis = new ZipInputStream(new FileInputStream(commitFile));
                ZipEntry ze = zis.getNextEntry();
                log_debug("import " + ze.getName());
                int len = (int)ze.getSize();
                if (len <= 0) {
                  len = 1024 * 1024;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[len];
                int size = 0;
                while ((size = zis.read(buffer)) != -1) {
                  if (size > 0) {
                    baos.write(buffer, 0, size);
                  }
                }
                String ddl = new String(baos.toByteArray(), "UTF-8");
                log_debug(ddl);
                zis.close();
                Statement stmt = conn.createStatement();
                try {
                  stmt.execute(ddl);
                  // ���s����DDL�����O�Ɏc��
                  insertSQLLog(ddl, Integer.toString(1), null, null, loginInfos);
                } catch (SQLException e) {
                  // �G���[���o���ꍇ�͍Ď��s
                  if (ddl.startsWith("CREATE INDEX ")) {
                    ddl = ddl.substring(0, ddl.indexOf(")") + 1);
                    String drop = "DROP INDEX " + ddl.substring(13).trim();
                    int p = drop.indexOf(" ", 11);
                    if (p != -1) {
                      drop = drop.substring(0, p);
                      try {
                        stmt.execute(drop);
                        insertSQLLog(drop, Integer.toString(1), null, null, loginInfos);
                      } catch (SQLException e2) {
                      }
                    }
                    try {
                      stmt.execute(ddl);
                      insertSQLLog(ddl, Integer.toString(1), null, null, loginInfos);
                    } catch (SQLException e2) {
                      printError(out, e2);
                    }
                  }
                }
                DbAccessUtils.deleteFile(commitFile, updatePath);
                ite.remove();
                updateDBCount ++;
              }
            }
            for (Iterator ite = commitDelFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              if (path.startsWith("db/")) {
                out.println("[" + path + "]���폜���Ă��܂�...");
                out.flush();
                File commitFile = new File(updateDelPath, path);
                // DROP DDL���쐬���Ď��s
                String type = path.substring(3);
                int p = type.indexOf("/");
                String id = type.substring(p + 1);
                if (!id.startsWith("\"")) {
                  id = id.toUpperCase();
                }
                // TODO: id���t�@�C�����Ƃ��ĕs���ȏꍇ�̑Ή������T�|�[�g
                type = type.substring(0, p).toUpperCase();
                String ddl = "DROP " + type + " " + id;
                Statement stmt = conn.createStatement();
                try {
                  stmt.execute(ddl);
                  // ���s����DDL�����O�Ɏc��
                  insertSQLLog(ddl, Integer.toString(1), null, null, loginInfos);
                } catch (SQLException e) {
                  printError(out, e);
                }
                DbAccessUtils.deleteFile(commitFile, updatePath);
                ite.remove();
                updateDBCount ++;
              }
            }
            conn.commit();
          } catch (Exception e) {
            // �G���[�̏ꍇ�́A���[���o�b�N���A�G���[�\��
            // ���A���AOracle�̏ꍇ��DDL���s���ɋ����R�~�b�g�����̂Œ���
            if (conn != null) {
              try {
                conn.rollback();
              } catch (SQLException se) {}
            }
            if (updateDBCount > 0) {
              if (updateMBBCount > 0) {
                out.println("�G���[���������܂����B�C���|�[�g�͒��f����܂������������̒�`�̂�DDL���s�ɔ����R�~�b�g���ꂽ�\��������܂�.");
                out.flush();
              }
            } else {
              out.println("�G���[���������܂����B�C���|�[�g�͒��f���ꃍ�[���o�b�N�������Ȃ��܂�.");
              out.flush();
            }
            printError(out, e);
            return;
          } finally {
            if (conn != null) {
              try {
                conn.close();
              } catch (SQLException se) {}
              conn = null;
            }
          }
          
          if (updateDBCount > 0 && isOracle(0)) {
            // Oracle�̏ꍇ�ŁADB�ύX���������ꍇ�́AINVALID���ăR���p�C������
            errorDBCount = recompileInvalidDBObjects(loginInfos);
          }
          
          // �t�@�C���֘A�͍Ō�ɂ����Ȃ��i�r���ŃV�X�e���ċN���ɂȂ�\������j
          // �ύX�̃V�X�e���ւ̔��f�i�t�@�C���֘A�j
          try {
            conn = getConnection(); // ���O�o�͂̂��߂�Connection
            conn.setAutoCommit(true); // �r���̃G���[���ł�rollback�����Ȃ����߃I�[�g�R�~�b�g�Ŏ��s
            Vector targetNewFiles = new Vector();
            targetNewFiles.addAll(commitNewFiles.keySet());
            Vector sortedNewFiles = new Vector(); // �R�s�[���ɕ��בւ���
            for (Iterator ite = targetNewFiles.iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              sortedNewFiles.add(path);
            }
            for (Iterator ite = sortedNewFiles.iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              if (!path.startsWith("mbb/") && !path.startsWith("db/")) { // ���̎��_�ł͑S�ď����Ă���͂������O�̂���
                // �X�V�Ώۃt�@�C�����V�X�e���t�H���_�ֈړ�
                out.println("[" + path + "]���C���X�g�[�����Ă��܂�...");
                out.flush();
                File commitFile = new File(updateNewPath, path);
                File currentFile = new File(appPath, path);
                boolean moveOk = DbAccessUtils.moveFile(commitFile, currentFile);
                log_debug("MOVE: " + commitFile.getAbsolutePath() + " -> " + currentFile.getAbsolutePath());
                // �R�s�[��͌��t�@�C�����폜
                if (moveOk) {
                  insertSQLLog("IMPORT \"<UPDATE FILE>\"", path, null, null, loginInfos);
                  // �t�H���_����ɂȂ����ꍇ�͍ċA�I�ɍ폜����
                  try {
                    if (commitFile.getParentFile().list().length == 0) {
                      DbAccessUtils.deleteFile(commitFile.getParentFile(), updatePath);
                    }
                  } catch (Exception e) {
                    log_debug(e);
                  }
                  String srcPath = getSourcePathFromClass(path);
                  if (srcPath != null) {
                    // �\�[�X������ꍇ
                    File commitSrcFile = new File(updateNewPath, srcPath);
                    if (commitSrcFile.exists()) {
                      File currentSrcFile = new File(appPath, srcPath);
                      boolean srcMoveOk = DbAccessUtils.moveFile(commitSrcFile, currentSrcFile);
                      if (srcMoveOk) {
                        // �t�H���_����ɂȂ����ꍇ�͍ċA�I�ɍ폜����
                        try {
                          if (commitSrcFile.getParentFile().list().length == 0) {
                            DbAccessUtils.deleteFile(commitSrcFile.getParentFile(), updatePath);
                          }
                        } catch (Exception e) {
                          log_debug(e);
                        }
                      }
                    }
                  }
                } else {
                  // �G���[�̏ꍇ 
                  insertSQLLog("IMPORT \"<UPDATE FILE>\"", path, "ERROR", null, loginInfos);
                }
                ite.remove();
                if (path.endsWith(".jsp")) {
                  updateJSPCount ++;
                }
                updateFileCount ++;
              }
            }
            // �폜�Ώۃt�@�C���̃V�X�e���ւ̔��f
            for (Iterator ite = commitDelFiles.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              if (!path.startsWith("mbb/") && !path.startsWith("db/")) { // ���̎��_�ł͑S�ď����Ă���͂������O�̂���
                out.println("[" + path + "]���폜���Ă��܂�...");
                out.flush();
                // �폜�Ώۃt�@�C�����V�X�e���t�H���_���폜
                File currentFile = new File(appPath, path);
                if (DbAccessUtils.deleteFile(currentFile, new File(appPath))) {
                  log_debug("DELETE: " + currentFile.getAbsolutePath());
                  insertSQLLog("IMPORT \"<DELETE FILE>\"", path, null, null, loginInfos);
                  // �폜��͌��t�@�C�����폜
                  File commitFile = new File(updateDelPath, path);
                  DbAccessUtils.deleteFile(commitFile, updatePath);
                  String srcPath = getSourcePathFromClass(path);
                  if (srcPath != null) {
                    // �\�[�X������ꍇ�̓\�[�X���폜
                    File currentSrcFile = new File(appPath, srcPath);
                    if (currentSrcFile.exists()) {
                      DbAccessUtils.deleteFile(currentSrcFile, new File(appPath));
                    }
                    File commitSrcFile = new File(updateDelPath, srcPath);
                    if (commitSrcFile.exists()) {
                      DbAccessUtils.deleteFile(commitSrcFile, updatePath);
                    }
                  }
                }
                ite.remove();
                if (path.endsWith(".jsp")) {
                  updateJSPCount ++;
                }
                updateFileCount ++;
              }
            }
            if (updateJSPCount > 0) {
              out.println("��JSP�t�@�C����ύX���܂����B���s���̃L���b�V���ɂ�蔽�f����Ȃ��ꍇ������܂��B");
            }
            if (updateDBCount > 0) {
              out.println("���f�[�^�x�[�X�I�u�W�F�N�g���X�V���܂����B�ˑ�����I�u�W�F�N�g�������ɂȂ�ꍇ������܂��B");
              if (errorDBCount > 0) {
                out.println("(�G���[�ƂȂ��Ă���I�u�W�F�N�g��=" + errorDBCount + ")");
              }
            }
          } catch (SQLException e) {
            log_debug(e);
          } finally {
            if (conn != null) {
              try {
                conn.commit();
              } catch (SQLException se) {}
              try {
                conn.close();
              } catch (SQLException se) {}
            }
            conn = null;
          }
        } catch (IOException e) {
          printError(out, e);
        }
        out.println("�I�����܂���.");
        out.println("�u<a href=\"?tab=Command&command=check%20table\">check table</a>�vDB�������`�F�b�N");
        if (restartCommand != null && restartCommand.trim().length() > 0) {
          out.println("�u<a href=\"?tab=Command&command=restart\">restart</a>�v�T�[�r�X�ċN��");
        }
      } else {
        // �t�@�C�����P���I������Ă��Ȃ��ꍇ
        out.println("�C���X�g�[���Ώۃt�@�C����I�����Ă�������.");
      }
      out.println("</pre>");
      out.flush();
      return;
    }
    
    if ("history".equals(option)) {
      printMBBScanModulesHistory(out, updatePath, option2, findKey);
      return;
    }
    
    // scan compare�̏ꍇ�͈ȉ��̏���
    // ��r��̏����擾����
    TreeMap remoteFiles = new TreeMap();
    TreeMap remoteApplications = new TreeMap();
    int items = 0; // �擾(or�폜)�Ώې�
    String alert = null;
    try {
      if ("compare".equals(option)) {
        // �����[�g�Ɣ�r
        try {
          String charset = "UTF-8";
          URLConnection uc = null;
          if (compareTarget != null) {
            StringBuffer scanUrl = new StringBuffer(); // url�̓p�X���[�h�t�����SURL
            if (url.indexOf("?") == -1) {
              scanUrl.append(url).append("?");
            } else {
              scanUrl.append(url).append("&");
            }
            scanUrl.append("command=scan%20all");
            StringBuffer ignorePath = new StringBuffer();
            for (Iterator ite = ignoreModules.iterator(); ite.hasNext(); ) {
              if (ignorePath.length() > 0) {
                ignorePath.append(",");
              }
              ignorePath.append(ite.next());
            }
            if (ignorePath.length() > 0) {
              scanUrl.append("&ip=").append(encodeURL(ignorePath.toString()));
            }
            if (compareModules.size() > 0) {
              scanUrl.append("&m=");
              for (int i = 0; i < compareModules.size(); ++i) {
                if (i > 0) {
                  scanUrl.append(",");
                }
                scanUrl.append(encodeURL((String)compareModules.get(i)));
              }
            }
            log_debug("connect: " + scanUrl);
            String posturl = scanUrl.toString();
            String params = posturl.substring(posturl.indexOf("?") + 1);
            posturl = posturl.substring(0, posturl.indexOf("?"));
            uc = DbAccessUtils.getURLConnection(posturl, stagingProxy);
            uc.setDoOutput(true);
            uc.addRequestProperty("Accept-Encoding", "gzip,deflate"); // ���k���i������V�����o�[�W�����̏ꍇ���k�ŕԂ�j
            uc.setConnectTimeout(10 * 1000); // 10�b
            uc.setReadTimeout(10 * 60 * 1000); // 10��
            OutputStream os = uc.getOutputStream();
            os.write(params.getBytes(charset));
            os.flush();
            os.close();
          }
          InputStream is = null;
          if (uc != null) {
            log_debug("Content-Encoding: " + uc.getContentEncoding());
            if ("gzip".equals(uc.getContentEncoding())) {
              is = new GZIPInputStream(uc.getInputStream());
            } else {
              is = uc.getInputStream();
            }
          } else {
            is = new ByteArrayInputStream(new byte[]{});
          }
          BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
          String line = null;
          while ((line = br.readLine()) != null) {
            if (line.startsWith("FILE\t")
                || line.startsWith("A\t") // A\t�͋��o�[�W�����݊��p
                ) {
              String[] fileEntry = line.split("\t", -1); // FILE\tID\tMD5SUM\tTIMESTAMP
              if (fileEntry.length == 4) {
                remoteFiles.put(fileEntry[1], new String[]{fileEntry[2], fileEntry[3]});
              }
            } else if (line.startsWith("MBB\t")) {
              String[] appEntry = line.split("\t", -1); // MBB\tID\tMD5SUM\tTIMESTAMP\tPACKAGE\tNAME\tUPDATEINFOS
              if (appEntry.length > 6) {
                // ID,{MD5SUM,TIMESTAMP,PACKAGE,NAME,UPDATEINFOS}
                remoteApplications.put(appEntry[1], new String[]{appEntry[2], appEntry[3], appEntry[4], appEntry[5], appEntry[6]});
              } else if (appEntry.length > 5) {
                remoteApplications.put(appEntry[1], new String[]{appEntry[2], appEntry[3], appEntry[4], appEntry[5], ""});
              }
            } else if (line.indexOf("value=\"login\"") != -1) {
              // ���O�C����ʂ��\�����ꂽ�ꍇ�i�p�X���[�h�ݒ肠��̏ꍇ�j
              alert = "�F�؃G���[";
            }
          }
        } catch (Exception e) {
          log_debug(e);
          printError(out, e);
          return; // �G���[�����������ꍇ�́A�I��
        }
      }
      if (alert != null && alert.trim().length() > 0) {
        // �G���[�����������ꍇ
        out.println("<script language=\"javascript\">");
        if (compareTarget != null) {
          out.println("alert('" + alert + "[" + compareTarget + "]');");
        } else {
          out.println("alert('" + alert + "');");
        }
        out.println("</script>");
        out.flush();
        return;
      }
      log_debug("remoteFiles=" + remoteFiles.size());
      log_debug("remoteApplications=" + remoteApplications.size());
      if (compareTarget != null) {
        if (remoteFiles.size() == 0 && retrievedFileCount == 0) {
          // �����[�g��������擾�ł��Ȃ������ꍇ�͏I��
          out.print("<span class=\"text\" style=\"color:#ff0000;\">");
          out.print("�ڑ��G���[�F�X�V��񂪎擾�ł��܂���ł����B(" + DbAccessUtils.escapeHTML(url) + ")");
          out.println("</span><br>");
          return;
        }
      }
      // ��r�\��
      if (selectMode > 0) {
        // ���Ɏ擾�ς݃t�@�C��������
        out.print("<span class=\"text\" style=\"color:#ff0000;\">");
        out.print("�C���X�g�[��������...");
        out.println("</span><br>");
      }
      out.println("<table id=\"comparelist\">");
      // �^�C�g���s
      out.print("<tr style=\"background-color:" + TABLE_HEADER_COLOR + ";\">");
      out.print("<td><input type=\"checkbox\" onclick=\"checkAll('file', this.checked);checkAll('delfile', this.checked);\">�S��</td>");
      out.print("<td>");
      out.print("�t�@�C����");
      out.print("</td>");
      out.print("<td>");
      if (compareTarget != null) {
        out.print(compareTarget);
      }
      out.print("</td>");
      out.print("<td>");
      out.print("���[�J��");
      out.print("</td>");
      out.print("<td>");
      out.print("���e��r");
      out.print("</td>");
      out.println("</tr>");
      out.flush();
      
      if (remoteFiles.size() > 0) {
        // ���[�J���t�@�C����remoteFiles���r
        File rootPath = new File(appPath);
        File[] files = rootPath.listFiles();
        for (int i = 0; i < files.length; ++i) {
          String path = getFilePath(appPath, files[i]);
          if (isIgnorePath(path, null)) {
            continue;
          }
          int mode = SCAN_COMMIT;
          int cnt = printMBBScanModulesCompareFiles(out, remoteFiles, retrivedFiles, scheduledDelFiles, appPath, files[i], null, null, loginInfos, mode, selectMode, ignoreModules, update);
          items += cnt;
        }
        // �����[�g�̂ݑ��݂���Ώ�(�V�K)��\��
        for (Iterator ite = remoteFiles.keySet().iterator(); ite.hasNext(); ) {
          String path = (String)ite.next();
          if (isIgnorePath(path, null)) {
            continue;
          }
          printMBBScanModulesNewFiles(out, retrivedFiles, path, (String[])remoteFiles.get(path), loginInfos, selectMode, update);
          items ++;
        }
      }
      if (remoteApplications.size() > 0) {
        // DB��`���W���[���̔�r�A�����[�g�����牽���������Ă��Ȃ������ꍇ�͔�r����Ȃ��i���[�J����DB���W���[���폜�ΏۊO�j
        Connection conn = null;
        try {
          conn = getConnection();
          conn.setAutoCommit(false);
          String[] mod_items = DEFAULT_MOD_ITEMS;
          for (int i = 0; i < mod_items.length; ++i) {
            if (compareModules.contains(mod_items[i])) {
              int cnt = printMBBScanModulesCompareDB(out, conn, mod_items[i], remoteApplications, retrivedFiles, scheduledDelFiles, selectMode, ignoreModules, update);
              log_debug(mod_items[i] + "=" + cnt);
              items += cnt;
            }
          }
          // �����[�g�̂ݑ��݂���Ώ�(�V�K)��\��
          for (Iterator ite = remoteApplications.keySet().iterator(); ite.hasNext(); ) {
            String path = (String)ite.next();
            if (isIgnorePath(path, "mbb/,db/")) {
              continue;
            }
            String[] modInfos = (String[])remoteApplications.get(path);
            printMBBScanModulesNewDBModule(out, path, retrivedFiles, selectMode, modInfos, update);
            items++;
          }
        } catch (SQLException e) {
          log_debug(e);
          printError(out, e);
        } finally {
          if (conn != null) {
            try {
              conn.close();
            } catch (SQLException e) {}
          }
        }
      }
      // update�t�H���_�Ɏ��c���ꂽ�c�[����(�t�ڑ��Ώۂ̏ꍇ����)
      log_debug("updateNewFiles(dead).size()=" + retrivedFiles.size());
      if (retrivedFiles.size() > 0) {
        for (Iterator ite = retrivedFiles.keySet().iterator(); ite.hasNext(); ) {
          String path = (String)ite.next();
          out.print("<tr><td>");
          out.print("<input type=\"checkbox\" name=\"file\" value=\"" + escapeInputValue(path) + "\"");
          out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\"");
          out.print(">");
          out.print("</td>");
          // �t�@�C����
          out.print("<td>");
          out.print("<font color=\"");
          out.print(DIFF_OLDER_COLOR);
          out.print("\"");
          out.print(">");
          out.print(path);
          out.print("</font>");
          out.print("</td>");
          // �����[�g�^�C���X�^���v
          out.print("<td>");
          out.print("</td>");
          // ���[�J���^�C���X�^���v
          out.print("<td>");
          out.print("<font color=\"" + DIFF_OLDER_COLOR + "\">");
          File file = new File(updateNewPath, path);
          long lastModified = file.lastModified();
          String localts = DbAccessUtils.toTimestampString(lastModified);
          out.print(localts);
          out.print("</font>");
          out.print("</td>");
          // �R�����g
          out.print("<td>");
          out.print("<span title=\"�擾��A�ڑ����Ŋ��ɍ폜���ꂽ�ꍇ�́A�擾�L�����Z���������Ȃ��Ă�������\">");
          out.print("(���̑��\���)");
          out.print("</span>");
          out.print("</td>");
          out.println("</tr>");
          out.flush();
        }
      }
      log_debug("updateDelFiles(dead).size()=" + scheduledDelFiles.size());
      if (scheduledDelFiles.size() > 0) {
        for (Iterator ite = scheduledDelFiles.keySet().iterator(); ite.hasNext(); ) {
          String path = (String)ite.next();
          out.print("<tr><td>");
          out.print("<input type=\"checkbox\" name=\"delfile\" value=\"" + escapeInputValue(path) + "\"");
          out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\"");
          out.print(">");
          out.print("</td>");
          // �t�@�C����
          out.print("<td>");
          out.print("<font color=\"");
          out.print(DIFF_OLDER_COLOR);
          out.print("\"");
          out.print(">");
          out.print(path);
          out.print("</font>");
          out.print("</td>");
          // �����[�g�^�C���X�^���v
          out.print("<td>");
          out.print("</td>");
          // ���[�J���^�C���X�^���v
          out.print("<td>");
          out.print("<font color=\"" + DIFF_OLDER_COLOR + "\">");
          File file = new File(updateDelPath, path);
          long lastModified = file.lastModified();
          String localts = DbAccessUtils.toTimestampString(lastModified);
          out.print(localts);
          out.print("</font>");
          out.print("</td>");
          // �R�����g
          out.print("<td>");
          out.print("(���̑��폜�\���)");
          out.print("</td>");
          out.println("</tr>");
          out.flush();
        }
      }
    } catch (Exception e) {
      log_debug(e);
      printError(out, e);
      out.flush();
      return;
    } finally {
      out.println("</table>");
      if (items > 0) {
        out.println("<input type=\"button\" id=\"retrievebtn\" value=\"�擾\" onclick=\"if(confirm('�I���t�@�C����update�t�H���_�֎擾���Č������܂��B��낵���ł����H'))doCommand('MBB','command','scan retrieve');return false;\">");
      }
      if (retrievedFileCount > 0) {
        String rebootmsg = "";
        if (classes > 0) { // .class,.jar���܂܂��ꍇ
          rebootmsg = "(�V�X�e�����ċN������܂�)";
        }
        out.print("<input type=\"button\" id=\"commitbtn\" value=\"�C���X�g�[��\" onclick=\"if(confirm('�I�������擾�σt�@�C�����C���X�g�[�����܂��B��낵���ł����H" + rebootmsg + "'))doCommand('MBB','command','scan commit');return false;\"");
        if (selectMode != 1) { // 1(�擾����)�̂݃f�t�H���g�L��
          out.print(" disabled");
        }
        out.println(">");
        out.print("<input type=\"button\" id=\"cancelbtn\" value=\"�擾�L�����Z��\" onclick=\"if(confirm('�I�������擾�σt�@�C�����L�����Z�����Č������܂��B��낵���ł����H'))doCommand('MBB','command','scan cancel');return false;\"");
        if (selectMode != 1) { // 1(�擾����)�̂݃f�t�H���g�L��
          out.print(" disabled");
        }
        out.println(">");
      }
      if (items > 0) {
        if (deployReportFileName != null && deployReportFileName.trim().length() > 0
            && ExcelManager.isEnabled()) {
          File deployReportFile = new File(deployReportFileName);
          if (deployReportFile.exists()) {
            out.println("<input type=\"button\" value=\"Excel\" onclick=\"doExcelReport(document.forms['downloadform'],document.getElementById('comparelist').innerHTML);return false;\">");
          }
        }
        if (ExcelManager.isEnabled()) {
          //TODO:TEST �ŏI�I�ɂ͏����e���v���[�g��o�^���Ă�����g�p����悤�ɂ�����
          out.println("<input type=\"button\" value=\"Excel\" onclick=\"doExcelReport(document.forms['downloadform'],document.getElementById('comparelist').innerHTML);return false;\">");
        }
      }
      // �\��ς݂�I��
      if (selectMode > 0) {
        out.println("<span class=\"text\"><a href=\"javascript:void(0);\" onclick=\"checkAllClass('file','scheduled');checkAllClass('delfile','scheduled');\">�\��ς�I��</a></span>");
      }
      // ���O�Ώۂ�\��
      StringBuffer ip = new StringBuffer();
      ip.append("<span class=\"text\"><a href=\"javascript:void(0);\" onclick=\"document.getElementById('ignorelist').style.display='';this.style.display='none';\">���O�Ώۂ�\��...</a><div class=\"text\" id=\"ignorelist\" style=\"display:none;\">");
      for (int i = 0; i < DEFAULT_MOD_ITEMS.length; ++i) {
        if (!compareModules.contains(DEFAULT_MOD_ITEMS[i])) {
          if (ip.length() > 0) {
            ip.append("<br>\n");
          }
          ip.append(DEFAULT_MOD_ITEMS[i]);
        }
      }
      for (Iterator ite = ignoreModules.iterator(); ite.hasNext(); ) {
        if (ip.length() > 0) {
          ip.append("<br>\n");
        }
        String path = (String)ite.next();
        ip.append(path.replaceAll("%2c", ","));
      }
      ip.append("</div>");
      ip.append("</span>");
      out.println(ip.toString());
      log_debug("ignoreModules.size()=" + ignoreModules.size());
      out.flush();
    }
  }
  /**
   * class�t�@�C���̃p�X���A�ΏۂƂȂ�\�[�X�̃p�X���擾����
   * �ΏۊO�t�@�C���܂��́Asrc�t�H���_�������ꍇ��null��Ԃ�
   * @param classPath
   * @return
   */
  private String getSourcePathFromClass(String classPath) {
    if (!srcExists) {
      return null;
    }
    if (classPath != null && classPath.startsWith("WEB-INF/classes/") && classPath.endsWith(".class")
        && classPath.indexOf("$") == -1) {
      return "src/" + classPath.substring(16, classPath.length() - 6) + ".java";
    }
    return null;
  }
  
  /**
   * �C���X�g�[�������̌���
   * @param out
   * @param updatePath
   * @param option2
   * @param findKey
   */
  private void printMBBScanModulesHistory(PrintWriter out, File updatePath, String option2, String findKey) {
    // �����̏Ɖ�
    int pageSize = 100;
    int page = 1;
    int line = 0;
    if (option2 != null && option2.length() < 5) {
      // �ő�pageSize�s�܂ł����\������Ȃ����Ascan history 1
      // �̂悤�Ɏw�肷��Ƃ���ƁA���̐�̉ߋ������o��(�ŐV=0)
      try {
        page = Integer.parseInt(option2);
        option2 = null;
      } catch (Exception e) {}
    }
    if (option2 == null) {
      // ������t�ꗗ�Ɖ�
      String[] histories = updatePath.list();
      Arrays.sort(histories);
      int start = histories.length - 1;
      while (start >= 0 && histories[start].length() < 10) {
        // ���̏��Ȃ��t�H���_���X�L�b�v(new, del�̃t�H���_�̓\�[�g�ōŌ�ɗ���Ƒz��B���̃t�H���_������ƃo�O��)
        start --;
      }
      out.println("<br>");
      out.println("<table id=\"historylist\">");
      out.print("<tr><td>�C���X�g�[������");
      out.println("</td></tr>");
      int i = start - (pageSize * (page - 1));
      for (; i >= 0; --i) {
        out.println("<tr><td>");
        out.println("<a href=\"?command=scan%20history%20" + histories[i] + "\">");
        out.println(histories[i].replaceAll("_", " "));
        out.println("</a>");
        out.println("</td></tr>");
        line ++;
        if (line >= pageSize) {
          break;
        }
      }
      int totalPages = start / pageSize + 1;
      if (totalPages > 1) {
        // �����y�[�W�ɂȂ�ꍇ�́A�y�[�W����\������
        out.println("<tr><td>");
        out.println("(" + page + "/" + totalPages + ")");
        if (i > 0) {
          out.println("<a href=\"?command=scan%20history%20" + (page + 1) + "\">���y�[�W</a>");
        }
        out.println("</td></tr>");
      }
      out.println("</table>");
      out.flush();
    } else {
      int p = option2.indexOf(" ");
      if (p != -1) {
        // �����t�@�C���w�肪�������ꍇ�́A���t�o�b�N�A�b�v����ڑ���֏����t�@�C���Ƃ��ăR�s�[
        String restorePath = option2.substring(p + 1); // �p�X
        option2 = option2.substring(0, p); // ���t�t�H���_
        File restoreFromFile = new File(new File(updatePath, option2), restorePath);
        if (restoreFromFile.exists()) {
          // �����Ώۃt�@�C�������݂���ꍇ
          File newPath = new File(updatePath, "new");
          File restoreToFile = new File(newPath, restorePath);
          log_debug("restoreFromFile="+restoreFromFile.getAbsolutePath());
          log_debug("restoreToFile="+restoreToFile.getAbsolutePath());
          try {
            DbAccessUtils.copyFile(restoreFromFile, restoreToFile);
          } catch (Exception e) {
            log_debug(e);
          }
          String srcPath = getSourcePathFromClass(restorePath);
          if (srcPath != null) {
            // �\�[�X������ꍇ�̓\�[�X�������ɃR�s�[
            File srcFile = new File(new File(updatePath, option2), srcPath);
            if (srcFile.exists()) {
              File restoreToSrcFile = new File(newPath, srcPath);
              try {
                DbAccessUtils.copyFile(srcFile, restoreToSrcFile);
              } catch (Exception e) {
                log_debug(e);
              }
            }
          }
        } else {
          // �����Ώۃt�@�C�������݂��Ȃ��ꍇ�i�V�K�ڑ��̖߂����폜�j
          if (restorePath.startsWith("mbb/") || restorePath.startsWith("db/")) {
            // TODO ���Ή�
          } else {
            File currentFile = new File(appPath, restorePath);
            if (currentFile.exists()) {
              File delPath = new File(updatePath, "del");
              File restoreToFile = new File(delPath, restorePath);
              log_debug("currentFile="+currentFile.getAbsolutePath());
              log_debug("restoreToFile="+restoreToFile.getAbsolutePath());
              try {
                DbAccessUtils.copyFile(currentFile, restoreToFile);
              } catch (Exception e) {
                log_debug(e);
              }
              String srcPath = getSourcePathFromClass(restorePath);
              if (srcPath != null) {
                File srcFile = new File(appPath, srcPath);
                if (srcFile.exists()) {
                  File restoreToSrcFile = new File(delPath, srcPath);
                  try {
                    DbAccessUtils.copyFile(srcFile, restoreToSrcFile);
                  } catch (Exception e) {
                    log_debug(e);
                  }
                }
              }
            }
          }
        }
      }
      // ���𖾍׏Ɖ�
      Connection conn = null;
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        if (findKey.length() > 0) {
          // ID�ɂ�闚������
          String[] histories = updatePath.list();
          Arrays.sort(histories);
          int start = histories.length - 1;
          while (start >= 0 && histories[start].length() < 10) {
            // new, del�̃t�H���_�̓\�[�g�ōŌ�ɗ���Ƒz��B���̃t�H���_������ƃo�O��
            start --;
          }
          out.println("<br>");
          out.println("<table id=\"historylist\">");
          out.print("<tr><td colspan=\"4\">�C���X�g�[����������(�Ώ�=" + findKey + ")");
          out.println("</td></tr>");
          out.println("<tr><th>�C���X�g�[������</th><th>����</th><th style=\"width:400px\">�Ώ�</th><th>�X�V�^�C���X�^���v</th><th>�X�V�O�^�C���X�^���v</th><th>(���݃^�C���X�^���v)</th></tr>");
          for (int i = start; i >= 0; --i) {
            // �V�����t�H���_���珇�Ɍ���
            File targetDate = new File(updatePath, histories[i]); // ���t�t�H���_
            File target = new File(targetDate, "update");
            if (target.isDirectory()) { // ��{�I�ɂ͂���͂������O�̂���
              // �V�K�E�X�V����������
              File update = new File(target, "new");
              if (update.exists()) {
                TreeMap fileList = new TreeMap();
                getLocalFileList(fileList, update.getAbsolutePath(), update);
                for (Iterator ite = fileList.keySet().iterator(); ite.hasNext(); ) {
                  String path = (String)ite.next();
                  boolean found = false;
                  if (new File(path).getName().toUpperCase().indexOf(findKey.toUpperCase()) != -1) {
                    found = true;
                  }
                  if (found) {
                    // �q�b�g
                    out.print("<tr><td>");
                    out.println("<a href=\"?command=scan%20history%20" + histories[i] + "\">");
                    out.print(histories[i].replaceAll("_", " "));
                    out.print("</a>");
                    out.print("</td>");
                    out.print("<td>");
                    File oldFile = new File(targetDate, path);
                    if (oldFile.exists()) {
                      out.print("�X�V");
                    } else {
                      out.print("�V�K");
                    }
                    out.print("</td>");
                    long currentTs = -1L; // ���݂̃^�C���X�^���v
                    boolean current = false;
                    if (path.startsWith("mbb/") || path.startsWith("db/")) {
                      // ��`��
                      currentTs = getMBBLastModified(conn, path);
                    } else {
                      // �t�@�C���n
                      File currentFile = new File(appPath, path);
                      if (currentFile.exists()) {
                        currentTs = currentFile.lastModified();
                      }
                    }
                    String[] data = (String[])fileList.get(path);
                    String ts1 = null;
                    long lts1 = -1;
                    if (data != null && data.length >= 2) {
                      ts1 = data[1];
                      try {
                        lts1 = Timestamp.valueOf(ts1).getTime();
                      } catch (Exception e) {}
                    }
                    // �t�@�C����
                    if (DbAccessUtils.compareTimestamp(currentTs, lts1, 2000) == 0) {
                      // �ŐV�̏ꍇ�͑����ŕ\��
                      out.print("<td><b>" + path + "</b></td>");
                      current = true;
                    } else {
                      out.print("<td>" + path + "</td>");
                    }
                    // �X�V�^�C���X�^���v
                    out.print("<td>");
                    if (ts1 != null) {
                      boolean comparable = false;
                      if (!current && currentTs != -1) {
                        // ���݃��W���[���ƃ^�C���X�^���v���قȂ邩���݂����݂���ꍇ�ɔ�r�\��
                        if (isComparable(path)) {
                          out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURL(path) + ":" + encodeURL(histories[i] + "/update/new") + "\" target=\"_blank\">");
                          comparable = true;
                        } else {
                          String srcPath = getSourcePathFromClass(path);
                          if (srcPath != null && new File(appPath, srcPath).exists()) {
                            out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURL(srcPath) + ":" + encodeURL(histories[i] + "/update/new") + "\" target=\"_blank\">");
                            comparable = true;
                          }
                        }
                      }
                      out.print(ts1);
                      if (comparable) {
                        out.print("</a>");
                      }
                    }
                    out.print("</td>");
                    // �X�V�O�^�C���X�^���v
                    out.print("<td>");
                    if (oldFile.exists()) {
                      boolean comparable = false;
                      String timestamp = DbAccessUtils.toTimestampString(oldFile.lastModified());
                      if (currentTs != -1) {
                        // ���݃��W���[�������݂���ꍇ�ɔ�r�\��
                        if (isComparable(path)) {
                          out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURL(path) + ":" + encodeURL(histories[i]) + "\" target=\"_blank\">");
                          comparable = true;
                        } else {
                          String srcPath = getSourcePathFromClass(path);
                          if (srcPath != null && new File(appPath, srcPath).exists()) {
                            out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURL(srcPath) + ":" + encodeURL(histories[i]) + "\" target=\"_blank\">");
                            comparable = true;
                          }
                        }
                      }
                      out.print(timestamp);
                      if (comparable) {
                        out.print("</a>");
                      }
                    }
                    out.print("</td>");
                    // ���݂̃^�C���X�^���v
                    out.print("<td>");
                    if (currentTs != -1) {
                      String timestamp = DbAccessUtils.toTimestampString(currentTs);
                      if (current) {
                        out.print("<b>");
                      }
                      out.print(timestamp);
                      if (current) {
                        out.print("</b>");
                      }
                    }
                    out.print("</td>");
                    out.println("</tr>");
                    line ++;
                    if (line >= pageSize) {
                      break;
                    }
                  }
                }
              }
              // �폜�̗���������
              File delete = new File(target, "del");
              if (delete.exists()) {
                TreeMap fileList = new TreeMap();
                getLocalFileList(fileList, delete.getAbsolutePath(), delete);
                for (Iterator ite = fileList.keySet().iterator(); ite.hasNext(); ) {
                  String path = (String)ite.next();
                  boolean found = false;
                  if (new File(path).getName().toUpperCase().indexOf(findKey.toUpperCase()) != -1) {
                    found = true;
                  }
                  if (found) {
                    // �q�b�g
                    out.print("<tr><td>");
                    out.println("<a href=\"?command=scan%20history%20" + histories[i] + "\">");
                    out.print(histories[i].replaceAll("_", " "));
                    out.print("</a>");
                    out.print("</td>");
                    out.print("<td>");
                    out.print("�폜");
                    out.print("</td>");
                    long currentTs = -1L; // ���݂̃^�C���X�^���v�i�ʏ�͑��݂��Ȃ��E�ăC���X�g�[���̏ꍇ�j
                    if (path.startsWith("mbb/") || path.startsWith("db/")) {
                      // ��`��
                      currentTs = getMBBLastModified(conn, path);
                    } else {
                      // �t�@�C���n
                      File currentFile = new File(appPath, path);
                      if (currentFile.exists()) {
                        currentTs = currentFile.lastModified();
                      }
                    }
                    String ts1 = null;
                    String[] data = (String[])fileList.get(path);
                    if (data != null && data.length >= 2) {
                      ts1 = data[1];
                    }
                    out.print("<td>" + path + "</td>");
                    // �X�V�^�C���X�^���v
                    out.print("<td>");
                    if (ts1 != null) {
                      out.print(ts1);
                    }
                    out.print("</td>");
                    // �X�V�O�^�C���X�^���v
                    out.print("<td>");
                    File oldFile = new File(targetDate, path);
                    if (oldFile.exists()) {
                      String timestamp = DbAccessUtils.toTimestampString(oldFile.lastModified());
                      out.print(timestamp);
                    }
                    out.print("</td>");
                    // ���݂̃^�C���X�^���v
                    out.print("<td>");
                    if (currentTs != -1) {
                      String timestamp = DbAccessUtils.toTimestampString(currentTs);
                      out.print(timestamp);
                    }
                    out.print("</td>");
                    out.println("</tr>");
                    line ++;
                    if (line >= pageSize) {
                      break;
                    }
                  }
                }
              }
            }
          }
          out.println("</table>");
          out.flush();
        } else {
          // �������w�肳�ꂽ�ꍇ�́A���̓����t�H���_�̏��i�C���X�g�[�����s�P�ʁj��S�ĕ\��
          File targetDate = new File(updatePath, option2);
          File target = new File(targetDate, "update");
          if (target.isDirectory()) {
            out.println("<br>");
            out.println("<table id=\"historylist\">");
            out.print("<tr><td colspan=\"4\">�C���X�g�[������(" + option2.replaceAll("_", " ") + ")");
            out.println("</td></tr>");
            out.println("<tr><th>����</th><th style=\"width:400px\" title=\"�����͌��݃o�[�W����\">�Ώ�</th><th>�X�V�^�C���X�^���v</th><th>�X�V�O�^�C���X�^���v</th><th>(���݃^�C���X�^���v)</th><th title=\"�X�V�O�o�[�W�������擾���܂�\">����</th></tr>");
            File update = new File(target, "new");
            if (update.exists()) {
              TreeMap fileList = new TreeMap();
              getLocalFileList(fileList, update.getAbsolutePath(), update);
              for (Iterator ite = fileList.keySet().iterator(); ite.hasNext(); ) {
                String path = (String)ite.next();
                if (path.startsWith("src/")) {
                  continue;
                }
                String[] data = (String[])fileList.get(path);
                out.print("<tr><td>");
                File oldFile = new File(targetDate, path);
                if (oldFile.exists()) {
                  out.print("�X�V");
                } else {
                  out.print("�V�K");
                }
                out.print("</td>");
                long currentTs = -1L; // ���݂̃^�C���X�^���v
                boolean current = false;
                if (path.startsWith("mbb/") || path.startsWith("db/")) {
                  // ��`��
                  currentTs = getMBBLastModified(conn, path);
                } else {
                  // �t�@�C���n
                  File currentFile = new File(appPath, path);
                  if (currentFile.exists()) {
                    currentTs = currentFile.lastModified();
                  }
                }
                String ts1 = null;
                long lts1 = -1;
                if (data != null && data.length >= 2) {
                  ts1 = data[1];
                  try {
                    lts1 = Timestamp.valueOf(ts1).getTime();
                  } catch (Exception e) {}
                }
                if (DbAccessUtils.compareTimestamp(currentTs, lts1, 2000) == 0) {
                  out.print("<td><b>" + path + "</b></td>");
                  current = true;
                } else {
                  out.print("<td>" + path + "</td>");
                }
                // �X�V�^�C���X�^���v
                out.print("<td>");
                if (ts1 != null) {
                  boolean comparable = false;
                  if (!current && currentTs != -1) {
                    // ���݃��W���[���ƃ^�C���X�^���v���قȂ邩���݂����݂���ꍇ�ɔ�r�\��
                    if (isComparable(path)) {
                      out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + ":" + encodeURL(option2 + "/update/new") + "\" target=\"_blank\">");
                      comparable = true;
                    } else {
                      String srcPath = getSourcePathFromClass(path);
                      if (srcPath != null && new File(appPath, srcPath).exists()) {
                        out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(srcPath) + ":" + encodeURL(option2 + "/update/new") + "\" target=\"_blank\">");
                        comparable = true;
                      }
                    }
                  }
                  out.print(ts1);
                  if (comparable) {
                    out.print("</a>");
                  }
                }
                out.print("</td>");
                // �X�V�O�^�C���X�^���v
                out.print("<td>");
                if (oldFile.exists()) {
                  boolean comparable = false;
                  String timestamp = DbAccessUtils.toTimestampString(oldFile.lastModified());
                  if (currentTs != -1) {
                    // ���݃��W���[�������݂���ꍇ�ɔ�r�\��
                    if (isComparable(path)) {
                      out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + ":" + encodeURL(option2) + "\" target=\"_blank\">");
                      comparable = true;
                    } else {
                      String srcPath = getSourcePathFromClass(path);
                      if (srcPath != null && new File(appPath, srcPath).exists()) {
                        out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(srcPath) + ":" + encodeURL(option2) + "\" target=\"_blank\">");
                        comparable = true;
                      }
                    }
                  }
                  out.print(timestamp);
                  if (comparable) {
                    out.print("</a>");
                  }
                }
                out.print("</td>");
                // ���݂̃^�C���X�^���v
                out.print("<td>");
                if (currentTs != -1) {
                  String timestamp = DbAccessUtils.toTimestampString(currentTs);
                  if (current) {
                    out.print("<b>");
                  }
                  out.print(timestamp);
                  if (current) {
                    out.print("</b>");
                  }
                }
                out.print("</td>");
                out.print("<td>");
                File restoreFile = new File(new File(updatePath, "new"), path);
                File restoreDelFile = new File(new File(updatePath, "del"), path); // TODO:���̔��f����new��del�̗���������ꍇ�A�ǂ��炪���s����邩�s��
                if (restoreFile.exists() || restoreDelFile.exists()) {
                  out.println("(�擾��)"); // TODO:�^�C���X�^���v���r���āA�擾�ς͕����Ȃ̂��V�K�Ȃ̂��킩��悤�ɂ�����
                } else {
                  if (!oldFile.exists() && (path.startsWith("mbb/") || path.startsWith("db/"))) {
                    // TODO MBB/DB���W���[���̐V�K�ڑ��̕����͖��Ή�
                  } else {
                    out.print("<input type=\"button\" id=\"restorebtn\" value=\"�擾\" onclick=\"if(confirm('�X�V�O�o�[�W�������擾���܂��B��낵���ł���?'))doCommand('MBB','command','scan history " + option2 + " " + path + "');return false;\" title=\"�X�V�O�o�[�W�����𕜌����܂�\">");
                  }
                }
                out.print("</td>");
                out.println("</tr>");
                out.flush();
                line ++;
              }
            }
          }
          File delete = new File(target, "del");
          if (delete.exists()) {
            TreeMap fileList = new TreeMap();
            getLocalFileList(fileList, delete.getAbsolutePath(), delete);
            for (Iterator ite = fileList.keySet().iterator(); ite.hasNext(); ) {
              String path = (String)ite.next();
              out.print("<tr><td>");
              out.print("�폜");
              out.print("</td>");
              out.print("<td>" + path + "</td>");
              out.print("<td></td>"); // �X�V�^�C���X�^���v�͖���
              out.print("<td>");
              File oldFile = new File(targetDate, path);
              String oldTimestamp = null;
              if (oldFile.exists()) {
                oldTimestamp = DbAccessUtils.toTimestampString(oldFile.lastModified());
                out.print(oldTimestamp);
              }
              out.print("</td>");
              long currentTs = -1L; // ���݂̃^�C���X�^���v
              boolean current = false;
              if (path.startsWith("mbb/") || path.startsWith("db/")) {
                // ��`��
                currentTs = getMBBLastModified(conn, path);
              } else {
                // �t�@�C���n
                File currentFile = new File(appPath, path);
                if (currentFile.exists()) {
                  currentTs = currentFile.lastModified();
                }
              }
              if (currentTs != -1) {
                // �폜�㕜�����ꂽ�^�C���X�^���v
                String ts1 = DbAccessUtils.toTimestampString(currentTs);
                long lts1 = -1;
                try {
                  lts1 = Timestamp.valueOf(ts1).getTime();
                } catch (Exception e) {
                  log_debug(e);
                }
                if (DbAccessUtils.compareTimestamp(Timestamp.valueOf(oldTimestamp).getTime(), lts1, 2000) == 0) {
                  // �폜�O�ƃ��[�J������v
                  out.print("<td><b>");
                  out.print(ts1);
                  out.print("</b></td>");
                  current = true;
                } else {
                  // �s��v
                  out.print("<td>");
                  out.print(ts1);
                  out.print("</td>");
                }
              } else {
                // �ʏ�͍폜��̓��[�J���ɑ��݂��Ȃ�
                out.print("<td></td>");
              }
              out.print("<td>");
              File restoreFile = new File(new File(updatePath, "new"), path);
              if (restoreFile.exists()) {
                out.println("(�擾��)"); // TODO:�^�C���X�^���v���r���āA�擾�ς͕����Ȃ̂��V�K�Ȃ̂��킩��悤�ɂ�����
              } else {
                if (!current) {
                  out.print("<input type=\"button\" id=\"restorebtn\" value=\"�擾\" onclick=\"doCommand('MBB','command','scan history " + option2 + " " + path + "');return false;\" title=\"�폜�O�o�[�W�����𕜌����܂�\">");
                }
              }
              out.print("</td>");
              out.println("</tr>");
              line ++;
            }
          }
          out.println("</table>");
          out.flush();
        }
      } catch (SQLException e) {
        log_debug(e);
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {}
        }
      }
    }
    if (line > 0) {
      if (ExcelManager.isEnabled()) {
        //TODO:TEST �ŏI�I�ɂ͏����e���v���[�g��o�^���Ă�����g�p����悤�ɂ�����
        out.println("<input type=\"button\" value=\"Excel\" onclick=\"doExcelReport(document.forms['downloadform'],document.getElementById('historylist').innerHTML);return false;\">");
      }
    }
    out.flush();
  }
  /**
   * ���W���[����r
   * @param out
   * @param request
   */
  private void printMBBCompareModule(PrintWriter out, HttpServletRequest request) {
    String command = request.getParameter("command");
    String url = request.getParameter("update");
    if (command == null || command.trim().length() == 0) {
      command = "compare";
    }
    String localPath = null;
    String historyPath = null;
    if (command.startsWith("compare ")) {
      localPath = command.substring(8); // ��둤��S�ă��W���[��ID�Ƃ���(�󔒂��܂܂��P�[�X�����邽��)
    } else {
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "compare"���X�L�b�v
      if (st.hasMoreTokens()) {
        localPath = st.nextToken();
      }
    }
    
    String compareTarget = null; // dbaccess�̎�O�܂ł�URL
    if (url == null) {
      url = stagingURL;
      if (stagingPass != null) {
        url = DbAccessUtils.appendParameter(url, "password", stagingPass);
      }
    }
    String url1 = url;
    String url2 = "";
    if (url.indexOf("?") != -1) {
      url1 = url.substring(0, url.indexOf("?"));
      url2 = url.substring(url.indexOf("?"));
    }
    if (!url1.endsWith("/dbaccess")) {
      if (url1.endsWith("/")) {
        url1 = url1 + "dbaccess";
      } else {
        url1 = url1 + "/dbaccess";
      }
      url = url1 + url2;
    }
    // compareTarget�ɕ\���p��URL��ݒ肷��
    if (url != null && url.indexOf("?") != -1) {
      compareTarget = url.substring(0, url.indexOf("?"));
    } else {
      compareTarget = url;
    }
    if (compareTarget != null) {
      compareTarget = compareTarget.substring(0, compareTarget.lastIndexOf("/") + 1);
    }
    if (localPath.indexOf(":") != -1) {
      // �����Ɣ�r
      historyPath = localPath.substring(localPath.indexOf(":") + 1);
      localPath = localPath.substring(0, localPath.indexOf(":"));
    }

    int width = 960;
    out.println("<input type=\"hidden\" name=\"mbbmenu\" value=\"COMPARE\">");
    out.println("<table style=\"width:" + width + "px;height:95%;\">");
    out.println("<col style=\"width:50%;\"><col style=\"width:50%;\">");
    out.println("<tr style=\"height:20px;\"><td colspan=\"2\">���W���[����r: " + DbAccessUtils.escapeHTML(DbAccessUtils.unescapeFileName(localPath)) + "</td></tr>");
    out.print("<tr style=\"height:20px;background-color:" + TABLE_HEADER_COLOR + ";\">");
    if (historyPath == null) {
      out.print("<td style=\"width:50%;\">�ڑ��Ώ�(" + compareTarget + ")</td>");
    } else {
      out.print("<td style=\"width:50%;\">����(" + historyPath + ")</td>");
    }
    out.println("<td>���[�J�����W���[��</td></tr>");
    out.flush();
    String encoding = "UTF-8";
    if (localPath.endsWith(".java")) {
      encoding = "Windows-31J"; // TODO: Java�\�[�X�̏ꍇ�A�Ƃ肠����Windows-31J�Œ�
    }
    // ���[�J�����W���[���̎擾
    String text1 = "";
    if (localPath.startsWith("db/")) {
      // DDL�̏ꍇ
      int p = localPath.indexOf("/", localPath.indexOf("/") + 1);
      String id = localPath.substring(p + 1);
      String mod_item = localPath.substring(0, p).toUpperCase();
      Connection conn = null;
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        text1 = getObjectDDL(conn, mod_item, id);
      } catch (SQLException e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(baos));
        try {
          text1 = "<font color=\"" + ERROR_COLOR + "\">" + new String(baos.toByteArray(), "UTF-8") + "</font>";
        } catch (Exception ex) {}
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {}
        }
      }
    } else if (localPath.startsWith("mbb/")) {
      // MBB��`�̓�
      Connection conn = null;
      try {
        conn = getConnection();
        conn.setAutoCommit(false);
        String tableName = localPath.substring(4, localPath.indexOf("/", 4)).toUpperCase() + "MASTER";
        String id = localPath.substring(localPath.lastIndexOf("/") + 1);
        Vector params = getRelationParams(conn, tableName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (printExportMCSV(baos, id.split(",", -1), params)) {
          try {
            text1 = DbAccessUtils.byteToText(baos.toByteArray(), localPath, encoding);
          } catch (Exception e) {}
        } else {
          text1 = "";
        }
      } catch (SQLException e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(baos));
        try {
          text1 = "<font color=\"" + ERROR_COLOR + "\">" + new String(baos.toByteArray(), "UTF-8") + "</font>";
        } catch (Exception ex) {}
      } finally {
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {}
        }
      }
    } else {
      // ���[�J���t�@�C��
      File localFile = new File(appPath, localPath);
      if (localPath.toLowerCase().endsWith(".xls")) {
        text1 = ExcelManager.excelToText(localFile);
      } else {
        text1 = DbAccessUtils.readTextFile(localFile, encoding);
      }
    }
    // �����[�g���W���[���̎擾
    String text2 = "";
    if (historyPath == null) {
      // �����[�g���_�E�����[�h�擾
      try {
        byte[] b = DbAccessUtils.getRemoteFile(url, stagingProxy, localPath);
        if (localPath.toLowerCase().endsWith(".xls")) {
          text2 = ExcelManager.excelToText(new ByteArrayInputStream(b));
        } else {
          text2 = DbAccessUtils.byteToText(b, localPath, encoding);
        }
      } catch (IOException e) {
        text2 = e.getMessage();
        log_debug(e);
      }
    } else {
      // �������擾
      File historyFile = new File(new File(appPath, "WEB-INF/update/" + historyPath), localPath);
      if (localPath.toLowerCase().endsWith(".xls")) {
        text2 = ExcelManager.excelToText(historyFile);
      } else if (localPath.startsWith("mbb/") || localPath.startsWith("db/")) {
        // MBB��`�� or DDL
        text2 = DbAccessUtils.readZippedTextFile(historyFile, localPath, encoding);
      } else {
        text2 = DbAccessUtils.readTextFile(historyFile, encoding);
      }
    }
    if (localPath.startsWith("db/view/")) {
      text1 = new SQLTokenizer(text1).format(1);
      text2 = new SQLTokenizer(text2).format(1);
    }
    // ��r�̎��s
    String[] texts = DbAccessUtils.diffText(text2, text1);
    out.println("<tr><td style=\"width:50%;\">");
    out.println("<div id=\"remote\" style=\"width:" + (width / 2) + "px;height:100%;overflow:scroll;\"");
    out.println(" onscroll=\"document.getElementById('local').scrollLeft=this.scrollLeft;document.getElementById('local').scrollTop=this.scrollTop;\">");
    out.println("<pre>");
    out.println(texts[0]);
    out.println("</pre>");
    out.println("</div>");
    out.println("</td>");
    out.println("<td>");
    out.println("<div id=\"local\" style=\"width:" + (width / 2) + "px;height:100%;overflow:scroll;\"");
    out.println(" onscroll=\"document.getElementById('remote').scrollLeft=this.scrollLeft;document.getElementById('remote').scrollTop=this.scrollTop;\">");
    out.println("<pre>");
    out.println(texts[1]);
    out.println("</pre>");
    out.println("</div>");
    out.println("</div></td>");
    out.println("</tr>");
    out.println("</table>");
    out.flush();
    if (texts.length > 2) {
      int diffcnt = 0;
      try {
        diffcnt = Integer.parseInt(texts[2]);
      } catch (Exception e) {}
      if (diffcnt > 0) {
        out.print("<nobr>");
        for (int i = 0; i < diffcnt; ++i) {
          out.print("<a href=\"#diff" + (i + 1) + "\"");
          if (i < 10) {
            out.print(" accesskey=\"" + ((i + 1) % 10) + "\"");
          }
          out.print(" onfocus=\"this.click()\"");
          out.print(">" + (i + 1) + "</a>&nbsp;");
        }
        out.println("</nobr>");
      }
    }
  }
  
  // ���[�J�����A�t�@�C�����X�g���擾����(�X�V�\��)�A�N���X�{jar�t�@�C���̐���Ԃ�
  // map�́A�L�[���t�@�C����,�l��{MD5SUM, �^�C���X�^���v}
  private int getLocalFileList(Map map, String rootPath, File file) {
    int classes = 0;
    if (rootPath == null) {
      rootPath = file.getAbsolutePath();
    }
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (int i = 0; i < files.length; ++i) {
          log_debug("files[" + i + "]=" + files[i].getName());
          classes += getLocalFileList(map, rootPath, files[i]);
        }
      }
      return classes;
    }
    log_debug("file=" + file.getName());
    String path = getFilePath(rootPath, file);
    if (path.startsWith(rootPath)) {
      path = path.substring(rootPath.length());
    }
    String rootFolderName = new File(rootPath).getName();
    if (rootFolderName.equals("new") || rootFolderName.equals("del")) {
      
    } else {
      if (isIgnorePath(path, null)) {
        log_debug("ignorePath:" + path);
        return 0;
      }
    }
    if ((path.startsWith("WEB-INF/classes/") && path.endsWith(".class"))
      || path.startsWith("WEB-INF/lib/") && path.endsWith(".jar")) {
      classes ++;
    }
    String md5sum = null;
    try {
      md5sum = MD5Sum.getMD5Sum(file);
    } catch (IOException e) {
    }
    String timestamp = DbAccessUtils.toTimestampString(file.lastModified());
    map.put(path, new String[]{md5sum, timestamp});
    return classes;
  }
  
  /**
   * �t�@�C�����X�g���r����(�t�@�C����MD5SUM���v�Z����r)
   *  jar�t�@�C���́A���̃t�@�C������r����
   *  mode��SCAN_LIST�̏ꍇ�́A���[�J���̃t�@�C������S�ĕԂ��̂�
   * @param out
   * @param compareFiles
   * @param retrivedFiles
   * @param updateDelFiles
   * @param file
   * @param zipEntry
   * @param zipInputStream
   * @param loginInfos
   * @param mode
   * @param ignorePath ��r���O�Ώ�
   * @param update �X�V��rURL�i�ʏ��null�^�X�V�ڑ����[�h����URL���w�肳���j
   * @return ��r���e���قȂ�t�@�C���̐�
   * @throws SQLException
   */
  private int printMBBScanModulesCompareFiles(PrintWriter out, Map compareFiles, Map retrivedFiles, Map updateDelFiles, String rootPath, File file, ZipEntry zipEntry, ZipInputStream zipInputStream, String[] loginInfos, int mode, int selectMode, Vector ignorePath, String update) throws SQLException {
    int items = 0;
    if (file.isDirectory()) {
      // �f�B���N�g���̏ꍇ�́A�z���̃t�@�C�����ċA�I�ɔ�r
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (Iterator ite = new TreeSet(Arrays.asList(files)).iterator(); ite.hasNext(); ) {
          File f = (File)ite.next();
          int cnt = printMBBScanModulesCompareFiles(out, compareFiles, retrivedFiles, updateDelFiles, rootPath, f, null, null, loginInfos, mode, selectMode, ignorePath, update);
          items += cnt;
        }
      }
      return items;
    }
    String path = getFilePath(rootPath, file);
    if (isIgnorePath(ignorePath, path, null)) {
      return 0;
    }
    if (path.startsWith("WEB-INF/")) {
      // WEB-INF�z���́Aweb.xml,classes,lib�t�H���_�̂ݕԂ�
      if (!path.startsWith("WEB-INF/web.xml") && !path.startsWith("WEB-INF/classes/") && !path.startsWith("WEB-INF/lib/")) {
        return items;
      }
    }
    long fileSize = file.length();
    long lastModified = file.lastModified();
    String jarPath = null;
    if (zipEntry != null) {
      jarPath = path;
      path = path + "!" + zipEntry.getName();
      fileSize = zipEntry.getSize();
      lastModified = zipEntry.getTime();
    }
    String md5sum = null;
    try {
      if (zipEntry == null) {
        md5sum = MD5Sum.getMD5Sum(file);
      } else {
        // ZipEntry������ꍇ��ZipInputStream���擾
        md5sum = MD5Sum.getMD5Sum(zipInputStream, (int)fileSize);
      }
    } catch (IOException e) {
    }
    String localts = DbAccessUtils.toTimestampString(lastModified);
    if (compareFiles != null && compareFiles.containsKey(path)) {
      // ��r�Ώۂɑ��݂���ꍇ�̓`�F�b�N�T�����r�������t�@�C�����ǂ����`�F�b�N����
      String[] targetInfo = (String[])compareFiles.remove(path);
      String remotemd5sum = targetInfo[0];
      String remotets = targetInfo[1];
      if (md5sum != null && md5sum.equals(remotemd5sum)) {
        // MD5SUM�������ꍇ�͏I��
        out.flush();
        if (path.endsWith(".jar")) {
          // jar�t�@�C���������ꍇ�́A���̔z���̃t�@�C���G���g�����S�ă��X�g����폜����
          for (Iterator ite = compareFiles.keySet().iterator(); ite.hasNext(); ) {
            String key = (String)ite.next();
            if (key.startsWith(path + "!")) {
              ite.remove();
            }
          }
        }
        
        // 2014/07/01 �\�[�X���e�ɍ������Ȃ��ꍇ�́A"��r�i���e����Ȃ��j"��\������ start
        out.print("<tr><td>");
        out.print("<input type=\"checkbox\" name=\"file\" value=\"" + escapeInputValue(path) + "\"");
        out.print(">");
        out.print("</td>");
        // �t�@�C����
        out.print("<td>");
        out.print(path);
        out.print("</td>");
        // �����[�g�^�C���X�^���v
        out.print("<td>");
        out.print(DbAccessUtils.focusTimestampString(remotets, System.currentTimeMillis()));
        out.print("</td>");
        // ���[�J���^�C���X�^���v
        out.print("<td>");
        out.print(localts);
        out.print("</td>");
        // �R�����g
        out.print("<td>");
        out.print("��r�i���e����Ȃ��j");
        out.print("</td>");
        out.println("</tr>");
        out.flush();
        // 2014/07/01 �\�[�X���e�ɍ������Ȃ��ꍇ�́A"��r�i���e����Ȃ��j"��\������ end
        
        return items;
      }
      items ++;
      // ��r�ΏۂƈقȂ�ꍇ
      boolean scheduled = false;
      boolean defaultCheck = false;
      if (retrivedFiles != null) {
        String checkPath = path;
        if (checkPath.indexOf("!") != -1) {
          checkPath = checkPath.substring(0, checkPath.indexOf("!"));
        }
        if (retrivedFiles.containsKey(checkPath)) {
          // ���Ɏ擾��
          scheduled = true;
          retrivedFiles.remove(path);
          if (path.startsWith("WEB-INF/classes/") && path.endsWith(".class")) {
            // �\�[�X���擾�ς̏ꍇ�̓��X�g����폜
            String srcPath = getSourcePathFromClass(path);
            if (srcPath != null && retrivedFiles.containsKey(srcPath)) {
              retrivedFiles.remove(srcPath);
            }
          }
        } else if (jarPath != null && retrivedFiles.containsKey(jarPath)) {
          // jar�͊��Ɏ擾��
          scheduled = true;
        }
      }
      
      int tscomp = DbAccessUtils.compareTimestamp(localts, remotets);
      if (selectMode == 0 && tscomp <= 0) {
        // ���擾�Ń����[�g�������^�C���X�^���v���V�����ꍇ
        if (!path.endsWith(".css") && !path.endsWith(".properties") && !path.endsWith(".xml") && !path.endsWith(".conf") && !path.endsWith(".cfg")) {
          // �f�t�H���g�I��ΏۊO�ȊO�̂݃f�t�H���g�`�F�b�N
          defaultCheck = true;
        }
      } else if (selectMode == 1 && scheduled) {
        // �擾����̎擾�ϑΏ�
        defaultCheck = true;
      }
      out.print("<tr><td>");
      if (zipEntry == null) {
        out.print("<input type=\"checkbox\" name=\"file\" value=\"" + escapeInputValue(path) + "\"");
        if (scheduled) {
          out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\" class=\"scheduled\"");
        }
        if (defaultCheck) {
          out.print(" checked");
        }
        out.print(">");
      }
      out.print("</td>");
      // �t�@�C����
      out.print("<td>");
      if (scheduled) {
        out.print("<font color=\"" + DIFF_SCHEDULED_COLOR + "\">");
      } else {
        if (tscomp <= 0) {
          out.print("<font color=\"" + DIFF_NEWER_COLOR + "\">");
        } else {
          out.print("<font color=\"" + DIFF_OLDER_COLOR + "\">");
        }
      }
      out.print(path);
      out.print("</font>");
      out.print("</td>");
      // �����[�g�^�C���X�^���v
      out.print("<td>");
      out.print(DbAccessUtils.focusTimestampString(remotets, System.currentTimeMillis()));
      out.print("</td>");
      // ���[�J���^�C���X�^���v
      out.print("<td>");
      if (scheduled) {
        out.print("<font color=\"" + DIFF_SCHEDULED_COLOR + "\">");
      }
      out.print(localts);
      if (scheduled) {
        out.print("</font>");
      }
      out.print("</td>");
      // �R�����g
      out.print("<td>");
      boolean comparable = false;
      if (tscomp != 0) {
        String updateParam = "";
        if (update != null) {
          try {
            updateParam = "&update=" + DbAccessUtils.escapeInputValue(java.net.URLEncoder.encode(update, "UTF-8"));
          } catch (Exception e) {}
        }
        if (isComparable(path)) {
          out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + updateParam + "\" target=\"_blank\" tabindex=\"-1\">");
          comparable = true;
        } else {
          String srcPath = getSourcePathFromClass(path);
          if (srcPath != null && new File(appPath, srcPath).exists()) {
            // �\�[�X�����݂���ꍇ
            out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(srcPath) + updateParam + "\" target=\"_blank\" tabindex=\"-1\">");
            comparable = true;
          }
        }
      }
      if (scheduled) {
        out.print("(�X�V�\���)");
      } else {
        if (localts.equals(remotets)) {
          out.print("�t�@�C�����e���قȂ�܂�");
        } else {
          if (comparable) {
            out.print("��r");
          }
        }
      }
      if (comparable) {
        out.print("</a>");
      }
      out.print("</td>");
      out.println("</tr>");
      out.flush();
    } else if (update == null) {
      items ++;
      // ��r�Ώۂɑ��݂��Ȃ��ꍇ
      if (mode == SCAN_LIST) {
        // �S���擾�p
        out.println("FILE\t" + path + "\t" + md5sum + "\t" + localts);
      } else {
        // ���[�J�����폜�Ώ�
        boolean scheduled = false;
        boolean defaultCheck = false;
        if (updateDelFiles != null && updateDelFiles.containsKey(path)) {
          scheduled = true;
          updateDelFiles.remove(path);
        }
        if (selectMode == 0) {
          // ���擾�̏ꍇ
          //defaultCheck = true; // �폜�̏ꍇ�́A�f�t�H���g�I�����Ȃ�
        } else if (selectMode == 1 && scheduled) {
          // �擾����̎擾�ϑΏ�
          defaultCheck = true;
        }
        out.print("<tr>");
        out.print("<td>");
        if (zipEntry == null) {
          out.print("<input type=\"checkbox\" name=\"delfile\" value=\"" + escapeInputValue(path) + "\"");
          if (scheduled) {
            out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\" class=\"scheduled\"");
          }
          if (defaultCheck) {
            out.print(" checked");
          }
          out.print(">");
        }
        out.print("</td>");
        // �t�@�C����
        out.print("<td>");
        if (scheduled) {
          // ���ɍ폜�\�胊�X�g�Ɋ܂܂��
          out.print("<font color=\"" + DIFF_SCHEDULED_COLOR + "\">");
        } else {
          out.print("<font color=\"" + DIFF_DELETED_COLOR + "\">");
        }
        if (zipEntry == null) {
          out.print(path);
        } else {
          out.print(file.getName() + path.substring(path.indexOf("!")));
        }
        out.print("</font>");
        out.print("</td>");
        // �����[�g�^�C���X�^���v
        out.print("<td>");
        out.print("</td>");
        // ���[�J���^�C���X�^���v
        out.print("<td>");
        if (scheduled) {
          out.print("<font class=\"del\" color=\"" + DIFF_SCHEDULED_COLOR + "\">");
        }
        out.print(localts);
        if (scheduled) {
          out.print("</font>");
        }
        out.print("</td>");
        // �R�����g
        out.print("<td>");
        boolean comparable = false;
        if (isComparable(path)) {
          out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + "\" target=\"_blank\" tabindex=\"-1\">");
          comparable = true;
        } else {
          String srcPath = getSourcePathFromClass(path);
          if (srcPath != null && new File(appPath, srcPath).exists()) {
            // �\�[�X�����݂���ꍇ
            out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(srcPath) + "\" target=\"_blank\" tabindex=\"-1\">");
            comparable = true;
          }
        }
        out.print("�폜");
        if (scheduled) {
          out.print("(�폜�\���)");
        }
        if (comparable) {
          out.print("</a>");
        }
        out.print("</td>");
        out.println("</tr>");
      }
      out.flush();
    }
    if (path.startsWith("WEB-INF/lib/") && path.endsWith(".jar")) {
      // mbb����͂��܂�JAR�t�@�C���̏ꍇ�A���̒��̃t�@�C�������X�e�B���O����
      String jarName = file.getName();
      if (jarName.startsWith("mbb")) {
        try {
          ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
          ZipEntry ze = null;
          while ((ze = zis.getNextEntry()) != null) {
            String name = ze.getName();
            if (name.startsWith("META-INF/") || name.endsWith("/")) {
              continue;
            }
            printMBBScanModulesCompareFiles(out, compareFiles, retrivedFiles, updateDelFiles, rootPath, file, ze, zis, loginInfos, mode, selectMode, ignorePath, update);
          }
          zis.close();
        } catch (IOException e) {
          log_debug(e);
        }
      }
    }
    return items;
  }
  /**
   * 
   * @param out
   * @param conn
   * @param compareFiles
   * @param updateNewFiles
   * @param updateDelFiles
   * @param rootPath
   * @param file
   * @param zipEntry
   * @param zipInputStream
   * @param loginInfos
   * @param mode
   * @param selectMode
   * @param ignorePath
   * @param update �X�V��rURL�i�ʏ��null�^�X�V�ڑ����[�h����URL���w�肳���j
   * @throws SQLException
   */
  private int printMBBScanModulesCompareDB(PrintWriter out, Connection conn, String moduleItem, Map remoteApplications, Map retrivedFiles, Map scheduledDelFiles, int selectMode, Vector ignorePath, String update) throws SQLException {
    int items = 0;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      boolean meta = false;
      if (isOracle(0)) {
        stmt = conn.prepareStatement(getModSelectSQL(moduleItem));
        rs = stmt.executeQuery();
      } else {
        if (moduleItem.startsWith("DB/")) {
          if (!moduleItem.endsWith("/TABLE") && !moduleItem.endsWith("/VIEW")) {
            return 0;
          }
          String objectType = moduleItem.substring(moduleItem.lastIndexOf("/") + 1);
          String schema = null;
          if (isMSSql(0)) {
            schema = "dbo";
          }
          rs = conn.getMetaData().getTables(null, schema, "%", new String[]{objectType});
          meta = true;
        } else {
          stmt = conn.prepareStatement(getModSelectSQL(moduleItem));
          rs = stmt.executeQuery();
        }
      }
      while (rs.next()) {
        String id = null;
        String packageId = null;
        String localts = null;
        String localname = null;
        String localinfos = null;
        if (meta) {
          id = rs.getString("TABLE_NAME").toUpperCase();
        } else {
          id = rs.getString(1);
          packageId = rs.getString(2);
          localts = rs.getString("TIMESTAMPVALUE");
          localinfos = rs.getString("UPDATECOMPANYID") + "," + rs.getString("UPDATEUSERID") + "," + rs.getString("UPDATEPROCESSID");
          localname = rs.getString("NAMEVALUE");
        }
        String path = DbAccessUtils.escapeFileName(getModBasePath(moduleItem) + id);
        if (isIgnorePath(path, "mbb/,db/")) {
          continue;
        }
        boolean tserror = false;
        boolean rtserror = false;
        if (localts == null) {
          localts = "";
        } else {
          if (DbAccessUtils.toTimestampLong(localts) == -1) {
            tserror = true;
          }
        }
        if (remoteApplications.containsKey(path)) {
          // �����[�g�ɑ���
          String[] modInfos = (String[])remoteApplications.remove(path);
          String remotesum = modInfos[0];
          if (remotesum != null && remotesum.trim().length() > 0) {
            String ddl = getObjectDDL(conn, moduleItem, id);
            if (ddl != null && remotesum.equals(new SQLTokenizer(ddl).md5Sum())) {
              // �`�F�b�N�T��������
              continue;
            }
          }
          String remotets = modInfos[1];
          String remotename = modInfos[3];
          String remoteinfos = null;
          if (modInfos.length > 4) {
            remoteinfos = modInfos[4];
          }
          int tscomp = localts.compareTo(remotets);
          if (tscomp == 0 && !tserror) {
            // �^�C���X�^���v����v����ꍇ�̓X�L�b�v
            continue;
          }
          if (DbAccessUtils.toTimestampLong(remotets) == -1) {
            rtserror = true;
          }
          items ++;
          //
          boolean scheduled = false;
          boolean defaultCheck = false;
          if (retrivedFiles != null && retrivedFiles.containsKey(path)) {
            // ���Ɏ擾��
            scheduled = true;
            retrivedFiles.remove(path);
          }
          out.print("<tr><td>");
          if (selectMode == 0 && tscomp <= 0 && !rtserror) {
            // ���擾�Ń����[�g�������^�C���X�^���v���V�����ꍇ
            defaultCheck = true;
          } else if (selectMode == 1 && scheduled) {
            // �擾����̎擾�ϑΏ�
            defaultCheck = true;
          }
          out.print("<input type=\"checkbox\" name=\"file\" value=\"" + escapeInputValue(path) + "\"");
          if (scheduled) {
            out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\" class=\"scheduled\"");
          }
          if (defaultCheck) {
            out.print(" checked");
          }
          out.print(">");
          out.print("</td>");
          // �t�@�C����
          out.print("<td>");
          out.print("<font color=\"");
          if (scheduled) {
            out.print(DIFF_SCHEDULED_COLOR);
          } else {
            if (tscomp <= 0) {
              out.print(DIFF_NEWER_COLOR);
            } else {
              out.print(DIFF_OLDER_COLOR);
            }
          }
          out.print("\"");
          if (remotename != null && remotename.trim().length() > 0) {
            if (packageId != null && packageId.trim().length() > 0) {
              remotename = packageId + " " + remotename;
            }
            out.print(" title=\"" + remotename + "\"");
          }
          out.print(">");
          out.print(DbAccessUtils.escapeHTML(path));
          out.print("</font>");
          out.print("</td>");
          // �����[�g�^�C���X�^���v
          out.print("<td");
          if (remoteinfos != null) {
            out.print(" title=\"" + remoteinfos + "\"");
          }
          out.print(">");
          if (rtserror) {
            out.print("<font color=\"" + ERROR_COLOR + "\">");
          }
          out.print(DbAccessUtils.focusTimestampString(remotets, System.currentTimeMillis()));
          if (rtserror) {
            out.print("</font>");
          }
          out.print("</td>");
          // ���[�J���^�C���X�^���v
          out.print("<td");
          if (localinfos != null) {
            out.print(" title=\"" + localinfos + "\"");
          }
          out.print(">");
          if (scheduled) {
            out.print("<font color=\"" + DIFF_SCHEDULED_COLOR + "\">");
          } else if (tserror) {
            out.print("<font color=\"" + ERROR_COLOR + "\">");
          }
          out.print(localts);
          if (scheduled || tserror) {
            out.print("</font>");
          }
          out.print("</td>");
          // �R�����g
          out.print("<td>");
          if (tscomp != 0 && isComparable(path)) {
            String updateParam = "";
            if (update != null) {
              try {
                updateParam = "&update=" + DbAccessUtils.escapeInputValue(java.net.URLEncoder.encode(update, "UTF-8"));
              } catch (Exception e) {}
            }
            out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + updateParam + "\" target=\"_blank\" tabindex=\"-1\">");
          }
          if (scheduled) {
            out.print("(�X�V�\���)");
          } else if (tserror || rtserror) {
            out.print("<font color=\"" + ERROR_COLOR + "\" title=\"�^�C���X�^���v���s���Ȃ��ߐ�������r�ł��܂���\">");
            out.print("�^�C���X�^���v�G���[");
            out.print("</font>");
          } else {
            if (tscomp != 0 && isComparable(path)) {
              out.print("��r");
            }
          }
          if (tscomp != 0 && isComparable(path)) {
            out.print("</a>");
          }
          out.print("</td>");
          out.println("</tr>");
          out.flush();
        } else if (update == null) {
          // �����[�g�ɑ��݂��Ȃ�
          items ++;
          // ���[�J�����폜�Ώ�
          boolean scheduled = false;
          boolean defaultCheck = false;
          if (scheduledDelFiles != null && scheduledDelFiles.containsKey(path)) {
            // ���Ɏ擾��
            scheduled = true;
            scheduledDelFiles.remove(path);
          }
          if (selectMode == 0) {
            // ���擾�̏ꍇ
            //defaultCheck = true; // �폜�̏ꍇ�̓f�t�H���g�I�����Ȃ�
          } else if (selectMode == 1 && scheduled) {
            // �擾����̎擾�ϑΏ�
            defaultCheck = true;
          }
          out.print("<tr>");
          out.print("<td>");
          out.print("<input type=\"checkbox\" name=\"delfile\" value=\"" + escapeInputValue(path) + "\"");
          if (scheduled) {
            out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\" class=\"scheduled\"");
          }
          if (defaultCheck) {
            out.print(" checked");
          }
          out.print(">");
          out.print("</td>");
          // �t�@�C����
          out.print("<td>");
          if (scheduled) {
            // ���ɍ폜�\�胊�X�g�Ɋ܂܂��
            out.print("<font class=\"del\" color=\"" + DIFF_SCHEDULED_COLOR + "\"");
          } else {
            out.print("<font class=\"del\" color=\"" + DIFF_DELETED_COLOR + "\"");
          }
          out.print(" title=\"" + localname + "\">");
          out.print(path);
          out.print("</font>");
          out.print("</td>");
          // �����[�g�^�C���X�^���v
          out.print("<td>");
          out.print("</td>");
          // ���[�J���^�C���X�^���v
          out.print("<td");
          if (localinfos != null) {
            out.print(" title=\"" + localinfos + "\"");
          }
          out.print(">");
          if (scheduled) {
            out.print("<font color=\"" + DIFF_SCHEDULED_COLOR + "\">");
          }
          out.print(localts);
          if (scheduled) {
            out.print("</font>");
          }
          out.print("</td>");
          // �R�����g
          out.print("<td>");
          if (isComparable(path)) {
            out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + "\" target=\"_blank\" tabindex=\"-1\">");
          }
          out.print("�폜");
          if (scheduled) {
            out.print("(�폜�\���)");
          }
          if (isComparable(path)) {
            out.print("</a>");
          }
          out.print("</td>");
          out.println("</tr>");
        }
        out.flush();
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    return items;
  }
  
  public void printMBBScanModulesNewDBModule(PrintWriter out, String path, Map retrivedFiles, int selectMode, String[] modInfos, String update) {
    // modInfos:{MD5SUM,TIMESTAMP,PACKAGE,NAME,UPDATEINFOS}
    String remotets = modInfos[1];
    String remotepkg = modInfos[2];
    String remotename = modInfos[3];
    if (remotepkg.trim().length() > 0) {
      remotename = remotepkg + " " + remotename;
    }
    String remoteinfos = null;
    if (modInfos.length > 4) {
      remoteinfos = modInfos[4];
    }
    boolean scheduled = false;
    boolean defaultCheck = false;
    if (retrivedFiles != null && retrivedFiles.containsKey(path)) {
      scheduled = true;
      retrivedFiles.remove(path);
    }
    if (selectMode == 0) {
      // ���擾�̏ꍇ
      defaultCheck = true;
    } else if (selectMode == 1 && scheduled) {
      // �擾����̎擾�ϑΏ�
      defaultCheck = true;
    }
    out.print("<tr>");
    out.print("<td>");
    out.print("<input type=\"checkbox\" name=\"file\" value=\"" + escapeInputValue(path) + "\"");
    if (scheduled) {
      out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\" class=\"scheduled\"");
    }
    if (defaultCheck) {
      out.print(" checked");
    }
    out.print(">");
    out.print("</td>");
    // �t�@�C����
    out.print("<td>");
    if (scheduled) {
      out.print("<font class=\"new\" color=\"" + DIFF_SCHEDULED_COLOR + "\"");
    } else {
      out.print("<font class=\"new\" color=\"" + DIFF_NEWER_COLOR + "\"");
    }
    out.print(" title=\"" + remotename + "\">");
    out.print(path);
    out.print("</font>");
    out.print("</td>");
    // �����[�g�^�C���X�^���v
    out.print("<td");
    if (remoteinfos != null) {
      out.print(" title=\"" + remoteinfos + "\"");
    }
    out.print(">");
    out.print(DbAccessUtils.focusTimestampString(remotets, System.currentTimeMillis()));
    out.print("</td>");
    // ���[�J���^�C���X�^���v
    out.print("<td>");
    out.print("</td>");
    // �R�����g
    out.print("<td>");
    if (isComparable(path)) {
      String updateParam = "";
      if (update != null) {
        try {
          updateParam = "&update=" + DbAccessUtils.escapeInputValue(java.net.URLEncoder.encode(update, "UTF-8"));
        } catch (Exception e) {}
      }
      out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + updateParam + "\" target=\"_blank\" tabindex=\"-1\">");
    }
    out.print("�V�K");
    if (scheduled) {
      out.print("(�X�V�\���)");
    }
    if (isComparable(path)) {
      out.print("</a>");
    }
    out.print("</td>");
    out.println("</tr>");
    out.flush();
  }
  // ��r��ɂ̂ݑ��݂���t�@�C��
  private void printMBBScanModulesNewFiles(PrintWriter out, Map retrivedFiles, String path, String[] info, String[] loginInfos, int selectMode, String update) throws IOException {
    boolean scheduled = false;
    boolean defaultCheck = false;
    if (retrivedFiles != null && retrivedFiles.containsKey(path)) {
      // �擾��
      scheduled = true;
      retrivedFiles.remove(path);
      if (path.startsWith("WEB-INF/classes/") && path.endsWith(".class")) {
        // �\�[�X���擾�ς̏ꍇ�̓��X�g����폜
        String srcPath = getSourcePathFromClass(path);
        if (srcPath != null && retrivedFiles.containsKey(srcPath)) {
          retrivedFiles.remove(srcPath);
        }
      }
    }
    if (selectMode == 0) {
      // ���擾�̏ꍇ
      if (!path.endsWith(".css") && !path.endsWith(".properties") && !path.endsWith(".xml") && !path.endsWith(".conf") && !path.endsWith(".cfg")) {
        // �f�t�H���g�I��ΏۊO�ȊO�̂݃f�t�H���g�`�F�b�N
        defaultCheck = true;
      }
    } else if (selectMode == 1 && scheduled) {
      // �擾����̎擾�ϑΏ�
      defaultCheck = true;
    }
    out.print("<tr>");
    out.print("<td>");
    if (path.indexOf("!") == -1) {
      // JAR���̃t�@�C���̏ꍇ�̓`�F�b�N�{�b�N�X�Ȃ�
      out.print("<input type=\"checkbox\" name=\"file\" value=\"" + escapeInputValue(path) + "\"");
      if (scheduled) {
        out.print(" onclick=\"document.getElementById('cancelbtn').disabled=false;document.getElementById('commitbtn').disabled=false;\" class=\"scheduled\"");
      }
      if (defaultCheck) {
        out.print(" checked");
      }
      out.print(">");
    } else {
      // jar�t�@�C���̏ꍇ�́Ajar�t�@�C�����X�V�Ώۂ��`�F�b�N
      String jar = path.substring(0, path.indexOf("!"));
      if (retrivedFiles != null && retrivedFiles.containsKey(jar)) {
        scheduled = true; // �ʏ�͊��ɑ��݂��Ȃ��H
      }
    }
    out.print("</td>");
    // �t�@�C����
    out.print("<td>");
    if (scheduled) {
      out.print("<font class=\"new\" color=\"" + DIFF_SCHEDULED_COLOR + "\">");
    } else {
      out.print("<font class=\"new\" color=\"" + DIFF_NEWER_COLOR + "\">");
    }
    out.print(DbAccessUtils.escapeHTML(path));
    out.print("</font>");
    out.print("</td>");
    // �����[�g�^�C���X�^���v
    out.print("<td>");
    out.print(DbAccessUtils.focusTimestampString(info[1], System.currentTimeMillis()));
    out.print("</td>");
    // ���[�J���^�C���X�^���v
    out.print("<td>");
    out.print("</td>");
    // �R�����g
    out.print("<td>");
    boolean comparable = false;
    String updateParam = "";
    if (update != null) {
      try {
        updateParam = "&update=" + DbAccessUtils.escapeInputValue(java.net.URLEncoder.encode(update, "UTF-8"));
      } catch (Exception e) {}
    }
    if (isComparable(path)) {
      out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(path) + updateParam + "\" target=\"_blank\">");
      comparable = true;
    } else {
      String srcPath = getSourcePathFromClass(path);
      if (srcPath != null && new File(appPath, "src").exists()) {
        // �\�[�X�t�H���_�����݂���ꍇ
        out.print("<a href=\"dbaccess?tab=MBB&mbbmenu=COMPARE&command=compare%20" + encodeURLPath(srcPath) + updateParam + "\" target=\"_blank\" tabindex=\"-1\">");
        comparable = true;
      }
    }
    out.print("�V�K");
    if (scheduled) {
      out.print("(�X�V�\���)");
    }
    if (comparable) {
      out.print("</a>");
    }
    out.print("</td>");
    out.println("</tr>");
    out.flush();
  }
  // file�̃R���e�L�X�g���[�g����̑��΃p�X���擾����
  private String getFilePath(String rootPath, File file) {
    String path = file.getAbsolutePath();
    if (path.startsWith(rootPath)) {
      path = path.substring(rootPath.length());
      if (!path.endsWith("/") && !path.endsWith("\\")) {
        path = path.substring(1);
      }
    }
    path = path.replaceAll("\\\\", "/");
    return path;
  }
  // ���W���[�����X�g�iMBB��`�́ADDL�j���ȉ��`���Ń��X�g�o��
  // MBB\t�p�X\tMD5SUM\t�^�C���X�^���v\t�p�b�P�[�WID\t����
  private int printMBBScanModulesListDBModules(PrintWriter out, Connection conn, String[] mod_items) {
    int count = 0;
    if (mod_items == null) {
      mod_items = DEFAULT_MOD_ITEMS;
    }
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      for (int i = 0; i < mod_items.length; ++i) {
        if (!isSupportedModuleType(mod_items[i])) {
          continue;
        }
        String objectType = mod_items[i].substring(mod_items[i].lastIndexOf("/") + 1);
        if (isOracle(0)) {
          stmt = conn.prepareStatement(getModSelectSQL(mod_items[i]));
          rs = stmt.executeQuery();
        } else {
          if (mod_items[i].startsWith("DB/")) {
            // Oracle�ȊO�̏ꍇ�́ASELECT���ł̃I�u�W�F�N�g�擾�͈ꕔ�������Ė��T�|�[�g
            if (!mod_items[i].endsWith("/TABLE") && !mod_items[i].endsWith("/VIEW")) {
              continue;
            }
            log_debug("objectType=" + objectType);
            rs = conn.getMetaData().getTables(null, schemas[0], "%", new String[]{objectType});
          } else {
            stmt = conn.prepareStatement(getModSelectSQL(mod_items[i]));
            rs = stmt.executeQuery();
          }
        }
        while (rs.next()) {
          String id = null;
          String packageId = null;
          String timestampValue = null;
          String updateInfos = null;
          String nameValue = null;
          if (isOracle(0)) {
            id = rs.getString(1);
            packageId = rs.getString(2);
            timestampValue = rs.getString("TIMESTAMPVALUE");
            updateInfos = rs.getString("UPDATECOMPANYID") + "," + rs.getString("UPDATEUSERID") + "," + rs.getString("UPDATEPROCESSID");
            nameValue = rs.getString("NAMEVALUE");
          } else {
            if (mod_items[i].startsWith("DB/")) {
              id = rs.getString("TABLE_NAME");
            } else {
              id = rs.getString(1);
              packageId = rs.getString(2);
              timestampValue = rs.getString("TIMESTAMPVALUE");
              updateInfos = rs.getString("UPDATECOMPANYID") + "," + rs.getString("UPDATEUSERID") + "," + rs.getString("UPDATEPROCESSID");
              nameValue = rs.getString("NAMEVALUE");
            }
          }
          if (timestampValue == null) {
            timestampValue = "";
          }
          if (nameValue == null) {
            nameValue = "";
          }
          String sum = "";
          if (mod_items[i].startsWith("DB/")) {
            String ddl = getObjectDDL(conn, objectType, id);
            //log_debug("ddl[" + id + "]=" + ddl);
            if (ddl != null) {
              //sum = MD5Sum.md5Sum(ddl);
              sum = new SQLTokenizer(ddl).md5Sum(); // �󔒓��𐮌`����MD5SUM���擾
            }
          }
          out.println("MBB\t" + getModBasePath(mod_items[i]) + DbAccessUtils.escapeFileName(escape(id)) + "\t" + sum + "\t" + escape(timestampValue) + "\t" + escape(packageId) + "\t" + escape(nameValue) + "\t" + escape(updateInfos));
          out.flush();
          count++;
        }
        rs.close();
        rs = null;
        if (stmt != null) {
          stmt.close();
          stmt = null;
        }
      }
    } catch (SQLException e) {
      log_debug(e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    return count;
  }
  /**
   * ���W���[����r�\�ȃp�X�𔻒f
   * @param path
   * @return
   */
  private static boolean isComparable(String path) {
    if (path.endsWith(".cfg")
        || path.endsWith(".css")
        || path.endsWith(".html")
        || path.endsWith(".js")
        || path.endsWith(".jsp")
        || path.endsWith(".properties")
        || path.endsWith(".txt")
        || path.endsWith(".xml")
        || path.endsWith(".xls")
        ) {
      return true;
    }
    if (path.startsWith("db/")) {
      return true;
    }
    if (path.startsWith("mbb/")) {
      return true;
    }
    return false;
  }
  /**
   * Oracle�ŁAINVALID�ƂȂ��Ă���I�u�W�F�N�g�����R���p�C������
   * @param conn
   * @param loginInfos
   * @return
   */
  private int recompileInvalidDBObjects(String[] loginInfos) {
    int errorDBCount = 0;
    Connection conn = null;
    Statement stmt1 = null;
    Statement stmt2 = null;
    ResultSet rs = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(true);
      stmt1 = conn.createStatement();
      rs = stmt1.executeQuery("SELECT OBJECT_TYPE, OBJECT_NAME FROM USER_OBJECTS WHERE STATUS='INVALID'");
      while (rs.next()) {
        String object_type = rs.getString(1);
        String object_name = rs.getString(2);
        stmt2 = conn.createStatement();
        try {
          String alter = "ALTER " + object_type + " \"" + object_name + "\" COMPILE";
          stmt2.execute(alter);
          //out.println(object_type + " " + object_name + "���ăR���p�C�����܂���.");
          // ���s����DDL�����O�Ɏc��
          insertSQLLog(alter, Integer.toString(1), null, null, loginInfos);
        } catch (SQLException e) {
          log_debug(e);
        } finally {
          try {
            stmt2.close();
          } catch (SQLException e) {}
          stmt2 = null;
        }
      }
      rs.close();
      rs = null;
      stmt2 = conn.createStatement();
      rs = stmt2.executeQuery("SELECT COUNT(OBJECT_NAME) FROM USER_OBJECTS WHERE STATUS='INVALID'");
      if (rs.next()) {
        errorDBCount = rs.getInt(1);
      }
    } catch (SQLException e) {
      log_debug(e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException se) {}
        rs = null;
      }
      if (stmt2 != null) {
        try {
          stmt2.close();
        } catch (SQLException se) {}
        stmt2 = null;
      }
      if (stmt1 != null) {
        try {
          stmt1.close();
        } catch (SQLException se) {}
        stmt1 = null;
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException se) {}
        conn = null;
      }
    }
    return errorDBCount;
  }
  
  
  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < value.length(); ++i) {
      char c = value.charAt(i);
      switch (c) {
      case '\\':
        sb.append("\\\\");
        break;
      case '\t':
        sb.append("\\t");
        break;
      case '\r':
        sb.append("\\r");
        break;
      case '\n':
        sb.append("\\n");
        break;
      default:
        sb.append(c);
      }
    }
    return sb.toString();
  }
  
  public static String encodeURL(String str) {
    if (str == null) {
      return "";
    }
    try {
      return java.net.URLEncoder.encode(str, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return java.net.URLEncoder.encode(str);
    }
  }
  public static String encodeURLPath(String str) {
    if (str == null) {
      return "";
    }
    int p = str.lastIndexOf("/");
    if (p != -1) {
      str = str.substring(0, p + 1) + DbAccessUtils.escapeFileName(str.substring(p + 1));
    }
    try {
      return java.net.URLEncoder.encode(str, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return java.net.URLEncoder.encode(str);
    }
  }
  
  /**
   * MBB�̒�`�̂��t�@�C���փG�N�X�|�[�g����
   * @param conn
   * @param zipfile
   * @param table
   * @param id
   * @throws IOException
   */
  private void exportMBBModule(Connection conn, File zipfile, String table, String id) throws IOException {
    String tableId = table.toUpperCase() + "MASTER";
    Vector expParams = getRelationParams(conn, tableId);
    String fileext = ".csv";
    String entryFileName =  table.toLowerCase() + "/" + getEntryFileName(id) + fileext;
    if (!zipfile.getParentFile().exists()) {
      zipfile.getParentFile().mkdirs();
    }
    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipfile));
    ZipEntry ze = new ZipEntry(entryFileName);
    long ts = DbAccessUtils.toTimestampLong(getTimestamp(conn, tableId, shiftArray((String[])expParams.get(0)), id));
    if (ts != -1) {
      ze.setTime(ts);
    }
    zos.putNextEntry(ze);
    
    boolean exported = printExportMCSV(zos, id.split(",", -1), expParams);
    
    zos.closeEntry();
    zos.close();
    
    if (!exported && zipfile.exists()) {
      // �Ώۃf�[�^�������ꍇ�͍폜�i�w�b�_�̂݋�t�@�C���������j
      zipfile.delete();
    }
  }
  /**
   * DB�I�u�W�F�N�g DDL���t�@�C���֏o�͂���iOracle�p�j
   * @param conn
   * @param zipfile
   * @param objectType
   * @param objectName
   * @throws SQLException
   * @throws IOException
   */
  private void exportDBObject(Connection conn, File zipfile, String objectType, String objectName) throws SQLException, IOException {
    String ddl = getObjectDDL(conn, objectType, objectName);
    if (ddl == null) {
      return;
    }
    if (!zipfile.getParentFile().exists()) {
      zipfile.getParentFile().mkdirs();
    }
    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipfile));
    ZipEntry ze = new ZipEntry(getEntryFileName(objectName) + ".sql");
    ze.setTime(getObjectLastModified(conn, objectType, objectName));
    byte[] bytes = ddl.getBytes("UTF-8");
    ze.setSize(bytes.length);
    zos.putNextEntry(ze);
    DbAccessUtils.writeFile(zos, new ByteArrayInputStream(bytes));
    zos.closeEntry();
    zos.finish();
    zos.close();
  }
  /**
   * DataSource��DataSource2�̃X�L�[�}���i�����e�[�u�����C�A�E�g�j�̔�r
   * @param out
   * @param command "compare [table]"
   */
  private void printCompare(PrintWriter out, String command, String datasource) {
    //"compare to xxx"�ŕʂ̃f�[�^�x�[�X�Ɣ�r(�X�L�[�}���̂�)
    if (command.substring(7).trim().startsWith("to ")) {
      int p = command.indexOf("to");
      datasource = new StringTokenizer(command.substring(p + 2).trim()).nextToken();
      try {
        Integer.parseInt(datasource);
      } catch (Exception e) {
        // �����ɕϊ��ł��Ȃ��ꍇ�́A���̂�茟��
        for (int i = 1; i < dataSourceNames.length; ++i) {
          if (datasource.equalsIgnoreCase(dataSourceNames[i])) {
            datasource = Integer.toString(i + 1);
            break;
          }
        }
      }
    }
    if (dataSources.length <= 1) {
      out.println("��r�Ώ� dataSource ���ݒ肳��Ă��܂���B");
      return;
    }
    
    int index = 0;
    if (datasource == null) {
      datasource = "2";
    }
    StringTokenizer st = new StringTokenizer(command);
    st.nextToken(); // "compare"���X�L�b�v
    String table = null;
    String option = null;
    if (st.hasMoreTokens()) {
      table = st.nextToken();
      if (st.hasMoreTokens()) {
        option = st.nextToken("\0").trim();
      }
      if (option != null && option.toUpperCase().equals("^UPDATE*")) {
        option = "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE";
      }
    }
    if (table != null) {
      if (table.indexOf("=") != -1) {
        if (datasource != null) {
          index = Integer.parseInt(datasource) - 1;
          out.println("1:" + schemas[0] + "@" + dataSourceNames[0] + " - " + datasource + ":" + schemas[index] + "@" +  dataSourceNames[index] + "<br>");
        }
        if (table.toUpperCase().startsWith("PROCESSID=")) {
          // �v���Z�XID�ł̔�r
          String processid = table.substring(10);
          if (processid.length() > 2 && (processid.startsWith("\"") || processid.startsWith("'"))
              && (processid.endsWith("\"") || processid.endsWith("'"))) {
            processid = processid.substring(1, processid.length() - 1);
          }
          String where = "WHERE PROCESSID='" + processid + "'";
          printCompareTable(out, "PROCESSMASTER", null, where, datasource);
          printCompareTable(out, "PROCESSNAME", null, where, datasource);
          printCompareTable(out, "PROCESSDEFINITIONMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
          printCompareTable(out, "PROCESSITEMRELDEFMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
          String itemdefinitionid = getItemdefinitionId(processid);
          where =  "WHERE ITEMDEFINITIONID='" + itemdefinitionid + "'";
          printCompareTable(out, "ITEMDEFINITIONMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
          out.flush();
        } else if (table.toUpperCase().startsWith("PAGEID=")) {
          // �y�[�WID�ł̔�r
          String pageid = table.substring(7);
          if (pageid.length() > 2 && (pageid.startsWith("\"") || pageid.startsWith("'"))
              && (pageid.endsWith("\"") || pageid.endsWith("'"))) {
            pageid = pageid.substring(1, pageid.length() - 1);
          }
          String where = "WHERE PAGEID='" + pageid + "'";
          printCompareTable(out, "PAGEMASTER", null, where, datasource);
          printCompareTable(out, "PAGENAME", null, where, datasource);
          printCompareTable(out, "VIEWPAGEMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
          printCompareTable(out, "VIEWPAGEINFO", null, where, datasource);
          if (pageid.indexOf("_") != -1) {
            pageid = pageid.substring(0, pageid.indexOf("_")) + "@%";
          }
          where =  "WHERE PAGEMESSAGEID LIKE '" + pageid + "'";
          printCompareTable(out, "PAGEMESSAGE", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
        } else if (table.toUpperCase().startsWith("TABLEID=")) {
          // �e�[�u��ID�ł̔�r
          String tableid = table.substring(8);
          String where = "WHERE TABLEID='" + tableid + "'";
          printCompareTable(out, "TABLEMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
          printCompareTable(out, "TABLENAME", null, where, datasource);
          printCompareTable(out, "TABLEINFO", null, where, datasource);
          printCompareTable(out, "TABLELAYOUTMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
        } else if (table.toUpperCase().startsWith("DATAFIELDID=")) {
          // �f�[�^�t�B�[���hID�ł̔�r
          String datafieldid = table.substring(12);
          String where = "WHERE DATAFIELDID='" + datafieldid + "'";
          printCompareTable(out, "DATAFIELDMASTER", null, where, datasource);
          printCompareTable(out, "DATAFIELDNAME", null, where, datasource);
          if (hasDataFieldInfo) {
            printCompareTable(out, "DATAFIELDINFO", null, where, datasource);
          }
          printCompareTable(out, "DATAFIELDVALUEMASTER", null, "^UPDATECOMPANYID|UPDATEUSERID|UPDATEPROCESSID|TIMESTAMPVALUE " + where, datasource);
          printCompareTable(out, "DATAFIELDVALUENAME", null, where, datasource);
        } else if (table.toUpperCase().startsWith("APPLICATIONID=")) {
          // �A�v���P�[�V����ID�ł̔�r
          String tableid = table.substring(14);
          String where = "WHERE APPLICATIONID='" + tableid + "'";
          printCompareTable(out, "APPLICATIONMASTER", null, where, datasource);
          printCompareTable(out, "APPLICATIONNAME", null, where, datasource);
        }
        out.flush();
        return;
      } else if (table.indexOf(":") != -1) {
        // �Q�̃e�[�u�����r
        String[] t = table.split(":");
        out.println("1:" + t[0] + " - " + "2:" + t[1] + "<br>");
        printCompareTable(out, t[0], t[1], option, null);
        out.flush();
        return;
      }
      if (datasource != null) {
        index = Integer.parseInt(datasource) - 1;
        out.println("1:" + schemas[0] + "@" + dataSourceNames[0] + " - " + datasource + ":" + schemas[index] + "@" +  dataSourceNames[index] + "<br>");
      }
      Vector tables = getObjectNames(table, OBJ_TYPE_PTABLE);
      for (int i = 0; i < tables.size(); ++i) {
        printCompareTable(out, (String)tables.get(i), null, option, datasource);
        out.flush();
      }
      return;
    }
    
    // �S�e�[�u����r
    Connection conn1 = null;
    Connection conn2 = null;
    try {
      conn1 = getConnection();
      conn1.setAutoCommit(false);
      conn2 = getConnection(index);
      if (conn2 == null) {
        out.println("��r�Ώ� Connection ���擾�ł��܂���B");
        return;
      }
      conn2.setAutoCommit(false);
      // ���ꖼ�̃e�[�u�������݂��邩�`�F�b�N
      Vector tableNames1 = getObjectNames(null, OBJ_TYPE_PTABLE);
      Vector aTables = new Vector(); // ��r�Ώ�(�o���ɑ���)�e�[�u�����i�[����
      ResultSet rs2 = conn2.getMetaData().getTables(null, conn2.getMetaData().getUserName(), "%", new String[]{"TABLE"});
      while (rs2.next()) {
        String table_name = rs2.getString("TABLE_NAME").toUpperCase();
        if (table_name != null && isSystemTable(table_name)) {
          continue;
        }
        if (!tableNames1.contains(table_name)) {
          out.println("�e�[�u��[" + table_name + "]����r��(" + getDSName(dataSourceNames[0]) + ")�ɑ��݂��܂���B<br>");
        } else {
          tableNames1.remove(table_name);
          aTables.add(table_name);
        }
      }
      rs2.close();
      if (tableNames1.size() > 0) {
        for (int i = 0; i < tableNames1.size(); i++) {
          out.println("�e�[�u��[" + tableNames1.get(i) + "]����r��(" + getDSName(dataSourceNames[index]) + ")�ɑ��݂��܂���B<br>");
        }
      }
      // �e�[�u���̒�`�̈Ⴂ���r
      for (int i = 0; i < aTables.size(); i++) {
        String table_name = (String)aTables.get(i);
        Vector colinfo1 = getColumnsInfo(table_name, conn1);
        Vector colinfo2 = getColumnsInfo(table_name, conn2);
        for (int j = 0; j < colinfo1.size(); j++) {
          Object[] arr = (Object[])colinfo1.get(j);
          String colName = (String)arr[0];
          int found = -1;
          for (int k = 0; k < colinfo2.size(); k++) {
            Object[] arr2 = (Object[])colinfo2.get(k);
            String colName2 = (String)arr2[0];
            if (colName2.equals(colName)) {
              // ���O����v����t�B�[���h���������ꍇ
              found = k;
              if (isOracle(0) 
                && (colName.toUpperCase().indexOf("TIMESTAMPVALUE") >= 0 
                    || colName.toUpperCase().indexOf("REGISTRATIONTIMESTAMPVALUE") >= 0) 
                && ((Integer)arr[3]).intValue() == 93 || ((Integer)arr2[3]).intValue() == 93) {
                // ORACLE����TIMESTAMPVALUE�̏ꍇ�̓T�C�Y���̔�r�͂����Ȃ�Ȃ�
                colinfo2.remove(arr2); // ��r�������Ȃ����t�B�[���h���͍폜����
                break;
              }
              // JDK1.4��Integer#compareTo(Object)��JDK5.0�Ɣ�݊�
              // JDK5.0�Ή��̂��߁AcompareTo(Object)�̎g�p�͂�߁A
              // compareTo(Integer)���g�p����B
              // size���r
              if (((Integer)arr[3]).intValue() == 3) {
                // DECIMAL�^�̏ꍇ
                if ( ( (Integer) arr[4]).compareTo((Integer) arr2[4]) != 0 ||
                     ( (Integer) arr[5]).compareTo((Integer) arr2[5]) != 0
                     ) {
                  out.println("�t�B�[���h[" + table_name + "." + colName +
                              "]�̒������قȂ�܂��B<br>");
                }
              } else {
                if ( ( (Integer) arr[2]).compareTo((Integer) arr2[2]) != 0) {
                  out.println("�t�B�[���h[" + table_name + "." + colName +
                              "]�̒������قȂ�܂��B<br>");
                }
              }
              // type���r
              if (((Integer)arr[3]).compareTo((Integer) arr2[3]) != 0) {
                out.println("�t�B�[���h[" + table_name + "." + colName +
                            "]�̃f�[�^�^���قȂ�܂��B<br>");
              }
              colinfo2.remove(arr2); // ��r�������Ȃ����t�B�[���h���͍폜����
              break;
            }
          }
          if (found == -1) {
            out.println("�t�B�[���h[" + table_name + "." + colName + "]����r��(" + getDSName(dataSourceNames[index]) + ")�ɑ��݂��܂���B<br>");
          }
        }
        if (colinfo2.size() > 0) {
          // �c�����t�B�[���h���o��
          for (int j = 0; j < colinfo2.size(); j++) {
            Object[] arr2 = (Object[])colinfo2.get(j);
            out.println("�t�B�[���h[" + table_name + "." + arr2[0] + "]����r��(" + getDSName(dataSourceNames[0]) + ")�ɑ��݂��܂���B<br>");
          }
        }
        out.flush();
      }
    } catch (SQLException e) {
      printError(out, e);
    } finally {
      if (conn1 != null) {
        try {
          conn1.close();
        } catch(SQLException e) {
        }
      }
      if (conn2 != null) {
        try {
          conn2.close();
        } catch(SQLException e) {
        }
      }
    }
    // �SVIEW�̈Ⴂ���r
    try {
      conn1 = getConnection();
      conn1.setAutoCommit(false);
      conn2 = getConnection(index);
      if (conn2 == null) {
        out.println("dataSource2 ��� Connection ���擾�ł��܂���B");
        return;
      }
      conn2.setAutoCommit(false);
      // ���ꖼ��VIEW�����݂��邩�`�F�b�N
      Vector objectNames1 = getObjectNames(null, OBJ_TYPE_PVIEW);
      Vector aViews = new Vector(); // ��r�Ώ�(�o���ɑ��݂���)VIEW���i�[����
      ResultSet rs2 = conn2.getMetaData().getTables(null, conn2.getMetaData().getUserName(), "%", new String[]{"VIEW"});
      while (rs2.next()) {
        String table_name = rs2.getString("TABLE_NAME").toUpperCase();
        if (table_name != null && isSystemTable(table_name)) {
          continue;
        }
        if (!objectNames1.contains(table_name)) {
          out.println("�r���[[" + table_name + "]����r��(" + getDSName(dataSourceNames[0]) + ")�ɑ��݂��܂���B<br>");
        } else {
          objectNames1.remove(table_name);
          aViews.add(table_name);
        }
      }
      rs2.close();
      if (objectNames1.size() > 0) {
        for (int i = 0; i < objectNames1.size(); i++) {
          out.println("�r���[[" + objectNames1.get(i) + "]����r��(" + getDSName(dataSourceNames[index]) + ")�ɑ��݂��܂���B<br>");
        }
      }
      // VIEW�̍��ڂ̈Ⴂ���r
      for (int i = 0; i < aViews.size(); i++) {
        String table_name = (String)aViews.get(i);
        Vector colinfo1 = getColumnsInfo(table_name, conn1);
        Vector colinfo2 = getColumnsInfo(table_name, conn2);
        boolean diff = false;
        for (int j = 0; j < colinfo1.size(); j++) {
          Object[] arr = (Object[])colinfo1.get(j);
          String colName = (String)arr[0];
          int found = -1;
          for (int k = 0; k < colinfo2.size(); k++) {
            Object[] arr2 = (Object[])colinfo2.get(k);
            String colName2 = (String)arr2[0];
            if (colName2.equals(colName)) {
              // ���O����v����t�B�[���h���������ꍇ
              found = k;
              if (isOracle(0) 
                && (colName.toUpperCase().indexOf("TIMESTAMPVALUE") >= 0 
                    || colName.toUpperCase().indexOf("REGISTRATIONTIMESTAMPVALUE") >= 0) 
                && ((Integer)arr[3]).intValue() == 93 || ((Integer)arr2[3]).intValue() == 93) {
                // ORACLE����TIMESTAMPVALUE�̏ꍇ�̓T�C�Y���̔�r�͂����Ȃ�Ȃ�
                colinfo2.remove(arr2); // ��r�������Ȃ����t�B�[���h���͍폜����
                break;
              }
              // size���r
              if (((Integer)arr[3]).intValue() == 3) {
                // DECIMAL�^�̏ꍇ
                if ( ( (Integer) arr[4]).compareTo((Integer) arr2[4]) != 0 ||
                     ( (Integer) arr[5]).compareTo((Integer) arr2[5]) != 0
                     ) {
                  out.println("�t�B�[���h[" + table_name + "." + colName +
                              "]�̒������قȂ�܂��B<br>");
                  diff = true;
                }
              } else {
                if ( ( (Integer) arr[2]).compareTo((Integer) arr2[2]) != 0) {
                  out.println("�t�B�[���h[" + table_name + "." + colName +
                              "]�̒������قȂ�܂��B<br>");
                  diff = true;
                }
              }
              // type���r
              if (((Integer)arr[3]).compareTo((Integer) arr2[3]) != 0) {
                out.println("�t�B�[���h[" + table_name + "." + colName +
                            "]�̃f�[�^�^���قȂ�܂��B<br>");
                diff = true;
              }
              colinfo2.remove(arr2); // ��r�������Ȃ����t�B�[���h���͍폜����
              break;
            }
          }
          if (found == -1) {
            out.println("�t�B�[���h[" + table_name + "." + colName + "]����r��(" + getDSName(dataSourceNames[index]) + ")�ɑ��݂��܂���B<br>");
            diff = true;
          }
        }
        if (colinfo2.size() > 0) {
          // �c�����t�B�[���h���o��
          for (int j = 0; j < colinfo2.size(); j++) {
            Object[] arr2 = (Object[])colinfo2.get(j);
            out.println("�t�B�[���h[" + table_name + "." + arr2[0] + "]����r��(" + getDSName(dataSourceNames[0]) + ")�ɑ��݂��܂���B<br>");
          }
          diff = true;
        }
        if (!diff && isOracle(0)) {
          // ��v���Ă���ꍇ�́AScript�̈Ⴂ���`�F�b�N(�R�����g�J�b�g�Ŏ擾�j
          String ddl1 = getViewScript(conn1, schemas[0], table_name, 2);
          String ddl2 = getViewScript(conn2, schemas[index], table_name, 2);
          if (ddl1 != null) {
            if (!ddl1.equals(ddl2)) {
              out.println("�r���[��`[" + table_name + "]���قȂ�܂��B<br>");
            }
          }
        }
        out.flush();
      }
    } catch (SQLException e) {
      printError(out, e);
    } finally {
      if (conn1 != null) {
        try {
          conn1.close();
        } catch(SQLException e) {
        }
      }
      if (conn2 != null) {
        try {
          conn2.close();
        } catch(SQLException e) {
        }
      }
    }
  }
  // Oracle��p
  // style: 0:�擾���e���̂܂܁A1:���`�A2:���`�i�R�����g�J�b�g�j
  private String getViewScript(Connection conn, String owner, String objectName, int style) {
    
    PreparedStatement stmt = null;
    ResultSet rs = null;
    String ddl = null;
    try {
      if (isOracle(0)) {
        ddl = DbAccessUtils.getCreateObjectDDLForOracle(conn, owner, "VIEW", objectName);
      } else if (isDerby(0)) {
        ddl = DbAccessUtils.getViewCreateDDLOfDerby(conn, owner.toUpperCase(), objectName);
      } else if (isMSSql(0)) {
        //SQL�T�[�o
        ddl = DbAccessUtils.getCreateObjectDDLForMsSql(conn, "VIEW", objectName);
      } else if (isMySql(0)) {
        //MySQL�T�[�o
        ddl = DbAccessUtils.getViewCreateDDLOfMySql(conn, objectName);
      }
      
      if (ddl != null && style > 0) {
        // ���s�̕␳
        ddl = ddl.replaceAll("\r\n", "\n");
        ddl = ddl.replaceAll("\r", "\n");
        ddl = ddl.replaceAll("\n", EOL);
        if (owner != null) {
          // "OWNER".����������
          String ownerstr = " \"" + owner.toUpperCase() + "\".\"";
          ddl = ddl.replaceAll(ownerstr, " \"");
        }
        // VIEW�̐��`
        StringBuffer view = new StringBuffer();
        SQLTokenizer st = new SQLTokenizer(ddl);
        String lastt = null;
        int linesize = 0;
        int phase = 0; // 0:AS���O�A1:AS�ȍ~
        while (st.hasMoreTokens()) {
          String t = st.nextToken();
          if (t.equals("(") && linesize > 2
              && !"IN".equals(lastt) && !"AND".equals(lastt) && !"OR".equals(lastt)
              && st.nextChar() != '+') {
            // "(" �������ꍇ�́A���s����
            if (phase == 0) {
              phase = 1;
            }
            view.append(EOL);
            linesize = 0;
          } else if (t.equals("AS")) {
            // "AS" �̑O����
            if (phase == 0) {
              phase = 1;
            }
            if (")".equals(lastt) && linesize > 20) {
              // ���J�b�R�̌�̏ꍇ�́A���s������
              view.append(EOL);
              linesize = 0;
            } else {
              if (lastt != null && lastt.equals("+") && t.equals(")")) {
                // (+)�͊Ԃɋ󔒂����Ȃ����߁A"+"�̎���")"�̏ꍇ�͋󔒂����Ȃ�
              } else {
                view.append(" ");
                linesize++;
              }
            }
          } else if (t.equals("SELECT") || t.equals("WHERE")) {
            // "SELECT" "WHERE" �̑O����
            if (lastt == null || !lastt.equals("(")) {
              if (linesize > 20) {
                view.append(EOL);
                linesize = 0;
              } else {
                view.append(" ");
                linesize++;
              }
            }
          } else {
            if (!t.equals(",") && !t.equals(")") && view.length() > 0) {
              if (lastt != null && lastt.equals("(") && t.equals("+")) {
                // (+)�͊Ԃɋ󔒂����Ȃ�����"("�̎���"+"�̑O�ɂ͋󔒂����Ȃ�
              } else {
                if (lastt == null || !lastt.equals("(")) {
                  view.append(" ");
                  linesize++;
                }
              }
            }
          }
          if (style == 2 && (t.startsWith("\n--") || t.startsWith("--") || t.startsWith("/*"))) {
            // style2�̏ꍇ�A�R�����g�͒ǉ����Ȃ�
          } else {
            if (t.startsWith("\n--")) {
              if (lastt != null && (lastt.startsWith("--") || lastt.startsWith("\n--"))) {
                // �O�̍s��--�R�����g�s�̏ꍇ�́A���s���o�͂���Ă���̂őO���s�o�͂̓X�L�b�v
              } else {
                // ���s
                view.append(EOL);
                linesize = 0;
              }
              t = t.substring(1);
            }
            if (phase > 0 && linesize <= 1 && !t.startsWith("(") && !t.startsWith(")")
                && !"(".equals(lastt)) {
              // �C���f���g
              view.append(" ");
              linesize ++;
            }
            view.append(t);
            if (t.startsWith("--")) {
              linesize = 0;
            } else {
              linesize += t.length();
            }
            if (linesize > 70 && st.nextChar() != ',' && st.nextChar() != 0
                && linesize + st.getNextString().length() > 78) {
              // ���̕����𑫂���78�𒴂���ꍇ�͉��s����
              view.append(EOL);
              linesize = 0;
            }
          }
          lastt = t;
        }
        ddl = view.toString();
       }
      if (ddl != null) {
        ddl = ddl.replaceFirst("\\s*$", "");
      }
    } catch (SQLException e) {
      log_debug(e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    return ddl;
  }
  
  private String getItemdefinitionId(String processId) {
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    String itemdefinitionId = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      stmt = conn.prepareStatement("SELECT ITEMDEFINITIONID FROM PROCESSMASTER WHERE PROCESSID=?");
      stmt.setString(1, processId);
      rs = stmt.executeQuery();
      if (rs.next()) {
        itemdefinitionId = rs.getString(1);
      }
      
    } catch (SQLException e) {
      
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {}
      }
      if (conn != null) {
        try {
          conn.close();
        } catch(SQLException e) {}
      }
    }
    return itemdefinitionId;
  }
  /**
   * DataSource��DataSource2�̎w�肵���e�[�u�����̃f�[�^���r���\��
   * @param out
   * @param tableName
   * @param option ^�t�B�[���h��|�t�B�[���h��... �ŁA��r�ΏۊO�Ƃ���t�B�[���h�w��
   *                ���̌���WHERE...�ƒ��o�������x�^�w��i�t�B�[���h�I�v�V�����������j
   */
  private void printCompareTable(PrintWriter out, String tableName, String tableName2, String option, String datasource) {
    
    Connection conn1 = null;
    Connection conn2 = null;
    
    PreparedStatement stmt1 = null;
    PreparedStatement stmt2 = null;
    ResultSet rs1 = null;
    ResultSet rs2 = null;
    try {
      conn1 = getConnection();
      conn1.setAutoCommit(false);
      if (datasource != null || tableName2 == null) {
        conn2 = getConnection(datasource);
        if (conn2 == null) {
          out.println("dataSource" + datasource + " ��� Connection ���擾�ł��܂���B");
          return;
        }
        conn2.setAutoCommit(false);
        tableName2 = tableName;
      } else if (tableName2 != null) {
        conn2 = getConnection();
        conn2.setAutoCommit(false);
      }
      Vector excludecols = new Vector();
      String where = null;
      if (option != null) {
        String tmp = option;
        if (tmp.startsWith("^")) {
          StringTokenizer st = new StringTokenizer(tmp.substring(1));
          if (st.hasMoreTokens()) {
            StringTokenizer ost = new StringTokenizer(st.nextToken(), "|");
            while (ost.hasMoreElements()) {
              excludecols.add(ost.nextToken());
            }
            if (st.hasMoreTokens()) {
              tmp = st.nextToken("\0").trim();
            }
          }
        }
        if (tmp.toUpperCase().startsWith("WHERE ")) {
          where = tmp;
        }
      }
      log_debug("option: " + option);
      log_debug("where: " + where);
      Vector pkeys = getPrimaryKeys(tableName);
      StringBuffer sql1 = new StringBuffer();
      StringBuffer sql2 = new StringBuffer();
      sql1.append("SELECT * FROM ").append(tableName);
      sql2.append("SELECT * FROM ").append(tableName2);
      if (where != null) {
        sql1.append(" ").append(where);
        sql2.append(" ").append(where);
      }
      if (pkeys.size() > 0) {
        sql1.append(" ORDER BY ");
        sql2.append(" ORDER BY ");
        for (int i = 0; i < pkeys.size(); ++i) {
          if (i > 0) {
            sql1.append(",");
            sql2.append(",");
          }
          sql1.append(pkeys.get(i));
          sql2.append(pkeys.get(i));
        }
      } else {
        // �v���C�}���L�[�̖����H�e�[�u��
      }
      stmt1 = conn1.prepareStatement(sql1.toString());
      if (conn2 != null) {
        stmt2 = conn2.prepareStatement(sql2.toString());
      }
      rs1 = stmt1.executeQuery();
      if (stmt2 != null) {
        try {
          rs2 = stmt2.executeQuery();
        } catch(SQLException se) {
        }
      }
      if (rs2 == null) {
        // conn2���Ƀe�[�u���������Ǝv����P�[�X
        out.print("<table style=\"width:100%;\">");
        out.print("<tr style=\"background-color:#cccccc;\"><td colspan=\"3\">" + tableName);
        out.print("<tr style=\"background-color:#ffcccc;\"><td>&gt;&gt;<td>2<td><b>�e�[�u�������݂��܂���</b>");
        out.print("</table>");
        return;
      }
      out.print("<table style=\"width:100%;\">");
      out.print("<tr style=\"background-color:#cccccc;\"><td colspan=\"3\">" + tableName);
      if (tableName2 != null) {
        out.print(":" + tableName2);
      }
      if (option != null) {
        out.print(" " + option);
      }
      boolean rs1skip = false;
      boolean rs2skip = false;
      int rs1cnt = 0;
      int rs2cnt = 0;
      int diffcnt = 0;
      int samecnt = 0;
      int rs1only = 0;
      int rs2only = 0;
      for (;;) {
        if (!rs1skip) {
          if (!rs1.next()) {
            break;
          }
          ++rs1cnt;
        }
        if (!rs2skip) {
          if (!rs2.next()) {
            if (!rs1skip) {
              out.print("<tr><td>&lt;<td>1<td>");
              printRS(out, rs1, null);
              ++rs1only;
            }
            break;
          }
          ++rs2cnt;
        }
        // �f�[�^���r
        int cmp = compareRSPKey(rs1, rs2, pkeys);
        if (cmp == 0) {
          // �L�[�̓����s�̓f�[�^���r
          boolean[] diff = getDiffRS(rs1, rs2, excludecols);
          boolean hasDiff = false;
          for (int i = 0; i < diff.length; ++i) {
            if (diff[i]) {
              // �Ⴂ������ꍇ�͏o��
              out.print("<tr><td>|<td>1<td>");
              printRS(out, rs1, diff);
              out.print("<tr><td>|<td>2<td>");
              printRS(out, rs2, diff);
              ++ diffcnt;
              hasDiff = true;
              break;
            }
          }
          if (!hasDiff) {
            ++samecnt;
          }
          // �����i�߂�
          rs1skip = false;
          rs2skip = false;
        } else if (cmp < 0) {
          // �L�[���قȂ�ꍇ(rs2�̕����傫��=rs1�̂ݑ��ݍs)
          ++rs1only;
          out.print("<tr><td>&lt;<td>1<td>");
          printRS(out, rs1, null);
          // rs1�̂ݎ��֐i�߂ČJ��Ԃ�
          rs1skip = false;
          rs2skip = true;
        } else {
          // �L�[���قȂ�ꍇ(rs1�̕����傫��=rs2�̂ݑ��ݍs)
          ++rs2only;
          out.print("<tr><td>&gt;<td>2<td>");
          printRS(out, rs2, null);
          // rs2�̂ݎ��֐i�߂ČJ��Ԃ�
          rs1skip = true;
          rs2skip = false;
        }
      }
      
      while (rs1.next()) {
        ++rs1cnt;
        ++rs1only;
        // conn1�݂̂ɂ���f�[�^�i�c��j
        out.print("<tr><td>&lt;<td>1<td>");
        printRS(out, rs1, null);
      }
      while (rs2.next()) {
        ++rs2cnt;
        ++rs2only;
        // conn2�݂̂ɂ���f�[�^�i�c��j
        out.print("<tr><td>&gt;<td>2<td>");
        printRS(out, rs2, null);
      }
      // �t�b�^
      if (rs1cnt != samecnt || rs2cnt != samecnt) {
        // �Ⴂ������s
        tableName = "<b>" + tableName + "*</b>";
      }
      out.print("<tr style=\"background-color:#ccccff;\"><td colspan=\"3\">" + tableName + ": 1=" + rs1cnt + "�s,2=" + rs2cnt + "�s");
      out.print(" : (����s=" + samecnt + ",���ٍs=" + diffcnt + ",1�̂�=" + rs1only + ",2�̂�=" + rs2only + ")");
      out.print("</table>");
    } catch (SQLException e) {
      printError(out, e);
    } finally {
      if (rs2 != null) {
        try {
          rs2.close();
        } catch(SQLException e) {
        }
      }
      if (rs1 != null) {
        try {
          rs1.close();
        } catch(SQLException e) {
        }
      }
      if (stmt2 != null) {
        try {
          stmt2.close();
        } catch(SQLException e) {
        }
      }
      if (stmt1 != null) {
        try {
          stmt1.close();
        } catch(SQLException e) {
        }
      }
      if (conn2 != null) {
        try {
          conn2.close();
        } catch(SQLException e) {
        }
      }
      if (conn1 != null) {
        try {
          conn1.close();
        } catch(SQLException e) {
        }
      }
    }
  }
  // printCompareTable���Ăяo�����
  private void printRS(PrintWriter out, ResultSet rs, boolean[] diff) throws SQLException {
    for (int i = 0; i < rs.getMetaData().getColumnCount(); ++i) {
      if (i == 0) {
        out.print("<nobr>");
      } else {
        out.print(" ");
      }
      String val = rs.getString(i + 1);
      String colname = rs.getMetaData().getColumnName(i + 1);
      if (diff != null && diff[i]) {
        // �Ⴂ�̂���J����
        out.print("<b title=\"" + colname + "\">");
        if (val != null) {
          out.print(val);
        } else {
          out.print("<i>null</i>");
        }
        out.print("</b>");
      } else {
        // �Ⴂ�̖����J�����i�܂���diff��null�̏ꍇ�j
        if (val != null) {
          out.print(val);
        } else {
          out.print("<i>null</i>");
        }
      }
    }
    out.println("</nobr>");
  }
  // printCompareTable���Ăяo�����
  private static int compareRSPKey(ResultSet rs1, ResultSet rs2, Vector pkeys) throws SQLException {
    for (int i = 0; i < pkeys.size(); ++i) {
      String s1 = rs1.getString((String)pkeys.get(i));
      String s2 = rs2.getString((String)pkeys.get(i));
      int cmp = s1.compareTo(s2);
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }
  // printCompareTable���Ăяo�����
  private static boolean[] getDiffRS(ResultSet rs1, ResultSet rs2, Vector excludecols) throws SQLException {
    int cols = rs1.getMetaData().getColumnCount();
    String[] colnames =  new String[cols];
    for (int i = 0; i < cols; ++i) {
      colnames[i] = rs1.getMetaData().getColumnName(i + 1);
    }
    boolean[] diffcols = new boolean[cols];
    for (int i = 0; i < cols; ++i) {
      String s1 = rs1.getString(colnames[i]);
      String s2 = rs2.getString(colnames[i]);
      if (excludecols.size() > 0) {
        // �����t�B�[���h������ꍇ�́A�t�B�[���h������v����Ζ���
        if (contains(excludecols, colnames[i])) {
          diffcols[i] = false;
          continue;
        }
      }
      if (s1 == null) { // null�̏ꍇ�A����null�ł���Έ�v
        if (s2 == null) {
          diffcols[i] = false;
        } else {
          diffcols[i] = true;
        }
        continue;
      } else if (s2 == null) {
        diffcols[i] = true;
        continue;
      }
      int cmp = s1.compareTo(s2);
      if (cmp != 0) {
        diffcols[i] = true;
      } else {
        diffcols[i] = false;
      }
    }
    return diffcols;
  }

  /**
   * export to �ŕۑ����ꂽ�e�L�X�g�t�@�C�������e�[�u�������擾
   * @param f �t�@�C���I�u�W�F�N�g
   * @return
   */
  private String getTableNameFromTextFileName(File f) {
    String name = f.getName();
    if (name.toUpperCase().endsWith(".TXT")) {
      return name.toUpperCase().substring(0, name.length() - 4);
    }
    return name;
  }

  private String[] getPhysicalColumnNames(Connection conn, String tableName) {
    String[] colnames = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT * FROM " + tableName + " WHERE 1=0");
      rs = stmt.executeQuery();
      colnames = getColumnNames(rs.getMetaData());
      rs.close();
      stmt.close();
    } catch(SQLException se) {
      // �e�[�u�������݂��Ȃ��ꍇ
      log_debug(se.getMessage());
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {}
      }
    }
    return colnames;
  }
  private Vector getNotNullColumnNames(Connection conn, String tableName) {
    Vector colnames = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT * FROM " + tableName + " WHERE 1=0");
      rs = stmt.executeQuery();
      colnames = getNotNullColumnNames(rs.getMetaData());
      rs.close();
      stmt.close();
    } catch(SQLException se) {
      // �e�[�u�������݂��Ȃ��ꍇ
      log_debug(se.getMessage());
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {}
      }
    }
    if (colnames == null) {
      return new Vector();
    }
    return colnames;
  }
  private int[] getColumnTypes(ResultSetMetaData meta) {
    int[] types = null;
    try {
      types = new int[meta.getColumnCount()];
      for (int i = 0; i < meta.getColumnCount(); i++) {
        types[i] = meta.getColumnType(i + 1);
      }
    } catch(SQLException e) {
    }

    return types;
  }

  private String[] getColumnNames(ResultSetMetaData meta) {
    String[] names = null;
    try {
      names = new String[meta.getColumnCount()];
      for (int i = 0; i < meta.getColumnCount(); i++) {
        names[i] = meta.getColumnName(i + 1);
      }
    } catch(SQLException e) {
    }

    return names;
  }

  private Vector getNotNullColumnNames(ResultSetMetaData meta) {
    Vector names = new Vector();
    try {
      for (int i = 0; i < meta.getColumnCount(); i++) {
        if (meta.isNullable(i + 1) == ResultSetMetaData.columnNoNulls) {
          String name = meta.getColumnName(i + 1);
          names.add(name);
        }
      }
    } catch(SQLException e) {
    }

    return names;
  }

  private int getColumnTypeByName(int[] types, String[] names, String name) {
    if (types == null || names == null) {
      return -1;
    }
    for (int i = 0; i < names.length; i++) {
      if (name.compareToIgnoreCase(names[i]) == 0) {
        return types[i];
      }
    }
    return -1;
  }


  private void printLog(PrintWriter out, String command) {
    StringTokenizer st = new StringTokenizer(command);
    String cmd = st.nextToken(); // show ���X�L�b�v
    String target = null;
    if (st.hasMoreTokens()) {
      target = st.nextToken();
    }
    File errorLog = new File(appPath, "logs/errorlog.txt"); // �f�t�H���g�G���[���O
    out.println("<pre>");
    if (target == null) {
      
      out.print("�Ώۂ��w�肵�Ă��������B(log");
      for (int i = 0; i < traceLogs.length; ++i) {
        out.print(" | sqllog" + (i + 1));
      }
      if (errorLog.exists()) {
        // �f�t�H���g�G���[���O�t�@�C�������݂���ꍇ
        out.print(" | errorlog");
      }
      out.println(")");
    } else if (target.equalsIgnoreCase("log")) {
      if (cmd.equalsIgnoreCase("clear")) {
        debugLog.clear();
        out.println("log���N���A���܂����B");
      } else {
        // �������O�̕\��
        synchronized (debugLog) {
          for (Iterator ite = debugLog.iterator(); ite.hasNext(); ) {
            out.println(ite.next());
          }
        }
      }
    } else if (target.toLowerCase().startsWith("sqllog") || target.toLowerCase().startsWith("log")) {
      String no = target.substring(target.length() - 1); // �Ō�̂P��
      int index = Integer.parseInt(no) - 1;
      if (cmd.equalsIgnoreCase("clear")) {
        traceLogs[index].clear();
        out.println("sqllog1���N���A���܂����B");
      } else {
        // tracelogX���O�̕\��
        if (index >= 0 && index < traceLogs.length && traceLogs[index] != null) {
          for (Iterator ite = traceLogs[index].iterator(); ite.hasNext(); ) {
            out.println(ite.next());
          }
        }
      }
    } else if (target.toLowerCase().equals("errorlog") && errorLog.exists()) {
      // �G���[���O��\��
      long seekp = 0;
      int maxSize = 1024 * 1024;
      if (errorLog.length() > maxSize) {
        // 1MB�ȏ゠��ꍇ
        seekp = errorLog.length() - maxSize;
      }
      BufferedReader br = null;
      try {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(errorLog), "UTF-8"));
        if (seekp > 0) {
          br.skip(seekp);
        }
        String line = null;
        while ((line = br.readLine()) != null) {
          out.println(line.replaceAll("<", "&lt;"));
        }
      } catch (Exception e) {
        printError(out, e);
      } finally {
        if (br != null) {
          try {
            br.close();
          } catch (IOException e) {}
        }
      }
      
    }

    out.println("</pre>");
    
  }
  
  private void printRestart(PrintWriter out, String command) {
    StringTokenizer st = new StringTokenizer(command);
    String cmd = st.nextToken();
    out.println("<pre>");
    if (restartCommand == null || restartCommand.trim().length() == 0) {
      out.println("���X�^�[�g�R�}���h���ݒ肳��Ă��܂���Bconfig�Őݒ肵�Ă��������B");
    } else {
      try {
        out.println("���X�^�[�g�R�}���h���J�n���܂��B");
        out.println(restartCommand);
        out.flush();
//        Process process = Runtime.getRuntime().exec(restartCommand);
//        int ret = process.waitFor();
        int ret = JavaExecutor.execute(new File(contextRoot, "WEB-INF/lib/mbb_coretools.jar").getAbsolutePath(), restartCommand, true);
        out.println("�I���R�[�h=" + ret);
      } catch (InterruptedException e) {
        printError(out, e);
      } catch (IOException e) {
        printError(out, e);
      }
    }
    out.println("</pre>");
  }

  
  /**
   * �T�[�o��̃t�H���_���G�N�X�|�[�g���ꂽ�t�@�C�����C���|�[�g�������Ȃ��B
   * @param out
   * @param inputdir
   * @param autocommit
   * @param replace
   */
  private void printImportFromFile(PrintWriter out, String inputdir, String autocommit, boolean replace) {
    Connection conn = null;
    File[] tablefiles = null;
    int cid = inputdir.indexOf(";");
    String replaceCompanyId = null;
    if (cid > 0) {
      replaceCompanyId = inputdir.substring(cid + 1).trim();
      inputdir = inputdir.substring(0, cid).trim();
    }
    File dir = new File(inputdir);
    if (!dir.exists()) {
      out.println("cannot open directory [" + inputdir + "]");
      return;
    }
    File[] list = dir.listFiles();
    tablefiles = new File[list.length];
    System.arraycopy(list, 0, tablefiles, 0, list.length);
    out.println("import " + list.length + " files.<br>");
    String line = "";
    int[] columnTypes = null;
    String[] columnNames = null;

    BufferedReader reader = null;
    try {
      conn = getConnection();
      setAutoCommit(conn, autocommit);
      TreeSet fts = new TreeSet(Arrays.asList(tablefiles));
      for (Iterator ite = fts.iterator(); ite.hasNext(); ) {
        File f = (File)ite.next();
        String tn = getTableNameFromTextFileName(f);
        if (tn.charAt(0) >= '0' && tn.charAt(0) <= '9') {
          // ��������n�܂�ꍇ�́A�s���I�h�܂ŏ���
          tn = tn.substring(tn.indexOf(".") + 1);
        }
        String fn = f.getPath();
        if (fn.length() >= 4 && fn.substring(fn.length() - 4).compareToIgnoreCase(".txt") != 0) {
          out.println(" (skip " + fn + ")<br>");
          continue;
        }
        out.println("import from " + fn + " into " + tn + "<br>");
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        line = reader.readLine();
        Vector colnames = getTabStrings(line);
        Statement stmt = conn.createStatement();
        try {
          ResultSet rs = stmt.executeQuery("SELECT * FROM " + tn);
          columnTypes = getColumnTypes(rs.getMetaData());
          columnNames = getColumnNames(rs.getMetaData());
          rs.close();
        } catch(Exception e) {
          // �e�[�u�������݂��Ȃ��Ǝv����ꍇ
          if (createFromFile(conn, inputdir, tn, autocommit)) {
            out.println("(crate " + tn + ") ");
            out.flush();
          }
          try {
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + tn);
            columnTypes = getColumnTypes(rs.getMetaData());
            columnNames = getColumnNames(rs.getMetaData());
            rs.close();
          } catch(Exception ex) {
          }
        }
        if (replace) {
          String sql = "DELETE FROM " + tn;
          try {
            if (!"1".equals(autocommit)) {
              try {
                conn.commit();
              } catch (SQLException e) {}
              conn.setAutoCommit(false);
            }
            try {
              int delcnt = stmt.executeUpdate(sql);
              stmt.close();
              if (!"1".equals(autocommit)) {
                conn.commit();
              }
              if (delcnt > 0) {
                out.println("&nbsp;&nbsp;delete " + delcnt + " records.<br>");
              }
            } catch (SQLException e) {}
            out.flush();
          } catch (SQLException e) {
            printError(out, sql);
            printError(out, e);
          }
        }

        if (line != null) {
          Timestamp ts = new Timestamp(System.currentTimeMillis());
          String insertsql = "INSERT INTO " + tn;
          String insertFields = " (";
          String insertvals = "";
          int instcnt = 0;
          int[] types = new int[colnames.size()];
          for (int i = 0, j = 0; i < colnames.size(); i++) {
            String colnm = (String)colnames.get(i);
            int t = getColumnTypeByName(columnTypes, columnNames, colnm);
            if (t != -1) {
              if (j != 0) {
                insertFields = insertFields + ",";
                insertvals = insertvals + ",";
              }
              insertFields = insertFields + colnm;
              insertvals = insertvals + "?";
              types[j] = t;
              j++;
            }
          }
//          if ("DATAFIELDMASTER".equals(tn)) {
//            insertFields = "";
//          } else {
//            insertFields = insertFields + ")";
//          }
          insertFields = insertFields + ")";
          insertsql = insertsql + insertFields + " VALUES (" + insertvals + ")";
          //out.println(insertsql + "<br>");
          line = reader.readLine();
          if (conn.isClosed()) {
            out.println("<font color=\"" + ERROR_COLOR + "\">get connection...</font><br>");
            conn = getConnection();
            conn.setAutoCommit(false);
          }
          if (!"1".equals(autocommit)) {
            try {
              conn.commit();
            } catch (SQLException e) {}
            conn.setAutoCommit(false);
          }
          PreparedStatement insstmt = conn.prepareStatement(insertsql);
          while(line != null) {
            insstmt.clearParameters();
            Vector vals = getTabStrings(line);
            for (int i = 0, j = 0; i < vals.size(); i++) {
              // �p�����[�^�ɒl���Z�b�g
              String colnm = (String)colnames.get(i);
              int colt = getColumnTypeByName(columnTypes, columnNames, colnm);
              if (colt != -1) {
                if (replaceCompanyId != null && colnm.equalsIgnoreCase("COMPANYID")) {
                  // ��ЃR�[�h�u���w��̏ꍇ
                  insstmt.setString(j + 1, replaceCompanyId);
                } else {
                  String val = DbAccessUtils.unescape((String)vals.get(i));
                  if (colt == Types.TIMESTAMP) {
                    if (!isDB2(0)) {
                      Timestamp t = null;
                      try {
                        t = Timestamp.valueOf(val);
                      } catch(Exception e) {
                        t = ts;
                      }
                      String tsval = t.toString();
                      if (tsval.length() > 23) {
                        tsval = tsval.substring(0, 23);
                      }
                      insstmt.setString(j + 1, tsval);
                    } else {
                      Timestamp t = null;
                      try {
                        t = Timestamp.valueOf(val);
                      } catch(Exception e) {
                        t = ts;
                      }
                      insstmt.setTimestamp(j + 1, t);
                    }
                  } else {
                    if (colt == Types.CHAR || colt == Types.VARCHAR || colt == Types.LONGVARCHAR) {
                      if (val != null && val.length() == 0) {
                        val = " ";
                      }
                      insstmt.setString(j + 1, val);
                    } else {
                      if (val.length() == 0) {
                        insstmt.setNull(j + 1, colt);
                      } else {
                        if (types[j] == java.sql.Types.INTEGER || types[j] == java.sql.Types.SMALLINT || types[j] == java.sql.Types.TINYINT) {
                          if ("true".equalsIgnoreCase(val)) {
                            val = "1";
                          } else if ("false".equalsIgnoreCase(val)) {
                            val = "0";
                          }
                          try {
                            if (val != null && val.trim().length() == 0) {
                              insstmt.setInt(j + 1, 0);
                            } else {
                              insstmt.setInt(j + 1, Integer.parseInt(val));
                            }
                          } catch (NumberFormatException e) {
                            insstmt.setString(j + 1, val);
                          }
                        } else if (types[j] == java.sql.Types.NUMERIC) {
                          try {
                            if (val != null && val.trim().length() == 0) {
                              insstmt.setInt(j + 1, 0);
                            } else {
                              insstmt.setInt(j + 1, Integer.parseInt(val));
                            }
                          } catch (NumberFormatException e) {
                            try {
                              insstmt.setBigDecimal(j + 1, new BigDecimal(val));
                            } catch (NumberFormatException e2) {
                              insstmt.setString(j + 1, val);
                            }
                          }
                        } else if (types[j] == java.sql.Types.BLOB) {
                          insstmt.setBinaryStream(j + 1, new ByteArrayInputStream(val.getBytes()), val.getBytes().length);
                        } else {
                          insstmt.setString(j + 1, val);
                        }
                      }
                    }
                  }
                }
                j ++;
              }
            }
            if (insstmt.executeUpdate() == 1) {
              // �G���[?
              instcnt ++;
            }
            line = reader.readLine();
            // autocommit�łȂ��ꍇ�́A10000���R�~�b�g
            if ( (instcnt % 10000) == 0 && !autocommit.equals("1")) {
              insstmt.close();
              if (!"1".equals(autocommit)) {
                conn.commit();
                conn.setAutoCommit(false);
              }
              insstmt = conn.prepareStatement(insertsql);
            }
          }
          if (insstmt != null) {
            insstmt.close();
          }
          if (instcnt > 0) {
            out.println("&nbsp;&nbsp;insert " + instcnt + " records.<br>");
            out.flush();
            if (!"1".equals(autocommit)) {
              conn.commit();
            }
          }
        }
        reader.close();
      }
      out.flush();
      conn.commit();
    } catch (SQLException e) {
      out.println(line);
      printError(out, e);
    } catch (IOException e) {
      out.println(line);
      printError(out, e);
    } catch (Exception e) {
      out.println(line);
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        }
        catch(SQLException e) {
        }
      }
      if (reader != null) {
        try {
          reader.close();
        } catch(Exception e) {

        }
      }
    }
  }

  private void printError(PrintWriter out, String msg) {
    try {
      out.print("<pre><font color=\"" + ERROR_COLOR + "\">");
      out.println(msg);
      out.print("</font>");
      out.println("</pre>");
      out.flush();
    } catch(Exception ee) {
    }
  }
  private void printError(PrintWriter out, Exception e) {
    try {
      out.print("<pre><font color=\"" + ERROR_COLOR + "\">");
      out.println(e.getMessage());
      out.println("<br>");
      e.printStackTrace(out);
      out.print("</font>");
      out.println("</pre>");
      out.flush();
    } catch(Exception ee) {
    }
  }
  
  private void printCount(PrintWriter out, String command) {
    //
    out.println("<pre>");
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "count"���X�L�b�v
      String option = null;
      if (st.hasMoreTokens()) {
        option = command.substring(6).trim();
      }
      TreeMap pTables = new TreeMap();
      // �����e�[�u���ꗗ���擾
      ResultSet rs = conn.getMetaData().getTables(null, schemas[0], "%", new String[]{"TABLE"});
      while (rs.next()) {
        String table_name = rs.getString("TABLE_NAME").toUpperCase();
        if (table_name != null && isSystemTable(table_name)) {
          continue;
        }
        pTables.put(table_name, new Vector());
      }
      rs.close();
      for (Iterator ite = pTables.keySet().iterator(); ite.hasNext(); ) {
        String tableId = (String)ite.next();
        StringBuffer sql = new StringBuffer();
        if (option == null) {
          sql.append("SELECT COUNT(*) FROM ").append(tableId);
        } else if (option.indexOf("=") > -1) {
          sql.append("SELECT COUNT(*) FROM ").append(tableId).append(" WHERE ").append(option);
        } else if (option.toUpperCase().startsWith("GROUP BY")) {
          String grp = option.substring(9);
          sql.append("SELECT ").append(grp).append(",COUNT(*) FROM ").append(tableId).append(" ").append(option);
        }
        out.println("[" + sql + "]");
        Statement stmt = conn.createStatement();
        try {
          ResultSet crs = stmt.executeQuery(sql.toString());
          //out.println("[" + tableId + "]");
          while (crs.next()) {
            for (int i = 0; i < crs.getMetaData().getColumnCount(); ++i) {
              if (i > 0) {
                out.print(" ");
              }
              out.print(crs.getString(i + 1));
            }
            out.println("");
          }
          crs.close();
        } catch(SQLException e) {
          out.println(e.getMessage());
        }
        stmt.close();
        out.flush();
      }
    } catch(Exception e) {
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {
        }
      }
    }
    out.println("</pre>");
    
  }

  /**
   *  �����e�[�u���Ƙ_���e�[�u��(TABLEMASTER/TABLELAYOUTMASTER)�̐������`�F�b�N
   * @param out
   * @param command
   */
  private void printCheck(PrintWriter out, HttpServletRequest request, String command) {
    //
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "check"���X�L�b�v
      String cmd = null;
      if (st.hasMoreTokens()) {
        cmd = st.nextToken();
      }
      if (cmd != null && cmd.equalsIgnoreCase("TABLE")) {
        String table = null;
        if (st.hasMoreTokens()) {
          table = st.nextToken();
        }
        if (table != null && table.trim().length() > 0) {
          // �P��e�[�u���`�F�b�N
          printCheckTable(conn, out, table.toUpperCase());
          StringBuffer comments = new StringBuffer();
          ClassManager entityClassManager = new ClassManager(appPath);
          int[] r = checkTableLayout(entityClassManager, conn, table, comments);
          if (r[0] != 0 || r[1] > 0 || r[2] > 0) {
            out.println("<pre>�G���[���F" + comments + "</pre>");
          }
        } else {
          // �S�e�[�u���`�F�b�N
          printCheckTables(conn, out, request);
        }
      } else if (cmd != null && cmd.equalsIgnoreCase("FUNCTION")) {
        printCheckFunctions(conn, out);
      } else if (cmd != null && cmd.equalsIgnoreCase("CLASS")) {
        printCheckClasses(conn, out);
      }
      out.flush();
    } catch(Exception e) {
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {
        }
      }
    }
    
  }
  
  /**
   * �����e�[�u���Ƙ_���e�[�u��(TABLELAYOUTMASTER)�̔�r
   * check table -> printCheck()���Ă΂��
   * @param conn DB�R�l�N�V����
   * @param out�@���b�Z�[�W�o�͐�
   * @throws SQLException
   */
  private void printCheckTables(Connection conn, PrintWriter out, HttpServletRequest request) throws SQLException {
    boolean createtable = request.getParameter("createtable") != null;
    if (createtable) {
      // �e�[�u���č\�z
      String[] tables = request.getParameterValues("table");
      if (tables != null && tables.length > 0) {
        out.println("<pre>");
        Connection conn2 = null;
        try {
          conn2 = getConnection();
          conn2.setAutoCommit(false);
          ClassManager entityClassManager = new ClassManager(appPath);
          for (int i = 0; i < tables.length; ++i) {
            if (tables[i].startsWith("V") && tables[i].indexOf("_") != -1) {
              // V����n�܂�_���܂܂��ꍇ��VIEW�Ɣ��f���ăX�L�b�v����
              out.println("�e�[�u����`[" + tables[i] + "]�̍č\�z�̓X�L�b�v���܂����B\n");
              continue;
            }
            StringBuffer emsg = new StringBuffer();
            int[] err = checkTableLayout(entityClassManager, conn2, tables[i], null); // �����e�[�u���Ɣ�r
            if (createTableFromTableLayoutMaster(conn2, tables[i], tables[i], emsg, getLoginInfos(request))) {
              out.println("�e�[�u��[" + tables[i] + "]���č\�z���܂����B\n");
              if (emsg.length() > 0) {
                out.println("<font color=\"" + ERROR_COLOR + "\">" + tables[i] + ":" + emsg + "</font>\n");
              }
            } else {
              out.println("<font color=\"" + ERROR_COLOR + "\">�e�[�u��[" + tables[i] + "]�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font>\n");
            }
            if (err[1] == 1 || err[1] == 2) {
              // ���̃e�[�u�������݂��Ȃ����ύX�̂���ꍇ
              emsg = new StringBuffer();
              if (createTableFromTableLayoutMaster(conn2, tables[i], DbAccessUtils.getNameTableName(tables[i]), emsg, getLoginInfos(request))) {
                out.println("�e�[�u��[" + DbAccessUtils.getNameTableName(tables[i]) + "]���č\�z���܂����B\n");
                if (emsg.length() > 0) {
                  out.println("<font color=\"" + ERROR_COLOR + "\">" + DbAccessUtils.getNameTableName(tables[i]) + ":" + emsg + "</font>\n");
                }
              } else {
                out.println("<font color=\"" + ERROR_COLOR + "\">�e�[�u��[" + DbAccessUtils.getNameTableName(tables[i]) + "]�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font>\n");
              }
            }
            if (err[2] == 1 || err[2] == 2) {
              // ���e�[�u�������݂��Ȃ����ύX�̂���ꍇ
              emsg = new StringBuffer();
              if (createTableFromTableLayoutMaster(conn2, tables[i], DbAccessUtils.getInfoTableName(tables[i]), emsg, getLoginInfos(request))) {
                out.println("�e�[�u��[" + DbAccessUtils.getInfoTableName(tables[i]) + "]���č\�z���܂����B\n");
                if (emsg.length() > 0) {
                  out.println("<font color=\"" + ERROR_COLOR + "\">" + DbAccessUtils.getInfoTableName(tables[i]) + ":" + emsg + "</font>\n");
                }
              } else {
                out.println("<font color=\"" + ERROR_COLOR + "\">�e�[�u��[" + DbAccessUtils.getInfoTableName(tables[i]) + "]�̍쐬�Ɏ��s���܂����B(" + emsg + ")</font>\n");
              }
            }
          }
          out.println("</pre>");
          out.flush();
          conn2.commit();
        } finally {
          if (conn2 != null) {
            try {
              conn2.close();
            } catch (SQLException e) {}
          }
        }
      }
    }

    checkTables(appPath, conn, out, schemas[0], null, null, true, false);
  }
  
  /**
   * �����e�[�u���Ƙ_���e�[�u��(TABLELAYOUTMASTER)�̔�r
   * @param conn DB�R�l�N�V����
   * @param out�@���b�Z�[�W�o�͐�
   * @param schema�@�ΏۃX�L�[�}
   * @param errorInfo �G���[���߂���
   * @param info ���o�̓t���O(true�̏ꍇ���o�́Afalse�̏ꍇ�G���[�̂�)
   * @throws SQLException
   */
  // �ߋ��o�[�W�����݊��p�i���A���A���������삵�Ȃ��B�R���p�C���G���[���p�j
  public static void checkTables(Connection conn, PrintWriter out, String schema, String filter, Vector errorInfo, boolean info, boolean skipSystem) throws SQLException {
    checkTables(".", conn, out, schema, filter, errorInfo, info, skipSystem);
  }
  public static void checkTables(String appPath, Connection conn, PrintWriter out, String schema, String filter, Vector errorInfo, boolean info, boolean skipSystem) throws SQLException {
    TreeMap pTables = new TreeMap(); // �S�����e�[�u��
    TreeMap pViews = new TreeMap(); // �S����VIEW
    TreeMap lTables = new TreeMap(); // �S�_���e�[�u��
    TreeMap tableLayouts = new TreeMap();
    Hashtable tablePackageId = new Hashtable();
    ClassManager entityClassManager = new ClassManager(appPath);
    // �����e�[�u���ꗗ���擾
    if (filter == null || filter.trim().length() == 0) {
      filter = "%";
    }
    Vector systemTables = null;
    if (skipSystem) {
      systemTables = getObjectNames(conn, null, null, OBJ_TYPE_MTABLE);
    }
    ResultSet rs = null;
    try {
      rs = conn.getMetaData().getTables(null, schema, filter, new String[]{"TABLE"});
      while (rs.next()) {
        String table_name = rs.getString("TABLE_NAME").toUpperCase();
        if (table_name != null && isSystemTable(table_name)) {
          continue;
        }
        if (skipSystem && systemTables != null) {
          if (systemTables.contains(table_name)) {
            continue;
          }
        }
        pTables.put(table_name, new Vector());
      }
      rs.close();
      rs = null;
      rs = conn.getMetaData().getTables(null, schema, filter, new String[]{"VIEW"});
      while (rs.next()) {
        String table_name = rs.getString("TABLE_NAME").toUpperCase();
        if (table_name != null && isSystemTable(table_name)) {
          continue;
        }
        if (skipSystem && systemTables != null) {
          if (systemTables.contains(table_name)) {
            continue;
          }
        }
        pViews.put(table_name, new Vector());
      }
    } catch (SQLException e) {
    } finally {
      if (rs != null) {
        try {
          rs.close();
          rs = null;
        } catch (SQLException e) {}
      }
    }
    if (pTables.size() == 0 && filter.equals("%")) {
      // schema���T�|�[�g���Ȃ��P�[�X�H
      try {
        rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
        while (rs.next()) {
          String table_name = rs.getString("TABLE_NAME").toUpperCase();
          if (table_name != null && isSystemTable(table_name)) {
            continue;
          }
          pTables.put(table_name, new Vector());
        }
        rs.close();
        rs = null;
        rs = conn.getMetaData().getTables(null, null, "%", new String[]{"VIEW"});
        while (rs.next()) {
          String table_name = rs.getString("TABLE_NAME").toUpperCase();
          if (table_name != null && isSystemTable(table_name)) {
            continue;
          }
          pViews.put(table_name, new Vector());
        }
      } catch (SQLException e) {
      } finally {
        if (rs != null) {
          try {
            rs.close();
            rs = null;
          } catch (SQLException e) {}
        }
      }
    }
    // DBACCESS�������I�Ɏg�p����e�[�u���͏��O
    if (pTables.containsKey(DBACCESS_IMPORTLOG)) {
      pTables.remove(DBACCESS_IMPORTLOG);
    }
    if (pTables.containsKey(DBACCESS_CONFIG)) {
      pTables.remove(DBACCESS_CONFIG);
    }
    // �_���e�[�u�����擾
    DbAccessUtils.getTableDefInfo(conn, lTables, tablePackageId);
    // ���C�A�E�g���擾
    String sql ="SELECT TABLEID,DATAFIELDID,DATAFIELDORDER,DATAFIELDCLASS"
        + ",(SELECT PHYSICALFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) PHYSICALFIELDID"
        + " FROM TABLELAYOUTMASTER a WHERE TABLEID LIKE ? ORDER BY TABLEID, DATAFIELDORDER";
    PreparedStatement pstmt = null;
    Hashtable nullDataFields = new Hashtable();
    try {
      pstmt = conn.prepareStatement(sql);
      pstmt.setString(1, filter);
      rs = pstmt.executeQuery();
      while (rs.next()) {
        String tableId = rs.getString(1).toUpperCase();
        String dataFieldId = rs.getString(2).toUpperCase();
        String dataFieldClass = rs.getString(4).toUpperCase();
        String physicalFieldId = rs.getString(5);
        if (physicalFieldId != null) {
          physicalFieldId = physicalFieldId.toUpperCase();
        } else {
          String nullDataField = (String)nullDataFields.get(tableId);
          if (nullDataField == null) {
            nullDataField = dataFieldId;
          } else {
            nullDataField = nullDataField + "," + dataFieldId;
          }
          nullDataFields.put(tableId, nullDataField);
        }
        Vector dataFields = (Vector)tableLayouts.get(tableId);
        if (dataFields == null) {
          dataFields = new Vector();
        }
        dataFields.add(new String[]{dataFieldId, dataFieldClass, physicalFieldId});
        tableLayouts.put(tableId, dataFields);
      }
    } catch (SQLException e) {
    } finally {
      if (rs != null) {
        try {
          rs.close();
          rs = null;
        } catch (SQLException e) {}
      }
      if (pstmt != null) {
        try {
          pstmt.close();
        } catch (SQLException e) {}
      }
    }
    
    Iterator pite = pTables.keySet().iterator();
    Iterator lite = lTables.keySet().iterator();
    String pTableId = null;
    String lTableId = null;
    if (pite.hasNext()) {
      pTableId = (String)pite.next();
    }
    if (lite.hasNext()) {
      lTableId = (String)lite.next();
    }
    
    // �_���e�[�u�����ꗗ��tmpLayouts�ɃR�s�[���A�������C�A�E�g�Ɣ�r�����珜������
    TreeMap tmpTableLayouts = (TreeMap)tableLayouts.clone();
    if (out != null) {
      out.println("<span class=\"text\">�����e�[�u����=" + pTables.size() + "</span><br>");
      out.println("<span class=\"text\">�_���e�[�u����=" + lTables.size() + "</span><br>");
      out.println("<script>function checkTableSelectUnSelectALL(){var checkedToBe = document.getElementById('checkTableSelectUnselectAll').checked;var allElements = document.getElementsByTagName('*'); for (var i=0; i< allElements.length; i++ ) { if (allElements[i].className == 'checkTableCheckBox' ) {allElements[i].checked = checkedToBe; } }}</script>");
      // 2014/07/01 EXCEL�ŏo�͂���@�\��ǉ� start
      out.println("<table id=\"tablelist\"><tr style=\"background-color:#cccccc;\"><td><input type=\"checkbox\" id=\"checkTableSelectUnselectAll\" onclick=\"checkTableSelectUnSelectALL()\"></td><td>TABLEID</td><td>PACKAGEID</td><td>STATUS</td><td></td></tr>");
      // 2014/07/01 EXCEL�ŏo�͂���@�\��ǉ� end
    }
    if (errorInfo != null && info) {
      errorInfo.add("�����e�[�u����=" + pTables.size());
      errorInfo.add("�_���e�[�u����=" + lTables.size());
    }
    while (true) {
      // �����e�[�u������pTableId�ɃZ�b�g���Ȃ��烋�[�v����
      StringBuffer comments = new StringBuffer();
      if (pTableId == null && lTableId == null) {
        // ����null�ɂȂ����ꍇ�͏I��
        break;
      }
      if (pTableId != null) {
        // �����e�[�u����NAME/INFO���X�L�b�v
        while (pTableId != null && (pTableId.endsWith("NAME") || pTableId.endsWith("INFO"))) {
          String base = pTableId.substring(0, pTableId.length() - 4);
          if (pTables.get(base) != null || pTables.get(base + "MASTER") != null) {
            // NAME/INFO�ŏI���e�[�u���ɑ΂���
            if (lTables.get(pTableId) != null) {
              // �_���e�[�u���ɂ���΁AINFO/NAME�ł��`�F�b�N�ΏۂƂ���(�ʏ�͂Ȃ��͂�)
              break;
            }
            if (pite.hasNext()) {
              pTableId = (String)pite.next();
            } else {
              pTableId = null;
            }
            continue;
          } else {
            break;
          }
        }
      }
      int cmp = 0;
      String pViewId = null;
      if (pTableId != null) {
        // �����e�[�u���ɑ΂�
        if (lTableId != null) {
          // �_���e�[�u��ID��r
          cmp = pTableId.compareTo(lTableId);
          if (cmp != 0) {
            if (pViews.containsKey(lTableId)) {
              // VIEW�ɑ���
              cmp = 0;
              pViewId = lTableId;
            }
          }
        } else {
          // �_���e�[�u���I��
          cmp = -1;
        }
      } else {
        // �����e�[�u���I��
        cmp = 1;
        if (lTableId != null) {
          if (pViews.containsKey(lTableId)) {
            // VIEW�ɑ���
            cmp = 0;
            pViewId = lTableId;
          }
        }
      }
      if (cmp == 0) {
        // �����e�[�u���Ƙ_���e�[�u����ID����v�����ꍇ���C�A�E�g�`�F�b�N
        int[] err = new int[3];
        if (tmpTableLayouts.get(lTableId) == null) {
          comments.append("�_���e�[�u�����C�A�E�g��񂪑��݂��܂���");
        } else {
          String nullDataField = null;
          if (pViewId != null) {
            err = checkTableLayout(entityClassManager, conn, pViewId, comments);
            nullDataField = (String)nullDataFields.get(pViewId);
          } else {
            err = checkTableLayout(entityClassManager, conn, pTableId, comments);
            nullDataField = (String)nullDataFields.get(pTableId);
          }
          if (nullDataField != null) {
            if (comments.length() > 0) {
              comments.append(",");
            }
            comments.append("�f�[�^���ڒ�`[" + nullDataField + "]�����݂��܂���");
            err = new int[] {-1, -1, -1};
          }
        }
        tmpTableLayouts.remove(lTableId);
        if (comments.length() == 0) {
          comments.append("OK");
        }
        String msg = comments.toString();
        if (errorInfo != null) {
          if (info || err[0] != 0) {
            errorInfo.add(lTableId + ": " + msg);
          }
        }
        if (err[0] != 0 || err[1] > 0 || err[2] > 0) {
          msg = "<font color=\"" + ERROR_COLOR + "\">" + msg + "</font>";
        }
        String packageId = "";
        String pkgTableId = pTableId;
        if (pViewId != null) {
          pkgTableId = pViewId;
        }
        if (tablePackageId.containsKey(pkgTableId)) {
          packageId = ((String[])tablePackageId.get(pkgTableId))[0];
          String useTableClass = ((String[])tablePackageId.get(lTableId))[1];
          if (!useTableClass.equals("1")) {
            packageId = "<span style=\"color:color:" + ERROR_COLOR + ";\" title=\"�p�b�P�[�WID���s���ł�\">" + packageId + "</span>";
          }
        }
        if (out != null) {
          out.print("<tr><td><input type=\"checkbox\" class=\"checkTableCheckBox\" name=\"table\" value=\"" + pkgTableId + "\"></td>");
          out.println("<td>" + pkgTableId + "</td><td>" + packageId + "</td><td>" + msg + "</td><td></td></tr>");
        }
        // �����e�[�u��ID�E�_���e�[�u��ID�����֐i�߂�
        if (pViewId == null) {
          if (pite.hasNext()) {
            pTableId = (String)pite.next();
          } else {
            pTableId = null;
          }
        }
        if (lite.hasNext()) {
          lTableId = (String)lite.next();
        } else {
          lTableId = null;
        }
      } else if (cmp < 0) {
        // �����e�[�u���̂ݑ���
        String packageId = "";
        if (tablePackageId.containsKey(pTableId)) {
          packageId = ((String[])tablePackageId.get(pTableId))[0];
          String useTableClass = ((String[])tablePackageId.get(lTableId))[1];
          if (!useTableClass.equals("1")) {
            packageId = "<span style=\"color:" + ERROR_COLOR + ";\" title=\"�p�b�P�[�WID���s���ł�\">" + packageId + "</span>";
          }
        }
        if (out != null) {
          out.print("<tr><td><input type=\"checkbox\" name=\"table\" value=\"" + pTableId + "\" disabled></td>");
          out.println("<td>" + pTableId + "</td><td>" + packageId + "</td><td>�����e�[�u���̂ݑ��݂��܂�</td><td>" + comments + "</td></tr>");
        }
        if (errorInfo != null && info) {
          errorInfo.add(pTableId + ": �����e�[�u���̂ݑ��݂��܂�");
        }
        // �����e�[�u��ID�����֐i�߂�
        if (pite.hasNext()) {
          pTableId = (String)pite.next();
        } else {
          pTableId = null;
        }
      } else {
        // �_���e�[�u���̂ݑ���
        boolean skipTable = false;
        if (skipSystem && systemTables != null) {
          if (systemTables.contains(lTableId)) {
            // �V�X�e���e�[�u��
            skipTable = true;
          }
        }
        if (!skipTable) {
          if (tmpTableLayouts.get(lTableId) == null) {
            comments.append("���C�A�E�g��񂪑��݂��܂���");
          }
          tmpTableLayouts.remove(lTableId);
          String packageId = "";
          if (tablePackageId.containsKey(lTableId)) {
            packageId = ((String[])tablePackageId.get(lTableId))[0];
            String useTableClass = ((String[])tablePackageId.get(lTableId))[1];
            if (!useTableClass.equals("1")) {
              packageId = "<span style=\"color:" + ERROR_COLOR + ";\" title=\"�p�b�P�[�WID���s���ł�\">" + packageId + "</span>";
            }
          }
          if (out != null) {
            String nullDataField = (String)nullDataFields.get(lTableId);
            String checked = "";
            if (nullDataField != null) {
              if (comments.length() > 0) {
                comments.append(",");
              }
              comments.append("�f�[�^���ڒ�`[" + nullDataField + "]�����݂��܂���");
            } else {
              if (comments.length() == 0 && !lTableId.startsWith("V_")) {
                // ���̃G���[�������ꍇ�̓f�t�H���g�`�F�b�N�Ƃ���
                checked = " checked";
              }
            }
            out.print("<tr><td><input type=\"checkbox\" class=\"checkTableCheckBox\" name=\"table\" value=\"" + lTableId + "\"" + checked + "></td>");
            String msg = "�_���e�[�u���̂ݑ��݂��܂�";
            if (comments.length() > 0) {
              msg = msg + "," + comments.toString();
            }
            msg = "<font color=\"" + ERROR_COLOR + "\">" + msg + "</font>";
            out.println("<td>" + lTableId + "</td><td>" + packageId + "</td><td>" + msg + "</td><td></td></tr>");
          }
          if (errorInfo != null && info) {
            errorInfo.add(lTableId + ": �_���e�[�u���̂ݑ��݂��܂�");
          }
        }
        // �_���e�[�u��ID�����֐i�߂�
        if (lite.hasNext()) {
          lTableId = (String)lite.next();
        } else {
          lTableId = null;
        }
      }
      if (out !=null) {
        out.flush();
      }
    }
    if (tmpTableLayouts.size() > 0 && filter.equals("%")) {
      // �������̘_���e�[�u�����c���Ă���ꍇ�i�ʏ�͒�`�Ɉُ�̂���Ώہj
      for (Iterator tmpite = tmpTableLayouts.keySet().iterator(); tmpite.hasNext(); ) {
        String tmpTableId = (String)tmpite.next();
        if (out != null) {
          out.print("<tr><td><input type=\"checkbox\" class=\"checkTableCheckBox\" name=\"table\" value=\"" + tmpTableId + "\"></td>");
          String msg = "�_���e�[�u���̂ݑ��݂��܂�";
          msg = "<font color=\"" + ERROR_COLOR + "\">" + msg + "</font>";
          out.println("<td>" + tmpTableId + "</td><td></td><td>" + msg + "</td><td></td></tr>");
        }
        if (errorInfo != null && info) {
          errorInfo.add(tmpTableId + ": �_���e�[�u���̂ݑ��݂��܂�");
        }
      }
    }
    if (out != null) {
      out.println("</table>");
      out.println("<input type=\"submit\" name=\"createtable\" value=\"�e�[�u���č\�z\" onclick=\"return confirm('�����e�[�u�����č\�z���܂��B���C�A�E�g�̌݊��������ꍇ�́A�Ώۃe�[�u���̃f�[�^�͑S�ď�������܂�����낵���ł���?');\">");
      //2014/07/01 EXCEL�ŏo�͂���@�\��ǉ� start
      out.println("<input onclick=\"doExcelReport(document.forms['downloadform'],document.getElementById('tablelist').innerHTML);return false;\" type=\"button\" value=\"EXCEL�o��\">");
      //2014/07/01 EXCEL�ŏo�͂���@�\��ǉ� end
      out.flush();
    }
  }
  
  private void printCheckTable(Connection conn, PrintWriter out, String table) throws SQLException {
    // ���C�A�E�g���擾
    Vector dataFields = new Vector();
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT DATAFIELDID,DATAFIELDORDER,DATAFIELDCLASS,(SELECT PHYSICALFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID=a.DATAFIELDID) PHYSICALFIELDID FROM TABLELAYOUTMASTER a WHERE TABLEID=? ORDER BY DATAFIELDORDER");
      stmt.setString(1, table);
      rs = stmt.executeQuery();
      while (rs.next()) {
        String dataFieldId = rs.getString(1).toUpperCase();
        String dataFieldClass = rs.getString(3).toUpperCase();
        String physicalFieldId = rs.getString(4);
        if (physicalFieldId != null) {
          physicalFieldId = physicalFieldId.toUpperCase();
        }
        dataFields.add(new String[]{dataFieldId, dataFieldClass, physicalFieldId});
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
        rs = null;
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
        stmt = null;
      }
    }
    try {
      stmt = conn.prepareStatement("SELECT * FROM " + table);
      rs = stmt.executeQuery();
      ResultSetMetaData rmeta = rs.getMetaData();
      out.println("<table>");
      out.println("<tr><td>�����e�[�u��</td><td>�e�[�u�����C�A�E�g�}�X�^</td></tr>");
      int cols = rmeta.getColumnCount();
      if (cols < dataFields.size()) {
        cols = dataFields.size();
      }
      for (int i = 0; i < cols; ++i) {
        String[] dataFieldInfo = null;
        if (i < dataFields.size()) {
          dataFieldInfo = (String[])dataFields.get(i);
        }
        out.print("<tr><td>");
        if (i < rmeta.getColumnCount()) {
          out.print(rmeta.getColumnName(i + 1));
        }
        out.print("</td><td>");
        if (dataFieldInfo != null) {
          out.print(dataFieldInfo[0]);
        }
        out.print("</td>");
        
        out.println("</tr>");
        out.flush();
      }
      out.println("<table>");
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    out.flush();
  }
  
  /**
   * �e�[�u�����C�A�E�g�ƕ������C�A�E�g�̔�r
   * @param conn
   * @param pTableId
   * @return int[3] [0]:��{�e�[�u���A[1]:���̃e�[�u���A[2]:���e�[�u��
   *    0:��v
   *    -1:�_���t�B�[���h��`���s��
   *    1:�����e�[�u�������݂��Ȃ�
   *    2:�_���e�[�u�������݂��Ȃ�
   *    3:�t�B�[���h�����قȂ�
   *    4:�t�B�[���h�����قȂ�
   *    5:�t�B�[���h�������قȂ�
   *    6:�v���C�}���L�[�����قȂ�
   */
  private static int[] checkTableLayout(ClassManager classManager, Connection conn, String tableName, StringBuffer comments) {
    if (comments == null) {
      comments = new StringBuffer();
    }
    int[] ret = new int[3];
    Statement stmt = null;
    ResultSet rs = null;
    try {
      Hashtable pTableLayoutFields = new Hashtable(); // �����e�[�u���̍��ڏ��
      Hashtable pTableLayoutKeys = new Hashtable(); // �����e�[�u���̃L�[���
      Vector pBaseTableLayout = new Vector(); // ��{�e�[�u���̑S��������
      Vector pNameTableLayout = new Vector(); // ���̃e�[�u���̑S��������
      Vector pInfoTableLayout = new Vector(); // ���e�[�u���̑S��������
      Object[] pNameValue = null;
      Object[] pInfoValue = null;
      //�����e�[�u�����擾
      try {
        // BASE�̕������̎擾
//        log_debug("[" + tableName + "]");
        stmt = conn.createStatement();
        rs = stmt.executeQuery("SELECT * FROM " + tableName);
        ResultSetMetaData rmeta = rs.getMetaData();
        int colCount = rmeta.getColumnCount();
        for (int i = 0; i < colCount; i++) {
          String pName = rmeta.getColumnName(i + 1);
          String pType = rmeta.getColumnTypeName(i + 1);
          int pSize = rmeta.getPrecision(i + 1); // ����
          int pScale = rmeta.getScale(i + 1); // �����_�ȉ�����
          boolean pNotNull = rmeta.isNullable(i + 1) == ResultSetMetaData.columnNoNulls;
          if (pScale < 0) {
            pScale = 0;
          }
          if (pSize == 0) {
            pSize = rmeta.getColumnDisplaySize(i + 1);
          }
          Object[] dbinfo = new Object[]{pName, pType, new Integer(pSize), new Integer(pScale), new Boolean(pNotNull)};
          pBaseTableLayout.add(dbinfo);
          pTableLayoutFields.put(pName.toUpperCase(), dbinfo);
          if (pNotNull == true) {
            pTableLayoutKeys.put(pName.toUpperCase(), dbinfo);
          }
        }
      } catch (SQLException e) {
        // �����e�[�u�������݂��Ȃ�
        commentLog(comments, e.getMessage());
        ret[0] = 1;
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException e) {}
          rs = null;
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {}
          stmt = null;
        }
      }
      try {
        // NAME�̕������̎擾
        stmt = conn.createStatement();
        rs = stmt.executeQuery("SELECT * FROM " + DbAccessUtils.getNameTableName(tableName));
        ResultSetMetaData rmeta = rs.getMetaData();
        int colCount = rmeta.getColumnCount();
        for (int i = 0; i < colCount; i++) {
          String pName = rmeta.getColumnName(i + 1);
          String pType = rmeta.getColumnTypeName(i + 1);
          int pSize = rmeta.getPrecision(i + 1); // ����
          int pScale = rmeta.getScale(i + 1); // �����_�ȉ�����
          if (pScale < 0) {
            pScale = 0;
          }
          if (pSize == 0 && pScale == 0) {
            pSize = rmeta.getColumnDisplaySize(i + 1);
          }
          boolean pNotNull = rmeta.isNullable(i + 1) == ResultSetMetaData.columnNoNulls;
          Object[] dbinfo = new Object[]{pName, pType, new Integer(pSize), new Integer(pScale), new Boolean(pNotNull)};
          pNameTableLayout.add(dbinfo);
          if (pName.equalsIgnoreCase("NAMEVALUE")) {
            pNameValue = dbinfo;
          }
        }
      } catch (SQLException e) {
        // �����e�[�u��(����)�����݂��Ȃ�
        ret[1] = 1;
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException e) {}
          rs = null;
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {}
          stmt = null;
        }
      }
      try {
        // INFO�̕������̎擾
        stmt = conn.createStatement();
        rs = stmt.executeQuery("SELECT * FROM " + DbAccessUtils.getInfoTableName(tableName));
        ResultSetMetaData rmeta = rs.getMetaData();
        int colCount = rmeta.getColumnCount();
        for (int i = 0; i < colCount; i++) {
          String pName = rmeta.getColumnName(i + 1);
          String pType = rmeta.getColumnTypeName(i + 1);
          int pSize = rmeta.getPrecision(i + 1); // ����
          int pScale = rmeta.getScale(i + 1); // �����_�ȉ�����
          if (pScale < 0) {
            pScale = 0;
          }
          if (pSize == 0 && pScale == 0) {
            pSize = rmeta.getColumnDisplaySize(i + 1);
          }
          boolean pNotNull = rmeta.isNullable(i + 1) == ResultSetMetaData.columnNoNulls;
          Object[] dbinfo = new Object[]{pName, pType, new Integer(pSize), new Integer(pScale), new Boolean(pNotNull)};
          pInfoTableLayout.add(dbinfo);
          if (pName.equalsIgnoreCase("VALUE")) {
            pInfoValue = dbinfo;
          }
        }
      } catch (SQLException e) {
        // �����e�[�u��(���)�����݂��Ȃ�
        ret[2] = 1;
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException e) {}
          rs = null;
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {}
          stmt = null;
        }
      }
      
      // �_���e�[�u�����̎擾
      Hashtable lTableLayout = getTableLayoutFull(conn, tableName);
      if (lTableLayout == null || lTableLayout.size() == 0) {
        commentLog(comments, "�_���e�[�u��[" + tableName + "]�����݂��܂���.");
        ret[0] = 2;
        ret[1] = 0; // ���͕̂s��
        ret[2] = 0; // ���͕s��
        return ret;
      }
      // Vector�̓��e�F{�������ږ�,�f�[�^�^�C�v,����,�����_�ȉ�����,NOT NULL, �f�[�^�t�B�[���hID(�_�����ږ�),�f�[�^�敪(1:�L�[,2:��{,3:����,4:���),�N���X�v���p�e�BID}
      Vector lpkeys = (Vector)lTableLayout.get("$pkey$");
      Vector lbases = (Vector)lTableLayout.get("$base$");
      Vector lnames = (Vector)lTableLayout.get("$name$");
      Vector linfos = (Vector)lTableLayout.get("$info$");
      if (lnames.size() == 0) {
        // �_�����̃e�[�u�������݂��Ȃ���΃��Z�b�g
        ret[1] = 0;
      }
      if (linfos.size() == 0) {
        // �_�����e�[�u�������݂��Ȃ���΃��Z�b�g
        ret[2] = 0;
      }
      // BASE�t�B�[���h�̃`�F�b�N
      if (pBaseTableLayout.size() > 0) {
        if (lbases.size() != pBaseTableLayout.size()) {
          commentLog(comments, "�t�B�[���h�����قȂ�܂�.[�e�[�u����`=" + lbases.size() + ":�����e�[�u��=" + pBaseTableLayout.size() + "]");
          ret[0] = 2;
        } else {
          // ��{�e�[�u���̕��т��`�F�b�N
          for (int i = 0; i < lbases.size(); ++i) {
            Object[] lFieldInfo = (Object[])lbases.get(i);
            String lpFieldId = (String)lFieldInfo[0];
            String dataFieldId = (String)lFieldInfo[5];
            if (lpFieldId == null) {
              commentLog(comments, "�f�[�^���ڒ�`�����݂��Ȃ����s���ł�[�f�[�^����ID=" + dataFieldId + "]");
              ret[0] = -1;
              return ret;
            }
            Object[] pFieldInfo = (Object[])pBaseTableLayout.get(i);
            if (!lpFieldId.equalsIgnoreCase((String)pFieldInfo[0])) {
              // �������ږ����قȂ�ꍇ
              Object[] pFieldInfo2 = (Object[])pTableLayoutFields.get(lpFieldId);
              if (pFieldInfo2 == null) {
                // �������C�A�E�g�ɑ��݂��Ȃ��ꍇ
                commentLog(comments, "�t�B�[���h�����قȂ�܂�[" + dataFieldId + ":" + pFieldInfo[0] + "]");
                ret[0] = 4;
                return ret;
              } else {
                // �قȂ���тɑ��݂���ꍇ
                int ip = -1;
                for (int j = 0; j < pBaseTableLayout.size(); ++j) {
                  pFieldInfo2 = (Object[])pBaseTableLayout.get(j);
                  if (lpFieldId.equalsIgnoreCase((String)pFieldInfo2[0])) {
                    ip = j;
                    break;
                  }
                }
                if (comments != null && comments.length() == 0) {
                  // �ŏ��̂P�ڂ̂ݒǉ�
                  commentLog(comments, "�t�B�[���h�����قȂ�܂�[" + lpFieldId + ":�e�[�u����`=" + (i + 1) + ",������`=" + (ip + 1) + "]");
                }
                //return 4; // �v���I�ł͂Ȃ��̂ŏI�����Ȃ�
              }
            }
            if (comments != null && comments.length() == 0) {
              // �G���[��������Α����`�F�b�N
              String mbbType = (String)lFieldInfo[1];
              String dbType = (String)pFieldInfo[1];
              Integer mbbSize = (Integer)lFieldInfo[2];
              Integer dbSize = (Integer)pFieldInfo[2];
              Integer mbbDecimal = (Integer)lFieldInfo[3];
              Integer dbDecimal = (Integer)pFieldInfo[3];
              if (mbbType == null || mbbSize == null) {
                commentLog(comments, "�f�[�^���ڒ�`���s���ł�[" + dataFieldId + "]");
                ret[0] = -1;
                return ret;
              }
              int r = compareFieldType(mbbType, mbbSize, mbbDecimal, dbType, dbSize, dbDecimal);
              if (r == 1) {
                commentLog(comments, "�_���t�B�[���h�����������e�[�u���ƈقȂ�܂�[" + dataFieldId + "/" + dispType(mbbType, mbbSize, mbbDecimal) + ":" + dispType(dbType, dbSize, dbDecimal) + "]");
                ret[0] = 5;
                return ret;
              }
              if (r == 2) {
                commentLog(comments, "�_���t�B�[���h����(����)�������e�[�u���ƈقȂ�܂�[" + dataFieldId + "/" + dispType(mbbType, mbbSize, mbbDecimal) + ":" + dispType(dbType, dbSize, dbDecimal) + "]");
                ret[0] = 5;
                return ret;
              }
              if (pTableLayoutKeys.containsKey(lpFieldId)) {
                // �L�[�̏ꍇ�͖��́E���̃L�[�������r
                if (lnames.size() > 0) {
                  if (pNameTableLayout.size() == 0) {
                    commentLog(comments, "�������̃e�[�u��[" + DbAccessUtils.getNameTableName(tableName) + "]�����݂��܂���");
                  } else if (i < pNameTableLayout.size()) {
                    // �������̃e�[�u���Ƒ�����r
                    Object[] pNameFieldInfo = (Object[])pNameTableLayout.get(i);
                    if (lpFieldId.equalsIgnoreCase((String)pNameFieldInfo[0])) {
                      dbType = (String)pNameFieldInfo[1];
                      dbSize = (Integer)pNameFieldInfo[2];
                      dbDecimal = (Integer)pNameFieldInfo[3];
                      r = compareFieldType(mbbType, mbbSize, mbbDecimal, dbType, dbSize, dbDecimal);
                      if (r == 1) {
                        commentLog(comments, "�_���t�B�[���h�����������e�[�u��(����)�ƈقȂ�܂�[" + dataFieldId + "/" + dispType(mbbType, mbbSize, mbbDecimal) + ":" + dispType(dbType, dbSize, dbDecimal) + "]");
                        ret[0] = 5;
                        return ret;
                      }
                      if (r == 2) {
                        commentLog(comments, "�_���t�B�[���h����(����)�������e�[�u��(����)�ƈقȂ�܂�[" + dataFieldId + "/" + dispType(mbbType, mbbSize, mbbDecimal) + ":" + dispType(dbType, dbSize, dbDecimal) + "]");
                        ret[0] = 5;
                        return ret;
                      }
                    }
                  }
                  if (linfos.size() > 0) {
                    if (pInfoTableLayout.size() == 0) {
                      commentLog(comments, "�������e�[�u��[" + DbAccessUtils.getInfoTableName(tableName) + "]�����݂��܂���");
                    } else if (i < pInfoTableLayout.size()) {
                      // �������e�[�u���Ƒ�����r
                      Object[] pInfoFieldInfo = (Object[])pInfoTableLayout.get(i);
                      if (lpFieldId.equalsIgnoreCase((String)pInfoFieldInfo[0])) {
                        dbType = (String)pInfoFieldInfo[1];
                        dbSize = (Integer)pInfoFieldInfo[2];
                        dbDecimal = (Integer)pInfoFieldInfo[3];
                        r = compareFieldType(mbbType, mbbSize, mbbDecimal, dbType, dbSize, dbDecimal);
                        if (r == 1) {
                          commentLog(comments, "�_���t�B�[���h�����������e�[�u��(���)�ƈقȂ�܂�[" + dataFieldId + "/" + dispType(mbbType, mbbSize, mbbDecimal) + ":" + dispType(dbType, dbSize, dbDecimal) + "]");
                          ret[0] = 5;
                          return ret;
                        }
                        if (r == 2) {
                          commentLog(comments, "�_���t�B�[���h����(����)�������e�[�u��(���)�ƈقȂ�܂�[" + dataFieldId + "/" + dispType(mbbType, mbbSize, mbbDecimal) + ":" + dispType(dbType, dbSize, dbDecimal) + "]");
                          ret[0] = 5;
                          return ret;
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        // �v���C�}���L�[�̃`�F�b�N
        if (lpkeys.size() != pTableLayoutKeys.size()) {
          commentLog(comments, "�v���C�}���L�[��(�܂���NOT NULL���ڐ�)���قȂ�܂�[" + lpkeys.size() + ":" + pTableLayoutKeys.size() + "]");
          ret[0] = 6;
        }
      }
      // ���̃e�[�u���̃`�F�b�N
      if (lnames.size() > 0) {
        if (pNameTableLayout.size() == 0 || pNameValue == null) {
          // ���̍��ڂ����邪�������̃e�[�u��������
          commentLog(comments, "���̃e�[�u�����قȂ�܂�[���̍��ڐ�=" + lnames.size() + ":�����e�[�u���Ȃ�]");
          ret[1] = 1;
        }
        if (ret[1] == 0) {
          int lsize = lpkeys.size() + 3; // DISPLANGID, PROPERTYID, NAMEVALUE
          if (pTableLayoutFields.get("DELETECLASS") != null) {
            lsize ++;
          }
          if (lsize != pNameTableLayout.size()) {
            // ���̃e�[�u���̍��ڐ����قȂ�
            commentLog(comments, "���̃e�[�u�����قȂ�܂�[���̍��ڐ�=" + lsize + ":" + pNameTableLayout.size() + "]");
            ret[1] = 2;
          }
          if (ret[1] == 0) {
            // ���̍��ڍő包�̃`�F�b�N
            int maxlen = 0;
            String maxFieldId = null;
            for (int i = 0; i < lnames.size(); ++i) {
              Object[] lFieldInfo = (Object[])lnames.get(i);
              try {
                int size = ((Integer)lFieldInfo[2]).intValue();
                if (size > maxlen) {
                  maxlen = size;
                  maxFieldId = (String)lFieldInfo[0];
                }
              } catch (Exception e) {}
            }
            if (maxlen > ((Integer)pNameValue[2]).intValue()) {
              commentLog(comments, "���̃e�[�u��NAMEVALUE�̌����𒴂��鍀�ڂ�����܂�[����ID=" + maxFieldId + ",���ڒ�=" + maxlen + ":������=" + pNameValue[2] + "]");
              ret[1] = 5;
            }
          }
        }
      }
      // ���e�[�u���̃`�F�b�N
      if (linfos.size() > 0) {
        if (pInfoTableLayout.size() == 0 || pInfoValue == null) {
          // ��񍀖ڂ����邪�������̃e�[�u��������
          commentLog(comments, "���e�[�u�����قȂ�܂�[��񍀖ڐ�" + linfos.size() + ":�����e�[�u���Ȃ�]");
          ret[2] = 1;
        }
        if (ret[2] == 0) {
          int lsize = lpkeys.size() + 2; // PROPERTYID, VALUE
          if (pTableLayoutFields.get("DELETECLASS") != null) {
            lsize ++;
          }
          if (lsize != pInfoTableLayout.size()) {
            // ���e�[�u���̍��ڐ����قȂ�
            commentLog(comments, "���e�[�u�����قȂ�܂�[��񍀖ڐ�=" + lsize + ":" + pInfoTableLayout.size() + "]");
            ret[2] = 2;
          }
          if (ret[2] == 0) {
            // ��񍀖ڍő包�̃`�F�b�N
            int maxlen = 0;
            String maxFieldId = null;
            for (int i = 0; i < linfos.size(); ++i) {
              Object[] lFieldInfo = (Object[])linfos.get(i);
              try {
                int size = ((Integer)lFieldInfo[2]).intValue();
                if (size > maxlen) {
                  maxlen = size;
                  maxFieldId = (String)lFieldInfo[0];
                }
              } catch (Exception e) {}
            }
            if (maxlen > ((Integer)pInfoValue[2]).intValue()) {
              commentLog(comments, "���e�[�u��VALUE�̌����𒴂��鍀�ڂ�����܂�[����ID=" + maxFieldId + ",���ڒ�=" + maxlen + ":������=" + pInfoValue[2] + "]");
              ret[2] = 5;
            }
          }
        }
      }

      
      if (comments.length() == 0) {
        // �G���[���Ȃ���΃G���e�B�e�B�N���X�̃t�B�[���h��`�Ƙ_����`�̈Ⴂ���`�F�b�N
        // �G���e�B�e�B�N���X���̎擾
        stmt = conn.createStatement();
        rs = stmt.executeQuery("SELECT PROPERTYID, VALUE FROM TABLEINFO WHERE TABLEID='" + tableName + "' AND (PROPERTYID='JAVAPACKAGEID' OR PROPERTYID='CLASSNAME')");
        String javaPackageId = null;
        String className = null;
        while (rs.next()) {
          String propertyId = rs.getString(1);
          String value = rs.getString(2);
          if ("JAVAPACKAGEID".equals(propertyId)) {
            javaPackageId = value;
          } else if ("CLASSNAME".equals(propertyId)) {
            className = value;
          }
        }
        
        if (javaPackageId != null && className != null && className.trim().length() > 0) {
          String fullClassName = PACKAGE_BASE + javaPackageId + "." + className;
          StringBuffer errorInfo = new StringBuffer();
          String[] classKeyFields = classManager.getKeyFields(fullClassName, errorInfo);
          Vector properties = classManager.getPropertyFields(fullClassName, null);
          int index = 0;
          if (classKeyFields == null) {
            commentLog(comments, "�G���e�B�e�B�N���X[" + fullClassName + "]���ǂݍ��߂܂���(" + errorInfo + ")");
            ret[0] = 6;
            return ret;
          } else {
            for (int i = 0; i < classKeyFields.length; ++i) {
              String classFieldId = classKeyFields[i];
              Object[] lFieldInfo = null;
              String dataFieldId = null;
              String propertyId = null;
              if (index < lpkeys.size()) {
                lFieldInfo = (Object[])lpkeys.get(index);
                dataFieldId = (String)lFieldInfo[5];
                propertyId = (String)lFieldInfo[7];
              }
              if (lFieldInfo == null || !classFieldId.equals(dataFieldId)) {
                commentLog(comments, "�G���e�B�e�B�N���X�ƃL�[��`���قȂ�܂�[�e�[�u����`=" + dataFieldId + ":�G���e�B�e�B�N���X=" + classFieldId + "]");
                ret[0] = 6;
                return ret;
              } else if (!properties.contains(propertyId)) {
                // �啶�����������قȂ�H
                String entityPid = null;
                for (int j = 0; j < properties.size(); ++j) {
                  String p = (String)properties.get(j);
                  if (propertyId.equalsIgnoreCase(p)) {
                    entityPid = p;
                    break;
                  }
                }
                if (entityPid != null) {
                  commentLog(comments, "�G���e�B�e�B�N���X�̃v���p�e�BID�ƈ�v���܂���[�f�[�^����ID=" + dataFieldId + ",�f�[�^���ڒ�`=" + propertyId + ":�G���e�B�e�B�N���X=" + entityPid + "]");
                } else {
                  commentLog(comments, "�G���e�B�e�B�N���X�̃v���p�e�BID�ƈ�v���܂���[�f�[�^����ID=" + dataFieldId + ",�f�[�^���ڒ�`=" + propertyId + ":�G���e�B�e�B�N���X=?]");
                }
                ret[0] = 6;
                return ret;
              }
              index++;
            }
            if (classKeyFields.length != lpkeys.size()) {
              commentLog(comments, "�G���e�B�e�B�N���X�ƃL�[���ڐ����قȂ�܂�[�e�[�u����`=" + lpkeys.size() + ":�G���e�B�e�B�N���X=" + classKeyFields.length + "]");
              ret[0] = 6;
              return ret;
            }
          }
          StringBuffer err = new StringBuffer();
          String[] classBaseFields = classManager.getBaseFields(fullClassName, err);
          if (classBaseFields != null) {
            for (int i = 0; i < classBaseFields.length && index < lbases.size(); ++i) {
              String classFieldId = classBaseFields[i];
              Object[] lFieldInfo = (Object[])lbases.get(index);
              String dataFieldId = (String)lFieldInfo[5];
              String propertyId = (String)lFieldInfo[7];
              if (!classFieldId.equals(dataFieldId)) {
                // �e�[�u����`�ƈقȂ�ꍇ
                Object[] fieldinfo = (Object[])lTableLayout.get(classFieldId);
                if (fieldinfo != null && (fieldinfo[6].equals("1") || fieldinfo[6].equals("2"))) {
                  // �t�B�[���h�̕��т��قȂ邾���H
                  continue;
                }
                commentLog(comments, "�G���e�B�e�B�N���X�ƃt�B�[���h��`���قȂ�܂�[�e�[�u����`=" + dataFieldId + ":�G���e�B�e�B�N���X=" + classFieldId + "]");
                ret[0] = 6;
                return ret;
              } else if (!properties.contains(propertyId)) {
                // �啶�����������قȂ�H
                String entityPid = null;
                for (int j = 0; j < properties.size(); ++j) {
                  String p = (String)properties.get(j);
                  if (propertyId.equalsIgnoreCase(p)) {
                    entityPid = p;
                    break;
                  }
                }
                if (entityPid != null) {
                  commentLog(comments, "�G���e�B�e�B�N���X�̃v���p�e�BID�ƈ�v���܂���[�f�[�^����ID=" + dataFieldId + ",�f�[�^���ڒ�`=" + propertyId + ":�G���e�B�e�B�N���X=" + entityPid + "]");
                } else {
                  commentLog(comments, "�G���e�B�e�B�N���X�̃v���p�e�BID�ƈ�v���܂���[�f�[�^����ID=" + dataFieldId + ",�f�[�^���ڒ�`=" + propertyId + ":�G���e�B�e�B�N���X=?]");
                }
                ret[0] = 6;
                return ret;
              }
              index++;
            }
            int tableBaseFieldCount = lbases.size() - lpkeys.size();
            if (classBaseFields.length != tableBaseFieldCount) {
              commentLog(comments, "�G���e�B�e�B�N���X�Ɗ�{�t�B�[���h�����قȂ�܂�[�e�[�u����`=" + tableBaseFieldCount + ":�G���e�B�e�B�N���X=" + classBaseFields.length + "]");
              ret[0] = 6;
              return ret;
            }
          }
        }
      }
      
      if (comments.length() == 0) {
        // �S�ăG���[��������΁A�L�[�̕��т��擪�ɂȂ��Ă��邩���`�F�b�N����
        for (int i = 0; i < lpkeys.size(); ++i) {
          Object[] keyInfo = (Object[])lpkeys.get(i);
          String keyFieldId = (String)keyInfo[5];
          Object[] lFieldInfo = (Object[])lbases.get(i);
          String dataFieldId = (String)lFieldInfo[5];
          if (!keyFieldId.equals(dataFieldId)) {
            commentLog(comments, "�x���F�L�[�̕��т��A�����Ă��܂���");
            break;
          }
        }
      }
    } catch (Exception e) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      commentLog(comments, e.getMessage() + " " + sw.toString());
      ret[0] = -1; // ���̑��\�����ʂȃG���[
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch(SQLException e) {}
      }
    }
    return ret;
  }
  private static String dispType(String type, Integer size, Integer decimal) {
    StringBuffer sb = new StringBuffer();
    sb.append(type);
    if ("CR".equals(type) || "VCR".equals(type)
        || "CHAR".equalsIgnoreCase(type) || "VARCHAR".equalsIgnoreCase(type) || "VARCHAR2".equalsIgnoreCase(type)) {
      sb.append("(").append(size).append(")");
    } else if ("NUM".equals(type) || "DECIMAL".equalsIgnoreCase(type) || "NUMBER".equalsIgnoreCase(type)) {
      sb.append("(").append(size);
      if (decimal != null && decimal.intValue() > 0) {
        sb.append(",").append(decimal);
      }
      sb.append(")");
    }
    return sb.toString();
  }
  private static void commentLog(StringBuffer comments, String msg) {
    if (comments != null) {
      if (comments.length() > 0) {
        comments.append("&nbsp;");
      }
      comments.append(msg);
    }
  }
  private static int compareFieldType(String mbbType, Integer mbbSize, Integer mbbDecimal, String dbType, Integer dbSize, Integer dbDecimal) {
    if (mbbType.equals("CR")) {
      if (dbType != null && dbType.toUpperCase().startsWith("CHAR")) {
        // OK
      } else {
        // �^���قȂ�
        return 1;
      }
      if (!mbbSize.equals(dbSize)) {
        // �������قȂ�
        return 2;
      }
    } else if (mbbType.equals("VCR")) {
      if (dbType != null && dbType.toUpperCase().startsWith("VARCHAR")) {
        // OK
      } else {
        // �^���قȂ�
        return 1;
      }
      if (!mbbSize.equals(dbSize)) {
        // �������قȂ�
        return 2;
      }
    } else if (mbbType.equals("DT")) {
      if (dbType != null && (dbType.toUpperCase().startsWith("CHAR") || dbType.toUpperCase().startsWith("VARCHAR")) && dbSize.intValue() == 8) {
        // OK
      } else {
        // �^���قȂ�
        return 1;
      }
    } else if (mbbType.equals("TS")) {
      if (dbType != null && (dbType.toUpperCase().startsWith("VARCHAR") && dbSize.intValue() >= 23)) {
        // OK
      } else {
        // �^���قȂ�
        return 1;
      }
    } else if (mbbType.equals("NUM")) {
      if (dbType != null && (dbType.toUpperCase().startsWith("NUM") || dbType.toUpperCase().startsWith("DEC")) && mbbSize.equals(dbSize) && mbbDecimal.equals(dbDecimal)) {
        // OK
      } else {
        // �^���قȂ�
        return 1;
      }
      if (!mbbSize.equals(dbSize) || (mbbDecimal != null && !mbbDecimal.equals(dbDecimal))) {
        // �������قȂ�
        return 2;
      }
    }
    return 0;
  }
  private void printCheckClasses(Connection conn, PrintWriter out) throws SQLException {
    try {
      log_debug(appPath);
      Vector classes = ClassManager.getDuplicateClasses(appPath);
      // �d��JAR�t�@�C���i�����o�[�W������JAR�����铙�j�̌��o
      Hashtable dupJars = new Hashtable();
      Hashtable dupJarItems = new Hashtable();
      for (Iterator ite = classes.iterator(); ite.hasNext(); ) {
        Vector pathes = (Vector)ite.next();
        for (Iterator ite2 = pathes.iterator(); ite2.hasNext(); ) {
          String path = (String)ite2.next();
          if (path.startsWith("lib/") && path.indexOf("!") != -1) {
            String jar = path.substring(0, path.indexOf("!"));
            if (jar.startsWith("lib/mbb_")) {
              continue;
            }
            if (!dupJars.containsKey(jar)) {
              dupJars.put(jar, new Integer(1));
              dupJarItems.put(jar, path);
            } else {
              Integer i = (Integer)dupJars.get(jar);
              String ji = (String)dupJarItems.get(jar);
              if (i.intValue() == 1) {
                dupJarItems.put(jar, ji + "...");
              }
              dupJars.put(jar, new Integer(i.intValue()+1));
            }
          }
        }
      }
      for (Enumeration enu = dupJars.keys(); enu.hasMoreElements(); ) {
        String key = (String)enu.nextElement();
        Integer i = (Integer)dupJars.get(key);
        if (i.intValue() == 1) {
          dupJars.remove(key);
        }
      }
      out.flush();
      out.println("<pre>");
      out.println("<span class=\"text\">[�d���N���X�t�@�C��]</span>");
      for (Iterator ite = new TreeMap(dupJars).keySet().iterator(); ite.hasNext(); ) {
        String key = (String)ite.next();
        String item = (String)dupJarItems.get(key);
        out.println(key + " (" + item.substring(item.indexOf("!") + 1) + ")");
      }
      for (Iterator ite = classes.iterator(); ite.hasNext(); ) {
        Vector pathes = (Vector)ite.next();
        for (Iterator ite2 = pathes.iterator(); ite2.hasNext(); ) {
          String path = (String)ite2.next();
          if (path.startsWith("lib/") && path.indexOf("!") != -1) {
            String jar = path.substring(0, path.indexOf("!"));
            if (dupJars.containsKey(jar)) {
              continue;
            }
          }
          out.println(path);
        }
      }
      out.println("</pre>");
      //
      out.println("<pre>");
      out.println("<span class=\"text\">[�N���X�t�@�C���Ȃ���`]</span>");
      Vector noclasses = ClassManager.getMissingClasses(conn, appPath);
      for (Iterator ite = noclasses.iterator(); ite.hasNext(); ) {
        String path = (String)ite.next();
        out.println(path);
      }
      out.println("</pre>");
      out.flush();
      //
      out.println("<pre>");
      out.println("<span class=\"text\">[�N���X�^�C�v�}�X�^�Ȃ���`]</span>");
      PreparedStatement stmt = null;
      ResultSet rs = null;
      try {
        stmt = conn.prepareStatement("SELECT DISTINCT PROCESSID, CLASSTYPE FROM PROCESSDEFINITIONMASTER"
            + " WHERE REMARK NOT LIKE '*%' AND CLASSTYPE NOT IN (SELECT CLASSTYPE FROM CLASSTYPEMASTER WHERE CLASSCLASS='2')"
            + " ORDER BY CLASSTYPE");
        rs = stmt.executeQuery();
        String lastProcessId = null;
        while (rs.next()) {
          String processId = rs.getString(1);
          String classType = rs.getString(2);
          if (!processId.equals(lastProcessId)) {
            out.println(processId + ":" + classType);
          }
          lastProcessId = processId;
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
        stmt = conn.prepareStatement("SELECT DISTINCT ITEMDEFINITIONID, CLASSTYPE FROM ITEMDEFINITIONMASTER"
            + " WHERE CLASSTYPE > ' ' AND REMARK NOT LIKE '*%' AND CLASSTYPE NOT IN (SELECT CLASSTYPE FROM CLASSTYPEMASTER WHERE CLASSCLASS='1')"
            + " ORDER BY CLASSTYPE");
        rs = stmt.executeQuery();
        while (rs.next()) {
          String processId = rs.getString(1);
          String classType = rs.getString(2);
          if (!processId.equals(lastProcessId)) {
            out.println(processId + ":" + classType);
          }
          lastProcessId = processId;
        }
        rs.close();
        rs = null;
        stmt.close();
        stmt = null;
      } catch (SQLException e) {
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException e) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException e) {}
        }
      }
      
      out.println("</pre>");
      out.flush();
    } catch (Exception e) {
      printError(out, e);
    }
    
  }
  /**
   * �@�\�\�����̃`�F�b�N
   * printCheck()���Ă΂��
   * @param conn
   * @param out
   * @throws SQLException
   */
  private void printCheckFunctions(Connection conn, PrintWriter out) throws SQLException {
    
    Hashtable packages = new Hashtable();
    
    out.println("<span class=\"text\">�@�\�}�X�^�ɓo�^����Ă���e�\�����̃p�b�P�[�W�̎g�p�\�敪���`�F�b�N</span><br>");
    out.println("<table><tr style=\"background-color:#cccccc;\"><td>�@�\ID</td><td>�\��ID</td><td>�\���敪</td><td>�G���[���</td></tr>");
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      stmt = conn.prepareStatement("SELECT FUNCTIONID,FUNCTIONCOMPOSITIONID,FUNCTIONCOMPOSITIONCLASS,(SELECT PACKAGEID FROM FUNCTIONMASTER WHERE FUNCTIONID=a.FUNCTIONID) FROM FUNCTIONCOMPOSITIONMASTER a ORDER BY FUNCTIONID,FUNCTIONCOMPOSITIONCLASS,FUNCTIONCOMPOSITIONID");
      rs = stmt.executeQuery();
      while (rs.next()) {
        String functionId = rs.getString(1);
        String functionCompositionId = rs.getString(2);
        String functionCompositionClass = rs.getString(3);
        String packageId = rs.getString(4);
        if (packages.get(packageId + ",FUNCTION") == null) {
          String msg = checkPackage(conn, packageId, "FUNCTIONMASTER");
          if (msg != null) {
            out.println("<tr><td>" + functionId + "</td><td>" + packageId + "</td><td>�p�b�P�[�WID</td><td>" + msg + "</td>");
          }
          packages.put(packageId + ",FUNCTION", "FUNCTION:" + msg);
        }
        if (functionCompositionClass != null && functionCompositionClass.equals("2")) {
          // �v���Z�XID
          String pkgid = getPackageId(conn, functionCompositionId, "PROCESSMASTER");
          if (pkgid != null) {
            String cache = (String)packages.get(pkgid + ",PROCESS");
            if (cache == null) {
              String msg = checkPackage(conn, pkgid, "PROCESSMASTER");
              if (msg != null) {
                out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�v���Z�XID</td><td>" + msg + "</td>");
              }
              if (msg == null) {
                packages.put(pkgid + ",PROCESS", "");
              } else {
                packages.put(pkgid + ",PROCESS", msg);
              }
            } else {
              if (cache.length() > 0) {
                out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�v���Z�XID</td><td>" + cache + "</td>");
              }
            }
            if (!pkgid.equals(packageId)) {
              out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�v���Z�XID</td><td>�p�b�P�[�WID[" + pkgid + "]���@�\�}�X�^�̃p�b�P�[�WID[" + packageId + "]�ƈقȂ�</td>");
            }
          } else {
            // �v���Z�X�����݂��Ȃ�...
            out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�v���Z�XID</td><td>�v���Z�X�}�X�^�ɑ��݂��܂���</td>");
          }
        } else if (functionCompositionClass != null && functionCompositionClass.equals("3")) {
          // �y�[�WID
          String pkgid = getPackageId(conn, functionCompositionId, "PAGEMASTER");
          if (pkgid != null) {
            String cache = (String)packages.get(pkgid + ",PAGE");
            if (cache == null) {
              String msg = checkPackage(conn, pkgid, "PAGEMASTER");
              if (msg != null) {
                out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�y�[�WID</td><td>" + msg + "</td>");
              }
              if (msg == null) {
                packages.put(pkgid + ",PAGE", "");
              } else {
                packages.put(pkgid + ",PAGE", msg);
              }
            } else {
              if (cache.length() > 0) {
                out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�y�[�WID</td><td>" + cache + "</td>");
              }
            }
            if (!pkgid.equals(packageId)) {
              out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�v���Z�XID</td><td>�p�b�P�[�WID[" + pkgid + "]���@�\�}�X�^�̃p�b�P�[�WID[" + packageId + "]�ƈقȂ�</td>");
            }
          } else {
            // �y�[�W�����݂��Ȃ�...
            out.println("<tr><td>" + functionId + "</td><td>" + functionCompositionId + "</td><td>�y�[�WID</td><td>�y�[�W�}�X�^�ɑ��݂��܂���</td>");
          }
        }
        out.flush();
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {}
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {}
      }
    }
    out.println("</table>");
    out.flush();
  }
  

  /**
   *  �_���e�[�u����`���CREATESQL���𐶐�
   * @param out
   * @param command
   */
  private void printDesc(PrintWriter out, String command) {
    //
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "desc"���X�L�b�v
      String objectName = null;
      if (st.hasMoreTokens()) {
        //objectName = st.nextToken().toUpperCase();
        objectName = st.nextToken();
      }
      log_debug(command);
      String createsql = null;
      String saveObjectType = null;
      //MsSQL, MySql��ǉ� 2013/11/14
      if (isOracle(0) || isDerby(0) || isMSSql(0) || isMySql(0)) {
        // Oracle,Derby,MsSQL,MySQL�̏ꍇ
        String objectType = DbAccessUtils.getObjectType(conn, objectName);
        saveObjectType = objectType;
        if (objectType != null) {
          if (objectType.equalsIgnoreCase("TABLE") || 
              objectType.equalsIgnoreCase("USER_TABLE") || //SQL�T�[�o
              objectType.equalsIgnoreCase("BASE TABLE") || //MySQL
              objectType.equalsIgnoreCase("T")) { //Derby
            createsql = DbAccessUtils.getCreateTableSQLFromTablelayoutMaster(conn, objectName);
            if (isOracle(0)) {
              String index = DbAccessUtils.getCreateNonUniqueIndexSQL(conn, schemas[0], objectName);
              if (index != null && index.trim().length() > 0) {
                createsql = createsql + index;
              }
            }
          } else if (objectType.equalsIgnoreCase("VIEW") || 
                     objectType.equalsIgnoreCase("V")) { //Derby
            createsql = getViewScript(conn, schemas[0], objectName, 0);
          } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(baos);
            printDDLExport(null, osw, null, conn, schemas[0], objectType, objectName);
            osw.close();
            createsql = baos.toString();
          }
        }
      } else {
        // Oracle,Derby,MsSQL,MySQL�ȊO
        createsql = DbAccessUtils.getCreateTableSQLFromTablelayoutMaster(conn, objectName);
      }
      //Debug 2013/11/28
      if (createsql == null || createsql.trim().length() == 0) {
        createsql = "command=�y" + command + "�zobjectType=" + saveObjectType;
      }
      out.println("<pre "
      + " ondblclick=\"document.getElementsByName('command')[0].value=this.innerText;doTab('Command');return false;\">");
      out.println(createsql);
      out.println("</pre>");
      boolean errorOut = false;
      if (isOracle(0)) {
        // oracle�̏ꍇ�̓G���[������΂��̏���\��
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
          stmt = conn.prepareStatement("SELECT LINE, POSITION, TEXT FROM USER_ERRORS WHERE NAME = ?");
          stmt.setString(1, objectName);
          rs = stmt.executeQuery();
          while (rs.next()) {
            int line = rs.getInt(1);
            int col = rs.getInt(2);
            String text = rs.getString(3);
            if (!errorOut) {
              errorOut = true;
              out.println("<pre style=\"color:" + ERROR_COLOR + "\">");
            }
            if (line > 0 && col > 0) {
              out.print("�s=" + line + ",��=" + col + ": " + text);
            } else {
              out.print(text);
            }
          }
        } finally {
          if (stmt != null) {
            stmt.close();
          }
          if (rs != null) {
            rs.close();
          }
        }
        if (errorOut) {
          out.println("</pre>");
        }
      }
    } catch(Exception e) {
      printError(out, e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {
        }
      }
    }
    
  }
  
  /**
   *  SQL���𐶐����� (sql �e�[�u����)
   * @param out
   * @param command
   */
  private void printSQL(PrintWriter out, String command) {
    //
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      StringTokenizer st = new StringTokenizer(command);
      st.nextToken(); // "sql"���X�L�b�v
      String tableName = null;
      String sql = null;
      String pre = null;
      if (st.hasMoreTokens()) {
        tableName = st.nextToken().toUpperCase();
      }
      if (st.hasMoreTokens()) {
        sql = st.nextToken().toUpperCase();
      }
      if (st.hasMoreTokens()) {
        pre = st.nextToken();
      }
      if (sql == null) {
        sql = "-";
      }
      log_debug(command);
      
      StringBuffer sb = new StringBuffer();
      sb.append(command).append("\n\n");
      stmt = conn.createStatement();
      rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE 1=2");
      ResultSetMetaData rm = rs.getMetaData();
      if (rm.getColumnCount() > 0) {
        if (sql.startsWith("SEL") || "-".equals(sql)) {
          sb.append("SELECT ");
          for (int i = 0; i < rm.getColumnCount(); ++i) {
            if (i > 0) {
              sb.append(", ");
            }
            if (pre != null && pre.length() > 0) {
              sb.append(pre).append(".");
            }
            sb.append(rm.getColumnName(i + 1));
          }
          sb.append(" FROM ").append(tableName);
          if (pre != null) {
            sb.append(" ").append(pre);
          }
          sb.append(";\n");
        }
        if (sql.startsWith("INS") || "-".equals(sql)) {
          if ("-".equals(sql)) {
            sb.append("\n");
          }
          sb.append("INSERT INTO ").append(tableName).append(" (");
          StringBuffer param = new StringBuffer();
          for (int i = 0; i < rm.getColumnCount(); ++i) {
            if (i > 0) {
              sb.append(", ");
              param.append(", ");
            }
            if (pre != null && pre.length() > 0) {
              sb.append(pre).append(".");
            }
            sb.append(rm.getColumnName(i + 1));
            param.append("?");
          }
          sb.append(") VALUES (").append(param.toString());
          sb.append(");\n");
        }
      }
      out.println("<pre class=\"pre-wrap\" style=\"width:100%;\">");
      out.println(sb.toString());
      out.println("</pre>");
    } catch(Exception e) {
      printError(out, e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e1) {
        }
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e1) {
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {
        }
      }
    }
    
  }
  
  private String getPackageId(Connection conn, String id, String tableId) throws SQLException {
    String packageId = null;
    String keyId = null;
    if (tableId.equalsIgnoreCase("PROCESSMASTER")) {
      keyId = "PROCESSID";
    } else if (tableId.equalsIgnoreCase("PAGEMASTER")) {
      keyId = "PAGEID";
    }
    if (keyId != null) {
      PreparedStatement stmt = conn.prepareStatement("SELECT PACKAGEID FROM " + tableId + " WHERE " + keyId + "=?");
      stmt.setString(1, id);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        packageId = rs.getString(1);
        if (packageId == null) {
          packageId = "";
        }
      }
      rs.close();
      stmt.close();
    }
    return packageId;
  }

  /**
   * �����^�u��SQL�𐶐�
   * @param out
   * @param command
   * @param obscure �B�����x���i0: ���̂܂܁A1:�B�������j
   */
  private void printFindReplace(PrintWriter out, String command, int obscure) {
	    // �ꊇ�u��
	    Connection conn = null;
	    try {
	      conn = getConnection();
	      conn.setAutoCommit(false);
        String cmd = null;
        String dlm = null;
	      StringTokenizer st = null;
	      int spc = command.indexOf(" ");
	      if (spc > 0) {
	        cmd = command.substring(0, spc);
	      }
	      if (cmd != null) {
	        // replace/x/ �̂悤�Ȍ`���̏ꍇ�́Ax���f���~�^�Ƃ��Ĉ���
	        int slash = cmd.indexOf("/");
          int lslash = cmd.lastIndexOf("/");
	        if (slash != -1 && lslash > slash + 1) {
	          dlm = cmd.substring(slash + 1, lslash);
	          st = new StringTokenizer(command.substring(spc + 1), dlm);
	          if (lslash < cmd.length() - 1) {
	            cmd = cmd.substring(0, slash) + cmd.substring(lslash);
	          } else {
	            cmd = cmd.substring(0, slash);
	          }
	        }
	      }
	      if (st == null) {
	        st = new StringTokenizer(command);
	        cmd = st.nextToken(); // "replace"/"grep"
	      }
	      boolean module = false;
        String tablePattern = null;
	      if (cmd.startsWith("grepm") || cmd.startsWith("findm")) {
	        cmd = cmd.substring(0, 4) + cmd.substring(5); // m����菜��
	        tablePattern = "APPLICATIONMASTER|APPLICATIONNAME|CLASSTYPEMASTER|CLASSTYPENAME|CLASSPROPERTYMASTER|CLASSPROPERTYNAME|DATAFIELDMASTER|DATAFIELDNAME|ITEMDEFINITIONMASTER|MENUITEMMASTER|PAGEMASTER|PAGENAME|PAGEMESSAGE|PROCESSMASTER|PROCESSNAME|PROCESSDEFINITIONMASTER|TABLEMASTER|TABLENAME|TABLEINFO|TABLELAYOUTMASTER|VIEWPAGEMASTER|VIEWPAGEINFO";
	        module = true;
	      }
        int maxcount = 0;
        String keyword = st.nextToken(); // �����L�[
        if (keyword.startsWith("\"")) {
          int idx = cmd.lastIndexOf("\"");
          if ((idx == cmd.length() - 1 || cmd.charAt(idx + 1) == ' ') && idx != cmd.indexOf("\"")) {
            keyword = cmd.substring(cmd.indexOf("\"") + 1, idx);
          }
        }
        String value = null; // �u���l�i�C�Ӂj
        boolean grep = false; // grep,find�̏ꍇ��true�Areplace�̏ꍇ��false

        if (cmd.toLowerCase().startsWith("grep/")||cmd.toLowerCase().startsWith("find/")||cmd.toLowerCase().startsWith("replace/")) {
          // grep/n �܂��� find/n �̏ꍇ�͍ő匏����n��
          int p = cmd.indexOf("/");
          if (cmd.substring(p + 1).equalsIgnoreCase("o")) {
            // grep/o replace/o �̏ꍇ�͞B������
            obscure = 1;
            cmd = cmd.substring(0, p);
          } else {
            // o�ȊO�̏ꍇ�́A�ő匏���Ƃ���
            if (p > 0) {
              try {
                maxcount = Integer.parseInt(cmd.substring(p + 1));
              } catch(Exception e) {
                log_debug(e);
              }
              cmd = cmd.substring(0, p);
            }
          }
        }
        if (cmd.equalsIgnoreCase("find")) {
          obscure = 1;
          grep = true;
        } else if (cmd.equalsIgnoreCase("grep")) {
          grep = true;
        }

        if (!grep) {
          // replace�R�}���h�̏ꍇ
          if (st.hasMoreTokens()) {
            value = st.nextToken();
            if (value.equals("\"")) {
              // �u���敶���񂪃_�u���N�H�[�g�������ꍇ
              value = st.nextToken("\0"); // �c����Ō�܂Ŏ擾
              if (value.endsWith("\"")) {
                value = value.substring(0, value.length() - 1);
              } else {
                value = "\"" + value;
              }
            }
            if (value.startsWith("\"") && value.endsWith("\"")) {
              // �_�u���N�H�[�g�Ŋ����Ă���ꍇ
              value = value.substring(1, value.length() - 1);
            }
            // �Ō�Ƀe�[�u���p�^�[�����w��\
            if (st.hasMoreTokens()) {
              tablePattern = st.nextToken();
            }
          }
        } else {
          // grep �R�}���h�̏ꍇ
          if (st.hasMoreTokens()) {
            tablePattern = st.nextToken();
          }
        }
        if (module) {
          out.println("<script language=\"javascript\">");
          out.println("function doResearch() {");
          out.println("  var text;");
          out.println("  if(window.getSelection){");
          out.println("    text=window.getSelection();");
          out.println("  }else if(document.selection){");
          out.println("    text=document.selection.createRange().text;");
          out.println("  }");
          out.println("  if(text){");
          out.println("    var cmd=document.getElementsByName('command')[0];");
          out.println("    cmd.value=cmd.value.replace(/ [^ ]+/,' '+text);");
          out.println("    doCommand('Command','execsql','1');");
          out.println("  }");
          out.println("}");
          out.println("</script>");
          
          out.println("<pre onDblClick=\"doResearch()\">");
        } else {
          out.println("<pre>");
        }
        out.flush();
        // �X���b�V���ŕ����L�[���[�h�w��
        Vector keywords = new Vector(); // �����L�[���[�h
        Vector values = new Vector(); // �u���l
        StringBuffer dispkwd = new StringBuffer();
        StringBuffer dispval = new StringBuffer();
        if (keyword.startsWith("\"") && keyword.endsWith("\"")) {
          // �����L�[���[�h���_�u���N�H�[�g�Ŋ����Ă���ꍇ��
          // �N�H�[�e�[�V�������O���Ă��̂܂܌���
          String kwd = keyword.substring(1, keyword.length() - 1);
          keywords.add(kwd);
          dispkwd.append("[").append(kwd).append("]");
          values.add(value);
          dispval.append("[").append(value).append("]");
          
        } else {
          // �_�u���N�H�[�g�Ŋ����Ă��Ȃ��ꍇ�́A"|"�ŕ����L�[���[�h�𕪉�
          StringTokenizer kst = new StringTokenizer(keyword, "|");
          while (kst.hasMoreTokens()) {
            String kwd = kst.nextToken();
            keywords.add(kwd);
            if (dispkwd.length() == 0) {
              dispkwd.append("[");
            } else {
              dispkwd.append(" [");
            }
            dispkwd.append(kwd).append("]");
          }
          if (!grep && keywords.size() > 1) {
            // �u���ŕ����L�[���[�h������ꍇ
            StringTokenizer vst = new StringTokenizer(value, "|");
            while (vst.hasMoreTokens()) {
              String val = vst.nextToken();
              values.add(val);
              if (dispval.length() == 0) {
                dispval.append("[");
              } else {
                dispval.append(" [");
              }
              dispval.append(val).append("]");
            }
            if (keywords.size() != values.size()) {
              // �L�[���[�h���ƒu�������̐�������Ȃ��ꍇ�̓G���[
              StringBuffer errmsg = new StringBuffer();
              int max = keywords.size();
              if (values.size() > max) {
                max = values.size();
              }
              for (int i = 0; i < max; ++i ) {
                if (errmsg.length() == 0) {
                  errmsg.append("[");
                } else {
                  errmsg.append(" [");
                }
                if (keywords.size() > i) {
                  errmsg.append(keywords.get(i)).append("��");
                } else {
                  errmsg.append("<br>�H</br>").append("��");
                }
                if (values.size() > i) {
                  errmsg.append(values.get(i)).append("]");
                } else {
                  errmsg.append("<br>�H</br>").append("]");
                }
              }
              out.println("</pre>");
              printError(out, new Exception("�L�[���[�h���ƒu��������̐��������܂���\n" + errmsg));
              return;
            }
          } else {
            // grep�̏ꍇ�܂��̓L�[���[�h��1�̏ꍇ
            if (value != null) {
              values.add(value);
              dispval.append("[").append(value).append("]");
            }
          }
        }
        out.println(cmd);
        out.println("�����L�[���[�h�F" + dispkwd);
        if (maxcount > 0) {
          out.println("(���o�����F" + maxcount + ")");
        }
        if (dlm != null) {
          out.println("(��ؕ����F" + dlm + ")");
        }
        if (!grep) {
          out.println("�ϊ��l�F" + dispval);
        }

        // �w��e�[�u���i���w�莞�͑S�Ẵe�[�u���j�ɑ΂��Č���
        int foundCount = 0;
        int[] foundCounts = new int[keywords.size()];
        for (int i = 0; i < foundCounts.length; ++i) {
          // �O�̂��ߏ�����
          foundCounts[i] = 0;
        }
        Vector tables = getObjectNames(tablePattern, OBJ_TYPE_PTABLE);
        if (tablePattern != null) {
          // �e�[�u�������w�肳�ꂽ�ꍇ�͕\������
          StringBuffer tbls = new StringBuffer();
          for (Iterator ite = new TreeSet(tables).iterator(); ite.hasNext(); ) {
            if (tbls.length() > 0) {
              tbls.append(",");
            }
            tbls.append((String)ite.next());
          }
          out.println("�����Ώۃe�[�u���F[" + tbls + "]");
        }
        if (obscure > 0) {
          out.println("�B�������F[on]");
        }

        out.flush();

        // replace�̏ꍇ�́A�ϊ�SQL���i�[���A�Ō�ɕ\������
        Vector replaceSqls = new Vector();
        boolean bk = false;
        for (Iterator ite = new TreeSet(tables).iterator(); ite.hasNext() && !bk; ) {
          String tableId = (String)ite.next();
          Vector pkeys = getPrimaryKeys(tableId);
          StringBuffer sql = new StringBuffer();
          sql.append("SELECT * FROM ").append(DbAccessUtils.getBaseTableName(tableId));
          if (pkeys.size() > 0) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < pkeys.size(); ++i) {
              if (i > 0) {
                sql.append(",");
              }
              sql.append("\"").append(pkeys.get(i)).append("\"");
            }
          }
          Statement stmt = conn.createStatement();
          ResultSet rs = null;
          try {
            rs = stmt.executeQuery(sql.toString());
          } catch (SQLException e) {
            out.println("<font color=\"" + ERROR_COLOR + "\">" + tableId + " : " + e.getMessage() + "</font>\n");
            continue;
          }
          ResultSetMetaData rmeta = rs.getMetaData();
          int fcount = 0; // ���������������J�E���g
          int rcount = 0; // ���R�[�h�������J�E���g
          int columnCount = rmeta.getColumnCount();
          int[] columnSizes = new int[columnCount];
          String[] columnNames = new String[columnCount];
          for (int i = 0; i < columnCount; ++i) {
            columnSizes[i] = rmeta.getColumnDisplaySize(i + 1);
            columnNames[i] = rmeta.getColumnName(i + 1);
            if (columnNames[i].equalsIgnoreCase("TIMESTAMPVALUE")) {
              // �^�C���X�^���v�͔�r�ΏۊO(�J��������0)�Ƃ���
              columnSizes[i] = 0;
            }
          }
          while (rs.next() && !bk) {
            ++rcount;
            // �t�B�[���h�l�����Ɍ���
            for (int i = 0; i < columnCount; ++i) {
              if (columnSizes[i] <= 0) {
                // �J�����T�C�Y���[���̈ȉ��̃t�B�[���h�̓X�L�b�v
                continue;
              }
              String fvalue = null;
              try {
                fvalue = rs.getString(i + 1);
              } catch (SQLException e) {
                // �G���[���o���ꍇ�̓X�L�b�v
                continue;
              }
              String orgfvalue = fvalue;
              String orgfname = columnNames[i];
              boolean replaced = false;
              String replacedValue = null;
              if (fvalue != null) {
                // �l��NULL�ȊO�̏ꍇ
                // �����L�[���[�h�����Ɍ���
                ObscureString osfvalue = null;
                if (obscure > 0) {
                  osfvalue = new ObscureString(fvalue);
                }
                for (int kw = 0; kw < keywords.size(); ++kw) {
                  String kwd = (String)keywords.get(kw);
                  String val = null;
                  if (values.size() <= 1) {
                    val = value;
                  } else {
                    val = (String)values.get(kw);
                  }
                  int kwdlen = kwd.length();
                  if (kwdlen == 0) {
                    // �󕶎��̏ꍇ��NULL�l�̂݃q�b�g������
                    continue;
                  }
                  if (kwdlen > columnSizes[i]) {
                    // �L�[���[�h�����J�����ő�T�C�Y�����傫���ꍇ�̓X�L�b�v
                    continue;
                  }
                  // �啶���������𖳎����Č���
                  int p = -1;
                  boolean start = false;
                  if (kwd.startsWith("^") && kwd.length() > 1) {
                    // ^������ΐ擪�����v������̂̂�
                    if (obscure == 0) {
                      // ��������
                      if (fvalue.startsWith(kwd.substring(1))) {
                        p = 0;
                        kwd = kwd.substring(1);
                        start = true;
                      }
                      
                    } else {
                      // �B������
                      if (osfvalue.startsWith(kwd.substring(1))) {
                        p = 0;
                        kwd = kwd.substring(1);
                        start = true;
                      }
                    }
                  } else {
                    // ������v
                    if (obscure == 0) {
                      // ��������
                      p = fvalue.indexOf(kwd);
                    } else {
                      // �B������
                      p = osfvalue.find(kwd);
                    }
                  }
                  if (p != -1) {
                    // �L�[���[�h�����o�����ꍇ
                    ++ foundCounts[kw];
                    // �\���p�Ɋe�J������A�����o��
                    StringBuffer line = new StringBuffer();
                    for (int j = 0; j < rmeta.getColumnCount(); ++j) {
                      if (j > 0) {
                        line.append(" ");
                      }
                      String v = null;
                      try {
                        v = rs.getString(j + 1);
                      } catch (SQLException e) {
                        // BLOB���ŃG���[����������
                        continue;
                      }
                      if (j != i) {
                        line.append(v);
                      } else {
                        // ��v����ӏ��������\��
                        try {
                          if (obscure > 0) {
                            if (!grep) {
                              line.append(osfvalue.replaceAllWithTag(kwd, null, "<b><s><font color=\"red\">", "</font></s><font color=\"#00ff00\">"+ val + "</font></b>"));
                            } else {
                              line.append(osfvalue.replaceAllWithTag(kwd, null, "<b>", "</b>"));
                            }
                          } else {
                            if (!grep) {
                              line.append(fvalue.replaceAll(kwd, "<b><s><font color=\"red\">" + kwd + "</font></s><font color=\"#00ff00\">" + val + "</font></b>"));
                            } else {
                              line.append(fvalue.replaceAll(kwd, "<b>" + kwd + "</b>"));
                            }
                          }
                        } catch(Exception e) {
                          printError(out, new Exception("v=" + v + ",kwd=" + kwd));
                          throw e;
                        }
                      }
                    }
                    out.println(tableId + " [" + rcount + "/" + kwd + "]: " + line);
                    if (!grep) {
                      if (obscure > 0) {
                        // �B�������u��
                        fvalue = osfvalue.replaceAll(kwd, val);
                      } else {
                        // ���������u��
                        fvalue = fvalue.replaceAll(kwd, val);
                      }
                      replaced = true;
                      replacedValue = fvalue;
                    }
                    ++fcount;
                    if (maxcount > 0 && fcount >= maxcount) {
                      // �ő匏���ɒB�����璆�f
                      bk = true;
                      break;
                    }
                  }
                }
              } else {
                // �t�B�[���h�l��NULL�̏ꍇ
                for (int kw = 0; kw < keywords.size(); ++kw) {
                  String kwd = (String)keywords.get(kw);
                  if (kwd.length() == 0) {
                    // �L�[���[�h���󕶎��̏ꍇ�̓q�b�g�Ƃ݂Ȃ�
                    ++ foundCounts[kw];
                    // �e�J������A�����o��
                    StringBuffer line = new StringBuffer();
                    for (int j = 0; j < rmeta.getColumnCount(); ++j) {
                      if (j > 0) {
                        line.append(" ");
                      }
                      String v = rs.getString(j + 1);
                      if (j != i) {
                        line.append(v);
                      } else {
                        line.append("<b>[NULL]</b>");
                      }
                    }
                    out.println(tableId + " [" + rcount + "/(null)]: " + line);
                    if (!grep) {
                      fvalue = "";
                      replaced = true;
                      replacedValue = fvalue;
                    }
                    ++fcount;
                    if (maxcount > 0 && fcount >= maxcount) {
                      // �ő匏���ɒB�����璆�f
                      bk = true;
                      break;
                    }

                  }
                }
              }
              if (replaced) {
                // �u��SQL����
                StringBuffer replacesql = new StringBuffer();
                boolean esc = false;
                if (replacedValue.indexOf("'") != -1) {
                  replacedValue = escapeSQL(replacedValue);
                  esc = true;
                }
                if (esc) {
                  replacesql.append("\\");
                }
                replacesql.append("UPDATE ").append(tableId).append(" SET ");
                replacesql.append(rmeta.getColumnName(i + 1)).append("='");
                replacesql.append(replacedValue);
                replacesql.append("' WHERE ");
                StringBuffer where = new StringBuffer();
                for (int j = 0; j < pkeys.size(); ++j) {
                  if (j > 0) {
                    where.append(" AND ");
                  }
                  String key = (String)pkeys.get(j);
                  String keyValue = rs.getString(key);
                  where.append(key).append(escapeSQLcond(keyValue));
                }
                // �ύX����Ă����ꍇ�Ƀq�b�g���Ȃ��悤�ɕϊ��O�l�������ɒǉ�
                where.append(" AND ").append(orgfname).append(escapeSQLcond(orgfvalue));
                //
                replacesql.append(where);
                replaceSqls.add(replacesql);
              }
              
            } // �J�������[�v
          }
          if (fcount > 0) {
            foundCount += fcount;
          } else {
//            if (!grep) {
//              out.println(tableId + ": �����s��=" + rcount + " : �Ώۃf�[�^�͌�����܂���ł���.");
//            }
          }
          rs.close();
          stmt.close();
          out.flush();
        }
        if (foundCount == 0) {
          out.println("�Ώۃf�[�^�͌�����܂���ł���.");
        } else {
          out.println(foundCount + " ��������܂���.");
        }
        if (replaceSqls.size() > 0) {
          for (int i = 0; i < replaceSqls.size(); ++i) {
            out.println(replaceSqls.get(i) + ";");
          }
        }
        if (keywords.size() > 1) {
          // �����L�[���[�h�w��̏ꍇ�́A���q�b�g�L�[���[�h���o��
          int ncnt = 0;
          for (int i = 0; i < foundCounts.length; ++i) {
            if (foundCounts[i] == 0) {
              if (ncnt == 0) {
                out.println("[�ΏۂȂ��L�[���[�h]");
              }
              out.println(keywords.get(i));
              ++ ncnt;
            }
          }
        }
        out.println("</pre><br>");
        
	    } catch(Exception e) {
	      printError(out, e);
	    } finally {
	      if (conn != null) {
	        try {
	          conn.close();
	        } catch (SQLException e1) {
	        }
	      }
	    }
	    
	  }
  

  /**
   * �w���v�\��
   * @param out 
   * @param command
   */
  private void printHelp(PrintWriter out, String command) {
    out.println("<table><tr><td>");
    out.println("<b>����R�}���h�ꗗ</b><br><br>");
    // export
    out.print("<b>");
    out.print("export&nbsp;&nbsp;<i>�e�[�u����</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�e�[�u�����̃e�[�u���̃f�[�^��export�`���ŕ\�����܂��B<br>");
    out.println("<br><br>");
    // import
    out.print("<b>");
    out.print("import&nbsp;&nbsp;<i>�e�[�u����</i><br>�f�[�^...");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;import�f�[�^���e�[�u�����̃e�[�u���ɒǉ����܂��B<br>");
    out.println("&nbsp;&nbsp;(�f�[�^��TAB��؂�A�P�s�ڂ̓t�B�[���h��)<br>");
    out.println("&nbsp;&nbsp;import/r&nbsp;&nbsp;<i>�e�[�u����</i><i>�f�[�^...</i>�Ńe�[�u�����e�[�u���̑S�f�[�^����U�N���A���Ă���C���|�[�g�������Ȃ��܂��B<br>");
    out.println("<br><br>");
    // export to
    out.print("<b>");
    out.print("export&nbsp;&nbsp;to&nbsp;&nbsp;<i>[�t�H���_��][;�I�v�V����]</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;�S�e�[�u���̃f�[�^��[�t�H���_��]�̃t�H���_��export�`���ŏo�͂��܂��B<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&lt;�I�v�V����&gt;<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&nbsp;;���o��ЃR�[�h<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;�E�E�E�w�肵����ЃR�[�h�Œ��o���܂�<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&nbsp;;���o��ЃR�[�h:�u����ЃR�[�h<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;�E�E�E�w�肵����ЃR�[�h�Œ��o���A�w�肵���u����ЃR�[�h�Œu�����܂�<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&nbsp;;TABLES=�e�[�u��ID:�e�[�u��ID:�c<br><br>");
    out.println("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;�E�E�E�w�肵���e�[�u��ID��ΏۂƂ��܂�<br>");
    out.println("<br><br>");
    // import from
    out.print("<b>");
    out.print("import&nbsp;&nbsp;from&nbsp;&nbsp;<i>[�t�H���_��]</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;[�t�H���_��]�̃t�H���_��export�`���f�[�^�𕜌����܂��B(�����f�[�^�͍폜����܂�)<br><br>");
    out.println("<br><br>");
    out.print("<b>");
    out.print("import&nbsp;&nbsp;append&nbsp;&nbsp;from&nbsp;&nbsp;<i>[�t�H���_��]</i><br>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;[�t�H���_��]�̃t�H���_��export�`���f�[�^�𕜌����܂��B(�����f�[�^�͎c�����܂�INSERT����܂�)<br><br>");
    out.println("<br><br>");
    if (stagingURL != null) {
      // stagingURL
      out.print("<b>");
      out.print("scan<br>");
      out.print("</b>");
      out.println("<br><br>");
      String url = stagingURL;
      if (url.indexOf("?") != -1) {
        url = url.substring(0, url.indexOf("?"));
        url = url.substring(0, url.lastIndexOf("/") + 1);
      }
      out.println("&nbsp;&nbsp;�����[�g��[" + url + "]�ƃt�@�C���̂��r�������Ȃ��܂��B<br><br>");
      out.println("<br><br>");
    }
    if (configEnabled) {
      // export to
      out.print("<b>");
      out.print("config");
      out.print("</b>");
      out.println("<br><br>");
      out.println("&nbsp;&nbsp;&nbsp;���ݒ��ʂ�\�����܂��B<br><br>");
      out.println("<br><br>");
    }
    if (dataSourceNames != null && dataSourceNames.length > 1) {
      // DATA_SOURCE_NAME2���w�肵���ꍇ�Ɏg����R�}���h
      // compare
      out.print("<b>");
      out.print("compare");
      out.print("</b>");
      out.println("<br><br>");
      out.println("&nbsp;&nbsp;2�̃f�[�^�x�[�X�Ԃ̕����e�[�u����`���r���܂��B<br>");
      out.println("&nbsp;&nbsp;(" + schemas[0] + "@" + dataSourceNames[0] + " : " + schemas[1] + "@" +  dataSourceNames[1] + ")<br>");
      out.println("<br><br>");
      //
      out.print("<b>");
      out.print("compare&nbsp;&nbsp;<i>�e�[�u����</i>&nbsp;&nbsp;<i>[�I�v�V����]</i>");
      out.print("</b>");
      out.println("<br><br>");
      out.println("&nbsp;&nbsp;2�̃f�[�^�x�[�X�Ԃ̃e�[�u�������̑S�f�[�^���r���قȂ�s��\�����܂��B<br>");
      out.println("&nbsp;&nbsp;�I�v�V������ ^�t�B�[���h�� ���w�肷�邱�Ƃɂ���r���O�t�B�[���h���w��ł��܂��B<br>");
      out.println("&nbsp;&nbsp;�I�v�V�����̍Ō�� WHERE����L�q���邱�Ƃɂ��Ώۃe�[�u���ɑ΂���WHERE�������w��ł��܂��B<br>");
      out.println("<br><br>");
      //
      out.print("<b>");
      out.print("compare&nbsp;&nbsp;processid=<i>�v���Z�XID</i>");
      out.print("</b>");
      out.println("<br><br>");
      out.println("&nbsp;&nbsp;2�̃f�[�^�x�[�X�Ԃ̃v���Z�X��`�f�[�^���r���قȂ�s��\�����܂��B<br>");
      out.println("<br><br>");
      //
      out.print("<b>");
      out.print("compare&nbsp;&nbsp;pageid=<i>�y�[�WID</i>");
      out.print("</b>");
      out.println("<br><br>");
      out.println("&nbsp;&nbsp;2�̃f�[�^�x�[�X�Ԃ̉�ʒ�`�f�[�^���r���قȂ�s��\�����܂��B<br>");
      out.println("<br><br>");
      //
      out.print("<b>");
      out.print("copy&nbsp;&nbsp;<i>�e�[�u����</i>");
      out.print("</b>");
      out.println("<br><br>");
      out.println("&nbsp;&nbsp;�f�[�u���f�[�^��" + schemas[1] + "@" + dataSourceNames[1] + "���猻�݂̃e�[�u���֑S���R�s�[���܂��B<br>");
      out.println("&nbsp;&nbsp;�f�[�u�����̓J���}��؂�ŕ����w�肷�邱�Ƃ��ł��܂��B<br>");
      out.println("&nbsp;&nbsp;�f�[�u�����Ɂu[PROCESS]�v���w�肷��ƃv���Z�X��`�֘A�e�[�u���ꎮ�A�u[PAGE]�v���w�肷��Ɖ�ʒ�`�֘A�ꎮ���ΏۂƂȂ�܂��B<br>");
      out.println("&nbsp;&nbsp;(�R�s�[��̃e�[�u�����̃f�[�^�͈�U�S�č폜����܂�)<br>");
      out.println("<br><br>");
    }
    // check table
    out.print("<b>");
    out.print("check table");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�����e�[�u���Ƙ_���e�[�u��(TABLEMASTER/TABLELAYOUTMASTER)���r���܂��B<br>");
    out.println("<br><br>");
    // check function
    out.print("<b>");
    out.print("check function");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�@�\�}�X�^�y�ы@�\�\�����̃p�b�P�[�WID�g�p�ۓ��̐��������`�F�b�N���܂��B<br>");
    out.println("<br><br>");
    // check function
    out.print("<b>");
    out.print("check class");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�N���X�t�@�C���̐�����(�d������)���`�F�b�N���܂��B<br>");
    out.println("<br><br>");
    // count
    out.print("<b>");
    out.print("count <i>[�I�v�V����]</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�S�e�[�u���̃��R�[�h�������J�E���g���܂��B<br>");
    out.println("&nbsp;&nbsp;�I�v�V�����ɁuCOMPANYID='�l'�v�A�uGROUP BY COMPANYID�v���w�肷�邱�Ƃ��\�ł��B<br>");
    out.println("<br><br>");
    // grep
    out.print("<b>");
    out.print("grep <i>�L�[���[�h</i> <i>[�Ώۃe�[�u��]</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�S�e�[�u���̃f�[�^���L�[���[�h���������܂��B(�L�[���[�h��\"|\"�ŋ�؂��ĕ����w��\)<br>");
    out.println("&nbsp;&nbsp;�Ώۃe�[�u����SQL���C���h�J�[�h�`���łP�܂��́A|��؂�Ńe�[�u���������w�肪�\�ł�<br>");
    out.println("&nbsp;&nbsp;grep/1 �`�̂悤�Ɏw�肷���1������������ł��؂�܂�<br>");
    out.println("&nbsp;&nbsp;�܂��Agrep/o �`�ŞB������(�󔒁E�S�p���p���_�����_����)�������Ȃ��܂�<br>");
    out.println("<br><br>");
    // replace
    out.print("<b>");
    out.print("replace <i>�L�[���[�h</i> <i>�ϊ��l</i> <i>[�Ώۃe�[�u��]</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�S�e�[�u���̃f�[�^���L�[���[�h���������Ώۃf�[�^�̌����ƕϊ��l�Œu������UPDATE���𐶐����܂��B<br>");
    out.println("&nbsp;&nbsp;�L�[���[�h�A�ϊ��l��|��؂�ŕ����w��\�ő΂ɂȂ�l�ɕϊ�����܂�(������v���Ă���K�v������܂�)<br>");
    out.println("&nbsp;&nbsp;�܂��Areplace/o �`�ŞB������(�󔒁E�S�p���p���_�����_����)�������Ȃ��܂�(���g�p����)<br>");
    out.println("<br><br>");
    // find
    out.print("<b>");
    out.print("find <i>�L�[���[�h</i> <i>[�Ώۃe�[�u��]</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;grep/o �`�Ɠ��`�̌����������Ȃ��܂�<br>");
    out.println("<br><br>");
    // findm
    out.print("<b>");
    out.print("findm <i>�L�[���[�h</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;find�����W���[���֘A�e�[�u����ΏۂɎ��s���܂�<br>");
    out.println("&nbsp;&nbsp;�i���s���ʂ̃_�u���N���b�N�őΏۃ��[�h�̍Č����������Ȃ��܂��j<br>");
    out.println("<br><br>");
    // desc
    out.print("<b>");
    out.print("desc <i>�_���e�[�u��ID</i>");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;�_���e�[�u����`����CREATE���𐶐����܂�<br>");
    out.println("<br><br>");
    // restart
    out.print("<b>");
    out.print("restart");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;config�Őݒ肵�� restart OS�R�}���h�����s���܂�<br>");
    out.println("<br><br>");
    // ���̑�
    out.print("<b>");
    out.print("���̑� ����@�\");
    out.print("</b>");
    out.println("<br><br>");
    out.println("&nbsp;&nbsp;SELECT/n ...  n�Ŏw�肵���s���Ō��ʃZ�b�g�̎擾��ł��؂�܂�<br><br>");
    out.println("&nbsp;&nbsp;\\<i>SQL</i> ...  �擪��\\��s�����ꍇ�́A���̂܂�SQL�����s���܂�<br><br>");
    out.println("&nbsp;&nbsp;SELECT/E ...  �Ώۃf�[�^��INSERT���`���ŕ\�����܂�<br><br>");
    
    if (dataSourceNames != null && dataSourceNames.length <= 1) {
      out.println("&nbsp;&nbsp;web.xml��DATA_SOURCE_NAME2���`���܂��ƁA�f�[�^�x�[�X�̔�r���\�ɂȂ�܂��B<br>");
    }
    
    out.flush();
    
    // �f�[�^�x�[�X���̕\��
    Connection conn = null;
    try {
      conn = getConnection();
      conn.setAutoCommit(false);
      DatabaseMetaData meta = conn.getMetaData();
      out.println("<hr>");
      out.println("[�V�X�e�����]<br>");
      out.println("<table>");
      // �T�[�o�[���
      String serverInfo = null;
      Properties prop = new Properties();
      // Tomcat�̃o�[�W�����擾
      InputStream is = null;
      try {
        is = getClass().getClassLoader().getResourceAsStream("org/apache/catalina/util/ServerInfo.properties");
        if (is != null) {
          prop.load(is);
          serverInfo = prop.getProperty("server.info");
        }
      } catch (Exception e) {
        
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e) {}
        }
      }
      if (serverInfo != null) {
        out.println("<tr><td>server.info:<td>" + serverInfo);
      }
      //out.println("<tr><td>_id:<td>" + _id);
      out.println("<tr><td>os.arch:<td>" + System.getProperty("os.arch"));
      out.println("<tr><td>os.name:<td>" + System.getProperty("os.name"));
      out.println("<tr><td>java.version:<td>" + System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
      out.println("<tr><td>java.home:<td>" + System.getProperty("java.home"));
      out.println("<tr><td>context path:<td>" + appPath);
      out.println("<tr><td>maxMemory:<td>" + Runtime.getRuntime().maxMemory());
      out.println("<tr><td>totalMemory:<td>" + Runtime.getRuntime().totalMemory());
      out.println("<tr><td>freeMemory:<td>" + Runtime.getRuntime().freeMemory());
      String dbaccessPath = DbAccessUtils.getJarPath("jp/co/bbs/unit/tools/servlets/DbAccess.class");
      if (dbaccessPath != null) {
        out.println("<tr><td>DBACCESS:<td>" + dbaccessPath);
      }
      ClassManager entityClassManager = new ClassManager(appPath);
      String mbbCorePath = DbAccessUtils.getJarPath("jp/co/bbs/unit/sys/AppController.class");
      if (mbbCorePath != null) {
        // JAR�̏ꍇ
        if (mbbCorePath.toLowerCase().endsWith(".jar")) {
          String ver = ClassManager.getManifestValue("jar:file:" + mbbCorePath, "Implementation-Version");
          if (ver != null) {
            // MANIFEST���擾�ł���΁A����"Implementation-Version"��⑫
            mbbCorePath = mbbCorePath + " (" + ver + ")";
          } else {
            // MANIFEST���擾�ł��Ȃ��ꍇ�́AAppController��_id��⑫
            mbbCorePath = mbbCorePath + " (" + entityClassManager.getMBBVersion() + ")";
          }
        } else {
          // JAR�t�@�C���łȂ��ꍇ�́i���̃P�[�X�͔������Ȃ��H�j�AAppController��_id��⑫
          mbbCorePath = mbbCorePath + " (" + entityClassManager.getMBBVersion() + ")";
        }
      } else {
        mbbCorePath = entityClassManager.getMBBVersion();
      }
      out.println("<tr><td>MBB:<td>" + mbbCorePath);
      String mbbHtmlToolsPath = DbAccessUtils.getJarPath("jp/co/bbs/unit/tools/html/PageElement.class");
      if (mbbHtmlToolsPath != null) {
        out.println("<tr><td><td>" + mbbHtmlToolsPath);
      }
      out.println("<tr><td>POI:<td>" + ExcelManager.getPOIVersion());
      if (!DocumentManager.isActive()) {
        out.println("<tr><td>DocumentManager:<td>off (mbbdoctools.jar���ǂݍ��߂܂���)");
      }
      out.println("</table>");
      out.println("<hr>");
      if (meta != null) {
        out.println("[Database Information (" + dataSourceNames[0] + ")]<br>");
        out.println("<table>");
        out.println("<tr><td>DatabaseProductName:<td>" + meta.getDatabaseProductName());
        out.println("<tr><td>DatabaseProductVersion:<td>" + meta.getDatabaseProductVersion());
        out.println("<tr><td>DriverName:<td>" + meta.getDriverName());
        out.println("<tr><td>DriverVersion:<td>" + meta.getDriverVersion());
        out.println("<tr><td>DriverClassName:<td>" + getDriverClassName(conn));
        out.println("<tr><td>URL:<td>" + meta.getURL());
        out.println("<tr><td>UserName:<td>" + meta.getUserName());
        out.println("</table>");
        out.flush();
      }
      if (dataSourceNames.length > 1) {
        for (int i = 1; i < dataSourceNames.length; ++i) {
          Connection conn2 = getConnection(i);
          if (conn2 != null) {
            DatabaseMetaData meta2 = conn2.getMetaData();
            out.println("<hr>");
            out.println("[Database Information (" + dataSourceNames[i] + ")]<br>");
            out.println("<table>");
            out.println("<tr><td>DatabaseProductName:<td>" + meta2.getDatabaseProductName());
            out.println("<tr><td>DatabaseProductVersion:<td>" + meta2.getDatabaseProductVersion());
            out.println("<tr><td>DriverName:<td>" + meta2.getDriverName());
            out.println("<tr><td>DriverVersion:<td>" + meta2.getDriverVersion());
            out.println("<tr><td>DriverClassName:<td>" + getDriverClassName(conn2));
            out.println("<tr><td>URL:<td>" + meta2.getURL());
            out.println("<tr><td>UserName:<td>" + meta2.getUserName());
            out.println("</table>");
            out.flush();
            conn2.close();
          }
        }
      }
    } catch(Exception e) {
      log_debug(e);
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e1) {
        }
      }
    }
    out.println("</td></tr></table>");
    out.flush();
  }
  
  private static String getDriverClassName(Connection conn) {
    StringBuffer sb = new StringBuffer();
    String className = null;
    if (conn instanceof TraceConnection) {
      className = ((TraceConnection)conn).getConnection().getClass().getName();
    } else {
      className = conn.getClass().getName();
    }
    sb.append(className);
    return sb.toString();
  }

  private boolean isDB2(int index) {
    if (index >= 0 && index < dbmsTypes.length && dbmsTypes[index] != null && dbmsTypes[index].equalsIgnoreCase("DB2")) {
      return true;
    }
    return false;
  }

  private boolean isOracle(int index) {
    if (index >= 0 && index < dbmsTypes.length && dbmsTypes[index] != null && dbmsTypes[index].equalsIgnoreCase("ORACLE")) {
      return true;
    }
    return false;
  }

  private boolean isMSSql(int index) {
    if (index >= 0 && index < dbmsTypes.length && dbmsTypes[index] != null && dbmsTypes[index].equalsIgnoreCase("MSSQL")) {
      return true;
    }
    return false;
  }

  private boolean isPgSql(int index) {
    if (index >= 0 && index < dbmsTypes.length && dbmsTypes[index] != null && dbmsTypes[index].equalsIgnoreCase("PGSQL")) {
      return true;
    }
    return false;
  }
  
  private boolean isDerby(int index) {
    if (index >= 0 && index < dbmsTypes.length && dbmsTypes[index] != null && dbmsTypes[index].equalsIgnoreCase("DERBY")) {
      return true;
    }
    return false;
  }
  
  private boolean isMySql(int index) {
    if (index >= 0 && index < dbmsTypes.length && dbmsTypes[index] != null && dbmsTypes[index].equalsIgnoreCase("MYSQL")) {
      return true;
    }
    return false;
  }
  
  private boolean isSupportedModuleType(String moduleType) {
    if ("DB/TYPE BODY".equals(moduleType) || "DB/PACKAGE".equals(moduleType) || "DB/PACKAGE BODY".equals(moduleType)) {
      // Oracle�̂݃T�|�[�g
      if (isOracle(0)) { 
        return true;
      } else {
        return false;
      }
    }
    return true;
  }
  
  private static boolean isSystemTable(String tableId) {
    if (tableId == null) {
      return true;
    }
    if (tableId.startsWith("BIN$")) {
      return true;
    }
    if (tableId.equals("CREATE$JAVA$LOB$TABLE") || tableId.equals("JAVA$OPTIONS")) {
      return true;
    }
    return false;
  }

  private boolean createFromFile(Connection conn, String dir, String tn, String autocommit) {
    File createFile = new File(dir + "/" + CREATE_SQL_FILE);
    if (createFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(createFile));
        String line = null;
        StringBuffer createSql = new StringBuffer();
        boolean start = false;
        while ((line = br.readLine()) != null) {
          if (!start) {
            if (line.toUpperCase().startsWith("CREATE TABLE " + tn.toUpperCase() + " (")) {
              start = true;
              createSql.append(line);
            }
          } else {
            createSql.append(" " + line);
            if (line.endsWith(");")) {
              break;
            }
          }
        }
        br.close();
        // TODO: ORACLE�̏ꍇ�́ADDL�𔭍s�������_��TRANSACTION��COMMIT�����̂Œ���
        String sql = createSql.toString();
        if (sql.trim().length() > 0) {
          if (sql.lastIndexOf(";") >= 0) {
            sql = sql.substring(0, sql.length() - 1);
          }
          if (isOracle(0) || isMSSql(0) || isPgSql(0)) {
            sql = DbAccessUtils.replaceAll(sql, " TIMESTAMP ,", " VARCHAR (30) ,");
            sql = DbAccessUtils.replaceAll(sql, " TIMESTAMP NOT ", " VARCHAR (30) NOT ");
          }
          if (!isOracle(0)) {
            sql = DbAccessUtils.replaceAll(sql, " VARCHAR2 (", " VARCHAR (");
            sql = DbAccessUtils.replaceAll(sql, " NUMBER (", " DECIMAL (");
            if (sql.indexOf(" USING INDEX") != -1) {
              sql = sql.substring(0, sql.indexOf(" USING INDEX")) + ")";
            }
          }
          if (!"1".equals(autocommit)) {
            try {
              conn.commit();
            } catch (SQLException e) {}
            try {
              conn.setAutoCommit(false);
            } catch (SQLException e) {}
          }
          Statement stmt = conn.createStatement();
          stmt.execute(sql);
          stmt.close();
          createFile = null;
          return true;
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
    createFile = null;
    return false;
  }
  
  private static void setAutoCommit(Connection conn, String autocommit) {
    if (conn == null) {
      return;
    }
    try {
      if (autocommit.equals("1")) {
        conn.setAutoCommit(true);
      } else {
        conn.setAutoCommit(false);
      }
    } catch(SQLException e) {
    }
  }

  /**
   * dataSource���Connection���擾(TraceLog�o�[�W����)
   * @return dataSource���擾����Connection
   * @throws SQLException
   */
  private Connection getConnection() throws SQLException {
    if (dataSources[0] != null) {
      if (traceLogs[0] == null) {
        traceLogs[0] = new TraceLogManager();
      }
      traceLogs[0].add(new TraceLogElement("getConnection():" + dataSourceNames[0]));
      Connection conn = ((TraceLogManager)traceLogs[0]).getConnection(dataSources[0].getConnection());
      return conn;
    } else {
      return null;
    }
  }

  /**
   * dataSources�̎w��index��Connection���擾(TraceLog�o�[�W����)
   * @return dataSource2���擾����Connection�A����`�̏ꍇ��null
   */
  private Connection getConnection(int index) {
    if (index < 0 || index >= dataSources.length) {
      return null;
    }
    try {
      if (index >= 0 && index < dataSources.length) {
        if (traceLogs[index] == null) {
          traceLogs[index] = new TraceLogManager();
        }
        traceLogs[index].add(new TraceLogElement("getConnection#" + index + ":" + dataSourceNames[index]));
        return ((TraceLogManager)traceLogs[index]).getConnection(dataSources[index].getConnection());
      }
    } catch(Exception e) {
      log_debug(e.getMessage());
    }
    return null;
  }

  /**
   * dataSource3���Connection���擾(TraceLog�o�[�W����)
   * @return dataSource3���擾����Connection�A����`�̏ꍇ��null
   */
  private Connection getConnection(String dataSource) throws SQLException {
    if (dataSource == null) {
      return null;
    }
    try {
      return getConnection(Integer.parseInt(dataSource) - 1);
    } catch (Exception e) {}
    return null;
  }

  /**
   * file �� zos �ɏo�͂��܂��B
   * @param zos zip�t�@�C���o�̓X�g���[��
   * @param file ���͌��t�@�C��
   */
  private void zipArchive(ZipOutputStream zos, File file) {
    if (file.isDirectory()) {
      // �f�B���N�g���͊܂܂��t�@�C�����ċN�Ăяo���B
      File[] files = file.listFiles();
      for (int i = 0; i < files.length; i++) {
        File f = files[i];
        zipArchive(zos, f);
      }
    } else {
      BufferedInputStream fis = null;
      try {
        // ���̓X�g���[������
        fis = new BufferedInputStream(new FileInputStream(file));

        // // Entry ���̂��擾����B
        // String entryName =
        // file.getAbsolutePath().replace(this.baseFilePath, "")
        // .substring(1);
        //
        // // �o�͐� Entry ��ݒ肷��B
        // zos.putNextEntry(new ZipEntry(entryName));

        // ���̓t�@�C����ǂݍ��ݏo�̓X�g���[���ɏ�������ł���
        byte[] buf = new byte[1024];
        while (true) {
          int len = fis.read(buf);
          if (len < 0) break;
          zos.write(buf, 0, len);
        }

      } catch (Exception e) {
        log_debug(e.getMessage());
      } finally {
        try {
          fis.close();
        } catch (IOException e) {
        }
      }
    }
  }
  
  /**
   * �R�}���h���C�����s�@�\
   * 
   * 
   * @param args
   */
  public static void main(String[] args) {
    String configName = null;
    String dir = ".";
    String exportpage = null;
    String exportprocess = null;
    String exporttable = null;
    String tmpl = null;
    String exportdatafield = null;
    String importfiles = null;
    String user = null;
    String pass = null;
    String ddl_to = null;
    String xls_to = null;
    String exclude = null;
    String url = null;
    boolean url_js = false;
    boolean url_jsp = false;
    boolean url_xls = false;
    
    System.out.println("DBACCESS ver." + version);
    if (args.length == 0) {
      System.out.println("-cfg UnitToolUser.cfg�̃t�@�C���p�X");
      System.out.println("-dir �o�͐�t�H���_");
      System.out.println("-exportpage �y�[�WID");
      System.out.println("-exportprocess �v���Z�XID");
      System.out.println("-exporttable �e�[�u��ID");
      System.out.println("-tmpl �e���v���[�g�t�@�C�� (-exporttable�Ɠ����g�p)");
      System.out.println("-exportdatafield �f�[�^�t�B�[���hID");
      System.out.println("-importfiles �C���|�[�g����t�@�C�����܂��̓f�B���N�g����");
      System.out.println("-user �f�[�^�x�[�X���[�U�[ID(cfg���D��)");
      System.out.println("-pass �f�[�^�x�[�X���[�U�[�p�X���[�h(cfg���D��)");
      System.out.println("-ddl_to DDL������f�B���N�g��");
      System.out.println("-xls_to EXCEL�o�͐�f�B���N�g��");
      System.out.println("-url �����[�gURL(�N���X�擾��)");
      System.out.println("�y�[�WID,�v���Z�XID,�e�[�u��ID��%�Ń��C���h�J�[�h�w�肪�ł��܂�.");
      return;
    }
    
    for (int i = 0; i < args.length; ++i) {
      String a1 = args[i];
      String a2 = null;
      if (i < args.length - 1) {
        a2 = args[i + 1];
      }
      if ("-cfg".equals(a1) && a2 != null && new File(a2).exists()) {
        configName = a2;
        ++i;
      } else if ("-dir".equals(a1) && a2 != null) {
        dir = a2;
        ++i;
      } else if ("-exportpage".equals(a1) && a2 != null) {
        exportpage = a2;
        ++i;
      } else if ("-exportprocess".equals(a1) && a2 != null) {
        exportprocess = a2;
        ++i;
      } else if ("-exporttable".equals(a1) && a2 != null) {
        exporttable = a2;
        ++i;
      } else if ("-tmpl".equals(a1) && a2 != null) {
        tmpl = a2;
        ++i;
      } else if ("-exportdatafield".equals(a1) && a2 != null) {
        exportdatafield = a2;
        ++i;
      } else if ("-importfiles".equals(a1) && a2 != null) {
        importfiles = a2;
        ++i;
      } else if ("-user".equals(a1) && a2 != null) {
        user = a2;
        ++i;
      } else if ("-pass".equals(a1) && a2 != null) {
        pass = a2;
        ++i;
      } else if ("-exclude".equals(a1) && a2 != null) {
        exclude = a2;
        ++i;
      } else if ("-ddl_to".equals(a1) && a2 != null) {
        ddl_to = a2;
        ++i;
      } else if ("-xls_to".equals(a1) && a2 != null) {
        xls_to = a2;
        ++i;
      } else if ("-url".equals(a1) && a2 != null) {
        url = a2;
        ++i;
      } else if ("-js".equals(a1)) {
        url_js = true;
      } else if ("-jsp".equals(a1)) {
        url_jsp = true;
      } else if ("-xls".equals(a1)) {
        url_xls = true;
      }
    }
    Properties config = new Properties();
    InputStream fis = null;
    try {
      if (configName != null) {
        fis = new FileInputStream(configName);
        config.load(fis);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {}
      }
    }
    String jdbcDriverClass = null;
    String jdbcURL = null;
    String dbuserid = null;
    String dbpassword = null;
    String classesPath = null;
    String proxy = null;
    if (config.getProperty("dbdriver") != null) {
      jdbcDriverClass = config.getProperty("dbdriver");
    } else if (config.getProperty("JDBCDriver") != null) {
      jdbcDriverClass = config.getProperty("JDBCDriver");
    }
    if (config.getProperty("dburl") != null) {
      jdbcURL = config.getProperty("dburl");
    } else if (config.getProperty("JDBCConnectionURL") != null) {
      jdbcURL = config.getProperty("JDBCConnectionURL");
    }
    if (config.getProperty("dbuser") != null) {
      dbuserid = config.getProperty("dbuser");
    } else if (config.getProperty("User") != null) {
      dbuserid = config.getProperty("User");
    }
    if (config.getProperty("dbpass") != null) {
      dbpassword = config.getProperty("dbpass");
    } else if (config.getProperty("Password") != null) {
      dbpassword = config.getProperty("Password");
    }
    if (config.getProperty("classpath") != null) {
      classesPath = config.getProperty("classpath");
    }
    if (config.getProperty("path") != null) {
      classesPath = config.getProperty("path") + "/WEB-INF/classes/;"
          + config.getProperty("path") + "/WEB-INF/lib";
    }
    if (config.getProperty("driverpath") != null) {
      classesPath = classesPath + ";" + config.getProperty("driverpath");
    }
    if (user != null && user.trim().length() > 0) {
      dbuserid = user;
    }
    if (pass != null && pass.trim().length() > 0) {
      dbpassword = pass;
    }
    if (config.getProperty("proxy") != null) {
      proxy = config.getProperty("proxy");
    }
    final String _jdbcDriverClass = jdbcDriverClass;
    final String _jdbcURL = jdbcURL;
    final String _dbuserid = dbuserid;
    final String _dbpassword = dbpassword;
    
    final Vector _connections = new Vector();
    DbAccess dbaccess = new DbAccess();
    DataSource dataSource = new DataSource() {
      public Connection getConnection() throws SQLException {
        Driver jdbcDriver = null;
        Class jdbcClass = null;
        try {
          jdbcClass = Class.forName(_jdbcDriverClass);
          if (jdbcClass != null) {
            jdbcDriver = (Driver) jdbcClass.newInstance();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        Connection conn = null;
        try {
          if (jdbcDriver != null) {
            Properties jdbcInfo = new Properties();
            jdbcInfo.put("user", _dbuserid);
            jdbcInfo.put("password", _dbpassword);
            conn = jdbcDriver.connect(_jdbcURL, jdbcInfo);
          } else {
            conn = DriverManager.getConnection(_jdbcURL, _dbuserid,
                _dbpassword);
          }
          _connections.add(conn);
        } catch (SQLException e) {
          e.printStackTrace();
          throw e;
        }
        return conn;
      }
      public Connection getConnection(String arg0, String arg1)
          throws SQLException {
        return getConnection();
      }
      public PrintWriter getLogWriter() throws SQLException {
        return null;
      }
      public int getLoginTimeout() throws SQLException {
        return 0;
      }
      public void setLogWriter(PrintWriter arg0) throws SQLException {
      }
      public void setLoginTimeout(int arg0) throws SQLException {
      }
    };
    try {
      dbaccess.addDataSource("jdbc/mbbds", dataSource, null, null);
    } catch (ServletException e) {
      e.printStackTrace();
    }
    
    if (exportpage != null) {
      File expDir = new File(dir);
      if (expDir.isDirectory() && new File(expDir, "page").isDirectory()) {
        expDir = new File(expDir, "page");
      }
      System.out.println("exportpage: path=" + expDir.getAbsolutePath());
      Connection conn = null;
      PreparedStatement stmt = null;
      ResultSet rs = null;
      try {
        conn = dbaccess.getConnection();
        conn.setAutoCommit(false);
        Vector params = dbaccess.getRelationParams(conn, "PAGEMASTER");
        String sql = "SELECT PAGEID FROM PAGEMASTER WHERE PAGEID LIKE ? ORDER BY PAGEID";
        stmt = conn.prepareStatement(sql);
        System.out.println(sql);
        exportpage = exportpage.replaceAll("\\*", "%");
        stmt.setString(1, exportpage);
        System.out.println("�o�͑Ώۃy�[�W:" + exportpage);
        rs = stmt.executeQuery();
        while (rs.next()) {
          String id = rs.getString(1);
          System.out.println(id);
          BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(expDir, id + ".csv")));
          dbaccess.printExportMCSV(bos, new String[]{id}, params);
          bos.flush();
          bos.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException se) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException se) {}
        }
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException se) {}
        }
      }
    }
    if (exportprocess != null) {
      File expDir = new File(dir);
      if (expDir.isDirectory() && new File(expDir, "process").isDirectory()) {
        expDir = new File(expDir, "process");
      }
      System.out.println("exportprocess: path=" + expDir.getAbsolutePath());
      Connection conn = null;
      PreparedStatement stmt = null;
      ResultSet rs = null;
      try {
        conn = dbaccess.getConnection();
        conn.setAutoCommit(false);
        Vector params = dbaccess.getRelationParams(conn, "PROCESSMASTER");
        String sql = "SELECT PROCESSID FROM PROCESSMASTER WHERE PROCESSID LIKE ? ORDER BY PROCESSID";
        stmt = conn.prepareStatement(sql);
        System.out.println(sql);
        exportprocess = exportprocess.replaceAll("\\*", "%");
        stmt.setString(1, exportprocess);
        System.out.println("�o�͑Ώۃv���Z�X:" + exportprocess);
        rs = stmt.executeQuery();
        while (rs.next()) {
          String id = rs.getString(1);
          System.out.println(id);
          BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(expDir, id + ".csv")));
          dbaccess.printExportMCSV(bos, new String[]{id}, params);
          bos.flush();
          bos.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException se) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException se) {}
        }
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException se) {}
        }
      }
    }
    if (exporttable != null) {
      File expDir = new File(dir);
      if (expDir.isDirectory() && new File(expDir, "table").isDirectory()) {
        expDir = new File(expDir, "table");
      }
      System.out.println("exporttable: path=" + expDir.getAbsolutePath());
      Connection conn = null;
      PreparedStatement stmt = null;
      ResultSet rs = null;
      try {
        conn = dbaccess.getConnection();
        conn.setAutoCommit(false);
        Vector params = dbaccess.getRelationParams(conn, "TABLEMASTER");
        String sql = "SELECT TABLEID FROM TABLEMASTER WHERE TABLEID LIKE ? ORDER BY TABLEID";
        stmt = conn.prepareStatement(sql);
        System.out.println(sql);
        exporttable = exporttable.replaceAll("\\*", "%");
        stmt.setString(1, exporttable);
        System.out.println("�o�͑Ώۃe�[�u��:" + exporttable);
        rs = stmt.executeQuery();
        while (rs.next()) {
          String id = rs.getString(1);
          System.out.println(id);
          BufferedOutputStream bos = null;
          try {
            if (tmpl == null) {
              bos = new BufferedOutputStream(new FileOutputStream(new File(expDir, id + ".csv")));
              dbaccess.printExportMCSV(bos, new String[]{id}, params);
              bos.flush();
            } else {
              bos = new BufferedOutputStream(new FileOutputStream(new File(expDir, id + ".xls")));
              DocumentManager.createTableDocument(conn, tmpl, id, bos);
              bos.flush();
            }
          } catch (IOException e) {
            e.printStackTrace();
            break;
          } finally {
            if (bos != null) {
              bos.close();
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException se) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException se) {}
        }
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException se) {}
        }
      }
    }
    if (exportdatafield != null) {
      File expDir = new File(dir);
      if (expDir.isDirectory() && new File(expDir, "datafield").isDirectory()) {
        expDir = new File(expDir, "datafield");
      }
      System.out.println("exportdatafield: path=" + expDir.getAbsolutePath());
      Connection conn = null;
      PreparedStatement stmt = null;
      ResultSet rs = null;
      try {
        conn = dbaccess.getConnection();
        conn.setAutoCommit(false);
        Vector params = dbaccess.getRelationParams(conn, "DATAFIELDMASTER");
        String sql = "SELECT DATAFIELDID FROM DATAFIELDMASTER WHERE DATAFIELDID LIKE ? ORDER BY DATAFIELDID";
        stmt = conn.prepareStatement(sql);
        System.out.println(sql);
        exportdatafield = exportdatafield.replaceAll("\\*", "%");
        stmt.setString(1, exportdatafield);
        System.out.println(exportdatafield);
        rs = stmt.executeQuery();
        while (rs.next()) {
          String id = rs.getString(1);
          System.out.println(id);
          BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(expDir, id + ".csv")));
          dbaccess.printExportMCSV(bos, new String[]{id}, params);
          bos.flush();
          bos.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException se) {}
        }
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException se) {}
        }
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException se) {}
        }
      }
    }
    if (ddl_to != null) {
      File outdir = new File(ddl_to);
      if (outdir.isDirectory()) {
        System.out.println("ddl to " + outdir.getAbsolutePath());
        PrintWriter pw = new PrintWriter(System.out);
        dbaccess.printDDLExportToFile(pw, "ddl to " + outdir.getAbsolutePath());
        pw.flush();
      }
    }
    if (xls_to != null) {
      File outdir = new File(xls_to);
      if (outdir.isDirectory()) {
        System.out.println("xls to " + outdir.getAbsolutePath());
        PrintWriter pw = new PrintWriter(System.out);
        try {
          Connection conn = dbaccess.getConnection();
          conn.setAutoCommit(false);
          PreparedStatement stmt = conn.prepareStatement("SELECT TABLEID, NAMEVALUE FROM TABLENAME WHERE DISPLANGID='JA' AND PROPERTYID='OFFICIALNAME' ORDER BY TABLEID");
          ResultSet rs = stmt.executeQuery();
          Vector excludeTables = new Vector();
          if (exclude != null) {
            excludeTables.addAll(Arrays.asList(exclude.split(",")));
          }
          while (rs.next()) {
            String table = rs.getString(1);
            String tableName = rs.getString(2);
            if (excludeTables.contains(table)) {
              pw.println(table + " ... (skip)");
              pw.flush();
              continue;
            }
            String fileName = table + "(" + tableName + ")";
            fileName = fileName.replaceAll("[\\\\\\./]", "_"); // �g���Ȃ������̒u��
            if (fileName.length() > 100) { // �����ꍇ�͒Z������(100����)
              fileName = fileName.substring(0, 100);
            }
            File file = new File(outdir, fileName + ".xls");
            pw.print(table + " ... " + file.getAbsolutePath());
            pw.flush();
            int count = 0;
            try {
              BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
              count = ExcelManager.excelExportFromTable(conn, table, out);
              out.flush();
              out.close();
            } catch (Exception e) {
              e.printStackTrace(pw);
            }
            pw.println(" " + count);
            pw.flush();
          }
          rs.close();
          stmt.close();
          conn.close();
        } catch(Exception e) {
          e.printStackTrace();
          e.printStackTrace(pw);
        }
      }
    }
    if (importfiles != null) {
      long timestamp = System.currentTimeMillis();
      File file = new File(importfiles);
      File[] files = null;
      if (file.exists()) {
        if (file.isDirectory()) {
          files = file.listFiles();
        } else {
          files = new File[]{file};
        }
        boolean error = false;
        Connection conn = null;
        try {
          conn = dbaccess.dataSources[0].getConnection();
          conn.setAutoCommit(false);
          String[] updateInfos = new String[]{"127.0.0.1", "MBB", "ADMIN", null};
          for (int i = 0; i < files.length; ++i) {
            file = files[i];
            if (file.getName().toLowerCase().endsWith(".csv")) {
              InputStream is = new FileInputStream(file);
              try {
                String msg = dbaccess.importMCSVData(null, conn, is, timestamp, IMPORT_NORMAL, updateInfos);
                System.out.println(msg);
              } catch (Exception e) {
                e.printStackTrace();
                error = true;
              }
              is.close();
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          error = true;
        } finally {
          if (conn != null) {
            try {
              if (error) {
                conn.rollback();
              } else {
                conn.commit();
              }
              conn.close();
            } catch (Exception e) {
            }
          }
        }
      }
    }
    if (url != null) {
      if (url.indexOf("?") == -1) {
        url = url + "?command=scan%20all";
      } else {
        url = url + "&command=scan%20all";
      }
      TreeMap remoteFiles = new TreeMap();
      String ignorePath = DEFAULT_IGNORE_PATH;
      url = url + "&ip=" + encodeURL(ignorePath.toString());
      log_debug("connect: " + url);
      try {
        URLConnection uc = DbAccessUtils.getURLConnection(url, proxy);
        uc.addRequestProperty("Accept-Encoding", "gzip,deflate");
        InputStream is = null;
        if ("gzip".equals(uc.getContentEncoding())) {
          is = new GZIPInputStream(uc.getInputStream());
        } else {
          is = uc.getInputStream();
        }
        String charset = "UTF-8";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
        String line = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("FILE\t")
              || line.startsWith("A\t") // A\t�͋��o�[�W�����݊��p
              ) {
            String[] fileEntry = line.split("\t", -1); // FILE ID MD5SUM TIMESTAMP
            if (fileEntry.length == 4) {
              remoteFiles.put(fileEntry[1], new String[]{fileEntry[2], fileEntry[3]});
            }
          } else if (line.indexOf("value=\"login\"") != -1) {
            // ���O�C����ʂ��\�����ꂽ�ꍇ�i�p�X���[�h�ݒ肠��̏ꍇ�j
            System.err.println("�F�؃G���[");
            break;
          }
        }
        br.close();
        StringBuffer cookies = new StringBuffer();
        for (Iterator ite = remoteFiles.keySet().iterator(); ite.hasNext(); ) {
          String path = (String)ite.next();
          if (path.indexOf("!") != -1) {
            continue;
          }
          if (url_js && path.endsWith(".js")) {
            // OK
          } else if (url_jsp && path.endsWith(".jsp")) {
            // OK
          } else if (url_xls && (path.endsWith(".xls") || path.endsWith(".XLS"))) {
            // OK
          } else if (path.equals("WEB-INF/lib/mbb_coretools.jar")) {
            // mbb_coretools.jar�i�������g�j�̓X�L�b�v
            continue;
          } else if (!path.startsWith("WEB-INF")) {
            // WEB-INF�ȊO�̓X�L�b�v
            continue;
          }
          System.out.println(path);
          File file = null;
          if (dir != null) {
            file = new File(dir, path);
          } else {
            file = new File(path);
          }
          DbAccessUtils.getRemoteFile(file, url, proxy, path, cookies);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
