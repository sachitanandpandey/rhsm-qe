package rhsm.base;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.testng.TestNGUtils;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.cli.tasks.SubscriptionManagerTasks;
import rhsm.data.ContentNamespace;
import rhsm.data.EntitlementCert;
import rhsm.data.Org;
import rhsm.data.ProductCert;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;
import rhsm.data.Translation;
import rhsm.data.YumRepo;

import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;
import com.redhat.qe.tools.abstraction.AbstractCommandLineData;

/**
 * @author ssalevan
 * @author jsefler
 *
 */
public class SubscriptionManagerCLITestScript extends SubscriptionManagerBaseTestScript {

	public static Connection dbConnection = null;
	
	protected static SubscriptionManagerTasks clienttasks	= null;
	protected static SubscriptionManagerTasks client1tasks	= null;	// client1 subscription manager tasks
	protected static SubscriptionManagerTasks client2tasks	= null;	// client2 subscription manager tasks
	
	public static Random randomGenerator = new Random(System.currentTimeMillis());
	
	public SubscriptionManagerCLITestScript() {
		super();
		// TODO Auto-generated constructor stub
	}


	
	
	// Configuration Methods ***********************************************************************
	
	@BeforeSuite(groups={"setup"},description="subscription manager set up")
	public void setupBeforeSuite() throws IOException, JSONException {
		if (isSetupBeforeSuiteComplete) return;
		
		// create SSHCommandRunners to connect to the subscription-manager clients
		File sshKeyPrivateKeyFile = new File(System.getProperty("automation.dir", null)+"/"+sm_sshKeyPrivate);
		if (!sshKeyPrivateKeyFile.exists()) Assert.fail("Expected to find the private ssh key for automation testing at '"+sshKeyPrivateKeyFile+"'.  Ask the RHSM Automation Administrator for a copy.");
		client = new SSHCommandRunner(sm_clientHostname, sm_sshUser, new File(sm_sshKeyPrivate), sm_sshkeyPassphrase, null);
		if (sm_sshEmergenecyTimeoutMS!=null) client.setEmergencyTimeout(Long.valueOf(sm_sshEmergenecyTimeoutMS));
		clienttasks = new SubscriptionManagerTasks(client);
		client1 = client;
		client1tasks = clienttasks;
		
		// will we be testing multiple clients?
		if (!(	sm_client2Hostname.equals("") /*|| client2username.equals("") || client2password.equals("")*/ )) {
			client2 = new SSHCommandRunner(sm_client2Hostname, sm_sshUser, new File(sm_sshKeyPrivate), sm_sshkeyPassphrase, null);
			if (sm_sshEmergenecyTimeoutMS!=null) client2.setEmergencyTimeout(Long.valueOf(sm_sshEmergenecyTimeoutMS));
			client2tasks = new SubscriptionManagerTasks(client2);
		} else {
			log.info("Multi-client testing will be skipped.");
		}
		
		// unregister clients in case they are still registered from prior run (DO THIS BEFORE SETTING UP A NEW CANDLEPIN)
		unregisterClientsAfterSuite();
		
		
		File serverCaCertFile = null;
		List<File> generatedProductCertFiles = new ArrayList<File>();
		
		// can we create an SSHCommandRunner to connect to the candlepin server ?
		if (!sm_serverHostname.equals("") && !sm_serverType.equals(CandlepinType.hosted)) {
			server = new SSHCommandRunner(sm_serverHostname, sm_sshUser, new File(sm_sshKeyPrivate), sm_sshkeyPassphrase, null);
			if (sm_sshEmergenecyTimeoutMS!=null) server.setEmergencyTimeout(Long.valueOf(sm_sshEmergenecyTimeoutMS));
			servertasks = new rhsm.cli.tasks.CandlepinTasks(server,sm_serverInstallDir,sm_serverImportDir,sm_serverType,sm_serverBranch);
		} else {
			log.info("Assuming the server is already setup and running.");
			servertasks = new rhsm.cli.tasks.CandlepinTasks(null,null,null,sm_serverType,sm_serverBranch);
		}
		
		// setup the candlepin server (only when the candlepin server is standalone)
		if (server!=null && sm_serverType.equals(CandlepinType.standalone)) {
			
			// NOTE: After updating the candlepin.conf file, the server needs to be restarted, therefore this will not work against the Hosted IT server which we don't want to restart or deploy
			//       I suggest manually setting this on hosted and asking calfanso to restart
			servertasks.updateConfigFileParameter("pinsetter.org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask.schedule","0 0\\/2 * * * ?");  // every 2 minutes
			servertasks.cleanOutCRL();
			servertasks.deploy();
			server.runCommandAndWait("df -h");server.runCommandAndWait("ls -Slh /var/log/tomcat6 | head");	// log candlepin's starting disk usage (for debugging information only)
			servertasks.setupTranslateToolkit(sm_translateToolkitGitRepository);
			servertasks.reportAPI();
			
			// also connect to the candlepin server database
			dbConnection = connectToDatabase();  // do this after the call to deploy since deploy will restart postgresql
			
			// fetch the generated Product Certs
			if (Boolean.valueOf(getProperty("sm.debug.fetchProductCerts","true"))) {
			log.info("Fetching the generated product certs...");
			//SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(server, "find "+serverInstallDir+servertasks.generatedProductsDir+" -name '*.pem'", 0);
			SSHCommandResult result = server.runCommandAndWait("find "+sm_serverInstallDir+servertasks.generatedProductsDir+" -name '*.pem'");
			String[] remoteFilesAsString = result.getStdout().trim().split("\\n");
			if (remoteFilesAsString.length==1 && remoteFilesAsString[0].equals("")) remoteFilesAsString = new String[]{};
			if (remoteFilesAsString.length==0) log.warning("No generated product certs were found on the candlpin server for use in testing.");
			for (String remoteFileAsString : remoteFilesAsString) {
				File remoteFile = new File(remoteFileAsString);
				File localFile = new File((getProperty("automation.dir", "/tmp")+"/tmp/"+remoteFile.getName()).replace("tmp/tmp", "tmp"));
				File localFileRenamed = new File(localFile.getPath().replace(".pem", "_.pem")); // rename the generated productCertFile to help distinguish it from a true RHEL productCertFiles
				RemoteFileTasks.getFile(server.getConnection(), localFile.getParent(),remoteFile.getPath());
				localFile.renameTo(localFileRenamed);
				generatedProductCertFiles.add(localFileRenamed);
			}
			}
		}
		
		// fetch the candlepin CA Cert (only when the candlepin server is not hosted)
		if (server!=null && !sm_serverType.equals(CandlepinType.hosted)) {
			log.info("Fetching Candlepin CA cert...");
			serverCaCertFile = new File((getProperty("automation.dir", "/tmp")+"/tmp/"+servertasks.candlepinCACertFile.getName()).replace("tmp/tmp", "tmp"));
			RemoteFileTasks.getFile(server.getConnection(), serverCaCertFile.getParent(), servertasks.candlepinCACertFile.getPath());
		}
		
		// setup the client(s) (with the fetched candlepin CA Cert and the generated product certs)
		for (SubscriptionManagerTasks smt : new SubscriptionManagerTasks[]{client2tasks, client1tasks}) {
			if (smt != null) setupClient(smt, serverCaCertFile, generatedProductCertFiles);
		}
		
		// determine the server URL that will be used for candlepin API calls
		if (sm_serverUrl.equals("")) {
			sm_serverUrl = getServerUrl(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile,"hostname"), clienttasks.getConfFileParameter(clienttasks.rhsmConfFile,"port"), clienttasks.getConfFileParameter(clienttasks.rhsmConfFile,"prefix"));
		}
		
		log.info("Installed version of candlepin...");
		JSONObject jsonStatus =null;
		try {
			//jsonStatus = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverHostname,sm_serverPort,sm_serverPrefix,"anybody","password","/status")); // seems to work no matter what credentials are passed		
			//jsonStatus = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverHostname,sm_serverPort,sm_serverPrefix,"","","/status"));
			//The above call works against onpremises, but causes the following againsta stage
			//201108251644:10.040 - INFO: SSH alternative to HTTP request: curl -k  --request GET https://rubyvip.web.stage.ext.phx2.redhat.com:80/clonepin/candlepin/status (rhsm.cli.tasks.CandlepinTasks.getResourceUsingRESTfulAPI)
			//201108251644:10.049 - WARNING: Required credentials not available for BASIC <any realm>@rubyvip.web.stage.ext.phx2.redhat.com:80 (org.apache.commons.httpclient.HttpMethodDirector.authenticateHost)
			//201108251644:10.052 - WARNING: Preemptive authentication requested but no default credentials available (org.apache.commons.httpclient.HttpMethodDirector.authenticateHost)
			jsonStatus = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/status"));
			if (jsonStatus!=null) {
				servertasks.statusCapabilities.clear();
				for (int i=0; i<jsonStatus.getJSONArray("managerCapabilities").length(); i++) {
					servertasks.statusCapabilities.add(jsonStatus.getJSONArray("managerCapabilities").getString(i));
				}
				servertasks.statusRelease		= jsonStatus.getString("release");
				servertasks.statusResult		= jsonStatus.getBoolean("result");
				servertasks.statusVersion		= jsonStatus.getString("version");
				servertasks.statusTimeUTC		= jsonStatus.getString("timeUTC");
				try {
				servertasks.statusStandalone	= jsonStatus.getBoolean("standalone");
				} catch(Exception e){log.warning(e.getMessage());log.warning("You should upgrade your candlepin server!");}

				//	# curl --insecure --user testuser1:password --request GET https://jsefler-f14-candlepin.usersys.redhat.com:8443/candlepin/status --stderr /dev/null | python -msimplejson/tool
				//	{
				//	    "release": "1", 
				//	    "result": true, 
				//	    "standalone": true, 
				//	    "timeUTC": "2012-03-08T18:58:07.688+0000", 
				//	    "version": "0.5.24"
				//	}
				
				//	# curl --stderr /dev/null --insecure --user ***:*** --request GET http://rubyvip.web.stage.ext.phx2.redhat.com/clonepin/candlepin/status | python -m simplejson/tool
				//	{
				//	    "managerCapabilities": [
				//	        "cores", 
				//	        "ram", 
				//	        "instance_multiplier", 
				//	        "derived_product", 
				//	        "cert_v3"
				//	    ], 
				//	    "release": "1", 
				//	    "result": true, 
				//	    "rulesSource": "DEFAULT", 
				//	    "rulesVersion": "4.3", 
				//	    "standalone": false, 
				//	    "timeUTC": "2013-09-27T13:51:08.783+0000", 
				//	    "version": "0.8.28"    <=== COULD ALSO BE "0.8.28.0" IF A HOT FIX WAS APPLIED
				//	}
				
				//	# curl -k -u ***:*** https://katellosach.usersys.redhat.com:443/katello/api/status --stderr /dev/null | python -mjson/tool
				//	{
				//	    "release": "Katello",
				//	    "result": true,
				//	    "standalone": true,
				//	    "timeUTC": "2014-01-29T09:04:14Z",
				//	    "version": "1.4.15-1.el6"
				//	}

				//TODO git candlepin version on hosted stage:
				// curl -s	http://git.corp.redhat.com/cgit/puppet-cfg/modules/candlepin/plain/data/rpm-versions.yaml?h=stage | grep candlepin
				// candlepin-it-jars: 0.5.26-1
				// candlepin-jboss: 0.5.26-1.el6

				log.info("Candlepin server '"+sm_serverHostname+"' is running: release="+servertasks.statusRelease+" version="+servertasks.statusVersion+" standalone="+servertasks.statusStandalone+" timeUTC="+servertasks.statusTimeUTC);
				Assert.assertEquals(servertasks.statusResult, true,"Candlepin status result");
				Assert.assertTrue(servertasks.statusRelease.matches("\\d+"), "Candlepin release matches d+");	// https://bugzilla.redhat.com/show_bug.cgi?id=703962
				Assert.assertTrue(servertasks.statusVersion.matches("\\d+\\.\\d+\\.\\d+(.\\d+)?"), "Candlepin version matches d+.d+.d+(.d+)? (Note: optional fourth digits indicate a hot fix)");
			}
		} catch (Exception e) {
			// Bug 843649 - subscription-manager server version reports Unknown against prod/stage candlepin
			log.warning("Ecountered exception while getting the Candlepin server '"+sm_serverHostname+"' version from the /status api: "+e);
		} 
		
	    File file = new File("test-output/version.txt"); // this will be in the automation.dir directory on hudson (workspace/automatjon/sm)
    	Writer output = new BufferedWriter(new FileWriter(file));
    	String infoMsg = "Installed versions...";
		log.info(infoMsg); output.write(infoMsg+"\n");
		if (client1 != null) {
			infoMsg = "Client1 '"+sm_client1Hostname+"' is running version: ";
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client1.runCommandAndWait("rpm -qa | egrep ^subscription-manager").getStdout();	// subscription-manager-0.63-1.el6.i686
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client1.runCommandAndWait("rpm -q python-rhsm").getStdout();	// python-rhsm-0.63-1.el6.i686
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client1.runCommandAndWait("cat /etc/redhat-release").getStdout();	// Red Hat Enterprise Linux Server release 6.1 Beta (Santiago)
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client1.runCommandAndWait("uname -a").getStdout();	// Linux jsefler-onprem-server.usersys.redhat.com 2.6.32-122.el6.x86_64 #1 SMP Wed Mar 9 23:54:34 EST 2011 x86_64 x86_64 x86_64 GNU/Linux
			log.info(infoMsg); output.write(infoMsg+"\n");
		}
		if (client2 != null) {
			infoMsg = "Client2 '"+sm_client2Hostname+"' is running version: ";
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client2.runCommandAndWait("rpm -qa | egrep ^subscription-manager").getStdout();	// subscription-manager-0.63-1.el6.i686
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client2.runCommandAndWait("rpm -q python-rhsm").getStdout();	// python-rhsm-0.63-1.el6.i686
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client2.runCommandAndWait("cat /etc/redhat-release").getStdout();	// Red Hat Enterprise Linux Server release 6.1 Beta (Santiago)
			log.info(infoMsg); output.write(infoMsg+"\n");
			infoMsg = client2.runCommandAndWait("uname -a").getStdout();	// Linux jsefler-onprem-server.usersys.redhat.com 2.6.32-122.el6.x86_64 #1 SMP Wed Mar 9 23:54:34 EST 2011 x86_64 x86_64 x86_64 GNU/Linux
			log.info(infoMsg); output.write(infoMsg+"\n");
		}
		output.close();
		
