(ns rhsm.gui.tests.system_tests
  (:use [test-clj.testng :only (gen-class-testng
                                data-driven)]
        [rhsm.gui.tasks.test-config :only (config
                                           clientcmd)]
        [com.redhat.qe.verify :only (verify)]
        [clojure.string :only (split
                               trim
                               blank?
                               trim-newline)]
        [slingshot.slingshot :only [throw+
                                    try+]]
        rhsm.gui.tasks.tools
        gnome.ldtp)
  (:require [rhsm.gui.tasks.tasks :as tasks]
            [clojure.tools.logging :as log]
            [rhsm.gui.tests.base :as base]
            [rhsm.gui.tasks.candlepin-tasks :as ctasks]
             rhsm.gui.tasks.ui)
  (:import [org.testng.annotations
            BeforeClass
            BeforeGroups
            AfterGroups
            Test
            DataProvider
            AfterClass]
            org.testng.SkipException))

(def ldtpd-log "/var/log/ldtpd/ldtpd.log")
(def rhsm-log "/var/log/rhsm/rhsm.log")
(def tmpCAcertpath "/tmp/CA-certs/")
(def CAcertpath "/etc/rhsm/ca/")
(def unreg-status "Keep your system up to date by registering.")
(def virt? (atom nil)) ;; Used to hold the virt value of system
(def ns-log "rhsm.gui.tests.system_tests")

(defn ^{BeforeClass {:groups ["setup"]}}
  clear_env [_]
  (try
    (if (= "RHEL7" (get-release)) (base/startup nil))
    (tasks/kill-app)
    (catch Exception e
      (reset! (skip-groups :system) true)
      (throw e))))

(defn ^{AfterClass {:groups ["setup"]
                    :alwaysRun true}}
  restart_env [_]
  (assert-valid-testing-arch)
  (tasks/restart-app :unregister? true))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-656896"]}}
  check_libglade_warnings
  "Asserts that the libglade-WARNINGs are corrected."
  [_]
  (try
    (let [output (get-logging @clientcmd
                              ldtpd-log
                              "check_libglade_warnings"
                              "libglade-WARNING"
                              (tasks/start-app))]
     (verify (not (substring? "libglade-WARNING" output))))
   (finally (tasks/kill-app))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-909823"]}}
  check_gtype_warnings
  "Asserts that the gtype WARNINGs are corrected."
  [_]
  (let [output (get-logging @clientcmd
                                  ldtpd-log
                                  "check_gtype_warnings"
                                  "gtype"
                                  (tasks/start-app))]
    (verify (not (substring? "gtype" output))))
  (tasks/kill-app))

(defn ^{Test {:groups ["system"
                       "tier1"
                       "blockedByBug-706384"]}}
  run_second_instance
  "Asserts that a second instance of rhsm-gui cannot be run."
  [_]
  (tasks/restart-app)
  (let [output (get-logging @clientcmd
                            ldtpd-log
                            "run_second_instance"
                            nil
                            (tasks/start-app)
                            (sleep 10000))]
    (verify (substring? "subscription-manager-gui is already running" output))
    (verify (not (substring? "Traceback" output)))))

(defn ^{Test {:groups ["system"
                       "tier1"
                       "blockedByBug-747014"]}}
  check_help_button
  "Assertst that the help window opens."
  [_]
  (try
    (tasks/ui click :getting-started)
    (tasks/ui waittillwindowexist :help-dialog 10)
    (verify (bool (tasks/ui guiexist :help-dialog)))
    (finally
     (tasks/ui closewindow :help-dialog))))

