package rhsm.data;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.redhat.qe.tools.abstraction.AbstractCommandLineData;

/**
 * @author jsefler
 *
 */
public class ProductSubscription extends AbstractCommandLineData {
	protected static String simpleDateFormat = "MM/dd/yyyy";	// 04/24/2011 https://bugzilla.redhat.com/show_bug.cgi?id=695234  https://bugzilla.redhat.com/show_bug.cgi?id=699442
	
	// abstraction fields
	public String productId;
	public String productName;	// TODO change to subscriptionName as well as all the "productName" references in the testcases
	public List<String> provides;	// list of provided product names
	public BigInteger serialNumber;	// Long serialNumber;	// Integer serialNumber; // serialNumber=290624661330496 is out of range for an Integer
	public Integer contractNumber;
	public BigInteger accountNumber;
	public Integer quantityUsed;
	public Boolean isActive;
	public Calendar startDate;
	public Calendar endDate;
	public String serviceLevel;
	public String serviceType;
	public List<String> statusDetails;
	public String machineType;	// added by bug 1009600 - Show System Type in list --consumed
	public String poolId;		// added by bug 908671 // Including the pool ID in the certificate required a change to Candlepin.  If the pool ID is not present in the entitlement certificate, subscription-manager will report the pool ID as "Not Available".
	public String subscriptionType;	// added by bug 1029968 - [RFE] request for new subscription-manager list installed/consumed field called "Subscription Type"

	public SubscriptionPool fromSubscriptionPool;
	
	public Boolean isExpired(){
		return endDate.after(new Date());
	}

	
	public ProductSubscription(Map<String, String> productData){
		super(productData);
		
		if (this.productName == null)
			this.productName = "";
	}
	

	
	/**
	 * UNMAINTAINED METHOD FROM ssalevan
	 * @param productName
	 * @param fromPool
	 */
	public ProductSubscription(String productName, SubscriptionPool fromPool){
		super(null);
		
		this.productName = productName;
		this.fromSubscriptionPool = fromPool;
	}
	
	@Override
	public String toString() {
		
		String string = "";
		if (productId != null)		string += String.format(" %s='%s'", "productId",productId);
		if (productName != null)	string += String.format(" %s='%s'", "productName",productName);
		if (provides != null)		string += String.format(" %s='%s'", "provides",provides);
		if (serialNumber != null)	string += String.format(" %s='%s'", "serialNumber",serialNumber);
		if (contractNumber != null)	string += String.format(" %s='%s'", "contractNumber",contractNumber);
		if (accountNumber != null)	string += String.format(" %s='%s'", "accountNumber",accountNumber);
		if (quantityUsed != null)	string += String.format(" %s='%s'", "quantityUsed",quantityUsed);
		if (isActive != null)		string += String.format(" %s='%s'", "isActive",isActive);
		if (startDate != null)		string += String.format(" %s='%s'", "startDate",formatDateString(startDate));
		if (endDate != null)		string += String.format(" %s='%s'", "endDate",formatDateString(endDate));
		if (serviceLevel != null)	string += String.format(" %s='%s'", "serviceLevel",serviceLevel);
		if (serviceType != null)	string += String.format(" %s='%s'", "serviceType",serviceType);
		if (statusDetails != null)	string += String.format(" %s='%s'", "statusDetails",statusDetails);
		if (machineType != null)	string += String.format(" %s='%s'", "machineType",machineType);
		if (poolId != null)			string += String.format(" %s='%s'", "poolId",poolId);
		if (subscriptionType != null)	string += String.format(" %s='%s'", "subscriptionType",subscriptionType);
		if (fromSubscriptionPool != null)		string += String.format(" %s='%s'", "fromPool",fromSubscriptionPool);

		return string.trim();
	}
	
	@Override
	protected Calendar parseDateString(String dateString){
		return parseDateString(dateString, simpleDateFormat);
	}
	
	//@Override
	public static String formatDateString(Calendar date){
		DateFormat dateFormat = new SimpleDateFormat(simpleDateFormat);
		return dateFormat.format(date.getTime());
	}
	
	@Override
	public boolean equals(Object obj){
		//return ((ProductSubscription)obj).productName.contains(this.productName);	// I think we need to compare more attributes than using .contains(...) on the productName jsefler 7/13/2010

		// assume the combination of productName and serialNumber is unique across all ProductSubscriptions
		return	((ProductSubscription)obj).productName.equals(this.productName) &&
				((ProductSubscription)obj).serialNumber.equals(this.serialNumber);
	}
	