		isSetupBeforeSuiteComplete = true;
	}
	
	public void setupClient(SubscriptionManagerTasks smt, File serverCaCertFile, List<File> generatedProductCertFiles) throws IOException, JSONException{		
		smt.installSubscriptionManagerRPMs(sm_yumInstallOptions);
		if (sm_yumInstallZStreamUpdates)				smt.installZStreamUpdates(sm_yumInstallOptions, sm_yumInstallZStreamUpdatePackages);
		smt.installSubscriptionManagerRPMs(sm_rpmInstallUrls,sm_rpmUpdateUrls,sm_yumInstallOptions);
		
		// rewrite rhsmcertd.certFrequency -> rhsmcertd.certCheckInterval   see bug 882459
		String certFrequency = smt.getConfFileParameter(smt.rhsmConfFile, "rhsmcertd", "certFrequency");
		if (certFrequency!=null) {
			smt.commentConfFileParameter(smt.rhsmConfFile, "certFrequency");
			//smt.config(null, null, true, new String[]{"rhsmcertd","certCheckInterval".toLowerCase(),certFrequency});
			smt.addConfFileParameter(smt.rhsmConfFile, "rhsmcertd", "certCheckInterval", certFrequency);
		}
		// rewrite rhsmcertd.healFrequency -> rhsmcertd.autoAttachInterval   see bug 882459
		String healFrequency = smt.getConfFileParameter(smt.rhsmConfFile, "rhsmcertd", "healFrequency");
		if (healFrequency!=null) {
			smt.commentConfFileParameter(smt.rhsmConfFile, "healFrequency");
			//smt.config(null, null, true, new String[]{"rhsmcertd","autoAttachInterval".toLowerCase(),healFrequency});
			smt.addConfFileParameter(smt.rhsmConfFile, "rhsmcertd", "autoAttachInterval", healFrequency);
		}
		
		// rhsm.conf [server] configurations
		if (!sm_serverHostname.equals(""))				smt.updateConfFileParameter(smt.rhsmConfFile, "hostname", sm_serverHostname);							else sm_serverHostname = smt.getConfFileParameter(smt.rhsmConfFile, "hostname");
		if (!sm_serverPrefix.equals(""))				smt.updateConfFileParameter(smt.rhsmConfFile, "prefix", sm_serverPrefix);								else sm_serverPrefix = smt.getConfFileParameter(smt.rhsmConfFile, "prefix");
		if (!sm_serverPort.equals(""))					smt.updateConfFileParameter(smt.rhsmConfFile, "port", sm_serverPort);									else sm_serverPort = smt.getConfFileParameter(smt.rhsmConfFile, "port");
		if (!sm_serverInsecure.equals(""))				smt.updateConfFileParameter(smt.rhsmConfFile, "insecure", sm_serverInsecure);							else sm_serverInsecure = smt.getConfFileParameter(smt.rhsmConfFile, "insecure");
		if (!sm_serverSslVerifyDepth.equals(""))		smt.updateConfFileParameter(smt.rhsmConfFile, "ssl_verify_depth", sm_serverSslVerifyDepth);							else sm_serverInsecure = smt.getConfFileParameter(smt.rhsmConfFile, "insecure");
		if (!sm_serverCaCertDir.equals(""))				smt.updateConfFileParameter(smt.rhsmConfFile, "ca_cert_dir", sm_serverCaCertDir);						else sm_serverCaCertDir = smt.getConfFileParameter(smt.rhsmConfFile, "ca_cert_dir");
	
		// rhsm.conf [rhsm] configurations
		if (!sm_rhsmBaseUrl.equals(""))					smt.updateConfFileParameter(smt.rhsmConfFile, "baseurl", sm_rhsmBaseUrl);								else sm_rhsmBaseUrl = smt.getConfFileParameter(smt.rhsmConfFile, "baseurl");
		if (!sm_rhsmRepoCaCert.equals(""))				smt.updateConfFileParameter(smt.rhsmConfFile, "repo_ca_cert", sm_rhsmRepoCaCert);						else sm_rhsmRepoCaCert = smt.getConfFileParameter(smt.rhsmConfFile, "repo_ca_cert");
		//if (!rhsmShowIncompatiblePools.equals(""))	smt.updateConfFileParameter(smt.rhsmConfFile, "showIncompatiblePools", rhsmShowIncompatiblePools);		else rhsmShowIncompatiblePools = smt.getConfFileParameter(smt.rhsmConfFile, "showIncompatiblePools");
		if (!sm_rhsmProductCertDir.equals(""))			smt.updateConfFileParameter(smt.rhsmConfFile, "productCertDir", sm_rhsmProductCertDir);					else sm_rhsmProductCertDir = smt.getConfFileParameter(smt.rhsmConfFile, "productCertDir");
		if (!sm_rhsmEntitlementCertDir.equals(""))		smt.updateConfFileParameter(smt.rhsmConfFile, "entitlementCertDir", sm_rhsmEntitlementCertDir);			else sm_rhsmEntitlementCertDir = smt.getConfFileParameter(smt.rhsmConfFile, "entitlementCertDir");
		if (!sm_rhsmConsumerCertDir.equals(""))			smt.updateConfFileParameter(smt.rhsmConfFile, "consumerCertDir", sm_rhsmConsumerCertDir);				else sm_rhsmConsumerCertDir = smt.getConfFileParameter(smt.rhsmConfFile, "consumerCertDir");
	
		// rhsm.conf [rhsmcertd] configurations
		if (!sm_rhsmcertdCertFrequency.equals(""))		smt.updateConfFileParameter(smt.rhsmConfFile, /*"certFrequency" CHANGED BY BUG 882459 TO*/"certCheckInterval",	sm_rhsmcertdCertFrequency);				else sm_rhsmcertdCertFrequency = smt.getConfFileParameter(smt.rhsmConfFile, /*"certFrequency" CHANGED BY BUG 882459 TO*/"certCheckInterval");
		if (!sm_rhsmcertdHealFrequency.equals(""))		smt.updateConfFileParameter(smt.rhsmConfFile, /*"healFrequency" CHANGED BY BUG 882459 TO*/"autoAttachInterval",	sm_rhsmcertdHealFrequency);				else sm_rhsmcertdHealFrequency = smt.getConfFileParameter(smt.rhsmConfFile, /*"healFrequency" CHANGED BY BUG 882459 TO*/"autoAttachInterval");
		
		smt.initializeFieldsFromConfigFile();
		smt.removeAllCerts(true,true, false);
		smt.removeAllFacts();
		smt.initializeSystemComplianceAttributes();
		smt.removeRhnSystemIdFile();
		smt.installRepoCaCerts(sm_repoCaCertUrls);
		smt.setupRhnDefinitions(sm_rhnDefinitionsGitRepository);
		smt.setupTranslateToolkit(sm_translateToolkitGitRepository);

		// create a facts file that will tell candlepin what version of x509 entitlement certificates this client understands;  removeAllFacts() should be called before this block of code!
		if (sm_clientCertificateVersion!=null) {
			Map<String,String> map = new HashMap<String,String>();
			map.put("system.certificate_version",sm_clientCertificateVersion);
			smt.createFactsFileWithOverridingValues(smt.certVersionFactsFilename, map);
		}

		// transfer a copy of the candlepin CA Cert from the candlepin server to the clients so we can test in secure mode
		log.info("Copying Candlepin cert onto client to enable certificate validation...");
		smt.installRepoCaCert(serverCaCertFile, sm_serverHostname.split("\\.")[0]+".pem");
		
		// transfer copies of all the generated product certs from the candlepin server to the clients
		log.info("Copying Candlepin generated product certs onto client to simulate installed products...");
		smt.installProductCerts(generatedProductCertFiles);
	}
	
	protected static boolean isSetupBeforeSuiteComplete = false;
	
