(ns rhsm.gui.tests.proxy-tests
  (:use [test-clj.testng :only (gen-class-testng)]
        [rhsm.gui.tasks.test-config :only (config
                                           clientcmd
                                           auth-proxyrunner
                                           noauth-proxyrunner)]
        [slingshot.slingshot :only (try+
                                    throw+)]
        [com.redhat.qe.verify :only (verify)]
        gnome.ldtp)
  (:require [rhsm.gui.tasks.tasks :as tasks])
  (:import [org.testng.annotations
            Test
            BeforeClass
            AfterClass
            BeforeGroups
            AfterGroups]))

(def auth-log "/var/log/squid/access.log")
(def noauth-log "/var/log/tinyproxy.log")
(def proxy-success "Proxy connection succeeded")

(defn ^{BeforeClass {:groups ["setup"]}}
  setup [_]
  (try+ (tasks/unregister)
        (catch [:type :not-registered] _)))

(defn register []
  (try+ (tasks/register (@config :username) (@config :password))
        (catch [:type :already-registered]
            {:keys [unregister-first]} (unregister-first)))
  (verify (not (tasks/ui showing? :register-system))))

(defn ^{Test {:groups ["proxy"]}}
  enable_proxy_auth
  "Asserts that the rhsm.conf file is correctly set after setting a proxy with auth."
  [_]
  (let [hostname  (@config :basicauth-proxy-hostname)
        port      (@config :basicauth-proxy-port)
        username  (@config :basicauth-proxy-username)
        password  (@config :basicauth-proxy-password)]
    (tasks/enableproxy hostname :port port :user username :pass password)
    (tasks/verify-conf-proxies hostname port username password)))

(defn ^{Test {:groups ["proxy"]}}
  enable_proxy_noauth
  "Asserts that the rhsm.conf file is correctly set after setting a proxy without auth."
  [_]
  (let [hostname  (@config :noauth-proxy-hostname)
        port      (@config :noauth-proxy-port)]
    (tasks/enableproxy hostname :port port)
    (tasks/verify-conf-proxies hostname port "" "")))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["enable_proxy_auth" "enable_proxy_noauth"]}}
  disable_proxy
  "Asserts that the rhsm.conf file is correctly set after diabling proxies."
  [_]
  (tasks/disableproxy)
  (tasks/verify-conf-proxies "" "" "" ""))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["enable_proxy_auth"]}}
  proxy_auth_connect
  "Asserts that rhsm can connect after setting a proxy with auth."
  [_]
  (enable_proxy_auth nil)
  (let [logoutput (tasks/get-logging @auth-proxyrunner
                                     auth-log
                                     "proxy-auth-connect"
                                     nil
                                     (register))]
    (verify (not  (clojure.string/blank? logoutput)))))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["enable_proxy_noauth"]}}
  proxy_noauth_connect
  "Asserts that rhsm can connect after setting a proxy without auth."
  [_]
  (enable_proxy_noauth nil)
  (let [logoutput (tasks/get-logging @noauth-proxyrunner
                                     noauth-log
                                     "proxy-noauth-connect"
                                     nil
                                     (register))]
    (verify (not  (clojure.string/blank? logoutput)))))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["disable_proxy"]}}
  disable_proxy_connect
  "Asserts that a proxy is not used after clearing proxy settings."
  [_]
  (disable_proxy nil)
  ;; note: if this takes forever, blank out the proxy log file.
  (let [logoutput (tasks/get-logging @auth-proxyrunner
                                     auth-log
                                     "disabled-auth-connect"
                                     nil
                                     (register))]
    (verify (clojure.string/blank? logoutput)))
  (let [logoutput (tasks/get-logging @noauth-proxyrunner
                                     noauth-log
                                     "disabled-auth-connect"
                                     nil
                                     (register))]
    (verify (clojure.string/blank? logoutput))))

(defn test_proxy [expected-message]
  (tasks/ui click :configure-proxy)
  (tasks/ui click :test-connection)
  (try+
   (let [message (tasks/ui gettextvalue :connection-status)]
     (verify (= expected-message message)))
   (finally (tasks/ui click :close-proxy))))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["enable_proxy_auth"]}}
  test_auth_proxy
  "Tests the 'test connection' button when using a proxy with auth."
  [_]
  (test_proxy "Proxy connection succeeded"))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["enable_proxy_noauth"]}}
  test_noauth_proxy
  "Tests the 'test connection' button when using a proxy without auth."
  [_]
  (test_proxy "Proxy connection succeeded"))

