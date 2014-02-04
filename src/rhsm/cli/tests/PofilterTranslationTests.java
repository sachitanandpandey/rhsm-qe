package rhsm.cli.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlrpc.XmlRpcException;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.testng.TestNGUtils;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.data.Translation;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;
//import com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern;

/**
 * @author fsharath
 * @author jsefler
 * @References
 *   Engineering Localization Services: https://home.corp.redhat.com/node/53593
 *   http://git.fedorahosted.org/git/?p=subscription-manager.git;a=blob;f=po/pt.po;h=0854212f4fab348a25f0542625df343653a4a097;hb=RHEL6.3
 *   Here is the raw rhsm.po file for LANG=pt
 *   http://git.fedorahosted.org/git/?p=subscription-manager.git;a=blob;f=po/pt.po;hb=RHEL6.3
 *   
 *   https://engineering.redhat.com/trac/LocalizationServices
 *   https://engineering.redhat.com/trac/LocalizationServices/wiki/L10nRHEL6LanguageSupportCriteria
 *   
 *   https://translate.zanata.org/zanata/project/view/subscription-manager/iter/0.99.X/stats
 *   
 *   https://fedora.transifex.net/projects/p/fedora/
 *   
 *   http://translate.sourceforge.net/wiki/
 *   http://translate.sourceforge.net/wiki/toolkit/index
 *   http://translate.sourceforge.net/wiki/toolkit/pofilter
 *   http://translate.sourceforge.net/wiki/toolkit/pofilter_tests
 *   http://translate.sourceforge.net/wiki/toolkit/installation
 *   
 *   https://github.com/translate/translate
 *   
 *   Translation Bug Reporting Process:
 *   	https://engineering.redhat.com/trac/LocalizationServices/wiki/L10nBugReportingProcess
 *   
 *   Translation Ticket Requests:
 *   	Subscription Manger 1.1.X Translation Request
 *   	https://engineering.redhat.com/rt/Ticket/Display.html?id=180955
 *   	https://translate.zanata.org/zanata/iteration/view/subscription-manager/1.1.X
 *   
 *   	Subscription Manger 1.8.X Translation Request
 *  	https://engineering.redhat.com/rt/Ticket/Display.html?id=192038
 *   	https://translate.zanata.org/zanata/iteration/view/subscription-manager/1.8.X
 *   
 *   Translators:
 *   
			as		[ngoswami@redhat.com]	(Nilamdyuti Goswami)
			bn-IN	[runab@redhat.com]		(Runa Bhattacharjee)
			de_DE	[hpeters@redhat.com]	(Hedda Peters)
			es_ES	[agarcia@redhat.com]	(Angela Garcia)		[gguerrer@redhat.com]	(Gladys Guerrero-Lozan)
			fr		[sfriedma@redhat.com]	(Sam Friedmann)
			gu		[swkothar@redhat.com]	(Sweta Kothari)
			hi		[rranjan@redhat.com]	(Rajesh Ranjan)
			it		[fvalen@redhat.com]		(Francesco Valente)
			ja		[noriko@redhat.com]		(Noriko Mizumoto)
			kn		[svenkate@redhat.com]	(Prasad Shankar)
			ko		[eukim@redhat.com]		(Eun Ju Kim)
			ml		[apeter@redhat.com]		(Ani Peter)
			mr		[sshedmak@redhat.com]	(Sandeep Shedmake)
			or		[mgiri@redhat.com]		(Manoj Giri)
			pa		[jsingh@redhat.com]		(Jaswinder Singh)
			pt-BR	[gcintra@redhat.com]	(Glaucia Cintra de Freitas)
			ru		[ypoyarko@redhat.com]	(Yulia Poyarkova)
			ta_IN	[shkumar@redhat.com]	(Shantha Kumar)
			te		[kkrothap@redhat.com]	(Krishnababu Krothap)
			zh-CN	[lliu@redhat.com]		(Leah Liu)
			zh-TW	[tchuang@redhat.com]	(Terry Chuang)
			
 *   
 **/


@Test(groups={"PofilterTranslationTests"})
public class PofilterTranslationTests extends SubscriptionManagerCLITestScript {
	
	
	// Test Methods ***********************************************************************