//	@BeforeSuite(groups={"gui-setup"},dependsOnMethods={"setupBeforeSuite"}, description="subscription manager gui set up")
//	public void setupGUIBeforeSuite() throws IOException {
//		// 201104251443:55.877 - FINE: ssh root@jsefler-onprem-workstation.usersys.redhat.com service vncserver restart (com.redhat.qe.tools.SSHCommandRunner.run)
//		// 201104251444:02.676 - FINE: Stdout: 
//		// Shutting down VNC server: 2:root [  OK  ]
//		// Starting VNC server: 2:root [  OK  ]
//		//if (client1!=null) RemoteFileTasks.runCommandAndAssert(client1, "service vncserver restart", /*Integer.valueOf(0) DON"T CHECK EXIT CODE SINCE IT RETURNS 1 WHEN STOP FAILS EVEN THOUGH START SUCCEEDS*/null, "Starting VNC server: 2:root \\[  OK  \\]", null);
//		//if (client2!=null) RemoteFileTasks.runCommandAndAssert(client2, "service vncserver restart", /*Integer.valueOf(0) DON"T CHECK EXIT CODE SINCE IT RETURNS 1 WHEN STOP FAILS EVEN THOUGH START SUCCEEDS*/null, "Starting VNC server: 2:root \\[  OK  \\]", null);
//		if (client1!=null) client1.runCommandAndWait("service vncserver restart");
//		if (client2!=null) client2.runCommandAndWait("service vncserver restart");
//
//		// vncviewer <client1tasks.hostname>:2
//	}
	
	
	protected String selinuxSuiteMarker = "SM TestSuite marker";	// do not use a timestamp on the whole suite marker
	protected String selinuxClassMarker = "SM TestClass marker "+String.valueOf(System.currentTimeMillis());	// using a timestamp on the class marker will help identify the test class during which a denial is logged
	@BeforeSuite(groups={"setup"},dependsOnMethods={"setupBeforeSuite"}, description="Ensure SELinux is Enforcing before running the test suite.")
	public void ensureSELinuxIsEnforcingBeforeSuite() {
		for (SubscriptionManagerTasks clienttasks : Arrays.asList(client1tasks,client2tasks)) {
			if (clienttasks!=null) {
				Assert.assertEquals(clienttasks.sshCommandRunner.runCommandAndWait("getenforce").getStdout().trim(), "Enforcing", "SELinux mode is set to enforcing on client "+clienttasks.sshCommandRunner.getConnection().getHostname());
				RemoteFileTasks.markFile(clienttasks.sshCommandRunner, clienttasks.auditLogFile, selinuxSuiteMarker);

			}
		}
	}
	
	@BeforeClass(groups={"setup"}, description="Mark the SELinux audit log before running the current class of tests so it can be searched for denials after the test class has run.")
	public void MarkSELinuxAuditLogBeforeClass() {
		for (SubscriptionManagerTasks clienttasks : Arrays.asList(client1tasks,client2tasks)) {
			if (clienttasks!=null) {
				RemoteFileTasks.markFile(clienttasks.sshCommandRunner, clienttasks.auditLogFile, selinuxClassMarker);
			}
		}
	}
	@AfterClass(groups={"setup"}, description="Search the SELinux audit log for denials after running the current class of tests")
	public void verifyNoSELinuxDenialsWereLoggedAfterClass() {
		for (SubscriptionManagerTasks clienttasks : Arrays.asList(client1tasks,client2tasks)) {
			if (clienttasks!=null) {
				Assert.assertTrue(RemoteFileTasks.getTailFromMarkedFile(clienttasks.sshCommandRunner, clienttasks.auditLogFile, selinuxClassMarker, "denied").trim().equals(""), "No SELinux denials found in the audit log '"+clienttasks.auditLogFile+"' on client "+clienttasks.sshCommandRunner.getConnection().getHostname()+" while executing this test class.");
			}
		}
	}
	
	public static String allRegisteredConsumerCertsDir = "/tmp/sm-allRegisteredConsumerCerts";
	@BeforeSuite(groups={"setup"},dependsOnMethods={"setupBeforeSuite"}, description="Prepare a temporary consumer cert directory where we can track all of the consumers created so we can return their entitlements after the suite.")
	public void prepareRegisteredConsumerCertsDirectoryBeforeSuite() {
		for (SubscriptionManagerTasks clienttasks : Arrays.asList(client1tasks,client2tasks)) {
			if (clienttasks!=null) {
				clienttasks.sshCommandRunner.runCommandAndWait("rm -rf "+allRegisteredConsumerCertsDir);
				clienttasks.sshCommandRunner.runCommandAndWait("mkdir -p "+allRegisteredConsumerCertsDir);
			}
		}
	}
	@BeforeSuite(groups={"setup"},dependsOnMethods={"setupBeforeSuite"}, description="Dump the system's hardware info to a tar file and upload it for archival review.")
	public void dumpHardwareInfoBeforeSuite() throws IOException {
		if (client!=null) {
			File remoteFile = new File("/root/hw_info_dump.tar");
			// [root@jsefler-5 ~]# dmidecode --dump-bin dmi_dump.bin; TMPDIR=`mktemp -d` && mkdir $TMPDIR/proc; cp /proc/cpuinfo $TMPDIR/proc/; cp /proc/sysinfo $TMPDIR/proc/; mkdir -p $TMPDIR/sys/devices/system/cpu; cp -r /sys/devices/system/cpu $TMPDIR/sys/devices/system/; tar -cf hw_info_dump.tar --ignore-failed-read --sparse dmi_dump.bin $TMPDIR  && tar -tvf hw_info_dump.tar && rm -rf $TMPDIR
			client.runCommandAndWait("dmidecode --dump-bin dmi_dump.bin; TMPDIR=`mktemp -d`; mkdir $TMPDIR/proc; cp /proc/cpuinfo $TMPDIR/proc/; cp /proc/sysinfo $TMPDIR/proc/; mkdir -p $TMPDIR/sys/devices/system/cpu; cp -r /sys/devices/system/cpu $TMPDIR/sys/devices/system/; tar -cf "+remoteFile.getName()+" --ignore-failed-read --sparse dmi_dump.bin $TMPDIR  && tar -tvf "+remoteFile.getName()+" && rm -rf $TMPDIR");
			if (!RemoteFileTasks.testExists(client, remoteFile.getPath())) client.runCommandAndWait("touch "+remoteFile.getPath());
			File localFile = new File((getProperty("automation.dir", "/tmp")+"/test-output/"+remoteFile.getName()));
			RemoteFileTasks.getFile(client.getConnection(), localFile.getParent(),remoteFile.getPath());
		}
	}
	@AfterSuite(groups={"cleanup"},description="attempt to delete any abandoned entitlements granted during the run of this suite")
	public void deleteAllRegisteredConsumerEntitlementsAfterSuite() {
		for (SubscriptionManagerTasks clienttasks : Arrays.asList(client1tasks,client2tasks)) {
			if (clienttasks!=null) {
				// determine the url to the server
				String url = "https://"+clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "hostname")+":"+clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "port")+clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "prefix");
				
				for (String consumerCertPath : Arrays.asList(clienttasks.sshCommandRunner.runCommandAndWait("find "+allRegisteredConsumerCertsDir+" -name '*cert.pem'").getStdout().trim().split("\n"))) {
					if (consumerCertPath.isEmpty()) continue;
					// extract the uuid from the consumerCertPath
					String uuid = consumerCertPath.replace(allRegisteredConsumerCertsDir, "").replace("_cert.pem", "").replace("/","");
					// extract the key from the consumerCertPath
					String consumerKeyPath = consumerCertPath.replace("cert.pem", "key.pem");
					// curl -k --cert /etc/pki/consumer/cert.pem --key /etc/pki/consumer/key.pem -X DELETE https://subscription.rhn.stage.redhat.com/subscription/consumers/2f801f45-3b79-42ee-9013-f4ad5bd35c3a/entitlements
					clienttasks.sshCommandRunner.runCommandAndWait("curl --stderr /dev/null --insecure --cert "+consumerCertPath+" --key "+consumerKeyPath+" --request DELETE "+url+"/consumers/"+uuid+"/entitlements");
				}
			}
		}
	}
	
	@AfterSuite(groups={"cleanup"},description="subscription manager tear down")
	public void unregisterClientsAfterSuite() {
		for (SubscriptionManagerTasks clienttasks : Arrays.asList(client1tasks,client2tasks)) {
			if (clienttasks!=null) {
				clienttasks.unregister_(null, null, null);	// release the entitlements consumed by the current registration
				clienttasks.clean_(null, null, null);	// in case the unregister fails, also clean the client
			}
		}
	}
	
	@AfterSuite(groups={"cleanup"},description="subscription manager tear down")
	public void disconnectDatabaseAfterSuite() {
		
		// close the candlepin database connection
		if (dbConnection!=null) {
			try {
				dbConnection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	// close the connection to the database
		}
		
		// log candlepin's ending disk usage (for debugging information only)
		if (server!=null) {
			server.runCommandAndWait("df -h");
			server.runCommandAndWait("ls -Slh /var/log/tomcat6 | head");
		}
	}
	
	@AfterSuite(groups={"return2beaker"/*"cleanup"*/},description="return clients to beaker",dependsOnMethods={"disconnectDatabaseAfterSuite","unregisterClientsAfterSuite"}/*, alwaysRun=true*/)
	public void return2beaker() {
		for (SSHCommandRunner client : Arrays.asList(client1,client2)) {
			if (client!=null) {
				if (Boolean.valueOf(getProperty("sm.client.return2beaker","true"))) {
					client.runCommandAndWait("return2beaker.sh");	// return this client back to beaker
				}
			}
		}
	}


	@BeforeSuite(groups={"setup"},dependsOnMethods={"setupBeforeSuite"}, description="delete selected secondary/duplicate subscriptions to reduce the number of available pools against a standalone candlepin server")
	public void deleteSomeSecondarySubscriptionsBeforeSuite() throws JSONException, Exception {
		Set<String> secondarySkusSkipped = new HashSet<String>();
		Set<String> secondarySkusToDelete = new HashSet<String>(Arrays.asList(new String[]{
		//	[root@jsefler-5 ~]# subscription-manager register --username admin --password admin --org admin --type system
		//	The system has been registered with ID: 9581087a-1b3a-483e-9756-c94142119d22 
		//	[root@jsefler-5 ~]# subscription-manager list --all --avail | egrep "SKU" | sort | wc -l
		//	86
		//  [root@jsefler-5 ~]# subscription-manager list --all --avail | egrep "SKU" | sort | awk '{print $2}' | xargs -i[] echo \"[]\",  
			"2cores-2ram-multiattr",
			"2cores-2ram-multiattr",
			"awesomeos-all-just-86_64-cont",
			"awesomeos-all-just-86_64-cont",
			"awesomeos-all-no-86_64-cont",
			"awesomeos-all-no-86_64-cont",
			"awesomeos-all-x86-cont",
			"awesomeos-all-x86-cont",
			"awesomeos-everything",
			"awesomeos-everything",
			"awesomeos-i386",
			"awesomeos-i386",
			"awesomeos-i686",
			"awesomeos-i686",
			"awesomeos-ia64",
			"awesomeos-ia64",
		//	"awesomeos-instancebased",		// kept because it will help test bug 963227
		//	"awesomeos-instancebased",
			"awesomeos-modifier",
			"awesomeos-modifier",
			"awesomeos-per-arch-cont",
			"awesomeos-per-arch-cont",
			"awesomeos-ppc64",
			"awesomeos-ppc64",
			"awesomeos-s390",
			"awesomeos-s390",
			"awesomeos-s390x",
			"awesomeos-s390x",
			"awesomeos-server",
			"awesomeos-server",
			"awesomeos-server-2-socket-std",
			"awesomeos-server-2-socket-std",
		//	"awesomeos-server-basic",		// kept because it is Multi-Entitlement: No
		//	"awesomeos-server-basic",
			"awesomeos-server-basic-dc",
			"awesomeos-server-basic-dc",
		//	"awesomeos-server-basic-me",	// kept because it is Multi-Entitlement: Yes
		//	"awesomeos-server-basic-me",
		//	"awesomeos-virt-4",				// kept because it is virt
		//	"awesomeos-virt-4",
		//	"awesomeos-virt-4",
		//	"awesomeos-virt-4",
		//	"awesomeos-virt-unlimited",
		//	"awesomeos-virt-unlimited",
		//	"awesomeos-virt-unlimited",
		//	"awesomeos-virt-unlimited",
			"awesomeos-workstation-basic",
			"awesomeos-workstation-basic",
			"awesomeos-x86",
			"awesomeos-x86",
			"awesomeos-x86_64",
			"awesomeos-x86_64",
			"cores-26",
			"cores-26",
			"cores4-multiattr",
			"cores4-multiattr",
			"cores-8-stackable",
			"cores-8-stackable",
			"management-100",
			"management-100",
			"non-stacked-6core8ram-multiattr",
			"non-stacked-6core8ram-multiattr",
			"non-stacked-8core4ram-multiattr",
			"non-stacked-8core4ram-multiattr",
			"non-stacked-multiattr",
			"non-stacked-multiattr",
			"ram-2gb-stackable",
			"ram-2gb-stackable",
			"ram2-multiattr",
			"ram2-multiattr",
			"ram-4gb-stackable",
			"ram-4gb-stackable",
			"ram-8gb",
			"ram-8gb",
			"ram-cores-8gb-4cores",
			"ram-cores-8gb-4cores",
			"sfs",
			"sfs",
			"sock2-multiattr",
			"sock2-multiattr",
			"sock-core-ram-multiattr",
			"sock-core-ram-multiattr",
			"stackable-with-awesomeos-x86_64",
			"stackable-with-awesomeos-x86_64",
		//	"virt-awesomeos-i386",			// kept because it is virt
		//	"virt-awesomeos-i386",
			""}));
		
		if (sm_clientOrg==null) return;
		if (!CandlepinType.standalone.equals(sm_serverType)) return;
		
		// process all of the pools belonging to ownerKey
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/owners/"+sm_clientOrg+"/pools?listall=true"));	
		List<String> secondarySubscriptionIdsDeleted = new ArrayList<String>();
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			String productId = jsonPool.getString("productId");
			String subscriptionId = jsonPool.getString("subscriptionId");
			
			// skip pools that were generated from consumption of a parent pool
			if (!jsonPool.isNull("sourceEntitlement")) continue;
			
			// skip pools that we do not want to delete
			if (!secondarySkusToDelete.contains(productId)) continue;
			
			// skip the first secondarySkusToDelete encountered
			if (!secondarySkusSkipped.contains(productId)) {
				secondarySkusSkipped.add(productId);
				continue;
			}
						
			// delete the subscription
			CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl, "/subscriptions/"+subscriptionId);
			secondarySubscriptionIdsDeleted.add(subscriptionId);
		}
		
		// refresh the pools
		if (!secondarySubscriptionIdsDeleted.isEmpty()) {
			JSONObject jobDetail = CandlepinTasks.refreshPoolsUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,sm_clientOrg);
			jobDetail = CandlepinTasks.waitForJobDetailStateUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,jobDetail,"FINISHED", 5*1000, 1);
		}
	}
	
	protected static ArrayList<String> invokedWorkaroundBugs = new ArrayList<String>();;
	@AfterSuite(groups={"cleanup"},description="log all the invoked bugzilla workarounds")
	public void logInvokedWorkaroundsAfterSuite() {
		if (!invokedWorkaroundBugs.isEmpty()) log.info(String.format("There were %s workarounds invoked for bugs in this run: https://bugzilla.redhat.com/buglist.cgi?bug_id=%s",invokedWorkaroundBugs.size(),joinListToString(invokedWorkaroundBugs,",")));
	}
	public static void addInvokedWorkaround(String bugId) {
		if (!invokedWorkaroundBugs.contains(bugId)) invokedWorkaroundBugs.add(bugId);
	}
	
	// Protected Methods ***********************************************************************


	
	protected String getServerUrl(String hostname, String port, String prefix) {
		// https://hostname:port/prefix
		if (!port.equals("")) port=(":"+port).replaceFirst("^:+", ":");
		if (!prefix.equals("")) prefix=("/"+prefix).replaceFirst("^/+", "/");
		return "https://"+hostname+port+prefix;	
	}
	
	protected Connection connectToDatabase() {
		/* Notes on setting up the db for a connection:
		 * # yum install postgresql-server
		 * 
		 * # service postgresql initdb 
		 * 
		 * # su - postgres
		 * $ psql
		 * # CREATE USER candlepin WITH PASSWORD 'candlepin';
		 * # ALTER user candlepin CREATEDB;
		 * [Ctrl-D]
		 * $ createdb -O candlepin candlepin
		 * $ exit
		 * 
		 * # vi /var/lib/pgsql/data/pg_hba.conf
		 * # TYPE  DATABASE    USER        CIDR-ADDRESS          METHOD
		 * local   all         all                               trust
		 * host    all         all         127.0.0.1/32          trust
		 *
		 * # vi /var/lib/pgsql/data/postgresql.conf
		 * listen_addresses = '*'
		 * 
		 * # netstat -lpn | grep 5432
		 * tcp        0      0 0.0.0.0:5432                0.0.0.0:*                   LISTEN      24935/postmaster    
		 * tcp        0      0 :::5432                     :::*                        LISTEN      24935/postmaster    
		 * unix  2      [ ACC ]     STREAM     LISTENING     1717127 24935/postmaster    /tmp/.s.PGSQL.5432
		 * 
		 */
		Connection dbConnection = null;
		try { 
			// Load the JDBC driver 
			Class.forName(sm_dbSqlDriver);	//	"org.postgresql.Driver" or "oracle.jdbc.driver.OracleDriver"
			
			// Create a connection to the database
			String url = sm_dbSqlDriver.contains("postgres")? 
					"jdbc:postgresql://" + sm_dbHostname + ":" + sm_dbPort + "/" + sm_dbName :
					"jdbc:oracle:thin:@" + sm_dbHostname + ":" + sm_dbPort + ":" + sm_dbName ;
			log.info(String.format("Attempting to connect to database with url and credentials: url=%s username=%s password=%s",url,sm_dbUsername,sm_dbPassword));
			dbConnection = DriverManager.getConnection(url, sm_dbUsername, sm_dbPassword);
			//log.finer("default dbConnection.getAutoCommit()= "+dbConnection.getAutoCommit());
			dbConnection.setAutoCommit(true);
			
			DatabaseMetaData dbmd = dbConnection.getMetaData(); //get MetaData to confirm connection
		    log.fine("Connection to "+dbmd.getDatabaseProductName()+" "+dbmd.getDatabaseProductVersion()+" successful.\n");

		} 
		catch (ClassNotFoundException e) { 
			log.warning("JDBC driver not found!:\n" + e.getMessage());
		} 
		catch (SQLException e) {
			log.warning("Could not connect to backend database:\n" + e.getMessage());
		}
		return dbConnection;
	}

	/* DELETEME  OLD CODE FROM ssalevan
	
	public void getSalesToEngineeringProductBindings(){
		try {
			String products = itDBConnection.nativeSQL("select * from butt;");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			log.info("Database query for Sales-to-Engineering product bindings failed!  Traceback:\n"+e.getMessage());
		}
	}
	*/
	

	public static void sleep(long milliseconds) {
		log.info("Sleeping for "+milliseconds+" milliseconds...");
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException e) {
			log.info("Sleep interrupted!");
		}
	}
	
	public static int getRandInt(){
		return Math.abs(randomGenerator.nextInt());
	}
	
	/**
	 * @param list - the list from which to return a subset of its contents
	 * @param subsetSize - specify the subset size desired 0<=subsetSize<=list.size();  if the value specified is outsize this range, it will be adjusted.
	 * @return a random subset of the list
	 * @throws Exception
	 */
	public static <T> List<T> getRandomSubsetOfList(List<T> list,int subsetSize) {
		if (subsetSize > list.size()) subsetSize = list.size();	// limit subsetSize to the size of the list
		if (subsetSize < 0) subsetSize = 0;
		List<T> clonedList = new ArrayList<T>(list);
		List<T> subsetList = new ArrayList<T>(subsetSize);
		for (int i = 0; i < subsetSize; i++) subsetList.add(clonedList.remove(randomGenerator.nextInt(clonedList.size())));
		return subsetList;
	}
	
	/**
	 * Randomize the input string case.  For example input="Hello World", output="HElLo wORlD"
	 * @param string
	 * @return
	 */
	protected String randomizeCaseOfCharactersInString(String string) {
		String output = "";
		for (int i=0; i<string.length(); i++) {
			String c = string.substring(i, i+1);
			output+=randomGenerator.nextInt(2)==0? c.toUpperCase():c.toLowerCase();
		}
		return output;
	}
	
	public static boolean isStringSimpleASCII(String str) {
		// Reference: http://www.asciitable.com/
	    for (int i = 0, n = str.length(); i < n; i++) {
	        if (str.charAt(i) > 127) { return false; }
	    }
	    return true;
	}
	
	public static boolean isInteger(String string) {  
	   try {  
	      Integer.parseInt(string);  
	      return true;  
	   }  
	   catch(Exception e) {  
	      return false;  
	   }  
	} 

	/**
	 * @param <T>
	 * @param list1
	 * @param list2
	 * @return true when lists have the same contents and same size, but not necessarily the same content order
	 */
	public static <T> boolean isEqualNoOrder(List<T> list1, List<T> list2) {  
	   return list1.containsAll(list2) && list2.containsAll(list1) && list1.size()==list2.size();
	   // similar to Assert.assertEqualsNoOrder(Object[] actual, Object[] expected, String message)
	}
	
	/**
	 * @param <T>
	 * @param list1
	 * @param list2
	 * @return true if list1.containsAny(list2)
	 */
	public static <T> boolean doesListOverlapList(List<T> list1, List<T> list2) {
		for (T list2item : list2) if (list1.contains(list2item)) return true;
		return false;
	}
	
	static public String joinListToString(List<String> list, String conjunction) {
	   StringBuilder sb = new StringBuilder();
	   boolean first = true;
	   for (String item : list) {
	      if (first) first = false;
	      else sb.append(conjunction);
	      sb.append(item);
	   }
	   return sb.toString();
	}
	
	static public List<String> getSubstringMatches(String string, String subStringRegex) {
		Pattern pattern = Pattern.compile(subStringRegex, Pattern.MULTILINE/* | Pattern.DOTALL*/);
		Matcher matcher = pattern.matcher(string);
		List<String> substringMatches = new ArrayList<String>();
		while (matcher.find()) substringMatches.add(matcher.group());
		return substringMatches;
	}
	
	static public boolean doesStringContainMatches(String string, String subStringRegex) {
		return !getSubstringMatches(string, subStringRegex).isEmpty();
	}
	
	
	// Protected Inner Data Class ***********************************************************************
	
	protected class RegistrationData {
		public String username=null;
		public String password=null;
		public String ownerKey=null;
		public SSHCommandResult registerResult=null;
		public List<SubscriptionPool> allAvailableSubscriptionPools=null;/*new ArrayList<SubscriptionPool>();*/
		public RegistrationData() {
			super();
		}
		public RegistrationData(String username, String password, String ownerKey,	SSHCommandResult registerResult, List<SubscriptionPool> allAvailableSubscriptionPools) {
			super();
			this.username = username;
			this.password = password;
			this.ownerKey = ownerKey;
			this.registerResult = registerResult;
			this.allAvailableSubscriptionPools = allAvailableSubscriptionPools;
		}
		
		public String toString() {
			String string = "";
			if (username != null)		string += String.format(" %s='%s'", "username",username);
			if (password != null)		string += String.format(" %s='%s'", "password",password);
			if (ownerKey != null)		string += String.format(" %s='%s'", "ownerKey",ownerKey);
			if (registerResult != null)	string += String.format(" %s=[%s]", "registerResult",registerResult);
			for (SubscriptionPool subscriptionPool : allAvailableSubscriptionPools) {
				string += String.format(" %s=[%s]", "availableSubscriptionPool",subscriptionPool);
			}
			return string.trim();
		}
	}
	
	// this list will be populated by subclass ResisterTests.RegisterWithCredentials_Test
	protected static List<RegistrationData> registrationDataList = new ArrayList<RegistrationData>();	

//	/**
//	 * Useful when trying to find a username that belongs to a different owner/org than the current username you are testing with.
//	 * @param key
//	 * @return null when no match is found
//	 * @throws JSONException
//	 */
//	protected RegistrationData findRegistrationDataNotMatchingOwnerKey(String key) throws JSONException {
//		Assert.assertTrue (!registrationDataList.isEmpty(), "The RegisterWithCredentials_Test has been executed thereby populating the registrationDataList with content for testing."); 
//		for (RegistrationData registration : registrationDataList) {
//			if (registration.ownerKey!=null) {
//				if (!registration.ownerKey.equals(key)) {
//					return registration;
//				}
//			}
//		}
//		return null;
//	}
	
	/**
	 * Useful when trying to find registerable credentials that belongs to a different (or same) owner than the current credentials you are testing with.
	 * @param matchingUsername
	 * @param username
	 * @param matchingOwnerKey
	 * @param ownerkey
	 * @return
	 * @throws JSONException
	 */
	protected List<RegistrationData> findGoodRegistrationData(Boolean matchingUsername, String username, Boolean matchingOwnerKey, String ownerKey) throws JSONException {
		List<RegistrationData> finalRegistrationData = new ArrayList<RegistrationData>();
		List<RegistrationData> goodRegistrationData = new ArrayList<RegistrationData>();
		List<String> ownersWithMatchingUsername = new ArrayList<String>();
		List<String> usernamesWithMatchingOwnerKey = new ArrayList<String>();
		Assert.assertTrue (!registrationDataList.isEmpty(), "The RegisterWithCredentials_Test has been executed thereby populating the registrationDataList with content for testing."); 
		for (RegistrationData registrationDatum : registrationDataList) {
			if (registrationDatum.registerResult.getExitCode().intValue()==0) {
				if (registrationDatum.ownerKey.equals(ownerKey)) usernamesWithMatchingOwnerKey.add(registrationDatum.username);
				if (registrationDatum.username.equals(username)) ownersWithMatchingUsername.add(registrationDatum.ownerKey);
				if ( matchingUsername &&  registrationDatum.username.equals(username) &&  matchingOwnerKey &&  registrationDatum.ownerKey.equals(ownerKey)) {
					goodRegistrationData.add(registrationDatum);
				}
				if ( matchingUsername &&  registrationDatum.username.equals(username) && !matchingOwnerKey && !registrationDatum.ownerKey.equals(ownerKey)) {
					goodRegistrationData.add(registrationDatum);
				}
				if (!matchingUsername && !registrationDatum.username.equals(username) &&  matchingOwnerKey &&  registrationDatum.ownerKey.equals(ownerKey)) {
					goodRegistrationData.add(registrationDatum);
				}
				if (!matchingUsername && !registrationDatum.username.equals(username) && !matchingOwnerKey && !registrationDatum.ownerKey.equals(ownerKey)) {
					goodRegistrationData.add(registrationDatum);
				}
			}
		}
		for (RegistrationData registrationDatum : goodRegistrationData) {
				if (ownerKey==null && !matchingOwnerKey &&  !matchingUsername) {
					if (!registrationDatum.username.equals(username)) {
						finalRegistrationData.add(registrationDatum);
					}
				} else if (ownerKey==null && !matchingOwnerKey && matchingUsername) {					
					if (registrationDatum.username.equals(username)) {
						finalRegistrationData.add(registrationDatum);
					}
				}
		}
		for (RegistrationData registrationDatum : goodRegistrationData) {
			if ( !matchingOwnerKey &&  !matchingUsername) {
				if (!ownersWithMatchingUsername.contains(registrationDatum.ownerKey )&& !usernamesWithMatchingOwnerKey.contains(registrationDatum.username)) {
					finalRegistrationData.add(registrationDatum);
				}
			} else if ( !matchingOwnerKey && matchingUsername) {					
				if (!ownersWithMatchingUsername.contains(registrationDatum.ownerKey) && username.equals(registrationDatum.username)) {
					finalRegistrationData.add(registrationDatum);
				}
			} else if ( matchingOwnerKey && !matchingUsername ) {
				if (ownersWithMatchingUsername.contains(registrationDatum.ownerKey) && !username.equals(registrationDatum.username)) {
					finalRegistrationData.add(registrationDatum);
				}
			} else {
				finalRegistrationData.add(registrationDatum);
			}
		}
		return finalRegistrationData;
	}
	