	/**
	 * @param stdoutListOfConsumedProducts - stdout from "subscription-manager-cli list --consumed"
	 * @return
	 */
	static public List<ProductSubscription> parse(String stdoutListOfConsumedProducts) {
		/*
		[root@jsefler-63server ~]# subscription-manager list --consumed
		+-------------------------------------------+
		    Consumed Product Subscriptions
		+-------------------------------------------+
		
		Product Name:         	Clustering Bits          
		Contract Number:      	7                        
		Account Number:       	12331131231              
		Serial Number:        	7790755495115731621      
		Active:               	True                     
		Quantity Used:        	1                        
		Service Level:        	Premium                  
		Service Type :        	Level 3                  
		Begins:               	06/02/2012               
		Expires:              	08/02/2013               
		
		Product Name:         	Load Balancing Bits      
		Contract Number:      	7                        
		Account Number:       	12331131231              
		Serial Number:        	7790755495115731621      
		Active:               	True                     
		Quantity Used:        	1                        
		Service Level:        	Premium                  
		Service Type :        	Level 3                  
		Begins:               	06/02/2012               
		Expires:              	08/02/2013               
		
		Product Name:         	Awesome OS Server Bits   
		Contract Number:      	7                        
		Account Number:       	12331131231              
		Serial Number:        	7790755495115731621      
		Active:               	True                     
		Quantity Used:        	1                        
		Service Level:        	Premium                  
		Service Type :        	Level 3                  
		Begins:               	06/02/2012               
		Expires:              	08/02/2013               
		
		Product Name:         	Shared Storage Bits      
		Contract Number:      	7                        
		Account Number:       	12331131231              
		Serial Number:        	7790755495115731621      
		Active:               	True                     
		Quantity Used:        	1                        
		Service Level:        	Premium                  
		Service Type :        	Level 3                  
		Begins:               	06/02/2012               
		Expires:              	08/02/2013               
		
		Product Name:         	Management Bits          
		Contract Number:      	7                        
		Account Number:       	12331131231              
		Serial Number:        	7790755495115731621      
		Active:               	True                     
		Quantity Used:        	1                        
		Service Level:        	Premium                  
		Service Type :        	Level 3                  
		Begins:               	06/02/2012               
		Expires:              	08/02/2013               
		
		Product Name:         	Large File Support Bits  
		Contract Number:      	7                        
		Account Number:       	12331131231              
		Serial Number:        	7790755495115731621      
		Active:               	True                     
		Quantity Used:        	1                        
		Service Level:        	Premium                  
		Service Type :        	Level 3                  
		Begins:               	06/02/2012               
		Expires:              	08/02/2013         
		*/
		
		
		/* the following new format introduced in rhel59 consolidates the listing above
		 * Bug 801187 - collapse list of provided products for subscription-manager list --consumed
		[root@jsefler-59server ~]# subscription-manager list --consumed
		+-------------------------------------------+
		   Consumed Subscriptions
		+-------------------------------------------+
		
		Subscription Name:    	Awesome OS Server Bundled
		Provides:             	Clustering Bits
		                      	Awesome OS Server Bits
		                      	Shared Storage Bits
		                      	Management Bits
		                      	Large File Support Bits
		                      	Load Balancing Bits
		SKU:                  	awesomeos-server
		Contract:             	7
		Account:              	12331131231
		Serial Number:        	2062941382077886001
		Active:               	True
		Quantity Used:        	1
		Service Level:        	Premium
		Service Type:         	Level 3
		Starts:               	06/02/2012
		Ends:                 	08/02/2013
		
		*/

		Map<String,String> regexes = new HashMap<String,String>();


		// abstraction field				regex pattern (with a capturing group) Note: the captured group will be trim()ed
		regexes.put("productName",			"Subscription Name:(.*)");
		regexes.put("provides",				"Provides:(.*(\\n.*?)+)^\\w+\\s?\\w+:");	// this assumes that Provides is NOT last in its subscription grouping since ^\w+\s?\w+: represents the start of the next property so as to capture a multi-line value
		regexes.put("productId",			"SKU:(.*)");		// Bug 806986 - Subscription-Manager should refer to subscription name and product name
		regexes.put("contractNumber",		"Contract:(.*)");	// Bug 818355 - Terminology Change: Contract Number -> Contract
		regexes.put("accountNumber",		"Account:(.*)");	// Bug 818339 - Terminology Change: Account Number -> Account
		regexes.put("serialNumber",			"Serial:(.*)");		// Bug 963815 - String Update: 'Serial Number' > 'Serial' in 'list --consumed'
		regexes.put("isActive",				"Active:(.*)");
		regexes.put("quantityUsed",			"Quantity Used:(.*)");
		regexes.put("serviceLevel",			"Service Level:(.*)");
		regexes.put("serviceType",			"Service Type:(.*)");
		regexes.put("statusDetails",		"Status Details:(.*(\\n.*?)+)^\\w+\\s?\\w+:");
		regexes.put("startDate",			"Starts:(.*)");	// Bug 812373 - Terminology Change to Subscription-Manager list --installed & --consumed
		regexes.put("endDate",				"Ends:(.*)");	// Bug 812373 - Terminology Change to Subscription-Manager list --installed & --consumed
		regexes.put("machineType",			"System Type:(.*)");	// Bug 1009600 - Show System Type in list --consumed; Show System Type in attach confirmation gui dialog.
		regexes.put("poolId",				"Pool ID:(.*)");	// Bug 908671 - RFE: include pool id in list --consumed
		regexes.put("subscriptionType",		"Subscription Type:(.*)");	// Bug 1029968 - [RFE] request for new subscription-manager list installed/consumed field called "Subscription Type"
		
		List<Map<String,String>> productList = new ArrayList<Map<String,String>>();
		for(String field : regexes.keySet()){
			Pattern pat = Pattern.compile(regexes.get(field), Pattern.MULTILINE);
			addRegexMatchesToList(pat, stdoutListOfConsumedProducts, productList, field);
		}
		
		List<ProductSubscription> productSubscriptions = new ArrayList<ProductSubscription>();
		for(Map<String,String> productMap : productList)
			productSubscriptions.add(new ProductSubscription(productMap));
		return productSubscriptions;
	}
}