(defn ^{Test {:groups ["proxy"]
              :dependsOnMethods ["disable_proxy"]}}
  test_disabled_proxy
  "Test that the 'test connection' button is disabled when proxy settings are cleared."
  [_]
  (tasks/ui click :configure-proxy)
  (tasks/ui click :test-connection)
  (try+ (verify (not (some #(= "sensitive" %)
                           (tasks/ui getallstates :test-connection))))
        (verify (= "" (tasks/ui gettextvalue :connection-status)))
        (finally (tasks/ui click :close-proxy))))

(defn ^{BeforeGroups {:groups ["proxy"]
                      :value ["proxy-enabled-check-status"]}}
  before_test_proxy_with_blank_fields[_]
  (tasks/ui click :configure-proxy)
  (tasks/ui check :proxy-checkbox))

(defn ^{Test {:groups ["proxy"
                       "proxy-enabled-check-status"
                       "blockedByBug-927340"]
              :dependsOnMethods ["disable_proxy"]}}
  test_proxy_with_blank_proxy
  "Test whether 'Test Connection' returns appropriate message when 'Location Proxy' is empty"
  [_]
  (tasks/ui click :test-connection)
  (let [message (tasks/ui gettextvalue :connection-status)]
    (verify (not (= message proxy-success)))))

(defn ^{Test {:groups ["proxy"
                       "proxy-enabled-check-status"]
              :dependsOnMethods ["test_proxy_with_blank_proxy" "disable_proxy"]}}
  test_proxy_with_blank_credentials
  "Test whether 'Test Connection' returns appropriate message when User and Password fields are empty"
  [_]
  (tasks/ui click :authentication-checkbox)
  (tasks/ui click :test-connection)
  (let [message (tasks/ui gettextvalue :connection-status)]
    (verify (not (= message proxy-success)))))  

(defn ^{AfterGroups {:groups ["proxy"]
                     :value ["proxy-enabled-check-status"]}}
  after_test_proxy_with_blank_fields[_]
  (tasks/ui uncheck :proxy-checkbox)
  (tasks/ui uncheck :authentication-checkbox)
  (tasks/ui click :close-proxy))

(defn ^{Test {:groups ["proxy"]}}
  test_bad_proxy
  "Tests the 'test connection' button when using a non-existant proxy."
  [_]
  (try+
   (tasks/enableproxy "doesnotexist.redhat.com")
   (test_proxy "Proxy connection failed")
   (finally (tasks/disableproxy))))

(defn ^{Test {:groups ["proxy"]}}
  bad_proxy
  "Tests error message when using a non-existant proxy."
  [_]
  (try+ (tasks/unregister)
        (catch [:type :not-registered] _))
  (try
    (let [hostname  "blahblah"
          port      "666"]
      (tasks/enableproxy hostname :port port)
      (tasks/verify-conf-proxies hostname port "" ""))
    (let [thrown-error (try+ (register)
                             (catch Object e (:type e)))]
      (verify (= thrown-error :network-error)))
    (finally
     (if (= 1 (tasks/ui guiexist :register-dialog))
       (tasks/ui click :register-cancel))
     (disable_proxy nil))))

(defn ^{Test {:groups ["proxy" "blockedByBug-729688"]}}
  bad_proxy_facts
  "Tests facts-update through a bad proxy."
  [_]
  (disable_proxy nil)
  (register)
  (try
    (let [hostname  "blahblah"
          port      "666"]
      (tasks/enableproxy hostname :port port)
      (tasks/verify-conf-proxies hostname port "" ""))
    (let [thrown-error (try+ (tasks/ui click :view-system-facts)
                             (tasks/ui waittillguiexist :facts-view)
                             (tasks/ui click :update-facts)
                             (tasks/checkforerror)
                             (catch Object e
                               (:type e)))]
      (verify (= thrown-error :error-updating)))
    (finally
     (if (= 1 (tasks/ui guiexist :facts-dialog))
       (tasks/ui click :close-facts))
     (disable_proxy nil))))

(defn ^{Test {:groups ["proxy" "blockedByBug-806993"]}}
  test_proxy_formatting
  "Tests the auto-formatting feature of the proxy location field."
  [_]
  (try+
   (tasks/enableproxy "http://some.host.name:1337")
   (tasks/ui click :configure-proxy)
   (tasks/ui waittillwindowexist :proxy-config-dialog 60)
   (let [location (tasks/ui gettextvalue :proxy-location)]
     (verify (not (tasks/substring? "http://" location)))
     (verify (= 2 (count (clojure.string/split location #":")))))
   (finally
     (tasks/ui click :close-proxy)
     (disable_proxy nil))))

(defn ^{AfterClass {:groups ["setup"]
                    :alwaysRun true}}
  cleanup [_]
  (disable_proxy nil)
  (tasks/restart-app))

(gen-class-testng)