(defn check_escape_window
  "Asserts that windows correctly render after exiting them with a shortcut."
  [window shortcut]
  (if (= "RHEL7" (get-release))
    (throw (SkipException.
            (str "Command 'generatekeyevent' failed! Skipping Test 'check_escape_window'.")))
    (do
      (if (or (= window :question-dialog)
              (= window :system-preferences-dialog))
        (tasks/restart-app :reregister? true)
        (tasks/restart-app :unregister? true))
      (sleep 3000)
      (let [exec-shortcut (fn [s] (tasks/ui generatekeyevent s))
            count-objects (fn [w] (count (tasks/ui getobjectlist w)))
            beforecount (do (exec-shortcut shortcut)
                                        ;sleeps are necessary because window doesn't instantly render
                            (tasks/ui waittillwindowexist window 10)
                            (sleep 3000)
                            (count-objects window))
                                        ;this has to be here due to weird issues in RHEL5
                                        ; where the objectlist was getting cached
                                        ; creating a traceback dumps the cache and this works for a quick fix
            fuckcache (fn [] (try+ (tasks/ui getchild "blah")
                                  (catch Exception e "")))]
        (comment
          (if (= "RHEL5" (get-release))
            (do
              (fuckcache)
              (log/info (str "Items: " beforecount))
              (fuckcache))))
        (exec-shortcut "<ESC>")
        (tasks/ui waittillguinotexist window)
        (try
          (exec-shortcut shortcut)
          (tasks/ui waittillwindowexist window 10)
          (if (= window :question-dialog)
            (verify (tasks/ui showing? :question-dialog
                              "Are you sure you want to unregister?")))
          (sleep 3000)
          (let [newcount (count-objects window)]
            (verify (= beforecount newcount)))
          (finally
            (tasks/restart-app :unregister? true)))))))

(data-driven
 check_escape_window {Test {:groups ["system"
                                     "tier2"
                                     "blockedByBug-862099"]}}
 [(if-not (assert-skip :system)
    (do
      [:register-dialog "<CTRL>r"]
      [:import-dialog "<CTRL>i"]
      [:system-preferences-dialog "<CTRL>p"]
      [:proxy-config-dialog "<CTRL>x"]
      [:question-dialog "<CTRL>u"]
      [:about-dialog "<CTRL>a"])
    (to-array-2d []))])


