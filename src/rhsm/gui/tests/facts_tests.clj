(ns rhsm.gui.tests.facts_tests
  (:use [test-clj.testng :only (gen-class-testng
                                data-driven)]
        [rhsm.gui.tasks.test-config :only (config
                                           clientcmd
                                           cli-tasks
                                           candlepin-runner)]
        [com.redhat.qe.verify :only (verify)]
        [clojure.string :only (blank?
                               split-lines
                               split
                               trim)]
        rhsm.gui.tasks.tools
        clojure.pprint
        gnome.ldtp)
  (:require [rhsm.gui.tasks.tasks :as tasks]
            [rhsm.gui.tests.base :as base]
            [rhsm.gui.tasks.candlepin-tasks :as ctasks]
            rhsm.gui.tasks.ui
            [clojure.tools.logging :as log])
  (:import [org.testng.annotations
            BeforeClass
            BeforeGroups
            AfterGroups
            Test
            DataProvider]
           org.testng.SkipException))

(def gui-facts (atom nil))
(def cli-facts (atom nil))
(def installed-certs (atom nil))
(def productstatus (atom nil))
(def status-before-subscribe (atom {}))
(def socket-val (atom nil)) ;; holds system socket value
(def ns-log "rhsm.gui.tests.facts_tests")

(defn get-cli-facts []
  (let [allfacts (:stdout
                  (run-command "subscription-manager facts --list"))
        allfactpairs (split-lines allfacts)
        factslist (into {} (map (fn [fact] (vec (split fact #": ")))
                                allfactpairs))]
    factslist))

(defn map-installed-certs []
  (let [certlist (map (fn [cert]
                        (.productNamespace cert))
                      (.getCurrentProductCerts @cli-tasks))
        productlist (atom {})]
    (doseq [c certlist]
      (let [name (.name c)
            version (.version c)
            arch (.arch c)]
        (if (nil? (@productlist name))
          (reset! productlist
                  (assoc @productlist
                    name
                    {:version version
                     :arch arch})))))
    @productlist))

(defn ^{BeforeClass {:groups ["facts"]}}
  register [_]
  (try
    (if (= "RHEL7" (get-release)) (base/startup nil))
    (tasks/register-with-creds)
    (reset! gui-facts (tasks/get-all-facts))
    (reset! cli-facts (get-cli-facts))
    (reset! installed-certs (map-installed-certs))
    (catch Exception e
      (reset! (skip-groups :facts) true)
      (throw e))))

(defn ^{Test {:groups ["facts"
                       "tier1"]
              :dataProvider "guifacts"}}
  match_each_fact
  "Tests that each fact in the GUI is showing the expected or known value."
  [_ fact value]
  (skip-if-bz-open "921249" (substring? "virt" fact))
  (verify (= (@cli-facts fact) value)))

(defn ^{Test {:groups ["facts"
                       "tier2"
                       "blockedByBug-950146"]}}
  facts_parity
  "Tests that the gui shows the same number of facts as the CLI."
  [_]
  (let [cli-uniq (clojure.set/difference (set @cli-facts) (set @gui-facts))
        gui-uniq (clojure.set/difference (set @gui-facts) (set @cli-facts))]
    (log/info (str "CLI uniques are: " cli-uniq))
    (log/info (str "GUI uniques are: " gui-uniq))
    (verify (empty? cli-uniq))
    (verify (empty? gui-uniq))))

(defn ^{Test {:groups ["facts"
                       "tier3"
                       "blockedByBug-683550"
                       "blockedByBug-825309"]
              :dataProvider "installed-products"}}
  check_version_arch
  "Checks that the version and arch field are displayed properly for each product."
  [_ product index]
  (tasks/unsubscribe_all)
  (let [version (:version (@installed-certs product))
        arch (:arch (@installed-certs product))
        guiversion (try (tasks/ui getcellvalue :installed-view index 1)
                        (catch Exception e nil))
        guiarch (try
                  (tasks/ui selectrowindex :installed-view index)
                  (tasks/ui gettextvalue :arch)
                     (catch Exception e nil))]
    (when-not (= 0 guiversion) (verify (= version guiversion)))
    (if (nil? arch)
      (verify (or (= "" guiarch) (nil? guiarch)))
      (verify (= arch guiarch)))))

;; run ^^this^^ in the console with:
;; (doseq [[p i] (ftest/get_installed_products nil :debug true)] (ftest/check_version_arch nil p i))

(defn ^{Test {:groups ["facts"
                       "tier2"
                       "blockedByBug-905136"
                       "blockedByBug-869306"]}}
  check_org_id
  "Tests that the orginization id is displayed properly in he facts dialog."
  [_]
  (try
    (tasks/ui click :view-system-facts)
    (tasks/ui waittillwindowexist :facts-dialog 10)
    (let [cli-raw (:stdout
                   (run-command "subscription-manager identity | grep 'org ID'"))
          cli-val (trim (last (split cli-raw #":")))
          gui-raw (tasks/ui gettextvalue :facts-org)
          ;gui-val (re-find #"\w+" (last (split gui-raw #" ")))
          ]
      ;(verify (= gui-val cli-val))
      (verify (substring? cli-val gui-raw)))
    (finally (tasks/ui click :close-facts))))

(defn ^{Test {:groups ["facts"
                       "tier2"
                       "blockedByBug-909294"
                       "blockedByBug-839772"]}}
  check_available_service_levels
  "Checks that all available service levels are shown in the GUI properly."
  [_]
  (try
    (if (tasks/ui showing? :register-system)
      (tasks/register-with-creds))
    (let [rawlevels (:stdout
                     (run-command "subscription-manager service-level --list"))
          cli-levels (drop 3 (split-lines rawlevels))
          expected-levels (sort (conj cli-levels "Not Set"))]
      (tasks/ui click :preferences)
      (tasks/ui waittillwindowexist :system-preferences-dialog 10)
      (tasks/ui showlist :service-level-dropdown)
      (let [gui-levels (sort (tasks/ui listsubmenus :service-level-dropdown))]
        (verify (= expected-levels gui-levels))
        (verify (not (nil? (some #{"Not Set"} gui-levels))))))
    (catch Exception e
      (if (substring? "Unable to find object name in application map"
                      (.getMessage e))
        (throw (SkipException.
                (str "Cannot access combo-box !! Skipping Test 'check_available_releases'.")))))
    (finally (if (bool (tasks/ui guiexist :system-preferences-dialog))
               (tasks/ui click :close-system-prefs)))))

(defn ^{Test {:groups ["facts"
                       "tier2"
                       "blockedByBug-909294"
                       "blockedByBug-908954"
                       "blockedByBug-839772"]}}
  check_available_releases
  "Checks that all avaiable releases are shown in the GUI properly."
  [_]
  (try
    (tasks/register-with-creds)
    (tasks/subscribe_all)
    (let [result (run-command "subscription-manager release --list")
          stdout (:stdout result)
          cli-releases  (if (clojure.string/blank? stdout)
                          []
                          (drop 3 (split-lines stdout)))
          expected-releases (into [] (sort (conj cli-releases "Not Set")))]
      (tasks/ui click :preferences)
      (tasks/ui waittillwindowexist :system-preferences-dialog 10)
      (tasks/ui showlist :release-dropdown)
      (sleep 2000)
      (let [gui-releases (into [] (sort (tasks/ui listsubmenus :release-dropdown)))]
        (verify (bash-bool (compare expected-releases gui-releases)))
        (verify (not (nil? (some #{"Not Set"} gui-releases))))))
    (catch Exception e
      (if (substring? "Unable to find object name in application map"
                      (.getMessage e))
        (throw (SkipException.
                (str "Cannot access combo-box !! Skipping Test 'check_available_releases'.")))))
    (finally (tasks/unsubscribe_all)
             (if (bool (tasks/ui guiexist :system-preferences-dialog))
               (tasks/ui click :close-system-prefs)))))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "acceptance"]}}
  check_releases
  "Tests that all available releases are shown in the GUI"
  [_]
  (let [certdir (tasks/conf-file-value "productCertDir")
        rhelcerts ["68" "69" "71" "72" "74" "76"]
        certlist (map #(str certdir "/" % ".pem") rhelcerts)
        certexist? (map #(= 0 (:exitcode
                               (run-command (str "test -f " %))))
                        certlist)]
    (verify (some true? certexist?)))
  (check_available_releases nil))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "blockedByBug-829900"]}}
  verify_about_information
  "Asserts that all the information in the about dialog is correct."
  [_]
  (try
    (tasks/restart-app)
    (tasks/ui click :about)
    (tasks/ui waittillwindowexist :about-dialog 10)
    (let [get-gui-version (fn [k] (last (split (tasks/ui gettextvalue k) #" ")))
          gui-pyrhsm (get-gui-version :python-rhsm-version)
          gui-rhsm (get-gui-version :rhsm-version)
          gui-rhsm-service (get-gui-version :rhsm-service-version)
          rpm-qi (fn [p s] (trim (:stdout
                                 (run-command
                                  (str "rpm -qi " p " | grep " s " | awk '{print $3}'")))))
          get-cli-version (fn [p] (str (rpm-qi p "Version") "-" (rpm-qi p "Release")))
          cli-pyrhsm (trim (get-cli-version "python-rhsm"))
          cli-rhsm (trim (get-cli-version "subscription-manager-gui"))
          cli-rhsm-service (trim (ctasks/get-rhsm-service-version))]
      (verify (= gui-pyrhsm cli-pyrhsm))
      (verify (= gui-rhsm cli-rhsm))
      (verify (= gui-rhsm-service cli-rhsm-service)))
    (finally (if (bool (tasks/ui guiexist :about-dialog))
               (tasks/ui click :close-about-dialog)))))

(defn ^{BeforeGroups {:groups ["facts"
                               "tier3"]
                      :value ["facts-product-status"]}}
  before_check_product_status [_]
  (let [output (:stdout
                (run-command "subscription-manager attach --auto"))
        not-blank? (fn [s] (not (blank? s)))
        raw-cli-data (filter not-blank? (drop 1 (split-lines output)))
        status (fn [s] (re-find #"Status.*" s))
        products (fn [s] (re-find #"Product Name.*" s))
        grab-value (fn [item] (trim (last (split item #":"))))
        not-nil? (fn [s] (not (nil? s)))
        list-status (into [] (map grab-value (filter not-nil? (map status raw-cli-data))))
        list-products (into [] (map grab-value (filter not-nil? (map products raw-cli-data))))
        cli-data (zipmap list-products list-status)]
    (reset! productstatus cli-data)))

(defn ^{Test {:groups ["facts"
                       "tier3"
                       "facts-product-status"
                       "blockedByBug-964332"]
              :dataProvider "installed-products"
              :priority (int 10)}}
  check_product_status
  "Asserts that all product statuses match the known statuses in the CLI."
  [_ product row]
  (let [gui-value (tasks/ui getcellvalue :installed-view row 2)
        cli-value (get @productstatus product)]
    (verify (= gui-value cli-value))))

(defn ^{AfterGroups {:groups ["facts"
                              "tier3"]
                     :value ["facts-product-status"]
                     :alwaysRun true}}
  after_check_product_status [_]
  (tasks/unsubscribe_all))

(defn ^{Test {:groups ["facts"
                       "tier3"]
              :dependsOnMethods ["check_product_status"]
              :dataProvider "installed-products"
              :priority (int 20)}}
  check_product_status_unsubscribe
  "Checks product status is correct after unsubscribing."
  [_ product row]
  (let [gui-status (tasks/ui getcellvalue :installed-view row 2)]
    (verify (= gui-status "Not Subscribed"))))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "blockedByBug-977855"]}}
  check_persistant_autoheal
  "Asserts that the selection made in the autohal checkbox is persistant."
  [_]
  (try
    (tasks/restart-app)
    (let [is-checked? (fn [] (bool (tasks/ui verifycheck :autoheal-checkbox)))
          waittillcheck (fn [b s]
                          (try
                            (loop-timeout
                             (* s 1000) []
                             (if-not (= b (is-checked?))
                               (do (sleep 500)
                                   (recur))
                               b))
                            (catch RuntimeException e (not b))))
          cpa (fn [c b]
                (tasks/ui click :preferences)
                (tasks/ui waittillwindowexist :system-preferences-dialog 5)
                (tasks/ui c :autoheal-checkbox)
                ;(println (str "first - c:" c " b:" b))
                (verify (= b (waittillcheck b 5)))
                (tasks/ui click :close-system-prefs)
                (tasks/ui waittillwindownotexist :system-preferences-dialog 5)
                (tasks/ui click :preferences)
                (tasks/ui waittillwindowexist :system-preferences-dialog 5)
                ;(println (str "second - c:" c " b:" b))
                (verify (= b (waittillcheck b 5)))
                (tasks/ui click :close-system-prefs)
                (tasks/ui waittillwindownotexist :system-preferences-dialog 5))]
      (cpa uncheck false)
      (cpa check true))
    (finally
      (if (bool (tasks/ui guiexist :system-preferences-dialog))
        (tasks/ui click :close-system-prefs)))))

(defn ^{BeforeGroups {:groups ["facts"
                               "tier1"]
                      :value ["check_status_message_for_subscriptions"]}}
  before_check_status_message
  [_]
  (tasks/unsubscribe_all)
  (tasks/restart-app :reregister? true))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "blockedByBug-1012501"
                       "blockedByBug-1040119"]
              :value ["check_status_message_for_subscriptions"]
              :priority (int 100)}}
  check_status_message_before_attaching
  "Asserts that status message displayed in main-window is right before subscriptions are attached"
  [_]
  (try
    (let
  	[installed-products (atom {})]
      (reset! installed-products (tasks/ui getrowcount :installed-view))
      (reset! status-before-subscribe
              (Integer. (re-find #"\d*" (tasks/ui gettextvalue :overall-status))))
      (verify (= @installed-products @status-before-subscribe)))))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "blockedByBug-1012501"
                       "blockedByBug-1040119"]
              :value ["check_status_message_for_subscriptions"]
              :dependsOnMethods ["check_status_message_before_attaching"]
              :priority (int 101)}}
  check_status_message_after_attaching
  "Asserts that status message displayed in main-window is right after attaching subscriptions"
  [_]
  (try
    (let
  	[subscribed-products (atom {})
         after-subscribe (atom {})]
      (tasks/search :match-installed? true)
      (dotimes [n 3]
        (tasks/subscribe (tasks/ui getcellvalue :all-subscriptions-view
                                   (rand-int (tasks/ui getrowcount :all-subscriptions-view)) 0)))
      (reset! subscribed-products (count (filter #(= "Subscribed" %)
                                                 (tasks/get-table-elements :installed-view 2))))
      (reset! after-subscribe (Integer. (re-find #"\d*"
                                                 (tasks/ui gettextvalue :overall-status))))
      (verify (= @after-subscribe (- @status-before-subscribe @subscribed-products))))))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "blockedByBug-1012501"
                       "blockedByBug-1040119"]
              :value ["check_status_message_for_subscriptions"]
              :dependsOnMethods ["check_status_message_after_attaching"]
              :priority (int 102)}}
  check_status_message_future_subscriptions
  "Asserts that status message displayed in main-window is right after attaching future
   subscriptions"
  [_]
  (try
    (let
  	[subscribed-products-date (atom {})
         after-date-products (atom {})
         present-date (do (tasks/ui selecttab :all-available-subscriptions)
                          (tasks/ui gettextvalue :date-entry))
         date-split (split present-date #"-")
         year (first date-split)
         month (second date-split)
         day (last date-split)
         new-year (+ (Integer. (re-find  #"\d+" year)) 1)]
      (tasks/ui enterstring :date-entry (str new-year "-" month "-" day))
      (tasks/search :match-installed? true)
      (dotimes [n 3]
        (tasks/subscribe (tasks/ui getcellvalue :all-subscriptions-view
                                   (rand-int (tasks/ui getrowcount :all-subscriptions-view)) 0)))
      (reset! subscribed-products-date (count (filter #(= "Subscribed" %)
                                                      (tasks/get-table-elements :installed-view 2))))
      (reset! after-date-products (Integer. (re-find #"\d*"
                                                     (tasks/ui gettextvalue :overall-status))))
      (verify (= @after-date-products (- @status-before-subscribe @subscribed-products-date))))))

(defn ^{Test {:groups ["facts"
                       "tier1"
                       "blockedByBug-1012501"
                       "blockedByBug-1040119"]
              :value ["check_status_message_for_subscriptions"]
              :dependsOnMethods ["check_status_message_future_subscriptions"]
              :priority (int 103)}}
  check_status_message_expired_subscriptions
  "Asserts that status message displayed in main-window is right after expiring
   attached subscriptions"
  [_]
  (if (nil?  @candlepin-runner)
    (throw (SkipException.
            (str "This test will not run on stage as a candelpin-runner cannot be created")))
    (do
      (try
        (let
            [subscribed-products-future (atom {})
             after-future-subscribe (atom {})]
          (run-command "date -s \"+1 year\"")
          (run-command "date -s \"+1 year\"" :runner @candlepin-runner)
          (tasks/restart-app)
          (reset! subscribed-products-future
                  (count (filter #(= "Subscribed" %)
                                 (tasks/get-table-elements :installed-view 2))))
          (reset! after-future-subscribe
                  (Integer. (re-find #"\d*"
                                     (tasks/ui gettextvalue :overall-status))))
          (verify (= @after-future-subscribe
                     (- @status-before-subscribe @subscribed-products-future))))
        (finally
          (run-command "date -s \"-1 year\"" :runner @candlepin-runner)
          (run-command "date -s \"-1 year\""))))))

(defn ^{AfterGroups {:groups ["facts"
                              "tier1"]
                     :value ["check_status_message_for_subscriptions"]
                     :alwaysRun true}}
  after_check_status_message
  [_]
  (let
      [time-cmd (str "systemctl stop ntpd.service;"
                   " ntpdate clock.redhat.com;"
                   " systemctl start ntpd.service")]
    (:stdout (run-command time-cmd))
    (:stdout (run-command time-cmd :runner @candlepin-runner))))


(defn ^{Test {:groups ["facts"
                       "tier2"
                       "acceptance"]}}
  verify_update_facts
  "Verifies if update facts grabs updated facts value"
  [_]
  (try
    (tasks/unsubscribe_all)
    (if (tasks/ui showing? :register-system)
      (tasks/register-with-creds))
    (tasks/write-facts "{\"cpu.cpu_socket(s)\": \"20\"}")
    (tasks/ui click :view-system-facts)
    (tasks/ui click :update-facts)
    (let [old-val (reset! socket-val
                          (get @gui-facts "cpu.cpu_socket(s)"))
          groups (tasks/get-table-elements :facts-view 0)
          items (zipmap groups (range (count groups)))
          row-num (get items "cpu")
          new-val (do
                    (tasks/ui selectrowindex :facts-view row-num)
                    (sleep 500)
                    (tasks/ui doubleclickrow :facts-view "cpu")
                    (sleep 500)
                    (last (for [x (range row-num 100)
                                :let [y (tasks/ui getcellvalue :facts-view x 0)]
                                :while (not (= "cpu.cpu_socket(s)" y))]
                            (tasks/ui getcellvalue :facts-view (+ x 1) 1))))]
      (verify (not (= old-val new-val))))
    (finally
      (tasks/ui click :close-facts)
      (tasks/write-facts (str "{\"cpu.cpu_socket(s)\":" \space "\"" @socket-val "\"}")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATA PROVIDERS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^{DataProvider {:name "guifacts"}}
  get_facts [_ & {:keys [debug]
                  :or {debug false}}]
  (log/info (str "======= Starting DataProvider: " ns-log "get_facts()"))
  (if-not (assert-skip :facts)
    (do
      (if-not debug
        (to-array-2d (vec @gui-facts))
        (vec @gui-facts)))
    (to-array-2d [])))

(defn ^{DataProvider {:name "installed-products"}}
  get_installed_products [_ & {:keys [debug]
                               :or {debug false}}]
  (log/info (str "======= Starting DataProvider: " ns-log "get_installed_products()"))
  (if-not (assert-skip :facts)
    (do
      (let [prods (tasks/get-table-elements :installed-view 0)
            indexes (range 0 (tasks/ui getrowcount :installed-view))
            prodlist (map (fn [item index] [item index]) prods indexes)]
        (if-not debug
          (to-array-2d (vec prodlist))
          prodlist)))
    (to-array-2d [])))

(defn printfact []
  (pprint (sort @gui-facts))
  (println (str "cli-facts: " (count @cli-facts)))
  (println (str "gui-facts: " (count @gui-facts)))
  (println (str "fact: " (@cli-facts "virt.is_guest")))
  (println (str "fact: " (@gui-facts "virt.is_guest"))))

(gen-class-testng)