//	/**
//	 * Useful when trying to find a username that belongs to the same owner/org as the current username you are testing with.
//	 * @param key
//	 * @param username
//	 * @return null when no match is found
//	 * @throws JSONException
//	 */
//	protected RegistrationData findRegistrationDataMatchingOwnerKeyButNotMatchingUsername(String key, String username) throws JSONException {
//		Assert.assertTrue (!registrationDataList.isEmpty(), "The RegisterWithCredentials_Test has been executed thereby populating the registrationDataList with content for testing."); 
//		for (RegistrationData registration : registrationDataList) {
//			if (registration.ownerKey!=null) {
//				if (registration.ownerKey.equals(key)) {
//					if (!registration.username.equals(username)) {
//						return registration;
//					}
//				}
//			}
//		}
//		return null;
//	}
//	
//	/**
//	 * Useful when trying to find registration data results from a prior registration by a given username.
//	 * @param key
//	 * @param username
//	 * @return null when no match is found
//	 * @throws JSONException
//	 */
//	protected RegistrationData findRegistrationDataMatchingUsername(String username) throws JSONException {
//		Assert.assertTrue (!registrationDataList.isEmpty(), "The RegisterWithCredentials_Test has been executed thereby populating the registrationDataList with content for testing."); 
//		for (RegistrationData registration : registrationDataList) {
//			if (registration.username.equals(username)) {
//				return registration;
//			}
//		}
//		return null;
//	}
	
	/**
	 * This can be called by Tests that depend on it in a BeforeClass method to insure that registrationDataList has been populated.
	 * @throws Exception 
	 */
	protected void RegisterWithCredentials_Test() throws Exception {
		if (registrationDataList.isEmpty()) {
			clienttasks.unregister(null,null,null); // make sure client is unregistered
			for (List<Object> credentials : getRegisterCredentialsDataAsListOfLists()) {
				rhsm.cli.tests.RegisterTests registerTests = new rhsm.cli.tests.RegisterTests();
				registerTests.setupBeforeSuite();
				try {
					registerTests.RegisterWithCredentials_Test((String)credentials.get(0), (String)credentials.get(1), (String)credentials.get(2));			
				} catch (AssertionError e) {
					log.warning("Ignoring a failure in RegisterWithCredentials_Test("+(String)credentials.get(0)+", "+(String)credentials.get(1)+", "+(String)credentials.get(2)+")");
				}
			}
		}
	}
	
	/**
	 * On the connected candlepin server database, update the startdate and enddate in the cp_subscription table on rows where the pool id is a match.
	 * @param pool
	 * @param startDate
	 * @param endDate
	 * @throws SQLException 
	 */
	protected void updateSubscriptionPoolDatesOnDatabase(SubscriptionPool pool, Calendar startDate, Calendar endDate) throws SQLException {
		//DateFormat dateFormat = new SimpleDateFormat(CandlepinAbstraction.dateFormat);
		String updateSubscriptionPoolEndDateSql = "";
		String updateSubscriptionPoolStartDateSql = "";
		if (endDate!=null) {
			//updateSubscriptionPoolEndDateSql = "update cp_subscription set enddate='"+AbstractCommandLineData.formatDateString(endDate)+"' where id=(select pool.subscriptionid from cp_pool pool where pool.id='"+pool.poolId+"');";
			// AbstractCommandLineData.formatDateString does not default to enough significant figures, changing to EntitlementCert.formatDateString...
			updateSubscriptionPoolEndDateSql = "update cp_subscription set enddate='"+EntitlementCert.formatDateString(endDate)+"' where id=(select pool.subscriptionid from cp_pool pool where pool.id='"+pool.poolId+"');";
		}
		if (startDate!=null) {
			//updateSubscriptionPoolStartDateSql = "update cp_subscription set startdate='"+AbstractCommandLineData.formatDateString(startDate)+"' where id=(select pool.subscriptionid from cp_pool pool where pool.id='"+pool.poolId+"');";
			// AbstractCommandLineData.formatDateString does not default to enough significant figures, changing to EntitlementCert.formatDateString...
			updateSubscriptionPoolStartDateSql = "update cp_subscription set startdate='"+EntitlementCert.formatDateString(startDate)+"' where id=(select pool.subscriptionid from cp_pool pool where pool.id='"+pool.poolId+"');";
		}
		
		Statement sql = dbConnection.createStatement();
		if (endDate!=null) {
			log.info("About to change the endDate in the database for this subscription pool: "+pool);
			log.fine("Executing SQL: "+updateSubscriptionPoolEndDateSql);
			Assert.assertEquals(sql.executeUpdate(updateSubscriptionPoolEndDateSql), 1, "Updated one row of the cp_subscription table with sql: "+updateSubscriptionPoolEndDateSql);
		}
		if (startDate!=null) {
			log.info("About to change the startDate in the database for this subscription pool: "+pool);
			log.fine("Executing SQL: "+updateSubscriptionPoolStartDateSql);
			Assert.assertEquals(sql.executeUpdate(updateSubscriptionPoolStartDateSql), 1, "Updated one row of the cp_subscription table with sql: "+updateSubscriptionPoolStartDateSql);
		}
		sql.close();
	}
	
	protected void updateSubscriptionDatesOnDatabase(String subscriptionId, Calendar startDate, Calendar endDate) throws SQLException {
		//DateFormat dateFormat = new SimpleDateFormat(CandlepinAbstraction.dateFormat);
		String updateSubscriptionEndDateSql = "";
		String updateSubscriptionStartDateSql = "";
		if (endDate!=null) {
			updateSubscriptionEndDateSql = "update cp_subscription set enddate='"+AbstractCommandLineData.formatDateString(endDate)+"' where id='"+subscriptionId+"';";
		}
		if (startDate!=null) {
			updateSubscriptionStartDateSql = "update cp_subscription set startdate='"+AbstractCommandLineData.formatDateString(startDate)+"' where id='"+subscriptionId+"';";
		}
		
		Statement sql = dbConnection.createStatement();
		if (endDate!=null) {
			log.info("About to change the endDate in the database for this subscription id: "+subscriptionId);
			log.fine("Executing SQL: "+updateSubscriptionEndDateSql);
			Assert.assertEquals(sql.executeUpdate(updateSubscriptionEndDateSql), 1, "Updated one row of the cp_subscription table with sql: "+updateSubscriptionEndDateSql);
		}
		if (startDate!=null) {
			log.info("About to change the startDate in the database for this subscription id: "+subscriptionId);
			log.fine("Executing SQL: "+updateSubscriptionStartDateSql);
			Assert.assertEquals(sql.executeUpdate(updateSubscriptionStartDateSql), 1, "Updated one row of the cp_subscription table with sql: "+updateSubscriptionStartDateSql);
		}
		sql.close();
	}
	

	final String iso8601DatePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"; 								//"2012-02-08T00:00:00.000+0000"
	final DateFormat iso8601DateFormat = new SimpleDateFormat(iso8601DatePattern);				//"2012-02-08T00:00:00.000+0000"