(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-785203"]}}
  check_close_button
  "Checks that the close menu item works."
  [_]
  (tasks/restart-app)
  (try
    (tasks/ui selectmenuitem :main-window "System")
    (tasks/ui selectmenuitem :main-window "Quit")
    (tasks/ui waittillwindownotexist :main-window 5)
    (verify (not (bool (tasks/ui guiexist :main-window))))
    (finally (tasks/restart-app))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-833578"]}}
  check_online_documentation
  "Asserts that the online documentation opens."
  [_]
  (try
    (if (substring? "not installed" (:stdout (run-command "rpm -q firefox")))
      (throw (SkipException. (str "Firefox does not exist on this machine"))))
    (if (not (bool (tasks/ui guiexist :main-window)))
      (tasks/start-app))
    (let [output (get-logging @clientcmd
                              ldtpd-log
                              "check_online_documentation"
                              nil
                              (do
                                (tasks/ui click :online-documentation)
                                (sleep 5000)))]
      (verify (bool (tasks/ui appundertest "Firefox")))
      (verify (not (substring? "Traceback" output))))
    (finally
      (run-command "killall -9 firefox"))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-707041"]}}
  date_picker_traceback
  "Asserts that the date chooser does not throw a traceback."
  [_]
  (try
    (if-not (bool (tasks/ui guiexist :main-window)) (tasks/restart-app))
    (try+ (tasks/register-with-creds :re-register? false)
          (catch [:type :already-registered] _))
    (tasks/ui selecttab :all-available-subscriptions)
    (let [output (get-logging @clientcmd
                                    "/var/log/ldtpd/ldtpd.log"
                                    "date_picker_traceback"
                                    "Traceback"
                                    (tasks/ui click :calendar)
                                    (verify
                                     (bool (tasks/ui waittillwindowexist :date-selection-dialog 10)))
                                    (tasks/ui click :today))]
      (verify (clojure.string/blank? output)))
    (finally (if (bool (tasks/ui guiexist :date-selection-dialog))
               (tasks/ui closewindow :date-selection-dialog)))))

(defn ^{Test {:groups ["system"
                       "tier1"
                       "blockedByBug-947485"]}}
  open_with_bad_hostname
  "Verifies that the gui can open with a bad hostname in /etc/rhsm/rhsm.conf."
  [_]
  (let [hostname (tasks/conf-file-value "hostname")]
    (try
     (run-command "subscription-manager clean")
     (tasks/restart-app)
     (tasks/register-with-creds)
     (tasks/kill-app)
     (tasks/set-conf-file-value "hostname" "blahblahdoesnotexist.redhat.com")
     (tasks/start-app)
     (tasks/ui waittillwindowexist :main-window 20)
     (verify (bool (tasks/ui guiexist :main-window)))
     (finally
       (tasks/set-conf-file-value "hostname" hostname)))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-920091"
                       "blockedByBug-1037712"]}}
  cli_unregister_check_traceback
  "Verifies whether it causes traceback when GUI is running and sub-man is unregistered through CLI"
  [_]
  (tasks/restart-app)
  (tasks/register-with-creds)
  (let [output (get-logging @clientcmd
                                  rhsm-log
                                  "check_traceback"
                                  nil
                                  (run-command "subscription-manager unregister"))]
    (verify (not (substring? "Traceback" output))))
  (tasks/kill-app))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-960465"]}}
    launch_gui_with_invalid_cert
    "Test to verify GUI can be launched with invalid certs"
    [_]
    (tasks/kill-app)
    (run-command "subscription-manager unregister")
    (try
      (safe-delete tmpCAcertpath)
      (run-command (str "mkdir " tmpCAcertpath))
      (tasks/set-conf-file-value "ca_cert_dir" tmpCAcertpath)
      (verify (= 1 (tasks/start-app)))
      (finally
        (tasks/set-conf-file-value "ca_cert_dir" CAcertpath))))

