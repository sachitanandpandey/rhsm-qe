(ns rhsm.gui.tests.autosubscribe_tests
  (:use [test-clj.testng :only (gen-class-testng)]
        [rhsm.gui.tasks.test-config :only (config
                                           clientcmd)]
        [com.redhat.qe.verify :only (verify)]
        [clojure.string :only (trim
                               split
                               split-lines
                               blank?)]
        [slingshot.slingshot :only [throw+
                                    try+]]
        rhsm.gui.tasks.tools
        gnome.ldtp)
  (:require [clojure.tools.logging :as log]
            [rhsm.gui.tasks.tasks :as tasks]
            [rhsm.gui.tests.subscribe_tests :as subscribe]
            [rhsm.gui.tests.base :as base]
            [rhsm.gui.tasks.candlepin-tasks :as ctasks]
             rhsm.gui.tasks.ui)
  (:import [org.testng.annotations
            BeforeClass
            AfterClass
            BeforeGroups
            AfterGroups
            Test
            DataProvider]
           org.testng.SkipException
           [rhsm.cli.tests ComplianceTests]
           [com.redhat.qe.auto.bugzilla BzChecker]))

(def somedir (ComplianceTests/productCertDirForSomeProductsSubscribable))
(def alldir (ComplianceTests/productCertDirForAllProductsSubscribable))
(def nodir (ComplianceTests/productCertDirForNoProductsSubscribable))
(def nonedir (ComplianceTests/productCertDirForNoProductsinstalled))
(def one-sla-dir (ComplianceTests/productCertDirForAllProductsSubscribableByOneCommonServiceLevel))
(def multi-sla-dir (ComplianceTests/productCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel))
(def complytests (atom nil))
(def productmap (atom {}))
(def common-sla (atom nil))
(def sla-list (atom nil))
(def entitlement-map (atom nil))

(defn- dirsetup? [dir]
  (and
   (= "exists" (trim
                (:stdout
                 (run-command (str  "test -d " dir " && echo exists")))))
   (= dir (tasks/conf-file-value "productCertDir"))))

(defn setup-product-map
  []
  (reset! productmap (ctasks/build-product-map))
  @productmap)

(defn ^{BeforeClass {:groups ["setup"]}}
  setup [_]
  (try
    ;; https://bugzilla.redhat.com/show_bug.cgi?id=723051
    ;; this bug crashes everything, so fail the BeforeClass if this is open
    (verify (not (.isBugOpen (BzChecker/getInstance) "723051")))
    (verify (not (.isBugOpen (BzChecker/getInstance) "1040119")))
    (if (= "RHEL7" (get-release)) (base/startup nil))
    (tasks/kill-app)
    (reset! complytests (ComplianceTests. ))
    (.setupProductCertDirsBeforeClass @complytests)
    (let [safe-upper (fn [s] (if s (.toUpperCase s) nil))]
      (reset! common-sla
              (safe-upper (ComplianceTests/allProductsSubscribableByOneCommonServiceLevelValue)))
      (reset! sla-list
              (map #(safe-upper %)
                   (seq (ComplianceTests/allProductsSubscribableByMoreThanOneCommonServiceLevelValues)))))
    (run-command "subscription-manager unregister")
    (tasks/start-app)
    (catch Exception e
      (log/info "Skipping Test Class: Autosubscribe")
      (reset! (skip-groups :autosubscribe) true)
      (throw e))))

(defn ^{AfterClass {:groups ["cleanup"]
                    :alwaysRun true}}
  cleanup [_]
  (assert-valid-testing-arch)
  (run-command "subscription-manager unregister")
  (.configureProductCertDirAfterClass @complytests)
  (tasks/restart-app))