// TODO DELETEME
//	protected Calendar parse_iso8601DateString(String dateString) {
//		try{
//			DateFormat dateFormat = new SimpleDateFormat(iso8601DateString);
//			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
//			Calendar calendar = new GregorianCalendar();
//			calendar.setTimeInMillis(dateFormat.parse(dateString).getTime());
//			return calendar;
//		}
//		catch (ParseException e){
//			log.warning("Failed to parse GMT date string '"+dateString+"' with format '"+iso8601DateString+"':\n"+e.getMessage());
//			return null;
//		}
//	}
	protected Calendar parseISO8601DateString(String dateString) {
		return parseISO8601DateString(dateString, null);
	}
	protected Calendar parseISO8601DateString(String dateString, String timeZone) {
		String datePattern = iso8601DatePattern;
		if (timeZone==null) datePattern = datePattern.replaceFirst("Z$", "");	// strip off final timezone offset symbol from iso8601DatePattern
		return parseDateStringUsingDatePattern(dateString, datePattern, timeZone);
	}
	protected Calendar parseDateStringUsingDatePattern(String dateString, String datePattern, String timeZone) {
		try{
			DateFormat dateFormat = new SimpleDateFormat(datePattern);	// format="yyyy-MM-dd'T'HH:mm:ss.SSSZ" will parse dateString="2012-02-08T00:00:00.000+0000"
			if (timeZone!=null) dateFormat.setTimeZone(TimeZone.getTimeZone(timeZone));	// timeZone="GMT"
			Calendar calendar = new GregorianCalendar();
			calendar.setTimeInMillis(dateFormat.parse(dateString).getTime());
			return calendar;
		}
		catch (ParseException e){
			log.warning("Failed to parse "+(timeZone==null?"":timeZone)+" date string '"+dateString+"' with format '"+datePattern+"':\n"+e.getMessage());
			return null;
		}
	}
	protected String formatISO8601DateString(Calendar date) throws UnsupportedEncodingException {
		String iso8601FormatedDateString = iso8601DateFormat.format(date.getTime());
		iso8601FormatedDateString = iso8601FormatedDateString.replaceFirst("(..$)", ":$1");		// "2012-02-08T00:00:00.000+00:00"	// see https://bugzilla.redhat.com/show_bug.cgi?id=720493 // http://books.xmlschemata.org/relaxng/ch19-77049.html requires a colon in the time zone for xsd:dateTime
		return iso8601FormatedDateString;
	}
	protected String urlEncode(String formattedDate) throws UnsupportedEncodingException {
		String urlEncodedDate = java.net.URLEncoder.encode(formattedDate, "UTF-8");	// "2012-02-08T00%3A00%3A00.000%2B00%3A00"	encode the string to escape the colons and plus signs so it can be passed as a parameter on an http call
		return urlEncodedDate;
	}
	
	
	protected Map<File,List<Translation>> buildTranslationFileMapForSubscriptionManager() {
		Map<File,List<Translation>> translationFileMapForSubscriptionManager = new HashMap<File, List<Translation>>();
		if (client==null) return translationFileMapForSubscriptionManager;
		
		SSHCommandResult translationFileListingResult = client.runCommandAndWait("rpm -ql subscription-manager | grep rhsm.mo");
		for (String translationFilePath : translationFileListingResult.getStdout().trim().split("\\n")) {
			if (translationFilePath.isEmpty()) continue; // skip empty lines
			
			File translationFile = new File(translationFilePath);
			
			// decompile the rhsm.mo file into its original-like rhsm.po file
			log.info("Decompiling the rhsm.mo file...");
			File translationPoFile = new File(translationFile.getPath().replaceFirst(".mo$", ".po"));
			RemoteFileTasks.runCommandAndAssert(client,"msgunfmt --no-wrap "+translationFile+ " -o "+translationPoFile,new Integer(0));
			
			// parse the translations from the rhsm.mo into the translationFileMap
			SSHCommandResult msgunfmtListingResult = client.runCommandAndWaitWithoutLogging("msgunfmt --no-wrap "+translationFilePath);
			translationFileMapForSubscriptionManager.put(translationFile, Translation.parse(msgunfmtListingResult.getStdout()));
		}
		return translationFileMapForSubscriptionManager;
	}
	
	
	protected Map<File,List<Translation>> buildTranslationFileMapForCandlepin() {
		Map<File,List<Translation>> translationFileMapForCandlepin = new HashMap<File, List<Translation>>();
		if (server==null) return translationFileMapForCandlepin;
		
		SSHCommandResult translationFileListingResult = server.runCommandAndWait("find "+sm_serverInstallDir+"/po -name *.po");
		for (String translationFilePath : translationFileListingResult.getStdout().trim().split("\\n")) {
			if (translationFilePath.isEmpty()) continue; // skip empty lines
			
			File translationFile = new File(translationFilePath);
					
			// parse the translations from the po file into the translationFileMap
			SSHCommandResult catListingResult = server.runCommandAndWaitWithoutLogging("cat "+translationFilePath);
			translationFileMapForCandlepin.put(translationFile, Translation.parse(catListingResult.getStdout()));
		}
		return translationFileMapForCandlepin;
	}
	
	
	protected void verifyCurrentEntitlementCertsAreReflectedInCurrentlySubscribedYumRepos(List<ProductCert> currentProductCerts) {
		if (currentProductCerts==null) currentProductCerts = clienttasks.getCurrentProductCerts();
		List<YumRepo> yumRepos = clienttasks.getCurrentlySubscribedYumRepos();
		List<EntitlementCert> entitlementCerts = clienttasks.getCurrentEntitlementCerts();
		int numYumReposProvided = 0;
		for (EntitlementCert entitlementCert : entitlementCerts) {
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (contentNamespace.type.equals("yum")) {
					if (clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, currentProductCerts)) {
						numYumReposProvided++;
						Assert.assertNotNull(YumRepo.findFirstInstanceWithMatchingFieldFromList("id", contentNamespace.label, yumRepos), "The '"+clienttasks.redhatRepoFile+"' has an entry for contentNamespace label '"+contentNamespace.label+"'.");
					}
				} else {
					// contentNamespace types that are not "yum" should not be included in the redhat.repo file (e.g. "file" and "kickstart")
					Assert.assertNull(YumRepo.findFirstInstanceWithMatchingFieldFromList("id", contentNamespace.label, yumRepos), "The '"+clienttasks.redhatRepoFile+"' should NOT have an entry for contentNamespace label '"+contentNamespace.label+"' because it's type '"+contentNamespace.type+"' is not equal to 'yum'.");				
				}
			}
		}
		if (entitlementCerts.isEmpty()) {
			Assert.assertTrue(yumRepos.isEmpty(),"When there are no entitlement contentNamespaces, then '"+clienttasks.redhatRepoFile+"' should have no yumRepo entries.");
		} else if (numYumReposProvided==0) {
			Assert.assertTrue(yumRepos.isEmpty(),"When none of the currently installed product certs provideTags matching the currently entitled content namespace requiredTags, then '"+clienttasks.redhatRepoFile+"' should have no yumRepo entries.");
		}
	}
	
	
	
	
	
	
	
	// Data Providers ***********************************************************************

	@DataProvider(name="getGoodRegistrationData")
	public Object[][] getGoodRegistrationDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getGoodRegistrationDataAsListOfLists());
	}
	/**
	 * @return List of [String username, String password, String owner]
	 */
	protected List<List<Object>> getGoodRegistrationDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		// parse the registrationDataList to get all the successfully registeredConsumers
		for (RegistrationData registeredConsumer : registrationDataList) {
			if (registeredConsumer.registerResult.getExitCode().intValue()==0) {
				ll.add(Arrays.asList(new Object[]{registeredConsumer.username, registeredConsumer.password, registeredConsumer.ownerKey}));
				
				// minimize the number of dataProvided rows (useful during automated testcase development)
				if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
			}
		}
		
		return ll;
	}
	
	@DataProvider(name="getAllAvailableSubscriptionPoolsData")
	public Object[][] getAllAvailableSubscriptionPoolsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getAllAvailableSubscriptionPoolsDataAsListOfLists());
	}
	protected List<List<Object>> getAllAvailableSubscriptionPoolsDataAsListOfLists() {
		return getAvailableSubscriptionPoolsDataAsListOfLists(true);
	}
	@DataProvider(name="getAvailableSubscriptionPoolsData")
	public Object[][] getAvailableSubscriptionPoolsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getAvailableSubscriptionPoolsDataAsListOfLists());
	}
	protected List<List<Object>> getAvailableSubscriptionPoolsDataAsListOfLists() {
		return getAvailableSubscriptionPoolsDataAsListOfLists(false);
	}
	/**
	 * @return a random subset of data rows from getAvailableSubscriptionPoolsDataAsListOfLists() (maximum of 3 rows) (useful to help reduce excessive test execution time)
	 * @throws Exception
	 */
	@DataProvider(name="getRandomSubsetOfAvailableSubscriptionPoolsData")
	public Object[][] getRandomSubsetOfAvailableSubscriptionPoolsDataAs2dArray() throws Exception {
		int subMax = 3;	// maximum subset count of data rows to return
		return TestNGUtils.convertListOfListsTo2dArray(getRandomSubsetOfList(getAvailableSubscriptionPoolsDataAsListOfLists(),subMax));
	}
	/**
	 * @return List of [SubscriptionPool pool]
	 */
	protected List<List<Object>> getAvailableSubscriptionPoolsDataAsListOfLists(boolean all) {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		if (sm_clientUsername==null) return ll;
		if (sm_clientPassword==null) return ll;
		
		// assure we are registered
		clienttasks.unregister(null, null, null);
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		if (client2tasks!=null)	{
			client2tasks.unregister(null, null, null);
			if (!sm_client2Username.equals("") && !sm_client2Password.equals(""))
				client2tasks.register(sm_client2Username, sm_client2Password, sm_client2Org, null, null, null, null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		}
		
		// unsubscribe from all consumed product subscriptions and then assemble a list of all SubscriptionPools
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		if (client2tasks!=null)	{
			client2tasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		}

		// populate a list of all available SubscriptionPools
		List<SubscriptionPool> pools = all? clienttasks.getCurrentlyAllAvailableSubscriptionPools():clienttasks.getCurrentlyAvailableSubscriptionPools();
		for (SubscriptionPool pool : pools) {
			ll.add(Arrays.asList(new Object[]{pool}));
			
			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		// manually reorder the pools so that the base "Red Hat Enterprise Linux*" pool is first in the list
		// This is a workaround for InstallAndRemovePackageAfterSubscribingToPool_Test so as to avoid installing
		// a package from a repo that has a package dependency from a repo that is not yet entitled.
		int i=0;
		for (List<Object> list : ll) {
			if (((SubscriptionPool)(list.get(0))).subscriptionName.startsWith("Red Hat Enterprise Linux")) {
				ll.remove(i);
				ll.add(0, list);
				break;
			}
			i++;
		}
		
		return ll;
	}

	
// DELETEME
//	@DataProvider(name="getUsernameAndPasswordData")
//	public Object[][] getUsernameAndPasswordDataAs2dArray() {
//		return TestNGUtils.convertListOfListsTo2dArray(getUsernameAndPasswordDataAsListOfLists());
//	}
//	protected List<List<Object>> getUsernameAndPasswordDataAsListOfLists() {
//		List<List<Object>> ll = new ArrayList<List<Object>>();
//		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users | python -mjson.tool
//		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users/testuser1 | python -mjson.tool
//		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users/testuser1/owners | python -mjson.tool
//
//		String[] usernames = sm_clientUsernames.split(",");
//		String[] passwords = sm_clientPasswords.split(",");
//		String password = passwords[0].trim();
//		for (int i = 0; i < usernames.length; i++) {
//			String username = usernames[i].trim();
//			// when there is not a 1:1 relationship between usernames and passwords, the last password is repeated
//			// this allows one to specify only one password when all the usernames share the same password
//			if (i<passwords.length) password = passwords[i].trim();
//			
//			// get the orgs for this username/password
//			List<String> orgs = clienttasks.getOrgs(username,password);
//			if (orgs.size()==1) {orgs.clear(); orgs.add(null);}	// 
//			
//			// append a username and password for each org the user belongs to
//			for (String org : orgs) {
//				ll.add(Arrays.asList(new Object[]{username,password,org}));
//			}
//		}
//		
//		return ll;
//	}

	@DataProvider(name="getRegisterCredentialsData")
	public Object[][] getRegisterCredentialsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getRegisterCredentialsDataAsListOfLists());
	}
	@DataProvider(name="getRegisterCredentialsExcludingNullOrgData")
	public Object[][] getRegisterCredentialsExcludingNullOrgDataAs2dArray() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		for (List<Object> l : getRegisterCredentialsDataAsListOfLists()) {
			// l contains: String username, String password, String owner
			if (l.get(2) != null) {
				if (!l.get(2).equals("null")) {
					ll.add(l);
				}
			}
		}
		return TestNGUtils.convertListOfListsTo2dArray(ll);
	}
	/**
	 * @return List of [String username, String password, String org]
	 */
	protected List<List<Object>> getRegisterCredentialsDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// when the candlepin server is not standalone, then we usually don't have access to the candlepin api paths to query the users, so let's use the input parameters 
		if (!sm_serverType.equals(CandlepinType.standalone)) {
			for (String username : sm_clientUsernames) {
				String password = sm_clientPasswordDefault;
			
				// get the orgs for this username/password
				//List<Org> orgs = clienttasks.getOrgs(username,password);	// fails when: You must first accept Red Hat's Terms and conditions. Please visit https://www.redhat.com/wapps/ugc
				List<Org> orgs = Org.parse(clienttasks.orgs_(username, password, null, null, null, null, null).getStdout());
				//if (orgs.size()==1) {orgs.clear(); orgs.add(new Org(null,null));}	// when a user belongs to only one org, then we don't really need to know the orgKey for registration
				if (orgs.isEmpty()) orgs.add(new Org("null","Null"));	// reveals when: You must first accept Red Hat's Terms and conditions. Please visit https://www.redhat.com/wapps/ugc
			
				// append a username and password for each org the user belongs to
				for (Org org : orgs) {
					ll.add(Arrays.asList(new Object[]{username,password,org.orgKey}));
				}
			}
			return ll;
		}
		
				
		// Notes...
		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users | python -mjson.tool
		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users/testuser1 | python -mjson.tool
		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users/testuser1/owners | python -mjson.tool

		// get all of the candlepin users
		// curl -k -u admin:admin https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users | python -mjson.tool
		JSONArray jsonUsers = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername,sm_serverAdminPassword,sm_serverUrl,"/users"));	
		for (int i = 0; i < jsonUsers.length(); i++) {
			JSONObject jsonUser = (JSONObject) jsonUsers.get(i);

			// Candlepin Users
			//    {
			//        "created": "2011-09-23T14:42:25.924+0000", 
			//        "hashedPassword": "e3e80f61a902ceca245e22005dffb4219ac1c5f7", 
			//        "id": "8a90f8c63296bc55013296bcc4040005", 
			//        "superAdmin": true, 
			//        "updated": "2011-09-23T14:42:25.924+0000", 
			//        "username": "admin"
			//    }, 
			
			// Katello Users...
			//    {
			//        "created_at": "2011-09-24T01:29:02Z", 
			//        "disabled": false, 
			//        "helptips_enabled": true, 
			//        "id": 1, 
			//        "own_role_id": 4, 
			//        "page_size": 25, 
			//        "password": "07a1dacc4f283e817c0ba353bd1452de49ce5723b2b7f56f6ee2f1f400a974b360f98acb90b630c7fa411f692bdb4c5cdd0f4b916efcf3c77e7cd0453446b185TS0YtS0uRjY0UznEsx7JqGIpEM1vEfIrBSNGdnXkFdkxsDhjmyFINBJVvkCTxeC7", 
			//        "updated_at": "2011-09-24T01:29:02Z", 
			//        "username": "admin"
			//    }, 
			
			//Boolean isSuperAdmin = jsonUser.getBoolean("superAdmin");
			String username = jsonUser.getString("username");
			String password = sm_clientPasswordDefault;
			if (username.equals(sm_serverAdminUsername)) password = sm_serverAdminPassword;
			
			
			// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=741961 - jsefler 9/29/2011
			if (username.equals("anonymous")) {
				boolean invokeWorkaroundWhileBugIsOpen = true;
				String bugId="741961"; 
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					log.warning("Ignoring the presence of user '"+username+"'.  No automated testing with this user will be executed.");
					continue;
				}
			}
			// END OF WORKAROUND
			
			// get the user's owners
			// curl -k -u testuser1:password https://jsefler-onprem-62candlepin.usersys.redhat.com:8443/candlepin/users/testuser1/owners | python -mjson.tool
			JSONArray jsonUserOwners = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(username,password,sm_serverUrl,"/users/"+username+"/owners"));	
			for (int j = 0; j < jsonUserOwners.length(); j++) {
				JSONObject jsonOwner = (JSONObject) jsonUserOwners.get(j);
				// {
				//    "contentPrefix": null, 
				//    "created": "2011-07-01T06:39:58.740+0000", 
				//    "displayName": "Snow White", 
				//    "href": "/owners/snowwhite", 
				//    "id": "8a90f8c630e46c7e0130e46ce114000a", 
				//    "key": "snowwhite", 
				//    "parentOwner": null, 
				//    "updated": "2011-07-01T06:39:58.740+0000", 
				//    "upstreamUuid": null
				// }
				String owner = jsonOwner.getString("key");
				
				// String username, String password, String owner
				ll.add(Arrays.asList(new Object[]{username,password,owner}));
			}
			
			// don't forget that some users (for which no owners are returned) probably have READ_ONLY permission to their orgs
			if (jsonUserOwners.length()==0) {
				ll.add(Arrays.asList(new Object[]{username,password,null}));			
			}
			
			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		return ll;
	}
	
	@DataProvider(name="getAllConsumedProductSubscriptionsData")
	public Object[][] getAllConsumedProductSubscriptionsDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllConsumedProductSubscriptionsDataAsListOfLists());
	}
	/**
	 * @return List of [ProductSubscription productSubscription]
	 */
	protected List<List<Object>> getAllConsumedProductSubscriptionsDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		if (sm_clientUsername==null) return ll;
		if (sm_clientPassword==null) return ll;
		
		// first make sure we are subscribed to all pools
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		
		// then assemble a list of all consumed ProductSubscriptions
		for (ProductSubscription productSubscription : clienttasks.getCurrentlyConsumedProductSubscriptions()) {
			ll.add(Arrays.asList(new Object[]{productSubscription}));
			
			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		return ll;
	}
	
	
	@DataProvider(name="getAllEntitlementCertsData")
	public Object[][] getAllEntitlementCertsDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllEntitlementCertsDataAsListOfLists());
	}
	/**
	 * @return List of [EntitlementCert entitlementCert]
	 */
	protected List<List<Object>> getAllEntitlementCertsDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (clienttasks==null) return ll;
		if (sm_clientUsername==null) return ll;
		if (sm_clientPassword==null) return ll;
		
		// first make sure we are subscribed to all pools
		clienttasks.unregister(null, null, null);
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null, null, null, null, (String)null, null, null, null, null, false, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();

		
		// then assemble a list of all consumed ProductSubscriptions
		for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
			ll.add(Arrays.asList(new Object[]{entitlementCert}));
			
			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}
		
		return ll;
	}
	
	
	@DataProvider(name="getAllSystemSubscriptionPoolProductData")
	public Object[][] getAllAvailableSystemSubscriptionPoolProductDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getSystemSubscriptionPoolProductDataAsListOfLists(false, false));
	}
	@DataProvider(name="getAvailableSystemSubscriptionPoolProductData")
	public Object[][] getAvailableSystemSubscriptionPoolProductDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getSystemSubscriptionPoolProductDataAsListOfLists(true, false));
	}

	/**
	 * @param matchSystemSoftware TODO
	 * @return List of [String productId, JSONArray bundledProductDataAsJSONArray]
	 */
	protected List<List<Object>> getSystemSubscriptionPoolProductDataAsListOfLists(boolean matchSystemHardware, boolean matchSystemSoftware) throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		List <String> productIdsAddedToSystemSubscriptionPoolProductData = new ArrayList<String>();

		// get the owner key for clientusername, clientpassword
		String consumerId = clienttasks.getCurrentConsumerId();
		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null));
		String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);
		// java.lang.RuntimeException: org.json.JSONException: JSONObject["owner"] not found.
		// ^^^ this will be thrown when the consumerId has been deleted at the server, but the client does not know it.

		Calendar now = new GregorianCalendar();
		now.setTimeInMillis(System.currentTimeMillis());
		
		// get the currently installed product certs
		List<ProductCert> productCerts = clienttasks.getCurrentProductCerts();
		
		// process all of the subscriptions belonging to ownerKey
		JSONArray jsonSubscriptions = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+ownerKey+"/subscriptions"));	
		for (int i = 0; i < jsonSubscriptions.length(); i++) {
			JSONObject jsonSubscription = (JSONObject) jsonSubscriptions.get(i);
			
			// skip future subscriptions that are not valid today (at this time now)
			Calendar startDate = parseISO8601DateString(jsonSubscription.getString("startDate"),"GMT");	// "startDate":"2012-02-08T00:00:00.000+0000"
			Calendar endDate = parseISO8601DateString(jsonSubscription.getString("endDate"),"GMT");	// "endDate":"2013-02-07T00:00:00.000+0000"
			if (!(startDate.before(now) && endDate.after(now))) continue;
			
			JSONObject jsonProduct = (JSONObject) jsonSubscription.getJSONObject("product");
			String productId = jsonProduct.getString("id");
			String productName = jsonProduct.getString("name");
			
			// skip subscriptions that have already been added to SystemSubscriptionPoolProductData
			if (productIdsAddedToSystemSubscriptionPoolProductData.contains(productId)) continue;

			// process this subscription productId
			JSONArray jsonProductAttributes = jsonProduct.getJSONArray("attributes");
			boolean productAttributesPassRulesCheck = true; // assumed
			String productAttributeSocketsValue = "";
			List<String> productSupportedArches = new ArrayList<String>();
			String productAttributeStackingIdValue = null;
			for (int j = 0; j < jsonProductAttributes.length(); j++) {	// loop product attributes to find a stacking_id
				if (((JSONObject) jsonProductAttributes.get(j)).getString("name").equals("stacking_id")) {
					productAttributeStackingIdValue = ((JSONObject) jsonProductAttributes.get(j)).getString("value");
					break;
				}
			}
			for (int j = 0; j < jsonProductAttributes.length(); j++) {
				JSONObject jsonProductAttribute = (JSONObject) jsonProductAttributes.get(j);
				String attributeName = jsonProductAttribute.getString("name");
				//String attributeValue = jsonProductAttribute.getString("value");
				String attributeValue = jsonProductAttribute.isNull("value")? null:jsonProductAttribute.getString("value");
				if (attributeName.equals("arch")) {
					productSupportedArches.addAll(Arrays.asList(attributeValue.trim().split(" *, *")));	// Note: the arch attribute can be a comma separated list of values
					if (productSupportedArches.contains("x86")) {productSupportedArches.addAll(Arrays.asList("i386","i486","i586","i686"));}  // Note: x86 is a general arch to cover all 32-bit intel microprocessors 
					if (!productSupportedArches.contains("ALL") && !productSupportedArches.contains(clienttasks.arch)) {
						productAttributesPassRulesCheck = false;
					}
				}
				if (attributeName.equals("variant")) {
//					if (!attributeValue.equalsIgnoreCase("ALL") && !attributeValue.equalsIgnoreCase(clienttasks.variant)) {
//						productAttributesPassRulesCheck = false;
//					}
				}
				if (attributeName.equals("type")) {

				}
				if (attributeName.equals("multi-entitlement")) {

				}
				if (attributeName.equals("warning_period")) {

				}
				if (attributeName.equals("version")) {
//					if (!attributeValue.equalsIgnoreCase(clienttasks.version)) {
//						productAttributesPassRulesCheck = false;
//					}
				}
				if (attributeName.equals("requires_consumer_type")) {
					if (!attributeValue.equalsIgnoreCase(ConsumerType.system.toString())) {
						productAttributesPassRulesCheck = false;
					}
				}
				if (attributeName.equals("stacking_id")) {
					// productAttributeStackingIdValue = attributeValue; // was already searched for above
				}
				if (attributeName.equals("sockets")) {
					productAttributeSocketsValue = attributeValue;
					
					// if this subscription is stackable (indicated by the presence of a stacking_id attribute)
					// then there is no need to check the system's sockets to see if this subscription should be available 
					// Because this subscription is stackable, it better not be filtered out from availability based on the system's sockets.
					if (productAttributeStackingIdValue==null) {
						
						SOCKETS:
						{
							// if the sockets attribute is not numeric (e.g. null or "unlimited"),  then this subscription should be available to this client
							try {Integer.valueOf(attributeValue);}
							catch (NumberFormatException e) {
								// do not mark productAttributesPassRulesCheck = false;
								log.warning("THE VALIDITY OF SUBSCRIPTION productName='"+productName+"' productId='"+productId+"' IS QUESTIONABLE.  IT HAS A '"+attributeName+"' PRODUCT ATTRIBUTE OF '"+attributeValue+"' WHICH IS NOT NUMERIC.  SIMPLY IGNORING THIS ATTRIBUTE.");
								break SOCKETS;
							}
							
							// if the sockets attribute is zero, then this subscription should be available to this client
							if (Integer.valueOf(attributeValue)==0) {
								// do not mark productAttributesPassRulesCheck = false;
							} else
	
							// if the socket count on this client exceeds the sockets attribute, then this subscription should NOT be available to this client
							if (Integer.valueOf(attributeValue) < Integer.valueOf(clienttasks.sockets)) {
								if (matchSystemHardware) productAttributesPassRulesCheck = false;
							}
						}
					}
				}
			}
			if (productAttributesPassRulesCheck) {
				
				// process this subscription's providedProducts
				Boolean atLeastOneProvidedProductIsInstalled = false; // assumed
				Boolean atLeastOneProvidedProductSatisfiesArch = false; // assumed
				JSONArray jsonBundledProductData = new JSONArray();
				JSONArray jsonProvidedProducts = (JSONArray) jsonSubscription.getJSONArray("providedProducts");
				if (jsonProvidedProducts.length()==0) atLeastOneProvidedProductIsInstalled = true;	// effectively true when no provided products are installed
				if (jsonProvidedProducts.length()==0) atLeastOneProvidedProductSatisfiesArch = true;	// effectively true when no provided products are installed
				for (int k = 0; k < jsonProvidedProducts.length(); k++) {
					JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(k);
					String providedProductName = jsonProvidedProduct.getString("name");
					String providedProductId = jsonProvidedProduct.getString("id");

					// process this providedProducts attributes
					JSONArray jsonProvidedProductAttributes = jsonProvidedProduct.getJSONArray("attributes");
					boolean providedProductAttributesPassRulesCheck = true; // assumed
					for (int l = 0; l < jsonProvidedProductAttributes.length(); l++) {
						JSONObject jsonProvidedProductAttribute = (JSONObject) jsonProvidedProductAttributes.get(l);
						String attributeName = jsonProvidedProductAttribute.getString("name");
						//String attributeValue = jsonProvidedProductAttribute.getString("value");
						String attributeValue = jsonProvidedProductAttribute.isNull("value")? null:jsonProvidedProductAttribute.getString("value");
						/* 6/17/2011 The availability of a Subscription depends only on its attributes and NOT the attributes of its provided products.
						 * You will get ALL of its provided product even if they don't make arch/socket sense.
						 * In this case you could argue that it is not subscription-manager's job to question the meaningfulness of the subscription and its provided products.
						 * For this reason, I am commenting out all the providedProductAttributesPassRulesCheck = false; ... (except "type")
						 */
						if (attributeName.equals("arch")) {
							List<String> supportedArches = new ArrayList<String>(Arrays.asList(attributeValue.trim().split(" *, *")));	// Note: the arch attribute can be a comma separated list of values
							if (supportedArches.contains("x86")) {supportedArches.addAll(Arrays.asList("i386","i486","i586","i686"));}  // Note: x86 is a general term to cover all 32-bit intel microprocessors 
							if (!productSupportedArches.isEmpty() && !productSupportedArches.containsAll(supportedArches)) {
								log.warning("THE VALIDITY OF SUBSCRIPTION productName='"+productName+"' productId='"+productId+"' WITH PROVIDED PRODUCT '"+providedProductName+"' IS QUESTIONABLE.  THE PROVIDED PRODUCT '"+providedProductId+"' ARCH ATTRIBUTE '"+attributeValue+"' IS NOT A SUBSET OF THE TOP LEVEL SUBSCRIPTION PRODUCT '"+productId+"' ARCH ATTRIBUTE '"+productSupportedArches+"'.");
							}
							if (!supportedArches.contains("ALL") && !supportedArches.contains(clienttasks.arch)) {
								//providedProductAttributesPassRulesCheck = false;
							} else {
								atLeastOneProvidedProductSatisfiesArch = true;
							}
						}
						if (attributeName.equals("variant")) {
//								if (!attributeValue.equalsIgnoreCase("ALL") && !attributeValue.equalsIgnoreCase(clienttasks.variant)) {
//									providedProductAttributesPassRulesCheck = false;
//								}
						}
						if (attributeName.equals("type")) {
							if (attributeValue.equals("MKT")) { // provided products of type "MKT" should not pass the rules check  e.g. providedProductName="Awesome OS Server Bundled"
								providedProductAttributesPassRulesCheck = false;	// do not comment out!
							}
						}
						if (attributeName.equals("version")) {
//								if (!attributeValue.equalsIgnoreCase(clienttasks.version)) {
//									providedProductAttributesPassRulesCheck = false;
//								}
						}
						if (attributeName.equals("requires_consumer_type")) {
							if (!attributeValue.equalsIgnoreCase(ConsumerType.system.toString())) {
								//providedProductAttributesPassRulesCheck = false;
							}
						}
						if (attributeName.equals("sockets")) {
//							if (!attributeValue.equals(productAttributeSocketsValue)) {
//								log.warning("THE VALIDITY OF SUBSCRIPTION productName='"+productName+"' productId='"+productId+"' WITH PROVIDED PRODUCT '"+providedProductName+"' IS QUESTIONABLE.  THE PROVIDED PRODUCT '"+providedProductId+"' SOCKETS ATTRIBUTE '"+attributeValue+"' DOES NOT MATCH THE TOP LEVEL SUBSCRIPTION PRODUCT '"+productId+"' SOCKETS ATTRIBUTE '"+productAttributeSocketsValue+"'.");
//							}
//							if (!productAttributeSocketsValue.equals("") && Integer.valueOf(attributeValue) > Integer.valueOf(productAttributeSocketsValue)) {
//								//providedProductAttributesPassRulesCheck = false;
//							}
						}
					}
					if (providedProductAttributesPassRulesCheck) {
						JSONObject bundledProduct = new JSONObject(String.format("{productName:'%s', productId:'%s'}", providedProductName,providedProductId));

						jsonBundledProductData.put(bundledProduct);
					}
					
					
					// is this provided product already installed
					if (ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", providedProductId, productCerts) != null) atLeastOneProvidedProductIsInstalled=true;
					
				}
				
				
				// Example:
				// < {systemProductId:'awesomeos-modifier', bundledProductData:<{productName:'Awesome OS Modifier Bits'}>} , {systemProductId:'awesomeos-server', bundledProductData:<{productName:'Awesome OS Server Bits'},{productName:'Clustering Bits'},{productName:'Shared Storage Bits'},{productName:'Management Bits'},{productName:'Large File Support Bits'},{productName:'Load Balancing Bits'}>} , {systemProductId:'awesomeos-server-basic', bundledProductData:<{productName:'Awesome OS Server Bits'}>} , {systemProductId:'awesomeos-workstation-basic', bundledProductData:<{productName:'Awesome OS Workstation Bits'}>} , {systemProductId:'awesomeos-server-2-socket-std', bundledProductData:<{productName:'Awesome OS Server Bits'},{productName:'Clustering Bits'},{productName:'Shared Storage Bits'},{productName:'Management Bits'},{productName:'Large File Support Bits'},{productName:'Load Balancing Bits'}>} , {systemProductId:'awesomeos-virt-4', bundledProductData:<{productName:'Awesome OS Server Bits'}>} , {systemProductId:'awesomeos-server-2-socket-prem', bundledProductData:<{productName:'Awesome OS Server Bits'},{productName:'Clustering Bits'},{productName:'Shared Storage Bits'},{productName:'Management Bits'},{productName:'Large File Support Bits'},{productName:'Load Balancing Bits'}>} , {systemProductId:'awesomeos-virt-4', bundledProductData:<{productName:'Awesome OS Server Bits'}>} , {systemProductId:'awesomeos-server-4-socket-prem',bundledProductData:<{productName:'Awesome OS Server Bits'},{productName:'Clustering Bits'},{productName:'Shared Storage Bits'},{productName:'Management Bits'},{productName:'Large File Support Bits'},{productName:'Load Balancing Bits'}>} , {systemProductId:'awesomeos-virt-4', bundledProductData:<{productName:'Awesome OS Server Bits'}>} , {systemProductId:'awesomeos-server-2-socket-bas', bundledProductData:<{productName:'Awesome OS Server Bits'},{productName:'Clustering Bits'},{productName:'Shared Storage Bits'},{productName:'Management Bits'},{productName:'Large File Support Bits'},{productName:'Load Balancing Bits'}>} , {systemProductId:'awesomeos-virt-4', bundledProductData:<{productName:'Awesome OS Server Bits'}>} , {systemProductId:'management-100', bundledProductData:<{productName:'Management Add-On'}>} , {systemProductId:'awesomeos-scalable-fs', bundledProductData:<{productName:'Awesome OS Scalable Filesystem Bits'}>}>
				// String systemProductId, JSONArray bundledProductDataAsJSONArray
				if (matchSystemSoftware && atLeastOneProvidedProductIsInstalled) {
					ll.add(Arrays.asList(new Object[]{productId, jsonBundledProductData}));
					productIdsAddedToSystemSubscriptionPoolProductData.add(productId);
				} else if (matchSystemHardware && atLeastOneProvidedProductSatisfiesArch) {
					ll.add(Arrays.asList(new Object[]{productId, jsonBundledProductData}));
					productIdsAddedToSystemSubscriptionPoolProductData.add(productId);		
				} else if (!matchSystemSoftware && !matchSystemHardware) {
					ll.add(Arrays.asList(new Object[]{productId, jsonBundledProductData}));
					productIdsAddedToSystemSubscriptionPoolProductData.add(productId);		
				}
			}
		}
		
		// minimize the number of dataProvided rows (useful during automated testcase development)
		// WARNING: When true, this will fail the VerifyAvailablePoolsPassTheHardwareRulesCheck_Test
		if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) ll=ll.subList(0,1);

		return ll;
		
	}
	
	@DataProvider(name="getNonAvailableSystemSubscriptionPoolProductData")
	public Object[][] getNonAvailableSystemSubscriptionPoolProductDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getNonAvailableSystemSubscriptionPoolProductDataAsListOfLists());
	}
	/**
	 * @return List of [String productId]
	 */
	protected List<List<Object>> getNonAvailableSystemSubscriptionPoolProductDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		List <String> productIdsAddedToNonAvailableSystemSubscriptionPoolProductData = new ArrayList<String>();

		// String systemProductId, JSONArray bundledProductDataAsJSONArray
		List<List<Object>> availSystemSubscriptionPoolProductData = getSystemSubscriptionPoolProductDataAsListOfLists(true,false);
		
		// get the owner key for clientusername, clientpassword
		String consumerId = clienttasks.getCurrentConsumerId();
		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null));
		String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);

		Calendar now = new GregorianCalendar();
		now.setTimeInMillis(System.currentTimeMillis());
		
		// process all of the subscriptions belonging to ownerKey
		JSONArray jsonSubscriptions = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+ownerKey+"/subscriptions"));	
		for (int i = 0; i < jsonSubscriptions.length(); i++) {
			JSONObject jsonSubscription = (JSONObject) jsonSubscriptions.get(i);
			
			JSONObject jsonProduct = (JSONObject) jsonSubscription.getJSONObject("product");
			String productId = jsonProduct.getString("id");
			String productName = jsonProduct.getString("name");
			
			// skip subscriptions that have already been added to NonAvailableSystemSubscriptionPoolProductData
			if (productIdsAddedToNonAvailableSystemSubscriptionPoolProductData.contains(productId)) continue;

			boolean isAvailable = false;
			for (List<Object> systemSubscriptionPoolProductDatum : availSystemSubscriptionPoolProductData) {
				String availProductId = (String) systemSubscriptionPoolProductDatum.get(0);
				JSONArray availJsonBundledProductData = (JSONArray) systemSubscriptionPoolProductDatum.get(1);
				if (availProductId.equals(productId)) {
					isAvailable = true;
					break;
				}
			}
			if (!isAvailable) {
				// String systemProductId
				ll.add(Arrays.asList(new Object[]{productId}));
				productIdsAddedToNonAvailableSystemSubscriptionPoolProductData.add(productId);
			}
		}
		return ll;
	}
	
	/* SUBSCRIPTION WITH BUNDLED PRODUCTS
	
	[root@jsefler-onprem-server ~]# curl -k -u admin:admin --request GET https://jsefler-f14-candlepin.usersys.redhat.com:8443/candlepin/subscriptions/8a90f8b42ee62404012ee624918b00a9 | json_reformat 
		  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
		                                 Dload  Upload   Total   Spent    Left  Speed
		100 13941    0 13941    0     0   127k      0 --:--:-- --:--:-- --:--:--  412k
		{
		  "id": "8a90f8b42ee62404012ee624918b00a9",
		  "owner": {
		    "href": "/owners/admin",
		    "id": "8a90f8b42ee62404012ee62448260005"
		  },
		  "certificate": null,
		  "product": {
		    "name": "Awesome OS Server Bundled (2 Sockets, Standard Support)",
		    "id": "awesomeos-server-2-socket-std",
		    "attributes": [
		      {
		        "name": "variant",
		        "value": "ALL",
		        "updated": "2011-03-24T04:34:39.173+0000",
		        "created": "2011-03-24T04:34:39.173+0000"
		      },
		      {
		        "name": "sockets",
		        "value": "2",
		        "updated": "2011-03-24T04:34:39.174+0000",
		        "created": "2011-03-24T04:34:39.174+0000"
		      },
		      {
		        "name": "arch",
		        "value": "ALL",
		        "updated": "2011-03-24T04:34:39.174+0000",
		        "created": "2011-03-24T04:34:39.174+0000"
		      },
		      {
		        "name": "support_level",
		        "value": "Standard",
		        "updated": "2011-03-24T04:34:39.174+0000",
		        "created": "2011-03-24T04:34:39.174+0000"
		      },
		      {
		        "name": "support_type",
		        "value": "L1-L3",
		        "updated": "2011-03-24T04:34:39.175+0000",
		        "created": "2011-03-24T04:34:39.175+0000"
		      },
		      {
		        "name": "management_enabled",
		        "value": "1",
		        "updated": "2011-03-24T04:34:39.175+0000",
		        "created": "2011-03-24T04:34:39.175+0000"
		      },
		      {
		        "name": "type",
		        "value": "MKT",
		        "updated": "2011-03-24T04:34:39.175+0000",
		        "created": "2011-03-24T04:34:39.175+0000"
		      },
		      {
		        "name": "warning_period",
		        "value": "30",
		        "updated": "2011-03-24T04:34:39.176+0000",
		        "created": "2011-03-24T04:34:39.176+0000"
		      },
		      {
		        "name": "version",
		        "value": "6.1",
		        "updated": "2011-03-24T04:34:39.176+0000",
		        "created": "2011-03-24T04:34:39.176+0000"
		      }
		    ],
		    "multiplier": 1,
		    "productContent": [

		    ],
		    "dependentProductIds": [

		    ],
		    "href": "/products/awesomeos-server-2-socket-std",
		    "updated": "2011-03-24T04:34:39.173+0000",
		    "created": "2011-03-24T04:34:39.173+0000"
		  },
		  "providedProducts": [
		    {
		      "name": "Clustering Bits",
		      "id": "37065",
		      "attributes": [
		        {
		          "name": "version",
		          "value": "1.0",
		          "updated": "2011-03-24T04:34:26.104+0000",
		          "created": "2011-03-24T04:34:26.104+0000"
		        },
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:26.104+0000",
		          "created": "2011-03-24T04:34:26.104+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T04:34:26.104+0000",
		          "created": "2011-03-24T04:34:26.104+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:26.104+0000",
		          "created": "2011-03-24T04:34:26.104+0000"
		        },
		        {
		          "name": "type",
		          "value": "SVC",
		          "updated": "2011-03-24T04:34:26.104+0000",
		          "created": "2011-03-24T04:34:26.104+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [
		        {
		          "content": {
		            "name": "always-enabled-content",
		            "id": "1",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "always-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": 200,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.415+0000",
		            "created": "2011-03-24T04:34:25.415+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "never-enabled-content",
		            "id": "0",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/never",
		            "label": "never-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/never/gpg",
		            "metadataExpire": 600,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.277+0000",
		            "created": "2011-03-24T04:34:25.277+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": false
		        }
		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/37065",
		      "updated": "2011-03-24T04:34:26.103+0000",
		      "created": "2011-03-24T04:34:26.103+0000"
		    },
		    {
		      "name": "Awesome OS Server Bundled",
		      "id": "awesomeos-server",
		      "attributes": [
		        {
		          "name": "version",
		          "value": "1.0",
		          "updated": "2011-03-24T04:34:35.841+0000",
		          "created": "2011-03-24T04:34:35.841+0000"
		        },
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:35.841+0000",
		          "created": "2011-03-24T04:34:35.841+0000"
		        },
		        {
		          "name": "support_level",
		          "value": "Premium",
		          "updated": "2011-03-24T04:34:35.841+0000",
		          "created": "2011-03-24T04:34:35.841+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T04:34:35.841+0000",
		          "created": "2011-03-24T04:34:35.841+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:35.841+0000",
		          "created": "2011-03-24T04:34:35.841+0000"
		        },
		        {
		          "name": "management_enabled",
		          "value": "1",
		          "updated": "2011-03-24T04:34:35.842+0000",
		          "created": "2011-03-24T04:34:35.842+0000"
		        },
		        {
		          "name": "type",
		          "value": "MKT",
		          "updated": "2011-03-24T04:34:35.842+0000",
		          "created": "2011-03-24T04:34:35.842+0000"
		        },
		        {
		          "name": "warning_period",
		          "value": "30",
		          "updated": "2011-03-24T04:34:35.842+0000",
		          "created": "2011-03-24T04:34:35.842+0000"
		        },
		        {
		          "name": "support_type",
		          "value": "Level 3",
		          "updated": "2011-03-24T04:34:35.842+0000",
		          "created": "2011-03-24T04:34:35.842+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [

		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/awesomeos-server",
		      "updated": "2011-03-24T04:34:35.841+0000",
		      "created": "2011-03-24T04:34:35.841+0000"
		    },
		    {
		      "name": "Awesome OS Server Bits",
		      "id": "37060",
		      "attributes": [
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T05:28:28.464+0000",
		          "created": "2011-03-24T05:28:28.464+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T05:28:28.465+0000",
		          "created": "2011-03-24T05:28:28.465+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T05:28:28.464+0000",
		          "created": "2011-03-24T05:28:28.464+0000"
		        },
		        {
		          "name": "type",
		          "value": "SVC",
		          "updated": "2011-03-24T05:28:28.465+0000",
		          "created": "2011-03-24T05:28:28.465+0000"
		        },
		        {
		          "name": "warning_period",
		          "value": "30",
		          "updated": "2011-03-24T05:28:28.465+0000",
		          "created": "2011-03-24T05:28:28.465+0000"
		        },
		        {
		          "name": "version",
		          "value": "6.1",
		          "updated": "2011-03-24T05:28:28.465+0000",
		          "created": "2011-03-24T05:28:28.465+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [
		        {
		          "content": {
		            "name": "tagged-content",
		            "id": "2",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "tagged-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": null,
		            "requiredTags": "TAG1,TAG2",
		            "updated": "2011-03-24T04:34:25.482+0000",
		            "created": "2011-03-24T04:34:25.482+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "always-enabled-content",
		            "id": "1",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "always-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": 200,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.415+0000",
		            "created": "2011-03-24T04:34:25.415+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "never-enabled-content",
		            "id": "0",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/never",
		            "label": "never-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/never/gpg",
		            "metadataExpire": 600,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.277+0000",
		            "created": "2011-03-24T04:34:25.277+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": false
		        },
		        {
		          "content": {
		            "name": "content",
		            "id": "1111",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path",
		            "label": "content-label",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/gpg/",
		            "metadataExpire": 0,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.559+0000",
		            "created": "2011-03-24T04:34:25.559+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        }
		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/37060",
		      "updated": "2011-03-24T04:34:32.608+0000",
		      "created": "2011-03-24T04:34:32.608+0000"
		    },
		    {
		      "name": "Load Balancing Bits",
		      "id": "37070",
		      "attributes": [
		        {
		          "name": "version",
		          "value": "1.0",
		          "updated": "2011-03-24T04:34:27.252+0000",
		          "created": "2011-03-24T04:34:27.252+0000"
		        },
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:27.252+0000",
		          "created": "2011-03-24T04:34:27.252+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T04:34:27.253+0000",
		          "created": "2011-03-24T04:34:27.253+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:27.252+0000",
		          "created": "2011-03-24T04:34:27.252+0000"
		        },
		        {
		          "name": "type",
		          "value": "SVC",
		          "updated": "2011-03-24T04:34:27.253+0000",
		          "created": "2011-03-24T04:34:27.253+0000"
		        },
		        {
		          "name": "warning_period",
		          "value": "30",
		          "updated": "2011-03-24T04:34:27.253+0000",
		          "created": "2011-03-24T04:34:27.253+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [
		        {
		          "content": {
		            "name": "always-enabled-content",
		            "id": "1",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "always-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": 200,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.415+0000",
		            "created": "2011-03-24T04:34:25.415+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "never-enabled-content",
		            "id": "0",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/never",
		            "label": "never-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/never/gpg",
		            "metadataExpire": 600,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.277+0000",
		            "created": "2011-03-24T04:34:25.277+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": false
		        }
		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/37070",
		      "updated": "2011-03-24T04:34:27.251+0000",
		      "created": "2011-03-24T04:34:27.251+0000"
		    },
		    {
		      "name": "Large File Support Bits",
		      "id": "37068",
		      "attributes": [
		        {
		          "name": "version",
		          "value": "1.0",
		          "updated": "2011-03-24T04:34:30.292+0000",
		          "created": "2011-03-24T04:34:30.292+0000"
		        },
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:30.293+0000",
		          "created": "2011-03-24T04:34:30.293+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T04:34:30.293+0000",
		          "created": "2011-03-24T04:34:30.293+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:30.293+0000",
		          "created": "2011-03-24T04:34:30.293+0000"
		        },
		        {
		          "name": "type",
		          "value": "SVC",
		          "updated": "2011-03-24T04:34:30.293+0000",
		          "created": "2011-03-24T04:34:30.293+0000"
		        },
		        {
		          "name": "warning_period",
		          "value": "30",
		          "updated": "2011-03-24T04:34:30.293+0000",
		          "created": "2011-03-24T04:34:30.293+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [
		        {
		          "content": {
		            "name": "always-enabled-content",
		            "id": "1",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "always-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": 200,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.415+0000",
		            "created": "2011-03-24T04:34:25.415+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "never-enabled-content",
		            "id": "0",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/never",
		            "label": "never-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/never/gpg",
		            "metadataExpire": 600,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.277+0000",
		            "created": "2011-03-24T04:34:25.277+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": false
		        }
		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/37068",
		      "updated": "2011-03-24T04:34:30.292+0000",
		      "created": "2011-03-24T04:34:30.292+0000"
		    },
		    {
		      "name": "Shared Storage Bits",
		      "id": "37067",
		      "attributes": [
		        {
		          "name": "version",
		          "value": "1.0",
		          "updated": "2011-03-24T04:34:28.860+0000",
		          "created": "2011-03-24T04:34:28.860+0000"
		        },
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:28.861+0000",
		          "created": "2011-03-24T04:34:28.861+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T04:34:28.861+0000",
		          "created": "2011-03-24T04:34:28.861+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:28.861+0000",
		          "created": "2011-03-24T04:34:28.861+0000"
		        },
		        {
		          "name": "type",
		          "value": "SVC",
		          "updated": "2011-03-24T04:34:28.861+0000",
		          "created": "2011-03-24T04:34:28.861+0000"
		        },
		        {
		          "name": "warning_period",
		          "value": "30",
		          "updated": "2011-03-24T04:34:28.861+0000",
		          "created": "2011-03-24T04:34:28.861+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [
		        {
		          "content": {
		            "name": "always-enabled-content",
		            "id": "1",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "always-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": 200,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.415+0000",
		            "created": "2011-03-24T04:34:25.415+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "never-enabled-content",
		            "id": "0",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/never",
		            "label": "never-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/never/gpg",
		            "metadataExpire": 600,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.277+0000",
		            "created": "2011-03-24T04:34:25.277+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": false
		        }
		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/37067",
		      "updated": "2011-03-24T04:34:28.859+0000",
		      "created": "2011-03-24T04:34:28.859+0000"
		    },
		    {
		      "name": "Management Bits",
		      "id": "37069",
		      "attributes": [
		        {
		          "name": "version",
		          "value": "1.0",
		          "updated": "2011-03-24T04:34:31.181+0000",
		          "created": "2011-03-24T04:34:31.181+0000"
		        },
		        {
		          "name": "variant",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:31.181+0000",
		          "created": "2011-03-24T04:34:31.181+0000"
		        },
		        {
		          "name": "sockets",
		          "value": "2",
		          "updated": "2011-03-24T04:34:31.181+0000",
		          "created": "2011-03-24T04:34:31.181+0000"
		        },
		        {
		          "name": "arch",
		          "value": "ALL",
		          "updated": "2011-03-24T04:34:31.181+0000",
		          "created": "2011-03-24T04:34:31.181+0000"
		        },
		        {
		          "name": "type",
		          "value": "SVC",
		          "updated": "2011-03-24T04:34:31.181+0000",
		          "created": "2011-03-24T04:34:31.181+0000"
		        },
		        {
		          "name": "warning_period",
		          "value": "30",
		          "updated": "2011-03-24T04:34:31.181+0000",
		          "created": "2011-03-24T04:34:31.181+0000"
		        }
		      ],
		      "multiplier": 1,
		      "productContent": [
		        {
		          "content": {
		            "name": "always-enabled-content",
		            "id": "1",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/always",
		            "label": "always-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/always/gpg",
		            "metadataExpire": 200,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.415+0000",
		            "created": "2011-03-24T04:34:25.415+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": true
		        },
		        {
		          "content": {
		            "name": "never-enabled-content",
		            "id": "0",
		            "type": "yum",
		            "modifiedProductIds": [

		            ],
		            "contentUrl": "/foo/path/never",
		            "label": "never-enabled-content",
		            "vendor": "test-vendor",
		            "gpgUrl": "/foo/path/never/gpg",
		            "metadataExpire": 600,
		            "requiredTags": null,
		            "updated": "2011-03-24T04:34:25.277+0000",
		            "created": "2011-03-24T04:34:25.277+0000"
		          },
		          "flexEntitlement": 0,
		          "physicalEntitlement": 0,
		          "enabled": false
		        }
		      ],
		      "dependentProductIds": [

		      ],
		      "href": "/products/37069",
		      "updated": "2011-03-24T04:34:31.180+0000",
		      "created": "2011-03-24T04:34:31.180+0000"
		    }
		  ],
		  "endDate": "2013-03-13T00:00:00.000+0000",
		  "startDate": "2012-03-13T00:00:00.000+0000",
		  "quantity": 15,
		  "contractNumber": "20",
		  "accountNumber": "12331131231",
		  "modified": null,
		  "tokens": [

		  ],
		  "upstreamPoolId": null,
		  "updated": "2011-03-24T04:34:39.627+0000",
		  "created": "2011-03-24T04:34:39.627+0000"
		}
	*/
	
	
	@DataProvider(name="getAllJSONPoolsData")
	public Object[][] getAllJSONPoolsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllJSONPoolsDataAsListOfLists());
	}
	/**
	 * @return List of [JSONObject jsonPool]
	 */
	protected List<List<Object>> getAllJSONPoolsDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// who is the owner of sm_clientUsername
		String clientOrg = sm_clientOrg;
		if (clientOrg==null) {
			List<RegistrationData> registrationData = findGoodRegistrationData(true,sm_clientUsername,false,clientOrg);
			if (registrationData.isEmpty() || registrationData.size()>1) throw new SkipException("Could not determine unique owner for username '"+sm_clientUsername+"'.  It is needed for a candlepin API call get pools by owner.");
			clientOrg = registrationData.get(0).ownerKey;
		}
		
		// process all of the pools belonging to ownerKey
		JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+clientOrg+"/pools?listall=true"));	
		for (int i = 0; i < jsonPools.length(); i++) {
			JSONObject jsonPool = (JSONObject) jsonPools.get(i);
			
			// exclude sub pools that were generated from consumption of a parent pool
			// including these has cause tests to mysteriously fail because the pool can be deleted if the source entitlement is revoked which can happen if the current consumer who has generated this subpool unregisters before the test that uses this data provider is executed. 
			if (!jsonPool.isNull("sourceEntitlement")) continue;
			
			ll.add(Arrays.asList(new Object[]{jsonPool}));
		}
		
		return ll;
	}

	
	@DataProvider(name="getAllFutureJSONPoolsData")
	public Object[][] getAllFutureJSONPoolsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllFutureJSONPoolsDataAsListOfLists(null));
	}
	@DataProvider(name="getAllFutureSystemJSONPoolsData")
	public Object[][] getAllFutureSystemJSONPoolsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllFutureJSONPoolsDataAsListOfLists(ConsumerType.system));
	}
	@DataProvider(name="getAllFutureSystemSubscriptionPoolsData")
	public Object[][] getAllFutureSystemSubscriptionPoolsDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllFutureSystemSubscriptionPoolsDataAsListOfLists());
	}
	
	/**
	 * @return List of [SubscriptionPool pool]
	 */
	protected List<List<Object>>getAllFutureSystemSubscriptionPoolsDataAsListOfLists() throws ParseException, JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (List<Object> l : getAllFutureJSONPoolsDataAsListOfLists(ConsumerType.system)) {
			JSONObject jsonPool = (JSONObject) l.get(0);
			
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			Calendar endDate = new GregorianCalendar();
			endDate.setTimeInMillis(dateFormat.parse(jsonPool.getString("endDate")).getTime());
			Boolean multiEntitlement = CandlepinTasks.isPoolProductMultiEntitlement(sm_clientUsername,sm_clientPassword, SubscriptionManagerBaseTestScript.sm_serverUrl, jsonPool.getString("id"));

			String quantity = String.valueOf(jsonPool.getInt("quantity"));
			
			ll.add(Arrays.asList(new Object[]{new SubscriptionPool(jsonPool.getString("productName"), jsonPool.getString("productId"), jsonPool.getString("id"), quantity, null, multiEntitlement, SubscriptionPool.formatDateString(endDate))}));
		}
		return ll;
	}
	
	/**
	 * @return List of [JSONObject jsonPool]
	 */
	protected List<List<Object>> getAllFutureJSONPoolsDataAsListOfLists(ConsumerType consumerType) throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// get the owner key for clientusername, clientpassword
		String consumerId = clienttasks.getCurrentConsumerId();
		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null));
		String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);

		for (List<Object> l : getAllFutureJSONSubscriptionsDataAsListOfLists(consumerType)) {
			JSONObject jsonSubscription = (JSONObject) l.get(0);
			String subscriptionId = jsonSubscription.getString("id");			
			Calendar startDate = parseISO8601DateString(jsonSubscription.getString("startDate"),"GMT");	// "startDate":"2012-02-08T00:00:00.000+0000"

			// process all of the pools belonging to ownerKey that are activeon startDate
			JSONArray jsonPools = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+ownerKey+"/pools" +"?activeon="+urlEncode(formatISO8601DateString(startDate))));
			for (int j = 0; j < jsonPools.length(); j++) {
				JSONObject jsonPool = (JSONObject) jsonPools.get(j);
				
				// remember all the jsonPools that come from subscriptionId
				if (jsonPool.isNull("subscriptionId")) continue;	// will happen if there is a generated sub-pool from a consumed person subscription
				if (jsonPool.getString("subscriptionId").equals(subscriptionId)) {

					// JSONObject jsonPool
					ll.add(Arrays.asList(new Object[]{jsonPool}));
					
					// minimize the number of dataProvided rows (useful during automated testcase development)
					if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
				}
			}
		}
		return ll;
	}
	
	/**
	 * @return List of [JSONObject jsonSubscription]
	 */
	protected List<List<Object>> getAllFutureJSONSubscriptionsDataAsListOfLists(ConsumerType consumerType) throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;

		// get the owner key for clientusername, clientpassword
		String consumerId = clienttasks.getCurrentConsumerId();
		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null));
		String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername, sm_clientPassword, sm_serverUrl, consumerId);

		Calendar now = new GregorianCalendar();
		now.setTimeInMillis(System.currentTimeMillis());
		
		// process all of the subscriptions belonging to ownerKey
		JSONArray jsonSubscriptions = new JSONArray(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/owners/"+ownerKey+"/subscriptions"));	
		for (int i = 0; i < jsonSubscriptions.length(); i++) {
			JSONObject jsonSubscription = (JSONObject) jsonSubscriptions.get(i);
			String id = jsonSubscription.getString("id");			
			Calendar startDate = parseISO8601DateString(jsonSubscription.getString("startDate"),"GMT");	// "startDate":"2012-02-08T00:00:00.000+0000"
			Calendar endDate = parseISO8601DateString(jsonSubscription.getString("endDate"),"GMT");	// "endDate":"2013-02-07T00:00:00.000+0000"

			// skip subscriptions to a product that doesn't satisfy the requested consumerType
			JSONObject jsonProduct = jsonSubscription.getJSONObject("product");
			JSONArray jsonProductAttributes = jsonProduct.getJSONArray("attributes");
			String requires_consumer_type = null;
			for (int j = 0; j < jsonProductAttributes.length(); j++) {
				JSONObject jsonProductAttribute = (JSONObject) jsonProductAttributes.get(j);
				if (jsonProductAttribute.getString("name").equals("requires_consumer_type")) {
					requires_consumer_type = jsonProductAttribute.getString("value");
					break;
				}
			}
			if (requires_consumer_type==null) requires_consumer_type = ConsumerType.system.toString();	// the absence of a "requires_consumer_type" implies requires_consumer_type is system
			if (!ConsumerType.valueOf(requires_consumer_type).equals(consumerType)) continue;
			
			// process subscriptions that are in the future
			if (startDate.after(now)) {
			
				// JSONObject jsonSubscription
				ll.add(Arrays.asList(new Object[]{jsonSubscription}));
			}
		}
		return ll;
	}

	