(defn ^{Test {:groups ["system"
                       "tier1"
                       "blockedByBug-923873"]}}
  check_status_when_unregistered
  "To verify that status in MyInstalledProducts icon color and product status
   are appropriately displayed when client is unregistered"
  [_]
  (tasks/restart-app :unregister? true)
  (run-command "subscription-manager clean")
  (verify (= unreg-status (tasks/ui gettextvalue :overall-status)))
  (tasks/do-to-all-rows-in
   :installed-view 2
   (fn [status]
     (verify (= status "Unknown")))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-1086377"
                       "blockedByBug-916666"]
              :priority (int 30)}}
  rhsmcertd_restart_check_timestamp
  "Checks whether the timestamp at which cert check was intiated is
   in sync with that displayed in help dialog"
  [_]
  (try
    (run-command "subscription-manager unregister")
    (run-command "subscription-manager clean")
    (let
        [rhsmcertd-log "/var/log/rhsm/rhsmcertd.log"
         output (get-logging @clientcmd
                             rhsmcertd-log
                             "cert-check-timestamp"
                             "Cert check interval"
                             (if (= (get-release) "RHEL7")
                               (run-command "systemctl restart rhsmcertd.service")
                               (run-command "service rhsmcertd restart")))
         log-timestamp (re-find #"\d+:\d+:\d+" output)
         ;; The following steps add minutes to the time as this is the default
         ;; interval in conf file. The step which follows is conversion of time
         ;; formats this is because the logs have 24hrs time format and the GUI
         ;; has 12hrs time format. The last step adds a zero if the time
         ;; is less than 10hrs which makes sting comparison easier
         interval (trim-newline (:stdout
                                 (run-command
                                  "cat /etc/rhsm/rhsm.conf | grep 'certCheckInterval'")))
         time-to-be-added (/ (read-string (re-find #"\d+" (str interval)))60)
         hours-log (first (clojure.string/split log-timestamp #":"))
         ;; read-string throws exception on strings '08' and '09'
         processed-hours-log (if (or (= interval "08") (= interval "09"))
                               (re-find #"[^0]" hours-log) hours-log)
         new-time (+ time-to-be-added (Integer. (re-find  #"\d+" processed-hours-log)))
         hours (if (> new-time 12) (- new-time 12) new-time)
         compare-time (str (if ( < hours 10) (str "0" hours) hours)
                           (re-find #":\d+:\d+" log-timestamp))]
      (tasks/ui click :about)
      (tasks/ui waittillwindowexist :about-dialog 10)
      (verify ( = compare-time (re-find #"\d+:\d+:\d+"
                                        (tasks/ui gettextvalue :next-system-check)))))
    (finally
      (if (bool (tasks/ui guiexist :about-dialog)) (tasks/ui click :close-about-dialog))
      ;; Worstcase scenario if service rhsmcertd is stopped we have to
      ;; turn it on as  rhsmcertd_stop_check_timestamp test depends on it
      (if (= (get-release) "RHEL7")
          (if-not (substring? "Active: active (running)"
                              (:stdout (run-command "systemctl status rhsmcertd.service")))
               (run-command "systemctl start rhsmcertd.service"))
          (if-not (substring? "running"
                              (:stdout (run-command "service rhsmcertd status")))
              (run-command "service rhsmcertd start")))
              (sleep 150000))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-1086377"
                       "blockedByBug-916666"]
              :dependsOnMethods ["rhsmcertd_restart_check_timestamp"]
              :priority (int 31)}}
  rhsmcertd_stop_check_timestamp
  "Checks wheter the timestamp in about dialog is displayed when rhsmcertd is stopped"
  [_]
  (try
    (run-command "subscription-manager unregister")
    (run-command "subscription-manager clean")
    (if (= (get-release) "RHEL7")
      (run-command "systemctl stop rhsmcertd.service")
      (run-command "service rhsmcertd stop"))
    (tasks/ui click :about)
    (tasks/ui waittillwindowexist :about-dialog 10)
    (verify (not (tasks/ui showing? :next-system-check)))
    (finally
      (if (bool (tasks/ui guiexist :about-dialog)) (tasks/ui click :close-about-dialog))
      (if (= (get-release) "RHEL7")
        (run-command "systemctl start rhsmcertd.service")
        (run-command "service rhsmcertd start"))
     ;; No sleep as we can continue without waiting for the service to
     ;; start as it does not affect the normal functioning of sub-man
     )))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-984083"]}}
  check_for_break_charecters_in_popups
  "Test to ceck if there are any break characters in pop-ups"
  [_]
  (try
    (if (tasks/ui showing? :register-system)
      (tasks/register-with-creds))
    (tasks/ui click :unregister-system)
    (tasks/ui waittillwindowexist :question-dialog 30)
    (verify (not (substring? "</b>" (tasks/ui gettextvalue :question-dialog "*Are you sure*"))))
    (finally
     (if (tasks/ui guiexist :question-dialog)
       (do
         (tasks/ui click :yes)
         (tasks/checkforerror)))
     (if-not (tasks/ui showing? :register-system) (tasks/unregister))
     (tasks/restart-app))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-977850"]}}
  check_preferences_menu_state
  "Asserts that the preferences menu behaves properly when unregistered"
  [_]
  (if-not (tasks/ui showing? :register-system)
    (tasks/unregister))
  (tasks/ui click :main-window "System")
  (sleep 2000)
  (verify (not (some #(= "VISIBLE" %) (tasks/ui getallstates :preferences))))
  (tasks/register-with-creds)
  (tasks/ui click :main-window "System")
  (sleep 2000)
  (verify (some #(= "VISIBLE" %) (tasks/ui getallstates :preferences))))

(defn ^{Test {:groups ["system"
                       "tier2"
                       "blockedByBug-977850"]}}
  check_system_preference_dialog
  "Verifies behavior of system preference dialog and its content"
  [_]
  (try+ (tasks/unregister)
        (catch [:type :not-registered] _))
  (try
    (tasks/ui selectmenuitem :preferences)
    (catch Exception e
      (substring? "Select menu item failed" (.getMessage e))))
  (if (bool (tasks/ui guiexist :system-preferences-dialog))
    (do
      (tasks/ui click :close-system-prefs)
      (throw (Exception. "Preference-dialog should not be displayed when system is unregistered"))))
  (tasks/register-with-creds)
  (tasks/ui click :preferences)
  (tasks/ui waittillwindowexist :system-preferences-dialog 60)
  (if (bool (tasks/ui guiexist :system-preferences-dialog))
    (try
      (verify (tasks/ui showing? :system-preferences-dialog "Enable auto-attach preference"))
      (verify (tasks/ui check :autoheal-checkbox))
      (finally (bool (tasks/ui guiexist :system-preferences-dialog))
               (do
                 (tasks/ui check :autoheal-checkbox)
                 (tasks/ui click :close-system-prefs))))))

(defn ^{Test {:groups ["system"
                       "tier1"
                       "acceptance"
                       "blockedByBug-818282"]}}
  check_ordered_contract_options
  "Checks if contracts in contract selection dialog are ordered based on host type"
  [_]
  (tasks/restart-app :reregister? true)
  (tasks/ui selecttab :all-available-subscriptions)
  (tasks/search)
  (let
      [sub-map (zipmap (range 0 (tasks/ui getrowcount :all-subscriptions-view))
                       (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdowns? false))
       both? (fn [pair] (=  "Both" (try
                                    (tasks/ui getcellvalue :all-subscriptions-view (key pair) 1)
                                    (catch Exception e))))
       row-sub-map (into {} (filter both? sub-map))
       cli-out (:stdout (run-command "subscription-manager facts --list | grep virt.is_guest"))
       virt? (= "true" (.toLowerCase (trim (last (split (trim-newline cli-out) #":")))))]
    (if-not (empty? row-sub-map)
      (do
        (doseq [map-entry row-sub-map]
          (try
            (tasks/ui selectrowindex :all-subscriptions-view (key map-entry))
            (tasks/ui click :attach)
            (tasks/ui waittillguiexist :contract-selection-dialog)
            (let [type-list (tasks/get-table-elements :contract-selection-table 1)]
              (if virt?
                (verify (not (sorted? type-list)))
                  (verify (sorted? type-list))))
            (finally
             (if (bool (tasks/ui guiexist :contract-selection-dialog))
               (tasks/ui click :cancel-contract-selection)))))))))

(defn ^{Test {:group ["system"
                      "tier2"
                      "blockedByBug-723992"
                      "blockedByBug-1040119"]}}
  check_gui_refresh
  "Checks whether the GUI refreshes in a reasonable amount of time"
  [_]
  (tasks/restart-app :unregister? true)
  (verify (tasks/ui showing? :register-system))
  (let [username (@config :username)
        password (@config :password)
        owner (@config :owner-key)
        server (ctasks/server-path)
        cmd (str "--username=" username " --password=" password " --org=" owner " --serverurl=" server)]
    (run-command (str "subscription-manager register " cmd)))
  (sleep 2000)
  (verify (not (tasks/ui showing? :register-system)))
  (try
    (let [status (tasks/ui gettextvalue :overall-status)
          auto-suscribe (run-command "subscription-manager subscribe --auto")]
      (sleep 2000)
      (verify (not (= status (tasks/ui gettextvalue :overall-status)))))
    (finally
     (tasks/unsubscribe_all)
     (tasks/unregister))))

(defn ^{Test {:groups ["system"
                       "tier3"]
              :value ["assert_subscription_field"]
              :dataProvider "subscribed"}}
  assert_subscription_field
  "Tests whether the subscripton field in installed view is populated when the entitlement
   is subscribed"
  [_ product]
  (if (not (= "Not Subscribed"
              (tasks/ui getcellvalue :installed-view
                        (tasks/skip-dropdown :installed-view product) 2)))
    (let [map (ctasks/build-product-map :all? true)
          gui-value (set (clojure.string/split-lines
                          (tasks/ui gettextvalue :providing-subscriptions)))
          cli-value (set (get map product))]
      (verify (< 0 (count (clojure.set/intersection gui-value cli-value)))))))

(defn ^{AfterGroups {:groups ["system"
                              "tier3"]
                     :value ["assert_subscription_field"]
                     :alwaysRun true}}
  after_assert_subscription_field
  [_]
  (tasks/unsubscribe_all)
  (tasks/unregister))

(defn ^{Test {:groups ["system"
                       "tier3"]
              :value ["check_subscription_type_all_available"]
              :dataProvider "all-subscriptions"}}
  check_subscription_type_all_subscriptions
  "Checks for subscription type in all available subscriptions"
  [_ product]
  (tasks/ui selecttab :all-available-subscriptions)
  (tasks/skip-dropdown :all-subscriptions-view product)
  (verify (not (blank? (tasks/ui gettextvalue :all-available-subscription-type)))))

(defn ^{AfterGroups {:groups ["system"
                              "tier3"]
                     :value ["check_subscription_type_all_available"]
                     :alwaysRun true}}
  after_check_subscription_type_all_available
  [_]
  (tasks/unsubscribe_all)
  (tasks/unregister))

(defn ^{Test {:groups ["system"
                       "tier3"]
              :value ["check_subscription_type_my_subs"]
              :dataProvider "my-subscriptions"}}
  check_subscription_type_my_subscriptions
  "Checks for subscription type in my subscriptions"
  [_ product]
  (tasks/ui selecttab :my-subscriptions)
  (tasks/skip-dropdown :my-subscriptions-view product)
  (verify (not (blank? (tasks/ui gettextvalue :subscription-type)))))

(defn ^{AfterGroups {:groups ["system"
                              "tier3"]
                     :value ["check_subscription_type_my_subs"]
                     :alwaysRun true}}
  after_check_subscription_type_my_subscription
  [_]
  (tasks/unsubscribe_all)
  (tasks/unregister))

(defn ^{Test {:groups ["system"
                       "acceptance"
                       "tier1"
                       "blockedByBug-1051383"]}}
  check_status_column
  "Asserts that the status column of GUI has only 'Subscribed', 'Partially Subscribed'
   and 'Not Subscribed'"
  [_]
  (try
    (if (not (bool (tasks/ui guiexist :main-window)))
      (tasks/start-app))
    (let [output (get-logging @clientcmd
                              ldtpd-log
                              "check_online_documentation"
                              nil
                              (do
                                (tasks/ui click :online-documentation)
                                (sleep 5000)))]
      (verify (bool (tasks/ui appundertest "Firefox")))
      (verify (not (substring? "Traceback" output))))
    (finally
      (run-command "killall -9 firefox"))))

(defn ^{Test {:groups ["system"
                       "tier1"
                       "acceptance"]}}
    launch_gui_from_gnome
    "Launches gui from gnome"
    [_]
    (try
      (if (bool (tasks/ui guiexist :main-window))
        (tasks/kill-app))
      (sleep 3000)
      (let [release (get-release)]
        (case release
          "RHEL5" (do
                    (tasks/ui click "Top Panel" "Red Hat Subscription Manager")
                    (sleep 5000)
                    (verify (tasks/ui guiexist :main-window)))
          "RHEL6" (do
                    (tasks/ui click "frmTopExpandedEdgePanel" "Red Hat Subscription Manager")
                    (tasks/ui activatewindow :main-window)
                    (verify (tasks/ui guiexist :main-window)))
          "RHEL7" (throw (SkipException.
                          (str "Skipping Test: ldtp on RHEL7 not starting")))
          (throw (Exception. "Error: Release not identified"))))
      (finally
        (if (not (bool (tasks/ui guiexist :main-window)))
          (tasks/start-app)))))

(defn ^{Test {:groups ["system"
                       "acceptance"
                       "tier1"]}}
  check_physical_only_pools
  "Identifies physical only pools from JSON and checks
   whether it throws appropriate error message"
  [_]
  (tasks/unsubscribe_all)
  (tasks/ui selecttab :my-installed-products)
  (if (tasks/ui showing? :register-system)
    (tasks/register-with-creds))
  (try
    (let [cli-cmd (:stdout
                   (run-command "subscription-manager facts --list | grep \"virt.is_guest\""))
          virt (trim (re-find #" .*" cli-cmd))
          prod-attr-map (ctasks/build-subscription-attr-type-map :all? true)
          phy-only? (fn [v] (if (not (nil? (re-find #"physical_only" v)))
                             true false))
          filter-product (fn [m] (if (not (empty? (filter phy-only? (val m)))) (key m)))
          subscriptions (into [] (filter string? (map filter-product prod-attr-map)))
          subscribe (fn [sub] (try
                          (tasks/subscribe sub)
                          (catch Exception e
                            (let [result (substring? "Error getting subscription:" (.getMessage e))]
                              result))))]
      (reset! virt? virt)
      (if (= @virt? "False")
        (do
          (tasks/write-facts "{\"virt.is_guest\": \"True\"}")
          (run-command "subscription-manager facts --update")))
      (tasks/search :match-system? false :do-not-overlap? false)
      (verify (not (some false? (map subscribe subscriptions)))))
    (finally
      (if (= @virt? "False")
        (do
          (tasks/write-facts (str "{\"virt.is_guest\":" \space "\"" @virt? "\"" "}"))
          (run-command "subscription-manager facts --update")))
      (tasks/unsubscribe_all))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;      DATA PROVIDERS      ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^{DataProvider {:name "subscribed"}}
  installed_products [_ & {:keys [debug]
                           :or {debug false}}]
  (log/info (str "======= Starting DataProvider: "
                 ns-log "installed_products()"))
  (if-not (assert-skip :system)
    (do
      (tasks/restart-app)
      (tasks/register-with-creds)
      (tasks/ui selecttab :my-installed-products)
      (let [subs (into [] (map vector (tasks/get-table-elements
                                       :installed-view
                                       0
                                       :skip-dropdowns? true)))]
        (if-not debug
          (to-array-2d subs)
          subs)))
    (to-array-2d [])))

(defn ^{DataProvider {:name "all-subscriptions"}}
  get_subscriptions [_ & {:keys [debug]
                          :or {debug false}}]
  (log/info (str "======= Starting DataProvider: " ns-log "get_subscriptions()"))
  (if-not (assert-skip :system)
    (do
      (tasks/restart-app)
      (tasks/register-with-creds)
      (tasks/search :match-system? false
                   :do-not-overlap? false)
      (let [subs (into [] (map vector (tasks/get-table-elements
                                       :all-subscriptions-view
                                       0
                                       :skip-dropdowns? true)))]
        (if-not debug
          (to-array-2d subs)
          subs)))
    (to-array-2d [])))

(defn ^{DataProvider {:name "my-subscriptions"}}
  my_subscriptions [_ & {:keys [debug]
                         :or {debug false}}]
  (log/info (str "======= Starting DataProvider: " ns-log "my_subscriptions"))
  (if-not (assert-skip :system)
    (do
      (tasks/restart-app :reregister? true)
      (tasks/subscribe_all)
      (tasks/ui selecttab :my-subscriptions)
      (let [subs (into [] (map vector (tasks/get-table-elements
                                       :my-subscriptions-view
                                       0
                                       :skip-dropdowns? true)))]
        (if-not debug
          (to-array-2d subs)
          subs)))
    (to-array-2d [])))

(gen-class-testng)