	@Test(	description="run pofilter translate tests on subscription manager translation files",
			dataProvider="getSubscriptionManagerTranslationFilePofilterTestData",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void subscriptionManagerPofilter_Test(Object bugzilla, String pofilterTest, File translationFile) {
		pofilter_Test(client, pofilterTest, translationFile);
	}
	
	@Test(	description="run pofilter translate tests on candlepin translation files",
			dataProvider="getCandlepinTranslationFilePofilterTestData",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void candlepinPofilter_Test(Object bugzilla, String pofilterTest, File translationFile) {
		pofilter_Test(server, pofilterTest, translationFile);
	}
	

	// Candidates for an automated Test:
	
	
	
	
	
	
	
	
	
	
	
	
	
	// Configuration Methods ***********************************************************************
	@BeforeClass(groups={"setup"})
	public void setupBeforeClass() {
		// for debugging purposes, load a reduced list of pofilterTests
		if (!getProperty("sm.debug.pofilterTests", "").equals("")) 	pofilterTests = Arrays.asList(getProperty("sm.debug.pofilterTests", "").trim().split(" *, *"));
	}


	
	// Protected Methods ***********************************************************************
	
	// see http://translate.sourceforge.net/wiki/toolkit/pofilter_tests
	//	Critical -- can break a program
	//    	accelerators, escapes, newlines, nplurals, printf, tabs, variables, xmltags, dialogsizes
	//	Functional -- may confuse the user
	//    	acronyms, blank, emails, filepaths, functions, gconf, kdecomments, long, musttranslatewords, notranslatewords, numbers, options, purepunc, sentencecount, short, spellcheck, urls, unchanged
	//	Cosmetic -- make it look better
	//    	brackets, doublequoting, doublespacing, doublewords, endpunc, endwhitespace, puncspacing, simplecaps, simpleplurals, startcaps, singlequoting, startpunc, startwhitespace, validchars
	//	Extraction -- useful mainly for extracting certain types of string
	//    	compendiumconflicts, credits, hassuggestion, isfuzzy, isreview, untranslated
	protected List<String> pofilterTests = Arrays.asList(
			//	Critical -- can break a program
			"accelerators", "escapes", "newlines", /*nplurals,*/ "printf", "tabs", "variables", "xmltags", /*dialogsizes,*/
			//	Functional -- may confuse the user
			/*acronyms,*/ "blank", "emails", "filepaths", /*functions,*/ "gconf", /*kdecomments,*/ "long", /*musttranslatewords,*/ "notranslatewords", /*numbers,*/ "options", /*purepunc,*/ /*sentencecount,*/ "short", /*spellcheck,*/ "urls", "unchanged",
			//	Cosmetic -- make it look better
			/*brackets, doublequoting, doublespacing,*/ "doublewords", /*endpunc, endwhitespace, puncspacing, simplecaps, simpleplurals, startcaps, singlequoting, startpunc, startwhitespace, validchars */
			//	Extraction -- useful mainly for extracting certain types of string
			/*compendiumconflicts, credits, hassuggestion, isfuzzy, isreview,*/ "untranslated");
	
	
	protected void pofilter_Test(SSHCommandRunner sshCommandRunner, String pofilterTest, File translationFile) {
		log.info("For an explanation of pofilter test '"+pofilterTest+"', see: http://translate.sourceforge.net/wiki/toolkit/pofilter_tests");
		File translationPoFile = new File(translationFile.getPath().replaceFirst(".mo$", ".po"));
		List<String> ignorableMsgIds = new ArrayList<String>();
		
		// if pofilter test -> notranslatewords, create a file with words that don't have to be translated to native language
		final String notranslateFile = "/tmp/notranslatefile";
		if (pofilterTest.equals("notranslatewords")) {
			
			// append words that should not be translated to this list
			// CAUTION: the pofilter notranslatewords work ONLY when the noTranslateWords does not contain spaces or non-alphabetic chars such as "Red Hat" and "subscription-manager".  Alternatively, use VerifyTranslationsDoNotTranslateSubStrings_Test for these types of "words"
			List<String> noTranslateWords = Arrays.asList(/*"Red Hat","subscription-manager","python-rhsm","consumer_types","consumer_export","proxy_hostname:proxy_port" MOVED TO VerifyTranslationsDoNotTranslateSubStrings_Test */);
			
			// echo all of the notranslateWords to the notranslateFile
			sshCommandRunner.runCommandAndWait("rm -f "+notranslateFile);	// remove the old one from the last run
			for(String noTranslateWord : noTranslateWords) sshCommandRunner.runCommandAndWait("echo \""+noTranslateWord+"\" >> "+notranslateFile);
			if (noTranslateWords.isEmpty()) throw new SkipException("Skipping this test since the list of noTranslateWords is empty.");
			Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner, notranslateFile),"The pofilter notranslate file '"+notranslateFile+"' has been created on the client.");
		}
		
		// execute the pofilter test
		String pofilterCommand = "pofilter --gnome -t "+pofilterTest;
		if (pofilterTest.equals("notranslatewords")) pofilterCommand += " --notranslatefile="+notranslateFile;
		SSHCommandResult pofilterResult = sshCommandRunner.runCommandAndWait(pofilterCommand+" "+translationPoFile);
		Assert.assertEquals(pofilterResult.getExitCode(), new Integer(0), "Successfully executed the pofilter tests.");
		
		// convert the pofilter test results into a list of failed Translation objects for simplified handling of special cases 
		List<Translation> pofilterFailedTranslations = Translation.parse(pofilterResult.getStdout());
		
		
		// remove the first translation which contains only meta data
		if (!pofilterFailedTranslations.isEmpty() && pofilterFailedTranslations.get(0).msgid.equals("")) pofilterFailedTranslations.remove(0);
		
		
		// ignore the following special cases of acceptable results for each of the pofilterTests..........
				
		if (pofilterTest.equals("accelerators")) {
			// these msgids are ignorable for all accelerator language tests because they are NOT used as gnome menu items
			// Note: If these fail the accelerators test, then that means the underscored words were translated which may or may
			// not be a bug as determined by the notranslatewords test or VerifyTranslationsDoNotTranslateSubStrings_Test.
			ignorableMsgIds.add("proxy url in the form of proxy_hostname:proxy_port");
			ignorableMsgIds.add("proxy URL in the form of proxy_hostname:proxy_port");
			ignorableMsgIds.add("%%prog %s [OPTIONS] CERT_FILE");
			ignorableMsgIds.add("%%prog %s [OPTIONS] MANIFEST_FILE");
			ignorableMsgIds.addAll(Arrays.asList(new String[]{"progress_label","org_selection_label","no_subs_label","system_name_label","org_selection_scrolledwindow","owner_treeview","progress_label","activation_key_entry","environment_treeview","env_select_vbox_label","default_button","choose_server_label","consumer_entry","organization_entry","registration_dialog_action_area","server_label","server_entry","proxy_button","close_button","facts_view","register_button","register_dialog_main_vbox","registration_dialog_action_area\n","register_details_label","register_progressbar","system_instructions_label","sla_selection_combobox","release_selection_combobox"}));	// these are various GTK widget ids, not gnome menu items
		}
		
		if (pofilterTest.equals("newlines")) {
			
			// common newlines msgid translations to ignore for all langs
			ignorableMsgIds.add(""+"\n"+"This software is licensed to you under the GNU General Public License, version 2 (GPLv2). There is NO WARRANTY for this software, express or implied, including the implied warranties of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2 along with this software; if not, see:\n"+"\n"+"http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt\n"+"\n"+"Red Hat trademarks are not licensed under GPLv2. No permission is granted to use or replicate Red Hat trademarks that are incorporated in this software or its documentation.\n");
			ignorableMsgIds.add(""+"Did not receive a completed unregistration message from RHN Classic for system %s.\n"+"Please investigate on the Customer Portal at https://access.redhat.com.");	// ignorable because the message is printed on the CLI terminal and not rendered in the GUI

			// newlines translations to ignore for specific langs
			if (translationFile.getPath().contains("/bn_IN/"))	ignorableMsgIds.addAll(Arrays.asList("Tip: Forgot your login or password? Look it up at http://red.ht/lost_password"));
//bug 908488		if (translationFile.getPath().contains("/or/"))		ignorableMsgIds.addAll(Arrays.asList("\n"+"Unable to register.\n"+"For further assistance, please contact Red Hat Global Support Services."));
//bug 908488		if (translationFile.getPath().contains("/or/"))		ignorableMsgIds.addAll(Arrays.asList("Unable to perform refresh due to the following exception: %s"));
			
			
			// newlines translations to ignore for specific langs
			String msgId = ""+"Redeeming the subscription may take a few minutes.\n"+"Please provide an email address to receive notification\n"+"when the redemption is complete.";
			Translation failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (translationFile.getPath().contains("/ko/") && failedTranslation!=null && failedTranslation.msgstr.equals("서브스크립션 교환에는 시간이 몇 분 소요될 수 있습니다. \n"+"완료 시 통지를 받기 위한 이메일 주소를 알려주십시오. ")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/ru/") && failedTranslation!=null && failedTranslation.msgstr.equals("Получение подписки может занять несколько минут.\n"+"Укажите электронный адрес для получения \n"+"уведомления об завершении операции.")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/ta_IN/") && failedTranslation!=null && failedTranslation.msgstr.equals("சந்தாவை மீட்டெடுப்பதற்கு சில நிமிடங்கள் எடுக்கலாம்.\n"+"மீட்டெடுத்தல் முடிந்ததும் அறிவிப்பை பெற ஒரு மின்னஞ்சல் முகவரியை கொடுக்கவும்.")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/te/") && failedTranslation!=null && failedTranslation.msgstr.equals("సబ్‌స్క్రిప్షన్‌ను వెచ్చించుటకు కొంత సమయం పట్టవచ్చును.\n"+"వెచ్చింపు పూర్తవగానే నోటీసును స్వీకరించుటకు వొక ఈమెయిల్ చిరునామాను అందించుము.")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/zh_CN/") && failedTranslation!=null && failedTranslation.msgstr.equals("兑换订阅可能需要几分钟的时间。\n"+"请提供电子邮件以在兑换完成时接收通知。")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/zh_CN/") && failedTranslation!=null && failedTranslation.msgstr.equals("兑换订阅可能需要几分钟的时间。请提供电子邮件以在兑换完成时接收通知。")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/hi/") && failedTranslation!=null && failedTranslation.msgstr.equals("सदस्यता रिडीम करना कुछ समय ले सकता है.\n"+"कृपया अधिसूचना पाने के लिए कोई ईमेल पता दाखिल करें जब रिडेप्शन पूरा होता है.")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/zh_TW/") && failedTranslation!=null && failedTranslation.msgstr.equals("兌換訂閱服務可能會花上幾分鐘。\n"+"請提供電子郵件信箱，好在兌換服務完成時通知您。")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/as/") && failedTranslation!=null && failedTranslation.msgstr.equals("স্বাক্ষৰণ ঘুৰাই নিয়ায় কিছু সময় লব পাৰে। ঘুৰাই নিয়া সম্পূৰ্ণ হলে \n"+"অধিসূচনা গ্ৰহণ কৰিবলে এটা ই-মেইল ঠিকনা প্ৰদান কৰক।")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/bn_IN/") && failedTranslation!=null && failedTranslation.msgstr.equals("সাবস্ক্রিপশন ব্যবহার করতে কয়েক মিনিট ব্যয় হতে পারে।\n"+"প্রয়োগ সম্পূর্ণ হওয়ার পরে সূচনা প্রদানের উদ্দেশ্যে অনুগ্রহ করে একটি ই-মেইল ঠিকানা উল্লেখ করুন।")) ignorableMsgIds.add(msgId);
			if (translationFile.getPath().contains("/kn/") && failedTranslation!=null && failedTranslation.msgstr.equals("ಚಂದಾದಾರಿಕೆಯನ್ನು ಹಿಂದಕ್ಕೆ ಪಡೆಯುವಿಕೆ ಕೆಲಹೊತ್ತು ಹಿಡಿಯಬಹುದು.\n"+"ಹಿಂದಕ್ಕೆ ಪಡೆಯುವಿಕೆಯು ಪೂರ್ಣಗೊಂಡ ನಂತರ ಸೂಚಿಸಲು ಒಂದು ಇಮೈಲ್ ವಿಳಾಸವನ್ನು ಒದಗಿಸಿ.")) ignorableMsgIds.add(msgId);
			// TEMPORARY WORKAROUND FOR BUG
			if (failedTranslation!=null) {
				String bugId = "928401"; boolean invokeWorkaroundWhileBugIsOpen = true;
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					log.warning("Ignoring this failed pofilter newlines test translation while bug '"+bugId+"' is open. "+failedTranslation);
					ignorableMsgIds.add(msgId);
				}
			}
			// END OF WORKAROUND
			
			msgId = "Please provide an email address to receive notification\n"+"when the redemption is complete.";
			failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (translationFile.getPath().contains("/zh_TW/") && failedTranslation!=null && failedTranslation.msgstr.equals("請輸入電子郵件地址，好在兌換完成時收到通知。")) ignorableMsgIds.add(msgId);
			
			msgId = ""+"Did not receive a completed unregistration message from RHN Classic for system %s.\n"+"Please investigate on the Customer Portal at https://access.redhat.com.";
			failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (translationFile.getPath().contains("/zh_CN/") && failedTranslation!=null && failedTranslation.msgstr.equals("未从 RHN 传统订阅收到关于系统 %s 的 完整未注册信息。请在 https://access.redhat.com 客户门户网站中检查。")) ignorableMsgIds.add(msgId);
			
			msgId = ""+"We have detected that you have multiple service levels on various products.\n"+"Please select how you want them assigned.";
			failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (translationFile.getPath().contains("/zh_CN/") && failedTranslation!=null && failedTranslation.msgstr.equals("已探测到您在不同产品中有多个服务等级。请选择如何分配它们。")) ignorableMsgIds.add(msgId);

			msgId = "Tip: Forgot your login or password? Look it up at http://redhat.com/forgot_password";
			failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (translationFile.getPath().contains("/or/") && failedTranslation!=null && failedTranslation.msgstr.equals("ସୂଚନା: ଆପଣଙ୍କର ଲଗଇନ କିମ୍ବା ପ୍ରବେଶ ସଂକେତ ଭୁଲିଯାଇଛନ୍ତି କି?"+"\n"+"http://redhat.com/forgot_password ରେ ଦେଖନ୍ତୁ")) ignorableMsgIds.add(msgId);
			
			msgId = "\n"+"Retrieving existing RHN Classic subscription information...";
			failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			// TEMPORARY WORKAROUND FOR BUG
			if (failedTranslation!=null) {
				String bugId = "928401"; boolean invokeWorkaroundWhileBugIsOpen = true;
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					log.warning("Ignoring this failed pofilter newlines test translation while bug '"+bugId+"' is open. "+failedTranslation);
					ignorableMsgIds.add(msgId);
				}
			}
			// END OF WORKAROUND
			
			msgId = "\n"+"Attempting to register system to Red Hat Subscription Management...";
			failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			// TEMPORARY WORKAROUND FOR BUG
			if (failedTranslation!=null) {
				String bugId = "928401"; boolean invokeWorkaroundWhileBugIsOpen = true;
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					log.warning("Ignoring this failed pofilter newlines test translation while bug '"+bugId+"' is open. "+failedTranslation);
					ignorableMsgIds.add(msgId);
				}
			}
			// END OF WORKAROUND
		}
		
		if (pofilterTest.equals("xmltags")) { 
			Boolean match = false; 
			for(Translation pofilterFailedTranslation : pofilterFailedTranslations) {
				// Parsing mgID and msgStr for XMLTags
				Pattern xmlTags = Pattern.compile("<.+?>");  
				Matcher tagsMsgID = xmlTags.matcher(pofilterFailedTranslation.msgid);
				Matcher tagsMsgStr = xmlTags.matcher(pofilterFailedTranslation.msgstr);
				// Populating a msgID tags into a list
				ArrayList<String> msgIDTags = new ArrayList<String>();
				while(tagsMsgID.find()) {
					msgIDTags.add(tagsMsgID.group());
				}
				// Sorting an list of msgID tags
				ArrayList<String> msgIDTagsSort = new ArrayList<String>(msgIDTags);
				Collections.sort(msgIDTagsSort);
				// Populating a msgStr tags into a list
				ArrayList<String> msgStrTags = new ArrayList<String>();
				while(tagsMsgStr.find()) {
					msgStrTags.add(tagsMsgStr.group());
				}
				// Sorting an list of msgStr tags
				ArrayList<String> msgStrTagsSort = new ArrayList<String>(msgStrTags);
				Collections.sort(msgStrTagsSort);
				// Verifying whether XMLtags are opened and closed appropriately 
				// If the above condition holds, then check for XML Tag ordering
				if(msgIDTagsSort.equals(msgStrTagsSort) && msgIDTagsSort.size() == msgStrTagsSort.size()) {
					int size = msgIDTags.size(),count=0;
					// Stack to hold XML tags
					Stack<String> stackMsgIDTags = new Stack<String>();
					Stack<String> stackMsgStrTags = new Stack<String>();
					// Temporary stack to hold popped elements
					Stack<String> tempStackMsgIDTags = new Stack<String>();
					Stack<String> tempStackMsgStrTags = new Stack<String>();
					while(count< size) {
						// If it's not a close tag push into stack
						if(!msgIDTags.get(count).contains("/")) stackMsgIDTags.push(msgIDTags.get(count));
						else {
							if(checkTags(stackMsgIDTags,tempStackMsgIDTags,msgIDTags.get(count))) match = true;
							else {
								// If an open XMLtag doesn't have an appropriate close tag exit loop
								match = false;
								break;
							}
						}
						// If it's not a close tag push into stack
						if(!msgStrTags.get(count).contains("/")) stackMsgStrTags.push(msgStrTags.get(count));
						else {
							if(checkTags(stackMsgStrTags,tempStackMsgStrTags,msgStrTags.get(count))) match = true;
							else {
								// If an open XMLtag doesn't have an appropriate close tag exit loop
								match = false;
								break;
							}
						}
						// Incrementing count to point to the next element
						count++;
					}
				}
				if(match) ignorableMsgIds.add(pofilterFailedTranslation.msgid);
			}
		}
		
		if (pofilterTest.equals("filepaths")) {
			
			// filepaths translations to ignore for specific langs
			String msgId = "Could not read installation number from /etc/sysconfig/rhn/install-num.  Aborting.";
			Translation failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (translationFile.getPath().contains("/ko/") && failedTranslation!=null && failedTranslation.msgstr.equals("/etc/sysconfig/rhn/install-num에서 설치 번호를 읽을 수 없습니다. 중지 중 ")) ignorableMsgIds.add(msgId);
			
			for(Translation pofilterFailedTranslation : pofilterFailedTranslations) {
				// Parsing mgID and msgStr for FilePaths ending ' ' (space) 
				Pattern filePath = Pattern.compile("/.*?( |$)", Pattern.MULTILINE);  
				Matcher filePathMsgID = filePath.matcher(pofilterFailedTranslation.msgid);
				Matcher filePathMsgStr = filePath.matcher(pofilterFailedTranslation.msgstr);
				ArrayList<String> filePathsInID = new ArrayList<String>();
				ArrayList<String> filePathsInStr = new ArrayList<String>();
				// Reading the filePaths into a list
				while(filePathMsgID.find()) {
					filePathsInID.add(filePathMsgID.group());
				}
				while(filePathMsgStr.find()) {
					filePathsInStr.add(filePathMsgStr.group());
				}
				// If the lists are equal in size, then compare the contents of msdID->filePath and msgStr->filePath
				//if(filePathsInID.size() == filePathsInStr.size()) {
					for(int i=0;i<filePathsInID.size();i++) {
						// If the msgID->filePath ends with '.', remove '.' and compare with msgStr->filePath
						if(filePathsInID.get(i).trim().startsWith("//")) {
							ignorableMsgIds.add(pofilterFailedTranslation.msgid);
							continue;
						}
						//contains("//")) ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
						if(filePathsInID.get(i).trim().charAt(filePathsInID.get(i).trim().length()-1) == '.') {
							String filePathID = filePathsInID.get(i).trim().substring(0, filePathsInID.get(i).trim().length()-1);
							if(filePathID.equals(filePathsInStr.get(i).trim())) ignorableMsgIds.add(pofilterFailedTranslation.msgid);
						}
						/*else {
							if(filePathsInID.get(i).trim().equals(filePathsInStr.get(i).trim())) ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
						}*/
					}
				//}
			}
		}
		
		// TODO remove or comment this ignore case once the msgID is corrected 
		// error: 		msgid "Error: you must register or specify --username and password to list service levels"
		// rectified:	msgid "Error: you must register or specify --username and --password to list service levels"
		if (pofilterTest.equals("options")) {
			ignorableMsgIds = Arrays.asList("Error: you must register or specify --username and password to list service levels");
		}
		
		if (pofilterTest.equals("short")) {
			// common short msgid translations to ignore for all langs
			ignorableMsgIds.addAll(Arrays.asList("No", "Yes", "Key", "Value", "N/A", "None", "Number", "and"));
			
			// ignore short translation for " and " ONLY when the msgstr has NOT trimmed the white space
			String msgId = " and ";
			Translation failedTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgId, pofilterFailedTranslations);
			if (failedTranslation!=null && failedTranslation.msgstr.startsWith(" ") && failedTranslation.msgstr.endsWith(" ")) ignorableMsgIds.add(msgId);
			// TEMPORARY WORKAROUND FOR BUG 984206 - def friendly_join(items): in utils.py should not use string " and "
			else if (failedTranslation!=null) {
				String bugId = "984206"; boolean invokeWorkaroundWhileBugIsOpen = true;
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");SubscriptionManagerCLITestScript.addInvokedWorkaround(bugId);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					log.warning("Ignoring this failed pofilter short test translation while bug '"+bugId+"' is open. "+failedTranslation);
					ignorableMsgIds.add(msgId);
				}
			}
			// END OF WORKAROUND
			
			// short msgids to ignore for specific langs
			if((translationFile.getPath().contains("/zh_TW/"))) ignorableMsgIds.addAll(Arrays.asList("automatically attach compatible subscriptions to this system","automatically attach compatible                                subscriptions to this system"));
		}
		
		if (pofilterTest.equals("doublewords")) {
			// common doublewords in the translation to ignore for all langs
			ignorableMsgIds.addAll(Arrays.asList("Subscription Subscriptions Box", "Subscription Subscriptions Label"));

			// doublewords in the translation to ignore for specific langs
			if((translationFile.getPath().contains("/pa/"))) ignorableMsgIds.addAll(Arrays.asList("Server URL can not be None"));
			if((translationFile.getPath().contains("/hi/"))) ignorableMsgIds.addAll(Arrays.asList("Server URL can not be None"));	// more info in bug 861095
			if((translationFile.getPath().contains("/fr/"))) ignorableMsgIds.addAll(Arrays.asList("The Subscription Management Service you register with will provide your system with updates and allow additional management.","The subscription management service you register with will provide your system with updates and allow additional management."));	// msgstr "Le service de gestion des abonnements « Subscription Management » avec lequel vous vous enregistrez fournira à votre système des mises à jour et permettra une gestion supplémentaire."
			if((translationFile.getPath().contains("/or/"))) ignorableMsgIds.addAll(Arrays.asList("Run the initial checks immediately, with no delay.","Run the initial checks immediatly, with no delay.","run the initial checks immediately, with no delay"));
		}
		
		if (pofilterTest.equals("unchanged")) {
			// common unchanged translations to ignore for all langs
			ignorableMsgIds.addAll(Arrays.asList(
						"connectionStatusLabel","progress_label","org_selection_label","no_subs_label","system_name_label","org_selection_scrolledwindow","owner_treeview","progress_label","activation_key_entry","environment_treeview","env_select_vbox_label","default_button","choose_server_label","consumer_entry","organization_entry","registration_dialog_action_area","server_label","server_entry","proxy_button","close_button","facts_view","register_button","register_dialog_main_vbox","registration_dialog_action_area\n","register_details_label","register_progressbar","system_instructions_label","sla_selection_combobox","release_selection_combobox","autoheal_checkbox",
						"hostname[:port][/prefix]",
						"python-rhsm: %s","subscription-manager: %s", "python-rhsm: %s", "%s of %s",
						"<b>SKU:</b>", "<b>HTTP Proxy</b>", "<b>python-rhsm version:</b> %s", "<b>python-rhsm Version:</b> %s",
						"prod 1, prod2, prod 3, prod 4, prod 5, prod 6, prod 7, prod 8",
						"RHN Classic", "Red Hat Subscription Manager", "Red Hat Subscription Management", "Red Hat Subscription Validity Applet",
						"floating-point", "integer", "long integer",
						"Copyright (c) 2012 Red Hat, Inc.",
						"\nThis software is licensed to you under the GNU General Public License, version 2 (GPLv2). There is NO WARRANTY for this software, express or implied, including the implied warranties of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2 along with this software; if not, see:\n"+"\n"+"http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt\n"+"\n"+"Red Hat trademarks are not licensed under GPLv2. No permission is granted to use or replicate Red Hat trademarks that are incorporated in this software or its documentation.\n"));

			// unchanged translations to ignore for specific langs
			if (translationFile.getPath().contains("/bn_IN/"))	ignorableMsgIds.addAll(Arrays.asList("Subscription Validity Applet","Auto-attach"));
			if (translationFile.getPath().contains("/ta_IN/"))	ignorableMsgIds.addAll(Arrays.asList("Org: ","org id: %s","org ID: %s","Repo Id:              \\t%s","Repo Url:             \\t%s","Auto-attach"));
			if (translationFile.getPath().contains("/pt_BR/"))	ignorableMsgIds.addAll(Arrays.asList("Org: ","org id: %s","org ID: %s","<b>subscription management service version:</b> %s","Status","Status:               \\t%s","<b>Status:</b>","Login:","Virtual","_Help","virtual", "Repo Id:              \\t%s", "Arch:                 \\t%s"/* omaciel says "Arquitetura:" is better */, "Pool Id:              \\t%s"/* omaciel says "ID do pool:" is better */));
			if (translationFile.getPath().contains("/de_DE/"))	ignorableMsgIds.addAll(Arrays.asList("Subscription Manager","Red Hat account: ","Account","<b>Account:</b>","Account:              \\t%s","<b>Subscription Management Service Version:</b> %s","<b>subscription management service version:</b> %s","subscription management server: %s","Login:","Arch","Arch:","Name","Name:                 \\t%s","Name:","Status","<b>Status:</b>","Status:               \\t%s","Status:","<b>Status Details:</b>","Status Details","Status Details:","Status Details Text","System Status Details","Version: %s","Version","Version:              \\t%s","Version:","_System","long integer","name: %s","label","Label","Name: %s","Release: %s","integer","Tags","Org: ","org ID: %s","\\tManifest","<b>Account:</b>","Account","Account:","Red Hat account: ","Server"));
			if (translationFile.getPath().contains("/es_ES/"))	ignorableMsgIds.addAll(Arrays.asList("Org: ","Serial","No","%s: error: %s","General:"));
			if (translationFile.getPath().contains("/te/"))		ignorableMsgIds.addAll(Arrays.asList("page 2"));
			if (translationFile.getPath().contains("/pa/"))		ignorableMsgIds.addAll(Arrays.asList("<b>python-rhsm version:</b> %s"));
			if (translationFile.getPath().contains("/fr/"))		ignorableMsgIds.addAll(Arrays.asList("Auto-attach","Options","options","Type","Arch","Arches","Architectures","Version","page 2"));
			if (translationFile.getPath().contains("/it/"))		ignorableMsgIds.addAll(Arrays.asList("Auto-attach","Org: ","org id: %s","org ID: %s","Account","<b>Account:</b>","Account:              \\t%s","Account","<b>Arch:</b>","Arch:                 \\t%s","Arch:","Arch","Login:","No","Password:","Release: %s","Password: ","Server"));
			if (translationFile.getPath().contains("/zh_TW/"))	ignorableMsgIds.addAll(Arrays.asList("Auto-attach"));
			if (translationFile.getPath().contains("/zh_CN/"))	ignorableMsgIds.addAll(Arrays.asList("Section: %s, Name: %s"));
			if (translationFile.getPath().contains("/bn_IN/"))	ignorableMsgIds.addAll(Arrays.asList("%prog [options]", "%prog [OPTIONS]","%%prog %s [OPTIONS]", "%%prog %s [OPTIONS] CERT_FILE", "%%prog %s [OPTIONS] MANIFEST_FILE"));
			if (translationFile.getPath().contains("/or/"))		ignorableMsgIds.addAll(Arrays.asList("%prog [options]", "%prog [OPTIONS]","%%prog %s [OPTIONS]", "%%prog %s [OPTIONS] CERT_FILE", "%%prog %s [OPTIONS] MANIFEST_FILE"));
			if (translationFile.getPath().contains("/zh_TW/"))	ignorableMsgIds.addAll(Arrays.asList("%prog [options]", "%prog [OPTIONS]","%%prog %s [OPTIONS]", "%%prog %s [OPTIONS] CERT_FILE", "%%prog %s [OPTIONS] MANIFEST_FILE"));
			if (translationFile.getPath().contains("/fr/"))		ignorableMsgIds.addAll(Arrays.asList("%prog [options]", "%prog [OPTIONS]","%%prog %s [OPTIONS]", "%%prog %s [OPTIONS] CERT_FILE", "%%prog %s [OPTIONS] MANIFEST_FILE"));
			if (translationFile.getPath().contains("/gu/"))		ignorableMsgIds.addAll(Arrays.asList(                   "%prog [OPTIONS]","%%prog %s [OPTIONS]", "%%prog %s [OPTIONS] CERT_FILE", "%%prog %s [OPTIONS] MANIFEST_FILE"));
		}
		
		if (pofilterTest.equals("urls")) {
			if(translationFile.getPath().contains("/zh_CN/")) ignorableMsgIds.addAll(Arrays.asList("Server URL has an invalid scheme. http:// and https:// are supported"));
			
			// search for failed pofilter urls translation and ignore acceptable cases
			for (Translation failedTranslation : pofilterFailedTranslations) {
				//	msgid ""
				//	"Did not receive a completed unregistration message from RHN Classic for system %s.\n"
				//	"Please investigate on the Customer Portal at https://access.redhat.com."
				// ignore translations that excluded the trailing period on the url
				if (failedTranslation.msgid.replaceAll("\\n","").matches(".*https://access\\.redhat\\.com\\.$") && failedTranslation.msgstr.contains("https://access.redhat.com")) {
					ignorableMsgIds.add(failedTranslation.msgid);
				}
				
				//	msgid ""
				//	"Too many content sets for certificate {0}. A newer client may be available "
				//	"to address this problem. See kbase "
				//	"https://access.redhat.com/knowledge/node/129003 for more information."
				// ignore translations that appended the url with a period or surrounded the url with parenthesis since their inclusion does NOT appear to affect the linkability of the url (e.g. "https://access.redhat.com/knowledge/node/129003)" resolves to the expected page and so does "https://access.redhat.com/knowledge/node/129003." )
				if (failedTranslation.msgid.replaceAll("\\n","").contains("https://access.redhat.com/knowledge/node/129003 ") && failedTranslation.msgstr.contains("https://access.redhat.com/knowledge/node/129003")) {
					ignorableMsgIds.add(failedTranslation.msgid);
				}
			}
		}
		
		
		
		
		// pluck out the ignorable pofilter test results
		for (String msgid : ignorableMsgIds) {
			Translation ignoreTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgid, pofilterFailedTranslations);
			if (ignoreTranslation!=null) {
				log.info("Ignoring result of pofiliter test '"+pofilterTest+"' for msgid: "+ignoreTranslation.msgid);
				pofilterFailedTranslations.remove(ignoreTranslation);
			}
		}
		
		// for convenience reading the logs, log warnings for the failed pofilter test results (when some of the failed test are being ignored)
		if (!ignorableMsgIds.isEmpty()) for (Translation pofilterFailedTranslation : pofilterFailedTranslations) {
			log.warning("Failed result of pofiliter test '"+pofilterTest+"' for translation: "+pofilterFailedTranslation);
		}
		
		// assert that there are no failed pofilter translation test results remaining after the ignorable pofilter test results have been plucked out
		Assert.assertEquals(pofilterFailedTranslations.size(),0, "Discounting the ignored test results, the number of failed pofilter '"+pofilterTest+"' tests for translation file '"+translationFile+"'.");
	}
	
	
	/**
	 * @param str
	 * @return the tag character (Eg: <b> or </b> return_val = 'b')
	 */
	protected char parseElement(String str){
		return str.charAt(str.length()-2);
	}
	
	/**
	 * @param tags
	 * @param temp
	 * @param tagElement
	 * @return whether every open tag has an appropriate close tag in order
	 */
	protected Boolean checkTags(Stack<String> tags, Stack<String> temp, String tagElement) {
		// If there are no open tags in the stack -> return
		if(tags.empty()) return false;
		String popElement = tags.pop();
		// If openTag in stack = closeTag -> return  
		if(!popElement.contains("/") && parseElement(popElement) == parseElement(tagElement)) return true;
		else {
			// Continue popping elements from stack and push to temp until appropriate open tag is found
			while(popElement.contains("/") || parseElement(popElement) != parseElement(tagElement)) {
				temp.push(popElement);
				// If stack = empty and no match, push back the popped elements -> return
				if(tags.empty()) {
					while(!temp.empty()) {
						tags.push(temp.pop());
					}
					return false;
				}
				popElement = tags.pop();
			}
			// If a match is found, push back the popped elements -> return
			while(!temp.empty()) tags.push(temp.pop());
		}
		return true;
	}
	
	
	
	// Data Providers ***********************************************************************
	
	@DataProvider(name="getSubscriptionManagerTranslationFilePofilterTestData")
	public Object[][] getSubscriptionManagerTranslationFilePofilterTestDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getSubscriptionManagerTranslationFilePofilterTestDataAsListOfLists());
	}
	protected List<List<Object>> getSubscriptionManagerTranslationFilePofilterTestDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		// Client side
		Map<File,List<Translation>> translationFileMapForSubscriptionManager = buildTranslationFileMapForSubscriptionManager();
		for (File translationFile : translationFileMapForSubscriptionManager.keySet()) {
			for (String pofilterTest : pofilterTests) {
				Set<String> bugIds = new HashSet<String>();

				// Bug 825362	[es_ES] failed pofilter accelerator tests for subscription-manager translations 
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/es_ES/")) bugIds.add("825362");
				// Bug 825367	[zh_CN] failed pofilter accelerator tests for subscription-manager translations 
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/zh_CN/")) bugIds.add("825367");
				// Bug 860084 - [ja_JP] two accelerators for msgid "Configure Pro_xy"
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/ja/")) bugIds.add("860084");
				// Bug 872697 - [ja_JP] two accelerators for msgid "Configure Pro_xy"
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/ja/")) bugIds.add("872697");
				// Bug 908879 - [es_ES] pofilter acceleratiors test failed for subscription-manager 1.8.X 
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/es_ES/")) bugIds.add("908879");
				
//				// Bug 825397	Many translated languages fail the pofilter newlines test
//				if (pofilterTest.equals("newlines") && !(translationFile.getPath().contains("/zh_CN/")||translationFile.getPath().contains("/ru/")||translationFile.getPath().contains("/ja/"))) bugIds.add("825397");			
				// Bug 887957 	[ml_IN] pofilter newlines test failed for msgid="Redeeming the subscription may take..." 
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/ml/"))) {bugIds.add("887957"); bugIds.add("825393");}
				// Bug 887966 - [es_ES] pofilter newlines test failed for msgid="Redeeming the subscription may take..."
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/es_ES/"))) {bugIds.add("887966"); bugIds.add("825393");}
				// Bug 887989 - [de_DE] pofilter newlines test failed on msgid "Redeeming the subscription may take..."
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/de_DE/"))) {bugIds.add("887989");}
				// Bug 887995 - [fr_FR] pofilter newlines test failed for msgid "Redeeming the subscription..." 
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/fr/"))) {bugIds.add("887995");}
				// Bug 887997 - [it] pofilter newlines test failed om msgid="Error subscribing: %s"
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/it/"))) {bugIds.add("887997");}
				// Bug 888006 - [kn] pofilter newlines test failed on a few msgids
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/kn/"))) {bugIds.add("888006");}
				// Bug 888010 - [te] pofilter newlines test failed on msgid="Redeeming the subscription may take a few..."
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/te/"))) {bugIds.add("888010");}
				// Bug 888936 - [gu] pofilter newlines test failed for a few msgids 
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/gu/"))) {bugIds.add("888936");}
				// Bug 888960 - [ko] pofilter newlines test failed 
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/ko/"))) {bugIds.add("888960");}
				// Bug 888964 - [bn_IN] pofilter newlines test failed 
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/bn_IN/"))) {bugIds.add("888964");}
				// Bug 888971 - [pt_BR] pofilter newlines test failed
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/pt_BR/"))) {bugIds.add("888971");}
				// Bug 888979 - [te] pofilter newlines test failed
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/te/"))) {bugIds.add("888979");}
				// Bug 908108 - [ru][ta_IN][te][zh_CN][es_ES][fr][de_DE][gu][zh_TW][kn] pofilter newlines test failed on msgid="Redeeming the subscription may take a few..." 
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/ru/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/ta_IN/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/te/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/zh_CN/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/es_ES/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/fr/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/de_DE/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/gu/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/zh_TW/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/kn/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/or/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/it/"))) {bugIds.add("908108");}
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/hi/"))) {bugIds.add("908108");}
				// Bug 908037 - [hi][it][ml][ru] character ¶ should not appear in translated msgstr
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/ml/"))) {bugIds.add("908037");}
				// Bug 908434 - [it] pofilter newlines test failed on msgid="Error subscribing: %s"
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/it/"))) {bugIds.add("908434");}
				// Bug 908488 - [or] pofilter newlines test failed
				if (pofilterTest.equals("newlines") && (translationFile.getPath().contains("/or/"))) {bugIds.add("908488");}
				
				// Bug 827059	[kn] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/kn/")) bugIds.add("827059");
				// Bug 827079 	[es-ES] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/es_ES/")) bugIds.add("827079");
				// Bug 827085	[hi] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/hi/")) bugIds.add("827085");
				// Bug 827089 	[hi] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/te/")) bugIds.add("827089");
				// Bug 887431 - [pt_BR] pofilter variables (and printf) test is failing on a missing %s in the msgstr
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("887431");
				// Bug 908866 - [kn] pofilter variables/printf test fails for subscription-manager 1.8.X
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/kn/")) bugIds.add("908866");
				
//				// Bug 827113 	Many Translated languages fail the pofilter tabs test
//				if (pofilterTest.equals("tabs") && !(translationFile.getPath().contains("/pa/")||translationFile.getPath().contains("/mr/")||translationFile.getPath().contains("/de_DE/")||translationFile.getPath().contains("/bn_IN/"))) bugIds.add("825397");
				// Bug 888858 - [ml] pofilter tabs test failed for several msgids
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/ml/")) bugIds.add("888858");
				// Bug 888864 - [es_ES] pofilter tabs test failed for several msgids 
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/es_ES/")) bugIds.add("888864");
				// Bug 888868 - [pt_BR] pofilter tabs test failed for several msgids
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("888868");
				// Bug 888873 - [or] pofilter tabs test failed for several msgids 
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/or/")) bugIds.add("888873");
				// Bug 888886 - [it] pofilter tabs test failed for several msgids
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/it/")) bugIds.add("888886");
				// Bug 888889 - [hi] pofilter tabs test failed for several msgids
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/hi/")) bugIds.add("888889");
				// Bug 888891 - [zh_CN] pofilter tabs test failed for several msgids 
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/zh_CN/")) bugIds.add("888891");
				// Bug 888923 - [te] pofilter tabs test failed for several msgids 
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/te/")) bugIds.add("888923");
				// Bug 888928 - [ja] pofilter tabs test failed
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/ja/")) bugIds.add("888928");
				// Bug 911759 - [it][ja] pofilter tabs is failing on subscription-manager 1.8.X
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/it/")) bugIds.add("911759");
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/ja/")) bugIds.add("911759");
				// Bug 928472 - [kn] pofilter tabs test failed for subscription-manager 1.8.X
				if (pofilterTest.equals("tabs") && translationFile.getPath().contains("/kn/")) bugIds.add("928472");
				
				// Bug 827161 	[bn_IN] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/bn_IN/")) bugIds.add("827161");
				// Bug 827208	[or] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/or/")) bugIds.add("827208");
				// Bug 827214	[or] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("827214");
				// Bug 828368 - [kn] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/kn/")) bugIds.add("828368");
				// Bug 828365 - [kn] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/kn/")) bugIds.add("828365");
				// Bug 828372 - [es_ES] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/es_ES/")) bugIds.add("828372");
				// Bug 828416 - [ru] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/ru/")) bugIds.add("828416");
				// Bug 843113 - [ta_IN] failed pofilter xmltags test for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("843113");
				// Bug 908511 - [es_ES] pofilter xmltags test is failing against subscription-manager 1.8.X
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/es_ES/")) bugIds.add("908511");
				
				// Bug 828566	[bn-IN] cosmetic bug for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/bn_IN/")) bugIds.add("828566");
				// Bug 828567 	[as] cosmetic bug for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/as/")) bugIds.add("828567");
				// Bug 828576 - [ta_IN] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("828576");
				// Bug 828579 - [ml] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ml/")) bugIds.add("828579");
				// Bug 828580 - [mr] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/mr/")) bugIds.add("828580");
				// Bug 828583 - [ko] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ko/")) bugIds.add("828583");
				// Bug 908521 - [mr][ta_IN][ml] pofilter filepaths test failed for subscription-manager 1.8.X
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/mr/")) bugIds.add("908521");
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("908521");
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ml/")) bugIds.add("908521");
				
				// Bug 828810 - [kn] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/kn/")) bugIds.add("828810");
				// Bug 828816 - [es_ES] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/es_ES/")) bugIds.add("828816");
				// Bug 828821 - [hi] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/hi/")) bugIds.add("828821");
				// Bug 828867 - [te] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/te/")) bugIds.add("828867");
				// Bug 887431 - [pt_BR] pofilter variables test is failing on a missing %s in the msgstr
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("887431");
				// Bug 908866 - [kn] pofilter variables/printf test fails for subscription-manager 1.8.X
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/kn/")) bugIds.add("908866");
				
				// Bug 828903 - [bn_IN] failed pofilter options tests for subscription-manager translations 
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/bn_IN/")) bugIds.add("828903");
				// Bug 828930 - [as] failed pofilter options tests for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/as/")) bugIds.add("828930");
				// Bug 828948 - [or] failed pofilter options tests for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/or/")) bugIds.add("828948");
				// Bug 828954 - [ta_IN] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("828954");
				// Bug 828958 - [pt_BR] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("828958");
				// Bug 828961 - [gu] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/gu/")) bugIds.add("828961");
				// Bug 828965 - [hi] failed pofilter options test for subscription-manager trasnlations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/hi/")) bugIds.add("828965");
				// Bug 828966 - [zh_CN] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/zh_CN/")) bugIds.add("828966");
				// Bug 828969 - [te] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/te/")) bugIds.add("828969");
				// Bug 842898 - [it] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/it/")) bugIds.add("842898");
				// Bug 886917 - [or_IN] bad translation for msgid "Error: Must use --auto-attach with --servicelevel." 
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/or/")) bugIds.add("886917");
				// Bug 887433 - [es_ES] pofilter options test failed on the translation of --auto-attach to --auto-adjuntar
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/es_ES/")) bugIds.add("887433");
				// Bug 887434 - [ru] pofilter options test failed on incorrect translation of commandline option "--list"
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/ru/")) bugIds.add("887434");
				// Bug 908869 - [pt_BR][ta_IN] pofilter options test failed for subscription-manager 1.8.X
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("908869");
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("908869");
				// Bug 928475 - [ru] pofilter options test failed on incorrect translation of commandline option "--list"
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/ru/")) bugIds.add("928475");
				// Bug 928523 - [ko] pofilter options test failed
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/ko/")) bugIds.add("928523");
				
				// Bug 828985 - [ml] failed pofilter urls test for subscription manager translations
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/ml/")) bugIds.add("828985");
				// Bug 828989 - [pt_BR] failed pofilter urls test for subscription-manager translations
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("828989");
				// Bug 860088 - [de_DE] translation for a url should not be altered 
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/de_DE/")) bugIds.add("860088");
				// Bug 872684 - [de_DE] pofilter urls test is failing on the trailing period to url http://red.ht/lost_password.
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/de_DE/")) bugIds.add("872684");
				// Bug 887429 - [pt_BR] failed pofilter urls test
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("887429");
				// Bug 908059 - [pt-BR] pofilter urls test is failing against subscription-manager 1.8.X
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("908059");
				// Bug 928469 - [ml] pofilter urls test failed for subscription-manager 1.8.X
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/ml/")) bugIds.add("928469");			
				// Bug 928489 - [pt_BR] pofilter urls test failed for subscription-manager 1.8.X
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("928489");
				// Bug 928487 - [ru] pofilter fails on url tests in subscription-manager 1.8.X
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/ru/")) bugIds.add("928487");
				
				// Bug 845304 - translation of the word "[OPTIONS]" has reverted
				if (pofilterTest.equals("unchanged")) bugIds.add("845304");
				// Bug 829459 - [bn_IN] failed pofilter unchanged option test for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/bn_IN/")) bugIds.add("829459");
				// Bug 829470 - [or] failed pofilter unchanged options for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/or/")) bugIds.add("829470");
				// Bug 829471 - [ta_IN] failed pofilter unchanged optioon test for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("829471");
				// Bug 829476 - [ml] failed pofilter unchanged option test for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ml/")) bugIds.add("829476");
				// Bug 829479 - [pt_BR] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pt_BR/")) {bugIds.add("829479"); bugIds.add("828958");}
				// Bug 829482 - [zh_TW] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/zh_TW/")) bugIds.add("829482");
				// Bug 829483 - [de_DE] failed pofilter unchanged options test for suubscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/de_DE/")) bugIds.add("829483");
				// Bug 829486 - [fr] failed pofilter unchanged options test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/fr/")) bugIds.add("829486");
				// Bug 829488 - [es_ES] failed pofilter unchanged option tests for subscription manager translations				
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/es_ES/")) bugIds.add("829488");
				// Bug 829491 - [it] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/it/")) bugIds.add("829491");
				// Bug 829492 - [hi] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/hi/")) bugIds.add("829492");
				// Bug 829494 - [zh_CN] failed pofilter unchanged option for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/zh_CN/")) bugIds.add("829494");
				// Bug 829495 - [pa] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pa/")) bugIds.add("829495");
				// Bug 840914 - [te] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/te/")) bugIds.add("840914");
				// Bug 840644 - [ta_IN] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("840644");
				// Bug 855087 -	[mr] missing translation for the word "OPTIONS" 
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/mr/")) bugIds.add("855087");
				// Bug 855085 -	[as] missing translation for the word "OPTIONS" 
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/as/")) bugIds.add("855085");
				// Bug 855081 -	[pt_BR] untranslated msgid "Arch" 
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("855081");
				// Bug 864095 - [it_IT] unchanged translation for msgid "System Engine Username: " 
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/it/")) bugIds.add("864095");
				// Bug 864092 - [es_ES] unchanged translation for msgid "Configure Proxy" 
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/es_ES/")) bugIds.add("864092");
				// Bug 871163 - [mr_IN] translation for msgid "Org: " is unchanged
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/mr/")) bugIds.add("871163");
				// Bug 872704 - [es_ES] unchanged translation for msgid "Configure Proxy" 
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/es_ES/")) bugIds.add("872704");
				// Bug 887890 - [pa_IN] polfilter unchanged test failed for msgid "Activation Keys are alphanumeric strings that..."
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pa/")) bugIds.add("887890");
				// Bug 908886 - [ta_IN][pt_BR][de_DE][zh_CN] pofilter unchanged test fails against subscription-manager 1.8.X
				// if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("908886");
				// if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("908886");
				// if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/de_DE/")) bugIds.add("908886");
				// if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/zh_CN/")) bugIds.add("908886");
				// Bug 908886 - [ml][ta_IN][kn][pt_BR][es_ES][it][hi][zh_CN][te][ja][pa][ko][or] pofilter unchanged test fails against subscription-manager 1.8.X
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ml/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/kn/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pt_BR/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/es_ES/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/it/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/hi/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/zh_CN/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/te/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ja/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pa/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ko/")) bugIds.add("908886");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/or/")) bugIds.add("908886");
				// Bug 911764 - [mr] pofilter unchanged test fails for subscription-manager 1.8.X
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/mr/")) bugIds.add("911764");
				// Bug 911772 - [ml] pofilter unchanged test fails for subscription-manager 1.8.X
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ml/")) bugIds.add("911772");
				// Bug 911776 - [it] msgids containing [OPTIONS] are not translated; expected [OPZIONI]
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/it/")) bugIds.add("911776");
				// Bug 911779 - [hi][te][pa][ko] unchanged translations for "[options]"
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/hi/")) bugIds.add("911779");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/te/")) bugIds.add("911779");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pa/")) bugIds.add("911779");
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ko/")) bugIds.add("911779");
				// Bug 921126 - [as] msgid "%%prog %s [OPTIONS] MANIFEST_FILE" is not translated in 1.8.X
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/as/")) bugIds.add("921126");
				// Bug 927979 - [kn] unchanged msgid "%%prog %s [OPTIONS] MANIFEST_FILE" in subscription-manager 1.8.X
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/kn/")) bugIds.add("927979");
				// Bug 927990 - [ta_IN] unchanged translations for several msgids containing "OPTIONS" in subscription-manager/1.8.X
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta/")) bugIds.add("927990");
				// Bug 928000 - [ml] unchanged translations for msgids containing the word OPTIONS
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ml/")) bugIds.add("928000");
				// Bug 813268 - [ta_IN] unlocalised strings for subscription-manager identity
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta_IN/")) bugIds.add("813268");
				
				// Bug 841011 - [kn] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("doublewords") && translationFile.getPath().contains("/kn/")) bugIds.add("841011");
				// Bug 861095 - [hi_IN] duplicate words appear in two msgid translations 
				if (pofilterTest.equals("doublewords") && translationFile.getPath().contains("/hi/")) bugIds.add("861095");
				// Bug 887923 - [gu] pofilter doublewords test failed on msgid=""The subscription management service you register with..."
				if (pofilterTest.equals("doublewords") && translationFile.getPath().contains("/gu/")) bugIds.add("887923");
				// Bug 911757 - [kn] pofilter doublewords test fails for subscription-manager 1.8.X
				if (pofilterTest.equals("doublewords") && translationFile.getPath().contains("/kn/")) bugIds.add("911757");
				
				// Bug 911762 - [te] pofilter blank test is failing against subscription-manager-migration 1.8.X
				if (pofilterTest.equals("blank") && translationFile.getPath().contains("/te/")) bugIds.add("911762");
				
				BlockedByBzBug blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
				ll.add(Arrays.asList(new Object[] {blockedByBzBug, pofilterTest, translationFile}));
				 
			}
		}
		
		return ll;
	}
	
	
	
	@DataProvider(name="getCandlepinTranslationFilePofilterTestData")
	public Object[][] getCandlepinTranslationFilePofilterTestDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getCandlepinTranslationFilePofilterTestDataAsListOfLists());
	}
	protected List<List<Object>> getCandlepinTranslationFilePofilterTestDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		
		// Server side
		Map<File,List<Translation>> translationFileMapForCandlepin = buildTranslationFileMapForCandlepin();
		for (File translationFile : translationFileMapForCandlepin.keySet()) {
			for (String pofilterTest : pofilterTests) {
				Set<String> bugIds = new HashSet<String>();
				
				// skip the accelerators test since there are gnome gui menu items generated from the candlepin msgids
				if (pofilterTest.equals("accelerators")) continue;
				
				// Bug 842450 - [ja_JP] failed pofilter newlines option test for candlepin translations
				if (pofilterTest.equals("newlines") && translationFile.getName().equals("ja.po")) bugIds.add("842450");
				if (pofilterTest.equals("tabs") && translationFile.getName().equals("ja.po")) bugIds.add("842450");
				
				// Bug 842784 - [ALL LANG] failed pofilter untranslated option test for candlepin translations
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("pa.po")) bugIds.add("842784");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("bn_IN.po")) bugIds.add("842784");
				
				// Bug 962011 - [ALL] candlepin master translations for all the new "compliance reasons" are needed
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("it.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("es_ES.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("pt_BR.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("gu.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("or.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("as.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("kn.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("de_DE.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("ko.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("ru.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("mr.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("hi.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("ja.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("te.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("ta_IN.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("ml.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("fr.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("zh_CN.po")) bugIds.add("962011");
				if (pofilterTest.equals("untranslated") && translationFile.getName().equals("zh_TW.po")) bugIds.add("962011");
				
				// Bug 865561 - [pa_IN] the pofilter escapes test is failing on msgid "Consumer Type with id {0} could not be found."
				if (pofilterTest.equals("escapes") && translationFile.getName().equals("pa.po")) bugIds.add("865561");
				
				// Bug 929218 - [de_DE] pofilter unchanged test is failing
				if (pofilterTest.equals("unchanged") && translationFile.getName().equals("de_DE.po")) bugIds.add("929218");
				
				BlockedByBzBug blockedByBzBug = new BlockedByBzBug(bugIds.toArray(new String[]{}));
				ll.add(Arrays.asList(new Object[] {blockedByBzBug, pofilterTest, translationFile}));
			}
		}
		
		return ll;
	}
	
}