// DELETEME
//	/**
//	 * @return List of [SubscriptionPool modifierPool, String label, List<String> modifiedProductIds, String requiredTags, List<SubscriptionPool> providingPools]
//	 */
//	protected List<List<Object>> getModifierSubscriptionDataAsListOfLists() throws JSONException, Exception {
//		List<List<Object>> ll = new ArrayList<List<Object>>();	if (!isSetupBeforeSuiteComplete) return ll;
//		
//		// get the owner key for clientusername, clientpassword
//		String consumerId = clienttasks.getCurrentConsumerId();
//		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, Boolean.TRUE, false, null, null, null));
//		//String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverHostname, sm_serverPort, sm_serverPrefix, sm_clientUsername, sm_clientPassword, consumerId);
//
//		
//		List<SubscriptionPool> allAvailablePools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
//		
//		// iterate through all available pools looking for those that contain products with content that modify other products
//		for (SubscriptionPool modifierPool : allAvailablePools) {
//			JSONObject jsonModifierPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+modifierPool.poolId));	
//			
//			// iterate through each of the providedProducts
//			JSONArray jsonModifierProvidedProducts = jsonModifierPool.getJSONArray("providedProducts");
//			for (int i = 0; i < jsonModifierProvidedProducts.length(); i++) {
//				JSONObject jsonModifierProvidedProduct = (JSONObject) jsonModifierProvidedProducts.get(i);
//				String modifierProvidedProductId = jsonModifierProvidedProduct.getString("productId");
//				
//				// get the productContents
//				JSONObject jsonProduct = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/products/"+modifierProvidedProductId));	
//				JSONArray jsonProductContents = jsonProduct.getJSONArray("productContent");
//				for (int j = 0; j < jsonProductContents.length(); j++) {
//					JSONObject jsonProductContent = (JSONObject) jsonProductContents.get(j);
//					JSONObject jsonContent = jsonProductContent.getJSONObject("content");
//					
//					// get the label and modifiedProductIds for each of the productContents
//					String label = jsonContent.getString("label");
//					String requiredTags = jsonContent.getString("requiredTags"); // comma separated string
//					if (requiredTags.equals("null")) requiredTags = null;
//					JSONArray jsonModifiedProductIds = jsonContent.getJSONArray("modifiedProductIds");
//					List<String> modifiedProductIds = new ArrayList<String>();
//					for (int k = 0; k < jsonModifiedProductIds.length(); k++) {
//						String modifiedProductId = (String) jsonModifiedProductIds.get(k);
//						modifiedProductIds.add(modifiedProductId);
//					}
//					
//					// does this pool contain productContents that modify other products?
//					if (modifiedProductIds.size()>0) {
//						
//						List<SubscriptionPool> providingPools = new ArrayList<SubscriptionPool>();
//						// yes, now its time to find the subscriptions that provide the modifiedProductIds
//						for (SubscriptionPool providingPool : allAvailablePools) {
//							JSONObject jsonProvidingPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+providingPool.poolId));	
//							
//							// iterate through each of the providedProducts
//							JSONArray jsonProvidingProvidedProducts = jsonProvidingPool.getJSONArray("providedProducts");
//							for (int l = 0; l < jsonProvidingProvidedProducts.length(); l++) {
//								JSONObject jsonProvidingProvidedProduct = (JSONObject) jsonProvidingProvidedProducts.get(l);
//								String providingProvidedProductId = jsonProvidingProvidedProduct.getString("productId");
//								if (modifiedProductIds.contains(providingProvidedProductId)) {
//									
//									// NOTE: This test takes a long time to run when there are many providingPools.
//									// To reduce the execution time, let's simply limit the number of providing pools tested to 2,
//									// otherwise this block of code could be commented out for a more thorough test.
//									boolean thisPoolProductIdIsAlreadyInProvidingPools = false;
//									for (SubscriptionPool providedPool : providingPools) {
//										if (providedPool.productId.equals(providingPool.productId)) {
//											thisPoolProductIdIsAlreadyInProvidingPools=true; break;
//										}
//									}
//									if (thisPoolProductIdIsAlreadyInProvidingPools||providingPools.size()>=2) break;
//									
//									providingPools.add(providingPool); break; // FIXME I THINK THE BREAK IS A TYPO SHOULD BE REMOVED
//								}
//							}
//						}
//										
//						ll.add(Arrays.asList(new Object[]{modifierPool, label, modifiedProductIds, requiredTags, providingPools}));
//					}
//				}
//			}
//		}	
//		return ll;
//	}
	/**
	 * @param limitPoolsModifiedCount This test takes a long time to run when there are many poolsModified.
	 * To reduce the execution time, use limitPoolsModifiedCount to limit the number of modifies pools tested,
	 * otherwise pass null to to include all poolsModified in the data for a more complete test.
	 * @return List of [SubscriptionPool modifierPool, String label, List<String> modifiedProductIds, String requiredTags, List<SubscriptionPool> poolsModified]
	 */
	protected List<List<Object>> getModifierSubscriptionDataAsListOfLists(Integer limitPoolsModifiedCount) throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>();	if (!isSetupBeforeSuiteComplete) return ll;
		
		// get the owner key for clientusername, clientpassword
		String consumerId = clienttasks.getCurrentConsumerId();
		if (consumerId==null) consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, Boolean.TRUE, false, null, null, null));
		//String ownerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_serverHostname, sm_serverPort, sm_serverPrefix, sm_clientUsername, sm_clientPassword, consumerId);

		
		List<SubscriptionPool> allAvailablePools = clienttasks.getCurrentlyAllAvailableSubscriptionPools();
		
		// iterate through all available pools looking for those that contain products with content that modify other products
		for (SubscriptionPool modifierPool : allAvailablePools) {
			JSONObject jsonModifierPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+modifierPool.poolId));	
			
			// iterate through each of the providedProducts
			JSONArray jsonModifierProvidedProducts = jsonModifierPool.getJSONArray("providedProducts");
			for (int i = 0; i < jsonModifierProvidedProducts.length(); i++) {
				JSONObject jsonModifierProvidedProduct = (JSONObject) jsonModifierProvidedProducts.get(i);
				String modifierProvidedProductId = jsonModifierProvidedProduct.getString("productId");
				
				// get the productContents
				JSONObject jsonProduct = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/products/"+modifierProvidedProductId));	
				JSONArray jsonProductContents = jsonProduct.getJSONArray("productContent");
				for (int j = 0; j < jsonProductContents.length(); j++) {
					JSONObject jsonProductContent = (JSONObject) jsonProductContents.get(j);
					JSONObject jsonContent = jsonProductContent.getJSONObject("content");
					
					// get the label and modifiedProductIds for each of the productContents
					String label = jsonContent.getString("label");
					String requiredTags = jsonContent.isNull("requiredTags")? null:jsonContent.getString("requiredTags"); // comma separated string
					JSONArray jsonModifiedProductIds = jsonContent.getJSONArray("modifiedProductIds");
					List<String> modifiedProductIds = new ArrayList<String>();
					for (int k = 0; k < jsonModifiedProductIds.length(); k++) {
						String modifiedProductId = (String) jsonModifiedProductIds.get(k);
						modifiedProductIds.add(modifiedProductId);
					}
					
					// does this pool contain productContents that modify other products?
					if (modifiedProductIds.size()>0) {
						
						// yes, now its time to find the pools that provide the modifiedProductIds and therefore are considered to be the pools that provide products that are modified
						List<SubscriptionPool> poolsModified = new ArrayList<SubscriptionPool>();
						LOOP_FOR_ALL_AVAILABLE_POOLS: for (SubscriptionPool poolModified : allAvailablePools) {
							JSONObject jsonPoolModified = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+poolModified.poolId));	
							
							// iterate through each of the providedProducts of the poolModified
							JSONArray jsonPoolModifiedProvidedProducts = jsonPoolModified.getJSONArray("providedProducts");
							for (int l = 0; l < jsonPoolModifiedProvidedProducts.length(); l++) {
								JSONObject jsonPoolModifiedProvidedProduct = (JSONObject) jsonPoolModifiedProvidedProducts.get(l);
								if (modifiedProductIds.contains(jsonPoolModifiedProvidedProduct.getString("productId"))) {
									
// DELETEME - BAD IDEA SINCE IT GIVES THE ILLUSION THAT ANOTHER POOL INSTANCE ON THE SAME SUBSCRIPTION (second contract number) APPEARS AS THOUGH IT DOES NOT QUALIFY AS A MODIFIED POOL
//									// no need to add this poolModified if the product it represents was already added to the poolsModified
//									boolean thisPoolModifiedProductIdIsAlreadyInPoolsModified = false;
//									for (SubscriptionPool pool : poolsModified) {
//										if (pool.productId.equals(poolModified.productId)) {
//											thisPoolModifiedProductIdIsAlreadyInPoolsModified=true; break;
//										}
//									}
//									if (thisPoolModifiedProductIdIsAlreadyInPoolsModified) break;
									
									poolsModified.add(poolModified);
									
									if (limitPoolsModifiedCount!=null && poolsModified.size()>=limitPoolsModifiedCount) break LOOP_FOR_ALL_AVAILABLE_POOLS;
								}
							}
						}
										
						ll.add(Arrays.asList(new Object[]{modifierPool, label, modifiedProductIds, requiredTags, poolsModified}));
					}
				}
			}
		}	
		return ll;
	}

	
	@DataProvider(name="getAllAvailableServiceLevelData")
	public Object[][] getAllAvailableServiceLevelDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getAllAvailableServiceLevelDataAsListOfLists());
	}
	/**
	 * @return List of [Object bugzilla, String serviceLevel]
	 */
	protected List<List<Object>>getAllAvailableServiceLevelDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// register with force (so we can find the org to which the sm_clientUsername belongs in case sm_clientOrg is null)
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "SubscriptionServiceLevelConsumer", null, null, null, null, (String)null, null, null, null, true, false, null, null, null));
		String org = CandlepinTasks.getOwnerKeyOfConsumerId(sm_clientUsername,sm_clientPassword,sm_serverUrl,consumerId);
		
		// get all the valid service levels available to this org
		// Object bugzilla, String serviceLevel
 		if (sm_serverOld) {
	 		for (String serviceLevel : clienttasks.getCurrentlyAvailableServiceLevels()) {
	 			ll.add(Arrays.asList(new Object[] {null,	serviceLevel}));
	 		}
 		} else
		for (String serviceLevel : CandlepinTasks.getServiceLevelsForOrgKey(sm_clientUsername, sm_clientPassword, sm_serverUrl, org)) {
			ll.add(Arrays.asList(new Object[] {null,	serviceLevel}));
		}
				
		return ll;
	}
	
	
	
	
	@DataProvider(name="getServerurl_TestData")
	public Object[][] getServerurl_TestDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getServerurl_TestDataAsListOfLists());
	}
	protected List<List<Object>> getServerurl_TestDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		if (servertasks==null) return ll;
		if (clienttasks==null) return ll;
		String defaultHostname = "subscription.rhn.redhat.com";
		String defaultPort = "443";
		String defaultPrefix = "/subscription";
		String serverurl;
		
		// initialize server_hostname server_port server_prefix with valid values from the current rhsm configurations
		String server_hostname	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "hostname");
		String server_port		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "port");
		String server_prefix	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "prefix");
		
		//  --serverurl=SERVER_URL      server url in the form of https://hostname:443/prefix

		// Object bugzilla, String serverurl, String expectedHostname, String expectedPort, String expectedPrefix, Integer expectedExitCode, String expectedStdout, String expectedStderr
		// positive tests
		serverurl= server_hostname+(server_port.isEmpty()?"":":"+server_port)+server_prefix;			ll.add(Arrays.asList(new Object[] {	null,	serverurl,	server_hostname,	server_port,	server_prefix,		new Integer(0),	null,	null}));
		serverurl= "https://"+serverurl;																ll.add(Arrays.asList(new Object[] {	null,	serverurl,	server_hostname,	server_port,	server_prefix,		new Integer(0),	null,	null}));
		
		if (server_port.equals(defaultPort)) {
			serverurl= server_hostname+server_prefix;													ll.add(Arrays.asList(new Object[] {	null,	serverurl,	server_hostname,	defaultPort,	server_prefix,		new Integer(0),	null,	null}));
			serverurl= "https://"+serverurl;															ll.add(Arrays.asList(new Object[] {	null,	serverurl,	server_hostname,	defaultPort,	server_prefix,		new Integer(0),	null,	null}));
		}
		if (server_prefix.equals(defaultPrefix)) {
			serverurl= server_hostname+(server_port.isEmpty()?"":":"+server_port);						ll.add(Arrays.asList(new Object[] {	null,	serverurl,	server_hostname,	server_port,	defaultPrefix,		new Integer(0),	null,	null}));
			serverurl= "https://"+serverurl;															ll.add(Arrays.asList(new Object[] {	null,	serverurl,	server_hostname,	server_port,	defaultPrefix,		new Integer(0),	null,	null}));
		}
		if (server_hostname.equals(defaultHostname)) {
			serverurl= (server_port.isEmpty()?"":":"+server_port);										ll.add(Arrays.asList(new Object[] {	null,	serverurl,	defaultHostname,	server_port,	server_prefix,		new Integer(0),	null,	null}));
			serverurl= "https://"+serverurl;															ll.add(Arrays.asList(new Object[] {	null,	serverurl,	defaultHostname,	server_port,	server_prefix,		new Integer(0),	null,	null}));
		}
		// TODO add a case for the ipaddress of hostname
		
		// ignored tests
		serverurl="";																					ll.add(Arrays.asList(new Object[] {	null,	serverurl,	/* last set */ ll.get(ll.size()-1).get(2),	ll.get(ll.size()-1).get(3),	ll.get(ll.size()-1).get(4),	new Integer(0),	null,	null}));	
	
		// negative tests