(defn ^{Test {:groups ["autosubscribe"
                       "configureProductCertDirForNoProductsSubscribable"
                       "blockedByBug-743704"]}}
  no_products_subscribable
  "Tests autosubscribe when no products are subscribable."
  [_]
  (run-command "subscription-manager unregister")
  (tasks/restart-app)
  (verify (dirsetup? nodir))
  (tasks/register-with-creds)
  (let [beforesubs (tasks/warn-count)
        user (@config :username)
        pass (@config :password)
        key  (@config :owner-key)
        ownername (ctasks/get-owner-display-name user pass key)]
    (verify (= (str beforesubs)
               (trim (:stdout
                      (run-command (str "ls " nodir " | wc -l"))))))
    (if (= 0 beforesubs)
        (verify (tasks/compliance?))
        (do
          (tasks/unregister)
          (let [msg (try+
                     (tasks/register user
                                     pass
                                     :skip-autosubscribe false
                                     :owner ownername)
                     (catch [:type :no-sla-available] {:keys [msg]} msg))]
            (verify (substring? "No service level will cover all installed products" msg)))
          (tasks/ui waittillwindownotexist :register-dialog 600)
          (sleep 1000)
          (verify (= (tasks/warn-count) beforesubs))
          (verify (not (tasks/compliance?)))))))

(defn ^{Test {:groups ["autosubscribe"
                       "configureProductCertDirForNoProductsInstalled"]}}
  no_products_installed
  "Tests autosubscribe when no products are installed."
  [_]
  (run-command "subscription-manager unregister")
  (tasks/restart-app)
  (verify (dirsetup? nonedir))
  (tasks/register-with-creds)
  (verify (= 0 (tasks/warn-count)))
  (verify (tasks/compliance?)))

(defn ^{Test {:groups ["autosubscribe"
                       "acceptance"
                       "configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevel"
                       "blockedByBug-857147"]}}
  simple_autosubscribe
  "Tests simple autosubscribe when all products can be covered by one service level."
  [_]
   (run-command "subscription-manager unregister")
   (tasks/restart-app)
   (verify (dirsetup? one-sla-dir))
   (tasks/register-with-creds)
   (let [beforesubs (tasks/warn-count)
         dircount (trim (:stdout
                         (run-command
                          (str "ls " one-sla-dir " | wc -l"))))
         user (@config :username)
         pass (@config :password)
         key  (@config :owner-key)
         ownername (ctasks/get-owner-display-name user pass key)]
     (tasks/unregister)
     (verify (= (str beforesubs)
               dircount))
     (if (= 0 beforesubs)
      (verify (tasks/compliance?))
      (do
        (tasks/register user
                        pass
                        :skip-autosubscribe false
                        :owner ownername)
        (tasks/ui waittillwindownotexist :register-dialog 600)
        (sleep 20000)
        (verify (<= (tasks/warn-count) beforesubs))
        (verify (tasks/compliance?))))))

(defn ^{Test {:groups ["autosubscribe"
                       "configureProductCertDirForAllProductsSubscribableByOneCommonServiceLevel"
                       "blockedByBug-921245"]
              :dependsOnMethods ["simple_autosubscribe"]}}
  assert_service_level
  "Asserts that the service level was set system wide after simple autosubscribe."
  [_]
  (if (nil? @common-sla) (throw (SkipException. "Common SLA is unset!")))
  (verify
   (substring? @common-sla
                     (:stdout (run-command "subscription-manager service-level"))))
  (let [_ (tasks/ui click :system-preferences)
        _ (tasks/ui waittillguiexist :system-preferences-dialog)
        sla-slected? (tasks/ui showing? :system-preferences-dialog @common-sla)
        _ (tasks/ui click :close-system-prefs)]
    (verify sla-slected?)))