//		serverurl= "https://"+server_hostname+":PORT"+server_prefix;									ll.add(Arrays.asList(new Object[] {	null,															serverurl,	null,	null,	null,		new Integer(255),	"Unable to reach the server at "+server_hostname+":PORT"+server_prefix,													null}));
		serverurl= "https://"+server_hostname+(server_port.isEmpty()?"":":"+server_port)+"/PREFIX";		ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug("842885"),									serverurl,	null,	null,	null,		new Integer(255),	"Unable to reach the server at "+server_hostname+(server_port.isEmpty()?":"+defaultPort:":"+server_port)+"/PREFIX",		null}));
		serverurl= "hostname";																			ll.add(Arrays.asList(new Object[] {	null,															serverurl,	null,	null,	null,		new Integer(255),	"Unable to reach the server at hostname:"+defaultPort+defaultPrefix,													null}));
		serverurl= "hostname:900";																		ll.add(Arrays.asList(new Object[] {	null,															serverurl,	null,	null,	null,		new Integer(255),	"Unable to reach the server at hostname:900"+defaultPrefix,																null}));
		serverurl= "hostname:900/prefix";																ll.add(Arrays.asList(new Object[] {	null,															serverurl,	null,	null,	null,		new Integer(255),	"Unable to reach the server at hostname:900/prefix",																	null}));
		serverurl= "/";																					ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug("830767"),									serverurl,	null,	null,	null,		new Integer(255),	"Unable to reach the server at "+defaultHostname+":"+defaultPort+"/",													null}));
		serverurl= "https://"+server_hostname+":PORT"+server_prefix;									ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496","878634","842845"}),	serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL port should be numeric",												null}));
		serverurl= "https://hostname:PORT/prefix";														ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496","878634","842845"}),	serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL port should be numeric",												null}));	
		serverurl= "https://hostname:/prefix";															ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496","878634"}),				serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL port should be numeric"/*"Error parsing serverurl:\nServer URL port could not be parsed"*/,												null}));
		serverurl= "https:/hostname/prefix";															ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL has an invalid scheme. http:// and https:// are supported",			null}));
		serverurl= "https:hostname/prefix";																ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL has an invalid scheme. http:// and https:// are supported",			null}));
		serverurl= "https//hostname/prefix";															ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL has an invalid scheme. http:// and https:// are supported",			null}));
		serverurl= "https/hostname/prefix";																ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL has an invalid scheme. http:// and https:// are supported",			null}));
		serverurl= "ftp://hostname/prefix";																ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL has an invalid scheme. http:// and https:// are supported",			null}));
		serverurl= "git://hostname/prefix";																ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL has an invalid scheme. http:// and https:// are supported",			null}));
		serverurl= "https://";																			ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL is just a schema. Should include hostname, and/or port and path",		null}));
		serverurl= "http://";																			ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL is just a schema. Should include hostname, and/or port and path",		null}));
		//TODO serverurl= "DON'T KNOW WHAT TO PUT HERE TO INVOKE THE ERROR; see exceptions.py";			ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL can not be empty",	null}));
		//TODO serverurl= "DON'T KNOW WHAT TO PUT HERE TO INVOKE THE ERROR; see exceptions.py";			ll.add(Arrays.asList(new Object[] {	new BlockedByBzBug(new String[]{"1044686","1054496"}),						serverurl,	null,	null,	null,		new Integer(255),	"Error parsing serverurl:\nServer URL can not be None",		null}));
		return ll;
	}



}