(defn ^{Test {:groups ["autosubscribe"
                       "blockedByBug-921245"
                       "blockedByBug-977851"]
              :dataProvider "my-installed-software"}}
  assert_correct_status
  "Tests that the status for each product is correct after subscribing to everything."
  [_ product]
  (let [index (tasks/ui gettablerowindex
                        :installed-view
                        product)
        status (tasks/ui getcellvalue
                         :installed-view
                         index 2)
        expected (@productmap product)
        rhel5?   (= "RHEL5" (get-release))
        boxarch (trim (:stdout
                       (run-command
                        (str
                         "subscription-manager facts --list | "
                         (if rhel5?
                           "grep \"uname.machine\" | "
                           "grep \"lscpu.architecture\" | ")
                         "cut -f 2 -d \":\""))))
        prodarch (split (try
                          (let [arch (tasks/ui getcellvalue
                                               :installed-view
                                               index 2)]
                            (if (string? arch)
                              arch
                              ""))
                          (catch Exception e
                            ""))
                        #",")]
    ;;(println (str "index: " index))
    ;;(println (str "status: "  status))
    ;;(println (str "expected: " expected))
    ;;(println (str "boxarch: "  boxarch))
    ;;(println (str "prodarch: " prodarch))
    (condp = status
        "Subscribed" (do (verify (not-nil? expected)))
        "Not Subscribed" (if (or (not-nil? (some #{boxarch} prodarch))
                                 (not-nil? (some #{"ALL"} prodarch)))
                           (verify (nil? expected))
                           (verify true))
        "Partially Subscribed" (do (verify (not-nil? expected)))
        (do
          (log/error (format "Status \"%s\" unknown!" status))
          (verify false)))))

(defn ^{Test {:groups ["autosubscribe"
                       "blockedByBug-1009600"
                       "blockedByBug-1011703"]}}
  check_subscription_type_auto_attach
  "Asserts if type column is present in register dialog"
  [_]
  (try
    (.configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel @complytests)
    (tasks/restart-app)
    (tasks/register-with-creds)
    (tasks/ui click :auto-attach)
    (sleep 8000)
    (tasks/ui waittillwindowexist :register-dialog 80)
    (tasks/ui click :register-dialog (clojure.string/capitalize(first @sla-list)))
    (tasks/ui click :register)
    (verify (tasks/ui showing? :register-dialog "Select Service Level"))
    (let [values (into [] (tasks/get-table-elements :auto-attach-subscriptions-table 1))
          phy-virt? (fn [val]
                      (not (or (= (.toLowerCase val) "virtual")
                                 (= (.toLowerCase val) "physical"))))
          error-list (filter phy-virt? values)]
      (verify (= 0 (count error-list))))
    (finally
     (if (bool(tasks/ui guiexist :register-dialog))
       (tasks/ui click :register-cancel))
     (tasks/unregister))))

(defn ^{Test {:groups ["autosubscribe"
                       "blockedByBug-812903"
                       "blockedByBug-1005329"]}}
  autosubscribe_select_product_sla
  "Asserts if autosubscribe works with selecting product sla"
  [_]
  (try
    (.configureProductCertDirForAllProductsSubscribableByMoreThanOneCommonServiceLevel @complytests)
    (tasks/restart-app)
    (tasks/register-with-creds)
    (tasks/ui click :auto-attach)
    (sleep 10000)
    (tasks/ui waittillwindowexist :register-dialog 80)
    (tasks/ui click :register-dialog (clojure.string/capitalize(first @sla-list)))
    (tasks/ui click :register)
    (if (tasks/ui showing? :register-dialog "Confirm Subscriptions")
      (do
        (sleep 3000)
        (tasks/ui click :register)
        (tasks/ui waittillwindownotexist :register-dialog 80)))
    (verify (= 1 (tasks/ui guiexist :main-window "System is properly subscribed*")))
    (finally
     (if (bool (tasks/ui guiexist :register-dialog)) (tasks/ui click :register-cancel))
     (tasks/unregister))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATA PROVIDERS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^{DataProvider {:name "my-installed-software"}}
   get_installed_software [_ & {:keys [debug]
                                :or {debug false}}]
   (if-not (assert-skip :autosubscribe)
     (do
       (.configureProductCertDirForSomeProductsSubscribable @complytests)
       (tasks/restart-app)
       (tasks/register-with-creds)
       (let [prods (into [] (map vector (tasks/get-table-elements
                                         :installed-view
                                         0)))
             user (@config :username)
             pass (@config :password)
             key  (@config :owner-key)
             ownername (if (= "" key)
                         nil
                         (ctasks/get-owner-display-name user pass key))]
         (setup-product-map)

         (comment
           (tasks/unregister)
           (tasks/register user
                           pass
                           :skip-autosubscribe false
                           :owner ownername))
         (if-not debug
           (to-array-2d prods)
           prods)))
     (to-array-2d [])))

(gen-class-testng)


;; TODO: write a separte test for https://bugzilla.redhat.com/show_bug.cgi?id=743704
;;   and restore the override.facts


;; cruft I want to keep:

(comment
  boxarch (trim (:stdout
                 (run-command
                  (str
                   "subscription-manager facts --list | "
                   "grep \"lscpu.architecture\" | "
                   "cut -f 2 -d \":\"")))))
